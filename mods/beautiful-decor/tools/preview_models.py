#!/usr/bin/env python3
"""Visor sencillo de modelos de bloque (sin abrir Minecraft).

Uso:  python3 tools/preview_models.py salida.png modelo1 [modelo2 ...] [--yaw 45] [--pitch 30] [--size 360]
Cada modelo se dibuja en una cuadrícula; con --views se muestran dos ángulos (frente y detrás).
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def default_uv(face, f, t):
    return {
        "down": [f[0], 16 - t[2], t[0], 16 - f[2]],
        "up": [f[0], f[2], t[0], t[2]],
        "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
        "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
        "west": [f[2], 16 - t[1], t[2], 16 - f[1]],
        "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    }[face]


def face_frame(face, f, t):
    """Esquina superior-izquierda de la textura y vectores +u, +v (en espacio del modelo)."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "north": ((x2, y2, z1), (-(x2 - x1), 0, 0), (0, -(y2 - y1), 0)),
        "south": ((x1, y2, z2), ((x2 - x1), 0, 0), (0, -(y2 - y1), 0)),
        "east": ((x2, y2, z2), (0, 0, -(z2 - z1)), (0, -(y2 - y1), 0)),
        "west": ((x1, y2, z1), (0, 0, (z2 - z1)), (0, -(y2 - y1), 0)),
        "up": ((x1, y2, z1), ((x2 - x1), 0, 0), (0, 0, (z2 - z1))),
        "down": ((x1, y1, z2), ((x2 - x1), 0, 0), (0, 0, -(z2 - z1))),
    }[face]


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
           "up": (0, 1, 0), "down": (0, -1, 0)}


def rot_point(p, rot):
    if not rot:
        return p
    o = rot["origin"]
    a = math.radians(rot["angle"])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def rot_vec(v, rot):
    return tuple(a - b for a, b in zip(rot_point(v, rot and {**rot, "origin": (0, 0, 0)}), (0, 0, 0)))


def load_model(name):
    ns, _, path = name.partition(":")
    if not path:
        path, ns = ns, "beautifuldecor"
    if "/" not in path:
        path = "block/" + path
    p = ASSETS / "models" / f"{path}.json"
    if ns != "beautifuldecor" or not p.exists():
        return {"textures": {}, "elements": []}
    data = json.loads(p.read_text(encoding="utf-8"))
    parent = data.get("parent")
    base = load_model(parent) if parent else {}
    merged = {"textures": dict(base.get("textures", {})), "elements": base.get("elements", [])}
    merged["textures"].update(data.get("textures", {}))
    if "elements" in data:
        merged["elements"] = data["elements"]
    return merged


_cache = {}


def texture(ref):
    ns, _, path = ref.partition(":")
    if ns != "beautifuldecor":
        return Image.new("RGBA", (16, 16), (170, 170, 170, 255))
    if ref not in _cache:
        p = ASSETS / "textures" / f"{path}.png"
        _cache[ref] = Image.open(p).convert("RGBA") if p.exists() else Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    return _cache[ref]


def resolve(tex, key):
    for _ in range(10):
        if not key.startswith("#"):
            return key
        key = tex.get(key[1:], "")
    return key


def render(model_name, yaw, pitch, size, bg, scene=None, center=(8, 8, 8), span=27.0):
    """model_name: nombre de modelo; scene: lista [(modelo, (dx,dy,dz))] para dibujar varias piezas juntas."""
    items = scene or [(model_name, (0, 0, 0))]
    S = size / span
    cx, cy = size / 2, size * 0.5

    def project(p):
        x, y, z = p[0] - center[0], p[1] - center[1], p[2] - center[2]
        a = math.radians(yaw)
        x2, z2 = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
        b = math.radians(pitch)
        y2 = y * math.cos(b) - z2 * math.sin(b)
        z3 = y * math.sin(b) + z2 * math.cos(b)
        return (cx + x2 * S, cy - y2 * S, z3)

    def view_normal(n):
        a = math.radians(yaw)
        b = math.radians(pitch)
        x2, z2 = n[0] * math.cos(a) + n[2] * math.sin(a), -n[0] * math.sin(a) + n[2] * math.cos(a)
        return (x2, n[1] * math.cos(b) - z2 * math.sin(b), n[1] * math.sin(b) + z2 * math.cos(b))

    faces = []
    all_els = []
    for name, off in items:
        m = load_model(name)
        for el in m["elements"]:
            all_els.append((el, m["textures"], off))
    for el, tex, off in all_els:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        if off != (0, 0, 0):
            rot = rot and {**rot, "origin": [a + b for a, b in zip(rot["origin"], off)]}
        for fn, fd in el["faces"].items():
            origin, du, dv = face_frame(fn, f, t)
            origin = tuple(a + b for a, b in zip(origin, off))
            fo, to_ = f, t
            tl = rot_point(origin, rot)
            tr = rot_point(tuple(o + d for o, d in zip(origin, du)), rot)
            bl = rot_point(tuple(o + d for o, d in zip(origin, dv)), rot)
            br = rot_point(tuple(o + a + b for o, a, b in zip(origin, du, dv)), rot)
            n = rot_vec(NORMALS[fn], rot)
            vn = view_normal(n)
            if vn[2] <= 1e-6:      # cara de espaldas a la cámara (la cámara está hacia +z)
                continue
            ptl, ptr, pbl, pbr = (project(p) for p in (tl, tr, bl, br))
            depth = (ptl[2] + ptr[2] + pbl[2] + pbr[2]) / 4
            uv = fd.get("uv") or default_uv(fn, f, t)   # uv por defecto en coordenadas locales del bloque
            ref = resolve(tex, fd["texture"])
            faces.append((depth, ptl, ptr, pbl, pbr, uv, ref, SHADE[fn] if abs(n[1]) < 0.99 else (1.0 if n[1] > 0 else 0.5)))
    import numpy as np
    zbuf = np.full((size, size), -1e9)
    rgb = np.zeros((size, size, 4), dtype=np.float64)
    for depth, ptl, ptr, pbl, pbr, uv, ref, sh in faces:
        img = texture(ref)
        W, H = img.size
        A = (ptr[0] - ptl[0], ptr[1] - ptl[1])
        B = (pbl[0] - ptl[0], pbl[1] - ptl[1])
        det = A[0] * B[1] - A[1] * B[0]
        if abs(det) < 1e-6:
            continue
        i00, i01 = B[1] / det, -B[0] / det
        i10, i11 = -A[1] / det, A[0] / det
        su, sv = (uv[2] - uv[0]) * W / 16.0, (uv[3] - uv[1]) * H / 16.0
        u0, v0 = uv[0] * W / 16.0, uv[1] * H / 16.0
        a_ = su * i00
        b_ = su * i01
        c_ = u0 - su * (i00 * ptl[0] + i01 * ptl[1])
        d_ = sv * i10
        e_ = sv * i11
        f_ = v0 - sv * (i10 * ptl[0] + i11 * ptl[1])
        layer = img.transform((size, size), Image.AFFINE, (a_, b_, c_, d_, e_, f_), resample=Image.NEAREST)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon([(ptl[0], ptl[1]), (ptr[0], ptr[1]), (pbr[0], pbr[1]), (pbl[0], pbl[1])], fill=255)
        bbox = mask.getbbox()
        if not bbox:
            continue
        x0, y0, x1, y1 = bbox
        arr = np.asarray(layer, dtype=np.float64)[y0:y1, x0:x1]
        m_ = np.asarray(mask)[y0:y1, x0:x1] > 0
        ys, xs = np.mgrid[y0:y1, x0:x1]
        s_ = i00 * (xs + 0.5 - ptl[0]) + i01 * (ys + 0.5 - ptl[1])
        t_ = i10 * (xs + 0.5 - ptl[0]) + i11 * (ys + 0.5 - ptl[1])
        z = ptl[2] + s_ * (ptr[2] - ptl[2]) + t_ * (pbl[2] - ptl[2])
        ok = m_ & (arr[:, :, 3] > 127) & (z > zbuf[y0:y1, x0:x1] + 1e-3)
        zsub = zbuf[y0:y1, x0:x1]
        zsub[ok] = z[ok]
        sub = rgb[y0:y1, x0:x1]
        sub[ok, 0] = arr[:, :, 0][ok] * sh
        sub[ok, 1] = arr[:, :, 1][ok] * sh
        sub[ok, 2] = arr[:, :, 2][ok] * sh
        sub[ok, 3] = 255
    canvas = Image.new("RGBA", (size, size), bg)
    layer = Image.fromarray(np.clip(rgb, 0, 255).astype("uint8"), "RGBA")
    canvas.alpha_composite(layer)
    return canvas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("models", nargs="+")
    ap.add_argument("--yaw", type=float, default=145)
    ap.add_argument("--pitch", type=float, default=28)
    ap.add_argument("--size", type=int, default=360)
    ap.add_argument("--views", type=int, default=1, help="1 = un ángulo, 2 = frente y detrás")
    ap.add_argument("--cols", type=int, default=3)
    ap.add_argument("--coldroom", action="store_true", help="dibuja la cámara frigorífica completa (27 piezas)")
    ap.add_argument("--open", action="store_true", help="con --coldroom: puerta abierta")
    ap.add_argument("--skip", default="", help="con --coldroom: piezas a ocultar, p.ej. py=2,pz=0 (cada condición se une con Y)")
    a = ap.parse_args()
    if a.coldroom:
        conds = [c.split("=") for c in a.skip.split(";") if c]
        def hidden(px, py, pz):
            for cs in conds:
                ok = True
                for item in cs[0].split(",") if len(cs) == 1 else []:
                    pass
                return False
            return False
        skips = []
        for group in [g for g in a.skip.split(";") if g]:
            skips.append({kv.split("=")[0]: int(kv.split("=")[1]) for kv in group.split(",")})
        scene = []
        for part in range(27):
            px, rem = part % 3, part // 3
            py, pz = rem % 3, rem // 3
            vals = {"px": px, "py": py, "pz": pz}
            if any(all(vals[k] == v for k, v in sk.items()) for sk in skips):
                continue
            name = f"cold_room_p{part}" + ("_open" if a.open and part in (1, 4) else "")
            scene.append((name, (-16 * px, 16 * py, 16 * pz)))
        bg = (46, 50, 58, 255)
        tiles = []
        yaws = [a.yaw] if a.views == 1 else [a.yaw, a.yaw + 180]
        for y in yaws:
            tiles.append(render("cold_room", y, a.pitch, a.size, bg, scene=scene, center=(-8, 20, 24), span=68.0))
        sheet = Image.new("RGBA", (len(tiles) * a.size, a.size), bg)
        for i, t in enumerate(tiles):
            sheet.paste(t, (i * a.size, 0))
        sheet.convert("RGB").save(a.out)
        print("guardado", a.out)
        return
    bg = (46, 50, 58, 255)
    tiles = []
    for name in a.models:
        yaws = [a.yaw] if a.views == 1 else [a.yaw, a.yaw + 180]
        for y in yaws:
            tiles.append((name, render(name, y, a.pitch, a.size, bg)))
    cols = min(a.cols, len(tiles))
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * a.size, rows * a.size), bg)
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(tiles):
        x, y = (i % cols) * a.size, (i // cols) * a.size
        sheet.paste(im, (x, y))
        d.text((x + 6, y + 4), name, fill=(255, 255, 255, 255))
    sheet.convert("RGB").save(a.out)
    print("guardado", a.out, sheet.size)


if __name__ == "__main__":
    main()
