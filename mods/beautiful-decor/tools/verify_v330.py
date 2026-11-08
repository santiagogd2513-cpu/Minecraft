#!/usr/bin/env python3
"""Comprueba la V3.3.0: el controlador de ascensor puede correr la zona de la cabina en X, Y y Z (menú, guardado, red y servidor).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES, idiomas es/en (mismas claves, textos que caben en su recuadro);
  * los PAQUETES REALES (ElevatorSavePacket y ElevatorControllerPacket) compilados con piezas de mentira de FriendlyByteBuf (mismos VarInt y bytes con signo
    que Minecraft): lo que se escribe es lo que se lee, con el desplazamiento incluso negativo y en los extremos (-64 y 64), no sobran ni faltan bytes,
    lo que llega al servidor llama a ElevatorShaft.applySettings con los mismos datos (y un jugador lejos o una zona sin cargar no cambia nada) y lo que
    llega al cliente abre el menú con ellos; además se revisa en el código que la firma de la pieza de mentira de ElevatorShaft sea IGUAL a la de verdad;
  * la zona con desplazamiento con la fórmula REAL de ElevatorZone y la marca del controlador en la cuadrícula, y la distribución del menú (tres filas
    X, Y, Z a la izquierda, el tamaño a la derecha; nada se pisa) — eso lo corre tools/verify_v321.py a través de su prueba de Java;
  * que todo esté conectado: bloque entidad (límite, NBT, copia entre pisos, zona), ElevatorShaft (reparte el desplazamiento a toda la columna), la
    cabina (se reconoce por la columna de su controlador), el bloque del controlador, el menú, el gancho del cliente y que ninguna cuenta de la zona
    se haya quedado sin desplazamiento; protocolo de red 10 o más;
  * y corre lo de la V3.2.1 (tools/verify_v321.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v330.py [--fast]     (--fast se salta lo de las versiones anteriores)
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


def squash(text):
    """El código sin saltos de línea ni espacios repetidos (para comparar llamadas partidas en varias líneas)."""
    return re.sub(r"\s+", " ", text)


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env, cwd=cwd)


def body(text, signature_regex):
    """Cuerpo (entre llaves) del primer método cuya firma cumple la expresión."""
    m = re.search(signature_regex, text)
    if not m:
        return None
    i = text.index("{", m.end() - 1) if text[m.end() - 1] != "{" else m.end() - 1
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[i + 1:j]
    return None


def call_args(text, start):
    """Argumentos (separados por coma en el primer nivel) de la llamada cuyo paréntesis de apertura está en text[start]."""
    depth = 0
    args = []
    cur = []
    for j in range(start, len(text)):
        ch = text[j]
        if ch == "(":
            depth += 1
            if depth == 1:
                continue
        elif ch == ")":
            depth -= 1
            if depth == 0:
                args.append("".join(cur).strip())
                return args
        elif ch == "," and depth == 1:
            args.append("".join(cur).strip())
            cur = []
            continue
        cur.append(ch)
    return args


# ---------------------------------------------------------------- versión y documentos
need(re.search(r"version = '3\.(3\.\d+|([4-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.3.0 o superior")
need(re.search(r"mod_version=3\.(3\.\d+|([4-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.3.0 o superior")
need("V3.3.0" in read(ROOT / "README.md"), "README sin la entrada V3.3.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.3.0" in nuevos and "PARCHE V3.2.1" in nuevos, "NUEVOS_BLOQUES debe empezar con una sección V3.x y conservar la V3.3.0 y la V3.2.1")

# ---------------------------------------------------------------- idiomas
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))
P = "gui.beautifuldecor.elevator."
screen_src = read(C / "ElevatorControllerScreen.java")
for k in ("shift", "shift_reset"):
    need(P + k in es and P + k in en, f"falta la clave {P + k} en es/en")
used = {k for k in re.findall(r'"(gui\.beautifuldecor\.elevator\.[a-z_.]+)"', screen_src) if not k.endswith(".")}
for k in sorted(used):
    need(k in es and k in en, f"el menú usa la clave {k} y no está en es/en")
    need(len(re.findall(r"%s", es.get(k, ""))) == len(re.findall(r"%s", en.get(k, ""))), f"{k}: es y en llevan distinta cantidad de %s")

# ---------------------------------------------------------------- los paquetes: mismo orden al escribir y al leer, y mismo orden en todas las llamadas
pk_save = read(JAVA / "network/ElevatorSavePacket.java")
pk_ctrl = read(JAVA / "network/ElevatorControllerPacket.java")
for name, src in (("ElevatorSavePacket", pk_save), ("ElevatorControllerPacket", pk_ctrl)):
    enc = body(src, r"public void encode\(FriendlyByteBuf \w+\) \{")
    dec = body(src, r"public static \w+ decode\(FriendlyByteBuf \w+\) \{")
    need(enc is not None and dec is not None, f"{name}: no se encontró encode o decode")
    if enc is None or dec is None:
        continue
    writes = [m.group(1) for m in re.finditer(r"buf\.write(\w+)\(", enc)]
    reads = [m.group(1) for m in re.finditer(r"buf\.read(\w+)\(", dec)]
    need(writes == reads, f"{name}: encode escribe {writes} y decode lee {reads}")
    fields = re.findall(r"buf\.write\w+\(this\.(\w+)", enc)
    local = re.findall(r"(?:[\w<>]+ )?(\w+) = [^;=]*buf\.read\w+\(", dec)
    need(fields == local, f"{name}: los datos se escriben en el orden {fields} y se leen en el orden {local}")
    ctor = re.search(rf"public {name}\(([^)]*)\)", src, re.S)
    params = [p.strip().split()[-1] for p in ctor.group(1).split(",")] if ctor else []
    need(params == fields, f"{name}: el constructor recibe {params} y encode escribe {fields}")
    m = re.search(rf"new {name}\(", dec)
    args = call_args(dec, m.end() - 1) if m else []
    need(args == params, f"{name}: decode arma el paquete con {args} y el constructor espera {params}")
    need(len(re.findall(r"readByte\(\)", dec)) >= 4 and "offX" in params and "offY" in params and "offZ" in params, f"{name} debe llevar offX, offY y offZ")
    need(re.findall(r"buf\.writeByte\(this\.off[XYZ]\)", enc) == ["buf.writeByte(this.offX)", "buf.writeByte(this.offY)", "buf.writeByte(this.offZ)"],
         f"{name}: el desplazamiento debe escribirse como X, Y, Z, un byte cada uno")
need(re.search(r"packet\.offX, packet\.offY, packet\.offZ, packet\.speed", squash(pk_save)), "ElevatorSavePacket.handle debe pasar el desplazamiento a applySettings (offX, offY, offZ, speed)")

# llamadas con el mismo orden en todos lados
ctrl_block = squash(read(JAVA / "block/ElevatorControllerBlock.java"))
need("new ElevatorControllerPacket(pos, facing, be.getWidth(), be.getDepth(), be.getHeight(), be.getOffsetX(), be.getOffsetY(), be.getOffsetZ(), be.getSpeed(), be.getFloorName(), menu)" in ctrl_block,
     "ElevatorControllerBlock debe mandar el desplazamiento al abrir el menú, en el orden del constructor")
need("be.getHeight(), be.getOffsetX(), be.getOffsetY(), be.getOffsetZ(), be.getSpeed(), be.getFloorName(), null)" in ctrl_block,
     "ElevatorControllerBlock debe pasar el desplazamiento al cambiar de lado con un clic (applySettings)")
screen_sq = squash(screen_src)
need("new ElevatorSavePacket(this.pos, this.facing, this.cabWidth, this.cabDepth, this.cabHeight, this.shift[0], this.shift[1], this.shift[2], this.speed, this.nameBox.getValue())" in screen_sq,
     "el menú debe guardar con el desplazamiento X, Y, Z en el orden del constructor")
hooks = squash(read(C / "ClientHooks.java"))
need("ElevatorZone.of(packet.pos, packet.facing, packet.width, packet.depth, packet.height, packet.offX, packet.offY, packet.offZ)" in hooks,
     "ClientHooks debe marcar la zona con el desplazamiento")

# ninguna cuenta de la zona se queda sin desplazamiento
for f in sorted(JAVA.rglob("*.java")):
    if f.name == "ElevatorZone.java":
        continue
    text = read(f)
    for m in re.finditer(r"ElevatorZone\.of\(", text):
        args = call_args(text, m.end() - 1)
        need(len(args) == 8, f"{f.relative_to(ROOT)}: ElevatorZone.of se llama con {len(args)} argumentos (debe llevar el desplazamiento: 8)")
zone_src = read(JAVA / "elevator/ElevatorZone.java")
need(re.search(r"public static ElevatorZone of\(BlockPos controller, Direction facing, int width, int depth, int height,\s*int offX, int offY, int offZ\)", zone_src),
     "ElevatorZone.of con desplazamiento no tiene la forma esperada")
need("minX + offX" in zone_src and "controller.getY() - 1 + offY" in zone_src and "minZ + offZ" in zone_src, "ElevatorZone.of debe sumar el desplazamiento a x, y y z")

# ---------------------------------------------------------------- bloque entidad, columna, cabina
be = read(JAVA / "block/entity/ElevatorControllerBlockEntity.java")
need("MAX_OFFSET = 64" in be, "ElevatorControllerBlockEntity.MAX_OFFSET debe ser 64")
set_off = body(be, r"public void setOffset\(int x, int y, int z\) \{") or ""
need(len(re.findall(r"Mth\.clamp\(\w+, -MAX_OFFSET, MAX_OFFSET\)", set_off)) == 3, "setOffset debe limitar los tres ejes a ±MAX_OFFSET")
for key in ("OffX", "OffY", "OffZ"):
    need(f'tag.putInt("{key}"' in be and f'tag.getInt("{key}")' in be, f"el controlador debe guardar y leer {key}")
need(re.search(r"Mth\.clamp\(tag\.getInt\(\"OffX\"\), -MAX_OFFSET, MAX_OFFSET\)", be), "al cargar, el desplazamiento se limita a ±MAX_OFFSET (un guardado raro no rompe nada)")
need("setOffset(other.offX, other.offY, other.offZ)" in be, "copyShapeFrom debe copiar también el desplazamiento (un piso nuevo hereda la zona del ascensor)")
need("this.width, this.depth, this.height, this.offX, this.offY, this.offZ)" in squash(be), "ElevatorControllerBlockEntity.zone() debe usar el desplazamiento")
shaft = read(JAVA / "elevator/ElevatorShaft.java")
apply_body = body(shaft, r"public static void applySettings\(ServerLevel level[^{]*\{") or ""
need("be.setOffset(offX, offY, offZ);" in apply_body and apply_body.index("be.setOffset(offX, offY, offZ);") > apply_body.index("be.setShape("),
     "applySettings debe repartir el desplazamiento a todos los controladores de la columna")
m = re.search(r"CABIN_SEARCH = (\d+)(?: \+ ElevatorLines\.ROW_RANGE)?;", shaft)   # V3.4.0: + el largo de las líneas de lado
need(m is not None and int(m.group(1)) >= 100, "la búsqueda de la cabina en viaje debe cubrir una zona corrida hasta 64 bloques y de hasta 50 de ancho (CABIN_SEARCH >= 100)")
need("CABIN_SEARCH" in (body(shaft, r"public static ElevatorCabinEntity travellingCabin\(Level \w+, BlockPos \w+\) \{") or ""), "travellingCabin debe usar CABIN_SEARCH")
cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
mc = body(cabin, r"public boolean matchesColumn\(int x, int z\) \{") or ""
need("sourceCtrl.getX()" in mc and "destCtrl.getX()" in mc and "this.minX - 1" in mc, "matchesColumn debe reconocer la columna de sus controladores y conservar la regla vieja")
# la firma de applySettings de la pieza de mentira es igual a la de verdad
real = re.search(r"public static void applySettings\(([^)]*)\)", shaft, re.S).group(1)
stub = re.search(r"public static void applySettings\(([^)]*)\)", read(ROOT / "tools/v330/teststub/com/example/beautifuldecor/elevator/ElevatorShaft.java"), re.S).group(1)
need(squash(real).replace("@Nullable ", "") == squash(stub).replace("@Nullable ", ""), "la firma de applySettings de la pieza de mentira no es igual a la de verdad")
# la pantalla usa el límite del bloque entidad
need("MAX_OFFSET" in screen_src and "this.shift[axis] = 0;" in screen_src, "el menú debe limitar a MAX_OFFSET y volver a 0 con un clic en el número")
need(re.search(r'PROTOCOL\s*=\s*"(10|1[1-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 10 o más (los paquetes del ascensor cambiaron)")

# ---------------------------------------------------------------- Java sin Minecraft
javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}


def glyph(ch):
    table = {" ": 4, "!": 2, '"': 5, "'": 3, "(": 5, ")": 5, "*": 5, ",": 2, ".": 2, ":": 2, ";": 2, "<": 5, ">": 5, "@": 7, "I": 4, "[": 4, "]": 4,
             "`": 3, "f": 5, "i": 2, "k": 5, "l": 3, "t": 4, "{": 5, "}": 5, "|": 2, "~": 7, "í": 3, "·": 2}
    return table.get(ch, 6)


def width(text):
    return sum(glyph(ch) for ch in text) - 1


if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v330_"))
    try:
        # sintaxis del Java tocado (los tipos de Minecraft no se pueden comprobar sin Minecraft)
        out = work / "syntax"
        out.mkdir()
        files = [C / "ElevatorControllerScreen.java", C / "ElevatorMenuLayout.java", C / "ElevatorPlatformView.java", C / "ClientHooks.java",
                 JAVA / "elevator/ElevatorZone.java", JAVA / "elevator/ElevatorShaft.java", JAVA / "block/entity/ElevatorControllerBlockEntity.java",
                 JAVA / "block/ElevatorControllerBlock.java", JAVA / "entity/ElevatorCabinEntity.java",
                 JAVA / "network/ElevatorSavePacket.java", JAVA / "network/ElevatorControllerPacket.java", JAVA / "network/ModMessages.java"]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = []
        for line in r.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(k in line for k in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype",
                                       "incompatible types", "cannot be applied", "is not abstract and does not override",
                                       "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
                                       "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        # los paquetes reales, de ida y vuelta
        outp = work / "out"
        outp.mkdir()
        srcs = [str(p) for d in ("tools/v321/teststub", "tools/v330/teststub", "tools/v320/teststub/javax") for p in (ROOT / d).rglob("*.java")]
        srcs += [str(JAVA / "network/ElevatorSavePacket.java"), str(JAVA / "network/ElevatorControllerPacket.java"), str(ROOT / "tools/v330/java/ElevatorPacketTest.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp)] + srcs, env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba de los paquetes:\n" + r.stderr[-2500:])
        if r.returncode == 0:
            r = run([java, "-cp", str(outp), "com.example.beautifuldecor.network.ElevatorPacketTest"], env=env, cwd=work, timeout=300)
            need(r.returncode == 0 and "ELEVATOR PACKETS OK" in r.stdout, "la prueba de los paquetes falló:\n" + (r.stdout + r.stderr)[-2500:])
            notes.append("paquetes del ascensor (reales): " + (r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "?"))

        # los textos nuevos caben en su recuadro
        out2 = work / "layout"
        out2.mkdir()
        srcs = [str(p) for p in (ROOT / "tools/v321/teststub").rglob("*.java")]
        srcs += [str(JAVA / "elevator/ElevatorZone.java"), str(C / "ElevatorPlatformView.java"), str(C / "ElevatorMenuLayout.java"), str(ROOT / "tools/v321/java/ElevatorMenuTest.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-d", str(out2)] + srcs, env=env, cwd=work)
        if r.returncode == 0:
            r = run([java, "-cp", str(out2), "com.example.beautifuldecor.client.ElevatorMenuTest", "dump"], env=env, cwd=work, timeout=120)
            rects = {}
            for line in r.stdout.splitlines():
                if line.startswith("RECT "):
                    _, w, name, x, y, ww, hh = line.split()
                    rects[(int(w), name)] = (int(x), int(y), int(ww), int(hh))
            for lang, d in (("es", es), ("en", en)):
                def fits(name, text, w=460, pad=0, extra=0):
                    rect = rects.get((w, name))
                    if rect is None:
                        errors.append(f"sin recuadro {name} para el ancho {w}")
                        return
                    need(width(text) + extra + pad <= rect[2], f"[{lang}] '{text}' ({width(text)} px) no cabe en {name} ({rect[2]} px) con panel de {w}")
                for w in (460, 419):
                    fits("labelShift", d[P + "shift"], w)
                    for i, axis in enumerate("XYZ"):
                        fits(f"shiftAxis{i}", axis, w)
                        for value in ("-64", "+64", "+12", "0"):
                            fits(f"shiftValue{i}", value, w, pad=6)
                        for k, label in enumerate(("-5", "-1", "+1", "+5")):
                            fits(f"shift{i}_{k}", label, w, pad=6)
                fits("hover", d[P + "shift_reset"], 460)
            notes.append("los textos nuevos (es y en) caben en su recuadro")
    finally:
        shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- limpieza del proyecto
for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

# ---------------------------------------------------------------- lo de la V3.2.1 (y, con él, lo de las anteriores)
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v321.py")], env=env, timeout=7200)
    need(r.returncode == 0 and "OK V3.2.1" in r.stdout, "verify_v321 falló:\n" + (r.stdout + r.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.3.0:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.3.0: el controlador de ascensor corre la zona de la cabina en X, Y y Z: menú (tres filas a la izquierda), guardado (NBT), paquetes (protocolo 10) y servidor "
      "(se reparte a toda la columna; la cabina en viaje se reconoce por su columna); mundos de antes con 0, 0, 0.")
for n in notes:
    print("  (nota)", n)
