#!/usr/bin/env python3
"""Comprueba SOLO la V3.8.0: avión de pasajeros BOING 7G7 Gigante (no vuelve a correr las verificaciones de versiones anteriores).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README, NUEVOS_BLOQUES, protocolo de red ("17" o más);
  * textura, máscara de color, regiones de "Otros", tabla Java, modelo Java e ícono son EXACTAMENTE lo que genera tools/v380/airliner.py;
  * neumáticos y aspas de turbina no se pueden pintar (ni un píxel con región) y todo lo demás visible sí; colores de referencia de Java = Python;
  * 130 asientos y sus posiciones coinciden con las del generador del modelo;
  * registrado todo: entidad, ítem, sonidos (+ .ogg), idiomas es/en (todas las claves que usa el Java), pestaña, renderizador, capa del modelo, teclas, paquete;
  * la tecla Z se consume en un solo sitio; la F le quita el efecto de "cambiar objeto de mano" mientras se pilota;
  * física: despegue, ascenso, descenso y tren simulados con las mismas fórmulas del juego;
  * el repintado en Java (con las texturas del avión) da lo mismo que la cuenta aparte en Python;
  * sintaxis del Java nuevo.

Uso: python3 tools/verify_v380.py
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
NAME = "airliner"
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
need(re.search(r"version = '3\.(([8-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.8.0 o superior")
need(re.search(r"mod_version=3\.(([8-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.8.0 o superior")
need("V3.8.0" in read(ROOT / "README.md"), "README sin la entrada V3.8.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.8.0") and "PARCHE V3.7.0" in nuevos, "NUEVOS_BLOQUES debe empezar con la sección V3.8.0 y conservar la V3.7.0")
need(re.search(r'PROTOCOL = "(1[7-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 17 o más (entidad y paquete nuevos)")

# ---------------------------------------------------------------- lo que generan las herramientas
from tools.v380 import airliner as AL  # noqa: E402
from tools.v320 import paint_masks as PM  # noqa: E402
from tools.v360 import extra_regions as ER  # noqa: E402
from tools.v210 import vcommon as vc  # noqa: E402

b, tex, glow = AL.build_all()
got = np.asarray(Image.open(ENT / f"{NAME}.png").convert("RGBA"))
need(got.shape == np.asarray(tex).shape and (got == np.asarray(tex)).all(), f"{NAME}.png no es lo que genera tools/v380/airliner.py")
need(got.shape[:2] == (AL.TEX_H, AL.TEX_W), "la textura del avión debe medir 512x512")
AL.pose(b, 0.0, 0.0, 0.0)
need(read(JAVA / "client/AirlinerModel.java") == AL.java_model(b), "AirlinerModel.java no es lo que genera tools/v380/airliner.py")
need(len(b.cubes) >= 400, "el modelo tiene muy pocos cubos")
icon = vc.make_icon(b, tex, glow, (AL.TEX_W, AL.TEX_H), 140, 20, AL.CENTER, 1.6, outline=(8, 12, 30, 255), brighten=1.05)
got_icon = Image.open(ASSETS / f"textures/item/{NAME}.png").convert("RGBA")
need(got_icon.size == (32, 32) and (np.asarray(got_icon) == np.asarray(icon)).all(), "el ícono no es lo que genera el programa (o no mide 32x32)")
need(json.loads(read(ASSETS / f"models/item/{NAME}.json")) == {"parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{NAME}"}},
     "models/item/airliner.json mal")

ks, ref, worst = PM.measure(AL, AL)
mask = PM.to_png(ks, ref.shape)
got_mask = np.asarray(Image.open(ENT / f"{NAME}_paint.png").convert("RGB"))
need((got_mask == mask).all(), f"{NAME}_paint.png no es lo que calcula tools/v320/paint_masks.py")
need(worst < 0.01, f"el repintado del avión no es lineal (desvío {worst:.3f})")

rmask, refs, _ = ER.compute(NAME)
got_reg = np.asarray(Image.open(ENT / f"{NAME}_regions.png").convert("RGB"))
need((got_reg == rmask).all(), f"{NAME}_regions.png no es lo que calcula tools/v360/extra_regions.py")
table = {}
for name in ER.GROUPS:
    table[name] = ER.compute(name)[1]
need(read(JAVA / "entity/VehiclePaintExtras.java") == ER.java_source({k: list(v) for k, v in table.items()}), "VehiclePaintExtras.java no es lo que genera tools/v360/extra_regions.py")
need(NAME in table and 6 <= len(table[NAME]) <= 14 and table[NAME][-1][0] == "details", "partes de Otros del avión mal")

base = np.asarray(Image.open(ENT / f"{NAME}.png").convert("RGBA"))
alpha = base[..., 3] > 0
mainmask = (got_mask > 0).any(axis=2)
forbidden = np.zeros(alpha.shape, bool)
for c in ER.geometry(NAME):
    if c.mat in ER.FORBIDDEN_MATS[NAME]:
        for x0, y0, cw, ch in ER.face_rects(c):
            forbidden[y0:y0 + ch, x0:x0 + cw] = True
forbidden &= alpha & ~mainmask
need(forbidden.sum() > 300, "no se encontraron los neumáticos y las aspas del avión en la geometría")
need(not ((got_reg[..., 0] > 0) & forbidden).any(), "hay píxeles de neumático o de aspa con región (se podrían pintar)")
need(not ((got_reg[..., 0] > 0) & mainmask).any(), "un píxel de pintura/manijas también está en una región de Otros")
need(not (alpha & ~mainmask & ~forbidden & (got_reg[..., 0] == 0)).any(), "hay píxeles visibles que no son neumático ni aspa y no se pueden pintar")
need(int(mask[..., 0].astype(bool).sum()) > 5000 and int(mask[..., 1].astype(bool).sum()) >= 3, "la máscara de pintura o de manijas está casi vacía")
need(int(mask[..., 2].astype(bool).sum()) == 0, "el avión no tiene rines: el canal 3 de la máscara debe estar vacío")
notes.append(f"{NAME}: {len(table[NAME])} partes de Otros, {int(forbidden.sum())} píxeles de neumáticos/aspas sin cambio posible; atlas {AL.TEX_W}x{AL.TEX_H}, {len(b.cubes)} cubos")

# colores de referencia: Java = Python
vp = read(JAVA / "entity/VehiclePaint.java")
m = re.search(r'AIRLINER\("airliner",\s*0x([0-9A-F]{6}),\s*0x([0-9A-F]{6}),\s*ORIGINAL\)', vp)
need(m is not None, "VehiclePaint.Kind.AIRLINER no está definido (o no tiene rines originales)")
if m:
    want = [AL.REFS["body"], AL.REFS["handle"]]
    got_c = [tuple(int(m.group(i)[j:j + 2], 16) for j in (0, 2, 4)) for i in (1, 2)]
    need(got_c == want, f"colores de referencia distintos: Java {got_c} Python {want}")

# ---------------------------------------------------------------- asientos: 130, y sus posiciones salen del generador
al = read(JAVA / "entity/AirlinerEntity.java")
need(re.search(r"SEATS = 130;", al) is not None, "el avión debe tener 130 plazas")
xs_java = re.search(r"double\[\] xs = \{([^}]*)\}", al)
need(xs_java is not None, "no se encontró la lista de asientos de la fila")
if xs_java:
    xs_j = [float(v) for v in xs_java.group(1).split(",")]
    xs_g = []
    for _, x0, x1, _k in AL.SEAT_GROUPS:
        w = x1 - x0
        n_seats = w // 3
        xs_g += [x1 - 1.5 - 3 * i for i in range(n_seats)]
    need(sorted(xs_j) == sorted(xs_g) and len(xs_j) == 8, f"asientos de la fila: Java {xs_j} / generador {sorted(xs_g, reverse=True)}")
    need(xs_j == sorted(xs_j, reverse=True), "las x de los asientos deben ir de izquierda a derecha (x positiva = izquierda)")
need("(45 - 6 * row) / 4.0" in al and AL.CABIN_ZC[0] == 45 and AL.ROW_PITCH == 6 and len(AL.CABIN_ZC) == 16, "las filas del Java no coinciden con CABIN_ZC del generador")
need("SEAT_FEET_Y = 16.0 / 4.0 - 0.75" in al, "altura de los pies sentados distinta de la del generador (cojín arriba en y=16)")
px = (AL.PILOT_X[0] + AL.PILOT_X[1]) / 2 / 4
need("seats[0] = new double[]{2.5, SEAT_FEET_Y, 80.5 / 4.0};" in al and "seats[1] = new double[]{-2.5, SEAT_FEET_Y, 80.5 / 4.0};" in al, "asientos de piloto y copiloto distintos del generador")
need(abs(px - 2.5) < 1e-9, "el piloto debería estar en x = 2,5 bloques")
cush = [c for c in b.cubes if c.name.startswith("pilot_cush_")]
need(len(cush) == 2, "faltan los cojines de piloto y copiloto en el modelo")
seat_n = 2 + 16 * 8
need(seat_n == 130, "2 + 16 filas x 8 asientos no suma 130")

# ---------------------------------------------------------------- registros, idiomas, sonidos, teclas, paquete
ent = read(JAVA / "init/ModEntities.java")
need('AIRLINER = ENTITIES.register("airliner"' in ent and "AirlinerEntity::new" in ent and ".sized(7.0f, 7.5f)" in ent and ".fireImmune()" in ent, "ModEntities sin el avión")
items = read(JAVA / "init/ModItems.java")
need('AIRLINER = ITEMS.register("airliner"' in items and "ModEntities.AIRLINER::get" in items, "ModItems sin el avión")
need('"airliner"' in read(JAVA / "init/ModCreativeTabs.java"), "el avión no está en la pestaña de vehículos")
ce = read(JAVA / "client/ClientEvents.java")
need("ModEntities.AIRLINER.get(), AirlinerRenderer::new" in ce and "AirlinerModel.LAYER, AirlinerModel::createBodyLayer" in ce and "event.register(AirlinerKeys.GEAR)" in ce,
     "ClientEvents sin el renderizador, la capa del modelo o la tecla del tren")
rd = read(JAVA / "client/AirlinerRenderer.java")
need(f'"textures/entity/{NAME}.png"' in rd and "VehiclePaintTextures.textureFor" in rd, "AirlinerRenderer mal")
need("implements PaintableVehicle" in al and "VehiclePaint.Kind.AIRLINER" in al and "ModItems.AIRLINER" in al and "ModSounds.AIRLINER_START" in al, "AirlinerEntity mal conectada")
msg = read(JAVA / "network/ModMessages.java")
need("AirlinerActionPacket.class" in msg and "NetworkDirection.PLAY_TO_SERVER" in msg[msg.index("AirlinerActionPacket.class"):msg.index("AirlinerActionPacket.class") + 200], "paquete del avión sin registrar")
pk = read(JAVA / "network/AirlinerActionPacket.java")
need("ACTION_EXIT = 0" in pk and "ACTION_GEAR = 1" in pk and "requestExit" in pk and "requestGear" in pk, "AirlinerActionPacket mal")
need('GLFW_KEY_F' in read(JAVA / "client/AirlinerKeys.java") and "key.beautifuldecor.airliner_gear" in read(JAVA / "client/AirlinerKeys.java"), "la tecla del tren debe ser F")
hk = read(JAVA / "client/HelicopterKeys.java")
need("GLFW_KEY_Z" in hk, "la tecla de bajarse debe seguir siendo Z")
# la Z solo se consume en un lugar (si no, dos manejadores se la pelean)
consumers = [p.name for p in (JAVA / "client").glob("*.java") if "HelicopterKeys.EXIT.consumeClick()" in read(p)]
need(consumers == ["HelicopterClientEvents.java"], f"la tecla Z debe consumirse en un solo lugar (HelicopterClientEvents); ahora: {consumers}")
hce = read(JAVA / "client/HelicopterClientEvents.java")
need("new AirlinerActionPacket(airliner.getId(), AirlinerActionPacket.ACTION_EXIT)" in hce, "la Z no baja del avión")
ace = read(JAVA / "client/AirlinerClientEvents.java")
need("keySwapOffhand" in ace and "AirlinerKeys.GEAR.getKey()" in ace and "TickEvent.Phase.START" in ace, "la F no le quita el efecto de cambiar objeto de mano")
need("input.shiftKeyDown = false" in ace, "Shift debe dejar de bajar del avión")
need("AirlinerActionPacket.ACTION_GEAR" in ace, "la F no manda el paquete del tren")
need("AirlinerEntity" in read(JAVA / "client/VehicleRadioScreen.java"), "el avión debe tener el mismo aviso de «sin radio» que el helicóptero")
need("IN_WALL" in read(JAVA / "entity/AirlinerEvents.java") and "setCanceled(true)" in read(JAVA / "entity/AirlinerEvents.java"), "falta cancelar el daño de pared para los pasajeros")
need("getDismountLocationForPassenger" in al and "requestExit" in al and "requestGear" in al, "faltan métodos de bajar/tren en AirlinerEntity")

snd = json.loads(read(ASSETS / "sounds.json"))
ms = read(JAVA / "init/ModSounds.java")
en, es = json.loads(read(ASSETS / "lang/en_us.json")), json.loads(read(ASSETS / "lang/es_es.json"))
for kind in ("engine", "start"):
    key = f"{NAME}_{kind}"
    need(f'simple("{key}")' in ms, f"ModSounds sin {key}")
    need(key in snd and snd[key]["sounds"][0]["name"] == f"beautifuldecor:{key}" and snd[key]["subtitle"] == f"subtitles.beautifuldecor.{key}", f"sounds.json mal en {key}")
    ogg = ASSETS / f"sounds/{key}.ogg"
    need(ogg.exists() and ogg.stat().st_size > 2000, f"falta el sonido {key}.ogg")
    need(f"subtitles.beautifuldecor.{key}" in en and f"subtitles.beautifuldecor.{key}" in es, f"falta el subtítulo de {key}")
used_keys = set()
for p in JAVA.rglob("*.java"):
    used_keys |= set(re.findall(r'"((?:hud|key|item|entity)\.beautifuldecor\.[a-z_.]*airliner[a-z_.]*)"', read(p)))
need(len(used_keys) >= 11, f"el Java del avión usa pocas claves de idioma ({len(used_keys)})")
for k in sorted(used_keys | {f"item.beautifuldecor.{NAME}", f"entity.beautifuldecor.{NAME}"}):
    need(k in en and k in es and en[k].strip() and es[k].strip(), f"falta la clave de idioma {k}")
for key, _, _ in table[NAME]:
    k = f"gui.beautifuldecor.paint.{key}"
    need(k in en and k in es, f"falta la clave de idioma {k} (parte de Otros del avión)")
for k in ("hud.beautifuldecor.airliner_status", "hud.beautifuldecor.airliner_controls", "hud.beautifuldecor.airliner_passenger"):
    nph_en, nph_es = en[k].count("%s"), es[k].count("%s")
    need(nph_en == nph_es and nph_en >= 1, f"{k}: distinto número de %s en es y en")
need(en["hud.beautifuldecor.airliner_status"].count("%s") == 3, "el estado del HUD recibe altura, velocidad y tren")
need(set(en) == set(es), "es_es y en_us no tienen las mismas claves")

# ---------------------------------------------------------------- física, con las fórmulas de AirlinerEntity.flyTick
def const(name, kind="float"):
    mm = re.search(rf"\b{name} = (-?[0-9.]+)f?;", al)
    need(mm is not None, f"no se encontró la constante {name}")
    return float(mm.group(1)) if mm else 0.0


MAX_SPEED, TAKEOFF, MIN_AIR = const("MAX_SPEED"), const("TAKEOFF_SPEED"), const("MIN_AIR_SPEED")
CLIMB, DESCEND = const("CLIMB_SPEED"), const("DESCEND_SPEED")
need(abs(MAX_SPEED * 20 - 100.0) < 1e-6, "la velocidad máxima debe ser 5 bloques por tick = 100 por segundo")
need(re.search(r"this\.speed \+ 0\.03f \* thrust", al) is not None, "la aceleración cambió (0,03 por tick); actualiza la simulación de la prueba")
ACC = 0.03
v = d = 0.0
t_take = None
for t in range(1, 2000):
    v = min(MAX_SPEED, v + ACC)
    d += v
    if t_take is None and v >= TAKEOFF:
        t_take, d_take = t, d
        break
need(t_take is not None and 2.0 <= t_take / 20.0 <= 6.0, f"el despegue debería tardar entre 2 y 6 s en alcanzar {TAKEOFF * 20:.0f} bloques/s (tarda {t_take})")
need(d_take <= 150.0, f"la carrera de despegue es muy larga ({d_take:.0f} bloques)")
# ascenso: velY sube 0,03 por tick hacia CLIMB; de 0 a CLIMB en ~19 ticks. Cuántos bloques en 10 s con Espacio sostenido.
vy = y = 0.0
for t in range(200):
    vy += max(-0.05, min(0.03, CLIMB - vy))
    y += vy
need(y > 70, f"con Espacio sostenido 10 s debería ganar más de 70 bloques (gana {y:.0f})")
need(CLIMB * 20 <= 14 and DESCEND * 20 <= 14, "ascenso/descenso máximo razonable (<= 14 bloques/s)")
# cabeceo: de 0 a -14 grados a 0,45 por tick = ~31 ticks
need(re.search(r"PITCH_RATE = 0\.45f", al) and const("MAX_PITCH_UP") >= const("GROUND_PITCH_UP") > 0 and const("MAX_PITCH_DOWN") > 0, "constantes de cabeceo")
# frenado en tierra: -0,07 por tick desde el tope
vb, tb, db = MAX_SPEED, 0, 0.0
while vb > 0:
    vb = max(0.0, vb - 0.07)
    tb += 1
    db += vb
need(tb / 20.0 <= 6.0 and db <= 200, f"frenar con S en tierra tarda {tb / 20:.1f} s y {db:.0f} bloques")
need(const("GEAR_RETRACT_MIN_HEIGHT") >= 2.0 and const("EXIT_MAX_HEIGHT") >= 1.0, "alturas de tren/bajarse")
# curva a tope: grados por tick = lerp(1,8,0.9)... radio
rate_top = 0.9
radius = MAX_SPEED / math.radians(rate_top)
need(100 <= radius <= 600, f"radio de giro a velocidad máxima fuera de lo razonable ({radius:.0f})")
# tren: GEAR_SPEED 0,03 por tick -> tarda ~33 ticks (1,7 s)
need(abs(const("GEAR_SPEED") - 0.03) < 1e-9, "velocidad del tren")
notes.append(f"física: 0 a {TAKEOFF * 20:.0f} b/s en {t_take / 20:.1f} s y {d_take:.0f} bloques; 10 s de ascenso = {y:.0f} bloques; frenada en tierra {tb / 20:.1f} s / {db:.0f} bloques; curva a tope: radio {radius:.0f} bloques; tren en {1 / 0.03 / 20:.1f} s")

# ---------------------------------------------------------------- Java: sintaxis y repintado real
javac, java = shutil.which("javac"), shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}


def rgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def expected_png(news):
    """Repintado en Python: original + k * (nuevo - referencia). news = {canal: color}."""
    out = base[..., :3].astype(np.float64)
    for ch, new in news.items():
        if ch < 2:
            key = ("body", "handle")[ch]
            dd = np.array(new, float) - np.array(AL.REFS[key], float)
            out += (got_mask[..., ch] / 100.0)[..., None] * dd
    for i, (_, refc, _) in enumerate(table[NAME]):
        ch = 3 + i
        if ch in news:
            dd = np.array(news[ch], float) - np.array(rgb(refc), float)
            sel = got_reg[..., 0] == i + 1
            k = (got_reg[..., 1] / 100.0)[..., None]
            out = np.where(sel[..., None], out + k * dd, out)
    res = base.copy()
    res[..., :3] = np.where(alpha[..., None], np.clip(np.round(out), 0, 255), base[..., :3]).astype(np.uint8)
    return res


if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java")
else:
    work = Path(tempfile.mkdtemp(prefix="v380_"))
    try:
        out = work / "syntax"
        out.mkdir()
        names = ("entity/AirlinerEntity.java", "entity/AirlinerEvents.java", "network/AirlinerActionPacket.java", "item/AirlinerItem.java",
                 "client/AirlinerClientEvents.java", "client/AirlinerKeys.java", "client/AirlinerEngineSound.java", "client/AirlinerRenderer.java",
                 "client/AirlinerModel.java", "client/HelicopterClientEvents.java", "client/VehicleRadioScreen.java", "entity/VehiclePaint.java",
                 "entity/VehiclePaintExtras.java", "init/ModEntities.java", "init/ModItems.java", "init/ModSounds.java", "init/ModCreativeTabs.java",
                 "client/ClientEvents.java", "network/ModMessages.java")
        files = [JAVA / p for p in names]
        for f in files:
            need(f.exists(), f"falta el archivo {f.name}")
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files if f.exists()], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed",
            "method does not override", "cannot be referenced", "constructor", "cannot be inherited", "is not public", "has private access", "incomparable types"))]
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
        for ch in range(2):
            color = palette[(ch + 2) % len(palette)]
            exp = work / f"exp_main{ch}.png"
            Image.fromarray(expected_png({ch: rgb(color)}), "RGBA").save(exp)
            cases.append(f"EXTRA {NAME} {ch} {color:06X} {exp}")
        garish = [0xFF00FF, 0x00FFFF, 0xFFFF00, 0x00FF00, 0xFF0000, 0x0000FF]
        news = {ch: rgb(garish[ch % 6]) for ch in [0, 1] + [3 + i for i in range(n)]}
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
    print("FALLÓ la verificación V3.8.0:")
    for e in errors:
        print(" -", e)
    for nn in notes:
        print("  (nota)", nn)
    sys.exit(1)
print("OK V3.8.0: avión BOING 7G7 Gigante con 130 plazas, tren con F, bajarse con Z, colores, sonidos y textos; solo se verificó esta versión.")
for nn in notes:
    print("  (nota)", nn)
