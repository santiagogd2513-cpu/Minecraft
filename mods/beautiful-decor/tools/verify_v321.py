#!/usr/bin/env python3
"""Comprueba la V3.2.1: menú del controlador de ascensor rediseñado (plataforma en vivo, tamaño a la derecha, lado/velocidad/nombre a la izquierda).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES, idiomas es/en (mismas claves, mismos %s en las dos lenguas, todas las claves que usa el menú);
  * la cuadrícula (ElevatorPlatformView) y la distribución (ElevatorMenuLayout) en Java, sin Minecraft: qué se ve desde arriba y por capas, cuentas,
    lectura "en tiempo real", capas, geometría, dónde cae la marca del controlador con la fórmula REAL de ElevatorZone (piezas de mentira de BlockPos y
    Direction), que ninguna pieza se pise ni se salga del panel en 13 tamaños de pantalla, y que el tamaño quede a la DERECHA y el lado, la velocidad y el
    nombre a la IZQUIERDA;
  * que cada texto de los idiomas quepa en su recuadro (con una tabla de anchos de la letra normal de Minecraft);
  * que el menú nuevo esté conectado (lee el mundo, usa ElevatorCabinEntity.isMovable para saber qué se mueve, no cambia la red: PROTOCOL sigue en "9");
  * que el Java nuevo no tenga errores de sintaxis;
  * y corre lo de la V3.2.0 (tools/verify_v320.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v321.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
sys.path.insert(0, str(ROOT))
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
C = JAVA / "client"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
errors = []
notes = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env, cwd=cwd)


# ---------------------------------------------------------------- versión y documentos
need(re.search(r"version = '3\.(2\.([1-9]\d*)|([3-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.2.1 o superior")
need(re.search(r"mod_version=3\.(2\.([1-9]\d*)|([3-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.2.1 o superior")
need("V3.2.1" in read(ROOT / "README.md"), "README sin la entrada V3.2.1")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.2.1" in nuevos and "PARCHE V3.2.0" in nuevos, "NUEVOS_BLOQUES debe empezar con una sección V3.x y conservar la V3.2.1 y la V3.2.0")

# ---------------------------------------------------------------- idiomas
for f in sorted(ASSETS.rglob("*.json")):
    try:
        json.loads(read(f))
    except Exception as exc:  # noqa: BLE001
        errors.append(f"JSON inválido: {f.relative_to(ROOT)}: {exc}")
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))
P = "gui.beautifuldecor.elevator."
screen_src = read(C / "ElevatorControllerScreen.java")
used = {k for k in re.findall(r'"(gui\.beautifuldecor\.elevator\.[a-z_.]+)"', screen_src) if not k.endswith(".")}
used |= {P + "side." + d for d in ("north", "east", "south", "west")}
for k in sorted(used):
    need(k in es and k in en, f"el menú usa la clave {k} y no está en es/en")
# cuántos %s lleva cada texto con argumentos (el menú los llama con esa cantidad)
ARGS = {"zone": 3, "zone_cells": 1, "speed_value": 1, "axis": 1, "platform.layer": 3, "platform.layer_short": 2,
        "platform.counts": 1, "platform.stays": 1, "platform.top": 0, "platform.hint": 0, "platform.empty": 0,
        "platform.travelling": 0, "platform": 0, "size": 0, "ctrl": 0, "name_hint": 0, "side": 0, "name": 0}
for k, n in ARGS.items():
    for lang, d in (("es", es), ("en", en)):
        got = len(re.findall(r"%s", d.get(P + k, "")))
        need(got == n, f"{P + k} ({lang}) lleva {got} %s y el menú le pasa {n}")
need("bloques" in es[P + "platform.counts"] and "blocks" in en[P + "platform.counts"], "texto de la cuenta de bloques")
for old in ("(vacío = Piso", "(empty = Floor"):
    need(old not in es[P + "name"] and old not in en[P + "name"], "el texto de nombre del piso es largo: la ayuda va en name_hint")
gen = read(ROOT / "tools/v220/elevator.py")
need("Cabin side" in gen and "Lado de la cabina" in gen, "tools/v220/elevator.py (generador de idiomas) debe tener los textos nuevos de lado/nombre/zona, o los pisaría")

# ---------------------------------------------------------------- Java sin Minecraft
javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}


def glyph(ch):
    """Ancho (con su 1 px de separación) de cada letra de la fuente normal de Minecraft."""
    table = {" ": 4, "!": 2, '"': 5, "'": 3, "(": 5, ")": 5, "*": 5, ",": 2, ".": 2, ":": 2, ";": 2, "<": 5, ">": 5, "@": 7, "I": 4, "[": 4, "]": 4,
             "`": 3, "f": 5, "i": 2, "k": 5, "l": 3, "t": 4, "{": 5, "}": 5, "|": 2, "~": 7, "í": 3, "·": 2}
    return table.get(ch, 6)


def width(text):
    return sum(glyph(ch) for ch in text) - 1


if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v321_"))
    try:
        # sintaxis del Java nuevo (los tipos de Minecraft no se pueden comprobar sin Minecraft)
        out = work / "syntax"
        out.mkdir()
        files = [C / "ElevatorControllerScreen.java", C / "ElevatorPlatformView.java", C / "ElevatorMenuLayout.java"]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = []
        for line in r.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(k in line for k in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype",
                                       "incompatible types", "cannot be applied", "is not abstract and does not override",
                                       "cannot infer", "bad operand", "not a functional interface")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        # pruebas de la cuadrícula, la distribución y la marca del controlador
        outp = work / "out"
        outp.mkdir()
        srcs = [str(p) for p in (ROOT / "tools/v321/teststub").rglob("*.java")]
        srcs += [str(JAVA / "elevator/ElevatorZone.java"), str(C / "ElevatorPlatformView.java"), str(C / "ElevatorMenuLayout.java"),
                 str(ROOT / "tools/v321/java/ElevatorMenuTest.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp)] + srcs, env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba del menú:\n" + r.stderr[-2000:])
        if r.returncode == 0:
            cp = str(outp)
            r = run([java, "-cp", cp, "com.example.beautifuldecor.client.ElevatorMenuTest"], env=env, cwd=work, timeout=300)
            need(r.returncode == 0 and "ELEVATOR MENU OK" in r.stdout, "la prueba del menú falló:\n" + (r.stdout + r.stderr)[-2500:])
            notes.append("cuadrícula y menú (Java): " + (r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "?"))

            # que cada texto quepa en su recuadro
            r = run([java, "-cp", cp, "com.example.beautifuldecor.client.ElevatorMenuTest", "dump"], env=env, cwd=work, timeout=120)
            rects = {}
            for line in r.stdout.splitlines():
                if line.startswith("RECT "):
                    _, w, name, x, y, ww, hh = line.split()
                    rects[(int(w), name)] = (int(x), int(y), int(ww), int(hh))
            need(len(rects) > 40, "no se pudo leer la distribución del menú")
            for lang, d in (("es", es), ("en", en)):
                def t(k, *a):
                    return d[P + k] % tuple(a) if a else d[P + k]

                def fits(name, text, w=460, pad=0, extra=0):
                    rect = rects.get((w, name))
                    if rect is None:
                        errors.append(f"sin recuadro {name} para el ancho {w}")
                        return
                    need(width(text) + extra + pad <= rect[2], f"[{lang}] '{text}' ({width(text)} px) no cabe en {name} ({rect[2]} px) con panel de {w}")

                for w in (460, 419):
                    fits("headingSide", t("side"), w)
                    fits("labelName", t("name"), w)
                    fits("nameBox", t("name_hint"), w, pad=8)
                    fits("headingSize", t("size"), w)
                    fits("headingPlatform", t("platform"), w)
                    fits("labelSpeed", t("speed"), w, extra=width(t("speed_value", "20.0")) + 6)
                    for key in ("side.north", "side.west", "side.east", "side.south"):
                        fits({"side.north": "sideN", "side.west": "sideW", "side.east": "sideE", "side.south": "sideS"}[key], t(key), w, pad=6)
                    fits("ctrl", t("ctrl"), w, pad=6)
                    for b in range(4):
                        fits(f"speed{b}", ["-5", "-0.5", "+0.5", "+5"][b], w, pad=6)
                    for i, wk in enumerate(("width", "depth", "height")):
                        for axis in ("X", "Y", "Z"):
                            fits(f"sizeLabel{i}", t(wk) + " (" + t("axis", axis) + ")", w, extra=width("50") + 8)
                    fits("zoneDims", t("zone", 50, 50, 24), w)
                    fits("zoneCells", t("zone_cells", 60000), w)
                    fits("layerLabel", t("platform.top"), w, pad=6)
                    fits("layerLabel", t("platform.layer_short", 24, 24), w, pad=6)
                    fits("save", t("save"), w, pad=6)
                    fits("close", t("close"), w, pad=6)
                # en el ancho de siempre (460) todo lo del centro cabe sin recortar
                fits("layerLabel", t("platform.layer", 24, 24, -64), 460, pad=6)
                fits("counts", t("platform.counts", 60000), 460)
                fits("hover", t("platform.stays", 60000), 460)
                fits("hover", t("platform.hint"), 460)
                fits("grid", t("platform.travelling"), 460, pad=12)
                fits("grid", t("platform.empty"), 460, pad=12)
            notes.append("todos los textos (es y en) caben en su recuadro con paneles de 460 y de 419 píxeles de ancho")
    finally:
        shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- que esté conectado (sin red nueva)
need("ElevatorPlatformView." in screen_src and "ElevatorMenuLayout" in screen_src and "DarkUi." in screen_src, "el menú no usa la cuadrícula, la distribución y DarkUi")
need("ElevatorCabinEntity.isMovable(" in screen_src, "el menú debe usar ElevatorCabinEntity.isMovable para saber qué bloques se mueven (así la cuadrícula es lo que de verdad se lleva la cabina)")
need("getMapColor(" in screen_src and "getBlockState(" in screen_src, "el menú debe leer el mundo (getBlockState) y el color de cada bloque (getMapColor)")
need("isPauseScreen" in screen_src and "public void tick()" in screen_src and "mouseScrolled(" in screen_src, "el menú debe declarar isPauseScreen, tick y mouseScrolled")
need("SCAN_EVERY" in screen_src and re.search(r"ticks % SCAN_EVERY", screen_src), "el menú debe volver a leer el mundo cada pocos ticks (tiempo real)")
need("plainSubstrByWidth" in screen_src, "los textos del menú deben recortarse si no caben")
need(re.search(r"mouseScrolled\(double \w+, double \w+, double \w+\)", screen_src), "mouseScrolled debe tener la forma de 1.20.1 (3 números)")
need(re.search(r"new ElevatorSavePacket\(this\.pos, this\.facing, this\.cabWidth, this\.cabDepth,\s*this\.cabHeight,[^;]*this\.speed, this\.nameBox\.getValue\(\)\)", screen_src),
     "el paquete de guardar debe llevar lo mismo y en el mismo orden que antes (V3.3.0 agrega el desplazamiento entre el alto y la velocidad)")
save = read(JAVA / "network/ElevatorSavePacket.java")
need(re.search(r"ElevatorSavePacket\(BlockPos \w+,\s*Direction \w+,\s*int \w+,\s*int \w+,\s*int \w+,\s*(int \w+,\s*int \w+,\s*int \w+,\s*)?float \w+,\s*String \w+\)", save),
     "el constructor de ElevatorSavePacket cambió: el menú ya no le pasa lo que pide")
shaft = read(JAVA / "elevator/ElevatorShaft.java")
need(re.search(r"public static ElevatorCabinEntity travellingCabin\(Level \w+, BlockPos \w+\)", shaft), "ElevatorShaft.travellingCabin cambió de forma")
cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
need(re.search(r"public static boolean isMovable\(BlockState \w+\)", cabin), "ElevatorCabinEntity.isMovable cambió de forma")
need(re.search(r'PROTOCOL\s*=\s*"(9|\d{2,})"', read(JAVA / "network/ModMessages.java")),
     "el protocolo de red debe ser 9 o más (la V3.2.1 no cambió la red; la V3.3.0 lo subió a 10)")
# el orden de la brújula del menú tiene que coincidir con el de la distribución: norte, oeste, este, sur
need(re.search(r"SIDE_BY_SLOT\s*=\s*\{Direction\.NORTH, Direction\.WEST, Direction\.EAST, Direction\.SOUTH\}", screen_src),
     "SIDE_BY_SLOT debe ir en el orden norte, oeste, este, sur (el de ElevatorMenuLayout.SIDE_*)")
lay_src = read(C / "ElevatorMenuLayout.java")
need(re.search(r"SIDE_N = 0;\s*static final int SIDE_W = 1;\s*static final int SIDE_E = 2;\s*static final int SIDE_S = 3;", lay_src),
     "los índices SIDE_N/W/E/S de ElevatorMenuLayout cambiaron")

# ---------------------------------------------------------------- limpieza del proyecto
for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

# ---------------------------------------------------------------- lo de la V3.2.0 (y, con él, lo de las anteriores)
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v320.py")], env=env, timeout=7200)
    need(r.returncode == 0 and "OK V3.2.0" in r.stdout, "verify_v320 falló:\n" + (r.stdout + r.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.2.1:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.2.1: menú del controlador de ascensor rediseñado: plataforma en vivo (bloques reales de la zona, vista desde arriba o por capas), "
      "tamaño a la derecha, lado/velocidad/nombre a la izquierda; sin cambios de red; idiomas y textos completos.")
for n in notes:
    print("  (nota)", n)
