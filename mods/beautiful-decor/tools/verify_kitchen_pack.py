#!/usr/bin/env python3
"""Comprueba la cocina nueva: blockstates -> modelos -> texturas, loot tables, idiomas y registro en Java."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
errors = []

names = [p.stem for p in (A / "blockstates").glob("*.json")
         if re.match(r"(stove_oven_|stove_cabinet_|microwave_|kitchen_oven|cutlery_)", p.stem)]
java = (ROOT / "src/main/java/com/example/beautifuldecor/init/ModBlocks.java").read_text(encoding="utf-8")
en = json.loads((A / "lang/en_us.json").read_text(encoding="utf-8"))
es = json.loads((A / "lang/es_es.json").read_text(encoding="utf-8"))


def resolve_model(ref):
    ns, _, path = ref.partition(":")
    if ns != "beautifuldecor":
        return None
    return A / "models" / f"{path}.json"


def textures_of(model_path, seen=None):
    """texturas resueltas de un modelo siguiendo la cadena de padres."""
    data = json.loads(model_path.read_text(encoding="utf-8"))
    tex = {}
    parent = data.get("parent", "")
    if parent.startswith("beautifuldecor:"):
        pp = resolve_model(parent)
        if not pp.exists():
            errors.append(f"{model_path.name}: falta el padre {parent}")
        else:
            tex.update(textures_of(pp)[0])
    tex.update(data.get("textures", {}))
    return tex, data


def check_model(ref):
    mp = resolve_model(ref)
    if mp is None or not mp.exists():
        errors.append(f"modelo inexistente: {ref}")
        return
    tex, data = textures_of(mp)

    def real(t, depth=0):
        while t.startswith("#") and depth < 10:
            t = tex.get(t[1:], "")
            depth += 1
        return t

    for k, v in tex.items():
        r = real(v)
        if r.startswith("beautifuldecor:"):
            if not (A / "textures" / f"{r.split(':')[1]}.png").exists():
                errors.append(f"{mp.name}: falta textura {r}")
        elif r.startswith("#") or not r:
            errors.append(f"{mp.name}: textura sin resolver {k}={v}")
    # elementos (solo los del propio modelo o de su padre)
    chain = [data]
    p = data.get("parent", "")
    while p.startswith("beautifuldecor:"):
        pd = json.loads(resolve_model(p).read_text(encoding="utf-8"))
        chain.append(pd)
        p = pd.get("parent", "")
    for d in chain:
        for el in d.get("elements", []):
            for v in el["from"] + el["to"]:
                if not -16 <= v <= 32:
                    errors.append(f"{mp.name}: coordenada fuera de rango {v}")
            for f, face in el["faces"].items():
                t = face["texture"]
                if t.startswith("#") and t[1:] not in tex:
                    errors.append(f"{mp.name}: cara {f} usa {t} sin definir")
                if any(not 0 <= u <= 16 for u in face["uv"]):
                    errors.append(f"{mp.name}: uv fuera de 0..16 {face['uv']}")


for n in sorted(names):
    st = json.loads((A / "blockstates" / f"{n}.json").read_text(encoding="utf-8"))
    for key, entry in st["variants"].items():
        check_model(entry["model"])
    item = A / "models/item" / f"{n}.json"
    if not item.exists():
        errors.append(f"{n}: falta modelo de ítem")
    if not (D / "loot_tables/blocks" / f"{n}.json").exists():
        errors.append(f"{n}: falta loot table")
    for lang, label in ((en, "en_us"), (es, "es_es")):
        if f"block.beautifuldecor.{n}" not in lang:
            errors.append(f"{n}: falta nombre en {label}")

# cada bloque debe estar registrado en Java (con prefijo+color o nombre completo)
expected = set()
for c in ["light_gray", "dark_gray", "black", "white"]:
    expected |= {f"stove_oven_{c}", f"stove_cabinet_{c}"}
for c in ["white", "gray", "dark_gray", "black"]:
    expected.add(f"microwave_{c}")
expected |= {"kitchen_oven", "cutlery_fork", "cutlery_knife", "cutlery_spoon", "cutlery_set", "cutlery_pack"}
if set(names) != expected:
    errors.append(f"bloques generados distintos de los esperados: {set(names) ^ expected}")
for must in ['"kitchen_oven"', '"cutlery_fork"', '"cutlery_knife"', '"cutlery_spoon"', '"cutlery_set"',
             '"cutlery_pack"', '"stove_oven_"', '"stove_cabinet_"', '"microwave_"']:
    if must not in java:
        errors.append(f"ModBlocks.java no registra {must}")

print(f"{len(names)} bloques revisados")
print("OK" if not errors else "\n".join(errors))
raise SystemExit(1 if errors else 0)
