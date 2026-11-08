#!/usr/bin/env python3
"""Comprueba la V1.8.2: cada ítem del mod tiene su ícono propio y un modelo de ítem plano que lo usa.

Uso: python3 tools/verify_v182_icons.py
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
SOURCE = ROOT / "tools/v182/item_models_src.json"
SPRITES = {"drone", "drone_remote", "tab_icon"}      # sprites hechos a mano antes de la 1.8.2
errors = []

models = {f.stem: json.loads(f.read_text(encoding="utf-8")) for f in (ASSETS / "models/item").glob("*.json")}
blockstates = {f.stem for f in (ASSETS / "blockstates").glob("*.json")}
src = json.loads(SOURCE.read_text(encoding="utf-8"))

for name in sorted(blockstates - set(models)):
    errors.append(f"el bloque {name} no tiene modelo de ítem")
for name in sorted(set(src) - set(models)):
    errors.append(f"falta el modelo de ítem de {name}")

checked = 0
for name, data in sorted(models.items()):
    if data.get("parent") != "minecraft:item/generated":
        errors.append(f"{name}: el modelo de ítem todavía no es un sprite (parent={data.get('parent')})")
        continue
    layer = data.get("textures", {}).get("layer0", "")
    if layer != f"beautifuldecor:item/{name}":
        errors.append(f"{name}: layer0 apunta a {layer!r}")
        continue
    tex = ASSETS / "textures/item" / f"{name}.png"
    if not tex.exists():
        errors.append(f"{name}: falta textures/item/{name}.png")
        continue
    if name in SPRITES:
        continue
    im = Image.open(tex).convert("RGBA")
    a = np.asarray(im)
    checked += 1
    if im.size != (32, 32):
        errors.append(f"{name}: el ícono mide {im.size}, debe ser 32x32")
        continue
    opaque = a[:, :, 3] > 127
    if opaque.sum() < 60:
        errors.append(f"{name}: ícono casi vacío ({int(opaque.sum())} px)")
    rgb = a[:, :, :3].astype(int)
    magenta = opaque & (rgb[:, :, 0] > 230) & (rgb[:, :, 1] < 40) & (rgb[:, :, 2] > 230)
    if magenta.sum() > 12:
        errors.append(f"{name}: el ícono tiene {int(magenta.sum())} px magenta (textura faltante en el modelo)")
    ys, xs = np.nonzero(a[:, :, 3] > 0)
    if xs.size:
        w, h = xs.max() - xs.min() + 1, ys.max() - ys.min() + 1
        if max(w, h) < 18:
            errors.append(f"{name}: el dibujo es muy pequeño dentro del slot ({w}x{h})")

# ningún ítem debe quedar apuntando a un modelo de bloque (era la causa de las puertas que se salían del slot)
for f in (ASSETS / "models/item").glob("*.json"):
    if "beautifuldecor:block/" in f.read_text(encoding="utf-8"):
        errors.append(f"{f.name} todavía usa un modelo de bloque")

if not re.search(r"version = '(1\.8\.[2-9]|[2-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")):
    errors.append("build.gradle no está en 1.8.2 o superior")
if not re.search(r"mod_version=(1\.8\.[2-9]|[2-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")):
    errors.append("gradle.properties no está en 1.8.2 o superior")

if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"{len(models)} modelos de ítem planos; {checked} íconos propios de 32x32 revisados (ninguno vacío ni con texturas faltantes)")
print("OK")
