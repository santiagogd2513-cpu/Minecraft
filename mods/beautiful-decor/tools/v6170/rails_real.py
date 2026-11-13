#!/usr/bin/env python3
"""V6.17.0: vías más realistas (recto, subidas, curvas) de las tres familias.

Se reutiliza el generador de V4.8.0 y se cambian: balasto con talud (dos escalones), 4 durmientes por bloque,
riel con pie, alma y cabeza (la cabeza sigue a 4/16 = RailPath.RAIL_TOP), grava con piedras y durmientes
(madera con veta / concreto con motas). Las posiciones de los rieles (x = 3..4,5 y 11,5..13) no cambian.
"""
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("rails480", HERE.parent / "v480" / "rails.py")
R = importlib.util.module_from_spec(spec)
spec.loader.exec_module(R)

# centros de los rieles (px): 3,75 y 12,25
LEFT, RIGHT = 3.75, 12.25


def rail_parts(c):
    """pie, alma y cabeza como (x0, x1, y0, y1)."""
    return [(c - 1.25, c + 1.25, 2.5, 3.0), (c - 0.4, c + 0.4, 3.0, 3.5), (c - 0.85, c + 0.85, 3.5, 4.0)]


def flat_model():
    el = [R.cuboid((0.5, 0, 0), (15.5, 1, 16), "ballast", ("up", "north", "south", "east", "west", "down")),
          R.cuboid((1.5, 1, 0), (14.5, 1.5, 16), "ballast")]
    for z in (1, 5, 9, 13):
        el.append(R.cuboid((2, 1.5, z), (14, 2.5, z + 2), "tie"))
    for c in (LEFT, RIGHT):
        for x0, x1, y0, y1 in rail_parts(c):
            el.append(R.cuboid((x0, y0, 0), (x1, y1, 16), "steel"))
    return {"textures": R.textures(), "elements": el}


def up_model(direction):
    def box(u0, u1, y0, y1, w0, w1, tex):
        if direction == "east":
            frm, to = (u0, y0, w0), (u1, y1, w1)
        elif direction == "west":
            frm, to = (16 - u1, y0, w0), (16 - u0, y1, w1)
        elif direction == "south":
            frm, to = (w0, y0, u0), (w1, y1, u1)
        else:
            frm, to = (w0, y0, 16 - u1), (w1, y1, 16 - u0)
        return R.cuboid(frm, to, tex)
    el = []
    for i in range(16):
        h = i + 0.5
        el.append(box(i, i + 1, 0, h + 1.0, 0.5, 15.5, "ballast"))
        el.append(box(i, i + 1, h + 1.0, h + 1.5, 1.5, 14.5, "ballast"))
        if i % 4 == 1:
            el.append(box(i, min(i + 2, 16), h + 1.5, h + 2.5, 2, 14, "tie"))
        for c in (LEFT, RIGHT):
            for x0, x1, y0, y1 in rail_parts(c):
                el.append(box(i, i + 1, h + y0 - 0.0, h + y1 - 0.0, x0, x1, "steel"))
    return {"textures": R.textures(), "elements": el}


def make_flat_textures():
    tex = R.ASSETS / "textures/block"
    b = np.asarray(R.noisy(R.BALLAST, 16, 14)).copy()
    for _ in range(26):  # piedras claras y oscuras
        x, y = R.rng.integers(0, 16, 2)
        d = int(R.rng.integers(-34, 35))
        b[y, x, :3] = np.clip(b[y, x, :3].astype(int) + d, 0, 255)
        if R.rng.random() < 0.4:
            b[y, (x + 1) % 16, :3] = np.clip(b[y, (x + 1) % 16, :3].astype(int) + d, 0, 255)
    Image.fromarray(b, "RGBA").save(tex / (R.PREFIX + "_ballast.png"))
    t = np.asarray(R.noisy(R.TIE, 16, 7)).copy()
    wood = R.TIE[0] > R.TIE[2] + 20
    if wood:
        for r in range(1, 16, 3):  # veta a lo largo
            t[r, :, :3] = (t[r, :, :3] * 0.78).astype(np.uint8)
        for _ in range(3):
            y = int(R.rng.integers(0, 16)); x = int(R.rng.integers(0, 12))
            t[y, x:x + 3, :3] = (t[y, x:x + 3, :3] * 0.65).astype(np.uint8)
    else:
        for _ in range(30):
            x, y = R.rng.integers(0, 16, 2)
            t[y, x, :3] = np.clip(t[y, x, :3].astype(int) + int(R.rng.integers(-18, 19)), 0, 255)
    Image.fromarray(t, "RGBA").save(tex / (R.PREFIX + "_tie.png"))
    s = np.asarray(R.noisy(R.STEEL, 16, 5)).copy()
    s[0:3, :, :3] = np.clip(s[0:3, :, :3].astype(int) + 34, 0, 255)  # brillo de la cabeza
    s[13:16, :, :3] = (s[13:16, :, :3] * 0.8).astype(np.uint8)
    Image.fromarray(s, "RGBA").save(tex / (R.PREFIX + "_steel.png"))


def make_curve_textures():
    """Igual que el original pero con 3 durmientes finos y rieles con pie oscuro, cabeza clara y línea brillante."""
    import math
    S, SS = 128, 4
    big = S * SS
    cx = cy = big
    unit = big / 16.0
    r_mid = 8 * unit

    def layer():
        return Image.new("RGBA", (big, big), (0, 0, 0, 0))
    base = layer()
    d = ImageDraw.Draw(base)
    d.pieslice((cx - (r_mid + 7 * unit), cy - (r_mid + 7 * unit), cx + (r_mid + 7 * unit), cy + (r_mid + 7 * unit)), 180, 270, fill=R.BALLAST + (255,))
    d.pieslice((cx - (r_mid - 7 * unit), cy - (r_mid - 7 * unit), cx + (r_mid - 7 * unit), cy + (r_mid - 7 * unit)), 180, 270, fill=(0, 0, 0, 0))
    ball = base.resize((S, S), Image.LANCZOS)
    arr = np.asarray(ball).copy()
    sel = arr[..., 3] > 0
    nz = R.rng.integers(-14, 15, (S, S, 1))
    stones = (R.rng.random((S, S, 1)) < 0.06) * R.rng.integers(-34, 35, (S, S, 1))
    arr[..., :3] = np.where(sel[..., None], np.clip(arr[..., :3].astype(int) + nz + stones, 0, 255), arr[..., :3]).astype(np.uint8)
    arr[..., 3] = np.where(arr[..., 3] > 127, 255, 0)
    Image.fromarray(arr, "RGBA").save(R.ASSETS / ("textures/block/" + R.PREFIX + "_curve_base.png"))

    topimg = Image.fromarray(arr.copy(), "RGBA").resize((big, big), Image.NEAREST)
    d = ImageDraw.Draw(topimg)
    for i in range(3):
        ang = math.radians(180 + (i + 0.5) * 90.0 / 3)
        er = (math.cos(ang), math.sin(ang)); et = (-math.sin(ang), math.cos(ang))
        c0 = (cx + r_mid * er[0], cy + r_mid * er[1])
        pts = [(c0[0] + er[0] * sr * unit + et[0] * st * unit, c0[1] + er[1] * sr * unit + et[1] * st * unit) for sr, st in ((-6, -1), (6, -1), (6, 1), (-6, 1))]
        d.polygon(pts, fill=R.TIE + (255,))
    rails = layer()
    dr = ImageDraw.Draw(rails)

    def band(off, half, color):
        r_c = r_mid + off * unit
        w = half * unit
        dr.pieslice((cx - (r_c + w), cy - (r_c + w), cx + (r_c + w), cy + (r_c + w)), 180, 270, fill=color + (255,))
        dr.pieslice((cx - (r_c - w), cy - (r_c - w), cx + (r_c - w), cy + (r_c - w)), 180, 270, fill=(0, 0, 0, 0))
    dark = tuple(int(v * 0.62) for v in R.STEEL)
    light = tuple(min(255, int(v * 1.12) + 8) for v in R.STEEL)
    for off in (-4.25, 4.25):
        band(off, 1.25, dark)          # pie
        band(off, 0.85, R.STEEL)       # cabeza
        band(off, 0.3, light)          # brillo
    topimg.alpha_composite(rails)
    mask = ball.resize((big, big), Image.NEAREST).split()[3]
    topimg.putalpha(mask.point(lambda v: 255 if v > 127 else 0))
    out = topimg.resize((S, S), Image.LANCZOS)
    a = np.asarray(out).copy()
    a[..., 3] = np.where(a[..., 3] > 127, 255, 0)
    Image.fromarray(a, "RGBA").save(R.ASSETS / ("textures/block/" + R.PREFIX + "_curve.png"))


R.flat_model, R.up_model = flat_model, up_model
R.make_flat_textures, R.make_curve_textures = make_flat_textures, make_curve_textures

if __name__ == "__main__":
    R.family("freight_rail", (118, 112, 106), (88, 64, 46), (166, 170, 180))
    R.family("passenger_rail", (156, 152, 146), (150, 150, 152), (110, 114, 124))
    R.family("metro_rail", (190, 192, 198), (72, 76, 86), (214, 220, 232))
    print("V6.17.0: vías realistas escritas")
