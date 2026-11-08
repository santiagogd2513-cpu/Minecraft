#!/usr/bin/env python3
"""Comprueba la V2.1.0: bicicleta, camioneta Ford F-150, moto a 120 bloques/s, caja de herramientas y cajones de cocina.

No compila nada (eso lo hace Gradle en tu equipo): revisa que cada pieza exista y esté conectada (registros, modelos Java,
texturas, sonidos, blockstates, modelos de bloque, loot, idiomas, pestaña) y que las velocidades sean las pedidas.

Uso: python3 tools/verify_v210.py
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
need(re.search(r"version = '(2\.([1-9]\d*)\.\d+|[3-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")),
     "build.gradle no está en 2.1.0 o superior")
need(re.search(r"mod_version=(2\.([1-9]\d*)\.\d+|[3-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")),
     "gradle.properties no está en 2.1.0 o superior")
need("V2.1.0" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V2.1.0")
need("PARCHE V2.1.0" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V2.1.0")

# ------------------------------------------------------------------------------------------------ velocidades pedidas
for cls, blocks_per_s in (("MotorcycleEntity", 120.0), ("BicycleEntity", 40.0)):
    m = re.search(r"MAX_SPEED\s*=\s*([\d.]+)f", read(f"entity/{cls}.java"))
    need(m and abs(float(m.group(1)) * 20 - blocks_per_s) < 1e-6, f"{cls}.MAX_SPEED no equivale a {blocks_per_s:g} bloques/s")
m = re.search(r"MAX_SPEED\s*=\s*([\d.]+)f", read("entity/PickupEntity.java"))
need(m and 0 < float(m.group(1)) * 20 <= 120, "PickupEntity.MAX_SPEED fuera de lo razonable")
for cls in ("MotorcycleEntity", "BicycleEntity", "PickupEntity"):
    need("extends VehicleEntity" in read(f"entity/{cls}.java"), f"{cls} no extiende VehicleEntity")

# ------------------------------------------------------------------------------------------------ registros
blocks, items, ents, sounds, bents = (read("init/ModBlocks.java"), read("init/ModItems.java"), read("init/ModEntities.java"),
                                      read("init/ModSounds.java"), read("init/ModBlockEntities.java"))
for ent in ("bicycle", "ford_f150"):
    need(f'ENTITIES.register("{ent}"' in ents, f"ModEntities no registra {ent}")
    need(f'ITEMS.register("{ent}"' in items, f"ModItems no registra el ítem {ent}")
need("VehicleItem" in items, "ModItems no usa VehicleItem")
need('ITEMS.register("toolbox_open"' in items and 'BLOCKS.register("toolbox_open"' in blocks, "toolbox_open sin registrar")
need("registerGroup(ModBlocks.KITCHEN_DRAWERS)" in items, "ModItems no registra los ítems de los cajones")
need('BLOCKS.register("kitchen_drawers_" + wood' in blocks and "KitchenDrawersBlock" in blocks, "ModBlocks no registra los cajones")
need("import com.example.beautifuldecor.block.KitchenDrawersBlock;" in blocks, "ModBlocks sin el import de KitchenDrawersBlock")
need('register("kitchen_drawers"' in bents and "KitchenDrawersBlockEntity::new" in bents, "ModBlockEntities no registra los cajones")
need("import com.example.beautifuldecor.block.entity.KitchenDrawersBlockEntity;" in bents, "ModBlockEntities sin el import")
for sid in ("bicycle_bell", "pickup_engine", "pickup_horn", "pickup_start"):
    need(f'simple("{sid}")' in sounds, f"ModSounds no registra {sid}")
    p = ASSETS / f"sounds/{sid}.ogg"
    need(p.exists() and p.read_bytes()[:4] == b"OggS", f"sounds/{sid}.ogg falta o no es un Ogg")
sj = jload(ASSETS / "sounds.json")
for sid in ("bicycle_bell", "pickup_engine", "pickup_horn", "pickup_start"):
    need(sid in sj, f"sounds.json sin {sid}")
    for s in sj.get(sid, {}).get("sounds", []):
        name = s["name"] if isinstance(s, dict) else s
        need((ASSETS / "sounds" / (name.split(":")[1] + ".ogg")).exists(), f"sounds.json: {name} no existe")
need(re.search(r'PROTOCOL = "([4-9]|\d{2,})"', read("network/ModMessages.java")), "el protocolo de red debe ser 4 o más")
cev = read("client/ClientEvents.java")
for needle in ("BicycleRenderer::new", "PickupRenderer::new", "BicycleModel.LAYER", "PickupModel.LAYER", "VehicleKeys.LIGHT"):
    need(needle in cev, f"ClientEvents no tiene {needle}")
tabs = read("init/ModCreativeTabs.java")
vm = re.search(r"VEHICLE_IDS\s*=\s*List\.of\((.*?)\);", tabs, re.S)
need(vm and re.findall(r'"([^"]+)"', vm.group(1)) == ["bicycle", "motorcycle", "ford_f150"], "VEHICLE_IDS debe ser bicycle, motorcycle, ford_f150")

# ------------------------------------------------------------------------------------------------ modelos de entidad
PARTS = {
    "BicycleModel": ("bicycle", 128, ["bike", "rear_wheel", "crank", "steer", "fork", "front_wheel", "kickstand"]),
    "PickupModel": ("ford_f150", 256, ["body", "glass", "steer_l", "steer_r", "wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
                                       "steering_tilt", "steering_wheel"]),
}
for cls, (tex, size, parts) in PARTS.items():
    src = read(f"client/{cls}.java")
    for part in parts:
        need(f'addOrReplaceChild("{part}"' in src, f"{cls} no define la pieza {part}")
        need(f'getChild("{part}")' in src, f"{cls} no usa getChild({part})")
    need(f"LayerDefinition.create(mesh, {size}, {size})" in src, f"{cls}: el atlas no es de {size}x{size}")
    for suffix in ("", "_glow"):
        need(png_size(ASSETS / f"textures/entity/{tex}{suffix}.png") == (size, size), f"{tex}{suffix}.png no es de {size}x{size}")
    need(png_size(ASSETS / f"textures/item/{tex}.png") == (32, 32), f"el ícono de {tex} no es de 32x32")
    need(jload(ASSETS / f"models/item/{tex}.json").get("parent") == "minecraft:item/generated", f"el modelo de ítem de {tex} no es un sprite")
    # cada cubo cabe en el atlas
    for u, v, w, h, d in re.findall(r"texOffs\((\d+), (\d+)\)\.addBox\([-\d.f, ]+?, ([\d.]+)f, ([\d.]+)f, ([\d.]+)f\)", src):
        u, v, w, h, d = int(u), int(v), float(w), float(h), float(d)
        need(u + 2 * (d + w) <= size + 0.5 and v + d + h <= size + 0.5, f"{cls}: cubo fuera del atlas texOffs({u},{v}) {w}x{h}x{d}")
    # no hay dos pedazos de código con el mismo nombre de variable de pieza
    names = re.findall(r"PartDefinition (p_\w+) =", src)
    need(len(names) == len(set(names)), f"{cls}: variables de pieza repetidas")
need("showGlass" in read("client/PickupModel.java") and "showGlass(true)" in read("client/PickupRenderer.java"),
     "el renderizador de la camioneta no dibuja los vidrios en una pasada aparte")
need("steeringWheel.zRot" in read("client/PickupModel.java"), "el volante no gira")
need("pose(" in read("client/BicycleRenderer.java") and "isParked" in read("client/BicycleRenderer.java"), "BicycleRenderer sin pose")

# ------------------------------------------------------------------------------------------------ bloques nuevos
WOODS = re.findall(r'"(\w+)"', re.search(r"WOODS\s*=\s*\{(.*?)\};", blocks, re.S).group(1))
need(len(WOODS) == 11, f"ModBlocks.WOODS tiene {len(WOODS)} maderas, deben ser 11")
ids = [f"kitchen_drawers_{w}" for w in WOODS] + ["toolbox_open"]
en, es = jload(ASSETS / "lang/en_us.json"), jload(ASSETS / "lang/es_es.json")
need(set(en) == set(es), f"en_us y es_es no tienen las mismas claves ({len(en)} vs {len(es)})")
TEX_USED = set()


def check_model(name):
    mp = ASSETS / f"models/block/{name}.json"
    if not mp.exists():
        errors.append(f"falta el modelo {name}")
        return
    data = jload(mp)
    textures = dict(data.get("textures", {}))
    parent = data.get("parent", "")
    chain = data
    while parent.startswith("beautifuldecor:block/"):
        chain = jload(ASSETS / "models/block" / (parent.split("/")[1] + ".json"))
        for k, v in chain.get("textures", {}).items():
            textures.setdefault(k, v)
        data.setdefault("elements", chain.get("elements"))
        parent = chain.get("parent", "")
    keys = set(textures)
    for ref in textures.values():
        if ref.startswith("#"):
            need(ref[1:] in keys, f"{name}: textura {ref} sin definir")
        elif ref.startswith("beautifuldecor:"):
            need((ASSETS / "textures" / (ref.split(":")[1] + ".png")).exists(), f"{name}: falta la textura {ref}")
    for el in data.get("elements") or []:
        for c in el["from"] + el["to"]:
            need(-16 <= c <= 32, f"{name}: coordenada fuera de rango {c}")
        rot = el.get("rotation")
        if rot:
            need(rot["angle"] in (-45, -22.5, 0, 22.5, 45), f"{name}: ángulo de rotación no permitido {rot['angle']}")
        for fn, face in el["faces"].items():
            need(face["texture"].lstrip("#") in keys, f"{name}: textura {face['texture']} sin definir")
            uv = face.get("uv")
            if uv:
                need(all(-0.001 <= v <= 16.001 for v in uv), f"{name}: uv fuera de 0..16 {uv}")


for bid in ids:
    for table in (en, es):
        need(f"block.beautifuldecor.{bid}" in table and f"item.beautifuldecor.{bid}" in table, f"falta el nombre de {bid}")
    need((DATA / f"loot_tables/blocks/{bid}.json").exists(), f"falta el loot de {bid}")
    need(png_size(ASSETS / f"textures/item/{bid}.png") == (32, 32), f"el ícono de {bid} no es de 32x32")
    need(jload(ASSETS / f"models/item/{bid}.json").get("parent") == "minecraft:item/generated", f"{bid}: el ítem no es un sprite")
    bs = jload(ASSETS / f"blockstates/{bid}.json")["variants"]
    if bid == "toolbox_open":
        need(set(bs) == {f"facing={f}" for f in ("north", "east", "south", "west")}, "toolbox_open: variantes de blockstate")
        check_model("toolbox_open")
    else:
        need(len(bs) == 8, f"{bid}: el blockstate debe tener 8 variantes (4 orientaciones x abierto/cerrado)")
        for key, v in bs.items():
            m_ = re.fullmatch(r"facing=(north|east|south|west),open=(true|false)", key)
            need(bool(m_), f"{bid}: clave de blockstate inesperada {key}")
            if m_:
                need(v["model"].endswith("_open" if m_.group(2) == "true" else "_closed"), f"{bid}: {key} usa el modelo equivocado")
                check_model(v["model"].split("/")[1])
        loot = json.dumps(jload(DATA / f"loot_tables/blocks/{bid}.json"))
        need("copy_name" in loot, f"{bid}: el loot no conserva el nombre")
for k in ("container.beautifuldecor.kitchen_drawers",):
    need(k in en and k in es, f"falta la clave {k}")
for k in ("item.beautifuldecor.bicycle", "entity.beautifuldecor.bicycle", "item.beautifuldecor.ford_f150",
          "entity.beautifuldecor.ford_f150", "subtitles.beautifuldecor.bicycle_bell", "subtitles.beautifuldecor.pickup_engine",
          "subtitles.beautifuldecor.pickup_horn", "subtitles.beautifuldecor.pickup_start"):
    need(k in en and k in es, f"falta la clave de idioma {k}")
for w in WOODS:
    need(f"{w.replace('_', ' ').title()} Kitchen Drawers" == en.get(f"block.beautifuldecor.kitchen_drawers_{w}"),
         f"nombre en inglés de kitchen_drawers_{w}")

# el modelo abierto debe tener los 3 cajones sacados y el cerrado ninguno por delante de z = 0
op = jload(ASSETS / "models/block/kitchen_drawers_open_base.json")["elements"]
cl = jload(ASSETS / "models/block/kitchen_drawers_closed_base.json")["elements"]
need(min(e["from"][2] for e in op) < -5, "el modelo abierto de los cajones no sale hacia el frente")
need(min(e["from"][2] for e in cl) >= -1.0, "el modelo cerrado de los cajones sobresale demasiado")
need(len(op) > len(cl), "el modelo abierto debe tener las bandejas de los cajones")

# ------------------------------------------------------------------------------------------------ Java de los cajones
kd = read("block/KitchenDrawersBlock.java")
for needle in ("extends BaseEntityBlock", "RenderShape.MODEL", "OPEN", "FACING", "Containers.dropContents", "recheckOpen", "openMenu",
               "getAnalogOutputSignal"):
    need(needle in kd, f"KitchenDrawersBlock sin {needle}")
kb = read("block/entity/KitchenDrawersBlockEntity.java")
for needle in ("RandomizableContainerBlockEntity", "ContainerOpenersCounter", "ChestMenu.threeRows", "getContainerSize", "return 27",
               "startOpen", "stopOpen", "recheckOpen", "saveAdditional", "load(CompoundTag"):
    need(needle in kb, f"KitchenDrawersBlockEntity sin {needle}")
need("javax.annotation.Nullable" in kd, "KitchenDrawersBlock debe usar javax.annotation.Nullable")

# ------------------------------------------------------------------------------------------------ pestaña y orden
order = read("init/ModCreativeOrder.java")
need('"kitchen_drawers_*"' in order and '"toolbox_open"' in order, "ModCreativeOrder no lista los bloques nuevos")
need("kitchen_drawers_" in read("init/ModCategory.java"), "ModCategory no incluye los cajones en Cocina y baño")

if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("bicicleta (40 b/s), camioneta F-150, moto (120 b/s), caja de herramientas y 11 cajones de cocina (registros, modelos, "
      "texturas, sonidos, blockstates, loot, textos, pestaña): bien")
print("OK")
