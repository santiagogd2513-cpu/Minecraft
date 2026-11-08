#!/usr/bin/env python3
"""Comprobación cruzada: cada bloque registrado en ModBlocks (paquete casa y calle) debe tener
blockstate, modelos (y sus texturas propias), modelo de ítem, loot table y nombre en en_us/es_es."""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = (ROOT / "src/main/java/com/example/beautifuldecor/init/ModBlocks.java").read_text(encoding="utf-8")
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"

slide_colors = re.search(r"SLIDE_COLORS = \{(.*?)\};", JAVA, re.S).group(1)
colors = re.findall(r'"(\w+)"', slide_colors)
woods = re.findall(r'"(\w+)"', re.search(r"WOODS = \{(.*?)\};", JAVA, re.S).group(1))
combos = re.findall(r'\{"(\w+)", "(\w+)"\}', re.search(r"BED_COMBOS = \{(.*?)\};", JAVA, re.S).group(1))
metals = re.findall(r'"(\w+)"', re.search(r"METAL_SHELF_TYPES = \{(.*?)\};", JAVA, re.S).group(1))
fans = re.findall(r'"(\w+)"', re.search(r'CEILING_FANS = .*?new String\[\]\{(.*?)\}', JAVA, re.S).group(1))

names = [f"ceiling_fan_{t}" for t in fans]
names += [f"bed_double_{c}" for c in colors] + [f"bed_double_{a}_{b}" for a, b in combos]
names += [f"shelf_{w}" for w in woods] + [f"shelf_metal_{m}" for m in metals]
names += ["sign_no_trucks"]
for n in names:
    if f'"{n}"' not in JAVA and not n.startswith(("ceiling_fan_", "bed_double_", "shelf_")):
        print("Falta en ModBlocks:", n)

errors = []
en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
es = json.loads((ASSETS / "lang/es_es.json").read_text(encoding="utf-8"))


def model_exists(ref):
    ns, _, path = ref.partition(":")
    if ns == "beautifuldecor":
        return (ASSETS / "models" / f"{path}.json").exists()
    return True  # modelos vanilla (minecraft:block/block)


def texture_exists(ref):
    if ref.startswith("#"):
        return True
    ns, _, path = ref.partition(":")
    if ns == "beautifuldecor":
        return (ASSETS / "textures" / f"{path}.png").exists()
    return True


def check_model(ref, seen=None):
    seen = seen or set()
    if ref in seen:
        return
    seen.add(ref)
    if not model_exists(ref):
        errors.append(f"modelo inexistente: {ref}")
        return
    ns, _, path = ref.partition(":")
    if ns != "beautifuldecor":
        return
    data = json.loads((ASSETS / "models" / f"{path}.json").read_text(encoding="utf-8"))
    if "parent" in data:
        check_model(data["parent"], seen)
    for t in data.get("textures", {}).values():
        if not texture_exists(t):
            errors.append(f"textura inexistente: {t} (en {ref})")
    for el in data.get("elements", []):
        for f in el["faces"].values():
            uv = f.get("uv")
            if uv and any(v < 0 or v > 16 for v in uv):
                errors.append(f"uv fuera de rango en {ref}: {uv}")
            if f["texture"].startswith("#") and f["texture"][1:] not in data.get("textures", {}):
                # la variable puede venir del hijo; se comprueba contra el modelo hoja más abajo
                pass


def leaf_textures(ref):
    """Texturas finales resueltas de un modelo (hoja + padres)."""
    texs = {}
    chain = []
    while ref and ref.startswith("beautifuldecor:"):
        data = json.loads((ASSETS / "models" / f"{ref.split(':')[1]}.json").read_text(encoding="utf-8"))
        chain.append(data)
        ref = data.get("parent")
    for data in reversed(chain):
        texs.update(data.get("textures", {}))
    return texs, chain


for n in names:
    for what, path in (("blockstate", ASSETS / f"blockstates/{n}.json"),
                       ("modelo de ítem", ASSETS / f"models/item/{n}.json"),
                       ("loot table", DATA / f"loot_tables/blocks/{n}.json")):
        if not path.exists():
            errors.append(f"{n}: falta {what}")
    key = f"block.beautifuldecor.{n}"
    if key not in en or key not in es:
        errors.append(f"{n}: falta nombre en en_us/es_es")
    bs = ASSETS / f"blockstates/{n}.json"
    if bs.exists():
        states = json.loads(bs.read_text(encoding="utf-8"))["variants"]
        for k, v in states.items():
            check_model(v["model"])
            texs, chain = leaf_textures(v["model"])
            for data in chain:
                for el in data.get("elements", []):
                    for f in el["faces"].values():
                        t = f["texture"]
                        if t.startswith("#") and t[1:] not in texs:
                            errors.append(f"{n}: variable de textura sin definir {t} en {k}")
        # número de variantes
        if n.startswith("bed_double_") and len(states) != 16:
            errors.append(f"{n}: se esperaban 16 variantes y hay {len(states)}")
        if n.startswith("ceiling_fan_") and set(states) != {"on=false", "on=true"}:
            errors.append(f"{n}: variantes inesperadas {sorted(states)}")
    item = ASSETS / f"models/item/{n}.json"
    if item.exists():
        d = json.loads(item.read_text(encoding="utf-8"))
        check_model(d["parent"])
        if n.startswith("bed_double_"):
            texs, chain = leaf_textures(d["parent"])
            texs.update(d.get("textures", {}))
            for data in chain:
                for el in data.get("elements", []):
                    for f in el["faces"].values():
                        t = f["texture"]
                        if t.startswith("#") and t[1:] not in texs:
                            errors.append(f"{n}: ítem con variable sin definir {t}")

# animaciones: el .png animado debe ser múltiplo exacto de su ancho
from PIL import Image
for t in fans:
    p = ASSETS / f"textures/block/ceiling_fan_{t}_blades_on.png"
    w, h = Image.open(p).size
    if h % w:
        errors.append(f"{p.name}: alto {h} no es múltiplo del ancho {w}")
    if not p.with_name(p.name + ".mcmeta").exists():
        errors.append(f"{p.name}: falta .mcmeta")

print(f"{len(names)} bloques comprobados; {len(set(names))} únicos")
if errors:
    print("\n".join(errors[:60]))
    sys.exit(1)
print("OK: todo coherente")
