#!/usr/bin/env python3
"""Riel del tren de carga (V4.8.0): texturas, modelos de bloque (recto, 4 subidas, 4 curvas), estados, objeto y botín.

  recto / subidas: cajas (balasto, durmientes, dos rieles de acero); la subida es una escalera de peldaños de 1/16 (sin giros de modelo).
  curvas: tres láminas (dos de balasto y una con durmientes y rieles) con una textura de 128x128 dibujada con un cuarto de círculo.
Altura de la cabeza del riel: 4/16 = 0,25 (RailPath.RAIL_TOP).
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
rng = np.random.default_rng(480)

PREFIX = "freight_rail"
BALLAST = (118, 112, 106)
TIE = (88, 64, 46)
STEEL = (166, 170, 180)


def noisy(color, n, amp=14):
    a = np.zeros((n, n, 4), np.uint8)
    a[..., :3] = color
    a[..., 3] = 255
    a[..., :3] = np.clip(a[..., :3].astype(int) + rng.integers(-amp, amp + 1, (n, n, 1)), 0, 255)
    return Image.fromarray(a, "RGBA")


def make_flat_textures():
    tex = ASSETS / "textures/block"
    noisy(BALLAST, 16, 18).save(tex / (PREFIX + "_ballast.png"))
    t = np.asarray(noisy(TIE, 16, 8)).copy()
    for r in range(0, 16, 4):
        t[r, :, :3] = (t[r, :, :3] * 0.8).astype(np.uint8)
    Image.fromarray(t, "RGBA").save(tex / (PREFIX + "_tie.png"))
    s = np.asarray(noisy(STEEL, 16, 6)).copy()
    s[0:4, :, :3] = np.clip(s[0:4, :, :3].astype(int) + 28, 0, 255)
    Image.fromarray(s, "RGBA").save(tex / (PREFIX + "_steel.png"))


def make_curve_textures():
    """Cuarto de círculo alrededor de la esquina inferior derecha (SE) del bloque, 128x128 (cada px del bloque = 8)."""
    S, SS = 128, 4
    big = S * SS
    cx = cy = big  # esquina (derecha, abajo)
    unit = big / 16.0

    def ring(img, r0, r1, color, a0=0.0, a1=90.0):
        d = ImageDraw.Draw(img)
        d.pieslice((cx - r1, cy - r1, cx + r1, cy + r1), 180, 270, fill=color)
        d.pieslice((cx - r0, cy - r0, cx + r0, cy + r0), 180, 270, fill=(0, 0, 0, 0))

    def layer():
        return Image.new("RGBA", (big, big), (0, 0, 0, 0))

    # balasto: de r = 0.5 +- 0.4375 bloques (radio 8 +- 7 px)
    r_mid = 8 * unit
    base = layer()
    d = ImageDraw.Draw(base)
    d.pieslice((cx - (r_mid + 7 * unit), cy - (r_mid + 7 * unit), cx + (r_mid + 7 * unit), cy + (r_mid + 7 * unit)), 180, 270, fill=BALLAST + (255,))
    d.pieslice((cx - (r_mid - 7 * unit), cy - (r_mid - 7 * unit), cx + (r_mid - 7 * unit), cy + (r_mid - 7 * unit)), 180, 270, fill=(0, 0, 0, 0))
    ball = base.resize((S, S), Image.LANCZOS)
    arr = np.asarray(ball).copy()
    sel = arr[..., 3] > 0
    nz = rng.integers(-16, 17, (S, S, 1))
    arr[..., :3] = np.where(sel[..., None], np.clip(arr[..., :3].astype(int) + nz, 0, 255), arr[..., :3]).astype(np.uint8)
    arr[..., 3] = np.where(arr[..., 3] > 127, 255, 0)
    Image.fromarray(arr, "RGBA").save(ASSETS / ("textures/block/" + PREFIX + "_curve_base.png"))

    # arriba: balasto + durmientes + dos rieles
    top = arr.copy()
    topimg = Image.fromarray(top, "RGBA").resize((big, big), Image.NEAREST)
    d = ImageDraw.Draw(topimg)
    n_ties = 6
    for i in range(n_ties):
        ang = math.radians(180 + (i + 0.5) * 90.0 / n_ties)
        er = (math.cos(ang), math.sin(ang))
        et = (-math.sin(ang), math.cos(ang))
        c0 = (cx + r_mid * er[0], cy + r_mid * er[1])
        pts = []
        for sr, st in ((-6, -1), (6, -1), (6, 1), (-6, 1)):
            pts.append((c0[0] + er[0] * sr * unit + et[0] * st * unit, c0[1] + er[1] * sr * unit + et[1] * st * unit))
        d.polygon(pts, fill=TIE + (255,))
    rails = layer()
    dr = ImageDraw.Draw(rails)
    for off in (-4.25, 4.25):
        r_c = r_mid + off * unit
        w = 0.75 * unit
        dr.pieslice((cx - (r_c + w), cy - (r_c + w), cx + (r_c + w), cy + (r_c + w)), 180, 270, fill=STEEL + (255,))
        dr.pieslice((cx - (r_c - w), cy - (r_c - w), cx + (r_c - w), cy + (r_c - w)), 180, 270, fill=(0, 0, 0, 0))
        w2 = 0.25 * unit
        dr.arc((cx - (r_c + w2 * 2), cy - (r_c + w2 * 2), cx + (r_c + w2 * 2), cy + (r_c + w2 * 2)), 180, 270, fill=(214, 218, 228, 255), width=int(w2))
    topimg.alpha_composite(rails)
    # lo que quedó fuera del balasto vuelve a ser transparente
    mask = ball.resize((big, big), Image.NEAREST).split()[3]
    topimg.putalpha(Image.eval(mask, lambda v: 255 if v > 127 else 0))
    # la tabla/riel pintados sobre transparente quedan sólo dentro del balasto; los rieles caen dentro (r 3.75..12.25 px)
    out = topimg.resize((S, S), Image.LANCZOS)
    a = np.asarray(out).copy()
    a[..., 3] = np.where(a[..., 3] > 127, 255, 0)
    Image.fromarray(a, "RGBA").save(ASSETS / ("textures/block/" + PREFIX + "_curve.png"))


def make_item_icon():
    a = np.zeros((16, 16, 4), np.uint8)
    for x in (4, 5, 10, 11):
        for y in range(0, 16):
            a[y, x] = (*STEEL, 255) if x in (4, 10) else (110, 114, 124, 255)
    for y in (1, 4, 7, 10, 13):
        for x in range(2, 14):
            if a[y, x, 3] == 0:
                a[y, x] = (*TIE, 255)
                a[y + 1 if y + 1 < 16 else y, x] = (*TIE, 255)
    Image.fromarray(a, "RGBA").save(ASSETS / ("textures/item/" + PREFIX + ".png"))
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / ("models/item/" + PREFIX + ".json")).write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": ("beautifuldecor:item/" + PREFIX + "")}}, indent=2), encoding="utf-8")


def textures():
    return {"ballast": "beautifuldecor:block/" + PREFIX + "_ballast", "tie": "beautifuldecor:block/" + PREFIX + "_tie",
            "steel": "beautifuldecor:block/" + PREFIX + "_steel", "particle": "beautifuldecor:block/" + PREFIX + "_ballast"}


def face(tex):
    return {"texture": "#" + tex}


def cuboid(frm, to, tex, faces=("up", "north", "south", "east", "west")):
    return {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {f: face(tex) for f in faces}}


def flat_model():
    el = [cuboid((1, 0, 0), (15, 2, 16), "ballast", ("up", "north", "south", "east", "west", "down"))]
    for z in (0.5, 3.5, 6.5, 9.5, 12.5):
        el.append(cuboid((2, 2, z), (14, 3, z + 2), "tie"))
    el.append(cuboid((3, 3, 0), (4.5, 4, 16), "steel"))
    el.append(cuboid((11.5, 3, 0), (13, 4, 16), "steel"))
    return {"textures": textures(), "elements": el}


def up_model(direction):
    """Escalera de 16 peldaños: u = a lo largo de la subida (0..16), w = a lo ancho."""
    def box(u0, u1, y0, y1, w0, w1, tex, faces=("up", "north", "south", "east", "west")):
        if direction == "east":
            frm, to = (u0, y0, w0), (u1, y1, w1)
        elif direction == "west":
            frm, to = (16 - u1, y0, w0), (16 - u0, y1, w1)
        elif direction == "south":
            frm, to = (w0, y0, u0), (w1, y1, u1)
        else:
            frm, to = (w0, y0, 16 - u1), (w1, y1, 16 - u0)
        return cuboid(frm, to, tex, faces)
    el = []
    for i in range(16):
        h = i + 0.5
        el.append(box(i, i + 1, 0, h + 2, 1, 15, "ballast"))
        if i % 3 == 1:
            el.append(box(i, i + 2 if i + 2 <= 16 else 16, h + 2, h + 3, 2, 14, "tie"))
        el.append(box(i, i + 1, h + 3, h + 4, 3, 4.5, "steel"))
        el.append(box(i, i + 1, h + 3, h + 4, 11.5, 13, "steel"))
    return {"textures": textures(), "elements": el}


def curve_model(uv):
    return {
        "textures": {"base": ("beautifuldecor:block/" + PREFIX + "_curve_base"), "top": ("beautifuldecor:block/" + PREFIX + "_curve"),
                     "particle": ("beautifuldecor:block/" + PREFIX + "_ballast")},
        "render_type": "minecraft:cutout",
        "elements": [
            {"from": [0, 1, 0], "to": [16, 1, 16], "faces": {"up": {"texture": "#base", "uv": uv}}},
            {"from": [0, 2, 0], "to": [16, 2, 16], "faces": {"up": {"texture": "#base", "uv": uv}}},
            {"from": [0, 3, 0], "to": [16, 3, 16], "faces": {"up": {"texture": "#top", "uv": uv}, "down": {"texture": "#base", "uv": uv}}},
        ]}


def write_models():
    mdir = ASSETS / "models/block"
    mdir.mkdir(parents=True, exist_ok=True)
    models = {(PREFIX + "_flat"): flat_model()}
    for d in ("east", "west", "south", "north"):
        models[f"{PREFIX}_up_{d}"] = up_model(d)
    # la textura tiene la curva alrededor de la esquina inferior derecha (SE); las demás se obtienen volteando los UV
    models[(PREFIX + "_curve_se")] = curve_model([0, 0, 16, 16])
    models[(PREFIX + "_curve_sw")] = curve_model([16, 0, 0, 16])
    models[(PREFIX + "_curve_ne")] = curve_model([0, 16, 16, 0])
    models[(PREFIX + "_curve_nw")] = curve_model([16, 16, 0, 0])
    for name, m in models.items():
        (mdir / f"{name}.json").write_text(json.dumps(m, indent=1), encoding="utf-8")
    pre = ("beautifuldecor:block/" + PREFIX + "_")
    variants = {
        "shape=north_south": {"model": pre + "flat"},
        "shape=east_west": {"model": pre + "flat", "y": 90},
        "shape=ascending_east": {"model": pre + "up_east"},
        "shape=ascending_west": {"model": pre + "up_west"},
        "shape=ascending_north": {"model": pre + "up_north"},
        "shape=ascending_south": {"model": pre + "up_south"},
        "shape=south_east": {"model": pre + "curve_se"},
        "shape=south_west": {"model": pre + "curve_sw"},
        "shape=north_west": {"model": pre + "curve_nw"},
        "shape=north_east": {"model": pre + "curve_ne"},
    }
    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    (ASSETS / ("blockstates/" + PREFIX + ".json")).write_text(json.dumps({"variants": variants}, indent=1), encoding="utf-8")
    loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": ("beautifuldecor:" + PREFIX + "")}],
                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    (DATA / "loot_tables/blocks").mkdir(parents=True, exist_ok=True)
    (DATA / ("loot_tables/blocks/" + PREFIX + ".json")).write_text(json.dumps(loot, indent=1), encoding="utf-8")


def make_cargo_textures():
    """Carga de los vagones (32x32 = cuatro mosaicos de 16x16): carbón, alimentos mezclados y minerales."""
    ent = ASSETS / "textures/entity"
    ent.mkdir(parents=True, exist_ok=True)

    def base(color, amp):
        a = np.zeros((32, 32, 4), np.uint8)
        a[..., 3] = 255
        a[..., :3] = np.clip(np.array(color)[None, None, :] + rng.integers(-amp, amp + 1, (32, 32, 1)), 0, 255)
        return a

    def blobs(a, colors, count, size=(1, 3)):
        for _ in range(count):
            x, y = rng.integers(0, 32, 2)
            w, h = rng.integers(size[0], size[1] + 1, 2)
            c = colors[rng.integers(0, len(colors))]
            for yy in range(h):
                for xx in range(w):
                    a[(y + yy) % 32, (x + xx) % 32, :3] = np.clip(np.array(c) + rng.integers(-10, 11), 0, 255)

    coal = base((28, 28, 32), 8)
    blobs(coal, [(12, 12, 14), (52, 54, 62), (74, 78, 90), (20, 20, 24)], 150)
    Image.fromarray(coal, "RGBA").save(ent / "cargo_coal.png")
    food = base((120, 84, 44), 10)
    blobs(food, [(200, 40, 36), (232, 168, 40), (70, 150, 60), (236, 140, 150), (240, 200, 90), (150, 96, 50), (222, 108, 28)], 170, (2, 3))
    Image.fromarray(food, "RGBA").save(ent / "cargo_food.png")
    mineral = base((104, 106, 112), 14)
    blobs(mineral, [(90, 92, 98), (124, 126, 134)], 120, (2, 4))
    blobs(mineral, [(240, 200, 60), (80, 220, 220), (70, 200, 110), (200, 60, 60), (232, 232, 240), (60, 90, 200), (210, 130, 90)], 80, (1, 2))
    Image.fromarray(mineral, "RGBA").save(ent / "cargo_mineral.png")


def make_item_models():
    for name in ("freight_loco", "coal_wagon", "food_wagon", "mineral_wagon", "steam_classic", "steam_modern", "coach_low", "coach_mid", "coach_high", "metro_head", "metro_car"):
        (ASSETS / "models/item" / f"{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{name}"}}, indent=2), encoding="utf-8")


def family(prefix, ballast, tie, steel):
    global PREFIX, BALLAST, TIE, STEEL
    PREFIX, BALLAST, TIE, STEEL = prefix, ballast, tie, steel
    make_flat_textures()
    make_curve_textures()
    make_item_icon()
    write_models()


def main():
    family("freight_rail", (118, 112, 106), (88, 64, 46), (166, 170, 180))
    # V5.0.0: vía de pasajeros (otro diseño: balasto claro, durmientes de concreto, rieles oscuros)
    family("passenger_rail", (156, 152, 146), (150, 150, 152), (110, 114, 124))
    family("metro_rail", (190, 192, 198), (72, 76, 86), (214, 220, 232))
    make_cargo_textures()
    make_item_models()
    print("escrito: texturas, modelos, estados, objeto y botín de los rieles de carga y de pasajeros")


if __name__ == "__main__":
    main()
