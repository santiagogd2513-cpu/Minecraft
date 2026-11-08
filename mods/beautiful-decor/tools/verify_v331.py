#!/usr/bin/env python3
"""Comprueba la V3.3.1: parche del ascensor (cabina lenta y llegada inmediata).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES;
  * ElevatorMotion (las cuentas del movimiento) es Java puro, sin nada de Minecraft, y se prueba de verdad (tools/v331/java/ElevatorMotionTest.java):
    780 viajes de 1 a 20 bloques por segundo (llega exacto, no se pasa, un solo tick cerca del piso), un pasajero con gravedad y colisión que falla
    en algunos ticks (con el pegado al suelo nunca sale por debajo; sin él, como en la V3.3.0, sí), el atraso del cliente y los bordes;
  * que la prueba MUERDE: se rompe a propósito ElevatorMotion (sin último paso de golpe; sin pegar al suelo) y la prueba tiene que fallar;
  * que la cabina use todo eso y en el orden correcto: el pegado al suelo corre también durante la espera de las puertas, después de mover la
    cabina y antes de convertirla en bloques; al llegar el último paso y arrive() van en el mismo tick; el jugador del servidor no se toca;
    el cliente termina de llevar al jugador cuando el servidor quita la cabina; MIN_STEP y RAMP ya no están duplicados en la cabina;
  * sintaxis del Java tocado (los tipos de Minecraft no se pueden comprobar sin Minecraft);
  * y corre lo de la V3.3.0 (tools/verify_v330.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v331.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
    return re.sub(r"\s+", " ", text)


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, env=env, cwd=cwd)


def body(text, signature_regex):
    """Cuerpo (entre llaves) del primer método cuya firma cumple la expresión."""
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
need(re.search(r"version = '3\.(3\.([1-9]\d*)|([4-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.3.1 o superior")
need(re.search(r"mod_version=3\.(3\.([1-9]\d*)|([4-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.3.1 o superior")
need("V3.3.1" in read(ROOT / "README.md"), "README sin la entrada V3.3.1")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.3.1" in nuevos and "PARCHE V3.3.0" in nuevos, "NUEVOS_BLOQUES debe conservar las secciones V3.3.1 y V3.3.0")

# ---------------------------------------------------------------- ElevatorMotion: Java puro
motion = read(JAVA / "elevator/ElevatorMotion.java")
need("net.minecraft" not in motion and "net.minecraftforge" not in motion and not re.search(r"^import ", motion, re.M),
     "ElevatorMotion debe ser Java puro (sin imports de Minecraft): así se prueba fuera del juego")
V332 = "travelledAt" in motion   # V3.3.2 cambió la curva de velocidad (en el tiempo); eso lo comprueba verify_v332.py
consts = (("ARRIVE_SNAP", None), ("CLIENT_CATCH_UP", None), ("STAND_TOLERANCE", None), ("ARRIVED", None)) if V332 else \
    (("MIN_STEP", "0.02"), ("RAMP", "0.35"), ("ARRIVE_SNAP", None), ("CLIENT_CATCH_UP", None), ("STAND_TOLERANCE", None), ("ARRIVED", None))
for const, value in consts:
    m = re.search(rf"public static final double {const} = ([\d.E-]+);", motion)
    need(m is not None, f"ElevatorMotion sin la constante {const}")
    if m and value:
        need(m.group(1) == value, f"{const} cambió de valor ({m.group(1)}; era {value}): el arranque y el frenado deben sentirse igual")
m = re.search(r"ARRIVE_SNAP = ([\d.]+);", motion)
need(m is not None and (0.02 if V332 else 0.05) <= float(m.group(1)) <= 0.25, "ARRIVE_SNAP debe ser un trozo corto: un salto mayor se nota")
m = re.search(r"CLIENT_CATCH_UP = ([\d.]+);", motion)
need(m is not None and 0.5 <= float(m.group(1)) <= 1.0, "CLIENT_CATCH_UP debe estar entre 0,5 y 1 bloque")
for fn in (("speedAt", "isLastStep", "stepFor", "standing", "liftedFeet") if V332 else ("speedFor", "isLastStep", "stepFor", "standing", "liftedFeet")):
    need(re.search(rf"public static \w+ {fn}\(", motion), f"ElevatorMotion sin el método {fn}")

# ---------------------------------------------------------------- la cabina usa todo eso, en el orden correcto
cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
need("import com.example.beautifuldecor.elevator.ElevatorMotion;" in cabin, "la cabina no importa ElevatorMotion")
need(not re.search(r"\bMIN_STEP\s*=", cabin) and not re.search(r"\bRAMP\s*=", cabin), "MIN_STEP y RAMP no deben estar duplicados en la cabina (viven en ElevatorMotion)")
need(not re.search(r"\bMIN_STEP\b|\bRAMP\b", cabin.replace("ElevatorMotion.MIN_STEP", "").replace("ElevatorMotion.RAMP", "")), "la cabina usa MIN_STEP o RAMP sin pasar por ElevatorMotion")
tick = body(cabin, r"public void tick\(\) \{") or ""
need(tick != "", "no se encontró ElevatorCabinEntity.tick")
t = squash(tick)
need("this.wait--; keepOnFloor(riders()); return;" in t, "durante la espera de las puertas la cabina debe pegar al suelo a quien va encima (keepOnFloor)")
need(("ElevatorMotion.isLastStep(this.speed, distance, travelled, this.moveTicks)" in t and "ElevatorMotion.stepFor(this.speed, distance, travelled, this.moveTicks)" in t) if V332
     else ("ElevatorMotion.isLastStep(remaining)" in t and "ElevatorMotion.stepFor(this.speed, travelled, remaining)" in t), "tick debe usar isLastStep y stepFor de ElevatorMotion")
i_move = t.find("moveBy(dy, riders);")
i_keep = t.find("keepOnFloor(riders);", i_move)
i_last = t.find("if (last) { arrive(); }")
need(0 < i_move < i_keep < i_last, "el orden del tick debe ser: moveBy, keepOnFloor, y al final (si es el último paso) arrive() en el mismo tick")
need(t.find("blockedAhead(dy)") != -1 and t.find("blockedAhead(dy)") < i_move, "blockedAhead debe mirarse con el paso real (dy) antes de mover")
need("Math.abs(remaining) < ElevatorMotion.ARRIVED" in t, "tick debe seguir llegando si ya está en el piso (ARRIVED)")
keep = body(cabin, r"private void keepOnFloor\(List<Entity> riders\) \{") or ""
k = squash(keep)
need(k != "", "no se encontró keepOnFloor")
need("rider instanceof ServerPlayer" in k and "continue;" in k, "keepOnFloor no debe tocar al jugador del servidor (su posición la manda su cliente)")
need("ElevatorMotion.standing(feet, bottom, top)" in k and "ElevatorMotion.liftedFeet(feet, bottom, top)" in k, "keepOnFloor debe usar standing y liftedFeet de ElevatorMotion")
need("rider.setOnGround(true)" in k and "rider.fallDistance = 0.0f" in k, "keepOnFloor debe dejar al pasajero 'en el suelo' y sin caída acumulada")
need(("rider.getX() < this.minX" in k and "rider.getZ() > this.minZ + this.sizeZ" in k)
     or ("!insideFootprint(rider, originX(), originZ())" in k and "rider.getX() >= ox && rider.getX() <= ox + this.sizeX" in squash(cabin)),
     "keepOnFloor solo debe tocar a quien está dentro de la planta de la cabina")
need("if (this.floorTop <= 0.0) { return; }" in k, "sin suelo (floorTop 0) keepOnFloor no debe hacer nada")
need("moveBy(dy, riders)" in t and re.search(r"private void moveBy\(double dy, List<Entity> riders\)", cabin), "moveBy debe recibir la lista de pasajeros (una sola búsqueda por tick)")
client = squash(body(cabin, r"public void onClientRemoval\(\) \{") or "")
need(client != "", "falta onClientRemoval (el cliente debe terminar de llevar al jugador cuando el servidor quita la cabina)")
need("this.level().isClientSide" in client and "ElevatorMotion.CLIENT_CATCH_UP" in client and "moveBy(rest, riders())" in client,
     "onClientRemoval debe llevar al jugador local lo que falta (solo en el cliente y si falta poco)")
need("@Override public void onClientRemoval" not in squash(cabin), "onClientRemoval va SIN @Override a propósito (si el gancho no existe, no rompe la compilación)")
arrive = squash(body(cabin, r"private void arrive\(\) \{") or "")
need("this.level().isClientSide" in arrive and "placeBlocks(base)" in arrive and ("this.discard()" in arrive or "land(base);" in arrive), "arrive() cambió de forma")
speed = squash(body(cabin, r"public float getSpeedFraction\(\) \{") or "")
need(("ElevatorMotion.speedAt(" if V332 else "ElevatorMotion.speedFor(") in speed, "getSpeedFraction debe usar la velocidad de ElevatorMotion (el zumbido sigue la misma velocidad)")

# ---------------------------------------------------------------- Java sin Minecraft
javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v331_"))
    try:
        # sintaxis del Java tocado (los tipos de Minecraft no se pueden comprobar sin Minecraft)
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / "entity/ElevatorCabinEntity.java", JAVA / "elevator/ElevatorMotion.java"]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = []
        for line in r.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(key in line for key in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype",
                                           "incompatible types", "cannot be applied", "is not abstract and does not override",
                                           "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
                                           "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        def run_test(motion_file, tag):
            outp = work / f"out_{tag}"
            outp.mkdir()
            r1 = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(motion_file),
                      str(ROOT / "tools/v331/java/ElevatorMotionTest.java")], env=env, cwd=work)
            if r1.returncode != 0 or "warning" in r1.stderr:
                return None, "no compila:\n" + r1.stderr[-2000:]
            r2 = run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", str(outp), "com.example.beautifuldecor.elevator.ElevatorMotionTest"],
                     env=env, cwd=work, timeout=600)
            return r2, (r2.stdout + r2.stderr)[-2500:]

        if V332:
            notes.append("la prueba de ElevatorMotion de la V3.3.1 usa la curva vieja: la reemplaza tools/v332 (verify_v332.py)")
            raise StopIteration
        # la prueba de verdad
        r, text = run_test(JAVA / "elevator/ElevatorMotion.java", "real")
        need(r is not None and r.returncode == 0 and "ELEVATOR MOTION OK" in r.stdout, "la prueba de ElevatorMotion falló:\n" + text)
        if r is not None and r.returncode == 0:
            for line in r.stdout.strip().splitlines():
                if line.startswith(("viajes:", "pasajeros:", "  fallo", "cliente atrasado", "ELEVATOR MOTION OK")):
                    notes.append(line.strip())

        # la prueba muerde: rota a propósito, tiene que fallar
        def mutant(tag, old, new):
            src = read(JAVA / "elevator/ElevatorMotion.java")
            if old not in src:
                errors.append(f"mutante {tag}: no se encontró el texto a cambiar")
                return
            d = work / f"mut_{tag}"
            (d / "com/example/beautifuldecor/elevator").mkdir(parents=True)
            f = d / "com/example/beautifuldecor/elevator/ElevatorMotion.java"
            f.write_text(src.replace(old, new, 1), encoding="utf-8")
            rm, textm = run_test(f, tag)
            need(rm is not None and rm.returncode != 0, f"la prueba NO se dio cuenta del mutante '{tag}' (debía fallar)")

        mutant("sin_ultimo_paso", "ARRIVE_SNAP = 0.12;", "ARRIVE_SNAP = 0.0;")
        mutant("sin_pegado", "return standing(feet, bottom, top) && feet < top ? top : feet;", "return feet;")
        mutant("pegado_a_destiempo", "feet >= bottom - STAND_TOLERANCE && feet <= top + STAND_TOLERANCE", "feet >= top + 1.0 && feet <= top + 2.0")
        mutant("catch_up_corto", "CLIENT_CATCH_UP = 1.0;", "CLIENT_CATCH_UP = 0.2;")
        mutant("llega_mal", "return remaining;\n        }\n        return Math.signum(remaining)", "return remaining * 0.5;\n        }\n        return Math.signum(remaining)")
        notes.append("la prueba falla con los 5 mutantes de ElevatorMotion (sin último paso, sin pegado, pegado a destiempo, tope corto, último paso a medias)")
    except StopIteration:
        pass
    finally:
        shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- limpieza del proyecto
for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

# ---------------------------------------------------------------- lo de la V3.3.0 (y, con él, lo de las anteriores)
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v330.py")], env=env, timeout=7200)
    need(r.returncode == 0 and "OK V3.3.0" in r.stdout, "verify_v330 falló:\n" + (r.stdout + r.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.3.1:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.3.1: ascensor — la cabina pega al suelo a quien va encima (también lenta y en la espera) y al llegar da el último paso y vuelve a ser bloques en el mismo tick.")
for n in notes:
    print("  (nota)", n)
