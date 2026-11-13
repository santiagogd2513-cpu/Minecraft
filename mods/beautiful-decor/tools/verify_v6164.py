#!/usr/bin/env python3
"""Comprueba SOLO la V6.16.4 (depósito, puertas, televisores, carbón inicial)."""
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

need("version = '6.16.4'" in read(ROOT / "build.gradle") and "mod_version=6.16.4" in read(ROOT / "gradle.properties"), "versión 6.16.4")
need("V6.16.4" in read(ROOT / "README.md") and read(ROOT / "NUEVOS_BLOQUES.txt").startswith("PARCHE V6.16.4"), "docs")
for n in ("depot_controller_front", "depot_controller_side", "depot_controller_top"):
    need((A / f"textures/block/{n}.png").exists(), n)
m = json.loads(read(A / "models/block/depot_controller.json"))
for v in m["textures"].values():
    need((A / "textures" / (v.split(":")[1] + ".png")).exists(), f"depot: falta {v}")
json.loads(read(A / "blockstates/depot_controller.json"))
json.loads(read(ROOT / "src/main/resources/data/beautifuldecor/loot_tables/blocks/depot_controller.json"))
need((A / "models/item/depot_controller.json").exists(), "modelo de ítem depósito")
need("DEPOT_CONTROLLER" in read(J / "init/ModBlocks.java") and "DEPOT_CONTROLLER" in read(J / "init/ModItems.java") and "DEPOT_CONTROLLER" in read(J / "init/ModBlockEntities.java"), "registros del depósito")
need('"depot_controller"' in read(J / "init/ModCreativeTabs.java"), "depósito en la pestaña de piezas")
for lang in ("es_es.json", "en_us.json"):
    d = json.loads(read(A / "lang" / lang))
    for k in ("block.beautifuldecor.depot_controller", "hud.beautifuldecor.train_doors_open", "hud.beautifuldecor.train_doors_closed",
              "message.beautifuldecor.train_doors_closed_board", "message.beautifuldecor.train_tv_full", "message.beautifuldecor.station.after_depot",
              "message.beautifuldecor.station.depot_added", "message.beautifuldecor.station.depot_not_first", "message.beautifuldecor.station.pending", "message.beautifuldecor.station.pending_dup", "message.beautifuldecor.station.applied"):
        need(k in d, f"{lang}: falta {k}")
tc = read(J / "entity/TrainCarEntity.java")
need("DOORS" in tc and "toggleTrainDoors" in tc and "installTv" in tc and "setDoors(chain, auto.doors == 1)" in tc, "puertas y tele en TrainCarEntity")
need("TrainExtras.draw" in read(J / "client/TrainCarRenderer.java"), "TrainExtras en el renderer")
need("auto.doors" in tc and "elapsed >= openAt" in read(J / "entity/TrainRoute.java"), "puertas por tiempo")
need("addPending" in read(J / "block/StationControllerBlock.java") and "applyPending" in read(J / "item/TrainRemoteItem.java") and "NOTE_BLOCK_PLING" in read(J / "network/TrainMenuSavePacket.java"), "paradas pendientes y sonidos")
need("level.isLoaded(target.rail) &&" in read(J / "entity/TrainRoute.java"), "guardia de chunk en TrainRoute")
need("train_auto.dwell" in read(J / "entity/TrainRoute.java") and "autoCar.route.status()" in tc, "aviso del automático")
need("Items.COAL, 128" in read(J / "item/TrainCarItem.java"), "carbón inicial")
need("depot" in read(J / "block/StationControllerBlock.java"), "regla del depósito")
ex = read(J / "entity/VehiclePaintExtras.java")
need(ex.count('case "') >= 29 and ex.count("freight_loco") >= 2, "VehiclePaintExtras perdió entradas")
javac = shutil.which("javac")
work = Path(tempfile.mkdtemp(prefix="v6160_"))
try:
    if javac:
        names = ("entity/TrainCarEntity.java", "entity/TrainRoute.java", "client/TrainExtras.java", "client/TrainCarRenderer.java", "block/StationControllerBlock.java", "item/TrainCarItem.java", "item/TrainRemoteItem.java", "network/TrainMenuSavePacket.java",
                 "init/ModBlocks.java", "init/ModItems.java", "init/ModBlockEntities.java", "init/ModCreativeTabs.java")
        files = [J / f for f in names]
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
    print("FALLÓ la verificación V6.16.4:")
    [print(" -", x) for x in errors]
    sys.exit(1)
print("OK V6.16.4: depósito, puertas, televisores y carbón; solo se verificó esta versión.")
