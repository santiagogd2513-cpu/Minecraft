#!/usr/bin/env python3
"""V7.5.0: arregla el parpadeo (z-fighting) de los modelos de tren y acomoda el pupitre de la cabina del metro.
 1) Pupitre del metro: estaba dentro de la pared de la nariz (z 52..56); pasa a z 48..52, delante de la pared.
 2) Caras que miran al mismo lado, en el mismo plano y que se solapan (se tapan entre sí y parpadean): la caja más chica sobresale 0,05
    con CubeDeformation (la textura no cambia). Una caja de vidrio que coincide con el cuerpo también sobresale.
Se ejecuta una sola vez sobre client/TrainCarModel.java (si ya se aplicó, no hace nada)."""
import itertools
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
P = ROOT / "src/main/java/com/example/beautifuldecor/client/TrainCarModel.java"
src = P.read_text(encoding="utf-8")
BOX = re.compile(r'addBox\((-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f\)')
GROW = 0.05
MIN_AREA = 1.0


def fl(x):
    s = ("%g" % x)
    return s + "f"


def fix_layer(layer_name, lay):
    parts = {}
    for m in re.finditer(r'addOrReplaceChild\("(body|glass)",\s*CubeListBuilder\.create\(\)(.*?)PartPose', lay, re.S):
        parts[m.group(1)] = (m.start(2), m.end(2))
    if "body" not in parts:
        return lay, 0
    boxes = []                 # (part, start, end, vals)
    for part, (a, b) in parts.items():
        for m in BOX.finditer(lay, a, b):
            boxes.append((part, m.start(), m.end(), tuple(float(m.group(i)) for i in range(1, 7))))

    def vol(v):
        return v[3] * v[4] * v[5]

    grow = set()
    for (i, A), (j, B) in itertools.combinations(list(enumerate(boxes)), 2):
        if A[0] == "glass" and B[0] == "glass":
            continue
        if A[0] != B[0]:
            glass_i = i if A[0] == "glass" else j
        else:
            glass_i = None
        a, b = A[3], B[3]
        hit = False
        for ax in range(3):
            o = [k for k in range(3) if k != ax]
            for side in (0, 1):
                pa = a[ax] + (a[3 + ax] if side else 0)
                pb = b[ax] + (b[3 + ax] if side else 0)
                if abs(pa - pb) > 1e-6:
                    continue
                w1 = min(a[o[0]] + a[3 + o[0]], b[o[0]] + b[3 + o[0]]) - max(a[o[0]], b[o[0]])
                w2 = min(a[o[1]] + a[3 + o[1]], b[o[1]] + b[3 + o[1]]) - max(a[o[1]], b[o[1]])
                if w1 > 1e-6 and w2 > 1e-6 and w1 * w2 >= MIN_AREA:
                    hit = True
        if hit:
            if glass_i is not None:
                grow.add(glass_i)
            else:
                grow.add(i if vol(a) <= vol(b) else j)
    if not grow:
        return lay, 0
    out = lay
    for idx in sorted(grow, reverse=True):
        part, s, e, v = boxes[idx]
        seg = out[s:e]
        out = out[:s] + seg[:-1] + ", new CubeDeformation(%sf))" % ("%g" % GROW) + out[e:]
    return out, len(grow)


def shift_metro_desk(lay):
    """Mueve las cajas del pupitre (x dentro de +-9, z entre -56 y -52, alturas 12..28) 4 hacia +z (java: el eje z va al revés del generador)."""
    n = 0

    def rep(m):
        nonlocal n
        x, y, z, w, h, d = (float(m.group(i)) for i in range(1, 7))
        if abs(x) <= 9 and x + w <= 9 and z >= -56 and z + d <= -52 and -28 <= y and y + h <= -12:
            n += 1
            return "addBox(%s, %s, %s, %s, %s, %s)" % (fl(x), fl(y), fl(z + 4), fl(w), fl(h), fl(d))
        return m.group(0)
    a = lay.index('addOrReplaceChild("body"')
    b = lay.index("PartPose", a)
    return lay[:a] + BOX.sub(rep, lay[a:b]) + lay[b:], n


def main():
    global src
    if "CubeDeformation" in src and "new CubeDeformation(0.05f)" in src:
        print("ya aplicado")
        return
    names = re.findall(r"private static LayerDefinition create(\w+)Layer", src)
    for nm in names:
        a = src.index("private static LayerDefinition create" + nm + "Layer")
        b = src.find("private static LayerDefinition create", a + 10)
        e = b if b > 0 else len(src)
        lay = src[a:e]
        if nm == "MetroHead":
            lay, moved = shift_metro_desk(lay)
            print("MetroHead: cajas del pupitre movidas:", moved)
        lay, n = fix_layer(nm, lay)
        print(nm, "cajas agrandadas:", n)
        src = src[:a] + lay + src[e:]
    if "import net.minecraft.client.model.geom.builders.CubeDeformation;" not in src:
        src = src.replace("import net.minecraft.client.model.geom.builders.CubeListBuilder;",
                          "import net.minecraft.client.model.geom.builders.CubeDeformation;\nimport net.minecraft.client.model.geom.builders.CubeListBuilder;", 1)
    P.write_text(src, encoding="utf-8")


main()
