#!/usr/bin/env python3
"""Camioneta Furd F-150 azul oscuro de BeautifulDecor (v2.1.0): geometría, textura, brillo, PickupModel.java, ícono y vistas.

Uso (desde la raíz del proyecto):
  python3 -m tools.v210.truck --preview vista.png      # solo mira
  python3 -m tools.v210.truck --write                  # escribe textura, brillo, modelo Java e ícono
Coordenadas "amigables": x = izquierda (+), y = altura, z = adelante (+); el origen está en el centro, a nivel del suelo.
El conductor va a la izquierda (x = +6,7), el acompañante a la derecha, ambos a z = +4,8 (ver PickupEntity).
El modelo tiene dos árboles de piezas: "body" (opaco) y "glass" (vidrios ahumados), que el renderizador dibuja por separado
para que los vidrios translúcidos se mezclen bien con el interior.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from . import vcommon as vc
from .vcommon import (ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, text, text_width, vline)
from ..v320.paintkit import tone

TEX_W = TEX_H = 256

# Colores de referencia de los tres canales que el jugador puede cambiar (V3.2.0): pintura, manijas y rines.
BODY_REF, BODY_HI_REF, BODY_LO_REF = (18, 38, 100), (52, 88, 170), (8, 20, 60)
HANDLE_REF = (176, 182, 194)
RIM_REF, RIM_HUB_REF, RIM_SPOKE_REF = (206, 210, 218), (204, 208, 216), (184, 190, 202)
BODY = BODY_REF
BODY_HI = BODY_HI_REF
BODY_LO = BODY_LO_REF
HANDLE = HANDLE_REF
RIM = RIM_REF
RIM_HUB = RIM_HUB_REF
RIM_SPOKE = RIM_SPOKE_REF
TRIM = (52, 54, 60)
MAT_COLOR = {
    "paint": BODY, "chrome": (190, 196, 208), "dark": (22, 23, 27), "liner": (36, 38, 43), "seat": (62, 64, 72),
    "carpet": (40, 42, 48), "dash": (24, 25, 30), "headliner": (150, 152, 162), "trim": TRIM, "tire": (26, 26, 29),
    "glass": (24, 34, 48), "lens": (160, 170, 184), "taillight": (150, 12, 16), "mirror": (24, 24, 28),
    "grille": (14, 14, 18), "hub": (190, 196, 208),
}


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


REFS = {"body": BODY_REF, "handle": HANDLE_REF, "rim": RIM_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global BODY, BODY_HI, BODY_LO, HANDLE, RIM, RIM_HUB, RIM_SPOKE
    BODY = tone(BODY_REF, BODY_REF, body)
    BODY_HI = tone(BODY_HI_REF, BODY_REF, body)
    BODY_LO = tone(BODY_LO_REF, BODY_REF, body)
    HANDLE = tone(HANDLE_REF, HANDLE_REF, handle)
    RIM = tone(RIM_REF, RIM_REF, rim)
    RIM_HUB = tone(RIM_HUB_REF, RIM_REF, rim)
    RIM_SPOKE = tone(RIM_SPOKE_REF, RIM_REF, rim)
    MAT_COLOR["paint"] = BODY


WHEEL_R = 7.0
FRONT_Z, REAR_Z = 23.0, -23.0
WHEEL_X = 12.0
Z_BACK, Z_FRONT = -35, 35
SEAT_X, SEAT_Z = 6.7, 4.8
WS_ANGLE = 40.0


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- bajos y mecánica
    box(b, body, "ub_front", -9, 9, 6, 15, 13, 35, "dark", kind="intake")
    box(b, body, "ub_mid", -9, 9, 6, 10, -34, 13, "dark", kind="plain")
    box(b, body, "muffler", 5, 9, 6, 9, -31, -21, "chrome", kind="muffler")
    box(b, body, "exhaust_tip", 6, 8, 7, 9, -37, -31, "chrome", kind="plain")

    # ---------------------------------------------------------------- ruedas
    for sx, nm in ((1, "l"), (-1, "r")):
        vc.add_wheel(b, body, f"wheel_r{nm}", (WHEEL_X * sx, WHEEL_R, REAR_Z), WHEEL_R, 5, tire_depth=5)
        st = b.part(f"steer_{nm}", body, (WHEEL_X * sx, WHEEL_R, FRONT_Z))
        vc.add_wheel(b, st, f"wheel_f{nm}", (0, 0, 0), WHEEL_R, 5, tire_depth=5)

    # ---------------------------------------------------------------- caja de carga
    box(b, body, "bed_floor", -12, 12, 10, 12, -34, -13, "liner", kind="bedfloor")
    lr(b, body, "bed_wall", 12, 14, 15, 22, -35, -11, "paint", kind="bedside")
    lr(b, body, "bed_low_f", 12, 14, 7, 15, -15, -11, "paint", kind="panel")
    lr(b, body, "bed_low_r", 12, 14, 7, 15, -35, -31, "paint", kind="panel")
    lr(b, body, "bed_rail", 11, 14, 22, 23, -35, -11, "dark", kind="rail")
    lr(b, body, "wheel_hump", 9, 12, 12, 15, -30, -16, "liner", kind="liner")
    box(b, body, "bed_front", -12, 12, 12, 22, -13, -11, "liner", kind="liner")
    box(b, body, "tailgate", -12, 12, 8, 22, -35, -33, "paint", kind="tailgate")
    box(b, body, "bumper_rear", -14, 14, 7, 10, -38, -35, "chrome", kind="bumper")
    lr(b, body, "tail", 11, 14, 14, 21, -36, -35, "taillight", kind="taillight", glow=True)

    # ---------------------------------------------------------------- cabina: suelo, puertas, tablero, asientos
    box(b, body, "cab_floor", -12, 12, 8, 10, -11, 13, "carpet", kind="plain")
    lr(b, body, "door", 12, 14, 7, 22, -11, 13, "paint", kind="door")
    lr(b, body, "step", 14, 16, 8, 9, -9, 11, "dark", kind="plain")
    lr(b, body, "handle_f", 14, 15, 18, 19, 3, 7, "paint", kind="handle")
    lr(b, body, "handle_r", 14, 15, 18, 19, -7, -3, "paint", kind="handle")
    box(b, body, "cab_back", -12, 12, 10, 22, -11, -9, "trim", kind="trimwall")
    box(b, body, "dash", -12, 12, 10, 21, 9, 13, "dash", kind="dash")
    box(b, body, "console", -2, 2, 10, 14, -3, 7, "dark", kind="plain")
    box(b, body, "shifter", -1, 1, 14, 17, 2, 4, "dark", kind="plain")

    for sx, nm in ((1, "l"), (-1, "r")):
        cx = SEAT_X * sx
        box(b, body, f"seat_{nm}", cx - 4, cx + 4, 10, 12, SEAT_Z - 4, SEAT_Z + 4, "seat", kind="seat")
        box(b, body, f"seat_back_{nm}", cx - 4, cx + 4, 12, 21, SEAT_Z - 6, SEAT_Z - 4, "seat", kind="seatback")
        box(b, body, f"headrest_{nm}", cx - 2, cx + 2, 21, 25, SEAT_Z - 6, SEAT_Z - 4, "seat", kind="seatback")
    box(b, body, "bench", -11, 11, 10, 12, -7, -1, "seat", kind="seat")
    box(b, body, "bench_back", -11, 11, 12, 20, -9, -7, "seat", kind="seatback")

    # volante: se inclina y gira con la dirección
    tilt = b.part("steering_tilt", body, (SEAT_X, 17.5, 8.2), (-deg(25.0), 0.0, 0.0))
    wheel = b.part("steering_wheel", tilt, (0, 0, 0))
    box(b, wheel, "sw_top", -3, 4, 3, 4, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_bottom", -3, 4, -4, -3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_left", 3, 4, -3, 3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_right", -3, -2, -3, 3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_hub", -1, 2, -1, 2, -1, 1, "dark", kind="swhub")
    box(b, wheel, "sw_spoke", -2, 3, 0, 1, 0, 1, "dark", kind="plain")
    box(b, body, "column", SEAT_X - 1, SEAT_X + 1, 14, 16, 9, 12, "dark", kind="plain")

    # ---------------------------------------------------------------- capó, defensas y parte delantera
    box(b, body, "front_block", -14, 14, 15, 22, 13, 35, "paint", kind="hood")
    box(b, body, "hood_dome", -8, 8, 22, 23, 15, 31, "paint", kind="hood_dome")
    lr(b, body, "fender_front", 11, 14, 7, 15, 31, 35, "dark", kind="plain")
    box(b, body, "grille", -9, 9, 15, 21, 35, 36, "grille", kind="grille")
    lr(b, body, "head", 9, 14, 16, 21, 35, 36, "lens", kind="head", glow=True)
    box(b, body, "bumper_front", -14, 14, 9, 13, 35, 38, "chrome", kind="bumper")
    box(b, body, "valance", -10, 10, 6, 9, 34, 37, "dark", kind="plain")
    lr(b, body, "fog", 5, 8, 7, 9, 37, 38, "lens", kind="fog")

    # ---------------------------------------------------------------- techo, pilares y espejos
    box(b, body, "roof", -14, 14, 30, 32, -11, 7, "paint", kind="roof")
    lr(b, body, "pillar_b", 12, 14, 22, 30, -1, 1, "paint", kind="pillar")
    lr(b, body, "pillar_c", 12, 14, 22, 30, -11, -9, "paint", kind="pillar")
    lr(b, body, "pillar_c_in", 10, 12, 22, 30, -11, -9, "paint", kind="pillar")
    lr(b, body, "mirror_arm", 14, 16, 24, 25, 10, 11, "dark", kind="plain")
    lr(b, body, "mirror", 15, 18, 23, 27, 9, 12, "mirror", kind="mirror")
    ws = b.part("ws_frame", body, (0, 22.0, 13.0), (-deg(WS_ANGLE), 0.0, 0.0))
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(ws, f"a_pillar_{nm}", (13.0 * sx, 5.2, 0.0), (2, 10, 2), "paint", kind="pillar", side=sx)

    # ---------------------------------------------------------------- vidrios (árbol aparte)
    wg = b.part("ws_glass", glass, (0, 22.0, 13.0), (-deg(WS_ANGLE), 0.0, 0.0))
    b.cube(wg, "windshield", (0, 5.2, 0.0), (24, 10, 1), "glass", kind="glass", translucent=True)
    lr(b, glass, "win_f1", 13, 14, 22, 25, 1, 11, "glass", kind="glass", translucent=True)
    lr(b, glass, "win_f2", 13, 14, 25, 28, 1, 9, "glass", kind="glass", translucent=True)
    lr(b, glass, "win_f3", 13, 14, 28, 30, 1, 7, "glass", kind="glass", translucent=True)
    lr(b, glass, "win_r", 13, 14, 22, 30, -9, -1, "glass", kind="glass", translucent=True)
    box(b, glass, "rear_glass", -10, 10, 22, 30, -11, -10, "glass", kind="glass", translucent=True)
    return b


def pose(b, wheel_rot=0.0, steer=0.0, glass=True, body=True):
    """Pose del modelo (mismo cálculo que PickupModel.pose)."""
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
    if r >= 6.1:
        return (24, 24, 27, 255)
    if r >= 5.2:
        return (38, 38, 43, 255)                           # costado del neumático
    if r >= 4.6:
        return RIM + (255,)                                # labio del rin
    if r < 1.6:
        if r < 0.95:
            return (150, 30, 36, 255)                      # insignia roja de Furd
        return RIM_HUB + (255,)
    for k in range(6):                                      # 6 rayos plateados
        a0 = math.radians(60 * k + 30)
        d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
        if d * r < 0.95:
            return RIM_SPOKE + (255,)
    return (46, 48, 56, 255)


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


def paint_cube(c, rng):
    if c.opts.get("wheel"):
        return vc.wheel_faces(c, rng, wheel_art, tread=((26, 26, 29), (42, 42, 47)))
    kind = c.opts.get("kind", "plain")
    if kind == "glass":
        return glass_faces(c, rng)
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    gloss = c.mat in ("paint", "chrome", "mirror")
    faces = vc.base_faces(c, color, rng, gloss=gloss)
    glow = {}
    top = vc.natural_top(faces)
    outer, inner = side_names(c)

    def gap(a, col=None, row=None, color=BODY_LO, alpha=0.9):
        if col is not None:
            vline(a, col, color, alpha=alpha)
        if row is not None:
            hline(a, row, color, alpha=alpha)

    if kind == "hood":
        rows, cols = top.shape[:2]
        gap(top, col=3)
        gap(top, col=cols - 4)
        gap(top, row=2)
        for x in range(cols):
            top[rows // 2, x, :3] = top[rows // 2, x, :3] * 1.0
        vline(top, 4, BODY_HI, alpha=0.35)
        vline(top, cols - 5, BODY_HI, alpha=0.35)
        a = faces["front"]
        hline(a, a.shape[0] - 1, BODY_LO, alpha=0.8)
        hline(a, 0, BODY_HI, alpha=0.5)
    elif kind == "hood_dome":
        rows, cols = top.shape[:2]
        vline(top, 0, BODY_HI, alpha=0.5)
        vline(top, cols - 1, BODY_HI, alpha=0.5)
        top[:, cols // 2 - 1:cols // 2 + 1, :3] *= 1.12
    elif kind == "door":
        img = vc.natural_side(faces, outer)
        h, w = img.shape[:2]
        vline(img, 0, BODY_LO, alpha=0.9)                              # borde trasero (con la caja)
        vline(img, 12, BODY_LO, alpha=0.9)                             # entre puerta delantera y trasera
        vline(img, w - 1, BODY_LO, alpha=0.9)                          # con el guardabarros
        hline(img, 0, BODY_HI, alpha=0.5)                              # línea de cintura
        hline(img, h - 4, BODY_HI, alpha=0.30)                         # pliegue de la carrocería
        hline(img, h - 1, BODY_LO, alpha=0.7)
        rect(img, 0, h - 3, w, h - 1, (26, 28, 34))                    # faldón inferior oscuro
        vc.store_side(faces, outer, img)
        trim = vc.natural_side(faces, inner)
        trim[..., :3] = TRIM
        hline(trim, 6, (70, 72, 80), alpha=0.9)
        vc.store_side(faces, inner, trim)
    elif kind == "handle":
        for f in ("west", "east", "top", "bottom", "front", "back"):
            if f == "top":
                top[..., :3] = HANDLE
            else:
                faces[f][..., :3] = HANDLE
    elif kind == "bedside":
        img = vc.natural_side(faces, outer)
        h, w = img.shape[:2]
        hline(img, 0, BODY_HI, alpha=0.4)
        hline(img, 2, BODY_HI, alpha=0.30)
        hline(img, h - 1, BODY_LO, alpha=0.6)
        vc.store_side(faces, outer, img)
        liner = vc.natural_side(faces, inner)
        liner[..., :3] = (34, 36, 40)
        vc.store_side(faces, inner, liner)
    elif kind == "panel":
        img = vc.natural_side(faces, outer)
        rect(img, 0, img.shape[0] - 3, img.shape[1], img.shape[0] - 1, (26, 28, 34))
        vc.store_side(faces, outer, img)
        for f in ("front", "back"):
            faces[f][..., :3] = BODY_LO
    elif kind == "rail":
        for y in range(top.shape[0]):
            top[y, :, :3] *= 1.0 if y % 2 else 1.25
    elif kind == "liner":
        for f in list(faces):
            faces[f][..., :3] = (36, 38, 43)
        for y in range(0, top.shape[0], 2):
            top[y, :, :3] = (30, 32, 36)
    elif kind == "bedfloor":
        for y in range(0, top.shape[0]):
            top[y, :, :3] = (44, 46, 52) if y % 3 else (30, 32, 36)
        vline(top, 0, (24, 26, 30), alpha=0.8)
        vline(top, top.shape[1] - 1, (24, 26, 30), alpha=0.8)
    elif kind == "tailgate":
        a = faces["back"]
        rows, cols = a.shape[:2]
        outline_rect(a, BODY_LO, alpha=0.9)
        rect(a, 1, 1, cols - 1, 2, BODY_HI, 255)
        rect(a, 2, rows - 4, cols - 2, rows - 3, BODY_LO, 255)
        w = text_width("FURD")
        text(a, "FURD", (cols - w) // 2, 4, (210, 216, 226))
        text(a, "F-150", (cols - text_width("F-150")) // 2, 11, (150, 158, 172))
        faces["back"] = a
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
    elif kind == "grille":
        a = faces["front"]
        rows, cols = a.shape[:2]
        a[..., :3] = (12, 12, 16)
        for y in range(rows):
            if y % 2 == 1:
                a[y, 1:cols - 1, :3] = (28, 30, 36)
        rect(a, 0, 0, cols, 1, (196, 202, 214))
        text(a, "FURD", (cols - text_width("FURD")) // 2, 1, (226, 230, 238))
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
        rect(a, 1, 1, cols - 1, 2, (226, 236, 248))                       # tira LED
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
    elif kind == "bumper":
        for f in ("front", "back", "west", "east"):
            a = faces[f]
            hline(a, 0, (236, 240, 248), alpha=0.7)
            hline(a, a.shape[0] - 1, (96, 100, 112), alpha=0.7)
        for y in range(top.shape[0]):
            top[y, :, :3] *= 1.08
    elif kind == "muffler":
        for f in ("west", "east"):
            a = faces[f]
            for x in range(a.shape[1]):
                a[:, x, :3] = np.array((120, 124, 134)) * (1 - x / max(1, a.shape[1] - 1) * 0.4)
    elif kind == "roof":
        rows, cols = top.shape[:2]
        top[:, :, :3] *= 1.0
        vline(top, 0, BODY_HI, alpha=0.4)
        vline(top, cols - 1, BODY_HI, alpha=0.4)
        faces["bottom"][..., :3] = (150, 152, 162)
        for f in ("west", "east", "front", "back"):
            hline(faces[f], faces[f].shape[0] - 1, BODY_LO, alpha=0.6)
    elif kind == "pillar":
        for f in ("west", "east", "front", "back", "bottom"):
            faces[f][..., :3] = TRIM
        img = vc.natural_side(faces, outer)
        img[..., :3] = BODY
        noise(img, rng, 0.03)
        vc.store_side(faces, outer, img)
        top[..., :3] = BODY
        if c.name.startswith("a_pillar"):
            for f in ("west", "east"):
                faces[f][..., :3] = BODY
    elif kind == "trimwall":
        for f in list(faces):
            faces[f][..., :3] = TRIM
    elif kind == "dash":
        rows, cols = top.shape[:2]
        top[..., :3] = (26, 27, 32)
        rect(top, 1, 1, cols - 1, 2, (46, 48, 56))
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (30, 31, 36)
        rect(a, 8, 2, 16, 7, (24, 24, 28))                                 # pantalla central
        rect(a, 9, 3, 15, 6, (44, 104, 176))
        rect(a, 2, 1, 6, 4, (16, 16, 20))                                  # tablero del conductor
        rect(a, 3, 2, 5, 3, (240, 120, 40))
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
            rect(faces[f], 0, 0, faces[f].shape[1], faces[f].shape[0], (22, 54, 140))
    elif kind in ("plain",):
        pass

    vc.store_top(faces, top)
    if c.mat in ("paint",) and kind not in ("pillar", "handle", "taillight"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.06, 0.12)
    return faces, glow


def build_all():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=2150)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=2150, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    body = vc.java_tree(b)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.PickupEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Camioneta Furd F-150 (V2.1.0). ARCHIVO GENERADO por tools/v210/truck.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX_W}x{TEX_H}. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios ahumados). El renderizador los dibuja en pasadas distintas con
 * showGlass(false) y showGlass(true), así el vidrio translúcido se mezcla bien con lo que hay detrás (asientos, tablero).
 * Piezas animadas: las 4 ruedas (giran), las dos ruedas delanteras (doblan) y el volante (gira con la dirección).
 */
public class PickupModel extends EntityModel<PickupEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "ford_f150"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart wheelFl;
    private final ModelPart wheelFr;
    private final ModelPart wheelRl;
    private final ModelPart wheelRr;
    private final ModelPart steerL;
    private final ModelPart steerR;
    private final ModelPart steeringWheel;

    public PickupModel(ModelPart root) {{
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.steerL = this.body.getChild("steer_l");
        this.steerR = this.body.getChild("steer_r");
        this.wheelFl = this.steerL.getChild("wheel_fl");
        this.wheelFr = this.steerR.getChild("wheel_fr");
        this.wheelRl = this.body.getChild("wheel_rl");
        this.wheelRr = this.body.getChild("wheel_rr");
        this.steeringWheel = this.body.getChild("steering_tilt").getChild("steering_wheel");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
    }}

    /** wheelRot en radianes (las 4 ruedas), steerRot = giro de las ruedas delanteras en radianes (positivo = a la derecha). */
    public void pose(float wheelRot, float steerRot) {{
        this.wheelFl.xRot = wheelRot;
        this.wheelFr.xRot = wheelRot;
        this.wheelRl.xRot = wheelRot;
        this.wheelRr.xRot = wheelRot;
        this.steerL.yRot = steerRot;
        this.steerR.yRot = steerRot;
        this.steeringWheel.zRot = -steerRot * 2.5f;
    }}

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {{
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }}

    @Override
    public void setupAnim(PickupEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {{
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {{
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }}
}}
'''


# ----------------------------------------------------------------------------------------------- main
VIEWS = [(145, 14), (215, 14), (90, 3), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 15.0, 0)


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
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), VIEWS, CENTER, 5.2, light_on=not args.dark, size=(420, 330))
        vc.sheet(imgs).convert("RGB").save(args.preview)
        big = tex.resize((TEX_W * 3, TEX_H * 3), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/ford_f150.png")
        glow.save(ASSETS / "textures/entity/ford_f150_glow.png")
        (JAVA / "client/PickupModel.java").write_text(java_model(b), encoding="utf-8")
        pose(b, 0.0, 0.0)
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 140, 16, CENTER, 8.5, outline=(8, 12, 30, 255), brighten=1.1)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item/ford_f150.png")
        print("escrito: textura, brillo, PickupModel.java, ícono")


if __name__ == "__main__":
    main()
