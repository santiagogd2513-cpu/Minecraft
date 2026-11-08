#!/usr/bin/env python3
"""Comprueba SOLO la V4.0.2: arreglo del aviso «No se pudieron mover las pestañas de Beautiful Decor al final» (UnsupportedOperationException en
TabOrder.moveToEnd, visto en latest.log). No corre verificaciones de versiones anteriores.

Uso: python3 tools/verify_v402.py
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
errors, notes = [], []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


need(re.search(r"version = '4\.(0\.([2-9]|\d{2,})|[1-9]\d*\.\d+)'|version = '([5-9]|\d{2,})\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 4.0.2 o superior")
need(re.search(r"mod_version=4\.(0\.([2-9]|\d{2,})|[1-9]\d*\.\d+)|mod_version=([5-9]|\d{2,})\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.0.2 o superior")
need("V4.0.2" in read(ROOT / "README.md"), "README sin la entrada V4.0.2")
nb = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nb.startswith("PARCHE V4.0.2") and "PARCHE V4.0.1" in nb, "NUEVOS_BLOQUES debe empezar con la V4.0.2 y conservar la V4.0.1")
need('PROTOCOL = "19"' in read(JAVA / "network/ModMessages.java"), "el protocolo no debía cambiar (19)")

order = read(JAVA / "TabOrder.java")
need("TabReorder.moveToEnd(CreativeModeTabRegistry.class" in order, "TabOrder debe usar TabReorder")
need("clear()" not in order and "getDeclaredFields" not in order, "TabOrder ya no debe tocar las listas a mano")
need('LOGGER.warn("Beautiful Decor: no se pudieron mover las pestañas al final: {}", t.toString())' in order, "el aviso final no debe imprimir una pila entera")
reorder = read(JAVA / "TabReorder.java")
need("UnsupportedOperationException" in reorder, "TabReorder debe tolerar listas de solo lectura")
imports = re.findall(r"^import ([\w.]+);", reorder, re.M)
need(all(i.startswith("java.") for i in imports), f"TabReorder debe ser Java puro (para probarlo): {imports}")
need("TabOrder::moveToEnd" in read(JAVA / "BeautifulDecor.java"), "BeautifulDecor debe seguir llamando a TabOrder::moveToEnd")

javac, java = shutil.which("javac"), shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if not javac or not java:
    errors.append("hace falta javac y java para correr la prueba")
else:
    work = Path(tempfile.mkdtemp(prefix="v402_"))
    try:
        r = subprocess.run([javac, "-encoding", "UTF-8", "-d", str(work), str(JAVA / "TabReorder.java"), str(ROOT / "tools/v402/java/TabReorderTest.java")],
                           capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, cwd=work)
        need(r.returncode == 0, "no compila la prueba:\n" + r.stderr[:1500])
        if r.returncode == 0:
            r = subprocess.run([java, "-cp", str(work), "TabReorderTest"], capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, cwd=work)
            need(r.returncode == 0 and "TAB REORDER OK" in r.stdout, "falla la prueba:\n" + r.stdout[-1500:] + r.stderr[-500:])
            notes.append("la prueba reproduce el aviso del log con el código anterior y confirma que el nuevo lo resuelve")
        # sintaxis de TabOrder (los tipos de Minecraft/Forge no existen aquí)
        r = subprocess.run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "2000", "-d", str(work), str(JAVA / "TabOrder.java"), str(JAVA / "TabReorder.java")],
                           capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, cwd=work)
        ignore = ("cannot find symbol", "does not exist", "package com.", "package net.", "package org.", "cannot access", "incompatible types")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis en TabOrder:\n" + "\n".join(bad[:10]))
        notes.append("la sintaxis de TabOrder es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V4.0.2:")
    for e in errors[:20]:
        print(" -", e)
    sys.exit(1)
print("OK V4.0.2: arreglo del aviso de las pestañas; solo se verificó esta versión.")
for n in notes:
    print("  (nota)", n)
