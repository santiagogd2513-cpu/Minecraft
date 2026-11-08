#!/usr/bin/env python3
"""Camión de reparto AMAZOOM (parodia de una marca de envíos famosa) de BeautifulDecor, V3.0.0: geometría, textura, brillo,
DeliveryTruckModel.java, ícono y vistas.

Mismo método que la camioneta Furd F-150 (tools/v210/truck.py): una sola definición de geometría produce el modelo Java y
la textura, así que nunca se desalinean.

Uso (desde la raíz del proyecto):
  python3 -m tools.v300.delivery_truck --preview vista.png      # solo mira
  python3 -m tools.v300.delivery_truck --write                  # escribe textura, brillo, modelo Java e ícono
Coordenadas "amigables": x = izquierda (+), y = altura, z = adelante (+); el origen está en el centro, a nivel del suelo.
Los asientos van a x = +-7, z = +9 (ver DeliveryTruckEntity). Dos árboles de piezas: "body" (opaco) y "glass" (vidrios).
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline
from .common300 import draw_text, text_width
from ..v320.paintkit import tone

TEX_W, TEX_H = 256, 320

# Colores de referencia de los canales que el jugador puede cambiar (V3.2.0): pintura, manijas y rines.
WHITE_REF, WHITE_LO_REF = (236, 238, 244), (186, 190, 202)
HANDLE_REF = (176, 182, 194)
RIM_REF, RIM_HUB_REF, RIM_SPOKE_REF = (206, 210, 218), (204, 208, 216), (184, 190, 202)
WHITE = WHITE_REF
WHITE_LO = WHITE_LO_REF
HANDLE = HANDLE_REF
RIM = RIM_REF
RIM_HUB = RIM_HUB_REF
RIM_SPOKE = RIM_SPOKE_REF
NAVY = (22, 44, 112)
NAVY_HI = (46, 78, 168)
NAVY_LO = (12, 24, 70)
ORANGE = (250, 140, 24)
GRAY = (74, 78, 88)
MAT_COLOR = {
    "paint": WHITE, "navy": NAVY, "chrome": (190, 196, 208), "dark": (22, 23, 27), "liner": (40, 42, 47),
    "seat": (58, 62, 72), "carpet": (44, 46, 52), "dash": (28, 29, 34), "trim": (64, 66, 74), "tire": (26, 26, 29),
    "glass": (24, 34, 48), "lens": (160, 170, 184), "taillight": (150, 12, 16), "mirror": (24, 24, 28),
    "grille": (14, 14, 18), "hub": (190, 196, 208), "box": WHITE, "tank": (150, 154, 164), "bumper": (120, 124, 134),
    "marker": (230, 150, 30),
}


REFS = {"body": WHITE_REF, "handle": HANDLE_REF, "rim": RIM_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global WHITE, WHITE_LO, HANDLE, RIM, RIM_HUB, RIM_SPOKE
    WHITE = tone(WHITE_REF, WHITE_REF, body)
    WHITE_LO = tone(WHITE_LO_REF, WHITE_REF, body)
    HANDLE = tone(HANDLE_REF, HANDLE_REF, handle)
    RIM = tone(RIM_REF, RIM_REF, rim)
    RIM_HUB = tone(RIM_HUB_REF, RIM_REF, rim)
    RIM_SPOKE = tone(RIM_SPOKE_REF, RIM_REF, rim)
    MAT_COLOR["paint"] = WHITE
    MAT_COLOR["box"] = WHITE


def box(b, part, name, x0, x1, y0, y1, z0, z1, mat, **opts):
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    for v in (w, h, d):
        assert abs(v - round(v)) < 1e-6 and v >= 1, (name, w, h, d)
    return b.cube(part, name, ((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0),
                  (int(round(w)), int(round(h)), int(round(d))), mat, **opts)


def lr(b, part, name, xa, xb, y0, y1, z0, z1, mat, **opts):
    """Pareja izquierda (x entre xa y xb, >= 0) y derecha (espejo)."""
    box(b, part, name + "_l", xa, xb, y0, y1, z0, z1, mat, side=1, **opts)
    box(b, part, name + "_r", -xb, -xa, y0, y1, z0, z1, mat, side=-1, **opts)


WHEEL_R = 7.5
FRONT_Z, REAR_Z = 17.0, -36.0
WHEEL_X = 13.0
BOX_Z0, BOX_Z1 = -68, -4
CAB_Z0, CAB_Z1 = -2, 24
SEAT_X, SEAT_Z = 7.0, 9.0
WS_ANGLE = 12.0


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- chasis y mecánica
    for k, (z0, z1) in enumerate(((-66, -36), (-36, -6), (-6, 24))):
        box(b, body, f"chassis_{k}", -9, 9, 8, 11, z0, z1, "dark", kind="plain")
    box(b, body, "engine_bay", -10, 10, 6, 11, 14, 27, "dark", kind="intake")
    lr(b, body, "tank", 10, 14, 6, 11, -24, -10, "tank", kind="tank")

    # ---------------------------------------------------------------- ruedas
    for sx, nm in ((1, "l"), (-1, "r")):
        vc.add_wheel(b, body, f"wheel_r{nm}", (WHEEL_X * sx, WHEEL_R, REAR_Z), WHEEL_R, 5, tire_depth=5)
        st = b.part(f"steer_{nm}", body, (WHEEL_X * sx, WHEEL_R, FRONT_Z))
        vc.add_wheel(b, st, f"wheel_f{nm}", (0, 0, 0), WHEEL_R, 5, tire_depth=5)
    lr(b, body, "flap", 12, 14, 3, 9, REAR_Z - 12, REAR_Z - 11, "dark", kind="plain")

    # ---------------------------------------------------------------- caja de carga
    box(b, body, "cargo", -15, 15, 9, 43, BOX_Z0, BOX_Z1, "box", kind="cargo")
    box(b, body, "rear_bumper", -15, 15, 8, 12, BOX_Z0 - 3, BOX_Z0, "bumper", kind="bumper")
    lr(b, body, "tail", 11, 15, 14, 21, BOX_Z0 - 1, BOX_Z0, "taillight", kind="taillight", glow=True)
    lr(b, body, "marker", 12, 14, 41, 42, BOX_Z1 - 1, BOX_Z1, "marker", kind="marker")
    box(b, body, "marker_c", -1, 1, 41, 42, BOX_Z1 - 1, BOX_Z1, "marker", kind="marker")

    # ---------------------------------------------------------------- cabina: piso, puertas, tablero, asientos
    box(b, body, "cab_floor", -14, 14, 10, 12, CAB_Z0, 24, "carpet", kind="plain")
    lr(b, body, "door", 12, 14, 11, 25, CAB_Z0, 22, "paint", kind="door")
    lr(b, body, "step", 14, 17, 9, 10, 4, 20, "dark", kind="plain")
    lr(b, body, "handle", 14, 15, 21, 22, 15, 20, "paint", kind="handle")
    box(b, body, "cab_back", -14, 14, 12, 34, CAB_Z0, 0, "trim", kind="trimwall")
    box(b, body, "dash", -12, 12, 12, 23, 17, 22, "dash", kind="dash")
    box(b, body, "console", -2, 2, 12, 16, 3, 14, "dark", kind="plain")
    box(b, body, "shifter", -1, 1, 16, 20, 10, 12, "dark", kind="plain")

    for sx, nm in ((1, "l"), (-1, "r")):
        cx = SEAT_X * sx
        box(b, body, f"seat_{nm}", cx - 4, cx + 4, 12, 14, SEAT_Z - 4, SEAT_Z + 4, "seat", kind="seat")
        box(b, body, f"seat_back_{nm}", cx - 4, cx + 4, 14, 25, SEAT_Z - 6, SEAT_Z - 4, "seat", kind="seatback")
        box(b, body, f"headrest_{nm}", cx - 2, cx + 2, 25, 29, SEAT_Z - 6, SEAT_Z - 4, "seat", kind="seatback")

    # volante (casi vertical, como en un camión): se inclina y gira con la dirección
    tilt = b.part("steering_tilt", body, (SEAT_X, 20.5, 14.5), (-deg(32.0), 0.0, 0.0))
    wheel = b.part("steering_wheel", tilt, (0, 0, 0))
    box(b, wheel, "sw_top", -4, 5, 4, 5, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_bottom", -4, 5, -5, -4, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_left", 4, 5, -4, 4, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_right", -4, -3, -4, 4, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_hub", -2, 3, -1, 2, -1, 1, "dark", kind="swhub")
    box(b, wheel, "sw_spoke", -3, 4, 0, 1, 0, 1, "dark", kind="plain")
    box(b, body, "column", SEAT_X - 1, SEAT_X + 1, 15, 18, 16, 20, "dark", kind="plain")

    # ---------------------------------------------------------------- frente de la cabina (cabina adelantada)
    box(b, body, "front_panel", -14, 14, 11, 25, 22, 26, "paint", kind="front_panel")
    box(b, body, "grille", -8, 8, 13, 22, 26, 27, "grille", kind="grille")
    lr(b, body, "head", 9, 14, 14, 21, 26, 27, "lens", kind="head", glow=True)
    box(b, body, "bumper_front", -15, 15, 8, 13, 26, 30, "bumper", kind="bumper")
    box(b, body, "valance", -11, 11, 5, 8, 24, 28, "dark", kind="plain")
    lr(b, body, "fog", 5, 8, 9, 11, 30, 31, "lens", kind="fog")
    box(b, body, "plate", -4, 4, 9, 12, 30, 31, "chrome", kind="plate")

    # ---------------------------------------------------------------- techo, pilares, espejos, deflector
    box(b, body, "roof", -14, 14, 34, 36, CAB_Z0, 26, "paint", kind="roof")
    lr(b, body, "pillar_a", 12, 14, 25, 34, 20, 24, "paint", kind="pillar")
    lr(b, body, "pillar_b", 12, 14, 25, 34, CAB_Z0, 2, "paint", kind="pillar")
    lr(b, body, "pillar_in_a", 10, 12, 25, 34, 20, 24, "paint", kind="pillar")
    lr(b, body, "mirror_arm", 14, 17, 28, 29, 20, 21, "dark", kind="plain")
    lr(b, body, "mirror", 17, 19, 22, 32, 18, 22, "mirror", kind="mirror")
    box(b, body, "fairing_1", -13, 13, 36, 38, -4, 6, "paint", kind="fairing")
    box(b, body, "fairing_2", -13, 13, 38, 41, -4, 2, "paint", kind="fairing")
    box(b, body, "fairing_3", -13, 13, 41, 43, -4, -1, "paint", kind="fairing")
    box(b, body, "stack", -14, -12, 12, 42, BOX_Z1 + 0, BOX_Z1 + 2, "chrome", kind="stack")
    box(b, body, "stack_cap", -15, -11, 42, 44, BOX_Z1 - 1, BOX_Z1 + 3, "dark", kind="plain")
    ws = b.part("ws_frame", body, (0, 25.0, 24.0), (-deg(WS_ANGLE), 0.0, 0.0))
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(ws, f"ws_pillar_{nm}", (13.0 * sx, 4.5, 0.0), (2, 9, 2), "paint", kind="pillar", side=sx)
    b.cube(ws, "ws_top", (0, 9.5, 0.0), (26, 1, 2), "paint", kind="pillar")

    # ---------------------------------------------------------------- vidrios (árbol aparte)
    wg = b.part("ws_glass", glass, (0, 25.0, 24.0), (-deg(WS_ANGLE), 0.0, 0.0))
    b.cube(wg, "windshield", (0, 4.5, 0.0), (24, 9, 1), "glass", kind="glass", translucent=True)
    lr(b, glass, "win_f", 13, 14, 25, 34, 2, 20, "glass", kind="glass", translucent=True)
    box(b, glass, "rear_glass", -10, 10, 24, 33, CAB_Z0, CAB_Z0 + 1, "glass", kind="glass", translucent=True)
    return b


def pose(b, wheel_rot=0.0, steer=0.0, glass=True, body=True):
    """Pose del modelo (mismo cálculo que DeliveryTruckModel.pose)."""
    for nm in ("wheel_rl", "wheel_rr", "wheel_fl", "wheel_fr"):
        b.parts[nm].rot = [wheel_rot, 0.0, 0.0]
    b.parts["steer_l"].rot = [0.0, steer, 0.0]
    b.parts["steer_r"].rot = [0.0, steer, 0.0]
    b.parts["steering_wheel"].rot = [0.0, 0.0, -steer * 2.5]
    b.parts["glass"].visible = glass
    b.parts["body"].visible = body


# ----------------------------------------------------------------------------------------------- pintura
def wheel_art(Y, Z, kind):
    r = math.hypot(Y, Z)
    ang = math.atan2(Z, Y)
    if r >= 6.5:
        return (24, 24, 27, 255)
    if r >= 5.5:
        return (38, 38, 43, 255)                           # costado del neumático
    if r >= 4.8:
        return RIM + (255,)                                # labio del rin
    if r < 1.7:
        if r < 1.0:
            return (22, 44, 112, 255)                      # tapa azul de AMAZOOM
        return RIM_HUB + (255,)
    for k in range(8):                                      # 8 agujeros de tuerca/rayos
        a0 = math.radians(45 * k + 22)
        d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
        if d * r < 0.9:
            return RIM_SPOKE + (255,)
    return (52, 54, 62, 255)


def glass_faces(c, rng):
    """Vidrio ahumado: azul grisáceo translúcido con un par de destellos diagonales suaves."""
    faces = {}
    for f, (rows, cols) in vc.face_sizes(c).items():
        a = solid(rows, cols, (30, 44, 62), 118)
        if f in ("west", "east", "front", "back") and rows >= 3 and cols >= 3:
            for x in range(cols):
                for k in range(0, rows + cols, 9):
                    y = k - x
                    if 0 <= y < rows and (k // 9) % 2 == 0:
                        a[y, x, :3] = (120, 140, 164)
                        a[y, x, 3] = 150
        faces[f] = a
    return faces, {}


def side_names(c):
    side = c.opts.get("side", 1)
    return ("east", "west") if side > 0 else ("west", "east")      # (exterior, interior)


def livery(img, mirrored=False):
    """Rotulado AMAZOOM en el costado de la caja (imagen vista de afuera con el frente a la DERECHA)."""
    h, w = img.shape[:2]
    # franja azul inferior con filete naranja
    rect(img, 0, h - 2, w, h, (26, 28, 34))                      # bajos de la caja
    rect(img, 0, h - 9, w, h - 2, NAVY)
    rect(img, 0, h - 11, w, h - 10, ORANGE)
    hline(img, h - 9, NAVY_HI, alpha=0.7)
    # marca: AMA (azul) + ZOOM (naranja) con líneas de velocidad
    scale = 2
    text_full = "AMAZOOM"
    tw = text_width(text_full, scale)
    x0 = (w - tw) // 2
    y0 = 6
    draw_text(img, "AMA", x0, y0, NAVY, scale, shadow=(188, 194, 210))
    ax = x0 + text_width("AMA", scale) + scale
    draw_text(img, "ZOOM", ax, y0, ORANGE, scale, shadow=(150, 80, 20))
    # subrayado naranja con punta de flecha (hacia el frente del camión)
    uy = y0 + 11
    ux0, ux1 = x0, x0 + tw
    rect(img, ux0, uy, ux1, uy + 1, ORANGE)
    tip = ux0 if mirrored else ux1
    for k in range(3):
        rect(img, tip - (k if not mirrored else -k), uy - k, tip - (k if not mirrored else -k) + 1, uy + 1 + k, ORANGE)
    # lema
    tag = "ENVIOS RAPIDOS"
    draw_text(img, tag, (w - text_width(tag)) // 2, 19, (84, 96, 140))
    # costuras de paneles
    for x in range(8, w - 4, 8):
        vline(img, x, WHITE_LO, y0=0, y1=h - 9, alpha=0.45)
    hline(img, 0, (206, 210, 220), alpha=0.8)
    return img


def paint_cube(c, rng):
    if c.opts.get("wheel"):
        return vc.wheel_faces(c, rng, wheel_art, tread=((26, 26, 29), (42, 42, 47)))
    kind = c.opts.get("kind", "plain")
    if kind == "glass":
        return glass_faces(c, rng)
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    gloss = c.mat in ("paint", "chrome", "mirror", "box")
    faces = vc.base_faces(c, color, rng, gloss=gloss)
    glow = {}
    top = vc.natural_top(faces)
    outer, inner = side_names(c)

    if kind == "cargo":
        # Costado derecho (west): el frente queda a la derecha de la imagen. Costado izquierdo (east): el render lo
        # muestra sin espejo y el frente queda a la izquierda; el texto se dibuja directo para que se lea bien en los dos.
        faces["west"] = livery(faces["west"])
        faces["east"] = livery(faces["east"], mirrored=True)
        a = faces["back"]                         # puertas traseras
        h, w = a.shape[:2]
        outline_rect(a, WHITE_LO, alpha=0.9)
        vline(a, w // 2, (110, 114, 128), alpha=0.95)
        vline(a, w // 2 - 1, (110, 114, 128), alpha=0.5)
        rect(a, 0, h - 2, w, h, (26, 28, 34))
        rect(a, 0, h - 9, w, h - 2, NAVY)
        rect(a, 0, h - 11, w, h - 10, ORANGE)
        for hx in (w // 2 - 4, w // 2 + 3):                                  # manijas
            rect(a, hx, 14, hx + 1, 24, (150, 154, 166))
        for hy in (5, h - 14):                                              # bisagras
            for hx in (1, w - 2):
                rect(a, hx, hy, hx + 1, hy + 3, (90, 94, 106))
        draw_text(a, "AMA", 2, 5, NAVY)
        draw_text(a, "ZOOM", 2 + text_width("AMA") + 1, 5, ORANGE)
        faces["back"] = a
        f = faces["front"]
        f[..., :3] = WHITE_LO
        noise(f, rng, 0.03)
        rect(f, 0, f.shape[0] - 11, f.shape[1], f.shape[0] - 2, NAVY)
        rect(f, 0, f.shape[0] - 13, f.shape[1], f.shape[0] - 12, ORANGE)
        draw_text(f, "AMAZOOM", (f.shape[1] - text_width("AMAZOOM")) // 2, 6, NAVY)
        for y in range(top.shape[0]):                                          # techo con nervaduras
            if y % 8 == 0:
                top[y, :, :3] *= 0.86
        vline(top, 0, (206, 210, 220), alpha=0.8)
        vline(top, top.shape[1] - 1, (206, 210, 220), alpha=0.8)
        rect(top, top.shape[1] // 2 - 3, 6, top.shape[1] // 2 + 3, 12, (160, 164, 176))        # claraboya de ventilación
        faces["bottom"][..., :3] = (60, 62, 70)
    elif kind == "door":
        img = vc.natural_side(faces, outer)
        h, w = img.shape[:2]
        vline(img, 0, WHITE_LO, alpha=0.9)
        vline(img, w - 1, WHITE_LO, alpha=0.9)
        hline(img, 0, (206, 210, 220), alpha=0.8)
        rect(img, 0, h - 5, w, h - 1, NAVY)
        hline(img, h - 6, ORANGE, alpha=1.0)
        hline(img, h - 1, (30, 34, 44), alpha=0.9)
        rect(img, 0, h - 1, w, h, (26, 28, 34))
        vc.store_side(faces, outer, img)
        trim = vc.natural_side(faces, inner)
        trim[..., :3] = (64, 66, 74)
        hline(trim, 5, (84, 86, 96), alpha=0.9)
        vc.store_side(faces, inner, trim)
    elif kind == "handle":
        for f in ("west", "east", "top", "bottom", "front", "back"):
            if f == "top":
                top[..., :3] = HANDLE
            else:
                faces[f][..., :3] = HANDLE
    elif kind == "front_panel":
        a = faces["front"]
        rows, cols = a.shape[:2]
        rect(a, 0, rows - 5, cols, rows, NAVY)
        hline(a, rows - 6, ORANGE)
        outline_rect(a, WHITE_LO, alpha=0.6)
        draw_text(a, "AMAZOOM", (cols - text_width("AMAZOOM")) // 2, 2, NAVY)
        rect(a, 1, 8, 5, 9, ORANGE)
        rect(a, cols - 5, 8, cols - 1, 9, ORANGE)
        faces["front"] = a
    elif kind == "taillight":
        for ff in ("west", "east", "top", "bottom", "front"):
            faces[ff][..., :3] = (96, 8, 12)
        a = faces["back"]
        a[..., :3] = (206, 24, 30)
        outline_rect(a, (60, 6, 8))
        hline(a, a.shape[0] // 2, (250, 120, 110), alpha=0.7)
        glow["back"] = solid(a.shape[0], a.shape[1], (255, 40, 30), 255)
        glow["west"] = solid(*faces["west"].shape[:2], (255, 40, 30), 255)
        glow["east"] = solid(*faces["east"].shape[:2], (255, 40, 30), 255)
    elif kind == "marker":
        for f in list(faces):
            faces[f][..., :3] = (230, 150, 30)
    elif kind == "grille":
        a = faces["front"]
        rows, cols = a.shape[:2]
        a[..., :3] = (12, 12, 16)
        for y in range(rows):
            if y % 2 == 1:
                a[y, 1:cols - 1, :3] = (30, 32, 40)
        rect(a, 0, 0, cols, 1, (196, 202, 214))
        rect(a, cols // 2 - 3, rows // 2 - 1, cols // 2 + 3, rows // 2 + 2, NAVY)         # insignia
        rect(a, cols // 2 - 2, rows // 2, cols // 2 + 2, rows // 2 + 1, ORANGE)
        for ff in ("west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (20, 20, 24)
    elif kind == "intake":
        a = faces["front"]
        for y in range(a.shape[0]):
            for x in range(a.shape[1]):
                a[y, x, :3] = (26, 28, 34) if (x + y) % 2 == 0 else (14, 14, 18)
    elif kind == "head":
        a = faces["front"]
        rows, cols = a.shape[:2]
        a[..., :3] = (174, 184, 198)
        outline_rect(a, (28, 30, 36))
        rect(a, 1, 1, cols - 1, 2, (226, 236, 248))
        rect(a, 1, rows - 2, cols - 1, rows - 1, (226, 236, 248))
        g = solid(rows, cols, (0, 0, 0), 0)
        rect(g, 1, 1, cols - 1, rows - 1, (255, 250, 228), 255)
        rect(g, 0, 0, cols, 1, (255, 232, 170), 255)
        glow["front"] = g
        for ff in ("west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (36, 38, 44)
    elif kind == "fog":
        a = faces["front"]
        a[..., :3] = (210, 216, 226)
    elif kind == "plate":
        a = faces["front"]
        a[..., :3] = (232, 232, 236)
        outline_rect(a, (30, 30, 40))
        draw_text(a, "ZOOM", (a.shape[1] - text_width("ZOOM")) // 2, 1, (22, 44, 112))
    elif kind == "bumper":
        for f in ("front", "back", "west", "east"):
            a = faces[f]
            hline(a, 0, (214, 218, 228), alpha=0.7)
            hline(a, a.shape[0] - 1, (60, 64, 74), alpha=0.7)
        for y in range(top.shape[0]):
            top[y, :, :3] *= 1.08
    elif kind == "tank":
        for f in ("west", "east"):
            a = faces[f]
            for x in range(a.shape[1]):
                a[:, x, :3] = np.array((170, 174, 184)) * (1 - x / max(1, a.shape[1] - 1) * 0.35)
    elif kind == "stack":
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            for y in range(a.shape[0]):
                a[y, :, :3] = np.array((190, 196, 208)) * (0.8 + 0.2 * math.sin(y * 0.5))
    elif kind == "roof":
        rows, cols = top.shape[:2]
        vline(top, 0, (206, 210, 220), alpha=0.5)
        vline(top, cols - 1, (206, 210, 220), alpha=0.5)
        faces["bottom"][..., :3] = (150, 152, 162)
        for f in ("west", "east", "front", "back"):
            hline(faces[f], faces[f].shape[0] - 1, WHITE_LO, alpha=0.6)
    elif kind == "fairing":
        for f in ("west", "east", "front", "back"):
            hline(faces[f], 0, (210, 214, 224), alpha=0.8)
        a = faces["front"]
        rect(a, 0, a.shape[0] - 1, a.shape[1], a.shape[0], NAVY)
    elif kind == "pillar":
        for f in ("west", "east", "front", "back", "bottom"):
            faces[f][..., :3] = (64, 66, 74)
        img = vc.natural_side(faces, outer)
        img[..., :3] = WHITE
        noise(img, rng, 0.03)
        vc.store_side(faces, outer, img)
        top[..., :3] = WHITE
        if c.name.startswith("ws_"):
            for f in ("west", "east"):
                faces[f][..., :3] = WHITE
    elif kind == "trimwall":
        for f in list(faces):
            faces[f][..., :3] = (64, 66, 74)
    elif kind == "dash":
        rows, cols = top.shape[:2]
        top[..., :3] = (30, 31, 36)
        rect(top, 1, 1, cols - 1, 2, (50, 52, 60))
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (32, 33, 38)
        rect(a, 9, 2, 17, 7, (24, 24, 28))                                 # pantalla central
        rect(a, 10, 3, 16, 6, (44, 104, 176))
        rect(a, 2, 1, 7, 5, (16, 16, 20))                                  # tablero del conductor
        rect(a, 3, 2, 6, 4, (240, 120, 40))
        hline(a, h - 1, (14, 14, 18), alpha=0.8)
    elif kind == "seat":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x + y) % 2 == 0:
                    top[y, x, :3] *= 1.12
        outline_rect(top, (40, 42, 50), alpha=0.8)
    elif kind == "seatback":
        for f in ("front", "back"):
            a = faces[f]
            for y in range(a.shape[0]):
                for x in range(a.shape[1]):
                    if (x + y) % 2 == 0:
                        a[y, x, :3] *= 1.12
            outline_rect(a, (40, 42, 50), alpha=0.8)
    elif kind == "mirror":
        for f in ("west", "east", "top", "bottom", "front"):
            faces[f][..., :3] = (22, 22, 26)
        a = faces["back"]
        a[..., :3] = (22, 22, 26)
        rect(a, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (128, 140, 156))
    elif kind == "swhub":
        for f in ("front", "back"):
            rect(faces[f], 0, 0, faces[f].shape[1], faces[f].shape[0], NAVY)

    vc.store_top(faces, top)
    if c.mat in ("paint", "box") and kind not in ("pillar", "handle", "taillight", "marker", "cargo"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.05, 0.10)
    return faces, glow


def build_all():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3150)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3150, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    """Misma plantilla que PickupModel (tools/v210/truck.py): el camión usa la misma API de pose."""
    from ..v210 import truck as pickup_tool
    src = pickup_tool.java_model(b)
    src = src.replace("PickupModel", "DeliveryTruckModel").replace("PickupEntity", "DeliveryTruckEntity")
    src = src.replace('MOD_ID, "ford_f150"', 'MOD_ID, "delivery_truck"')
    src = src.replace("Camioneta Furd F-150 (V2.1.0). ARCHIVO GENERADO por tools/v210/truck.py",
                      "Camión de reparto AMAZOOM (V3.0.0). ARCHIVO GENERADO por tools/v300/delivery_truck.py")
    src = src.replace("LayerDefinition.create(mesh, 256, 256)", f"LayerDefinition.create(mesh, {TEX_W}, {TEX_H})")
    src = src.replace("Atlas de textura de 256x256", f"Atlas de textura de {TEX_W}x{TEX_H}")
    assert "ford_f150" not in src and "Pickup" not in src
    return src


# ----------------------------------------------------------------------------------------------- main
VIEWS = [(145, 14), (215, 14), (90, 3), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 20.0, -20)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--wheel", type=float, default=0.5)
    ap.add_argument("--steer", type=float, default=0.0)
    ap.add_argument("--dark", action="store_true")
    args = ap.parse_args()

    b, tex, glow = build_all()
    used = max(c.v + int(c.h + c.d) for c in b.cubes)
    print(f"{len(b.cubes)} cubos, atlas usado hasta y={used}/{TEX_H}")
    pose(b, args.wheel, args.steer)
    if args.preview:
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), VIEWS, CENTER, 3.6, light_on=not args.dark, size=(420, 330))
        vc.sheet(imgs).convert("RGB").save(args.preview)
        big = tex.resize((TEX_W * 3, TEX_H * 3), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/delivery_truck.png")
        glow.save(ASSETS / "textures/entity/delivery_truck_glow.png")
        (JAVA / "client/DeliveryTruckModel.java").write_text(java_model(b), encoding="utf-8")
        pose(b, 0.0, 0.0)
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 140, 16, CENTER, 5.6, outline=(8, 12, 30, 255), brighten=1.0)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item/delivery_truck.png")
        print("escrito: textura, brillo, DeliveryTruckModel.java, ícono")


if __name__ == "__main__":
    main()
