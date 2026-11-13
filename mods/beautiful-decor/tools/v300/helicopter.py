#!/usr/bin/env python3
"""Helicóptero BOING HX-6 (parodia de una marca de aviones y helicópteros famosa) de BeautifulDecor, V3.0.0:
geometría, textura, HelicopterModel.java, ícono y vistas.

Una sola definición de geometría produce el modelo Java y la textura (como la moto y los carros), así nunca se desalinean.

Uso (desde la raíz del proyecto):
  python3 -m tools.v300.helicopter --preview vista.png      # solo mira
  python3 -m tools.v300.helicopter --write                  # escribe textura, modelo Java e ícono
Coordenadas "amigables": x = izquierda (+), y = altura, z = adelante (+); el origen está en el centro, a nivel del suelo.
Asientos (ver HelicopterEntity): delanteros a x = +-6, z = 0; traseros a x = +-6, z = -14; el que se sube primero pilota.
Piezas animadas: "rotor" (rotor principal, gira en y) y "tail_rotor" (rotor de cola, gira en x).
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, outline_rect, rect, solid, vline
from .common300 import draw_text, text_width
from .delivery_truck import box, glass_faces, lr
from ..v320.paintkit import tone

TEX_W, TEX_H = 256, 448     # V6.9.0: 128 filas más para los accesorios de las skins

# Colores de referencia de los canales que el jugador puede cambiar (V3.2.0): pintura y manijas de las puertas (no tiene rines).
WHITE_REF, WHITE_LO_REF = (234, 236, 242), (184, 188, 200)
HANDLE_REF = (176, 182, 194)
WHITE = WHITE_REF
WHITE_LO = WHITE_LO_REF
HANDLE = HANDLE_REF
NAVY = (26, 58, 140)
NAVY_LO = (14, 30, 80)
RED = (200, 34, 40)
# V6.9.0: skin que se pinta (0 = original; 1 policía, 2 bomberos, 3 noticias) y rótulos; los fija tools/v690/heli_skins.py
SKIN = 0
DOOR_TXT, BOOM_TXT, BOOM_SCALE, FIN_TXT = "BH-6", "BOING", 2, "B"
MAT_COLOR = {
    "paint": WHITE, "navy": NAVY, "red": RED, "dark": (24, 25, 30), "skid": (72, 76, 86), "blade": (46, 48, 54),
    "seat": (60, 64, 76), "carpet": (46, 48, 54), "dash": (28, 29, 34), "trim": (66, 68, 76), "glass": (24, 36, 54),
    "hub": (30, 31, 36), "tip": (240, 190, 30), "metal": (170, 174, 184), "lens": (230, 236, 244),
}

REFS = {"body": WHITE_REF, "handle": HANDLE_REF}


def set_paint(body=(0, 0, 0), handle=(0, 0, 0), rim=(0, 0, 0)):
    """Desplaza los colores de cada canal (delta 0 = los de siempre). Ver tools/v320/paintkit.py."""
    global WHITE, WHITE_LO, HANDLE
    WHITE = tone(WHITE_REF, WHITE_REF, body)
    WHITE_LO = tone(WHITE_LO_REF, WHITE_REF, body)
    HANDLE = tone(HANDLE_REF, HANDLE_REF, handle)
    MAT_COLOR["paint"] = WHITE


CAB_Z0, CAB_Z1 = -24, 14
BOOM_Z0 = -68


def build():
    b = vc.new_builder()
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))

    # ---------------------------------------------------------------- patines
    for k, (z0, z1) in enumerate(((-24, 0), (0, 22))):
        lr(b, body, f"skid{k}", 10, 12, 0, 2, z0, z1, "skid", kind="skid")
    lr(b, body, "skid_front_rise", 10, 12, 2, 6, 22, 24, "skid", kind="skid")
    lr(b, body, "skid_rear_rise", 10, 12, 2, 4, -26, -24, "skid", kind="skid")
    lr(b, body, "strut_f", 9, 11, 2, 7, 8, 10, "skid", kind="skid")
    lr(b, body, "strut_r", 9, 11, 2, 7, -16, -14, "skid", kind="skid")
    box(b, body, "cross_f", -10, 10, 4, 6, 8, 10, "skid", kind="skid")
    box(b, body, "cross_r", -10, 10, 4, 6, -16, -14, "skid", kind="skid")

    # ---------------------------------------------------------------- cabina: piso, paredes bajas, techo
    box(b, body, "floor", -10, 10, 6, 8, CAB_Z0, CAB_Z1, "carpet", kind="plain")
    lr(b, body, "door", 10, 12, 8, 20, CAB_Z0, CAB_Z1, "paint", kind="door")
    lr(b, body, "pillar_a", 10, 12, 20, 34, 11, 14, "paint", kind="pillar")
    lr(b, body, "pillar_b", 10, 12, 20, 34, -1, 1, "paint", kind="pillar")
    lr(b, body, "pillar_c", 10, 12, 20, 34, -26, -22, "paint", kind="pillar")
    box(b, body, "rear_wall", -10, 10, 8, 34, -26, -24, "paint", kind="rear_wall")
    box(b, body, "roof", -12, 12, 34, 36, -26, 14, "paint", kind="roof")
    box(b, body, "brow", -12, 12, 30, 34, 10, 14, "paint", kind="brow")
    box(b, body, "nose_lower", -10, 10, 8, 13, 14, 24, "paint", kind="nose")
    box(b, body, "nose_tip", -7, 7, 8, 12, 24, 28, "paint", kind="nose_tip")
    box(b, body, "dash", -10, 10, 13, 19, 8, 12, "dash", kind="dash")
    box(b, body, "chin", -8, 8, 5, 8, 12, 22, "navy", kind="chin")
    # V6.6.0: faros (nariz) y foco de panza que brillan al encender las luces
    lr(b, body, "headlamp", 3, 6, 9, 11, 28, 29, "lens", kind="lamp")
    box(b, body, "belly_lamp", -2, 2, 4, 5, 15, 19, "lens", kind="lamp")
    box(b, body, "strobe", -1, 1, 36, 37, -24, -22, "red", kind="lamp")

    # ---------------------------------------------------------------- asientos (4): delanteros z = 0, traseros z = -14
    for sx, nm in ((1, "l"), (-1, "r")):
        cx = 6.0 * sx
        box(b, body, f"seat_f_{nm}", cx - 4, cx + 4, 8, 12, -4, 4, "seat", kind="seat")
        box(b, body, f"seat_f_back_{nm}", cx - 4, cx + 4, 12, 24, -6, -4, "seat", kind="seatback")
        box(b, body, f"seat_f_head_{nm}", cx - 2, cx + 2, 24, 28, -6, -4, "seat", kind="seatback")
    box(b, body, "bench", -10, 10, 8, 12, -18, -10, "seat", kind="seat")
    box(b, body, "bench_back", -10, 10, 12, 24, -20, -18, "seat", kind="seatback")
    box(b, body, "console", -2, 2, 8, 14, -4, 10, "dark", kind="plain")
    # V6.6.0: interior: relojes en el tablero, botón de luces (HelicopterEntity.BUTTONS), palanca cíclica y panel de interruptores en el techo
    for k, x in enumerate((-8, -5, 5, 8)):
        box(b, body, f"gauge_{k}", x, x + 2, 15, 17, 7, 8, "metal", kind="plain")
    box(b, body, "btn_light", -1, 1, 15, 17, 7, 8, "lens", kind="plain")
    for sx in (1, -1):
        box(b, body, f"stick_{sx}", 6 * sx - 1, 6 * sx + 1, 8, 15, 5, 6, "dark", kind="plain")
        box(b, body, f"stick_knob_{sx}", 6 * sx - 1, 6 * sx + 1, 15, 17, 5, 6, "red", kind="plain")
    box(b, body, "overhead", -5, 5, 32, 34, 2, 8, "dark", kind="plain")
    for k, x in enumerate((-3, -1, 1, 3)):
        box(b, body, f"switch_{k}", x, x + 1, 31, 32, 4, 6, "metal", kind="plain")

    # ---------------------------------------------------------------- techo: motor, mástil, rotor principal
    box(b, body, "cowl", -8, 8, 36, 40, -18, 2, "paint", kind="cowl")
    box(b, body, "exhaust", -2, 2, 40, 42, -16, -12, "dark", kind="plain")
    box(b, body, "mast", -2, 2, 40, 46, -4, 0, "hub", kind="plain")
    rotor = b.part("rotor", body, (0, 46.0, -2.0))
    b.cube(rotor, "hub", (0, 1.0, 0.0), (8, 3, 8), "hub", kind="plain")
    b.cube(rotor, "blade_a", (0, 0.5, 0.0), (112, 1, 5), "blade", kind="blade")
    blade_b = b.part("blade_b_part", rotor, (0, 1.5, 0.0), (0.0, deg(90.0), 0.0))   # misma huella que blade_a: ahorra atlas
    b.cube(blade_b, "blade_b", (0, 0.0, 0.0), (112, 1, 5), "blade", kind="blade")

    # ---------------------------------------------------------------- cola
    box(b, body, "aft_fairing", -9, 9, 8, 18, -36, -26, "paint", kind="aft")
    box(b, body, "boom_1", -4, 4, 18, 30, BOOM_Z0, -26, "paint", kind="boom")
    box(b, body, "boom_2", -3, 3, 20, 29, -78, BOOM_Z0, "paint", kind="boom2")
    box(b, body, "fin", -1, 1, 29, 46, -80, -72, "navy", kind="fin")
    box(b, body, "fin_lower", -1, 1, 16, 20, -78, -74, "navy", kind="fin")
    lr(b, body, "stab", 1, 11, 24, 25, -76, -70, "paint", kind="stab")
    tail_rotor = b.part("tail_rotor", body, (3.0, 38.0, -76.0))
    b.cube(tail_rotor, "tr_hub", (0, 0, 0), (2, 3, 3), "hub", kind="plain")
    b.cube(tail_rotor, "tr_blade_a", (1.0, 0, 0), (1, 14, 2), "blade", kind="trblade")
    tr_b = b.part("tr_blade_b_part", tail_rotor, (1.5, 0, 0), (deg(90.0), 0.0, 0.0))
    b.cube(tr_b, "tr_blade_b", (0, 0, 0), (1, 14, 2), "blade", kind="trblade")

    # ---------------------------------------------------------------- parabrisas (marco + vidrio inclinado)
    ws = b.part("ws_frame", body, (0, 12.0, 23.0), (-deg(31.0), 0.0, 0.0))
    for sx, nm in ((1, "l"), (-1, "r")):
        b.cube(ws, f"ws_pillar_{nm}", (11.0 * sx, 10.5, 0.0), (2, 21, 2), "paint", kind="pillar", side=sx)
    b.cube(ws, "ws_mid", (0, 10.5, 0.0), (2, 21, 2), "paint", kind="pillar")
    b.cube(ws, "ws_bottom", (0, 0.5, 0.0), (22, 1, 2), "dark", kind="plain")

    # ---------------------------------------------------------------- vidrios (árbol aparte)
    wg = b.part("ws_glass", glass, (0, 12.0, 23.0), (-deg(31.0), 0.0, 0.0))
    b.cube(wg, "windshield_l", (5.5, 10.5, 0.0), (9, 20, 1), "glass", kind="glass", translucent=True)
    b.cube(wg, "windshield_r", (-5.5, 10.5, 0.0), (9, 20, 1), "glass", kind="glass", translucent=True)
    lr(b, glass, "win_f", 11, 12, 20, 33, 1, 11, "glass", kind="glass", translucent=True)
    lr(b, glass, "win_r", 11, 12, 20, 33, -22, -1, "glass", kind="glass", translucent=True)
    box(b, glass, "rear_glass", -8, 8, 22, 32, -26, -25, "glass", kind="glass", translucent=True)
    # ---------------------------------------------------------------- V6.9.0: accesorios de las skins (material 'hub' = el repintado no los toca)
    sb = b.part("siren_btn", body, (0, 0, 0))
    box(b, sb, "btn_siren", 2, 4, 15, 17, 7, 8, "hub", kind="sirenbtn")                 # junto al botón de luces (HelicopterEntity.SIREN_BUTTON)
    bar = b.part("bar", body, (0, 0, 0))                                                # barra de luces del techo (policía y bomberos)
    box(b, bar, "bar_base", -9, 9, 36, 37, 4, 8, "hub", kind="plain")
    box(b, bar, "bar_mid", -1, 1, 37, 39, 4, 8, "hub", kind="plain")
    bar_red = b.part("bar_red", body, (0, 0, 0))
    box(b, bar_red, "bar_red_lamp", 1, 9, 37, 39, 4, 8, "hub", kind="lightbar_r", glow=True)
    bar_blue = b.part("bar_blue", body, (0, 0, 0))
    box(b, bar_blue, "bar_blue_lamp", -9, -1, 37, 39, 4, 8, "hub", kind="lightbar_b", glow=True)
    police = b.part("police", body, (0, 0, 0))                                          # foco gigante en el costado izquierdo
    box(b, police, "spot_arm", 10, 14, 15, 17, 16, 18, "hub", kind="plain")
    box(b, police, "spot", 14, 20, 11, 21, 12, 22, "hub", kind="spot", glow=True)
    fire = b.part("fire", body, (0, 0, 0))                                              # tanques de agua laterales y cañón
    lr(b, fire, "tank", 12, 16, 8, 18, -20, 0, "hub", kind="tank")
    box(b, fire, "cannon_base", -2, 2, 5, 7, 22, 26, "hub", kind="steel")
    box(b, fire, "cannon", -1, 1, 5, 7, 26, 34, "hub", kind="steel")
    news = b.part("news", body, (0, 0, 0))                                              # cámara giratoria, plato y antena
    box(b, news, "turret_base", -2, 2, 7, 8, 23, 27, "hub", kind="plain")
    box(b, news, "turret", -3, 3, 2, 7, 22, 28, "hub", kind="turret")
    box(b, news, "dish_stem", -1, 1, 36, 39, -24, -22, "hub", kind="plain")
    box(b, news, "dish", -5, 5, 39, 41, -26, -20, "hub", kind="dish")
    box(b, news, "antenna", 6, 7, 36, 46, -24, -23, "hub", kind="plain")
    return b


def pose(b, rotor=0.0, tail=0.0, glass=True, body=True):
    for nm in ("police", "fire", "news", "bar", "bar_red", "bar_blue", "siren_btn"):
        b.parts[nm].visible = False                       # accesorios de skins: ocultos en la vista del original
    b.parts["rotor"].rot = [0.0, rotor, 0.0]
    b.parts["tail_rotor"].rot = [tail, 0.0, 0.0]
    b.parts["glass"].visible = glass
    b.parts["body"].visible = body


# ----------------------------------------------------------------------------------------------- pintura
def paint_cube(c, rng):
    kind = c.opts.get("kind", "plain")
    if kind == "glass":
        return glass_faces(c, rng)
    color = MAT_COLOR.get(c.mat, (255, 0, 255))
    gloss = c.mat in ("paint", "navy", "red", "metal")
    faces = vc.base_faces(c, color, rng, gloss=gloss)
    glow = {}
    top = vc.natural_top(faces)
    side = c.opts.get("side", 1)
    outer = "east" if side > 0 else "west"
    inner = "west" if side > 0 else "east"

    if kind == "lamp":
        for f in list(faces):
            faces[f][..., :3] = (255, 244, 214) if c.mat == "lens" else (230, 40, 44)
            glow[f] = solid(faces[f].shape[0], faces[f].shape[1], (255, 244, 214) if c.mat == "lens" else (255, 40, 30), 255)
    elif kind == "door":
        img = faces[outer]                              # el render muestra las dos caras sin espejo
        h, w = img.shape[:2]
        rect(img, 0, h - 4, w, h, NAVY)                 # franja azul inferior
        hline(img, h - 5, RED, alpha=1.0)
        hline(img, 0, (210, 214, 224), alpha=0.8)
        for x in (0, w // 2 - 2, w - 1):
            vline(img, x, WHITE_LO, alpha=0.9)
        txt = DOOR_TXT
        draw_text(img, txt, 3 if side > 0 else w - 3 - text_width(txt), 2, NAVY_LO)
        hx = (19, 24) if side > 0 else (11, 16)         # manija (V3.2.0): junto a la costura, en la parte lisa de la puerta
        rect(img, hx[0], 3, hx[1], 4, HANDLE)
        rect(img, hx[0], 4, hx[1], 5, (60, 64, 72))     # su sombra
        faces[outer] = img
        trim = faces[inner]
        trim[..., :3] = (66, 68, 76)
        hline(trim, 5, (86, 88, 98), alpha=0.9)
        faces[inner] = trim
    elif kind == "boom":
        for f in ("west", "east"):
            img = faces[f]
            h, w = img.shape[:2]
            rect(img, 0, h - 1, w, h, NAVY)
            txt = BOOM_TXT
            tw = text_width(txt, BOOM_SCALE)
            draw_text(img, txt, (w - tw) // 2, 0 if BOOM_SCALE == 2 else 3, NAVY_LO, BOOM_SCALE)
            faces[f] = img
        top[..., :3] = WHITE
        vline(top, top.shape[1] // 2, NAVY, alpha=0.5)
    elif kind == "boom2":
        for f in ("west", "east"):
            img = faces[f]
            rect(img, 0, 0, img.shape[1], 2, NAVY)
            rect(img, 0, img.shape[0] - 2, img.shape[1], img.shape[0], NAVY)
        for f in ("front", "back"):
            faces[f][..., :3] = WHITE_LO
    elif kind == "fin":
        for f in list(faces):
            faces[f][..., :3] = NAVY
        for f in ("west", "east"):
            img = faces[f]
            rect(img, 0, 0, img.shape[1], 3, RED)
            draw_text(img, FIN_TXT, 2, 6, WHITE)
    elif kind == "stab":
        for f in ("front", "back", "west", "east"):
            faces[f][..., :3] = WHITE_LO
        top[..., :3] = WHITE
        vline(top, 0, NAVY, alpha=0.9)
    elif kind in ("lightbar_r", "lightbar_b"):
        col = (255, 36, 28) if kind == "lightbar_r" else (40, 96, 255)
        for ff in list(faces):
            faces[ff][..., :3] = tuple(int(v * 0.55) for v in col)
            glow[ff] = solid(faces[ff].shape[0], faces[ff].shape[1], col, 255)
    elif kind == "sirenbtn":
        for ff in list(faces):
            faces[ff][..., :3] = (214, 32, 30)
    elif kind == "spot":                                   # foco gigante: carcasa oscura y lente grande al frente
        for ff in list(faces):
            faces[ff][..., :3] = (38, 40, 48)
        a = faces["front"]
        rect(a, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (255, 250, 232))
        g = solid(a.shape[0], a.shape[1], (0, 0, 0), 0)
        rect(g, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (255, 250, 232), 255)
        glow["front"] = g
    elif kind == "tank":                                   # tanque de agua rojo con franja blanca
        for ff in list(faces):
            faces[ff][..., :3] = (190, 22, 28)
            a = faces[ff]
            if ff in ("west", "east", "front", "back") and a.shape[0] >= 6:
                rect(a, 0, a.shape[0] // 2 - 1, a.shape[1], a.shape[0] // 2 + 1, (245, 245, 250))
    elif kind == "steel":
        for ff in list(faces):
            faces[ff][..., :3] = (176, 180, 190)
    elif kind == "turret":                                 # cámara de noticias: bola oscura con lente
        for ff in list(faces):
            faces[ff][..., :3] = (30, 32, 38)
        a = faces["front"]
        rect(a, 1, 1, a.shape[1] - 1, a.shape[0] - 1, (70, 120, 190))
        rect(a, 2, 2, 4, 4, (200, 230, 255))
    elif kind == "dish":
        for ff in list(faces):
            faces[ff][..., :3] = (232, 234, 240)
    elif kind == "skid":
        for f in list(faces):
            a = faces[f]
            hline(a, 0, (130, 134, 146), alpha=0.6)
    elif kind == "blade":
        for f in list(faces):
            faces[f][..., :3] = (46, 48, 54)
        # puntas amarillas (cara de arriba y de abajo; la geometría de las palas es larga en x o en z)
        t = top
        rows, cols = t.shape[:2]
        n = 8
        if cols >= rows:
            rect(t, 0, 0, n, rows, (240, 190, 30))
            rect(t, cols - n, 0, cols, rows, (240, 190, 30))
        else:
            rect(t, 0, 0, cols, n, (240, 190, 30))
            rect(t, 0, rows - n, cols, rows, (240, 190, 30))
        b_ = faces["bottom"]
        rows, cols = b_.shape[:2]
        if cols >= rows:
            rect(b_, 0, 0, n, rows, (240, 190, 30))
            rect(b_, cols - n, 0, cols, rows, (240, 190, 30))
        else:
            rect(b_, 0, 0, cols, n, (240, 190, 30))
            rect(b_, 0, rows - n, cols, rows, (240, 190, 30))
    elif kind == "trblade":
        for f in list(faces):
            faces[f][..., :3] = (46, 48, 54)
        for f in ("west", "east"):
            img = faces[f]
            rows, cols = img.shape[:2]
            if rows >= cols:
                rect(img, 0, 0, cols, 2, (240, 190, 30))
                rect(img, 0, rows - 2, cols, rows, (240, 190, 30))
            else:
                rect(img, 0, 0, 2, rows, (240, 190, 30))
                rect(img, cols - 2, 0, cols, rows, (240, 190, 30))
    elif kind == "cowl":
        for f in ("west", "east", "front", "back"):
            a = faces[f]
            hline(a, 0, (214, 218, 228), alpha=0.7)
        for f in ("west", "east"):                                     # rejillas de ventilación
            a = faces[f]
            for y in range(1, a.shape[0] - 1, 2):
                a[y, 3:a.shape[1] - 3, :3] = (120, 124, 136)
        for y in range(0, top.shape[0], 3):
            top[y, 2:top.shape[1] - 2, :3] *= 0.9
    elif kind == "roof":
        rows, cols = top.shape[:2]
        vline(top, 0, (206, 210, 220), alpha=0.5)
        vline(top, cols - 1, (206, 210, 220), alpha=0.5)
        for y in range(rows):
            if y % 8 == 0:
                top[y, :, :3] *= 0.9
        rect(top, 0, 0, cols, 4, NAVY)                                     # franja azul sobre el parabrisas
        faces["bottom"][..., :3] = (150, 152, 162)
    elif kind == "brow":
        for f in ("front", "back", "west", "east"):
            hline(faces[f], 0, (214, 218, 228), alpha=0.7)
        a = faces["front"]
        rect(a, 0, a.shape[0] - 3, a.shape[1], a.shape[0], NAVY)
        top[..., :3] = WHITE
    elif kind == "nose":
        a = faces["front"]
        rows, cols = a.shape[:2]
        rect(a, 0, rows - 3, cols, rows, NAVY)
        hline(a, rows - 4, RED)
        for f in ("west", "east"):
            img = faces[f]
            rect(img, 0, img.shape[0] - 3, img.shape[1], img.shape[0], NAVY)
            hline(img, img.shape[0] - 4, RED)
    elif kind == "nose_tip":
        for f in ("front", "west", "east"):
            a = faces[f]
            rect(a, 0, a.shape[0] - 2, a.shape[1], a.shape[0], NAVY)
        a = faces["front"]
        rect(a, a.shape[1] // 2 - 2, 0, a.shape[1] // 2 + 2, 2, (255, 246, 200))     # luz de aterrizaje
    elif kind == "chin":
        for f in list(faces):
            faces[f][..., :3] = NAVY
    elif kind == "aft":
        for f in ("west", "east"):
            img = faces[f]
            rect(img, 0, img.shape[0] - 3, img.shape[1], img.shape[0], NAVY)
        faces["back"][..., :3] = WHITE_LO
        faces["bottom"][..., :3] = (60, 62, 70)
    elif kind == "pillar":
        for f in ("west", "east", "front", "back", "bottom"):
            faces[f][..., :3] = (66, 68, 76)
        img = faces[outer]
        img[..., :3] = WHITE
        noise(img, rng, 0.03)
        faces[outer] = img
        top[..., :3] = WHITE
        if c.name.startswith("ws_"):
            for f in ("west", "east"):
                faces[f][..., :3] = WHITE
    elif kind == "rear_wall":
        for f in ("front", "bottom"):
            faces[f][..., :3] = (66, 68, 76)
        a = faces["back"]
        rect(a, 0, a.shape[0] - 3, a.shape[1], a.shape[0], NAVY)
        hline(a, a.shape[0] - 4, RED)
    elif kind == "dash":
        rows, cols = top.shape[:2]
        top[..., :3] = (30, 31, 36)
        rect(top, 1, 1, cols - 1, 2, (50, 52, 60))
        a = faces["back"]
        h, w = a.shape[:2]
        a[..., :3] = (32, 33, 38)
        for k, x in enumerate((2, 8, 14)):                                  # relojes
            rect(a, x, 1, x + 4, 5, (16, 16, 20))
            rect(a, x + 1, 2, x + 3, 4, (230, 150, 40) if k != 1 else (60, 190, 120))
        rect(a, 7, 4, 13, 6, (44, 104, 176))                                # pantalla
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

    vc.store_top(faces, top)
    if c.mat in ("paint", "navy") and kind not in ("pillar", "boom", "boom2", "fin", "blade", "trblade"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.05, 0.10)
    return faces, glow


def build_all():
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3250)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def raw_atlas():
    """Textura sin recortar (floats) con la paleta actual; para calcular las máscaras de color."""
    b = build()
    vc.pack(b.cubes, TEX_W, TEX_H)
    return vc.paint_atlas(b.cubes, TEX_W, TEX_H, paint_cube, seed=3250, raw=True)[0]


# ----------------------------------------------------------------------------------------------- Java
def java_model(b):
    body = vc.java_tree(b)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.HelicopterEntity;
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
 * Helicóptero BOING HX-6 (V3.0.0). ARCHIVO GENERADO por tools/v300/helicopter.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de {TEX_W}x{TEX_H}. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios). El renderizador los dibuja en pasadas distintas con
 * showGlass(false) y showGlass(true), así el vidrio translúcido se mezcla bien con lo que hay detrás (asientos, tablero).
 * Piezas animadas: "rotor" (rotor principal, gira en el eje vertical) y "tail_rotor" (rotor de cola, gira en el eje x).
 */
public class HelicopterModel extends EntityModel<HelicopterEntity> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "helicopter"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart rotor;
    private final ModelPart tailRotor;
    private final ModelPart police;
    private final ModelPart fire;
    private final ModelPart news;
    private final ModelPart bar;
    private final ModelPart barRed;
    private final ModelPart barBlue;
    private final ModelPart sirenBtn;
    private boolean barsOn;

    public HelicopterModel(ModelPart root) {{
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.rotor = this.body.getChild("rotor");
        this.tailRotor = this.body.getChild("tail_rotor");
        this.police = this.body.getChild("police");
        this.fire = this.body.getChild("fire");
        this.news = this.body.getChild("news");
        this.bar = this.body.getChild("bar");
        this.barRed = this.body.getChild("bar_red");
        this.barBlue = this.body.getChild("bar_blue");
        this.sirenBtn = this.body.getChild("siren_btn");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
    }}

    /** rotorAngle = giro del rotor principal en radianes; tailAngle = giro del rotor de cola en radianes. */
    public void pose(float rotorAngle, float tailAngle) {{
        this.rotor.yRot = rotorAngle;
        this.tailRotor.xRot = tailAngle;
    }}

    /** V6.9.0: accesorios de la skin: 0 = ninguno, 1 = policía, 2 = bomberos, 3 = noticias. */
    public void setSkin(int skin) {{
        this.police.visible = skin == 1;
        this.fire.visible = skin == 2;
        this.news.visible = skin == 3;
        this.barsOn = skin == 1 || skin == 2;
        this.bar.visible = this.barsOn;
        this.sirenBtn.visible = this.barsOn;
        this.setBarPhase(-2);
    }}

    /** V6.9.0: -2 = las dos mitades de la barra (pasada normal); en la pasada de brillo, 0 = roja, 1 = azul, otro = ninguna. */
    public void setBarPhase(int phase) {{
        this.barRed.visible = this.barsOn && (phase == -2 || phase == 0);
        this.barBlue.visible = this.barsOn && (phase == -2 || phase == 1);
    }}

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {{
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }}

    @Override
    public void setupAnim(HelicopterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
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
VIEWS = [(145, 16), (215, 16), (90, 3), (0, 6), (180, 6), (180, 80)]
CENTER = (0, 22.0, -22)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--rotor", type=float, default=0.5)
    ap.add_argument("--tail", type=float, default=0.3)
    args = ap.parse_args()

    b, tex, glow = build_all()
    used = max(c.v + int(c.h + c.d) for c in b.cubes)
    print(f"{len(b.cubes)} cubos, atlas usado hasta y={used}/{TEX_H}")
    pose(b, args.rotor, args.tail)
    if args.preview:
        imgs = vc.views(b, tex, glow, (TEX_W, TEX_H), VIEWS, CENTER, 2.6, light_on=False, size=(420, 330))
        vc.sheet(imgs).convert("RGB").save(args.preview)
        big = tex.resize((TEX_W * 3, TEX_H * 3), Image.NEAREST)
        bg = Image.new("RGBA", big.size, (90, 96, 108, 255))
        bg.alpha_composite(big)
        bg.convert("RGB").save(Path(args.preview).with_name("atlas_" + Path(args.preview).name))
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        tex.save(ASSETS / "textures/entity/helicopter.png")
        glow.save(ASSETS / "textures/entity/helicopter_glow.png")
        (JAVA / "client/HelicopterModel.java").write_text(java_model(b), encoding="utf-8")
        pose(b, 0.0, 0.0)
        icon = vc.make_icon(b, tex, glow, (TEX_W, TEX_H), 140, 18, CENTER, 4.0, outline=(8, 12, 30, 255), brighten=1.05)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / "textures/item/helicopter.png")
        print("escrito: textura, HelicopterModel.java, ícono")


if __name__ == "__main__":
    main()
