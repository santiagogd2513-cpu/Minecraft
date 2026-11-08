#!/usr/bin/env python3
"""Copia el decodificador MP3 JLayer (javazoom.jl.decoder, LGPL) a un paquete propio del mod.

Por qué: la mod MusicBoxRadio también trae javazoom en la raíz de su jar; si las dos mods están instaladas a la vez, Forge se queja de
un paquete repetido. Aquí se reescriben los nombres dentro de cada .class (tabla de constantes) de javazoom/jl/ a
com/example/beautifuldecor/lib/jl/ sin tocar el código. Los .ser (tablas) se copian junto a las clases.

Uso (desde la raíz del proyecto):
  python3 -m tools.v240.relocate_jlayer RUTA/javazoom.zip      # lee el zip y escribe src/main/resources/com/example/beautifuldecor/lib/jl/decoder/
"""
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/com/example/beautifuldecor/lib/jl/decoder"
OLD_SLASH, NEW_SLASH = b"javazoom/jl/", b"com/example/beautifuldecor/lib/jl/"
OLD_DOT, NEW_DOT = b"javazoom.jl.", b"com.example.beautifuldecor.lib.jl."


def relocate(data):
    assert data[:4] == b"\xca\xfe\xba\xbe", "no es un .class"
    out = bytearray(data[:8])
    count = struct.unpack(">H", data[8:10])[0]
    out += data[8:10]
    pos = 10
    i = 1
    while i < count:
        tag = data[pos]
        if tag == 1:                                   # Utf8
            n = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            raw = data[pos + 3:pos + 3 + n]
            raw = raw.replace(OLD_SLASH, NEW_SLASH).replace(OLD_DOT, NEW_DOT)
            out += b"\x01" + struct.pack(">H", len(raw)) + raw
            pos += 3 + n
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3}[tag]
            out += data[pos:pos + size]
            pos += size
            if tag in (5, 6):
                i += 1
        i += 1
    out += data[pos:]
    return bytes(out)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    OUT.mkdir(parents=True, exist_ok=True)
    n = 0
    with zipfile.ZipFile(sys.argv[1]) as z:
        for name in z.namelist():
            if not name.startswith("javazoom/jl/decoder/") or name.endswith("/"):
                continue
            data = z.read(name)
            base = name.rsplit("/", 1)[1]
            if name.endswith(".class"):
                data = relocate(data)
            elif not name.endswith(".ser"):
                continue
            (OUT / base).write_bytes(data)
            n += 1
    print(f"{n} archivos en {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
