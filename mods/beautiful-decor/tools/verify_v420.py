#!/usr/bin/env python3
"""Comprueba SOLO la V4.2.0: radio de mesa y bafles vinculables, sincronizada con la lista de emisoras de los autos."""
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


need(re.search(r"version = '4\.([3-9]\d*\.\d+|2\.\d+)'|version = '([5-9]|\d{2,})\.", read(ROOT / "build.gradle")), "build.gradle no está en 4.2.0 o superior")
need(re.search(r"mod_version=4\.([3-9]\d*\.\d+|2\.\d+)|mod_version=([5-9]|\d{2,})\.", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.2.0 o superior")
need("V4.2.0" in read(ROOT / "README.md"), "README sin la entrada V4.2.0")
nb = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nb.startswith("PARCHE V4.2.0") and "PARCHE V4.1.2" in nb, "NUEVOS_BLOQUES debe empezar con la V4.2.0 y conservar la V4.1.2")
mm = code(JAVA / "network/ModMessages.java")
need('PROTOCOL = "20"' in mm, "el protocolo debe ser 20")
need("RadioBlockPacket.class" in mm and "RadioStatePacket.class" in mm and "PLAY_TO_CLIENT" in mm[mm.index("RadioStatePacket.class"):mm.index("RadioStatePacket.class") + 120], "paquetes de radio sin registrar")

# ---- registro, pestañas, textos
blocks, items, bes = code(JAVA / "init/ModBlocks.java"), code(JAVA / "init/ModItems.java"), code(JAVA / "init/ModBlockEntities.java")
for n in ("radio_table", "radio_speaker"):
    need(f'BLOCKS.register("{n}"' in blocks, f"bloque {n} sin registrar")
    need(f'ITEMS.register("{n}"' in items, f"ítem {n} sin registrar")
    need((AS / f"blockstates/{n}.json").exists(), f"blockstate {n}")
    need((AS / f"models/item/{n}.json").exists() and (AS / f"textures/item/{n}.png").exists(), f"ítem {n} sin modelo o icono")
    need((DATA / f"loot_tables/blocks/{n}.json").exists(), f"loot table {n}")
need("RADIO_SPEAKER" in bes and "RadioSpeakerBlockEntity::new" in bes, "block entity del bafle")
need("RadioSpeakerItem(ModBlocks.RADIO_SPEAKER.get()" in items, "el bafle debe ser RadioSpeakerItem")
tabs = code(JAVA / "init/ModCreativeTabs.java")
need('path.equals("radio_table")' in tabs and 'path.equals("radio_speaker")' in tabs, "radio y bafle deben ir en Tecnología")
need('"radio_table", "radio_speaker"' in code(JAVA / "init/ModCreativeOrder.java"), "orden de Tecnología")
es, en = json.loads(read(AS / "lang/es_es.json")), json.loads(read(AS / "lang/en_us.json"))
for k in ("block.beautifuldecor.radio_table", "block.beautifuldecor.radio_speaker", "tooltip.beautifuldecor.radio.speaker_linked", "tooltip.beautifuldecor.radio.speaker_hint",
          "message.beautifuldecor.radio.linked_item", "message.beautifuldecor.radio.speaker_unlinked", "message.beautifuldecor.radio.other_dimension",
          "message.beautifuldecor.radio.speaker_ready", "message.beautifuldecor.radio.speaker_status"):
    need(k in es and k in en, f"falta la clave {k}")
used = set(re.findall(r'"((?:message|tooltip)\.beautifuldecor\.radio\.[a-z_]+)"', "".join(code(p) for p in (JAVA / "block/RadioSpeakerBlock.java", JAVA / "block/RadioTableBlock.java", JAVA / "item/RadioSpeakerItem.java"))))
for k in used:
    need(k in es and k in en, f"clave usada sin traducir: {k}")

# ---- modelos y texturas: todo lo que se nombra existe
for f in ("radio_table", "radio_table_on", "radio_speaker"):
    m = json.loads(read(AS / f"models/block/{f}.json"))
    for k, t in m["textures"].items():
        need((AS / "textures" / (t.split(":")[1] + ".png")).exists(), f"{f}: falta la textura {t}")
    refs = set()
    for el in m["elements"]:
        for face in el["faces"].values():
            refs.add(face["texture"])
    for r in refs:
        need(r.startswith("#") and r[1:] in m["textures"], f"{f}: cara con textura {r} sin definir")
bs = json.loads(read(AS / "blockstates/radio_table.json"))["variants"]
need(len(bs) == 8 and all(("on=true" in k) == k.endswith("true") for k in bs), "blockstate de la radio: 4 giros x 2 estados")
for k, v in bs.items():
    need(v["model"].endswith("_on") == ("on=true" in k), f"blockstate {k} usa el modelo equivocado")
need(len(json.loads(read(AS / "blockstates/radio_speaker.json"))["variants"]) == 4, "blockstate del bafle: 4 giros")

# ---- la lista de emisoras es la misma que la de los autos (sincronizadas)
scr = code(JAVA / "client/VehicleRadioScreen.java")
need("RadioFavorites.list()" in scr and "RadioFavorites.add(" in scr and "implements RadioSource" in scr and "RadioSource vehicle" in scr, "el menú compartido debe usar RadioFavorites y RadioSource")
need("VehicleEntity vehicle;" not in scr, "el menú no debe seguir atado al vehículo")
need("new VehicleRadioScreen(new RadioBlockClient.TableSource(pos))" in code(JAVA / "client/ClientHooks.java"), "la radio de mesa debe abrir el mismo menú")
need("RadioBlockClient.tick(minecraft)" in code(JAVA / "client/GarageClientEvents.java") and "RadioBlockClient.reset()" in code(JAVA / "client/GarageClientEvents.java"), "RadioBlockClient sin conectar al tick/salida")

# ---- servidor
hub = code(JAVA / "radio/RadioHub.java")
need("extends SavedData" in hub and "PacketDistributor.DIMENSION" in hub and "sendAll" in hub, "RadioHub")
ev = code(JAVA / "event/RadioEvents.java")
need(all(x in ev for x in ("PlayerLoggedInEvent", "PlayerChangedDimensionEvent", "PlayerRespawnEvent")), "RadioEvents debe sincronizar al entrar, cambiar de dimensión y reaparecer")
pk = code(JAVA / "network/RadioBlockPacket.java")
need("REACH" in pk and "instanceof RadioTableBlock" in pk and "RadioStations.cleanUrl" in pk and "isLoaded" in pk, "el paquete debe validar cercanía, bloque y dirección")
tb = code(JAVA / "block/RadioTableBlock.java")
need("RadioHub.get(sl).remove(sl, pos)" in tb and "instanceof RadioSpeakerItem" in tb and "openRadioScreen" in tb, "bloque de la radio")
sb = code(JAVA / "block/RadioSpeakerBlock.java")
need("getLinkDimension" in sb and "setRadio" in sb, "bafle: vínculo y dimensión")
need("CLIENT.remove(this)" in code(JAVA / "block/entity/RadioSpeakerBlockEntity.java") and code(JAVA / "block/entity/RadioSpeakerBlockEntity.java").count("CLIENT.remove(this)") == 2, "el bafle debe quitarse de la lista del cliente al salir")
cl = code(JAVA / "client/RadioBlockClient.java")
need("RadioPicker.pick(candidates, RadioPicker.MAX_PLAYING)" in cl and "setRange(6.0, 30.0)" in cl and "RadioSpeakerBlockEntity.CLIENT" in cl, "reproducción de radios y bafles")

# ---- Java puro + sintaxis
javac, java = shutil.which("javac"), shutil.which("java")
work = Path(tempfile.mkdtemp(prefix="v420_"))
try:
    if not (javac and java):
        notes.append("sin javac/java: no se compiló ni se probó nada")
    else:
        out = work / "cls"
        out.mkdir()
        r = run([javac, "-nowarn", "-d", str(out), str(JAVA / "radio/RadioEntry.java"), str(JAVA / "radio/RadioStations.java"), str(ROOT / "tools/v420/java/RadioEntryTest.java")])
        need(r.returncode == 0, "no compila RadioEntry:\n" + r.stderr[:800])
        if r.returncode == 0:
            r = run([java, "-cp", str(out), "RadioEntryTest"])
            need(r.returncode == 0 and "RadioEntryTest OK" in r.stdout, "RadioEntryTest falló:\n" + r.stdout[:800] + r.stderr[:400])
        files = [JAVA / p for p in ("radio/RadioHub.java", "network/RadioStatePacket.java", "network/RadioBlockPacket.java", "network/ModMessages.java", "event/RadioEvents.java",
                                    "block/RadioTableBlock.java", "block/RadioSpeakerBlock.java", "block/entity/RadioSpeakerBlockEntity.java", "item/RadioSpeakerItem.java",
                                    "client/RadioSource.java", "client/RadioBlockClient.java", "client/VehicleRadioScreen.java", "client/ClientHooks.java",
                                    "client/GarageClientEvents.java", "init/ModBlocks.java", "init/ModItems.java", "init/ModBlockEntities.java",
                                    "init/ModCreativeTabs.java", "init/ModCreativeOrder.java", "init/ModCategory.java")]
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
    print("FALLÓ la verificación V4.2.0:")
    for e in errors[:25]:
        print(" -", e)
    sys.exit(1)
print("OK V4.2.0: radio de mesa y bafles; solo se verificó esta versión.")
for n in notes:
    print("  (nota)", n)
