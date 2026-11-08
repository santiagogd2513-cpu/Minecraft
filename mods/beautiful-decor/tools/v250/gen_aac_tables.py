#!/usr/bin/env python3
"""Extrae de libavcodec (la copia local de ffmpeg) las tablas de Huffman y de bandas de AAC y escribe AacTables.java.
Solo se usa al desarrollar; el resultado ya está en el proyecto."""
import struct, glob, sys
p = sorted(glob.glob('/usr/lib/x86_64-linux-gnu/libavcodec.so.60.*'))[0]
d = open(p, 'rb').read()
sfc = d.find(struct.pack('<2I', 0x3ffe8, 0x3ffe6))
sf_codes = struct.unpack_from('<121I', d, sfc)
sf_lens = None
for off in range(sfc - 400, sfc + 400):
    w = d[off:off + 121]
    if min(w) >= 1 and max(w) <= 19 and sum(1 << (20 - b) for b in w) == 1 << 20 and all(c < (1 << l) for c, l in zip(sf_codes, w)):
        sf_lens = list(w)
        break
assert sf_lens
tabs = {
    1: (12224096, 12224192, 81), 2: (12223808, 12223904, 81), 3: (12223520, 12223616, 81), 4: (12223232, 12223328, 81),
    5: (12222944, 12223040, 81), 6: (12222656, 12222752, 81), 7: (12222464, 12222528, 64), 8: (12222272, 12222336, 64),
    9: (12221728, 12221920, 169), 10: (12221184, 12221376, 169), 11: (12220256, 12220576, 289)}
swb_long = {  # indice -> offset de la tabla
    '96': 12211072, '64': 12210944, '48': 12210816, '32': 12210464, '24': 12210176, '16': 12209920, '8': 12209792}
swb_long_n = {'96': 41, '64': 47, '48': 49, '32': 51, '24': 47, '16': 43, '8': 40}
swb_short = {'96': 12211040, '48': 12210576, '24': 12210016, '16': 12209888, '8': 12209760}
swb_short_n = {'96': 12, '48': 14, '24': 15, '16': 15, '8': 15}

def hexs(vals, width):
    return ''.join(('%0' + str(width) + 'x') % v for v in vals)

out = []
out.append('package com.example.beautifuldecor.browser.audio;\n')
out.append('/** Tablas de AAC (generadas por tools/v250/gen_aac_tables.py): Huffman espectral y de factores de escala, y límites de bandas. */')
out.append('final class AacTables {')
out.append('    private AacTables() {\n    }\n')
out.append('    /** Longitudes de código de cada libro espectral 1..11 (hex, 1 byte cada una). */')
out.append('    static final String[] SPEC_LEN_HEX = {')
for k in range(1, 12):
    lo, co, n = tabs[k]
    out.append('        "%s",' % hexs(d[lo:lo + n], 2))
out.append('    };\n')
out.append('    /** Códigos de cada libro espectral 1..11 (hex, 2 bytes cada uno). */')
out.append('    static final String[] SPEC_CODE_HEX = {')
for k in range(1, 12):
    lo, co, n = tabs[k]
    out.append('        "%s",' % hexs(struct.unpack_from('<%dH' % n, d, co), 4))
out.append('    };\n')
out.append('    static final String SF_LEN_HEX = "%s";' % hexs(sf_lens, 2))
out.append('    static final String SF_CODE_HEX = "%s";\n' % hexs(sf_codes, 6))
def arr(off, n):
    return '{' + ', '.join(str(x) for x in struct.unpack_from('<%dH' % (n + 1), d, off)) + '}'
# índice de frecuencia de muestreo -> tabla
long_idx = ['96', '96', '64', '48', '48', '32', '24', '24', '16', '16', '16', '8']
short_idx = ['96', '96', '96', '48', '48', '48', '24', '24', '16', '16', '16', '8']
out.append('    /** Límites de bandas de factor de escala para ventanas largas (1024), por índice de frecuencia de muestreo. */')
out.append('    static final int[][] SWB_LONG = {')
for k in long_idx:
    out.append('        ' + arr(swb_long[k], swb_long_n[k]) + ',')
out.append('    };\n')
out.append('    /** Ídem para ventanas cortas (128). */')
out.append('    static final int[][] SWB_SHORT = {')
for k in short_idx:
    out.append('        ' + arr(swb_short[k], swb_short_n[k]) + ',')
out.append('    };\n')
out.append('    static final int[] TNS_MAX_BANDS_LONG = {31, 31, 34, 40, 42, 51, 46, 46, 42, 42, 42, 39};')
out.append('    static final int[] TNS_MAX_BANDS_SHORT = {9, 9, 10, 14, 14, 14, 14, 14, 14, 14, 14, 14};')
out.append('}')
sys.stdout.write('\n'.join(out) + '\n')
