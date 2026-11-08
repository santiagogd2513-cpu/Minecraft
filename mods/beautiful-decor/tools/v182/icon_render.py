"""Dibuja un modelo de bloque como ícono de ítem (32x32) sin abrir Minecraft.

- Resuelve la cadena de modelos (incluye los padres de vanilla que usa el mod: cube_all, cube_column, stairs).
- Ajusta el dibujo al slot: el ícono nunca se sale del cuadro, sea una puerta de 2 bloques o un salero.
- Dibuja con z-buffer a alta resolución y reduce (suaviza los colores, silueta limpia), vidrio translúcido incluido.
- Añade un contorno oscuro de 1 px, como los ítems de vanilla.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .vanilla_tex import vanilla_texture

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
NS = "beautifuldecor"

ICON = 32            # tamaño final del ícono
INNER = 30           # lado máximo de la silueta (deja 1 px de contorno por cada lado)
SS = 6               # supersampling
CAP = 2.0            # un objeto pequeño no se amplía más de 2x respecto a un bloque completo

SHADE = {"up": 1.0, "down": 0.55, "north": 0.82, "south": 0.82, "east": 0.66, "west": 0.66}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
           "up": (0, 1, 0), "down": (0, -1, 0)}

# padres de vanilla que usan algunos modelos del mod
def _face(tex, uv=None):
    d = {"texture": tex}
    if uv:
        d["uv"] = uv
    return d


VANILLA = {
    "block/block": {"elements": []},
    "block/cube_all": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                                     "faces": {f: _face("#all") for f in NORMALS}}]},
    "block/cube_column": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": _face("#end"), "up": _face("#end"), "north": _face("#side"), "south": _face("#side"),
        "west": _face("#side"), "east": _face("#side")}}]},
    "block/stairs": {"elements": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {
            "down": _face("#bottom"), "up": _face("#top"), "north": _face("#side"), "south": _face("#side"),
            "west": _face("#side"), "east": _face("#side")}},
        {"from": [8, 8, 0], "to": [16, 16, 16], "faces": {
            "up": _face("#top"), "north": _face("#side", [0, 0, 8, 8]), "south": _face("#side", [8, 0, 16, 8]),
            "west": _face("#side", [0, 0, 16, 8]), "east": _face("#side")}}]},
}


def _split(name):
    ns, _, path = name.partition(":")
    if not path:
        ns, path = "minecraft", ns
    return ns, path


def merge_model(data, _depth=0):
    """Mezcla un modelo (dict JSON) con su cadena de padres: {'textures': {...}, 'elements': [...]}."""
    parent = data.get("parent")
    base = load_model(parent, _depth + 1) if parent and _depth < 12 else {"textures": {}, "elements": []}
    merged = {"textures": dict(base["textures"]), "elements": base["elements"]}
    merged["textures"].update(data.get("textures", {}))
    if "elements" in data:
        merged["elements"] = data["elements"]
    return merged


def load_model(name, _depth=0):
    """Devuelve {'textures': {...}, 'elements': [...]} con la cadena de padres ya mezclada."""
    ns, path = _split(name)
    if ns == NS:
        p = ASSETS / "models" / f"{path}.json"
        if not p.exists():
            raise FileNotFoundError(f"modelo {name} no existe")
        data = json.loads(p.read_text(encoding="utf-8"))
    else:
        data = VANILLA.get(path, {"elements": []})
    return merge_model(data, _depth)


_tex_cache = {}


def texture(ref):
    if ref in _tex_cache:
        return _tex_cache[ref]
    ns, path = _split(ref)
    if ns != NS:
        im = vanilla_texture(ref)
    else:
        p = ASSETS / "textures" / f"{path}.png"
        if not p.exists():
            im = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
        else:
            im = Image.open(p).convert("RGBA")
            if (p.parent / (p.name + ".mcmeta")).exists() and im.height > im.width:
                im = im.crop((0, 0, im.width, im.width))     # textura animada: primer cuadro
    _tex_cache[ref] = im
    return im


def resolve(tex, key):
    for _ in range(12):
        if not key.startswith("#"):
            return key
        key = tex.get(key[1:], "")
    return key


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


def rot_point(p, rot, vector=False):
    if not rot:
        return p
    o = (0, 0, 0) if vector else rot["origin"]
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


def collect_quads(model):
    """Lista de caras en 3D: esquinas (tl, tr, bl, br), normal, uv, textura, sombreado."""
    quads = []
    for el in model["elements"]:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        for fn, fd in el.get("faces", {}).items():
            origin, du, dv = face_frame(fn, f, t)
            tl = rot_point(origin, rot)
            tr = rot_point(tuple(o + d for o, d in zip(origin, du)), rot)
            bl = rot_point(tuple(o + d for o, d in zip(origin, dv)), rot)
            br = rot_point(tuple(o + a + b for o, a, b in zip(origin, du, dv)), rot)
            n = rot_point(NORMALS[fn], rot, vector=True)
            ref = resolve(model["textures"], fd.get("texture", ""))
            if not ref:
                continue
            uv = list(fd.get("uv") or default_uv(fn, f, t))
            if max(abs(v) for v in uv) > 16.001:
                # UV escritas en píxeles de la textura (p. ej. [0,0,38,62] en 64x64) en vez de 0-16: se normalizan
                tw, th = texture(ref).size
                uv = [uv[0] * 16.0 / tw, uv[1] * 16.0 / th, uv[2] * 16.0 / tw, uv[3] * 16.0 / th]
            shade = 1.0
            if el.get("shade") is not False:
                shade = SHADE[fn] if abs(n[1]) < 0.99 else (1.0 if n[1] > 0 else SHADE["down"])
            quads.append({"c": (tl, tr, bl, br), "n": n, "uv": uv, "ref": ref, "shade": shade})
    return quads


def make_projection(yaw, pitch):
    a, b = math.radians(yaw), math.radians(pitch)
    ca, sa, cb, sb = math.cos(a), math.sin(a), math.cos(b), math.sin(b)

    def project(p):
        x, y, z = p
        x2, z2 = x * ca + z * sa, -x * sa + z * ca
        return (x2, y * cb - z2 * sb, y * sb + z2 * cb)        # (x pantalla, y arriba, profundidad hacia la cámara)

    return project


def view_extent(quads, project, yaw, pitch):
    pts = []
    for q in quads:
        if project(q["n"])[2] <= 1e-6:
            continue
        pts += [project(p) for p in q["c"]]
    if not pts:
        return None
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), max(xs), min(ys), max(ys)


def block_extent(yaw, pitch):
    """Tamaño en pantalla (unidades de modelo) de un bloque completo de 16x16x16 con esta vista."""
    project = make_projection(yaw, pitch)
    pts = [project((x, y, z)) for x in (0, 16) for y in (0, 16) for z in (0, 16)]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return max(xs) - min(xs), max(ys) - min(ys)


def model_bounds(quads):
    pts = [p for q in quads for p in q["c"]]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    return lo, hi


def choose_view(quads):
    """Vista según la forma: paneles delgados y piezas altas se ven más de frente."""
    lo, hi = model_bounds(quads)
    w, h, d = hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]
    if d <= 4.5 and w >= 6 and h >= 6 and (w > 2.2 * d and h > 2.2 * d):
        return 158.0, 16.0          # panel vertical (reloj, cuadro, interruptor, puerta): casi de frente
    if h >= 1.5 * max(w, d):
        return 150.0, 20.0          # pieza alta y angosta
    if h <= 0.3 * max(w, d):
        return 145.0, 40.0          # pieza plana (alfombra, plato, consola): se ve más desde arriba
    return 145.0, 30.0


def rasterize(quads, yaw, pitch):
    """Devuelve una imagen RGBA (ICON*SS)^2 con el dibujo ajustado al slot."""
    project = make_projection(yaw, pitch)
    ext = view_extent(quads, project, yaw, pitch)
    if ext is None:
        return None
    x0, x1, y0, y1 = ext
    bw, bh = block_extent(yaw, pitch)
    w, h = max(x1 - x0, 1e-6), max(y1 - y0, 1e-6)
    fit = min(INNER / w, INNER / h)
    cap = CAP * INNER / max(bw, bh)
    scale = min(fit, cap) * SS                        # píxeles del lienzo grande por unidad de modelo
    size = ICON * SS
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

    def to_px(p):
        sx, sy, sz = project(p)
        return ((sx - cx) * scale + size / 2, size / 2 - (sy - cy) * scale, sz)

    zbuf = np.full((size, size), -1e9)
    rgb = np.zeros((size, size, 3))
    opaque = np.zeros((size, size), dtype=bool)
    translucent = []

    def face_pixels(q):
        ptl, ptr, pbl, pbr = (to_px(p) for p in q["c"])
        img = texture(q["ref"])
        W, H = img.size
        A = (ptr[0] - ptl[0], ptr[1] - ptl[1])
        B = (pbl[0] - ptl[0], pbl[1] - ptl[1])
        det = A[0] * B[1] - A[1] * B[0]
        if abs(det) < 1e-6:
            return None
        i00, i01 = B[1] / det, -B[0] / det
        i10, i11 = -A[1] / det, A[0] / det
        uv = q["uv"]
        su, sv = (uv[2] - uv[0]) * W / 16.0, (uv[3] - uv[1]) * H / 16.0
        u0, v0 = uv[0] * W / 16.0, uv[1] * H / 16.0
        coeffs = (su * i00, su * i01, u0 - su * (i00 * ptl[0] + i01 * ptl[1]),
                  sv * i10, sv * i11, v0 - sv * (i10 * ptl[0] + i11 * ptl[1]))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon([(ptl[0], ptl[1]), (ptr[0], ptr[1]), (pbr[0], pbr[1]), (pbl[0], pbl[1])], fill=255)
        bbox = mask.getbbox()
        if not bbox:
            return None
        bx0, by0, bx1, by1 = bbox
        layer = img.transform((size, size), Image.AFFINE, coeffs, resample=Image.NEAREST)
        arr = np.asarray(layer, dtype=np.float64)[by0:by1, bx0:bx1]
        m = np.asarray(mask)[by0:by1, bx0:bx1] > 0
        ys, xs = np.mgrid[by0:by1, bx0:bx1]
        s_ = i00 * (xs + 0.5 - ptl[0]) + i01 * (ys + 0.5 - ptl[1])
        t_ = i10 * (xs + 0.5 - ptl[0]) + i11 * (ys + 0.5 - ptl[1])
        z = ptl[2] + s_ * (ptr[2] - ptl[2]) + t_ * (pbl[2] - ptl[2])
        return bx0, by0, bx1, by1, arr, m, z

    visible = [q for q in quads if project(q["n"])[2] > 1e-6]
    for q in visible:
        fp = face_pixels(q)
        if fp is None:
            continue
        bx0, by0, bx1, by1, arr, m, z = fp
        alpha = arr[:, :, 3]
        full = m & (alpha >= 250) & (z > zbuf[by0:by1, bx0:bx1] + 1e-3)
        zsub = zbuf[by0:by1, bx0:bx1]
        zsub[full] = z[full]
        rsub = rgb[by0:by1, bx0:bx1]
        rsub[full] = arr[:, :, :3][full] * q["shade"]
        opaque[by0:by1, bx0:bx1][full] = True
        part = m & (alpha > 8) & (alpha < 250)
        if part.any():
            translucent.append((float(np.mean(z[part])), bx0, by0, bx1, by1, arr, part, z, q["shade"]))

    # pasada de vidrio: de atrás hacia adelante, solo donde no hay nada opaco delante
    alpha_acc = opaque.astype(np.float64)
    pre = rgb * alpha_acc[:, :, None]
    for _, bx0, by0, bx1, by1, arr, part, z, sh in sorted(translucent, key=lambda t: t[0]):
        vis = part & (z > zbuf[by0:by1, bx0:bx1] - 1e-3)
        if not vis.any():
            continue
        a = (arr[:, :, 3] / 255.0)[vis]
        src = arr[:, :, :3][vis] * sh
        psub = pre[by0:by1, bx0:bx1]
        asub = alpha_acc[by0:by1, bx0:bx1]
        psub[vis] = src * a[:, None] + psub[vis] * (1 - a[:, None])
        asub[vis] = a + asub[vis] * (1 - a)
    return pre, alpha_acc


def finish(pre, alpha_acc):
    """Reduce el lienzo, limpia la silueta y añade el contorno."""
    size = ICON * SS
    pre = pre.reshape(ICON, SS, ICON, SS, 3).mean(axis=(1, 3))
    alpha = alpha_acc.reshape(ICON, SS, ICON, SS).mean(axis=(1, 3))
    color = np.where(alpha[:, :, None] > 1e-6, pre / np.maximum(alpha[:, :, None], 1e-6), 0)
    out = np.zeros((ICON, ICON, 4))
    solid = alpha >= 0.5
    ghost = (alpha >= 0.12) & ~solid
    out[solid, :3] = color[solid]
    out[solid, 3] = 255
    out[ghost, :3] = color[ghost]
    out[ghost, 3] = 170
    sil = solid | ghost
    # contorno de 1 px alrededor de la silueta
    pad = np.pad(sil, 1)
    near = np.zeros_like(sil)
    cols = np.zeros((ICON, ICON, 3))
    cnt = np.zeros((ICON, ICON))
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        nb = pad[1 + dy:1 + dy + ICON, 1 + dx:1 + dx + ICON]
        near |= nb
        shifted = np.roll(np.roll(color, -dy, axis=0), -dx, axis=1)
        cols += np.where(nb[:, :, None], shifted, 0)
        cnt += nb
    edge = near & ~sil
    avg = cols / np.maximum(cnt[:, :, None], 1)
    out[edge, :3] = np.clip(avg[edge] * 0.32, 0, 255)
    out[edge, 3] = 255
    return Image.fromarray(np.clip(out, 0, 255).astype("uint8"), "RGBA")


def render_icon(model, view=None):
    """model: nombre de modelo ('beautifuldecor:block/x') o un dict JSON de modelo (p. ej. el modelo de ítem original)."""
    model = merge_model(model) if isinstance(model, dict) else load_model(model)
    quads = collect_quads(model)
    if not quads:
        return None
    yaw, pitch = view or choose_view(quads)
    res = rasterize(quads, yaw, pitch)
    if res is None:
        return None
    return finish(*res)
