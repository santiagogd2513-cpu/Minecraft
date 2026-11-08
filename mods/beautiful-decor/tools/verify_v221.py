#!/usr/bin/env python3
"""Comprueba la V2.2.1: puerta doble de ascensor (dos puertas juntas se emparejan).

No compila nada: revisa la lógica en Java (propiedad PART, emparejar al colocar, soltar la pareja al romper, espejo),
los 24 estados del blockstate, los modelos de las mitades (volteo de textura y escondite hacia el lado correcto) y la versión.

Uso: python3 tools/verify_v221.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
errors = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


need(re.search(r"version = '(2\.(2\.([1-9]\d*)|([3-9]|\d{2,})\.\d+)|[3-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")),
     "build.gradle no está en 2.2.1 o superior")
need(re.search(r"mod_version=(2\.(2\.([1-9]\d*)|([3-9]|\d{2,})\.\d+)|[3-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")),
     "gradle.properties no está en 2.2.1 o superior")
need("V2.2.1" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V2.2.1")
need("PARCHE V2.2.1" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V2.2.1")

src = (JAVA / "block/ElevatorDoorBlock.java").read_text(encoding="utf-8")
need(src.count("{") == src.count("}") and src.count("(") == src.count(")"), "ElevatorDoorBlock: llaves o paréntesis desparejados")
need('EnumProperty.create("part", DoorPart.class)' in src, "ElevatorDoorBlock sin la propiedad part")
need(re.search(r'SINGLE\("single"\),\s*LEFT\("left"\),\s*RIGHT\("right"\)', src), "DoorPart: nombres single/left/right")
need("builder.add(FACING, OPEN, PART, TallHalves.HALF)" in src, "createBlockStateDefinition debe incluir PART y la mitad (V2.3.0)")
need(".setValue(PART, DoorPart.SINGLE)" in src, "el estado por defecto debe ser puerta sola")
for needle in ("import net.minecraft.util.StringRepresentable;", "import net.minecraft.world.level.block.state.properties.EnumProperty;",
               "import java.util.Objects;"):
    need(needle in src, f"ElevatorDoorBlock sin {needle}")
need(len(re.findall(r"^import .*;$", src, re.M)) == len(set(re.findall(r"^import .*;$", src, re.M))), "ElevatorDoorBlock: imports repetidos")
# la pareja a mi izquierda -> soy la hoja derecha; la pareja a mi derecha -> soy la hoja izquierda
need(re.search(r"canPair\(context\.getLevel\(\), pos\.relative\(facing\.getClockWise\(\)\)[^;]*\)\) \{\s*part = DoorPart\.RIGHT;", src),
     "la pareja a la izquierda (getClockWise) debe volverme la hoja derecha")
need(re.search(r"canPair\(context\.getLevel\(\), pos\.relative\(facing\.getCounterClockWise\(\)\)[^;]*\)\) \{\s*part = DoorPart\.LEFT;", src),
     "la pareja a la derecha (getCounterClockWise) debe volverme la hoja izquierda")
need("DoorPart.RIGHT ? facing.getClockWise() : facing.getCounterClockWise()" in src, "partnerPos: la hoja derecha busca a su izquierda y la izquierda a su derecha")
need("public void onPlace(" in src and "public void onRemove(" in src, "falta onPlace/onRemove")
need("super.onRemove(state, level, pos, newState, moving)" in src, "onRemove debe llamar a super (quita el block entity)")
need("!state.is(newState.getBlock())" in src and "oldState.is(state.getBlock())" in src,
     "onPlace/onRemove deben ignorar los cambios de estado del mismo bloque (abrir/cerrar)")
need("Objects.equals(link, otherLink)" in src, "solo deben emparejarse puertas del mismo controlador")
need("mirror == Mirror.NONE ? mirrored : mirrored.setValue(PART" in src, "mirror debe intercambiar izquierda y derecha")
need("OPENED_LEFT" in src and "OPENED_RIGHT" in src and "case LEFT ->" in src and "case RIGHT ->" in src, "colisión abierta por mitad")
need(re.search(r"OPENED_LEFT = ShapeRotator\.all\(ModBlock\.box\(13, 0, 6, 16, 16, 10\)\)", src), "la hoja izquierda abierta debe quedar en x = 13..16")
need(re.search(r"OPENED_RIGHT = ShapeRotator\.all\(ModBlock\.box\(0, 0, 6, 3, 16, 10\)\)", src), "la hoja derecha abierta debe quedar en x = 0..3")
be = (JAVA / "block/entity/ElevatorDoorBlockEntity.java").read_text(encoding="utf-8")
need("level.getGameTime() % 4L != 0L" in be, "las puertas deben consultar todas en el mismo tick (se abren juntas)")

# blockstate y modelos
bs = json.loads((ASSETS / "blockstates/elevator_door.json").read_text(encoding="utf-8"))["variants"]
need(len(bs) == 48, f"blockstate: {len(bs)} variantes, deben ser 48 (V2.3.0: puerta de 2 bloques de alto)")
for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for half in ("lower", "upper"):
        for open_ in ("false", "true"):
            for part in ("single", "left", "right"):
                key = f"facing={facing},half={half},open={open_},part={part}"
                v = bs.get(key)
                need(v is not None, f"blockstate sin {key}")
                if v:
                    state = "open" if open_ == "true" else "closed"
                    want = (f"beautifuldecor:block/elevator_door_{state}_{half}" if part == "single"
                            else f"beautifuldecor:block/elevator_door_{part}_{state}_{half}")
                    need(v["model"] == want, f"{key}: modelo {v['model']} en vez de {want}")
                    need(v.get("y", 0) == rot, f"{key}: rotación {v.get('y', 0)} en vez de {rot}")
for half in ("lower", "upper"):
    need((ASSETS / f"textures/block/elevator_door_leaf_{half}.png").exists(), f"falta la textura elevator_door_leaf_{half}")
for part, state, half in [(a, b, c) for a in ("left", "right") for b in ("closed", "open") for c in ("lower", "upper")]:
    if True:
        path = ASSETS / f"models/block/elevator_door_{part}_{state}_{half}.json"
        need(path.exists(), f"falta {path.name}")
        if not path.exists():
            continue
        m = json.loads(path.read_text(encoding="utf-8"))
        need(len(m["elements"]) == 1, f"{path.name}: debe tener una sola hoja")
        el = m["elements"][0]
        x0, x1 = el["from"][0], el["to"][0]
        want = (0, 16) if state == "closed" else ((13, 16) if part == "left" else (0, 3))
        need((x0, x1) == want, f"{path.name}: x = {x0}..{x1}, debía ser {want[0]}..{want[1]}")
        for tex in m["textures"].values():
            need((ASSETS / "textures" / (tex.split(":")[1] + ".png")).exists(), f"{path.name}: falta la textura {tex}")
        # la unión debe quedar del lado de la pareja: izquierda -> a su derecha vista de frente (cara norte sin voltear)
        n = el["faces"]["north"]["uv"]
        s = el["faces"]["south"]["uv"]
        if state == "closed":
            need((n[0] < n[2]) == (part == "left"), f"{path.name}: cara norte con la unión del lado equivocado")
            need((s[0] < s[2]) == (part == "right"), f"{path.name}: cara sur con la unión del lado equivocado")
        for u in n + s:
            need(0 <= u <= 16, f"{path.name}: uv fuera de rango")

if errors:
    print("FALLÓ la verificación V2.2.1:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.2.1: puerta doble de ascensor (emparejar al colocar, soltar al romper, espejo, 48 estados, modelos).")
