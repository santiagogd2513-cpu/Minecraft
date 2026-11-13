#!/usr/bin/env python3
"""V7.5.0: texturas de brillo (faros) de las locomotoras: copia de la textura ya entregada SOLO los píxeles de las cajas 'headlight'.
Comprueba antes que el generador de V4.8.0 sigue dando el mismo orden de cajas y la misma posición en la textura que el Java entregado.
Uso (desde la raíz): python3 -m tools.v750.glow"""
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v480 import trains as T

ROOT = Path(__file__).resolve().parents[2]
JAVA = (ROOT / "src/main/java/com/example/beautifuldecor/client/TrainCarModel.java").read_text(encoding="utf-8")
TEX = ROOT / "src/main/resources/assets/beautifuldecor/textures/entity"
NAMES = {"freight_loco": "FreightLoco", "steam_classic": "SteamClassic", "steam_modern": "SteamModern", "metro_head": "MetroHead"}

for name, cls in NAMES.items():
    b = T.build(name)
    T.pack_all(b, name)
    a = JAVA.index("private static LayerDefinition create" + cls + "Layer")
    e = JAVA.find("private static LayerDefinition create", a + 10)
    lay = JAVA[a:e if e > 0 else None]
    body = re.search(r'addOrReplaceChild\("body",(.*?)PartPose', lay, re.S).group(1)
    glass = re.search(r'addOrReplaceChild\("glass",(.*?)PartPose', lay, re.S).group(1)
    jb = [(int(u), int(v)) for u, v in re.findall(r'texOffs\((\d+), (\d+)\)\.addBox', body)]
    jg = [(int(u), int(v)) for u, v in re.findall(r'texOffs\((\d+), (\d+)\)\.addBox', glass)]
    mine = [c for c in b.cubes if c.part is not None] if hasattr(b.cubes[0], "part") else b.cubes
    # el orden en el Java: primero las cajas de "body", luego las de "glass" (las ruedas son partes aparte y no cuentan aquí)
    bodies = [c for c in b.cubes if getattr(c, "part", None) is b.parts["body"]]
    glasses = [c for c in b.cubes if getattr(c, "part", None) is b.parts["glass"]]
    ok = [(int(c.u), int(c.v)) for c in bodies] == jb[:len(bodies)] if bodies else False
    print(name, "cajas body", len(bodies), "java", len(jb), "glass", len(glasses), len(jg), "sincronizado:", ok)
    if not ok:
        sys.exit("el generador ya no coincide con el Java entregado: no se genera el brillo de " + name)
    base = Image.open(TEX / (name + ".png")).convert("RGBA")
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    n = 0
    for c in bodies:
        if c.mat != "headlight":
            continue
        for f in ("top", "bottom", "west", "east", "front", "back"):
            try:
                x0, y0, cols, rows = vc.face_rect(c, f)
            except Exception:
                continue
            box = (x0, y0, x0 + cols, y0 + rows)
            out.paste(base.crop(box), box)
        n += 1
    out.save(TEX / (name + "_glow.png"))
    print("  ", name + "_glow.png", n, "cajas de faro")
