#!/usr/bin/env python3
"""Comprueba la versión 1.5.0: lavaplatos, computador HS, sofás en esquina e icono.

Revisa blockstates -> modelos -> texturas, loot tables, idiomas, registro en Java y que cada
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

# --- lavaplatos
sinks = [f"kitchen_sink_{c}" for c in ("white", "light_gray", "dark_gray", "black")]
for n in sinks:
    check_state(n, {"facing": FACING})
    check_common(n)
    if f'"{n.rsplit("_", 2)[0]}_"' not in blocks_java and '"kitchen_sink_"' not in blocks_java:
        errors.append(f"ModBlocks.java no registra {n}")
if '"light_gray", "dark_gray", "black"' not in blocks_java:
    errors.append("ModBlocks.java: faltan los colores de los lavaplatos")

# --- computador HS
check_state("hs_desktop", {"facing": FACING, "on": BOOL})
check_common("hs_desktop")
for must in ('"hs_desktop"', "HsDesktopBlock"):
    if must not in blocks_java:
        errors.append(f"ModBlocks.java no registra {must}")
if '"hs_desktop"' not in items_java:
    errors.append("ModItems.java no registra el ítem hs_desktop")
if not (JAVA / "block/HsDesktopBlock.java").exists():
    errors.append("falta HsDesktopBlock.java")

# --- sofás
sofas = [f"sofa_{c}" for c in ("red", "blue")] + [f"sofa_{w}" for w in (
    "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")]
for n in sofas:
    check_state(n, {"corner": ["none", "left", "right"], "facing": FACING,
                    "left_connected": BOOL, "right_connected": BOOL})
sofa_java = (JAVA / "block/SofaBlock.java").read_text(encoding="utf-8")
if not re.search(r'EnumProperty<Corner>\s+CORNER\s*=\s*EnumProperty\.create\("corner"', sofa_java):
    errors.append("SofaBlock.java no define la propiedad corner")

# --- categorías y pestañas
cat = (JAVA / "init/ModCategory.java").read_text(encoding="utf-8")
tabs = (JAVA / "init/ModCreativeTabs.java").read_text(encoding="utf-8")
if '"hs_desktop"' not in cat or '"kitchen_sink_"' not in cat:
    errors.append("ModCategory.java no incluye hs_desktop o kitchen_sink_")
if '"hs_desktop"' not in tabs:
    errors.append("ModCreativeTabs.java no incluye hs_desktop en la pestaña de tecnología")

# --- icono y descripción
res = ROOT / "src/main/resources"
toml = (res / "META-INF/mods.toml").read_text(encoding="utf-8")
if 'logoFile="logo.png"' not in toml:
    errors.append("mods.toml no declara logoFile")
if "electrónicos, muebles y funcionalidades" not in toml or "mod de decoración para Minecraft" not in toml:
    errors.append("mods.toml no tiene la descripción pedida")
for img, size in (("logo.png", (256, 256)), ("pack.png", (128, 128))):
    path = res / img
    if not path.exists():
        errors.append(f"falta {img}")
    else:
        from PIL import Image
        if Image.open(path).size != size:
            errors.append(f"{img} no mide {size}")

print(f"{len(sinks)} lavaplatos, 1 computador, {len(sofas)} sofás revisados")
print("OK" if not errors else "\n".join(errors))
raise SystemExit(1 if errors else 0)
