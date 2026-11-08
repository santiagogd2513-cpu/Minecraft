#!/usr/bin/env python3
"""Comprueba la V2.0.1: pestaña creativa de Vehículos (solo vehículos conducibles montado; el dron no cuenta).

Uso: python3 tools/verify_v201.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
LANG = ROOT / "src/main/resources/assets/beautifuldecor/lang"
errors = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


tabs = (JAVA / "init/ModCreativeTabs.java").read_text(encoding="utf-8")
need('"beautifuldecor_vehicles"' in tabs and "BEAUTIFUL_DECOR_VEHICLES" in tabs, "ModCreativeTabs no registra la pestaña de vehículos")
need("itemGroup.beautifuldecor_vehicles" in tabs, "la pestaña de vehículos no tiene título traducible")
m = re.search(r"VEHICLE_IDS\s*=\s*List\.of\((.*?)\);", tabs, re.S)
ids = re.findall(r'"([^"]+)"', m.group(1)) if m else []
need("motorcycle" in ids, "la moto no está en VEHICLE_IDS")
need(not {"drone", "drone_remote"} & set(ids), "el dron y su control no deben estar en la pestaña de vehículos")
need(len(ids) == len(set(ids)), "VEHICLE_IDS tiene repetidos")
items = (JAVA / "init/ModItems.java").read_text(encoding="utf-8")
for i in ids:
    need(f'ITEMS.register("{i}"' in items, f"VEHICLE_IDS lista {i}, que no es un ítem registrado")
tech = tabs[tabs.index("public static boolean isTechTabItem"):tabs.index("private static ItemStack iconOrFallback")]
for i in ids:
    need(f'path.equals("{i}")' not in tech, f"{i} sigue en la pestaña Tech")
need("!isVehicleTabItem(path)" in tabs, "la pestaña de decoración no excluye los vehículos")
need("BEAUTIFUL_DECOR_VEHICLES" in (JAVA / "TabOrder.java").read_text(encoding="utf-8"), "TabOrder no mueve la pestaña de vehículos al final")
need("BEAUTIFUL_DECOR_VEHICLES" in (JAVA / "client/CreativeSidebar.java").read_text(encoding="utf-8"), "CreativeSidebar no conoce la pestaña")
need('p.equals("motorcycle")' not in (JAVA / "init/ModCategory.java").read_text(encoding="utf-8"), "la moto sigue en una categoría de la pestaña de decoración")
for name in ("es_es.json", "en_us.json"):
    data = json.loads((LANG / name).read_text(encoding="utf-8"))
    need("itemGroup.beautifuldecor_vehicles" in data, f"{name}: falta itemGroup.beautifuldecor_vehicles")
need(re.search(r"version = '(2\.0\.[1-9]|2\.[1-9]\.\d+|[3-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")), "build.gradle no está en 2.0.1 o superior")
need(re.search(r"mod_version=(2\.0\.[1-9]|2\.[1-9]\.\d+|[3-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")), "gradle.properties no está en 2.0.1 o superior")
need("V2.0.1" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V2.0.1")
need("PARCHE V2.0.1" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V2.0.1")
if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("pestaña de vehículos:", ", ".join(ids), "- bien")
print("OK")
