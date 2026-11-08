#!/usr/bin/env python3
"""Genera los assets de los ascensores de la 2.2.0: controlador, pantalla y puerta.

Uso (desde la raíz del proyecto):  python3 -m tools.v220.elevator
Escribe texturas, modelos, blockstates, loot tables, nombres/textos en los dos idiomas y registra los ítems en
tools/v182/item_models_src.json (de ahí salen los íconos: python3 -m tools.v182.make_icons --only ... --write).
Los nombres deben coincidir con ModBlocks.java.
"""
import json

from ..v180.common import (ASSETS, FACINGS, ITEMS, LANG, LOOT, MODELS, STATES, draw_pattern, fill_noise, frame, new, rect,
                           save_tex, shade, write_json, write_loot)

BLOCKS = ("elevator_controller", "elevator_display", "elevator_door")
NEW_ITEMS = {}

GRAPHITE = (46, 50, 58, 255)
STEEL = (172, 178, 188, 255)
CYAN = (56, 189, 248, 255)
GREEN = (52, 211, 153, 255)
LCD = (8, 12, 16, 255)


# ====================================================================================================== texturas
def controller_textures():
    # --- frente: pantalla LCD con flechas, teclado y un LED
    im = fill_noise(new(16), GRAPHITE, 3, 2201, vgrad=8)
    frame(im, 0, 0, 15, 15, shade(GRAPHITE, -26))
    frame(im, 1, 1, 14, 14, shade(GRAPHITE, 12))
    rect(im, 3, 2, 12, 6, shade(CYAN, -150))          # marco luminoso del LCD
    rect(im, 4, 3, 11, 5, LCD)
    draw_pattern(im, ["010", "111"], 4, 3, CYAN)      # flecha arriba
    draw_pattern(im, ["111", "010"], 9, 3, CYAN)      # flecha abajo
    rect(im, 7, 4, 8, 4, shade(CYAN, -90))
    for y in (8, 11):
        for x in (4, 7, 10):
            rect(im, x, y, x + 1, y + 1, (96, 102, 114, 255))
            rect(im, x, y, x + 1, y, (132, 138, 150, 255))
    rect(im, 13, 13, 13, 13, GREEN)
    rect(im, 12, 13, 12, 13, shade(GREEN, -120))
    save_tex("elevator_controller_front", im)

    # --- lados: chapa con rejilla de ventilación
    side = fill_noise(new(16), GRAPHITE, 3, 2202, vgrad=6)
    frame(side, 0, 0, 15, 15, shade(GRAPHITE, -26))
    frame(side, 1, 1, 14, 14, shade(GRAPHITE, 10))
    for y in range(4, 12, 2):
        rect(side, 4, y, 11, y, shade(GRAPHITE, -34))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rect(side, x, y, x, y, (150, 156, 168, 255))
    save_tex("elevator_controller_side", side)

    top = fill_noise(new(16), shade(GRAPHITE, 4), 3, 2203)
    frame(top, 0, 0, 15, 15, shade(GRAPHITE, -26))
    frame(top, 1, 1, 14, 14, shade(GRAPHITE, 14))
    rect(top, 6, 6, 9, 9, shade(GRAPHITE, -22))
    rect(top, 7, 7, 8, 8, (150, 156, 168, 255))
    save_tex("elevator_controller_top", top)


def display_textures():
    # La cara delantera usa solo la parte central de 12x12 (uv 2..14).
    im = fill_noise(new(16), STEEL, 3, 2211, vgrad=18)
    frame(im, 2, 2, 13, 13, (70, 74, 82, 255))
    rect(im, 3, 3, 12, 12, LCD)
    frame(im, 3, 3, 12, 12, shade(CYAN, -140))
    draw_pattern(im, ["00100", "01110", "11111"], 5, 4, CYAN)      # flecha arriba
    draw_pattern(im, ["11111", "01110", "00100"], 5, 9, CYAN)      # flecha abajo
    for x in (5, 7, 9):
        rect(im, x, 7, x + 1, 7, shade(CYAN, -80))                 # puntitos de los pisos
    rect(im, 11, 4, 11, 4, GREEN)
    save_tex("elevator_display_front", im)

    side = fill_noise(new(16), STEEL, 4, 2212, vgrad=22)
    for y in range(1, 16, 3):
        rect(side, 0, y, 15, y, shade(STEEL, 14))
    frame(side, 0, 0, 15, 15, (96, 102, 112, 255))
    save_tex("elevator_display_side", side)


def door_textures():
    """V2.3.0: la puerta mide 2 de alto (mitad de abajo y mitad de arriba), como una puerta normal del juego."""
    edge_color = (96, 102, 112, 255)
    dark = (62, 66, 74, 255)
    panel = shade(STEEL, -30)
    for half in ("lower", "upper"):
        up = half == "upper"
        top_y, bot_y = (0, 15)
        # ---- dos hojas que se juntan en el centro (puerta sola)
        door = fill_noise(new(16), STEEL, 3, 2221 + (7 if up else 0), vgrad=14)
        for y in range(1, 15, 2):
            rect(door, 1, y, 14, y, shade(STEEL, 9))                           # cepillado horizontal
        rect(door, 0, 0, 0, 15, edge_color)
        rect(door, 15, 0, 15, 15, edge_color)
        rect(door, 7, 0, 8, 15, dark)                                           # unión de las dos hojas
        rect(door, 6, 0, 6, 15, shade(STEEL, 20))
        rect(door, 9, 0, 9, 15, shade(STEEL, 20))
        if up:
            rect(door, 0, top_y, 15, top_y, (84, 90, 100, 255))                 # canto de arriba de la puerta
        else:
            rect(door, 0, bot_y, 15, bot_y, (84, 90, 100, 255))                 # canto de abajo
        for x0, x1 in ((2, 4), (11, 13)):                                       # panel hundido de cada hoja (sigue en la otra mitad)
            ys = (3, 15) if up else (0, 12)
            rect(door, x0, ys[0], x0, ys[1], panel)
            rect(door, x1, ys[0], x1, ys[1], panel)
            cap = ys[0] if up else ys[1]
            rect(door, x0, cap, x1, cap, panel)
        save_tex(f"elevator_door_{half}", door)
        # ---- una sola hoja grande (puerta doble); la unión queda a la derecha y el modelo la voltea para la otra mitad
        leaf_tex = fill_noise(new(16), STEEL, 3, 2223 + (7 if up else 0), vgrad=14)
        for y in range(1, 15, 3):
            rect(leaf_tex, 1, y, 14, y, shade(STEEL, 9))
        rect(leaf_tex, 0, 0, 0, 15, edge_color)                                 # canto exterior
        rect(leaf_tex, 15, 0, 15, 15, dark)                                     # unión (junto a la otra hoja)
        rect(leaf_tex, 14, 0, 14, 15, shade(STEEL, 20))
        if up:
            rect(leaf_tex, 0, top_y, 15, top_y, (84, 90, 100, 255))
            rect(leaf_tex, 3, 3, 11, 3, panel)
            rect(leaf_tex, 3, 3, 3, 15, panel)
            rect(leaf_tex, 11, 3, 11, 15, panel)
        else:
            rect(leaf_tex, 0, bot_y, 15, bot_y, (84, 90, 100, 255))
            rect(leaf_tex, 3, 12, 11, 12, panel)
            rect(leaf_tex, 3, 0, 3, 12, panel)
            rect(leaf_tex, 11, 0, 11, 12, panel)
        save_tex(f"elevator_door_leaf_{half}", leaf_tex)
    save_tex("elevator_door_edge", fill_noise(new(16), (96, 102, 112, 255), 3, 2222, vgrad=10))


# ====================================================================================================== modelos
def item_model(block_id, model):
    write_json(ITEMS / f"{block_id}.json", {"parent": f"beautifuldecor:block/{model}"})
    NEW_ITEMS[block_id] = {"parent": f"beautifuldecor:block/{model}"}


def tex(name):
    return f"beautifuldecor:block/{name}"


def gen_controller():
    faces = {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"}
    write_json(MODELS / "elevator_controller.json", {
        "textures": {"particle": tex("elevator_controller_side"), "front": tex("elevator_controller_front"),
                     "side": tex("elevator_controller_side"), "top": tex("elevator_controller_top")},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                      "faces": {f: {"uv": [0, 0, 16, 16], "texture": ref} for f, ref in faces.items()}}]})
    variants = {f"facing={f}": ({"model": "beautifuldecor:block/elevator_controller", "y": rot} if rot
                                else {"model": "beautifuldecor:block/elevator_controller"})
                for f, rot in FACINGS.items()}
    write_json(STATES / "elevator_controller.json", {"variants": variants})
    item_model("elevator_controller", "elevator_controller")
    write_loot("elevator_controller")


def display_model(z0, z1):
    return {
        "textures": {"particle": tex("elevator_display_side"), "front": tex("elevator_display_front"),
                     "side": tex("elevator_display_side")},
        "elements": [{
            "from": [2, 2, z0], "to": [14, 14, z1],
            "faces": {
                "north": {"uv": [2, 2, 14, 14], "texture": "#front"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#side"},
                "east": {"uv": [0, 2, z1 - z0, 14], "texture": "#side"},
                "west": {"uv": [0, 2, z1 - z0, 14], "texture": "#side"},
                "up": {"uv": [2, 0, 14, z1 - z0], "texture": "#side"},
                "down": {"uv": [2, 0, 14, z1 - z0], "texture": "#side"}}}]}


def gen_display():
    write_json(MODELS / "elevator_display.json", display_model(13, 16))
    write_json(MODELS / "elevator_display_item.json", display_model(6.5, 9.5))     # centrado, para el ícono
    rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    variants = {}
    for facing, extra in rot.items():
        entry = {"model": "beautifuldecor:block/elevator_display"}
        entry.update(extra)
        variants[f"facing={facing}"] = entry
    write_json(STATES / "elevator_display.json", {"variants": variants})
    item_model("elevator_display", "elevator_display_item")
    write_loot("elevator_display")


def leaf(x0, x1, key="#door", z0=6, z1=10, y0=0, y1=16):
    return {
        "from": [x0, y0, z0], "to": [x1, y1, z1],
        "faces": {
            "north": {"uv": [x0, 0, x1, 16], "texture": key},
            "south": {"uv": [16 - x1, 0, 16 - x0, 16], "texture": key},
            "east": {"uv": [0, 0, z1 - z0, 16], "texture": "#edge"},
            "west": {"uv": [0, 0, z1 - z0, 16], "texture": "#edge"},
            "up": {"uv": [x0, 0, x1, z1 - z0], "texture": "#edge"},
            "down": {"uv": [x0, 0, x1, z1 - z0], "texture": "#edge"}}}


def half_leaf(part, x0, x1, key="#leaf", z0=6, z1=10, y0=0, y1=16):
    """Hoja de la puerta doble. part = "left" / "right" (mirando la puerta de frente; en el modelo "north" la izquierda es el este).

    La textura de la hoja tiene la unión a la derecha. Cara norte: el espectador está al norte y ve x = 16 a su izquierda.
    Cara sur: el espectador está al sur y ve x = 0 a su izquierda. Se voltea la textura donde la unión cae a la izquierda.
    """
    def u(p, flipped):
        return 16 - p if flipped else p

    north_flip = part == "right"        # la unión de la hoja derecha está en x = 16 (a la izquierda vista desde el norte)
    south_flip = part == "left"         # la unión de la hoja izquierda está en x = 0 (a la izquierda vista desde el sur)
    n0, n1 = 16 - x1, 16 - x0           # posición en pantalla (0 = izquierda) vista desde el norte
    s0, s1 = x0, x1                     # ídem vista desde el sur
    return {
        "from": [x0, y0, z0], "to": [x1, y1, z1],
        "faces": {
            "north": {"uv": [u(n0, north_flip), 0, u(n1, north_flip), 16], "texture": key},
            "south": {"uv": [u(s0, south_flip), 0, u(s1, south_flip), 16], "texture": key},
            "east": {"uv": [0, 0, z1 - z0, 16], "texture": "#edge"},
            "west": {"uv": [0, 0, z1 - z0, 16], "texture": "#edge"},
            "up": {"uv": [x0, 0, x1, z1 - z0], "texture": "#edge"},
            "down": {"uv": [x0, 0, x1, z1 - z0], "texture": "#edge"}}}


def door_elements(part, state, key_single, key_leaf, y0=0, y1=16):
    """Elementos de una mitad (de alto) de la puerta. part: single / left / right; state: closed / open."""
    if part == "single":
        spans = ((0, 8), (8, 16)) if state == "closed" else ((0, 3), (13, 16))
        return [leaf(a, b, key_single, y0=y0, y1=y1) for a, b in spans]
    span = (0, 16) if state == "closed" else ((13, 16) if part == "left" else (0, 3))
    return [half_leaf(part, span[0], span[1], key_leaf, y0=y0, y1=y1)]


def gen_door():
    for half in ("lower", "upper"):
        textures = {"particle": tex(f"elevator_door_{half}"), "door": tex(f"elevator_door_{half}"),
                    "leaf": tex(f"elevator_door_leaf_{half}"), "edge": tex("elevator_door_edge")}
        for part in ("single", "left", "right"):
            for state in ("closed", "open"):
                name = f"elevator_door_{state}_{half}" if part == "single" else f"elevator_door_{part}_{state}_{half}"
                write_json(MODELS / f"{name}.json", {"textures": textures,
                                                     "elements": door_elements(part, state, "#door", "#leaf")})
    # modelo entero (para el ícono): la mitad de arriba se pone encima de la de abajo
    icon_tex = {"particle": tex("elevator_door_lower"), "door": tex("elevator_door_lower"), "door_u": tex("elevator_door_upper"),
                "edge": tex("elevator_door_edge")}
    write_json(MODELS / "elevator_door_item.json", {
        "textures": icon_tex,
        "elements": door_elements("single", "closed", "#door", "#door") + door_elements("single", "closed", "#door_u", "#door_u", 16, 32)})
    variants = {}
    for facing, rot in FACINGS.items():
        for half in ("lower", "upper"):
            for open_ in ("false", "true"):
                for part in ("single", "left", "right"):
                    state = "open" if open_ == "true" else "closed"
                    model = f"elevator_door_{state}_{half}" if part == "single" else f"elevator_door_{part}_{state}_{half}"
                    entry = {"model": "beautifuldecor:block/" + model}
                    if rot:
                        entry["y"] = rot
                    variants[f"facing={facing},half={half},open={open_},part={part}"] = entry
    write_json(STATES / "elevator_door.json", {"variants": variants})
    item_model("elevator_door", "elevator_door_item")
    NEW_ITEMS["elevator_door"] = {"parent": "beautifuldecor:block/elevator_door_item",
                                  "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]}}}
    # solo la mitad de abajo suelta el ítem
    write_json(LOOT / "elevator_door.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "beautifuldecor:elevator_door"}],
        "conditions": [{"condition": "minecraft:block_state_property", "block": "beautifuldecor:elevator_door",
                        "properties": {"half": "lower"}}, {"condition": "minecraft:survives_explosion"}]}]})


# ====================================================================================================== textos
LANG_EN = {
    "block.beautifuldecor.elevator_controller": "Elevator Controller",
    "item.beautifuldecor.elevator_controller": "Elevator Controller",
    "block.beautifuldecor.elevator_display": "Elevator Display",
    "item.beautifuldecor.elevator_display": "Elevator Display",
    "block.beautifuldecor.elevator_door": "Elevator Door",
    "item.beautifuldecor.elevator_door": "Elevator Door",
    "tooltip.beautifuldecor.elevator.linked": "Linked to the controller at %s, %s, %s",
    "tooltip.beautifuldecor.elevator.link_hint": "Right-click an Elevator Controller to link it",
    "message.beautifuldecor.elevator.linked_ok": "Linked to the controller at %s, %s, %s",
    "message.beautifuldecor.elevator.unlinked": "This is not linked to a controller yet",
    "message.beautifuldecor.elevator.missing": "The linked controller is gone or not loaded",
    "message.beautifuldecor.elevator.busy": "The elevator is moving",
    "message.beautifuldecor.elevator.busy_settings": "You cannot change the elevator while it is moving",
    "message.beautifuldecor.elevator.saved": "Elevator settings saved",
    "message.beautifuldecor.elevator.here": "The elevator is already on this floor",
    "message.beautifuldecor.elevator.out_of_world": "That floor is outside the world",
    "message.beautifuldecor.elevator.unloaded": "The elevator zone is not loaded",
    "message.beautifuldecor.elevator.blocked": "Something is blocking the elevator",
    "message.beautifuldecor.elevator.stuck": "The elevator got stuck and stopped",
    "gui.beautifuldecor.elevator.width": "Width",
    "gui.beautifuldecor.elevator.depth": "Depth",
    "gui.beautifuldecor.elevator.height": "Height",
    "gui.beautifuldecor.elevator.speed": "Speed",
    "gui.beautifuldecor.elevator.speed_unit": "blocks per second (1 to 20)",
    "gui.beautifuldecor.elevator.name": "Floor name",
    "gui.beautifuldecor.elevator.side": "Cabin side",
    "gui.beautifuldecor.elevator.side.north": "North",
    "gui.beautifuldecor.elevator.side.east": "East",
    "gui.beautifuldecor.elevator.side.south": "South",
    "gui.beautifuldecor.elevator.side.west": "West",
    "gui.beautifuldecor.elevator.zone": "Zone: %s x %s x %s",
    "gui.beautifuldecor.elevator.save": "Save",
    "gui.beautifuldecor.elevator.close": "Close",
    "gui.beautifuldecor.elevator.floors": "%s floors - pick one",
    "gui.beautifuldecor.elevator.moving": "The elevator is moving...",
    "gui.beautifuldecor.elevator.cabin": "ELEVATOR",
    "gui.beautifuldecor.elevator.here": "YOU ARE HERE",
    "subtitles.beautifuldecor.elevator_hum": "Elevator hums",
    "subtitles.beautifuldecor.elevator_ding": "Elevator dings",
    "subtitles.beautifuldecor.elevator_door": "Elevator doors slide",
}
LANG_ES = {
    "block.beautifuldecor.elevator_controller": "Controlador de ascensor",
    "item.beautifuldecor.elevator_controller": "Controlador de ascensor",
    "block.beautifuldecor.elevator_display": "Pantalla de ascensor",
    "item.beautifuldecor.elevator_display": "Pantalla de ascensor",
    "block.beautifuldecor.elevator_door": "Puerta de ascensor",
    "item.beautifuldecor.elevator_door": "Puerta de ascensor",
    "tooltip.beautifuldecor.elevator.linked": "Vinculada al controlador de %s, %s, %s",
    "tooltip.beautifuldecor.elevator.link_hint": "Clic derecho en un controlador de ascensor para vincularla",
    "message.beautifuldecor.elevator.linked_ok": "Vinculada al controlador de %s, %s, %s",
    "message.beautifuldecor.elevator.unlinked": "Todavía no está vinculada a un controlador",
    "message.beautifuldecor.elevator.missing": "El controlador vinculado ya no está o no está cargado",
    "message.beautifuldecor.elevator.busy": "El ascensor se está moviendo",
    "message.beautifuldecor.elevator.busy_settings": "No puedes cambiar el ascensor mientras se mueve",
    "message.beautifuldecor.elevator.saved": "Ajustes del ascensor guardados",
    "message.beautifuldecor.elevator.here": "El ascensor ya está en este piso",
    "message.beautifuldecor.elevator.out_of_world": "Ese piso queda fuera del mundo",
    "message.beautifuldecor.elevator.unloaded": "La zona del ascensor no está cargada",
    "message.beautifuldecor.elevator.blocked": "Algo bloquea el ascensor",
    "message.beautifuldecor.elevator.stuck": "El ascensor se atascó y se detuvo",
    "gui.beautifuldecor.elevator.width": "Ancho",
    "gui.beautifuldecor.elevator.depth": "Fondo",
    "gui.beautifuldecor.elevator.height": "Alto",
    "gui.beautifuldecor.elevator.speed": "Velocidad",
    "gui.beautifuldecor.elevator.speed_unit": "bloques por segundo (de 1 a 20)",
    "gui.beautifuldecor.elevator.name": "Nombre del piso",
    "gui.beautifuldecor.elevator.side": "Lado de la cabina",
    "gui.beautifuldecor.elevator.side.north": "Norte",
    "gui.beautifuldecor.elevator.side.east": "Este",
    "gui.beautifuldecor.elevator.side.south": "Sur",
    "gui.beautifuldecor.elevator.side.west": "Oeste",
    "gui.beautifuldecor.elevator.zone": "Zona: %s x %s x %s",
    "gui.beautifuldecor.elevator.save": "Guardar",
    "gui.beautifuldecor.elevator.close": "Cerrar",
    "gui.beautifuldecor.elevator.floors": "%s pisos - elige uno",
    "gui.beautifuldecor.elevator.moving": "El ascensor se está moviendo...",
    "gui.beautifuldecor.elevator.cabin": "ASCENSOR",
    "gui.beautifuldecor.elevator.here": "ESTÁS AQUÍ",
    "subtitles.beautifuldecor.elevator_hum": "El ascensor zumba",
    "subtitles.beautifuldecor.elevator_ding": "El ascensor suena",
    "subtitles.beautifuldecor.elevator_door": "Se deslizan las puertas del ascensor",
}


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


def add_sounds_json():
    path = ASSETS / "sounds.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    for name in ("elevator_hum", "elevator_ding", "elevator_door"):
        data[name] = {"subtitle": f"subtitles.beautifuldecor.{name}",
                      "sounds": [{"name": f"beautifuldecor:{name}", "volume": 1.0}]}
    write_json(path, data)


def main():
    controller_textures()
    display_textures()
    door_textures()
    gen_controller()
    gen_display()
    gen_door()
    merge_lang()
    register_icon_sources()
    add_sounds_json()
    print(f"2.2.0: {len(NEW_ITEMS)} bloques de ascensor generados")


if __name__ == "__main__":
    main()
