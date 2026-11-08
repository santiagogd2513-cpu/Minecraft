#!/usr/bin/env python3
"""Genera src/main/java/com/example/beautifuldecor/browser/video/H264Tables.java con las tablas numéricas del estándar H.264.

Las tablas grandes (contextos CABAC, CAVLC) se leyeron de libavcodec (ffmpeg 6.1) por búsqueda de patrones y se comprobaron
contra la norma; el resultado queda como texto hexadecimal dentro del .java. No hace falta volver a correrlo.
"""
import sys
from pathlib import Path

LIB = "/usr/lib/x86_64-linux-gnu/libavcodec.so.60"
OUT = Path(__file__).resolve().parent.parent.parent / "src/main/java/com/example/beautifuldecor/browser/video/H264Tables.java"
d = open(LIB, "rb").read()


def chunk(off, n):
    return list(d[off:off + n])


def hexs(vals):
    return "".join("%02x" % (v & 255) for v in vals)


def lit(name, vals, doc):
    h = hexs(vals)
    lines = [h[i:i + 96] for i in range(0, len(h), 96)]
    body = '\n            + '.join('"%s"' % l for l in lines)
    return f"    /** {doc} */\n    static final String {name} =\n            {body};\n\n"


cabac = []
for off in (12617248, 12611104, 12613152, 12615200):      # I, P/B idc0, idc1, idc2
    cabac += chunk(off, 2048)
assert cabac[0:6] == [20, 241, 2, 54, 3, 74]
src = []
src.append("package com.example.beautifuldecor.browser.video;\n\n")
src.append("/** Tablas numéricas de H.264 (generado por tools/v250/gen_h264_tables.py; no editar a mano). */\n")
src.append("final class H264Tables {\n    private H264Tables() {\n    }\n\n")
src.append(lit("CABAC_INIT_HEX", cabac, "Contextos CABAC: 4 juegos (I, idc0, idc1, idc2) x 1024 x (m, n) en int8."))
src.append(lit("COEFF_TOKEN_LEN_HEX", chunk(12620864, 272), "coeff_token: longitudes [4][68] (índice = 4*totalCoeff + trailingOnes)."))
src.append(lit("COEFF_TOKEN_BITS_HEX", chunk(12620576, 272), "coeff_token: códigos [4][68]."))
src.append(lit("CHROMA_DC_TOKEN_LEN_HEX", chunk(12621296, 20), "coeff_token del DC de croma: longitudes [20]."))
src.append(lit("CHROMA_DC_TOKEN_BITS_HEX", chunk(12621264, 20), "coeff_token del DC de croma: códigos [20]."))
src.append(lit("TOTAL_ZEROS_LEN_HEX", chunk(12620320, 240), "total_zeros: longitudes [15][16]."))
src.append(lit("TOTAL_ZEROS_BITS_HEX", chunk(12620064, 240), "total_zeros: códigos [15][16]."))
src.append(lit("CHROMA_TZ_LEN_HEX", chunk(12620040, 12), "total_zeros del DC de croma: longitudes [3][4]."))
src.append(lit("CHROMA_TZ_BITS_HEX", chunk(12620024, 12), "total_zeros del DC de croma: códigos [3][4]."))
src.append(lit("RUN_LEN_HEX", chunk(12619776, 112), "run_before: longitudes [7][16]."))
src.append(lit("RUN_BITS_HEX", chunk(12619648, 112), "run_before: códigos [7][16]."))
src.append(lit("SIG8_HEX", chunk(12610496, 63), "Contexto de significance_coeff_flag para bloques 8x8 (cuadro)."))
src.append(lit("LAST8_HEX", chunk(12351200, 63), "Contexto de last_significant_coeff_flag para bloques 8x8."))
src.append("}\n")
OUT.write_text("".join(src), encoding="utf-8")
print("escrito", OUT, OUT.stat().st_size, "bytes")
