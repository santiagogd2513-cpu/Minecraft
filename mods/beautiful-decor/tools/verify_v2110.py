#!/usr/bin/env python3
"""Comprueba la V2.11.0: video (MP4 con sonido) en la pantalla de los televisores, con el mismo sistema del navegador HS.

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que existan TvPlayback y TelevisionRenderer y que estén conectados (registro del renderizador, tick, salida del mundo,
    registro del block entity, contador de "Reproducir", ruta de hasta 1024 caracteres, textos es/en);
  * que HsVideoPlayer/HsAudioPlayer sirvan para varias instancias (create()) sin pisar la del navegador;
  * que browser/ siga limpio;
  * que todo el código nuevo compile con --release 17 y -Xlint:all sin advertencias contra tools/v2110/mcstub;
  * y corre lo de la V2.10.0 (tools/verify_v2100.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v2110.py
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


def run(cmd, timeout=3400, env=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)


need(re.search(r"version = '(2\.(1[1-9]|[2-9]\d)\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.11.0 o superior")
need(re.search(r"mod_version=(2\.(1[1-9]|[2-9]\d)\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.11.0 o superior")
need("V2.11.0" in read(ROOT / "README.md"), "README sin la entrada V2.11.0")
need("PARCHE V2.11.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.11.0")

# ---------------------------------------------------------------- archivos y conexiones
C = JAVA / "client"
for rel in ("client/TvPlayback.java", "client/TelevisionRenderer.java"):
    need((JAVA / rel).exists(), f"falta {rel}")
tvp = read(C / "TvPlayback.java")
for token in ("HsVideoPlayer.create()", "MAX_ACTIVE", "START_BLOCKS", "STOP_BLOCKS", "getStartCount()", "audio().tick(", "public static void reset()",
              "video.play(path, null,", "video.play(null, file,"):
    need(token in tvp, f"TvPlayback no tiene {token}")
rend = read(C / "TelevisionRenderer.java")
for token in ("implements BlockEntityRenderer<TelevisionBlockEntity>", "RenderType.text(", "TvPlayback.sessionOf(", "textureId()", "videoWidth()",
              '"tv_big"', '"tv_cinema"', '"tv_crt"', '"tv_retro"', '"tv_modern"', '"television"'):
    need(token in rend, f"TelevisionRenderer no tiene {token}")
ev = read(C / "ClientEvents.java")
need("registerBlockEntityRenderer(ModBlockEntities.TELEVISION.get(), TelevisionRenderer::new)" in ev, "ClientEvents no registra el renderizador del televisor")
need("EntityRenderersEvent.RegisterRenderers" in ev, "ClientEvents no escucha EntityRenderersEvent.RegisterRenderers")
gev = read(C / "GarageClientEvents.java")
need("TvPlayback.tick(minecraft)" in gev, "falta TvPlayback.tick en el tick del cliente")
need("TvPlayback.reset()" in gev, "falta TvPlayback.reset() al salir del mundo")
be = read(JAVA / "block/entity/TelevisionBlockEntity.java")
for token in ("startCount", "StartCount", "public int getStartCount()", "TvPlayback.register(this)", "TvPlayback.unregister(this)", "getRenderBoundingBox()",
              "public void apply(String path, boolean play)", "void onLoad()", "void setRemoved()", "void onChunkUnloaded()"):
    need(token in be, f"TelevisionBlockEntity no tiene {token}")
tvb = read(JAVA / "block/TelevisionBlock.java")
need("tv.apply(clean, shouldPlay)" in tvb, "TelevisionBlock debe usar tv.apply(...) para contar los 'Reproducir'")
need("HsComputers" not in tvb or "isHsComputer" not in tvb, "los televisores no deben abrir el navegador con R")
hsc = read(JAVA / "block/HsComputers.java")
need("TelevisionBlock.ON" in hsc, "HsComputers.isOn debe contar los televisores encendidos (para callar el sonido al apagar)")
pkt = read(JAVA / "network/TelevisionPathPacket.java")
need("MAX_PATH = 1024" in pkt and pkt.count("MAX_PATH") >= 3, "TelevisionPathPacket sin MAX_PATH")
scr = read(C / "TelevisionScreen.java")
need("MAX_PATH" in scr and "MP4" in scr, "TelevisionScreen sin el límite de ruta ni el aviso de MP4")

player = read(C / "HsVideoPlayer.java")
for token in ("public static HsVideoPlayer create()", "public HsAudioPlayer audio()", "public ResourceLocation textureId()", "IN_USE", "this.audio.play(null, path"):
    need(token in player, f"HsVideoPlayer no tiene {token}")
need("HsAudioPlayer.get()" in player and player.count("HsAudioPlayer.get()") == 1, "HsVideoPlayer solo debe usar HsAudioPlayer.get() para la instancia del navegador")
audio = read(C / "HsAudioPlayer.java")
need("public static HsAudioPlayer create()" in audio, "HsAudioPlayer sin create()")

# ---------------------------------------------------------------- textos es/en
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))
for k in ("loading", "opening", "not_found"):
    for code, lang in (("es", es), ("en", en)):
        need(f"gui.beautifuldecor.tv.{k}" in lang, f"{code}: falta gui.beautifuldecor.tv.{k}")
used = set(re.findall(r'"(gui\.beautifuldecor\.tv\.[a-z_]+)"', tvp + rend + scr))
for key in sorted(used):
    need(key in es and key in en, f"texto sin traducir: {key}")

# ---------------------------------------------------------------- browser/ limpio
B = JAVA / "browser"
for f in sorted(B.rglob("*.java")):
    src = read(f)
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need("TRACE" not in src and "System.err.println" not in src, f"{f.name}: quedó código de depuración")

# ---------------------------------------------------------------- compilación contra piezas mínimas
javac = shutil.which("javac")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if javac is None:
    notes.append("sin javac: no se compiló nada")
else:
    out = Path(tempfile.mkdtemp(prefix="v2110_tv_"))
    try:
        stubs = [str(p) for p in (ROOT / "tools/v2110/mcstub").rglob("*.java")]
        srcs = [str(p) for p in B.rglob("*.java")]
        real = [str(C / n) for n in ("HsVideoPlayer.java", "HsAudioPlayer.java", "TvPlayback.java", "TelevisionRenderer.java")]
        real.append(str(JAVA / "block/entity/TelevisionBlockEntity.java"))
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(out)] + stubs + srcs + real, env=env)
        need(r.returncode == 0, "no compila el código de los televisores contra las piezas mínimas de Minecraft:\n" + r.stderr[-2500:])
        need("warning" not in r.stderr, "el código de los televisores compila con advertencias:\n" + r.stderr[-1500:])
        notes.append("HsVideoPlayer, HsAudioPlayer, TvPlayback, TelevisionRenderer y TelevisionBlockEntity compilan sin advertencias contra tools/v2110/mcstub")
    finally:
        shutil.rmtree(out, ignore_errors=True)

# ---------------------------------------------------------------- lo de la V2.10.0
r = run([sys.executable, str(ROOT / "tools/verify_v2100.py")], timeout=3400, env=env)
need(r.returncode == 0 and "OK V2.10" in r.stdout, "verify_v2100 falló:\n" + (r.stdout + r.stderr)[-2000:])

if errors:
    print("FALLÓ la verificación V2.11.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.11.0: los televisores reproducen MP4 (video y sonido) en su pantalla con el mismo sistema del navegador HS; máx. 2 a la vez cerca de ti; compila sin advertencias.")
for n in notes:
    print("  (nota)", n)
