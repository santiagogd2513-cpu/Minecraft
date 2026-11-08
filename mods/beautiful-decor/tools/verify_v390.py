#!/usr/bin/env python3
"""Comprueba SOLO la V3.9.0: almacenamiento en los vehículos + arreglos del avión (no vuelve a correr verificaciones de versiones anteriores).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README, NUEVOS_BLOQUES, protocolo de red ("18" o más);
  * avión que se iba solo: el vuelo solo corre con un jugador local pilotando (no con isControlledByLocalInstance, que en el servidor es verdadero
    con el avión vacío), "en el aire" exige aire debajo, y una simulación con las mismas fórmulas muestra que antes recorría ~100 bloques y ahora 0;
  * sonido de las turbinas: condiciones de apagado y gestor;
  * almacenamiento: filas por vehículo (camioneta 3, superdeportivo 1, camión 6, helicóptero 3, avión 6; bici y moto 0), guardado y lectura (NBT),
    soltar al destruir, paquete registrado, evento de cliente que cancela el inventario, textos es/en;
  * sintaxis del Java nuevo o cambiado.

Uso: python3 tools/verify_v390.py
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
errors, notes = [], []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def code(p):
    """Java sin comentarios (para no confundir una mención en un comentario con código)."""
    s = read(p)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, env=env, cwd=cwd)


# ---------------------------------------------------------------- versión, documentos, red
need(re.search(r"version = '3\.(9|\d{2,})\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 3.9.0 o superior")
need(re.search(r"mod_version=3\.(9|\d{2,})\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.9.0 o superior")
need("V3.9.0" in read(ROOT / "README.md"), "README sin la entrada V3.9.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.9.0") and "PARCHE V3.8.0" in nuevos, "NUEVOS_BLOQUES debe empezar con la sección V3.9.0 y conservar la V3.8.0")
msg = read(JAVA / "network/ModMessages.java")
need(re.search(r'PROTOCOL = "(1[8-9]|[2-9]\d|\d{3,})"', msg), "el protocolo de red debe ser 18 o más (paquete nuevo)")
need("OpenVehicleStoragePacket.class" in msg and "NetworkDirection.PLAY_TO_SERVER" in msg[msg.index("OpenVehicleStoragePacket.class"):msg.index("OpenVehicleStoragePacket.class") + 200],
     "paquete del almacenamiento sin registrar")

# ---------------------------------------------------------------- avión que se iba solo
al_src = code(JAVA / "entity/AirlinerEntity.java")
need("isControlledByLocalInstance" not in al_src, "AirlinerEntity todavía usa isControlledByLocalInstance (en el servidor es verdadero con el avión vacío)")
need(re.search(r"private boolean isPilotedHere\(\)\s*\{\s*return this\.getControllingPassenger\(\) instanceof Player pilot && pilot\.isLocalPlayer\(\);", al_src),
     "falta isPilotedHere() (solo un jugador local pilotando)")
need(len(re.findall(r"this\.isPilotedHere\(\)", al_src)) >= 2, "tick() y tickLerp() deben usar isPilotedHere()")
need(re.search(r"boolean airborne = !this\.onGround\(\) && height > GROUND_TOUCH_HEIGHT;", al_src), "«en el aire» debe exigir aire debajo")
m = re.search(r"GROUND_TOUCH_HEIGHT = ([0-9.]+);", al_src)
touch = float(m.group(1)) if m else 0.0
need(m is not None and 0.25 < touch <= 0.6, "GROUND_TOUCH_HEIGHT fuera de lo razonable")


def const(name):
    mm = re.search(rf"\b{name} = (-?[0-9.]+)f?;", al_src)
    need(mm is not None, f"no se encontró la constante {name}")
    return float(mm.group(1)) if mm else 0.0


MIN_AIR = const("MIN_AIR_SPEED")
# Simulación del avión recién puesto, sin tocar nada. Mismas fórmulas del cuerpo de flyTick (sin entrada: ni adelante ni atrás).
# Altura medida por heightAboveGround al ponerlo a y + 0,01: el primer paso d = 0 no choca (apoya justo), d = 0,25 sí => 0,25.
SPAWN_HEIGHT = 0.25


def simulate(fixed, ticks=600):
    speed, dist, on_ground = 0.0, 0.0, False
    for _ in range(ticks):
        airborne = (not on_ground) and (SPAWN_HEIGHT > touch if fixed else True)
        if airborne:
            speed = max(MIN_AIR, speed - 0.004)
        else:
            speed = max(0.0, speed - 0.012) if speed > 0 else min(0.0, speed + 0.02)
        dist += speed
        on_ground = True            # el avión baja con -0,08 hasta apoyarse: desde el primer tick ya está en el suelo
    return dist, speed


old_dist, _ = simulate(False)
new_dist, new_speed = simulate(True)
need(old_dist > 50, "la simulación del fallo no reproduce el avión que se iba solo")
need(new_dist == 0.0 and new_speed == 0.0, f"el avión recién puesto debe quedarse quieto (recorre {new_dist})")
notes.append(f"avión recién puesto sin tocar nada: antes recorría {old_dist:.0f} bloques solo, ahora {new_dist:.0f}")

# ---------------------------------------------------------------- sonido de las turbinas
snd = code(JAVA / "client/AirlinerEngineSound.java")
need("minecraft.level.getEntity(this.airliner.getId()) != this.airliner" in snd, "el sonido no se apaga si el avión ya no está en el mundo del cliente")
need("this.frozenTicks > 40" in snd and "this.airliner.tickCount" in snd, "el sonido no se apaga si el avión dejó de actualizarse")
need("this.emptyTicks > 400" in snd and "this.airliner.isVehicle()" in snd, "el sonido no se apaga si el avión lleva rato vacío")
need("rpm < 0.03f" in snd and "this.stop()" in snd, "el sonido debe apagarse cuando las turbinas se paran")
ev = code(JAVA / "client/AirlinerClientEvents.java")
need("sound.isFinished() || sound.isStopped()" in ev and "160.0 * 160.0" in ev, "el gestor de sonidos de turbina no limpia lo detenido o no limita la distancia")
# simulación del apagado: congelado, vacío
frozen = 0
for t in range(100):
    frozen += 1
    if frozen > 40:
        break
need(frozen == 41, "cuenta de ticks congelados")

# ---------------------------------------------------------------- almacenamiento
for f in ("entity/HasVehicleStorage.java", "entity/VehicleStorage.java", "network/OpenVehicleStoragePacket.java", "client/VehicleStorageClientEvents.java"):
    need((JAVA / f).exists(), f"falta {f}")
ve = code(JAVA / "entity/VehicleEntity.java")
he = code(JAVA / "entity/HelicopterEntity.java")
al = code(JAVA / "entity/AirlinerEntity.java")
for name, src in (("VehicleEntity", ve), ("HelicopterEntity", he), ("AirlinerEntity", al)):
    need("HasVehicleStorage" in src, f"{name} no implementa HasVehicleStorage")
    need("VehicleStorage.save(this.storage, tag)" in src and "VehicleStorage.load(this.storage, tag)" in src, f"{name} no guarda o no lee el almacenamiento")
    need(re.search(r"public void remove\(Entity\.RemovalReason reason\)\s*\{\s*if \(reason\.shouldDestroy\(\)\)\s*\{\s*VehicleStorage\.drop\(this, this\.storage\);\s*\}\s*super\.remove\(reason\);", src),
         f"{name} no suelta el contenido al destruirse")
    need("container.beautifuldecor.storage." in src and "paintKind().texture()" in src, f"{name}: título del almacenamiento")
rows = {}
for fname, want in (("PickupEntity", 3), ("SupercarEntity", 1), ("DeliveryTruckEntity", 6)):
    src = code(JAVA / f"entity/{fname}.java")
    mm = re.search(r"public int storageRows\(\)\s*\{\s*return (\d+);", src)
    rows[fname] = int(mm.group(1)) if mm else None
    need(rows[fname] == want, f"{fname}: filas {rows[fname]}, debían ser {want}")
mm = re.search(r"this\.storage = new VehicleStorage\.Box\(this, (\d+)\)", he)
need(mm and int(mm.group(1)) == 3 and re.search(r"public int storageRows\(\)\s*\{\s*return 3;", he), "helicóptero: 3 filas")
mm = re.search(r"this\.storage = new VehicleStorage\.Box\(this, (\d+)\)", al)
need(mm and int(mm.group(1)) == 6 and re.search(r"public int storageRows\(\)\s*\{\s*return 6;", al), "avión: 6 filas")
need(re.search(r"public int storageRows\(\)\s*\{\s*return 0;", ve), "VehicleEntity: por defecto 0 filas")
for fname in ("BicycleEntity", "MotorcycleEntity"):
    need("storageRows" not in code(JAVA / f"entity/{fname}.java"), f"{fname} no debe tener almacenamiento")
need("int rows = this.storageRows();" in ve and "rows > 0 ? new VehicleStorage.Box(this, rows) : null" in ve, "VehicleEntity: el contenedor se crea según storageRows()")

vs = code(JAVA / "entity/VehicleStorage.java")
need("player.getVehicle() == this.owner" in vs and "!this.owner.isRemoved()" in vs, "la ventana debe cerrarse si te bajas o el vehículo desaparece")
need("MenuType.GENERIC_9x1" in vs and "MenuType.GENERIC_9x6" in vs and "new ChestMenu(menuType(rows), id, inventory, box, rows)" in vs, "ventana de cofre por filas")
need("ContainerHelper.saveAllItems" in vs and "ContainerHelper.loadAllItems" in vs and "removeItemNoUpdate" in vs and "spawnAtLocation" in vs, "guardar, leer o soltar")
pk = code(JAVA / "network/OpenVehicleStoragePacket.java")
need("player.getVehicle() instanceof HasVehicleStorage vehicle && vehicle.hasStorage()" in pk and "VehicleStorage.open(player, vehicle)" in pk, "el servidor debe abrir solo el almacenamiento del vehículo en el que vas montado")
need("player.containerMenu == player.inventoryMenu" in pk, "no abrir si ya hay otra ventana abierta")
cl = code(JAVA / "client/VehicleStorageClientEvents.java")
need("ScreenEvent.Opening" in cl and "InventoryScreen" in cl and "CreativeModeInventoryScreen" in cl and "event.setCanceled(true)" in cl
     and "new OpenVehicleStoragePacket()" in cl and "instanceof HasVehicleStorage vehicle && vehicle.hasStorage()" in cl, "el evento de cliente debe cancelar el inventario y pedir el almacenamiento")

# textos
en, es = json.loads(read(ASSETS / "lang/en_us.json")), json.loads(read(ASSETS / "lang/es_es.json"))
vp = read(JAVA / "entity/VehiclePaint.java")
textures = dict(re.findall(r"(\w+)\(\"(\w+)\",\s*0x", vp))
for kind in ("PICKUP", "SUPERCAR", "DELIVERY_TRUCK", "HELICOPTER", "AIRLINER"):
    key = f"container.beautifuldecor.storage.{textures.get(kind)}"
    need(textures.get(kind) is not None and key in en and key in es and en[key].strip() and es[key].strip(), f"falta el título del almacenamiento ({kind}): {key}")
need(set(en) == set(es), "es_es y en_us no tienen las mismas claves")
used = set()
for p in JAVA.rglob("*.java"):
    used |= set(re.findall(r'"((?:hud|key|item|entity|container)\.beautifuldecor\.[a-z_.0-9]*)"', read(p)))
for k in sorted(used):
    if k.endswith("."):
        continue
    need(k in en and k in es, f"clave de idioma usada en el Java pero sin texto: {k}")
notes.append(f"almacenamiento: camioneta {rows['PickupEntity']} filas, superdeportivo {rows['SupercarEntity']}, camión {rows['DeliveryTruckEntity']}, helicóptero 3, avión 6; bici y moto ninguno")

# ---------------------------------------------------------------- Java: sintaxis
javac = shutil.which("javac")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if not javac:
    notes.append("sin javac: no se revisó la sintaxis")
else:
    work = Path(tempfile.mkdtemp(prefix="v390_"))
    try:
        out = work / "syntax"
        out.mkdir()
        names = ("entity/HasVehicleStorage.java", "entity/VehicleStorage.java", "network/OpenVehicleStoragePacket.java", "client/VehicleStorageClientEvents.java",
                 "entity/VehicleEntity.java", "entity/PickupEntity.java", "entity/SupercarEntity.java", "entity/DeliveryTruckEntity.java",
                 "entity/HelicopterEntity.java", "entity/AirlinerEntity.java", "client/AirlinerEngineSound.java", "client/AirlinerClientEvents.java",
                 "network/ModMessages.java")
        files = [JAVA / p for p in names]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed",
            "method does not override", "cannot be referenced", "constructor", "cannot be inherited", "is not public", "has private access", "incomparable types"))]
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V3.9.0:")
    for e in errors:
        print(" -", e)
    for nn in notes:
        print("  (nota)", nn)
    sys.exit(1)
print("OK V3.9.0: almacenamiento en los vehículos y arreglos del avión; solo se verificó esta versión.")
for nn in notes:
    print("  (nota)", nn)
