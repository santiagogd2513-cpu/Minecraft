"""Letreros de neón Bugerati: aro, corazón, estrella, rayo, luna y flecha en 4 colores + el logo dorado."""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from .common import *

S = 256
COLORS = {
    "pink": ((255, 64, 160), "Pink", "rosa"),
    "cyan": ((60, 230, 255), "Cyan", "cian"),
    "yellow": ((255, 222, 64), "Yellow", "amarillo"),
    "red": ((255, 72, 72), "Red", "rojo"),
}
SHAPES = {
    "ring": ("Ring", "aro"),
    "heart": ("Heart", "corazón"),
    "star": ("Star", "estrella"),
    "bolt": ("Lightning Bolt", "rayo"),
    "moon": ("Moon", "luna"),
    "arrow": ("Arrow", "flecha"),
}


def circle(cx, cy, r, a0=0, a1=360, n=96):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def paths(shape):
    if shape == "ring":
        return [circle(128, 128, 92), circle(128, 128, 56)]
    if shape == "heart":
        pts = []
        for i in range(121):
            t = math.pi * 2 * i / 120
            x = 16 * math.sin(t) ** 3
            y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
            pts.append((128 + x * 5.6, 118 - y * 5.6))
        return [pts]
    if shape == "star":
        pts = []
        for i in range(11):
            r = 100 if i % 2 == 0 else 44
            a = math.radians(-90 + 36 * i)
            pts.append((128 + r * math.cos(a), 134 + r * math.sin(a)))
        return [pts]
    if shape == "bolt":
        return [[(158, 20), (70, 142), (122, 142), (98, 236), (190, 104), (138, 104), (158, 20)]]
    if shape == "moon":
        c1, r1 = (118, 128), 96
        c2, r2 = (166, 108), 74
        outer = [p for p in circle(c1[0], c1[1], r1, 0, 360, 180)
                 if math.hypot(p[0] - c2[0], p[1] - c2[1]) >= r2]
        inner = [p for p in circle(c2[0], c2[1], r2, 0, 360, 180)
                 if math.hypot(p[0] - c1[0], p[1] - c1[1]) <= r1]
        return [outer, inner]
    if shape == "arrow":
        return [[(24, 98), (136, 98), (136, 52), (232, 128), (136, 204), (136, 158), (24, 158), (24, 98)]]
    if shape == "logo":
        b1 = [(98, 66), (98, 190)]
        top = [(98, 66), (140, 66)] + circle(140, 97, 31, -90, 90, 24) + [(140, 128), (98, 128)]
        bottom = [(98, 128), (150, 128)] + circle(150, 159, 31, -90, 90, 24) + [(150, 190), (98, 190)]
        return [circle(128, 128, 112), b1, top, bottom]
    raise ValueError(shape)


def render_neon(shape, color):
    tube = Image.new("L", (S, S), 0)
    core = Image.new("L", (S, S), 0)
    dt, dc = ImageDraw.Draw(tube), ImageDraw.Draw(core)
    for path in paths(shape):
        dt.line(path, fill=255, width=13, joint="curve")
        dc.line(path, fill=255, width=5, joint="curve")
        for (x, y) in (path[0], path[-1]):
            dt.ellipse([x - 6.5, y - 6.5, x + 6.5, y + 6.5], fill=255)
            dc.ellipse([x - 2.5, y - 2.5, x + 2.5, y + 2.5], fill=255)
    glow = tube.filter(ImageFilter.GaussianBlur(11))
    t = np.asarray(tube, dtype=np.float64) / 255.0
    cr = np.asarray(core, dtype=np.float64) / 255.0
    g = np.asarray(glow, dtype=np.float64) / 255.0
    alpha = np.clip(g * 0.85 + t, 0, 1)
    base = np.array(color, dtype=np.float64)
    white = base + (255 - base) * 0.82
    rgb = np.zeros((S, S, 3))
    for i in range(3):
        rgb[:, :, i] = base[i] * (1 - cr) + white[i] * cr
    out = np.dstack([rgb, alpha * 255]).astype("uint8")
    im = Image.fromarray(out, "RGBA").resize((64, 64), Image.LANCZOS)
    return im


def board_texture():
    im = new(16, 16, (16, 16, 22, 236))
    frame(im, 0, 0, 15, 15, (60, 62, 72, 240))
    save_tex("bg_neon_board", im)


def build_model(art):
    m = Model(render_type="minecraft:translucent")
    board = m.tex("board", "bg_neon_board")
    tex_art = m.tex("art", art)
    metal = m.tex("metal", "bg_gold")
    m.box([0.5, 0.5, 15], [15.5, 15.5, 16], board)
    m.box([0.5, 0.5, 14.6], [15.5, 15.5, 15], None, north=(tex_art, [0, 0, 16, 16]))
    for (x, y) in ((1.0, 1.0), (14.0, 1.0), (1.0, 14.0), (14.0, 14.0)):
        m.box([x, y, 14.2], [x + 1, y + 1, 14.6], metal)
    return m


def generate():
    board_texture()
    for shape, (sen, ses) in SHAPES.items():
        for color, (rgb, cen, ces) in COLORS.items():
            bid = f"bugerati_neon_{shape}_{color}"
            save_tex(bid, render_neon(shape, rgb))
            build_model(bid).save(bid, bid)
            facing_state(bid, lambda e, b=bid: b)
            write_item_model(bid)
            names(bid, f"Bugerati Neon {sen} ({cen})", f"Neón Bugerati {ses} ({ces})")
    bid = "bugerati_neon_logo"
    save_tex(bid, render_neon("logo", (255, 196, 70)))
    build_model(bid).save(bid, bid)
    facing_state(bid, lambda e, b=bid: b)
    write_item_model(bid)
    names(bid, "Bugerati Neon Logo (Gold)", "Neón logo Bugerati (dorado)")


def all_ids():
    ids = [f"bugerati_neon_{s}_{c}" for s in SHAPES for c in COLORS]
    return ids + ["bugerati_neon_logo"]


if __name__ == "__main__":
    generate()
