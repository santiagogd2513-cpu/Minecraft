#!/usr/bin/env python3
"""Comprueba la V3.6.0: botón "Otros" en los menús de colores (vehículos y puerta de garaje).

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README, NUEVOS_BLOQUES, idiomas es/en y protocolo de red ("15" o más);
  * vehículos: las máscaras *_regions.png y la tabla VehiclePaintExtras.java son EXACTAMENTE lo que calcula tools/v360/extra_regions.py; cada píxel
    de neumático (terrestres) y de hélice o rotor (helicóptero) queda sin región; lo demás visible del vehículo tiene una región;
  * puerta de garaje: con los colores por defecto se ve igual que antes (capa por capa), las capas de detalle partidas no pierden ni repiten
    ningún píxel, los colores por defecto de Java coinciden con los del generador, y los modelos regenerados son los guardados;
  * todo conectado: datos sincronizados y NBT de las partes de Otros en las dos clases de entidad, texturas repintadas con la máscara de otros,
    menús con el botón "Otros" y su lista, paquetes con el protocolo nuevo;
  * la prueba (tools/v360/java/OthersTest.java) con el Java de verdad contra la cuenta de Python, y que muerde (mutantes);
  * sintaxis del Java tocado;
  * y corre lo de la V3.5.0 (tools/verify_v350.py).

Uso: python3 tools/verify_v360.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
sys.path.insert(0, str(ROOT))
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
ENT = ASSETS / "textures/entity"
errors = []
notes = []


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
need(re.search(r"version = '3\.(([6-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.6.0 o superior")
need(re.search(r"mod_version=3\.(([6-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.6.0 o superior")
need("V3.6.0" in read(ROOT / "README.md"), "README sin la entrada V3.6.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.6.0" in nuevos and "PARCHE V3.5.0" in nuevos,
     "NUEVOS_BLOQUES debe tener la sección V3.6.0 y conservar la V3.5.0")
need(re.search(r'PROTOCOL = "(1[5-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 15 o más (paquetes con más colores)")

# ---------------------------------------------------------------- vehículos: máscaras y tabla
from tools.v360 import extra_regions as ER  # noqa: E402
from tools.v320 import paint_masks as PM  # noqa: E402

table = {}
for name in ER.GROUPS:
    mask, refs, leftover = ER.compute(name)
    table[name] = refs
    committed = np.asarray(Image.open(ENT / f"{name}_regions.png").convert("RGB"))
    need(committed.shape == mask.shape and (committed == mask).all(), f"{name}_regions.png no es lo que calcula tools/v360/extra_regions.py")
    base = np.asarray(Image.open(ENT / f"{name}.png").convert("RGBA"))
    need(committed.shape[:2] == base.shape[:2], f"{name}_regions.png no mide lo mismo que la textura")
    alpha = base[..., 3] > 0
    need(not (committed[..., 0][~alpha] > 0).any(), f"{name}: hay regiones en píxeles transparentes")
    nreg = len(refs)
    need(committed[..., 0].max() == nreg, f"{name}: el número de regiones de la máscara no coincide con la tabla")
    need(nreg <= ER.MAX_REGIONS, f"{name}: demasiadas regiones")
    main = (np.asarray(Image.open(ENT / f"{name}_paint.png").convert("RGB")) > 0).any(axis=2)
    need(not ((committed[..., 0] > 0) & main).any(), f"{name}: un píxel de pintura, manijas o rines también está en una región de Otros")
    # lo que no se puede cambiar queda sin región
    cubes = ER.geometry(name)
    forbidden = np.zeros(alpha.shape, bool)
    for c in cubes:
        if c.mat in ER.FORBIDDEN_MATS[name]:
            for x0, y0, cw, ch in ER.face_rects(c):
                forbidden[y0:y0 + ch, x0:x0 + cw] = True
    forbidden &= alpha & ~main
    need(forbidden.sum() > 100, f"{name}: no se encontraron neumáticos ni hélices en la geometría")
    need(not ((committed[..., 0] > 0) & forbidden).any(), f"{name}: hay píxeles de neumático o hélice con región (se podrían cambiar)")
    # todo lo demás visible tiene región o ya es de pintura/manijas/rines
    uncovered = alpha & ~main & ~forbidden & (committed[..., 0] == 0)
    need(not uncovered.any(), f"{name}: {int(uncovered.sum())} píxeles visibles no se pueden cambiar y no son neumático ni rotor")
    # k razonable
    k = committed[..., 1][committed[..., 0] > 0]
    need(k.size > 0 and k.max() <= 255, f"{name}: k fuera de rango")
    notes.append(f"{name}: {nreg} partes de Otros; sin cambio posible solo {int(forbidden.sum())} píxeles de {'neumáticos' if name != 'helicopter' else 'hélices y rotor'}")
    Image.fromarray((forbidden * 255).astype(np.uint8), "L").save(Path(tempfile.gettempdir()) / f"v360_forbidden_{name}.png")

need(read(JAVA / "entity/VehiclePaintExtras.java") == ER.java_source({k: list(v) for k, v in table.items()}),
     "VehiclePaintExtras.java no es lo que genera tools/v360/extra_regions.py")

# ---------------------------------------------------------------- puerta de garaje
from tools.v230 import garage_colors as GC  # noqa: E402
from tools.v200 import garage_door as G  # noqa: E402

gcj = read(JAVA / "block/GarageColors.java")


def hexcolor(rgb):
    return "0x%02X%02X%02X" % tuple(rgb)


for const, rgb in (("DEFAULT_WINDOW", GC.D_WINDOW), ("DEFAULT_WINDOW_INSIDE", GC.D_WINDOW_INSIDE), ("DEFAULT_HANDLE", GC.D_HANDLE),
                   ("DEFAULT_SEAL", GC.D_SEAL), ("DEFAULT_HARDWARE", GC.D_HARDWARE), ("DEFAULT_VENTS", GC.D_VENTS)):
    need(f"{const} = {hexcolor(rgb)};" in gcj, f"GarageColors.{const} no coincide con el generador ({hexcolor(rgb)})")
for const, rgb in (("DEFAULT_BODY", GC.D_BODY), ("DEFAULT_ACCENT", GC.D_ACCENT), ("DEFAULT_FRAME", GC.D_FRAME), ("DEFAULT_INSIDE", GC.D_INSIDE)):
    need(f"{const} = {hexcolor(rgb)};" in gcj, f"GarageColors.{const} cambió")

# las capas de detalle partidas: ni un píxel perdido ni repetido
det_out = GC.detail_mask_out() & ~GC.accent_mask(lambda: G.leaf_texture(True))
det_in = GC.detail_mask_in()
det_hdr = GC.detail_mask_hdr() & ~GC.accent_mask(G.header_texture)
win, seal, hdl = GC.window_mask(), GC.seal_mask(), GC.handle_mask()
parts_out = [det_out & win, det_out & hdl, det_out & seal]
parts_in = [det_in & win, det_in & ~win & ~seal, det_in & seal]
parts_hdr = [det_hdr & ~GC.indicator_mask(), det_hdr & GC.indicator_mask()]
for label, whole, parts in (("fuera", det_out, parts_out), ("dentro", det_in, parts_in), ("cajón", det_hdr, parts_hdr)):
    total = np.zeros(whole.shape, int)
    for p in parts:
        total += p.astype(int)
    need(total.max() <= 1, f"capas de detalle ({label}): un píxel está en dos capas")
    need(((total > 0) == whole).all(), f"capas de detalle ({label}): faltan o sobran píxeles respecto al detalle de siempre")

# con los colores por defecto la puerta se ve igual que antes (la misma prueba de la V2.3.0, ahora con las capas de Otros)
leaf, hdr, jamb = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME)
inside, _, _ = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME, "in")
for label, got, want in (("hoja", leaf, G.leaf_texture(True)), ("hoja interior", inside, G.leaf_texture(False)),
                         ("cajón", hdr, G.header_texture()), ("riel", jamb, G.jamb_texture())):
    ref = np.asarray(want.convert("RGB")).astype(float)
    diff = np.abs(np.round(got) - ref)
    need(int((diff > 2).sum()) <= max(8, int(0.005 * ref.size)), f"la {label} con colores por defecto cambió")
    need(diff.max() <= 12, f"la {label} con colores por defecto cambió (diferencia máxima {diff.max():.0f})")
# con colores de Otros cambia justo lo que debe: ventanas cambian las ventanas y nada más
custom, _, _ = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME, "out", {"window": (250, 20, 20)})
changed = (np.abs(np.round(custom) - np.round(leaf)).max(axis=2) > 2)
need(changed.any() and not (changed & ~(det_out & win)).any(), "cambiar las ventanas debe cambiar solo los píxeles de las ventanas")
for key, mask_ in (("handle", det_out & hdl), ("seal", det_out & seal)):
    c2, _, _ = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME, "out", {key: (250, 20, 20)})
    ch2 = (np.abs(np.round(c2) - np.round(leaf)).max(axis=2) > 2)
    need(ch2.any() and not (ch2 & ~mask_).any(), f"cambiar '{key}' debe cambiar solo sus píxeles")
c3, _, _ = GC.composite(GC.D_BODY, GC.D_ACCENT, GC.D_FRAME, "in", {"hardware": (250, 20, 20)})
ch3 = (np.abs(np.round(c3) - np.round(inside)).max(axis=2) > 2)
need(ch3.any() and not (ch3 & ~(det_in & ~win & ~seal)).any(), "cambiar las bisagras y rodillos debe cambiar solo sus píxeles")

# texturas y modelos
TEXDIR = ASSETS / "textures/block"
for key, fname in GC.FILES.items():
    need((TEXDIR / f"{fname}.png").exists(), f"falta la textura {fname}")
for old in GC.OLD_FILES:
    need(not (TEXDIR / f"{old}.png").exists(), f"quedó la capa vieja {old}.png sin usar")
for fname, im in GC.layers().items():
    got = np.asarray(Image.open(TEXDIR / f"{fname}.png").convert("RGBA"))
    need((got == np.asarray(im)).all(), f"la textura {fname} no es lo que genera tools/v230/garage_colors.py")

blockstate = json.loads(read(ASSETS / "blockstates/garage_door.json"))["multipart"]
names_in_state = {p["apply"]["model"].split("/")[1] for p in blockstate}
files = {p.stem for p in (ASSETS / "models/block").glob("garage_door_p*_r*.json")}
need(names_in_state == files, "garage_door: los modelos del blockstate no coinciden con los archivos")
seen = {}
for part in range(9):
    for r in range(G.RAISE_MAX + 1):
        data = GC.to_json(GC.build_part(part, r))
        h = json.dumps(data, sort_keys=True)
        if h not in seen:
            seen[h] = G.model_name(part, r)
            need(seen[h] in files, f"falta el modelo {seen[h]}")
            if seen[h] in files:
                need(json.loads(read(ASSETS / f"models/block/{seen[h]}.json")) == data, f"el modelo {seen[h]} no es lo que genera el programa")
need(len(seen) == len(files), f"sobran modelos de la puerta ({len(files)} archivos, {len(seen)} distintos)")
tinted_keys = set()
for name in sorted(files):
    m = json.loads(read(ASSETS / f"models/block/{name}.json"))
    need(m.get("render_type") == "minecraft:cutout", f"{name}: debe usar render_type cutout")
    textures = m.get("textures", {})
    for ref in textures.values():
        need((ASSETS / "textures" / (ref.split(":")[1] + ".png")).exists(), f"{name}: falta la textura {ref}")
    for el in m.get("elements", []):
        for fn, face in el["faces"].items():
            key = face["texture"][1:]
            need(key in textures, f"{name}: textura {key} sin definir")
            if key in GC.TINT:
                need(face.get("tintindex") == GC.TINT[key], f"{name}: la cara {fn} ({key}) debe tener tintindex {GC.TINT[key]}")
                tinted_keys.add(key)
            else:
                need("tintindex" not in face, f"{name}: la cara {fn} ({key}) no debe tener tinte")
for key in ("ow", "iw", "oh", "ih", "os", "is", "hv"):
    need(key in tinted_keys, f"ningún modelo usa la capa de Otros '{key}'")
need(set(GC.TINT.values()) == set(range(10)), "los tintindex de la puerta deben ser 0..9")

# ---------------------------------------------------------------- lo conectado
ve, he = read(JAVA / "entity/VehicleEntity.java"), read(JAVA / "entity/HelicopterEntity.java")
for label, text, cls in (("VehicleEntity", ve, "VehicleEntity"), ("HelicopterEntity", he, "HelicopterEntity")):
    need(f"PAINT_EXTRA =" in text and "EntityDataSerializers.STRING" in text.split("PAINT_EXTRA =")[1].split(";")[0], f"{label}: dato sincronizado PAINT_EXTRA (texto)")
    need("this.entityData.define(PAINT_EXTRA, PaintExtras.EMPTY)" in text, f"{label}: define PAINT_EXTRA")
    need("this.paintExtras.get(this.entityData.get(PAINT_EXTRA)" in text and "PaintExtras.with(this.entityData.get(PAINT_EXTRA)" in text,
         f"{label}: getPaintRaw/setPaintRaw deben leer y escribir las partes de Otros")
    need("VehiclePaint.read(this, tag)" in text and "VehiclePaint.write(this, tag)" in text, f"{label}: las partes de Otros se guardan en el NBT")
vp = read(JAVA / "entity/VehiclePaint.java")
need("CHANNELS = MAIN + VehiclePaintExtras.MAX" in vp, "VehiclePaint: CHANNELS = 3 + partes de Otros")
tex = read(JAVA / "client/VehiclePaintTextures.java")
need("_regions.png" in tex and "layers.regions()" in tex and "keyBuilder.append(':').append(colors[ch])" in tex, "VehiclePaintTextures: usa la máscara de Otros y la clave incluye todos los colores")
pm = read(JAVA / "client/PaintMath.java")
need("MAIN + region - 1" in pm and "int[] regions" in pm, "PaintMath: usa las regiones")
pk = read(JAVA / "network/VehiclePaintPacket.java")
need("VehiclePaint.CHANNELS" in pk, "VehiclePaintPacket debe mandar todos los canales")
vs = read(JAVA / "client/VehicleColorScreen.java")
for needle in ("gui.beautifuldecor.vehicle.others", "setListOpen", "renderList", "listChannels", "gui.beautifuldecor.paint.", "this.hexBox.visible"):
    need(needle in vs, f"VehicleColorScreen: falta {needle}")
need("keyCode == 256" in vs, "VehicleColorScreen: Esc con la lista abierta solo la cierra")
gs = read(JAVA / "client/GarageColorScreen.java")
for needle in ("gui.beautifuldecor.garage.others", "setListOpen", "renderList", "GarageColors.slotKey", "this.hexBox.visible", "GarageColors.HARDWARE"):
    need(needle in gs, f"GarageColorScreen: falta {needle}")
need("keyCode == 256" in gs, "GarageColorScreen: Esc con la lista abierta solo la cierra")
need("GarageColors.SLOTS" in read(JAVA / "network/GarageColorPacket.java"), "GarageColorPacket manda todas las ranuras")
need("GarageColors.SLOTS" in read(JAVA / "block/entity/GarageDoorBlockEntity.java"), "GarageDoorBlockEntity guarda todas las ranuras")
need("GarageColors.defaults()" in read(JAVA / "block/entity/GarageDoorBlockEntity.java") and "i < saved.length ? saved[i] : defaults[i]" in read(JAVA / "block/entity/GarageDoorBlockEntity.java"),
     "las puertas de antes (con 3 colores) deben recibir los demás por defecto")

# ---------------------------------------------------------------- idiomas
en = json.loads(read(ASSETS / "lang/en_us.json"))
es = json.loads(read(ASSETS / "lang/es_es.json"))
keys = ["gui.beautifuldecor.vehicle.others", "gui.beautifuldecor.vehicle.editing", "gui.beautifuldecor.garage.others", "gui.beautifuldecor.garage.editing",
        "gui.beautifuldecor.garage.window", "gui.beautifuldecor.garage.handle", "gui.beautifuldecor.garage.seal", "gui.beautifuldecor.garage.hardware",
        "gui.beautifuldecor.garage.vents"]
used_parts = {k for refs in table.values() for k, _, _ in refs}
keys += ["gui.beautifuldecor.paint." + k for k in sorted(used_parts)]
for k in keys:
    need(k in en and k in es and en[k].strip() and es[k].strip(), f"falta la clave de idioma {k}")
need(en.get("gui.beautifuldecor.vehicle.editing", "").count("%s") == 1 and es.get("gui.beautifuldecor.vehicle.editing", "").count("%s") == 1, "vehicle.editing debe tener un %s")
need(en.get("gui.beautifuldecor.garage.editing", "").count("%s") == 1 and es.get("gui.beautifuldecor.garage.editing", "").count("%s") == 1, "garage.editing debe tener un %s")
for k in ("gui.beautifuldecor.vehicle.others", "gui.beautifuldecor.garage.others"):
    need(es.get(k) == "Otros", f"{k} en español debe decir 'Otros'")
for k in en:
    if k.startswith("gui.beautifuldecor.paint."):
        need(k in es, f"{k} falta en español")
        need(k[len("gui.beautifuldecor.paint."):] in used_parts, f"{k} no lo usa ningún vehículo")

# ---------------------------------------------------------------- pruebas y Java
javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}


def expected_png(name, news):
    """La cuenta del repintado en Python, aparte del Java: original + k * (nuevo - referencia). news = {canal: color}."""
    base = np.asarray(Image.open(ENT / f"{name}.png").convert("RGBA"))
    main = np.asarray(Image.open(ENT / f"{name}_paint.png").convert("RGB")).astype(float)
    regions = np.asarray(Image.open(ENT / f"{name}_regions.png").convert("RGB"))
    refs = {i: rc for i, rc in enumerate(ER_MAIN[name].values())} if False else None
    out = base[..., :3].astype(np.float64)
    ref_main = {}
    for i, ch in enumerate(("body", "handle", "rim")):
        if ch in PM.VEHICLES[name][1].REFS:
            ref_main[i] = PM.VEHICLES[name][1].REFS[ch]
    for ch, new in news.items():
        if ch in ref_main:
            d = np.array(new, float) - np.array(ref_main[ch], float)
            out += (main[..., ch] / 100.0)[..., None] * d
    n = len(table[name])
    for i in range(n):
        ch = 3 + i
        if ch in news:
            ref = table[name][i][1]
            d = np.array(news[ch], float) - np.array([(ref >> 16) & 255, (ref >> 8) & 255, ref & 255], float)
            sel = regions[..., 0] == i + 1
            k = (regions[..., 1] / 100.0)[..., None]
            out = np.where(sel[..., None], out + k * d, out)
    res = base.copy()
    visible = base[..., 3] > 0
    res[..., :3] = np.where(visible[..., None], np.clip(np.round(out), 0, 255), base[..., :3]).astype(np.uint8)
    return res


def rgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v360_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files_ = [JAVA / p for p in ("entity/VehiclePaint.java", "entity/VehiclePaintExtras.java", "entity/PaintExtras.java", "entity/VehicleEntity.java",
                                     "entity/HelicopterEntity.java", "client/PaintMath.java", "client/VehiclePaintTextures.java", "client/VehicleColorScreen.java",
                                     "client/GarageColorScreen.java", "block/GarageColors.java", "block/entity/GarageDoorBlockEntity.java",
                                     "network/GarageColorPacket.java", "network/VehiclePaintPacket.java", "network/ModMessages.java")]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files_], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed",
            "method does not override"))]
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        # casos del repintado: cada parte de cada vehículo por separado, todo junto, y lo que nunca debe cambiar
        cases = []
        palette = [0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0x101010, 0xF0F0F0]
        for name in ER.GROUPS:
            n = len(table[name])
            for i in range(n):
                color = palette[i % len(palette)]
                exp = work / f"exp_{name}_{i}.png"
                Image.fromarray(expected_png(name, {3 + i: rgb(color)}), "RGBA").save(exp)
                cases.append(f"EXTRA {name} {3 + i} {color:06X} {exp}")
            news = {}
            for i, ch in enumerate(("body", "handle", "rim")):
                if ch in PM.VEHICLES[name][1].REFS:
                    news[i] = None
            chans = sorted(news) + [3 + i for i in range(n)]
            garish = [0xFF00FF, 0x00FFFF, 0xFFFF00, 0x00FF00, 0xFF0000, 0x0000FF]
            news = {ch: rgb(garish[ch % 6]) for ch in chans}
            exp = work / f"exp_{name}_all.png"
            Image.fromarray(expected_png(name, news), "RGBA").save(exp)
            cases.append(f"ALL {name} {exp}")
            fb = Path(tempfile.gettempdir()) / f"v360_forbidden_{name}.png"
            cases.append(f"FORBIDDEN {name} {fb}")
        (work / "cases.txt").write_text("\n".join(cases) + "\n", encoding="utf-8")

        base_srcs = [str(p) for p in (ROOT / "tools/v320/teststub").rglob("*.java")]
        base_srcs += [str(JAVA / p) for p in ("entity/VehiclePaint.java", "entity/VehiclePaintExtras.java", "entity/PaintExtras.java", "entity/PaintableVehicle.java",
                                              "client/PaintMath.java", "block/GarageColors.java")]
        test_src = str(ROOT / "tools/v360/java/OthersTest.java")

        def run_test(tag, overrides=None, limit=240):
            files2 = [overrides.get(f, f) if overrides else f for f in base_srcs]
            outp = work / f"out_{tag}"
            outp.mkdir()
            c = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all,-serial", "-d", str(outp)] + files2 + [test_src], env=env, cwd=work)
            if c.returncode != 0 or "warning:" in c.stderr:
                return None, "no compila:\n" + c.stderr[-2000:]
            try:
                res = run([java, "-cp", str(outp), "com.example.beautifuldecor.entity.OthersTest", str(ENT), str(work / "cases.txt")], env=env, cwd=work, timeout=limit)
            except subprocess.TimeoutExpired:
                return None, f"la prueba no termina en {limit} s"
            return res, (res.stdout + res.stderr)[-2500:]

        res, text = run_test("real")
        need(res is not None and res.returncode == 0 and "OTHERS OK" in res.stdout, "la prueba de Otros falló:\n" + text)
        if res is not None and res.returncode == 0:
            for line in res.stdout.strip().splitlines():
                notes.append(line.strip())

        def mutant(tag, rel, old, new):
            src_path = JAVA / rel
            src = read(src_path)
            if old not in src:
                errors.append(f"mutante {tag}: no se encontró el texto a cambiar")
                return
            d = work / f"mut_{tag}"
            d.mkdir()
            f = d / src_path.name
            f.write_text(src.replace(old, new, 1), encoding="utf-8")
            rm, _ = run_test(tag, {str(src_path): str(f)})
            need(rm is not None and rm.returncode != 0, f"la prueba NO se dio cuenta del mutante '{tag}' (debía fallar)")

        mutant("region_corrida", "client/PaintMath.java", "int ch = MAIN + region - 1;", "int ch = MAIN + region;")
        mutant("k_de_otro_byte", "client/PaintMath.java", "int kb = (rm >> 8) & 255;", "int kb = (rm >> 16) & 255;")
        mutant("sin_regiones", "client/PaintMath.java", "if (ch < channels && kb != 0 && active[ch]) {\n                    float k = kb / SCALE;\n                    r += k * delta[ch][0];\n                    g += k * delta[ch][1];\n                    b += k * delta[ch][2];\n                }\n            }\n            out[i]",
               "if (false) {\n                    float k = kb / SCALE;\n                    r += k * delta[ch][0];\n                    g += k * delta[ch][1];\n                    b += k * delta[ch][2];\n                }\n            }\n            out[i]")
        mutant("texto_sin_vacio", "entity/PaintExtras.java", "return any ? sb.toString() : EMPTY;", "return sb.toString();")
        mutant("with_corrido", "entity/PaintExtras.java", "values[index] = value < 0 ? VehiclePaint.ORIGINAL : value & 0xFFFFFF;", "values[(index + 1) % values.length] = value < 0 ? VehiclePaint.ORIGINAL : value & 0xFFFFFF;")
        mutant("parte_de_mas", "entity/VehiclePaint.java", "channel - EXTRA_FIRST < this.extraKeys.length;", "channel - EXTRA_FIRST <= this.extraKeys.length;")
        mutant("ventanas_sin_aclarar", "block/GarageColors.java", "Math.round(c + (255 - c) * f)", "Math.round(c + (255 - c) * 0.0)")
        mutant("manija_con_el_sello", "block/GarageColors.java", "case 6 -> colors[HANDLE] & 0xFFFFFF;", "case 6 -> colors[SEAL] & 0xFFFFFF;")
        mutant("neumatico_en_la_tabla", "entity/VehiclePaintExtras.java", '"plate", "details"}', '"plate", "tire"}')
        notes.append("la prueba de Otros falla con los 9 mutantes (región corrida, k de otro byte, sin regiones, texto sin vacío, canal corrido, una parte de más, "
                     "ventanas de dentro sin aclarar, manija con el color del sello, un neumático en la tabla)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for bad_ in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad_.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if "--fast" not in sys.argv:
    rr = run([sys.executable, str(ROOT / "tools/verify_v350.py")], env=env, timeout=7200)
    need(rr.returncode == 0 and "OK V3.5.0" in rr.stdout, "verify_v350 falló:\n" + (rr.stdout + rr.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.6.0:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.6.0: botón \"Otros\" con todos los colores de los vehículos (menos neumáticos y hélices) y de la puerta de garaje.")
for n in notes:
    print("  (nota)", n)
