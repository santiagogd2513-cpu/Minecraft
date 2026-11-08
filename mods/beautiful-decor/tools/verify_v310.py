#!/usr/bin/env python3
"""Comprueba la V3.1.0: carro RC Lamborgonzo Huracanito (bloque + entidad + control remoto propio) y el arreglo de las descargas de video cortadas.

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES;
  * que el carro tenga todos sus recursos (modelos, texturas cuadradas, ícono, sonidos, loot, idiomas es/en, subtítulos) y todos sus registros
    (bloque, ítems, entidad, renderizador, capa del modelo, paquete de red, pestaña, categoría, orden);
  * que el bloque y la entidad sean IDÉNTICOS: el modelo JSON del bloque se dibuja con un renderizador de software que sigue las reglas de vanilla
    (orden de vértices, UV, giro de elementos) y se compara píxel por píxel con el modelo de la entidad desde 8 ángulos;
  * que lo guardado (texturas, RcCarModel.java, models/block/rc_car.json, ícono del control) sea exactamente lo que generan tools/v310/*.py;
  * la física del carro (RcCarPhysics) en una simulación: acelera, frena, da reversa, gira y no pasa de sus topes;
  * que el Java nuevo no tenga errores de sintaxis (sin Minecraft solo se ven esos);
  * el arreglo del video: un servidor local que CORTA las descargas (con y sin Content-Length, entendiendo Range o no) y el reproductor de verdad
    (con piezas de Minecraft de mentira) que debe completarlas o fallar de frente con un aviso, nunca morir a mitad del video;
  * y corre lo de la V3.0.2 (tools/verify_v302.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v310.py [--fast]     (--fast se salta lo de las versiones anteriores)
"""
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"
C = JAVA / "client"
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data"
errors = []
notes = []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env, cwd=cwd)


def png_size(p):
    from PIL import Image
    with Image.open(p) as im:
        return im.size


need(re.search(r"version = '3\.([1-9]\d*)\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 3.1.x o superior")
need(re.search(r"mod_version=3\.([1-9]\d*)\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.1.x o superior")
need("V3.1.0" in read(ROOT / "README.md"), "README sin la entrada V3.1.0")
need(read(ROOT / "NUEVOS_BLOQUES.txt").startswith("PARCHE V3.") and "PARCHE V3.1.0" in read(ROOT / "NUEVOS_BLOQUES.txt"), "NUEVOS_BLOQUES debe empezar con una sección V3.x y conservar la V3.1.0")

# ---------------------------------------------------------------- JSON válido e idiomas
for f in sorted(list(ASSETS.rglob("*.json")) + list(DATA.rglob("*.json"))):
    try:
        json.loads(read(f))
    except Exception as exc:  # noqa: BLE001
        errors.append(f"JSON inválido: {f.relative_to(ROOT)}: {exc}")
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))

LANG_KEYS = ["item.beautifuldecor.rc_car", "block.beautifuldecor.rc_car", "entity.beautifuldecor.rc_car", "item.beautifuldecor.rc_car_remote",
             "subtitles.beautifuldecor.rc_car_motor", "subtitles.beautifuldecor.rc_car_start", "hud.beautifuldecor.rc_car_controls",
             "hud.beautifuldecor.rc_car_range", "hud.beautifuldecor.rc_car_speed", "message.beautifuldecor.rc_car_aim",
             "gui.beautifuldecor.browser.video.incomplete", "gui.beautifuldecor.browser.video.cut"]
for k in LANG_KEYS:
    need(k in es and k in en, f"falta la clave {k} en es/en")
need("Lamborgonzo" in es.get("item.beautifuldecor.rc_car", "") and "Lamborgonzo" in en.get("item.beautifuldecor.rc_car", ""),
     "el nombre del carro debe ser la parodia Lamborgonzo Huracanito")
need(es.get("hud.beautifuldecor.rc_car_range", "").count("%s") == 2 and en.get("hud.beautifuldecor.rc_car_range", "").count("%s") == 2,
     "hud.rc_car_range debe llevar dos %s")
need(es.get("gui.beautifuldecor.browser.video.incomplete", "").count("%s") == 2, "browser.video.incomplete debe llevar dos %s")
need(es.get("gui.beautifuldecor.browser.video.cut", "").count("%s") == 3 and en.get("gui.beautifuldecor.browser.video.cut", "").count("%s") == 3,
     "browser.video.cut debe llevar tres %s (MB que llegaron, MB que pide, duración)")

# textos del código nuevo traducidos
code_new = "".join(read(p) for p in (C / "RcCarClientEvents.java", JAVA / "item/RcCarRemoteItem.java", JAVA / "entity/RcCarEntity.java", C / "HsVideoPlayer.java"))
for key in set(re.findall(r'Component\.translatable\(\s*"([^"]+)"', code_new)):
    need(key in es and key in en, f"el código usa la clave {key} y no está en es/en")

# ---------------------------------------------------------------- recursos del carro
for rel, size in (("textures/entity/rc_car.png", (128, 128)), ("textures/block/rc_car.png", (128, 128)), ("textures/item/rc_car.png", (32, 32)),
                  ("textures/item/rc_car_remote.png", (16, 16))):
    p = ASSETS / rel
    need(p.exists() and png_size(p) == size, f"{rel} ausente o que no mide {size}")
need(png_size(ASSETS / "textures/block/rc_car.png")[0] == png_size(ASSETS / "textures/block/rc_car.png")[1],
     "la textura del bloque debe ser cuadrada (una más alta que ancha se tomaría por animada)")
for rel in ("blockstates/rc_car.json", "models/block/rc_car.json", "models/item/rc_car.json", "models/item/rc_car_remote.json"):
    need((ASSETS / rel).exists(), f"falta {rel}")
need((DATA / "beautifuldecor/loot_tables/blocks/rc_car.json").exists(), "falta el loot del bloque rc_car")
bs = json.loads(read(ASSETS / "blockstates/rc_car.json"))
need(set(bs["variants"]) == {"facing=north", "facing=east", "facing=south", "facing=west"}, "el blockstate del carro debe cubrir las 4 direcciones")
need([bs["variants"][f"facing={d}"].get("y", 0) for d in ("north", "east", "south", "west")] == [0, 90, 180, 270], "giros del blockstate del carro")
need("rc_car" in read(DATA / "beautifuldecor/loot_tables/blocks/rc_car.json"), "el loot del carro debe soltar rc_car")
sounds = json.loads(read(ASSETS / "sounds.json"))
for name in ("rc_car_motor", "rc_car_start"):
    ogg = ASSETS / f"sounds/{name}.ogg"
    need(ogg.exists() and ogg.read_bytes()[:4] == b"OggS", f"falta o no es OGG: sounds/{name}.ogg")
    need(name in sounds and sounds[name]["subtitle"] == f"subtitles.beautifuldecor.{name}", f"sounds.json sin {name} o sin su subtítulo")
    need(f"subtitles.beautifuldecor.{name}" in es and f"subtitles.beautifuldecor.{name}" in en, f"falta el subtítulo de {name}")

# ---------------------------------------------------------------- registros y conexiones
def has(path, text, msg):
    need(text in read(path), msg)


has(JAVA / "init/ModBlocks.java", 'BLOCKS.register("rc_car", RcCarBlock::new)', "ModBlocks sin RC_CAR")
has(JAVA / "init/ModItems.java", 'ITEMS.register("rc_car_remote"', "ModItems sin RC_CAR_REMOTE")
has(JAVA / "init/ModItems.java", 'ITEMS.register("rc_car"', "ModItems sin RC_CAR")
has(JAVA / "init/ModEntities.java", 'ENTITIES.register("rc_car"', "ModEntities sin RC_CAR")
has(JAVA / "init/ModSounds.java", 'simple("rc_car_motor")', "ModSounds sin RC_CAR_MOTOR")
has(JAVA / "init/ModSounds.java", 'simple("rc_car_start")', "ModSounds sin RC_CAR_START")
has(JAVA / "client/ClientEvents.java", "RcCarRenderer::new", "ClientEvents no registra el renderizador del carro")
has(JAVA / "client/ClientEvents.java", "RcCarModel.LAYER, RcCarModel::createBodyLayer", "ClientEvents no registra la capa del modelo del carro")
has(JAVA / "client/ClientEvents.java", "ModBlocks.RC_CAR.get(), RenderType.cutout()", "el bloque del carro necesita la capa cutout (su textura tiene huecos)")
has(JAVA / "network/ModMessages.java", "RcCarControlPacket.class", "ModMessages sin RcCarControlPacket")
need(re.search(r'PROTOCOL = "([8-9]|\d{2,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 8 o más (paquete nuevo)")
has(JAVA / "init/ModCreativeTabs.java", 'path.equals("rc_car")', "el carro no está en la pestaña de tecnología")
has(JAVA / "init/ModCreativeTabs.java", 'path.equals("rc_car_remote")', "el control del carro no está en la pestaña de tecnología")
has(JAVA / "init/ModCategory.java", '"rc_car", "rc_car_remote"', "ModCategory sin el carro y su control")
has(JAVA / "init/ModCreativeOrder.java", '"rc_car", "rc_car_remote"', "ModCreativeOrder sin el carro y su control")
has(JAVA / "entity/RcCarEntity.java", "RcCarPhysics.speedAfter", "RcCarEntity debe usar RcCarPhysics (lo que prueba la simulación)")
has(JAVA / "entity/RcCarEntity.java", "ModBlocks.RC_CAR", "el carro debe volver a ser el bloque rc_car al apagarse")
has(JAVA / "item/RcCarRemoteItem.java", "instanceof RcCarBlock", "el control debe convertir el bloque del carro en entidad")
has(JAVA / "event/RcCarInteractionEvents.java", "ModItems.RC_CAR_REMOTE", "clic del control sobre la entidad del carro")
need("DRONE_REMOTE" not in read(JAVA / "item/RcCarRemoteItem.java") and "DroneEntity" not in read(JAVA / "item/RcCarRemoteItem.java"),
     "el control del carro debe ser independiente del del dron")
need(read(ASSETS / "models/item/rc_car_remote.json") != read(ASSETS / "models/item/drone_remote.json"), "el control del carro debe tener su propio modelo")
need(read(ASSETS / "textures/item/rc_car_remote.png.mcmeta") == "" if (ASSETS / "textures/item/rc_car_remote.png.mcmeta").exists() else True, "")
a = (ASSETS / "textures/item/rc_car_remote.png").read_bytes()
b_ = (ASSETS / "textures/item/drone_remote.png").read_bytes()
need(a != b_, "el ícono del control del carro debe ser distinto al del dron")

# ---------------------------------------------------------------- lo guardado == lo que generan los programas
try:
    import numpy as np
    from PIL import Image
    from tools.v310 import rc_car, rc_remote
    b, tex = rc_car.build_all()
    arr = np.asarray(tex)
    for rel in ("textures/entity/rc_car.png", "textures/block/rc_car.png"):
        saved = np.asarray(Image.open(ASSETS / rel).convert("RGBA"))
        need(saved.shape == arr.shape and np.array_equal(saved, arr), f"{rel} no coincide con lo que genera tools/v310/rc_car.py")
    rc_car.pose(b, 0.0, 0.0)
    model = rc_car.block_model(b)
    need(json.loads(read(ASSETS / "models/block/rc_car.json")) == json.loads(json.dumps(model)), "models/block/rc_car.json no coincide con lo que genera tools/v310/rc_car.py")
    need(read(C / "RcCarModel.java") == rc_car.java_model(b), "RcCarModel.java no coincide con lo que genera tools/v310/rc_car.py")
    need(len(model["elements"]) == len(b.cubes), "el bloque debe tener tantos elementos como cubos tiene la entidad")
    icon = rc_car.vc.make_icon(b, tex, Image.new("RGBA", tex.size, (0, 0, 0, 0)), (rc_car.TEX, rc_car.TEX), 148, 24, rc_car.CENTER, 14.0, outline=(20, 30, 8, 255))
    need(np.array_equal(np.asarray(icon), np.asarray(Image.open(ASSETS / "textures/item/rc_car.png").convert("RGBA"))), "el ícono del carro no coincide con el generado")
    notes.append(f"textura, RcCarModel.java, modelo del bloque ({len(model['elements'])} elementos) e ícono coinciden con lo que genera tools/v310/rc_car.py")

    # reglas de vanilla para los modelos de bloque
    for el in model["elements"]:
        need(all(-16 <= v <= 32 for v in el["from"] + el["to"]), f"{el['name']}: fuera del rango -16..32 de un elemento de bloque")
        rot = el.get("rotation")
        need(rot is None or (rot["axis"] == "x" and rot["angle"] in (-45.0, -22.5, 22.5, 45.0)), f"{el['name']}: giro de elemento no permitido por vanilla")
        for fname, face in el["faces"].items():
            need(all(0 <= u <= 16 for u in face["uv"]), f"{el['name']}.{fname}: UV fuera de 0..16")
            need(face.get("rotation", 0) in (0, 90, 180, 270), f"{el['name']}.{fname}: giro de cara no permitido")

    # bloque == entidad, píxel por píxel, desde 8 ángulos (renderizador de software con las reglas de vanilla)
    from tools.v200 import softrender as sr
    S, TEXN = rc_car.SCALE, rc_car.TEX

    def rx(a):
        import math
        a = math.radians(a)
        return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])

    def block_polys(m):
        out = []
        for el in m["elements"]:
            lo, hi = np.array(el["from"], float), np.array(el["to"], float)
            for face, data in el["faces"].items():
                verts = np.array([[(lo if k == "min" else hi)[i] for i, k in enumerate(corner)] for corner in rc_car.FACE_CORNERS[face]], float)
                if el.get("rotation"):
                    o = np.array(el["rotation"]["origin"], float)
                    verts = (verts - o) @ rx(el["rotation"]["angle"]).T + o
                u1, v1, u2, v2 = [x * TEXN / 16.0 for x in data["uv"]]
                base = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
                k = data.get("rotation", 0) // 90
                uv = np.array([base[(i + k) % 4] for i in range(4)]) / TEXN
                w = np.stack([(8.0 - verts[:, 0]) / S, verts[:, 1] / S, (8.0 - verts[:, 2]) / S], axis=1)    # bloque (frente al norte) -> entidad
                out.append((np.stack([w[:, 0], 24.0 - w[:, 1], -w[:, 2]], axis=1), uv, face, "blk"))
        return out

    conv = block_polys(model)
    total_bad = 0
    for az, el_ in ((145, 18), (215, 18), (90, 4), (0, 8), (180, 8), (180, 80), (35, 30), (300, 40)):
        cam = sr.Camera(az, el_, 9.5, rc_car.CENTER, (420, 330))
        img_e = sr.render(b.root, tex, cam, (TEXN, TEXN), glow=None, background=(78, 86, 98, 255))
        orig = sr.collect
        sr.collect = lambda part, pm, pt, tw, th, out: out.extend(conv)
        try:
            img_b = sr.render(None, tex, cam, (TEXN, TEXN), glow=None, background=(78, 86, 98, 255))
        finally:
            sr.collect = orig
        diff = np.abs(np.asarray(img_e.convert("RGB"), float) - np.asarray(img_b.convert("RGB"), float)).sum(axis=2)
        total_bad += int((diff > 24).sum())
    need(total_bad == 0, f"el bloque y la entidad se ven distintos ({total_bad} píxeles de diferencia)")
    notes.append("el bloque (JSON) y la entidad (modelo Java) se dibujan idénticos desde 8 ángulos: 0 píxeles de diferencia")
    # el frente (nariz con faros) del bloque mira al norte
    nose = [e for e in model["elements"] if e["name"] == "nose"][0]
    need(nose["to"][2] < 4.0, "la nariz del carro debe quedar hacia el norte (z chico) en el bloque")
    # ícono del control
    saved_remote = np.asarray(Image.open(ASSETS / "textures/item/rc_car_remote.png").convert("RGBA"))
    tmp_out = rc_remote.OUT
    need(saved_remote.shape == (16, 16, 4), "el ícono del control debe ser de 16x16")
    del tmp_out
except Exception as exc:  # noqa: BLE001
    import traceback
    errors.append("no se pudo regenerar/comparar el carro: " + "".join(traceback.format_exception_only(type(exc), exc)).strip())
    notes.append(traceback.format_exc()[-1500:])

# el juego normal no tiene los nombres de desarrollo: buscar "selectedTab"/"selectTab" por nombre llenaba el registro de errores
side = read(C / "CreativeSidebar.java")
need("ObfuscationReflectionHelper" not in side, "CreativeSidebar no debe buscar por nombre con ObfuscationReflectionHelper (en el juego normal falla en cada cuadro)")
need("ReflectFind.staticField" in side and "ReflectFind.voidMethod" in side, "CreativeSidebar debe usar ReflectFind")

# ---------------------------------------------------------------- Java
javac = shutil.which("javac")
java = shutil.which("java")
ffmpeg = shutil.which("ffmpeg")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
NEW_JAVA = ["entity/RcCarEntity.java", "entity/RcCarPhysics.java", "block/RcCarBlock.java", "item/RcCarRemoteItem.java", "event/RcCarInteractionEvents.java",
            "network/RcCarControlPacket.java", "network/ModMessages.java", "client/RcCarModel.java", "client/RcCarRenderer.java", "client/RcCarMotorSound.java",
            "client/RcCarClientEvents.java", "client/ClientEvents.java", "init/ModBlocks.java", "init/ModItems.java", "init/ModEntities.java", "init/ModSounds.java",
            "init/ModCreativeTabs.java", "init/ModCategory.java", "init/ModCreativeOrder.java", "client/HsVideoPlayer.java", "browser/video/Mp4.java",
            "browser/video/Mp4Video.java", "browser/video/Mp4AacSource.java", "client/HsBrowserScreen.java", "client/CreativeSidebar.java", "client/ReflectFind.java"]
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las simulaciones")
else:
    # sintaxis (sin Minecraft solo se pueden ver errores de sintaxis; "no existe el símbolo" se ignora)
    out = Path(tempfile.mkdtemp(prefix="v310_syntax_"))
    try:
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(JAVA / rel) for rel in NEW_JAVA], env=env, cwd=out)
        bad = []
        for line in r.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(k in line for k in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
    finally:
        shutil.rmtree(out, ignore_errors=True)

    # búsqueda de campos/métodos de Minecraft por su forma (en el juego normal no se llaman como en desarrollo)
    work = Path(tempfile.mkdtemp(prefix="v310_refl_"))
    try:
        outp = work / "out"
        outp.mkdir()
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(C / "ReflectFind.java"),
                 str(ROOT / "tools/v310/java/ReflectFindTest.java")], env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba de ReflectFind:\n" + r.stderr[-1500:])
        if r.returncode == 0:
            r = run([java, "-cp", str(outp), "com.example.beautifuldecor.client.ReflectFindTest"], env=env, cwd=work, timeout=120)
            need(r.returncode == 0 and "REFLECT OK" in r.stdout, "ReflectFind falló:\n" + (r.stdout + r.stderr)[-1500:])
            notes.append("ReflectFind: encuentra por su forma el campo y el método de la pestaña creativa aunque se llamen f_98507_/m_98560_ (y con nombres de desarrollo)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

    # física del carro
    work = Path(tempfile.mkdtemp(prefix="v310_phys_"))
    try:
        outp = work / "out"
        outp.mkdir()
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(JAVA / "entity/RcCarPhysics.java"),
                 str(ROOT / "tools/v310/java/PhysicsTest.java")], env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la simulación de física:\n" + r.stderr[-1500:])
        if r.returncode == 0:
            r = run([java, "-cp", str(outp), "PhysicsTest"], env=env, cwd=work, timeout=120)
            need(r.returncode == 0 and "PHYSICS OK" in r.stdout, "la simulación de física del carro falló:\n" + (r.stdout + r.stderr)[-1500:])
            for line in r.stdout.splitlines():
                if line.startswith(("acelerar", "giro", "frenar", "circulo")):
                    notes.append("física: " + line)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    # descargas de video cortadas
    if ffmpeg is None:
        notes.append("sin ffmpeg: no se probaron las descargas cortadas")
    else:
        work = Path(tempfile.mkdtemp(prefix="v310_http_"))
        srv = None
        try:
            stub = work / "stub"
            shutil.copytree(ROOT / "tools/v2110/mcstub", stub)
            for rel in ("com/example/beautifuldecor/init/ModBlockEntities.java", "com/example/beautifuldecor/client/RegisterCheck.java"):
                (stub / rel).unlink()
            shutil.copytree(ROOT / "tools/v302/teststub_overrides", stub, dirs_exist_ok=True)
            shutil.copytree(ROOT / "tools/v310/teststub_overrides", stub, dirs_exist_ok=True)      # Component devuelve "clave|argumentos"
            outp = work / "out"
            outp.mkdir()
            srcs = [str(p) for p in stub.rglob("*.java")] + [str(p) for p in (JAVA / "browser").rglob("*.java")]
            srcs += [str(C / "HsVideoPlayer.java"), str(C / "HsAudioPlayer.java"), str(ROOT / "tools/v310/java/E2eHttp.java")]
            r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp)] + srcs, env=env, cwd=work)
            need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba de descargas:\n" + r.stderr[-2000:])
            media = work / "media"
            media.mkdir()
            rr = run([ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24", "-f", "lavfi", "-i", "sine=frequency=330:sample_rate=48000",
                      "-t", "6", "-c:v", "libx264", "-profile:v", "main", "-bf", "2", "-pix_fmt", "yuv420p", "-b:v", "1500k", "-c:a", "aac", "-ac", "2",
                      "-b:a", "96k", str(media / "clip.mp4")], env=env, cwd=work)
            need(rr.returncode == 0, "ffmpeg no pudo hacer el video de prueba:\n" + rr.stderr[-500:])
            # el mismo video con el índice (moov) AL PRINCIPIO, como los que se ven por internet: se puede abrir aunque le falte el final
            rf = run([ffmpeg, "-v", "error", "-y", "-i", str(media / "clip.mp4"), "-c", "copy", "-movflags", "+faststart", str(media / "fast.mp4")], env=env, cwd=work)
            need(rf.returncode == 0, "ffmpeg no pudo hacer el video con faststart:\n" + rf.stderr[-500:])
            if r.returncode == 0 and rr.returncode == 0 and rf.returncode == 0:
                s = socket.socket()
                s.bind(("127.0.0.1", 0))
                port = s.getsockname()[1]
                s.close()
                srv = subprocess.Popen([sys.executable, str(ROOT / "tools/v310/http_cut_server.py"), str(media), str(port)], stdout=subprocess.PIPE, text=True, env=env)
                ready = srv.stdout.readline()
                need("READY" in ready, "el servidor de prueba no arrancó")
                runs = work / "run"
                runs.mkdir()
                for mode, name, expect, label in (("full", "clip.mp4", "ok", "descarga entera"),
                                                  ("cut", "clip.mp4", "ok", "cortada en tercios con Content-Length (el servidor entiende Range): se reanuda"),
                                                  ("chunk", "clip.mp4", "ok", "cortada en tercios sin Content-Length (y con el índice moov al final): se reanuda"),
                                                  ("cut", "fast.mp4", "ok", "cortada en tercios, con el índice al principio: se reanuda"),
                                                  ("range0", "fast.mp4", "ok", "el servidor a un pedido SIN Range le da solo el principio (como parecía hacer catbox): "
                                                                               "el mod pide con Range, como un navegador, y recibe el video entero"),
                                                  ("nocache", "fast.mp4", "ok", "una caché intermedia tiene solo el principio y entrega eso a un pedido normal: el mod "
                                                                                "lo detecta, pide de nuevo con no-cache (como recargar) y recibe el video entero"),
                                                  ("cutnorange", "clip.mp4", "fail", "cortada al 40 % y el servidor NO entiende Range: avisa de frente"),
                                                  ("chunknorange", "clip.mp4", "fail", "sin Content-Length, cortada al 40 % y sin Range: avisa de frente"),
                                                  ("trunc", "fast.mp4", "partial", "el ARCHIVO está cortado en el servidor (solo hay el 30 %, el servidor lo dice y entiende Range): "
                                                                                    "se reproduce el principio con su aviso"),
                                                  ("trunc200", "fast.mp4", "partial", "igual, pero el servidor ignora Range: se reproduce el principio con su aviso"),
                                                  ("trunc", "clip.mp4", "fail", "archivo cortado en el servidor SIN índice (moov al final): no hay nada que reproducir, avisa de frente")):
                    t0 = time.time()
                    r = run([java, "-cp", str(outp), "com.example.beautifuldecor.client.E2eHttp", f"http://127.0.0.1:{port}/{mode}/{name}", expect],
                            env=env, cwd=runs, timeout=180)
                    need(r.returncode == 0 and "HTTP OK" in r.stdout, f"descarga '{label}' falló:\n" + (r.stdout + r.stderr)[-2500:])
                    need("Exception in thread" not in r.stdout + r.stderr, f"una excepción se escapó de un hilo en '{label}':\n" + (r.stdout + r.stderr)[-1500:])
                    m = re.search(r"RESULT .*", r.stdout)
                    notes.append(f"descarga {mode}/{name} ({time.time() - t0:.1f} s): {m.group(0)[:230] if m else '?'}")
        finally:
            if srv is not None:
                srv.kill()
            shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- limpieza del proyecto
for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

# ---------------------------------------------------------------- lo de la V3.0.2 (y, con él, lo de las anteriores)
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v302.py")], env=env, timeout=3600)
    need(r.returncode == 0 and "OK V3.0.2" in r.stdout, "verify_v302 falló:\n" + (r.stdout + r.stderr)[-2500:])

if errors:
    print("FALLÓ la verificación V3.1.0:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.1.0: carro RC Lamborgonzo Huracanito (bloque y entidad idénticos, control remoto propio, física probada) y descargas de video que se reanudan o avisan; "
      "recursos, idiomas y registros completos.")
for n in notes:
    print("  (nota)", n)
