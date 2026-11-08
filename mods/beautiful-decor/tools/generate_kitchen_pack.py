#!/usr/bin/env python3
"""Genera texturas, modelos, blockstates, ítems, loot tables e idiomas de la cocina nueva:

  * stove_oven_<color>     estufa con horno (light_gray, dark_gray, black, white); la puerta se abre con clic
  * stove_cabinet_<color>  mueble de estufa sin horno (mismos 4 colores)
  * kitchen_oven           horno de cocina normal (puerta que se abre con clic)
  * microwave_<color>      microondas (white, gray, dark_gray, black); la puerta se abre con clic
  * cutlery_fork / _knife / _spoon / _set / _pack   cubiertos sueltos, juego y paquete en servilleta

Los nombres deben coincidir con ModBlocks.java (KITCHEN_APPLIANCES y CUTLERY).
Requiere Pillow para las texturas (pip install pillow).
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
MODELS, ITEMS, STATES = ASSETS / "models/block", ASSETS / "models/item", ASSETS / "blockstates"
TEX, LOOT, LANG = ASSETS / "textures/block", DATA / "loot_tables/blocks", ASSETS / "lang"

STOVE_COLORS = ["light_gray", "dark_gray", "black", "white"]
MICRO_COLORS = ["white", "gray", "dark_gray", "black"]
ES_COLOR = {"white": "blanco", "light_gray": "gris claro", "gray": "gris", "dark_gray": "gris oscuro",
            "black": "negro"}
EN_COLOR = {"white": "White", "light_gray": "Light Gray", "gray": "Gray", "dark_gray": "Dark Gray",
            "black": "Black"}
BASE = {"white": (238, 238, 234), "light_gray": (186, 189, 194), "gray": (128, 131, 136),
        "dark_gray": (68, 71, 76), "black": (28, 29, 32)}
# perillas: negras sobre colores claros, metálicas sobre los oscuros
KNOB = {"white": "handle_black", "light_gray": "handle_black", "gray": "handle_black",
        "dark_gray": "handle_metal", "black": "handle_metal"}
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("north", "south", "east", "west", "up", "down")
lang_en, lang_es = {}, {}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tex(name):
    return f"beautifuldecor:block/{name}"


# ------------------------------------------------------------------ texturas (16x16)
def clamp(v):
    return max(0, min(255, int(v)))


def add(c, d):
    return tuple(clamp(v + d) for v in c) + (255,)


def save(name, im):
    TEX.mkdir(parents=True, exist_ok=True)
    im.save(TEX / f"{name}.png")


def make_body(name, base):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    lum = sum(base) / 3
    border = -(8 + 0.12 * lum)
    for y in range(16):
        for x in range(16):
            d = 6 - 12 * (y / 15)
            if x in (0, 15) or y in (0, 15):
                d += border
            elif x in (1, 14) or y in (1, 14):
                d += 4
            px[x, y] = add(base, d)
    save(name, im)


def make_steel(name, base=(176, 180, 186), seed=7):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    streak = [rnd.randint(-6, 6) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            d = streak[x] + 5 - 8 * (y / 15)
            if x in (0, 15) or y in (0, 15):
                d -= 26
            px[x, y] = add(base, d)
    save(name, im)


def make_glass():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = (20, 23, 27)
            if x in (0, 15) or y in (0, 15):
                c = (8, 9, 10)
            elif (x - y) in (3, 4) and x < 11:
                c = add(c, 26)
            elif (x - y) in (6, 7) and x < 14:
                c = add(c, 12)
            px[x, y] = c + (255,) if len(c) == 3 else c
    save("kitchen_glass", im)


def make_oven_inside():
    rnd = random.Random(11)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = (44 + rnd.randint(-2, 2),) * 2 + (48,)
            if y in (4, 8, 12):
                c = (84, 84, 90)
            px[x, y] = tuple(c) + (255,)
    save("kitchen_oven_inside", im)


def make_micro_inside():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = (204, 206, 210)
            if x % 4 == 1 and y % 4 == 1:
                c = (170, 172, 178)
            if x in (0, 15) or y in (0, 15):
                c = (150, 152, 158)
            px[x, y] = c + (255,)
    save("kitchen_micro_inside", im)


def make_cooktop():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    centers = [(4, 4), (11, 4), (4, 11), (11, 11)]
    for y in range(16):
        for x in range(16):
            c = (16, 17, 19)
            if x in (0, 15) or y in (0, 15):
                c = (6, 6, 7)
            for cx, cy in centers:
                dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if dist <= 1.6:
                    c = (30, 31, 34)
                elif 2.4 <= dist <= 3.6:
                    c = (96, 99, 105)
            px[x, y] = c + (255,)
    save("kitchen_cooktop", im)


FONT = {"1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
        "0": ["111", "101", "101", "101", "111"]}


def make_display():
    im = Image.new("RGBA", (16, 16), (10, 16, 13, 255))
    px = im.load()
    green = (88, 240, 138, 255)
    for ch, x0 in (("1", 0), ("2", 4), ("0", 9), ("0", 13)):
        for ry, row in enumerate(FONT[ch]):
            for rx, bit in enumerate(row):
                if bit == "1" and x0 + rx < 16:
                    px[x0 + rx, 5 + ry] = green
    px[7, 6] = green
    px[7, 8] = green
    save("kitchen_display", im)


def make_cutlery_steel():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = 12 - 22 * (x / 15)
            c = add((200, 205, 212), d)
            if x in (0, 15) or y in (0, 15):
                c = add((200, 205, 212), -55)
            px[x, y] = c
    save("cutlery_steel", im)


def make_napkin():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = (242, 241, 236)
            if y in (5, 10):
                c = (214, 212, 205)
            if x in (0, 15) or y in (0, 15):
                c = (196, 194, 186)
            px[x, y] = c + (255,)
    save("cutlery_napkin", im)


def make_band():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = (176, 40, 48) if y not in (0, 15) else (128, 26, 32)
            px[x, y] = c + (255,)
    save("cutlery_band", im)


def gen_textures():
    for color, base in BASE.items():
        make_body(f"kitchen_body_{color}", base)
    make_steel("kitchen_steel")
    make_glass()
    make_oven_inside()
    make_micro_inside()
    make_cooktop()
    make_display()
    make_cutlery_steel()
    make_napkin()
    make_band()


# ------------------------------------------------------------------ helpers de modelo
def box(frm, to, default, full=False, **faces):
    """Caja con la misma textura en todas las caras; faces permite cambiar o quitar (None) una cara.
    full=True estira la textura completa en cada cara (así se ve el contorno de paneles y puertas)."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    out = {}
    for f in FACES:
        t = faces.get(f, default)
        if t is None:
            continue
        if full:
            uv = [0, 0, 16, 16]
        else:
            uv = [0, 0, round(min(sizes[f][0], 16), 3), round(min(sizes[f][1], 16), 3)]
        out[f] = {"uv": uv, "texture": t}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": out}


def write_parent(name, elements, textures=None, display=None):
    model = {"parent": "minecraft:block/block", "textures": {"particle": "#body"}, "elements": elements}
    if textures:
        model["textures"].update(textures)
    if display:
        model["display"] = display
    write_json(MODELS / f"{name}.json", model)


def write_child(name, parent, textures):
    t = dict(textures)
    t.setdefault("particle", t.get("body"))
    write_json(MODELS / f"{name}.json", {"parent": f"beautifuldecor:block/{parent}", "textures": t})


def y_states(model_for, props):
    variants = {}
    combos = [{}]
    for prop in props:
        combos = [dict(c, **{prop: v}) for c in combos for v in ("false", "true")]
    for facing, rot in FACINGS.items():
        for combo in combos:
            key = ",".join([f"facing={facing}"] + [f"{k}={v}" for k, v in combo.items()])
            entry = {"model": model_for(combo)}
            if rot:
                entry["y"] = rot
            variants[key] = entry
    return {"variants": variants}


def loot(name):
    write_json(LOOT / f"{name}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def names(name, en, es):
    lang_en[f"block.beautifuldecor.{name}"] = en
    lang_es[f"block.beautifuldecor.{name}"] = es


def register_block(name, closed_model, open_model, en, es):
    """blockstate (con o sin puerta), modelo de ítem, loot e idiomas."""
    if open_model:
        write_json(STATES / f"{name}.json", y_states(
            lambda c: f"beautifuldecor:block/{open_model if c['open'] == 'true' else closed_model}", ["open"]))
    else:
        write_json(STATES / f"{name}.json", y_states(lambda c: f"beautifuldecor:block/{closed_model}", []))
    write_json(ITEMS / f"{name}.json", {"parent": f"beautifuldecor:block/{closed_model}"})
    loot(name)
    names(name, en, es)


# ------------------------------------------------------------------ estufa con horno / mueble sin horno
def stove_common(with_knobs=True):
    """cooktop, respaldo con pantalla y perillas (compartido por estufa y mueble)."""
    els = [
        box([0, 13, 0], [16, 14, 16], "#body", up="#top"),                      # placa de cocción
        box([0, 14, 13], [16, 16, 16], "#body"),                                 # respaldo
        box([5, 14.4, 12.85], [11, 15.6, 13], "#display", south=None, east=None, west=None,
            up=None, down=None),                                                 # pantalla
    ]
    if with_knobs:
        for x in (2, 5.5, 9, 12.5):
            els.append(box([x, 10, -0.5], [x + 2, 12, 1], "#knob", south=None))
    return els


def stove_oven_closed():
    els = [box([0, 0, 1], [16, 13, 16], "#body")]
    els += stove_common()
    els += [
        box([1, 1, 0], [15, 9, 1], "#body", full=True, south=None, north="#body"),  # puerta
        box([2.5, 2, -0.15], [13.5, 6.5, 0], "#glass", south=None),                  # ventana
        box([2.5, 7.3, -1.2], [13.5, 8.2, -0.5], "#steel"),                          # manija
        box([2.5, 7.3, -0.5], [3.5, 8.2, 0], "#steel"),
        box([12.5, 7.3, -0.5], [13.5, 8.2, 0], "#steel"),
    ]
    return els


def stove_oven_open():
    els = [
        box([0, 0, 1], [16, 1, 16], "#body", up="#inside"),                 # fondo
        box([0, 9, 1], [16, 13, 16], "#body", down="#inside"),              # techo del horno + panel
        box([0, 1, 1], [1, 9, 16], "#body", east="#inside"),                # pared izquierda
        box([15, 1, 1], [16, 9, 16], "#body", west="#inside"),              # pared derecha
        box([1, 1, 15], [15, 9, 16], "#body", north="#inside"),             # fondo del horno
    ]
    els += stove_common()
    els += [box([1, 1, -8], [15, 2, 1], "#body", up="#inside", south=None)]  # puerta abatida
    return els


def stove_cabinet():
    els = [box([0, 0, 1], [16, 13, 16], "#body")]
    els += stove_common()
    els += [
        box([1, 1, 0.3], [7.8, 9, 1], "#body", full=True, south=None),      # puerta izquierda
        box([8.2, 1, 0.3], [15, 9, 1], "#body", full=True, south=None),     # puerta derecha
        box([6.4, 4, -0.4], [7.2, 7.5, 0.3], "#steel"),                     # manijas verticales
        box([8.8, 4, -0.4], [9.6, 7.5, 0.3], "#steel"),
    ]
    return els


def gen_stoves():
    write_parent("kitchen_stove_oven_closed", stove_oven_closed())
    write_parent("kitchen_stove_oven_open", stove_oven_open())
    write_parent("kitchen_stove_cabinet", stove_cabinet())
    for c in STOVE_COLORS:
        t = {"body": tex(f"kitchen_body_{c}"), "top": tex("kitchen_cooktop"), "glass": tex("kitchen_glass"),
             "inside": tex("kitchen_oven_inside"), "knob": tex(KNOB[c]), "steel": tex("handle_metal"),
             "display": tex("kitchen_display")}
        write_child(f"stove_oven_{c}_closed", "kitchen_stove_oven_closed", t)
        write_child(f"stove_oven_{c}_open", "kitchen_stove_oven_open", t)
        write_child(f"stove_cabinet_{c}", "kitchen_stove_cabinet", t)
        register_block(f"stove_oven_{c}", f"stove_oven_{c}_closed", f"stove_oven_{c}_open",
                       f"Stove with Oven ({EN_COLOR[c]})", f"Estufa con horno ({ES_COLOR[c]})")
        register_block(f"stove_cabinet_{c}", f"stove_cabinet_{c}", None,
                       f"Stove Cabinet ({EN_COLOR[c]})", f"Mueble de estufa sin horno ({ES_COLOR[c]})")


# ------------------------------------------------------------------ horno de cocina
def oven_controls():
    els = []
    for x in (2, 5.5, 9):
        els.append(box([x, 13, -0.4], [x + 2.5, 15.5, 1], "#knob", south=None))
    els.append(box([11.5, 13, 0.85], [14.5, 15, 1], "#display", south=None, east=None, west=None,
                   up=None, down=None))
    return els


def kitchen_oven_closed():
    els = [box([0, 0, 1], [16, 16, 16], "#body")]
    els += oven_controls()
    els += [
        box([1, 1, 0], [15, 11.5, 1], "#body", full=True, south=None),
        box([2, 2.5, -0.15], [14, 8.3, 0], "#glass", south=None),
        box([2, 9.3, -1.3], [14, 10.3, -0.6], "#steel"),
        box([2.5, 9.3, -0.6], [3.5, 10.3, 0], "#steel"),
        box([12.5, 9.3, -0.6], [13.5, 10.3, 0], "#steel"),
    ]
    return els


def kitchen_oven_open():
    els = [
        box([0, 0, 1], [16, 1, 16], "#body", up="#inside"),
        box([0, 11.5, 1], [16, 16, 16], "#body", down="#inside"),
        box([0, 1, 1], [1, 11.5, 16], "#body", east="#inside"),
        box([15, 1, 1], [16, 11.5, 16], "#body", west="#inside"),
        box([1, 1, 15], [15, 11.5, 16], "#body", north="#inside"),
    ]
    els += oven_controls()
    els += [box([1, 1, -10.5], [15, 2, 1], "#body", up="#inside", south=None)]
    return els


def gen_oven():
    write_parent("kitchen_oven_closed", kitchen_oven_closed())
    write_parent("kitchen_oven_open", kitchen_oven_open())
    t = {"body": tex("kitchen_steel"), "glass": tex("kitchen_glass"), "inside": tex("kitchen_oven_inside"),
         "knob": tex("handle_black"), "steel": tex("handle_metal"), "display": tex("kitchen_display")}
    write_child("kitchen_oven_model_closed", "kitchen_oven_closed", t)
    write_child("kitchen_oven_model_open", "kitchen_oven_open", t)
    register_block("kitchen_oven", "kitchen_oven_model_closed", "kitchen_oven_model_open",
                   "Kitchen Oven", "Horno de cocina")


# ------------------------------------------------------------------ microondas
def micro_panel():
    return [
        box([12, 5.3, 1.85], [14.5, 7, 2], "#display", south=None, east=None, west=None, up=None, down=None),
        box([12, 3.9, 1.7], [14.5, 4.7, 2], "#steel", south=None),
        box([12, 2.4, 1.7], [14.5, 3.2, 2], "#steel", south=None),
    ]


def microwave_closed():
    els = [box([1, 0, 2], [15, 9, 14], "#body")]
    els += micro_panel()
    els += [
        box([2, 1.5, 1], [11.5, 7.5, 2], "#body", full=True, south=None),    # puerta
        box([3, 2.5, 0.85], [10.5, 6.5, 1], "#glass", south=None),             # ventana
        box([10.3, 3, 0.2], [11.1, 6, 1], "#steel"),                           # manija
    ]
    return els


def microwave_open():
    els = [
        box([1, 0, 2], [15, 1.5, 14], "#body", up="#inside"),
        box([1, 7.5, 2], [15, 9, 14], "#body", down="#inside"),
        box([1, 1.5, 2], [2, 7.5, 14], "#body", east="#inside"),
        box([11.5, 1.5, 2], [15, 7.5, 14], "#body", west="#inside"),
        box([2, 1.5, 13], [11.5, 7.5, 14], "#body", north="#inside"),
    ]
    els += micro_panel()
    els += [
        box([1, 1.5, -7.5], [2, 7.5, 2], "#body", east="#glass", south=None),  # puerta abierta
        box([0.2, 3, -7.1], [1, 6, -6.3], "#steel"),                            # manija
    ]
    return els


def gen_microwaves():
    write_parent("kitchen_microwave_closed", microwave_closed())
    write_parent("kitchen_microwave_open", microwave_open())
    for c in MICRO_COLORS:
        t = {"body": tex(f"kitchen_body_{c}"), "glass": tex("kitchen_glass"),
             "inside": tex("kitchen_micro_inside"), "steel": tex("handle_metal"),
             "display": tex("kitchen_display")}
        write_child(f"microwave_{c}_closed", "kitchen_microwave_closed", t)
        write_child(f"microwave_{c}_open", "kitchen_microwave_open", t)
        register_block(f"microwave_{c}", f"microwave_{c}_closed", f"microwave_{c}_open",
                       f"Microwave ({EN_COLOR[c]})", f"Microondas ({ES_COLOR[c]})")


# ------------------------------------------------------------------ cubiertos (planos sobre la mesa; punta hacia el norte)
S = "#steel"


def fork(cx):
    return [
        box([cx - 0.6, 0, 6], [cx + 0.6, 0.5, 13], S, up=S),            # mango
        box([cx - 0.5, 0, 4.2], [cx + 0.5, 0.4, 6], S),                  # cuello
        box([cx - 1.5, 0, 3], [cx + 1.5, 0.4, 4.2], S),                  # base de las puntas
        box([cx - 1.5, 0, 1.2], [cx - 0.8, 0.4, 3], S),                  # puntas
        box([cx - 0.35, 0, 1.2], [cx + 0.35, 0.4, 3], S),
        box([cx + 0.8, 0, 1.2], [cx + 1.5, 0.4, 3], S),
    ]


def knife(cx):
    return [
        box([cx - 0.7, 0, 8], [cx + 0.7, 0.7, 13], S),                   # mango
        box([cx - 0.7, 0, 1.5], [cx + 0.4, 0.3, 8], S),                  # hoja
        box([cx + 0.4, 0, 2.5], [cx + 0.7, 0.55, 8], S),                 # lomo
    ]


def spoon(cx):
    return [
        box([cx - 0.5, 0, 6], [cx + 0.5, 0.4, 13], S),                   # mango
        box([cx - 1.2, 0, 2.3], [cx + 1.2, 0.5, 6], S),                  # cuenco
        box([cx - 0.7, 0, 1.6], [cx + 0.7, 0.5, 2.3], S),                # punta redondeada
    ]


CUTLERY_DISPLAY = {
    "gui": {"rotation": [45, 225, 0], "translation": [0, 1.5, 0], "scale": [1.9, 1.9, 1.9]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
}


def gen_cutlery():
    steel = {"steel": tex("cutlery_steel")}
    pieces = {
        "cutlery_fork": (fork(8), "Fork", "Tenedor", steel),
        "cutlery_knife": (knife(8), "Knife", "Cuchillo", steel),
        "cutlery_spoon": (spoon(8), "Spoon", "Cuchara", steel),
        "cutlery_set": (fork(4.5) + knife(8) + spoon(11.5), "Cutlery Set", "Juego de cubiertos", steel),
    }
    for name, (els, en, es, textures) in pieces.items():
        write_json(MODELS / f"{name}.json", {
            "parent": "minecraft:block/block",
            "textures": dict(textures, particle="beautifuldecor:block/cutlery_steel"),
            "elements": els, "display": CUTLERY_DISPLAY})
        register_block(name, name, None, en, es)
    pack = [
        box([5.5, 0, 3.5], [10.5, 1.8, 12.5], "#napkin"),                        # servilleta enrollada
        box([5.4, 0, 7], [10.6, 1.9, 8.5], "#band"),                              # banda
        box([6.4, 0.5, 12.5], [7.6, 1.3, 14], S),                                 # mango del tenedor
        box([8.4, 0.5, 12.5], [9.8, 1.5, 14], S),                                 # mango del cuchillo
        box([6.2, 0.5, 2.4], [6.8, 1.0, 3.5], S),                                 # puntas del tenedor
        box([7.1, 0.5, 2.4], [7.7, 1.0, 3.5], S),
        box([8.0, 0.5, 2.4], [8.6, 1.0, 3.5], S),
    ]
    write_json(MODELS / "cutlery_pack.json", {
        "parent": "minecraft:block/block",
        "textures": {"steel": tex("cutlery_steel"), "napkin": tex("cutlery_napkin"),
                     "band": tex("cutlery_band"), "particle": tex("cutlery_napkin")},
        "elements": pack, "display": CUTLERY_DISPLAY})
    register_block("cutlery_pack", "cutlery_pack", None, "Cutlery Pack (napkin-wrapped)",
                   "Paquete de cubiertos (en servilleta)")


def merge_lang():
    for fname, extra in (("en_us.json", lang_en), ("es_es.json", lang_es)):
        path = LANG / fname
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    gen_textures()
    gen_stoves()
    gen_oven()
    gen_microwaves()
    gen_cutlery()
    merge_lang()
    print(f"Listo: {len(lang_en)} bloques de cocina.")
