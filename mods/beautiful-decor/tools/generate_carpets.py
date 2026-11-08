#!/usr/bin/env python3
"""Genera las alfombras decorativas (v1.3.0): texturas propias, modelos, blockstates, ítems, loot e idiomas.
Los nombres deben coincidir con CARPET_STRIPES / CARPET_CHECKERS de ModBlocks.java."""

import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}

# rayas: nombre -> (claro, medio, oscuro), inglés, español
STRIPES = {
    "blue": ((112, 172, 236), (52, 102, 204), (22, 42, 112), "Blue Striped", "azul a rayas"),
    "pink": ((250, 192, 216), (236, 112, 162), (132, 52, 162), "Pink Striped", "rosa a rayas"),
    "mono": ((236, 236, 236), (132, 132, 136), (30, 30, 34), "Black, Gray & White Striped",
             "negra, gris y blanca a rayas"),
}
# cuadros: nombre -> (A, B), inglés, español
CHECKERS = {
    "white_black": ((236, 236, 236), (30, 30, 34), "White & Black", "blanco y negro"),
    "lightblue_darkblue": ((122, 182, 240), (26, 46, 122), "Light Blue & Dark Blue", "azul claro y azul oscuro"),
    "red_white": ((192, 36, 36), (236, 236, 236), "Red & White", "rojo y blanco"),
    "red_black": ((192, 36, 36), (30, 30, 34), "Red & Black", "rojo y negro"),
    "pink_purple": ((242, 152, 192), (132, 52, 162), "Pink & Purple", "rosado y morado"),
    "green_lime": ((42, 122, 42), (132, 212, 62), "Green & Lime", "verde y lima"),
    "yellow_orange": ((250, 216, 62), (240, 132, 32), "Yellow & Orange", "amarillo y naranja"),
    "gray_lightgray": ((96, 96, 102), (176, 176, 182), "Gray & Light Gray", "gris y gris claro"),
    "cyan_blue": ((32, 172, 172), (42, 72, 172), "Cyan & Blue", "cian y azul"),
    "brown_beige": ((112, 72, 42), (226, 202, 156), "Brown & Beige", "marrón y beige"),
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def shade(color, rnd):
    d = rnd.randint(-7, 7)
    return tuple(max(0, min(255, c + d)) for c in color) + (255,)


def make_texture(name, pixel_color):
    rnd = random.Random(name)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(pixel_color(x, y), rnd)
    img.save(ASSETS / f"textures/block/{name}.png")


def make_model(name):
    faces = {f: {"uv": [0, 0, 16, 16], "texture": "#carpet"} for f in ("up", "down")}
    for f in ("north", "south", "east", "west"):
        faces[f] = {"uv": [0, 15, 16, 16], "texture": "#carpet"}
    write_json(ASSETS / f"models/block/{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#carpet", "carpet": f"beautifuldecor:block/{name}"},
        "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": faces}],
    })
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": {
        f"facing={f}": ({"model": f"beautifuldecor:block/{name}", "y": r} if r
                        else {"model": f"beautifuldecor:block/{name}"}) for f, r in FACINGS.items()}})
    write_json(ASSETS / f"models/item/{name}.json", {"parent": f"beautifuldecor:block/{name}"})
    write_json(DATA / f"loot_tables/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def main():
    en, es = {}, {}
    for key, (light, mid, dark, n_en, n_es) in STRIPES.items():
        name = f"carpet_stripes_{key}"
        band = [light, mid, dark, mid]          # bandas de 2 px: claro, medio, oscuro, medio (x2 = 16 px)
        make_texture(name, lambda x, y, b=band: b[(x // 2) % 4])
        make_model(name)
        en[f"block.beautifuldecor.{name}"] = f"{n_en} Carpet"
        es[f"block.beautifuldecor.{name}"] = f"Alfombra {n_es}"
    for key, (a, b, n_en, n_es) in CHECKERS.items():
        name = f"carpet_checker_{key}"
        make_texture(name, lambda x, y, a=a, b=b: a if ((x // 4) + (y // 4)) % 2 == 0 else b)
        make_model(name)
        en[f"block.beautifuldecor.{name}"] = f"{n_en} Checkered Carpet"
        es[f"block.beautifuldecor.{name}"] = f"Alfombra de cuadros {n_es}"
    for fname, extra in (("en_us.json", en), ("es_es.json", es)):
        p = ASSETS / "lang" / fname
        data = json.loads(p.read_text(encoding="utf-8"))
        data.update(extra)
        p.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    # comprobación cruzada con ModBlocks.java
    java = (ROOT / "src/main/java/com/example/beautifuldecor/init/ModBlocks.java").read_text(encoding="utf-8")
    import re
    j_stripes = re.findall(r'"(\w+)"', re.search(r"CARPET_STRIPES = \{(.*?)\};", java, re.S).group(1))
    j_checkers = re.findall(r'"(\w+)"', re.search(r"CARPET_CHECKERS = \{(.*?)\};", java, re.S).group(1))
    assert j_stripes == list(STRIPES), (j_stripes, list(STRIPES))
    assert j_checkers == list(CHECKERS), (j_checkers, list(CHECKERS))
    print(f"Listo: {len(en)} alfombras (Java y assets coinciden).")


if __name__ == "__main__":
    main()
