#!/usr/bin/env python3
"""Genera los assets de la versión 1.6.0 de Beautiful Decor:

  * stairs_<madera>            escaleras de las 11 maderas (usan las texturas de tablones de vanilla)
  * stairs_concrete_<color>    escaleras de los 16 colores de concreto
  * stairs_smooth_quartz       escaleras de cuarzo liso
                               (+ etiquetas de minería para que se puedan romper y dropeen bien)
  * puertas dobles             modelos "espejo" (bisagra al otro lado) y blockstates con la propiedad
                               hinge para wooden_door_metal, glass_door y metal_door_black
  * washbasin_<diseño>_<color> lavamanos en 3 diseños (pedestal, pared, mueble) y 4 colores
                               (white, light_gray, dark_gray, black)

Los nombres deben coincidir con ModBlocks.java. Requiere Pillow (pip install pillow).
"""
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_v150_pack import (ASSETS, DATA, ITEMS, LANG, LOOT, MODELS, STATES, TEX,  # noqa: E402
                                add, box, clamp, loot, mirror_x, save, tex, write_child, write_json,
                                write_parent, y_states)

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
         "crimson", "warped"]
ES_WOOD = {"oak": "roble", "spruce": "abeto", "birch": "abedul", "jungle": "jungla", "acacia": "acacia",
           "dark_oak": "roble oscuro", "mangrove": "manglar", "cherry": "cerezo", "bamboo": "bambú",
           "crimson": "carmesí", "warped": "distorsionado"}
EN_WOOD = {w: w.replace("_", " ").title() for w in WOODS}
CONCRETE = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black"]
ES_CONCRETE = {"white": "blanco", "orange": "naranja", "magenta": "magenta", "light_blue": "celeste",
               "yellow": "amarillo", "lime": "lima", "pink": "rosa", "gray": "gris", "light_gray": "gris claro",
               "cyan": "cian", "purple": "morado", "blue": "azul", "brown": "marrón", "green": "verde",
               "red": "rojo", "black": "negro"}
EN_CONCRETE = {c: c.replace("_", " ").title() for c in CONCRETE}
WB_COLORS = ["white", "light_gray", "dark_gray", "black"]
WB_DESIGNS = ["pedestal", "wall", "vanity"]
ES_COLOR = {"white": "blanco", "light_gray": "gris claro", "dark_gray": "gris oscuro", "black": "negro"}
EN_COLOR = {"white": "White", "light_gray": "Light Gray", "dark_gray": "Dark Gray", "black": "Black"}
ES_DESIGN = {"pedestal": "de pedestal", "wall": "de pared", "vanity": "con mueble"}
EN_DESIGN = {"pedestal": "Pedestal", "wall": "Wall-Mounted", "vanity": "Vanity"}
lang_en, lang_es = {}, {}


def names(name, en, es):
    lang_en[f"block.beautifuldecor.{name}"] = en
    lang_es[f"block.beautifuldecor.{name}"] = es


# ================================================================== ESCALERAS
def stair_state(base):
    """blockstate estándar de escaleras (idéntico al de vanilla)."""
    yrot = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y in yrot.items():
        for half in ("bottom", "top"):
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                kind = "" if shape == "straight" else ("_inner" if shape.startswith("inner") else "_outer")
                left = shape.endswith("left")
                if half == "bottom":
                    rot = (y - 90) % 360 if left and shape != "straight" else y
                else:
                    rot = y if left or shape == "straight" else (y + 90) % 360
                entry = {"model": f"beautifuldecor:block/{base}{kind}"}
                if half == "top":
                    entry["x"] = 180
                if rot:
                    entry["y"] = rot
                entry["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = entry
    return {"variants": variants}


def gen_stairs():
    entries = {}   # nombre -> (textura vanilla, en, es)
    for w in WOODS:
        entries[f"stairs_{w}"] = (f"minecraft:block/{w}_planks", f"Beautiful Stairs ({EN_WOOD[w]})",
                                  f"Escalera bonita de {ES_WOOD[w]}")
    for c in CONCRETE:
        entries[f"stairs_concrete_{c}"] = (f"minecraft:block/{c}_concrete",
                                           f"Beautiful Concrete Stairs ({EN_CONCRETE[c]})",
                                           f"Escalera bonita de concreto ({ES_CONCRETE[c]})")
    entries["stairs_smooth_quartz"] = ("minecraft:block/quartz_block_bottom", "Beautiful Smooth Quartz Stairs",
                                       "Escalera bonita de cuarzo liso")
    for name, (texture, en, es) in entries.items():
        t = {"bottom": texture, "top": texture, "side": texture, "particle": texture}
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            write_json(MODELS / f"{name}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": t})
        write_json(STATES / f"{name}.json", stair_state(name))
        write_json(ITEMS / f"{name}.json", {"parent": f"beautifuldecor:block/{name}"})
        loot(name)
        names(name, en, es)

    # etiquetas: herramienta correcta para romperlas y tags de escaleras de vanilla
    ids = [f"beautifuldecor:{n}" for n in entries]
    wood_ids = [f"beautifuldecor:stairs_{w}" for w in WOODS]
    stone_ids = [i for i in ids if i not in wood_ids]
    tags = DATA.parent / "minecraft" / "tags"
    write_json(tags / "blocks/mineable/pickaxe.json", {"replace": False, "values": stone_ids})
    write_json(tags / "blocks/mineable/axe.json", {"replace": False, "values": wood_ids})
    for kind in ("blocks", "items"):
        write_json(tags / f"{kind}/stairs.json", {"replace": False, "values": ids})
        write_json(tags / f"{kind}/wooden_stairs.json", {"replace": False, "values": wood_ids})
    return entries


# ================================================================== PUERTAS DOBLES
DOORS = ["wooden_door_metal", "glass_door", "metal_door_black"]


def gen_doors():
    for door in DOORS:
        for suffix in ("", "_open"):
            data = json.loads((MODELS / f"{door}{suffix}.json").read_text(encoding="utf-8"))
            if data.get("elements") and not data.get("_mirrored"):
                data["elements"] = [mirror_x(e) for e in data["elements"]]
            write_json(MODELS / f"{door}_left{suffix}.json", data)
        variants = {}
        for facing, rot in {"north": 0, "east": 90, "south": 180, "west": 270}.items():
            for hinge in ("left", "right"):
                for is_open in ("false", "true"):
                    # hinge=right conserva los modelos originales; hinge=left usa el espejo
                    model = door + ("_left" if hinge == "left" else "") + ("_open" if is_open == "true" else "")
                    entry = {"model": f"beautifuldecor:block/{model}"}
                    if rot:
                        entry["y"] = rot
                    variants[f"facing={facing},hinge={hinge},open={is_open}"] = entry
        write_json(STATES / f"{door}.json", {"variants": variants})


# ================================================================== LAVAMANOS
BASE = {"white": (240, 240, 237), "light_gray": (188, 191, 196), "dark_gray": (72, 75, 80),
        "black": (30, 31, 34)}


def gen_washbasin_textures():
    for c, base in BASE.items():
        # cerámica lisa con un brillo suave
        im = Image.new("RGBA", (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                d = 5 - 9 * (y / 15)
                if (x - y) in (2, 3) and x < 9:
                    d += 7
                if x in (0, 15) or y in (0, 15):
                    d -= 7
                px[x, y] = add(base, d)
        save(f"washbasin_ceramic_{c}", im)
        # interior un poco más oscuro
        im = Image.new("RGBA", (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                px[x, y] = add(base, -13 + 3 * (y / 15))
        save(f"washbasin_inner_{c}", im)
        # fondo de la tina con el desagüe al centro
        im = Image.new("RGBA", (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if dist <= 1.8:
                    px[x, y] = (24, 25, 28, 255)
                elif dist <= 2.8:
                    px[x, y] = (150, 154, 160, 255)
                else:
                    px[x, y] = add(base, -16 + 2 * (y / 15))
        save(f"washbasin_floor_{c}", im)


def basin(x1, x2, z1, z2, yb, yt, t=1.0):
    """Tina hueca: suelo con desagüe y cuatro paredes (caras interiores con la textura #inner)."""
    floor = box([x1, yb, z1], [x2, yb + 1, z2], "#ceramic", up="#floor")
    floor["faces"]["up"]["uv"] = [0, 0, 16, 16]
    return [
        floor,
        box([x1, yb + 1, z1], [x2, yt, z1 + t], "#ceramic", south="#inner"),
        box([x1, yb + 1, z2 - t], [x2, yt, z2], "#ceramic", north="#inner"),
        box([x1, yb + 1, z1 + t], [x1 + t, yt, z2 - t], "#ceramic", east="#inner"),
        box([x2 - t, yb + 1, z1 + t], [x2, yt, z2 - t], "#ceramic", west="#inner"),
    ]


def pedestal_elements():
    els = [
        box([4.5, 0, 5.5], [11.5, 1.5, 12.5], "#ceramic"),               # base
        box([6, 1.5, 7], [10, 8.5, 11], "#ceramic"),                      # columna
    ]
    els += basin(2, 14, 2.5, 13, 8.5, 12)
    els += [
        box([2, 9.5, 13], [14, 14, 15], "#ceramic"),                      # salpicadero
        box([7, 14, 13], [9, 15, 15], "#chrome"),                         # base del grifo
        box([7.4, 14.8, 10.5], [8.6, 15.8, 14.5], "#chrome"),             # pico
        box([7.4, 14, 10.5], [8.6, 14.8, 11.5], "#chrome"),               # boquilla
        box([4.2, 14, 13], [5.8, 15.2, 14.6], "#chrome"),                 # llave izquierda
        box([10.2, 14, 13], [11.8, 15.2, 14.6], "#chrome"),               # llave derecha
    ]
    return els


def wall_elements():
    els = basin(2, 14, 5, 16, 8, 12.5)
    els += [
        box([2, 12.5, 14.5], [14, 14, 16], "#ceramic"),                   # salpicadero pegado a la pared
        box([7.2, 4.5, 13], [8.8, 8, 14.6], "#chrome"),                   # sifón vertical
        box([7.2, 4.5, 14.6], [8.8, 6.1, 16], "#chrome"),                 # sifón hacia la pared
        box([7, 14, 15], [9, 15.5, 16], "#chrome"),                       # base del grifo
        box([7.4, 14.3, 11.5], [8.6, 15.3, 15], "#chrome"),               # pico
        box([7.2, 15.5, 15.1], [8.8, 16, 15.9], "#chrome"),               # palanca
    ]
    return els


def vanity_elements():
    els = [
        box([1, 0, 3], [15, 11.5, 15], "#body"),                          # mueble
        box([1.6, 1, 2.6], [7.8, 10.5, 3], "#body", full=True, south=None),    # puerta izquierda
        box([8.2, 1, 2.6], [14.4, 10.5, 3], "#body", full=True, south=None),   # puerta derecha
        box([6.5, 4, 2.2], [7.3, 8, 2.6], "#chrome"),                     # manijas
        box([8.7, 4, 2.2], [9.5, 8, 2.6], "#chrome"),
        box([0, 11.5, 2], [16, 13, 16], "#ceramic"),                      # cubierta
    ]
    els += basin(4, 12, 5, 13, 13, 15.5)                                  # lavabo de apoyo
    els += [
        box([7, 13, 13.5], [9, 14, 15.5], "#chrome"),                     # base del grifo
        box([7.4, 14, 14], [8.6, 16, 15], "#chrome"),                     # columna
        box([7.4, 15, 11], [8.6, 16, 15], "#chrome"),                     # pico
        box([7.4, 14.2, 11], [8.6, 15, 12], "#chrome"),                   # boquilla
    ]
    return els


def gen_washbasins():
    gen_washbasin_textures()
    designs = {"pedestal": pedestal_elements(), "wall": wall_elements(), "vanity": vanity_elements()}
    for design, els in designs.items():
        write_parent(f"washbasin_{design}", els, particle="#ceramic")
        for c in WB_COLORS:
            name = f"washbasin_{design}_{c}"
            write_child(name, f"washbasin_{design}", {
                "ceramic": tex(f"washbasin_ceramic_{c}"), "inner": tex(f"washbasin_inner_{c}"),
                "floor": tex(f"washbasin_floor_{c}"), "body": tex(f"kitchen_body_{c}"),
                "chrome": tex("handle_metal"), "particle": tex(f"washbasin_ceramic_{c}")})
            write_json(STATES / f"{name}.json", y_states(lambda combo, n=name: f"beautifuldecor:block/{n}", []))
            write_json(ITEMS / f"{name}.json", {"parent": f"beautifuldecor:block/{name}"})
            loot(name)
            names(name, f"{EN_DESIGN[design]} Washbasin ({EN_COLOR[c]})",
                  f"Lavamanos {ES_DESIGN[design]} ({ES_COLOR[c]})")


def merge_lang():
    lang_en["category.beautifuldecor.stairs"] = "Stairs"
    lang_es["category.beautifuldecor.stairs"] = "Escaleras"
    for fname, extra in (("en_us.json", lang_en), ("es_es.json", lang_es)):
        path = LANG / fname
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    stairs = gen_stairs()
    gen_doors()
    gen_washbasins()
    merge_lang()
    print(f"Listo: {len(stairs)} escaleras, {len(DOORS)} puertas con bisagra, "
          f"{len(WB_DESIGNS) * len(WB_COLORS)} lavamanos.")
