#!/usr/bin/env python3
"""
xexpack: empaquetador casero de XEX2 para la Xbox 360 (sin el kit de Microsoft).

  xexpack.py gen-imports sdk/imports.txt > imports.S      genera los "stubs" de importacion
  xexpack.py pack plugin.elf sdk/imports.txt salida.xex   arma el XEX (sin cifrar ni comprimir)

El XEX resultante tiene: cabecera XEX2, informacion de seguridad (sin firma RSA), importaciones, y la imagen PE
(sin comprimir ni cifrar) tal como la carga la consola: imagen en memoria == datos del archivo.
"""
import argparse, hashlib, struct, sys

PAGE = 0x1000


def p32(v): return struct.pack(">I", v & 0xFFFFFFFF)
def p16(v): return struct.pack(">H", v & 0xFFFF)
def sha1(b): return hashlib.sha1(b).digest()
def align(v, a): return (v + a - 1) // a * a


def read_imports(path):
    libs, rows = [], []
    for line in open(path):
        t = line.split("#")[0].split()
        if not t:
            continue
        name, lib, ordinal = t[0], t[1], int(t[2], 0)
        if lib not in libs:
            libs.append(lib)
        rows.append((name, lib, ordinal))
    return libs, rows


def gen_imports(path):
    libs, rows = read_imports(path)
    out = ["/* generado por xexpack.py: no editar */", ".section .xslot,\"aw\"", ".balign 4"]
    for n, lib, o in rows:
        out += [".globl __imp_%s" % n, "__imp_%s: .long 0x%08X" % (n, (0 << 24) | (libs.index(lib) << 16) | o)]
    out += [".section .xthunk,\"ax\""]
    for n, lib, o in rows:
        out += [".balign 16", ".globl %s" % n, "%s:" % n, "  .long 0x%08X" % ((1 << 24) | (libs.index(lib) << 16) | o),
                "  .long 0x60000000", "  .long 0x60000000", "  .long 0x4E800020"]
    print("\n".join(out))


# ---------------------------------------------------------------- ELF32 big-endian
def read_elf(path):
    d = open(path, "rb").read()
    if d[:4] != b"\x7fELF" or d[4] != 1 or d[5] != 2:
        raise SystemExit("no es un ELF32 big-endian")
    e_entry = struct.unpack_from(">I", d, 24)[0]
    shoff = struct.unpack_from(">I", d, 32)[0]
    shentsize, shnum, shstrndx = struct.unpack_from(">HHH", d, 46)
    secs = []
    for i in range(shnum):
        name, typ, flags, addr, off, size = struct.unpack_from(">IIIIII", d, shoff + i * shentsize)
        secs.append(dict(name_off=name, type=typ, flags=flags, addr=addr, off=off, size=size))
    stroff = secs[shstrndx]["off"]
    for s in secs:
        e = d.index(b"\0", stroff + s["name_off"])
        s["name"] = d[stroff + s["name_off"]:e].decode()
    # tabla de simbolos
    syms = {}
    for s in secs:
        if s["type"] == 2:  # SYMTAB
            link = struct.unpack_from(">I", d, shoff + secs.index(s) * shentsize + 24)[0]
            strt = secs[link]["off"]
            for k in range(s["size"] // 16):
                nm, val, sz, info, other, shndx = struct.unpack_from(">IIIBBH", d, s["off"] + k * 16)
                e = d.index(b"\0", strt + nm)
                syms[d[strt + nm:e].decode()] = val
    return d, e_entry, secs, syms


def build_pe(d, entry, secs, syms, base):
    alloc = [s for s in secs if s["flags"] & 2 and s["size"] and s["name"] != ".hdr"]
    end = max(s["addr"] + s["size"] for s in alloc)
    image_size = align(end - base, PAGE)
    img = bytearray(image_size)
    pe_secs = []
    for s in alloc:
        if s["type"] != 8:  # NOBITS ya es cero
            img[s["addr"] - base:s["addr"] - base + s["size"]] = d[s["off"]:s["off"] + s["size"]]
    # secciones PE: se agrupan por nombre de ELF
    for s in alloc:
        ex = bool(s["flags"] & 4); wr = bool(s["flags"] & 1)
        ch = 0x00000020 if ex else 0x00000040
        ch |= (0x20000000 if ex else 0) | 0x40000000 | (0x80000000 if wr else 0)
        pe_secs.append((s["name"][:8], s["addr"] - base, s["size"], ch, ex, wr))
    code_size = sum(align(s[2], PAGE) for s in pe_secs if s[4])
    init_size = sum(align(s[2], PAGE) for s in pe_secs if not s[4])
    return img, pe_secs, code_size, init_size


def pe_headers_be(base, entry_va, nsec_list, code_size, init_size, image_size):
    """Cabeceras PE para PowerPC big-endian (el PE de Xbox 360 usa big-endian en todos sus campos)."""
    h = bytearray(PAGE)
    h[0:2] = b"MZ"
    struct.pack_into(">I", h, 0x3C, 0x80)           # e_lfanew
    o = 0x80
    h[o:o + 4] = b"PE\0\0"; o += 4
    nsec = len(nsec_list)
    # IMAGE_FILE_HEADER
    struct.pack_into(">HHIIIHH", h, o, 0x01F2, nsec, 0, 0, 0, 0xE0, 0x0102)  # PowerPC BE, 32 bit machine
    o += 20
    # IMAGE_OPTIONAL_HEADER32
    opt = o
    struct.pack_into(">HBBIIIIIII", h, o, 0x10B, 8, 0, code_size, init_size, 0, entry_va - base, 0x1000, 0x1000 + 0, base)
    # (offsets 0x00..0x1F ya escritos: magic, linker, SizeOfCode, SizeOfInitData, SizeOfUninit, EntryRVA, BaseOfCode, BaseOfData, ImageBase)
    o = opt + 32
    struct.pack_into(">II", h, o, PAGE, PAGE); o += 8                 # SectionAlignment, FileAlignment
    struct.pack_into(">HHHHHH", h, o, 1, 0, 0, 0, 5, 1); o += 12      # versiones OS / imagen / subsystem
    struct.pack_into(">IIII", h, o, 0, image_size, PAGE, 0); o += 16  # Win32Version, SizeOfImage, SizeOfHeaders, CheckSum
    struct.pack_into(">HH", h, o, 14, 0); o += 4                      # Subsystem XBOX(14), DllCharacteristics
    struct.pack_into(">IIII", h, o, 0x40000, 0x1000, 0x100000, 0x1000); o += 16  # stack/heap
    struct.pack_into(">II", h, o, 0, 16); o += 8                      # LoaderFlags, NumberOfRvaAndSizes
    o += 16 * 8                                                       # data directories vacios
    assert o - opt == 0xE0, hex(o - opt)
    for (name, rva, size, ch, ex, wr) in nsec_list:
        h[o:o + 8] = name.encode().ljust(8, b"\0")
        struct.pack_into(">IIIIIIHHI", h, o + 8, size, rva, align(size, PAGE), rva, 0, 0, 0, 0, ch)
        o += 40
    return h


# ---------------------------------------------------------------- XEX2
def opt_data(key, payload):
    return (key, payload)


def build_xex(img, base, entry_va, libs, rows, syms, title_id, flags, stack, name, page4k=True):
    # ---- tabla de importaciones
    names = b"".join(n.encode() + b"\0" for n in libs)
    names = names + b"\0" * (align(len(names), 4) - len(names))
    libblocks = []
    for li, lib in enumerate(libs):
        recs = []
        for (n, l, o) in rows:
            if l != lib:
                continue
            recs += [syms["__imp_" + n], syms[n]]
        blk = bytearray()
        blk += p32(0x28 + 4 * len(recs))
        blk += b"\0" * 20                                   # digest del siguiente (se rellena luego)
        blk += p32(0x00000100 + li)                         # id de la libreria
        blk += p32(0x20000000)                              # version
        blk += p32(0x20000000)                              # version minima
        blk += p16(li) + p16(len(recs))
        for r in recs: blk += p32(r)
        libblocks.append(bytearray(blk))
    for i in range(len(libblocks) - 1):                    # cadena de digests (ver nota en README)
        libblocks[i][4:24] = sha1(bytes(libblocks[i + 1]))
    imp = p32(0) + p32(len(names)) + p32(len(libs)) + names + b"".join(bytes(b) for b in libblocks)
    imp = p32(len(imp)) + imp[4:]

    # ---- descriptores de pagina (una por seccion: todo el codigo / el resto)
    npages = len(img) // PAGE
    descs = b""
    # un unico descriptor "datos" cubriendo todas las paginas (tipo 2), con el digest de esa imagen
    descs += p32((npages << 4) | 2) + sha1(bytes(img))
    # ---- cabeceras opcionales
    # media id, version, version base, title id, plataforma, tipo de ejecutable, disco, discos  (0x18 bytes)
    exec_id = p32(0) + p32(0) + p32(0) + p32(title_id) + bytes([0, 0, 0, 0]) + p32(0)
    exec_id = exec_id[:0x18]
    pename = name.encode() + b"\0"
    pename = p32(4 + len(pename)) + pename
    pename += b"\0" * (align(len(pename), 4) - len(pename))
    fmt = p32(8) + p16(0) + p16(0)                          # sin cifrar, sin comprimir

    # lista de (key, kind, payload)  kind: 'v' valor directo, 'd' datos fuera
    heads = [
        (0x000003FF, "d", fmt),
        (0x00010001, "v", p32(base)),
        (0x00010100, "v", p32(entry_va)),
        (0x00010201, "v", p32(base)),
        (0x000103FF, "d", imp),
        (0x000183FF, "d", pename),
        (0x00020200, "v", p32(stack)),
        (0x00040006, "d", exec_id),
    ]
    # los de tamaño fijo en dwords: la clave lleva el tamaño en dwords en el byte bajo
    def key_for(k, kind, pl):
        return k   # las claves ya traen el tamaño en su byte bajo (00/01 = valor directo, FF = con tamaño, otro = dwords)
    count = len(heads)
    header_fixed = 24 + 8 * count
    # security info
    sec_size = 0x184 + len(descs)
    sec_off = header_fixed
    data_off = align(sec_off + sec_size, 4)
    # colocar los datos
    blobs = []
    cur = data_off
    tbl = b""
    for k, kind, pl in heads:
        kk = key_for(k, kind, pl)
        if kind == "v":
            tbl += p32(kk) + pl
        else:
            tbl += p32(kk) + p32(cur)
            blobs.append((cur, pl))
            cur += align(len(pl), 4)
    hdr_end = align(cur, PAGE if page4k else 0x1000)
    pe_off = hdr_end
    sec = bytearray(sec_size)
    sec[0:4] = p32(pe_off)                                 # header_size
    sec[4:8] = p32(len(img))                               # image_size
    # 0x08..0x108 firma RSA: ceros (la consola con RGH no la comprueba)
    sec[0x108:0x10C] = p32(0x100)
    sec[0x10C:0x110] = p32(0x10000000 if page4k else 0)   # flags de imagen: paginas de 4 KB
    sec[0x110:0x114] = p32(base)
    sec[0x114:0x128] = sha1(descs)                         # section_digest
    sec[0x128:0x12C] = p32(len(libs))
    sec[0x12C:0x140] = sha1(imp)                           # import_table_digest
    sec[0x160:0x164] = p32(0)                              # sin exportaciones
    sec[0x178:0x17C] = p32(0xFFFFFFFF)                     # region: todas
    sec[0x17C:0x180] = p32(0xFFFFFFFF)                     # medios permitidos: todos
    sec[0x180:0x184] = p32(1)
    sec[0x184:] = descs
    head = bytearray()
    head += b"XEX2" + p32(flags) + p32(pe_off) + p32(0) + p32(sec_off) + p32(count)
    head += tbl
    assert len(head) == header_fixed
    head += sec
    head += b"\0" * (data_off - len(head))
    for off, pl in blobs:
        head += b"\0" * (off - len(head))
        head += pl
    head += b"\0" * (pe_off - len(head))
    # digest de cabecera (best effort): sha1 de la cabecera sin la firma ni el propio digest
    hd = sha1(bytes(head[:sec_off]) + bytes(head[sec_off + 0x108:sec_off + 0x164]))
    head[sec_off + 0x164:sec_off + 0x178] = hd
    return bytes(head) + bytes(img)


def pack(elf, imports, out, base, flags, title_id, name, stack):
    d, entry, secs, syms = read_elf(elf)
    libs, rows = read_imports(imports)
    img, pe_secs, code_size, init_size = build_pe(d, entry, secs, syms, base)
    h = pe_headers_be(base, entry, pe_secs, code_size, init_size, len(img))
    img[0:PAGE] = h
    xex = build_xex(bytes(img), base, entry, libs, rows, syms, title_id, flags, stack, name)
    open(out, "wb").write(xex)
    print("%s: %d bytes (cabecera 0x%X + imagen 0x%X), entrada 0x%08X, base 0x%08X, %d importaciones"
          % (out, len(xex), len(xex) - len(img), len(img), entry, base, len(rows)))


def main():
    ap = argparse.ArgumentParser()
    sp = ap.add_subparsers(dest="cmd", required=True)
    g = sp.add_parser("gen-imports"); g.add_argument("imports")
    p = sp.add_parser("pack")
    p.add_argument("elf"); p.add_argument("imports"); p.add_argument("out")
    p.add_argument("--base", type=lambda x: int(x, 0), default=0x92000000)
    p.add_argument("--flags", type=lambda x: int(x, 0), default=0x8, help="flags del modulo (0x8 = DLL, 0x1 = titulo)")
    p.add_argument("--title-id", type=lambda x: int(x, 0), default=0)
    p.add_argument("--name", default="plugin.dll")
    p.add_argument("--stack", type=lambda x: int(x, 0), default=0x20000)
    a = ap.parse_args()
    if a.cmd == "gen-imports":
        gen_imports(a.imports)
    else:
        pack(a.elf, a.imports, a.out, a.base, a.flags, a.title_id, a.name, a.stack)


if __name__ == "__main__":
    main()
