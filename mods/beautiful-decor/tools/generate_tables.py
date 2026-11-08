#!/usr/bin/env python3
"""Genera modelos, blockstates, ítems, loot tables e idioma de las mesas (11 maderas cada una):

  * pedestal_table_<madera>  mesa de una sola pata (estilo restaurante)
  * table_<madera>           mesa clásica de cuatro patas (se une con las mesas vecinas)
  * writing_desk_<madera>    escritorio con un cajón grande que se abre con clic
  * shelf_table_<madera>     mesa con huequitos y repisas

Los nombres deben coincidir con ModBlocks.java.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
MODELS, ITEMS, STATES = ASSETS / "models/block", ASSETS / "models/item", ASSETS / "blockstates"
LOOT, LANG = DATA / "loot_tables/blocks", ASSETS / "lang"

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
         "bamboo", "crimson", "warped"]
ES_WOOD = {"oak": "roble", "spruce": "abeto", "birch": "abedul", "jungle": "jungla", "acacia": "acacia",
           "dark_oak": "roble oscuro", "mangrove": "mangle", "cherry": "cerezo", "bamboo": "bambú",
           "crimson": "carmesí", "warped": "distorsionada"}
EN_WOOD = {w: w.replace("_", " ").title() for w in WOODS}
TRIM = {w: (f"minecraft:block/stripped_{w}_log" if w not in ("bamboo", "crimson", "warped")
            else {"bamboo": "minecraft:block/stripped_bamboo_block",
                  "crimson": "minecraft:block/stripped_crimson_stem",
                  "warped": "minecraft:block/stripped_warped_stem"}[w]) for w in WOODS}
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("north", "south", "east", "west", "up", "down")
KNOB = "beautifuldecor:block/handle_metal"
lang_en, lang_es = {}, {}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def box(frm, to, tex):
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    faces = {f: {"uv": [0, 0, round(min(sizes[f][0], 16), 3), round(min(sizes[f][1], 16), 3)], "texture": tex}
             for f in FACES}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


def base_model(name, elements):
    write_json(MODELS / f"{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#planks"},
        "elements": elements})


def child(name, parent, wood):
    write_json(MODELS / f"{name}.json", {
        "parent": f"beautifuldecor:block/{parent}",
        "textures": {"particle": f"minecraft:block/{wood}_planks", "planks": f"minecraft:block/{wood}_planks",
                     "trim": TRIM[wood], "knob": KNOB}})


def loot(name):
    write_json(LOOT / f"{name}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def y_states(model_for, props):
    """variants para facing + combinaciones de propiedades booleanas."""
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


def names(name, en, es):
    lang_en[f"block.beautifuldecor.{name}"] = en
    lang_es[f"block.beautifuldecor.{name}"] = es


# ---------------------------------------------------------------- mesa de una pata
def gen_pedestal():
    els = [
        box([3, 0, 3], [13, 1.5, 13], "#trim"),            # base
        box([4.5, 1.5, 4.5], [11.5, 3, 11.5], "#trim"),    # pie ancho
        box([6.5, 3, 6.5], [9.5, 12, 9.5], "#trim"),       # columna
        box([5.5, 12, 5.5], [10.5, 13, 10.5], "#trim"),    # soporte del tablero
        # tablero octogonal (sin solapar caras)
        box([0, 13, 3], [16, 16, 13], "#planks"),
        box([2, 13, 1], [14, 16, 3], "#planks"), box([2, 13, 13], [14, 16, 15], "#planks"),
        box([4, 13, 0], [12, 16, 1], "#planks"), box([4, 13, 15], [12, 16, 16], "#planks"),
    ]
    base_model("pedestal_table_base", els)
    for w in WOODS:
        n = f"pedestal_table_{w}"
        child(n, "pedestal_table_base", w)
        write_json(STATES / f"{n}.json", y_states(lambda c, n=n: f"beautifuldecor:block/{n}", []))
        write_json(ITEMS / f"{n}.json", {"parent": f"beautifuldecor:block/{n}"})
        loot(n)
        names(n, f"{EN_WOOD[w]} Pedestal Table", f"Mesa de una pata de {ES_WOOD[w]}")


# ---------------------------------------------------------------- mesa de cuatro patas
LEGS = {"nw": ([1, 0, 1], [3.5, 13, 3.5]), "ne": ([12.5, 0, 1], [15, 13, 3.5]),
        "sw": ([1, 0, 12.5], [3.5, 13, 15]), "se": ([12.5, 0, 12.5], [15, 13, 15])}
LEG_WHEN = {"nw": ("north", "west"), "ne": ("north", "east"), "sw": ("south", "west"), "se": ("south", "east")}


def top_elements():
    return [box([0, 13, 0], [16, 16, 16], "#planks"),
            box([0.5, 12.2, 0.5], [15.5, 13, 15.5], "#trim")]   # faldón fino bajo el tablero


def gen_table():
    base_model("table_top_base", top_elements())
    for k, (a, b) in LEGS.items():
        base_model(f"table_leg_{k}_base", [box(a, b, "#trim")])
    base_model("table_full_base", top_elements() + [box(a, b, "#trim") for a, b in LEGS.values()])
    for w in WOODS:
        n = f"table_{w}"
        child(f"{n}_top", "table_top_base", w)
        for k in LEGS:
            child(f"{n}_leg_{k}", f"table_leg_{k}_base", w)
        child(n, "table_full_base", w)
        parts = [{"apply": {"model": f"beautifuldecor:block/{n}_top"}}]
        for k, (d1, d2) in LEG_WHEN.items():
            parts.append({"when": {"AND": [{d1: "false"}, {d2: "false"}]},
                          "apply": {"model": f"beautifuldecor:block/{n}_leg_{k}"}})
        write_json(STATES / f"{n}.json", {"multipart": parts})
        write_json(ITEMS / f"{n}.json", {"parent": f"beautifuldecor:block/{n}"})
        loot(n)
        names(n, f"{EN_WOOD[w]} Table", f"Mesa de {ES_WOOD[w]}")


# ---------------------------------------------------------------- escritorio con cajón
def desk_elements(open_):
    z = -9 if open_ else 0      # el cajón sale 9 píxeles hacia el frente
    els = [
        box([0, 13, 0], [16, 16, 16], "#planks"),                   # tablero
        box([0.5, 0, 1], [2.5, 13, 15], "#trim"),                   # costado izquierdo
        box([13.5, 0, 1], [15.5, 13, 15], "#trim"),                 # costado derecho
        box([2.5, 1, 13.5], [13.5, 13, 15], "#planks"),             # panel trasero
        box([2.5, 4, 1.5], [13.5, 5, 13.5], "#trim"),               # travesaño inferior
        box([2.5, 12, 1.5], [13.5, 13, 13.5], "#trim"),             # riel superior del cajón
        # cajón
        box([2.5, 6.5, 1 + z], [13.5, 12, 2 + z], "#planks"),       # frente
        box([3, 6.5, 2 + z], [13, 7.3, 13 + z], "#trim"),           # fondo del cajón
        box([3, 7.3, 2 + z], [3.8, 11.5, 13 + z], "#trim"),         # lado izq.
        box([12.2, 7.3, 2 + z], [13, 11.5, 13 + z], "#trim"),       # lado der.
        box([3.8, 7.3, 12.2 + z], [12.2, 11.5, 13 + z], "#trim"),   # trasera del cajón
        box([6, 8.6, 0 + z], [10, 10, 1 + z], "#knob"),             # tirador
    ]
    return els


def gen_desk():
    base_model("writing_desk_closed_base", desk_elements(False))
    base_model("writing_desk_open_base", desk_elements(True))
    for w in WOODS:
        n = f"writing_desk_{w}"
        child(f"{n}_closed", "writing_desk_closed_base", w)
        child(f"{n}_open", "writing_desk_open_base", w)
        write_json(STATES / f"{n}.json", y_states(
            lambda c, n=n: f"beautifuldecor:block/{n}_{'open' if c['open'] == 'true' else 'closed'}", ["open"]))
        write_json(ITEMS / f"{n}.json", {"parent": f"beautifuldecor:block/{n}_closed"})
        loot(n)
        names(n, f"{EN_WOOD[w]} Writing Desk", f"Escritorio de {ES_WOOD[w]}")


# ---------------------------------------------------------------- mesa con repisas y huequitos
def shelf_table_elements():
    els = [
        box([0, 13, 0], [16, 16, 16], "#planks"),                  # tablero
        box([0, 0, 1], [1.5, 13, 15], "#trim"),                    # costado izquierdo
        box([14.5, 0, 1], [16, 13, 15], "#trim"),                  # costado derecho
        box([1.5, 0, 13.5], [14.5, 13, 15], "#planks"),            # fondo
        box([1.5, 0, 1.5], [14.5, 1.5, 13.5], "#planks"),          # base
        box([1.5, 6, 1.5], [14.5, 7.5, 13.5], "#planks"),          # repisa del medio
        box([7.25, 7.5, 1.5], [8.75, 13, 13.5], "#trim"),          # divisor: dos huequitos arriba
    ]
    # pequeños adornos dentro de los huequitos
    els += [
        box([2.5, 7.5, 4], [5.5, 10.5, 8], "minecraft:block/bookshelf"),
        box([10, 7.5, 5], [13, 9, 9.5], "minecraft:block/white_concrete"),
        box([3.5, 1.5, 4], [7.5, 4.5, 9], "minecraft:block/barrel_side"),
    ]
    return els


def gen_shelf_table():
    els = shelf_table_elements()
    base_model("shelf_table_base", els)
    for w in WOODS:
        n = f"shelf_table_{w}"
        child(n, "shelf_table_base", w)
        write_json(STATES / f"{n}.json", y_states(lambda c, n=n: f"beautifuldecor:block/{n}", []))
        write_json(ITEMS / f"{n}.json", {"parent": f"beautifuldecor:block/{n}"})
        loot(n)
        names(n, f"{EN_WOOD[w]} Shelf Table", f"Mesa con repisas de {ES_WOOD[w]}")


def merge_lang():
    for fname, extra in (("en_us.json", lang_en), ("es_es.json", lang_es)):
        path = LANG / fname
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    gen_pedestal()
    gen_table()
    gen_desk()
    gen_shelf_table()
    merge_lang()
    print(f"Listo: {len(lang_en)} bloques de mesas.")
