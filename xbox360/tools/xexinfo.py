#!/usr/bin/env python3
"""Lector de XEX2: muestra cabeceras, información de seguridad e importaciones. Sirve para validar lo que arma xexpack."""
import struct, sys

OPT_NAMES = {
    0x2FF: "resource info", 0x3FF: "file format info", 0x405: "base reference", 0x5FF: "delta patch",
    0x80FF: "bounding path", 0x8105: "device id", 0x10001: "original base address", 0x10100: "entry point",
    0x10201: "image base address", 0x103FF: "import libraries", 0x18002: "checksum/timestamp",
    0x183FF: "original PE name", 0x200FF: "static libraries", 0x20104: "TLS info", 0x20200: "default stack size",
    0x20301: "fs cache size", 0x20401: "default heap size", 0x30000: "system flags", 0x40006: "execution id",
    0x401FF: "service id list", 0x40201: "workspace size", 0x40310: "game ratings", 0x40404: "lan key",
    0x405FF: "logo", 0x406FF: "multidisc", 0x407FF: "alt title ids", 0x40801: "additional title memory",
    0xE10402: "exports by name",
}


def u32(d, o): return struct.unpack_from(">I", d, o)[0]
def u16(d, o): return struct.unpack_from(">H", d, o)[0]


def parse(d):
    if d[:4] != b"XEX2":
        raise ValueError("no es XEX2 (magic %r)" % d[:4])
    x = {"module_flags": u32(d, 4), "pe_offset": u32(d, 8), "security_offset": u32(d, 16)}
    n = u32(d, 20)
    opts = {}
    for i in range(n):
        key, val = u32(d, 24 + i * 8), u32(d, 28 + i * 8)
        size = key & 0xFF
        if size == 0x01:
            data = struct.pack(">I", val)
        elif size == 0xFF:
            ln = u32(d, val)
            data = d[val:val + ln]
        else:
            data = d[val:val + size * 4]
        opts[key] = (val, data)
    x["opts"] = opts
    s = x["security_offset"]
    x["sec"] = {
        "header_size": u32(d, s), "image_size": u32(d, s + 4), "image_flags": u32(d, s + 0x10C),
        "load_address": u32(d, s + 0x110), "import_table_count": u32(d, s + 0x128),
        "region": u32(d, s + 0x178), "allowed_media": u32(d, s + 0x17C), "page_desc_count": u32(d, s + 0x180),
    }
    if 0x3FF in opts:
        b = opts[0x3FF][1]
        x["enc"], x["comp"] = u16(b, 4), u16(b, 6)
    if 0x103FF in opts:
        x["imports"] = parse_imports(opts[0x103FF][1])
    return x


def parse_imports(b):
    size, strsize, count = u32(b, 0), u32(b, 4), u32(b, 8)
    names, p = [], 12
    end = 12 + strsize
    while p < end:
        e = b.index(b"\0", p)
        if e == p:
            p += 1
            continue
        names.append(b[p:e].decode()); p = e + 1
        while p < end and b[p] == 0:
            p += 1
    libs = []
    p = end
    for _ in range(count):
        lsize = u32(b, p)
        lib = {"digest": b[p + 4:p + 24].hex(), "import_id": u32(b, p + 24), "version": u32(b, p + 28),
               "min_version": u32(b, p + 32), "name_index": u16(b, p + 36), "count": u16(b, p + 38)}
        lib["records"] = [u32(b, p + 40 + i * 4) for i in range(lib["count"])]
        lib["name"] = names[lib["name_index"] & 0xFF] if (lib["name_index"] & 0xFF) < len(names) else "?"
        libs.append(lib)
        p += lsize
    return {"names": names, "libs": libs}


def main(path):
    d = open(path, "rb").read()
    x = parse(d)
    print("flags=0x%X pe_offset=0x%X security_offset=0x%X" % (x["module_flags"], x["pe_offset"], x["security_offset"]))
    print("sec:", {k: hex(v) for k, v in x["sec"].items()})
    if "enc" in x: print("cifrado=%d compresion=%d" % (x["enc"], x["comp"]))
    for k, (v, data) in sorted(x["opts"].items()):
        print("  opt 0x%X %-22s val=0x%X len=%d" % (k, OPT_NAMES.get(k, "?"), v, len(data)))
    for lib in x.get("imports", {}).get("libs", []):
        print("  lib %s v=0x%X min=0x%X imports=%d first=%s" % (lib["name"], lib["version"], lib["min_version"], lib["count"],
              [hex(r) for r in lib["records"][:4]]))


if __name__ == "__main__":
    main(sys.argv[1])
