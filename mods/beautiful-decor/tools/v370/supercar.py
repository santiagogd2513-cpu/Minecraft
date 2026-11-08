#!/usr/bin/env python3
"""Superdeportivo Bugotti Chillón (V3.7.0, parodia): geometría, textura, brillo, SupercarModel.java, ícono y vistas.

Diseño propio (no copia ningún auto real): baja y ancha, nariz en cuña con una sola barra de luces LED de lado a lado y una gran rejilla
rectangular de ranuras, tomas de aire laterales detrás de las puertas, toma de aire sobre el techo (en vez de aleta), tapa del motor
inclinada con respiraderos, alerón alto fijo sobre dos soportes, barra de luz trasera de lado a lado, difusor de fibra de carbono y cuatro escapes.
Uso (desde la raíz del proyecto):
  python3 -m tools.v370.supercar --preview vista.png      # solo mira
  python3 -m tools.v370.supercar --write                  # escribe textura, brillo, modelo Java e ícono
Coordenadas "amigables": x = izquierda (+), y = altura, z = adelante (+); el origen está en el centro, a nivel del suelo (px; 16 px = 1 bloque).
Mismo juego de piezas animadas que la camioneta (4 ruedas, 2 ruedas que doblan, volante) para usar la misma plantilla de modelo.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import (ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline)
from ..v320.paintkit import tone

TEX_W, TEX_H = 384, 256
NAME = "bugotti_chillon"

# Colores de referencia de los tres canales que el jugador puede cambiar con la tecla R: pintura, manijas y rines.
BODY_REF, BODY_HI_REF, BODY_LO_REF = (24, 84, 214), (74, 142, 250), (10, 42, 128)
HANDLE_REF = (40, 42, 50)
RIM_REF, RIM_HUB_REF, RIM_SPOKE_REF = (236, 150, 24), (232, 146, 22), (208, 126, 18)
BODY, BODY_HI, BODY_LO = BODY_REF, BODY_HI_REF, BODY_LO_REF
HANDLE = HANDLE_REF
RIM, RIM_HUB, RIM_SPOKE = RIM_REF, RIM_HUB_REF, RIM_SPOKE_REF
TRIM = (46, 48, 56)
MAT_COLOR = {
    "paint": BODY, "carbon": (26, 27, 32), "chrome": (190, 196, 208), "dark": (20, 21, 25), "carpet": (32, 34, 40), "seat": (38, 40, 46),
    "dash": (24, 25, 30), "trim": TRIM, "tire": (26, 26, 29), "glass": (24, 40, 58), "lens": (172, 184, 202),
    "taillight": (150, 12, 16), "mirror": (24, 24, 28), "intake": (14, 14, 18), "grille": (12, 12, 16),
}
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


WHEEL_R = 5.5
FRONT_Z, REAR_Z = 22.0, -22.0
FRONT_X, REAR_X = 13.0, 12.5
SEAT_X, SEAT_Z = 6.5, -5.0
WS_ANGLE = 54.0
DECK_ANGLE = 15.0


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


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- bajos, splitter y difusor
    box(b, body, "ub_center", -9, 9, 3, 5, -33, 31, "carbon", kind="carbon")
    box(b, body, "ub_mid", -14, 14, 3, 5, -16, 16, "carbon", kind="carbon")
    box(b, body, "splitter", -15, 15, 2, 4, 31, 38, "carbon", kind="carbon")
    box(b, body, "diffuser", -12, 12, 3, 8, -37, -32, "carbon", kind="diffuser")
    lr(b, body, "skirt", 14, 16, 4, 6, -16, 16, "carbon", kind="carbon")

    # ---------------------------------------------------------------- ruedas
    for sx, nm in ((1, "l"), (-1, "r")):
        vc.add_wheel(b, body, f"wheel_r{nm}", (REAR_X * sx, WHEEL_R, REAR_Z), WHEEL_R, 7, tire_depth=4)
        st = b.part(f"steer_{nm}", body, (FRONT_X * sx, WHEEL_R, FRONT_Z))
        vc.add_wheel(b, st, f"wheel_f{nm}", (0, 0, 0), WHEEL_R, 6, tire_depth=4)
    lr(b, body, "well_f", 8, 10, 4, 15, 16, 29, "dark", kind="plain")
    lr(b, body, "well_r", 7, 9, 4, 17, -29, -16, "dark", kind="plain")

    # ---------------------------------------------------------------- nariz en cuña, capó y guardabarros delanteros
    box(b, body, "nose", -14, 14, 4, 11, 28, 36, "paint", kind="nose")
    box(b, body, "lip", -12, 12, 4, 8, 36, 38, "paint", kind="lip")
    box(b, body, "hood_a", -13, 13, 10, 13, 8, 18, "paint", kind="hood")
    box(b, body, "hood_b", -13, 13, 10, 12, 18, 28, "paint", kind="hood")
    lr(b, body, "fender_f", 11, 16, 11, 15, 16, 29, "paint", kind="fender")
    lr(b, body, "quarter_f", 14, 16, 5, 11, 28, 34, "paint", kind="panel")
    box(b, body, "lamp_bar", -12, 12, 8, 10, 36, 37, "lens", kind="lampbar", glow=True)
    lr(b, body, "drl", 12, 14, 6, 10, 36, 37, "lens", kind="lampbar", glow=True)
    box(b, body, "front_intake", -9, 9, 4, 8, 38, 39, "grille", kind="grille")
    lr(b, body, "corner_intake", 9, 13, 4, 7, 37, 38, "intake", kind="intake")

    # ---------------------------------------------------------------- puertas, tomas laterales, espejos y manijas
    lr(b, body, "door", 14, 16, 6, 15, -10, 11, "paint", kind="door")
    lr(b, body, "cowl_f", 14, 16, 6, 13, 11, 16, "paint", kind="panel")
    lr(b, body, "cowl_r", 14, 16, 6, 13, -16, -10, "paint", kind="panel")
    lr(b, body, "shoulder", 12, 16, 14, 15, -12, 8, "paint", kind="panel")
    lr(b, body, "side_intake", 16, 17, 8, 13, -15, -10, "intake", kind="intake")
    lr(b, body, "handle", 16, 17, 11, 12, -4, 1, "paint", kind="handle")
    lr(b, body, "mirror_arm", 16, 18, 15, 16, 3, 5, "dark", kind="plain")
    lr(b, body, "mirror", 17, 20, 15, 18, 2, 6, "mirror", kind="mirror")

    # ---------------------------------------------------------------- cabina: suelo, paredes, asientos, tablero y volante
    box(b, body, "cab_floor", -12, 12, 4, 5, -14, 8, "carpet", kind="plain")
    lr(b, body, "tub", 12, 14, 5, 14, -14, 8, "trim", kind="trimwall")
    box(b, body, "cab_back", -13, 13, 5, 20, -14, -12, "trim", kind="trimwall")
    box(b, body, "dash", -12, 12, 7, 11, 4, 8, "dash", kind="dash")
    box(b, body, "console", -2, 2, 5, 9, -9, 4, "dark", kind="plain")
    for sx, nm in ((1, "l"), (-1, "r")):
        cx = SEAT_X * sx
        box(b, body, f"seat_{nm}", cx - 4, cx + 4, 5, 6, SEAT_Z - 4, SEAT_Z + 4, "seat", kind="seat")
        box(b, body, f"seat_back_{nm}", cx - 4, cx + 4, 6, 15, SEAT_Z - 6, SEAT_Z - 4, "seat", kind="seatback")
        box(b, body, f"headrest_{nm}", cx - 2, cx + 2, 15, 19, SEAT_Z - 6, SEAT_Z - 4, "seat", kind="seatback")
        box(b, body, f"bolster_{nm}", cx - 5 if sx > 0 else cx + 4, cx - 4 if sx > 0 else cx + 5, 6, 10, SEAT_Z - 4, SEAT_Z + 3, "seat", kind="plain")
    tilt = b.part("steering_tilt", body, (SEAT_X, 11.5, 3.0), (-deg(30.0), 0.0, 0.0))
    wheel = b.part("steering_wheel", tilt, (0, 0, 0))
    box(b, wheel, "sw_top", -3, 3, 3, 4, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_bottom", -2, 2, -3, -2, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_left", 3, 4, -3, 3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_right", -4, -3, -3, 3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_hub", -1, 2, -1, 2, -1, 1, "dark", kind="swhub")
    box(b, wheel, "sw_spoke", -3, 4, 0, 1, 0, 1, "dark", kind="plain")
    box(b, body, "column", SEAT_X - 1, SEAT_X + 1, 8, 10, 3, 6, "dark", kind="plain")

    # ---------------------------------------------------------------- techo, parabrisas, pilares y toma de aire del techo
    box(b, body, "roof", -13, 13, 21, 22, -12, -2, "paint", kind="roof")
    box(b, body, "scoop", -3, 3, 22, 25, -10, -5, "paint", kind="scoop")
    box(b, body, "scoop_mouth", -3, 3, 22, 25, -5, -4, "intake", kind="intake")
    lr(b, body, "pillar_c", 12, 14, 15, 21, -12, -10, "paint", kind="pillar")
    ws = b.part("ws_frame", body, (0, 13.0, 8.0), (-deg(WS_ANGLE), 0.0, 0.0))
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(ws, f"a_pillar_{nm}", (12.0 * sx, 7.0, 0.0), (2, 14, 2), "paint", kind="pillar", side=sx)

    # ---------------------------------------------------------------- parte de atrás: haunches, tapa del motor, cola, alerón
    lr(b, body, "haunch", 11, 16, 11, 18, -29, -15, "paint", kind="fender")
    lr(b, body, "quarter_r", 14, 16, 6, 14, -36, -29, "paint", kind="panel")
    box(b, body, "tail", -14, 14, 6, 14, -36, -28, "paint", kind="tail")
    box(b, body, "tail_bar", -13, 13, 10, 12, -37, -36, "taillight", kind="tailbar", glow=True)
    for x0, x1 in ((4, 7), (8, 11)):
        box(b, body, f"exhaust_l{x0}", x0, x1, 6, 9, -40, -36, "chrome", kind="exhaust")
        box(b, body, f"exhaust_r{x0}", -x1, -x0, 6, 9, -40, -36, "chrome", kind="exhaust")
    deck = b.part("deck", body, (0, 20.0, -12.0), (-deg(DECK_ANGLE), 0.0, 0.0))
    b.cube(deck, "deck_cover", (0, 0, -12.5), (24, 2, 25), "paint", kind="deck")
    b.cube(deck, "deck_vent_l", (6.5, 1.5, -9.0), (5, 1, 9), "intake", kind="intake")
    b.cube(deck, "deck_vent_r", (-6.5, 1.5, -9.0), (5, 1, 9), "intake", kind="intake")
    lr(b, body, "pylon", 8, 10, 14, 20, -34, -32, "carbon", kind="carbon")
    box(b, body, "wing", -16, 16, 20, 22, -38, -29, "carbon", kind="wing")
    lr(b, body, "endplate", 15, 16, 18, 24, -38, -28, "paint", kind="endplate")

    # ---------------------------------------------------------------- vidrios (árbol aparte)
    wg = b.part("ws_glass", glass, (0, 13.0, 8.0), (-deg(WS_ANGLE), 0.0, 0.0))
    b.cube(wg, "windshield", (0, 7.0, 0.0), (22, 14, 1), "glass", kind="glass", translucent=True)
    lr(b, glass, "side_win", 13, 14, 15, 21, -10, 3, "glass", kind="glass", translucent=True)
    return b


def pose(b, wheel_rot=0.0, steer=0.0, glass=True, body=True):
    """Pose del modelo (mismo cálculo que SupercarModel.pose)."""
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
    if r >= 5.2:
        return (24, 24, 27, 255)                           # banda de rodadura
    if r >= 4.1:
        return (38, 39, 44, 255)                           # costado del neumático
    if r >= 3.4:
        return RIM + (255,)                                # labio del rin
    if r < 1.3:
        return ((196, 28, 34, 255) if r < 0.8 else RIM_HUB + (255,))   # insignia roja en el centro
    for k in range(5):                                     # 5 rayos
        a0 = math.radians(72 * k + 18)
        d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
        if d * r < 0.95:
            return RIM_SPOKE + (255,)
    cal = abs(math.atan2(math.sin(ang - math.radians(54)), math.cos(ang - math.radians(54))))
    if cal * r < 1.35 and 2.0 < r < 3.3:
        return (206, 26, 32, 255)                          # pinza de freno roja
    return (96, 100, 108, 255) if r > 1.6 else (60, 62, 70, 255)     # disco de freno


def glass_faces(c, rng):
    """Vidrio ahumado azulado translúcido con un par de destellos diagonales."""
    faces = {}
    for f, (rows, cols) in vc.face_sizes(c).items():
        a = solid(rows, cols, (28, 46, 68), 112)
        if f in ("west", "east", "front", "back") and rows >= 3 and cols >= 3:
            for x in range(cols):
                for k in range(0, rows + cols, 8):
                    y = k - x
                    if 0 <= y < rows and (k // 8) % 2 == 0:
                        a[y, x, :3] = (130, 156, 186)
                        a[y, x, 3] = 150
        faces[f] = a
    return faces, {}


def side_names(c):
    side = c.opts.get("side", 1)
    return ("east", "west") if side > 0 else ("west", "east")      # (exterior, interior)


def weave(a, c1, c2, step=2):
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            a[y, x, :3] = c1 if ((x // step) + (y // step)) % 2 == 0 else c2


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
    LO = BODY_LO

    if kind in ("hood",):
        rows, cols = top.shape[:2]
        vline(top, cols // 2, BODY_LO, alpha=0.8)
        vline(top, 3, BODY_HI, alpha=0.35)
        vline(top, cols - 4, BODY_HI, alpha=0.35)
        for x in (cols // 2 - 4, cols // 2 + 3):                 # dos respiraderos negros
            rect(top, x, rows // 2 - 2, x + 1, rows // 2 + 2, (14, 14, 18))
    elif kind in ("fender", "panel", "nose", "lip", "tail", "deck", "endplate", "scoop"):
        a = faces["front"]
        hline(a, a.shape[0] - 1, LO, alpha=0.7)
        if kind == "fender":
            vline(top, 0, BODY_HI, alpha=0.4)
            vline(top, top.shape[1] - 1, BODY_HI, alpha=0.4)
            img = vc.natural_side(faces, outer)
            hline(img, img.shape[0] - 1, LO, alpha=0.8)
            vc.store_side(faces, outer, img)
        if kind == "nose":
            for f in ("front",):
                hline(faces[f], 0, BODY_HI, alpha=0.5)
            rect(faces["front"], 2, faces["front"].shape[0] - 3, faces["front"].shape[1] - 2, faces["front"].shape[0] - 1, (22, 24, 30))
        if kind == "tail":
            a = faces["back"]
            rows, cols = a.shape[:2]
            rect(a, 0, rows - 3, cols, rows, (22, 24, 30))                    # faldón inferior oscuro
            hline(a, 0, BODY_HI, alpha=0.4)
            rect(a, cols // 2 - 4, 1, cols // 2 + 4, 4, (206, 210, 220))      # placa-insignia plateada
            rect(a, cols // 2 - 3, 2, cols // 2 + 3, 3, (24, 84, 214))
        if kind == "deck":
            vline(top, top.shape[1] // 2, LO, alpha=0.7)
            hline(top, 0, BODY_HI, alpha=0.4)
        if kind == "endplate":
            img = vc.natural_side(faces, outer)
            rect(img, 1, 1, img.shape[1] - 1, 3, (236, 150, 24))            # franja naranja
            vc.store_side(faces, outer, img)
    elif kind == "door":
        img = vc.natural_side(faces, outer)
        h, w = img.shape[:2]
        vline(img, 0, LO, alpha=0.9)
        vline(img, w - 1, LO, alpha=0.9)
        hline(img, 0, BODY_HI, alpha=0.55)
        hline(img, h - 3, BODY_HI, alpha=0.3)
        rect(img, 0, h - 2, w, h, (24, 26, 32))
        rect(img, 3, 3, w - 3, 4, (236, 150, 24))                         # filete naranja que baja por la puerta
        vc.store_side(faces, outer, img)
        trim = vc.natural_side(faces, inner)
        trim[..., :3] = TRIM
        vc.store_side(faces, inner, trim)
    elif kind == "handle":
        for f in ("west", "east", "front", "back", "bottom"):
            faces[f][..., :3] = HANDLE
        top[..., :3] = HANDLE
    elif kind == "roof":
        rows, cols = top.shape[:2]
        vline(top, 0, BODY_HI, alpha=0.4)
        vline(top, cols - 1, BODY_HI, alpha=0.4)
        faces["bottom"][..., :3] = (60, 62, 72)
        rect(top, cols // 2 - 1, 0, cols // 2 + 1, rows, (236, 150, 24))  # franja naranja de lado a lado del techo
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
    elif kind == "carbon":
        for f in list(faces):
            weave(faces[f], (24, 25, 29), (38, 39, 45))
        weave(top, (24, 25, 29), (38, 39, 45))
    elif kind == "diffuser":
        weave(top, (24, 25, 29), (38, 39, 45))
        a = faces["back"]
        for x in range(a.shape[1]):
            a[:, x, :3] = (16, 17, 20) if x % 3 == 0 else (30, 31, 36)    # aletas verticales
    elif kind == "wing":
        weave(top, (24, 25, 29), (40, 41, 48))
        weave(faces["bottom"], (20, 21, 25), (30, 31, 36))
        a = faces["front"]
        hline(a, 0, (236, 150, 24), alpha=0.9)
    elif kind == "intake" or kind == "grille":
        for f in ("front", "back", "top", "west", "east", "bottom"):
            a = faces[f]
            for y in range(a.shape[0]):
                for x in range(a.shape[1]):
                    a[y, x, :3] = (26, 28, 34) if (x + y) % 2 == 0 else (12, 12, 16)
        if kind == "grille":
            a = faces["front"]
            for y in range(a.shape[0]):
                a[y, :, :3] = (30, 32, 38) if y % 2 == 0 else (10, 10, 13)
            hline(a, 0, (236, 150, 24), alpha=0.95)
    elif kind == "lampbar":
        a = faces["front"]
        rows, cols = a.shape[:2]
        a[..., :3] = (232, 240, 252)
        outline_rect(a, (28, 30, 36))
        g = solid(rows, cols, (0, 0, 0), 0)
        rect(g, 0, 0, cols, rows, (255, 252, 236), 255)
        glow["front"] = g
        for ff in ("west", "east", "top", "bottom", "back"):
            faces[ff][..., :3] = (36, 38, 44)
    elif kind == "tailbar":
        for ff in ("west", "east", "top", "bottom", "front"):
            faces[ff][..., :3] = (96, 8, 12)
        a = faces["back"]
        a[..., :3] = (214, 24, 30)
        for x in range(4, a.shape[1], 6):
            a[:, x, :3] = (40, 6, 8)                                       # separadores negros
        g = solid(a.shape[0], a.shape[1], (255, 40, 30), 255)
        for x in range(4, a.shape[1], 6):
            g[:, x, 3] = 0
        glow["back"] = g
        glow["west"] = solid(*faces["west"].shape[:2], (255, 40, 30), 255)
        glow["east"] = solid(*faces["east"].shape[:2], (255, 40, 30), 255)
    elif kind == "exhaust":
        for f in ("west", "east", "top", "bottom"):
            a = faces[f]
            for x in range(a.shape[1]):
                a[:, x, :3] = np.array((130, 134, 144)) * (1 - x / max(1, a.shape[1] - 1) * 0.45)
        a = faces["back"]
        a[..., :3] = (190, 196, 208)
        rect(a, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (10, 10, 12))
    elif kind == "dash":
        rows, cols = top.shape[:2]
        top[..., :3] = (26, 27, 32)
        rect(top, 1, 1, cols - 1, 2, (46, 48, 56))
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (30, 31, 36)
        rect(a, 9, 1, 15, 4, (24, 24, 28))
        rect(a, 10, 2, 14, 3, (44, 150, 220))                              # pantalla central
        rect(a, 2, 1, 7, 3, (16, 16, 20))
        rect(a, 3, 2, 6, 3, (240, 120, 40))                                # tablero del conductor
        hline(a, h - 1, (14, 14, 18), alpha=0.8)
    elif kind == "seat":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x + y) % 2 == 0:
                    top[y, x, :3] *= 1.12
        rect(top, 0, 0, 1, top.shape[0], (236, 150, 24))                   # costura naranja
        outline_rect(top, (40, 42, 50), alpha=0.8)
    elif kind == "seatback":
        for f in ("front", "back"):
            a = faces[f]
            for y in range(a.shape[0]):
                for x in range(a.shape[1]):
                    if (x + y) % 2 == 0:
                        a[y, x, :3] *= 1.12
            outline_rect(a, (40, 42, 50), alpha=0.8)
        vline(faces["back"], faces["back"].shape[1] // 2, (236, 150, 24), alpha=0.9)
    elif kind == "mirror":
        for f in ("west", "east", "top", "bottom", "front"):
            faces[f][..., :3] = (22, 22, 26)
        a = faces["back"]
        a[..., :3] = (22, 22, 26)
        rect(a, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (128, 140, 156))
    elif kind == "swhub":
        for f in ("front", "back"):
            rect(faces[f], 0, 0, faces[f].shape[1], faces[f].shape[0], (206, 26, 32))
    elif kind == "trimwall":
        for f in list(faces):
            faces[f][..., :3] = TRIM

    vc.store_top(faces, top)
    if c.mat in ("paint",) and kind not in ("pillar", "handle", "tail"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.06, 0.12)
    return faces, glow


def build_all():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3700)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3700, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    """Misma plantilla que PickupModel (tools/v210/truck.py): mismas piezas animadas y misma API de pose."""
    from ..v210 import truck as pickup_tool
    src = pickup_tool.java_model(b)
    src = src.replace("PickupModel", "SupercarModel").replace("PickupEntity", "SupercarEntity")
    src = src.replace('MOD_ID, "ford_f150"', f'MOD_ID, "{NAME}"')
    src = src.replace("Camioneta Furd F-150 (V2.1.0). ARCHIVO GENERADO por tools/v210/truck.py",
                      "Superdeportivo Bugotti Chillón (V3.7.0). ARCHIVO GENERADO por tools/v370/supercar.py")
    src = src.replace("LayerDefinition.create(mesh, 256, 256)", f"LayerDefinition.create(mesh, {TEX_W}, {TEX_H})")
    src = src.replace("Atlas de textura de 256x256", f"Atlas de textura de {TEX_W}x{TEX_H}")
    assert "ford_f150" not in src and "Pickup" not in src
    return src


# ----------------------------------------------------------------------------------------------- main
VIEWS = [(145, 14), (215, 14), (90, 3), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 12.0, 0)


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
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), VIEWS, CENTER, 4.6, light_on=not args.dark, size=(420, 330))
        vc.sheet(imgs).convert("RGB").save(args.preview)
        big = tex.resize((TEX_W * 2, TEX_H * 2), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / f"textures/entity/{NAME}.png")
        glow.save(ASSETS / f"textures/entity/{NAME}_glow.png")
        pose(b, 0.0, 0.0)               # el modelo Java se escribe en reposo (la pose real la pone el renderizador en cada fotograma)
        (JAVA / "client/SupercarModel.java").write_text(java_model(b), encoding="utf-8")
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 140, 16, CENTER, 7.5, outline=(8, 12, 30, 255), brighten=1.1)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / f"textures/item/{NAME}.png")
        print("escrito: textura, brillo, SupercarModel.java, ícono")


if __name__ == "__main__":
    main()
