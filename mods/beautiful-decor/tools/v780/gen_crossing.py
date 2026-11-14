#!/usr/bin/env python3
"""V7.8.0: texturas, modelos, blockstates, items, loot y textos de los cruces de tren."""
import json, random
from pathlib import Path
from PIL import Image

R = Path(__file__).resolve().parent.parent.parent
A = R / "src/main/resources/assets/beautifuldecor"
D = R / "src/main/resources/data/beautifuldecor"
MOD = "beautifuldecor"

def wj(p, obj):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=1, ensure_ascii=False), encoding="utf-8")

# ---------- texturas (16x16 con un poco de ruido, para que no se vean planas)
def tex(name, rgb, edge=0.82, seed=1):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1.0 + rnd.uniform(-0.05, 0.05)
            if x in (0, 15) or y in (0, 15):
                k *= edge
            im.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in rgb) + (255,))
    im.save(A / "textures/block" / f"{name}.png")

tex("crossing_red", (205, 32, 32), seed=11)
tex("crossing_white", (238, 238, 238), seed=12)
tex("crossing_yellow", (232, 190, 36), seed=13)
tex("crossing_dark", (46, 49, 54), seed=14)

T = lambda n: f"{MOD}:block/{n}"
ALL = ["north", "east", "south", "west", "up", "down"]

def box(x1, y1, z1, x2, y2, z2, t):
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": {f: {"texture": f"#{t}"} for f in ALL}}

def model(name, textures, elements):
    textures = dict(textures)
    textures.setdefault("particle", next(iter(textures.values())))
    wj(A / "models/block" / f"{name}.json", {"parent": "block/block", "textures": textures, "elements": elements})

TEX = {"metal": T("metal"), "dark": T("crossing_dark"), "yellow": T("crossing_yellow"), "red": T("crossing_red"),
       "white": T("crossing_white"), "on": T("signal_red_on"), "off": T("signal_red_off"),
       "green": T("signal_green_on")}

# ---------- detector
for powered in (False, True):
    led = "on" if powered else "green"
    el = [box(4, 0, 4, 12, 11, 12, "yellow"),
          box(6, 11, 6, 10, 13, 10, led),
          box(5, 5, 3.5, 11, 9, 4, "dark"), box(5, 5, 12, 11, 9, 12.5, "dark"),
          box(3.5, 5, 5, 4, 9, 11, "dark"), box(12, 5, 5, 12.5, 9, 11, "dark")]
    model("crossing_detector_on" if powered else "crossing_detector_off", TEX, el)
wj(A / "blockstates/crossing_detector.json", {"variants": {
    "powered=false": {"model": T("crossing_detector_off")}, "powered=true": {"model": T("crossing_detector_on")}}})

# ---------- sostenedor
model("crossing_post", TEX, [box(6, 0, 6, 10, 16, 10, "metal"), box(4, 0, 4, 12, 2, 12, "dark"), box(5.5, 6, 5.5, 10.5, 7.5, 10.5, "yellow"),
                             box(5.5, 11, 5.5, 10.5, 12.5, 10.5, "yellow")])
wj(A / "blockstates/crossing_post.json", {"variants": {"": {"model": T("crossing_post")}}})

# ---------- mecanismo (brazo hacia el este)
model("crossing_mechanism", TEX, [box(3, 0, 3, 13, 14, 13, "dark"), box(2, 14, 2, 14, 15, 14, "metal"), box(13, 5, 6, 16, 11, 10, "metal"),
                                  box(3.5, 4, 2.5, 12.5, 10, 3, "yellow"), box(3.5, 4, 13, 12.5, 10, 13.5, "yellow")])
ROT = {"east": 0, "south": 90, "west": 180, "north": 270}
wj(A / "blockstates/crossing_mechanism.json", {"variants": {f"facing={d}": {"model": T("crossing_mechanism"), "y": r} for d, r in ROT.items()}})

# ---------- semáforo (paneles hacia el norte y el sur)
def light_model(lamp):
    a_on = lamp == 1
    b_on = lamp == 2
    el = [box(7, 0, 7, 9, 4, 9, "metal"), box(1, 3, 6, 15, 14, 10, "dark")]
    for (x1, x2, on) in ((2, 6, a_on), (10, 14, b_on)):
        t = "on" if on else "off"
        el.append(box(x1, 6, 5.5, x2, 11, 6, t))
        el.append(box(x1, 6, 10, x2, 11, 10.5, t))
        el.append(box(x1 - 0.5, 11, 5, x2 + 0.5, 11.8, 6.4, "metal"))      # visera
        el.append(box(x1 - 0.5, 11, 9.6, x2 + 0.5, 11.8, 11, "metal"))
    model(f"crossing_light_{lamp}", TEX, el)
for l in range(3):
    light_model(l)
ROTL = {"north": 0, "east": 90, "south": 180, "west": 270}
wj(A / "blockstates/crossing_light.json", {"variants": {f"facing={d},lamp={l}": {"model": T(f"crossing_light_{l}"), "y": r}
                                                          for d, r in ROTL.items() for l in range(3)}})

# ---------- barrera (hacia el este; pivote en x=0 de la parte 0)
def stripe(i):
    return "red" if i % 2 == 0 else "white"

def arm_cells(arm, part):
    """Devuelve los elementos de una parte de la barrera para una posición del brazo."""
    el = []
    if arm == 3:                                    # horizontal
        for i in range(4):
            el.append(box(4 * i, 6.8, 7, 4 * i + 4, 9.2, 9, stripe(i)))
    elif arm == 2:                                  # ~9 grados
        for i in range(4):
            d = 16 * part + 4 * i + 2
            yc = 8 + 0.16 * d
            el.append(box(4 * i, yc - 1.2, 7, 4 * i + 4, yc + 1.2, 9, stripe(i)))
    elif arm == 1 and part == 0:                    # a 45 grados, solo el primer tramo
        for i in range(2):
            yc = 8 + (4 * i + 2)
            el.append(box(4 * i, yc - 1.2, 7, 4 * i + 4, yc + 1.2, 9, stripe(i)))
    elif arm == 0 and part == 0:                    # parada: un poste vertical pegado al mecanismo
        for i in range(4):
            el.append(box(0, 4 * i, 7, 2.4, 4 * i + 4, 9, stripe(i)))
    if arm >= 2 and part == 2:                      # luz roja en la punta
        top = el[-1]["to"][1]
        el.append(box(12, top, 7.5, 15.5, top + 1.8, 8.5, "on"))
    return el

names = {}
for arm in range(4):
    for part in range(3):
        el = arm_cells(arm, part)
        if not el:
            names[(arm, part)] = "crossing_arm_empty"
            continue
        n = f"crossing_arm_{arm}_{part}"
        if arm == 3 and part in (0, 1):
            n = "crossing_arm_3_0"                  # iguales
        names[(arm, part)] = n
        model(n, TEX, el)
model("crossing_arm_empty", TEX, [])
var = {}
for d, r in ROT.items():
    for arm in range(4):
        for part in range(3):
            v = {"model": T(names[(arm, part)])}
            if r:
                v["y"] = r
            var[f"facing={d},part={part},arm={arm}"] = v
wj(A / "blockstates/crossing_barrier.json", {"variants": var})

# ---------- items
items = {"crossing_detector": "crossing_detector_off", "crossing_post": "crossing_post", "crossing_mechanism": "crossing_mechanism",
         "crossing_light": "crossing_light_1", "crossing_barrier": "crossing_arm_3_0"}
for it, m in items.items():
    wj(A / "models/item" / f"{it}.json", {"parent": T(m)})

# ---------- loot (la barrera no suelta por loot: la suelta el código una sola vez)
for b in ("crossing_detector", "crossing_post", "crossing_mechanism", "crossing_light"):
    wj(D / "loot_tables/blocks" / f"{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"{MOD}:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# ---------- textos
ES = {"crossing_detector": "Detector de trenes", "crossing_post": "Sostenedor de cruce", "crossing_mechanism": "Mecanismo de barrera",
      "crossing_light": "Semáforo de cruce", "crossing_barrier": "Barrera de cruce"}
EN = {"crossing_detector": "Train Detector", "crossing_post": "Crossing Post", "crossing_mechanism": "Barrier Mechanism",
      "crossing_light": "Crossing Signal", "crossing_barrier": "Crossing Barrier"}
EXTRA_ES = {"subtitles.beautifuldecor.crossing_bell": "Suena la campana del cruce",
            "hud.beautifuldecor.barrier_needs_mechanism": "Haz clic derecho en un lado del mecanismo de barrera",
            "hud.beautifuldecor.barrier_already": "Ese mecanismo ya tiene una barrera",
            "hud.beautifuldecor.barrier_no_room": "No hay espacio: la barrera necesita 3 bloques libres"}
EXTRA_EN = {"subtitles.beautifuldecor.crossing_bell": "Crossing bell rings",
            "hud.beautifuldecor.barrier_needs_mechanism": "Right-click a side of the barrier mechanism",
            "hud.beautifuldecor.barrier_already": "That mechanism already has a barrier",
            "hud.beautifuldecor.barrier_no_room": "No room: the barrier needs 3 free blocks"}

def add_lang(fname, names, extra):
    p = A / "lang" / fname
    s = p.read_text(encoding="utf-8")
    if '"block.beautifuldecor.crossing_detector"' in s:
        return
    lines = []
    for k, v in names.items():
        lines.append(f'  "block.beautifuldecor.{k}": "{v}",')
        lines.append(f'  "item.beautifuldecor.{k}": "{v}",')
    for k, v in extra.items():
        lines.append(f'  "{k}": "{v}",')
    marker = '  "itemGroup.beautifuldecor_parts"'
    assert marker in s, fname
    s = s.replace(marker, "\n".join(lines) + "\n" + marker, 1)
    json.loads(s)
    p.write_text(s, encoding="utf-8")

add_lang("es_es.json", ES, EXTRA_ES)
add_lang("en_us.json", EN, EXTRA_EN)
print("OK gen_crossing")
