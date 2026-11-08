#!/usr/bin/env python3
"""Comprueba la versión 1.8.0: impresora HS, congelador, cámara frigorífica, cajas y marca Bugerati.

Revisa blockstates -> modelos -> texturas, loot tables, idiomas, registro en Java y que cada combinación de
propiedades de los blockstates cubra todos los estados posibles del bloque (con el orden alfabético que exige
Minecraft en las claves de variante).
"""
import json
import re
import sys
from itertools import product
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
errors = []

en = json.loads((A / "lang/en_us.json").read_text(encoding="utf-8"))
es = json.loads((A / "lang/es_es.json").read_text(encoding="utf-8"))
blocks_java = (JAVA / "init/ModBlocks.java").read_text(encoding="utf-8")
items_java = (JAVA / "init/ModItems.java").read_text(encoding="utf-8")
cat_java = (JAVA / "init/ModCategory.java").read_text(encoding="utf-8")
used_textures = set()


def model_path(ref):
    ns, _, path = ref.partition(":")
    return A / "models" / f"{path}.json" if ns == "beautifuldecor" else None


def resolved_textures(mp):
    data = json.loads(mp.read_text(encoding="utf-8"))
    tex = {}
    parent = data.get("parent", "")
    if parent.startswith("beautifuldecor:"):
        pp = model_path(parent)
        if not pp.exists():
            errors.append(f"{mp.name}: falta el padre {parent}")
        else:
            tex.update(resolved_textures(pp)[0])
    tex.update(data.get("textures", {}))
    return tex, data


def check_model(ref):
    mp = model_path(ref)
    if mp is None or not mp.exists():
        errors.append(f"modelo inexistente: {ref}")
        return
    tex, data = resolved_textures(mp)

    def real(t):
        for _ in range(10):
            if not t.startswith("#"):
                break
            t = tex.get(t[1:], "")
        return t

    for k, v in tex.items():
        r = real(v)
        if r.startswith("beautifuldecor:"):
            used_textures.add(r.split(":")[1].split("/")[-1])
            if not (A / "textures" / f"{r.split(':')[1]}.png").exists():
                errors.append(f"{mp.name}: falta textura {r}")
        elif r.startswith("#") or not r:
            errors.append(f"{mp.name}: textura sin resolver {k}={v}")
    for el in data.get("elements", []):
        for v in el["from"] + el["to"]:
            if not -16 <= v <= 32:
                errors.append(f"{mp.name}: coordenada fuera de rango {v}")
        if any(a > b for a, b in zip(el["from"], el["to"])):
            errors.append(f"{mp.name}: from > to {el['from']} {el['to']}")
        rot = el.get("rotation")
        if rot and (abs(rot["angle"]) > 45 or rot["angle"] % 22.5):
            errors.append(f"{mp.name}: ángulo de rotación no válido {rot['angle']}")
        if not el["faces"]:
            errors.append(f"{mp.name}: elemento sin caras")
        for f, face in el["faces"].items():
            t = face["texture"]
            if t.startswith("#") and t[1:] not in tex:
                errors.append(f"{mp.name}: cara {f} usa {t} sin definir")
            if "uv" in face and any(not 0 <= u <= 16 for u in face["uv"]):
                errors.append(f"{mp.name}: uv fuera de 0..16 {face['uv']}")
    if "particle" not in tex:
        errors.append(f"{mp.name}: sin textura particle")


def check_state(name, props):
    path = A / "blockstates" / f"{name}.json"
    if not path.exists():
        errors.append(f"{name}: falta blockstate")
        return
    variants = json.loads(path.read_text(encoding="utf-8"))["variants"]
    keys = sorted(props)
    expected = set()
    for combo in product(*(props[k] for k in keys)):
        key = ",".join(f"{k}={v}" for k, v in zip(keys, combo))
        expected.add(key)
        if key not in variants:
            errors.append(f"{name}: falta la variante {key}")
    for key, entry in variants.items():
        if key not in expected:
            errors.append(f"{name}: variante sobrante {key}")
        check_model(entry["model"])


def check_common(name, loot=True):
    ip = A / "models/item" / f"{name}.json"
    if not ip.exists():
        errors.append(f"{name}: falta modelo de ítem")
    else:
        item_parent = json.loads(ip.read_text(encoding="utf-8"))["parent"]
        if item_parent == "minecraft:item/generated":      # desde la 1.8.2 cada ítem es un sprite propio
            if not (A / "textures/item" / f"{name}.png").exists():
                errors.append(f"{name}: falta el ícono textures/item/{name}.png")
        else:
            check_model(item_parent)
    if loot and not (D / "loot_tables/blocks" / f"{name}.json").exists():
        errors.append(f"{name}: falta loot table")
    for lang, label in ((en, "en_us"), (es, "es_es")):
        for kind in ("block", "item"):
            if f"{kind}.beautifuldecor.{name}" not in lang:
                errors.append(f"{name}: falta {kind} en {label}")


FACING = ["north", "east", "south", "west"]
BOOL = ["false", "true"]

# --- impresora HS (mismo mecanismo: bloque "printer", facing + printing)
check_state("printer", {"facing": FACING, "printing": BOOL})
check_common("printer", loot=False)
check_model("beautifuldecor:block/printer_printing")
if "Impresora HS" not in es["block.beautifuldecor.printer"] or "HS" not in en["block.beautifuldecor.printer"]:
    errors.append("la impresora no lleva la marca HS en el nombre")
pb = (JAVA / "block/PrinterBlock.java").read_text(encoding="utf-8")
if 'BooleanProperty.create("printing")' not in pb or "startPrint" not in pb:
    errors.append("PrinterBlock.java fue modificado: el mecanismo debe quedar igual")

# --- congelador
check_state("freezer_chest", {"facing": FACING, "open": BOOL})
check_common("freezer_chest")
if 'registerOpenable("freezer_chest"' not in blocks_java:
    errors.append("ModBlocks.java no registra freezer_chest")

# --- cámara frigorífica
check_state("cold_room", {"facing": FACING, "open": BOOL, "part": [str(i) for i in range(27)]})
check_common("cold_room")
for part in range(27):
    check_model(f"beautifuldecor:block/cold_room_p{part}")
for part in (1, 4):
    check_model(f"beautifuldecor:block/cold_room_p{part}_open")
loot = json.loads((D / "loot_tables/blocks/cold_room.json").read_text(encoding="utf-8"))
if "block_state_property" not in json.dumps(loot):
    errors.append("cold_room: el loot debe soltar solo desde la pieza 0")
for must in ('"cold_room"', "ColdRoomBlock.LIGHT_PART"):
    if must not in blocks_java:
        errors.append(f"ModBlocks.java no contiene {must}")
if "COLD_ROOM = ITEMS.register" not in items_java:
    errors.append("ModItems.java no registra cold_room")
if not (JAVA / "block/ColdRoomBlock.java").exists():
    errors.append("falta ColdRoomBlock.java")

# --- cajas
crate_ids = ["crate_empty", "crate_fries", "crate_ribs", "crate_balls"]
for n in crate_ids:
    check_state(n, {"facing": FACING})
    check_common(n)
if "registerGroup(ModBlocks.CRATES)" not in items_java:
    errors.append("ModItems.java no registra las cajas")

# --- Bugerati
lamps = ["bugerati_floor_lamp", "bugerati_arc_lamp", "bugerati_table_lamp", "bugerati_orb_lamp",
         "bugerati_pendant_lamp", "bugerati_wall_sconce"] + [f"bugerati_chandelier_{f}" for f in ("gold", "silver", "black")]
for n in lamps:
    check_state(n, {"facing": FACING, "open": BOOL})
    check_common(n)
    check_model(f"beautifuldecor:block/{n}_on")
    if f'"{n}"' not in blocks_java and not n.startswith("bugerati_chandelier_"):
        errors.append(f"ModBlocks.java no registra {n}")
neon = [f"bugerati_neon_{s}_{c}" for s in ("ring", "heart", "star", "bolt", "moon", "arrow")
        for c in ("pink", "cyan", "yellow", "red")] + ["bugerati_neon_logo"]
for n in neon:
    check_state(n, {"facing": FACING})
    check_common(n)
for must in ("BUGERATI_NEON_SHAPES", "BUGERATI_NEON_COLORS", "BUGERATI_CHANDELIER_FINISHES",
             "registerBugeratiNeon(\"bugerati_neon_logo\")", "new NeonPanelBlock(", "new BugeratiLampBlock("):
    if must not in blocks_java:
        errors.append(f"ModBlocks.java no contiene {must}")
if "registerGroup(ModBlocks.BUGERATI)" not in items_java:
    errors.append("ModItems.java no registra los ítems Bugerati")
for f in ("NeonPanelBlock", "BugeratiLampBlock"):
    if not (JAVA / f"block/{f}.java").exists():
        errors.append(f"falta {f}.java")

# --- categorías y versión
for needle in ('p.startsWith("bugerati_")', 'p.equals("cold_room")', 'p.startsWith("crate_")', 'p.equals("freezer_chest")'):
    if needle not in cat_java:
        errors.append(f"ModCategory.java no contiene {needle}")
if not re.search(r"version = '(1\.8\.\d+|[2-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")):
    errors.append("build.gradle no está en 1.8.x")
if not re.search(r"mod_version=(1\.8\.\d+|[2-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")):
    errors.append("gradle.properties no está en 1.8.x")

# --- texturas generadas que ningún modelo usa (posible error de nombre)
generated = [p.stem for p in (A / "textures/block").glob("*.png")
             if p.stem.startswith(("hs_pr_", "fz_", "cr_", "crate_", "bg_", "bugerati_"))]
all_models_text = "".join(p.read_text(encoding="utf-8") for p in (A / "models/block").glob("*.json"))
for t in generated:
    if f"block/{t}\"" not in all_models_text:
        errors.append(f"textura sin usar: {t}")

total = 1 + 1 + 1 + len(crate_ids) + len(lamps) + len(neon)
print(f"{total} bloques revisados (impresora, congelador, cámara, {len(crate_ids)} cajas, {len(lamps)} luces, {len(neon)} neones)")
print("OK" if not errors else "\n".join(errors))
raise SystemExit(1 if errors else 0)
