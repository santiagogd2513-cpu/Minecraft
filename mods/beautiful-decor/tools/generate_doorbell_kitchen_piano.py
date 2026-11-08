#!/usr/bin/env python3
"""Genera la v1.4.0 de BeautifulDecor: timbres, cocina (escurridores, cafetera, exprimidor,
sartenes, tapas) y piano. Crea texturas, modelos, blockstates, ítems, loot tables, idiomas y sonidos.

Uso:  python3 tools/generate_doorbell_kitchen_piano.py
Los nombres deben coincidir con DOORBELLS / KITCHENWARE / PIANOS de ModBlocks.java y con
ModSounds.java (doorbell, piano_do ... piano_si).

Los círculos se dibujan con cajas alineadas a los ejes (estilo voxel), sin rotaciones de elemento,
para que el resultado no dependa del signo de rotación del motor de modelos.
"""

import json
import math
import random
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
SOURCE_DOORBELL = ROOT / "tools/source_sounds/doorbell_original.ogg"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("north", "east", "south", "west", "up", "down")

BLOCKS = {}   # nombre -> dict(en, es, loot_px0)
TEXTURES_USED = set()


# --------------------------------------------------------------------------- utilidades
def wj(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def r(v):
    return round(v, 4)


def E(frm, to, tex, faces=None):
    f = {k: {"texture": tex} for k in FACES}
    if faces:
        f.update(faces)
    return {"from": [r(v) for v in frm], "to": [r(v) for v in to], "faces": f}


def raster(plane, c, R, lo, hi, tex, r_in=0.0, g=0.5, bottom=None):
    """Disco (o anillo si r_in > 0) hecho de cajas. plane: 'y' (c = x,z; grosor en y),
    'x' (c = z,y; grosor en x), 'z' (c = x,y; grosor en z)."""
    n = int(math.ceil(R / g)) + 1
    cells = set()
    for i in range(-n, n):
        for j in range(-n, n):
            d = math.hypot((i + 0.5) * g, (j + 0.5) * g)
            if r_in < d <= R:
                cells.add((i, j))
    rows = {}
    for j in range(-n, n):
        runs, start = [], None
        for i in range(-n, n + 1):
            inside = (i, j) in cells
            if inside and start is None:
                start = i
            if not inside and start is not None:
                runs.append((start, i))
                start = None
        rows[j] = set(runs)
    rects, active = [], {}
    for j in range(-n, n + 1):
        cur = rows.get(j, set())
        for key in list(active):
            if key not in cur:
                rects.append((key[0], key[1], active.pop(key), j))
        for key in cur:
            active.setdefault(key, j)
    out = []
    for i0, i1, j0, j1 in rects:
        a0, a1 = c[0] + i0 * g, c[0] + i1 * g
        b0, b1 = c[1] + j0 * g, c[1] + j1 * g
        if plane == "y":
            frm, to = (a0, lo, b0), (a1, hi, b1)
        elif plane == "x":
            frm, to = (lo, b0, a0), (hi, b1, a1)
        else:
            frm, to = (a0, b0, lo), (a1, b1, hi)
        faces = {"down": {"texture": bottom}} if bottom and plane == "y" else None
        out.append(E(frm, to, tex, faces))
    return out


def write_texture(name, w, h, pixel):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = pixel(x, y)
    img.save(ASSETS / f"textures/block/{name}.png")


def solid(name, color, noise=2, alpha=255):
    rnd = random.Random(name)

    def pixel(x, y):
        d = rnd.randint(-noise, noise)
        return tuple(max(0, min(255, ch + d)) for ch in color) + (alpha,)

    write_texture(name, 16, 16, pixel)


def tex_ref(name):
    TEXTURES_USED.add(name)
    return f"beautifuldecor:block/{name}"


def emit_block(name, es, en, textures, elements, render_type=None, particle=None):
    """Modelo de bloque + ítem + blockstate (4 orientaciones) + loot table."""
    tex = {k: tex_ref(v) for k, v in textures.items()}
    tex["particle"] = tex_ref(particle or next(iter(textures.values())))
    model = {"parent": "minecraft:block/block"}
    if render_type:
        model["render_type"] = render_type
    model["textures"] = tex
    model["elements"] = elements
    wj(ASSETS / f"models/block/{name}.json", model)
    wj(ASSETS / f"models/item/{name}.json", {"parent": f"beautifuldecor:block/{name}"})
    wj(ASSETS / f"blockstates/{name}.json", {"variants": {
        f"facing={f}": ({"model": f"beautifuldecor:block/{name}", "y": y} if y else
                        {"model": f"beautifuldecor:block/{name}"}) for f, y in FACINGS.items()}})
    write_loot(name)
    BLOCKS[name] = {"es": es, "en": en}


def write_loot(name, px0=False):
    pool = {
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{name}"}],
        "conditions": [],
    }
    if px0:
        pool["conditions"].append({"condition": "minecraft:block_state_property",
                                   "block": f"beautifuldecor:{name}", "properties": {"px": "0"}})
    pool["conditions"].append({"condition": "minecraft:survives_explosion"})
    wj(DATA / f"loot_tables/blocks/{name}.json", {"type": "minecraft:block", "pools": [pool]})


# --------------------------------------------------------------------------- TIMBRES
DOORBELLS = {
    # color: (placa, botón, ES, EN)
    "white": ((242, 242, 244), (200, 202, 208), "Timbre blanco", "White Doorbell"),
    "light_gray": ((176, 178, 184), (236, 236, 240), "Timbre gris claro", "Light Gray Doorbell"),
    "dark_gray": ((70, 72, 78), (126, 128, 136), "Timbre gris oscuro", "Dark Gray Doorbell"),
}


def make_doorbells():
    solid("doorbell_led", (255, 170, 40), noise=0)
    for key, (plate, button, es, en) in DOORBELLS.items():
        solid(f"doorbell_{key}_plate", plate, noise=2)
        solid(f"doorbell_{key}_button", button, noise=2)
        els = [E((5.5, 4, 14.5), (10.5, 12, 16), "#plate")]
        els += raster("z", (8, 7.2), 1.8, 13.6, 14.5, "#button")
        els.append(E((7.4, 10.0, 14.2), (8.6, 11.2, 14.5), "#led"))
        emit_block(f"doorbell_{key}", es, en,
                   {"plate": f"doorbell_{key}_plate", "button": f"doorbell_{key}_button",
                    "led": "doorbell_led"}, els, particle=f"doorbell_{key}_plate")


# --------------------------------------------------------------------------- ESCURRIDORES
def make_dish_racks():
    solid("rack_tray", (205, 208, 214), noise=2)
    solid("rack_metal", (150, 154, 160), noise=2)
    solid("plate_white", (240, 240, 238), noise=1)
    solid("plate_cream", (236, 224, 190), noise=1)
    solid("plate_blue", (150, 190, 230), noise=1)

    def wire(x, y):
        if x % 4 == 0 or y % 4 == 0:
            return (140, 144, 150, 255)
        return (0, 0, 0, 0)

    write_texture("rack_wire", 16, 16, wire)

    def base():
        els = [E((1, 0, 3), (15, 0.8, 13), "#tray"),
               E((1, 0.8, 3), (15, 1.8, 3.6), "#tray"),
               E((1, 0.8, 12.4), (15, 1.8, 13), "#tray"),
               E((1, 0.8, 3.6), (1.6, 1.8, 12.4), "#tray"),
               E((14.4, 0.8, 3.6), (15, 1.8, 12.4), "#tray"),
               E((1.6, 5.0, 4.2), (14.4, 5.5, 4.7), "#metal"),
               E((1.6, 5.0, 11.3), (14.4, 5.5, 11.8), "#metal")]
        for i in range(7):
            x = 2.5 + i * 1.9
            wf = {"east": {"texture": "#wire", "uv": [0, 0, 16, 8]},
                  "west": {"texture": "#wire", "uv": [0, 0, 16, 8]}}
            els.append(E((x, 1.8, 4.2), (x + 0.4, 5.5, 11.8), "#metal", wf))
        return els

    textures = {"tray": "rack_tray", "metal": "rack_metal", "wire": "rack_wire"}
    emit_block("dish_rack_empty", "Escurridor de platos (vacío)", "Dish Rack (Empty)",
               textures, base(), render_type="minecraft:cutout", particle="rack_tray")

    els = base()
    plate_tex = ["#p_white", "#p_cream", "#p_blue", "#p_white", "#p_cream", "#p_blue"]
    for i in range(6):
        xc = 2.5 + i * 1.9 + 0.4 + 0.75
        els += raster("x", (8, 5.2), 3.2, xc - 0.25, xc + 0.25, plate_tex[i])
    textures2 = dict(textures, p_white="plate_white", p_cream="plate_cream", p_blue="plate_blue")
    emit_block("dish_rack_plates", "Escurridor de platos (con platos)", "Dish Rack (With Plates)",
               textures2, els, render_type="minecraft:cutout", particle="rack_tray")


# --------------------------------------------------------------------------- CAFETERA
def make_coffee_maker():
    solid("coffee_body", (38, 38, 44), noise=2)
    solid("coffee_silver", (176, 178, 186), noise=2)
    solid("coffee_liquid", (62, 36, 22), noise=2)
    solid("coffee_led", (70, 230, 100), noise=0)
    solid("coffee_glass", (205, 225, 235), noise=0, alpha=85)

    def panel(x, y):
        if 3 <= x <= 12 and 4 <= y <= 11:
            return (90, 150, 110, 255) if (x + y) % 5 else (50, 100, 70, 255)
        return (38, 38, 44, 255)

    write_texture("coffee_panel", 16, 16, panel)

    els = [E((2.5, 0, 3), (13.5, 1.5, 14.5), "#body"),
           E((3.5, 1.5, 10), (12.5, 13.5, 14.5), "#body"),
           E((3.5, 9.5, 4.5), (12.5, 13.5, 10), "#body"),
           E((7, 8.6, 6), (9, 9.5, 8), "#silver"),
           E((10.0, 5.2, 9.9), (12.0, 8.7, 10.0), "#panel"),
           E((10.2, 3.0, 9.8), (11.0, 3.8, 10.0), "#led")]
    c = (8, 6.6)
    els += raster("y", c, 3.2, 1.5, 1.9, "#glass")
    els += raster("y", c, 3.2, 1.9, 7.5, "#glass", r_in=2.6)
    els += raster("y", c, 2.6, 1.9, 5.2, "#liquid")
    els += raster("y", c, 3.3, 7.5, 8.2, "#body")
    els.append(E((7.4, 8.2, 6.0), (8.6, 9.0, 7.2), "#body"))
    els += [E((11.2, 5.8, 6.0), (12.8, 6.6, 7.2), "#body"),
            E((11.2, 2.4, 6.0), (12.8, 3.2, 7.2), "#body"),
            E((12.0, 2.4, 6.0), (12.8, 6.6, 7.2), "#body")]
    emit_block("coffee_maker", "Cafetera", "Coffee Maker",
               {"body": "coffee_body", "silver": "coffee_silver", "liquid": "coffee_liquid",
                "led": "coffee_led", "glass": "coffee_glass", "panel": "coffee_panel"},
               els, render_type="minecraft:translucent", particle="coffee_body")


# --------------------------------------------------------------------------- EXPRIMIDOR
def make_orange_juicer():
    solid("juicer_white", (236, 236, 238), noise=2)
    solid("juicer_gray", (140, 142, 148), noise=2)
    solid("juicer_orange", (244, 138, 24), noise=2)
    solid("juicer_cone", (255, 190, 80), noise=2)
    els = [E((3, 0, 3), (13, 5, 13), "#white"),
           E((7.2, 1.8, 2.6), (8.8, 3.4, 3.0), "#gray")]
    c = (8, 8)
    els += raster("y", c, 4.0, 5.0, 5.8, "#orange")
    els += raster("y", c, 4.6, 5.0, 8.2, "#orange", r_in=4.0)
    els.append(E((6.7, 5.4, 2.2), (9.3, 6.2, 3.8), "#orange"))
    els += raster("y", c, 2.6, 5.8, 7.0, "#cone")
    els += raster("y", c, 1.8, 7.0, 8.4, "#cone")
    els += raster("y", c, 1.0, 8.4, 9.6, "#cone")
    emit_block("orange_juicer", "Exprimidor de naranjas", "Orange Juicer",
               {"white": "juicer_white", "gray": "juicer_gray", "orange": "juicer_orange",
                "cone": "juicer_cone"}, els, particle="juicer_white")


# --------------------------------------------------------------------------- SARTENES Y TAPAS
PANS = {
    # clave: (exterior, interior, mango, ES, EN)
    "cast_iron": ((40, 40, 44), (26, 26, 28), (52, 52, 56), "Sartén de hierro fundido", "Cast Iron Frying Pan"),
    "stainless": ((196, 199, 205), (172, 175, 182), (140, 142, 148), "Sartén de acero inoxidable",
                  "Stainless Steel Frying Pan"),
    "copper": ((190, 112, 62), (204, 204, 210), (70, 50, 40), "Sartén de cobre", "Copper Frying Pan"),
    "nonstick": ((84, 86, 96), (18, 18, 20), (20, 20, 22), "Sartén antiadherente", "Nonstick Frying Pan"),
    "red": ((178, 30, 38), (236, 232, 222), (30, 30, 32), "Sartén esmaltada roja", "Red Enamel Frying Pan"),
}
LIDS = {
    # clave: (tapa, aro, pomo, ES, EN, translúcida)
    "glass": ((205, 228, 238), (150, 152, 158), (22, 22, 24), "Tapa de vidrio", "Glass Pan Lid", True),
    "stainless": ((196, 199, 205), (150, 152, 158), (22, 22, 24), "Tapa de acero inoxidable",
                  "Stainless Steel Pan Lid", False),
    "cast_iron": ((40, 40, 44), (30, 30, 33), (60, 60, 64), "Tapa de hierro fundido", "Cast Iron Pan Lid", False),
    "copper": ((190, 112, 62), (150, 90, 50), (60, 44, 36), "Tapa de cobre", "Copper Pan Lid", False),
    "red": ((178, 30, 38), (60, 60, 64), (30, 30, 32), "Tapa esmaltada roja", "Red Enamel Pan Lid", False),
}


def make_pans_and_lids():
    for key, (outer, inner, handle, es, en) in PANS.items():
        solid(f"pan_{key}_outer", outer, noise=2)
        solid(f"pan_{key}_inner", inner, noise=2)
        solid(f"pan_{key}_handle", handle, noise=2)
        c, R = (7, 8), 5.0
        els = raster("y", c, R - 1.0, 0, 0.8, "#inner", bottom="#outer")
        els += raster("y", c, R, 0, 2.4, "#outer", r_in=R - 0.5)
        els += raster("y", c, R - 0.5, 0.8, 2.4, "#inner", r_in=R - 1.0)
        els.append(E((11.4, 0.9, 7.4), (13.2, 1.7, 8.6), "#handle"))
        els.append(E((13.2, 0.9, 7.2), (16, 2.0, 8.8), "#handle"))
        emit_block(f"frying_pan_{key}", es, en,
                   {"outer": f"pan_{key}_outer", "inner": f"pan_{key}_inner",
                    "handle": f"pan_{key}_handle"}, els, particle=f"pan_{key}_outer")

    for key, (lid, rim, knob, es, en, glass) in LIDS.items():
        solid(f"lid_{key}_top", lid, noise=0 if glass else 2, alpha=95 if glass else 255)
        solid(f"lid_{key}_rim", rim, noise=2)
        solid(f"lid_{key}_knob", knob, noise=2)
        c = (8, 8)
        els = raster("y", c, 5.5, 0, 0.6, "#rim")
        els += raster("y", c, 5.0, 0.6, 1.3, "#lid")
        els += raster("y", c, 3.9, 1.3, 2.0, "#lid")
        els += raster("y", c, 2.6, 2.0, 2.6, "#lid")
        els += [E((7.2, 2.6, 7.2), (8.8, 3.4, 8.8), "#knob"),
                E((6.6, 3.4, 6.6), (9.4, 4.3, 9.4), "#knob")]
        emit_block(f"pan_lid_{key}", es, en,
                   {"lid": f"lid_{key}_top", "rim": f"lid_{key}_rim", "knob": f"lid_{key}_knob"},
                   els, render_type="minecraft:translucent" if glass else None,
                   particle=f"lid_{key}_rim")


# --------------------------------------------------------------------------- PIANO
KEY_LINES = [2, 5, 7, 9, 11, 14]
BLACK_KEY_BOUNDARIES = [1, 2, 4, 5, 6]   # entre do-re, re-mi, fa-sol, sol-la, la-si


def make_piano_textures():
    solid("piano_wood", (26, 24, 29), noise=2)
    solid("piano_black_key", (14, 14, 17), noise=1)
    solid("piano_gold", (214, 176, 56), noise=3)

    def keys(x, y):
        if x in KEY_LINES:
            return (70, 68, 70, 255)
        return (242, 240, 232, 255)

    write_texture("piano_keys", 16, 16, keys)

    img = Image.new("RGBA", (32, 16))
    px = img.load()
    rnd = random.Random("piano_front")
    for y in range(16):
        for x in range(32):
            d = rnd.randint(-2, 2)
            px[x, y] = (26 + d, 24 + d, 29 + d, 255)
    for y in range(2, 6):          # partitura (la parte visible del frente son las filas 1..6)
        for x in range(4, 28):
            px[x, y] = (238, 235, 225, 255)
    for x in range(4, 28):
        px[x, 3] = (110, 108, 104, 255)
        px[x, 5] = (110, 108, 104, 255)
    for x in range(6, 27, 3):
        px[x, rnd.choice((3, 5))] = (20, 20, 22, 255)
    px[15, 2] = px[15, 3] = px[15, 4] = px[15, 5] = (170, 166, 156, 255)
    px[16, 2] = px[16, 3] = px[16, 4] = px[16, 5] = (170, 166, 156, 255)
    img.crop((0, 0, 16, 16)).save(ASSETS / "textures/block/piano_front_0.png")
    img.crop((16, 0, 32, 16)).save(ASSETS / "textures/block/piano_front_1.png")


def piano_half(half, front_tex):
    """Elementos de una mitad del piano (x local 0..16, mirando al norte). half 0 = izquierda (bloque
    donde se coloca), half 1 = derecha. Altura total: 10 píxeles (antes 16): el piano es más bajo y
    pensado para ir sobre el soporte (piano_stand)."""
    els = [
        E((0, 0, 4.5), (16, 1.5, 15.5), "#wood"),                          # base
        E((0, 1.5, 4), (16, 4, 16), "#wood"),                              # cuerpo inferior
        E((0, 4, 2), (16, 5, 16), "#wood"),                                # tapa del teclado
        E((0, 5, 2.5), (16, 5.8, 7.5), "#keys",
          {"up": {"texture": "#keys", "uv": [0, 0, 16, 5]},
           "north": {"texture": "#keys", "uv": [0, 0, 16, 1]}}),          # teclas blancas
        E((0, 5, 7.5), (16, 9, 16), "#wood",
          {"north": {"texture": front_tex, "uv": [0, 2, 16, 6]}}),         # mueble superior + partitura
        E((0, 9, 6.5), (16, 10, 16), "#wood"),                             # tapa superior
    ]
    for i in BLACK_KEY_BOUNDARIES:
        b = 16 - i * 16 / 7
        els.append(E((b - 0.6, 5.8, 4), (b + 0.6, 6.9, 7.5), "#black"))
    if half == 0:
        els.append(E((1, 0.4, 2.8), (3, 1.2, 4.5), "#gold"))
    else:
        els.append(E((13, 0.4, 2.8), (15, 1.2, 4.5), "#gold"))
    return els


def piano_stand_half(half):
    """Soporte de piano: mismo ancho (2 bloques), fondo y altura (16) que el piano original. Tablero arriba,
    cuatro patas en los extremos, repisa baja y topes dorados. half 0 = origen, half 1 = la otra mitad;
    el extremo exterior es x=16 en la mitad 0 y x=0 en la mitad 1."""
    outer = 16 if half == 0 else 0
    sgn = -1 if half == 0 else 1          # hacia dentro del bloque desde el extremo exterior
    leg_a, leg_b = sorted((outer + sgn * 0.5, outer + sgn * 3.0))
    els = [
        E((0, 13, 2), (16, 16, 16), "#wood"),                              # tablero
        E((0, 11, 3), (16, 13, 15), "#wood"),                              # faldón bajo el tablero
    ]
    for z1, z2 in ((3, 5.5), (12.5, 15)):                                  # patas delantera y trasera
        els.append(E((leg_a, 0.6, z1), (leg_b, 11, z2), "#wood"))
        els.append(E((leg_a - 0.2, 0, z1 - 0.2), (leg_b + 0.2, 0.6, z2 + 0.2), "#gold"))
    els.append(E((leg_a, 7.5, 5.5), (leg_b, 8.7, 12.5), "#wood"))          # travesaño lateral
    shelf = (0, 15.5) if half == 0 else (0.5, 16)
    els.append(E((shelf[0], 3, 4), (shelf[1], 4.5, 14), "#wood"))          # repisa baja
    return els


def make_piano(textures=True):
    if textures:
        make_piano_textures()
    textures = {"wood": tex_ref("piano_wood"), "keys": tex_ref("piano_keys"),
                "black": tex_ref("piano_black_key"), "gold": tex_ref("piano_gold"),
                "particle": tex_ref("piano_wood")}
    for half, side in ((0, "left"), (1, "right")):
        t = dict(textures, front=tex_ref(f"piano_front_{half}"))
        wj(ASSETS / f"models/block/piano_{side}.json", {
            "parent": "minecraft:block/block", "textures": t,
            "elements": piano_half(half, "#front")})
    # ítem: las dos mitades juntas, centradas (mitad 0 a la izquierda del espectador = +x)
    items = []
    for half in (0, 1):
        shift = (16 if half == 0 else 0) - 8
        for e in piano_half(half, f"#front{half}"):
            e["from"][0] = r(e["from"][0] + shift)
            e["to"][0] = r(e["to"][0] + shift)
            items.append(e)
    t = dict(textures, front0=tex_ref("piano_front_0"), front1=tex_ref("piano_front_1"))
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 1.4, 0], "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.22, 0.22, 0.22]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                  "scale": [0.2, 0.2, 0.2]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                  "scale": [0.28, 0.28, 0.28]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                 "scale": [0.28, 0.28, 0.28]},
    }
    wj(ASSETS / "models/block/piano_item.json", {
        "parent": "minecraft:block/block", "textures": t, "elements": items, "display": display})
    wj(ASSETS / "models/item/piano.json", {"parent": "beautifuldecor:block/piano_item"})

    variants = []
    for f, y in FACINGS.items():
        for px_val, side in ((0, "left"), (1, "right")):
            apply = {"model": f"beautifuldecor:block/piano_{side}"}
            if y:
                apply["y"] = y
            variants.append({"when": {"facing": f, "px": str(px_val)}, "apply": apply})
    wj(ASSETS / "blockstates/piano.json", {"multipart": variants})
    write_loot("piano", px0=True)
    BLOCKS["piano"] = {"es": "Piano", "en": "Piano"}


def make_piano_stand():
    textures = {"wood": tex_ref("piano_wood"), "gold": tex_ref("piano_gold"), "particle": tex_ref("piano_wood")}
    for half, side in ((0, "left"), (1, "right")):
        wj(ASSETS / f"models/block/piano_stand_{side}.json", {
            "parent": "minecraft:block/block", "textures": dict(textures),
            "elements": piano_stand_half(half)})
    items = []
    for half in (0, 1):
        shift = (16 if half == 0 else 0) - 8
        for e in piano_stand_half(half):
            e["from"][0] = r(e["from"][0] + shift)
            e["to"][0] = r(e["to"][0] + shift)
            items.append(e)
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.22, 0.22, 0.22]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                  "scale": [0.2, 0.2, 0.2]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                  "scale": [0.28, 0.28, 0.28]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                 "scale": [0.28, 0.28, 0.28]},
    }
    wj(ASSETS / "models/block/piano_stand_item.json", {
        "parent": "minecraft:block/block", "textures": dict(textures), "elements": items, "display": display})
    wj(ASSETS / "models/item/piano_stand.json", {"parent": "beautifuldecor:block/piano_stand_item"})
    variants = []
    for f, y in FACINGS.items():
        for px_val, side in ((0, "left"), (1, "right")):
            apply = {"model": f"beautifuldecor:block/piano_stand_{side}"}
            if y:
                apply["y"] = y
            variants.append({"when": {"facing": f, "px": str(px_val)}, "apply": apply})
    wj(ASSETS / "blockstates/piano_stand.json", {"multipart": variants})
    write_loot("piano_stand", px0=True)
    BLOCKS["piano_stand"] = {"es": "Soporte de piano", "en": "Piano Stand"}


# --------------------------------------------------------------------------- SONIDOS
NOTES = [("do", 261.63), ("re", 293.66), ("mi", 329.63), ("fa", 349.23),
         ("sol", 392.00), ("la", 440.00), ("si", 493.88)]


def synth_piano(freq, dur=2.8, sr=44100):
    t = np.arange(int(dur * sr)) / sr
    rng = np.random.default_rng(int(freq * 100))
    sig = np.zeros_like(t)
    inharm = 0.00035
    for n in range(1, 16):
        f = n * freq * math.sqrt(1 + inharm * n * n)
        if f > 11000:
            break
        amp = 1.0 / (n ** 1.15)
        a = 0.45 + 0.13 * n
        env = 0.6 * np.exp(-t * a * 4.0) + 0.4 * np.exp(-t * a)
        for detune in (-0.0009, 0.0, 0.0010):
            ph = rng.uniform(0, 2 * math.pi)
            sig += amp * env * np.sin(2 * math.pi * f * (1 + detune) * t + ph) / 3.0
    sig *= 1 - np.exp(-t / 0.004)
    thump = rng.normal(0, 1, len(t)) * np.exp(-t / 0.012)
    k = np.ones(24) / 24
    sig += 0.05 * np.convolve(thump, k, mode="same")
    fade = np.clip((dur - t) / 0.15, 0, 1)
    sig *= fade
    sig = sig / np.max(np.abs(sig)) * 0.85
    return sr, (sig * 32767).astype(np.int16)


def to_ogg(src, dst, mono=True):
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(src)]
    if mono:
        cmd += ["-ac", "1"]
    cmd += ["-c:a", "libvorbis", "-q:a", "5", str(dst)]
    subprocess.run(cmd, check=True)


def make_sounds():
    snd = ASSETS / "sounds"
    snd.mkdir(parents=True, exist_ok=True)
    to_ogg(SOURCE_DOORBELL, snd / "doorbell.ogg", mono=True)   # mono = atenúa con la distancia
    with tempfile.TemporaryDirectory() as tmp:
        for name, freq in NOTES:
            sr, data = synth_piano(freq)
            wav = Path(tmp) / f"piano_{name}.wav"
            with wave.open(str(wav), "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(sr)
                w.writeframes(data.tobytes())
            to_ogg(wav, snd / f"piano_{name}.ogg", mono=True)

    path = ASSETS / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    sounds["doorbell"] = {"subtitle": "subtitles.beautifuldecor.doorbell",
                          "sounds": [{"name": "beautifuldecor:doorbell", "volume": 1.0}]}
    for name, _ in NOTES:
        sounds[f"piano_{name}"] = {"subtitle": "subtitles.beautifuldecor.piano",
                                   "sounds": [{"name": f"beautifuldecor:piano_{name}", "volume": 1.0}]}
    wj(path, sounds)


# --------------------------------------------------------------------------- IDIOMAS
def write_lang():
    extra = {
        "es_es": {"subtitles.beautifuldecor.doorbell": "Suena un timbre",
                  "subtitles.beautifuldecor.piano": "Suena una nota de piano"},
        "en_us": {"subtitles.beautifuldecor.doorbell": "Doorbell rings",
                  "subtitles.beautifuldecor.piano": "Piano note plays"},
    }
    for lang, key in (("es_es", "es"), ("en_us", "en")):
        path = ASSETS / f"lang/{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        for name, names in BLOCKS.items():
            data[f"block.beautifuldecor.{name}"] = names[key]
            data[f"item.beautifuldecor.{name}"] = names[key]
        data.update(extra[lang])
        wj(path, data)


# --------------------------------------------------------------------------- COMPROBACIÓN
def verify():
    errors = []
    for t in sorted(TEXTURES_USED):
        if not (ASSETS / f"textures/block/{t}.png").exists():
            errors.append(f"textura inexistente: {t}")
    for name in BLOCKS:
        for p in (ASSETS / f"models/block/{name}.json" if name not in ("piano", "piano_stand")
                  else ASSETS / f"models/block/{name}_left.json",
                  ASSETS / f"models/item/{name}.json", ASSETS / f"blockstates/{name}.json",
                  DATA / f"loot_tables/blocks/{name}.json"):
            if not p.exists():
                errors.append(f"falta {p}")
    mine = [ASSETS / f"models/block/{n}.json" for n in BLOCKS if n not in ("piano", "piano_stand")]
    mine += [ASSETS / f"models/block/{n}.json" for n in ("piano_left", "piano_right", "piano_item",
                                                          "piano_stand_left", "piano_stand_right",
                                                          "piano_stand_item")]
    for jf in mine:
        try:
            m = json.loads(jf.read_text(encoding="utf-8"))
        except Exception as ex:
            errors.append(f"JSON inválido {jf}: {ex}")
            continue
        for e in m.get("elements", []):
            for a, b in zip(e["from"], e["to"]):
                if not (-16 <= a <= 32 and -16 <= b <= 32 and a <= b):
                    errors.append(f"caja inválida en {jf.name}: {e['from']} {e['to']}")
                    break
            for face in e["faces"].values():
                tk = face["texture"]
                if tk.startswith("#") and tk[1:] not in m.get("textures", {}) and "parent" in m \
                        and m["parent"] == "minecraft:block/block":
                    errors.append(f"variable de textura sin definir en {jf.name}: {tk}")
    for name, _ in NOTES:
        if not (ASSETS / f"sounds/piano_{name}.ogg").exists():
            errors.append(f"falta sonido piano_{name}")
    if not (ASSETS / "sounds/doorbell.ogg").exists():
        errors.append("falta sonido doorbell")
    return errors


def main():
    make_doorbells()
    make_dish_racks()
    make_coffee_maker()
    make_orange_juicer()
    make_pans_and_lids()
    make_piano()
    make_piano_stand()
    make_sounds()
    write_lang()
    errors = verify()
    print(f"Bloques generados: {len(BLOCKS)}")
    print("\n".join(sorted(BLOCKS)))
    if errors:
        print("\nERRORES:")
        print("\n".join(errors))
        raise SystemExit(1)
    print("\nComprobación OK")


if __name__ == "__main__":
    main()
