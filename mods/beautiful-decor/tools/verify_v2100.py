#!/usr/bin/env python3
"""Comprueba la V2.10.0: el último paso del JavaScript en el navegador HS (paso 4 de 4): ancla de desplazamiento, medidas reales,
eventos scroll/resize, IntersectionObserver y ResizeObserver.

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que existan ScrollAnchor, Geometry, DomGeometry y DomObservers, que ya no queden las medidas falsas (0) ni los observadores
    de mentira de IntersectionObserver/ResizeObserver y que browser/ siga limpio;
  * que la pantalla use el ancla, publique la página acomodada y compile sin advertencias contra tools/v280/mcstub;
  * que browser/ y las pruebas compilen con --release 17 y -Xlint:all sin advertencias;
  * que tools/v280/runtests.py dé lo guardado (al menos 17 pruebas);
  * y corre lo de la V2.9.0 (tools/verify_v290.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v2100.py
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


def run(cmd, timeout=3400, env=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)


need(re.search(r"version = '(2\.(1\d|[2-9]\d)\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.10.0 o superior")
need(re.search(r"mod_version=(2\.(1\d|[2-9]\d)\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.10.0 o superior")
need("V2.10.0" in read(ROOT / "README.md"), "README sin la entrada V2.10.0")
need("PARCHE V2.10.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.10.0")

# ---------------------------------------------------------------- archivos
B = JAVA / "browser"
JS = B / "js"
for f in (B / "ScrollAnchor.java", B / "Geometry.java", JS / "DomGeometry.java", JS / "DomObservers.java"):
    need(f.exists(), f"{f.relative_to(JAVA)} no existe")
host = read(JS / "DomHost.java")
for token in ("int[] rect(Node n)", "Node nodeAt(int x, int y)", "int[] pageSize()", "void viewportChanged(boolean scrolled, boolean resized)",
              "DomGeometry.install(this)", "DomObservers.install(this)"):
    need(token in host, f"DomHost no tiene {token}")
need("rect(h, 0, 0, 0, 0)" not in read(JS / "DomNodes.java").split("static JsObject rect(")[0], "DomNodes todavía tiene medidas falsas")
els = read(JS / "DomElements.java")
need('"offsetWidth"' not in els, "DomElements todavía define offsetWidth falso")
win = read(JS / "DomWindow.java")
need('stubClass(h, "IntersectionObserver"' not in win and 'stubClass(h, "ResizeObserver"' not in win, "DomWindow todavía tiene observadores de mentira")
obs = read(JS / "DomObservers.java")
for token in ('"IntersectionObserver"', '"ResizeObserver"', "rootMargin", "threshold"):
    need(token in obs, f"DomObservers no define {token}")
geo = read(JS / "DomGeometry.java")
for token in ("getBoundingClientRect", "scrollIntoView", "elementFromPoint", "offsetParent", "scrollHeight", "scrollTop"):
    need(token in geo, f"DomGeometry no define {token}")
session = read(B / "BrowserSession.java")
for token in ("public void publishLayout(Page page)", "Geometry geometry()", "viewportChanged("):
    need(token in session, f"BrowserSession no tiene {token}")
rt = read(B / "PageRuntime.java")
for token in ("public void viewportChanged(", "public void layoutChanged(", "public int[] rect(", "public Node nodeAt(", "public int[] pageSize("):
    need(token.replace("public ", "") in rt or token in rt, f"PageRuntime no tiene {token}")
need("domVersion" in read(B / "Page.java"), "Page no guarda domVersion")
for f in sorted(B.rglob("*.java")):
    src = read(f)
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need("TRACE" not in src and "System.err.println" not in src, f"{f.name}: quedó código de depuración")
for t in ("t08_geometry", "t09_observers", "t10_observers_late"):
    need((ROOT / f"tools/v280/dom/{t}.html").exists(), f"falta tools/v280/dom/{t}.html")
    need((ROOT / f"tools/v280/expected/{t}.html.out").exists(), f"{t}: sin salida esperada guardada")
for t in ("s06_anchor", "s07_geo"):
    need((ROOT / f"tools/v280/{t}.sact").exists(), f"falta tools/v280/{t}.sact")
    need((ROOT / f"tools/v280/expected/{t}.sact.out").exists(), f"{t}: sin salida esperada guardada")

screen = read(JAVA / "client/HsBrowserScreen.java")
for token in ("ScrollAnchor", "publishLayout(", "stickUntil", "lastLayoutCost", "np.domVersion"):
    need(token in screen, f"HsBrowserScreen no tiene {token}")
need("removed() {" in screen and "setPaused(true)" in screen, "HsBrowserScreen debe seguir pausando la página al cerrarse")

javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if javac is None or java is None:
    notes.append("sin javac/java: no se compiló ni se probó nada")
else:
    out = Path(tempfile.mkdtemp(prefix="v2100_scr_"))
    try:
        stubs = [str(p) for p in (ROOT / "tools/v280/mcstub").rglob("*.java")]
        srcs = [str(p) for p in B.rglob("*.java")]
        real = [str(JAVA / "client/HsBrowserScreen.java"), str(JAVA / "client/DarkUi.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(out)] + stubs + srcs + real, env=env)
        need(r.returncode == 0, "no compila la pantalla del navegador contra las piezas mínimas de Minecraft:\n" + r.stderr[-2000:])
        need("warning" not in r.stderr, "la pantalla compila con advertencias:\n" + r.stderr[-1500:])
    finally:
        shutil.rmtree(out, ignore_errors=True)
    r = run([sys.executable, str(ROOT / "tools/v280/runtests.py")], timeout=1500, env=env)
    need(r.returncode == 0, "fallaron las pruebas de tools/v280:\n" + (r.stdout + r.stderr)[-2500:])
    oks = [l for l in r.stdout.splitlines() if l.startswith("ok ")]
    need(len(oks) >= 17, f"se esperaban al menos 17 pruebas y pasaron {len(oks)}")
    notes.append(f"{len(oks)} pruebas (DOM, medidas, observadores, ancla, temporizadores, red y sesión) coinciden con lo guardado")

# ---------------------------------------------------------------- lo de la V2.9.0
r = run([sys.executable, str(ROOT / "tools/verify_v290.py")], timeout=3400, env=env)
need(r.returncode == 0 and "OK V2.9" in r.stdout, "verify_v290 falló:\n" + (r.stdout + r.stderr)[-1500:])

if errors:
    print("FALLÓ la verificación V2.10.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.10.0: la página se acomoda sin saltos cuando un script la cambia; medidas reales (getBoundingClientRect, offset*, client*, scroll*, scrollIntoView, elementFromPoint); eventos scroll/resize; IntersectionObserver y ResizeObserver; compila sin advertencias.")
for n in notes:
    print("  (nota)", n)
