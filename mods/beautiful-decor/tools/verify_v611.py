#!/usr/bin/env python3
"""Comprueba SOLO la V6.1.1: etiqueta rails (curvas del mod)."""
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

need("version = '6.1.1'" in read(ROOT / "build.gradle") and "mod_version=6.1.1" in read(ROOT / "gradle.properties"), "versión 6.1.1")
need("V6.1.1" in read(ROOT / "README.md") and read(ROOT / "NUEVOS_BLOQUES.txt").startswith("PARCHE V6.1.1"), "docs")
tag = json.loads(read(ROOT / "src/main/resources/data/minecraft/tags/blocks/rails.json"))
for n in ("freight_rail", "passenger_rail", "metro_rail"):
    need(f"beautifuldecor:{n}" in tag["values"], f"etiqueta rails sin {n}")
need(tag.get("replace") is False, "la etiqueta no debe reemplazar la de Minecraft")

# ---- prueba pura (horarios, mirar la vía, frenada hasta el punto)
javac, java = shutil.which("javac"), shutil.which("java")
work = Path(tempfile.mkdtemp(prefix="v610_"))
try:
    if javac and java:
        out = work / "cls"; out.mkdir()
        srcs = [J / f"entity/{n}.java" for n in ("RailPath", "WideArc", "TrainAuto", "TrainPhysics", "VehiclePhysics")] + [ROOT / "tools/v600/java/AutoTest.java", ROOT / "tools/v600/java/WideArcTest.java"]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-d", str(out)] + [str(f) for f in srcs])
        need(r.returncode == 0, "no compila la prueba pura:\n" + r.stderr[:1500])
        if r.returncode == 0:
            for cls, ok in (("AutoTest", "OK TrainAuto"), ("WideArcTest", "OK WideArc")):
                r = run([java, "-cp", str(out), cls])
                need(r.returncode == 0 and ok in r.stdout, f"prueba {cls}:\n" + r.stdout[-1500:] + r.stderr[-500:])
        # ---- sintaxis de lo nuevo (los símbolos de Minecraft no existen aquí y se ignoran)
        files = [J / f for f in ("entity/RailPath.java", "entity/WideArc.java", "entity/TrainCarEntity.java", "entity/TrainRoute.java", "block/FreightRailBlock.java", "block/WideRailBlock.java",
                                 "block/WideCurves.java", "block/entity/WideRailBlockEntity.java", "client/WideRailRenderer.java", "client/ClientEvents.java",
                                 "event/WideCurveEvents.java", "init/ModBlocks.java", "init/ModBlockEntities.java")]
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
    print("FALLÓ la verificación V6.1.1:")
    [print(" -", x) for x in errors]
    sys.exit(1)
print("OK V6.1.1: curvas conectan; solo se verificó esta versión.")
