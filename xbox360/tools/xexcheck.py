#!/usr/bin/env python3
"""Comprueba un XEX armado con las mismas reglas que usa un cargador (Xenia): cabeceras, PE, secciones e importaciones."""
import struct, sys
sys.path.insert(0, __import__("os").path.dirname(__file__))
from xexinfo import parse, u32, u16

PAGE = 0x1000


def check(path):
    d = open(path, "rb").read()
    x = parse(d)
    errs = []
    def need(c, m):
        if not c: errs.append(m)
    need(x.get("enc") == 0 and x.get("comp") == 0, "esperaba sin cifrar y sin comprimir")
    base = struct.unpack(">I", x["opts"][0x10201][1])[0] if len(x["opts"][0x10201][1]) else x["opts"][0x10201][0]
    entry = x["opts"][0x10100][0]
    img = d[x["pe_offset"]:]
    need(len(img) == x["sec"]["image_size"], "tamaño de imagen distinto al de la cabecera de seguridad")
    need(img[:2] == b"MZ", "falta MZ")
    pe = struct.unpack_from(">I", img, 0x3C)[0]
    need(img[pe:pe + 4] == b"PE\0\0", "falta PE")
    mach, nsec, _, _, _, optsz, chars = struct.unpack_from(">HHIIIHH", img, pe + 4)
    need(mach == 0x1F2, "Machine != POWERPCBE (0x1F2)")
    need(chars & 0x100, "falta 32BIT_MACHINE")
    need(optsz == 0xE0, "SizeOfOptionalHeader != 0xE0")
    o = pe + 24
    need(struct.unpack_from(">H", img, o)[0] == 0x10B, "magic opcional != 0x10B")
    ibase = struct.unpack_from(">I", img, o + 28)[0]
    need(ibase == base, "ImageBase del PE distinto al de la cabecera XEX")
    need(struct.unpack_from(">H", img, o + 68)[0] == 14, "subsistema != XBOX")
    eprva = struct.unpack_from(">I", img, o + 16)[0]
    need(base + eprva == entry, "punto de entrada del PE != el de la cabecera XEX")
    secs = []
    so = o + 0xE0
    for i in range(nsec):
        name = img[so + i * 40:so + i * 40 + 8].rstrip(b"\0").decode()
        vsize, va, rsize, raw = struct.unpack_from(">IIII", img, so + i * 40 + 8)
        flags = struct.unpack_from(">I", img, so + i * 40 + 36)[0]
        secs.append((name, va, vsize, flags))
        need(raw == va, "seccion %s: PointerToRawData != VirtualAddress (la consola copia la imagen tal cual)" % name)
        need(va + rsize <= len(img), "seccion %s se sale de la imagen" % name)
    def where(addr):
        for n, va, vs, fl in secs:
            if base + va <= addr < base + va + vs: return n, fl
        return None, 0
    need(where(entry)[0] is not None and where(entry)[1] & 0x20000000, "la entrada no cae en una seccion ejecutable")
    for lib in x["imports"]["libs"]:
        recs = lib["records"]
        need(len(recs) % 2 == 0, "importaciones de %s: numero impar de registros" % lib["name"])
        for i in range(0, len(recs) - 1, 2):
            slot, thunk = recs[i], recs[i + 1]
            vs = struct.unpack_from(">I", img, slot - base)[0]
            vt = struct.unpack_from(">I", img, thunk - base)[0]
            need(vs >> 24 == 0 and vt >> 24 == 1 and (vs & 0xFFFF) == (vt & 0xFFFF), "registros distintos en %s #%d: %08X %08X" % (lib["name"], i // 2, vs, vt))
            need(where(slot)[0] and where(slot)[1] & 0x80000000, "el hueco de %s no es escribible" % lib["name"])
            need(where(thunk)[0] and where(thunk)[1] & 0x20000000 and thunk % 16 == 0, "el stub de %s no es ejecutable/alineado" % lib["name"])
    print("secciones:", [(n, hex(va), hex(vs)) for n, va, vs, fl in secs])
    print("importaciones:", {l["name"]: l["count"] // 2 for l in x["imports"]["libs"]})
    if errs:
        print("FALLOS:"); [print(" -", e) for e in errs]; return 1
    print("OK: el XEX cumple las reglas del cargador")
    return 0

if __name__ == "__main__":
    sys.exit(check(sys.argv[1]))
