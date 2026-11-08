#!/usr/bin/env python3
"""Comprueba la V3.3.4: el jugador va pegado a la cabina (sin temblar al subir ni quedarse atrás al bajar).

  * versión, README y NUEVOS_BLOQUES;
  * ElevatorMotion sigue siendo Java puro y tiene RIDE_HEADROOM, TINY_MOVE_SQR, riderTop, riderBottom e isRider;
  * la prueba de pasajeros (tools/v334/java/ElevatorRiderTest.java): con la búsqueda vieja una plataforma de alto 1 tiembla y deja atrás al
    jugador; con la nueva, 0 de separación al final de cada tick en 1 920 viajes;
  * que la prueba MUERDE (mutantes);
  * que la cabina busque a los pasajeros con riderTop/riderBottom (también al llegar) y dé exactos los pasos diminutos;
  * sintaxis del Java tocado;
  * y corre lo de la V3.3.3 (tools/verify_v333.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v334.py [--fast]
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


need(re.search(r"version = '3\.(3\.([4-9]|\d{2,})|([4-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.3.4 o superior")
need(re.search(r"mod_version=3\.(3\.([4-9]|\d{2,})|([4-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.3.4 o superior")
need("V3.3.4" in read(ROOT / "README.md"), "README sin la entrada V3.3.4")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.3.4" in nuevos and "PARCHE V3.3.3" in nuevos, "NUEVOS_BLOQUES debe conservar las secciones V3.3.4 y V3.3.3")

motion = read(JAVA / "elevator/ElevatorMotion.java")
need("net.minecraft" not in motion and not re.search(r"^import ", motion, re.M), "ElevatorMotion debe seguir siendo Java puro")
m = re.search(r"public static final double RIDE_HEADROOM = ([\d.]+);", motion)
need(m is not None and 0.5 <= float(m.group(1)) <= 2.0, "RIDE_HEADROOM debe estar entre 0,5 y 2 bloques")
need("public static final double TINY_MOVE_SQR = 1.0E-7;" in motion, "TINY_MOVE_SQR debe ser el umbral de Entity.move (1.0E-7)")
for fn in ("riderTop", "riderBottom", "isRider"):
    need(re.search(rf"public static \w+ {fn}\(", motion), f"ElevatorMotion sin {fn}")

cabin = read(JAVA / "entity/ElevatorCabinEntity.java")
riders = squash(body(cabin, r"private List<Entity> riders\(\) \{") or "")
need("getY() + ElevatorMotion.riderBottom()" in riders and "getY() + ElevatorMotion.riderTop(this.sizeY, this.floorTop)" in riders,
     "riders() debe buscar desde riderBottom hasta riderTop (por encima de la cabina), no hasta el techo de la zona")
need("getY() + this.sizeY" not in riders, "riders() ya no debe terminar en el techo de la zona")
loc = squash(body(cabin, r"private void landOnClient\(int baseY\) \{") or "")
need("ElevatorMotion.riderTop(this.sizeY, this.floorTop)" in loc, "al llegar, la búsqueda de quien iba encima también debe llegar por encima de la cabina")
carry = squash(body(cabin, r"private void carry\(Entity rider, double dy\) \{") or "")
need("dy * dy <= ElevatorMotion.TINY_MOVE_SQR" in carry and "rider.setPos(rider.getX(), rider.getY() + dy, rider.getZ());" in carry and "rider.move(MoverType.SHULKER" in carry,
     "carry debe dar exactos (setPos) los pasos diminutos y con Entity.move los demás")

javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v334_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / "entity/ElevatorCabinEntity.java", JAVA / "elevator/ElevatorMotion.java"]
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
                     str(ROOT / "tools/v334/java/ElevatorRiderTest.java")], env=env, cwd=work)
            if c.returncode != 0 or "warning" in c.stderr:
                return None, "no compila:\n" + c.stderr[-2000:]
            try:
                res = run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", str(outp), "com.example.beautifuldecor.elevator.ElevatorRiderTest"],
                          env=env, cwd=work, timeout=limit)
            except subprocess.TimeoutExpired:
                return None, f"la prueba no termina en {limit} s"
            return res, (res.stdout + res.stderr)[-2500:]

        res, text = run_test(JAVA / "elevator/ElevatorMotion.java", "real")
        need(res is not None and res.returncode == 0 and "ELEVATOR RIDERS OK" in res.stdout, "la prueba de pasajeros falló:\n" + text)
        if res is not None and res.returncode == 0:
            for line in res.stdout.strip().splitlines():
                if line.startswith(("pasajeros:", "  antes", "  el caso", "ELEVATOR RIDERS OK")):
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

        mutant("busqueda_hasta_el_techo", "return Math.max(sizeY, floorTop) + RIDE_HEADROOM;", "return sizeY;")
        mutant("sin_margen", "public static final double RIDE_HEADROOM = 1.0;", "public static final double RIDE_HEADROOM = 0.0;")
        mutant("arrastra_lo_de_abajo", "    public static double riderBottom() {\n        return 0.0;", "    public static double riderBottom() {\n        return -3.0;")
        notes.append("la prueba de pasajeros falla con los 3 mutantes (búsqueda hasta el techo, sin margen, arrastra lo de abajo)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if "--fast" not in sys.argv:
    rr = run([sys.executable, str(ROOT / "tools/verify_v333.py")], env=env, timeout=7200)
    need(rr.returncode == 0 and "OK V3.3.3" in rr.stdout, "verify_v333 falló:\n" + (rr.stdout + rr.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.3.4:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.3.4: ascensor — quien está de pie sobre la cabina (también sobre una plataforma de alto 1) es pasajero: no tiembla al subir ni se queda atrás al bajar.")
for n in notes:
    print("  (nota)", n)
