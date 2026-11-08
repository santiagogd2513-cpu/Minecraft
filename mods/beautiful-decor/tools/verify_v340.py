#!/usr/bin/env python3
"""Comprueba la V3.4.0: el ascensor también va de lado (líneas de controladores a la misma altura).

  * versión, README, NUEVOS_BLOQUES, idiomas y protocolo ("13");
  * ElevatorLines es Java puro y se prueba de verdad (tools/v340/java/ElevatorLinesTest.java) junto con ElevatorMotion: líneas, capas en
    cualquier eje (igual que la cuenta vertical de antes; cada columna del camino revisada una vez y en orden), llevar de lado, atascos;
  * que la prueba MUERDE (mutantes);
  * que todo esté conectado: ElevatorShaft (líneas, conexiones, red, viaje en cualquier eje, sin inventar cabinas), la cabina (eje de viaje en
    obstáculos, llegada, atasco, bloques, guardado y aparición; al jugador del servidor no se lo mueve de lado), la pantalla y su paquete, la llamada;
  * sintaxis del Java tocado;
  * y corre lo de la V3.3.4 (tools/verify_v334.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v340.py [--fast]
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


# ---------------------------------------------------------------- versión, documentos, idiomas, red
need(re.search(r"version = '3\.([4-9]|\d{2,})\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 3.4.0 o superior")
need(re.search(r"mod_version=3\.([4-9]|\d{2,})\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.4.0 o superior")
need("V3.4.0" in read(ROOT / "README.md"), "README sin la entrada V3.4.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.4.0" in nuevos and "PARCHE V3.3.4" in nuevos, "NUEVOS_BLOQUES debe conservar las secciones V3.4.0 y V3.3.4")
need(re.search(r'PROTOCOL = "(1[3-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 13 o más (pantalla y aparición de la cabina cambiaron)")
en = json.loads(read(ASSETS / "lang/en_us.json"))
es = json.loads(read(ASSETS / "lang/es_es.json"))
for key in ("message.beautifuldecor.elevator.mismatch", "message.beautifuldecor.elevator.blocked_side", "message.beautifuldecor.elevator.not_straight",
            "gui.beautifuldecor.elevator.dir.east", "gui.beautifuldecor.elevator.dir.west", "gui.beautifuldecor.elevator.dir.north",
            "gui.beautifuldecor.elevator.dir.south", "gui.beautifuldecor.elevator.stops"):
    need(key in en and key in es, f"falta la clave de idioma {key}")
for key in ("gui.beautifuldecor.elevator.dir.east", "gui.beautifuldecor.elevator.stops"):
    need("%s" in en.get(key, "") and "%s" in es.get(key, ""), f"{key} debe llevar %s")

# ---------------------------------------------------------------- ElevatorLines (puro)
lines = read(JAVA / "elevator/ElevatorLines.java")
need("net.minecraft" not in lines and not re.search(r"^import ", lines, re.M), "ElevatorLines debe ser Java puro")
for fn in ("lineAxis", "inRange", "leadingSlice", "stuckBase", "directionKey", "along"):
    need(re.search(rf"public static \w+ {fn}\(", lines), f"ElevatorLines sin {fn}")
m = re.search(r"public static final int ROW_RANGE = (\d+);", lines)
need(m is not None and 16 <= int(m.group(1)) <= 256, "ROW_RANGE debe estar entre 16 y 256 bloques")
zone = read(JAVA / "elevator/ElevatorZone.java")
need("public ElevatorZone shifted(int dx, int dy, int dz)" in zone and "public int minAlong(int axis)" in zone, "ElevatorZone sin shifted/minAlong")

# ---------------------------------------------------------------- ElevatorShaft
shaft = read(JAVA / "elevator/ElevatorShaft.java")
ss = squash(shaft)
for sig in (r"public static List<BlockPos> row\(Level level, BlockPos any, int axis\)", r"public static boolean linked\(Level level, BlockPos a, BlockPos b\)",
            r"public static List<Stop> stops\(Level level, BlockPos own\)", r"public static Set<BlockPos> network\(Level level, BlockPos start\)",
            r"public static boolean sameCabin\(ElevatorControllerBlockEntity a, ElevatorControllerBlockEntity b\)",
            r"public static void requestTrip\(ServerLevel level, BlockPos targetPos, @Nullable BlockPos callerPos, @Nullable ServerPlayer who\)",
            r"public static void requestTrip\(ServerLevel level, BlockPos targetPos, @Nullable ServerPlayer who\)"):
    need(re.search(sig, shaft), f"ElevatorShaft sin {sig}")
row = squash(body(shaft, r"public static List<BlockPos> row\(Level level, BlockPos any, int axis\) \{") or "")
need("if (!level.isLoaded(p)) { continue; }" in row, "row() no debe cargar chunks (solo mira los cargados)")
sweep = squash(body(shaft, r"private static boolean sweepIsClear\(") or "")
need("if (!level.isLoaded(p)) { return false; }" in sweep and "inAny(skip, x, y, z)" in sweep, "sweepIsClear: no cargar chunks y saltar las zonas de la cabina")
need("int yMax = Math.max(a.minY(), b.minY()) + Math.max(1, height) - 1;" in sweep, "sweepIsClear: revisa 'height' capas desde la base")
need("ElevatorLines.sweepHeight(axis, ca.zone().sizeY())" in shaft and "ElevatorLines.sweepHeight(axis, from.sizeY())" in shaft,
     "las líneas y los viajes de lado deben dejar sitio a quien va de pie encima (sweepHeight)")
need("ElevatorLines.sweepHeight(this.axis, this.sizeY)" in read(JAVA / "entity/ElevatorCabinEntity.java"),
     "la cabina, al ir de lado, debe revisar también el sitio de quien va de pie encima")
heli = read(JAVA / "entity/HelicopterEntity.java")
need("public static final float MAX_SPEED = 3.4f;" in heli, "el helicóptero debe ir a 68 bloques por segundo (MAX_SPEED = 3.4f bloques por tick)")
need("? 0.07 : 0.05;" in heli, "el helicóptero debe acelerar un poco más para llegar a 68 b/s")
trip = squash(body(shaft, r"public static void requestTrip\(ServerLevel level, BlockPos targetPos, @Nullable BlockPos callerPos, @Nullable ServerPlayer who\) \{") or "")
for piece in ("nearestCabin(level, targetPos, target, line(level, targetPos, shared))", "cabinByBlocks(level, target, line(level, targetPos, shared), shared)",
              "networkHasCabin(level, targetPos)", "message.beautifuldecor.elevator.not_straight", "cabinHeadingNear(level, targetPos)",
              "!sameCabin(source, target)", "from.shifted(targetPos.getX() - sourcePos.getX(), targetPos.getY() - sourcePos.getY(), targetPos.getZ() - sourcePos.getZ())",
              "ElevatorCabinEntity.capture(level, from, axis, to.minAlong(axis), source.getSpeed(), sourcePos, targetPos, DEPARTURE_WAIT)",
              "pathIsClear(level, from, to, axis)", "message.beautifuldecor.elevator.blocked_side"):
    need(piece in trip, f"requestTrip sin: {piece}")
i_heading = trip.find("cabinHeadingNear(level, targetPos)")
i_net = trip.find("networkHasCabin(level, targetPos)")
i_here = trip.find("target.setCabinHereSilently(true);")
need(0 < i_heading < i_net < i_here, "antes de dar por buena la parada pedida (sin cabina en ningún lado) hay que descartar una cabina en viaje y una cabina en otra línea de la red")
apply_ = squash(body(shaft, r"public static void applySettings\(") or "")
need("Set<BlockPos> group = network(level, pos);" in apply_ and "for (BlockPos other : group)" in apply_, "applySettings debe repartir los ajustes a toda la red conectada")
join = squash(body(shaft, r"public static void joinColumn\(ServerLevel level, BlockPos newPos\) \{") or "")
need("row(level, newPos, axis)" in join, "un controlador nuevo sin columna debe unirse a su línea de lado")
need("cabin.matchesStop(any)" in ss, "travellingCabin debe usar matchesStop (columna o línea de lado)")

# ---------------------------------------------------------------- la cabina
cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
cs = squash(cabin)
need("private int axis = ElevatorLines.AXIS_Y;" in cs, "la cabina sin eje de viaje")
for fn in ("private double axisPos()", "private void setAxisPos(double value)", "private double originX()", "private double originZ()",
           "public boolean matchesStop(BlockPos any)", "private void carrySide(Entity rider, double d)", "private void placeBlocksAt(int ox, int baseY, int oz)"):
    need(fn in cs, f"la cabina sin {fn}")
need("setCabinY" not in cabin, "ya no debe quedar setCabinY (todo va por setAxisPos)")
tick = squash(body(cabin, r"public void tick\(\) \{") or "")
need("double remaining = this.targetY - axisPos();" in tick and "double travelled = Math.abs(axisPos() - this.startY);" in tick, "tick debe medir en el eje de viaje")
move = squash(body(cabin, r"private void moveBy\(double dy, List<Entity> riders\) \{") or "")
need("if (this.axis != ElevatorLines.AXIS_Y) { setAxisPos(axisPos() + dy); for (Entity rider : riders) { carrySide(rider, dy); } return; }" in move,
     "moveBy de lado: el suelo y luego los pasajeros")
side = squash(body(cabin, r"private void carrySide\(Entity rider, double d\) \{") or "")
need("if (rider instanceof ServerPlayer) { return; }" in side and "ElevatorMotion.TINY_MOVE_SQR" in side, "carrySide: no mover al jugador del servidor; pasos diminutos exactos")
blocked = squash(body(cabin, r"private boolean blockedAhead\(double dy\) \{") or "")
need("ElevatorLines.leadingSlice(pos, pos + dy, size)" in blocked and "if (!this.level().isLoaded(p)) { return true; }" in blocked, "blockedAhead en cualquier eje (sin cargar = bloqueado)")
place = squash(body(cabin, r"private void placeBlocks\(int base\) \{") or "")
need("placeBlocksAt(ox, oy, oz);" in place, "placeBlocks debe poner los bloques en el eje de viaje")
loc = squash(body(cabin, r"private void landOnClient\(int baseY\) \{") or "")
need("setAxisPos(baseY);" in loc and "insideFootprint(rider, ox, oz)" in loc and "double finalTop = getY() + this.floorTop;" in loc, "landOnClient en cualquier eje")
need('tag.putInt("Axis", this.axis);' in cs and 'tag.contains("Axis")' in cs, "el eje debe guardarse (y las cabinas viejas cargar en vertical)")
need("buf.writeByte(this.axis);" in cs and "int readAxis = buf.readByte();" in cs, "el eje debe ir en los datos de aparición")
w = cs.find("buf.writeDouble(this.floorTop); buf.writeByte(this.axis);")
r = cs.find("this.floorTop = buf.readDouble(); int readAxis = buf.readByte();")
need(w > 0 and r > 0, "el eje va justo después de floorTop, al escribir y al leer")

# ---------------------------------------------------------------- pantalla, paquete y llamada
disp = squash(read(JAVA / "block/ElevatorDisplayBlock.java"))
need("ElevatorShaft.stops(level, controller)" in disp and "stop.axis()" in disp, "la pantalla debe listar las paradas (columna y lados)")
pk = squash(read(JAVA / "network/ElevatorDisplayPacket.java"))
need("public record FloorEntry(BlockPos pos, String name, boolean cabinHere, int axis)" in pk and "buf.writeByte(floor.axis());" in pk
     and "buf.readBoolean(), buf.readByte())" in pk, "el paquete de la pantalla debe llevar el eje de cada parada")
scr = squash(read(JAVA / "client/ElevatorDisplayScreen.java"))
need("private int floorAt(int row) { return row; }" in scr and "where(floor)" in scr and "gui.beautifuldecor.elevator.dir." in scr, "la pantalla debe mostrar la lista en orden y la dirección de las paradas de lado")
call = squash(read(JAVA / "network/ElevatorCallPacket.java"))
need("ElevatorShaft.lineAxis(controller, packet.target)" in call and "ElevatorLines.inRange(" in call and "ElevatorShaft.requestTrip(level, packet.target, controller, player)" in call,
     "la llamada debe aceptar destinos en una línea del controlador de la pantalla (y cerca)")

# ---------------------------------------------------------------- Java
javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v340_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / "entity/ElevatorCabinEntity.java", JAVA / "elevator/ElevatorShaft.java", JAVA / "elevator/ElevatorLines.java", JAVA / "elevator/ElevatorZone.java",
                 JAVA / "elevator/ElevatorMotion.java", JAVA / "network/ElevatorDisplayPacket.java", JAVA / "network/ElevatorCallPacket.java",
                 JAVA / "network/ModMessages.java", JAVA / "block/ElevatorDisplayBlock.java", JAVA / "client/ElevatorDisplayScreen.java"]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed"))]
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        def run_test(lines_file, tag, limit=300):
            outp = work / f"out_{tag}"
            outp.mkdir()
            c = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(lines_file), str(JAVA / "elevator/ElevatorMotion.java"),
                     str(ROOT / "tools/v340/java/ElevatorLinesTest.java")], env=env, cwd=work)
            if c.returncode != 0 or "warning" in c.stderr:
                return None, "no compila:\n" + c.stderr[-2000:]
            try:
                res = run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", str(outp), "com.example.beautifuldecor.elevator.ElevatorLinesTest"],
                          env=env, cwd=work, timeout=limit)
            except subprocess.TimeoutExpired:
                return None, f"la prueba no termina en {limit} s"
            return res, (res.stdout + res.stderr)[-2500:]

        res, text = run_test(JAVA / "elevator/ElevatorLines.java", "real")
        need(res is not None and res.returncode == 0 and "ELEVATOR LINES OK" in res.stdout, "la prueba de líneas falló:\n" + text)
        if res is not None and res.returncode == 0:
            for line in res.stdout.strip().splitlines():
                if line.startswith(("capas:", "llevar de lado:", "ELEVATOR LINES OK")):
                    notes.append(line.strip())

        def mutant(tag, old, new):
            src = read(JAVA / "elevator/ElevatorLines.java")
            if src.count(old) != 1:
                errors.append(f"mutante {tag}: el texto a cambiar no aparece exactamente una vez")
                return
            d = work / f"mut_{tag}"
            (d / "com/example/beautifuldecor/elevator").mkdir(parents=True)
            f = d / "com/example/beautifuldecor/elevator/ElevatorLines.java"
            f.write_text(src.replace(old, new, 1), encoding="utf-8")
            rm, _ = run_test(f, tag)
            need(rm is not None and rm.returncode != 0, f"la prueba NO se dio cuenta del mutante '{tag}' (debía fallar)")

        mutant("diagonal_cuenta", "        if (ay == by && ax == bx) {\n            return AXIS_Z;\n        }\n        return NO_LINE;",
               "        if (ay == by && ax == bx) {\n            return AXIS_Z;\n        }\n        return ay == by ? AXIS_X : NO_LINE;")
        mutant("capa_corrida", "            int next = (int) Math.ceil(newMin + size - 1.0E-6) - 1;", "            int next = (int) Math.ceil(newMin + size - 1.0E-6);")
        mutant("capa_hacia_atras", "            int next = (int) Math.floor(newMin + 1.0E-6);", "            int next = (int) Math.floor(newMin + 1.0E-6) - 1;")
        mutant("atasco_adelante", "        return dir > 0 ? (int) Math.floor(pos + 1.0E-6) : (int) Math.ceil(pos - 1.0E-6);",
               "        return dir > 0 ? (int) Math.ceil(pos - 1.0E-6) : (int) Math.floor(pos + 1.0E-6);")
        mutant("este_oeste_al_reves", '            return to > from ? "east" : "west";', '            return to > from ? "west" : "east";')
        mutant("sin_sitio_para_el_jugador", "public static final int SIDE_CLEARANCE = 3;", "public static final int SIDE_CLEARANCE = 1;")
        notes.append("la prueba de líneas falla con los 6 mutantes (diagonal, capa corrida, capa hacia atrás, atasco adelante, este/oeste al revés, sin sitio para el jugador)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if "--fast" not in sys.argv:
    rr = run([sys.executable, str(ROOT / "tools/verify_v334.py")], env=env, timeout=7200)
    need(rr.returncode == 0 and "OK V3.3.4" in rr.stdout, "verify_v334 falló:\n" + (rr.stdout + rr.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.4.0:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.4.0: ascensor de lado — líneas de controladores a la misma altura (X o Z), viaje recto en cualquier eje con la misma curva, pasajeros y llegada sin hueco.")
for n in notes:
    print("  (nota)", n)
