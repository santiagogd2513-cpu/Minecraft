#!/usr/bin/env python3
"""Comprueba la versión 1.6.0: escaleras, puertas dobles y lavamanos.

Revisa blockstates -> modelos -> texturas, loot tables, idiomas, etiquetas, registro en Java y que cada
combinación de propiedades de los blockstates cubra todos los estados posibles del bloque.
"""
import json
import re
from itertools import product
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
errors = []

en = json.loads((A / "lang/en_us.json").read_text(encoding="utf-8"))
es = json.loads((A / "lang/es_es.json").read_text(encoding="utf-8"))
blocks_java = (JAVA / "init/ModBlocks.java").read_text(encoding="utf-8")
items_java = (JAVA / "init/ModItems.java").read_text(encoding="utf-8")


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
    if ref.startswith("minecraft:item/"):      # desde la 1.8.2 los ítems son sprites (item/generated de vanilla)
        return
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
            if not (A / "textures" / f"{r.split(':')[1]}.png").exists():
                errors.append(f"{mp.name}: falta textura {r}")
        elif r.startswith("#") or not r:
            errors.append(f"{mp.name}: textura sin resolver {k}={v}")
    chain = [data]
    p = data.get("parent", "")
    while p.startswith("beautifuldecor:"):
        pd = json.loads(model_path(p).read_text(encoding="utf-8"))
        chain.append(pd)
        p = pd.get("parent", "")
    for d in chain:
        for el in d.get("elements", []):
            for v in el["from"] + el["to"]:
                if not -16 <= v <= 32:
                    errors.append(f"{mp.name}: coordenada fuera de rango {v}")
            if any(a > b for a, b in zip(el["from"], el["to"])):
                errors.append(f"{mp.name}: from > to {el['from']} {el['to']}")
            for f, face in el["faces"].items():
                t = face["texture"]
                if t.startswith("#") and t[1:] not in tex:
                    errors.append(f"{mp.name}: cara {f} usa {t} sin definir")
                if "uv" in face and any(not 0 <= u <= 16 for u in face["uv"]):
                    errors.append(f"{mp.name}: uv fuera de 0..16 {face['uv']}")


def check_state(name, props):
    """props: {propiedad: [valores]}; exige una variante para cada combinación."""
    path = A / "blockstates" / f"{name}.json"
    if not path.exists():
        errors.append(f"{name}: falta blockstate")
        return
    variants = json.loads(path.read_text(encoding="utf-8"))["variants"]
    keys = sorted(props)
    for combo in product(*(props[k] for k in keys)):
        key = ",".join(f"{k}={v}" for k, v in zip(keys, combo))
        if key not in variants:
            errors.append(f"{name}: falta la variante {key}")
    for key, entry in variants.items():
        parts = key.split(",")
        if [p.split("=")[0] for p in parts] != sorted(p.split("=")[0] for p in parts):
            errors.append(f"{name}: propiedades sin orden alfabético en '{key}'")
        check_model(entry["model"])


def check_common(name):
    if not (A / "models/item" / f"{name}.json").exists():
        errors.append(f"{name}: falta modelo de ítem")
    else:
        check_model(json.loads((A / "models/item" / f"{name}.json").read_text(encoding="utf-8"))["parent"])
    if not (D / "loot_tables/blocks" / f"{name}.json").exists():
        errors.append(f"{name}: falta loot table")
    for lang, label in ((en, "en_us"), (es, "es_es")):
        if f"block.beautifuldecor.{name}" not in lang:
            errors.append(f"{name}: falta nombre en {label}")



FACING = ["north", "east", "south", "west"]
BOOL = ["false", "true"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]
CONCRETE = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black"]

# --- escaleras
stairs = [f"stairs_{w}" for w in WOODS] + [f"stairs_concrete_{c}" for c in CONCRETE] + ["stairs_smooth_quartz"]
for n in stairs:
    check_state(n, {"facing": ["east", "north", "south", "west"], "half": ["bottom", "top"],
                    "shape": ["inner_left", "inner_right", "outer_left", "outer_right", "straight"]})
    check_common(n)
    for suffix in ("_inner", "_outer"):
        if not (A / "models/block" / f"{n}{suffix}.json").exists():
            errors.append(f"{n}: falta el modelo {suffix}")
for name in ("registerStairs(\"stairs_\" + wood", "registerStairs(\"stairs_concrete_\" + color",
             'registerStairs("stairs_smooth_quartz"', "StairBlock"):
    if name not in blocks_java:
        errors.append(f"ModBlocks.java no registra {name}")
tags = D.parent / "minecraft/tags"
pick = json.loads((tags / "blocks/mineable/pickaxe.json").read_text(encoding="utf-8"))["values"]
axe = json.loads((tags / "blocks/mineable/axe.json").read_text(encoding="utf-8"))["values"]
for n in stairs:
    want = axe if n.removeprefix("stairs_") in WOODS else pick
    if f"beautifuldecor:{n}" not in want:
        errors.append(f"{n}: falta en la etiqueta de minería")

# --- puertas dobles
doors = ["wooden_door_metal", "glass_door", "metal_door_black"]
for n in doors:
    check_state(n, {"facing": FACING, "hinge": ["left", "right"], "open": BOOL})
    for suffix in ("_left", "_left_open"):
        if not (A / "models/block" / f"{n}{suffix}.json").exists():
            errors.append(f"{n}: falta el modelo espejo {suffix}")
    if f'"{n}",' not in blocks_java:
        errors.append(f"ModBlocks.java no registra {n}")
if blocks_java.count("new HingedDoorBlock(") != 3:
    errors.append("las tres puertas deben registrarse como HingedDoorBlock")
if not (JAVA / "block/HingedDoorBlock.java").exists():
    errors.append("falta HingedDoorBlock.java")

# --- lavamanos
wb = [f"washbasin_{d}_{c}" for d in ("pedestal", "wall", "vanity") for c in ("white", "light_gray", "dark_gray", "black")]
for n in wb:
    check_state(n, {"facing": FACING})
    check_common(n)
for must in ("WASHBASIN_DESIGNS", "WASHBASIN_COLORS", '"washbasin_"'):
    if must not in blocks_java:
        errors.append(f"ModBlocks.java no registra {must}")

# --- categorías, ítems e idiomas
cat = (JAVA / "init/ModCategory.java").read_text(encoding="utf-8")
if 'startsWith("stairs_")' not in cat or 'startsWith("washbasin_")' not in cat:
    errors.append("ModCategory.java no incluye stairs_ o washbasin_")
if "registerGroup(ModBlocks.STAIRS)" not in items_java or "registerGroup(ModBlocks.WASHBASINS)" not in items_java:
    errors.append("ModItems.java no registra los ítems de escaleras y lavamanos")
for lang, label in ((en, "en_us"), (es, "es_es")):
    if "category.beautifuldecor.stairs" not in lang:
        errors.append(f"falta la categoría stairs en {label}")

print(f"{len(stairs)} escaleras, {len(doors)} puertas, {len(wb)} lavamanos revisados")
print("OK" if not errors else "\n".join(errors))
raise SystemExit(1 if errors else 0)
