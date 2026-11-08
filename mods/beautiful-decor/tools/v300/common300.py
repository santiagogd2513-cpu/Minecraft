"""Piezas comunes de la V3.0.0 (camión de reparto y helicóptero): fuente de letras de varios anchos y texto sobre la textura."""
import numpy as np

# Letras de 5 px de alto y ancho variable (3 o 4). Cada fila es una cadena de 0/1 separada por espacios.
GLYPHS = {
    "A": "010 101 111 101 101", "B": "110 101 110 101 110", "C": "011 100 100 100 011", "D": "110 101 101 101 110",
    "E": "111 100 110 100 111", "F": "111 100 110 100 100", "G": "011 100 101 101 011", "H": "101 101 111 101 101",
    "I": "111 010 010 010 111", "J": "001 001 001 101 010", "K": "101 101 110 101 101", "L": "100 100 100 100 111",
    "M": "1001 1111 1111 1001 1001", "N": "1001 1101 1111 1011 1001", "O": "111 101 101 101 111",
    "P": "110 101 110 100 100", "Q": "111 101 101 111 001", "R": "110 101 110 101 101", "S": "011 100 010 001 110",
    "T": "111 010 010 010 010", "U": "101 101 101 101 111", "V": "101 101 101 101 010", "W": "10101 10101 10101 11111 01010",
    "X": "101 101 010 101 101", "Y": "101 101 010 010 010", "Z": "111 001 010 100 111",
    "0": "111 101 101 101 111", "1": "010 110 010 010 111", "2": "110 001 010 100 111", "3": "110 001 010 001 110",
    "4": "101 101 111 001 001", "5": "111 100 110 001 110", "6": "011 100 111 101 111", "7": "111 001 010 010 010",
    "8": "111 101 111 101 111", "9": "111 101 111 001 110",
    "-": "000 000 111 000 000", ".": "0 0 0 0 1", " ": "00 00 00 00 00",
}


def _glyph(ch):
    rows = GLYPHS.get(ch.upper(), GLYPHS[" "]).split()
    return rows


def text_width(s, scale=1, gap=1):
    total = 0
    for ch in s:
        total += len(_glyph(ch)[0]) * scale + gap * scale
    return total - gap * scale if s else 0


def draw_text(a, s, x, y, color, scale=1, gap=1, shadow=None):
    """Escribe s sobre un arreglo RGBA (x, y = esquina superior izquierda). shadow = color de una sombra de 1 px abajo-derecha."""
    def put(cx0, cy0, col):
        cx = cx0
        for ch in s:
            rows = _glyph(ch)
            for ry, row in enumerate(rows):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        for sy in range(scale):
                            for sx in range(scale):
                                xx, yy = cx + rx * scale + sx, cy0 + ry * scale + sy
                                if 0 <= yy < a.shape[0] and 0 <= xx < a.shape[1]:
                                    a[yy, xx, :3] = col
                                    a[yy, xx, 3] = 255
            cx += len(rows[0]) * scale + gap * scale
    if shadow is not None:
        put(x + 1, y + 1, shadow)
    put(x, y, color)
