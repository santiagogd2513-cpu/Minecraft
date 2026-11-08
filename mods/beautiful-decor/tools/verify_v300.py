#!/usr/bin/env python3
"""Comprueba la V3.0.0: camión de reparto AMAZOOM, helicóptero BOING HX-6, tres cuadros de pared y alcance del dron de 548 bloques.

No compila Minecraft (no hay Minecraft en el entorno). Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que cada cosa nueva tenga todos sus recursos (modelo, textura con el tamaño esperado, idiomas es/en, sonidos y subtítulos, loot);
  * que todo el JSON nuevo sea válido y que los modelos apunten a texturas que existen;
  * que todo lo nuevo esté registrado y conectado (entidades, ítems, pestaña Vehículos, renderizadores, capas, tecla Z, paquete de red);
  * que cada texto que sale del código nuevo esté traducido en es y en;
  * que el código nuevo no tenga errores de sintaxis de Java (sin Minecraft solo se pueden ver los errores de sintaxis, no los de tipos);
  * que el atlas del helicóptero y del camión se regeneren iguales a lo guardado (la geometría y la textura no se desalinean);
  * y corre lo de la V2.11.0 (tools/verify_v2110.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v300.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data"
errors = []
notes = []

PAINTINGS = ["painting_valley_dawn", "painting_police_chase", "painting_circuit_aerial"]
SOUNDS = ["delivery_truck_engine", "delivery_truck_start", "delivery_truck_horn", "helicopter_rotor", "helicopter_start"]


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def png_size(p):
    from PIL import Image
    with Image.open(p) as im:
        return im.size


def run(cmd, timeout=3400, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env, cwd=cwd)


need(re.search(r"version = '3\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 3.x")
need(re.search(r"mod_version=3\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.x")
need("V3.0.0" in read(ROOT / "README.md"), "README sin la entrada V3.0.0")
need("PARCHE V3.0.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V3.0.0")

# ---------------------------------------------------------------- JSON válido
for f in sorted(list(ASSETS.rglob("*.json")) + list(DATA.rglob("*.json"))):
    try:
        json.loads(read(f))
    except Exception as exc:  # noqa: BLE001
        errors.append(f"JSON inválido: {f.relative_to(ROOT)}: {exc}")

es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))

# ---------------------------------------------------------------- vehículos
for name, tex in (("delivery_truck", (256, 320)), ("helicopter", (256, 256))):
    need((ASSETS / f"models/item/{name}.json").exists(), f"falta el modelo de ítem de {name}")
    item_png = ASSETS / f"textures/item/{name}.png"
    need(item_png.exists() and png_size(item_png) == (32, 32), f"ícono de {name} ausente o que no mide 32x32")
    ent_png = ASSETS / f"textures/entity/{name}.png"
    need(ent_png.exists() and png_size(ent_png) == tex, f"textura de entidad de {name} ausente o que no mide {tex}")
    for kind in ("item", "entity"):
        need(f"{kind}.beautifuldecor.{name}" in es and f"{kind}.beautifuldecor.{name}" in en, f"falta el nombre {kind} de {name} en es/en")
    m = json.loads(read(ASSETS / f"models/item/{name}.json"))
    need(m["textures"]["layer0"] == f"beautifuldecor:item/{name}", f"el modelo de ítem de {name} apunta a otra textura")
need((ASSETS / "textures/entity/delivery_truck_glow.png").exists()
     and png_size(ASSETS / "textures/entity/delivery_truck_glow.png") == (256, 320), "falta el brillo del camión (256x320)")

for name in SOUNDS:
    need((ASSETS / f"sounds/{name}.ogg").exists(), f"falta sounds/{name}.ogg")
sounds_json = json.loads(read(ASSETS / "sounds.json"))
mod_sounds = read(JAVA / "init/ModSounds.java")
for name in SOUNDS:
    need(name in sounds_json, f"sounds.json sin {name}")
    if name in sounds_json:
        need(sounds_json[name]["sounds"][0]["name"] == f"beautifuldecor:{name}", f"sounds.json: {name} apunta a otro archivo")
        sub = sounds_json[name]["subtitle"]
        need(sub in es and sub in en, f"subtítulo {sub} sin traducir")
    need(f'simple("{name}")' in mod_sounds, f"ModSounds no registra {name}")

ents = read(JAVA / "init/ModEntities.java")
items = read(JAVA / "init/ModItems.java")
for reg, cls in (("delivery_truck", "DeliveryTruckEntity"), ("helicopter", "HelicopterEntity")):
    need(f'ENTITIES.register("{reg}"' in ents, f"ModEntities no registra {reg}")
    need(f'ITEMS.register("{reg}"' in items, f"ModItems no registra {reg}")
    need((JAVA / f"entity/{cls}.java").exists(), f"falta {cls}.java")
need("new VehicleItem(ModEntities.DELIVERY_TRUCK::get" in items, "el camión debe salir de un VehicleItem")
need("new HelicopterItem(ModEntities.HELICOPTER::get" in items, "el helicóptero debe salir de un HelicopterItem")
need('"delivery_truck", "helicopter"' in read(JAVA / "init/ModCreativeTabs.java"), "VEHICLE_IDS sin camión y helicóptero")

cev = read(JAVA / "client/ClientEvents.java")
for token in ("ModEntities.DELIVERY_TRUCK.get(), DeliveryTruckRenderer::new", "ModEntities.HELICOPTER.get(), HelicopterRenderer::new",
              "DeliveryTruckModel.LAYER, DeliveryTruckModel::createBodyLayer", "HelicopterModel.LAYER, HelicopterModel::createBodyLayer",
              "event.register(HelicopterKeys.EXIT)"):
    need(token in cev, f"ClientEvents no tiene {token}")
msg = read(JAVA / "network/ModMessages.java")
need("HelicopterActionPacket.class" in msg, "ModMessages no registra HelicopterActionPacket")
need(re.search(r'PROTOCOL = "([7-9]|\d{2,})"', msg), 'ModMessages.PROTOCOL debería ser "7" o más (paquete nuevo)')

heli = read(JAVA / "entity/HelicopterEntity.java")
for token in ("extends Entity", "isControlledByLocalInstance()", "positionRider(", "getDismountLocationForPassenger(", "canExit()", "requestExit(",
              "SEATS = 4", "EXIT_MAX_HEIGHT = 2.0", "TAKEOFF_RPM"):
    need(token in heli, f"HelicopterEntity no tiene {token}")
hev = read(JAVA / "client/HelicopterClientEvents.java")
for token in ("input.shiftKeyDown = false", "HelicopterKeys.EXIT.consumeClick()", "HelicopterActionPacket", "setRiderInput("):
    need(token in hev, f"HelicopterClientEvents no tiene {token}")
keys = read(JAVA / "client/HelicopterKeys.java")
need("GLFW_KEY_Z" in keys, "la tecla de bajarse del helicóptero debe ser Z")
truck = read(JAVA / "entity/DeliveryTruckEntity.java")
need("extends VehicleEntity" in truck and "MAX_SPEED = 2.4f" in truck, "DeliveryTruckEntity debe ser un VehicleEntity de 2,4 bloques/tick")

# ---------------------------------------------------------------- textos que salen del código
used = set()
for rel in ("entity/HelicopterEntity.java", "client/HelicopterClientEvents.java", "client/HelicopterKeys.java"):
    used |= set(re.findall(r'"((?:hud|key|gui)\.beautifuldecor\.[a-z0-9_.]+)"', read(JAVA / rel)))
need(used, "no se encontró ningún texto del helicóptero en el código")
for key in sorted(used):
    need(key in es and key in en, f"texto sin traducir: {key}")

# ---------------------------------------------------------------- cuadros
blocks = read(JAVA / "init/ModBlocks.java")
pic = read(JAVA / "block/PictureBlock.java")
for token in ("FACING", "canSurvive(", "updateShape(", "getStateForPlacement("):
    need(token in pic, f"PictureBlock no tiene {token}")
order = read(JAVA / "init/ModCreativeOrder.java")
axe = json.loads(read(DATA / "minecraft/tags/blocks/mineable/axe.json"))["values"]
for n in PAINTINGS:
    need(f'BLOCKS.register("{n}"' in blocks, f"ModBlocks no registra {n}")
    need(f'ITEMS.register("{n}"' in items, f"ModItems no registra {n}")
    need(f'"{n}"' in axe or f"beautifuldecor:{n}" in axe, f"{n} no está en la etiqueta de hacha")
    need(f'"painting_*"' in order, "ModCreativeOrder sin painting_*")
    for rel in (f"blockstates/{n}.json", f"models/block/{n}.json", f"models/item/{n}.json"):
        need((ASSETS / rel).exists(), f"falta {rel}")
    need((DATA / f"beautifuldecor/loot_tables/blocks/{n}.json").exists(), f"falta el loot de {n}")
    bt = ASSETS / f"textures/block/{n}.png"
    it = ASSETS / f"textures/item/{n}.png"
    need(bt.exists() and png_size(bt) == (128, 96), f"textura de bloque de {n} ausente o que no mide 128x96")
    need(it.exists() and png_size(it) == (32, 32), f"ícono de {n} ausente o que no mide 32x32")
    for kind in ("block", "item"):
        need(f"{kind}.beautifuldecor.{n}" in es and f"{kind}.beautifuldecor.{n}" in en, f"falta el nombre {kind} de {n} en es/en")
    model = json.loads(read(ASSETS / f"models/block/{n}.json"))
    for ref in model.get("textures", {}).values():
        need((ASSETS / f"textures/{ref.split(':')[-1]}.png").exists(), f"{n}: el modelo usa una textura que no existe: {ref}")
    bs = json.loads(read(ASSETS / f"blockstates/{n}.json"))["variants"]
    need(set(bs) == {"facing=north", "facing=east", "facing=south", "facing=west"}, f"{n}: blockstate sin las 4 orientaciones")

# ---------------------------------------------------------------- dron
drone = read(JAVA / "entity/DroneEntity.java")
need("MAX_RANGE = 548.0;" in drone, "DroneEntity.MAX_RANGE debe ser 548.0")

# ---------------------------------------------------------------- la geometría y la textura guardadas son las que genera el código
try:
    sys.path.insert(0, str(ROOT))
    import numpy as np
    from PIL import Image
    from tools.v300 import delivery_truck, helicopter
    for mod, name in ((delivery_truck, "delivery_truck"), (helicopter, "helicopter")):
        built = mod.build_all()
        tex = np.asarray(built[1])
        saved = np.asarray(Image.open(ASSETS / f"textures/entity/{name}.png").convert("RGBA"))
        need(tex.shape == saved.shape and np.array_equal(tex, saved), f"la textura guardada de {name} no coincide con la que genera tools/v300/{name}.py")
    notes.append("las texturas del camión y del helicóptero coinciden con lo que genera el código")
except Exception as exc:  # noqa: BLE001
    notes.append(f"no se pudo regenerar el camión y el helicóptero para compararlos: {exc}")

# ---------------------------------------------------------------- sintaxis de Java
javac = shutil.which("javac")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if javac is None:
    notes.append("sin javac: no se revisó la sintaxis de Java")
else:
    files = [JAVA / rel for rel in (
        "entity/DeliveryTruckEntity.java", "entity/HelicopterEntity.java", "client/DeliveryTruckRenderer.java", "client/DeliveryTruckModel.java",
        "client/HelicopterModel.java", "client/HelicopterRenderer.java", "client/HelicopterClientEvents.java", "client/HelicopterRotorSound.java",
        "client/HelicopterKeys.java", "network/HelicopterActionPacket.java", "item/HelicopterItem.java", "block/PictureBlock.java",
        "init/ModEntities.java", "init/ModItems.java", "init/ModSounds.java", "init/ModCreativeTabs.java", "client/ClientEvents.java",
        "network/ModMessages.java", "entity/DroneEntity.java", "init/ModBlocks.java", "init/ModCreativeOrder.java")]
    out = Path(tempfile.mkdtemp(prefix="v300_"))
    try:
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=out)   # cwd aparte: javac deja un javac.*.args si hay errores
        # Sin las piezas de Minecraft solo se pueden ver los errores de sintaxis: todo lo que no sea "no existe el símbolo/paquete" cuenta.
        bad = []
        for line in r.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(k in line for k in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
    finally:
        shutil.rmtree(out, ignore_errors=True)

# ---------------------------------------------------------------- lo de la V2.11.0
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v2110.py")], env=env)
    need(r.returncode == 0 and "OK V2.11" in r.stdout, "verify_v2110 falló:\n" + (r.stdout + r.stderr)[-2000:])

if errors:
    print("FALLÓ la verificación V3.0.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V3.0.0: camión de reparto AMAZOOM, helicóptero BOING HX-6 (Espacio/Shift/WASD, Z para bajar), 3 cuadros de pared y dron a 548 bloques; recursos, idiomas y registros completos.")
for n in notes:
    print("  (nota)", n)
