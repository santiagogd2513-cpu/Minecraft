#!/usr/bin/env python3
"""Comprueba la V2.2.0: sistema de ascensores (controlador, pantalla, puerta, cabina física).

No compila nada (eso lo hace Gradle en tu equipo): revisa que cada pieza exista y esté conectada (registros, paquetes, modelos,
texturas, sonidos, blockstates, loot, idiomas, pestaña), que los límites pedidos estén en el código (cabina por defecto 3x3,
máximo 50x50) y que los métodos propios que se llaman entre clases existan.

Uso: python3 tools/verify_v220.py
"""
import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/beautifuldecor"
DATA = RES / "data/beautifuldecor"
errors = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(rel):
    p = JAVA / rel
    if not p.exists():
        errors.append(f"falta {rel}")
        return ""
    return p.read_text(encoding="utf-8")


def jload(path):
    return json.loads(path.read_text(encoding="utf-8"))


def png_size(path):
    if not path.exists():
        errors.append(f"falta {path.relative_to(ROOT)}")
        return None
    with open(path, "rb") as f:
        head = f.read(24)
    need(head[:8] == b"\x89PNG\r\n\x1a\n", f"{path.name}: no es un PNG")
    return struct.unpack(">II", head[16:24])


# ------------------------------------------------------------------------------------------------ versión y docs
need(re.search(r"version = '(2\.([2-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")),
     "build.gradle no está en 2.2.0 o superior")
need(re.search(r"mod_version=(2\.([2-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")),
     "gradle.properties no está en 2.2.0 o superior")
need("V2.2.0" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V2.2.0")
need("PARCHE V2.2.0" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V2.2.0")

# ------------------------------------------------------------------------------------------------ archivos Java
FILES = [
    "elevator/ElevatorZone.java", "elevator/ElevatorShaft.java", "entity/ElevatorCabinEntity.java",
    "block/ElevatorControllerBlock.java", "block/ElevatorDisplayBlock.java", "block/ElevatorDoorBlock.java",
    "block/entity/ElevatorControllerBlockEntity.java", "block/entity/ElevatorDisplayBlockEntity.java",
    "block/entity/ElevatorDoorBlockEntity.java", "item/ElevatorLinkBlockItem.java",
    "network/ElevatorControllerPacket.java", "network/ElevatorDisplayPacket.java", "network/ElevatorSavePacket.java",
    "network/ElevatorCallPacket.java", "client/ElevatorClientHooks.java", "client/ElevatorHumSound.java",
    "client/ElevatorCabinRenderer.java", "client/ElevatorOverlay.java", "client/DarkUi.java",
    "client/ElevatorControllerScreen.java", "client/ElevatorDisplayScreen.java",
]
SRC = {f: read(f) for f in FILES}
for f, text in SRC.items():
    need(text.count("{") == text.count("}"), f"{f}: llaves desparejadas")
    need(text.count("(") == text.count(")"), f"{f}: paréntesis desparejados")
    need(re.search(r"^package com\.example\.beautifuldecor\.[a-z.]+;", text, re.M) is not None, f"{f}: sin package")
    pkg = re.search(r"^package (.+);", text, re.M)
    if pkg:
        need(pkg.group(1).replace("com.example.beautifuldecor", "").strip(".").replace(".", "/") == str(Path(f).parent),
             f"{f}: el package no coincide con la carpeta")
    cls = Path(f).stem
    need(re.search(rf"\b(class|record|interface|enum) {cls}\b", text) is not None, f"{f}: no declara {cls}")

# ------------------------------------------------------------------------------------------------ límites pedidos
ctrl_be = SRC["block/entity/ElevatorControllerBlockEntity.java"]
need(re.search(r"MAX_WIDTH\s*=\s*50\b", ctrl_be) and re.search(r"MAX_DEPTH\s*=\s*50\b", ctrl_be), "el tamaño máximo debe ser 50 x 50")
need(re.search(r"int width\s*=\s*3;", ctrl_be) and re.search(r"int depth\s*=\s*3;", ctrl_be), "el tamaño por defecto debe ser 3 x 3")
need(re.search(r"MAX_CELLS\s*=\s*60000", ctrl_be), "MAX_CELLS (controlador) debe ser 60000")
need(re.search(r"MAX_CELLS\s*=\s*60000", SRC["entity/ElevatorCabinEntity.java"]), "MAX_CELLS (cabina) debe ser 60000")
need(re.search(r"MIN_SPEED\s*=\s*1\.0f", ctrl_be) and re.search(r"MAX_SPEED\s*=\s*20\.0f", ctrl_be), "velocidad 1..20 bloques/s")
zone = SRC["elevator/ElevatorZone.java"]
for needle in ("record ElevatorZone", "static ElevatorZone of(", "cells()", "atY(", "toAabb()", "contains("):
    need(needle in zone, f"ElevatorZone sin {needle}")
need("getClockWise()" in zone, "ElevatorZone: la columna extra debe ir a la derecha (getClockWise)")

# ------------------------------------------------------------------------------------------------ registros
blocks, items, ents, sounds, bents = (read("init/ModBlocks.java"), read("init/ModItems.java"), read("init/ModEntities.java"),
                                      read("init/ModSounds.java"), read("init/ModBlockEntities.java"))
for bid, cls in (("elevator_controller", "ElevatorControllerBlock"), ("elevator_display", "ElevatorDisplayBlock"),
                 ("elevator_door", "ElevatorDoorBlock")):
    need(f'BLOCKS.register("{bid}"' in blocks and f"new {cls}(" in blocks, f"ModBlocks no registra {bid}")
    need(f"import com.example.beautifuldecor.block.{cls};" in blocks, f"ModBlocks sin el import de {cls}")
    need(f'ITEMS.register("{bid}"' in items, f"ModItems no registra el ítem {bid}")
    need(f'BLOCK_ENTITIES.register("{bid}"' in bents, f"ModBlockEntities no registra {bid}")
    need(f"ModBlocks.{bid.upper()}.get()" in bents, f"ModBlockEntities: {bid} sin su bloque")
for cls in ("ElevatorControllerBlockEntity", "ElevatorDisplayBlockEntity", "ElevatorDoorBlockEntity"):
    need(f"import com.example.beautifuldecor.block.entity.{cls};" in bents and f"{cls}::new" in bents, f"ModBlockEntities sin {cls}")
need("new ElevatorLinkBlockItem(ModBlocks.ELEVATOR_DISPLAY.get()" in items and "new ElevatorLinkBlockItem(ModBlocks.ELEVATOR_DOOR.get()" in items,
     "la pantalla y la puerta deben ser ElevatorLinkBlockItem")
need("import com.example.beautifuldecor.item.ElevatorLinkBlockItem;" in items, "ModItems sin el import de ElevatorLinkBlockItem")
need('ENTITIES.register("elevator_cabin"' in ents and "ElevatorCabinEntity::new" in ents, "ModEntities no registra elevator_cabin")
need("import com.example.beautifuldecor.entity.ElevatorCabinEntity;" in ents, "ModEntities sin el import de ElevatorCabinEntity")
for sid in ("elevator_hum", "elevator_ding", "elevator_door"):
    need(f'simple("{sid}")' in sounds, f"ModSounds no registra {sid}")
    p = ASSETS / f"sounds/{sid}.ogg"
    need(p.exists() and p.read_bytes()[:4] == b"OggS", f"sounds/{sid}.ogg falta o no es un Ogg")
sj = jload(ASSETS / "sounds.json")
for sid in ("elevator_hum", "elevator_ding", "elevator_door"):
    need(sid in sj, f"sounds.json sin {sid}")
    for s in sj.get(sid, {}).get("sounds", []):
        name = s["name"] if isinstance(s, dict) else s
        need((ASSETS / "sounds" / (name.split(":")[1] + ".ogg")).exists(), f"sounds.json: {name} no existe")

# ------------------------------------------------------------------------------------------------ red
msgs = read("network/ModMessages.java")
m = re.search(r'PROTOCOL = "(\d+)"', msgs)
need(m and int(m.group(1)) >= 5, "el protocolo de red debe ser 5 o más")
for pk, direction in (("ElevatorControllerPacket", "PLAY_TO_CLIENT"), ("ElevatorDisplayPacket", "PLAY_TO_CLIENT"),
                      ("ElevatorSavePacket", "PLAY_TO_SERVER"), ("ElevatorCallPacket", "PLAY_TO_SERVER")):
    need(re.search(rf"messageBuilder\({pk}\.class, id\+\+, NetworkDirection\.{direction}\)", msgs), f"ModMessages no registra {pk} ({direction})")
    for fn in ("decode", "encode", "handle"):
        need(f"{pk}::{fn}" in msgs, f"ModMessages: falta {pk}::{fn}")
        src = SRC[f"network/{pk}.java"]
        need(re.search(rf"\b{fn}\(", src), f"{pk} no define {fn}")
need("sendToPlayer(" in msgs, "ModMessages sin sendToPlayer")
ids = re.findall(r"messageBuilder\(\w+\.class, id\+\+", msgs)
need(len(ids) == len(set(ids)) and len(ids) >= 4, "ModMessages: ids de mensaje repetidos")

# ------------------------------------------------------------------------------------------------ cliente
cev = read("client/ClientEvents.java")
need("ElevatorCabinRenderer::new" in cev and "ModEntities.ELEVATOR_CABIN.get()" in cev, "ClientEvents no registra el renderizador de la cabina")
ch = read("client/ClientHooks.java")
need("openElevatorController(" in ch and "openElevatorDisplay(" in ch, "ClientHooks sin los métodos del ascensor")
for pk, fn in (("ElevatorControllerPacket", "openElevatorController"), ("ElevatorDisplayPacket", "openElevatorDisplay")):
    need(f"ClientHooks.{fn}(" in SRC[f"network/{pk}.java"], f"{pk} no llama a ClientHooks.{fn}")
need("RenderLevelStageEvent" in SRC["client/ElevatorOverlay.java"], "ElevatorOverlay sin RenderLevelStageEvent")
need("isPauseScreen" in SRC["client/ElevatorControllerScreen.java"] and "isPauseScreen" in SRC["client/ElevatorDisplayScreen.java"],
     "las pantallas deben declarar isPauseScreen (no pausar el juego)")
need(re.search(r"0x[Ff]{0,2}[0-3][0-9A-Fa-f]{5}", SRC["client/DarkUi.java"]) or "0xFF1" in SRC["client/DarkUi.java"] or "0xE" in SRC["client/DarkUi.java"],
     "DarkUi sin colores oscuros")
for pk in ("ElevatorControllerScreen", "ElevatorDisplayScreen"):
    need("DarkUi." in SRC[f"client/{pk}.java"], f"{pk} no usa DarkUi (modo oscuro)")

# ------------------------------------------------------------------------------------------------ llamadas entre clases propias
shaft = SRC["elevator/ElevatorShaft.java"]
cabin = SRC["entity/ElevatorCabinEntity.java"]
for name in ("column", "floors", "controller", "travellingCabin", "applySettings", "joinColumn", "requestTrip", "defaultName", "tell"):
    need(re.search(rf"static [\w<>\[\], .?]+ {name}\(", shaft), f"ElevatorShaft sin el método estático {name}")
for name in ("capture", "isMovable"):
    need(re.search(rf"static [\w<>\[\], .?]+ {name}\(", cabin), f"ElevatorCabinEntity sin el método estático {name}")
for caller, text in SRC.items():
    for mm in re.finditer(r"ElevatorShaft\.(\w+)\(", text):
        need(re.search(rf"static [\w<>\[\], .?]+ {mm.group(1)}\(", shaft), f"{caller} llama a ElevatorShaft.{mm.group(1)}, que no existe")
    for mm in re.finditer(r"ElevatorCabinEntity\.(\w+)\(", text):
        need(re.search(rf"static [\w<>\[\], .?]+ {mm.group(1)}\(", cabin), f"{caller} llama a ElevatorCabinEntity.{mm.group(1)}, que no existe")
    for mm in re.finditer(r"ElevatorZone\.(\w+)\(", text):
        need(re.search(rf"static [\w<>\[\], .?]+ {mm.group(1)}\(", zone), f"{caller} llama a ElevatorZone.{mm.group(1)}, que no existe")
    for mm in re.finditer(r"ElevatorLinkBlockItem\.(\w+)\(", text):
        need(re.search(rf"static [\w<>\[\], .?]+ {mm.group(1)}\(", SRC["item/ElevatorLinkBlockItem.java"]),
             f"{caller} llama a ElevatorLinkBlockItem.{mm.group(1)}, que no existe")
    for mm in re.finditer(r"ElevatorControllerBlockEntity\.(\w+)\b", text):
        if mm.group(1).isupper():
            need(re.search(rf"static final \w+ {mm.group(1)}\b", ctrl_be), f"{caller}: ElevatorControllerBlockEntity.{mm.group(1)} no existe")
# métodos de instancia del controlador que se usan por otras clases
INST = set(re.findall(r"public [\w<>\[\], .?]+ (\w+)\(", ctrl_be))
for caller, text in SRC.items():
    if caller.endswith("ElevatorControllerBlockEntity.java"):
        continue
for needle in ("isCabinHere", "setCabinHere", "setCabinHereSilently", "zone", "setShape", "setFloorName", "copyShapeFrom", "getFacing",
               "getWidth", "getDepth", "getHeight", "getSpeed", "getFloorName"):
    need(needle in INST, f"ElevatorControllerBlockEntity sin el método {needle}")

# ------------------------------------------------------------------------------------------------ diseño de la cabina
need("IEntityAdditionalSpawnData" in cabin and "getAddEntityPacket" in cabin, "la cabina debe sincronizar su contenido al aparecer")
need("saveWithFullMetadata" in cabin, "la cabina debe guardar los bloques con sus datos (cofres, etc.)")
need("removeBlockEntity" in cabin, "la cabina debe quitar el block entity antes de borrar el bloque (si no, suelta el contenido)")
need("blockedAhead" in cabin and "returning" in cabin, "la cabina debe frenar si algo bloquea el camino")
need("ElevatorHumSound" in SRC["client/ElevatorClientHooks.java"] or "ElevatorHumSound" in cabin, "falta el zumbido de la cabina")
need("ELEVATOR_DING" in cabin, "la cabina no suena al llegar")
need("DEPARTURE_WAIT" in shaft, "ElevatorShaft sin DEPARTURE_WAIT")
need("cabinHere" in SRC["block/entity/ElevatorDoorBlockEntity.java"] or "isCabinHere" in SRC["block/entity/ElevatorDoorBlockEntity.java"],
     "la puerta no consulta si la cabina está en su piso")

# ------------------------------------------------------------------------------------------------ simulación del movimiento
import math


def sim(dist, bps, step_min=0.02, ramp=0.35):
    speed = bps / 20.0
    y = 0.0
    for tick in range(200000):
        rem = dist - y
        if abs(rem) < 1e-4:
            return tick, y
        v = min(speed, max(step_min, ramp * min(abs(y), abs(rem))))
        y += math.copysign(min(v, abs(rem)), rem)
    return None, y


# V3.3.1: MIN_STEP y RAMP se mudaron a elevator/ElevatorMotion.java (la cabina las usa desde ahí)
motion_src = read("elevator/ElevatorMotion.java") if (JAVA / "elevator/ElevatorMotion.java").exists() else cabin
mm = re.search(r"MIN_STEP\s*=\s*([\d.]+)", motion_src)
rr = re.search(r"RAMP\s*=\s*([\d.]+)", motion_src)
if "travelledAt" in motion_src:   # V3.3.2: la curva va en el tiempo; la comprueba tools/verify_v332.py con la prueba de verdad
    need("public static double stepFor(" in motion_src and "public static boolean isLastStep(" in motion_src, "ElevatorMotion sin stepFor/isLastStep")
    mm = rr = None
else:
    need(mm and rr, "sin MIN_STEP/RAMP (ElevatorMotion o ElevatorCabinEntity)")
if mm and rr:
    for dist in (1, 3, 40, -25, 300):
        for bps in (1, 4, 20):
            t, y = sim(dist, bps, float(mm.group(1)), float(rr.group(1)))
            need(t is not None and abs(y - dist) < 1e-3, f"la cabina no llega: {dist} bloques a {bps} b/s")

# ------------------------------------------------------------------------------------------------ recursos
en, es = jload(ASSETS / "lang/en_us.json"), jload(ASSETS / "lang/es_es.json")
need(set(en) == set(es), f"en_us y es_es no tienen las mismas claves ({len(en)} vs {len(es)})")
used_keys = set()
for f, text in SRC.items():
    used_keys |= set(re.findall(r"\"((?:gui|message|tooltip|subtitles|container)\.beautifuldecor\.[a-z0-9_.]+)\"", text))
for key in sorted(used_keys):
    if key.endswith("."):  # prefijo al que se le agrega un sufijo en el código (p. ej. el lado)
        need(any(k.startswith(key) for k in en) and any(k.startswith(key) for k in es), f"falta el grupo de claves {key}*")
    else:
        need(key in en and key in es, f"falta la clave de idioma {key}")
for d in ("north", "east", "south", "west"):
    need(f"gui.beautifuldecor.elevator.side.{d}" in en and f"gui.beautifuldecor.elevator.side.{d}" in es, f"falta el nombre del lado {d}")
for bid in ("elevator_controller", "elevator_display", "elevator_door"):
    for table in (en, es):
        need(f"block.beautifuldecor.{bid}" in table and f"item.beautifuldecor.{bid}" in table, f"falta el nombre de {bid}")
for sid in ("elevator_hum", "elevator_ding", "elevator_door"):
    need(f"subtitles.beautifuldecor.{sid}" in en and f"subtitles.beautifuldecor.{sid}" in es, f"falta el subtítulo de {sid}")
need(en.get("block.beautifuldecor.elevator_controller") == "Elevator Controller", "nombre en inglés del controlador")
need(en.get("block.beautifuldecor.elevator_display") == "Elevator Display", "nombre en inglés de la pantalla")
need(en.get("block.beautifuldecor.elevator_door") == "Elevator Door", "nombre en inglés de la puerta")

TEXTURES = ["elevator_controller_front", "elevator_controller_side", "elevator_controller_top", "elevator_display_front",
            "elevator_display_side", "elevator_door_lower", "elevator_door_upper", "elevator_door_edge"]   # V2.3.0: puerta de 2 bloques de alto
for t in TEXTURES:
    need(png_size(ASSETS / f"textures/block/{t}.png") is not None, f"falta la textura {t}")


def check_model(name):
    mp = ASSETS / f"models/block/{name}.json"
    if not mp.exists():
        errors.append(f"falta el modelo {name}")
        return
    data = jload(mp)
    textures = dict(data.get("textures", {}))
    need("elements" in data and data["elements"], f"{name}: sin elementos")
    keys = set(textures)
    for ref in textures.values():
        if ref.startswith("#"):
            need(ref[1:] in keys, f"{name}: textura {ref} sin definir")
        elif ref.startswith("beautifuldecor:"):
            need((ASSETS / "textures" / (ref.split(":")[1] + ".png")).exists(), f"{name}: falta la textura {ref}")
    for el in data.get("elements") or []:
        for c in el["from"] + el["to"]:
            need(-16 <= c <= 32, f"{name}: coordenada fuera de rango {c}")
        for fn, face in el["faces"].items():
            need(face["texture"].lstrip("#") in keys, f"{name}: textura {face['texture']} sin definir")
            uv = face.get("uv")
            if uv:
                need(all(-0.001 <= v <= 16.001 for v in uv), f"{name}: uv fuera de 0..16 {uv}")


for name in ("elevator_controller", "elevator_display", "elevator_display_item", "elevator_door_item",
             "elevator_door_closed_lower", "elevator_door_closed_upper", "elevator_door_open_lower", "elevator_door_open_upper"):
    check_model(name)

# blockstates
bs = jload(ASSETS / "blockstates/elevator_controller.json")["variants"]
need(set(bs) == {f"facing={f}" for f in ("north", "east", "south", "west")}, "elevator_controller: variantes de blockstate")
bs = jload(ASSETS / "blockstates/elevator_display.json")["variants"]
need(set(bs) == {f"facing={f}" for f in ("north", "east", "south", "west", "up", "down")}, "elevator_display: variantes de blockstate")
bs = jload(ASSETS / "blockstates/elevator_door.json")["variants"]
need(len(bs) == 48, "elevator_door: el blockstate debe tener 48 variantes (4 orientaciones x mitad baja/alta x abierta/cerrada x sola/izquierda/derecha)")
for key, v in bs.items():
    m_ = re.fullmatch(r"facing=(north|east|south|west),half=(lower|upper),open=(true|false),part=(single|left|right)", key)
    need(bool(m_), f"elevator_door: clave de blockstate inesperada {key}")
    if m_:
        want_end = ("_open_" if m_.group(3) == "true" else "_closed_") + m_.group(2)
        need(v["model"].endswith(want_end), f"elevator_door: {key} usa el modelo equivocado")
        check_model(v["model"].split("/")[1])
door_src = SRC["block/ElevatorDoorBlock.java"]
need("FACING" in door_src and "OPEN" in door_src, "ElevatorDoorBlock sin FACING/OPEN")
need("TallHalves.HALF" in door_src and "DoubleBlockHalf" in door_src, "ElevatorDoorBlock: la puerta debe ser de 2 bloques de alto (mitad baja y alta)")
need("FACING" in SRC["block/ElevatorControllerBlock.java"], "ElevatorControllerBlock sin FACING")

for bid in ("elevator_controller", "elevator_display", "elevator_door"):
    need((DATA / f"loot_tables/blocks/{bid}.json").exists(), f"falta el loot de {bid}")
    need(png_size(ASSETS / f"textures/item/{bid}.png") == (32, 32), f"el ícono de {bid} no es de 32x32")
    need((ASSETS / f"models/item/{bid}.json").exists(), f"falta el modelo de ítem de {bid}")

# ------------------------------------------------------------------------------------------------ pestaña y orden
tabs = read("init/ModCreativeTabs.java")
need('path.startsWith("elevator_")' in tabs, "ModCreativeTabs: los ascensores deben ir en la pestaña Tech")
order = read("init/ModCreativeOrder.java")
for bid in ("elevator_controller", "elevator_display", "elevator_door"):
    need(f'"{bid}"' in order, f"ModCreativeOrder sin {bid}")

# ------------------------------------------------------------------------------------------------ resultado
errors = [e for e in errors if e]
if errors:
    print("FALLÓ la verificación V2.2.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.2.0: ascensores (controlador, pantalla, puerta, cabina) bien conectados.")
