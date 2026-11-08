#!/usr/bin/env python3
"""Comprueba la V2.8.0: DOM, eventos, formularios y scripts en las páginas del navegador HS (paso 2 de 4).

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que existan los archivos del DOM (browser/js/Dom*.java, PageRuntime) y que browser/ siga limpio (sin Minecraft/LWJGL/Forge);
  * que la pantalla (HsBrowserScreen) tenga lo que conecta la página con el teclado, el ratón y los formularios, y que compile
    sin advertencias contra las piezas mínimas de Minecraft de tools/v280/mcstub;
  * que browser/ y las pruebas compilen con --release 17 y -Xlint:all sin advertencias;
  * que tools/v280/runtests.py (DOM, eventos, formularios, window y sesión contra un servidor local) dé las salidas guardadas;
  * y corre lo de la V2.7.0 (tools/verify_v270.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v280.py
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


need(re.search(r"version = '(2\.([8-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.8.0 o superior")
need(re.search(r"mod_version=(2\.([8-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.8.0 o superior")
need("V2.8.0" in read(ROOT / "README.md"), "README sin la entrada V2.8.0")
need("PARCHE V2.8.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.8.0")

# ---------------------------------------------------------------- archivos
JS = JAVA / "browser/js"
for name in ("DomHost", "DomObj", "DomNodes", "DomEvents", "DomForms", "DomStyle", "DomUrl", "DomWindow", "DomSelector", "DomDocument", "DomLists"):
    need((JS / f"{name}.java").exists(), f"browser/js/{name}.java no existe")
need((JAVA / "browser/PageRuntime.java").exists(), "browser/PageRuntime.java no existe")
for f in sorted((JAVA / "browser").rglob("*.java")):
    src = read(f)
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need("TRACE" not in src and "System.err.println" not in src, f"{f.name}: quedó código de depuración")
for t in ("tools/v280/runtests.py", "tools/v280/server.py", "tools/v280/java/DomRun.java", "tools/v280/java/SessionRun.java"):
    need((ROOT / t).exists(), f"falta {t}")
for h in sorted((ROOT / "tools/v280/dom").glob("*.html")) + sorted((ROOT / "tools/v280").glob("*.sact")):
    need((ROOT / "tools/v280/expected" / (h.name + ".out")).exists(), f"{h.name}: sin salida esperada guardada")

# ---------------------------------------------------------------- la pantalla
screen = read(JAVA / "client/HsBrowserScreen.java")
for token in ("PageRuntime", "routeKey(", "charTyped(", "keyReleased(", "drawEditing(", "openPopup(", "drawPopup(", "setViewport(",
              "takeScrollRequest(", "tryLock()", "GLFW_KEY_J", "nodeAt(", "chooseOption(", "blurFocus()", "toggleScripting()"):
    need(token in screen, f"HsBrowserScreen no tiene {token}")
need("r.tryLock()" in screen and "syncLocked()" in screen, "la pantalla debe tomar el bloqueo de la página antes de acomodarla")

javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if javac is None or java is None:
    notes.append("sin javac/java: no se compiló ni se probó nada")
else:
    out = Path(tempfile.mkdtemp(prefix="v280_scr_"))
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
    notes.append(f"{len(oks)} pruebas de DOM/sesión coinciden con lo guardado")

# ---------------------------------------------------------------- lo de la V2.7.0
r = run([sys.executable, str(ROOT / "tools/verify_v270.py")], timeout=3000, env=env)
need(r.returncode == 0 and "OK V2.7" in r.stdout, "verify_v270 falló:\n" + (r.stdout + r.stderr)[-1500:])

if errors:
    print("FALLÓ la verificación V2.8.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.8.0: las páginas ejecutan sus scripts (DOM, eventos, formularios, window, history, storage); la pantalla del navegador compila sin advertencias; pruebas de DOM y de sesión iguales a lo guardado.")
for n in notes:
    print("  (nota)", n)
