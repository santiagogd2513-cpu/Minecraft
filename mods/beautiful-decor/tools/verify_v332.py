#!/usr/bin/env python3
"""Comprueba la V3.3.2: ascensor mucho más fluido y realista.

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES, y el protocolo de red ("11");
  * ElevatorMotion (las cuentas del movimiento) es Java puro, sin nada de Minecraft, y se prueba de verdad (tools/v332/java/ElevatorMotionTest.java):
    858 viajes de 1 a 20 bloques por segundo (llega exacto, no se pasa, arranca desde cero, la aceleración nunca pasa de MAX_ACCEL, un solo tick cerca
    del piso), un pasajero con gravedad y colisión que falla en algunos ticks (suelos de 1, 1/2 y 1/16), el atraso del cliente y retomar viajes;
  * que la prueba MUERDE: se rompe a propósito ElevatorMotion (sin curva suave, sin arranque suave, sin rampa mínima, sin último paso, sin pegado, etc.)
    y la prueba tiene que fallar;
  * que la cabina use todo eso y en el orden correcto (curva en el tiempo con moveTicks, pegado al suelo, último paso + arrive() en el mismo tick),
    que cuando da la vuelta se lo avise al cliente (dato sincronizado) y que los datos de aparición y de guardado vayan en el mismo orden al escribir y al leer;
  * sintaxis del Java tocado (los tipos de Minecraft no se pueden comprobar sin Minecraft);
  * y corre lo de la V3.3.1 (tools/verify_v331.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v332.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
    text = re.sub(r"(?m)^\s*//.*$", "", text)          # comentarios de línea completa
    text = re.sub(r"(?m)([;{}])\s*//.*$", r"\1", text)   # comentarios al final de una línea de código
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
need(re.search(r"version = '3\.(3\.([2-9]|\d{2,})|([4-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.3.2 o superior")
need(re.search(r"mod_version=3\.(3\.([2-9]|\d{2,})|([4-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.3.2 o superior")
need("V3.3.2" in read(ROOT / "README.md"), "README sin la entrada V3.3.2")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.3.2" in nuevos and "PARCHE V3.3.1" in nuevos, "NUEVOS_BLOQUES debe conservar las secciones V3.3.2 y V3.3.1")
need(re.search(r'PROTOCOL = "(1[1-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")),
     "el protocolo de red debe ser 11 o más (los datos de aparición de la cabina cambiaron)")

# ---------------------------------------------------------------- ElevatorMotion: Java puro
motion = read(JAVA / "elevator/ElevatorMotion.java")
need("net.minecraft" not in motion and "net.minecraftforge" not in motion and not re.search(r"^import ", motion, re.M),
     "ElevatorMotion debe ser Java puro (sin imports de Minecraft): así se prueba fuera del juego")
limits = {"MAX_ACCEL": (0.005, 0.03), "ARRIVE_SNAP": (0.02, 0.12), "STAND_TOLERANCE": (0.0, 0.01), "STAND_BELOW": (0.1, 0.3),
          "CLIENT_CATCH_UP": (0.5, 1.0), "ARRIVED": (0.0, 0.001)}
for const, (lo, hi) in limits.items():
    m = re.search(rf"public static final double {const} = ([\d.E-]+);", motion)
    need(m is not None, f"ElevatorMotion sin la constante {const}")
    if m:
        need(lo <= float(m.group(1)) <= hi, f"{const} = {m.group(1)} fuera de lo razonable ({lo} a {hi})")
m = re.search(r"public static final int MIN_RAMP_TICKS = (\d+);", motion)
need(m is not None and 15 <= int(m.group(1)) <= 60, "MIN_RAMP_TICKS debe ser una rampa de entre 0,75 y 3 segundos (15 a 60 ticks)")
for fn in ("rampTicks", "peakSpeed", "travelledAt", "isLastStep", "stepFor", "speedAt", "ticksFor", "standing", "liftedFeet"):
    need(re.search(rf"public static \w+ {fn}\(", motion), f"ElevatorMotion sin el método {fn}")
need(not re.search(r"\bMIN_STEP\b|\bRAMP\b", motion), "ElevatorMotion no debe conservar la rampa vieja (MIN_STEP / RAMP)")

# ---------------------------------------------------------------- la cabina usa todo eso, en el orden correcto
cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
cs = squash(cabin)
need("import com.example.beautifuldecor.elevator.ElevatorMotion;" in cabin, "la cabina no importa ElevatorMotion")
need(not re.search(r"\bMIN_STEP\b|\bRAMP\b", cabin), "la cabina no debe usar la rampa vieja (MIN_STEP / RAMP)")
tick = body(cabin, r"public void tick\(\) \{") or ""
need(tick != "", "no se encontró ElevatorCabinEntity.tick")
t = squash(tick)
need("this.wait--; keepOnFloor(riders()); return;" in t, "durante la espera de las puertas la cabina debe pegar al suelo a quien va encima (keepOnFloor)")
need("ElevatorMotion.isLastStep(this.speed, distance, travelled, this.moveTicks)" in t and "ElevatorMotion.stepFor(this.speed, distance, travelled, this.moveTicks)" in t,
     "tick debe usar isLastStep y stepFor de ElevatorMotion con distance, travelled y moveTicks")
need("this.moveTicks++;" in t, "tick debe contar los ticks de viaje (moveTicks++)")
need("double distance = Math.abs(this.targetY - this.startY);" in t and ("double travelled = Math.abs(getY() - this.startY);" in t or "double travelled = Math.abs(axisPos() - this.startY);" in t),
     "tick debe calcular la distancia del viaje y lo recorrido con startY y targetY")
need("if (!this.resumed) { this.resumed = true; this.moveTicks = ElevatorMotion.ticksFor(this.speed, distance, travelled); }" in t,
     "tick debe retomar la curva en el primer tick (ticksFor), para viajes guardados y clientes que llegan a medias")
i_inc = t.find("this.moveTicks++;")
i_blocked = t.find("blockedAhead(dy)")
i_move = t.find("moveBy(dy, riders);")
i_keep = t.find("keepOnFloor(riders);", i_move)
i_last = t.find("if (last) { arrive(); }")
need(0 < i_inc < i_blocked < i_move < i_keep < i_last, "el orden del tick debe ser: moveTicks++, blockedAhead, moveBy, keepOnFloor, y al final (si es el último paso) arrive() en el mismo tick")
need("if (dy != 0.0) { moveBy(dy, riders); }" in t, "con paso 0 (esperando a la curva) no hace falta mover a nadie")
need("Math.abs(remaining) < ElevatorMotion.ARRIVED" in t, "tick debe seguir llegando si ya está en el piso (ARRIVED)")
need("ElevatorMotion.speedAt(this.speed, distance, this.moveTicks)" in squash(body(cabin, r"public float getSpeedFraction\(\) \{") or ""),
     "getSpeedFraction debe usar ElevatorMotion.speedAt (el zumbido sigue la misma curva)")
keep = squash(body(cabin, r"private void keepOnFloor\(List<Entity> riders\) \{") or "")
need(keep != "" and "rider instanceof ServerPlayer" in keep and "ElevatorMotion.standing(feet, bottom, top)" in keep and "ElevatorMotion.liftedFeet(feet, bottom, top)" in keep,
     "keepOnFloor cambió de forma (no debe tocar al jugador del servidor; usa standing y liftedFeet)")
arrive = squash(body(cabin, r"private void arrive\(\) \{") or "")
need("this.level().isClientSide" in arrive and "placeBlocks(base)" in arrive and ("this.discard()" in arrive or "land(base);" in arrive), "arrive() cambió de forma")
client = squash(body(cabin, r"public void onClientRemoval\(\) \{") or "")
need("ElevatorMotion.CLIENT_CATCH_UP" in client and "moveBy(rest, riders())" in client, "onClientRemoval debe seguir llevando al jugador local lo que le falta")

# la vuelta de la cabina se le avisa al cliente
need("EntityDataAccessor<Boolean> DATA_RETURNING" in cs and "SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.BOOLEAN)" in cs,
     "falta el dato sincronizado DATA_RETURNING")
need("this.entityData.define(DATA_RETURNING, false);" in squash(body(cabin, r"protected void defineSynchedData\(\) \{") or ""), "defineSynchedData debe definir DATA_RETURNING")
blocked = squash(body(cabin, r"private void handleBlocked\(\) \{") or "")
need("startReturn();" in blocked and "this.entityData.set(DATA_RETURNING, true);" in blocked, "handleBlocked debe dar la vuelta (startReturn) y avisar al cliente (DATA_RETURNING)")
ret = squash(body(cabin, r"private void startReturn\(\) \{") or "")
need("this.returning = true;" in ret and ("this.startY = getY();" in ret or "this.startY = axisPos();" in ret) and "this.targetY = this.homeY;" in ret and "this.moveTicks = 0;" in ret, "startReturn debe empezar un viaje nuevo hacia el piso de salida")
sync = squash(body(cabin, r"public void onSyncedDataUpdated\(EntityDataAccessor<\?> key\) \{") or "")
poll = squash(body(cabin, r"private void pollSync\(\) \{") or "")   # V3.3.3: la vuelta y la llegada se atienden en pollSync
need(("DATA_RETURNING.equals(key)" in sync and "this.level().isClientSide" in sync and "startReturn();" in sync and "!this.returning" in sync)
     or ("DATA_RETURNING.equals(key)" in sync and "pollSync();" in sync and "startReturn();" in poll and "!this.returning" in poll and "DATA_RETURNING" in poll),
     "onSyncedDataUpdated debe hacer que el cliente dé la vuelta (una sola vez)")
need("@Override public void onSyncedDataUpdated" not in cs and "@Override public void lerpTo" not in cs,
     "onSyncedDataUpdated y lerpTo van SIN @Override a propósito (si el gancho no coincidiera con la versión, no rompe la compilación)")
need("public void lerpTo(" in cs, "falta lerpTo (el cliente debe ignorar los avisos de posición del servidor)")

# los datos de aparición se escriben y se leen en el mismo orden
w = body(cabin, r"public void writeSpawnData\(FriendlyByteBuf buf\) \{") or ""
r = body(cabin, r"public void readSpawnData\(FriendlyByteBuf buf\) \{") or ""
wk = re.findall(r"buf\.write(Boolean|Int|VarInt|Double|Float)\(([^)]*)\)", w)
rk = re.findall(r"(?:this\.(\w+) = )?buf\.read(Boolean|Int|VarInt|Double|Float)\(\)", r)
w_seq = [(k, re.sub(r"this\.|\.length", "", a).strip()) for k, a in wk[:14]]
r_seq = [(k, name) for name, k in rk[:14]]
need([k for k, _ in w_seq[:14]][:14] == [k for k, _ in r_seq[:14]][:14],
     f"readSpawnData lee otros tipos que los que escribe writeSpawnData: {[k for k, _ in w_seq]} vs {[k for k, _ in r_seq]}")
for field in ("homeY", "returning"):
    need(re.search(rf"buf\.write\w+\(this\.{field}\)", w), f"writeSpawnData no escribe {field}")
    need(re.search(rf"this\.{field} = buf\.read\w+\(\)", r), f"readSpawnData no lee {field}")
need("this.homeY = this.startY;" not in r, "readSpawnData ya no debe suponer que el piso de salida es startY (viaja con la cabina)")
# el orden de homeY / returning respecto de targetY y speed es el mismo en los dos lados
def order(text, pattern_for):
    pos = {n: (re.search(pattern_for(n), text).start() if re.search(pattern_for(n), text) else -1) for n in ("targetY", "homeY", "returning", "speed")}
    return [n for n, _ in sorted(pos.items(), key=lambda kv: kv[1])]
need(order(w, lambda n: rf"buf\.write\w+\(this\.{n}\)") == order(r, lambda n: rf"this\.{n} = buf\.read\w+\(\)"),
     "writeSpawnData y readSpawnData no ponen targetY, homeY, returning y speed en el mismo orden")
need('tag.putInt("MoveTicks", this.moveTicks);' in cs and 'this.moveTicks = tag.getInt("MoveTicks");' in cs, "MoveTicks debe guardarse y leerse")
need("this.resumed = false;" in squash(body(cabin, r"protected void readAdditionalSaveData\(CompoundTag tag\) \{") or ""), "al cargar una cabina guardada, la curva debe retomarse (resumed = false)")

# ---------------------------------------------------------------- Java sin Minecraft
javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v332_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / "entity/ElevatorCabinEntity.java", JAVA / "elevator/ElevatorMotion.java", JAVA / "network/ModMessages.java"]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = []
        for line in r1.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(key in line for key in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype",
                                           "incompatible types", "cannot be applied", "is not abstract and does not override",
                                           "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
                                           "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference",
                                           "static import only from classes", "cannot be accessed")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        def run_test(motion_file, tag, limit=300):
            outp = work / f"out_{tag}"
            outp.mkdir()
            c = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(motion_file),
                     str(ROOT / "tools/v332/java/ElevatorMotionTest.java")], env=env, cwd=work)
            if c.returncode != 0 or "warning" in c.stderr:
                return None, "no compila:\n" + c.stderr[-2000:]
            try:
                res = run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", str(outp), "com.example.beautifuldecor.elevator.ElevatorMotionTest"],
                          env=env, cwd=work, timeout=limit)
            except subprocess.TimeoutExpired:
                return None, f"la prueba no termina en {limit} s"
            return res, (res.stdout + res.stderr)[-2500:]

        res, text = run_test(JAVA / "elevator/ElevatorMotion.java", "real")
        need(res is not None and res.returncode == 0 and "ELEVATOR MOTION OK" in res.stdout, "la prueba de ElevatorMotion falló:\n" + text)
        if res is not None and res.returncode == 0:
            for line in res.stdout.strip().splitlines():
                if line.startswith(("viajes:", "fluidez:", "  duración", "pasajeros:", "cliente atrasado", "retomar", "ELEVATOR MOTION OK")):
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

        mutant("curva_con_tiron", "return x2 * x2 * (x2 - 3.0 * x + 2.5);", "return x * x * 0.5;")
        mutant("arranque_de_golpe", "return peak * ramp * ease(ticks / ramp);", "return peak * ticks;")
        mutant("rampa_corta", "public static final int MIN_RAMP_TICKS = 20;", "public static final int MIN_RAMP_TICKS = 3;")
        mutant("aceleracion_brusca", "public static final double MAX_ACCEL = 0.015;", "public static final double MAX_ACCEL = 0.15;")
        mutant("sin_ultimo_paso", "ARRIVE_SNAP = 0.04;", "ARRIVE_SNAP = 0.0;")
        mutant("sin_pegado", "return standing(feet, bottom, top) && feet < top ? top : feet;", "return feet;")
        mutant("pegado_a_destiempo", "feet >= bottom - STAND_BELOW && feet <= top + STAND_TOLERANCE", "feet >= top + 1.0 && feet <= top + 2.0")
        mutant("suelo_fino_no", "STAND_BELOW = 0.2;", "STAND_BELOW = 0.0;")
        mutant("catch_up_corto", "CLIENT_CATCH_UP = 1.0;", "CLIENT_CATCH_UP = 0.2;")
        mutant("llega_mal", "        if (isLastStep(maxSpeed, distance, travelled, ticks)) {\n            return rest;", "        if (isLastStep(maxSpeed, distance, travelled, ticks)) {\n            return rest * 0.5;")
        mutant("se_pasa_del_piso", "return Math.max(0.0, Math.min(want - travelled, rest));", "return Math.max(0.0, want - travelled + 0.01);")
        mutant("no_retoma", "        return lo;\n    }", "        return 0;\n    }")
        notes.append("la prueba falla con los 12 mutantes de ElevatorMotion (curva con tirón, arranque de golpe, rampa corta, aceleración brusca, sin último paso, "
                     "sin pegado, pegado a destiempo, suelo fino, tope corto, llegada a medias, se pasa del piso, no retoma)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- limpieza del proyecto
for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

# ---------------------------------------------------------------- lo de la V3.3.1 (y, con él, lo de las anteriores)
if "--fast" not in sys.argv:
    rr = run([sys.executable, str(ROOT / "tools/verify_v331.py")], env=env, timeout=7200)
    need(rr.returncode == 0 and "OK V3.3.1" in rr.stdout, "verify_v331 falló:\n" + (rr.stdout + rr.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.3.2:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.3.2: ascensor — movimiento en curva S (arranca desde cero, acelera y frena suave), la cabina que da la vuelta se lo avisa al cliente, pegado al suelo también en suelos finos.")
for n in notes:
    print("  (nota)", n)
