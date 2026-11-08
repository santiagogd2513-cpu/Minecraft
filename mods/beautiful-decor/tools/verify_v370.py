#!/usr/bin/env python3
"""Comprueba SOLO la V3.7.0: superdeportivo Bugotti Chillón (no vuelve a correr las verificaciones de versiones anteriores).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README, NUEVOS_BLOQUES, protocolo de red ("16" o más);
  * textura, brillo, máscara de color, regiones de "Otros", tabla Java, modelo Java e ícono son EXACTAMENTE lo que generan las herramientas;
  * los neumáticos no se pueden pintar (ni un píxel con región) y todo lo demás visible sí; colores de referencia de Java = Python;
  * registrado todo: entidad, ítem, sonidos (+ .ogg), idiomas es/en, pestaña de vehículos, renderizador y capa del modelo, modelo de ítem;
  * velocidad máxima = 250 bloques por segundo, y simulación de aceleración, frenado y curvas con las mismas fórmulas del juego;
  * el repintado en Java (con las texturas del auto) da lo mismo que la cuenta aparte en Python;
  * sintaxis del Java nuevo.

Uso: python3 tools/verify_v370.py
"""
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
ENT = ASSETS / "textures/entity"
NAME = "bugotti_chillon"
errors, notes = [], []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, env=env, cwd=cwd)


import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

# ---------------------------------------------------------------- versión, documentos, red
need(re.search(r"version = '3\.(([7-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.7.0 o superior")
need(re.search(r"mod_version=3\.(([7-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.7.0 o superior")
need("V3.7.0" in read(ROOT / "README.md"), "README sin la entrada V3.7.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.7.0") and "PARCHE V3.6.0" in nuevos, "NUEVOS_BLOQUES debe empezar con la sección V3.7.0 y conservar la V3.6.0")
need(re.search(r'PROTOCOL = "(1[6-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 16 o más (entidad nueva)")

# ---------------------------------------------------------------- lo que generan las herramientas
from tools.v370 import supercar as SC  # noqa: E402
from tools.v320 import paint_masks as PM, paintkit as PK  # noqa: E402
from tools.v360 import extra_regions as ER  # noqa: E402
from tools.v210 import vcommon as vc  # noqa: E402

b, tex, glow = SC.build_all()
for fname, im in ((f"{NAME}.png", tex), (f"{NAME}_glow.png", glow)):
    got = np.asarray(Image.open(ENT / fname).convert("RGBA"))
    need(got.shape == np.asarray(im).shape and (got == np.asarray(im)).all(), f"{fname} no es lo que genera tools/v370/supercar.py")
need(read(JAVA / "client/SupercarModel.java") == SC.java_model(b), "SupercarModel.java no es lo que genera tools/v370/supercar.py")
SC.pose(b, 0.0, 0.0)
icon = vc.make_icon(b, tex, glow, (SC.TEX_W, SC.TEX_H), 140, 16, SC.CENTER, 7.5, outline=(8, 12, 30, 255), brighten=1.1)
got_icon = Image.open(ASSETS / f"textures/item/{NAME}.png").convert("RGBA")
need(got_icon.size == (32, 32) and (np.asarray(got_icon) == np.asarray(icon)).all(), "el ícono no es lo que genera el programa (o no mide 32x32)")
need(json.loads(read(ASSETS / f"models/item/{NAME}.json")) == {"parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{NAME}"}},
     "models/item/bugotti_chillon.json mal")

ks, ref, worst = PM.measure(SC, SC)
mask = PM.to_png(ks, ref.shape)
got_mask = np.asarray(Image.open(ENT / f"{NAME}_paint.png").convert("RGB"))
need((got_mask == mask).all(), f"{NAME}_paint.png no es lo que calcula tools/v320/paint_masks.py")
need(worst < 0.01, f"el repintado del auto no es lineal (desvío {worst:.3f})")

rmask, refs, _ = ER.compute(NAME)
got_reg = np.asarray(Image.open(ENT / f"{NAME}_regions.png").convert("RGB"))
need((got_reg == rmask).all(), f"{NAME}_regions.png no es lo que calcula tools/v360/extra_regions.py")
table = {}
for name in ER.GROUPS:
    table[name] = ER.compute(name)[1]
need(read(JAVA / "entity/VehiclePaintExtras.java") == ER.java_source({k: list(v) for k, v in table.items()}), "VehiclePaintExtras.java no es lo que genera tools/v360/extra_regions.py")

base = np.asarray(Image.open(ENT / f"{NAME}.png").convert("RGBA"))
alpha = base[..., 3] > 0
mainmask = (got_mask > 0).any(axis=2)
forbidden = np.zeros(alpha.shape, bool)
for c in ER.geometry(NAME):
    if c.mat in ER.FORBIDDEN_MATS[NAME]:
        for x0, y0, cw, ch in ER.face_rects(c):
            forbidden[y0:y0 + ch, x0:x0 + cw] = True
forbidden &= alpha & ~mainmask
need(forbidden.sum() > 500, "no se encontraron los neumáticos del auto en la geometría")
need(not ((got_reg[..., 0] > 0) & forbidden).any(), "hay píxeles de neumático con región (se podrían pintar)")
need(not ((got_reg[..., 0] > 0) & mainmask).any(), "un píxel de pintura/manijas/rines también está en una región de Otros")
need(not (alpha & ~mainmask & ~forbidden & (got_reg[..., 0] == 0)).any(), "hay píxeles visibles que no son neumático y no se pueden pintar")
need(int(mask[..., 2].astype(bool).sum()) > 300 and int(mask[..., 1].astype(bool).sum()) > 20, "la máscara de rines o de manijas está casi vacía")
notes.append(f"{NAME}: {len(table[NAME])} partes de Otros, {int(forbidden.sum())} píxeles de neumáticos sin cambio posible; atlas {SC.TEX_W}x{SC.TEX_H}, {len(b.cubes)} cubos")

# colores de referencia: Java = Python
vp = read(JAVA / "entity/VehiclePaint.java")
m = re.search(r'SUPERCAR\("bugotti_chillon",\s*0x([0-9A-F]{6}),\s*0x([0-9A-F]{6}),\s*0x([0-9A-F]{6})\)', vp)
need(m is not None, "VehiclePaint.Kind.SUPERCAR no está definido")
if m:
    want = [SC.REFS["body"], SC.REFS["handle"], SC.REFS["rim"]]
    got = [tuple(int(m.group(i)[j:j + 2], 16) for j in (0, 2, 4)) for i in (1, 2, 3)]
    need(got == want, f"colores de referencia distintos: Java {got} Python {want}")

# ---------------------------------------------------------------- registros, idiomas, sonidos
sc = read(JAVA / "entity/SupercarEntity.java")
need("VehiclePaint.Kind.SUPERCAR" in sc and "extends VehicleEntity" in sc and "ModItems.BUGOTTI_CHILLON" in sc, "SupercarEntity mal conectada")
mm = re.search(r"MAX_SPEED = ([0-9.]+)f", sc)
need(mm and abs(float(mm.group(1)) * 20 - 250.0) < 1e-6, "la velocidad máxima debe ser 12,5 bloques por tick = 250 bloques por segundo")
need("this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset()" in sc, "SupercarEntity.positionRider")
need("BUGOTTI_CHILLON = ENTITIES.register(\"bugotti_chillon\"" in read(JAVA / "init/ModEntities.java"), "ModEntities sin el auto")
need("BUGOTTI_CHILLON = ITEMS.register(\"bugotti_chillon\"" in read(JAVA / "init/ModItems.java") and "ModEntities.BUGOTTI_CHILLON::get" in read(JAVA / "init/ModItems.java"), "ModItems sin el auto")
need('"bugotti_chillon"' in read(JAVA / "init/ModCreativeTabs.java"), "el auto no está en la pestaña de vehículos")
ce = read(JAVA / "client/ClientEvents.java")
need("ModEntities.BUGOTTI_CHILLON.get(), SupercarRenderer::new" in ce and "SupercarModel.LAYER, SupercarModel::createBodyLayer" in ce, "ClientEvents sin el renderizador o la capa del auto")
rd = read(JAVA / "client/SupercarRenderer.java")
need(f'"textures/entity/{NAME}.png"' in rd and f'"textures/entity/{NAME}_glow.png"' in rd and "VehiclePaintTextures.textureFor" in rd, "SupercarRenderer mal")
snd = json.loads(read(ASSETS / "sounds.json"))
ms = read(JAVA / "init/ModSounds.java")
en, es = json.loads(read(ASSETS / "lang/en_us.json")), json.loads(read(ASSETS / "lang/es_es.json"))
for kind in ("engine", "horn", "start"):
    key = f"{NAME}_{kind}"
    need(f'simple("{key}")' in ms, f"ModSounds sin {key}")
    need(key in snd and snd[key]["sounds"][0]["name"] == f"beautifuldecor:{key}" and snd[key]["subtitle"] == f"subtitles.beautifuldecor.{key}", f"sounds.json mal en {key}")
    need((ASSETS / f"sounds/{key}.ogg").exists() and (ASSETS / f"sounds/{key}.ogg").stat().st_size > 2000, f"falta el sonido {key}.ogg")
    need(f"subtitles.beautifuldecor.{key}" in en and f"subtitles.beautifuldecor.{key}" in es, f"falta el subtítulo de {key}")
for k in (f"item.beautifuldecor.{NAME}", f"entity.beautifuldecor.{NAME}", "gui.beautifuldecor.paint.carbon", "gui.beautifuldecor.paint.intake"):
    need(k in en and k in es and en[k].strip() and es[k].strip(), f"falta la clave de idioma {k}")
for key, _, _ in table[NAME]:
    k = f"gui.beautifuldecor.paint.{key}"
    need(k in en and k in es, f"falta la clave de idioma {k} (parte de Otros del auto)")
need(set(en) == set(es), "es_es y en_us no tienen las mismas claves")

# ---------------------------------------------------------------- física, con las fórmulas de VehicleEntity.driveTick
vmax, acc, grip, max_turn = 12.5, 0.45, 0.95, 5.5
spec = re.search(r"new Spec\(\s*MAX_SPEED, ([0-9.]+)f,\s*([0-9.]+)f,.*?\n\s*([0-9.]+)f,.*?\n\s*([0-9.]+)f,", sc, re.S)
need(spec is not None and abs(float(spec.group(2)) - acc) < 1e-9 and abs(float(spec.group(3)) - max_turn) < 1e-9 and abs(float(spec.group(4)) - grip) < 1e-9,
     "los números del Spec no son los que usa la simulación de la prueba")
v, t_ticks, ticks95, dist = 0.0, 0, None, 0.0
for t_ticks in range(1, 600):
    v += acc * (1.0 - v / (vmax * 1.03))
    v = min(v, vmax)
    dist += v
    if ticks95 is None and v >= 0.95 * vmax:
        ticks95 = t_ticks
    if v >= 0.999 * vmax:
        break
need(ticks95 is not None and ticks95 / 20.0 <= 6.0, f"tarda demasiado en llegar al 95% de la velocidad máxima ({ticks95})")
need(t_ticks / 20.0 <= 12.0 and v <= vmax + 1e-9, "no llega a la velocidad máxima en 12 s o se pasa de ella")
turn_deg = min(max_turn, math.degrees(grip / vmax)) * min(1.0, vmax / 0.35)
radius = vmax / math.radians(turn_deg)
need(80.0 <= radius <= 260.0, f"radio de giro a tope fuera de lo razonable: {radius:.0f} bloques")
vb = vmax
stop_ticks, stop_dist = 0, 0.0
while vb > 0.03 and stop_ticks < 1000:
    vb -= 0.10 + 0.04 * vb
    stop_ticks += 1
    stop_dist += max(vb, 0)
need(stop_ticks <= 80, "frena demasiado lento")
notes.append(f"física: 95% de la velocidad en {ticks95 / 20:.1f} s, tope en {t_ticks / 20:.1f} s ({dist:.0f} bloques); curva a tope: radio {radius:.0f} bloques; frenada con S: {stop_ticks / 20:.1f} s y {stop_dist:.0f} bloques")

# ---------------------------------------------------------------- Java: sintaxis y repintado real
javac, java = shutil.which("javac"), shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}


def rgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def expected_png(news):
    """Repintado en Python: original + k * (nuevo - referencia). news = {canal: color}."""
    out = base[..., :3].astype(np.float64)
    for ch, new in news.items():
        if ch < 3:
            key = ("body", "handle", "rim")[ch]
            d = np.array(new, float) - np.array(SC.REFS[key], float)
            out += (got_mask[..., ch] / 100.0)[..., None] * d
    for i, (_, refc, _) in enumerate(table[NAME]):
        ch = 3 + i
        if ch in news:
            d = np.array(news[ch], float) - np.array(rgb(refc), float)
            sel = got_reg[..., 0] == i + 1
            k = (got_reg[..., 1] / 100.0)[..., None]
            out = np.where(sel[..., None], out + k * d, out)
    res = base.copy()
    res[..., :3] = np.where(alpha[..., None], np.clip(np.round(out), 0, 255), base[..., :3]).astype(np.uint8)
    return res


if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java")
else:
    work = Path(tempfile.mkdtemp(prefix="v370_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / p for p in ("entity/SupercarEntity.java", "client/SupercarRenderer.java", "client/SupercarModel.java", "entity/VehiclePaint.java",
                                    "entity/VehiclePaintExtras.java", "init/ModEntities.java", "init/ModItems.java", "init/ModSounds.java", "init/ModCreativeTabs.java",
                                    "client/ClientEvents.java")]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed",
            "method does not override", "cannot be referenced", "constructor"))]
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        cases = []
        palette = [0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0x101010, 0xF0F0F0]
        n = len(table[NAME])
        for i in range(n):
            color = palette[i % len(palette)]
            exp = work / f"exp_{i}.png"
            Image.fromarray(expected_png({3 + i: rgb(color)}), "RGBA").save(exp)
            cases.append(f"EXTRA {NAME} {3 + i} {color:06X} {exp}")
        for ch in range(3):
            color = palette[(ch + 2) % len(palette)]
            exp = work / f"exp_main{ch}.png"
            Image.fromarray(expected_png({ch: rgb(color)}), "RGBA").save(exp)
            cases.append(f"EXTRA {NAME} {ch} {color:06X} {exp}")
        garish = [0xFF00FF, 0x00FFFF, 0xFFFF00, 0x00FF00, 0xFF0000, 0x0000FF]
        news = {ch: rgb(garish[ch % 6]) for ch in list(range(3)) + [3 + i for i in range(n)]}
        exp = work / "exp_all.png"
        Image.fromarray(expected_png(news), "RGBA").save(exp)
        cases.append(f"ALL {NAME} {exp}")
        fb = work / "forbidden.png"
        Image.fromarray((forbidden * 255).astype(np.uint8), "L").save(fb)
        cases.append(f"FORBIDDEN {NAME} {fb}")
        (work / "cases.txt").write_text("\n".join(cases) + "\n", encoding="utf-8")

        srcs = [str(p) for p in (ROOT / "tools/v320/teststub").rglob("*.java")]
        srcs += [str(JAVA / p) for p in ("entity/VehiclePaint.java", "entity/VehiclePaintExtras.java", "entity/PaintExtras.java", "entity/PaintableVehicle.java",
                                         "client/PaintMath.java", "block/GarageColors.java")]
        srcs.append(str(ROOT / "tools/v360/java/OthersTest.java"))
        outp = work / "out"
        outp.mkdir()
        c = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all,-serial", "-d", str(outp)] + srcs, env=env, cwd=work)
        need(c.returncode == 0 and "warning:" not in c.stderr, "no compila la prueba del repintado:\n" + c.stderr[-2000:])
        if c.returncode == 0:
            res = run([java, "-cp", str(outp), "com.example.beautifuldecor.entity.OthersTest", str(ENT), str(work / "cases.txt")], env=env, cwd=work, timeout=300)
            need(res.returncode == 0 and "OTHERS OK" in res.stdout, "la prueba del repintado falló:\n" + (res.stdout + res.stderr)[-2500:])
            for line in res.stdout.strip().splitlines():
                notes.append(line.strip())
    finally:
        shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V3.7.0:")
    for e in errors:
        print(" -", e)
    for nn in notes:
        print("  (nota)", nn)
    sys.exit(1)
print("OK V3.7.0: superdeportivo Bugotti Chillón a 250 bloques por segundo, con colores, radio, sonidos y textos; solo se verificó esta versión.")
for nn in notes:
    print("  (nota)", nn)
