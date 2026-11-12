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
        self.entries = size // self.cb
        self.fat32 = self.entries >= 0xFFF4
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
                struct.pack_into('>HHHHHH', e, 52, date, tm, date, tm, date, tm)
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
