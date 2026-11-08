#!/usr/bin/env python3
"""Comprueba la V3.2.0: menú de colores de los vehículos (tecla R): pintura, manijas de las puertas y rines.

No hay Minecraft en el entorno. Se revisa lo que sí se puede revisar:
  * versión, README y NUEVOS_BLOQUES, idiomas es/en;
  * que las texturas guardadas sean EXACTAMENTE lo que generan los programas (así repintar no cambió ni un píxel de los colores de siempre)
    y que las máscaras *_paint.png sean las que calcula tools/v320/paint_masks.py;
  * que los colores de referencia y los canales de cada vehículo en Java (VehiclePaint) coincidan con los de los generadores;
  * que solo se puedan pintar moto, bicicleta, camioneta, camión de reparto y helicóptero (el carro RC y el dron NO), y que todo esté conectado
    (entidades con datos sincronizados y NBT, ítems, paquete de red con protocolo nuevo, renderizadores, tecla R, recarga de recursos);
  * la cuenta del repintado (PaintMath) en Java con las texturas y máscaras REALES, contra la fórmula de Python, y que con los colores
    originales salga la textura sin cambiar un píxel;
  * la lógica de colores (VehiclePaint): limpieza, vista previa, ítem y NBT;
  * que el Java nuevo no tenga errores de sintaxis;
  * y corre lo de la V3.1.0 (tools/verify_v310.py, que a su vez corre las anteriores).

Uso: python3 tools/verify_v320.py [--fast]     (--fast se salta lo de las versiones anteriores)
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
C = JAVA / "client"
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
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env, cwd=cwd)


def has(path, text, msg):
    need(text in read(path), msg)


# ---------------------------------------------------------------- versión y documentos
need(re.search(r"version = '3\.([2-9]|\d{2,})\.\d+'", read(ROOT / "build.gradle")), "build.gradle no está en 3.2.x o superior")
need(re.search(r"mod_version=3\.([2-9]|\d{2,})\.\d+", read(ROOT / "gradle.properties")), "gradle.properties no está en 3.2.x o superior")
need("V3.2.0" in read(ROOT / "README.md"), "README sin la entrada V3.2.0")
nuevos = read(ROOT / "NUEVOS_BLOQUES.txt")
need(nuevos.startswith("PARCHE V3.") and "PARCHE V3.2.0" in nuevos and "PARCHE V3.1.3" in nuevos,
     "NUEVOS_BLOQUES debe empezar con una sección V3.x y conservar la V3.2.0 y la V3.1.3")
for f in sorted(ASSETS.rglob("*.json")):
    try:
        json.loads(read(f))
    except Exception as exc:  # noqa: BLE001
        errors.append(f"JSON inválido: {f.relative_to(ROOT)}: {exc}")
es = json.loads(read(ASSETS / "lang/es_es.json"))
en = json.loads(read(ASSETS / "lang/en_us.json"))
need(set(es) == set(en), "es_es y en_us no tienen las mismas claves: " + str(sorted(set(es) ^ set(en))[:6]))
code_new = "".join(read(p) for p in (C / "VehicleColorScreen.java", C / "GarageClientEvents.java", C / "VehiclePaintTextures.java"))
keys = set(re.findall(r'"(gui\.beautifuldecor\.[a-z_.]+)"', read(C / "VehicleColorScreen.java")))
keys = {k for k in keys if not k.endswith(".")}      # V3.6.0: "gui.beautifuldecor.paint." es el comienzo de claves que se arman con el nombre de cada parte
for k in keys:
    need(k in es and k in en, f"el menú usa la clave {k} y no está en es/en")
for k in ("gui.beautifuldecor.vehicle.title", "gui.beautifuldecor.vehicle.body", "gui.beautifuldecor.vehicle.handle", "gui.beautifuldecor.vehicle.rim",
          "gui.beautifuldecor.vehicle.apply", "gui.beautifuldecor.vehicle.original", "gui.beautifuldecor.vehicle.cancel"):
    need(k in es and k in en, f"falta la clave {k} en es/en")
need(all(len(en[k]) <= 8 for k in ("gui.beautifuldecor.vehicle.handle", "gui.beautifuldecor.vehicle.rim", "gui.beautifuldecor.vehicle.body")),
     "los nombres de las fichas en inglés son largos y no caben en la ficha")
need("vehículo" in es.get("key.beautifuldecor.garage_colors", "") and "vehicle" in en.get("key.beautifuldecor.garage_colors", ""),
     "el nombre de la tecla R debe mencionar los vehículos")

# ---------------------------------------------------------------- texturas y máscaras
KINDS = {  # tipo Java -> (textura, módulo generador, clase de entidad)
    "MOTORCYCLE": "motorcycle", "BICYCLE": "bicycle", "PICKUP": "ford_f150", "DELIVERY_TRUCK": "delivery_truck", "HELICOPTER": "helicopter"}
java_kinds = {}
text = read(JAVA / "entity/VehiclePaint.java")
for m in re.finditer(r'^\s*([A-Z_]+)\("(\w+)",\s*(0x[0-9A-Fa-f]+|ORIGINAL),\s*(0x[0-9A-Fa-f]+|ORIGINAL),\s*(0x[0-9A-Fa-f]+|ORIGINAL)\)', text, re.M):
    java_kinds[m.group(1)] = (m.group(2), [-1 if v == "ORIGINAL" else int(v, 16) for v in m.groups()[2:]])
need(set(java_kinds) == set(KINDS), f"VehiclePaint.Kind debe tener exactamente {sorted(KINDS)}; tiene {sorted(java_kinds)}")
try:
    import numpy as np
    from PIL import Image
    from tools.v320 import paint_masks as pm
    from tools.v320 import paintkit as pk

    for kind, tex_name in KINDS.items():
        gen, painter = pm.VEHICLES[tex_name]
        jn, jrefs = java_kinds[kind]
        need(jn == tex_name, f"{kind}: la textura en Java es {jn} y debería ser {tex_name}")
        for i, ch in enumerate(pk.CHANNELS):
            if ch in painter.REFS:
                want = sum(int(v) << s for v, s in zip(painter.REFS[ch], (16, 8, 0)))
                need(jrefs[i] == want, f"{kind}.{ch}: el color de referencia en Java es {jrefs[i]:06X} y el del generador es {want:06X}")
            else:
                need(jrefs[i] == -1, f"{kind}.{ch}: el generador no tiene ese canal pero Java sí")
        # textura guardada == lo que genera el programa (con la paleta de siempre)
        painter.set_paint()
        built = gen.build_all()
        tex = np.asarray(built[1].convert("RGBA"))
        saved = np.asarray(Image.open(ENT / f"{tex_name}.png").convert("RGBA"))
        need(saved.shape == tex.shape and np.array_equal(saved, tex), f"textures/entity/{tex_name}.png no coincide con lo que generan los programas")
        # máscara guardada == la que calcula paint_masks
        ks, ref, worst = pm.measure(gen, painter)
        mask = pm.to_png(ks, ref.shape)
        saved_mask = np.asarray(Image.open(ENT / f"{tex_name}_paint.png").convert("RGB"))
        need(saved_mask.shape == mask.shape and np.array_equal(saved_mask, mask), f"{tex_name}_paint.png no coincide con lo que calcula tools/v320/paint_masks.py")
        need(saved_mask.shape[:2] == saved.shape[:2], f"{tex_name}_paint.png no mide lo mismo que la textura")
        need(worst < 1e-3, f"{tex_name}: algún píxel no depende linealmente del color (desvío {worst})")
        alpha = saved[..., 3] > 0
        for i, ch in enumerate(pk.CHANNELS):
            n = int(((saved_mask[..., i] > 0) & alpha).sum())
            need((n > 0) == (ch in painter.REFS), f"{tex_name}: el canal {ch} {'no tiene' if ch in painter.REFS else 'tiene'} píxeles en la máscara")
        # los píxeles transparentes de la textura no deben depender del color
        need(int(((saved_mask > 0).any(axis=-1) & ~alpha).sum()) == 0, f"{tex_name}: hay máscara sobre píxeles transparentes")
    notes.append("las 5 texturas guardadas coinciden con sus generadores y las 5 máscaras con tools/v320/paint_masks.py; los colores de referencia de Java y de Python son los mismos")
except Exception as exc:  # noqa: BLE001
    import traceback
    errors.append("no se pudieron comparar las texturas y máscaras: " + "".join(traceback.format_exception_only(type(exc), exc)).strip())
    notes.append(traceback.format_exc()[-1500:])

# ---------------------------------------------------------------- conexiones
has(JAVA / "entity/VehicleEntity.java", "extends Entity implements PaintableVehicle", "VehicleEntity debe implementar PaintableVehicle")
has(JAVA / "entity/HelicopterEntity.java", "extends Entity implements PaintableVehicle", "HelicopterEntity debe implementar PaintableVehicle")
for fn, kind in (("MotorcycleEntity", "MOTORCYCLE"), ("BicycleEntity", "BICYCLE"), ("PickupEntity", "PICKUP"), ("DeliveryTruckEntity", "DELIVERY_TRUCK")):
    has(JAVA / f"entity/{fn}.java", f"VehiclePaint.Kind.{kind}", f"{fn} debe declarar su paintKind {kind}")
has(JAVA / "entity/HelicopterEntity.java", "VehiclePaint.Kind.HELICOPTER", "HelicopterEntity debe declarar su paintKind")
for fn in ("RcCarEntity", "DroneEntity"):
    src = read(JAVA / f"entity/{fn}.java")
    need("PaintableVehicle" not in src and "VehiclePaint" not in src, f"{fn} NO debe poder pintarse (el carro RC y el dron no cuentan como vehículos)")
for fn in ("RcCarRenderer", "DroneRenderer", "ElevatorCabinRenderer"):
    need("VehiclePaintTextures" not in read(C / f"{fn}.java"), f"{fn} no debe usar texturas repintadas")
for fn in ("MotorcycleRenderer", "BicycleRenderer", "PickupRenderer", "DeliveryTruckRenderer", "HelicopterRenderer"):
    src = read(C / f"{fn}.java")
    need("VehiclePaintTextures.textureFor" in src, f"{fn} debe dibujar con la textura repintada")
    need("(TEXTURE)" not in src, f"{fn} todavía dibuja con la textura de siempre en alguna pasada")
for fn in ("VehicleEntity", "HelicopterEntity"):
    src = read(JAVA / f"entity/{fn}.java")
    need(src.count("SynchedEntityData.defineId") >= 3 and "PAINT_BODY" in src and "PAINT_HANDLE" in src and "PAINT_RIM" in src, f"{fn}: faltan los datos sincronizados de color")
    need("VehiclePaint.read(this, tag)" in src and "VehiclePaint.write(this, tag)" in src, f"{fn}: los colores deben guardarse en el mundo (NBT)")
    need("VehiclePaint.carry(this, this.getPickResult())" in src, f"{fn}: al recogerlo el ítem debe llevarse los colores")
has(JAVA / "item/VehicleItem.java", "VehiclePaint.restore(vehicle", "VehicleItem debe poner los colores del ítem")
has(JAVA / "item/HelicopterItem.java", "VehiclePaint.restore(helicopter", "HelicopterItem debe poner los colores del ítem")
msgs = read(JAVA / "network/ModMessages.java")
need(re.search(r'PROTOCOL = "([9]|\d{2,})"', msgs), "el protocolo de red debe pasar a 9 al agregar un paquete")
need("VehiclePaintPacket.class" in msgs and "NetworkDirection.PLAY_TO_SERVER" in msgs.split("VehiclePaintPacket.class")[1].split(".add()")[0],
     "VehiclePaintPacket debe ir de cliente a servidor")
# el paquete nuevo va al final: no mueve los números de los anteriores
need(msgs.rindex("VehiclePaintPacket.class") > msgs.rindex("RcCarControlPacket.class"), "el paquete nuevo debe registrarse al final")
pk_src = read(JAVA / "network/VehiclePaintPacket.java")
for part in ("distanceToSqr", "instanceof PaintableVehicle", "VehiclePaint.clean", "context.getSender()"):
    need(part in pk_src, f"VehiclePaintPacket: falta la revisión del servidor ({part})")
has(C / "GarageClientEvents.java", "VehicleColorScreen.tryOpen", "la tecla R debe abrir el menú de colores del vehículo")
has(C / "GarageClientEvents.java", "VehiclePaintTextures.reset()", "al salir del mundo se deben soltar las texturas repintadas")
has(C / "ClientEvents.java", "RegisterClientReloadListenersEvent", "al recargar recursos se deben soltar las texturas repintadas")
scr = read(C / "VehicleColorScreen.java")
need("public void removed()" in scr and "setPaintPreview(null)" in scr, "el menú debe quitar la vista previa al cerrarse, sea como sea")
need("getCameraEntity() != player" in scr, "R no debe abrir el menú mientras se pilota un carro RC o un dron")
need("ModMessages.sendToServer(new VehiclePaintPacket" in scr, "Aplicar debe mandar el paquete")
need("DarkUi." in scr and "GarageColorScreen" not in scr.replace("como los demás menús", ""), "el menú debe usar el estilo oscuro DarkUi")
need("fill(0, 0, this.width, this.height, DarkUi.DIM)" not in scr, "el menú no debe oscurecer toda la pantalla: el vehículo tiene que verse")

# ---------------------------------------------------------------- Java
javac = shutil.which("javac")
java = shutil.which("java")
env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
NEW_JAVA = ["entity/VehiclePaint.java", "entity/PaintableVehicle.java", "entity/VehicleEntity.java", "entity/HelicopterEntity.java",
            "entity/MotorcycleEntity.java", "entity/BicycleEntity.java", "entity/PickupEntity.java", "entity/DeliveryTruckEntity.java",
            "network/VehiclePaintPacket.java", "network/ModMessages.java", "item/VehicleItem.java", "item/HelicopterItem.java",
            "client/PaintMath.java", "client/VehiclePaintTextures.java", "client/VehicleColorScreen.java", "client/GarageClientEvents.java",
            "client/ClientEvents.java", "client/MotorcycleRenderer.java", "client/BicycleRenderer.java", "client/PickupRenderer.java",
            "client/DeliveryTruckRenderer.java", "client/HelicopterRenderer.java"]
if not (javac and java):
    notes.append("sin javac/java: no se revisó el Java ni se corrieron las pruebas")
else:
    out = Path(tempfile.mkdtemp(prefix="v320_syntax_"))
    try:
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "20000", "-d", str(out)] + [str(JAVA / rel) for rel in NEW_JAVA], env=env, cwd=out)
        bad = []
        for line in r.stderr.splitlines():
            if ": error:" not in line:
                continue
            if any(k in line for k in ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype",
                                       "incompatible types", "cannot be applied", "is not abstract and does not override")):
                continue
            bad.append(line)
        need(not bad, "errores de sintaxis en el Java nuevo:\n" + "\n".join(bad[:15]))
        notes.append("la sintaxis del Java nuevo es correcta (los tipos de Minecraft no se pudieron comprobar sin Minecraft)")
    finally:
        shutil.rmtree(out, ignore_errors=True)

    # lógica de colores (VehiclePaint) con piezas de mentira de Minecraft que tienen la misma forma que las de 1.20.1
    work = Path(tempfile.mkdtemp(prefix="v320_logic_"))
    try:
        outp = work / "out"
        outp.mkdir()
        srcs = [str(p) for p in (ROOT / "tools/v320/teststub").rglob("*.java")]
        srcs += [str(JAVA / "entity/VehiclePaint.java"), str(JAVA / "entity/VehiclePaintExtras.java"), str(JAVA / "entity/PaintableVehicle.java"), str(ROOT / "tools/v320/java/PaintLogicTest.java")]
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp)] + srcs, env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba de la lógica de colores:\n" + r.stderr[-2000:])
        if r.returncode == 0:
            r = run([java, "-cp", str(outp), "com.example.beautifuldecor.entity.PaintLogicTest"], env=env, cwd=work, timeout=120)
            need(r.returncode == 0 and "PAINT LOGIC OK" in r.stdout, "la lógica de colores falló:\n" + (r.stdout + r.stderr)[-2000:])
            notes.append("lógica de colores: canales por vehículo, limpieza, vista previa, ítem y NBT (incluso mundos viejos) correctos")
    finally:
        shutil.rmtree(work, ignore_errors=True)

    # la cuenta del repintado, en Java, contra la fórmula de Python y con las texturas reales
    work = Path(tempfile.mkdtemp(prefix="v320_math_"))
    try:
        from tools.v320 import paint_masks as pm
        cases = []
        palette = [(200, 24, 24), (240, 240, 244), (12, 12, 14), (40, 200, 90), (250, 190, 30), (90, 60, 200)]
        for name, (gen, painter) in pm.VEHICLES.items():
            base = np.asarray(Image.open(ENT / f"{name}.png").convert("RGBA"))
            mask = np.asarray(Image.open(ENT / f"{name}_paint.png").convert("RGB"))
            refs = {ch: tuple(int(x) for x in v) for ch, v in painter.REFS.items()}
            combos = []
            for ch in painter.REFS:
                for col in palette:
                    combos.append({ch: col})
            combos.append({ch: palette[(i + 1) % len(palette)] for i, ch in enumerate(painter.REFS)})
            for j, news in enumerate(combos):
                expected = pm.apply(base, mask, refs, news)
                exp_path = work / f"{name}_{j}.png"
                Image.fromarray(expected, "RGBA").save(exp_path)

                def hexc(d, ch):
                    return "-" if ch not in d else "%02X%02X%02X" % tuple(d[ch])
                # un canal que no se pide queda con su color de referencia (y uno que el vehículo no tiene, con "-")
                full = {ch: news.get(ch, refs[ch]) for ch in refs}
                cases.append(f"{name} " + " ".join(hexc(refs, ch) for ch in pk.CHANNELS) + " " + " ".join(hexc(full, ch) for ch in pk.CHANNELS) + f" {exp_path}")
        (work / "cases.txt").write_text("\n".join(cases) + "\n", encoding="utf-8")
        outp = work / "out"
        outp.mkdir()
        r = run([javac, "--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-d", str(outp), str(C / "PaintMath.java"),
                 str(ROOT / "tools/v320/java/PaintMathTest.java")], env=env, cwd=work)
        need(r.returncode == 0 and "warning" not in r.stderr, "no compila la prueba de PaintMath:\n" + r.stderr[-2000:])
        if r.returncode == 0:
            r = run([java, "-cp", str(outp), "com.example.beautifuldecor.client.PaintMathTest", str(ENT), str(work / "cases.txt")], env=env, cwd=work, timeout=300)
            need(r.returncode == 0 and "PAINT MATH OK" in r.stdout, "PaintMath falló:\n" + (r.stdout + r.stderr)[-2500:])
            notes.append("PaintMath (Java) con las texturas reales: " + (r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "?"))
    except Exception as exc:  # noqa: BLE001
        import traceback
        errors.append("no se pudo probar PaintMath: " + "".join(traceback.format_exception_only(type(exc), exc)).strip())
        notes.append(traceback.format_exc()[-1500:])
    finally:
        shutil.rmtree(work, ignore_errors=True)

# ---------------------------------------------------------------- limpieza del proyecto
for bad in ROOT.rglob("__pycache__"):
    notes.append(f"(aviso) hay un __pycache__ que no debe ir en el zip: {bad.relative_to(ROOT)}")
for stray in list(ROOT.glob("out")) + list(ROOT.glob("javac.*.args")):
    errors.append(f"sobra en la raíz del proyecto: {stray.name}")

# ---------------------------------------------------------------- lo de la V3.1.0 (y, con él, lo de las anteriores)
if "--fast" not in sys.argv:
    r = run([sys.executable, str(ROOT / "tools/verify_v310.py")], env=env, timeout=7200)
    need(r.returncode == 0 and "OK V3.1.0" in r.stdout, "verify_v310 falló:\n" + (r.stdout + r.stderr)[-3500:])

if errors:
    print("FALLÓ la verificación V3.2.0:")
    for e in errors:
        print(" -", e)
    for n in notes:
        print("  (nota)", n)
    sys.exit(1)
print("OK V3.2.0: menú de colores de los vehículos (tecla R): pintura, manijas y rines, con vista previa en vivo; texturas de siempre intactas; "
      "solo moto, bicicleta, camioneta, camión y helicóptero (ni carro RC ni dron); recursos, idiomas y registros completos.")
for n in notes:
    print("  (nota)", n)
