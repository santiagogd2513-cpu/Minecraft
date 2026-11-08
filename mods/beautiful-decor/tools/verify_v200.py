#!/usr/bin/env python3
"""Comprueba la V2.0.0 (moto de carreras y puerta de garaje 3x3): registros, recursos, modelos, blockstate, textos y versión.

No compila nada (eso lo hace Gradle en tu equipo): revisa que cada pieza exista y esté conectada, que la geometría de la
puerta (modelo visible y colisión del bloque) coincida, que la velocidad máxima sea 190 bloques/s y que los idiomas estén completos.

Uso: python3 tools/verify_v200.py
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
    return (JAVA / rel).read_text(encoding="utf-8")


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    need(head[:8] == b"\x89PNG\r\n\x1a\n", f"{path.name}: no es un PNG")
    return struct.unpack(">II", head[16:24])


# ------------------------------------------------------------------------------------------------ versión
need(re.search(r"version = '(2\.\d+\.\d+|[3-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")),
     "build.gradle no está en 2.0.0 o superior")
need(re.search(r"mod_version=(2\.\d+\.\d+|[3-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")),
     "gradle.properties no está en 2.0.0 o superior")
need("V2.0.0" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V2.0.0")
need("PARCHE V2.0.0" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V2.0.0")

# ------------------------------------------------------------------------------------------------ registros
blocks, items, ents, sounds = (read("init/ModBlocks.java"), read("init/ModItems.java"),
                               read("init/ModEntities.java"), read("init/ModSounds.java"))
need('BLOCKS.register("garage_door"' in blocks and "GarageDoorBlock" in blocks, "ModBlocks no registra garage_door")
need("import com.example.beautifuldecor.block.GarageDoorBlock;" in blocks, "ModBlocks sin el import de GarageDoorBlock")
need('ITEMS.register("garage_door"' in items and "ModBlocks.GARAGE_DOOR" in items, "ModItems no registra el ítem garage_door")
need('ITEMS.register("motorcycle"' in items, "ModItems no registra el ítem motorcycle")
need('ENTITIES.register("motorcycle"' in ents, "ModEntities no registra la entidad motorcycle")
for sid in ("motorcycle_engine", "motorcycle_horn", "motorcycle_start", "garage_door"):
    need(f'simple("{sid}")' in sounds, f"ModSounds no registra {sid}")
    need((ASSETS / f"sounds/{sid}.ogg").exists(), f"falta sounds/{sid}.ogg")
    head = (ASSETS / f"sounds/{sid}.ogg").read_bytes()[:4] if (ASSETS / f"sounds/{sid}.ogg").exists() else b""
    need(head == b"OggS", f"sounds/{sid}.ogg no es un Ogg válido")
sj = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
for sid in ("motorcycle_engine", "motorcycle_horn", "motorcycle_start", "garage_door"):
    need(sid in sj, f"sounds.json sin {sid}")
    for s in sj.get(sid, {}).get("sounds", []):
        name = s["name"] if isinstance(s, dict) else s
        need((ASSETS / "sounds" / (name.split(":")[1] + ".ogg")).exists(), f"sounds.json: {name} no existe")

messages = read("network/ModMessages.java")
need("VehicleActionPacket" in messages, "ModMessages no registra VehicleActionPacket")
need(re.search(r'PROTOCOL = "([3-9]|\d{2,})"', messages), "el protocolo de red debe ser 3 o más (paquete nuevo)")
pk = read("network/VehicleActionPacket.java")
enc = pk.split("encode")[1].split("}")[0]
dec = pk.split("decode")[1].split("}")[0]
need([m for m in re.findall(r"write(\w+)\(", enc)] == [m for m in re.findall(r"read(\w+)\(", dec)],
     "VehicleActionPacket no es simétrico")

# ------------------------------------------------------------------------------------------------ moto
moto = read("entity/MotorcycleEntity.java")
m = re.search(r"MAX_SPEED\s*=\s*([\d.]+)f", moto)
need(m and float(m.group(1)) * 20 in (120.0, 190.0), "la velocidad máxima de la moto no es 190 bloques/s (2.0.0) ni 120 (2.1.0)")
veh = read("entity/VehicleEntity.java")
need("getControllingPassenger" in veh and "getDismountLocationForPassenger" in veh, "los vehículos no se manejan/bajan")
need("LIGHT" in veh and "toggleLight" in veh, "los vehículos no tienen luz conmutable")
need("isControlledByLocalInstance" in veh, "los vehículos no se controlan desde el cliente del conductor")
need("noCulling" in veh, "los vehículos no desactivan el culling")
ev = read("client/VehicleClientEvents.java")
need("MovementInputUpdateEvent" in ev and "consumeClick" in ev and "ViewportEvent.ComputeFov" in ev,
     "faltan los eventos de cliente de la moto")
need("VehicleKeys" in read("client/ClientEvents.java") and "registerLayerDefinition" in read("client/ClientEvents.java"),
     "ClientEvents no registra teclas/modelo de la moto")
mm = read("client/MotorcycleModel.java")
for part in ("rear_wheel", "steer", "fork", "front_wheel", "kickstand"):
    need(f'"{part}"' in mm, f"MotorcycleModel sin la pieza {part}")
need("256, 256" in mm, "el atlas de la moto no es de 256x256")
for tex in ("textures/entity/motorcycle.png", "textures/entity/motorcycle_glow.png"):
    need(png_size(ASSETS / tex) == (256, 256), f"{tex} no es de 256x256")
need(png_size(ASSETS / "textures/item/motorcycle.png") == (32, 32), "el ícono de la moto no es de 32x32")
need(json.loads((ASSETS / "models/item/motorcycle.json").read_text(encoding="utf-8"))["parent"] == "minecraft:item/generated",
     "el modelo de ítem de la moto no es un sprite")
need(read("client/MotorcycleRenderer.java").count("motorcycle") >= 2, "MotorcycleRenderer sin sus texturas")
# el modelo Java no debe dibujar nada fuera del atlas: cada texOffs + caja cabe en 256x256
for u, v, w, h, d in re.findall(r"texOffs\((\d+), (\d+)\)\.addBox\([-\d.f, ]+?, ([\d.]+)f, ([\d.]+)f, ([\d.]+)f\)", mm):
    u, v, w, h, d = int(u), int(v), float(w), float(h), float(d)
    need(u + 2 * (d + w) - 0 <= 256.5 and v + d + h <= 256.5, f"cubo fuera del atlas: texOffs({u},{v}) {w}x{h}x{d}")

# ------------------------------------------------------------------------------------------------ puerta de garaje
gd = read("block/GarageDoorBlock.java")
need("MAX_RAISE = 12" in gd and 'create("raise", 0, 12)' in gd and 'create("part", 0, 8)' in gd, "GarageDoorBlock: propiedades")
need("playerWillDestroy" in gd and "onRemove" in gd and "setPlacedBy" in gd and "getStateForPlacement" in gd,
     "GarageDoorBlock: faltan colocación/rotura de las 9 piezas")
need("neighborChanged" in gd and "hasNeighborSignal" in gd, "GarageDoorBlock: sin redstone")
need("personUnder" in gd and "scheduleTick" in gd, "GarageDoorBlock: sin animación o sin seguridad al cerrar")

# Geometría: la colisión de Java (hoja y cajón) debe coincidir con los modelos
need("px(0, 13, 3, 16, 16, 13)" in gd and "px(xFrom, yLow, 7, xTo, 16, 9)" in gd and "4 * raise" in gd,
     "GarageDoorBlock: la colisión no coincide con el modelo (cajón y 13..16, hoja z 7..9, 4 px por paso)")

gen = json.loads((ASSETS / "blockstates/garage_door.json").read_text(encoding="utf-8"))
mp = gen.get("multipart", [])
need(len(mp) == 4 * 9 * 13, f"blockstate: {len(mp)} entradas, deben ser {4 * 9 * 13}")
seen = set()
for entry in mp:
    w = entry["when"]
    seen.add((w["facing"], int(w["part"]), int(w["raise"])))
    need(set(w) == {"facing", "part", "raise"}, f"blockstate: when inesperado {w}")
    name = entry["apply"]["model"].split(":")[1]
    need((ASSETS / "models" / f"{name}.json").exists(), f"blockstate apunta a un modelo que no existe: {name}")
need(len(seen) == 4 * 9 * 13, "blockstate: faltan combinaciones facing/part/raise")

TEXTURES_USED = set()
for f in (ASSETS / "models/block").glob("garage_door_*.json"):
    data = json.loads(f.read_text(encoding="utf-8"))
    for key, ref in data["textures"].items():
        TEXTURES_USED.add(ref)
        need((ASSETS / "textures" / (ref.split(":")[1] + ".png")).exists(), f"{f.name}: falta la textura {ref}")
    keys = set(data["textures"])
    for el in data["elements"]:
        for c in el["from"] + el["to"]:
            need(-16 <= c <= 32, f"{f.name}: coordenada fuera de rango {c}")
        for fn, face in el["faces"].items():
            need(face["texture"].lstrip("#") in keys, f"{f.name}: textura {face['texture']} sin definir")
            uv = face.get("uv")
            if uv:
                need(all(-0.001 <= v <= 16.001 for v in uv), f"{f.name}: uv fuera de 0..16 {uv}")
# la hoja de cada pieza: z 7..9, nunca por encima de la fila ni de y 13 en la de arriba
for part in range(9):
    for r in range(13):
        mf = ASSETS / "models/block" / f"garage_door_p{part}_r{r}.json"
        if not mf.exists():
            continue      # modelo compartido con otro (mismo contenido)
        data = json.loads(mf.read_text(encoding="utf-8"))
        for el in data["elements"]:
            if el["from"][2] == 7:
                need(el["to"][2] == 9, f"{mf.name}: la hoja no es de 2 px")
                top = 13 if part // 3 == 2 else 16
                need(el["to"][1] <= top, f"{mf.name}: la hoja sube más que el cajón")

for t, size in (("gd_door_out", (96, 96)), ("gd_door_in", (96, 96)), ("gd_header", (96, 6)), ("gd_frame", (16, 16)),
                ("gd_jamb", (16, 16)), ("gd_seal", (16, 16))):
    need(png_size(ASSETS / f"textures/block/{t}.png") == size, f"textura {t} con tamaño inesperado")
need(png_size(ASSETS / "textures/item/garage_door.png") == (32, 32), "el ícono de la puerta no es de 32x32")
need((ASSETS / "models/block/garage_door_item.json").exists(), "falta el modelo de miniatura garage_door_item")
loot = json.loads((DATA / "loot_tables/blocks/garage_door.json").read_text(encoding="utf-8"))
need(json.dumps(loot).count('"part": "0"') == 1, "el loot de la puerta debe soltar solo con part=0")

# Idiomas
en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
es = json.loads((ASSETS / "lang/es_es.json").read_text(encoding="utf-8"))
need(set(en) == set(es), f"en_us y es_es no tienen las mismas claves ({len(en)} vs {len(es)})")
for k in ("item.beautifuldecor.motorcycle", "entity.beautifuldecor.motorcycle", "item.beautifuldecor.garage_door",
          "block.beautifuldecor.garage_door", "key.beautifuldecor.motorcycle_light", "key.beautifuldecor.motorcycle_horn",
          "hud.beautifuldecor.motorcycle_speed", "hud.beautifuldecor.motorcycle_light_on",
          "hud.beautifuldecor.motorcycle_light_off", "hud.beautifuldecor.motorcycle_controls",
          "subtitles.beautifuldecor.motorcycle_engine", "subtitles.beautifuldecor.motorcycle_horn",
          "subtitles.beautifuldecor.motorcycle_start", "subtitles.beautifuldecor.garage_door"):
    need(k in en and k in es, f"falta la clave de idioma {k}")
need(en["hud.beautifuldecor.motorcycle_controls"].count("%s") == 2 and es["hud.beautifuldecor.motorcycle_controls"].count("%s") == 2,
     "hud.motorcycle_controls debe tener 2 %s (tecla de luz y de claxon)")
need("hud.beautifuldecor.motorcycle_speed" in en and "%s" in en["hud.beautifuldecor.motorcycle_speed"], "velocímetro sin %s")

# Pestaña creativa
need('"garage_door"' in read("init/ModCreativeOrder.java"), "ModCreativeOrder no lista la puerta")
need("motorcycle" in read("init/ModCreativeTabs.java"), "ModCreativeTabs no incluye la moto")

if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("moto (190 bloques/s, luz, claxon, modelo, sonidos) y puerta de garaje 3x3 (blockstate, modelos, colisión, redstone, textos): bien")
print("OK")
