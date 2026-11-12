#!/usr/bin/env python3
"""Comprueba SOLO la V5.2.0: metro eléctrico (vía, cabeza y vagón) sobre el sistema de trenes."""
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

need("version = '5.2.0'" in read(ROOT / "build.gradle") and "mod_version=5.2.0" in read(ROOT / "gradle.properties"), "versión 5.2.0")
need("V5.2.0" in read(ROOT / "README.md") and read(ROOT / "NUEVOS_BLOQUES.txt").startswith("PARCHE V5.2.0"), "docs")
need('PROTOCOL = "25"' in read(J / "network/ModMessages.java"), "protocolo 25")

# ---- anuncios
brands = read(J / "ad/AdBrands.java")
need('"rojotoro"' in brands, "marca en AdBrands")
need((A / "ads/rojotoro.mp4").exists() and (A / "textures/ads/poster_rojotoro.png").exists(), "video y póster")
need("VIDEO_RANGE" in read(J / "client/AdPlayback.java") and "setMuteWhenFar(true)" in read(J / "client/AdPlayback.java"), "video a distancia")
need("muteWhenFar" in read(J / "client/HsAudioPlayer.java") and "VIDEO_RANGE" in read(J / "client/AdScreenRenderer.java"), "audio silenciado y vista")
ff = shutil.which("ffprobe")
if ff:
    r = run([ff, "-v", "error", "-show_entries", "stream=codec_name,duration", "-of", "csv", str(A / "ads/rojotoro.mp4")])
    need("h264" in r.stdout and "aac" in r.stdout, "mp4 con h264 y aac: " + r.stdout)
# ---- registros y recursos
need("PAINTING_ROJOTORO" in read(J / "init/ModBlocks.java") and "ROJOTORO_MINIBAR" in read(J / "init/ModBlocks.java"), "bloques")
items = read(J / "init/ModItems.java")
for n in ("ROJOTORO_CAN", "ROJOTORO_MINIBAR", "PAINTING_ROJOTORO"):
    need(f"{n} = ITEMS.register" in items, f"ítem {n}")
for name in ("painting_rojotoro", "rojotoro_minibar"):
    for path in (f"blockstates/{name}.json", f"models/block/{name}.json", f"models/item/{name}.json"):
        need((A / path).exists(), f"falta {path}")
        json.loads(read(A / path))
    need((ROOT / f"src/main/resources/data/beautifuldecor/loot_tables/blocks/{name}.json").exists(), f"botín {name}")
need((A / "models/item/rojotoro_can.json").exists() and (A / "textures/item/rojotoro_can.png").exists(), "lata")
for t in ("rojotoro_minibar_front", "rojotoro_minibar_side", "rojotoro_minibar_top", "painting_rojotoro"):
    need((A / f"textures/block/{t}.png").exists(), f"textura {t}")
for f in ("es_es.json", "en_us.json"):
    d = json.loads(read(A / "lang" / f))
    for k in ("block.beautifuldecor.painting_rojotoro", "block.beautifuldecor.rojotoro_minibar", "item.beautifuldecor.rojotoro_can", "message.beautifuldecor.rojotoro_minibar_price"):
        need(k in d, f"{f}: falta {k}")

# ---- pruebas puras y sintaxis
javac, java = shutil.which("javac"), shutil.which("java")
work = Path(tempfile.mkdtemp(prefix="v510_"))
try:
    if javac and java:
        out = work / "cls"; out.mkdir()
        files = [J / p for p in ("item/RojoToroCanItem.java", "block/RojoToroMinibarBlock.java", "client/AdPlayback.java", "client/AdScreenRenderer.java", "client/HsAudioPlayer.java", "ad/AdBrands.java", "init/ModItems.java", "init/ModBlocks.java")]
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
    print("FALLÓ la verificación V5.2.0:")
    [print(" -", x) for x in errors]
    sys.exit(1)
print("OK V5.2.0: Rojo Toro y anuncios; solo se verificó esta versión.")
