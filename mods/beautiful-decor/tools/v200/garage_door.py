#!/usr/bin/env python3
"""Puerta de garaje seccional 3x3 que se abre hacia arriba (v2.0.0): texturas, modelos, blockstate, loot e ícono.

Uso (desde la raíz del proyecto):
  python3 -m tools.v200.garage_door --preview vista.png     # solo mira: dibuja la puerta cerrada/abriendo (no escribe nada)
  python3 -m tools.v200.garage_door --write                 # escribe texturas, modelos, blockstate, loot, ícono y modelo de ítem

Geometría (modelos mirando al norte; el frente es la cara norte):
  part = columna + 3 * fila; la columna 0 es la de la IZQUIERDA vista de frente (lado +x) y crece hacia -x.
  Marco: rieles de 2 px a cada lado (x 14..16 en la columna 0, x 0..2 en la 2) y cajón del motor arriba (y 13..16).
  Hoja: lámina de 2 px (z 7..9) que sube de a 4 px por paso (raise 0..12) y se esconde dentro del cajón.
  La textura de la hoja cubre los 3x3 bloques en 96x96 texels (2 texels por px): 4 secciones de 24 texels.
"""
import argparse
import hashlib
import json
import random
import sys
from pathlib import Path

from PIL import Image

from ..v180 import common as C
from ..v180.common import FACINGS, Model, frame, mix, new, rect, shade, write_json

ROOT = C.ROOT
ASSETS = C.ASSETS
BLOCK_ID = "garage_door"
RAISE_MAX = 12
STEP_PX = 4                      # px de subida por paso
HEADER_BOTTOM = 13               # el cajón ocupa y 13..16 de la fila de arriba
LEAF_Z = (7, 9)
TEX = 96                         # texels de la textura de la hoja (48 px x 2)
Z_JAMB = (3, 13)

RED = (206, 28, 36, 255)
RED_DARK = (148, 18, 26, 255)
BLACK = (14, 14, 17, 255)


# ----------------------------------------------------------------------------------------------- texturas
def leaf_texture(outside):
    """Hoja vista desde fuera (graphite + franja roja) o desde dentro (acero claro, bisagras, rodillos)."""
    rng = random.Random(311 if outside else 312)
    im = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 255))
    px = im.load()
    base = (46, 49, 57) if outside else (120, 126, 137)
    groove = -34 if outside else -46

    for k in range(4):                                   # secciones de arriba (0) a abajo (3)
        y0 = 24 * k
        for y in range(24):
            t = y / 23.0
            d = 10 - 22 * t
            for x in range(TEX):
                sheen = 5 * (1 - abs(x - 48) / 48.0)    # un poco más claro al centro
                px[x, y0 + y] = shade(base, d + sheen + rng.randint(-2, 2))
        if k > 0:                                        # nervaduras prensadas
            for off in (8, 15):
                for x in range(TEX):
                    px[x, y0 + off] = shade(px[x, y0 + off], -18)
                    px[x, y0 + off + 1] = shade(px[x, y0 + off + 1], 12)
        for x in range(TEX):                             # junta entre secciones
            px[x, y0 + 22] = shade(px[x, y0 + 22], groove)
            px[x, y0 + 23] = shade(px[x, y0 + 23], groove - 12)
            if k < 3:
                px[x, y0 + 24] = shade(px[x, y0 + 24], 16)
    # bordes de la hoja un poco más oscuros (los cubren los rieles)
    for y in range(TEX):
        for x in (4, 5, 90, 91):
            px[x, y] = shade(px[x, y], -14)

    # sello de goma inferior
    for x in range(TEX):
        px[x, 91] = RED if outside else shade(base, -60)
        for y in range(92, 96):
            c = BLACK if y != 93 else (30, 30, 34, 255)
            px[x, y] = shade(c, rng.randint(-2, 2))

    if outside:
        for x in range(4, 92):                           # franja roja de carreras en la sección de abajo
            px[x, 75] = RED
            px[x, 76] = RED_DARK
            px[x, 74] = shade(px[x, 74], -22)
        # manija cromada y cerradura
        for x in range(40, 57):
            px[x, 83] = shade(px[x, 83], -26)
        for x in range(40, 57):
            px[x, 80] = (214, 218, 226, 255)
            px[x, 81] = (160, 165, 175, 255)
            px[x, 82] = (98, 102, 112, 255)
        for y in range(80, 83):
            px[39, y] = (50, 52, 58, 255)
            px[57, y] = (50, 52, 58, 255)
        rect(im, 46, 86, 50, 90, (24, 25, 29, 255))
        frame(im, 46, 86, 50, 90, (120, 124, 134, 255))
        px[48, 87] = (200, 204, 212, 255)
        px[48, 88] = (200, 204, 212, 255)
        px[48, 89] = (10, 10, 12, 255)
    else:
        for jy in (22, 46, 70):                          # bisagras y rodillos en cada junta
            for hx in (12, 46, 80):
                rect(im, hx, jy - 3, hx + 7, jy + 3, (74, 78, 88, 255))
                frame(im, hx, jy - 3, hx + 7, jy + 3, (34, 36, 42, 255))
                px[hx + 2, jy - 1] = (170, 174, 184, 255)
                px[hx + 5, jy + 1] = (170, 174, 184, 255)
            for rx in (7, 89):
                C.disc(im, rx, jy, 2.6, (40, 42, 48, 255))
                C.disc(im, rx, jy, 1.3, (176, 180, 190, 255))

    # ventanas de la sección de arriba (5), solo visibles por debajo del cajón
    wx = [8, 25, 42, 59, 76]
    for x0 in wx:
        y0, w, h = 9, 13, 10
        frame_c = (15, 16, 19, 255) if outside else (66, 70, 80, 255)
        rect(im, x0 - 1, y0 - 1, x0 + w, y0 + h, frame_c)
        for yy in range(h):
            t = yy / (h - 1.0)
            for xx in range(w):
                if outside:
                    c = mix((52, 80, 108), (12, 20, 32), t)
                else:
                    c = mix((182, 218, 240), (110, 162, 206), t)
                d = xx + yy * 1.4
                if 3.5 <= d % 14 < 6.5:
                    c = shade(c, 44 if outside else 22)
                px[x0 + xx, y0 + yy] = c
        for xx in range(-1, w + 1):                      # alféizar
            px[x0 + xx, y0 + h + 1] = shade(px[x0 + xx, y0 + h + 1], 20)
    return im


def header_texture():
    """Frente del cajón del motor (96x6): 3 bloques de ancho, 3 px de alto."""
    rng = random.Random(321)
    im = Image.new("RGBA", (96, 6), (0, 0, 0, 255))
    px = im.load()
    for y in range(6):
        for x in range(96):
            t = y / 5.0
            c = mix((54, 57, 66), (26, 28, 33), t)
            px[x, y] = shade(c, rng.randint(-2, 2) + (12 if y == 0 else 0))
    for x in range(96):
        px[x, 4] = RED
        px[x, 5] = BLACK
    for sx in (31, 63):
        for y in range(4):
            px[sx, y] = shade(px[sx, y], -26)
            px[sx + 1, y] = shade(px[sx + 1, y], 10)
    for x in range(10, 28, 3):                           # rejillas de ventilación del motor
        for y in range(1, 4):
            px[x, y] = (12, 13, 16, 255)
    for x in range(88, 91):                              # luz indicadora
        for y in range(1, 3):
            px[x, y] = (255, 70, 70, 255) if (x, y) != (88, 1) else (255, 190, 190, 255)
    return im


def frame_texture(seed=331, base=(30, 32, 37)):
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()
    for y in range(16):
        row = rng.randint(-2, 2)
        for x in range(16):
            px[x, y] = shade(base + (255,), row + rng.randint(-2, 2))
    return im


def jamb_texture():
    im = frame_texture(332, (28, 30, 35))
    px = im.load()
    for y in range(16):
        px[0, y] = (22, 24, 28, 255)
        px[1, y] = RED if y % 16 not in (0, 15) else RED_DARK
    return im


def seal_texture():
    rng = random.Random(341)
    im = Image.new("RGBA", (16, 16), BLACK)
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(BLACK if y % 4 else (28, 28, 32, 255), rng.randint(-2, 2))
    return im


TEXTURES = {
    "gd_door_out": lambda: leaf_texture(True),
    "gd_door_in": lambda: leaf_texture(False),
    "gd_header": header_texture,
    "gd_frame": frame_texture,
    "gd_jamb": jamb_texture,
    "gd_seal": seal_texture,
}


# ----------------------------------------------------------------------------------------------- modelos
def leaf_x_range(col):
    return {0: (0, 14), 1: (0, 16), 2: (2, 16)}[col]


def fx_range(col, xa, xb):
    """Coordenada horizontal del marco (0..48 de izquierda a derecha vista de frente) de x en [xa, xb] del bloque."""
    return 16 * col + 16 - xb, 16 * col + 16 - xa


def new_model():
    m = Model()
    for key, name in (("door", "gd_door_out"), ("dooin", "gd_door_in"), ("hdr", "gd_header"),
                      ("frame", "gd_frame"), ("jamb", "gd_jamb"), ("seal", "gd_seal")):
        m.tex(key, name)
    return m


def build_part(part, raise_):
    col, row = part % 3, part // 3
    m = new_model()
    top = HEADER_BOTTOM if row == 2 else 16

    # rieles laterales
    if col in (0, 2):
        xa, xb = (14, 16) if col == 0 else (0, 2)
        outer = "east" if col == 0 else "west"
        inner = "west" if col == 0 else "east"
        ufront = [0, 0, 2, 16] if col == 0 else [2, 0, 0, 16]
        uback = [2, 0, 0, 16] if col == 0 else [0, 0, 2, 16]
        faces = {"north": ("#jamb", ufront), "south": ("#jamb", uback), outer: "#frame", inner: "#frame"}
        if row == 0:
            faces["down"] = "#frame"
        m.box([xa, 0, Z_JAMB[0]], [xb, top, Z_JAMB[1]], None, **faces)

    # cajón del motor (fila de arriba)
    if row == 2:
        fx0, fx1 = fx_range(col, 0, 16)
        faces = {"north": ("#hdr", [fx0 / 3.0, 0, fx1 / 3.0, 16]),
                 "south": ("#hdr", [(48 - fx1) / 3.0, 0, (48 - fx0) / 3.0, 16]),
                 "up": "#frame", "down": "#frame"}
        if col == 0:
            faces["east"] = "#frame"
        if col == 2:
            faces["west"] = "#frame"
        m.box([0, HEADER_BOTTOM, Z_JAMB[0]], [16, 16, Z_JAMB[1]], None, **faces)

    # hoja
    yb = STEP_PX * raise_
    y_lo = max(16 * row, yb) - 16 * row
    if y_lo < top:
        xa, xb = leaf_x_range(col)
        yd0, yd1 = 16 * row + y_lo, 16 * row + top
        s0, s1 = yd0 - yb, yd1 - yb
        fx0, fx1 = fx_range(col, xa, xb)
        faces = {
            "north": ("#door", [fx0 / 3.0, (48 - s1) / 3.0, fx1 / 3.0, (48 - s0) / 3.0]),
            "south": ("#dooin", [(48 - fx1) / 3.0, (48 - s1) / 3.0, (48 - fx0) / 3.0, (48 - s0) / 3.0]),
        }
        if yb > 0 and 16 * row <= yb < 16 * row + 16:
            faces["down"] = "#seal"
        m.box([xa, y_lo, LEAF_Z[0]], [xb, top, LEAF_Z[1]], None, **faces)
    return m


def item_model():
    """Miniatura 1/3 para el ícono (la puerta real no cabe en los 16 px de un modelo de ítem)."""
    m = new_model()
    k = 1.0 / 3.0
    m.box([16 - 2 * k, 0, 5.6], [16, 16 - k, 10.4], None, north=("#jamb", [0, 0, 2, 16]),
          south=("#jamb", [2, 0, 0, 16]), east="#frame", west="#frame", up="#frame")
    m.box([0, 0, 5.6], [2 * k, 16 - k, 10.4], None, north=("#jamb", [2, 0, 0, 16]),
          south=("#jamb", [0, 0, 2, 16]), east="#frame", west="#frame", up="#frame")
    m.box([0, 16 - k, 5.0], [16, 16, 11.0], None, north=("#hdr", [0, 0, 16, 16]), south=("#hdr", [0, 0, 16, 16]),
          up="#frame", east="#frame", west="#frame", down="#frame")
    m.box([2 * k, 0, 7.2], [16 - 2 * k, 16 - k, 8.8], None,
          north=("#door", [2 * k, 1, 16 - 2 * k, 16]), south=("#dooin", [2 * k, 1, 16 - 2 * k, 16]))
    return m


def model_name(part, raise_):
    return f"garage_door_p{part}_r{raise_}"


def write_models():
    """Escribe los modelos (sin duplicados) y devuelve {(part, raise): nombre}."""
    seen, names = {}, {}
    for part in range(9):
        for r in range(RAISE_MAX + 1):
            data = build_part(part, r).to_json("gd_frame")
            key = hashlib.sha1(json.dumps(data, sort_keys=True).encode()).hexdigest()
            if key not in seen:
                seen[key] = model_name(part, r)
                write_json(C.MODELS / f"{seen[key]}.json", data)
            names[(part, r)] = seen[key]
    return names


def write_blockstate(names):
    parts = []
    for facing, rot in FACINGS.items():
        for part in range(9):
            for r in range(RAISE_MAX + 1):
                apply = {"model": f"beautifuldecor:block/{names[(part, r)]}"}
                if rot:
                    apply["y"] = rot
                parts.append({"when": {"facing": facing, "part": str(part), "raise": str(r)}, "apply": apply})
    write_json(C.STATES / f"{BLOCK_ID}.json", {"multipart": parts})


def generate():
    for name, fn in TEXTURES.items():
        C.save_tex(name, fn())
    names = write_models()
    item_model().save("garage_door_item", "gd_frame")
    write_blockstate(names)
    C.write_loot_part0(BLOCK_ID)
    return names


# ----------------------------------------------------------------------------------------------- icono y vistas
def make_icon():
    import numpy as np
    from ..v182.icon_render import render_icon
    icon = render_icon("beautifuldecor:block/garage_door_item", view=(156.0, 12.0))
    if icon is None:
        return None
    # la puerta es oscura: se aclara el ícono (solo el ícono) para que se lea bien sobre el fondo gris del inventario
    a = np.asarray(icon).astype(np.float32)
    rgb = 255.0 * (a[..., :3] / 255.0) ** 0.62
    lum = rgb.mean(axis=2, keepdims=True)
    rgb = np.clip(lum + (rgb - lum) * 1.25, 0, 255)             # un poco más de color (el rojo)
    a[..., :3] = np.where(a[..., 3:4] > 0, rgb, a[..., :3])
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def scene_model(raise_):
    """Los 9 modelos juntos (cada pieza movida a su sitio) para dibujar la puerta entera."""
    elements, textures = [], {}
    for part in range(9):
        col, row = part % 3, part // 3
        data = build_part(part, raise_).to_json("gd_frame")
        textures.update(data["textures"])
        for el in data["elements"]:
            el = json.loads(json.dumps(el))
            el["from"][0] -= 16 * col
            el["to"][0] -= 16 * col
            el["from"][1] += 16 * row
            el["to"][1] += 16 * row
            elements.append(el)
    return {"textures": textures, "elements": elements}


def render_big(model, view, size=300):
    from ..v182 import icon_render as R
    old = (R.ICON, R.INNER, R.SS, R.CAP)
    R.ICON, R.INNER, R.SS, R.CAP = size, size - 4, 3, 99.0
    try:
        return R.render_icon(model, view=view)
    finally:
        R.ICON, R.INNER, R.SS, R.CAP = old


def preview(path):
    generate_preview_assets = generate()          # el render lee las texturas/modelos del proyecto
    tiles = []
    for r, view in ((0, (158.0, 12.0)), (4, (158.0, 12.0)), (8, (158.0, 12.0)), (12, (158.0, 12.0)),
                    (0, (-28.0, 14.0)), (6, (-28.0, 14.0))):
        tiles.append(render_big(scene_model(r), view))
    w, h = tiles[0].size
    sheet = Image.new("RGBA", (w * 3, h * 2), (78, 86, 98, 255))
    for i, t in enumerate(tiles):
        sheet.alpha_composite(t, ((i % 3) * w, (i // 3) * h))
    sheet.convert("RGB").save(path)
    icon = make_icon()
    big = icon.resize((icon.width * 8, icon.height * 8), Image.NEAREST)
    bg = Image.new("RGBA", big.size, (139, 139, 139, 255))
    bg.alpha_composite(big)
    out = Path(path)
    bg.convert("RGB").save(out.with_name("icon_" + out.name))
    print("vista previa:", path)
    return generate_preview_assets


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="hoja de vistas aquí (escribe los recursos del bloque, pero no el ícono)")
    ap.add_argument("--write", action="store_true")
    a = ap.parse_args()
    if a.preview:
        preview(a.preview)
    if a.write:
        generate()
        icon = make_icon()
        if icon is None:
            sys.exit("no se pudo dibujar el ícono")
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item" / f"{BLOCK_ID}.png")
        write_json(ASSETS / "models/item" / f"{BLOCK_ID}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{BLOCK_ID}"}})
        src = ROOT / "tools/v182/item_models_src.json"
        data = json.loads(src.read_text(encoding="utf-8"))
        data[BLOCK_ID] = {"parent": "beautifuldecor:block/garage_door_item"}
        src.write_text(json.dumps(dict(sorted(data.items())), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print("escrito: texturas, modelos, blockstate, loot, ícono, modelo de ítem")


if __name__ == "__main__":
    main()
