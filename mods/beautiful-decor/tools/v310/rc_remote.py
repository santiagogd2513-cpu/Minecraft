#!/usr/bin/env python3
"""Ícono del control remoto del carro RC (V3.1.0): un control de pistola, verde lima y negro, con volante y antena.

Uso (desde la raíz del proyecto):  python3 -m tools.v310.rc_remote
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/beautifuldecor/textures/item/rc_car_remote.png"

# Leyenda: . vacío, K contorno, D gris oscuro, G gris, L lima, H lima claro, R LED rojo, W blanco
ART = [
    "..............W.",
    ".............KW.",
    "............KG..",
    "..KKKK.....KG...",
    ".KLHHLK...KG....",
    ".KHLLHKKKKKK....",
    ".KLLLLDDDDDDK...",
    "..KKKKDGDRDDK...",
    "...KDDDDDDDDK...",
    "...KDDLLLLDDK...",
    "....KDDDDDDK....",
    "....KDDLLDDK....",
    "....KDDDDDDK....",
    "....KDDLLDDK....",
    "....KKDDDDKK....",
    ".....KKKKKK.....",
]
COLORS = {
    "K": (14, 14, 18), "D": (46, 48, 56), "G": (132, 138, 150), "L": (132, 204, 24), "H": (182, 240, 64),
    "R": (230, 40, 40), "W": (230, 232, 240),
}


def main():
    a = np.zeros((16, 16, 4), dtype=np.uint8)
    for y, row in enumerate(ART):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                a[y, x] = COLORS[ch] + (255,)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(a, "RGBA").save(OUT)
    print("escrito:", OUT)


if __name__ == "__main__":
    main()
