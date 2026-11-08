#!/usr/bin/env python3
"""Pruebas del DOM y de la sesión del navegador (V2.8.0). Sin Minecraft: usa DomRun y SessionRun contra un servidor local.

Uso: runtests.py [--update] [directorio_de_clases]
  * Sin directorio de clases compila browser/ y las pruebas en un directorio temporal.
  * --update guarda las salidas actuales en tools/v280/expected/ (como esperadas).
Devuelve 0 si todo coincide.
"""
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

here = Path(__file__).resolve().parent
root = here.parent.parent
java_src = root / "src/main/java/com/example/beautifuldecor/browser"
expected = here / "expected"
args = [a for a in sys.argv[1:] if a != "--update"]
update = "--update" in sys.argv[1:]
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
env["TZ"] = "America/Bogota"
PORT = 8765

tmp = Path(tempfile.mkdtemp(prefix="v280_"))
classes = Path(args[0]) if args else tmp / "classes"
if not args:
    classes.mkdir()
    srcs = [str(p) for p in java_src.rglob("*.java")] + [str(p) for p in (here / "java").glob("*.java")]
    r = subprocess.run(["javac", "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(classes)] + srcs,
                       capture_output=True, text=True, env=env)
    if r.returncode != 0 or "warning" in r.stderr:
        print("NO COMPILA:\n" + r.stderr[-2000:])
        sys.exit(1)

# servidor local de prueba
server = subprocess.Popen([sys.executable, str(here / "server.py"), str(PORT)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(50):
    try:
        socket.create_connection(("127.0.0.1", PORT), timeout=0.2).close()
        break
    except OSError:
        time.sleep(0.1)

jobs = []
for html in sorted((here / "dom").glob("*.html")):
    act = html.with_suffix(".act")
    cmd = ["java", "-cp", str(classes), "DomRun", str(html)] + ([str(act)] if act.exists() else [])
    jobs.append((html.name, cmd))
for sact in sorted(here.glob("*.sact")):
    jobs.append((sact.name, ["java", "-cp", str(classes), "SessionRun", str(sact)]))

expected.mkdir(exist_ok=True)
bad = 0
try:
    for name, cmd in jobs:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=120, env=env, cwd=str(here))
        out = r.stdout.replace(str(here), "<tests>").rstrip("\n")
        path = expected / (name + ".out")
        if update:
            path.write_text(out + "\n", encoding="utf-8")
            print("guardado", path.name)
            continue
        want = path.read_text(encoding="utf-8").rstrip("\n") if path.exists() else None
        if want is None:
            print("FALTA", path.name)
            bad += 1
        elif want != out:
            bad += 1
            a, b = want.split("\n"), out.split("\n")
            print("DIFERENTE", name)
            shown = 0
            for i in range(max(len(a), len(b))):
                x = a[i] if i < len(a) else "<nada>"
                y = b[i] if i < len(b) else "<nada>"
                if x != y and shown < 4:
                    print("  línea", i + 1, "\n    esperada:", x, "\n    obtenida:", y)
                    shown += 1
        else:
            print("ok", name)
        if "FAIL" in out:
            print("  (la salida de", name, "tiene FAIL)")
            bad += 1
finally:
    server.terminate()
    shutil.rmtree(tmp, ignore_errors=True)
sys.exit(1 if bad else 0)
