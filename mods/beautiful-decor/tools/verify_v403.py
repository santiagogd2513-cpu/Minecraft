#!/usr/bin/env python3
"""Comprueba SOLO la V4.0.3: «Escucharme» encendido por defecto + aviso de audio en el menú del micrófono. No corre verificaciones anteriores.

Uso: python3 tools/verify_v403.py
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
CL = JAVA / "megaphone/client"
LANG = ROOT / "src/main/resources/assets/beautifuldecor/lang"
errors, notes = [], []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def code(p):
    s = re.sub(r"/\*.*?\*/", "", read(p), flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


need(re.search(r"version = '4\.(0\.([3-9]|\d{2,})|[1-9]\d*\.\d+)'|version = '([5-9]|\d{2,})\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 4.0.3 o superior")
need(re.search(r"mod_version=4\.(0\.([3-9]|\d{2,})|[1-9]\d*\.\d+)|mod_version=([5-9]|\d{2,})\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.0.3 o superior")
need("V4.0.3" in read(ROOT / "README.md"), "README sin la entrada V4.0.3")
nb = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nb.startswith("PARCHE V4.0.3") and "PARCHE V4.0.2" in nb, "NUEVOS_BLOQUES debe empezar con la V4.0.3 y conservar la V4.0.2")
need('PROTOCOL = "19"' in code(JAVA / "network/ModMessages.java"), "el protocolo no debía cambiar (19)")

client = code(CL / "PaClient.java")
need("boolean listenSelf = true;" in client, "«Escucharme» debe venir encendido")
need("received++" in client and "resetStats" in client, "contador de bloques recibidos")
play = code(CL / "PaPlayback.java")
need(play.count("alGetError()") >= 4 and "problem = " in play and "played++" in play, "PaPlayback debe revisar errores de OpenAL y contar")
need(play.rfind("AL10.alGetError();", 0, play.index("AL10.alBufferData")) != -1, "hay que limpiar errores viejos antes de subir el audio")
screen = code(CL / "MicrophoneScreen.java")
need("PaPlayback.problem()" in screen and "PaClient.received()" in screen and "PaClient.resetStats()" in screen, "el menú debe mostrar el aviso de audio y reiniciar los contadores")

es = json.loads(read(LANG / "es_es.json"))
en = json.loads(read(LANG / "en_us.json"))
used = set()
for f in CL.glob("*.java"):
    used |= set(re.findall(r'"(screen\.beautifuldecor\.microphone\.[a-z_]+)"', code(f)))
for key in sorted(used):
    need(key in es and key in en, f"falta el texto {key} en es o en")
for key in ("playback_problem", "playback_none", "playback_ok"):
    k = f"screen.beautifuldecor.microphone.{key}"
    need(k in used, f"texto sin usar: {k}")
    need(es.get(k) and en.get(k) and es[k] != en[k], f"{k}: falta o igual en los dos idiomas")
    need(len(re.findall("%s", es[k])) == len(re.findall("%s", en[k])), f"{k}: distinta cantidad de %s")
need(len(re.findall("%s", es["screen.beautifuldecor.microphone.playback_ok"])) == 2, "playback_ok lleva 2 números")

javac = shutil.which("javac")
if not javac:
    notes.append("sin javac: no se revisó la sintaxis")
else:
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    work = Path(tempfile.mkdtemp(prefix="v403_"))
    try:
        files = [CL / "PaClient.java", CL / "PaPlayback.java", CL / "MicrophoneScreen.java"]
        r = subprocess.run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "5000", "-d", str(work)] + [str(f) for f in files], capture_output=True, text=True,
                           encoding="utf-8", errors="replace", env=env, cwd=work)
        ignore = ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "package com.", "package net.", "package org.", "cannot access", "incompatible types", "cannot be applied",
                  "no suitable", "cannot infer", "cannot be dereferenced", "constructor", "bad operand", "invalid method reference", "has private access", "cannot be referenced")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis:\n" + "\n".join(bad[:10]))
        notes.append("la sintaxis del Java cambiado es correcta (los tipos de Minecraft y de LWJGL no se pudieron comprobar sin ellos)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V4.0.3:")
    for e in errors[:20]:
        print(" -", e)
    sys.exit(1)
print("OK V4.0.3: «Escucharme» por defecto y aviso de audio; solo se verificó esta versión.")
for n in notes:
    print("  (nota)", n)
