#!/usr/bin/env python3
"""Comprueba la V2.7.0: motor de JavaScript propio (paquete browser/js), paso 1 de 4.

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que browser/js tenga sus archivos, no use Minecraft/LWJGL/Forge y no deje código de depuración;
  * que browser/ completo compile con --release 17 y -Xlint:all sin ninguna advertencia;
  * que el motor dé la misma salida que node 22 en los scripts de tools/v270/js (si no hay node, compara con las salidas
    guardadas en tools/v270/expected; los de fechas se corren en 5 zonas horarias);
  * y corre lo de la V2.6.0 (tools/verify_v260.py).

Uso: python3 tools/verify_v270.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
errors = []
notes = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def run(cmd, timeout=1800, env=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)


need(re.search(r"version = '(2\.([7-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.7.0 o superior")
need(re.search(r"mod_version=(2\.([7-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.7.0 o superior")
need("V2.7.0" in read(ROOT / "README.md"), "README sin la entrada V2.7.0")
need("PARCHE V2.7.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.7.0")

# ---------------------------------------------------------------- archivos y pureza de browser/js
JS = JAVA / "browser/js"
core = ("JsEngine", "Realm", "Parser", "Lexer", "Ast", "Js", "Closure", "Coroutine", "JsPromise", "JsGenerator", "JsAsyncGenerator", "LibObject",
        "LibArray", "LibString", "LibNumber", "LibJson", "LibRegExp", "LibDate", "LibCollections", "LibPromise", "LibGlobal", "Inspect",
        "NumFmt", "V8Math", "DateUtil")
for name in core:
    need((JS / f"{name}.java").exists(), f"browser/js/{name}.java no existe")
for f in sorted((JAVA / "browser").rglob("*.java")):
    src = read(f)
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need("TRACE" not in src and "System.err.println" not in src, f"{f.name}: quedó código de depuración")
for f in sorted(JS.glob("*.java")):
    need(f.read_text(encoding="utf-8").startswith("package com.example.beautifuldecor.browser.js;"), f"{f.name}: paquete incorrecto")
for t in ("tools/v270/difftest.py", "tools/v270/nodeRun.js", "tools/v270/java/JsRun.java"):
    need((ROOT / t).exists(), f"falta {t}")
scripts = sorted((ROOT / "tools/v270/js").glob("*.js"))
need(len(scripts) >= 19, f"faltan scripts de prueba en tools/v270/js (hay {len(scripts)})")
for s in scripts:
    need(any((ROOT / "tools/v270/expected").glob(s.name + "*.out")), f"{s.name}: sin salida esperada guardada")

# ---------------------------------------------------------------- compilación sin advertencias y comparación con node
javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if javac is None or java is None:
    notes.append("sin javac/java: no se compiló ni se probó el motor")
else:
    out = Path(tempfile.mkdtemp(prefix="v270_"))
    try:
        srcs = [str(p) for p in (JAVA / "browser").rglob("*.java")]
        tests = [str(p) for p in (ROOT / "tools/v270/java").glob("*.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(out)] + srcs + tests, env=env)
        need(r.returncode == 0, "no compilan browser/ y las pruebas de tools/v270/java:\n" + r.stderr[-1500:])
        need("warning" not in r.stderr, "browser/ compila con advertencias:\n" + r.stderr[-1500:])
        if r.returncode == 0:
            if shutil.which("node") is None:
                notes.append("sin node: se compararon las salidas con las guardadas en tools/v270/expected")
            r = run([sys.executable, str(ROOT / "tools/v270/difftest.py"), str(out)], timeout=1800, env=env)
            need(r.returncode == 0, "el motor de JavaScript no coincide con node:\n" + (r.stdout + r.stderr)[-2500:])
            summary = [l for l in r.stdout.splitlines() if l.startswith("RESULTADO")]
            if summary:
                notes.append(summary[0])
    finally:
        shutil.rmtree(out, ignore_errors=True)

# ---------------------------------------------------------------- lo de la V2.6.0
r = run([sys.executable, str(ROOT / "tools/verify_v260.py")], timeout=2400, env=env)
need(r.returncode == 0 and "OK V2.6" in r.stdout, "verify_v260 falló:\n" + (r.stdout + r.stderr)[-1500:])

if errors:
    print("FALLÓ la verificación V2.7.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.7.0: motor de JavaScript propio (browser/js), igual que node 22 en lenguaje, biblioteca, JSON, fechas, promesas, generadores y consola; compila sin advertencias.")
for n in notes:
    print("  (nota)", n)
