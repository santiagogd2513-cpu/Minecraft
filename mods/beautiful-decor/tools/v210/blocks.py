#!/usr/bin/env python3
"""Genera los bloques de la 2.1.0: caja de herramientas abierta (toolbox_open) y cajones de cocina (11 maderas).

Uso (desde la raíz del proyecto):  python3 -m tools.v210.blocks
Escribe texturas, modelos, blockstates, loot tables, nombres en los dos idiomas y registra los ítems en
tools/v182/item_models_src.json (de ahí salen los íconos: python3 -m tools.v182.make_icons --only ... --write).
Los nombres deben coincidir con ModBlocks.java.
"""
import json

from ..v180.common import (ASSETS, FACINGS, ITEMS, LANG, LOOT, MODELS, STATES, Model, fill_noise, frame, new,
                           rect, save_tex, shade, write_json)

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]
ES_WOOD = {"oak": "roble", "spruce": "abeto", "birch": "abedul", "jungle": "jungla", "acacia": "acacia",
           "dark_oak": "roble oscuro", "mangrove": "mangle", "cherry": "cerezo", "bamboo": "bambú",
           "crimson": "carmesí", "warped": "distorsionada"}
EN_WOOD = {w: w.replace("_", " ").title() for w in WOODS}
TRIM = {w: (f"minecraft:block/stripped_{w}_log" if w not in ("bamboo", "crimson", "warped")
            else {"bamboo": "minecraft:block/stripped_bamboo_block",
                  "crimson": "minecraft:block/stripped_crimson_stem",
                  "warped": "minecraft:block/stripped_warped_stem"}[w]) for w in WOODS}
KNOB = "beautifuldecor:block/handle_metal"
MARBLE = "beautifuldecor:block/kitchen_counter"
FACES = ("north", "south", "east", "west", "up", "down")

LANG_EN, LANG_ES = {}, {}
NEW_ITEMS = {}          # id -> modelo de ítem ORIGINAL (para los íconos)


def names(block_id, en, es):
    for kind in ("block", "item"):
        LANG_EN[f"{kind}.beautifuldecor.{block_id}"] = en
        LANG_ES[f"{kind}.beautifuldecor.{block_id}"] = es


def loot(block_id, copy_name=False):
    entry = {"type": "minecraft:item", "name": f"beautifuldecor:{block_id}"}
    if copy_name:
        entry["functions"] = [{"function": "minecraft:copy_name", "source": "block_entity"}]
    write_json(LOOT / f"{block_id}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def y_states(block_id, model_for, props):
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
    write_json(STATES / f"{block_id}.json", {"variants": variants})


def item_model(block_id, parent_model):
    """Modelo de ítem provisional (el de bloque); make_icons lo cambia por el sprite y guarda este en item_models_src."""
    write_json(ITEMS / f"{block_id}.json", {"parent": f"beautifuldecor:block/{parent_model}"})
    NEW_ITEMS[block_id] = {"parent": f"beautifuldecor:block/{parent_model}"}


# ====================================================================================================== caja de herramientas
RED = (200, 38, 36, 255)
RED_IN = (146, 26, 28, 255)


def toolbox_textures():
    # chapa roja con un relieve horizontal y remaches en las esquinas
    im = fill_noise(new(16), RED, 4, 2101, vgrad=14)
    rect(im, 0, 0, 15, 0, shade(RED, 40))
    rect(im, 0, 15, 15, 15, shade(RED, -60))
    rect(im, 0, 4, 15, 4, shade(RED, -46))
    rect(im, 0, 5, 15, 5, shade(RED, 36))
    px = im.load()
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[x, y] = (226, 214, 190, 255)
        px[x + (1 if x < 8 else -1), y] = shade(RED, -20)
    save_tex("toolbox_red", im)
    save_tex("toolbox_red_in", fill_noise(new(16), RED_IN, 4, 2102, vgrad=10))
    # alfombrilla ranurada del fondo
    floor = fill_noise(new(16), (52, 54, 60, 255), 3, 2103)
    for y in range(0, 16, 2):
        rect(floor, 0, y, 15, y, shade((52, 54, 60, 255), -14))
    save_tex("toolbox_floor", floor)
    # metal cepillado
    silver = fill_noise(new(16), (178, 184, 196, 255), 5, 2104, vgrad=24)
    for y in range(1, 16, 3):
        rect(silver, 0, y, 15, y, shade((178, 184, 196, 255), 22))
    frame(silver, 0, 0, 15, 15, (118, 124, 136, 255))
    save_tex("toolbox_silver", silver)
    # borde superior de las paredes
    save_tex("toolbox_rim", fill_noise(new(16), (214, 60, 56, 255), 3, 2105))
    # madera del martillo
    wood = fill_noise(new(16), (150, 98, 52, 255), 5, 2106)
    for x in range(1, 16, 3):
        rect(wood, x, 0, x, 15, shade((150, 98, 52, 255), -20))
    save_tex("toolbox_wood", wood)
    # amarillo con franjas negras (destornillador y cinta métrica)
    yellow = fill_noise(new(16), (244, 196, 36, 255), 3, 2107, vgrad=16)
    rect(yellow, 0, 6, 15, 8, (36, 34, 30, 255))
    rect(yellow, 0, 11, 15, 11, (36, 34, 30, 255))
    save_tex("toolbox_yellow", yellow)
    save_tex("toolbox_iron", fill_noise(new(16), (78, 82, 92, 255), 5, 2108, vgrad=26))
    save_tex("toolbox_blue", fill_noise(new(16), (40, 94, 204, 255), 4, 2109, vgrad=18))
    # interior de la tapa: tela oscura con tres bolsillos elásticos
    lid = fill_noise(new(16), (120, 22, 26, 255), 3, 2110)
    for x0 in (1, 6, 11):
        rect(lid, x0, 3, x0 + 3, 12, (58, 60, 68, 255))
        rect(lid, x0, 3, x0 + 3, 3, (96, 100, 112, 255))
        rect(lid, x0, 12, x0 + 3, 12, (34, 36, 42, 255))
    save_tex("toolbox_lid_in", lid)


def toolbox_model():
    m = Model()
    red, red_in = m.tex("red", "toolbox_red"), m.tex("redin", "toolbox_red_in")
    floor, silver = m.tex("floor", "toolbox_floor"), m.tex("silver", "toolbox_silver")
    rim, wood = m.tex("rim", "toolbox_rim"), m.tex("wood", "toolbox_wood")
    yellow, iron = m.tex("yellow", "toolbox_yellow"), m.tex("iron", "toolbox_iron")
    blue, lid_in = m.tex("blue", "toolbox_blue"), m.tex("lidin", "toolbox_lid_in")

    # cuerpo
    m.box([1, 0, 3], [15, 1, 12], red, up=floor)
    m.box([1, 1, 3], [15, 6.5, 4], red, south=red_in, up=rim)
    m.box([1, 1, 11], [15, 6.5, 12], red, north=red_in, up=rim)
    m.box([1, 1, 4], [2, 6.5, 11], red, east=red_in, up=rim)
    m.box([14, 1, 4], [15, 6.5, 11], red, west=red_in, up=rim)
    m.box([6.5, 4.2, 2.5], [9.5, 6.2, 3], silver)                      # cierre
    m.box([7.3, 5.0, 2.4], [8.7, 5.6, 2.5], iron)
    m.box([0.4, 2.5, 6], [1, 5, 9], silver)                            # asas laterales
    m.box([15, 2.5, 6], [15.6, 5, 9], silver)
    m.box([2, 6, 11.5], [4, 7.5, 12.8], silver)                        # bisagras
    m.box([12, 6, 11.5], [14, 7.5, 12.8], silver)

    # tapa abierta: placa vertical con reborde, inclinada 22,5 grados hacia atrás desde la bisagra
    hinge = {"origin": [8, 6.5, 12], "axis": "x", "angle": 22.5}
    m.box([1, 6.5, 12], [15, 14.5, 13], red, north=lid_in, rot=hinge)
    m.box([1, 6.5, 10.8], [2, 14.5, 12], red, east=red_in, rot=hinge)
    m.box([14, 6.5, 10.8], [15, 14.5, 12], red, west=red_in, rot=hinge)
    m.box([2, 13.5, 10.8], [14, 14.5, 12], red, down=red_in, rot=hinge)
    m.box([4.5, 10, 13], [11.5, 11.5, 14.2], silver, rot=hinge)         # asa de la tapa

    # herramientas
    m.box([2, 1, 4.6], [8.5, 2.2, 5.9], wood)                           # martillo
    m.box([8.5, 1, 4.0], [11.3, 3.2, 6.5], iron)
    m.box([11.3, 1, 4.7], [12.6, 2, 5.8], iron)
    m.box([2, 1, 7.0], [5.4, 2.6, 8.6], yellow)                         # destornillador
    m.box([5.4, 1, 7.4], [10, 1.7, 8.2], silver)
    m.box([2.5, 1, 9.0], [9.5, 1.8, 10.4], silver)                      # llave inglesa
    m.box([9.5, 1, 8.5], [11.5, 2.4, 10.9], silver)
    m.box([11.8, 1, 6.4], [13.8, 3.4, 8.8], yellow)                     # cinta métrica
    m.box([11, 1, 8.9], [12.4, 1.8, 10.9], silver)                      # alicates
    m.box([12.4, 1, 9.0], [13.9, 1.9, 9.9], blue)
    m.box([12.4, 1, 9.9], [13.9, 1.9, 10.8], blue)
    for x, z in ((3.0, 5.0), (3.8, 6.0), (6.0, 6.2)):                   # tornillos sueltos
        m.box([x, 1, z], [x + 0.8, 1.8, z + 0.8], silver)
    m.save("toolbox_open", "toolbox_red")


def gen_toolbox():
    toolbox_textures()
    toolbox_model()
    y_states("toolbox_open", lambda c: "beautifuldecor:block/toolbox_open", [])
    item_model("toolbox_open", "toolbox_open")
    loot("toolbox_open")
    names("toolbox_open", "Open Toolbox", "Caja de herramientas abierta")


# ====================================================================================================== cajones de cocina
def box(frm, to, tex):
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    faces = {f: {"uv": [0, 0, round(min(sizes[f][0], 16), 3), round(min(sizes[f][1], 16), 3)], "texture": tex}
             for f in FACES}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


# alturas de los tres cajones (abajo, medio, arriba) dentro del hueco y 3..13
DRAWERS = [(3.0, 6.1), (6.5, 9.6), (10.0, 13.0)]
PULL = [4.0, 5.5, 7.0]               # cuánto sale cada cajón cuando está abierto


def drawer_elements(open_):
    els = [
        box([0, 14, 0], [16, 16, 16], "#top"),                      # encimera de mármol
        box([0, 0, 1.5], [16, 2, 16], "#trim"),                     # zócalo
        box([0, 2, 0], [2, 14, 16], "#trim"),                       # costado izquierdo
        box([14, 2, 0], [16, 14, 16], "#trim"),                     # costado derecho
        box([2, 2, 14.5], [14, 14, 16], "#planks"),                 # fondo
        box([2, 13, 0], [14, 14, 14.5], "#planks"),                 # travesaño superior
        box([2, 2, 0], [14, 3, 14.5], "#trim"),                     # travesaño inferior
    ]
    for (y0, y1), pull in zip(DRAWERS, PULL):
        z = -pull if open_ else 0.0
        els.append(box([2.2, y0, 0 + z], [13.8, y1, 1.2 + z], "#planks"))                    # frente
        els.append(box([5, (y0 + y1) / 2 - 0.5, -0.8 + z], [11, (y0 + y1) / 2 + 0.5, 0 + z], "#knob"))   # tirador
        if open_:
            els.append(box([2.8, y0 + 0.2, 1.2 + z], [13.2, y0 + 0.8, 13.5 + z], "#trim"))               # fondo del cajón
            els.append(box([2.8, y0 + 0.8, 1.2 + z], [3.6, y1 - 0.4, 13.5 + z], "#trim"))                # lado izquierdo
            els.append(box([12.4, y0 + 0.8, 1.2 + z], [13.2, y1 - 0.4, 13.5 + z], "#trim"))              # lado derecho
            els.append(box([3.6, y0 + 0.8, 12.7 + z], [12.4, y1 - 0.4, 13.5 + z], "#trim"))              # trasera
    return els


def gen_drawers():
    for name, open_ in (("kitchen_drawers_closed_base", False), ("kitchen_drawers_open_base", True)):
        write_json(MODELS / f"{name}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": "#planks"},
            "elements": drawer_elements(open_)})
    for w in WOODS:
        n = f"kitchen_drawers_{w}"
        for state, base in (("closed", "kitchen_drawers_closed_base"), ("open", "kitchen_drawers_open_base")):
            write_json(MODELS / f"{n}_{state}.json", {
                "parent": f"beautifuldecor:block/{base}",
                "textures": {"particle": f"minecraft:block/{w}_planks", "planks": f"minecraft:block/{w}_planks",
                             "trim": TRIM[w], "knob": KNOB, "top": MARBLE}})
        y_states(n, lambda c, n=n: f"beautifuldecor:block/{n}_{'open' if c['open'] == 'true' else 'closed'}", ["open"])
        item_model(n, f"{n}_closed")
        loot(n, copy_name=True)
        names(n, f"{EN_WOOD[w]} Kitchen Drawers", f"Cajones de cocina de {ES_WOOD[w]}")
    LANG_EN["container.beautifuldecor.kitchen_drawers"] = "Kitchen Drawers"
    LANG_ES["container.beautifuldecor.kitchen_drawers"] = "Cajones de cocina"


# ====================================================================================================== registro
def merge_lang():
    for code, extra in (("en_us", LANG_EN), ("es_es", LANG_ES)):
        path = LANG / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        write_json(path, data)


def register_icon_sources():
    path = ASSETS.parents[4] / "tools/v182/item_models_src.json"
    src = json.loads(path.read_text(encoding="utf-8"))
    src.update(NEW_ITEMS)
    path.write_text(json.dumps(src, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    gen_toolbox()
    gen_drawers()
    merge_lang()
    register_icon_sources()
    print(f"2.1.0: {len(NEW_ITEMS)} bloques nuevos ({', '.join(list(NEW_ITEMS)[:3])}...)")


if __name__ == "__main__":
    main()
