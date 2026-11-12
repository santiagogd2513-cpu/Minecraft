#!/usr/bin/env python3
"""Arma discos de mentira con el formato de la Xbox 360 (XTAF y paquetes STFS) para probar los lectores.

uso: hacer_imagenes.py <carpeta de salida>
  particion16.img   partición XTAF con tabla de 16 bits
  particion32.img   partición XTAF con tabla de 32 bits (clústeres de 512 bytes)
  disco.img         disco duro de mentira: la partición «Datos» en 0x130EB0000 (archivo disperso)
  usb/Data0000...   memoria USB de mentira: la partición repartida en archivos Data
  esperado.txt      lo que hay que encontrar
"""
import os, struct, sys, zlib, hashlib, random

def png(w, h, rgb):
    raw = b''.join(b'\x00' + bytes(rgb) * w for _ in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')

def utf16(s, n):
    b = s.encode('utf-16-be')
    return b[:n - 2] + b'\x00' * (n - len(b[:n - 2]))

def stfs(magic, ctype, title_id, display, title, thumb, tthumb, extra=0):
    h = bytearray(0x971A + extra)
    h[0:4] = magic.encode('latin-1')
    struct.pack_into('>I', h, 0x344, ctype)
    struct.pack_into('>I', h, 0x360, title_id)
    h[0x411:0x411 + 0x80] = utf16(display, 0x80)
    h[0x1691:0x1691 + 0x80] = utf16(title, 0x80)
    struct.pack_into('>I', h, 0x1712, len(thumb))
    struct.pack_into('>I', h, 0x1716, len(tthumb))
    h[0x171A:0x171A + len(thumb)] = thumb
    h[0x571A:0x571A + len(tthumb)] = tthumb
    return bytes(h)

class Xtaf:
    def __init__(self, size, spc):
        self.size, self.spc = size, spc
        self.cb = spc * 512
        self.entries = size // self.cb + 1
        self.fat32 = self.entries >= 0xFFF0
        fat = self.entries * (4 if self.fat32 else 2)
        fat = (fat + 0xFFF) & ~0xFFF
        self.data_at = 0x1000 + fat
        self.img = bytearray(size)
        self.fat = [0] * self.entries
        self.nextfree = 2   # el 1 es la raíz
        self.dirs = {1: []}
        struct.pack_into('>4sIII', self.img, 0, b'XTAF', 0x1234ABCD, spc, 1)

    def alloc(self, n):
        cl = list(range(self.nextfree, self.nextfree + n))
        self.nextfree += n
        end = 0xFFFFFFFF if self.fat32 else 0xFFFF
        for a, b in zip(cl, cl[1:]):
            self.fat[a] = b
        self.fat[cl[-1]] = end
        return cl

    def write_clusters(self, cl, data):
        for i, c in enumerate(cl):
            chunk = data[i * self.cb:(i + 1) * self.cb]
            at = self.data_at + (c - 1) * self.cb
            self.img[at:at + len(chunk)] = chunk

    def add_file(self, dircl, name, data):
        if data:
            n = (len(data) + self.cb - 1) // self.cb
            cl = self.alloc(n)
            self.write_clusters(cl, data)
            first = cl[0]
        else:
            first = 0
        self.dirs[dircl].append((name, False, first, len(data)))

    def add_dir(self, dircl, name):
        cl = self.alloc(1)[0]
        self.dirs[cl] = []
        self.dirs[dircl].append((name, True, cl, 0))
        return cl

    def path(self, dircl, parts):
        for p in parts:
            hit = [e for e in self.dirs[dircl] if e[0] == p and e[1]]
            dircl = hit[0][2] if hit else self.add_dir(dircl, p)
        return dircl

    def finish(self):
        # las carpetas se escriben al final (pueden necesitar más de un clúster)
        end = 0xFFFFFFFF if self.fat32 else 0xFFFF
        for cl, ents in list(self.dirs.items()):
            raw = bytearray()
            for name, isdir, first, size in ents:
                nb = name.encode('latin-1')
                e = bytearray(b'\xff' * 64)
                e[0] = len(nb); e[1] = 0x10 if isdir else 0
                e[2:2 + 42] = nb + b'\xff' * (42 - len(nb))
                struct.pack_into('>II', e, 44, first, size)
                date = ((2010 - 1980) << 9) | (7 << 5) | 15
                tm = (14 << 11) | (30 << 5) | 10
                struct.pack_into('>HHHHHH', e, 52, date, tm, date, tm, date, tm)   # creación, escritura, acceso
                raw += e
            need = max(1, (len(raw) + self.cb - 1) // self.cb)
            raw += b'\xff' * (need * self.cb - len(raw))
            # el primer clúster ya existe; los demás se piden ahora
            cls = [cl] + (self.alloc(need - 1) if need > 1 else [])
            for a, b in zip(cls, cls[1:]):
                self.fat[a] = b
            self.fat[cls[-1]] = end
            self.write_clusters(cls, bytes(raw))
        o = 0x1000
        for v in self.fat:
            if self.fat32:
                struct.pack_into('>I', self.img, o, v); o += 4
            else:
                struct.pack_into('>H', self.img, o, v); o += 2
        return bytes(self.img)


def sha1(b):
    return hashlib.sha1(b).digest()

def backing_data(n, sex):
    r = (((n + 170) // 170) << sex) + n
    if n < 170: return r
    r += ((n + 28900) // 28900) << sex
    if n < 28900: return r
    return r + (1 << sex)

def backing_table(n, sex):
    if n < 170: return 0
    r = (n // 170) * (0xAC if sex else 0xAB)
    r += ((n // 28900) + 1) << sex
    if n // 28900 == 0: return r
    return r + (1 << sex)

def paquete(entradas, sex, fragmentar=False, titulo='Paquete de prueba'):
    """entradas: lista de (nombre, indice_del_padre, es_carpeta, datos). Devuelve los bytes de un paquete STFS con SHA-1 válidos."""
    FIRST = 0xB000
    nft = (len(entradas) + 1 + 63) // 64          # bloques de la tabla de archivos
    nft = max(1, (len(entradas) + 63) // 64)
    bloques = {}      # n -> bytes
    sig = {}          # n -> siguiente
    n = 0
    ft_blocks = list(range(nft)); n = nft
    cons = {}
    asign = {}
    archivos = [(i, e) for i, e in enumerate(entradas) if not e[2] and len(e[3]) > 0]
    if fragmentar and len(archivos) >= 2:
        # los dos primeros archivos se reparten los bloques de forma intercalada (no consecutivos)
        (ia, ea), (ib, eb) = archivos[0], archivos[1]
        na, nb = (len(ea[3]) + 4095) // 4096, (len(eb[3]) + 4095) // 4096
        la, lb = [], []
        while len(la) < na or len(lb) < nb:
            if len(la) < na: la.append(n); n += 1
            if len(lb) < nb: lb.append(n); n += 1
        asign[ia], asign[ib] = la, lb
        cons[ia] = cons[ib] = False
        resto = archivos[2:]
    else:
        resto = archivos
    for i, e in resto:
        nb = (len(e[3]) + 4095) // 4096
        asign[i] = list(range(n, n + nb)); n += nb
        cons[i] = True
    total = n
    # contenido de los bloques y cadena
    for k, b in enumerate(ft_blocks):
        sig[b] = ft_blocks[k + 1] if k + 1 < len(ft_blocks) else 0xFFFFFF
    for i, lst in asign.items():
        data = entradas[i][3]
        for k, b in enumerate(lst):
            bloques[b] = data[k * 4096:(k + 1) * 4096].ljust(4096, b'\x00')
            sig[b] = lst[k + 1] if k + 1 < len(lst) else 0xFFFFFF
    # tabla de archivos
    ft = bytearray(nft * 4096)
    for i, (nombre, padre, dirp, data) in enumerate(entradas):
        nb = nombre.encode('latin-1')
        o = i * 64
        ft[o:o + len(nb)] = nb
        flags = len(nb) | (0x80 if dirp else 0) | (0x40 if cons.get(i, True) and not dirp else 0)
        ft[o + 0x28] = flags
        cnt = len(asign.get(i, []))
        ft[o + 0x29:o + 0x2C] = cnt.to_bytes(3, 'little')
        ft[o + 0x2C:o + 0x2F] = cnt.to_bytes(3, 'little')
        ft[o + 0x2F:o + 0x32] = (asign[i][0] if i in asign else 0).to_bytes(3, 'little')
        struct.pack_into('>HI', ft, o + 0x32, 0xFFFF if padre < 0 else padre, 0 if dirp else len(data))
        date = ((2011 - 1980) << 9) | (3 << 5) | 9
        tm = (10 << 11) | (20 << 5) | 5
        struct.pack_into('>I', ft, o + 0x38, (date << 16) | tm)
    for k, b in enumerate(ft_blocks):
        bloques[b] = bytes(ft[k * 4096:(k + 1) * 4096])
    # el archivo completo
    ultimo_dato = backing_data(total - 1, sex) if total else 0
    ngrupos = (total + 169) // 170
    ultima_tabla = backing_table((ngrupos - 1) * 170, sex) + (1 if sex else 0) if ngrupos else 0
    tam = FIRST + (max(ultimo_dato, ultima_tabla) + 1) * 4096 + 4096
    img = bytearray(tam)
    ht = stfs('CON ', 1, 0x4D5307E6, titulo, 'Juego de prueba', png(32, 32, (200, 30, 30)), png(64, 64, (30, 200, 60)))
    img[0:len(ht)] = ht
    struct.pack_into('>I', img, 0x340, 0xAD0E)
    img[0x379] = 0x24
    img[0x37B] = 0 if sex else 1
    struct.pack_into('<H', img, 0x37C, nft)
    img[0x37E:0x381] = (0).to_bytes(3, 'little')
    struct.pack_into('>II', img, 0x395, total, 0)
    for b, data in bloques.items():
        at = FIRST + (backing_data(b, sex) << 12)
        img[at:at + 4096] = data
    # tablas de hash de nivel 0
    l0 = {}
    for g in range(ngrupos):
        t = bytearray(4096)
        for k in range(170):
            b = g * 170 + k
            if b >= total: break
            e = sha1(bloques.get(b, b'\x00' * 4096)) + bytes([0x80]) + sig.get(b, 0xFFFFFF).to_bytes(3, 'big')
            t[k * 24:(k + 1) * 24] = e
        base = FIRST + (backing_table(g * 170, sex) << 12)
        activa = (g % 2) if sex else 0       # con dos copias, la buena alterna: la otra queda vacía
        img[base + activa * 4096:base + activa * 4096 + 4096] = t
        l0[g] = (bytes(t), activa)
    nivel = 0 if total <= 170 else 1 if total <= 28900 else 2
    step0 = 0xAC if sex else 0xAB
    step1 = 0x723A if sex else 0x718F
    def l1_pos(k): return step0 if k == 0 else k * step1 + (1 << sex)
    if nivel == 0:
        top_t, top_copy = l0[0]
    else:
        n1 = (ngrupos + 169) // 170
        l1 = {}
        for k in range(n1):
            t = bytearray(4096)
            for j in range(170):
                gg = k * 170 + j
                if gg >= ngrupos: break
                tb, cp = l0[gg]
                t[j * 24:j * 24 + 20] = sha1(tb)
                t[j * 24 + 20] = 0x80 | (0x40 if cp else 0)
            cp1 = (k % 2) if sex else 0
            at = FIRST + (l1_pos(k) << 12) + cp1 * 4096
            if at + 8192 > len(img): img.extend(b'\x00' * (at + 8192 - len(img)))
            img[at:at + 4096] = t
            l1[k] = (bytes(t), cp1)
        if nivel == 1:
            top_t, top_copy = l1[0]
        else:
            t2 = bytearray(4096)
            for k in range(n1):
                t2[k * 24:k * 24 + 20] = sha1(l1[k][0])
                t2[k * 24 + 20] = 0x80 | (0x40 if l1[k][1] else 0)
            c2 = 1 if sex else 0
            at = FIRST + (step1 << 12) + c2 * 4096
            if at + 8192 > len(img): img.extend(b'\x00' * (at + 8192 - len(img)))
            img[at:at + 4096] = t2
            top_t, top_copy = bytes(t2), c2
    img[0x381:0x395] = sha1(top_t)
    if top_copy: img[0x37B] |= 2
    # el SHA-1 de la cabecera: de 0x344 hasta la primera tabla
    img[0x32C:0x340] = sha1(bytes(img[0x344:FIRST]))
    return bytes(img)

def hacer_paquetes(out):
    rnd = random.Random(11)
    a = bytes(rnd.randrange(256) for _ in range(10_000))
    b = bytes(rnd.randrange(256) for _ in range(300_000))
    leeme = 'Archivo de prueba\n'.encode()
    texto = [('leeme.txt', -1, False, leeme), ('datos', -1, True, b''), ('a.bin', 1, False, a), ('b.bin', 1, False, b), ('vacio.txt', -1, False, b'')]
    esperado = []
    for nombre, sex, frag in (('paquete_macho.stfs', 0, False), ('paquete_hembra.stfs', 1, False), ('paquete_fragmentado.stfs', 0, True), ('paquete_hembra_frag.stfs', 1, True)):
        open(os.path.join(out, nombre), 'wb').write(paquete(texto, sex, frag))
    esperado.append('paq a.bin %d %s' % (len(a), hashlib.md5(a).hexdigest()))
    esperado.append('paq b.bin %d %s' % (len(b), hashlib.md5(b).hexdigest()))
    # un paquete grande: cruza el límite de 28 900 bloques (tablas de segundo nivel)
    grande = rnd.randbytes(4096 * 29_300)
    open(os.path.join(out, 'paquete_grande.stfs'), 'wb').write(paquete([('grande.bin', -1, False, grande), ('fin.txt', -1, False, b'fin')], 0, False))
    esperado.append('grande %d %s' % (len(grande), hashlib.md5(grande).hexdigest()))
    # un paquete mediano con dos copias de tablas, que cruza varias tablas de 170 bloques
    medio = rnd.randbytes(900_000)
    open(os.path.join(out, 'paquete_medio.stfs'), 'wb').write(paquete([('medio.bin', -1, False, medio), ('x', -1, False, b'x' * 5000)], 1, False))
    esperado.append('medio %d %s' % (len(medio), hashlib.md5(medio).hexdigest()))
    return esperado

def construir(size, spc, esperado):
    x = Xtaf(size, spc)
    x.fat[1] = 0xFFFFFFFF if x.fat32 else 0xFFFF   # la raíz, un clúster
    rnd = random.Random(7)
    root = 1
    x.add_file(root, 'name.txt', b'Hola desde la Xbox 360\n')
    big = bytes(rnd.randrange(256) for _ in range(100_000))
    x.add_file(root, 'grande.bin', big)
    esperado.append('archivo grande.bin %d %s' % (len(big), hashlib.md5(big).hexdigest()))
    c = x.path(root, ['Content', '0000000000000000', '4D5307E6', '00000001'])
    x.add_file(c, 'SAVE0001', stfs('CON ', 1, 0x4D5307E6, 'Partida 1', 'Gears of War', png(32, 32, (200, 30, 30)), png(64, 64, (30, 200, 60)), 5000))
    c2 = x.path(root, ['Content', '0000000000000000', '4D5307E6', '00000002'])
    x.add_file(c, 'PAQUETE', paquete([('leeme.txt', -1, False, b'Dentro de la Xbox'), ('datos', -1, True, b''), ('a.bin', 1, False, b'A' * 9000)], 0, False))
    x.add_file(c2, 'DLC0001', stfs('LIVE', 2, 0x4D5307E6, 'Paquete de mapas', 'Gears of War', b'', png(64, 64, (30, 200, 60))))
    c3 = x.path(root, ['Content', '0000000000000000', '41560817', '00000001'])
    x.add_file(c3, 'GUARDADO', stfs('CON ', 1, 0x41560817, 'Nivel 3', 'Ñandú Racing', b'', png(64, 64, (10, 10, 250))))
    c4 = x.path(root, ['Content', 'E000123456789ABC', 'FFFE07D1', '00010000'])
    x.add_file(c4, 'E000123456789ABC', stfs('PIRS', 0x10000, 0xFFFE07D1, 'santiago', 'Xbox 360 Dashboard', png(64, 64, (90, 90, 90)), b''))
    x.add_file(x.path(root, ['Content', '0000000000000000', '4D5307E6']), 'vacio', b'')
    # una carpeta con muchas entradas (necesita varios clústeres)
    many = x.path(root, ['muchos'])
    for i in range(700):
        x.add_file(many, 'archivo_%04d.txt' % i, ('contenido %d' % i).encode())
    esperado.append('titulos 4D5307E6 Gears of War|41560817 Ñandú Racing')
    esperado.append('muchos 700')
    return x.finish()

def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    esperado = []
    img16 = construir(64 << 20, 32, esperado)
    open(os.path.join(out, 'particion16.img'), 'wb').write(img16)
    img32 = construir(64 << 20, 1, [])
    open(os.path.join(out, 'particion32.img'), 'wb').write(img32)
    esperado += hacer_paquetes(out)
    open(os.path.join(out, 'esperado.txt'), 'w', encoding='utf-8').write('\n'.join(esperado) + '\n')
    # disco duro de mentira: archivo disperso con la partición en 0x130EB0000
    with open(os.path.join(out, 'disco.img'), 'wb') as f:
        f.seek(0x130EB0000)
        f.write(img16)
    # memoria USB de mentira: relleno que no es XTAF y después la partición, repartida en archivos
    usb = os.path.join(out, 'usb')
    os.makedirs(usb, exist_ok=True)
    blob = b'\x00' * 0x3000 + img16
    piece = 0x700000
    for i in range(0, len(blob), piece):
        open(os.path.join(usb, 'Data%04d' % (i // piece)), 'wb').write(blob[i:i + piece])

main()
