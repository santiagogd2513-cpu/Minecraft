"""Texturas de Minecraft aproximadas (colores promedio + un poco de ruido) para dibujar los íconos.

El sandbox no tiene los archivos del juego, así que los bloques que usan texturas vanilla (tablones, lana,
concreto, troncos, metales...) se dibujan con una aproximación de 16x16. Es solo para los PNG de los íconos;
en el juego los bloques siguen usando sus texturas reales.
"""
import hashlib
import random

from PIL import Image

PLANKS = {
    "oak": (162, 130, 78), "spruce": (114, 84, 48), "birch": (192, 175, 121), "jungle": (160, 115, 81),
    "acacia": (168, 90, 50), "dark_oak": (66, 43, 20), "mangrove": (117, 54, 48), "cherry": (226, 178, 172),
    "bamboo": (193, 173, 80), "crimson": (101, 48, 70), "warped": (43, 104, 99),
}
STRIPPED = {
    "oak": (177, 144, 86), "spruce": (115, 89, 52), "birch": (196, 176, 118), "jungle": (171, 132, 84),
    "acacia": (174, 92, 59), "dark_oak": (72, 56, 36), "mangrove": (119, 54, 47), "cherry": (215, 145, 148),
    "bamboo": (193, 173, 80), "crimson": (137, 57, 90), "warped": (57, 150, 147),
}
CONCRETE = {
    "white": (207, 213, 214), "orange": (224, 97, 0), "magenta": (169, 48, 159), "light_blue": (36, 137, 199),
    "yellow": (240, 175, 21), "lime": (94, 168, 24), "pink": (213, 101, 142), "gray": (55, 58, 62),
    "light_gray": (125, 125, 115), "cyan": (21, 119, 136), "purple": (100, 31, 156), "blue": (44, 46, 143),
    "brown": (96, 60, 32), "green": (73, 91, 36), "red": (142, 33, 33), "black": (8, 10, 15),
}
WOOL = {
    "white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
    "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "gray": (62, 68, 71),
    "light_gray": (142, 142, 134), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
    "brown": (114, 71, 40), "green": (84, 109, 27), "red": (160, 39, 34), "black": (20, 21, 25),
}
GLASS = {
    "white": (255, 255, 255), "red": (153, 51, 51), "green": (102, 127, 51), "light_blue": (102, 153, 216),
}
METALS = {
    "iron_block": (220, 220, 220), "gold_block": (249, 236, 79), "emerald_block": (42, 203, 87),
    "netherite_block": (66, 61, 63), "copper_block": (192, 107, 80),
}


def _rng(name):
    return random.Random(int(hashlib.md5(name.encode()).hexdigest()[:8], 16))


def _clamp(v):
    return max(0, min(255, int(round(v))))


def _mul(c, f):
    return tuple(_clamp(v * f) for v in c)


def _noise_tex(name, base, amp, alpha=255):
    r = _rng(name)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = 1 + r.uniform(-amp, amp) / 100.0
            px[x, y] = _mul(base, n) + (alpha,)
    return im


def _planks(name, base):
    r = _rng(name)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for row in range(4):
        board = 1 + r.uniform(-0.06, 0.06)
        seam = r.randint(2, 13)
        for y in range(row * 4, row * 4 + 4):
            for x in range(16):
                f = board * (1 + r.uniform(-0.035, 0.035))
                if y % 4 == 3:
                    f *= 0.80
                elif x == seam:
                    f *= 0.86
                px[x, y] = _mul(base, f) + (255,)
    return im


def _grain(name, base, amp=5):
    r = _rng(name)
    cols = [1 + r.uniform(-amp, amp) / 100.0 for _ in range(16)]
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = _mul(base, cols[x] * (1 + r.uniform(-0.03, 0.03))) + (255,)
    return im


def _metal(name, base):
    r = _rng(name)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            f = 1 + r.uniform(-0.03, 0.03)
            if x in (0, 15) or y in (0, 15):
                f *= 0.88
            elif x in (1, 14) or y in (1, 14):
                f *= 1.07
            px[x, y] = _mul(base, f) + (255,)
    return im


def _glass(name, base):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = _mul(base, 0.85) + (235,)
            else:
                px[x, y] = base + (105,)
    return im


def _grid(name, base, line):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (line if (x % 8 == 0 or y % 8 == 0) else base) + (255,)
    return im


def vanilla_texture(ref):
    """ref: 'minecraft:block/oak_planks' (o sin namespace). Devuelve una imagen RGBA 16x16."""
    key = ref.split(":", 1)[-1].replace("block/", "")
    for wood, c in PLANKS.items():
        if key == f"{wood}_planks":
            return _planks(key, c)
    for wood, c in STRIPPED.items():
        if key in (f"stripped_{wood}_log", f"stripped_{wood}_stem", f"stripped_{wood}_block"):
            return _grain(key, c)
    for col, c in CONCRETE.items():
        if key == f"{col}_concrete":
            return _noise_tex(key, c, 2.5)
    for col, c in WOOL.items():
        if key == f"{col}_wool":
            return _noise_tex(key, c, 6)
    for col, c in GLASS.items():
        if key == f"{col}_stained_glass":
            return _glass(key, c)
    if key in METALS:
        return _metal(key, METALS[key])
    table = {
        "dark_oak_log": lambda: _grain(key, (60, 46, 26), 12),
        "red_concrete_powder": lambda: _noise_tex(key, (168, 54, 50), 5),
        "glowstone": lambda: _noise_tex(key, (225, 170, 95), 16),
        "sea_lantern": lambda: _grid(key, (172, 199, 190), (205, 225, 216)),
        "polished_andesite": lambda: _noise_tex(key, (132, 134, 133), 3),
        "quartz_block_bottom": lambda: _noise_tex(key, (236, 230, 223), 1.5),
        "redstone_block": lambda: _noise_tex(key, (171, 27, 9), 10),
        "stone": lambda: _noise_tex(key, (125, 125, 125), 7),
    }
    if key in table:
        return table[key]()
    return _noise_tex(key, (170, 170, 170), 4)
