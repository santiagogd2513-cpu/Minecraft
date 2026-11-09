#!/usr/bin/env python3
"""Comprueba SOLO la V4.1.1: el anuncio de HS (marca insignia) y que las pantallas pasen los 7 anuncios. No corre verificaciones anteriores.

Uso: python3 tools/verify_v411.py
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
AS = RES / "assets/beautifuldecor"
DATA = RES / "data/beautifuldecor"
errors, notes = [], []
NAMES = ["ad_screen_small", "ad_screen_medium", "ad_screen_large"]


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def code(p):
    s = re.sub(r"/\*.*?\*/", "", read(p), flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def run(cmd, **kw):
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, **kw)


# ---------------------------------------------------------------- versión y documentos
need(re.search(r"version = '4\.(1\.([1-9]|\d{2,})|[2-9]\d*\.\d+|\d{2,}\.\d+)'|version = '([5-9]|\d{2,})\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 4.1.1 o superior")
need(re.search(r"mod_version=4\.(1\.([1-9]|\d{2,})|[2-9]\d*\.\d+|\d{2,}\.\d+)|mod_version=([5-9]|\d{2,})\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.1.1 o superior")
need("V4.1.1" in read(ROOT / "README.md"), "README sin la entrada V4.1.1")
nb = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nb.startswith("PARCHE V4.1.1") and "PARCHE V4.1.0" in nb, "NUEVOS_BLOQUES debe empezar con la V4.1.1 y conservar la V4.1.0")
need('PROTOCOL = "19"' in code(JAVA / "network/ModMessages.java"), "el protocolo no debía cambiar (19)")

# ---------------------------------------------------------------- registros
blocks = code(JAVA / "init/ModBlocks.java")
need("AD_SCREENS" in blocks and all(f'"{n}"' in blocks for n in NAMES) and "new AdScreenBlock(" in blocks, "ModBlocks: faltan las 3 pantallas")
need(re.search(r"sizes\s*=\s*\{4,\s*8,\s*12\}", blocks), "los tamaños deben ser 4, 8 y 12")
need("registerGroup(ModBlocks.AD_SCREENS)" in code(JAVA / "init/ModItems.java"), "ModItems: faltan los ítems de las pantallas")
bes = code(JAVA / "init/ModBlockEntities.java")
need("AD_SCREEN =" in bes and "AdScreenBlockEntity::new" in bes and "ModBlocks.AD_SCREENS.values()" in bes, "ModBlockEntities: falta el block entity")
need("AdScreenRenderer::new" in code(JAVA / "client/ClientEvents.java"), "ClientEvents: falta el renderizador")
gce = code(JAVA / "client/GarageClientEvents.java")
need("AdPlayback.tick(minecraft)" in gce and "AdPlayback.reset()" in gce, "GarageClientEvents: AdPlayback debe ejecutarse cada tick y soltarse al salir")
need("AdScreenBlock" in code(JAVA / "block/HsComputers.java"), "HsComputers debe reconocer la pantalla de anuncios (si no, el audio se corta)")
cat = code(JAVA / "init/ModCategory.java")
order = code(JAVA / "init/ModCreativeOrder.java")
for n in NAMES:
    need(f'"{n}"' in cat, f"ModCategory sin {n}")
    need(f'"{n}"' in order, f"ModCreativeOrder sin {n}")

# ---------------------------------------------------------------- el multibloque
blk = code(JAVA / "block/AdScreenBlock.java")
need("PX = IntegerProperty.create(\"px\", 0, MAX_SIZE - 1)" in blk and "MAX_SIZE = 12" in blk, "PX/PY deben llegar a 11")
need("getClockWise(), state.getValue(PX)" in blk and "below(state.getValue(PY))" in blk, "originOf")
need("canBeReplaced(context)" in blk and "isOutsideBuildHeight" in blk, "la colocación debe comprobar todo el espacio")
need("PX) == 0 && state.getValue(PY) == 0" in blk and "new AdScreenBlockEntity" in blk, "solo la esquina lleva block entity")
need("playerWillDestroy" in blk and "onRemove" in blk and "removeBlock" in blk, "romper una parte debe romper todas")
need("toggle()" in blk and "ad_screen.on" in blk, "clic derecho enciende o apaga")
bee = code(JAVA / "block/entity/AdScreenBlockEntity.java")
need('"On"' in bee and "getUpdatePacket" in bee and "getRenderBoundingBox" in bee, "el block entity guarda y sincroniza el estado y abarca toda la pantalla")
need("AdPlayback.register(this)" in bee and "AdPlayback.unregister(this)" in bee, "el block entity debe registrarse en el cliente")

# ---------------------------------------------------------------- assets
es = json.loads(read(AS / "lang/es_es.json"))
en = json.loads(read(AS / "lang/en_us.json"))
for n in NAMES:
    for what, p in (("blockstate", AS / f"blockstates/{n}.json"), ("modelo de ítem", AS / f"models/item/{n}.json"), ("loot table", DATA / f"loot_tables/blocks/{n}.json"),
                    ("ícono", AS / f"textures/item/{n}.png")):
        need(p.is_file(), f"falta el {what} de {n}")
    k = f"block.beautifuldecor.{n}"
    need(k in es and k in en and es[k] != en[k], f"texto {k} falta o igual en es/en")
    if (AS / f"blockstates/{n}.json").is_file():
        bs = json.loads(read(AS / f"blockstates/{n}.json"))
        ys = {m["when"]["facing"]: m["apply"].get("y", 0) for m in bs["multipart"]}
        need(ys == {"north": 0, "east": 90, "south": 180, "west": 270}, f"{n}: giros por orientación")
        need(all(m["apply"]["model"] == "beautifuldecor:block/ad_panel" for m in bs["multipart"]), f"{n}: modelo")
    if (DATA / f"loot_tables/blocks/{n}.json").is_file():
        lt = read(DATA / f"loot_tables/blocks/{n}.json")
        need(f"beautifuldecor:{n}" in lt and '"px": "0"' in lt and '"py": "0"' in lt, f"{n}: solo la esquina suelta el ítem")
for k in ("message.beautifuldecor.ad_screen.on", "message.beautifuldecor.ad_screen.off"):
    need(k in es and k in en and es[k] != en[k], f"texto {k}")
need((AS / "models/block/ad_panel.json").is_file() and (AS / "textures/block/ad_screen_off.png").is_file(), "modelo o textura del panel")
try:
    from PIL import Image
    for n in NAMES:
        im = Image.open(AS / f"textures/item/{n}.png")
        need(im.size == (32, 32), f"ícono {n} debe ser 32x32")
    need(Image.open(AS / "textures/block/ad_screen_off.png").size == (16, 16), "ad_screen_off debe ser 16x16")
except ImportError:
    notes.append("sin PIL: no se revisó el tamaño de las texturas")

# ---------------------------------------------------------------- marcas, videos y pósters
brands_src = code(JAVA / "ad/AdBrands.java")
m = re.search(r"BRANDS = List\.of\(([^)]*)\)", brands_src)
brands = re.findall(r'"([a-z]+)"', m.group(1)) if m else []
need(len(brands) == 7 and brands[0] == "hs", f"debe haber 7 marcas con HS primero, hay {len(brands)}")
for b in brands:
    need((AS / f"ads/{b}.mp4").is_file(), f"falta el video de {b}")
    need((AS / f"textures/ads/poster_{b}.png").is_file(), f"falta el póster de {b}")
mp4s = sorted(p.stem for p in (AS / "ads").glob("*.mp4"))
need(mp4s == sorted(brands), f"los videos ({mp4s}) deben ser justo las marcas ({sorted(brands)})")
# cada marca debe estar en el mod de verdad (ropa de nombre: se busca en los textos o en el código)
alltext = json.dumps(es, ensure_ascii=False).lower() + "".join(read(p).lower() for p in (JAVA / "init").glob("*.java"))
for b, alias in (("hs", "hs"), ("furd", "furd"), ("amazoom", "amazoom"), ("lamborgonzo", "lamborgonzo"), ("boing", "boing"), ("bugerati", "bugerati"), ("bugotti", "bugotti")):
    need(alias in alltext, f"la marca {b} no aparece en el mod")

ffprobe = shutil.which("ffprobe")
if ffprobe:
    for b in brands:
        f = AS / f"ads/{b}.mp4"
        if not f.is_file():
            continue
        r = run([ffprobe, "-v", "error", "-show_entries", "stream=codec_name,profile,width,height,avg_frame_rate,sample_rate,channels,has_b_frames:format=duration", "-of", "json", str(f)])
        try:
            info = json.loads(r.stdout)
        except ValueError:
            errors.append(f"{b}.mp4: ffprobe no pudo leerlo")
            continue
        st = {s["codec_name"]: s for s in info["streams"]}
        v, a = st.get("h264"), st.get("aac")
        need(v is not None and a is not None, f"{b}.mp4 debe tener H.264 y AAC")
        if v and a:
            need(v["profile"] in ("Baseline", "Constrained Baseline") and str(v.get("has_b_frames")) == "0", f"{b}.mp4: H.264 Baseline sin cuadros B")
            need(v["width"] == 384 and v["height"] == 384, f"{b}.mp4: 384x384")
            need(a["profile"] == "LC" and int(a["channels"]) == 1 and int(a["sample_rate"]) == 44100, f"{b}.mp4: AAC-LC mono 44100")
            dur = float(info["format"]["duration"])
            need(11.5 <= dur <= 12.5, f"{b}.mp4: dura {dur:.1f} s")
        need(f.stat().st_size < 800_000, f"{b}.mp4 pesa demasiado ({f.stat().st_size // 1000} KB)")
else:
    notes.append("sin ffprobe: no se revisó el formato de los videos")

# ---------------------------------------------------------------- decodificar los anuncios con el decodificador del propio mod
javac, java, ffmpeg = shutil.which("javac"), shutil.which("java"), shutil.which("ffmpeg")
work = Path(tempfile.mkdtemp(prefix="v410_"))
try:
    if not (javac and java):
        notes.append("sin javac/java: no se compiló ni se probó nada del Java")
    else:
        out = work / "cls"
        out.mkdir()
        srcs = [str(p) for p in (JAVA / "browser").rglob("*.java")] + [str(JAVA / "ad/AdBrands.java")]
        tests = [str(ROOT / "tools/v250/java/H264Test.java"), str(ROOT / "tools/v410/java/AdMediaTest.java"), str(ROOT / "tools/v410/java/AdBrandsTest.java")]
        r = run([javac, "-nowarn", "-d", str(out)] + srcs + tests)
        need(r.returncode == 0, "no compila el Java puro:\n" + r.stderr[:1500])
        if r.returncode == 0:
            r = run([java, "-cp", str(out), "AdBrandsTest"])
            need(r.returncode == 0 and "AdBrandsTest OK" in r.stdout, "AdBrandsTest falló:\n" + r.stdout[:800])
            if not ffmpeg:
                notes.append("sin ffmpeg: no se comparó la decodificación contra ffmpeg")
            else:
                for b in brands:
                    f = AS / f"ads/{b}.mp4"
                    if not f.is_file():
                        continue
                    yuv, pcm = work / f"{b}.yuv", work / f"{b}.pcm"
                    run([ffmpeg, "-v", "error", "-y", "-i", str(f), "-f", "rawvideo", "-pix_fmt", "yuv420p", str(yuv)])
                    run([ffmpeg, "-v", "error", "-y", "-i", str(f), "-f", "s16le", "-ac", "1", "-ar", "44100", str(pcm)])
                    rv = run([java, "-cp", str(out), "H264Test", str(f), str(yuv)])
                    need("IGUAL cuadros=240" in rv.stdout and "malos=0" in rv.stdout, f"{b}: el decodificador de video del mod no coincide con ffmpeg:\n{rv.stdout[-400:]}")
                    ra = run([java, "-cp", str(out), "AdMediaTest", str(f), str(pcm)])
                    mm = re.search(r"rate=(\d+) ch=(\d+) seconds=([\d.]+) rms=(\d+) corr=([-\d.]+) shift=(-?\d+)", ra.stdout)
                    if not mm:
                        errors.append(f"{b}: no se pudo decodificar el audio:\n{ra.stdout[-300:]}{ra.stderr[-300:]}")
                    else:
                        need(float(mm.group(5)) > 0.999 and int(mm.group(6)) == 0, f"{b}: el AAC del mod no coincide con ffmpeg (corr={mm.group(5)} desfase={mm.group(6)})")
                        need(int(mm.group(4)) > 1500 and float(mm.group(3)) > 11.5, f"{b}: audio casi mudo o corto (rms={mm.group(4)}, {mm.group(3)} s)")
            notes.append("el video y el audio de los 7 anuncios se decodificaron con el decodificador del propio mod")

        # sintaxis del Java con tipos de Minecraft (no se pueden resolver aquí)
        files = [JAVA / "block/AdScreenBlock.java", JAVA / "block/entity/AdScreenBlockEntity.java", JAVA / "client/AdPlayback.java", JAVA / "client/AdScreenRenderer.java",
                 JAVA / "block/HsComputers.java", JAVA / "init/ModBlocks.java", JAVA / "init/ModBlockEntities.java", JAVA / "init/ModItems.java", JAVA / "client/GarageClientEvents.java",
                 JAVA / "client/ClientEvents.java", JAVA / "init/ModCategory.java", JAVA / "init/ModCreativeOrder.java"]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "5000", "-d", str(work / "syn")] + [str(f) for f in files], cwd=work)
        ignore = ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "package com.", "package net.", "package org.", "cannot access",
                  "incompatible types", "cannot be applied", "no suitable", "cannot infer", "cannot be dereferenced", "constructor", "bad operand", "invalid method reference",
                  "has private access", "cannot be referenced", "static import only", "unreported exception", "not a functional interface", "lambda body", "incomparable types", "cannot return")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis:\n" + "\n".join(bad[:10]))
        notes.append("la sintaxis del Java cambiado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
finally:
    shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V4.1.1:")
    for e in errors[:25]:
        print(" -", e)
    sys.exit(1)
print("OK V4.1.1: anuncio de HS y 7 anuncios con video y sonido; solo se verificó esta versión.")
for n in notes:
    print("  (nota)", n)
