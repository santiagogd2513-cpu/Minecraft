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

TEX_W, TEX_H = 384, 320     # V6.14.0: 64 filas más para los accesorios de las skins
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
    "paint": BODY, "carbon": (26, 27, 32), "chrome": (190, 196, 208), "dark": (20, 21, 25), "carpet": (32, 34, 40), "seat": (52, 54, 64),
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


# ---- V6.14.0: skins (colores fijos: no se repintan con la tecla R). 0 = original, 1 = carreras, 2 = policía, 3 = callejero, 4 = taxi
SKIN = 0
ACCENT = (236, 150, 24)
SKIN_DEFS = {
    1: ("racing", (206, 28, 34), (240, 84, 76), (120, 12, 16), (245, 245, 248), (232, 232, 238)),
    2: ("police", (236, 238, 244), (252, 253, 255), (168, 174, 190), (16, 30, 84), (200, 204, 214)),
    3: ("street", (30, 30, 36), (60, 60, 72), (12, 12, 16), (40, 255, 130), (40, 255, 130)),
    4: ("taxi", (250, 198, 24), (255, 228, 96), (176, 128, 8), (20, 20, 24), (210, 214, 224)),
}


def set_skin(n):
    """Pone la paleta de la skin n (0 = la original, repintable)."""
    global SKIN, ACCENT, BODY, BODY_HI, BODY_LO, RIM, RIM_HUB, RIM_SPOKE
    SKIN = n
    if n == 0:
        ACCENT = (236, 150, 24)
        set_paint()
        return
    _, BODY, BODY_HI, BODY_LO, ACCENT, rim = SKIN_DEFS[n]
    RIM = rim
    RIM_HUB = tuple(max(0, v - 8) for v in rim)
    RIM_SPOKE = tuple(max(0, v - 28) for v in rim)
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
    # V6.12.0: interior deportivo: pedales, cuadro de instrumentos, palanca, asientos tipo baquet con cinturón, paneles de puerta y arco de seguridad
    box(b, body, "pedal_brake", 5, 7, 5, 8, 3, 4, "dark", kind="pedal")
    box(b, body, "pedal_gas", 8, 9, 5, 9, 3, 4, "dark", kind="pedal")
    box(b, body, "footrest", 10, 12, 5, 7, 2, 5, "dark", kind="pedal")
    box(b, body, "binnacle", 2, 11, 11, 13, 4, 7, "dash", kind="binnacle")
    box(b, body, "console_top", -2, 2, 9, 10, -9, 1, "dash", kind="ctrlpanel")
    box(b, body, "shifter_boot", -2, 2, 10, 11, -4, 0, "dark", kind="boot")
    box(b, body, "shifter_stem", -1, 1, 11, 13, -3, -1, "dark", kind="plain")
    box(b, body, "shifter_knob", -1, 1, 13, 14, -3, -1, "chrome", kind="knob")
    box(b, body, "handbrake", -2, -1, 10, 11, -9, -5, "dark", kind="boot")
    box(b, body, "paddle_l", 10, 11, 11, 14, 1, 2, "chrome", kind="paddle")
    box(b, body, "paddle_r", 2, 3, 11, 14, 1, 2, "chrome", kind="paddle")
    for sx, nm in ((1, "l"), (-1, "r")):
        cx = SEAT_X * sx
        ox = cx + 4 * sx      # lado de afuera del asiento
        box(b, body, f"seat_wing_{nm}", min(ox, ox + sx), max(ox, ox + sx), 6, 10, SEAT_Z - 4, SEAT_Z + 4, "seat", kind="seatwing")
        box(b, body, f"back_wing_{nm}", min(ox, ox + sx), max(ox, ox + sx), 8, 14, SEAT_Z - 6, SEAT_Z - 2, "seat", kind="seatwing")
        box(b, body, f"seat_front_{nm}", cx - 4, cx + 4, 5, 7, SEAT_Z + 4, SEAT_Z + 5, "seat", kind="seatwing")
        belt = b.part(f"belt_{nm}", body, (cx + 3.0 * sx, 17.5, SEAT_Z - 3.5), (0.0, 0.0, deg(32.0) * sx))
        b.cube(belt, f"belt_strap_{nm}", (0.0, -5.5, 0.0), (2, 11, 1), "dark", kind="belt", side=sx)
        box(b, body, f"buckle_{nm}", cx - 4 * sx - (1 if sx > 0 else 0), cx - 4 * sx + (0 if sx > 0 else 1), 6, 8, SEAT_Z - 1, SEAT_Z, "chrome", kind="plain")
    lr(b, body, "armrest", 11, 12, 9, 11, -7, 3, "trim", kind="armrest")
    lr(b, body, "inner_handle", 11, 12, 11, 13, -2, 2, "chrome", kind="plain")
    lr(b, body, "hoop_post", 7, 9, 14, 21, -13, -11, "carbon", kind="carbon")
    box(b, body, "hoop_bar", -9, 9, 19, 21, -13, -11, "carbon", kind="carbon")
    box(b, body, "rv_mirror", -3, 3, 17, 19, -3, -1, "mirror", kind="mirror")
    box(b, body, "rv_stem", -1, 1, 19, 21, -3, -2, "dark", kind="plain")
    tilt = b.part("steering_tilt", body, (SEAT_X, 11.5, 3.0), (-deg(30.0), 0.0, 0.0))
    wheel = b.part("steering_wheel", tilt, (0, 0, 0))
    box(b, wheel, "sw_top", -3, 3, 3, 4, -1, 1, "dark", kind="swtop")
    box(b, wheel, "sw_bottom", -2, 2, -3, -2, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_left", 3, 4, -3, 3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_right", -4, -3, -3, 3, -1, 1, "dark", kind="plain")
    box(b, wheel, "sw_hub", -1, 2, -1, 2, -1, 1, "dark", kind="swhub")
    box(b, wheel, "sw_spoke", -3, 4, 0, 1, 0, 1, "dark", kind="plain")
    box(b, body, "column", SEAT_X - 1, SEAT_X + 1, 8, 10, 3, 6, "dark", kind="plain")
    # V6.5.0: botones del tablero (en SupercarEntity.BUTTONS): luces a la derecha del centro y radio a la izquierda; el claxon es el centro del volante
    box(b, body, "btn_light", 1, 3, 8, 10, 3, 4, "lens", kind="plain")
    box(b, body, "btn_radio", -3, -1, 8, 10, 3, 4, "chrome", kind="plain")

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

    # ---------------------------------------------------------------- V6.14.0: accesorios de las skins (se muestran según SupercarModel.setSkin; material 'tire' para que el repintado no los toque)
    sirenbtn = b.part("siren_btn", body, (0, 0, 0))
    box(b, sirenbtn, "btn_siren", -6, -4, 8, 10, 3, 4, "tire", kind="sirenbtn")            # botón de la sirena en el tablero (SupercarEntity.BUTTONS)
    bar = b.part("bar", body, (0, 0, 0))                                                  # barra de luces del techo (policía)
    box(b, bar, "bar_base", -9, 9, 22, 23, -4, -2, "tire", kind="plain")
    box(b, bar, "bar_mid", -1, 1, 23, 25, -4, -2, "tire", kind="plain")
    bar_red = b.part("bar_red", body, (0, 0, 0))
    box(b, bar_red, "bar_red_lamp", 1, 9, 23, 25, -4, -2, "tire", kind="lightbar_r", glow=True)
    bar_blue = b.part("bar_blue", body, (0, 0, 0))
    box(b, bar_blue, "bar_blue_lamp", -9, -1, 23, 25, -4, -2, "tire", kind="lightbar_b", glow=True)
    racing = b.part("racing", body, (0, 0, 0))                                            # aletas del frente y alerón extra de carreras
    lr(b, racing, "canard", 13, 17, 5, 6, 35, 38, "tire", kind="plain")
    box(b, racing, "rear_lip", -13, 13, 14, 15, -38, -37, "tire", kind="plain")
    cop = b.part("cop", body, (0, 0, 0))                                                  # defensa de empuje de la policía
    box(b, cop, "push_bar", -8, 8, 4, 6, 39, 40, "tire", kind="plain")
    lr(b, cop, "push_post", 7, 8, 4, 8, 38, 39, "tire", kind="plain")
    street = b.part("street", body, (0, 0, 0))                                            # luces de neón bajo la carrocería
    lr(b, street, "neon_side", 14, 16, 3, 4, -16, 16, "tire", kind="neon", glow=True)
    box(b, street, "neon_front", -10, 10, 2, 3, 38, 39, "tire", kind="neon", glow=True)
    taxi = b.part("taxi", body, (0, 0, 0))                                                # letrero luminoso del techo
    box(b, taxi, "taxi_sign", -5, 5, 22, 25, -4, -2, "tire", kind="taxisign", glow=True)
    return b


def pose(b, wheel_rot=0.0, steer=0.0, glass=True, body=True):
    """Pose del modelo (mismo cálculo que SupercarModel.pose)."""
    for nm in ("racing", "cop", "street", "taxi", "bar", "bar_red", "bar_blue", "siren_btn"):
        b.parts[nm].visible = False                       # accesorios de skins: ocultos en la vista del original
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


def natural_flip(a):
    return a


def rtext(img, face, s_, x, y, color, scale=1):
    """Texto legible desde fuera: en la cara 'east' la imagen se ve al revés, así que se escribe volteada."""
    if face == "east":
        tmp = img[:, ::-1].copy()
        vc.text(tmp, s_, x, y, color, scale=scale)
        img[:] = tmp[:, ::-1]
    else:
        vc.text(img, s_, x, y, color, scale=scale)


def decal_door(img, face):
    """V6.14.0: rotulado de la puerta según la skin (la imagen se ve con el frente a la derecha). La fila de arriba queda tapada por el panel del hombro: se empieza en la fila 1."""
    h, w = img.shape[:2]
    if SKIN == 1:                                                      # carreras: círculo blanco con el 7
        cx, cy = w // 2, 4
        for y in range(h):
            for x in range(w):
                if (x - cx) ** 2 + (y - cy) ** 2 <= 10:
                    img[y, x, :3] = (245, 245, 248)
        rtext(img, face, "7", cx - 1, cy - 2, (206, 28, 34))
        rect(img, 0, h - 2, w, h - 1, (245, 245, 248))
    elif SKIN == 2:                                                    # policía: franja azul marino con dorado y PN
        rect(img, 0, 6, w, 9, (16, 30, 84))
        hline(img, 6, (255, 205, 50), alpha=1.0)
        rtext(img, face, "PN", 8, 1, (16, 30, 84))
    elif SKIN == 3:                                                    # callejero: franja de neón y el nombre
        rect(img, 0, 7, w, 8, (40, 255, 130))
        rtext(img, face, "NITRO", 1, 1, (40, 255, 130))
    elif SKIN == 4:                                                    # taxi: cuadros blanco y negro y el rótulo
        for x in range(w):
            for y in range(6, 9):
                img[y, x, :3] = (20, 20, 24) if ((x // 2) + y) % 2 == 0 else (245, 245, 248)
        rtext(img, face, "TAXI", 3, 1, (20, 20, 24))


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
        if SKIN == 1:                                            # carreras: franjas blancas a lo largo
            rect(top, cols // 2 - 3, 0, cols // 2 - 1, rows, ACCENT)
            rect(top, cols // 2 + 1, 0, cols // 2 + 3, rows, ACCENT)
        elif SKIN == 3:                                          # callejero: dos líneas de neón
            vline(top, 2, ACCENT, alpha=1.0)
            vline(top, cols - 3, ACCENT, alpha=1.0)
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
            rect(img, 1, 1, img.shape[1] - 1, 3, ACCENT)            # franja naranja
            vc.store_side(faces, outer, img)
    elif kind == "door":
        img = vc.natural_side(faces, outer)
        h, w = img.shape[:2]
        vline(img, 0, LO, alpha=0.9)
        vline(img, w - 1, LO, alpha=0.9)
        hline(img, 0, BODY_HI, alpha=0.55)
        hline(img, h - 3, BODY_HI, alpha=0.3)
        rect(img, 0, h - 2, w, h, (24, 26, 32))
        if SKIN == 0:
            rect(img, 3, 3, w - 3, 4, ACCENT)                     # filete naranja que baja por la puerta
        else:
            decal_door(img, outer)
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
        rect(top, cols // 2 - 1, 0, cols // 2 + 1, rows, ACCENT)  # franja naranja de lado a lado del techo
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
        hline(a, 0, ACCENT, alpha=0.9)
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
            hline(a, 0, ACCENT, alpha=0.95)
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
        rect(a, 8, 0, 16, 3, (12, 12, 16))                                 # marco de la pantalla central
        rect(a, 9, 1, 15, 2, (44, 150, 220))
        rect(a, 10, 1, 12, 2, (120, 220, 255))
        rect(a, 13, 1, 14, 2, (250, 200, 60))
        for x0 in (2, 5, 17, 20):                                          # rejillas de ventilación
            rect(a, x0, 1, x0 + 2, 2, (10, 10, 13))
            rect(a, x0, 2, x0 + 2, 3, (46, 48, 56))
        rect(a, 6, 3, 8, 4, ACCENT)                                # botones
        rect(a, 16, 3, 18, 4, ACCENT)
        hline(a, h - 1, (14, 14, 18), alpha=0.8)
    elif kind == "seat":
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x + y) % 2 == 0:
                    top[y, x, :3] *= 1.12
        rect(top, 0, 0, 1, top.shape[0], ACCENT)                   # costura naranja
        outline_rect(top, (40, 42, 50), alpha=0.8)
    elif kind == "seatback":
        for f in ("front", "back"):
            a = faces[f]
            for y in range(a.shape[0]):
                for x in range(a.shape[1]):
                    if (x + y) % 2 == 0:
                        a[y, x, :3] *= 1.12
            outline_rect(a, (40, 42, 50), alpha=0.8)
        vline(faces["back"], faces["back"].shape[1] // 2, ACCENT, alpha=0.9)
    elif kind == "mirror":
        for f in ("west", "east", "top", "bottom", "front"):
            faces[f][..., :3] = (22, 22, 26)
        a = faces["back"]
        a[..., :3] = (22, 22, 26)
        rect(a, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (128, 140, 156))
    elif kind == "swhub":
        for f in ("front", "back"):
            rect(faces[f], 0, 0, faces[f].shape[1], faces[f].shape[0], (206, 26, 32))
    elif kind == "pedal":
        for f in ("back", "top"):
            a = faces[f]
            for y in range(a.shape[0]):
                a[y, :, :3] = (150, 154, 164) if y % 2 == 0 else (60, 62, 70)      # pedal de aluminio con estrías
        top = faces["top"].copy()
        top = natural_flip(top)
    elif kind == "binnacle":
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (14, 14, 18)
        rect(a, 0, 0, 2, 2, (30, 30, 36)); rect(a, 0, 0, 1, 1, (236, 240, 250))   # reloj izquierdo (velocímetro)
        rect(a, w - 2, 0, w, 2, (30, 30, 36)); rect(a, w - 1, 0, w, 1, (236, 240, 250))
        rect(a, 3, 0, w - 3, 2, (8, 40, 70))                                       # pantalla digital central
        rect(a, 4, 0, 6, 1, (90, 220, 255))
        rect(a, w - 6, 1, w - 4, 2, (250, 200, 60))
        top[..., :3] = (22, 23, 28)
    elif kind == "ctrlpanel":
        rows, cols = top.shape[:2]
        top[..., :3] = (22, 23, 28)
        for k in range(rows - 1):                                                  # botones en dos filas
            if k % 2 == 0:
                for x in (0, 2, 3):
                    if x < cols:
                        top[k, x, :3] = ACCENT if (k // 2) % 3 == 0 else (150, 154, 164)
    elif kind == "boot":
        top[..., :3] = (18, 19, 23)
        weave(top, (18, 19, 23), (30, 31, 36), step=1)
    elif kind == "knob":
        top[..., :3] = (206, 26, 32)
        for f in ("west", "east", "front", "back"):
            faces[f][..., :3] = (190, 196, 208)
    elif kind == "paddle":
        for f in list(faces):
            faces[f][..., :3] = (150, 154, 164)
        top[..., :3] = (190, 196, 208)
    elif kind in ("lightbar_r", "lightbar_b"):
        col = (255, 36, 28) if kind == "lightbar_r" else (40, 96, 255)
        for ff in list(faces):
            faces[ff][..., :3] = tuple(int(v * 0.55) for v in col)
            glow[ff] = solid(faces[ff].shape[0], faces[ff].shape[1], col, 255)
    elif kind == "neon":
        for ff in list(faces):
            faces[ff][..., :3] = (40, 255, 130)
            glow[ff] = solid(faces[ff].shape[0], faces[ff].shape[1], (60, 255, 150), 255)
    elif kind == "taxisign":
        for ff in list(faces):
            faces[ff][..., :3] = (255, 214, 60)
            glow[ff] = solid(faces[ff].shape[0], faces[ff].shape[1], (255, 220, 80), 255)
        for ff in ("front", "back"):
            a = faces[ff]
            rect(a, 0, a.shape[0] // 2, a.shape[1], a.shape[0] // 2 + 1, (20, 20, 24))
            glow[ff] = solid(a.shape[0], a.shape[1], (255, 220, 80), 255)
    elif kind == "sirenbtn":
        for ff in list(faces):
            faces[ff][..., :3] = (214, 32, 30)
    elif kind == "swtop":
        for f in ("front", "back", "top", "bottom", "west", "east"):
            faces[f][..., :3] = (20, 21, 25)
        rect(faces["front"], 2, 0, 4, faces["front"].shape[0], ACCENT)     # marca naranja a las 12
        rect(faces["back"], 2, 0, 4, faces["back"].shape[0], ACCENT)
        rect(top, 2, 0, 4, top.shape[0], ACCENT)
    elif kind == "seatwing":
        for f in list(faces):
            weave(faces[f], (46, 48, 58), (58, 60, 72), step=1)
        weave(top, (46, 48, 58), (60, 62, 74), step=1)
        outline_rect(top, ACCENT, alpha=0.7)
    elif kind == "belt":
        for f in list(faces):
            faces[f][..., :3] = (20, 21, 25)
        a = faces["back"]
        for y in range(1, a.shape[0], 4):
            a[y, :, :3] = (214, 30, 36)                                            # rayitas rojas del cinturón
        a = faces["front"]
        for y in range(1, a.shape[0], 4):
            a[y, :, :3] = (214, 30, 36)
    elif kind == "armrest":
        img = vc.natural_side(faces, inner)
        img[..., :3] = (34, 36, 42)
        hline(img, 0, ACCENT, alpha=0.9)
        vc.store_side(faces, inner, img)
        top[..., :3] = (34, 36, 42)
        rect(top, 0, 1, 1, 3, (206, 210, 220)); rect(top, 0, 5, 1, 6, (206, 210, 220))   # botones de ventanilla
        rect(top, 0, 8, 1, 9, ACCENT)
    elif kind == "bulkhead_unused":
        a = faces["front"]
        h, w = a.shape[:2]
        a[..., :3] = (36, 38, 44)
        for x in range(0, w, 4):
            a[:, x, :3] = (30, 32, 38)
        for cx in (w // 2 - 7, w // 2 + 5):                                        # dos bocinas traseras
            rect(a, cx, 2, cx + 3, 5, (14, 14, 18))
            rect(a, cx + 1, 3, cx + 2, 4, (60, 62, 72))
        hline(a, 0, ACCENT, alpha=0.8)
    elif kind == "trimwall":
        for f in list(faces):
            faces[f][..., :3] = TRIM
        if c.name.startswith("tub"):
            img = vc.natural_side(faces, inner)
            h, w = img.shape[:2]
            hline(img, 3, ACCENT, alpha=0.9)                               # filete naranja
            rect(img, w - 7, 4, w - 3, 8, (14, 14, 18))                            # bocina delantera
            rect(img, w - 6, 5, w - 4, 7, (60, 62, 72))
            rect(img, 4, 4, 9, 8, (26, 28, 34))                                    # panel acolchado
            outline_rect(img[3:8, 3:10], (46, 48, 56), alpha=0.8)
            vc.store_side(faces, inner, img)
        if c.name == "cab_back":
            a = faces["front"]
            h, w = a.shape[:2]
            a[..., :3] = (36, 38, 44)
            for x in range(0, w, 4):
                a[:, x, :3] = (30, 32, 38)
            for cx in (w // 2 - 8, w // 2 + 5):
                rect(a, cx, 2, cx + 3, 5, (14, 14, 18))
                rect(a, cx + 1, 3, cx + 2, 4, (60, 62, 72))
            hline(a, 0, ACCENT, alpha=0.8)

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
    # V6.14.0: las piezas de skin de la plantilla de la camioneta se renombran a las del superdeportivo (1 carreras, 2 policía, 3 callejero, 4 taxi)
    import re
    for a, c in (("police", "racing"), ("delivery", "cop"), ("construction", "street"), ("fire", "taxi")):
        src = re.sub(r"\b" + a + r"\b", c, src)
    src = src.replace("this.barsOn = skin == 1 || skin == 4;", "this.barsOn = skin == 2;")
    src = src.replace("0 = ninguno, 1 = policía, 2 = reparto, 3 = construcción, 4 = bomberos", "0 = ninguno, 1 = carreras, 2 = policía, 3 = callejero, 4 = taxi")
    assert "ford_f150" not in src and "Pickup" not in src
    return src


# ----------------------------------------------------------------------------------------------- main
VIEWS = [(145, 14), (215, 14), (90, 3), (0, 6), (180, 6), (180, 80)]
CUT_VIEWS = [(150, 30), (210, 30), (90, 10), (0, 15), (180, 15), (180, 85)]
CENTER = (0, 12.0, 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--wheel", type=float, default=0.5)
    ap.add_argument("--steer", type=float, default=0.0)
    ap.add_argument("--dark", action="store_true")
    ap.add_argument("--cutaway", action="store_true")
    args = ap.parse_args()

    b, tex, glow = build_all()
    used = max(c.v + int(c.h + c.d) for c in b.cubes)
    print(f"{len(b.cubes)} cubos, atlas usado hasta y={used}/{TEX_H}")
    pose(b, args.wheel, args.steer)
    if args.cutaway:                    # solo para mirar la cabina: quita techo, puertas, vidrios y paneles de afuera
        hide = ("roof", "scoop", "door_", "pillar", "shoulder", "cowl", "side_win", "windshield", "mirror", "side_intake", "handle", "a_pillar", "deck", "quarter", "fender", "haunch", "tail")
        def strip(part):
            part.cubes = [c for c in part.cubes if not c.name.startswith(hide) or c.name.startswith("rv_")]
            for ch in part.children:
                strip(ch)
        strip(b.root)
    if args.preview:
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), CUT_VIEWS if args.cutaway else VIEWS, CENTER, 4.6, light_on=not args.dark, size=(420, 330))
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
