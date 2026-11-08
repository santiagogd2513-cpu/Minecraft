#!/usr/bin/env python3
"""Comprueba la V3.5.0: radio en los vehículos terrestres (tecla X).

  * versión, README, NUEVOS_BLOQUES, idiomas y protocolo de red ("14");
  * que no haya nada de otros mods (ni "musicbox" en el código ni en los recursos);
  * las piezas sin Minecraft (radio/*, IcyStream) son Java puro;
  * que todo esté conectado: datos de la radio en VehicleEntity (definidos, guardados y leídos), paquete registrado y que el servidor revise
    que quien lo manda va montado, tecla X registrada y atendida, la radio solo en VehicleEntity (el helicóptero no lo es), AudioPipe pide
    los títulos SOLO para la radio, HsAudioPlayer sigue al vehículo, VehicleRadioClient elige con RadioPicker y se calla al salir;
  * la prueba (tools/v350/java/RadioTest.java) con el AudioPipe de verdad contra un servidor de radio de mentira, y que muerde (mutantes);
  * sintaxis del Java tocado;
  * y corre lo de la V3.4.0 (tools/verify_v340.py).

Uso: python3 tools/verify_v350.py [--fast]
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


def squash(text):
    text = re.sub(r"(?m)^\s*//.*$", "", text)
    text = re.sub(r"(?m)([;{}])\s*//.*$", r"\1", text)
    return re.sub(r"\s+", " ", text)


def run(cmd, timeout=600, env=None, cwd=None):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, env=env, cwd=cwd)


def body(text, signature_regex):
    m = re.search(signature_regex, text)
    if not m:
        return None
    i = m.end() - 1 if text[m.end() - 1] == "{" else text.index("{", m.end())
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[i + 1:j]
    return None


# ---------------------------------------------------------------- versión, documentos, red
need(re.search(r"version = '3\.(([5-9]|\d{2,})\.\d+)'", read(ROOT / "build.gradle")), "build.gradle no está en 3.5.0 o superior")
need(re.search(r"mod_version=3\.(([5-9]|\d{2,})\.\d+)", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.5.0 o superior")
need("V3.5.0" in read(ROOT / "README.md"), "README sin la entrada V3.5.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.5.0" in nuevos and "PARCHE V3.4.0" in nuevos, "NUEVOS_BLOQUES debe empezar con una sección V3.x y conservar la V3.5.0 y la V3.4.0")
need(re.search(r'PROTOCOL = "(1[4-9]|[2-9]\d|\d{3,})"', read(JAVA / "network/ModMessages.java")), "el protocolo de red debe ser 14 o más (paquete y datos nuevos)")

# ---------------------------------------------------------------- nada de otros mods
for f in list((ROOT / "src").rglob("*")):
    # (el aviso de licencia de JLayer, THIRD_PARTY_JLAYER.txt, nombra a otro mod que trae la misma librería: eso no es código copiado)
    if f.is_file() and f.suffix in (".java", ".json", ".toml", ".txt", ".mcmeta") and not f.name.startswith("THIRD_PARTY"):
        need("musicbox" not in read(f).lower(), f"{f.relative_to(ROOT)} menciona musicbox: la radio debe ser propia")
for f in (ROOT / "src").rglob("*"):
    need("musicbox" not in f.name.lower(), f"archivo con nombre de otro mod: {f.relative_to(ROOT)}")

# ---------------------------------------------------------------- Java puro
for rel in ("radio/RadioStations.java", "radio/RadioPicker.java", "browser/audio/IcyStream.java"):
    src = read(JAVA / rel)
    need("net.minecraft" not in src and "net.minecraftforge" not in src, f"{rel} debe ser Java puro (sin Minecraft)")

# ---------------------------------------------------------------- AudioPipe y el reproductor
pipe = read(JAVA / "browser/audio/AudioPipe.java")
ps = squash(pipe)
need("public AudioPipe(Env env, long retryDelayMs) { this(env, retryDelayMs, false); }" in ps, "AudioPipe de siempre (navegador, televisores) NO debe pedir títulos")
need('this.icyTitles ? Http.request("GET", url, null, Map.of("Icy-MetaData", "1"), null) : Http.get(url, null)' in ps, "solo con icyTitles se pide Icy-MetaData")
need("body = new IcyStream(body, metaint, this.env::title);" in ps and "if (metaint > 0)" in ps, "con icy-metaint el sonido debe pasar por IcyStream")
need("default void title(String title)" in ps and "default void station(String name)" in ps, "Env.title/station con implementación vacía (los demás Env no cambian)")
hs = read(JAVA / "client/HsAudioPlayer.java")
hss = squash(hs)
need("public void playFollowing(String url, String name, Supplier<Vec3> where, BooleanSupplier alive, ResourceKey<Level> dim)" in hss, "falta HsAudioPlayer.playFollowing")
play = squash(body(hs, r"public void play\(String url, Path file, String name, BlockPos computer, ResourceKey<Level> dim\) \{") or "")
need("this.follow = null;" in play and "this.icyTitles = false;" in play, "play() (navegador/televisor) debe quitar el modo radio")
need("new AudioPipe(env(my), 1500, this.icyTitles)" in hss, "el hilo del sonido debe pasar icyTitles a AudioPipe")
tick = squash(body(hs, r"private void tickInner\(Minecraft minecraft\) \{") or "")
need("Supplier<Vec3> where = this.follow;" in tick and "if (d > STOP_BLOCKS) { stop(); return; }" in tick, "tickInner: la radio se calla lejos o si el vehículo ya no está")

# ---------------------------------------------------------------- el vehículo
veh = read(JAVA / "entity/VehicleEntity.java")
vs = squash(veh)
for acc, ser in (("RADIO_URL", "STRING"), ("RADIO_NAME", "STRING"), ("RADIO_ON", "BOOLEAN"), ("RADIO_VOLUME", "INT")):
    need(f"EntityDataAccessor<" in vs and f"{acc} = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.{ser});" in vs, f"VehicleEntity sin {acc}")
    need(f"this.entityData.define({acc}," in vs, f"VehicleEntity no define {acc}")
for key in ("RadioUrl", "RadioName", "RadioOn", "RadioVolume"):
    need(f'"{key}"' in squash(body(veh, r"protected void addAdditionalSaveData\(CompoundTag tag\) \{") or "") and f'"{key}"' in squash(body(veh, r"protected void readAdditionalSaveData\(CompoundTag tag\) \{") or ""),
         f"la radio no guarda/lee {key}")
should = squash(body(veh, r"public boolean radioShouldPlay\(\) \{") or "")
need("this.isRadioOn()" in should and "this.isVehicle()" in should and "!this.getRadioUrl().isEmpty()" in should, "radioShouldPlay: encendida, con emisora y con alguien a bordo")
need("class HelicopterEntity extends Entity" in squash(read(JAVA / "entity/HelicopterEntity.java")), "el helicóptero no debe ser VehicleEntity (no tiene radio)")

# ---------------------------------------------------------------- red
msgs = read(JAVA / "network/ModMessages.java")
need("CHANNEL.messageBuilder(VehicleRadioPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)" in msgs, "VehicleRadioPacket sin registrar")
pk = read(JAVA / "network/VehicleRadioPacket.java")
handle = squash(body(pk, r"public static void handle\(VehicleRadioPacket packet, Supplier<NetworkEvent.Context> supplier\) \{") or "")
need("player.getVehicle() != entity" in handle, "el servidor debe revisar que quien maneja la radio va montado en ese vehículo")
need("RadioStations.cleanUrl(packet.url)" in handle and "RadioStations.cleanName(packet.name)" in handle, "el servidor debe limpiar la URL y el nombre")
need("buf.readUtf(RadioStations.MAX_URL)" in pk and "buf.readUtf(RadioStations.MAX_NAME)" in pk, "el paquete debe limitar el largo al leer")

# ---------------------------------------------------------------- cliente: tecla, gestor, menú
keys = read(JAVA / "client/VehicleKeys.java")
need('RADIO = new KeyMapping("key.beautifuldecor.vehicle_radio", GLFW.GLFW_KEY_X, CATEGORY)' in keys, "la tecla de la radio debe ser X")
need("event.register(VehicleKeys.RADIO);" in read(JAVA / "client/ClientEvents.java"), "la tecla de la radio no se registra")
gce = squash(read(JAVA / "client/GarageClientEvents.java"))
need("while (VehicleKeys.RADIO.consumeClick()) { VehicleRadioScreen.tryOpen(minecraft); }" in gce, "X debe abrir la radio")
need("VehicleRadioClient.tick(minecraft);" in gce and "VehicleRadioClient.reset();" in gce, "las radios deben sonar cada tick y callarse al salir del mundo")
scr = read(JAVA / "client/VehicleRadioScreen.java")
opener = squash(body(scr, r"public static void tryOpen\(Minecraft minecraft\) \{") or "")
need("player.getVehicle() instanceof VehicleEntity vehicle" in opener and "message.beautifuldecor.radio.land_only" in opener, "la radio solo se abre en vehículos terrestres")
need("public boolean isPauseScreen() { return false; }" in squash(scr), "el menú de la radio no debe pausar el juego")
cl = squash(read(JAVA / "client/VehicleRadioClient.java"))
need("RadioPicker.pick(candidates, RadioPicker.MAX_PLAYING)" in cl and "playFollowing(" in cl and "vehicle::radioShouldPlay" in cl, "VehicleRadioClient debe elegir con RadioPicker y seguir al vehículo")
need("RETRY_MS" in cl, "una emisora que falla debe reintentarse cada tanto (no en cada tick)")

# ---------------------------------------------------------------- idiomas
en = json.loads(read(ASSETS / "lang/en_us.json"))
es = json.loads(read(ASSETS / "lang/es_es.json"))
need(set(en) == set(es), "en_us y es_es no tienen las mismas claves")
used = set()
for f in (scr, read(JAVA / "client/VehicleRadioClient.java")):
    used |= set(re.findall(r'"((?:gui|message|key)\.beautifuldecor\.[a-z0-9_.]+)"', f))
used.add("key.beautifuldecor.vehicle_radio")
for k in sorted(used):
    need(k in en and k in es, f"falta la clave de idioma {k}")
need(en.get("gui.beautifuldecor.radio.full", "").count("%s") == 1 and es.get("gui.beautifuldecor.radio.full", "").count("%s") == 1, "radio.full debe tener un %s")

# ---------------------------------------------------------------- Java
javac = shutil.which("javac")
java = shutil.which("java")
env = {k2: v for k2, v in os.environ.items() if k2 != "JAVA_TOOL_OPTIONS"}
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    work = Path(tempfile.mkdtemp(prefix="v350_"))
    try:
        out = work / "syntax"
        out.mkdir()
        files = [JAVA / p for p in ("client/VehicleRadioScreen.java", "client/VehicleRadioClient.java", "client/RadioFavorites.java", "client/VehicleKeys.java",
                                    "client/GarageClientEvents.java", "client/ClientEvents.java", "client/HsAudioPlayer.java", "network/VehicleRadioPacket.java",
                                    "network/ModMessages.java", "entity/VehicleEntity.java", "radio/RadioStations.java", "radio/RadioPicker.java",
                                    "browser/audio/IcyStream.java", "browser/audio/AudioPipe.java")]
        r1 = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(f) for f in files], env=env, cwd=work)
        bad = [l for l in r1.stderr.splitlines() if ": error:" in l and not any(k in l for k in (
            "cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "incompatible types", "cannot be applied",
            "is not abstract and does not override", "cannot infer", "bad operand", "not a functional interface", "no suitable", "cannot be dereferenced",
            "unreported exception", "cannot access", "bad type", "package com.", "invalid method reference", "static import only from classes", "cannot be accessed",
            "method does not override"))]
        need(not bad, "errores de sintaxis en el Java tocado:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java tocado es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")

        pure = [JAVA / "browser/audio", JAVA / "browser/video"]
        def sources(overrides):
            files = [str(p) for d in pure for p in sorted(d.glob("*.java"))] + [str(JAVA / "browser/Http.java"), str(JAVA / "browser/Url.java"),
                                                                                str(JAVA / "radio/RadioStations.java"), str(JAVA / "radio/RadioPicker.java")]
            for orig, repl in overrides.items():
                files = [repl if f == str(orig) else f for f in files]
            return files + [str(ROOT / "tools/v350/java/RadioTest.java")]

        def run_test(tag, overrides=None, limit=240):
            outp = work / f"out_{tag}"
            outp.mkdir()
            c = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all,-serial", "-d", str(outp)] + sources(overrides or {}), env=env, cwd=work)
            if c.returncode != 0 or "warning:" in c.stderr:
                return None, "no compila:\n" + c.stderr[-2000:]
            try:
                res = run([java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", str(outp), "com.example.beautifuldecor.browser.audio.RadioTest"],
                          env=env, cwd=work, timeout=limit)
            except subprocess.TimeoutExpired:
                return None, f"la prueba no termina en {limit} s"
            return res, (res.stdout + res.stderr)[-2500:]

        res, text = run_test("real")
        need(res is not None and res.returncode == 0 and "VEHICLE RADIO OK" in res.stdout, "la prueba de la radio falló:\n" + text)
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
            rm, _ = run_test(tag, {src_path: str(f)})
            need(rm is not None and rm.returncode != 0, f"la prueba NO se dio cuenta del mutante '{tag}' (debía fallar)")

        mutant("titulos_en_el_sonido", "browser/audio/IcyStream.java", "int n = this.in.read(buf, off, Math.min(len, this.untilMeta));", "int n = this.in.read(buf, off, len);")
        mutant("titulo_cortado_en_comilla", "browser/audio/IcyStream.java", "int stop = text.indexOf(\"';\", start);", "int stop = text.indexOf('\\'', start);")
        mutant("no_pide_titulos", "browser/audio/AudioPipe.java", 'Map.of("Icy-MetaData", "1")', 'Map.of("Icy-MetaData", "0")')
        mutant("navegador_pide_titulos", "browser/audio/AudioPipe.java", "this(env, retryDelayMs, false);", "this(env, retryDelayMs, true);")
        mutant("acepta_ftp", "radio/RadioStations.java", '} else if (lower.startsWith("http://")) {', '} else if (lower.contains("://")) {')
        mutant("propia_al_final", "radio/RadioPicker.java", "Comparator.comparing((Candidate c) -> !c.own())", "Comparator.comparing((Candidate c) -> c.own())")
        mutant("no_sigue_listas", "browser/audio/AudioPipe.java", "            return openUrl(target, depth + 1);", "            return openUrl(url, 2);")
        notes.append("la prueba de la radio falla con los 7 mutantes (títulos en el sonido, título cortado en una comilla, no pide títulos, el navegador pide títulos, acepta ftp, "
                     "la propia al final, no sigue listas .pls)")
    finally:
        shutil.rmtree(work, ignore_errors=True)

for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

if "--fast" not in sys.argv:
    rr = run([sys.executable, str(ROOT / "tools/verify_v340.py")], env=env, timeout=7200)
    need(rr.returncode == 0 and "OK V3.4.0" in rr.stdout, "verify_v340 falló:\n" + (rr.stdout + rr.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.5.0:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.5.0: radio propia en los vehículos terrestres (tecla X): emisoras, volumen, canción que suena y emisoras propias; suena desde el vehículo para los cercanos.")
for n in notes:
    print("  (nota)", n)
