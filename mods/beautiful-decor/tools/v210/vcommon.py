"""Herramientas comunes de los vehículos de la 2.1.0 (bicicleta y camioneta): atlas, pintura, vistas, ícono y Java.

Reutiliza el renderizador de software (tools/v200/softrender.py) y el constructor de piezas de la moto. Coordenadas
"amigables": x = izquierda del vehículo (+), y = altura sobre el suelo, z = hacia delante (+); todo en px (16 px = 1 bloque).
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v200.moto_model import Builder, deg                        # noqa: F401  (se reexporta)
from ..v200.moto_paint import clampc, edges, hline, noise, rect, solid, vline, vshade   # noqa: F401
from ..v200.softrender import Camera, Cube, Part, render         # noqa: F401

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
JAVA = ROOT / "src/main/java/com/example/beautifuldecor"


# ----------------------------------------------------------------------------------------------- geometría
def new_builder():
    """Builder con la raíz virtual 'root' (pivote en y = 24, el suelo). Las piezas de primer nivel cuelgan de ella."""
    return Builder()


def tube(b, parent, name, p0, p1, thick, mat, extra=0.0, **opts):
    """Tubo de sección thick x thick entre p0 y p1 = (y, z) en el sistema del padre (x = 0). Devuelve (pieza, cubo)."""
    (y0, z0), (y1, z1) = p0, p1
    dy, dz = y1 - y0, z1 - z0
    length = math.hypot(dy, dz) + extra
    theta = math.atan2(dy, dz)
    x = opts.pop("x", 0.0)
    part = b.part(name, parent, (x, y0, z0), (-theta, 0.0, 0.0))
    n = max(1, int(round(length)))
    cube = b.cube(part, name + "_c", (0, 0, n / 2.0), (thick, thick, n), mat, **opts)
    return part, cube


# ----------------------------------------------------------------------------------------------- atlas
def pack(cubes, tw, th):
    """Coloca la huella (2*(w+d) x h+d) de cada cubo en el atlas, por estantes. Devuelve la altura usada."""
    items = sorted(cubes, key=lambda c: (-(c.h + c.d), -2 * (c.w + c.d)))
    x = y = shelf = 0
    for c in items:
        fw, fh = int(2 * (c.w + c.d)), int(c.h + c.d)
        if x + fw > tw:
            x, y, shelf = 0, y + shelf, 0
        if y + fh > th:
            raise RuntimeError(f"El atlas {tw}x{th} se llenó (cubo {c.name}); hay que compactar")
        c.u, c.v = x, y
        x += fw
        shelf = max(shelf, fh)
    return y + shelf


def face_rect(c, f):
    """(x0, y0, columnas, filas) de la cara en el atlas."""
    u, v, w, h, d = c.u, c.v, int(c.w), int(c.h), int(c.d)
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }[f]


def paint_atlas(cubes, tw, th, paint_cube, seed=2100, raw=False):
    atlas = np.zeros((th, tw, 4), dtype=np.float32)
    glow = np.zeros((th, tw, 4), dtype=np.float32)
    for i, c in enumerate(cubes):
        rng = np.random.default_rng(seed + i)
        faces, gl = paint_cube(c, rng)
        for f, arr in faces.items():
            x0, y0, cols, rows = face_rect(c, f)
            if arr.shape[0] != rows or arr.shape[1] != cols:
                raise RuntimeError(f"{c.name}.{f}: pintura {arr.shape[:2]} no coincide con {rows}x{cols}")
            atlas[y0:y0 + rows, x0:x0 + cols] = arr
        for f, arr in gl.items():
            x0, y0, cols, rows = face_rect(c, f)
            if arr.shape[0] != rows or arr.shape[1] != cols:
                raise RuntimeError(f"{c.name}.{f}: brillo {arr.shape[:2]} no coincide con {rows}x{cols}")
            glow[y0:y0 + rows, x0:x0 + cols] = arr
    if raw:                      # sin recortar ni redondear: para medir cuánto depende cada píxel de un color (V3.2.0)
        return atlas, glow
    return np.clip(atlas, 0, 255).astype(np.uint8), np.clip(glow, 0, 255).astype(np.uint8)


# ----------------------------------------------------------------------------------------------- pintura
def face_sizes(c):
    return {"top": (c.d, c.w), "bottom": (c.d, c.w), "west": (c.h, c.d), "east": (c.h, c.d),
            "front": (c.h, c.w), "back": (c.h, c.w)}


def base_faces(c, color, rng, gloss=False, flat=False):
    """Seis caras del color dado, con un degradado vertical suave y algo de ruido."""
    faces = {}
    for f, (rows, cols) in face_sizes(c).items():
        a = solid(rows, cols, color)
        if f in ("west", "east", "front", "back") and not flat:
            vshade(a, 1.12 if gloss else 1.04, 0.84 if gloss else 0.93)
        noise(a, rng, 0.025 if gloss else 0.045)
        faces[f] = a
    return faces


def natural_side(faces, face):
    """Cara lateral 'como se ve' con el frente del vehículo a la DERECHA de la imagen."""
    img = faces[face]
    return img[:, ::-1].copy() if face == "east" else img


def store_side(faces, face, img):
    faces[face] = img[:, ::-1].copy() if face == "east" else img


def natural_top(faces):
    """Cara de arriba a la vista natural (frente hacia arriba)."""
    return faces["top"][::-1, ::-1].copy()


def store_top(faces, img):
    faces["top"] = img[::-1, ::-1].copy()


def outline_rect(a, color, alpha=1.0):
    hline(a, 0, color, alpha=alpha)
    hline(a, a.shape[0] - 1, color, alpha=alpha)
    vline(a, 0, color, alpha=alpha)
    vline(a, a.shape[1] - 1, color, alpha=alpha)


FONT = {
    "A": "010 101 111 101 101", "D": "110 101 101 101 110", "F": "111 100 110 100 100", "O": "010 101 101 101 010",
    "R": "110 101 110 101 101", "1": "010 110 010 010 111", "5": "111 100 110 001 110", "0": "111 101 101 101 111",
    "U": "101 101 101 101 111", "E": "111 100 110 100 111",
    "-": "000 000 111 000 000", " ": "000 000 000 000 000",
    # V6.8.0: letras para los rótulos de las skins (las que ya existían no cambian)
    "B": "110 101 110 101 110", "M": "101 111 111 101 101", "S": "111 100 111 001 111", "P": "111 101 111 100 100",
    "N": "111 101 101 101 101", "Z": "111 001 010 100 111", "I": "111 010 010 010 111", "C": "111 100 100 100 111",
    "L": "100 100 100 100 111", "T": "111 010 010 010 010", "H": "101 101 111 101 101", "G": "111 100 101 101 111",
    "7": "111 001 010 010 010", "X": "101 101 010 101 101", "K": "101 101 110 101 101", "V": "101 101 101 101 010", "Y": "101 101 010 010 010", "J": "001 001 001 101 111", "3": "111 001 111 001 111", "2": "111 001 111 100 111", "4": "101 101 111 001 001",
}


def text(a, s, x, y, color, scale=1, gap=1, mirror=False):
    """Texto con la fuente 3x5 sobre un arreglo RGBA (x, y = esquina superior izquierda)."""
    cx = x
    for ch in s.upper():
        rows = FONT.get(ch, FONT[" "]).split()
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "1":
                    for sy in range(scale):
                        for sx in range(scale):
                            xx, yy = cx + rx * scale + sx, y + ry * scale + sy
                            if 0 <= yy < a.shape[0] and 0 <= xx < a.shape[1]:
                                a[yy, xx, :3] = color
                                a[yy, xx, 3] = 255
        cx += (3 + gap) * scale


def text_width(s, scale=1, gap=1):
    return len(s) * (3 + gap) * scale - gap * scale


def wheel_faces(c, rng, art, tread=((24, 24, 27), (40, 40, 45)), side_tint=None):
    """Ruedas hechas de cajas giradas: el arte se proyecta sobre el plano de la rueda para que las 4 cajas coincidan.

    art(Y, Z, kind) -> (r, g, b, a) o None, con Y, Z en px desde el eje.
    """
    kind = c.opts["kind"]
    phi = c.opts.get("phi", 0.0)
    cp, sp = math.cos(phi), math.sin(phi)
    out = {}
    for face in ("west", "east"):
        rows, cols = c.h, c.d
        a = np.zeros((rows, cols, 4), dtype=np.float32)
        for r in range(rows):
            for q in range(cols):
                z = c.d / 2.0 - (q + 0.5) if face == "west" else -c.d / 2.0 + (q + 0.5)
                y = -c.h / 2.0 + r + 0.5
                Y = y * cp - z * sp
                Z = y * sp + z * cp
                col = art(Y, Z, kind)
                if col is not None:
                    a[r, q] = col
        out[face] = a
    base, stripe = tread
    if kind == "tire":
        for face in ("top", "bottom"):
            a = solid(c.d, c.w, base)
            for q in range(0, c.d, 2):
                a[q, :, :3] = stripe
            out[face] = noise(a, rng, 0.05)
        for face in ("front", "back"):
            out[face] = noise(solid(c.h, c.w, base), rng, 0.05)
    else:
        col = c.opts.get("edge_color", (150, 154, 164))
        for face in ("top", "bottom"):
            out[face] = solid(c.d, c.w, col)
        for face in ("front", "back"):
            out[face] = solid(c.h, c.w, col)
    return out, {}


def add_wheel(b, parent, name, pivot, radius, tire_w, hub_w=0, tire_depth=None):
    """Rueda: neumático octagonal de 4 cajas giradas 0/45/90/135 (el rin y los rayos van pintados en sus caras)
    y, si hub_w > 0, una caja fina de eje. Devuelve la pieza de la rueda."""
    w = b.part(name, parent, pivot)
    height = int(round(2 * radius))
    depth = tire_depth or max(2, int(round(radius * 0.72)))
    for i in range(4):
        p = b.part(f"{name}_t{i}", w, (0, 0, 0), (deg(45.0 * i), 0, 0))
        b.cube(p, f"{name}_tire{i}", (0, 0, 0), (tire_w, height, depth), "tire", wheel=True, kind="tire",
               phi=deg(45.0 * i), radius=radius)
    if hub_w:
        b.cube(w, f"{name}_hub", (0, 0, 0), (hub_w, 2, 2), "hub", kind="hub")
    return w


# ----------------------------------------------------------------------------------------------- vistas e ícono
def views(b, tex, glow, tex_size, specs, center, scale, light_on=True, size=(360, 300), translucent=("glass",)):
    out = []
    for az, el in specs:
        cam = Camera(az, el, scale, center, size)
        out.append(render(b.root, tex, cam, tex_size, glow=glow if light_on else None,
                          background=(78, 86, 98, 255), translucent_mats=translucent))
    return out


def sheet(images, cols=3):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    s = Image.new("RGBA", (cols * w, rows * h), (50, 56, 66, 255))
    for i, im in enumerate(images):
        s.paste(im, ((i % cols) * w, (i // cols) * h))
    return s


def make_icon(b, tex, glow, tex_size, az, el, center, scale, outline=(20, 14, 16, 255), translucent=("glass",),
              brighten=1.0):
    cam = Camera(az, el, scale, center, (640, 640))
    big = render(b.root, tex, cam, tex_size, glow=glow, background=(0, 0, 0, 0), supersample=2,
                 translucent_mats=translucent)
    bbox = big.getbbox()
    big = big.crop(bbox)
    k = 30.0 / max(big.size)
    small = big.resize((max(1, round(big.width * k)), max(1, round(big.height * k))), Image.LANCZOS)
    arr = np.asarray(small).astype(np.float32)
    alpha = arr[..., 3] > 90
    arr[~alpha] = 0
    arr[alpha, 3] = 255
    arr[alpha, :3] = np.clip(arr[alpha, :3] * brighten, 0, 255)
    small = Image.fromarray(arr.astype(np.uint8), "RGBA")
    icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    ox, oy = (32 - small.width) // 2, (32 - small.height) // 2
    icon.paste(small, (ox, oy), small)
    a = np.asarray(icon).astype(np.float32)
    sil = a[..., 3] > 0
    pad = np.pad(sil, 1)
    near = np.zeros_like(sil)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        near |= pad[1 + dy:33 + dy, 1 + dx:33 + dx]
    edge = near & ~sil
    a[edge] = outline
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# ----------------------------------------------------------------------------------------------- Java
def f(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return (s if s not in ("", "-0") else "0") + "f"


def _cubes_code(cubes, pad):
    code = "CubeListBuilder.create()"
    for c in cubes:
        code += (f"\n{pad}        .texOffs({c.u}, {c.v}).addBox({f(c.x)}, {f(c.y)}, {f(c.z)}, "
                 f"{f(c.w)}, {f(c.h)}, {f(c.d)})")
    return code


def java_part(part, parent_var, names, indent, top_offset=None):
    """Código Java que crea 'part' (y sus hijos) colgando de parent_var."""
    var = names[part.name]
    pad = "        " + "    " * indent
    lines = []
    px, py, pz = (float(v) for v in part.pivot)
    if top_offset is not None:
        px, py, pz = px + top_offset[0], py + top_offset[1], pz + top_offset[2]
    rx, ry, rz = part.rot
    if rx or ry or rz:
        pose = f"PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)})"
    else:
        pose = f"PartPose.offset({f(px)}, {f(py)}, {f(pz)})"
    lines.append(f'{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("{part.name}",\n'
                 f'{pad}        {_cubes_code(part.cubes, pad)},\n{pad}        {pose});')
    for ch in part.children:
        lines.extend(java_part(ch, var, names, indent))
    return lines


def java_tree(b):
    """Cuerpo de createBodyLayer: cada hijo de la raíz de Python es una pieza de primer nivel (el suelo está en y = 24)."""
    names = {name: "p_" + name for name in b.parts}
    lines = []
    for ch in b.root.children:
        lines.extend(java_part(ch, "root", names, 0, top_offset=(0.0, 24.0, 0.0)))
    return "\n".join(lines)
