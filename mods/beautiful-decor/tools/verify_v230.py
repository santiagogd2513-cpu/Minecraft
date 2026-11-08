#!/usr/bin/env python3
"""Comprueba la V2.3.0: camioneta "Furd", 6 computadores HS nuevos, 3 ventiladores de pie, puerta de ascensor de 2 bloques
de alto y colores personalizables de la puerta de garaje (tecla R).

No compila nada (eso lo hace Gradle en tu equipo): revisa registros, modelos, texturas, blockstates, loot, idiomas, red y que
la puerta de garaje con sus colores por defecto se vea igual que antes (las capas recompuestas == las texturas originales).

Uso: python3 tools/verify_v230.py
"""
import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
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
    if not path.exists():
        errors.append(f"falta {path.relative_to(ROOT)}")
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def png_size(path):
    if not path.exists():
        errors.append(f"falta {path.relative_to(ROOT)}")
        return None
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def balanced(name, text):
    need(text.count("{") == text.count("}"), f"{name}: llaves desparejadas")
    need(text.count("(") == text.count(")"), f"{name}: paréntesis desparejados")
    need(text.count("[") == text.count("]"), f"{name}: corchetes desparejados")


# ------------------------------------------------------------------------------------------------ versión y docs
need(re.search(r"version = '(2\.([3-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")),
     "build.gradle no está en 2.3.0 o superior")
need(re.search(r"mod_version=(2\.([3-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")),
     "gradle.properties no está en 2.3.0 o superior")
need("V2.3.0" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V2.3.0")
need("PARCHE V2.3.0" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V2.3.0")

en = jload(ASSETS / "lang/en_us.json")
es = jload(ASSETS / "lang/es_es.json")
need(set(en) == set(es), f"es_es y en_us no tienen las mismas claves ({len(set(en) ^ set(es))} de diferencia)")

# ------------------------------------------------------------------------------------------------ Furd
for lang, name in ((en, "en"), (es, "es")):
    for k in ("item.beautifuldecor.ford_f150", "entity.beautifuldecor.ford_f150"):
        need("Furd" in lang.get(k, "") and "Ford" not in lang.get(k, ""), f"{name}: {k} debe llamarse Furd")
need(png_size(ASSETS / "textures/entity/ford_f150.png") is not None, "falta la textura de la camioneta")
need(png_size(ASSETS / "textures/item/ford_f150.png") == (32, 32), "el ícono de la camioneta no es de 32x32")
for rel in ("entity/PickupEntity.java", "client/PickupRenderer.java", "client/PickupModel.java", "init/ModEntities.java"):
    need("Ford" not in read(rel).replace("FORD_F150", "").replace("ford_f150", ""), f"{rel}: todavía dice Ford")
need("ford_f150" in read("init/ModEntities.java"), "el id ford_f150 debe seguir igual (para no romper los mundos guardados)")

# ------------------------------------------------------------------------------------------------ computadores HS
DESKTOPS = ("hs_desktop_retro", "hs_desktop_compact", "hs_desktop_amber", "hs_desktop_slim", "hs_desktop_gaming", "hs_desktop_dual")
blocks = read("init/ModBlocks.java")
need("HS_DESKTOPS" in blocks and "HsDesktopBlock.variantShape" in blocks, "ModBlocks no registra los computadores HS nuevos")
hs = read("block/HsDesktopBlock.java")
balanced("HsDesktopBlock.java", hs)
need("variantShape" in hs, "HsDesktopBlock sin variantShape")
for bid in DESKTOPS:
    need(f'"{bid}"' in blocks, f"ModBlocks sin {bid}")
    need(f'"{bid}"' in hs, f"HsDesktopBlock.variantShape sin {bid}")
    bs = jload(ASSETS / f"blockstates/{bid}.json")
    for facing in ("north", "east", "south", "west"):
        for on in ("true", "false"):
            need(any(facing in k and f"on={on}" in k for k in bs.get("variants", {})), f"{bid}: blockstate sin {facing}/on={on}")
    for st in ("on", "off"):
        m = jload(ASSETS / f"models/block/{bid}_{st}.json")
        need(bool(m.get("elements")), f"{bid}_{st}: sin elementos")
        for ref in m.get("textures", {}).values():
            if ref.startswith("beautifuldecor:"):
                need((ASSETS / "textures" / (ref.split(":")[1] + ".png")).exists(), f"{bid}_{st}: falta la textura {ref}")
    need((DATA / f"loot_tables/blocks/{bid}.json").exists(), f"falta el loot de {bid}")
    need(png_size(ASSETS / f"textures/item/{bid}.png") == (32, 32), f"el ícono de {bid} no es de 32x32")
    need((ASSETS / f"models/item/{bid}.json").exists(), f"falta el modelo de ítem de {bid}")
    for lang, n in ((en, "en"), (es, "es")):
        need(f"block.beautifuldecor.{bid}" in lang and f"item.beautifuldecor.{bid}" in lang, f"{n}: falta el nombre de {bid}")
tabs = read("init/ModCreativeTabs.java")
need('startsWith("hs_desktop_")' in tabs, "ModCreativeTabs: los computadores HS nuevos van en la pestaña Tech")
need('"hs_desktop_*"' in read("init/ModCreativeOrder.java"), "ModCreativeOrder sin hs_desktop_*")

# ------------------------------------------------------------------------------------------------ ventiladores de pie
FANS = ("pedestal_fan_white", "pedestal_fan_black", "pedestal_fan_retro")
tall = read("block/TallHalves.java")
pf = read("block/PedestalFanBlock.java")
balanced("TallHalves.java", tall)
balanced("PedestalFanBlock.java", pf)
need("PEDESTAL_FANS" in blocks and "PedestalFanBlock" in blocks, "ModBlocks no registra los ventiladores de pie")
need("TallHalves" in pf and "DoubleBlockHalf" in tall, "PedestalFanBlock debe ser de 2 bloques de alto")
for bid in FANS:
    need(f'"{bid}"' in blocks or "pedestal_fan_" in blocks, f"ModBlocks sin {bid}")
    bs = jload(ASSETS / f"blockstates/{bid}.json").get("variants", {})
    need(len(bs) == 16, f"{bid}: el blockstate debe tener 16 variantes (4 orientaciones x mitad x encendido), tiene {len(bs)}")
    for key, v in bs.items():
        need(re.fullmatch(r"facing=(north|east|south|west),half=(lower|upper),on=(true|false)", key) is not None,
             f"{bid}: clave inesperada {key}")
        need((ASSETS / "models/block" / (v["model"].split("/")[1] + ".json")).exists(), f"{bid}: falta el modelo de {key}")
    for name in ("lower", "upper_on", "upper_off", "item"):
        m = jload(ASSETS / f"models/block/{bid}_{name}.json")
        need(bool(m.get("elements")), f"{bid}_{name}: sin elementos")
    loot = json.dumps(jload(DATA / f"loot_tables/blocks/{bid}.json"))
    need('"half": "lower"' in loot, f"{bid}: solo la mitad baja debe soltar el ítem")
    need(png_size(ASSETS / f"textures/item/{bid}.png") == (32, 32), f"el ícono de {bid} no es de 32x32")
    for lang, n in ((en, "en"), (es, "es")):
        need(f"block.beautifuldecor.{bid}" in lang, f"{n}: falta el nombre de {bid}")
need('startsWith("pedestal_fan_")' in tabs, "ModCreativeTabs: los ventiladores de pie van en la pestaña Tech")
need('"pedestal_fan_*"' in read("init/ModCreativeOrder.java"), "ModCreativeOrder sin pedestal_fan_*")

# ------------------------------------------------------------------------------------------------ puerta de ascensor de 2 bloques
door = read("block/ElevatorDoorBlock.java")
balanced("ElevatorDoorBlock.java", door)
need("TallHalves.HALF" in door and "DoubleBlockHalf" in door, "ElevatorDoorBlock debe ser de 2 bloques de alto")
for name in ("lower", "upper"):
    need((ASSETS / f"textures/block/elevator_door_{name}.png").exists(), f"falta la textura elevator_door_{name}")
need(not (ASSETS / "textures/block/elevator_door.png").exists(), "quedó la textura vieja de 1 bloque elevator_door.png")
bs = jload(ASSETS / "blockstates/elevator_door.json").get("variants", {})
need(len(bs) == 48, f"elevator_door: el blockstate debe tener 48 variantes, tiene {len(bs)}")
loot = json.dumps(jload(DATA / "loot_tables/blocks/elevator_door.json"))
need('"half": "lower"' in loot, "elevator_door: solo la mitad baja debe soltar el ítem")

# ------------------------------------------------------------------------------------------------ puerta de garaje con colores
gc = read("block/GarageColors.java")
be = read("block/entity/GarageDoorBlockEntity.java")
gp = read("network/GarageColorPacket.java")
scr = read("client/GarageColorScreen.java")
keys = read("client/GarageKeys.java")
ev = read("client/GarageClientEvents.java")
gdb = read("block/GarageDoorBlock.java")
for name, text in (("GarageColors", gc), ("GarageDoorBlockEntity", be), ("GarageColorPacket", gp),
                   ("GarageColorScreen", scr), ("GarageKeys", keys), ("GarageClientEvents", ev), ("GarageDoorBlock", gdb)):
    balanced(name + ".java", text)
    need(re.search(r"^package com\.example\.beautifuldecor\.[a-z.]+;", text, re.M) is not None, f"{name}: sin package")
    need(re.search(rf"\b(class|record|interface|enum) {name}\b", text) is not None, f"{name}: no declara {name}")
    imports = re.findall(r"^import .*;$", text, re.M)
    need(len(imports) == len(set(imports)), f"{name}: imports repetidos")

# los colores y márgenes K tienen que ser los mismos en Java y en el generador de texturas
from tools.v230 import garage_colors as GC  # noqa: E402


def hexcolor(rgb):
    return "0x%02X%02X%02X" % tuple(rgb)


for const, rgb in (("DEFAULT_BODY", GC.D_BODY), ("DEFAULT_ACCENT", GC.D_ACCENT), ("DEFAULT_FRAME", GC.D_FRAME),
                   ("DEFAULT_INSIDE", GC.D_INSIDE)):
    need(f"{const} = {hexcolor(rgb)};" in gc, f"GarageColors.{const} no coincide con el generador ({hexcolor(rgb)})")
for const, k in (("K_BODY", GC.K_BODY), ("K_ACCENT", GC.K_ACCENT), ("K_FRAME", GC.K_FRAME), ("K_INSIDE", GC.K_INSIDE)):
    need(re.search(rf"{const} = {re.escape(repr(float(k)))};", gc) is not None, f"GarageColors.{const} no coincide con el generador ({k})")

need("implements EntityBlock" in gdb and "newBlockEntity" in gdb, "GarageDoorBlock debe ser un EntityBlock")
need("public static BlockPos partPos" in gdb and "public static BlockPos originOf" in gdb, "GarageDoorBlock: partPos/originOf deben ser públicos")
need("colorsAt" in gdb and "applyColors" in gdb, "GarageDoorBlock sin colorsAt/applyColors")
need("GARAGE_DOOR" in read("init/ModBlockEntities.java") and "GarageDoorBlockEntity::new" in read("init/ModBlockEntities.java"),
     "ModBlockEntities no registra el block entity de la puerta de garaje")
need('saveAdditional' in be and 'getUpdateTag' in be and 'getUpdatePacket' in be and "refreshRender" in be,
     "GarageDoorBlockEntity debe guardarse y sincronizarse con el cliente")
need("GarageColorPacket" in read("network/ModMessages.java") and 'PROTOCOL = "6"' in read("network/ModMessages.java"),
     "ModMessages: registrar GarageColorPacket y subir el protocolo a 6")
need("PLAY_TO_SERVER" in read("network/ModMessages.java").split("GarageColorPacket.class")[1].split(".add()")[0],
     "GarageColorPacket va del cliente al servidor")
need("distanceToSqr" in gp and "isLoaded" in gp, "GarageColorPacket debe comprobar distancia y chunk cargado")
cev = read("client/ClientEvents.java")
need("GarageKeys.COLORS" in cev and "RegisterColorHandlersEvent.Block" in cev and "ModBlocks.GARAGE_DOOR" in cev,
     "ClientEvents: registrar la tecla y el tinte de la puerta de garaje")
need("RenderType.cutout()" in cev and "GARAGE_DOOR" in cev.split("RenderType.cutout()")[0].splitlines()[-1],
     "ClientEvents: la puerta de garaje debe dibujarse en la capa cutout")
need("GLFW_KEY_R" in keys and "key.beautifuldecor.garage_colors" in keys, "la tecla de la puerta de garaje debe ser R")
need("consumeClick" in ev and "GarageDoorBlock" in ev and "hitResult" in ev and "GarageColorScreen" in ev,
     "GarageClientEvents debe abrir el menú solo apuntando a la puerta")
need("GarageColorPacket" in scr and "DarkUi" in scr and "EditBox" in scr, "GarageColorScreen incompleta")
for k in ("key.beautifuldecor.garage_colors", "gui.beautifuldecor.garage.title", "gui.beautifuldecor.garage.body",
          "gui.beautifuldecor.garage.accent", "gui.beautifuldecor.garage.frame", "gui.beautifuldecor.garage.red",
          "gui.beautifuldecor.garage.green", "gui.beautifuldecor.garage.blue", "gui.beautifuldecor.garage.preview",
          "gui.beautifuldecor.garage.original", "gui.beautifuldecor.garage.apply", "gui.beautifuldecor.garage.reset",
          "gui.beautifuldecor.garage.cancel"):
    need(k in en and k in es, f"falta el texto {k} en es_es o en_us")
    used = k in scr or k in keys
    need(used, f"el texto {k} no se usa en el código")

# blockstate (sin cambios) y modelos con tinte
st = jload(ASSETS / "blockstates/garage_door.json")["multipart"]
need(len(st) == 4 * 9 * 13, f"garage_door: el blockstate debe tener {4 * 9 * 13} partes, tiene {len(st)}")
names = {p["apply"]["model"].split("/")[1] for p in st}
files = {p.stem for p in (ASSETS / "models/block").glob("garage_door_p*_r*.json")}
need(names == files, "garage_door: los modelos del blockstate no coinciden con los archivos")
TINTED = {"ob": 0, "hb": 0, "oa": 1, "ha": 1, "ja": 1, "fb": 2, "jb": 2, "ib": 3}
for name in sorted(files):
    m = jload(ASSETS / f"models/block/{name}.json")
    need(m.get("render_type") == "minecraft:cutout", f"{name}: debe usar render_type cutout")
    textures = m.get("textures", {})
    for ref in textures.values():
        need((ASSETS / "textures" / (ref.split(":")[1] + ".png")).exists(), f"{name}: falta la textura {ref}")
    for el in m.get("elements", []):
        for fn, face in el["faces"].items():
            key = face["texture"][1:]
            need(key in textures, f"{name}: textura {key} sin definir")
            if key in TINTED:
                need(face.get("tintindex") == TINTED[key], f"{name}: la cara {fn} ({key}) debe tener tintindex {TINTED[key]}")
            else:
                need("tintindex" not in face, f"{name}: la cara {fn} ({key}) no debe tener tinte")
            uv = face.get("uv")
            if uv:
                need(all(-0.001 <= v <= 16.001 for v in uv), f"{name}: uv fuera de 0..16")

# con los colores por defecto la puerta se ve igual que antes
import numpy as np  # noqa: E402
from tools.v200 import garage_door as G  # noqa: E402

leaf, hdr, jamb = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME)
inside, _, _ = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME, "in")
for label, got, want in (("hoja", leaf, G.leaf_texture(True)), ("hoja interior", inside, G.leaf_texture(False)),
                         ("cajón", hdr, G.header_texture()), ("riel", jamb, G.jamb_texture())):
    ref = np.asarray(want.convert("RGB")).astype(float)
    diff = np.abs(np.round(got) - ref)
    bad = int((diff > 2).sum())
    need(bad <= max(8, int(0.005 * ref.size)), f"la {label} con colores por defecto cambió ({bad} valores se alejan más de 2)")
    need(diff.max() <= 12, f"la {label} con colores por defecto cambió (diferencia máxima {diff.max():.0f})")
frame_ref = np.asarray(G.frame_texture().convert("RGB")).astype(float)
for name in ("gd_door_out_b", "gd_door_in_b", "gd_hdr_b", "gd_frame_b", "gd_jamb_b"):
    p = ASSETS / f"textures/block/{name}.png"
    need(png_size(p) is not None, f"falta la textura {name}")
    if p.exists():
        from PIL import Image
        need(np.asarray(Image.open(p).convert("RGBA"))[..., 3].min() == 255, f"{name}: la capa base debe ser opaca")
for name in ("gd_door_out_a", "gd_door_out_d", "gd_door_in_d", "gd_hdr_a", "gd_hdr_d", "gd_jamb_a"):
    need(png_size(ASSETS / f"textures/block/{name}.png") is not None, f"falta la textura {name}")
need(png_size(ASSETS / "textures/item/garage_door.png") == (32, 32), "el ícono de la puerta de garaje cambió de tamaño")

# ------------------------------------------------------------------------------------------------ resultado
if errors:
    print("FALLÓ la verificación V2.3.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.3.0: Furd, 6 computadores HS, 3 ventiladores de pie, puerta de ascensor de 2 bloques y colores de la puerta de garaje.")
