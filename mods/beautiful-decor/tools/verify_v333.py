#!/usr/bin/env python3
"""Comprueba la V3.3.3: el jugador ya no atraviesa el piso cuando la cabina llega.

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES, y el protocolo de red ("12");
  * ElevatorMotion sigue siendo Java puro y tiene LINGER_TICKS, LAND_WINDOW y landedFeet;
  * la prueba de la llegada (tools/v333/java/ElevatorLandingTest.java): servidor y cliente tick a tick con el orden real de los paquetes
    (bloques en la fase de chunks del tick siguiente, datos de entidades después, "quitar la entidad" al instante), retrasos de red, colisión
    que funciona o no; con el código viejo el jugador atraviesa el piso, con el nuevo nunca;
  * que la prueba MUERDE (mutantes: sin espera, sin subir al jugador al llegar, ventana corta);
  * que la cabina, el renderizador y el zumbido usen todo eso;
  * sintaxis del Java tocado;
  * y corre lo de la V3.3.2 (tools/verify_v332.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v333.py [--fast]     (--fast se salta lo de las versiones anteriores)
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
errors = []
notes = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def squash(text):
    text = re.sub(r"(?m)^\s*//.*$", "", text)
    text = re.sub(r"(?m)([;{}])\s*//.*$", r"\1", text)
    return re.sub(r"\s+", " ", text)


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, env=env, cwd=cwd)


def body(text, signature_regex):
    m = re.search(signature_regex, text)
    if not m:
        return None
    i = m.end() - 1 if text[m.end() - 1] == "{" else text.index("{", m.end())
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[i + 1:j]
    return None


# ---------------------------------------------------------------- versión y documentos
need(re.search(r"version = '3\.(3\.([3-9]|\d{2,})|([4-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.3.3 o superior")
need(re.search(r"mod_version=3\.(3\.([3-9]|\d{2,})|([4-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.3.3 o superior")
need("V3.3.3" in read(ROOT / "README.md"), "README sin la entrada V3.3.3")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.3.3" in nuevos and "PARCHE V3.3.2" in nuevos, "NUEVOS_BLOQUES debe conservar las secciones V3.3.3 y V3.3.2")
need(re.search(r'PROTOCOL = "(1[2-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 12 o más (dato sincronizado nuevo en la cabina)")

# ---------------------------------------------------------------- ElevatorMotion
motion = read(JAVA / "elevator/ElevatorMotion.java")
need("net.minecraft" not in motion and not re.search(r"^import ", motion, re.M), "ElevatorMotion debe seguir siendo Java puro")
m = re.search(r"public static final int LINGER_TICKS = (\d+);", motion)
need(m is not None and 2 <= int(m.group(1)) <= 10, "LINGER_TICKS debe estar entre 2 y 10 ticks (los bloques salen un tick después; no más de medio segundo)")
m = re.search(r"public static final double LAND_WINDOW = ([\d.]+);", motion)
need(m is not None and 1.0 <= float(m.group(1)) <= 3.0, "LAND_WINDOW debe estar entre 1 y 3 bloques")
need(re.search(r"public static double landedFeet\(double feet, double finalTop, double rest\)", motion), "ElevatorMotion sin landedFeet")

# ---------------------------------------------------------------- la cabina
cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
cs = squash(cabin)
need("EntityDataAccessor<Integer> DATA_LANDED_Y" in cs and "SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.INT)" in cs, "falta el dato sincronizado DATA_LANDED_Y")
need("this.entityData.define(DATA_LANDED_Y, NOT_LANDED);" in cs, "defineSynchedData debe definir DATA_LANDED_Y")
arrive = squash(body(cabin, r"private void arrive\(\) \{") or "")
need("placeBlocks(base);" in arrive and "land(base);" in arrive and "this.discard()" not in arrive, "arrive() debe poner los bloques y llamar a land(base), NO quitar la cabina en el acto")
blocked = squash(body(cabin, r"private void handleBlocked\(\) \{") or "")
need("land(base);" in blocked and "this.discard()" not in blocked, "si la cabina se atasca, también debe esperar (land) en vez de quitarse en el acto")
land = squash(body(cabin, r"private void land\(int baseY\) \{") or "")
need(("setCabinY(baseY);" in land or "setAxisPos(baseY);" in land) and all(x in land for x in ("this.finished = true;", "this.landed = true;", "this.linger = ElevatorMotion.LINGER_TICKS;",
                             "this.entityData.set(DATA_LANDED_Y, baseY);")), "land() debe marcar la llegada, esperar LINGER_TICKS y avisar al cliente")
tl = squash(body(cabin, r"private void tickLanded\(\) \{") or "")
need("keepOnFloor(riders());" in tl and "this.linger--;" in tl and "this.discard();" in tl, "tickLanded: el cliente sostiene al jugador; el servidor cuenta y recién al final quita la cabina")
tick = squash(body(cabin, r"public void tick\(\) \{") or "")
i_poll = tick.find("pollSync();")
i_landed = tick.find("if (this.landed) { tickLanded(); return; }")
i_fin = tick.find("if (this.finished) { return; }")
need(0 < i_poll < i_landed < i_fin, "tick: primero leer los avisos (cliente), luego la cabina ya llegada, luego 'terminada'")
need("if (this.level().isClientSide) { keepOnFloor(riders()); return; } arrive(); return;" in tick,
     "cuando la cabina del cliente ya llegó pero el servidor no mandó los bloques, el cliente debe seguir pegando al jugador")
poll = squash(body(cabin, r"private void pollSync\(\) \{") or "")
need("startReturn();" in poll and "landOnClient(landedY);" in poll and "NOT_LANDED" in poll, "pollSync debe atender la vuelta y la llegada")
sync = squash(body(cabin, r"public void onSyncedDataUpdated\(EntityDataAccessor<\?> key\) \{") or "")
need("DATA_LANDED_Y.equals(key)" in sync and "pollSync();" in sync, "onSyncedDataUpdated debe atender la llegada en el momento")
loc = squash(body(cabin, r"private void landOnClient\(int baseY\) \{") or "")
need("this.landed = true;" in loc and "ElevatorMotion.landedFeet(feet, finalTop," in loc and ("setCabinY(baseY);" in loc or "setAxisPos(baseY);" in loc) and "rider.setPos(rider.getX(), lifted, rider.getZ());" in loc,
     "landOnClient debe dejar la cabina en su sitio y subir al jugador a la superficie del piso nuevo")
need("double finalTop = baseY + this.floorTop;" in loc or ("double finalTop = getY() + this.floorTop;" in loc and loc.find("setAxisPos(baseY);") < loc.find("double finalTop = getY()")),
     "landOnClient: la superficie es la base más el grosor del suelo")
need("if (!this.ready || this.landed) {" in squash(body(cabin, r"public boolean matchesColumn\(int x, int z\) \{") or ""), "una cabina ya llegada no debe contar como 'en viaje'")
need("public boolean isLanded()" in cs, "falta isLanded()")
need("this.landed" in squash(body(cabin, r"public void onClientRemoval\(\) \{") or ""), "onClientRemoval no debe volver a mover al jugador si ya se atendió la llegada")
need('tag.putBoolean("Landed", this.landed);' in cs and 'tag.getBoolean("Landed")' in cs, "Landed debe guardarse y leerse (una cabina guardada justo al llegar no repone bloques)")
keep = squash(body(cabin, r"private void keepOnFloor\(List<Entity> riders\) \{") or "")
need("rider.setPos(rider.getX(), lifted, rider.getZ());" in keep, "keepOnFloor debe subir al jugador exacto (setPos): Entity.move ignora los movimientos muy pequeños")
need(cabin.count("this.discard();") == 1, "la cabina solo debe quitarse en un sitio (tickLanded), tras la espera")

# ---------------------------------------------------------------- dibujo y sonido
rend = read(JAVA / "client/ElevatorCabinRenderer.java")
rs = squash(rend)
need("if (!cabin.isReady() || cabin.isLanded()) { return; }" in rs, "el renderizador no debe dibujar la cabina ya llegada")
need("level.getBlockState(at) == state" in rs and "continue;" in rs, "el renderizador no debe dibujar un bloque de la cabina donde ya está el bloque de verdad")
need("LevelRenderer.getLightColor(level, at)" in rs, "cada bloque debe tomar la luz de su sitio")
need("Mth.lerp(partialTick, cabin.yo, cabin.getY())" in rs, "la luz se mira en la posición suave del fotograma")
need("this.cabin.isRemoved() || this.cabin.isLanded()" in read(JAVA / "client/ElevatorHumSound.java"), "el zumbido debe cortarse al llegar")

# ---------------------------------------------------------------- Java
javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v333_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / "entity/ElevatorCabinEntity.java", JAVA / "elevator/ElevatorMotion.java", JAVA / "client/ElevatorCabinRenderer.java",
                 JAVA / "client/ElevatorHumSound.java", JAVA / "network/ModMessages.java"]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed"))]
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        def run_test(motion_file, tag, limit=300):
            outp = work / f"out_{tag}"
            outp.mkdir()
            c = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(motion_file),
                     str(ROOT / "tools/v333/java/ElevatorLandingTest.java")], env=env, cwd=work)
            if c.returncode != 0 or "warning" in c.stderr:
                return None, "no compila:\n" + c.stderr[-2000:]
            try:
                res = run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", str(outp), "com.example.beautifuldecor.elevator.ElevatorLandingTest"],
                          env=env, cwd=work, timeout=limit)
            except subprocess.TimeoutExpired:
                return None, f"la prueba no termina en {limit} s"
            return res, (res.stdout + res.stderr)[-2500:]

        res, text = run_test(JAVA / "elevator/ElevatorMotion.java", "real")
        need(res is not None and res.returncode == 0 and "ELEVATOR LANDING OK" in res.stdout, "la prueba de la llegada falló:\n" + text)
        if res is not None and res.returncode == 0:
            for line in res.stdout.strip().splitlines():
                if line.startswith(("llegada:", "  código viejo", "  lo más", "ELEVATOR LANDING OK")):
                    notes.append(line.strip())

        def mutant(tag, old, new):
            src = read(JAVA / "elevator/ElevatorMotion.java")
            if old not in src:
                errors.append(f"mutante {tag}: no se encontró el texto a cambiar")
                return
            d = work / f"mut_{tag}"
            (d / "com/example/beautifuldecor/elevator").mkdir(parents=True)
            f = d / "com/example/beautifuldecor/elevator/ElevatorMotion.java"
            f.write_text(src.replace(old, new, 1), encoding="utf-8")
            rm, _ = run_test(f, tag)
            need(rm is not None and rm.returncode != 0, f"la prueba NO se dio cuenta del mutante '{tag}' (debía fallar)")

        mutant("sin_espera", "LINGER_TICKS = 4;", "LINGER_TICKS = 0;")
        mutant("sin_subir_al_llegar", "return feet < finalTop && feet >= finalTop - window ? finalTop : feet;", "return feet;")
        mutant("ventana_corta", "double window = Math.max(LAND_WINDOW, Math.abs(rest) + 0.5);", "double window = 0.01;")
        notes.append("la prueba de la llegada falla con los 3 mutantes (sin espera, sin subir al jugador al llegar, ventana corta)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if "--fast" not in sys.argv:
    rr = run([sys.executable, str(ROOT / "tools/verify_v332.py")], env=env, timeout=7200)
    need(rr.returncode == 0 and "OK V3.3.2" in rr.stdout, "verify_v332 falló:\n" + (rr.stdout + rr.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.3.3:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.3.3: ascensor — la cabina espera a que el cliente tenga los bloques antes de quitarse y deja al jugador sobre el piso nuevo: ya no se atraviesa al llegar.")
for n in notes:
    print("  (nota)", n)
