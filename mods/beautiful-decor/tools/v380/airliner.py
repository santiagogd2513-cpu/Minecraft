#!/usr/bin/env python3
"""Avión de pasajeros BOING 7G7 «Gigante» (V3.8.0, parodia de una marca de aviones famosa): geometría, textura, AirlinerModel.java, ícono y vistas.

Diseño propio: fuselaje HUECO (se camina con la vista por dentro: cabina de pasajeros con 128 asientos en filas de 2-4-2, pasillos,
galleys y puertas) con cabina de mando de 2 asientos, 4 turbinas bajo las alas, alas en flecha escalonadas con alerones y puntas
azules, cola alta y tren de aterrizaje que se recoge (nariz + 2 patas principales de 4 ruedas).
El modelo se hace a ESCALA 1:4: 1 unidad del modelo = 1/4 de bloque (el renderizador lo agranda 4 veces), así un avión de 62 bloques de largo
cabe en un atlas de 512 x 512. Las cajas que se repiten (módulos de pared, ventanillas, asientos, ruedas...) comparten el mismo trozo del atlas
(opción share=...), por eso hay unas 800 cajas pero pocas texturas distintas.

Uso (desde la raíz del proyecto):
  python3 -m tools.v380.airliner --preview vista.png      # solo mira
  python3 -m tools.v380.airliner --write                  # escribe textura, modelo Java e ícono
Coordenadas "amigables": x = izquierda (+), y = altura, z = adelante (+); el origen está en el centro de la cabina, a nivel del suelo.
Unidades: 4 por bloque (pasajeros: asientos en AirlinerEntity.SEAT_POS, calculados con las mismas cifras).
Piezas animadas: "fan0..3" (turbinas, giran en z), ruedas "w_*" (giran en x) y tren "gear_nose", "gear_ml", "gear_mr" (se pliegan).
"""
import argparse
import math
import re
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline
from ..v300.common300 import draw_text, text_width
from ..v320.paintkit import tone

TEX_W, TEX_H = 512, 512
NAME = "airliner"
UNITS_PER_BLOCK = 4

# Colores de referencia de los canales que el jugador puede cambiar con la tecla R: pintura del fuselaje y manijas de las puertas (sin rines).
WHITE_REF, WHITE_LO_REF = (234, 236, 242), (184, 188, 200)
HANDLE_REF = (176, 182, 194)
WHITE, WHITE_LO, HANDLE = WHITE_REF, WHITE_LO_REF, HANDLE_REF
NAVY, NAVY_LO = (24, 52, 128), (14, 30, 80)
RED = (200, 34, 40)
LINING = (214, 216, 222)           # forro claro del interior de la cabina
MAT_COLOR = {
    "paint": WHITE, "navy": NAVY, "red": RED, "dark": (24, 25, 30), "metal": (172, 176, 188), "fan": (62, 66, 76),
    "tire": (26, 26, 29), "seat": (36, 58, 124), "carpet": (50, 58, 90), "trim": (96, 100, 112), "dash": (28, 29, 34),
    "glass": (24, 36, 54), "lens": (246, 244, 226), "taillight": (206, 30, 34), "hub": (150, 154, 164),
}
REFS = {"body": WHITE_REF, "handle": HANDLE_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global WHITE, WHITE_LO, HANDLE
    WHITE = tone(WHITE_REF, WHITE_REF, body)
    WHITE_LO = tone(WHITE_LO_REF, WHITE_REF, body)
    HANDLE = tone(HANDLE_REF, HANDLE_REF, handle)
    MAT_COLOR["paint"] = WHITE


# ----------------------------------------------------------------------------------------------- medidas (unidades de 1/4 de bloque)
Y_KEEL, Y_HOLD_B, Y_HOLD_A, Y_FLOOR, Y_WALL0, Y_WALL1 = 6, 7, 10, 13, 14, 27
ROW_PITCH = 6
CABIN_ZC = [45 - ROW_PITCH * i for i in range(16)]            # 16 filas de 8 asientos = 128 pasajeros
ALL_ZC = list(range(93, -70, -ROW_PITCH))                     # 28 módulos de fuselaje (cabina de mando, entrada, cabina, galley trasero)
DOOR_ZC = {63: "f", 57: "f", -57: "r", -63: "r"}              # puertas: hueco de 12 de largo
BOING_L = {33: "B", 27: "O", 21: "I", 15: "N", 9: "G"}        # la B delante en el costado izquierdo...
BOING_R = {9: "B", 15: "O", 21: "I", 27: "N", 33: "G"}        # ...y detrás en el derecho: se lee bien desde afuera
TYPE_SIDE = {-9: "7", -15: "G", -21: "7"}
SEAT_GROUPS = (("a", 9, 15, "s"), ("b", -6, 6, "m"), ("c", -15, -9, "s"))
PILOT_X = (7, 13)
WHEEL_R = 3.5
# gradiente de tren: ángulos de plegado (radianes) cuando el tren está arriba; los signos se fijan mirando la vista previa
GEAR_SIGN = {"gear_ml": 1.0, "gear_mr": -1.0, "gear_nose": 1.0}
GEAR_ANGLE = math.pi / 2
ENGINES = [(40, 18), (68, 6), (-40, 18), (-68, 6)]            # (x del centro, z del frente)
WING = [(16, 36, -12, 20, 10, 13), (36, 56, -19, 8, 10, 13), (56, 76, -26, -4, 10, 13), (76, 96, -33, -16, 11, 13),
        (96, 116, -40, -28, 11, 13)]
STAB = [(10, 26, -106, -90), (26, 40, -110, -98), (40, 50, -112, -102)]


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


# ----------------------------------------------------------------------------------------------- geometría
def fuselage(b, body, glass):
    """Módulos de 6 de largo: paredes con ventanilla, techo escalonado, piso, bodega y, en la cabina, los asientos."""
    for zc in ALL_ZC:
        z0, z1 = zc - 3, zc + 3
        n = f"{zc:+d}".replace("+", "p").replace("-", "m")
        if zc not in DOOR_ZC:
            for sx, nm, x0, x1, letters in ((1, "l", 15, 16, BOING_L), (-1, "r", -16, -15, BOING_R)):
                letter = letters.get(zc) or TYPE_SIDE.get(zc)
                opts = dict(kind="wall", side=sx)
                if letter:
                    opts["letter"] = letter
                    opts["share"] = None
                else:
                    opts["share"] = f"wall_{nm}"
                box(b, body, f"wall_{nm}_{n}", x0, x1, Y_WALL0, Y_WALL1, z0, z1, "paint", **opts)
                box(b, glass, f"win_{nm}_{n}", x0, x1, 17, 21, z0 + 1, z1 - 1, "glass", kind="glass", translucent=True,
                    side=sx, share=f"win_{nm}")
        box(b, body, f"roof_a_{n}", -16, 16, 27, 28, z0, z1, "paint", kind="roof_a", share="roof_a")
        box(b, body, f"roof_b_{n}", -14, 14, 28, 29, z0, z1, "paint", kind="roof_b", share="roof_b")
        box(b, body, f"roof_c_{n}", -10, 10, 29, 30, z0, z1, "paint", kind="roof_c", share="roof_c")
        box(b, body, f"floor_{n}", -15, 15, Y_FLOOR, Y_WALL0, z0, z1, "carpet", kind="floor", share="floor")
        box(b, body, f"hold_a_{n}", -16, 16, Y_HOLD_A, Y_FLOOR, z0, z1, "paint", kind="hold_a", share="hold_a")
        box(b, body, f"hold_b_{n}", -12, 12, Y_HOLD_B, Y_HOLD_A, z0, z1, "paint", kind="hold_b", share="hold_b")
        box(b, body, f"hold_c_{n}", -8, 8, Y_KEEL, Y_HOLD_B, z0, z1, "paint", kind="hold_c", share="hold_c")
    for sx, nm, x0, x1 in ((1, "l", 15, 16), (-1, "r", -16, -15)):
        box(b, body, f"door_f_{nm}", x0, x1, Y_WALL0, Y_WALL1, 54, 66, "paint", kind="door", side=sx, share=f"door_{nm}")
        box(b, body, f"door_r_{nm}", x0, x1, Y_WALL0, Y_WALL1, -66, -54, "paint", kind="door", side=sx, share=f"door_{nm}")

    # ---- asientos de pasajeros: 3 bloques por fila (ventana izquierda 2, centro 4, ventana derecha 2)
    for i, zc in enumerate(CABIN_ZC):
        for nm, x0, x1, size in SEAT_GROUPS:
            box(b, body, f"cush_{nm}{i}", x0, x1, 14, 16, zc - 2, zc + 2, "seat", kind="cushion", share=f"cush_{size}")
            box(b, body, f"back_{nm}{i}", x0, x1, 16, 22, zc - 3, zc - 2, "seat", kind="seatback", share=f"back_{size}")


def cockpit(b, body, glass):
    """Cabina de mando: dos asientos, pedestal, tablero y parabrisas inclinado."""
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (PILOT_X[0], PILOT_X[1]) if sx > 0 else (-PILOT_X[1], -PILOT_X[0])
        # V6.6.0: asientos de los pilotos 3 unidades (75 cm) más altos para ver por el parabrisas (AirlinerEntity.PILOT_RAISE)
        box(b, body, f"pilot_cush_{nm}", x0, x1, 17, 19, 78, 83, "seat", kind="cushion", share="pilot_cush")
        box(b, body, f"pilot_back_{nm}", x0, x1, 19, 27, 77, 78, "seat", kind="seatback", share="pilot_back")
        box(b, body, f"pilot_base_{nm}", x0 + 1, x1 - 1, 14, 17, 79, 82, "dark", kind="plain")
    box(b, body, "pedestal", -3, 3, 14, 18, 76, 90, "dash", kind="pedestal")
    box(b, body, "glare", -15, 15, 17, 21, 90, 95, "dash", kind="dash")
    # V6.5.0: palanca del tren de aterrizaje en el pedestal (AirlinerEntity.GEAR_LEVER): clic = recoger / bajar el tren
    box(b, body, "gear_lever", -1, 1, 18, 19, 80, 82, "chrome", kind="plain")
    box(b, body, "gear_knob", -1, 1, 19, 20, 80, 82, "taillight", kind="plain")
    # V6.6.0: interior: botón de luces del pedestal (AirlinerEntity.LIGHT_BUTTON), relojes en el tablero y palancas de los motores
    box(b, body, "btn_light", 1, 3, 18, 19, 84, 86, "lens", kind="plain")
    for k, x in enumerate((-12, -9, -6, 6, 9, 12)):
        box(b, body, f"gauge_{k}", x, x + 2, 18, 21, 89, 90, "metal", kind="plain")
    for k, x in enumerate((-2, 0)):
        box(b, body, f"throttle_{k}", x - 1, x, 18, 22, 86, 88, "metal", kind="plain")
    box(b, body, "overhead", -6, 6, 24, 27, 80, 90, "dash", kind="overhead")
    ws = b.part("ws_frame", body, (0, 20.0, 97.0), (-deg(30.0), 0.0, 0.0))
    for k, x in enumerate((14, 0, -14)):
        b.cube(ws, f"ws_pillar_{k}", (x, 4.5, 0.0), (2, 9, 2), "paint", kind="ws_pillar")
    box(b, ws, "ws_sill", -13, 13, 0, 1, -1, 1, "dark", kind="plain")
    wg = b.part("ws_glass", glass, (0, 20.0, 97.0), (-deg(30.0), 0.0, 0.0))
    b.cube(wg, "ws_glass_l", (7, 4.5, 0.0), (12, 8, 1), "glass", kind="glass", translucent=True)
    b.cube(wg, "ws_glass_r", (-7, 4.5, 0.0), (12, 8, 1), "glass", kind="glass", translucent=True)


def nose_and_tail(b, body):
    box(b, body, "nose_0", -14, 14, 8, 20, 96, 103, "paint", kind="nose0")
    box(b, body, "nose_1", -12, 12, 9, 19, 103, 110, "paint", kind="nose1")
    box(b, body, "nose_2", -10, 10, 10, 18, 110, 116, "paint", kind="nose1")
    box(b, body, "nose_3", -7, 7, 11, 17, 116, 121, "paint", kind="nose1")
    box(b, body, "nose_4", -4, 4, 12, 16, 121, 125, "paint", kind="radome")
    lr(b, body, "nose_light", 10, 12, 11, 13, 110, 111, "lens", kind="light")
    box(b, body, "tail_0", -14, 14, 11, 29, -84, -72, "paint", kind="tail0")
    box(b, body, "tail_1", -11, 11, 14, 28, -96, -84, "paint", kind="tail1")
    box(b, body, "tail_2", -7, 7, 17, 27, -108, -96, "paint", kind="tail1")
    box(b, body, "tail_3", -4, 4, 20, 26, -118, -108, "paint", kind="tail1")
    box(b, body, "apu", -2, 2, 21, 25, -120, -118, "dark", kind="plain")
    # ---- cola: deriva alta de tres escalones (azul) y estabilizadores en flecha
    box(b, body, "fin_a", -1, 1, 26, 45, -108, -84, "navy", kind="fin_a")
    box(b, body, "fin_b", -1, 1, 45, 59, -108, -90, "navy", kind="fin")
    box(b, body, "fin_c", -1, 1, 59, 68, -108, -96, "navy", kind="fin")
    box(b, body, "beacon", -1, 1, 68, 70, -102, -100, "taillight", kind="light")
    for k, (x0, x1, zte, zle) in enumerate(STAB):
        lr(b, body, f"stab_{k}", x0, x1, 26, 28, zte, zle, "navy" if k == 2 else "paint", kind="stab", share=f"stab_{k}")


def wings(b, body):
    for k, (x0, x1, zte, zle, y0, y1) in enumerate(WING):
        lr(b, body, f"wing_{k}", x0, x1, y0, y1, zte, zle, "navy" if k == 4 else "paint", kind="wing", seg=k, share=f"wing_{k}")
    lr(b, body, "winglet", 114, 116, 13, 23, -40, -30, "navy", kind="fin", share="winglet")
    box(b, body, "nav_l", 116, 118, 11, 13, -37, -35, "taillight", kind="navred", side=1)
    box(b, body, "nav_r", -118, -116, 11, 13, -37, -35, "taillight", kind="navgreen", side=-1)
    lr(b, body, "root_light", 16, 18, 11, 13, 20, 21, "lens", kind="light", share="root_light")


def engines(b, body):
    for i, (cx, zf) in enumerate(ENGINES):
        e = b.part(f"eng{i}", body, (cx, 7.5, float(zf)))
        b.cube(e, f"nac_a{i}", (0, 0, -9), (10, 7, 18), "paint", kind="nacelle", share="nac_a")
        b.cube(e, f"nac_b{i}", (0, 0, -9), (8, 9, 18), "paint", kind="nacelle", share="nac_b")
        b.cube(e, f"cone{i}", (0, 0, -19), (6, 6, 2), "metal", kind="metal", share="cone")
        b.cube(e, f"plug{i}", (0, 0, -21), (4, 4, 2), "metal", kind="metal", share="plug")
        fan = b.part(f"fan{i}", e, (0, 0, 0.5))
        b.cube(fan, f"fan_disc{i}", (0, 0, 0), (8, 7, 1), "fan", kind="fan", share="fan")


def gear(b, body):
    """Tren: nariz (2 ruedas) y dos patas principales (4 ruedas cada una). Las ruedas son octágonos de 4 cajas giradas.
    Las patas cuelgan de un pivote alto (dentro de la bodega) para que al plegarse queden dentro del fuselaje."""
    for nm, x, z in (("gear_ml", 9, -6), ("gear_mr", -9, -6)):
        g = b.part(nm, body, (x, 12.0, float(z)))
        b.cube(g, f"{nm}_strut", (0, -4.5, 0), (2, 9, 2), "metal", kind="metal", share="strut")
        b.cube(g, f"{nm}_beam", (0, -8.5, 0), (2, 1, 8), "metal", kind="metal", share="beam")
        for ix, wx in enumerate((2.5, -2.5)):
            for iz, wz in enumerate((3.0, -3.0)):
                vc.add_wheel(b, g, f"w_{nm}_{ix}{iz}", (wx, -8.5, wz), WHEEL_R, 2)
        b.cube(g, f"{nm}_axle_a", (0, -8.5, 3), (6, 1, 1), "metal", kind="metal", share="axle")
        b.cube(g, f"{nm}_axle_b", (0, -8.5, -3), (6, 1, 1), "metal", kind="metal", share="axle")
    g = b.part("gear_nose", body, (0, 10.0, 70.0))
    b.cube(g, "nose_strut", (0, -3.5, 0), (2, 7, 2), "metal", kind="metal", share="nose_strut")
    b.cube(g, "nose_axle", (0, -6.5, 0), (6, 1, 1), "metal", kind="metal", share="axle")
    for ix, wx in enumerate((2.0, -2.0)):
        vc.add_wheel(b, g, f"w_nose_{ix}", (wx, -6.5, 0), WHEEL_R, 2)


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))
    fuselage(b, body, glass)
    cockpit(b, body, glass)
    nose_and_tail(b, body)
    wings(b, body)
    engines(b, body)
    gear(b, body)
    for c in b.cubes:                      # las ruedas son todas iguales: comparten textura
        if c.opts.get("wheel"):
            c.opts["share"] = f"wheel_{c.opts['phi']:.3f}"
    return b


def pack_all(b):
    """Reparte el atlas: las cajas con la misma opción share usan el mismo trozo (la primera lo reserva)."""
    reps, dupes, first = [], [], {}
    for c in b.cubes:
        key = c.opts.get("share")
        if key is None:
            reps.append(c)
        elif key in first:
            dupes.append((c, first[key]))
        else:
            first[key] = c
            reps.append(c)
    used = vc.pack(reps, TEX_W, TEX_H)
    for c, r in dupes:
        assert (c.w, c.h, c.d) == (r.w, r.h, r.d), (c.name, r.name)
        assert c.mat == r.mat, (c.name, r.name)
        c.u, c.v = r.u, r.v
    return used


def wheel_names(b):
    """Las piezas de rueda (no las cajas giradas fijas w_..._t0..3 que forman el octágono)."""
    return [n for n in b.parts if n.startswith("w_") and not re.fullmatch(r"w_.+_t\d", n)]


def pose(b, fan=0.0, wheel=0.0, gear=0.0, glass=True, body=True):
    """Pose del modelo (mismo cálculo que AirlinerModel.pose): fan = giro de las turbinas, wheel = giro de las ruedas, gear = 0 abajo .. 1 arriba."""
    for i in range(4):
        b.parts[f"fan{i}"].rot = [0.0, 0.0, fan]
    for nm in wheel_names(b):
        b.parts[nm].rot = [wheel, 0.0, 0.0]
    b.parts["gear_ml"].rot = [0.0, 0.0, GEAR_SIGN["gear_ml"] * GEAR_ANGLE * gear]
    b.parts["gear_mr"].rot = [0.0, 0.0, GEAR_SIGN["gear_mr"] * GEAR_ANGLE * gear]
    b.parts["gear_nose"].rot = [GEAR_SIGN["gear_nose"] * GEAR_ANGLE * gear, 0.0, 0.0]
    b.parts["glass"].visible = glass
    b.parts["body"].visible = body


# ----------------------------------------------------------------------------------------------- pintura
def wheel_art(Y, Z, kind):
    r = math.hypot(Y, Z)
    if r >= 2.7:
        return (24, 24, 27, 255)
    if r >= 1.7:
        return (38, 39, 44, 255)
    return (150, 154, 164, 255) if r > 0.7 else (90, 94, 104, 255)


def glass_faces(c, rng):
    """Vidrio ahumado azul grisáceo translúcido con un par de destellos diagonales."""
    faces = {}
    for f, (rows, cols) in vc.face_sizes(c).items():
        a = solid(rows, cols, (30, 44, 62), 120)
        if f in ("west", "east") and rows >= 3 and cols >= 3:
            for x in range(cols):
                for k in range(0, rows + cols, 6):
                    y = k - x
                    if 0 <= y < rows and (k // 6) % 2 == 0:
                        a[y, x, :3] = (120, 140, 164)
                        a[y, x, 3] = 150
        faces[f] = a
    return faces, {}


def put_glyph(img, ch, col0, row0, color):
    """Una letra de la fuente 3x5 sobre una cara lateral, tal como se ve desde afuera con la cara ya puesta de frente."""
    draw_text(img, ch, col0, row0, color)


def paint_cube(c, rng):
    if c.opts.get("wheel"):
        return vc.wheel_faces(c, rng, wheel_art, tread=((26, 26, 29), (42, 42, 47)))
    kind = c.opts.get("kind", "plain")
    if kind == "glass":
        return glass_faces(c, rng)
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    gloss = c.mat in ("paint", "navy", "red")
    faces = vc.base_faces(c, color, rng, gloss=gloss)
    glow = {}
    top = faces["top"]
    side = c.opts.get("side", 1)
    outer = "east" if side > 0 else "west"
    inner = "west" if side > 0 else "east"

    if kind == "wall":
        img = faces[outer]                                   # 13 filas x 6 columnas
        h, w = img.shape[:2]
        rect(img, 0, 10, w, 12, NAVY)                        # línea azul bajo las ventanillas
        hline(img, 12, (150, 154, 166), alpha=0.5)
        letter = c.opts.get("letter")
        if letter:
            put_glyph(img, letter, 1 if side < 0 else 2, 1, NAVY_LO)
        img[6:10, 1:5, 3] = 0                                # hueco de la ventanilla
        lin = faces[inner]
        lin[..., :3] = LINING
        noise(lin, rng, 0.02)
        hline(lin, 12, (160, 164, 176), alpha=0.6)
        hline(lin, 5, (196, 198, 206), alpha=0.8)
        lin[6:10, 1:5, 3] = 0
        # marco de la ventanilla (por dentro, 1 px más oscuro)
        for r in (5, 10):
            lin[r, 1:5, :3] = (150, 154, 166)
    elif kind == "door":
        img = faces[outer]                                   # 13 x 12
        h, w = img.shape[:2]
        outline_rect(img, WHITE_LO, alpha=0.9)
        rect(img, 0, 10, w, 12, NAVY)
        rect(img, w // 2 - 2, 3, w // 2 + 2, 7, (30, 44, 62))                       # ventanilla de la puerta
        rect(img, w // 2 + 2, 8, w // 2 + 5, 9, HANDLE)                              # manija
        lin = faces[inner]
        lin[..., :3] = (180, 184, 194)
        outline_rect(lin, (120, 124, 136), alpha=0.9)
    elif kind == "roof_a":
        faces["bottom"][..., :3] = LINING
        noise(faces["bottom"], rng, 0.02)
        vline(faces["bottom"], faces["bottom"].shape[1] // 2, (190, 192, 202), alpha=0.7)
    elif kind in ("roof_b", "roof_c"):
        vline(top, top.shape[1] // 2, WHITE_LO, alpha=0.5)
    elif kind == "floor":
        cols = top.shape[1]
        top[..., :3] = color
        for x in range(cols):
            if 6 <= x <= 8 or 21 <= x <= 23:                                         # alfombra del pasillo
                top[:, x, :3] = (88, 98, 140)
        top[:, 0, :3] = (30, 34, 54)
        top[:, cols - 1, :3] = (30, 34, 54)
        noise(top, rng, 0.04)
        faces["bottom"][..., :3] = (120, 124, 134)
    elif kind == "hold_a":
        for f in ("west", "east"):
            hline(faces[f], faces[f].shape[0] - 1, WHITE_LO, alpha=0.7)
    elif kind == "hold_b":
        for f in ("west", "east", "front", "back"):
            faces[f][..., :3] = WHITE_LO
    elif kind == "hold_c":
        for f in ("west", "east", "front", "back"):
            faces[f][..., :3] = (120, 124, 136)
        faces["bottom"][..., :3] = (96, 100, 112)
    elif kind == "cushion":
        top[..., :3] = color
        for y in range(top.shape[0]):
            for x in range(top.shape[1]):
                if (x + y) % 2 == 0:
                    top[y, x, :3] *= 1.12
        outline_rect(top, (20, 34, 80), alpha=0.8)
    elif kind == "seatback":
        for f in ("front", "back"):
            a = faces[f]
            for y in range(a.shape[0]):
                for x in range(a.shape[1]):
                    if (x + y) % 2 == 0:
                        a[y, x, :3] *= 1.10
            rect(a, 0, 0, a.shape[1], 2, (222, 226, 236))                           # funda del cabezal
            outline_rect(a, (20, 34, 80), alpha=0.7)
    elif kind == "pedestal":
        top[..., :3] = (30, 31, 36)
        for x in (1, top.shape[1] - 2):
            vline(top, x, (60, 64, 74), alpha=0.9)
        rect(top, 2, 2, 4, 6, (200, 60, 50))                                          # palancas de empuje
        rect(top, 2, 8, 4, 12, (60, 120, 200))
    elif kind == "dash":
        top[..., :3] = (30, 31, 36)
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (32, 33, 38)
        for x in range(2, w - 4, 6):
            rect(a, x, 0, x + 4, 3, (16, 16, 20))
            rect(a, x + 1, 1, x + 3, 2, (60, 150, 210) if (x // 6) % 2 == 0 else (60, 190, 120))
        hline(a, h - 1, (14, 14, 18), alpha=0.8)
    elif kind == "overhead":
        faces["bottom"][..., :3] = (40, 42, 48)
        for k in range(1, faces["bottom"].shape[1] - 1, 3):
            rect(faces["bottom"], k, 2, k + 1, 8, (230, 150, 40))
    elif kind == "ws_pillar":
        for f in list(faces):
            faces[f][..., :3] = WHITE
    elif kind == "nose0":
        for f in ("west", "east", "front"):
            a = faces[f]
            rect(a, 0, a.shape[0] - 3, a.shape[1], a.shape[0], NAVY)
        faces["back"][..., :3] = (36, 38, 46)
        hline(top, 0, WHITE_LO, alpha=0.5)
    elif kind == "nose1":
        for f in ("west", "east"):
            a = faces[f]
            rect(a, 0, a.shape[0] - 2, a.shape[1], a.shape[0], NAVY)
    elif kind == "radome":
        for f in list(faces):
            faces[f][..., :3] = (206, 210, 220)
        rect(faces["front"], 1, 1, 3, 3, (60, 62, 72))
    elif kind == "tail0":
        for f in ("west", "east"):
            a = faces[f]
            rect(a, 0, a.shape[0] - 4, a.shape[1], a.shape[0], NAVY)
        faces["front"][..., :3] = LINING
    elif kind == "tail1":
        for f in ("west", "east"):
            a = faces[f]
            rect(a, 0, a.shape[0] - 3, a.shape[1], a.shape[0], NAVY)
    elif kind == "fin_a":
        for f in list(faces):
            faces[f][..., :3] = NAVY
        for f in ("west", "east"):
            img = faces[f]                                                           # 19 filas x 24 columnas
            rows, cols = img.shape[:2]
            cy, cx = 8, cols // 2
            for r in range(rows):
                for q in range(cols):
                    if (r - cy) ** 2 + (q - cx) ** 2 <= 49:
                        img[r, q, :3] = (240, 242, 248)
            draw_text(img, "B", cx - 3, cy - 5, NAVY_LO, 2)
            rect(img, 0, 0, cols, 2, RED)
    elif kind == "fin":
        for f in list(faces):
            faces[f][..., :3] = NAVY
        for f in ("west", "east"):
            rect(faces[f], 0, 0, faces[f].shape[1], 1, RED)
    elif kind == "stab":
        top[..., :3] = color
        hline(top, 0, WHITE_LO, alpha=0.5)
        faces["bottom"][..., :3] = WHITE_LO
    elif kind == "wing":
        rows, cols = top.shape[:2]
        k = c.opts.get("seg", 0)
        if c.mat == "paint":
            top[..., :3] = color
            hline(top, rows - 1, WHITE_LO, alpha=0.9)                                # borde de fuga
            hline(top, max(0, rows - 6), WHITE_LO, alpha=0.6)                       # alerón
            faces["bottom"][..., :3] = WHITE_LO
            vline(top, 0, WHITE_LO, alpha=0.6)
        else:
            hline(top, rows - 1, (14, 30, 80), alpha=0.7)
        if k == 0:
            a = faces["front"]
            rect(a, 0, 0, a.shape[1], a.shape[0], (206, 210, 220))
    elif kind == "nacelle":
        for f in ("front",):
            faces[f][..., :3] = NAVY
        for f in ("top", "bottom"):
            rect(faces[f], 0, 0, min(3, faces[f].shape[1]), faces[f].shape[0], NAVY)
        for f in ("west", "east"):
            a = faces[f]
            rect(a, a.shape[1] - 3, 0, a.shape[1], a.shape[0], NAVY)
        faces["back"][..., :3] = (60, 64, 74)
    elif kind == "fan":
        f = faces["front"]
        rows, cols = f.shape[:2]
        f[..., :3] = (28, 30, 36)
        cy, cx = (rows - 1) / 2.0, (cols - 1) / 2.0
        for r in range(rows):
            for q in range(cols):
                ang = math.atan2(r - cy, q - cx)
                dist = math.hypot(r - cy, q - cx)
                if dist < 0.9:
                    f[r, q, :3] = (170, 174, 186)
                elif int(((ang + math.pi) / (2 * math.pi)) * 12) % 2 == 0:
                    f[r, q, :3] = (110, 114, 126)
        faces["back"][..., :3] = (40, 42, 50)
    elif kind == "metal":
        for f in list(faces):
            hline(faces[f], 0, (210, 214, 224), alpha=0.5)
    elif kind == "light":
        for f in list(faces):
            faces[f][..., :3] = color
            glow[f] = solid(faces[f].shape[0], faces[f].shape[1], tuple(min(255, int(v * 1.2) + 20) for v in color), 255)     # V6.6.0: brillan con las luces
    elif kind == "navred":
        for f in list(faces):
            faces[f][..., :3] = (230, 40, 44)
            glow[f] = solid(faces[f].shape[0], faces[f].shape[1], (255, 40, 40), 255)
    elif kind == "navgreen":
        for f in list(faces):
            faces[f][..., :3] = (40, 220, 90)
            glow[f] = solid(faces[f].shape[0], faces[f].shape[1], (40, 255, 100), 255)

    if c.mat in ("paint", "navy") and kind in ("hold_a", "roof_a", "nose0", "tail0", "nacelle"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.04, 0.08)
    return faces, glow


def build_all():
    b = build()
    pack_all(b)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3800)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = build()
    pack_all(b)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3800, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    body = vc.java_tree(b)
    fans = ", ".join(f'this.body.getChild("eng{i}").getChild("fan{i}")' for i in range(4))
    wheels = []
    for nm in wheel_names(b):
        wheels.append('this.body.getChild("%s").getChild("%s")' % (b.parts[nm].parent.name, nm))
    wheel_code = ",\n                ".join(wheels)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.AirlinerEntity;
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
 * Avión BOING 7G7 Gigante (V3.8.0). ARCHIVO GENERADO por tools/v380/airliner.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX_W}x{TEX_H}. Modelo a escala 1:4 (el renderizador lo agranda {UNITS_PER_BLOCK} veces: 1 unidad = 1/4 de bloque).
 * Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24). Dos árboles: "body" (opaco) y "glass" (vidrios).
 * Piezas animadas: turbinas (giran en z), ruedas (giran en x) y las tres patas del tren (se pliegan).
 */
public class AirlinerModel extends EntityModel<AirlinerEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "airliner"), "main");
    /** Cuántas veces el renderizador agranda el modelo. */
    public static final float SCALE = {float(UNITS_PER_BLOCK)}f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart[] fans;
    private final ModelPart[] wheels;
    private final ModelPart gearLeft;
    private final ModelPart gearRight;
    private final ModelPart gearNose;

    public AirlinerModel(ModelPart root) {{
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.fans = new ModelPart[] {{{fans}}};
        this.wheels = new ModelPart[] {{
                {wheel_code}}};
        this.gearLeft = this.body.getChild("gear_ml");
        this.gearRight = this.body.getChild("gear_mr");
        this.gearNose = this.body.getChild("gear_nose");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
    }}

    /** fanAngle = giro de las turbinas (rad); wheelAngle = giro de las ruedas (rad); gear = 0 tren abajo .. 1 tren arriba. */
    public void pose(float fanAngle, float wheelAngle, float gear) {{
        for (ModelPart fan : this.fans) {{
            fan.zRot = fanAngle;
        }}
        for (ModelPart wheel : this.wheels) {{
            wheel.xRot = wheelAngle;
        }}
        float fold = gear * {GEAR_ANGLE:.4f}f;
        this.gearLeft.zRot = {GEAR_SIGN["gear_ml"]:.1f}f * fold;
        this.gearRight.zRot = {GEAR_SIGN["gear_mr"]:.1f}f * fold;
        this.gearNose.xRot = {GEAR_SIGN["gear_nose"]:.1f}f * fold;
    }}

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {{
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }}

    @Override
    public void setupAnim(AirlinerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
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
VIEWS = [(145, 16), (215, 16), (90, 3), (0, 82), (180, 6), (180, 40)]
CENTER = (0, 14.0, -4)


def hide(b, predicate):
    """Quita (temporalmente) las cajas que cumplen predicate para ver el interior. Devuelve lo necesario para devolverlas."""
    saved = []
    for p in b.parts.values():
        keep = []
        for c in p.cubes:
            (saved.append((p, c)) if predicate(c) else keep.append(c))
        p.cubes = keep
    return saved


def restore(saved):
    for p, c in saved:
        p.cubes.append(c)


def render_views(b, tex, glow, specs, scale, size=(480, 330), ss=2):
    from ..v200.softrender import Camera, render
    out = []
    for az, el in specs:
        cam = Camera(az, el, scale, CENTER, size)
        out.append(render(b.root, tex, cam, (TEX_W, TEX_H), glow=None, background=(78, 86, 98, 255), supersample=ss,
                          translucent_mats=("glass",)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--fan", type=float, default=0.4)
    ap.add_argument("--wheel", type=float, default=0.3)
    ap.add_argument("--gear", type=float, default=0.0)
    ap.add_argument("--inside", action="store_true", help="vista con el costado izquierdo y el techo quitados")
    ap.add_argument("--scale", type=float, default=1.55)
    ap.add_argument("--views", default="")
    ap.add_argument("--center", default="")
    ap.add_argument("--hide", default="", help="prefijos de cajas a quitar de la vista (separados por coma)")
    args = ap.parse_args()

    global CENTER
    if args.center:
        CENTER = tuple(float(v) for v in args.center.split(","))
    b, tex, glow = build_all()
    used = max(c.v + int(c.h + c.d) for c in b.cubes)
    uniq = len({(c.u, c.v) for c in b.cubes})
    print(f"{len(b.cubes)} cajas ({uniq} trozos distintos del atlas), atlas usado hasta y={used}/{TEX_H}")
    pose(b, args.fan, args.wheel, args.gear)
    if args.preview:
        specs = VIEWS
        if args.views:
            specs = [tuple(float(v) for v in s.split(":")) for s in args.views.split(",")]
        saved = []
        if args.hide:
            prefixes = tuple(args.hide.split(","))
            saved += hide(b, lambda c: c.name.startswith(prefixes))
        if args.inside:
            saved += hide(b, lambda c: c.name.startswith(("wall_l", "roof_", "win_l", "door_f_l", "door_r_l")))
        imgs = render_views(b, tex, glow, specs, args.scale)
        restore(saved)
        vc.sheet(imgs, cols=2).convert("RGB").save(args.preview)
        big = tex.resize((TEX_W * 2, TEX_H * 2), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / f"textures/entity/{NAME}.png")
        glow.save(ASSETS / f"textures/entity/{NAME}_glow.png")
        pose(b, 0.0, 0.0, 0.0)          # el modelo Java se escribe en reposo (la pose real la pone el renderizador en cada fotograma)
        (JAVA / "client/AirlinerModel.java").write_text(java_model(b), encoding="utf-8")
        from ..v200.softrender import Camera, render
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 140, 20, CENTER, 1.6, outline=(8, 12, 30, 255), brighten=1.05)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / f"textures/item/{NAME}.png")
        print("escrito: textura, AirlinerModel.java, ícono")


if __name__ == "__main__":
    main()
