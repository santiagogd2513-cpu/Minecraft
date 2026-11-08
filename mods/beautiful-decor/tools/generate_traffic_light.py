#!/usr/bin/env python3
"""Semaforo realista: cabeza de 3 luces con visera, en las 4 caras, sobre poste con base.
No toca la logica (propiedad color=red/yellow/green); solo modelos y texturas."""
import json, math, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "beautifuldecor")
TEX = os.path.join(ROOT, "textures", "block")
MOD = os.path.join(ROOT, "models", "block")

# ---------- texturas de lente (16x16, circulo con brillo) ----------
BASE = {"red": (235, 40, 35), "yellow": (255, 190, 25), "green": (40, 220, 90)}
def lens(color, on):
    r, g, b = BASE[color]
    im = Image.new("RGBA", (16, 16), (14, 14, 16, 255))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.2:
                if on:
                    k = 1.0 + 0.35 * max(0, 1 - d / 5) - 0.25 * (d / 7.2) ** 3
                    c = [min(255, int(v * k)) for v in (r, g, b)]
                    if d < 2.2 and x < 8 and y < 8: c = [min(255, v + 70) for v in c]
                else:
                    k = 0.22 - 0.06 * (d / 7.2)
                    c = [int(v * k) + 6 for v in (r, g, b)]
                im.putpixel((x, y), (*c, 255))
            elif d <= 7.9:
                im.putpixel((x, y), (30, 30, 34, 255))
    return im
for c in BASE:
    for on in (True, False):
        lens(c, on).save(os.path.join(TEX, f"signal_{c}_{'on' if on else 'off'}.png"))

# ---------- geometria ----------
def box(f, t, tex, faces=("north","east","south","west","up","down"), extra=None):
    el = {"from": f, "to": t, "faces": {}}
    for k in faces:
        el["faces"][k] = {"texture": tex}
    return el

def head_elements(state):
    els = []
    # base y poste
    els.append(box([5, 0, 5], [11, 1, 11], "#metal"))
    els.append(box([6, 1, 6], [10, 1.5, 10], "#metal"))
    els.append(box([7, 1.5, 7], [9, 2, 9], "#metal"))
    # cabeza (carcasa) y tapa
    els.append(box([4.5, 2, 4.5], [11.5, 15.5, 11.5], "#body"))
    els.append(box([5, 15.5, 5], [11, 16, 11], "#metal", faces=("north","east","south","west","up")))
    lamps = [("red", 11.5, 15.0), ("yellow", 7.0, 10.5), ("green", 2.5, 6.0)]
    # lado: (cara, funcion de coordenadas)
    def place(side, u0, u1, y0, y1, d0, d1):
        # u: ancho local 0..16 centrado en 8; d: profundidad hacia fuera desde la carcasa (4.5/11.5)
        if side == "north": return [u0, y0, 4.5 - d1], [u1, y1, 4.5 - d0]
        if side == "south": return [u0, y0, 11.5 + d0], [u1, y1, 11.5 + d1]
        if side == "west":  return [4.5 - d1, y0, u0], [4.5 - d0, y1, u1]
        return [11.5 + d0, y0, u0], [11.5 + d1, y1, u1]
    for side in ("north", "east", "south", "west"):
        for name, y0, y1 in lamps:
            tex = f"#{name}_on" if state == name else f"#{name}_off"
            f, t = place(side, 6.25, 9.75, y0, y1, 0, 0.15)
            el = box(f, t, tex, faces=(side,))
            el["faces"][side]["uv"] = [0, 0, 16, 16]
            if state == name:
                el["faces"][side]["forge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
            els.append(el)
            # visera: techo + dos laterales
            f, t = place(side, 5.5, 10.5, y1 + 0.1, y1 + 0.9, 0, 1.6); els.append(box(f, t, "#visor"))
            f, t = place(side, 5.5, 6.1, y0 - 0.1, y1 + 0.9, 0, 1.4); els.append(box(f, t, "#visor"))
            f, t = place(side, 9.9, 10.5, y0 - 0.1, y1 + 0.9, 0, 1.4); els.append(box(f, t, "#visor"))
    return els

def model(state):
    return {
        "parent": "block/block",
        "textures": {
            "particle": "beautifuldecor:block/traffic_body",
            "body": "beautifuldecor:block/traffic_body",
            "visor": "beautifuldecor:block/traffic_body",
            "metal": "beautifuldecor:block/metal",
            **{f"{c}_{o}": f"beautifuldecor:block/signal_{c}_{o}" for c in BASE for o in ("on", "off")},
        },
        "elements": head_elements(state),
    }

for s in BASE:
    with open(os.path.join(MOD, f"traffic_light_{s}.json"), "w") as fh:
        json.dump(model(s), fh, indent=1)
print("Semaforo regenerado.")
