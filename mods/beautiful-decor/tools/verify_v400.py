#!/usr/bin/env python3
"""Comprueba SOLO la V4.0.0: sistema de megafonía (micrófono + bocina). No vuelve a correr verificaciones de versiones anteriores.

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README, NUEVOS_BLOQUES, protocolo de red ("19" o más);
  * assets (texturas, modelos, blockstates, loot tables) idénticos a los que genera tools/v400/megaphone.py; íconos de 32x32 con dibujo;
    texturas y modelos que se referencian existen; blockstates cubren todas las combinaciones de las propiedades de los bloques;
  * registros: bloques, ítems, entidades de bloque, categoría, orden de la pestaña y los 4 paquetes (con su dirección);
  * el audio de verdad: se compila y corre tools/v400/java/PaAudioTest.java (códec ADPCM, compuerta, nivel, distancia) con el Java real del mod;
  * límites del servidor (alcance, ritmo de paquetes, tamaño) coherentes con lo que manda el cliente;
  * nada del cliente (net.minecraft.client, LWJGL, megaphone.client) se carga en el servidor fuera de DistExecutor;
  * textos es/en completos, iguales en los marcadores, y todos los que usa el Java existen;
  * sintaxis del Java nuevo o cambiado.

Uso: python3 tools/verify_v400.py
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
DATA = ROOT / "src/main/resources/data/beautifuldecor"
errors, notes = [], []


def need(cond, msg):
    if not cond:
        errors.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def code(p):
    """Java sin comentarios (para no confundir una mención en un comentario con código)."""
    s = read(p)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, env=env, cwd=cwd)


NEW_JAVA = [
    "block/MicrophoneBlock.java", "block/PaSpeakerBlock.java", "block/entity/MicrophoneBlockEntity.java", "block/entity/PaSpeakerBlockEntity.java",
    "item/PaSpeakerItem.java", "megaphone/ImaAdpcm.java", "megaphone/PaDsp.java", "megaphone/PaServer.java",
    "megaphone/client/MicBackend.java", "megaphone/client/OpenAlMicBackend.java", "megaphone/client/JavaSoundMicBackend.java",
    "megaphone/client/MicCapture.java", "megaphone/client/PaPlayback.java", "megaphone/client/PaClient.java", "megaphone/client/MicrophoneScreen.java",
    "network/MicControlPacket.java", "network/MicAudioPacket.java", "network/MicStatePacket.java", "network/SpeakerAudioPacket.java",
]
CHANGED_JAVA = ["init/ModBlocks.java", "init/ModItems.java", "init/ModBlockEntities.java", "init/ModCategory.java", "init/ModCreativeOrder.java",
                "client/ClientHooks.java", "network/ModMessages.java"]

# ---------------------------------------------------------------- versión, documentos, red
need(re.search(r"version = '(4|[5-9]|\d{2,})\.\d+\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 4.0.0 o superior")
need(re.search(r"mod_version=(4|[5-9]|\d{2,})\.\d+\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 4.0.0 o superior")
need("V4.0.0" in read(ROOT / "README.md"), "README sin la entrada V4.0.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V4.0.0") and "PARCHE V3.9.0" in nuevos, "NUEVOS_BLOQUES debe empezar con la sección V4.0.0 y conservar la V3.9.0")
msg = read(JAVA / "network/ModMessages.java")
need(re.search(r'PROTOCOL = "(19|[2-9]\d|\d{3,})"', msg), "el protocolo de red debe ser 19 o más (paquetes nuevos)")

# ---------------------------------------------------------------- assets idénticos a los del generador
try:
    from PIL import Image
    from tools.v400 import megaphone as gen

    expected = gen.build_files()
    for rel, obj in expected.items():
        p = ROOT / rel
        if not p.exists():
            errors.append(f"falta {rel}")
            continue
        if isinstance(obj, Image.Image):
            got = Image.open(p).convert("RGBA")
            need(got.size == (32, 32), f"{rel} no es de 32x32")
            need(got.tobytes() == obj.convert("RGBA").tobytes(), f"{rel} no coincide con el generador")
        else:
            need(read(p) == obj, f"{rel} no coincide con el generador")
    notes.append(f"{len(expected)} archivos de assets idénticos a los del generador")
    for name in ("microphone", "pa_speaker"):
        p = ASSETS / f"textures/item/{name}.png"
        need(p.exists(), f"falta el ícono {name}")
        if p.exists():
            icon = Image.open(p).convert("RGBA")
            need(icon.size == (32, 32), f"el ícono {name} no es de 32x32")
            need(sum(1 for a in icon.getchannel("A").tobytes() if a > 0) > 120, f"el ícono {name} está casi vacío")
except Exception as e:  # noqa: BLE001
    errors.append(f"no se pudo revisar los assets: {e!r}")

# texturas y modelos referenciados
for model in ("microphone", "microphone_talking", "pa_speaker"):
    mp = ASSETS / f"models/block/{model}.json"
    if not mp.exists():
        errors.append(f"falta el modelo {model}")
        continue
    data = json.loads(read(mp))
    need(data.get("parent") == "minecraft:block/block", f"{model}: parent inesperado")
    for key, ref in data.get("textures", {}).items():
        tex = ref.split(":", 1)[1]
        need((ASSETS / f"textures/{tex}.png").exists(), f"{model}: falta la textura {ref}")
    for el in data.get("elements", []):
        for side, face in el.get("faces", {}).items():
            tex = face.get("texture", "")
            need(tex.startswith("#") and tex[1:] in data["textures"], f"{model}: cara con textura sin definir {tex}")

# blockstates: todas las combinaciones
bs_mic = json.loads(read(ASSETS / "blockstates/microphone.json"))["variants"]
want_mic = {f"facing={f},talking={t}" for f in ("north", "south", "east", "west") for t in ("false", "true")}
need(set(bs_mic) == want_mic, f"blockstate del micrófono: variantes {sorted(set(bs_mic) ^ want_mic)} de más o de menos")
for key, var in bs_mic.items():
    need(var["model"] == ("beautifuldecor:block/microphone_talking" if "talking=true" in key else "beautifuldecor:block/microphone"), f"micrófono {key}: modelo equivocado")
bs_spk = json.loads(read(ASSETS / "blockstates/pa_speaker.json"))["variants"]
need(set(bs_spk) == {f"facing={f}" for f in ("north", "south", "east", "west")}, "blockstate de la bocina: faltan o sobran variantes")
for name in ("microphone", "pa_speaker"):
    loot = json.loads(read(DATA / f"loot_tables/blocks/{name}.json"))
    need(loot["pools"][0]["entries"][0]["name"] == f"beautifuldecor:{name}", f"loot de {name} no suelta el bloque")
    need(json.loads(read(ASSETS / f"models/item/{name}.json"))["textures"]["layer0"] == f"beautifuldecor:item/{name}", f"modelo de ítem de {name}")

mic_java = code(JAVA / "block/MicrophoneBlock.java")
need('"talking"' in mic_java and "HORIZONTAL_FACING" in mic_java, "MicrophoneBlock debe tener las propiedades facing y talking")
need("canSupportCenter" in mic_java and "updateShape" in mic_java, "el micrófono debe necesitar un bloque firme debajo")
spk_java = code(JAVA / "block/PaSpeakerBlock.java")
need("HORIZONTAL_FACING" in spk_java, "PaSpeakerBlock debe tener facing")

# ---------------------------------------------------------------- registros
blocks = code(JAVA / "init/ModBlocks.java")
for ident, cls in (("microphone", "MicrophoneBlock"), ("pa_speaker", "PaSpeakerBlock")):
    need(re.search(rf'register\("{ident}",\s*\(\)\s*->\s*new {cls}\(', blocks), f"ModBlocks sin {ident}")
need("MicrophoneBlock.TALKING" in blocks and "lightLevel" in blocks, "el micrófono debe dar luz al hablar")
items = code(JAVA / "init/ModItems.java")
need(re.search(r'register\("microphone",\s*\(\)\s*->\s*new BlockItem\(ModBlocks\.MICROPHONE\.get\(\)', items), "ModItems sin el ítem del micrófono")
need(re.search(r'register\("pa_speaker",\s*\(\)\s*->\s*new PaSpeakerItem\(ModBlocks\.PA_SPEAKER\.get\(\)', items), "ModItems sin la bocina (PaSpeakerItem)")
bes = code(JAVA / "init/ModBlockEntities.java")
need(re.search(r'register\("microphone".*?MicrophoneBlockEntity::new,\s*ModBlocks\.MICROPHONE\.get\(\)', bes, re.S), "ModBlockEntities sin el micrófono")
need(re.search(r'register\("pa_speaker".*?PaSpeakerBlockEntity::new,\s*ModBlocks\.PA_SPEAKER\.get\(\)', bes, re.S), "ModBlockEntities sin la bocina")
need('"microphone", "pa_speaker"' in code(JAVA / "init/ModCategory.java"), "ModCategory: micrófono y bocina deben ir en electrónica")
need('"microphone", "pa_speaker"' in code(JAVA / "init/ModCreativeOrder.java"), "ModCreativeOrder: micrófono y bocina sin ordenar")
need("createTickerHelper" in mic_java and "ModBlockEntities.MICROPHONE" in mic_java, "el micrófono debe tener su ticker")
need("openMicrophoneScreen" in code(JAVA / "client/ClientHooks.java"), "ClientHooks sin openMicrophoneScreen")

msg_code = code(JAVA / "network/ModMessages.java")
for cls, direction in (("MicControlPacket", "PLAY_TO_SERVER"), ("MicAudioPacket", "PLAY_TO_SERVER"), ("MicStatePacket", "PLAY_TO_CLIENT"),
                       ("SpeakerAudioPacket", "PLAY_TO_CLIENT")):
    m = re.search(rf"messageBuilder\({cls}\.class,\s*id\+\+,\s*NetworkDirection\.(\w+)\)(.*?)\.add\(\)", msg_code, re.S)
    need(m is not None, f"paquete {cls} sin registrar")
    if m:
        need(m.group(1) == direction, f"{cls} debe ir {direction}")
        need(f"{cls}::decode" in m.group(2) and f"{cls}::encode" in m.group(2) and f"{cls}::handle" in m.group(2), f"{cls}: decoder/encoder/handler")
ids = re.findall(r"messageBuilder\((\w+)\.class", msg_code)
need(len(ids) == len(set(ids)), "paquetes registrados dos veces")

# ---------------------------------------------------------------- límites y coherencia cliente/servidor
server = code(JAVA / "megaphone/PaServer.java")
dsp = code(JAVA / "megaphone/PaDsp.java")


def const(src, name):
    m = re.search(rf"{name}\s*=\s*([0-9.]+)", src)
    return float(m.group(1)) if m else None


talk, keep, per_sec = const(server, "TALK_RANGE"), const(server, "KEEP_RANGE"), const(server, "MAX_PACKETS_PER_SECOND")
rate, frame, hear = const(dsp, "RATE"), const(dsp, "FRAME"), const(dsp, "HEAR_RANGE")
need(talk == 12.0 and keep == 16.0 and per_sec == 60.0, f"límites del servidor inesperados ({talk}, {keep}, {per_sec})")
need(rate == 16000 and frame == 640 and hear == 48.0, "constantes del audio inesperadas")
packets_per_s = rate / frame
need(packets_per_s < per_sec, "el cliente mandaría más paquetes por segundo de los que acepta el servidor")
block_bytes = 3 + int(frame) // 2
need(block_bytes == 323 and block_bytes <= 1024, "el bloque de voz no cabe en el máximo del paquete")
need("MAX_BYTES = 1024" in code(JAVA / "network/MicAudioPacket.java"), "MAX_BYTES del paquete de audio")
need("readByteArray(MicAudioPacket.MAX_BYTES)" in code(JAVA / "network/SpeakerAudioPacket.java"), "SpeakerAudioPacket debe limitar el tamaño al leer")
need("Math.min(buf.readVarInt(), MAX_SPEAKERS)" in code(JAVA / "network/SpeakerAudioPacket.java"), "SpeakerAudioPacket debe limitar la cantidad de bocinas al leer")
need("MAX_SPEAKERS = 16" in code(JAVA / "block/entity/MicrophoneBlockEntity.java"), "máximo de 16 bocinas por micrófono")
for rule, text in (("distancia al hablar", "TALK_RANGE * TALK_RANGE"), ("distancia al seguir hablando", "KEEP_RANGE * KEEP_RANGE"),
                   ("ritmo", "MAX_PACKETS_PER_SECOND"), ("quien manda es quien habla", "!talk.mic.equals(pos)"),
                   ("tamaño mínimo", "ImaAdpcm.HEADER + 2"), ("corte por silencio", "SILENCE_TIMEOUT_TICKS"),
                   ("una sola persona por micrófono", "message.beautifuldecor.pa.busy")):
    need(text in server, f"PaServer sin la regla: {rule}")
need("PaServer.KEEP_RANGE" in code(JAVA / "megaphone/client/MicCapture.java"), "el cliente debe apagarse antes de que el servidor lo corte por distancia")
need(re.search(r"player\.isAlive\(\)", server) and "PlayerLoggedOutEvent" in server and "ServerStoppedEvent" in server, "el servidor debe soltar el micrófono al morir/salir")
need("Bus.FORGE" in server, "PaServer debe estar en el bus de Forge")

# ---------------------------------------------------------------- el servidor no carga clases del cliente
SERVER_SIDE = ["block/MicrophoneBlock.java", "block/PaSpeakerBlock.java", "block/entity/MicrophoneBlockEntity.java", "block/entity/PaSpeakerBlockEntity.java",
               "item/PaSpeakerItem.java", "megaphone/PaServer.java", "megaphone/ImaAdpcm.java", "megaphone/PaDsp.java", "network/MicControlPacket.java",
               "network/MicAudioPacket.java", "network/MicStatePacket.java", "network/SpeakerAudioPacket.java"]
for rel in SERVER_SIDE:
    src = code(JAVA / rel)
    imports = re.findall(r"^import ([\w.]+);", src, re.M)
    bad = [i for i in imports if i.startswith("net.minecraft.client") or i.startswith("org.lwjgl") or ".megaphone.client" in i or i.startswith("javax.sound")]
    need(not bad, f"{rel} importa clases del cliente: {bad}")
    for m in re.finditer(r"megaphone\.client\.\w+|client\.ClientHooks", src):
        before = src[max(0, m.start() - 260):m.start()]
        need("DistExecutor.unsafeRunWhenOn" in before, f"{rel}: {m.group(0)} se usa fuera de DistExecutor")
client_dir = JAVA / "megaphone/client"
need("OpenAL" not in server and "org.lwjgl" not in server, "PaServer no debe tocar OpenAL")
need("Dist.CLIENT" in code(client_dir / "PaClient.java"), "PaClient debe registrarse solo en el cliente")
need(not re.search(r"^import .*javax\.sound", code(client_dir / "MicCapture.java"), re.M), "MicCapture no debe importar Java Sound (se carga solo si hace falta)")
need("catch (Throwable" in code(client_dir / "MicCapture.java"), "la captura debe tolerar que falte una librería")

# ---------------------------------------------------------------- reproducción y captura: detalles que no se pueden probar con sonido real
play = code(client_dir / "PaPlayback.java")
for text, why in (("AL_ROLLOFF_FACTOR, 0.0f", "sin atenuación de OpenAL (la distancia la calculamos)"), ("alcGetCurrentContext", "contexto de audio actual"),
                  ("PREBUFFER", "colchón contra el lag"), ("MAX_QUEUED", "tope de retraso"), ("alSourceUnqueueBuffers", "reciclar buffers"),
                  ("alDeleteSources", "liberar fuentes"), ("shutdown", "limpiar al salir"), ("PaDsp.distanceGain", "volumen por distancia")):
    need(text in play, f"PaPlayback sin {why}")
cap = code(client_dir / "MicCapture.java")
for text, why in (("SILENT_FRAMES", "aviso de micrófono mudo"), ("Gate", "compuerta de ruido"), ("Agc", "nivel automático"), ("ImaAdpcm.Encoder", "compresión"),
                  ("session.alive", "hilo que se apaga"), ("Thread.sleep", "sin gastar el procesador")):
    need(text in cap, f"MicCapture sin {why}")
al = code(client_dir / "OpenAlMicBackend.java")
for text in ("alcCaptureOpenDevice", "alcCaptureStart", "ALC_CAPTURE_SAMPLES", "alcCaptureSamples", "alcCaptureStop", "alcCaptureCloseDevice", "AL_FORMAT_MONO16"):
    need(text in al, f"OpenAlMicBackend sin {text}")
screen = code(client_dir / "MicrophoneScreen.java")
need("isPauseScreen" in screen and "return false" in screen[screen.index("isPauseScreen"):screen.index("isPauseScreen") + 80],
     "el menú del micrófono no debe pausar el juego (en un mundo propio detendría el servidor)")
for action in ("START_TALK", "STOP_TALK", "LINK_NEARBY", "UNLINK_ALL", "QUERY"):
    need(action in screen and action in server, f"la acción {action} debe existir en el menú y en el servidor")

# ---------------------------------------------------------------- textos es / en
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
used = set()
for rel in NEW_JAVA:
    used |= set(re.findall(r'"((?:message|tooltip|screen|hud|block|container)\.beautifuldecor\.[a-z_.0-9]+)"', code(JAVA / rel)))
need(len(used) >= 30, f"se esperaban al menos 30 textos usados, hay {len(used)}")
for key in sorted(used):
    need(key in es, f"falta en es_es: {key}")
    need(key in en, f"falta en en_us: {key}")
new_prefix = ("message.beautifuldecor.pa.", "screen.beautifuldecor.microphone.", "hud.beautifuldecor.pa.", "tooltip.beautifuldecor.pa.")
for key in list(es) + list(en):
    if key.startswith(new_prefix):
        need(key in used, f"texto sin usar: {key}")
        need(key in es and key in en, f"texto solo en un idioma: {key}")
for key in ("block.beautifuldecor.microphone", "block.beautifuldecor.pa_speaker"):
    need(key in es and key in en, f"falta el nombre {key}")
    need(es.get(key) != en.get(key), f"{key} igual en los dos idiomas")
for key in used & set(es) & set(en):
    need(len(re.findall(r"%s", es[key])) == len(re.findall(r"%s", en[key])), f"{key}: distinta cantidad de %s en es y en")
# "%%" es un porcentaje literal; el resto de % sueltos rompería el formato
for lang, table in (("es", es), ("en", en)):
    for key, text in table.items():
        if key.startswith(new_prefix):
            need("%" not in re.sub(r"%[s%]", "", text), f"{lang} {key}: % suelto")

# ---------------------------------------------------------------- el audio de verdad (Java puro) y la sintaxis del Java nuevo
javac, java = shutil.which("javac"), shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
if not javac or not java:
    notes.append("sin javac/java: no se corrió la prueba de audio ni se revisó la sintaxis")
else:
    work = Path(tempfile.mkdtemp(prefix="v400_"))
    try:
        out = work / "audio"
        out.mkdir()
        srcs = [JAVA / "megaphone/ImaAdpcm.java", JAVA / "megaphone/PaDsp.java", ROOT / "tools/v400/java/PaAudioTest.java"]
        r = run([javac, "-encoding", "UTF-8", "-d", str(out)] + [str(f) for f in srcs], env=env, cwd=work)
        need(r.returncode == 0, "no compila la prueba de audio:\n" + r.stderr[:1500])
        if r.returncode == 0:
            r = run([java, "-cp", str(out), "PaAudioTest"], env=env, cwd=work)
            need(r.returncode == 0 and "PA AUDIO OK" in r.stdout, "falla la prueba de audio:\n" + r.stdout[-1500:] + r.stderr[-500:])
            notes.append(" | ".join(l for l in r.stdout.splitlines() if "SNR" in l or "KB" in l or "OK" in l)[:200])

        out2 = work / "syntax"
        out2.mkdir()
        files = [JAVA / p for p in NEW_JAVA + CHANGED_JAVA]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out2)] + [str(f) for f in files], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed",
            "method does not override", "cannot be referenced", "constructor", "cannot be inherited", "is not public", "has private access", "incomparable types"))]
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft y de LWJGL no se pudieron comprobar sin ellos)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if errors:
    print("FALLÓ la verificación V4.0.0:")
    for e in errors:
        print(" -", e)
    for nn in notes:
        print("  (nota)", nn)
    sys.exit(1)
print("OK V4.0.0: sistema de megafonía; solo se verificó esta versión.")
for nn in notes:
    print("  (nota)", nn)
