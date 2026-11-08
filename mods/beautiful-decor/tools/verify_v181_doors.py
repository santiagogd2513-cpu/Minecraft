#!/usr/bin/env python3
"""Comprueba el parche 1.8.1 (puertas dobles): la colisión de cada puerta debe girar igual que su modelo,
y dos puertas juntas deben formar una pared continua (sin hueco por donde pase el jugador).

Simula lo mismo que hace ShapeRotator (x, z) -> (1 - z, x) por cada giro, y el "y": 90/180/270 del blockstate.
Uso: python3 tools/verify_v181_doors.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
TOGGLE = (ROOT / "src/main/java/com/example/beautifuldecor/block/ToggleBlock.java").read_text(encoding="utf-8")
PLAYER_WIDTH = 0.6
errors = []


def num(expr):
    expr = expr.strip()
    if "/" in expr:
        a, b = expr.split("/")
        return float(a) / float(b)
    return float(expr)


def parse_box(name):
    m = re.search(name + r"\s*=\s*Shapes\.box\(([^;]*)\);", TOGGLE)
    if not m:
        errors.append(f"no encuentro {name} en ToggleBlock.java")
        return None
    vals = [num(v) for v in m.group(1).replace("\n", " ").split(",")]
    return tuple(vals)  # x1 y1 z1 x2 y2 z2 en bloques


def rotate_box(box, steps):
    x1, y1, z1, x2, y2, z2 = box
    for _ in range(steps % 4):
        x1, z1, x2, z2 = 1.0 - z2, x1, 1.0 - z1, x2
    return (x1, y1, z1, x2, y2, z2)


def model_box(name):
    els = json.load(open(ASSETS / "models/block" / f"{name}.json"))["elements"]
    xs = [e["from"][0] for e in els] + [e["to"][0] for e in els]
    zs = [e["from"][2] for e in els] + [e["to"][2] for e in els]
    return (min(xs) / 16, 0, min(zs) / 16, max(xs) / 16, 2, max(zs) / 16)


def overlap(a1, a2, b1, b2):
    return min(a2, b2) - max(a1, b1)


# --- el código Java debe usar la forma girada
for needle in ("rotatedShapes", "ShapeRotator.all(shape)", "shapeFor(state)"):
    if needle not in TOGGLE:
        errors.append(f"ToggleBlock.java no contiene '{needle}'")
if TOGGLE.count("return shape;") > 1:
    errors.append("ToggleBlock.java todavía devuelve la forma sin girar en getShape/getCollisionShape")

DOORS = {
    # bloque -> (colisión norte, modelos cerrados que usa el blockstate)
    "wooden_door_metal": ("DOOR_SHAPE", ["wooden_door_metal", "wooden_door_metal_left"]),
    "glass_door": ("DOOR_SHAPE", ["glass_door", "glass_door_left"]),
    "metal_door_black": ("DOOR_SHAPE", ["metal_door_black", "metal_door_black_left"]),
    "glass_sliding_door": ("DOOR_SHAPE", ["glass_sliding_door"]),
    "dog_door": ("DOG_DOOR_SHAPE", ["dog_door"]),
}
STEPS = {"north": 0, "east": 1, "south": 2, "west": 3}

checked = 0
for block, (const, models) in DOORS.items():
    north = parse_box(const)
    if north is None:
        continue
    bs = json.load(open(ASSETS / "blockstates" / f"{block}.json"))["variants"]
    for facing, steps in STEPS.items():
        col = rotate_box(north, steps)
        # el eje fino de la colisión debe ser el mismo que el del modelo girado por el blockstate
        for mname in models:
            key = next(k for k in bs if f"facing={facing}" in k and "open=false" in k
                       and bs[k]["model"].endswith("/" + mname))
            y = bs[key].get("y", 0)
            mod = rotate_box(model_box(mname), y // 90)
            col_thin_x = (col[3] - col[0]) < (col[5] - col[2])
            mod_thin_x = (mod[3] - mod[0]) < (mod[5] - mod[2])
            if col_thin_x != mod_thin_x:
                errors.append(f"{block} facing={facing}: la colisión mira a otro lado que el modelo ({mname})")
            # la losa de colisión debe pisar el grosor del modelo (se choca donde se ve la hoja de la puerta)
            if col_thin_x:
                ov = overlap(col[0], col[3], mod[0], mod[3])
                need = 0.5 * min(col[3] - col[0], mod[3] - mod[0])
            else:
                ov = overlap(col[2], col[5], mod[2], mod[5])
                need = 0.5 * min(col[5] - col[2], mod[5] - mod[2])
            if ov < need - 1e-9:
                errors.append(f"{block} facing={facing}: la colisión no coincide con la hoja del modelo ({mname})")
            checked += 1
        # puertas dobles (todas menos la de perro): dos bloques contiguos a lo largo del ancho forman una pared sin hueco
        if block != "dog_door":
            wide_x = (col[3] - col[0]) > (col[5] - col[2])
            if wide_x:
                a = (col[0], col[3])
                b = (col[0] + 1, col[3] + 1)
            else:
                a = (col[2], col[5])
                b = (col[2] + 1, col[5] + 1)
            gap = b[0] - a[1]
            if gap > 1e-9:
                errors.append(f"{block} facing={facing}: hueco de {gap:.3f} bloques en la unión de la puerta doble "
                              f"(el jugador mide {PLAYER_WIDTH})")

# la regresión original: con la forma SIN girar, mirando al este, sí quedaba un hueco por donde cabe el jugador
old = parse_box("DOOR_SHAPE")
east_model = rotate_box(model_box("wooden_door_metal"), 1)
thin_x_old = (old[3] - old[0]) < (old[5] - old[2])
thin_x_model = (east_model[3] - east_model[0]) < (east_model[5] - east_model[2])
if thin_x_old == thin_x_model:
    errors.append("la comprobación de regresión no reproduce el error original (revisar el script)")

if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"{len(DOORS)} tipos de puerta x 4 direcciones revisados ({checked} combinaciones de modelo): "
      "colisión girada con el modelo y puertas dobles sin hueco")
print("OK")
