#!/usr/bin/env python3
"""Comprueba la V2.9.0: temporizadores y red (fetch, XMLHttpRequest, AbortController) en las páginas del navegador HS (paso 3 de 4).

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que existan DomTimers y DomNet, que ya no queden los temporizadores falsos de la V2.8.0 y que browser/ siga limpio;
  * que la pantalla avise de la pausa (setPaused) y compile sin advertencias contra tools/v280/mcstub;
  * que browser/ y las pruebas compilen con --release 17 y -Xlint:all sin advertencias;
  * que tools/v280/runtests.py (temporizadores, fetch, XMLHttpRequest, DOM y sesión contra un servidor local) dé lo guardado;
  * y corre lo de la V2.8.0 (tools/verify_v280.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v290.py
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


def run(cmd, timeout=1800, env=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)


need(re.search(r"version = '(2\.([9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.9.0 o superior")
need(re.search(r"mod_version=(2\.([9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.9.0 o superior")
need("V2.9.0" in read(ROOT / "README.md"), "README sin la entrada V2.9.0")
need("PARCHE V2.9.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.9.0")

# ---------------------------------------------------------------- archivos
JS = JAVA / "browser/js"
for name in ("DomTimers", "DomNet"):
    need((JS / f"{name}.java").exists(), f"browser/js/{name}.java no existe")
win = read(JS / "DomWindow.java")
need("para que los scripts no se rompan" not in win and "queueTask" not in win, "DomWindow todavía tiene los temporizadores falsos")
host = read(JS / "DomHost.java")
for token in ("boolean schedule(", "Runnable fetch(", "NetResult fetchSync(", "class NetRequest", "class NetResult"):
    need(token in host, f"DomHost no tiene {token}")
net = read(JS / "DomNet.java")
for token in ('"fetch"', '"XMLHttpRequest"', '"AbortController"', '"Headers"', '"Request"', '"Response"'):
    need(token in net, f"DomNet no define {token}")
rt = read(JAVA / "browser/PageRuntime.java")
for token in ("public boolean schedule(", "public Runnable fetch(", "public void pause("):
    need(token in rt, f"PageRuntime no tiene {token}")
for f in sorted((JAVA / "browser").rglob("*.java")):
    src = read(f)
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need("TRACE" not in src and "System.err.println" not in src, f"{f.name}: quedó código de depuración")
for t in ("t05_timers", "t06_fetch", "t07_xhr"):
    need((ROOT / f"tools/v280/dom/{t}.html").exists(), f"falta tools/v280/dom/{t}.html")
    need((ROOT / f"tools/v280/expected/{t}.html.out").exists(), f"{t}: sin salida esperada guardada")
for t in ("s03", "s04", "s05"):
    need((ROOT / f"tools/v280/{t}.sact").exists(), f"falta tools/v280/{t}.sact")
    need((ROOT / f"tools/v280/expected/{t}.sact.out").exists(), f"{t}: sin salida esperada guardada")

screen = read(JAVA / "client/HsBrowserScreen.java")
need("setPaused(false)" in screen and "setPaused(true)" in screen, "HsBrowserScreen debe pausar los temporizadores al cerrarse y reanudarlos al abrirse")

javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if javac is None or java is None:
    notes.append("sin javac/java: no se compiló ni se probó nada")
else:
    out = Path(tempfile.mkdtemp(prefix="v290_scr_"))
    try:
        stubs = [str(p) for p in (ROOT / "tools/v280/mcstub").rglob("*.java")]
        srcs = [str(p) for p in (JAVA / "browser").rglob("*.java")]
        real = [str(JAVA / "client/HsBrowserScreen.java"), str(JAVA / "client/DarkUi.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(out)] + stubs + srcs + real, env=env)
        need(r.returncode == 0, "no compila la pantalla del navegador contra las piezas mínimas de Minecraft:\n" + r.stderr[-2000:])
        need("warning" not in r.stderr, "la pantalla compila con advertencias:\n" + r.stderr[-1500:])
    finally:
        shutil.rmtree(out, ignore_errors=True)
    r = run([sys.executable, str(ROOT / "tools/v280/runtests.py")], timeout=900, env=env)
    need(r.returncode == 0, "fallaron las pruebas de tools/v280:\n" + (r.stdout + r.stderr)[-2500:])
    oks = [l for l in r.stdout.splitlines() if l.startswith("ok ")]
    need(len(oks) >= 12, f"se esperaban al menos 12 pruebas y pasaron {len(oks)}")
    notes.append(f"{len(oks)} pruebas (DOM, temporizadores, fetch, XHR y sesión) coinciden con lo guardado")

# ---------------------------------------------------------------- lo de la V2.8.0
r = run([sys.executable, str(ROOT / "tools/verify_v280.py")], timeout=3400, env=env)
need(r.returncode == 0 and "OK V2.8" in r.stdout, "verify_v280 falló:\n" + (r.stdout + r.stderr)[-1500:])

if errors:
    print("FALLÓ la verificación V2.9.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.9.0: setTimeout/setInterval/requestAnimationFrame/requestIdleCallback, fetch, XMLHttpRequest, AbortController, Headers/Request/Response; los temporizadores se pausan con el navegador cerrado; compila sin advertencias.")
for n in notes:
    print("  (nota)", n)
