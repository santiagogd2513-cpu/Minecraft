#!/usr/bin/env python3
"""Comprueba SOLO la V4.0.1: barra de desplazamiento en el menú de la radio de los vehículos (no corre verificaciones de versiones anteriores).

Uso: python3 tools/verify_v401.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREEN = ROOT / "src/main/java/com/example/beautifuldecor/client/VehicleRadioScreen.java"
errors, notes = [], []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


need(re.search(r"version = '4\.(0\.[1-9]|[1-9]\d*\.\d+)'|version = '([5-9]|\d{2,})\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 4.0.1 o superior")
need(re.search(r"mod_version=4\.(0\.[1-9]|[1-9]\d*\.\d+)|mod_version=([5-9]|\d{2,})\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.0.1 o superior")
need("V4.0.1" in read(ROOT / "README.md"), "README sin la entrada V4.0.1")
nb = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nb.startswith("PARCHE V4.0.1") and "PARCHE V4.0.0" in nb, "NUEVOS_BLOQUES debe empezar con la V4.0.1 y conservar la V4.0.0")
need('PROTOCOL = "19"' in read(ROOT / "src/main/java/com/example/beautifuldecor/network/ModMessages.java"), "el protocolo no debía cambiar (19)")

src = re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", read(SCREEN), flags=re.S))
for text, why in (("mouseDragged", "arrastrar"), ("mouseReleased", "soltar"), ("draggingBar", "estado del arrastre"), ("scrollToPointer", "mover la lista con el puntero"),
                  ("overBar", "zona de agarre"), ("scrollable()", "mostrar la barra solo si hace falta"), ("rowsRight()", "dejar sitio a la barra"),
                  ("mouseScrolled", "la rueda sigue funcionando")):
    need(text in src, f"VehicleRadioScreen sin {why}")
need("W - 12 - rightW" not in src and "this.left + W - 24" not in src, "quedaron medidas viejas que ignoran la barra")
need(src.index("overBar(mx, my)") < src.index("RadioFavorites.remove"), "la barra debe atenderse antes que las filas (la ✕ está al lado)")

# ---- las cuentas de la barra, copiadas de Java, en todo el recorrido
def thumb_h(lh, content):
    return max(18, lh * lh // max(1, content))


def thumb_y(top, lh, content, scroll):
    return top + (lh - thumb_h(lh, content)) * scroll // max(1, content - lh)


def scroll_to(top, lh, content, off, my):
    span = max(1, lh - thumb_h(lh, content))
    t = (my - off - top) / span
    s = int(round(t * (content - lh)))      # Math.round en Java redondea .5 hacia arriba; aquí la diferencia no importa (+-1 px)
    return max(0, min(s, max(0, content - lh)))


for lh in (60, 100, 108, 130):
    for content in (lh + 1, lh + 20, lh * 2, lh * 4, 1200):
        top = 120
        h = thumb_h(lh, content)
        need(18 <= h <= lh, f"tirador fuera de medida (lh={lh}, contenido={content}, h={h})")
        last = None
        for scroll in range(0, content - lh + 1):
            y = thumb_y(top, lh, content, scroll)
            need(top <= y <= top + lh - h, f"el tirador se sale de la pista (scroll={scroll})")
            need(last is None or y >= last, "el tirador debe bajar al bajar la lista")
            last = y
            # agarrar el tirador por el medio y arrastrar exactamente hasta donde está debe dejar la lista casi igual
            off = h / 2
            back = scroll_to(top, lh, content, off, y + off)
            tol = max(2, (content - lh) // max(1, lh - h) + 1)       # un píxel de la barra equivale a esta cantidad de lista
            need(abs(back - scroll) <= tol, f"ida y vuelta (lh={lh}, contenido={content}): {scroll} -> {back}")
        need(scroll_to(top, lh, content, h / 2, top - 500) == 0, "arrastrar más arriba del inicio debe quedarse en 0")
        need(scroll_to(top, lh, content, h / 2, top + lh + 500) == content - lh, "arrastrar más abajo del final debe llegar al final")
notes.append("las cuentas de la barra son coherentes en todo el recorrido")

# ---- sintaxis del Java cambiado
javac = shutil.which("javac")
if not javac:
    notes.append("sin javac: no se revisó la sintaxis")
else:
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    work = Path(tempfile.mkdtemp(prefix="v401_"))
    try:
        r = subprocess.run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(work), str(SCREEN)], capture_output=True, text=True,
                           encoding="utf-8", errors="replace", env=env, cwd=work)
        ignore = ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
                  "cannot infer", "no suitable", "cannot be dereferenced", "cannot access", "package com.", "package net.", "package org.", "method does not override",
                  "cannot be referenced", "constructor", "has private access", "is not public", "cannot be accessed", "bad operand", "invalid method reference")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis:\n" + "\n".join(bad[:10]))
        notes.append("la sintaxis del Java cambiado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V4.0.1:")
    for e in errors[:20]:
        print(" -", e)
    sys.exit(1)
print("OK V4.0.1: barra de desplazamiento en la radio; solo se verificó esta versión.")
for n in notes:
    print("  (nota)", n)
