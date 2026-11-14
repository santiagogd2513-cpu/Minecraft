#!/usr/bin/env python3
"""Tractomula KENWORT K880 (parodia de una marca famosa de tractocamiones) de BeautifulDecor, V7.9.0: geometría, textura, brillo,
SemiTruckModel.java e ícono. Mismo método que el camión de reparto (tools/v300/delivery_truck.py).

Uso (desde la raíz del proyecto):
  python3 -m tools.v790.semi_truck --preview vista.png
  python3 -m tools.v790.semi_truck --write
Coordenadas "amigables" (px, 16 px = 1 bloque): x = izquierda (+), y = altura, z = adelante (+); el origen está en el centro, a nivel del suelo.
Asientos a x = +-8, z = +4; quinta rueda (donde se engancha el remolque) en z = -36.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline
from ..v300.common300 import draw_text, text_width
from ..v320.paintkit import tone

TEX_W, TEX_H = 384, 384

BODY_REF = (181, 18, 27)
HANDLE_REF = (176, 182, 194)
RIM_REF = (206, 210, 218)
RED, RED_LO = BODY_REF, (132, 12, 20)
HANDLE = HANDLE_REF
RIM, RIM_HUB, RIM_SPOKE = RIM_REF, (204, 208, 216), (184, 190, 202)
GOLD = (226, 178, 40)
MAT_COLOR = {
    "paint": RED, "chrome": (190, 196, 208), "dark": (22, 23, 27), "liner": (40, 42, 47), "seat": (58, 62, 72),
    "carpet": (44, 46, 52), "dash": (28, 29, 34), "trim": (64, 66, 74), "tire": (26, 26, 29), "glass": (24, 34, 48),
    "lens": (160, 170, 184), "taillight": (150, 12, 16), "mirror": (24, 24, 28), "grille": (14, 14, 18),
    "hub": (190, 196, 208), "tank": (170, 174, 184), "bumper": (190, 196, 208), "marker": (230, 150, 30), "btn_blue": (50, 120, 225),
}

REFS = {"body": BODY_REF, "handle": HANDLE_REF, "rim": RIM_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global RED, RED_LO, HANDLE, RIM, RIM_HUB, RIM_SPOKE
    RED = tone(BODY_REF, BODY_REF, body)
    RED_LO = tone((132, 12, 20), BODY_REF, body)
    HANDLE = tone(HANDLE_REF, HANDLE_REF, handle)
    RIM = tone(RIM_REF, RIM_REF, rim)
    RIM_HUB = tone((204, 208, 216), RIM_REF, rim)
    RIM_SPOKE = tone((184, 190, 202), RIM_REF, rim)
    MAT_COLOR["paint"] = RED


def box(b, part, name, x0, x1, y0, y1, z0, z1, mat, **opts):
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    for v in (w, h, d):
        assert abs(v - round(v)) < 1e-6 and v >= 1, (name, w, h, d)
    return b.cube(part, name, ((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0),
                  (int(round(w)), int(round(h)), int(round(d))), mat, **opts)


def lr(b, part, name, xa, xb, y0, y1, z0, z1, mat, **opts):
    box(b, part, name + "_l", xa, xb, y0, y1, z0, z1, mat, side=1, **opts)
    box(b, part, name + "_r", -xb, -xa, y0, y1, z0, z1, mat, side=-1, **opts)


WHEEL_R = 8.0
FRONT_Z = 44.0
DRIVE_Z = (-30.0, -46.0)
WHEEL_X = 18.0
SEAT_X, SEAT_Z = 8.0, 4.0
HITCH_Z = -36
WS_ANGLE = 8.0


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- chasis y mecánica
    box(b, body, "chassis_f", -10, 10, 9, 13, 8, 54, "dark", kind="plain")
    box(b, body, "chassis_m", -10, 10, 9, 13, -26, 8, "dark", kind="plain")
    box(b, body, "chassis_r", -10, 10, 9, 13, -58, -26, "dark", kind="plain")
    box(b, body, "engine_bay", -12, 12, 12, 19, 20, 52, "dark", kind="intake")
    lr(b, body, "tank", 20, 26, 9, 21, -20, -4, "tank", kind="tank")
    lr(b, body, "flap", 14, 22, 3, 9, -62, -61, "dark", kind="plain")
    # quinta rueda: la plataforma donde se apoya el remolque
    box(b, body, "fifth", -14, 14, 13, 15, HITCH_Z - 10, HITCH_Z + 10, "dark", kind="fifth")
    box(b, body, "fifth_slot", -2, 2, 15, 16, HITCH_Z - 8, HITCH_Z + 2, "trim", kind="plain")

    # ---------------------------------------------------------------- ruedas
    for sx, nm in ((1, "l"), (-1, "r")):
        st = b.part(f"steer_{nm}", body, (WHEEL_X * sx, WHEEL_R, FRONT_Z))
        vc.add_wheel(b, st, f"wheel_f{nm}", (0, 0, 0), WHEEL_R, 7, tire_depth=6)
        for k, z in enumerate(DRIVE_Z):
            vc.add_wheel(b, body, f"wheel_r{k}{nm}", (WHEEL_X * sx, WHEEL_R, z), WHEEL_R, 8, tire_depth=6)
    lr(b, body, "fender", 13, 23, 17, 19, 34, 54, "paint", kind="fender")
    lr(b, body, "fender_r", 13, 23, 17, 19, -54, -22, "paint", kind="fender")

    # ---------------------------------------------------------------- cabina: piso, puertas, tablero, asientos
    box(b, body, "cab_floor", -19, 19, 13, 15, -10, 18, "carpet", kind="plain")
    lr(b, body, "door", 18, 20, 15, 46, -8, 16, "paint", kind="door")
    lr(b, body, "step", 20, 24, 10, 11, -4, 14, "dark", kind="plain")
    lr(b, body, "step2", 20, 24, 12, 13, -4, 14, "dark", kind="plain")
    lr(b, body, "handle", 20, 21, 33, 34, 9, 14, "paint", kind="handle")
    box(b, body, "cab_back", -19, 19, 15, 46, -10, -8, "trim", kind="trimwall")
    box(b, body, "dash", -14, 14, 22, 32, 15, 20, "dash", kind="dash")
    box(b, body, "console", -3, 3, 15, 22, -4, 14, "dark", kind="plain")
    box(b, body, "shifter", -1, 1, 22, 28, 8, 10, "dark", kind="plain")
    # cama del camarote (se ve por detrás de los asientos)
    box(b, body, "bunk", -17, 17, 15, 21, -28, -10, "seat", kind="seat")
    box(b, body, "bunk_wall", -19, 19, 15, 52, -30, -28, "trim", kind="trimwall")

    for sx, nm in ((1, "l"), (-1, "r")):
        cx = SEAT_X * sx
        box(b, body, f"seat_{nm}", cx - 5, cx + 5, 15, 17, SEAT_Z - 5, SEAT_Z + 5, "seat", kind="seat")
        box(b, body, f"seat_back_{nm}", cx - 5, cx + 5, 17, 31, SEAT_Z - 7, SEAT_Z - 5, "seat", kind="seatback")
        box(b, body, f"headrest_{nm}", cx - 3, cx + 3, 31, 36, SEAT_Z - 7, SEAT_Z - 5, "seat", kind="seatback")

    tilt = b.part("steering_tilt", body, (SEAT_X, 28.0, 12.0), (-deg(36.0), 0.0, 0.0))
    wheel = b.part("steering_wheel", tilt, (0, 0, 0))
    box(b, wheel, "sw_top", -5, 6, 5, 6, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_bottom", -5, 6, -6, -5, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_left", 5, 6, -5, 5, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_right", -5, -4, -5, 5, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_hub", -2, 3, -1, 2, -1, 1, "dark", kind="swhub")
    box(b, wheel, "sw_spoke", -4, 5, 0, 1, 0, 1, "dark", kind="plain")
    box(b, body, "column", SEAT_X - 1, SEAT_X + 1, 22, 27, 12, 16, "dark", kind="plain")
    # botones del tablero (en SemiTruckEntity.BUTTONS): luces, radio, enganche y rampa
    box(b, body, "btn_light", 1, 3, 28, 30, 14, 15, "lens", kind="plain")
    box(b, body, "btn_radio", -3, -1, 28, 30, 14, 15, "chrome", kind="plain")
    box(b, body, "btn_hitch", -9, -7, 28, 30, 14, 15, "marker", kind="plain")
    box(b, body, "btn_ramp", -6, -4, 28, 30, 14, 15, "btn_blue", kind="plain")        # V7.10.0: rampa de la niñera

    # ---------------------------------------------------------------- capó largo, parrilla, parachoques
    box(b, body, "hood", -15, 15, 19, 37, 18, 52, "paint", kind="hood")
    box(b, body, "hood_ridge", -6, 6, 37, 39, 22, 50, "paint", kind="hood")
    box(b, body, "front_panel", -20, 20, 15, 34, 16, 20, "paint", kind="front_panel")
    box(b, body, "grille", -12, 12, 17, 37, 52, 54, "grille", kind="grille")
    box(b, body, "grille_top", -13, 13, 37, 39, 52, 55, "chrome", kind="grille_top")
    lr(b, body, "head", 15, 21, 22, 30, 46, 52, "lens", kind="head", glow=True)
    box(b, body, "bumper_front", -21, 21, 9, 16, 54, 60, "bumper", kind="bumper")
    box(b, body, "valance", -14, 14, 5, 9, 50, 56, "dark", kind="plain")
    lr(b, body, "fog", 8, 12, 10, 13, 60, 61, "lens", kind="fog")
    box(b, body, "plate", -5, 5, 10, 14, 60, 61, "chrome", kind="plate")

    # ---------------------------------------------------------------- techo, camarote, visera, chimeneas, espejos
    box(b, body, "roof", -20, 20, 46, 48, -10, 20, "paint", kind="roof")
    box(b, body, "visor", -20, 20, 44, 46, 18, 26, "paint", kind="visor")
    box(b, body, "sleeper", -20, 20, 15, 54, -30, -10, "paint", kind="sleeper")
    lr(b, body, "pillar_a", 18, 20, 32, 46, 16, 20, "paint", kind="pillar")
    lr(b, body, "pillar_b", 18, 20, 15, 46, -10, -6, "paint", kind="pillar")
    lr(b, body, "mirror_arm", 20, 24, 36, 37, 14, 15, "dark", kind="plain")
    lr(b, body, "mirror", 24, 26, 26, 40, 11, 17, "mirror", kind="mirror")
    lr(b, body, "stack", 21, 23, 14, 62, -12, -10, "chrome", kind="stack")
    lr(b, body, "stack_cap", 21, 25, 62, 64, -13, -9, "dark", kind="plain")
    for i, x in enumerate((-12, -6, 0, 6, 12)):
        box(b, body, f"cab_light_{i}", x - 1, x + 1, 48, 50, 16, 17, "marker", kind="marker")
    lr(b, body, "tail", 12, 17, 12, 18, -62, -61, "taillight", kind="taillight", glow=True)
    lr(b, body, "side_vent", 20, 21, 36, 51, -27, -13, "dark", kind="plain")

    ws = b.part("ws_frame", body, (0, 32.0, 20.0), (-deg(WS_ANGLE), 0.0, 0.0))
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(ws, f"ws_pillar_{nm}", (19.0 * sx, 7.0, 0.0), (2, 14, 2), "paint", kind="pillar", side=sx)
    b.cube(ws, "ws_top", (0, 14.5, 0.0), (36, 1, 2), "paint", kind="pillar")

    # ---------------------------------------------------------------- vidrios (árbol aparte)
    wg = b.part("ws_glass", glass, (0, 32.0, 20.0), (-deg(WS_ANGLE), 0.0, 0.0))
    b.cube(wg, "windshield", (0, 7.0, 0.0), (36, 14, 1), "glass", kind="glass", translucent=True)
    lr(b, glass, "win_f", 19, 20, 33, 46, -6, 16, "glass", kind="glass", translucent=True)
    lr(b, glass, "win_s", 20, 21, 38, 50, -26, -14, "glass", kind="glass", translucent=True)
    box(b, glass, "rear_glass", -8, 8, 28, 40, -10, -9, "glass", kind="glass", translucent=True)
    return b


def pose(b, wheel_rot=0.0, steer=0.0, glass=True, body=True):
    for nm in b.parts:
        if nm.startswith("wheel_") and "_t" not in nm:
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
    if r >= 7.0:
        return (24, 24, 27, 255)
    if r >= 5.8:
        return (38, 38, 43, 255)
    if r >= 5.0:
        return RIM + (255,)
    if r < 1.9:
        if r < 1.0:
            return (181, 18, 27, 255)
        return RIM_HUB + (255,)
    for k in range(10):
        a0 = math.radians(36 * k + 18)
        d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
        if d * r < 0.9:
            return RIM_SPOKE + (255,)
    return (52, 54, 62, 255)


def glass_faces(c, rng):
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
    return ("east", "west") if side > 0 else ("west", "east")


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

    if kind == "door":
        img = faces[outer]                                                 # se dibuja directo: así el texto se lee bien en los dos costados
        h, w = img.shape[:2]
        vline(img, 0, RED_LO, alpha=0.9)
        vline(img, w - 1, RED_LO, alpha=0.9)
        hline(img, 0, (220, 90, 96), alpha=0.7)
        rect(img, 0, h - 6, w, h - 5, GOLD)                                  # filete dorado
        rect(img, 0, h - 5, w, h, (26, 8, 10))
        draw_text(img, "KW", (w - text_width("KW")) // 2, 5, (238, 238, 240), shadow=(60, 8, 12))
        faces[outer] = img
        trim = faces[inner]
        trim[..., :3] = (64, 66, 74)
        hline(trim, 5, (84, 86, 96), alpha=0.9)
    elif kind == "handle":
        for f in ("west", "east", "top", "bottom", "front", "back"):
            if f == "top":
                top[..., :3] = HANDLE
            else:
                faces[f][..., :3] = HANDLE
    elif kind == "hood":
        for f in ("west", "east"):                                              # costados del capó: rayas doradas
            a = faces[f]
            rect(a, 0, a.shape[0] - 5, a.shape[1], a.shape[0] - 4, GOLD)
            rect(a, 0, a.shape[0] - 3, a.shape[1], a.shape[0] - 2, GOLD)
        a = faces["front"]
        hline(a, 0, (220, 90, 96), alpha=0.6)
        for y in range(top.shape[0]):
            if y % 6 == 3:
                top[y, 1:-1, :3] *= 0.9
        vline(top, 0, (220, 90, 96), alpha=0.6)
        vline(top, top.shape[1] - 1, (220, 90, 96), alpha=0.6)
    elif kind == "front_panel":
        a = faces["front"]
        rows, cols = a.shape[:2]
        rect(a, 0, rows - 4, cols, rows, (26, 8, 10))
        hline(a, rows - 5, GOLD)
        outline_rect(a, RED_LO, alpha=0.6)
        faces["front"] = a
    elif kind == "fender":
        for f in ("west", "east", "front", "back"):
            hline(faces[f], 0, (220, 90, 96), alpha=0.6)
        for y in range(top.shape[0]):
            top[y, :, :3] *= 1.0 - 0.06 * ((y % 4) == 0)
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
        a[..., :3] = (190, 196, 208)                                              # cromo
        for x in range(2, cols - 2):
            if x % 3 != 1:
                a[2:rows - 2, x, :3] = (22, 22, 26)                               # rendijas verticales
        rect(a, 0, 0, cols, 2, (226, 230, 238))
        rect(a, 0, rows - 2, cols, rows, (150, 154, 166))
        rect(a, 0, 0, 2, rows, (226, 230, 238))
        rect(a, cols - 2, 0, cols, rows, (150, 154, 166))
        for ff in ("west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (60, 62, 70)
    elif kind == "grille_top":
        a = faces["front"]
        a[..., :3] = (210, 214, 224)
        draw_text(a, "KW", (a.shape[1] - text_width("KW")) // 2, 0, RED_LO)
        rect(a, 0, a.shape[0] - 1, a.shape[1], a.shape[0], (120, 124, 136))
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
        faces["front"][..., :3] = (210, 216, 226)
    elif kind == "plate":
        a = faces["front"]
        a[..., :3] = (232, 232, 236)
        outline_rect(a, (30, 30, 40))
        draw_text(a, "KW 880", (a.shape[1] - text_width("KW 880")) // 2, 1, (120, 12, 20))
    elif kind == "bumper":
        for f in ("front", "back", "west", "east"):
            a = faces[f]
            hline(a, 0, (240, 244, 250), alpha=0.8)
            hline(a, a.shape[0] - 1, (90, 94, 106), alpha=0.8)
        for y in range(top.shape[0]):
            top[y, :, :3] *= 1.05
    elif kind == "tank":
        for f in ("west", "east"):
            a = faces[f]
            for x in range(a.shape[1]):
                a[:, x, :3] = np.array((196, 200, 212)) * (1 - x / max(1, a.shape[1] - 1) * 0.35)
            for y in range(0, a.shape[0], 5):
                a[y, :, :3] *= 0.8
    elif kind == "stack":
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            for y in range(a.shape[0]):
                a[y, :, :3] = np.array((200, 206, 218)) * (0.82 + 0.18 * math.sin(y * 0.45))
    elif kind in ("roof", "visor"):
        rows, cols = top.shape[:2]
        vline(top, 0, (220, 90, 96), alpha=0.5)
        vline(top, cols - 1, (220, 90, 96), alpha=0.5)
        faces["bottom"][..., :3] = (150, 152, 162)
        for f in ("west", "east", "front", "back"):
            hline(faces[f], faces[f].shape[0] - 1, RED_LO, alpha=0.6)
    elif kind == "sleeper":
        for f in ("west", "east"):
            img = faces[f]
            h, w = img.shape[:2]
            rect(img, 0, h - 8, w, h - 7, GOLD)
            rect(img, 0, h - 6, w, h - 5, GOLD)
            rect(img, 0, h - 4, w, h, (26, 8, 10))
            draw_text(img, "KENWORT", (w - text_width("KENWORT")) // 2, 4, (238, 238, 240), shadow=(60, 8, 12))
            draw_text(img, "K880", (w - text_width("K880")) // 2, h - 22, GOLD, shadow=(60, 8, 12))
            faces[f] = img
        a = faces["back"]
        rows, cols = a.shape[:2]
        rect(a, 0, rows - 4, cols, rows, (26, 8, 10))
        draw_text(a, "KENWORT", (cols - text_width("KENWORT")) // 2, 4, (238, 238, 240), shadow=(60, 8, 12))
        hline(a, rows - 5, GOLD)
        faces["back"] = a
    elif kind == "pillar":
        for f in ("west", "east", "front", "back", "bottom"):
            faces[f][..., :3] = (64, 66, 74)
        img = vc.natural_side(faces, outer)
        img[..., :3] = RED
        noise(img, rng, 0.03)
        vc.store_side(faces, outer, img)
        top[..., :3] = RED
        if c.name.startswith("ws_"):
            for f in ("west", "east"):
                faces[f][..., :3] = RED
    elif kind == "trimwall":
        for f in list(faces):
            faces[f][..., :3] = (64, 66, 74)
    elif kind == "fifth":
        for f in list(faces):
            faces[f][..., :3] = (34, 36, 42)
        rect(top, 0, 0, top.shape[1], 1, (70, 74, 84))
        rect(top, 0, top.shape[0] - 1, top.shape[1], top.shape[0], (70, 74, 84))
    elif kind == "dash":
        rows, cols = top.shape[:2]
        top[..., :3] = (30, 31, 36)
        rect(top, 1, 1, cols - 1, 2, (50, 52, 60))
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (32, 33, 38)
        rect(a, 11, 2, 19, 7, (24, 24, 28))
        rect(a, 12, 3, 18, 6, (44, 104, 176))
        rect(a, 2, 1, 9, 6, (16, 16, 20))
        rect(a, 3, 2, 8, 5, (240, 120, 40))
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
            rect(faces[f], 0, 0, faces[f].shape[1], faces[f].shape[0], (181, 18, 27))

    vc.store_top(faces, top)
    if c.mat == "paint" and kind not in ("pillar", "handle", "taillight", "marker"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.05, 0.10)
    return faces, glow


def build_all():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=7900)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=7900, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
JAVA_TEMPLATE = '''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SemiTruckEntity;
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
 * Tractomula KENWORT K880 (V7.9.0). ARCHIVO GENERADO por tools/v790/semi_truck.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX_W}x{TEX_H}. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios). Piezas animadas: las 6 ruedas (giran), las dos delanteras (doblan) y el volante.
 */
public class SemiTruckModel extends EntityModel<SemiTruckEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "semi_truck"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart[] wheels;
    private final ModelPart steerL;
    private final ModelPart steerR;
    private final ModelPart steeringWheel;

    public SemiTruckModel(ModelPart root) {{
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.steerL = this.body.getChild("steer_l");
        this.steerR = this.body.getChild("steer_r");
        this.wheels = new ModelPart[] {{
                this.steerL.getChild("wheel_fl"), this.steerR.getChild("wheel_fr"),
                this.body.getChild("wheel_r0l"), this.body.getChild("wheel_r0r"),
                this.body.getChild("wheel_r1l"), this.body.getChild("wheel_r1r")}};
        this.steeringWheel = this.body.getChild("steering_tilt").getChild("steering_wheel");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
    }}

    /** wheelRot en radianes (las 6 ruedas), steerRot = giro de las ruedas delanteras en radianes (positivo = a la derecha). */
    public void pose(float wheelRot, float steerRot) {{
        for (ModelPart wheel : this.wheels) {{
            wheel.xRot = wheelRot;
        }}
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
    public void setupAnim(SemiTruckEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
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


def java_model(b):
    return JAVA_TEMPLATE.format(body=vc.java_tree(b), TEX_W=TEX_W, TEX_H=TEX_H)


# ----------------------------------------------------------------------------------------------- main
VIEWS = [(145, 14), (215, 14), (90, 3), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 26.0, -4)


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
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), VIEWS, CENTER, 3.0, light_on=not args.dark, size=(420, 330))
        vc.sheet(imgs).convert("RGB").save(args.preview)
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/semi_truck.png")
        glow.save(ASSETS / "textures/entity/semi_truck_glow.png")
        (JAVA / "client/SemiTruckModel.java").write_text(java_model(b), encoding="utf-8")
        pose(b, 0.0, 0.0)
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 140, 16, CENTER, 4.6, outline=(40, 8, 12, 255), brighten=1.0)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item/semi_truck.png")
        print("escrito: textura, brillo, SemiTruckModel.java, ícono")


if __name__ == "__main__":
    main()
