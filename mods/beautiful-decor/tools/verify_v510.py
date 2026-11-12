#!/usr/bin/env python3
"""Comprueba SOLO la V5.1.0: metro eléctrico (vía, cabeza y vagón) sobre el sistema de trenes."""
import json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
J = ROOT / "src/main/java/com/example/beautifuldecor"
A = ROOT / "src/main/resources/assets/beautifuldecor"
errors = []
def need(c, m):
    if not c:
        errors.append(m)
def read(p): return Path(p).read_text(encoding="utf-8")
def run(cmd, **kw):
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, **kw)

need("version = '5.1.0'" in read(ROOT / "build.gradle") and "mod_version=5.1.0" in read(ROOT / "gradle.properties"), "versión 5.1.0")
need("V5.1.0" in read(ROOT / "README.md") and read(ROOT / "NUEVOS_BLOQUES.txt").startswith("PARCHE V5.1.0"), "docs")
need('PROTOCOL = "24"' in read(J / "network/ModMessages.java") and "TrainInputPacket" in read(J / "network/ModMessages.java"), "protocolo 24 y paquete registrado")

# ---- registros
need("FREIGHT_RAIL" in read(J / "init/ModBlocks.java") and "PASSENGER_RAIL" in read(J / "init/ModBlocks.java") and "METRO_RAIL" in read(J / "init/ModBlocks.java"), "bloques de riel")
items = read(J / "init/ModItems.java")
for n in ("FREIGHT_RAIL", "PASSENGER_RAIL", "FREIGHT_LOCO", "COAL_WAGON", "FOOD_WAGON", "MINERAL_WAGON", "STEAM_CLASSIC", "STEAM_MODERN", "COACH_LOW", "COACH_MID", "COACH_HIGH", "METRO_RAIL", "METRO_HEAD", "METRO_CAR"):
    need(f"{n} = ITEMS.register" in items, f"ítem {n}")
need("TRAIN_CAR" in read(J / "init/ModEntities.java"), "entidad TRAIN_CAR")
ce = read(J / "client/ClientEvents.java")
need("TrainCarRenderer::new" in ce and "TrainCarModel.layer(" in ce, "renderizador y capas registrados")
need('"passenger_rail", "steam_classic", "steam_modern", "coach_low", "coach_mid", "coach_high"' in read(J / "init/ModCreativeTabs.java") and '"metro_rail", "metro_head", "metro_car"' in read(J / "init/ModCreativeTabs.java"), "pestaña de vehículos")
need("TrainCarEntity" in read(J / "client/VehicleCameraZoom.java"), "zoom de cámara")
need("stride(" in read(J / "item/TrainCarItem.java") and "double length" in read(J / "entity/TrainCarEntity.java"), "largo por coche y distancia de enganche")

# ---- pintura: colores de referencia de Java = los del generador
gen = read(ROOT / "tools/v480/trains.py")
paint = read(J / "entity/VehiclePaint.java")
extras = read(J / "entity/VehiclePaintExtras.java")
NEW = ("steam_classic", "steam_modern", "coach_low", "coach_mid", "coach_high", "metro_head", "metro_car")
for tex in ("freight_loco", "coal_wagon", "food_wagon", "mineral_wagon") + NEW:
    need(f'"{tex}"' in paint and f'"{tex}"' in extras, f"pintura {tex}")
    for suf in ("", "_paint", "_regions"):
        need((A / f"textures/entity/{tex}{suf}.png").exists(), f"falta textura {tex}{suf}")
    need((A / f"textures/item/{tex}.png").exists() and (A / f"models/item/{tex}.json").exists(), f"ítem {tex}")
for c in ("cargo_coal", "cargo_food", "cargo_mineral"):
    need((A / f"textures/entity/{c}.png").exists(), f"falta {c}")
for hexa in ("0xC8461E", "0x3A3E46", "0x3F8038", "0xA5562B", "0xE8C020", "0x8A8E98", "0x24583E", "0x223E76", "0x7A5032", "0x2E628E", "0x701C2A", "0xE8EAF0"):
    need(hexa in paint, f"color {hexa} en VehiclePaint")
need("(200, 70, 30)" in gen and "(58, 62, 70)" in gen and "(63, 128, 56)" in gen and "(165, 86, 43)" in gen and "(36, 88, 62)" in gen and "(34, 62, 118)" in gen and "(122, 80, 50)" in gen and "(46, 98, 142)" in gen and "(112, 28, 42)" in gen and "(232, 234, 240)" in gen, "colores del generador")
# las regiones de cada máscara no pasan del número de extras de Java
try:
    from PIL import Image
    import numpy as np
    counts = {"freight_loco": 8, "coal_wagon": 4, "food_wagon": 4, "mineral_wagon": 4, "steam_classic": 8, "steam_modern": 8, "coach_low": 6, "coach_mid": 6, "coach_high": 6, "metro_head": 8, "metro_car": 6}
    for tex, n in counts.items():
        reg = np.asarray(Image.open(A / f"textures/entity/{tex}_regions.png").convert("RGB"))
        need(int(reg[..., 0].max()) == n, f"{tex}: regiones {int(reg[..., 0].max())} != {n}")
        im = Image.open(A / f"textures/entity/{tex}.png")
        need(im.size == (1024, 1024 if tex.startswith(("coach", "metro")) else 512), f"{tex}: tamaño")
except ImportError:
    pass

# ---- rieles: JSON (carga y pasajeros)
for fam in ("freight_rail", "passenger_rail", "metro_rail"):
    bs = json.loads(read(A / f"blockstates/{fam}.json"))["variants"]
    need(len(bs) == 10, f"blockstate {fam}: 10 formas")
    for v in bs.values():
        name = v["model"].split(":")[1]
        need((A / "models" / f"{name}.json").exists(), f"falta modelo {name}")
        json.loads(read(A / "models" / f"{name}.json"))
    need((ROOT / f"src/main/resources/data/beautifuldecor/loot_tables/blocks/{fam}.json").exists(), f"botín {fam}")
    need((A / f"models/item/{fam}.json").exists() and (A / f"textures/item/{fam}.png").exists(), f"ítem {fam}")
need("family()" in read(J / "block/MetroRailBlock.java") and "family()" in read(J / "block/PassengerRailBlock.java") and "rail.family() == family" in read(J / "entity/TrainCarEntity.java"), "familias de vía")
need("seats()" in read(J / "entity/TrainCarEntity.java") and (J / "entity/TrainSeats.java").exists(), "asientos")
seats = read(J / "entity/TrainSeats.java")
for n, k in (("coach_low", 28), ("coach_mid", 20), ("coach_high", 12), ("metro_head", 2), ("metro_car", 24)):
    m = re.search(r'SEATS.put\("' + n + r'", new double\[\]\[\] \{(.*?)\}\);', seats)
    need(m is not None and m.group(1).count("{") == k, f"{n}: {k} asientos")
for f in ("es_es.json", "en_us.json"):
    d = json.loads(read(A / "lang" / f))
    for k in ("block.beautifuldecor.passenger_rail", "block.beautifuldecor.metro_rail", "hud.beautifuldecor.train_fuel", "hud.beautifuldecor.train_seated", "hud.beautifuldecor.train_no_fuel",
              "message.beautifuldecor.train_wrong_rail") + tuple(f"{p}.beautifuldecor.{n}" for n in NEW for p in ("item", "entity")) + (
              "block.beautifuldecor.freight_rail", "item.beautifuldecor.freight_loco", "item.beautifuldecor.coal_wagon", "item.beautifuldecor.food_wagon",
              "item.beautifuldecor.mineral_wagon", "entity.beautifuldecor.freight_loco", "entity.beautifuldecor.coal_wagon", "entity.beautifuldecor.food_wagon",
              "entity.beautifuldecor.mineral_wagon", "hud.beautifuldecor.train_status", "hud.beautifuldecor.train_cargo", "hud.beautifuldecor.train_controls",
              "hud.beautifuldecor.train_end_of_track", "message.beautifuldecor.train_no_track"):
        need(k in d, f"{f}: falta {k}")
model = read(J / "client/TrainCarModel.java")
need(all(f"create{n}Layer" in model for n in ("FreightLoco", "CoalWagon", "FoodWagon", "MineralWagon", "SteamClassic", "SteamModern", "CoachLow", "CoachMid", "CoachHigh", "MetroHead", "MetroCar")), "capas del modelo")
need('hasChild("w" + i)' in model and model.count("getChild(") >= 3 and '"w15"' in model, "ruedas del modelo")

# ---- pruebas puras y sintaxis
javac, java = shutil.which("javac"), shutil.which("java")
work = Path(tempfile.mkdtemp(prefix="v510_"))
try:
    if javac and java:
        out = work / "cls"; out.mkdir()
        for tests, srcs, label in ((["RailPathTest"], ["RailPath", "TrainPhysics", "VehiclePhysics"], "RailPathTest"),
                                   (["VehiclePhysicsTest"], ["VehiclePhysics"], "VehiclePhysicsTest"),
                                   (["FlightPhysicsTest"], ["FlightPhysics"], "FlightPhysicsTest")):
            r = run([javac, "-d", str(out)] + [str(J / f"entity/{s}.java") for s in srcs] + [str(ROOT / f"tools/v480/java/{t}.java") for t in tests])
            need(r.returncode == 0, f"no compila {label}:\n" + r.stderr[:800])
            if r.returncode == 0:
                r = run([java, "-cp", str(out), label])
                need(r.returncode == 0 and f"{label} OK" in r.stdout, f"{label} falló:\n" + r.stdout[:600] + r.stderr[:600])
        files = [J / p for p in ("entity/TrainCarEntity.java", "entity/TrainSeats.java", "block/PassengerRailBlock.java", "block/MetroRailBlock.java", "entity/RailPath.java", "entity/TrainPhysics.java", "item/TrainCarItem.java", "block/FreightRailBlock.java",
                                 "network/TrainInputPacket.java", "client/TrainCarRenderer.java", "client/TrainClientEvents.java", "client/TrainCarModel.java",
                                 "entity/VehiclePaint.java", "entity/VehiclePaintExtras.java", "init/ModItems.java", "init/ModEntities.java", "init/ModBlocks.java")]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "5000", "-d", str(work / "syn")] + [str(f) for f in files], cwd=work)
        ignore = ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "package com.", "package net.", "package org.", "cannot access",
                  "incompatible types", "cannot be applied", "no suitable", "cannot infer", "cannot be dereferenced", "constructor", "bad operand", "invalid method reference",
                  "has private access", "cannot be referenced", "static import only", "unreported exception", "not a functional interface", "lambda body", "incomparable types",
                  "cannot return", "is not abstract and does not override", "cannot be inherited", "method does not override", "unexpected type", "cannot be converted")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis:\n" + "\n".join(bad[:10]))
finally:
    shutil.rmtree(work, ignore_errors=True)
if errors:
    print("FALLÓ la verificación V5.1.0:")
    [print(" -", x) for x in errors]
    sys.exit(1)
print("OK V5.1.0: metro; solo se verificó esta versión.")
