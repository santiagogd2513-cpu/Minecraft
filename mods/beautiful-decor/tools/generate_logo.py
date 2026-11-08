#!/usr/bin/env python3
"""Genera el icono del mod (logo.png, 256x256) y el icono del paquete de recursos (pack.png, 128x128).

Dibujo pixel-art de 64x64 (un sofá, una lámpara de pie y un televisor con el logo HS) ampliado con
vecino más cercano para mantener el estilo de Minecraft. Requiere Pillow.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources"
N = 64

FONT = {
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"], "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"],
    "H": ["101", "101", "111", "101", "101"], "I": ["111", "010", "010", "010", "111"],
    "L": ["100", "100", "100", "100", "111"], "O": ["010", "101", "101", "101", "010"],
    "R": ["110", "101", "110", "101", "101"], "S": ["111", "100", "111", "001", "111"],
    "T": ["111", "010", "010", "010", "010"], "U": ["101", "101", "101", "101", "111"],
}


def text(d, s, cy, color, shadow=None):
    width = len(s) * 4 - 1
    x = (N - width) // 2
    for ch in s:
        for ry, row in enumerate(FONT[ch]):
            for rx, bit in enumerate(row):
                if bit == "1":
                    if shadow:
                        d.point((x + rx, cy + ry + 1), fill=shadow)
                    d.point((x + rx, cy + ry), fill=color)
        x += 4


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def build():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # fondo: degradado morado -> azul con esquinas redondeadas
    top, bottom = (84, 42, 150), (34, 74, 138)
    bg = Image.new("RGBA", (N, N))
    bd = ImageDraw.Draw(bg)
    for y in range(N):
        bd.line([(0, y), (N - 1, y)], fill=mix(top, bottom, y / (N - 1)) + (255,))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], radius=7, fill=255)
    im.paste(bg, (0, 0), mask)
    d = ImageDraw.Draw(im)

    # borde claro
    d.rounded_rectangle([0, 0, N - 1, N - 1], radius=7, outline=(190, 160, 255, 255))

    # habitación: pared y piso de madera
    d.rectangle([4, 4, 59, 43], fill=(238, 224, 204, 255))
    d.rectangle([4, 4, 59, 5], fill=(214, 196, 172, 255))
    d.rectangle([4, 36, 59, 43], fill=(168, 110, 64, 255))
    for x in range(4, 60, 7):
        d.line([(x, 36), (x, 43)], fill=(138, 86, 48, 255))
    d.line([(4, 36), (59, 36)], fill=(196, 140, 86, 255))

    # televisor / monitor en la pared con el logo HS
    d.rectangle([20, 8, 39, 19], fill=(32, 34, 42, 255))
    for y in range(9, 19):
        d.line([(21, y), (38, y)], fill=mix((60, 130, 230), (30, 80, 190), (y - 9) / 9) + (255,))
    for ch, x0 in (("H", 26), ("S", 31)):
        for ry, row in enumerate(FONT[ch]):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.point((x0 + rx, 11 + ry), fill=(255, 255, 255, 255))
    d.point((29, 18), fill=(90, 255, 140, 255))

    # sofá
    coral, coral_d, coral_l = (222, 84, 92), (170, 52, 66), (244, 118, 118)
    d.rectangle([13, 25, 45, 33], fill=coral_d + (255,))                 # respaldo
    d.rectangle([15, 26, 43, 31], fill=coral + (255,))
    d.line([(29, 26), (29, 31)], fill=coral_d + (255,))
    d.rectangle([14, 31, 44, 36], fill=coral_l + (255,))                  # asiento
    d.line([(29, 31), (29, 36)], fill=coral + (255,))
    d.rectangle([10, 28, 14, 37], fill=coral_d + (255,))                  # brazos
    d.rectangle([44, 28, 48, 37], fill=coral_d + (255,))
    d.rectangle([11, 28, 13, 29], fill=coral + (255,))
    d.rectangle([45, 28, 47, 29], fill=coral + (255,))
    for x in (12, 46):                                                    # patas
        d.rectangle([x, 38, x + 1, 39], fill=(92, 58, 36, 255))

    # lámpara de pie con luz cálida
    d.rectangle([54, 18, 54, 38], fill=(70, 66, 76, 255))
    d.rectangle([52, 38, 56, 39], fill=(70, 66, 76, 255))
    d.polygon([(51, 16), (57, 16), (56, 9), (52, 9)], fill=(255, 226, 120, 255))
    d.line([(52, 9), (56, 9)], fill=(255, 245, 190, 255))
    for y in range(17, 22):
        d.line([(53 - (y - 17) // 2, y), (55 + (y - 17) // 2, y)],
               fill=mix((255, 236, 160), (238, 224, 204), (y - 16) / 6) + (255,))

    # planta a la izquierda
    d.rectangle([6, 33, 9, 37], fill=(176, 92, 60, 255))
    d.rectangle([6, 33, 9, 33], fill=(210, 120, 80, 255))
    for (x, y) in ((7, 31), (8, 30), (6, 30), (9, 31), (7, 29), (8, 28)):
        d.point((x, y), fill=(70, 160, 84, 255))
    d.point((7, 32), fill=(70, 160, 84, 255))
    d.point((8, 32), fill=(70, 160, 84, 255))

    # panel de texto
    d.rounded_rectangle([4, 46, 59, 59], radius=2, fill=(30, 18, 60, 255))
    text(d, "BEAUTIFUL", 48, (255, 255, 255, 255), (120, 70, 200, 255))
    text(d, "DECOR", 54, (255, 214, 102, 255), (120, 70, 200, 255))
    return im


if __name__ == "__main__":
    small = build()
    logo = small.resize((256, 256), Image.NEAREST)
    logo.save(OUT / "logo.png")
    small.resize((128, 128), Image.NEAREST).save(OUT / "pack.png")
    print("logo.png (256x256) y pack.png (128x128) generados en", OUT)
