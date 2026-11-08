#!/usr/bin/env python3
"""Genera los assets de la versión 1.5.0 de Beautiful Decor:

  * kitchen_sink_<color>   lavaplatos de cocina (white, light_gray, dark_gray, black)
  * hs_desktop             computador de escritorio HS (torre + monitor + teclado + mouse);
                           clic derecho = encender/apagar la pantalla
  * sofás en esquina       modelos "corner" para los 13 sofás (rojo, azul y las 11 maderas) y sus
                           blockstates nuevos (propiedad corner = none | left | right)

Los nombres deben coincidir con ModBlocks.java. Requiere Pillow (pip install pillow).
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

SINK_COLORS = ["white", "light_gray", "dark_gray", "black"]
ES_COLOR = {"white": "blanco", "light_gray": "gris claro", "dark_gray": "gris oscuro", "black": "negro"}
EN_COLOR = {"white": "White", "light_gray": "Light Gray", "dark_gray": "Dark Gray", "black": "Black"}
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("north", "south", "east", "west", "up", "down")
lang_en, lang_es = {}, {}


# ------------------------------------------------------------------ utilidades
def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tex(name):
    return f"beautifuldecor:block/{name}"


def clamp(v):
    return max(0, min(255, int(v)))


def add(c, d):
    return tuple(clamp(v + d) for v in c[:3]) + (255,)


def save(name, im):
    TEX.mkdir(parents=True, exist_ok=True)
    im.save(TEX / f"{name}.png")


def box(frm, to, default, full=False, **faces):
    """Caja con la misma textura en todas las caras; faces permite cambiar o quitar (None) una cara.
    full=True estira la textura completa en cada cara."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    out = {}
    for f in FACES:
        t = faces.get(f, default)
        if t is None:
            continue
        uv = [0, 0, 16, 16] if full else [0, 0, round(min(sizes[f][0], 16), 3), round(min(sizes[f][1], 16), 3)]
        out[f] = {"uv": uv, "texture": t}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": out}


def write_parent(name, elements, textures=None, display=None, particle="#body"):
    model = {"parent": "minecraft:block/block", "textures": {"particle": particle}, "elements": elements}
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
            key = ",".join([f"facing={facing}"] + [f"{k}={v}" for k, v in sorted(combo.items())])
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


def glyphs(im, rows, x0, y0, color, flip=False):
    px = im.load()
    for ry, row in enumerate(rows):
        for rx, bit in enumerate(row):
            if bit == "1":
                px[x0 + rx, y0 + ry] = color


H4x7 = ["1001", "1001", "1001", "1111", "1001", "1001", "1001"]
S4x7 = ["0111", "1000", "1000", "0110", "0001", "0001", "1110"]
H3x5 = ["101", "101", "111", "101", "101"]
S3x5 = ["111", "100", "111", "001", "111"]


# ================================================================== LAVAPLATOS
def gen_sink_textures():
    # fondo de la tina: acero con el desagüe en el centro de la zona visible (8x9 px)
    rnd = random.Random(21)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = add((168, 172, 178), rnd.randint(-4, 4) - 5 * (y / 15))
    for y in range(16):
        for x in range(16):
            dist = ((x - 3.5) ** 2 + (y - 4) ** 2) ** 0.5
            if dist <= 1.3:
                px[x, y] = (30, 32, 36, 255)
            elif 1.3 < dist <= 2.3:
                px[x, y] = (118, 122, 128, 255)
    save("kitchen_sink_basin", im)

    # escurridor: acero con ranuras longitudinales
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = add((184, 188, 194), 4 - 6 * (x / 15))
            if y % 3 == 1:
                c = add((184, 188, 194), -30)
            px[x, y] = c
    save("kitchen_sink_drain", im)


def sink_elements():
    els = []
    # --- mueble inferior (bajo la tina) y puertas
    els.append(box([0, 0, 1], [16, 7.5, 16], "#body"))
    # --- cuerpo alrededor de la tina (y 7.5..12); la tina ocupa x 2..10, z 3..12
    els.append(box([0, 7.5, 1], [16, 12, 3], "#body", south="#steel"))                 # frente
    els.append(box([0, 7.5, 12], [16, 12, 16], "#body", north="#steel"))               # fondo
    els.append(box([0, 7.5, 3], [2, 12, 12], "#body", east="#steel"))                  # lado izquierdo
    els.append(box([10, 7.5, 3], [16, 12, 12], "#body", west="#steel"))                # escurridor (bloque)
    # --- fondo de la tina
    els.append(box([2, 7, 3], [10, 7.5, 12], "#steel", up="#basin"))
    # --- cubierta (y 12..13) con un aro de acero alrededor de la tina
    els.append(box([0, 12, 0], [2, 13, 3], "#body", up="#top"))
    els.append(box([2, 12, 0], [10, 13, 3], "#body", up="#top", south="#steel"))
    els.append(box([10, 12, 0], [16, 13, 3], "#body", up="#top", south=None))
    els.append(box([0, 12, 3], [2, 13, 12], "#body", up="#top", east="#steel"))
    els.append(box([10, 12, 3], [16, 13, 12], "#body", up="#drain", west="#steel"))
    els.append(box([0, 12, 12], [2, 13, 16], "#body", up="#top"))
    els.append(box([2, 12, 12], [10, 13, 16], "#body", up="#top", north="#steel"))
    els.append(box([10, 12, 12], [16, 13, 16], "#body", up="#top", north=None))
    # --- puertas del mueble
    els.append(box([1, 1, 0.3], [7.8, 11, 1], "#body", full=True, south=None))
    els.append(box([8.2, 1, 0.3], [15, 11, 1], "#body", full=True, south=None))
    els.append(box([6.4, 5, -0.4], [7.2, 8.5, 0.3], "#steel"))                         # manijas verticales
    els.append(box([8.8, 5, -0.4], [9.6, 8.5, 0.3], "#steel"))
    # --- grifo cromado (gira sobre el fondo de la tina)
    els.append(box([5.5, 13, 13], [7.5, 14, 15], "#chrome"))                           # base
    els.append(box([6, 14, 13.5], [7, 16, 14.5], "#chrome"))                           # columna
    els.append(box([6, 15, 9.5], [7, 16, 14.5], "#chrome"))                            # pico horizontal
    els.append(box([6, 14.2, 9.5], [7, 15, 10.5], "#chrome"))                          # boquilla
    els.append(box([8.8, 13, 13.3], [9.8, 14.6, 14.3], "#chrome"))                     # palanca
    return els


def gen_sinks():
    write_parent("kitchen_sink", sink_elements())
    for c in SINK_COLORS:
        t = {"body": tex(f"kitchen_body_{c}"), "top": tex(f"kitchen_body_{c}"), "steel": tex("kitchen_steel"),
             "basin": tex("kitchen_sink_basin"), "drain": tex("kitchen_sink_drain"),
             "chrome": tex("handle_metal")}
        name = f"kitchen_sink_{c}"
        write_child(name, "kitchen_sink", t)
        write_json(STATES / f"{name}.json", y_states(lambda combo, n=name: f"beautifuldecor:block/{n}", []))
        write_json(ITEMS / f"{name}.json", {"parent": f"beautifuldecor:block/{name}"})
        loot(name)
        names(name, f"Kitchen Sink ({EN_COLOR[c]})", f"Lavaplatos ({ES_COLOR[c]})")


# ================================================================== COMPUTADOR HS
def gen_pc_textures():
    # carcasa oscura genérica con borde
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = 4 - 8 * (y / 15)
            if x in (0, 15) or y in (0, 15):
                d -= 12
            px[x, y] = add((44, 46, 52), d)
    save("hs_pc_case", im)

    # pantalla encendida (recorte 11x10): fondo azul degradado + logo HS + barra inferior
    def screen(on):
        im = Image.new("RGBA", (16, 16), (12, 13, 16, 255))
        px = im.load()
        for y in range(10):
            for x in range(11):
                px[x, y] = (22, 23, 28, 255)                       # marco
        for y in range(1, 8):
            for x in range(1, 10):
                if on:
                    px[x, y] = (32 + y * 5, 92 + y * 9, 214 - y * 3, 255)
                else:
                    c = 14 + (3 if (x - y) in (2, 3) else 0)
                    px[x, y] = (c, c + 1, c + 4, 255)
        if on:
            glyphs(im, H3x5, 3, 2, (255, 255, 255, 255))
            glyphs(im, S3x5, 7 - 0, 2, (255, 255, 255, 255))
            for x in range(1, 10):
                px[x, 7] = (210, 214, 224, 255)                    # barra de tareas
            px[1, 7] = (80, 200, 255, 255)
            px[2, 7] = (255, 200, 80, 255)
        # chin: LED diminuto
        px[5, 9] = (90, 255, 140, 255) if on else (70, 72, 78, 255)
        return im

    save("hs_pc_screen_on", screen(True))
    save("hs_pc_screen_off", screen(False))

    # lateral de la torre (recorte 12x12): panel oscuro con el logo HS grande
    def side(on):
        im = Image.new("RGBA", (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                d = 3 - 6 * (y / 15)
                if x in (0, 11) or y in (0, 11):
                    d -= 14
                px[x, y] = add((34, 36, 42), d)
        accent = (70, 190, 255, 255) if on else (96, 108, 120, 255)
        glyphs(im, H4x7, 1, 2, accent)
        glyphs(im, S4x7, 6, 2, accent)
        for x in range(1, 11):
            px[x, 10] = accent if on else (60, 66, 74, 255)        # tira de luz inferior
        return im

    save("hs_pc_side_on", side(True))
    save("hs_pc_side_off", side(False))

    # frente de la torre (recorte 3x12)
    def front(on):
        im = Image.new("RGBA", (16, 16), (44, 46, 52, 255))
        px = im.load()
        for y in range(12):
            px[0, y] = (30, 31, 36, 255)
            px[2, y] = (30, 31, 36, 255)
        for y in (1, 3):
            for x in range(3):
                px[x, y] = (14, 15, 18, 255)                        # bahías de unidad óptica
        px[1, 6] = (150, 154, 160, 255)
        px[1, 8] = (210, 214, 220, 255) if on else (120, 124, 130, 255)   # botón de encendido
        px[1, 10] = (90, 255, 140, 255) if on else (50, 52, 58, 255)      # LED
        return im

    save("hs_pc_front_on", front(True))
    save("hs_pc_front_off", front(False))

    # teclado (recorte 8x4)
    im = Image.new("RGBA", (16, 16), (30, 31, 36, 255))
    px = im.load()
    for y in range(4):
        for x in range(8):
            if (x + y) % 2 == 0 or y == 3:
                px[x, y] = (150, 154, 162, 255)
            else:
                px[x, y] = (112, 116, 124, 255)
            if y == 3 and 1 < x < 6:
                px[x, y] = (170, 174, 182, 255)
    save("hs_pc_keys", im)

    # mouse (recorte 2x3)
    im = Image.new("RGBA", (16, 16), (60, 62, 68, 255))
    px = im.load()
    px[0, 0] = (36, 37, 42, 255)
    px[1, 0] = (36, 37, 42, 255)
    px[0, 1] = (86, 88, 96, 255)
    px[1, 1] = (86, 88, 96, 255)
    save("hs_pc_mouse", im)


def pc_elements():
    els = [
        # monitor (11x10) con su pantalla en la cara norte
        box([0.5, 3, 7], [11.5, 13, 8.6], "#case", north="#screen"),
        box([5, 0.8, 7.5], [7, 3, 8.2], "#case"),                                  # cuello
        box([3, 0, 5.5], [9, 0.8, 9.5], "#case"),                                  # base
        # teclado y mouse
        box([1.5, 0, 1], [9.5, 1, 5], "#case", up="#keys"),
        box([10, 0, 2], [12, 1, 5], "#case", up="#mouse"),
        # torre (3 de ancho, 12 de alto y 12 de profundo)
        box([12.5, 0, 3], [15.5, 12, 15], "#case", north="#front", east="#side", west="#side"),
    ]
    return els


def gen_pc():
    write_parent("hs_pc", pc_elements(), particle="#case")
    for state in ("on", "off"):
        write_child(f"hs_desktop_{state}", "hs_pc", {
            "case": tex("hs_pc_case"), "screen": tex(f"hs_pc_screen_{state}"),
            "front": tex(f"hs_pc_front_{state}"), "side": tex(f"hs_pc_side_{state}"),
            "keys": tex("hs_pc_keys"), "mouse": tex("hs_pc_mouse"), "particle": tex("hs_pc_case")})
    write_json(STATES / "hs_desktop.json", y_states(
        lambda c: f"beautifuldecor:block/hs_desktop_{'on' if c['on'] == 'true' else 'off'}", ["on"]))
    write_json(ITEMS / "hs_desktop.json", {
        "parent": "beautifuldecor:block/hs_desktop_on",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
        }})
    loot("hs_desktop")
    names("hs_desktop", "HS Desktop Computer", "Computador de escritorio HS")


# ================================================================== SOFÁS EN ESQUINA
SOFA_WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
              "crimson", "warped"]
SOFA_FABRICS = ["red", "blue"]


def mirror_x(el):
    """Refleja un elemento en el eje X del bloque (pasa de esquina-derecha a esquina-izquierda)."""
    el = json.loads(json.dumps(el))
    x1, x2 = el["from"][0], el["to"][0]
    el["from"][0], el["to"][0] = round(16 - x2, 3), round(16 - x1, 3)
    faces = el["faces"]
    if "east" in faces and "west" in faces:
        faces["east"], faces["west"] = faces["west"], faces["east"]
    elif "east" in faces:
        faces["west"] = faces.pop("east")
    elif "west" in faces:
        faces["east"] = faces.pop("west")
    return el


def plain_box(frm, to, texture, **faces):
    """Caja de UV automático (igual que sofa_base)."""
    out = {}
    for f in FACES:
        t = faces.get(f, texture)
        if t is not None:
            out[f] = {"texture": t}
    return {"from": frm, "to": to, "faces": out}


def corner_wood(left_connected):
    """Sofá de madera, esquina con el respaldo extra en el costado derecho (modelo mirando al norte)."""
    lx = 0 if left_connected else 2
    els = [
        plain_box([1, 0, 3], [2.5, 2, 4.5], "#trim"),
        plain_box([1, 0, 11.5], [2.5, 2, 13], "#trim"),
        plain_box([11.5, 0, 11.5], [13, 2, 13], "#trim"),
        plain_box([11.5, 0, 0.5], [13, 2, 2], "#trim"),
        plain_box([0, 2, 2], [14, 6, 14], "#planks"),            # base
        plain_box([2, 2, 0], [14, 6, 2], "#planks"),             # base: tramo hacia el sofá vecino
        plain_box([0, 6, 12.2], [14, 14, 14], "#planks"),        # respaldo trasero
        plain_box([12.2, 6, 0], [14, 14, 12.2], "#planks"),      # respaldo lateral (continúa el del vecino)
        plain_box([lx, 6, 3.5], [12.2, 8.8, 12.2], "#cushion"),  # asiento
        plain_box([3.5, 6, 0], [12.2, 8.8, 3.5], "#cushion"),    # asiento: tramo hacia el vecino
        plain_box([lx, 8.8, 10], [12.2, 13.5, 12.2], "#cushion"),
        plain_box([10, 8.8, 0], [12.2, 13.5, 10], "#cushion"),
    ]
    if not left_connected:
        els += [plain_box([0, 6, 2], [2, 10.5, 14], "#planks"),
                plain_box([0, 10.5, 2.8], [2, 11.3, 13.2], "#cushion")]
    return els


def fabric_box(frm, to, tex_all="#fabric", down=None, **faces):
    """Caja con UV explícito como los sofás rojo y azul originales."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    out = {}
    for f in FACES:
        t = faces.get(f, tex_all)
        if f == "down":
            t = down
        if t is None:
            continue
        out[f] = {"uv": [0, 0, round(sizes[f][0], 3), round(sizes[f][1], 3)], "texture": t}
    return {"from": frm, "to": to, "faces": out}


def corner_fabric(left_connected):
    els = [
        fabric_box([0, 0, 2], [14, 6, 14], down="#wood"),
        fabric_box([2, 0, 0], [14, 6, 2], down="#wood"),
        fabric_box([0, 6, 12], [14, 12, 14]),
        fabric_box([12, 6, 0], [14, 12, 12]),
        fabric_box([1, 0, 3], [2.5, 2, 4.5], tex_all="#wood", down="#wood"),
        fabric_box([1, 0, 11.5], [2.5, 2, 13], tex_all="#wood", down="#wood"),
        fabric_box([11.5, 0, 11.5], [13, 2, 13], tex_all="#wood", down="#wood"),
        fabric_box([11.5, 0, 0.5], [13, 2, 2], tex_all="#wood", down="#wood"),
    ]
    if not left_connected:
        els.append(fabric_box([0, 6, 2], [2, 10, 12]))
    return els


def gen_sofa_corners():
    # --- modelos padre
    parents = {}
    for lc in (False, True):
        suffix = "_left_connected" if lc else ""
        right = corner_wood(lc)
        parents[f"sofa_base_corner_right{suffix}"] = (right, "#planks", None)
        parents[f"sofa_base_corner_left{'_right_connected' if lc else ''}"] = ([mirror_x(e) for e in right],
                                                                              "#planks", None)
        fright = corner_fabric(lc)
        parents[f"sofa_fabric_corner_right{suffix}"] = (fright, "#fabric", None)
        parents[f"sofa_fabric_corner_left{'_right_connected' if lc else ''}"] = (
            [mirror_x(e) for e in fright], "#fabric", None)
    for name, (els, particle, _) in parents.items():
        write_json(MODELS / f"{name}.json", {"parent": "minecraft:block/block",
                                             "textures": {"particle": particle}, "elements": els})

    # --- modelos hijos y blockstates
    def child_models(sofa, parent_prefix, textures):
        """Devuelve los 4 modelos hijos de esquina de este sofá."""
        out = {}
        for side in ("right", "left"):
            for connected in (False, True):
                if side == "right":
                    suffix = "_left_connected" if connected else ""
                    model = f"{sofa}_corner_right{suffix}"
                    parent = f"{parent_prefix}_corner_right{suffix}"
                else:
                    suffix = "_right_connected" if connected else ""
                    model = f"{sofa}_corner_left{suffix}"
                    parent = f"{parent_prefix}_corner_left{suffix}"
                write_json(MODELS / f"{model}.json", {"parent": f"beautifuldecor:block/{parent}",
                                                      "textures": textures})
                out[(side, connected)] = model
        return out

    def straight_model(sofa, lc, rc):
        if lc and rc:
            return f"{sofa}_both_connected"
        if lc:
            return f"{sofa}_left_connected"
        if rc:
            return f"{sofa}_right_connected"
        return sofa

    def write_state(sofa, corner_models):
        variants = {}
        for corner in ("none", "left", "right"):
            for facing, rot in FACINGS.items():
                for lc in (False, True):
                    for rc in (False, True):
                        key = f"corner={corner},facing={facing},left_connected={str(lc).lower()}," \
                              f"right_connected={str(rc).lower()}"
                        if corner == "none":
                            model = straight_model(sofa, lc, rc)
                        elif corner == "right":
                            model = corner_models[("right", lc)]      # el lado derecho es pared
                        else:
                            model = corner_models[("left", rc)]       # el lado izquierdo es pared
                        entry = {"model": f"beautifuldecor:block/{model}"}
                        if rot:
                            entry["y"] = rot
                        variants[key] = entry
        write_json(STATES / f"{sofa}.json", {"variants": variants})

    for wood in SOFA_WOODS:
        sofa = f"sofa_{wood}"
        textures = json.loads((MODELS / f"{sofa}.json").read_text(encoding="utf-8"))["textures"]
        write_state(sofa, child_models(sofa, "sofa_base", textures))
    for color in SOFA_FABRICS:
        sofa = f"sofa_{color}"
        textures = json.loads((MODELS / f"{sofa}.json").read_text(encoding="utf-8"))["textures"]
        write_state(sofa, child_models(sofa, "sofa_fabric", textures))


def merge_lang():
    for fname, extra in (("en_us.json", lang_en), ("es_es.json", lang_es)):
        path = LANG / fname
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    gen_sink_textures()
    gen_sinks()
    gen_pc_textures()
    gen_pc()
    gen_sofa_corners()
    merge_lang()
    print(f"Listo: {len(lang_en)} bloques nuevos y modelos de sofá en esquina.")
