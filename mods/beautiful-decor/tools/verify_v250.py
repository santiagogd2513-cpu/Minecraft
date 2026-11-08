#!/usr/bin/env python3
"""Comprueba la V2.5.0: el navegador HS reproduce videos MP4 normales (H.264 + AAC-LC), todo en Java puro.

No compila Minecraft. Revisa:
  * todo lo de la V2.4.x (corre tools/verify_v240.py);
  * versión, README y NUEVOS_BLOQUES;
  * que browser/ (incluidos browser/video y browser/audio) siga sin usar Minecraft/LWJGL y compile sin ninguna advertencia;
  * la conexión del video en la pantalla del navegador y que todos los textos existan en español e inglés;
  * y, si hay ffmpeg, decodifica videos y audios de prueba hechos con ffmpeg/libx264 y exige el mismo resultado que ffmpeg
    (H.264: igual bit a bit; AAC: error de 1 en la muestra de 16 bits; MP4 completo: orden de cuadros, colores y recorte del
    retraso del codificador), y una prueba de archivos dañados (fuzz).

Uso: python3 tools/verify_v250.py
"""
import json
import re
import shlex
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


def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


need(re.search(r"version = '(2\.([5-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 2.5.0 o superior")
need(re.search(r"mod_version=(2\.([5-9]|\d{2,})\.\d+|[3-9]\.\d+\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 2.5.0 o superior")
need("V2.5.0" in read(ROOT / "README.md"), "README sin la entrada V2.5.0")
need("PARCHE V2.5.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V2.5.0")

# ---------------------------------------------------------------- archivos y pureza de browser/
for rel in ("browser/video/Mp4.java", "browser/video/H264Decoder.java", "browser/video/H264Deblock.java", "browser/video/H264Cabac.java",
            "browser/video/H264Intra.java", "browser/video/H264Inter.java", "browser/video/H264Transform.java",
            "browser/video/H264Params.java", "browser/video/H264Pic.java", "browser/video/H264Tables.java",
            "browser/video/Mp4Video.java", "browser/video/Mp4AacSource.java", "browser/audio/AacDecoder.java",
            "browser/audio/AacTables.java", "client/HsVideoPlayer.java"):
    need((JAVA / rel).exists(), f"falta {rel}")
for f in sorted((JAVA / "browser").rglob("*.java")):
    src = read(f)
    for bad in ("net.minecraft", "com.mojang", "org.lwjgl", "net.minecraftforge"):
        need(bad not in src, f"{f.name}: el paquete browser/ no debe usar {bad}")
    need("TRACE" not in src and "System.err.println" not in src, f"{f.name}: quedó código de depuración")

# ---------------------------------------------------------------- conexión con la pantalla
screen = read(JAVA / "client/HsBrowserScreen.java")
need("playVideoUrl" in screen and "playVideoFile" in screen and "HsVideoPlayer.get().play(" in screen, "la pantalla no arranca videos")
need("HsVideoPlayer.get().update()" in screen, "la pantalla debe llamar a HsVideoPlayer.update() al dibujar")
need("public void removed()" in screen and re.search(r"removed\(\) \{\s*HsVideoPlayer\.get\(\)\.stop\(\)", screen), "al cerrar la pantalla el video debe pararse")
need("drawVideo" in screen and "videoShown" in screen, "falta dibujar el video en la pantalla")
player = read(JAVA / "client/HsVideoPlayer.java")
need("this.audio.play(null, path" in player, "el sonido del video debe pasar por HsAudioPlayer (es el reloj)")
need("releasePending" in player and "releaseTexture" in player, "HsVideoPlayer debe soltar la textura en el hilo principal")
pipe = read(JAVA / "browser/audio/AudioPipe.java")
need("Mp4AacSource.looksLikeMp4" in pipe, "AudioPipe debe reconocer el sonido de un MP4")
sess = read(JAVA / "browser/BrowserSession.java")
need("playVideoUrl" in sess and "hs:playvideo" in sess and "hs:video" in sess, "BrowserSession sin las rutas de video")

# ---------------------------------------------------------------- textos es/en
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))
used = set(re.findall(r'"(gui\.beautifuldecor\.browser\.video\.[a-z_]+)"', player + screen))
used |= {"gui.beautifuldecor.browser.video." + k for k in re.findall(r'"gui\.beautifuldecor\.browser\.video\.([a-z_]+)"', player)}
for k in sorted(used):
    need(k in es and k in en, f"falta el texto {k}")
need(len([k for k in es if k.startswith("gui.beautifuldecor.browser.video.")]) >= 8, "faltan textos del video")

# ---------------------------------------------------------------- 1. lo de la V2.4.x
r = run([sys.executable, str(ROOT / "tools/verify_v240.py")], timeout=1200)
need(r.returncode == 0 and "OK V2.4" in r.stdout, "verify_v240 falló:\n" + (r.stdout + r.stderr)[-1500:])

# ---------------------------------------------------------------- 2. compilación sin advertencias y pruebas reales
javac = shutil.which("javac")
if javac is None:
    notes.append("sin javac: no se compiló ni se probó")
else:
    out = Path(tempfile.mkdtemp(prefix="v250_"))
    try:
        srcs = [str(p) for p in (JAVA / "browser").rglob("*.java")]
        tests = [str(p) for p in (ROOT / "tools/v250/java").glob("*.java")]
        r = run([javac, "-Xlint:all", "-d", str(out)] + srcs + tests)
        need(r.returncode == 0, "no compilan browser/ y las pruebas de tools/v250/java:\n" + r.stderr[-1500:])
        need("warning" not in r.stderr, "browser/ compila con advertencias:\n" + r.stderr[-1500:])
        ffmpeg = shutil.which("ffmpeg")
        if r.returncode == 0 and ffmpeg is None:
            notes.append("sin ffmpeg: se omitieron las pruebas contra ffmpeg (H.264, AAC, MP4 completo, fuzz)")
        elif r.returncode == 0:
            work = out / "t"
            work.mkdir()

            def ff(*args):
                rr = run([ffmpeg, "-v", "error", "-y", *args], timeout=300)
                need(rr.returncode == 0, "ffmpeg falló: " + " ".join(args)[:120] + " " + rr.stderr[-200:])
                return rr.returncode == 0

            def java(*args):
                rr = run(["java", "-cp", str(out), *args], timeout=600)
                return (rr.stdout + rr.stderr).strip()

            # ---- H.264: debe ser idéntico a ffmpeg
            srcs_v = {"ts2": "testsrc2=size=176x144:rate=15", "noise": "testsrc2=size=176x144:rate=15,noise=alls=25:allf=t"}
            h264 = {
                "i_dbk": ("ts2", "-profile:v baseline -preset ultrafast -x264-params keyint=1"),
                "p_base": ("noise", "-profile:v baseline -preset medium -x264-params keyint=30:ref=3"),
                "i_cabac": ("ts2", "-profile:v main -preset ultrafast -x264-params keyint=1:cabac=1"),
                "p_cabac": ("noise", "-profile:v main -preset medium -x264-params keyint=30:bframes=0:ref=3"),
                "b_main": ("noise", "-profile:v main -preset medium -x264-params keyint=30:bframes=3:b-pyramid=normal:ref=3"),
                "b_temporal": ("noise", "-profile:v main -preset medium -x264-params keyint=30:bframes=3:direct=temporal"),
                "b_spatial": ("noise", "-profile:v main -preset medium -x264-params keyint=30:bframes=3:direct=spatial"),
                "high": ("noise", "-profile:v high -preset medium -x264-params keyint=30:bframes=3:8x8dct=1:ref=3"),
                "cavlc_b": ("noise", "-profile:v high -preset medium -x264-params keyint=30:bframes=3:cabac=0:8x8dct=1"),
                "weightp": ("noise", "-profile:v main -preset medium -x264-params keyint=30:bframes=0:weightp=2"),
                "weightb": ("noise", "-profile:v main -preset medium -x264-params keyint=30:bframes=3:weightb=1"),
                "cqm": ("noise", "-profile:v high -preset medium -x264-params keyint=30:bframes=2:cqm=jvt:8x8dct=1"),
                "slices": ("noise", "-profile:v main -preset medium -x264-params keyint=30:slices=4"),
                "cip": ("noise", "-profile:v main -preset medium -x264-params keyint=30:constrained-intra=1"),
            }
            first = None
            for name, (src, opts) in h264.items():
                mp4 = work / f"{name}.mp4"
                yuv = work / f"{name}.yuv"
                if ff("-f", "lavfi", "-i", srcs_v[src], "-frames:v", "16", "-c:v", "libx264", "-pix_fmt", "yuv420p", *shlex.split(opts), str(mp4)) \
                        and ff("-i", str(mp4), "-f", "rawvideo", "-pix_fmt", "yuv420p", "-fps_mode", "passthrough", str(yuv)):
                    res = java("H264Test", str(mp4), str(yuv))
                    need(res.startswith("IGUAL"), f"H.264 {name}: {res[-300:]}")
                    first = first or mp4
            if first:
                res = java("H264Fuzz", str(first), "60")
                need("FUZZ OK" in res, "fuzz H.264: " + res[-300:])

            # ---- AAC: error de 1 en 16 bits
            aac_src = "aevalsrc='0.3*sin(2*PI*440*t)+0.2*sin(2*PI*1234*t)+0.1*random(0)|0.25*sin(2*PI*660*t)+0.15*random(1)':s=44100:d=3"
            aac = {
                "a_base": ("-c:a aac -b:a 128k", 2),
                "a_mono": ("-ac 1 -c:a aac -b:a 64k", 1),
                "a_tns": ("-c:a aac -b:a 96k -aac_tns 1", 2),
                "a_pns": ("-c:a aac -b:a 48k -aac_pns 1 -aac_is 1", 2),
                "a_22k": ("-c:a aac -b:a 32k -ar 22050", 2),
                "a_8k": ("-c:a aac -b:a 16k -ar 8000", 2),
            }
            for name, (opts, ch) in aac.items():
                mp4 = work / f"{name}.mp4"
                pcm = work / f"{name}.pcm"
                if ff("-f", "lavfi", "-i", aac_src, *shlex.split(opts), str(mp4)) and ff("-i", str(mp4), "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", str(ch), str(pcm)):
                    res = java("AacTest", str(mp4), str(pcm))
                    need(res.startswith("IGUAL"), f"AAC {name}: {res[-300:]}")

            # ---- MP4 completo: cuadros B, colores, audio recortado
            av = work / "av.mp4"
            if ff("-f", "lavfi", "-i", "testsrc2=size=320x180:rate=25:d=3", "-f", "lavfi", "-i", "sine=f=440:d=3:r=44100", "-c:v", "libx264", "-preset", "medium",
                  "-bf", "2", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", str(av)) \
                    and ff("-i", str(av), "-f", "rawvideo", "-pix_fmt", "rgba", "-fps_mode", "passthrough", str(work / "av.rgba")) \
                    and ff("-i", str(av), "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "1", str(work / "av.pcm")):
                res = java("MediaTest", str(av), str(work / "av.rgba"), str(work / "av.pcm"))
                need("MEDIA OK" in res, "MP4 completo: " + res[-400:])
    finally:
        shutil.rmtree(out, ignore_errors=True)

if errors:
    print("FALLÓ la verificación V2.5.0:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V2.5.0: video MP4 (H.264 + AAC-LC) en el navegador HS, sin dependencias, textos es/en, "
      "pruebas reales contra ffmpeg." + ("" if not notes else " Notas: " + "; ".join(notes)))
