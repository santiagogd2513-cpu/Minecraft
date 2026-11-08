#!/usr/bin/env python3
"""Genera los ventiladores de pie de la 2.3.0 (pedestal_fan_white / black / retro), de dos bloques de alto.

Uso (desde la raíz del proyecto):  python3 -m tools.v230.fans
La cabeza (jaula, aspas animadas, motor) sale del modelo del ventilador mediano, agrandada 1,3 veces y subida a la mitad de arriba;
la mitad de abajo lleva la base y el tubo. Escribe modelos, blockstates, loot, idiomas y el modelo de ícono (se renderiza con make_icons).
"""
import copy
import json

from ..v180.common import ASSETS, FACINGS, ITEMS, LANG, MODELS, STATES, write_json
from ..v180.common import LOOT

VARIANTS = {
    # id: (cáscara, base, inglés, español)
    "pedestal_fan_white": ("minecraft:block/white_concrete", "minecraft:block/light_gray_concrete",
                           "White Pedestal Fan", "Ventilador de pie blanco"),
    "pedestal_fan_black": ("minecraft:block/black_concrete", "minecraft:block/gray_concrete",
                           "Black Pedestal Fan", "Ventilador de pie negro"),
    "pedestal_fan_retro": ("minecraft:block/light_blue_concrete", "minecraft:block/white_concrete",
                           "Retro Pedestal Fan", "Ventilador de pie retro"),
}
SCALE = 1.3
CENTER_X, CENTER_Y = 8.0, 12.2          # centro de la cabeza del ventilador mediano
TARGET_Y = 8.5                          # centro de la cabeza dentro de la mitad de arriba
NEW_ITEMS = {}
LANG_EN, LANG_ES = {}, {}


def rnd(v):
    return round(v * 1000) / 1000


def head_elements():
    src = json.loads((MODELS / "fan_medium_on.json").read_text(encoding="utf-8"))["elements"][4:]   # jaula, aros, aspas, motor
    out = []
    for el in src:
        el = copy.deepcopy(el)
        for key in ("from", "to"):
            x, y, z = el[key]
            el[key] = [rnd(CENTER_X + (x - CENTER_X) * SCALE), rnd(TARGET_Y + (y - CENTER_Y) * SCALE), z]
        if "rotation" in el:
            ox, oy, oz = el["rotation"]["origin"]
            el["rotation"]["origin"] = [rnd(CENTER_X + (ox - CENTER_X) * SCALE), rnd(TARGET_Y + (oy - CENTER_Y) * SCALE), oz]
        out.append(el)
    return out


def cube(frm, to, tex):
    return {"from": frm, "to": to, "faces": {f: {"texture": tex} for f in ("north", "east", "south", "west", "up", "down")}}


LOWER = [cube([3, 0, 3], [13, 1, 13], "#base"), cube([4, 1, 4], [12, 1.8, 12], "#base"),
         cube([7.2, 1.8, 7.2], [8.8, 16, 8.8], "#base"), cube([6.6, 9, 6.6], [9.4, 10, 9.4], "#shell")]
UPPER_STAND = [cube([7.2, 0, 7.2], [8.8, 6.2, 8.8], "#base"), cube([6.6, 4.6, 6.6], [9.4, 5.6, 9.4], "#shell"),
               cube([7.2, 6.2, 8.0], [8.8, 7.2, 9.8], "#base")]


def textures(shell, base, state=None):
    t = {"particle": shell, "shell": shell, "base": base, "grille": "beautifuldecor:block/fan_grille"}
    if state:
        t["blades"] = f"beautifuldecor:block/fan_medium_blades_{state}"
    return t


def main():
    head = head_elements()
    for bid, (shell, base, en, es) in VARIANTS.items():
        write_json(MODELS / f"{bid}_lower.json", {"parent": "minecraft:block/block", "textures": textures(shell, base),
                                                  "elements": LOWER})
        for state in ("on", "off"):
            write_json(MODELS / f"{bid}_upper_{state}.json", {
                "parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                "textures": textures(shell, base, state), "elements": UPPER_STAND + head})
        # modelo entero (para el ícono): la mitad de arriba se sube 16
        up = copy.deepcopy(UPPER_STAND + head)
        for el in up:
            el["from"][1] = rnd(el["from"][1] + 16)
            el["to"][1] = rnd(el["to"][1] + 16)
            if "rotation" in el:
                el["rotation"]["origin"][1] = rnd(el["rotation"]["origin"][1] + 16)
        write_json(MODELS / f"{bid}_item.json", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                                                 "textures": textures(shell, base, "on"), "elements": LOWER + up})
        variants = {}
        for facing, rot in FACINGS.items():
            for half in ("lower", "upper"):
                for on in ("false", "true"):
                    model = f"{bid}_lower" if half == "lower" else f"{bid}_upper_{'on' if on == 'true' else 'off'}"
                    entry = {"model": f"beautifuldecor:block/{model}"}
                    if rot:
                        entry["y"] = rot
                    variants[f"facing={facing},half={half},on={on}"] = entry
        write_json(STATES / f"{bid}.json", {"variants": variants})
        item = {"parent": f"beautifuldecor:block/{bid}_item",
                "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.38, 0.38, 0.38]}}}
        write_json(ITEMS / f"{bid}.json", item)
        NEW_ITEMS[bid] = item
        write_json(LOOT / f"{bid}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{bid}"}],
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"beautifuldecor:{bid}",
                            "properties": {"half": "lower"}}, {"condition": "minecraft:survives_explosion"}]}]})
        for kind in ("block", "item"):
            LANG_EN[f"{kind}.beautifuldecor.{bid}"] = en
            LANG_ES[f"{kind}.beautifuldecor.{bid}"] = es
    for code, extra in (("en_us", LANG_EN), ("es_es", LANG_ES)):
        path = LANG / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        write_json(path, data)
    path = ASSETS.parents[4] / "tools/v182/item_models_src.json"
    src = json.loads(path.read_text(encoding="utf-8"))
    src.update(NEW_ITEMS)
    path.write_text(json.dumps(src, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"2.3.0: {len(VARIANTS)} ventiladores de pie generados")


if __name__ == "__main__":
    main()
