#!/usr/bin/env python3
"""Genera los assets del paquete "casa y calle" (v1.2.0):

  * Ventiladores de techo (clic = encender/apagar, aspas animadas)
  * Camas matrimoniales 2x2 (un color y combinaciones de dos colores)
  * Estanterías de madera y metal
  * Señal de tránsito "prohibido camiones"

Escribe modelos, blockstates, modelos de ítem, texturas, loot tables y entradas de idioma.
Los nombres tienen que coincidir con los registrados en ModBlocks.java
(el script termina con una comprobación cruzada, ver tools/verify_home_and_street_pack.py).
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
MODELS = ASSETS / "models/block"
ITEMS = ASSETS / "models/item"
STATES = ASSETS / "blockstates"
TEX = ASSETS / "textures/block"
LOOT = DATA / "loot_tables/blocks"
LANG = ASSETS / "lang"

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("north", "south", "east", "west", "up", "down")

DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
ES_COLOR = {"white": "blanca", "orange": "naranja", "magenta": "magenta", "light_blue": "celeste",
            "yellow": "amarilla", "lime": "lima", "pink": "rosa", "gray": "gris",
            "light_gray": "gris claro", "cyan": "cian", "purple": "morada", "blue": "azul",
            "brown": "marrón", "green": "verde", "red": "roja", "black": "negra"}
EN_COLOR = {c: c.replace("_", " ").title() for c in DYES}

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
         "bamboo", "crimson", "warped"]
ES_WOOD = {"oak": "roble", "spruce": "abeto", "birch": "abedul", "jungle": "jungla",
           "acacia": "acacia", "dark_oak": "roble oscuro", "mangrove": "mangle",
           "cherry": "cerezo", "bamboo": "bambú", "crimson": "carmesí", "warped": "distorsionada"}
EN_WOOD = {w: w.replace("_", " ").title() for w in WOODS}

# (manta, sábanas/almohadas)
BED_COMBOS = [("red", "white"), ("blue", "white"), ("green", "white"), ("black", "white"),
              ("gray", "white"), ("pink", "white"), ("purple", "white"), ("light_blue", "white"),
              ("black", "red"), ("blue", "yellow"), ("red", "black"), ("gray", "light_gray"),
              ("lime", "green"), ("orange", "brown"), ("cyan", "blue"), ("magenta", "purple")]

METAL_SHELVES = {  # nombre -> (textura vanilla, inglés, español)
    "iron": ("minecraft:block/iron_block", "Iron", "de hierro"),
    "black": ("minecraft:block/netherite_block", "Black Steel", "de acero negro"),
    "gold": ("minecraft:block/gold_block", "Gold", "dorada"),
    "copper": ("minecraft:block/copper_block", "Copper", "de cobre"),
}


CEILING_FANS = {  # nombre -> (carcasa, varilla, color aspa, borde aspa, inglés, español)
    "wood": ("minecraft:block/dark_oak_planks", "minecraft:block/gray_concrete",
             (156, 106, 60), (96, 62, 32), "Wooden", "de madera"),
    "white": ("minecraft:block/white_concrete", "minecraft:block/light_gray_concrete",
              (240, 240, 240), (170, 170, 175), "White", "blanco"),
    "black": ("minecraft:block/black_concrete", "minecraft:block/gray_concrete",
              (44, 44, 50), (90, 90, 100), "Black", "negro"),
}

lang_en = {}
lang_es = {}


# ---------------------------------------------------------------- utilidades
def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def box(frm, to, tex, faces=FACES, face_tex=None, uv_override=None, rotation=None):
    """Cubo con UV proporcionales (siempre dentro de 0..16 para no salirse de la textura)."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h),
             "up": (w, d), "down": (w, d)}
    out = {}
    for face in faces:
        fw, fh = sizes[face]
        t = (face_tex or {}).get(face, tex)
        uv = (uv_override or {}).get(face, [0, 0, round(min(fw, 16), 3), round(min(fh, 16), 3)])
        out[face] = {"uv": uv, "texture": t}
    element = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": out}
    if rotation:
        element["rotation"] = rotation
    return element


def y_variants(model_for, extra=None):
    """Variantes de blockstate para las 4 orientaciones. model_for(facing)->str|None."""
    variants = {}
    for facing, rot in FACINGS.items():
        key = f"facing={facing}"
        entry = {"model": model_for(facing)}
        if rot:
            entry["y"] = rot
        variants[key] = entry
    return variants


def simple_loot(name, conditions=None):
    pool = {
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{name}"}],
        "conditions": (conditions or []) + [{"condition": "minecraft:survives_explosion"}],
    }
    write_json(LOOT / f"{name}.json", {"type": "minecraft:block", "pools": [pool]})


def register_names(name, en, es):
    lang_en[f"block.beautifuldecor.{name}"] = en
    lang_es[f"block.beautifuldecor.{name}"] = es


# ---------------------------------------------------------------- 1) VENTILADORES DE TECHO
BLADE_FRAMES = 6  # 4 aspas => simetría de 90°, 6 fotogramas de 15°
BLADE_SIZE = 32


def draw_blades(angle_deg, fill, edge):
    ss = 8
    size = BLADE_SIZE * ss
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = size / 2
    for k in range(4):
        a = math.radians(angle_deg + 90 * k)
        # aspa: paleta ancha de punta redondeada (r = 3.5 .. 15.6 en px de 32)
        steps = 48
        pts = []
        for i in range(steps + 1):
            t = i / steps
            r = (3.5 + 12.1 * t) * ss
            tip = max(0.0, (t - 0.86) / 0.14)
            half = 2.9 * math.sqrt(max(0.0, 1 - tip * tip)) * ss
            pts.append((r, half))
        poly_local = [(r, h) for r, h in pts] + [(r, -h) for r, h in reversed(pts)]
        poly = [(cx + r * math.cos(a) - h * math.sin(a), cy - (r * math.sin(a) + h * math.cos(a)))
                for r, h in poly_local]
        d.polygon(poly, fill=edge + (255,))
        shrink = []
        for r, h in poly_local:
            h2 = h * 0.62
            r2 = (3.5 + 0.45) * ss + (r - 3.5 * ss) * 0.93
            shrink.append((cx + r2 * math.cos(a) - h2 * math.sin(a),
                           cy - (r2 * math.sin(a) + h2 * math.cos(a))))
        d.polygon(shrink, fill=fill + (255,))
    img = img.resize((BLADE_SIZE, BLADE_SIZE), Image.LANCZOS)
    # evita bordes semitransparentes borrosos (render cutout)
    px = img.load()
    for y in range(BLADE_SIZE):
        for x in range(BLADE_SIZE):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    return img


def gen_ceiling_fans():
    for key, (housing, rod, fill, edge, en, es) in CEILING_FANS.items():
        name = f"ceiling_fan_{key}"
        # texturas: estática (apagado) y tira animada (encendido)
        draw_blades(22, fill, edge).save(TEX / f"{name}_blades_off.png")
        strip = Image.new("RGBA", (BLADE_SIZE, BLADE_SIZE * BLADE_FRAMES), (0, 0, 0, 0))
        for i in range(BLADE_FRAMES):
            strip.paste(draw_blades(22 + i * (90.0 / BLADE_FRAMES), fill, edge), (0, i * BLADE_SIZE))
        strip.save(TEX / f"{name}_blades_on.png")
        write_json(TEX / f"{name}_blades_on.png.mcmeta", {"animation": {"frametime": 1}})

        for state in ("off", "on"):
            textures = {"particle": housing, "housing": housing, "rod": rod,
                        "blades": f"beautifuldecor:block/{name}_blades_{state}"}
            elements = [
                box([6, 15, 6], [10, 16, 10], "#housing"),                      # florón al techo
                box([7.5, 12.4, 7.5], [8.5, 15, 8.5], "#rod"),                  # varilla
                box([5.5, 10.2, 5.5], [10.5, 12.4, 10.5], "#housing"),          # motor
                box([6.5, 9.5, 6.5], [9.5, 10.2, 9.5], "#rod"),                 # tapa inferior
                box([0, 11.2, 0], [16, 11.6, 16], "#blades", faces=("up", "down"),
                    uv_override={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}),
            ]
            write_json(MODELS / f"{name}_{state}.json", {
                "parent": "minecraft:block/block",
                "render_type": "minecraft:cutout",
                "textures": textures,
                "elements": elements,
            })
        write_json(STATES / f"{name}.json", {"variants": {
            "on=false": {"model": f"beautifuldecor:block/{name}_off"},
            "on=true": {"model": f"beautifuldecor:block/{name}_on"},
        }})
        write_json(ITEMS / f"{name}.json", {
            "parent": f"beautifuldecor:block/{name}_off",
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0],
                                "scale": [0.8, 0.8, 0.8]}},
        })
        simple_loot(name)
        register_names(name, f"{en} Ceiling Fan", f"Ventilador de techo {es}")


# ---------------------------------------------------------------- 2) CAMAS MATRIMONIALES
def bed_part_elements(part, ox=0, oz=0):
    """Elementos de una casilla de la cama, orientada hacia el norte (cabecera al norte).

    part = (px, pz): px 0 = izquierda/1 = derecha mirando hacia la cabecera; pz 0 = pies, 1 = cabecera.
    (ox, oz) desplaza los elementos (se usa para el modelo del ítem).
    """
    px, pz = part
    left = px == 0
    # x de las patas/postes exteriores
    ox_post = (0, 2) if left else (14, 16)
    els = []

    def add(frm, to, tex, **kw):
        els.append(box([frm[0] + ox, frm[1], frm[2] + oz], [to[0] + ox, to[1], to[2] + oz], tex, **kw))

    add([0, 2.5, 0], [16, 5, 16], "#frame")                         # larguero/base
    if pz == 0:  # PIES
        add([0, 5, 0], [16, 9.6, 14], "#blanket")                    # manta
        add([0, 2.5, 14], [16, 10, 16], "#frame")                    # pie de cama
        add([ox_post[0], 10, 14], [ox_post[1], 12, 16], "#frame")    # poste
        add([ox_post[0], 0, 14], [ox_post[1], 2.5, 16], "#frame")    # pata
    else:        # CABECERA
        add([0, 2.5, 0], [16, 15, 2], "#frame")                      # cabecera
        add([ox_post[0], 15, 0], [ox_post[1], 16, 2], "#frame")      # remate
        add([ox_post[0], 0, 0], [ox_post[1], 2.5, 2], "#frame")      # pata
        add([0, 5, 2], [16, 9, 8], "#sheet")                         # colchón
        pillow_x = (2, 15) if left else (1, 14)
        add([pillow_x[0], 9, 3], [pillow_x[1], 11.5, 8], "#pillow")  # almohada
        add([0, 5, 8], [16, 9.9, 10.2], "#sheet")                    # doblez de sábana
        add([0, 5, 10.2], [16, 9.6, 16], "#blanket")                 # manta
    return els


PART_NAMES = {(0, 0): "foot_left", (1, 0): "foot_right", (0, 1): "head_left", (1, 1): "head_right"}


def gen_beds():
    # modelos padre por casilla (con variables de textura)
    for part, pname in PART_NAMES.items():
        write_json(MODELS / f"bed_double_{pname}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": "#blanket"},
            "elements": bed_part_elements(part),
        })
    # modelo padre del ítem: la cama entera (2x2) centrada en la casilla
    item_elements = []
    layout = {(0, 1): (-8, -8), (1, 1): (8, -8), (0, 0): (-8, 8), (1, 0): (8, 8)}  # (px,pz)->(ox,oz)
    for part, (ox, oz) in layout.items():
        item_elements += bed_part_elements(part, ox, oz)
    write_json(MODELS / "bed_double_item.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#blanket"},
        "elements": item_elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.34, 0.34, 0.34]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.15, 0.15, 0.15]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.18, 0.18, 0.18]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                      "scale": [0.2, 0.2, 0.2]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                     "scale": [0.2, 0.2, 0.2]},
        },
    })

    variants = [(f"bed_double_{c}", c, c, "oak", f"{EN_COLOR[c]}", f"{ES_COLOR[c]}") for c in DYES]
    for blanket, sheet in BED_COMBOS:
        variants.append((f"bed_double_{blanket}_{sheet}", blanket, sheet, "dark_oak",
                         f"{EN_COLOR[blanket]} & {EN_COLOR[sheet]}",
                         f"{ES_COLOR[blanket]} y {ES_COLOR[sheet]}"))

    for name, blanket, sheet, frame, en, es in variants:
        textures = {
            "frame": f"minecraft:block/{frame}_planks",
            "sheet": f"minecraft:block/{sheet}_wool",
            "pillow": f"minecraft:block/{sheet}_wool",
            "blanket": f"minecraft:block/{blanket}_wool",
        }
        for pname in PART_NAMES.values():
            write_json(MODELS / f"{name}_{pname}.json",
                       {"parent": f"beautifuldecor:block/bed_double_{pname}", "textures": textures})
        write_json(ITEMS / f"{name}.json", {"parent": "beautifuldecor:block/bed_double_item",
                                            "textures": textures})
        states = {}
        for facing, rot in FACINGS.items():
            for (px, pz), pname in PART_NAMES.items():
                entry = {"model": f"beautifuldecor:block/{name}_{pname}"}
                if rot:
                    entry["y"] = rot
                states[f"facing={facing},px={px},pz={pz}"] = entry
        write_json(STATES / f"{name}.json", {"variants": states})
        simple_loot(name, [{"condition": "minecraft:block_state_property",
                            "block": f"beautifuldecor:{name}",
                            "properties": {"px": "0", "pz": "0"}}])
        register_names(name, f"Double Bed ({en})", f"Cama matrimonial {es}")


# ---------------------------------------------------------------- 3) ESTANTERÍAS
BOOK_TEXTURES = ["minecraft:block/red_concrete", "minecraft:block/blue_concrete",
                 "minecraft:block/green_concrete", "minecraft:block/yellow_concrete",
                 "minecraft:block/white_concrete", "minecraft:block/orange_concrete",
                 "minecraft:block/purple_concrete", "minecraft:block/brown_concrete"]


def books_row(x_start, x_end, floor_y, z0, z1, seed, max_h=3.3):
    """Fila de libros de pie (alturas y colores pseudo-aleatorios pero deterministas)."""
    els = []
    x = x_start
    i = seed
    while x < x_end - 0.9:
        width = 1.1 + ((i * 7) % 3) * 0.35
        height = max_h - ((i * 5) % 4) * 0.35
        if x + width > x_end:
            break
        tex = BOOK_TEXTURES[(i * 3) % len(BOOK_TEXTURES)]
        els.append(box([x, floor_y, z0], [x + width, floor_y + height, z1], tex))
        x += width + (0.15 if i % 4 else 1.0)
        i += 1
    return els


def shelf_wood_elements():
    els = []
    z0 = 4
    # laterales, fondo y tablas
    els.append(box([0, 0, z0], [1.5, 16, 16], "#wood"))
    els.append(box([14.5, 0, z0], [16, 16, 16], "#wood"))
    els.append(box([1.5, 0, 14.8], [14.5, 16, 16], "#wood"))
    for y in (0, 5.2, 10.4, 14.5):
        els.append(box([1.5, y, z0], [14.5, y + 1.5, 14.8], "#wood"))
    els += books_row(2, 14, 1.5, 6, 13.5, 1)
    els += books_row(2, 14, 6.7, 6, 13.5, 4)
    els.append(box([3, 11.9, 6], [10, 13.2, 13], "minecraft:block/blue_concrete"))
    els.append(box([3.4, 13.2, 6.4], [9.6, 14.5, 12.6], "minecraft:block/red_concrete"))
    return els


def shelf_metal_elements():
    els = []
    z0 = 4
    for x in (0, 14.8):
        for z in (z0, 14.8):
            els.append(box([x, 0, z], [x + 1.2, 16, z + 1.2], "#metal"))     # postes
    for y in (0.6, 5.4, 10.2, 15.0):
        els.append(box([0, y, z0], [16, y + 0.9, 16], "#metal"))            # bandejas
    # travesaños traseros en X
    els.append(box([1.2, 1.5, 15.6], [14.8, 4.8, 16], "#metal"))
    els.append(box([1.2, 6.3, 15.6], [14.8, 9.6, 16], "#metal"))
    # cajas y libros
    els.append(box([1.8, 1.5, 6], [7.8, 5.3, 13.5], "minecraft:block/spruce_planks"))
    els.append(box([8.4, 1.5, 6.5], [14, 4.3, 13], "minecraft:block/barrel_side"))
    els += books_row(1.8, 14.2, 6.3, 6.5, 13, 2)
    els.append(box([2.5, 11.1, 6], [9, 14.5, 13], "minecraft:block/white_concrete"))
    els.append(box([9.6, 11.1, 6.5], [13.6, 13.1, 12.5], "minecraft:block/orange_concrete"))
    return els


def gen_shelves():
    write_json(MODELS / "shelf_wood_base.json", {
        "parent": "minecraft:block/block", "textures": {"particle": "#wood"},
        "elements": shelf_wood_elements()})
    write_json(MODELS / "shelf_metal_base.json", {
        "parent": "minecraft:block/block", "textures": {"particle": "#metal"},
        "elements": shelf_metal_elements()})

    def emit(name, parent, textures, en, es):
        write_json(MODELS / f"{name}.json", {"parent": f"beautifuldecor:block/{parent}", "textures": textures})
        write_json(STATES / f"{name}.json", {"variants": y_variants(lambda f: f"beautifuldecor:block/{name}")})
        write_json(ITEMS / f"{name}.json", {"parent": f"beautifuldecor:block/{name}"})
        simple_loot(name)
        register_names(name, en, es)

    for wood in WOODS:
        emit(f"shelf_{wood}", "shelf_wood_base",
             {"wood": f"minecraft:block/{wood}_planks", "particle": f"minecraft:block/{wood}_planks"},
             f"{EN_WOOD[wood]} Shelf", f"Estantería de {ES_WOOD[wood]}")
    for key, (tex, en, es) in METAL_SHELVES.items():
        emit(f"shelf_metal_{key}", "shelf_metal_base", {"metal": tex, "particle": tex},
             f"{en} Metal Shelf", f"Estantería metálica {es}")


# ---------------------------------------------------------------- 4) SEÑAL Y CONOS
def gen_sign_texture():
    S = 64
    ss = 6
    big = S * ss
    c = big / 2

    def circle(d, r, fill):
        d.ellipse([c - r, c - r, c + r, c + r], fill=fill)

    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    circle(d, 31.2 * ss, (200, 25, 30, 255))     # aro rojo
    circle(d, 24.6 * ss, (250, 250, 250, 255))   # fondo blanco
    k = ss

    def r(x1, y1, x2, y2, fill=(15, 15, 18, 255)):
        d.rectangle([x1 * k, y1 * k, x2 * k, y2 * k], fill=fill)

    # camión: caja + cabina + ruedas
    r(14, 22, 38, 39)
    d.polygon([(39 * k, 27 * k), (47 * k, 27 * k), (53 * k, 33 * k), (53 * k, 39 * k), (39 * k, 39 * k)],
              fill=(15, 15, 18, 255))
    d.polygon([(41 * k, 29 * k), (46 * k, 29 * k), (49.5 * k, 33 * k), (41 * k, 33 * k)],
              fill=(250, 250, 250, 255))                       # ventanilla
    for wx in (21, 31, 46):
        d.ellipse([(wx - 4.2) * k, 35.5 * k, (wx + 4.2) * k, 44 * k], fill=(15, 15, 18, 255))
        d.ellipse([(wx - 1.6) * k, 38 * k, (wx + 1.6) * k, 41.6 * k], fill=(250, 250, 250, 255))
    # barra roja diagonal (arriba-izquierda a abajo-derecha) recortada al círculo blanco
    bar = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bar)
    w = 4.2 * ss
    off = 29.5 * ss
    bd.polygon([(c - off - w * 0.7, c - off + w * 0.7), (c - off + w * 0.7, c - off - w * 0.7),
                (c + off + w * 0.7, c + off - w * 0.7), (c + off - w * 0.7, c + off + w * 0.7)],
               fill=(200, 25, 30, 255))
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse([c - 31.2 * ss, c - 31.2 * ss, c + 31.2 * ss, c + 31.2 * ss], fill=255)
    bar.putalpha(Image.composite(bar.getchannel("A"), Image.new("L", (big, big), 0), mask))
    img.alpha_composite(bar)
    final = img.resize((S, S), Image.LANCZOS)
    px = final.load()
    for y in range(S):
        for x in range(S):
            rr, gg, bb, a = px[x, y]
            px[x, y] = (rr, gg, bb, 255) if a >= 128 else (0, 0, 0, 0)
    final.save(TEX / "sign_no_trucks.png")
    # reverso: disco metálico gris con la misma silueta
    back = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bp = back.load()
    for y in range(S):
        for x in range(S):
            if px[x, y][3]:
                dist = math.hypot(x - S / 2 + 0.5, y - S / 2 + 0.5)
                shade = 150 if dist < 28 else 120
                bp[x, y] = (shade, shade, shade + 6, 255)
    back.save(TEX / "sign_no_trucks_back.png")


def gen_sign():
    gen_sign_texture()
    write_json(MODELS / "sign_no_trucks.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "beautifuldecor:block/sign_post",
                     "sign": "beautifuldecor:block/sign_no_trucks",
                     "back": "beautifuldecor:block/sign_no_trucks_back",
                     "post": "beautifuldecor:block/sign_post"},
        "elements": [
            box([7, 0, 7], [9, 13, 9], "#post"),
            box([7, 9, 6.6], [9, 10.2, 7], "#post"),                    # abrazadera
            box([2, 3.5, 6.5], [14, 15.5, 6.5], "#sign", faces=("north",),
                uv_override={"north": [0, 0, 16, 16]}),
            box([2, 3.5, 6.55], [14, 15.5, 6.55], "#back", faces=("south",),
                uv_override={"south": [16, 0, 0, 16]}),
        ],
    })
    write_json(STATES / "sign_no_trucks.json", {"variants": y_variants(lambda f: "beautifuldecor:block/sign_no_trucks")})
    write_json(ITEMS / "sign_no_trucks.json", {"parent": "beautifuldecor:block/sign_no_trucks"})
    simple_loot("sign_no_trucks")
    register_names("sign_no_trucks", "No Trucks Sign", "Señal: prohibido camiones")



# ---------------------------------------------------------------- idioma
def merge_lang():
    for fname, extra in (("en_us.json", lang_en), ("es_es.json", lang_es)):
        path = LANG / fname
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    for p in (MODELS, ITEMS, STATES, TEX, LOOT, LANG):
        p.mkdir(parents=True, exist_ok=True)
    gen_ceiling_fans()
    gen_beds()
    gen_shelves()
    gen_sign()
    merge_lang()
    print(f"Listo: {len(lang_en)} bloques nuevos.")


if __name__ == "__main__":
    main()
