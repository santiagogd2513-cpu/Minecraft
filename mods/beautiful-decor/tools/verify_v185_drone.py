#!/usr/bin/env python3
"""Comprueba la V1.8.5 (dron): controles, cámara, bloqueo de interacciones, paquete, textos y versión.

No compila nada (eso lo hace Gradle en tu equipo): revisa que el código tenga las piezas que arreglan cada fallo,
que no queden las causas viejas, que los imports estén completos y que la matemática del movimiento sea la esperada.

Uso: python3 tools/verify_v185_drone.py
"""
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
LANG = ROOT / "src/main/resources/assets/beautifuldecor/lang"
errors = []


def read(rel):
    return (JAVA / rel).read_text(encoding="utf-8")


def need(cond, msg):
    if not cond:
        errors.append(msg)


# ------------------------------------------------------------------------------------------------ código
entity = read("entity/DroneEntity.java")
client = read("client/DroneClientEvents.java")
renderer = read("client/DroneRenderer.java")
model = read("client/DroneModel.java")
packet = read("network/DroneControlPacket.java")
events = read("event/DroneInteractionEvents.java")
messages = read("network/ModMessages.java")
entities = read("init/ModEntities.java")

# --- causas viejas que ya no deben estar
need("yaw += 2.5f" not in client and "yaw -= 2.5f" not in client, "A/D todavía giran el dron (yaw += 2.5f)")
need("getCameraEntity() == drone" not in renderer, "el renderer todavía se salta el dibujo cuando la cámara es el dron")
need("freezePlayer" not in client, "queda el congelado viejo del jugador (setPos cada tick)")
need("pilot.setPos" not in entity, "el servidor todavía fija al piloto con setPos cada tick")

# --- controles
need(re.search(r"keyUp\.isDown\(\)\)\s*forward\s*\+=\s*1", client), "W no suma adelante")
need(re.search(r"keyDown\.isDown\(\)\)\s*forward\s*-=\s*1", client), "S no resta adelante")
need(re.search(r"keyRight\.isDown\(\)\)\s*strafe\s*\+=\s*1", client), "D no desplaza a la derecha")
need(re.search(r"keyLeft\.isDown\(\)\)\s*strafe\s*-=\s*1", client), "A no desplaza a la izquierda")
need(re.search(r"keyJump\.isDown\(\)\)\s*vertical\s*\+=\s*1", client), "Espacio no sube")
need(re.search(r"keyShift\.isDown\(\)\)\s*vertical\s*-=\s*1", client), "Shift no baja")
need("GLFW.GLFW_KEY_R" in client, "falta la tecla R para salir")
need("DroneControlPacket.exit(" in client, "R no avisa al servidor para apagar el dron")

# --- cámara fluida
need("TickEvent.RenderTickEvent" in client and "getViewYRot(partialTick)" in client,
     "la cámara no copia la vista del jugador en cada fotograma")
need("setViewRotation" in entity and "this.yRotO = yaw" in entity, "setViewRotation no iguala la rotación anterior")
need("isControlledByLocalInstance" in entity and "return this.localControl ||" in entity,
     "el servidor aún puede mover el dron que vuela el cliente (isControlledByLocalInstance)")
need("public void lerpTo(" in entity and "public void lerpMotion(" in entity,
     "faltan lerpTo/lerpMotion protegidos para el piloto local")
need("getLeanForward(" in client and "getLeanForward(" in model, "la inclinación suavizada no se usa en cámara y modelo")

# --- sin mano / sin bloques / sin ataques
for needle, msg in (
        ("RenderHandEvent", "no se oculta la mano"),
        ("RenderHighlightEvent.Block", "no se oculta el resaltado de bloque"),
        ("InputEvent.InteractionKeyMappingTriggered", "no se cancelan los clics (ataque a entidad inválida)"),
        ("MovementInputUpdateEvent", "el jugador todavía se mueve/salta/agacha mientras vuela"),
        ("keyAttack.setDown(false)", "se puede picar manteniendo el clic"),
        ("PlayerInteractEvent.LeftClickBlock", "falta bloquear LeftClickBlock en cliente"),
):
    need(needle in client, msg)
for needle in ("PlayerInteractEvent.LeftClickBlock", "PlayerInteractEvent.RightClickBlock",
               "PlayerInteractEvent.RightClickItem", "PlayerInteractEvent.EntityInteract",
               "PlayerInteractEvent.EntityInteractSpecific", "AttackEntityEvent", "BlockEvent.BreakEvent",
               "BlockEvent.EntityPlaceEvent"):
    need(needle in events, f"el servidor no bloquea {needle} al piloto")
need("DroneEntity.isPiloting(" in events, "el servidor no sabe quién está pilotando")
need("setCancellationResult(InteractionResult.sidedSuccess" in events, "el clic del mando sobre el dron no se consume")

# --- dibujo
need("event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())" in client,
     "el HUD del dron se dibuja una vez por cada capa del HUD")

# --- volver a ser bloque
need("ModBlocks.DRONE.get().defaultBlockState()" in entity and "packIntoBlock()" in entity,
     "el dron no vuelve a ser bloque")
# dos puntos de llamada cubren todo: stopPiloting (R / mando) y el tick (piloto muerto, desconectado, sin señal o fuera de alcance)
need("this.packIntoBlock();" in re.search(r"public void stopPiloting.*?\n    \}", entity, re.S).group(0),
     "stopPiloting (R / mando) no vuelve el dron un bloque")
need(re.search(r"if \(invalid\) \{.*?this\.packIntoBlock\(\);", entity, re.S),
     "el dron no se guarda como bloque si el piloto muere, se desconecta o se pierde la señal")

# --- paquete: encode y decode en el mismo orden
enc = re.search(r"public void encode\(.*?\{(.*?)\n    \}", packet, re.S).group(1)
dec = re.search(r"public static DroneControlPacket decode\(.*?\{(.*?)\n    \}", packet, re.S).group(1)
enc_kinds = [m.group(1) for m in re.finditer(r"buffer\.write(\w+)\(", enc)]
dec_kinds = [m.group(1) for m in re.finditer(r"buffer\.read(\w+)\(", dec)]
need(enc_kinds == dec_kinds and enc_kinds == ["VarInt", "Double", "Double", "Double", "Float", "Float", "Boolean"],
     f"el paquete no es simétrico: escribe {enc_kinds}, lee {dec_kinds}")
need(re.search(r'PROTOCOL = "([2-9]|\d{2,})"', messages), "el protocolo de red debe subir a 2 o más (el paquete cambió)")
need(".sized(0.8f, 0.5f)" in entities, "falta la hitbox nueva del dron")

# ------------------------------------------------------------------------------------------------ imports
JAVA_LANG = {
    "Math", "Float", "Double", "Integer", "Long", "String", "Override", "Object", "Runnable", "Boolean", "Iterable",
    "Exception", "Deprecated", "SuppressWarnings", "FunctionalInterface", "Byte", "Short", "Character",
    "RemovalReason",          # tipo anidado heredado de Entity (Entity.RemovalReason)
}


def strip_code(src):
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    return src


checked = ["entity/DroneEntity.java", "client/DroneClientEvents.java", "client/DroneRenderer.java",
           "client/DroneModel.java", "network/DroneControlPacket.java", "event/DroneInteractionEvents.java"]
for rel in checked:
    src = read(rel)
    code = strip_code(src)
    imports = set(re.findall(r"^import\s+(?:static\s+)?[\w.]*\.(\w+);", src, re.M))
    declared = set(re.findall(r"\b(?:class|interface|enum|record)\s+(\w+)", code))
    same_pkg = {f.stem for f in (JAVA / rel).parent.glob("*.java")}
    unresolved = set()
    for m in re.finditer(r"(?<![\w.])([A-Z][A-Za-z0-9_]*)\b", code):
        name = m.group(1)
        if name.isupper() or len(name) == 1:           # constantes y parámetros de tipo
            continue
        if name in imports or name in declared or name in same_pkg or name in JAVA_LANG:
            continue
        unresolved.add(name)
    # los imports no usados no rompen nada, pero los que faltan sí
    if unresolved:
        errors.append(f"{rel}: clases usadas sin import: {sorted(unresolved)}")

# ------------------------------------------------------------------------------------------------ matemática
def direction_from_rotation(pitch, yaw):
    """Igual que Vec3.directionFromRotation de Minecraft."""
    f = math.cos(-yaw * 0.017453292 - math.pi)
    f1 = math.sin(-yaw * 0.017453292 - math.pi)
    f2 = -math.cos(-pitch * 0.017453292)
    f3 = math.sin(-pitch * 0.017453292)
    return (f1 * f2, f3, f * f2)


# yaw 0 = sur (+Z), 90 = oeste (-X), 180 = norte (-Z), 270 = este (+X)
cases = {0: ((0, 0, 1), (-1, 0, 0)), 90: ((-1, 0, 0), (0, 0, -1)),
         180: ((0, 0, -1), (1, 0, 0)), 270: ((1, 0, 0), (0, 0, 1))}
for yaw, (want_fwd, want_right) in cases.items():
    for shift in (0, 360, -720, 1080):
        fwd = direction_from_rotation(0, yaw + shift)
        right = direction_from_rotation(0, yaw + shift + 90)
        if any(abs(a - b) > 1e-4 for a, b in zip(fwd, want_fwd)):
            errors.append(f"adelante con yaw {yaw + shift} da {fwd}, esperado {want_fwd}")
        if any(abs(a - b) > 1e-4 for a, b in zip(right, want_right)):
            errors.append(f"derecha con yaw {yaw + shift} da {right}, esperado {want_right}")

# la derecha del jugador es (adelante x arriba): D (+strafe) tiene que ir hacia ahí
for yaw in (0, 33, 90, 180, 250):
    fx, _, fz = direction_from_rotation(0, yaw)
    rx, _, rz = direction_from_rotation(0, yaw + 90)
    cross_y = fz * rx - fx * rz       # componente Y de (adelante x derecha); en Minecraft debe ser positiva (giro horario)
    if abs(abs(cross_y) - 1.0) > 1e-4:
        errors.append(f"adelante y derecha no son perpendiculares con yaw {yaw}")

# suavizado de velocidad: v = v*0.72 + objetivo*0.28 con objetivo 0.22
v = 0.0
for tick in range(1, 41):
    v = v * 0.72 + 0.22 * 0.28
    if tick == 20 and v < 0.21:
        errors.append(f"la velocidad tarda demasiado en llegar al tope ({v:.3f} a los 20 ticks)")
if abs(v - 0.22) > 0.005:
    errors.append(f"la velocidad no converge a 0.22 (llega a {v:.3f})")
stopped = None
for tick in range(1, 80):
    v = v * 0.72
    if v * v < 0.0004:
        stopped = tick
        break
if stopped is None or stopped > 12:
    errors.append(f"el dron tarda demasiado en frenar al soltar las teclas ({stopped} ticks)")

# ------------------------------------------------------------------------------------------------ textos, versión, docs
for name in ("es_es.json", "en_us.json"):
    data = json.loads((LANG / name).read_text(encoding="utf-8"))
    controls = data.get("hud.beautifuldecor.drone_controls", "")
    need("W/A/S/D" in controls, f"{name}: el texto de controles no menciona W/A/S/D")
    need("girar" not in controls and "turn" not in controls, f"{name}: el texto todavía dice que A/D giran")
    need(data.get("hud.beautifuldecor.drone_range", "").count("%s") == 2, f"{name}: falta hud.beautifuldecor.drone_range")

need(re.search(r"version = '(1\.8\.[5-9]|[2-9]\.\d+\.\d+)'", (ROOT / "build.gradle").read_text(encoding="utf-8")),
     "build.gradle no está en 1.8.5 o superior")
need(re.search(r"mod_version=(1\.8\.[5-9]|[2-9]\.\d+\.\d+)", (ROOT / "gradle.properties").read_text(encoding="utf-8")),
     "gradle.properties no está en 1.8.5 o superior")
need("V1.8.5" in (ROOT / "README.md").read_text(encoding="utf-8"), "README sin la entrada V1.8.5")
need("PARCHE V1.8.5" in (ROOT / "NUEVOS_BLOQUES.txt").read_text(encoding="utf-8"), "NUEVOS_BLOQUES sin la sección V1.8.5")

if errors:
    print("ERRORES:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("controles, cámara, bloqueo de interacciones, paquete, imports, matemática de movimiento, textos y versión: bien")
print("OK")
