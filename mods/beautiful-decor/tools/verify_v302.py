#!/usr/bin/env python3
"""Comprueba la V3.0.2: el video (navegador HS y televisores) ya no puede cerrar el juego por un error en el hilo principal,
sube menos datos a la GPU y deja notas en latest.log.

No hay Minecraft en el entorno, así que se prueba con piezas de mentira (tools/v2110/mcstub + tools/v302/teststub_overrides: texturas que guardan
los píxeles, un OpenAL de mentira que "suena" en tiempo real). Revisa:
  * versión, README y NUEVOS_BLOQUES;
  * que los puntos del hilo principal estén protegidos (update, draw, tick de audio, tick de televisores, render del televisor);
  * que la reducción a la mitad (HsVideoPlayer.halve) dé exactamente el promedio de 2x2;
  * que se reproduzcan de principio a fin, por el reproductor de verdad, un MP4 de 720p con B-frames y AAC estéreo (textura de 640x360), uno de 360p
    (sin reducir), uno de 60 cuadros sin sonido (no se suben más de ~35 por segundo), reiniciar a mitad, parar 20 veces seguidas y un archivo que no es video;
  * y corre lo de la V3.0.0 (tools/verify_v300.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v302.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
C = JAVA / "client"
errors = []
notes = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env, cwd=cwd)


need(re.search(r"version = '3\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 3.x")
need(re.search(r"mod_version=3\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.x")
need("V3.0.2" in read(ROOT / "README.md"), "README sin la entrada V3.0.2")
need("PARCHE V3.0.2" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES sin la sección V3.0.2")

# ---------------------------------------------------------------- protecciones del hilo principal
vp = read(C / "HsVideoPlayer.java")
ap = read(C / "HsAudioPlayer.java")
tv = read(C / "TvPlayback.java")
tr = read(C / "TelevisionRenderer.java")
need(re.search(r"public void update\(\) \{\s*try \{\s*updateInner\(\);\s*\} catch \(Throwable", vp), "HsVideoPlayer.update() debe atrapar Throwable")
need(re.search(r"public boolean draw\(.*?\) \{.*?catch \(Throwable", vp, re.S), "HsVideoPlayer.draw() debe atrapar Throwable")
need(re.search(r"public void tick\(Minecraft minecraft\) \{\s*try \{\s*tickInner\(minecraft\);\s*\} catch \(Throwable", ap), "HsAudioPlayer.tick() debe atrapar Throwable")
need(re.search(r"public static void tick\(Minecraft mc\) \{.*?try \{\s*tickInner\(mc\);\s*\} catch \(Throwable", tv, re.S), "TvPlayback.tick() debe atrapar Throwable")
need("startSafely" in tv and "static void fail(" in tv, "TvPlayback debe empezar los videos con startSafely y tener fail()")
need(re.search(r"public void render\(.*?\) \{\s*try \{\s*draw\(tv, pose, buffer\);\s*\} catch \(Throwable t\) \{\s*TvPlayback\.fail\(tv, t\);", tr),
     "TelevisionRenderer.render() debe atrapar Throwable y llamar a TvPlayback.fail")
need(tr.count("pose.pushPose();") == tr.count("pose.popPose();") == 2 and tr.count("finally {") >= 2, "el renderizador de televisores debe equilibrar pushPose/popPose con finally")
need("FULL_RES_PIXELS = 600_000" in vp and "MIN_UPLOAD_GAP_US = 28_000L" in vp, "faltan los límites de carga del video")

# ---------------------------------------------------------------- pruebas con piezas de mentira
javac = shutil.which("javac")
java = shutil.which("java")
ffmpeg = shutil.which("ffmpeg")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se corrieron las pruebas de reproducción")
else:
    work = Path(tempfile.mkdtemp(prefix="v302_"))
    try:
        stub = work / "stub"
        shutil.copytree(ROOT / "tools/v2110/mcstub", stub)
        for rel in ("com/example/beautifuldecor/init/ModBlockEntities.java", "com/example/beautifuldecor/client/RegisterCheck.java"):
            (stub / rel).unlink()
        shutil.copytree(ROOT / "tools/v302/teststub_overrides", stub, dirs_exist_ok=True)
        out = work / "out"
        out.mkdir()
        srcs = [str(p) for p in stub.rglob("*.java")] + [str(p) for p in (JAVA / "browser").rglob("*.java")]
        srcs += [str(C / "HsVideoPlayer.java"), str(C / "HsAudioPlayer.java")] + [str(p) for p in (ROOT / "tools/v302/java").glob("*.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(out)] + srcs, env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba de reproducción:\n" + r.stderr[-2000:])
        if r.returncode == 0:
            r = run([java, "-cp", str(out), "com.example.beautifuldecor.client.HalveTest"], env=env, cwd=work)
            need(r.returncode == 0 and "halve OK 1280x720" in r.stdout, "la reducción a la mitad no da el promedio de 2x2:\n" + (r.stdout + r.stderr)[-1500:])
            notes.append("la reducción de 1280x720 a 640x360 da exactamente el promedio de cada cuadrito de 2x2")
            if ffmpeg is None:
                notes.append("sin ffmpeg: no se reprodujeron videos de prueba")
            else:
                def make(name, args):
                    rr = run([ffmpeg, "-v", "error", "-y"] + args + [str(work / name)], env=env, cwd=work)
                    need(rr.returncode == 0, f"ffmpeg no pudo hacer {name}:\n{rr.stderr[-500:]}")

                make("hd.mp4", ["-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "8",
                                "-c:v", "libx264", "-profile:v", "high", "-bf", "2", "-g", "60", "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac", "2", "-b:a", "128k",
                                "-movflags", "+faststart"])
                make("sd.mp4", ["-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24", "-f", "lavfi", "-i", "sine=frequency=330:sample_rate=48000", "-t", "6",
                                "-c:v", "libx264", "-profile:v", "main", "-bf", "2", "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac", "2", "-b:a", "96k"])
                make("silent60.mp4", ["-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=60", "-t", "4", "-c:v", "libx264", "-profile:v", "high", "-pix_fmt", "yuv420p", "-an"])
                if not errors:
                    for args, label in ((["hd.mp4", "640", "360"], "720p con B-frames y AAC estéreo"), (["sd.mp4", "640", "360"], "360p")):
                        r = run([java, "-cp", str(out), "com.example.beautifuldecor.client.E2e"] + args, env=env, cwd=work, timeout=120)
                        need(r.returncode == 0 and "E2E OK" in r.stdout, f"la reproducción de {label} falló:\n" + (r.stdout + r.stderr)[-2500:])
                        m = re.search(r"RESULT .*", r.stdout)
                        if m:
                            notes.append(f"{label}: {m.group(0)[:230]}")
                    r = run([java, "-cp", str(out), "com.example.beautifuldecor.client.E2e2"], env=env, cwd=work, timeout=180)
                    need(r.returncode == 0 and "E2E2 OK" in r.stdout, "reiniciar / archivo dañado / parar muchas veces / 60 cuadros sin sonido falló:\n" + (r.stdout + r.stderr)[-2500:])
                    need("Exception in thread" not in r.stdout + r.stderr, "una excepción se escapó de un hilo:\n" + (r.stdout + r.stderr)[-1500:])
                    notes.append("reiniciar a mitad, parar 20 veces seguidas, un archivo que no es video y un video de 60 cuadros sin sonido se portan bien")
    finally:
        shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- lo de la V3.0.0 (y, con él, lo de la V2.11.0 y anteriores)
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v300.py")], env=env, timeout=3400)
    need(r.returncode == 0 and "OK V3.0.0" in r.stdout, "verify_v300 falló:\n" + (r.stdout + r.stderr)[-2500:])

if errors:
    print("FALLÓ la verificación V3.0.2:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK V3.0.2: el video ya no puede cerrar el juego por un error en el hilo principal; los videos grandes se reducen a la mitad antes de subirse a la GPU; "
      "se reproducen de principio a fin en las pruebas; notas en latest.log.")
for n in notes:
    print("  (nota)", n)
