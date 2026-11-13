#!/usr/bin/env python3
"""V7.0.0: vías de ancho real. Trocha 1,435 m (rieles a +-11,48 px del centro del bloque), traviesas de 2,6 m y balasto de 3 bloques.

Los modelos sobresalen del bloque (se permite de -16 a 32 px). Las cajas más anchas que un bloque se parten en trozos de una
celda, cada uno con su UV, para que la textura no se salga del mosaico. La curva de 1 bloque (esquina) no cambia.
"""
import importlib.util
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("rails_real", HERE.parent / "v6170" / "rails_real.py")
RR = importlib.util.module_from_spec(spec)
spec.loader.exec_module(RR)
R = RR.R

HALF = 11.48           # mitad de la trocha en px (1,435 m / 2 = 0,7175 bloques)
LEFT, RIGHT = 8 - HALF, 8 + HALF


def rail_parts(c):
    return [(c - 1.25, c + 1.25, 2.5, 3.0), (c - 0.4, c + 0.4, 3.0, 3.5), (c - 0.85, c + 0.85, 3.5, 4.0)]


def pieces(a, b):
    """Parte [a, b] en trozos que caben cada uno en una celda de 16."""
    out = []
    k = math.floor(a / 16.0)
    while a < b - 1e-6:
        end = min(b, (k + 1) * 16.0)
        out.append((a, end, k))
        a = end
        k += 1
    return out


def emit(frm, to, tex, out):
    dy = to[1] - frm[1]
    for xa, xb, kx in pieces(frm[0], to[0]):
        for za, zb, kz in pieces(frm[2], to[2]):
            lx0, lx1, lz0, lz1 = xa - 16 * kx, xb - 16 * kx, za - 16 * kz, zb - 16 * kz
            h = min(16.0, dy)
            faces = {
                "up": {"texture": "#" + tex, "uv": [round(lx0, 3), round(lz0, 3), round(lx1, 3), round(lz1, 3)]},
                "down": {"texture": "#" + tex, "uv": [round(lx0, 3), round(lz0, 3), round(lx1, 3), round(lz1, 3)]},
                "north": {"texture": "#" + tex, "uv": [round(lx0, 3), 0, round(lx1, 3), round(h, 3)]},
                "south": {"texture": "#" + tex, "uv": [round(lx0, 3), 0, round(lx1, 3), round(h, 3)]},
                "east": {"texture": "#" + tex, "uv": [round(lz0, 3), 0, round(lz1, 3), round(h, 3)]},
                "west": {"texture": "#" + tex, "uv": [round(lz0, 3), 0, round(lz1, 3), round(h, 3)]},
            }
            out.append({"from": [round(xa, 3), round(frm[1], 3), round(za, 3)], "to": [round(xb, 3), round(to[1], 3), round(zb, 3)], "faces": faces})


def flat_model():
    el = []
    emit((-16, 0, 0), (32, 1, 16), "ballast", el)
    emit((-13, 1, 0), (29, 1.5, 16), "ballast", el)
    for z in (1, 5, 9, 13):
        emit((-12.8, 1.5, z), (28.8, 2.5, z + 2), "tie", el)
    for c in (LEFT, RIGHT):
        for x0, x1, y0, y1 in rail_parts(c):
            emit((x0, y0, 0), (x1, y1, 16), "steel", el)
    return {"textures": R.textures(), "elements": el}


def up_model(direction):
    def box(u0, u1, y0, y1, w0, w1, tex, el):
        if direction == "east":
            frm, to = (u0, y0, w0), (u1, y1, w1)
        elif direction == "west":
            frm, to = (16 - u1, y0, w0), (16 - u0, y1, w1)
        elif direction == "south":
            frm, to = (w0, y0, u0), (w1, y1, u1)
        else:
            frm, to = (w0, y0, 16 - u1), (w1, y1, 16 - u0)
        emit(frm, to, tex, el)
    el = []
    for i in range(16):
        h = i + 0.5
        box(i, i + 1, 0, h + 1.0, -16, 32, "ballast", el)
        box(i, i + 1, h + 1.0, h + 1.5, -13, 29, "ballast", el)
        if i % 4 == 1:
            box(i, min(i + 2, 16), h + 1.5, h + 2.5, -12.8, 28.8, "tie", el)
        for c in (LEFT, RIGHT):
            for x0, x1, y0, y1 in rail_parts(c):
                box(i, i + 1, h + y0, h + y1, x0, x1, "steel", el)
    return {"textures": R.textures(), "elements": el}


R.flat_model, R.up_model = flat_model, up_model

if __name__ == "__main__":
    R.family("freight_rail", (118, 112, 106), (88, 64, 46), (166, 170, 180))
    R.family("passenger_rail", (156, 152, 146), (150, 150, 152), (110, 114, 124))
    R.family("metro_rail", (190, 192, 198), (72, 76, 86), (214, 220, 232))
    print("V7.0.0: vías anchas escritas")
