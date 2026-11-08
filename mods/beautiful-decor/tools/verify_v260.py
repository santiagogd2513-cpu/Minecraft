#!/usr/bin/env python3
"""Comprueba la V2.6.0: cinta transportadora (se une con otras cintas, hace esquinas y lleva ítems, esferas, mobs y jugadores).

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que el bloque esté registrado (bloque, ítem, categoría y orden del creativo) y que tenga sus 12 estados (4 direcciones x 3 formas);
  * que existan modelos, texturas (cinta animada de 8 cuadros), icono, botín, receta y textos es/en;
  * que los modelos solo usen texturas y las 3 formas encajen (rieles y zonas de la cinta cubren el bloque sin huecos);
  * la lógica de unión y recorrido (copia exacta de ConveyorBeltBlock en tools/v260/belt_logic_test.py);
  * y corre lo de la V2.5.0 (tools/verify_v250.py).

Uso: python3 tools/verify_v260.py
"""
import json
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
errors = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


need(re.search(r"version = '(2\.([6-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.6.0 o superior")
need(re.search(r"mod_version=(2\.([6-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.6.0 o superior")
need("V2.6.0" in read(ROOT / "README.md"), "README sin la entrada V2.6.0")
need("PARCHE V2.6.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.6.0")

# ---------------------------------------------------------------- código
block = read(JAVA / "block/ConveyorBeltBlock.java")
need("entityInside" in block and "updateShape" in block and "getStateForPlacement" in block, "ConveyorBeltBlock: faltan entityInside, updateShape o getStateForPlacement")
need("CURVE_LEFT" in block and "CURVE_RIGHT" in block and "STRAIGHT" in block, "ConveyorBeltBlock: faltan las formas")
need("level.isClientSide != player" in block, "ConveyorBeltBlock: jugadores en el cliente y el resto en el servidor")
need(block.count("{") == block.count("}") and block.count("(") == block.count(")"), "ConveyorBeltBlock: llaves o paréntesis desparejados")
need('"conveyor_belt"' in read(JAVA / "init/ModBlocks.java") and "new ConveyorBeltBlock" in read(JAVA / "init/ModBlocks.java"), "ModBlocks sin la cinta")
need('"conveyor_belt"' in read(JAVA / "init/ModItems.java"), "ModItems sin la cinta")
need('"conveyor_belt"' in read(JAVA / "init/ModCreativeOrder.java"), "ModCreativeOrder sin la cinta")
need('"conveyor_belt"' in read(JAVA / "init/ModCategory.java"), "ModCategory sin la cinta")

# ---------------------------------------------------------------- recursos
bs = json.loads(read(ASSETS / "blockstates/conveyor_belt.json"))["variants"]
want = {f"facing={f},shape={s}" for f in ("north", "east", "south", "west") for s in ("straight", "curve_left", "curve_right")}
need(set(bs) == want, "blockstate de la cinta: debe tener exactamente las 12 combinaciones")
rot = {"north": 0, "east": 90, "south": 180, "west": 270}
for key, v in bs.items():
    facing = key.split(",")[0].split("=")[1]
    need(v.get("y", 0) == rot[facing], f"{key}: giro incorrecto")
    need((ASSETS / f"models/block/{v['model'].split(':')[1].split('/')[1]}.json").exists(), f"{key}: falta el modelo {v['model']}")
need(json.loads(read(ASSETS / "models/item/conveyor_belt.json"))["textures"]["layer0"] == "beautifuldecor:item/conveyor_belt", "modelo de ítem")
icon = Image.open(ASSETS / "textures/item/conveyor_belt.png")
need(icon.size == (32, 32), "el icono debe ser de 32x32")
belt = Image.open(ASSETS / "textures/block/conveyor_belt.png")
need(belt.size == (16, 128), f"la cinta animada debe ser de 16x128 (8 cuadros), es {belt.size}")
need((ASSETS / "textures/block/conveyor_belt.png.mcmeta").exists() and "animation" in read(ASSETS / "textures/block/conveyor_belt.png.mcmeta"), "falta la animación de la cinta")
need(Image.open(ASSETS / "textures/block/conveyor_frame.png").size == (16, 16), "marco 16x16")
# los 8 cuadros son el mismo dibujo corrido 1 píxel hacia arriba: el cuadro k+1 en la fila y es el cuadro k en la fila y+1
px = belt.load()
anim_ok = all(px[x, (k + 1) * 16 + y] == px[x, k * 16 + y + 1] for k in range(7) for y in range(15) for x in range(16))
need(anim_ok, "los cuadros de la cinta deben ir corriendo 1 px hacia arriba (la cinta avanza hacia el norte)")
for lg in ("es_es", "en_us"):
    d = json.loads(read(ASSETS / f"lang/{lg}.json"))
    need("block.beautifuldecor.conveyor_belt" in d and "item.beautifuldecor.conveyor_belt" in d, f"{lg}: falta el nombre de la cinta")
es = set(json.loads(read(ASSETS / "lang/es_es.json")))
en = set(json.loads(read(ASSETS / "lang/en_us.json")))
need(es == en, "es_es y en_us no tienen las mismas claves")
loot = json.loads(read(DATA / "loot_tables/blocks/conveyor_belt.json"))
need(loot["pools"][0]["entries"][0]["name"] == "beautifuldecor:conveyor_belt", "botín de la cinta")
rec = json.loads(read(DATA / "recipes/conveyor_belt.json"))
need(rec["result"]["item"] == "beautifuldecor:conveyor_belt" and rec["result"]["count"] >= 1, "receta de la cinta")

# ---------------------------------------------------------------- modelos: cobertura de la cara de arriba
def covered(model):
    """Dibuja (en una cuadrícula de 16x16 px) lo que cubre la superficie de la cinta y los rieles vistos desde arriba."""
    cells = [[None] * 16 for _ in range(16)]
    for e in model["elements"]:
        f, t = e["from"], e["to"]
        tex = e["faces"].get("up", {}).get("texture")
        if not tex:
            continue
        top = t[1]
        for z in range(int(f[2]), int(t[2])):
            for x in range(int(f[0]), int(t[0])):
                if cells[z][x] is None or cells[z][x][0] <= top:
                    cells[z][x] = (top, tex)
    return cells


for name in ("conveyor_straight", "conveyor_curve_left", "conveyor_curve_right"):
    m = json.loads(read(ASSETS / f"models/block/{name}.json"))
    for tex in m["textures"].values():
        need((ASSETS / f"textures/{tex.split(':')[1]}.png").exists(), f"{name}: falta la textura {tex}")
    cells = covered(m)
    holes = [(x, z) for z in range(16) for x in range(16) if cells[z][x] is None]
    if name == "conveyor_straight":
        need(not holes, f"{name}: queda sin cubrir {holes[:4]}")
    else:
        need(not holes, f"{name}: queda sin cubrir {holes[:4]}")
    # la superficie de la cinta debe estar a 4 px (coincide con la altura de colisión de 4/16)
    tops = {c[0] for row in cells for c in row if c and c[1] == "#belt"}
    need(tops == {4}, f"{name}: la superficie de la cinta debe estar a 4 px, está en {tops}")

# ---------------------------------------------------------------- lógica de unión y recorrido
r = subprocess.run([sys.executable, str(ROOT / "tools/v260/belt_logic_test.py")], capture_output=True, text=True)
need(r.returncode == 0 and "OK" in r.stdout, "lógica de la cinta:\n" + (r.stdout + r.stderr)[-800:])

# ---------------------------------------------------------------- lo de la V2.5.0
r = subprocess.run([sys.executable, str(ROOT / "tools/verify_v250.py")], capture_output=True, text=True, timeout=2400)
need(r.returncode == 0 and "OK V2.5" in r.stdout, "verify_v250 falló:\n" + (r.stdout + r.stderr)[-1500:])

if errors:
    print("FALLÓ la verificación V2.6.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.6.0: cinta transportadora (unión automática, esquinas, lleva ítems/mobs/jugadores), 12 estados, textura animada, es/en, lógica probada.")
