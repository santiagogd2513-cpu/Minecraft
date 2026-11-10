#!/usr/bin/env python3
"""Comprueba SOLO la V4.3.2: cámaras de seguridad, monitor y pantalla."""
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
AS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
errors, notes = [], []


def need(c, m):
    if not c:
        errors.append(m)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def code(p):
    s = re.sub(r"/\*.*?\*/", "", read(p), flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def run(cmd, **kw):
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, **kw)


need(re.search(r"version = '(4\.([3-9]\d*)\.\d+|([5-9]|\d{2,})\.)", read(ROOT / "build.gradle")), "build.gradle no está en 4.3.0 o superior")
need(re.search(r"mod_version=(4\.([3-9]\d*)\.\d+|([5-9]|\d{2,})\.)", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.3.0 o superior")
need("V4.3.2" in read(ROOT / "README.md"), "README sin la entrada V4.3.2")
nb = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nb.startswith("PARCHE V4.3.2") and "PARCHE V4.3.1" in nb, "NUEVOS_BLOQUES debe empezar con la V4.3.2 y conservar la V4.3.1")
mm = code(JAVA / "network/ModMessages.java")
need('PROTOCOL = "21"' in mm, "el protocolo debe ser 21")
for p in ("CameraListPacket", "CameraEditPacket", "MonitorSelectPacket", "OpenCameraEditPacket"):
    need(f"{p}.class" in mm, f"paquete {p} sin registrar")

blocks, items, bes = code(JAVA / "init/ModBlocks.java"), code(JAVA / "init/ModItems.java"), code(JAVA / "init/ModBlockEntities.java")
names = ("security_camera_black", "security_camera_white", "security_monitor", "security_screen")
es, en = json.loads(read(AS / "lang/es_es.json")), json.loads(read(AS / "lang/en_us.json"))
for n in names:
    need(f'BLOCKS.register("{n}"' in blocks, f"bloque {n} sin registrar")
    need(f'ITEMS.register("{n}"' in items, f"ítem {n} sin registrar")
    need((AS / f"blockstates/{n}.json").exists(), f"blockstate {n}")
    need((AS / f"models/item/{n}.json").exists() and (AS / f"textures/item/{n}.png").exists(), f"ítem {n} sin modelo o icono")
    need((DATA / f"loot_tables/blocks/{n}.json").exists(), f"loot table {n}")
    need(f"block.beautifuldecor.{n}" in es and f"block.beautifuldecor.{n}" in en, f"falta el nombre de {n}")
need("SECURITY_CAMERA" in bes and "SECURITY_MONITOR" in bes, "block entities sin registrar")
need('startsWith("security_")' in code(JAVA / "init/ModCreativeTabs.java"), "las cámaras deben ir en Tecnología")
need("security_monitor" in code(JAVA / "init/ModCreativeOrder.java"), "orden de Tecnología")

# claves gui usadas
used = set()
for p in JAVA.glob("client/Camera*.java"):
    used |= set(re.findall(r'"(gui\.beautifuldecor\.camera\.[a-z_]+)"', code(p)))
for p in (JAVA / "client/SecurityMonitorRenderer.java",):
    used |= set(re.findall(r'"(gui\.beautifuldecor\.camera\.[a-z_]+)"', code(p)))
need(len(used) >= 18, "pocas claves gui de cámara detectadas")
for k in used:
    need(k in es and k in en, f"clave sin traducir: {k}")

# modelos / texturas
for n in names:
    bs = json.loads(read(AS / f"blockstates/{n}.json"))["variants"]
    need(len(bs) == (8 if "camera" in n else 8), f"blockstate {n}: variantes")
    for v in bs.values():
        mp = AS / "models/block" / (v["model"].split("/")[-1] + ".json")
        need(mp.exists(), f"falta el modelo {v['model']}")
        if mp.exists():
            m = json.loads(read(mp))
            for t in m["textures"].values():
                if not t.startswith("#"):
                    need((AS / "textures" / (t.split(":")[1] + ".png")).exists(), f"{mp.name}: falta textura {t}")
            for el in m["elements"]:
                for f in el["faces"].values():
                    need(f["texture"][1:] in m["textures"], f"{mp.name}: textura {f['texture']} sin definir")
for t in ("security_camera_head_black", "security_camera_head_white", "camera_noise"):
    need((AS / f"textures/block/{t}.png").exists(), f"falta la textura {t}")
for n in ("security_monitor", "security_screen"):
    lt = read(DATA / f"loot_tables/blocks/{n}.json")
    need('"px": "0"' in lt, f"loot de {n} sin condición px=0")

# código
need("openCameraListScreen" in code(JAVA / "client/ClientHooks.java") and "openCameraEditScreen" in code(JAVA / "client/ClientHooks.java"), "ClientHooks")
ce = code(JAVA / "client/ClientEvents.java")
need("SecurityCameraRenderer::new" in ce and "SecurityMonitorRenderer::new" in ce and "SecurityCameraModel.LAYER" in ce, "ClientEvents sin renderers o capa")
need(code(JAVA / "client/CameraFeedManager.java").count("catch") >= 1, "CameraFeedManager sin protección")
need("MAX_NAME" in code(JAVA / "camera/CameraInfo.java"), "CameraInfo")
fm = code(JAVA / "client/CameraFeedManager.java")
need("sharedRenderer()" in fm and "f.shared" in fm and '{"oculus", "iris"}' in fm, "modo compartido con Embeddium")
need("MAX_LIVE = 24" in fm and "MAX_FEEDS = MAX_LIVE + 1" in fm and "MAX_PER_FRAME" in fm and "frameMs < 30 ? 62" in fm, "límite de 24 cámaras / 16 fps")

javac, java = shutil.which("javac"), shutil.which("java")
work = Path(tempfile.mkdtemp(prefix="v430_"))
try:
    if not (javac and java):
        notes.append("sin javac/java: no se compiló ni se probó nada")
    else:
        out = work / "cls"
        out.mkdir()
        r = run([javac, "-nowarn", "-d", str(out)] + [str(JAVA / f"camera/{c}.java") for c in ("CameraSweep", "CameraNight", "CameraClock", "CameraInfo")] + [str(ROOT / "tools/v430/java/CameraLogicTest.java")])
        need(r.returncode == 0, "no compila la lógica de cámaras:\n" + r.stderr[:800])
        if r.returncode == 0:
            r = run([java, "-cp", str(out), "CameraLogicTest"])
            need(r.returncode == 0 and "CameraLogicTest OK" in r.stdout, "CameraLogicTest falló:\n" + r.stdout[:800] + r.stderr[:400])
        files = [p for p in (list((JAVA / "camera").glob("*.java")) + list(JAVA.glob("client/Camera*.java")) + list(JAVA.glob("client/Security*.java"))
                             + [JAVA / p for p in ("network/CameraListPacket.java", "network/CameraEditPacket.java", "network/MonitorSelectPacket.java", "network/OpenCameraEditPacket.java",
                                                   "network/ModMessages.java", "event/CameraEvents.java", "block/SecurityCameraBlock.java", "block/SecurityMonitorBlock.java",
                                                   "block/entity/SecurityCameraBlockEntity.java", "block/entity/SecurityMonitorBlockEntity.java", "client/ClientHooks.java",
                                                   "client/ClientEvents.java", "init/ModBlocks.java", "init/ModItems.java", "init/ModBlockEntities.java",
                                                   "init/ModCreativeTabs.java", "init/ModCreativeOrder.java", "init/ModCategory.java")])]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "5000", "-d", str(work / "syn")] + [str(f) for f in files], cwd=work)
        ignore = ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "package com.", "package net.", "package org.", "cannot access",
                  "incompatible types", "cannot be applied", "no suitable", "cannot infer", "cannot be dereferenced", "constructor", "bad operand", "invalid method reference",
                  "has private access", "cannot be referenced", "static import only", "unreported exception", "not a functional interface", "lambda body", "incomparable types", "cannot return",
                  "is not abstract and does not override", "cannot be inherited", "method does not override")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis:\n" + "\n".join(bad[:10]))
finally:
    shutil.rmtree(work, ignore_errors=True)

if errors:
    print("FALLÓ la verificación V4.3.2:")
    for e in errors[:25]:
        print(" -", e)
    sys.exit(1)
print("OK V4.3.2: cámaras de seguridad (hasta 24 a la vez); solo se verificó esta versión.")
for n in notes:
    print("  (nota)", n)
