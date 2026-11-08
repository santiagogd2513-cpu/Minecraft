#!/usr/bin/env python3
"""Comprueba la V2.4.0/V2.4.1: navegador en los computadores HS (tecla R), modo oscuro, descargas a "HS Downloads", audio MP3/OGG/WAV,
radios en vivo (ICY, .m3u/.pls, reconexión) y salir con la tecla H.

No compila Minecraft. Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que el paquete browser/ no use nada de Minecraft/LWJGL (así se puede probar fuera del juego);
  * el decodificador JLayer reubicado (sin ningún "javazoom/" adentro) y que Mp3Source apunte a ese paquete;
  * la conexión de la tecla R, el cierre del audio al salir del mundo y la carpeta "HS Downloads";
  * que todos los textos del navegador existan en español e inglés;
  * y, si hay javac, compila el paquete browser/ y corre las pruebas reales (HTML, diseño, WAV, red local, descargas, historial).

Uso: python3 tools/verify_v240.py
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
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/beautifuldecor"
errors = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def balanced(src):
    """Llaves y paréntesis parejos, sin contar los que están dentro de textos, caracteres o comentarios."""
    src = re.sub(r'"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'|//[^\n]*|/\*.*?\*/', "", src, flags=re.S)
    return src.count("{") == src.count("}") and src.count("(") == src.count(")") and src.count("[") == src.count("]")


need(re.search(r"version = '(2\.([4-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.4.0 o superior")
need(re.search(r"version = '(2\.4\.([1-9]|\d{2,})|2\.([5-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.4.1 o superior")
need(re.search(r"mod_version=(2\.([4-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.4.0 o superior")
need("V2.4.0" in read(ROOT / "README.md"), "README sin la entrada V2.4.0")
need("PARCHE V2.4.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.4.0")
need("V2.4.1" in read(ROOT / "README.md"), "README sin la entrada V2.4.1")
need("PARCHE V2.4.1" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.4.1")
need((RES / "THIRD_PARTY_JLAYER.txt").exists() and "LGPL" in read(RES / "THIRD_PARTY_JLAYER.txt"), "falta el aviso de licencia de JLayer")

# ---------------------------------------------------------------- paquete browser/ puro
browser = sorted((JAVA / "browser").rglob("*.java"))
need(len(browser) >= 13, f"browser/: faltan archivos ({len(browser)})")
for f in browser:
    src = read(f)
    need(balanced(src), f"{f.name}: llaves o paréntesis desparejados")
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need(len(re.findall(r"^import .*;$", src, re.M)) == len(set(re.findall(r"^import .*;$", src, re.M))), f"{f.name}: imports repetidos")

downloads = read(JAVA / "browser/Downloads.java")
need('FOLDER = "HS Downloads"' in downloads, 'la carpeta debe llamarse "HS Downloads"')
need("gameDir.resolve(FOLDER)" in downloads, "la carpeta debe colgar de la raíz del juego")
need(".part" in downloads and "uniquePath" in downloads and "resolveFile" in downloads and "startsWith(dir.normalize())" in downloads,
     "Downloads: nombres únicos, archivo .part y protección de la carpeta")
url = read(JAVA / "browser/Url.java")
need("isInternal(base) ? r : null" in url, "Url.resolve debe bloquear los enlaces hs: que vienen de páginas web")
sess = read(JAVA / "browser/BrowserSession.java")
need("allowedFromWeb" in sess and "hs:cancel" in sess and "MAX_HTML" in sess, "BrowserSession: protección de enlaces internos y límite de tamaño")
http = read(JAVA / "browser/Http.java")
need("setConnectTimeout" in http and "setReadTimeout" in http and "Demasiadas redirecciones" in http, "Http: tiempos de espera y límite de redirecciones")
layout = read(JAVA / "browser/Layout.java")
need("Page.ROLE_" in layout and "MAX_ITEMS" in layout and "MAX_TABLE_DEPTH" in layout, "Layout: roles de color (modo oscuro) y topes")

# ---------------------------------------------------------------- JLayer reubicado
jl = RES / "com/example/beautifuldecor/lib/jl/decoder"
classes = sorted(jl.glob("*.class")) if jl.exists() else []
need(len(classes) >= 30, f"JLayer reubicado: solo {len(classes)} clases")
for name in ("Bitstream", "Decoder", "Header", "SampleBuffer", "Obuffer", "BitstreamException", "JavaLayerException"):
    need((jl / f"{name}.class").exists(), f"JLayer reubicado: falta {name}.class")
for c in classes:
    data = c.read_bytes()
    need(data[:4] == b"\xca\xfe\xba\xbe", f"{c.name}: no es un .class")
    need(b"javazoom" not in data, f"{c.name}: todavía menciona javazoom")
need(not (RES / "javazoom").exists(), "no debe haber un paquete javazoom en la raíz de los recursos (choca con MusicBoxRadio)")
mp3 = read(JAVA / "browser/audio/Mp3Source.java")
need('"com.example.beautifuldecor.lib.jl.decoder."' in mp3, "Mp3Source debe apuntar al JLayer reubicado")
need("javazoom" not in mp3, "Mp3Source no debe nombrar javazoom")

# ---------------------------------------------------------------- cliente
for name in ("HsAudioPlayer", "HsBrowserScreen", "PageImages"):
    src = read(JAVA / f"client/{name}.java")
    need(balanced(src), f"{name}: llaves o paréntesis desparejados")
    need(len(re.findall(r"^import .*;$", src, re.M)) == len(set(re.findall(r"^import .*;$", src, re.M))), f"{name}: imports repetidos")
ogg = read(JAVA / "client/audio/OggSource.java")
need("stb_vorbis_close" in ogg and "memFree" in ogg, "OggSource debe liberar la memoria")
audio = read(JAVA / "client/HsAudioPlayer.java")
for needle in ("alDeleteSources", "alDeleteBuffers", "catch (Throwable", "STOP_BLOCKS", "SoundSource.MASTER", "SoundSource.RECORDS", "AL_SOURCE_RELATIVE",
               "AudioPipe", "pipe.abort()", "PREBUFFER_LIVE", "AL_BUFFERS_PROCESSED"):
    need(needle in audio, f"HsAudioPlayer: falta {needle}")
pipe = read(JAVA / "browser/audio/AudioPipe.java")
for needle in ("MAX_RECONNECTS", "live_ogg", "format_changed", "\"lost\"", "abort()", "WavSource", "Mp3Source", "isLiveStream()"):
    need(needle in pipe, f"AudioPipe: falta {needle}")
httpsrc = read(JAVA / "browser/Http.java")
for needle in ("rawGet(", "readHeaderLine", "isLiveStream()", "status == -1", "icy-"):
    need(needle in httpsrc, f"Http: falta {needle}")
sess41 = read(JAVA / "browser/BrowserSession.java")
for needle in ("playlist(", "#EXT-X-", "isAacMime", "&live=1", "lower.startsWith(\"file1=\")"):
    need(needle in sess41, f"BrowserSession: falta {needle}")
screen = read(JAVA / "client/HsBrowserScreen.java")
for needle in ("Downloads.init(minecraft.gameDirectory.toPath())", "HsComputers.isOn", "InternalPages.spanish", "images.close()", "Layout.layout(",
               "GLFW_KEY_L", "GLFW_KEY_F5", "GLFW_KEY_H", "GLFW_MOD_ALT", "mouseDragged", "enableScissor", "disableScissor", "fallbackPage"):
    need(needle in screen, f"HsBrowserScreen: falta {needle}")
need("0xFF101216" in screen, "HsBrowserScreen: fondo oscuro fijo")
hk = screen.index("GLFW_KEY_H")
need(screen.index("this.address.isFocused()") < hk, "HsBrowserScreen: la H solo cierra si la barra de direcciones no está activa")
need("isPauseScreen" in screen, "HsBrowserScreen: debe declarar isPauseScreen (no pausar en supervivencia)")
events = read(JAVA / "client/GarageClientEvents.java")
for needle in ("HsComputers.isHsComputer(state)", "HsComputers.isOn(state)", "HsBrowserScreen.open(minecraft, blockHit.getBlockPos())",
               "message.beautifuldecor.browser.off", "ClientPlayerNetworkEvent.LoggingOut", "HsBrowserScreen.reset()", "HsAudioPlayer.get().tick(minecraft)",
               "GarageKeys.COLORS.consumeClick()", "GarageDoorBlock"):
    need(needle in events, f"GarageClientEvents: falta {needle}")
comp = read(JAVA / "block/HsComputers.java")
need("HsDesktopBlock.ON" in comp and "LaptopBlock.OPEN" in comp, "HsComputers: escritorio encendido / portátil abierto")

# ---------------------------------------------------------------- textos
lang = {}
for code in ("es_es", "en_us"):
    lang[code] = json.loads(read(ASSETS / f"lang/{code}.json"))
need(set(lang["es_es"]) == set(lang["en_us"]), "es_es y en_us no tienen las mismas claves: " + ", ".join(sorted(set(lang["es_es"]) ^ set(lang["en_us"]))[:8]))
need(lang["es_es"].get("gui.beautifuldecor.browser.lang") == "es" and lang["en_us"].get("gui.beautifuldecor.browser.lang") == "en", "clave de idioma del navegador")
used = set()
for f in list((JAVA / "client").rglob("*.java")) + list((JAVA / "browser").rglob("*.java")):
    for m in re.finditer(r'"((?:gui|message|key)\.beautifuldecor\.browser[\w.]*)"', read(f)):
        if not m.group(1).endswith("."):
            used.add(m.group(1))
for m in re.finditer(r'addButton\(B_\w+, [^"\n]*"[^"\n]*", "(\w+)"\)', screen):
    used.add("gui.beautifuldecor.browser." + m.group(1))
used.add("gui.beautifuldecor.browser.stop")
need(len(used) >= 20, f"pocos textos del navegador encontrados ({len(used)})")
for k in sorted(used):
    for code in lang:
        need(k in lang[code], f"{code}: falta la clave {k}")
for code in lang:
    for k in ("format", "too_big", "format_changed", "lost", "live_ogg", "live"):
        need(f"gui.beautifuldecor.browser.audio.{k}" in lang[code], f"{code}: falta la clave audio.{k}")
    need("(H)" in lang[code].get("gui.beautifuldecor.browser.close", ""), f"{code}: el botón de cerrar debe decir (H)")
for code in lang:
    need("%s" in lang[code].get("gui.beautifuldecor.browser.audio.error", ""), f"{code}: el mensaje de error de audio necesita %s")
    key_text = lang[code].get("key.beautifuldecor.garage_colors", "").lower()
    need(("garaje" in key_text or "garage" in key_text) and "hs" in key_text, f"{code}: el texto de la tecla R debe nombrar la puerta de garaje y el computador HS")

# ---------------------------------------------------------------- pruebas reales del paquete browser/
javac = shutil.which("javac")
java = shutil.which("java")
if javac and java:
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    with tempfile.TemporaryDirectory() as tmp:
        srcs = [str(p) for p in (JAVA / "browser").rglob("*.java")]
        tests = [str(p) for p in (ROOT / "tools/v240/java").glob("*.java")]
        r = subprocess.run([javac, "-encoding", "UTF-8", "-d", tmp] + srcs, capture_output=True, text=True, env=env)
        need(r.returncode == 0, "no compila el paquete browser/:\n" + r.stderr[-1500:])
        if r.returncode == 0:
            r = subprocess.run([javac, "-encoding", "UTF-8", "-d", tmp, "-cp", tmp] + tests, capture_output=True, text=True, env=env)
            need(r.returncode == 0, "no compilan las pruebas de tools/v240/java:\n" + r.stderr[-1500:])
            if r.returncode == 0:
                for t in ("BrowserTest", "SessionTest", "StreamTest"):
                    try:
                        r = subprocess.run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", f"{tmp}:{RES}", t],
                                           capture_output=True, text=True, env=env, timeout=240)
                        out = r.stdout + r.stderr
                        need(r.returncode == 0 and "TODO OK" in out, f"{t} falló:\n" + "\n".join(l for l in out.splitlines() if "FALLO" in l or "Exception" in l)[:1500])
                    except subprocess.TimeoutExpired:
                        errors.append(f"{t}: se pasó del tiempo")
else:
    print("(aviso) no hay javac/java: se saltan las pruebas reales del navegador")

if errors:
    print("FALLÓ la verificación V2.4.1:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.4.1: navegador HS (R en computador encendido, H para salir), modo oscuro, HS Downloads, audio MP3/OGG/WAV y radios en vivo (ICY, .m3u/.pls, reconexión), JLayer reubicado, textos es/en, pruebas reales.")
