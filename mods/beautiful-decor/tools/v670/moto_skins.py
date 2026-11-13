"""V6.7.0: texturas de las skins de la moto (policía y repartidor): mismo atlas que la moto original, con otra pintura y rotulado.
Uso (desde la raíz): python3 -m tools.v670.moto_skins --write"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image

from ..v200 import moto_model, moto_paint
from ..v200.moto_paint import face_rect, pack, paint_atlas

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/beautifuldecor/textures/entity"

# letras 3x5 (un 1 es un píxel pintado)
FONT = {
    "P": ("111", "101", "111", "100", "100"), "N": ("101", "111", "111", "111", "101"), "A": ("010", "101", "111", "101", "101"),
    "Z": ("111", "001", "010", "100", "111"), "D": ("110", "101", "101", "101", "110"), "C": ("111", "100", "100", "100", "111"),
    "H": ("101", "101", "111", "101", "101"),
}


def put_text(img, c, face, text, color, x_off=None, y_off=None):
    x0, y0, cols, rows = face_rect(c, face)
    width = len(text) * 4 - 1
    if width > cols or rows < 5:
        return
    sx = x0 + (cols - width) // 2 if x_off is None else x0 + x_off
    sy = y0 + (rows - 5) // 2 if y_off is None else y0 + y_off
    for i, ch in enumerate(text):
        for r, line in enumerate(FONT[ch]):
            for k, bit in enumerate(line):
                if bit == "1":
                    img[sy + r, sx + i * 4 + k] = (*color, 255)


def fill(img, c, face, color):
    x0, y0, cols, rows = face_rect(c, face)
    img[y0:y0 + rows, x0:x0 + cols] = (*color, 255)


def stripe(img, c, face, row, color, thick=1):
    x0, y0, cols, rows = face_rect(c, face)
    if row < rows:
        img[y0 + row:y0 + min(rows, row + thick), x0:x0 + cols] = (*color, 255)


def base_with_palette(body, rim):
    moto_paint.set_paint(body=tuple(body[i] - moto_paint.RED_REF[i] for i in range(3)), rim=tuple(rim[i] - moto_paint.RIM_REF[i] for i in range(3)))
    b = moto_model.build()
    pack(b.cubes)
    atlas, _ = paint_atlas(b.cubes)
    moto_paint.set_paint()
    return b, atlas.copy()


def police():
    b, img = base_with_palette((22, 40, 110), (235, 235, 245))
    cubes = {c.name: c for c in b.cubes}
    WHITE, GOLD = (240, 240, 248), (240, 196, 40)
    for nm in ("cheek_l", "cheek_r"):
        c = cubes[nm]
        for face in ("west", "east"):
            fill(img, c, face, (22, 40, 110))
            stripe(img, c, face, 0, WHITE)
            stripe(img, c, face, 4, GOLD)
            put_text(img, c, face, "PN", WHITE, y_off=0 if False else None)
    for nm in ("tank",):
        for face in ("west", "east"):
            stripe(img, cubes[nm], face, 3, WHITE)
    for nm in ("tail_cowl",):
        for face in ("west", "east"):
            stripe(img, cubes[nm], face, 1, GOLD)
    for nm in ("bar_base", "btn_siren"):
        pass
    return img


def delivery():
    b, img = base_with_palette((255, 140, 30), (26, 40, 100))
    cubes = {c.name: c for c in b.cubes}
    NAVY, ORANGE, WHITE = (26, 40, 100), (255, 140, 30), (236, 238, 244)
    main, lid = cubes["dbox_main"], cubes["dbox_lid"]
    for face in ("west", "east", "back", "front", "top", "bottom"):
        fill(img, main, face, NAVY)
    for face in ("west", "east", "back", "front", "top", "bottom"):
        fill(img, lid, face, ORANGE)
    for face in ("west", "east"):
        put_text(img, main, face, "AZ", ORANGE)
        stripe(img, main, face, 6, ORANGE)
    put_text(img, main, "back", "AZ", ORANGE)
    stripe(img, main, "back", 6, ORANGE)
    for nm in ("cheek_l", "cheek_r"):
        for face in ("west", "east"):
            put_text(img, cubes[nm], face, "AZ", NAVY)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()
    for name, fn in (("motorcycle_police", police), ("motorcycle_delivery", delivery)):
        img = Image.fromarray(fn(), "RGBA")
        if args.write:
            img.save(OUT / f"{name}.png")
            print("escrito", name)


if __name__ == "__main__":
    main()
