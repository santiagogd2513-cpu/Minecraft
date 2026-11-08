"""Pinta la textura 256x256 de la moto (y la textura de brillo) a partir de la definición de moto_model.py.

Cada cubo tiene 6 caras en el atlas (disposición de ModelPart.Cube). Las caras laterales/frontales/traseras se
guardan "como las ve un observador"; la cara de arriba se guarda girada 180 grados respecto a la vista natural
(frente hacia arriba), tal como la usa Minecraft. Las ruedas se pintan con un arte común proyectado sobre el plano
de la rueda, así las 4 cajas que forman la llanta muestran exactamente lo mismo y no parpadean.
"""
import math
import random

import numpy as np

from .moto_model import TEX_H, TEX_W
from ..v320.paintkit import tone

# ----------------------------------------------------------------------------------------------- paleta
# Colores de referencia de los canales que el jugador puede cambiar (V3.2.0): pintura roja y rines rojos (sin manijas).
RED_REF, RED_HI_REF, RED_LO_REF = (200, 16, 24), (244, 58, 48), (120, 8, 16)
RIM_REF = (206, 18, 26)
RIM_SPOKE_REF, RIM_EDGE_REF, RIM_HUBMAT_REF = (214, 22, 28), (190, 16, 22), (200, 16, 24)
RIM_DARK_REF, RIM_DARK2_REF = (60, 8, 10), (70, 8, 12)
RED = RED_REF
RED_HI = RED_HI_REF
RED_LO = RED_LO_REF
RIM = RIM_REF
RIM_SPOKE = RIM_SPOKE_REF
RIM_EDGE = RIM_EDGE_REF
RIM_HUBMAT = RIM_HUBMAT_REF
RIM_DARK = RIM_DARK_REF
RIM_DARK2 = RIM_DARK2_REF
BLACK = (24, 24, 28)
BLACK_HI = (70, 72, 82)
GRAPHITE = (46, 48, 54)
CHROME = (178, 184, 196)
WHITE = (238, 240, 244)

MAT_COLOR = {
    "red": RED, "red_cover": RED, "black": BLACK, "black_gloss": (20, 20, 25), "engine": (52, 54, 60),
    "engine_dark": (34, 35, 40), "radiator": (30, 32, 36), "chrome": CHROME, "seat": (28, 28, 32),
    "frame": (30, 31, 36), "spring": (28, 28, 32), "exhaust": (40, 42, 48), "disc": (150, 154, 164),
    "hub": RED, "tire": (22, 22, 25), "rubber": (18, 18, 20), "plate": WHITE, "glass": (26, 34, 46),
    "lens": (150, 160, 172), "taillight": (150, 12, 16), "dash": (14, 15, 18),
}


REFS = {"body": RED_REF, "rim": RIM_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global RED, RED_HI, RED_LO, RIM, RIM_SPOKE, RIM_EDGE, RIM_HUBMAT, RIM_DARK, RIM_DARK2
    RED = tone(RED_REF, RED_REF, body)
    RED_HI = tone(RED_HI_REF, RED_REF, body)
    RED_LO = tone(RED_LO_REF, RED_REF, body)
    RIM = tone(RIM_REF, RIM_REF, rim)
    RIM_SPOKE = tone(RIM_SPOKE_REF, RIM_REF, rim)
    RIM_EDGE = tone(RIM_EDGE_REF, RIM_REF, rim)
    RIM_HUBMAT = tone(RIM_HUBMAT_REF, RIM_REF, rim)
    RIM_DARK = tone(RIM_DARK_REF, RIM_REF, rim)
    RIM_DARK2 = tone(RIM_DARK2_REF, RIM_REF, rim)
    MAT_COLOR["red"] = RED
    MAT_COLOR["red_cover"] = RED
    MAT_COLOR["hub"] = RIM_HUBMAT


def clampc(a):
    return np.clip(a, 0, 255)


def solid(rows, cols, color, alpha=255):
    a = np.zeros((rows, cols, 4), dtype=np.float32)
    a[..., 0], a[..., 1], a[..., 2], a[..., 3] = color[0], color[1], color[2], alpha
    return a


def vshade(a, top, bottom):
    rows = a.shape[0]
    g = np.linspace(top, bottom, rows, dtype=np.float32)[:, None]
    a[..., :3] *= g[..., None]
    return a


def noise(a, rng, amp):
    a[..., :3] *= (1.0 + rng.uniform(-amp, amp, a.shape[:2] + (1,))).astype(np.float32)
    return a


def hline(a, row, color, x0=0, x1=None, alpha=1.0):
    if 0 <= row < a.shape[0]:
        x1 = a.shape[1] if x1 is None else x1
        a[row, x0:x1, :3] = a[row, x0:x1, :3] * (1 - alpha) + np.array(color, np.float32) * alpha


def vline(a, col, color, y0=0, y1=None, alpha=1.0):
    if 0 <= col < a.shape[1]:
        y1 = a.shape[0] if y1 is None else y1
        a[y0:y1, col, :3] = a[y0:y1, col, :3] * (1 - alpha) + np.array(color, np.float32) * alpha


def rect(a, x0, y0, x1, y1, color, alpha=255):
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(a.shape[1], x1), min(a.shape[0], y1)
    if x1 > x0 and y1 > y0:
        a[y0:y1, x0:x1, :3] = color
        a[y0:y1, x0:x1, 3] = alpha


def edges(a, hi=0.18, lo=0.28):
    """Brillo en el borde de arriba/izquierda y sombra abajo/derecha (relieve de 1 px)."""
    if a.shape[0] > 2:
        a[0, :, :3] *= 1 + hi
        a[-1, :, :3] *= 1 - lo
    if a.shape[1] > 2:
        a[:, 0, :3] *= 1 + hi * 0.5
        a[:, -1, :3] *= 1 - lo * 0.5
    return a


# ----------------------------------------------------------------------------------------------- ruedas
def wheel_art(Y, Z, kind):
    """Color RGBA (0-255) en el punto (Y, Z) del plano de la rueda, en px desde el eje. None = transparente."""
    r = math.hypot(Y, Z)
    ang = math.atan2(Z, Y)
    if kind == "tire":
        if r >= 4.7:
            return (34, 34, 38, 255) if r < 5.0 else (22, 22, 25, 255)
        if r >= 4.05:
            return RIM + (255,)                            # filete rojo del rin
        if r >= 3.45:
            return (10, 10, 12, 255)                       # labio negro del rin
        if r < 1.2:
            return RIM + (255,) if r > 0.55 else RIM_DARK + (255,)
        # fondo negro con 5 rayos rojos
        for k in range(5):
            a0 = math.radians(-90 + 72 * k)
            d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
            if d * r < 0.62 and r > 1.0:
                return RIM_SPOKE + (255,)
        return (16, 16, 20, 255)
    if kind == "disc":
        if r > 2.55 or r < 1.35:
            return None
        hole = (int(round((ang + math.pi) / (math.pi / 6))) % 2 == 0) and 1.65 < r < 2.25
        return (74, 76, 84, 255) if hole else (168, 172, 182, 255)
    if kind == "hub":
        return RIM + (255,) if r > 0.6 else RIM_DARK2 + (255,)
    return (255, 0, 255, 255)


def paint_wheel_face(c, face, rows, cols):
    """Arte proyectado de las caras x de una caja de rueda."""
    a = np.zeros((rows, cols, 4), dtype=np.float32)
    phi = c.opts.get("phi", 0.0)
    cp, sp = math.cos(phi), math.sin(phi)
    for r in range(rows):
        for q in range(cols):
            if face == "west":
                z = c.d / 2.0 - (q + 0.5)
            else:
                z = -c.d / 2.0 + (q + 0.5)
            y = -c.h / 2.0 + r + 0.5
            Y = y * cp - z * sp
            Z = y * sp + z * cp
            col = wheel_art(Y, Z, c.opts["kind"])
            if col is not None:
                a[r, q] = col
    return a


def paint_wheel_cube(c, rng):
    out = {}
    kind = c.opts["kind"]
    for face in ("west", "east"):
        rows, cols = c.h, c.d
        out[face] = paint_wheel_face(c, face, rows, cols)
    if kind == "tire":
        for face in ("top", "bottom"):                     # banda de rodadura
            a = solid(c.d, c.w, (24, 24, 27))
            for q in range(0, c.d, 2):
                a[q, :, :3] = (40, 40, 45)
            out[face] = noise(a, rng, 0.05)
        for face in ("front", "back"):
            out[face] = noise(solid(c.h, c.w, (20, 20, 23)), rng, 0.05)
    elif kind == "disc":
        for face in ("top", "bottom"):
            out[face] = solid(c.d, c.w, (120, 124, 134))
        for face in ("front", "back"):
            out[face] = solid(c.h, c.w, (120, 124, 134))
    else:
        for face in ("top", "bottom"):
            out[face] = solid(c.d, c.w, RIM_EDGE)
        for face in ("front", "back"):
            out[face] = solid(c.h, c.w, RIM_EDGE)
    return out, {}


# ----------------------------------------------------------------------------------------------- piezas
def digit_pixels():
    return {"1": ["010", "110", "010", "010", "111"], "9": ["111", "101", "111", "001", "111"],
            "0": ["111", "101", "101", "101", "111"]}


def base_faces(c, rng):
    """Color de partida de las 6 caras (brillo suave y ruido)."""
    col = MAT_COLOR.get(c.mat, (255, 0, 255))
    gloss = c.mat in ("red", "red_cover", "black_gloss", "chrome", "disc")
    faces = {}
    sizes = {"top": (c.d, c.w), "bottom": (c.d, c.w), "west": (c.h, c.d), "east": (c.h, c.d),
             "front": (c.h, c.w), "back": (c.h, c.w)}
    for f, (rows, cols) in sizes.items():
        a = solid(rows, cols, col)
        if f in ("west", "east", "front", "back"):
            vshade(a, 1.12 if gloss else 1.04, 0.86 if gloss else 0.94)
        noise(a, rng, 0.03 if gloss else 0.05)
        faces[f] = a
    return faces


def stripe_top(a, center, width, color):
    """Franja a lo largo de la moto en la cara de arriba (vista natural: el frente va hacia arriba)."""
    x0 = center - width // 2
    rect(a, x0, 0, x0 + width, a.shape[0], color)


def paint_cube(c, rng):
    """Devuelve (caras, brillo) de un cubo: dict nombre -> arreglo RGBA (filas, columnas, 4)."""
    if c.opts.get("wheel"):
        return paint_wheel_cube(c, rng)
    faces = base_faces(c, rng)
    glow = {}
    kind = c.opts.get("kind", "plain")
    side = c.opts.get("side", 1)
    outer = "east" if side > 0 else "west"                 # cara exterior de las piezas de cada lado
    top = faces["top"][::-1, ::-1].copy()                  # a la vista natural (frente arriba)

    def natural(face):                                     # imagen "como se ve" con el frente a la DERECHA
        img = faces[face]
        return img[:, ::-1].copy() if face == "east" else img

    def store(face, img):
        faces[face] = img[:, ::-1].copy() if face == "east" else img

    if kind == "tank":
        stripe_top(top, top.shape[1] // 2, 2, (18, 18, 22))
        vline(top, top.shape[1] // 2 - 2, RED_LO, alpha=0.7)
        vline(top, top.shape[1] // 2 + 1, RED_LO, alpha=0.7)
        rect(top, top.shape[1] // 2 - 1, 5, top.shape[1] // 2 + 1, 7, (190, 194, 204))   # tapón
        for f in ("west", "east"):
            img = natural(f)
            h, w = img.shape[:2]
            for x in range(w):                                # franja negra baja y corte rojo
                y0 = int(h * 0.62 + (x / max(w - 1, 1)) * h * 0.18)
                img[y0:, x, :3] = (18, 18, 22)
                if y0 - 1 >= 0:
                    img[y0 - 1, x, :3] = RED_LO
            store(f, img)
    elif kind == "tank_ridge":
        stripe_top(top, top.shape[1] // 2, 1, (18, 18, 22))
    elif kind == "cowl" or kind == "tail":
        stripe_top(top, top.shape[1] // 2, 2, (18, 18, 22))
        for f in ("west", "east"):
            img = natural(f)
            h, w = img.shape[:2]
            for x in range(w):                                # tajo negro hacia atrás
                y0 = int(h * 0.55 + (1 - x / max(w - 1, 1)) * h * 0.3)
                img[y0:, x, :3] = (18, 18, 22)
            store(f, img)
        if kind == "tail":
            b = faces["back"]
            rect(b, 0, b.shape[0] - 1, b.shape[1], b.shape[0], (18, 18, 22))
    elif kind == "cheek":
        img = natural(outer)
        h, w = img.shape[:2]
        for x in range(w):                                    # franja negra fina que sube hacia el frente
            y0 = int(h * 0.70 - (x / max(w - 1, 1)) * h * 0.35)
            img[y0:y0 + 1, x, :3] = (18, 18, 22)
            if y0 + 1 < h:
                img[y0 + 1:y0 + 2, x, :3] = (150, 10, 18)
        for k in range(3):                                    # rejillas de aire
            hline(img, 1 + 2 * k, (16, 16, 20), 1, max(2, int(w * 0.35)))
        store(outer, img)
    elif kind == "shell_top":
        stripe_top(top, top.shape[1] // 2, 2, (18, 18, 22))
        vline(top, top.shape[1] // 2 - 2, RED_LO, alpha=0.7)
        vline(top, top.shape[1] // 2 + 1, RED_LO, alpha=0.7)
        for f_ in ("west", "east"):
            img = natural(f_)
            hline(img, img.shape[0] - 1, (18, 18, 22), 0, img.shape[1])
            store(f_, img)
    elif kind == "shell_side":
        img = natural(outer)
        h, w = img.shape[:2]
        for x in range(w):
            y0 = int(h * 0.30 + (x / max(w - 1, 1)) * h * 0.40)    # franja negra que baja hacia el frente
            img[y0:y0 + 1, x, :3] = (18, 18, 22)
        rect(img, 1, 1, min(w - 1, 4), 3, (20, 20, 24))            # toma de aire
        store(outer, img)
    elif kind == "shell_front":
        stripe_top(top, top.shape[1] // 2, 2, (18, 18, 22))
    elif kind == "nose":
        stripe_top(top, top.shape[1] // 2, 1, (18, 18, 22))
        f = faces["front"]
        rect(f, 0, f.shape[0] - 1, f.shape[1], f.shape[0], (18, 18, 22))
    elif kind == "wingtip":
        pass
    elif kind == "seat":
        a = top
        rows, cols = a.shape[:2]
        for y in range(rows):
            for x in range(cols):
                if (x + y) % 2 == 0:
                    a[y, x, :3] *= 1.28
        for x in range(1, cols - 1):
            if x % 2 == 1:
                a[1, x, :3] = (190, 18, 24)
                a[rows - 2, x, :3] = (190, 18, 24)
        for y in range(1, rows - 1):
            if y % 2 == 1:
                a[y, 1, :3] = (190, 18, 24)
                a[y, cols - 2, :3] = (190, 18, 24)
    elif kind == "engine":
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            for y in range(1, a.shape[0], 2):
                a[y, :, :3] *= 0.72
        for y in range(1, top.shape[0], 2):
            top[y, :, :3] *= 0.74
    elif kind == "cover":
        for f in ("west", "east"):
            a = faces[f]
            h, w = a.shape[:2]
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            for y in range(h):
                for x in range(w):
                    r = math.hypot(x - cx, y - cy)
                    if r < min(w, h) / 2.0 - 0.4:
                        a[y, x, :3] *= 1.18 if r < min(w, h) / 4.0 else 0.88
    elif kind == "radiator":
        a = faces["front"]
        for y in range(a.shape[0]):
            for x in range(a.shape[1]):
                if (x + y) % 2 == 0:
                    a[y, x, :3] = (58, 62, 70)
    elif kind == "belly":
        for f in ("west", "east"):
            a = faces[f]
            hline(a, a.shape[0] - 1, RED, 0, a.shape[1], 0.9)
    elif kind == "lens":
        f = faces["front"]
        f[..., :3] = (168, 178, 192)
        rect(f, 0, 0, f.shape[1], 1, (28, 30, 36))
        rect(f, 0, f.shape[0] - 1, f.shape[1], f.shape[0], (28, 30, 36))
        rect(f, 0, 0, 1, f.shape[0], (28, 30, 36))
        rect(f, f.shape[1] - 1, 0, f.shape[1], f.shape[0], (28, 30, 36))
        rect(f, 1, 1, f.shape[1] - 1, 2, (214, 226, 240))        # franja LED
        g = solid(f.shape[0], f.shape[1], (0, 0, 0), 0)
        rect(g, 0, 0, g.shape[1], g.shape[0], (255, 250, 226), 255)
        rect(g, 0, 0, g.shape[1], 1, (255, 228, 160), 255)
        glow["front"] = g
        for ff in ("west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (36, 38, 44)
    elif kind == "taillight":
        for ff in ("front", "west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (120, 10, 14)
        for ff in ("back", "west", "east"):
            g = solid(faces[ff].shape[0], faces[ff].shape[1], (255, 36, 28), 255)
            glow[ff] = g
        glow["top"] = solid(c.d, c.w, (255, 36, 28), 255)[::-1, ::-1].copy()
    elif kind == "glass":
        for f in ("front", "back"):
            a = faces[f]
            a[..., :3] = (22, 30, 42)
            a[..., 3] = 150
            for x in range(a.shape[1]):
                y = (x * 2) % a.shape[0]
                a[y, x, :3] = (96, 110, 130)
                a[y, x, 3] = 190
        for f in ("west", "east", "top", "bottom"):
            faces[f][..., :3] = (20, 24, 32)
            faces[f][..., 3] = 200
        top = faces["top"][::-1, ::-1].copy()
    elif kind == "mirror":
        a = faces["back"]
        a[..., :3] = (120, 130, 144)
        rect(a, 0, 0, a.shape[1], 1, (16, 16, 20))
        rect(a, 0, a.shape[0] - 1, a.shape[1], a.shape[0], (16, 16, 20))
        rect(a, 0, 0, 1, a.shape[0], (16, 16, 20))
        rect(a, a.shape[1] - 1, 0, a.shape[1], a.shape[0], (16, 16, 20))
    elif kind == "dash":
        t = top
        rows, cols = t.shape[:2]
        rect(t, 0, 0, cols, rows, (12, 12, 15))
        rect(t, 1, 0, cols - 1, rows - 1, (20, 120, 180))        # pantalla azul
        rect(t, 1, 0, 2, 1, (255, 60, 40))
        rect(t, 2, 1, 3, 2, (255, 255, 255))
        fr = faces["back"]
        rect(fr, 0, 0, fr.shape[1], fr.shape[0], (22, 22, 26))
        rect(fr, 1, 0, 3, 1, (60, 220, 90))
    elif kind == "fork":
        for f in ("west", "east", "front", "back"):
            vline(faces[f], 0, BLACK_HI, alpha=0.6)
    elif kind == "fork_low":
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            vline(a, 0, (236, 240, 246), alpha=0.8)
            vline(a, a.shape[1] - 1, (96, 100, 110), alpha=0.8)
    elif kind == "fender":
        stripe_top(top, top.shape[1] // 2, 1, (18, 18, 22))
    elif kind == "spring":
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            for y in range(0, a.shape[0], 2):
                a[y, :, :3] = (200, 16, 24)
    elif kind == "exhaust":
        for f in ("west", "east", "top", "bottom"):
            a = faces[f] if f in ("west", "east", "bottom") else top
            n = a.shape[1] if f in ("west", "east") else a.shape[0]
            for x in range(a.shape[1]):
                t = x / max(a.shape[1] - 1, 1)
                if f in ("west", "east"):
                    a[:, x, :3] = np.array((44, 46, 54)) * (1 - t) + np.array((96, 84, 78)) * t   # zona calentada
        faces["top"] = top[::-1, ::-1].copy()
        top = faces["top"][::-1, ::-1].copy()
    elif kind == "plate":
        for f in ("front", "back"):
            a = faces[f]
            a[..., :3] = (236, 238, 242)
            rect(a, 0, 0, a.shape[1], 1, (20, 20, 24))
            rect(a, 0, a.shape[0] - 1, a.shape[1], a.shape[0], (20, 20, 24))
            rect(a, 0, 0, 1, a.shape[0], (20, 20, 24))
            rect(a, a.shape[1] - 1, 0, a.shape[1], a.shape[0], (20, 20, 24))
            rect(a, 1, 1, 2, a.shape[0] - 1, (206, 20, 28))
    elif kind == "grip":
        for f in ("west", "east", "top", "bottom"):
            a = faces[f] if f != "top" else top
            for x in range(0, a.shape[1], 2):
                a[:, x, :3] = (36, 36, 40)
        top = faces["top"][::-1, ::-1].copy()
        top = top[::-1, ::-1].copy()
    elif kind == "swing":
        for f in ("west", "east"):
            a = faces[f]
            hline(a, 0, RED, 0, a.shape[1], 0.9)
    elif kind == "clamp_red":
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.25, 0.3)
    elif kind in ("stalk", "bar", "clamp", "peg", "lever", "cap", "pad", "plain", "wing", "beak"):
        pass

    faces["top"] = top[::-1, ::-1].copy()
    if kind == "exhaust" or kind == "grip":
        faces["top"] = top[::-1, ::-1].copy()
    for f in ("west", "east", "front", "back"):
        if c.mat in ("red", "black_gloss", "chrome"):
            edges(faces[f], 0.10, 0.22)
    return faces, glow


# ----------------------------------------------------------------------------------------------- atlas
def pack(cubes):
    """Coloca la huella de cada cubo (2*(w+d) x h+d) en el atlas, por estantes."""
    items = sorted(cubes, key=lambda c: (-(c.h + c.d), -2 * (c.w + c.d)))
    x = y = shelf = 0
    for c in items:
        fw, fh = int(2 * (c.w + c.d)), int(c.h + c.d)
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        if y + fh > TEX_H:
            raise RuntimeError("El atlas 256x256 se llenó; hay que compactar cubos")
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


def paint_atlas(cubes, seed=2000, raw=False):
    atlas = np.zeros((TEX_H, TEX_W, 4), dtype=np.float32)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.float32)
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
