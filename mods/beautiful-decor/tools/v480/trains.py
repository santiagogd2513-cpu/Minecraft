#!/usr/bin/env python3
"""Tren de carga (V4.8.0): locomotora y vagones de carbón, alimentos y minerales. Diseño propio (marcas de parodia: ninguna, sin letras de marcas).

Genera para cada coche: textura (textures/entity/X.png), máscaras de pintura para la tecla R (X_paint.png y X_regions.png), ícono del ítem
(textures/item/X.png) y UN archivo Java con los cuatro modelos (client/TrainCarModel.java).

Medidas en unidades de 1/8 de bloque (el renderizador agranda el modelo 2 veces). Coordenadas amigables: x = izquierda (+), y = altura sobre
la cabeza del riel, z = hacia delante (+). Los ejes están a +-14 (vagón) o +-17 (locomotora) del centro; el largo del cuerpo es 52 / 58.

Uso (desde la raíz del proyecto):
  python3 -m tools.v480.trains --preview vista.png
  python3 -m tools.v480.trains --write
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

from ..v210 import vcommon as vc
from ..v210.vcommon import ASSETS, JAVA, deg, edges, hline, noise, rect, solid, vline

TEX_W, TEX_H = 1024, 512
BIG = ("coach_low", "coach_mid", "coach_high", "metro_head", "metro_car")      # los vagones de pasajeros (160 de largo y 190 cajas) necesitan un atlas de 1024 de alto


def H(name):
    return 1024 if name in BIG else TEX_H
UNITS_PER_BLOCK = 8
WHEEL_NAMES = [f"w{i}" for i in range(8)]

# ---- colores de referencia (0xRRGGBB): deben coincidir con VehiclePaint.Kind y VehiclePaintExtras
HANDLE_REF = (232, 192, 32)
RIM_REF = (138, 142, 152)
SPECS = {
    "freight_loco": dict(
        body=(200, 70, 30), extras=[("accent", (240, 192, 32)), ("black", (28, 29, 34)), ("chrome", (184, 188, 200)), ("glass", (42, 64, 88)),
                                    ("interior", (106, 90, 72)), ("headlight", (246, 242, 216)), ("taillight", (200, 34, 42)), ("details", (58, 61, 70))]),
    "coal_wagon": dict(
        body=(58, 62, 70), extras=[("accent", (200, 70, 30)), ("black", (28, 29, 34)), ("interior", (42, 44, 50)), ("details", (80, 84, 94))]),
    "food_wagon": dict(
        body=(63, 128, 56), extras=[("accent", (242, 239, 228)), ("black", (28, 29, 34)), ("interior", (154, 116, 72)), ("details", (107, 75, 42))]),
    "mineral_wagon": dict(
        body=(165, 86, 43), extras=[("accent", (224, 176, 48)), ("black", (28, 29, 34)), ("interior", (74, 78, 88)), ("details", (106, 58, 30))]),
}
SPECS.update({
    "steam_classic": dict(
        body=(36, 88, 62), extras=[("accent", (214, 170, 56)), ("black", (24, 25, 29)), ("chrome", (184, 188, 200)), ("glass", (42, 64, 88)),
                                   ("interior", (106, 90, 72)), ("headlight", (246, 242, 216)), ("taillight", (200, 34, 42)), ("details", (150, 52, 40))]),
    "steam_modern": dict(
        body=(34, 62, 118), extras=[("accent", (236, 236, 240)), ("black", (24, 25, 29)), ("chrome", (184, 188, 200)), ("glass", (42, 64, 88)),
                                    ("interior", (106, 90, 72)), ("headlight", (246, 242, 216)), ("taillight", (200, 34, 42)), ("details", (70, 74, 84))]),
    "coach_low": dict(
        body=(122, 80, 50), extras=[("accent", (220, 190, 96)), ("black", (28, 29, 34)), ("glass", (42, 64, 88)), ("interior", (96, 80, 62)),
                                    ("seat", (146, 104, 62)), ("details", (70, 52, 38))]),
    "coach_mid": dict(
        body=(46, 98, 142), extras=[("accent", (240, 240, 244)), ("black", (28, 29, 34)), ("glass", (42, 64, 88)), ("interior", (150, 140, 126)),
                                    ("seat", (52, 72, 124)), ("details", (80, 86, 96))]),
    "coach_high": dict(
        body=(112, 28, 42), extras=[("accent", (226, 186, 66)), ("black", (28, 29, 34)), ("glass", (42, 64, 88)), ("interior", (116, 84, 56)),
                                    ("seat", (140, 40, 52)), ("details", (60, 40, 30))]),
    "metro_head": dict(
        body=(232, 234, 240), extras=[("accent", (28, 120, 210)), ("black", (24, 25, 29)), ("chrome", (184, 188, 200)), ("glass", (42, 64, 88)),
                                      ("interior", (96, 102, 112)), ("headlight", (246, 242, 216)), ("taillight", (200, 34, 42)), ("details", (120, 126, 138))]),
    "metro_car": dict(
        body=(232, 234, 240), extras=[("accent", (28, 120, 210)), ("black", (28, 29, 34)), ("glass", (42, 64, 88)), ("interior", (150, 156, 166)),
                                      ("seat", (30, 90, 160)), ("details", (120, 126, 138))]),
})
FIXED = {"tire": (22, 22, 25), "rail_steel": (150, 154, 164)}


def material(spec, mat):
    """(color, canal) del material. canal: ('main', 0|1|2), ('extra', índice desde 1) o None."""
    if mat == "body":
        return spec["body"], ("main", 0)
    if mat == "handle":
        return HANDLE_REF, ("main", 1)
    if mat == "rim":
        return RIM_REF, ("main", 2)
    for i, (key, rgb) in enumerate(spec["extras"]):
        if key == mat:
            return rgb, ("extra", i + 1)
    if mat in FIXED:
        return FIXED[mat], None
    raise KeyError(mat)


# ---------------------------------------------------------------------------------------------- geometría
# V4.9.0: tamaños realistas (1 unidad = 1/8 m): locomotora 20 x 3 x 4,5 m, vagones 14 x 3 x (3,4 .. 4,5) m. Ruedas de 1 m.
LOCO_L, WAGON_L = 160, 112
LOCO_BOGIE, WAGON_BOGIE = 44, 36          # ejes de los bogies a +- este valor del centro (distancia entre bogies 11 m / 9 m)
WHEEL_R = 4                               # radio de rueda (8 unidades = 1 m de diámetro)


def box(b, part, name, x0, x1, y0, y1, z0, z1, mat, **opts):
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    for v in (w, h, d):
        assert abs(v - round(v)) < 1e-6 and v >= 1, (name, w, h, d)
    return b.cube(part, name, ((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0), (int(round(w)), int(round(h)), int(round(d))), mat, **opts)


def sym(b, part, name, x0, x1, y0, y1, z0, z1, mat, **opts):
    """Caja a la izquierda (x0..x1 con x0 >= 0) y su espejo."""
    box(b, part, name + "_l", x0, x1, y0, y1, z0, z1, mat, **opts)
    box(b, part, name + "_r", -x1, -x0, y0, y1, z0, z1, mat, **opts)


def undercarriage(b, body, bogie_z, length):
    """Chasis negro, dos bogies con dos ejes cada uno (8 ruedas que giran), topes y enganches."""
    half = length // 2
    box(b, body, "frame", -10, 10, 6, 9, -half, half, "black", kind="frame")
    has_chrome = "chrome" in [e[0] for e in b.extras]
    for i, bz in enumerate((-bogie_z, bogie_z)):
        box(b, body, f"bolster{i}", -9, 9, 5, 7, bz - 15, bz + 15, "black", kind="frame")
        sym(b, body, f"side{i}", 3, 4, 3, 7, bz - 14, bz + 14, "black", kind="frame")
        for j, dz in enumerate((-10, 10)):
            n = i * 4 + j * 2
            for k, sx in enumerate((1, -1)):
                w = b.part(f"w{n + k}", body, (sx * 2.2, WHEEL_R, bz + dz))
                for q in range(2):
                    p = b.part(f"w{n + k}_t{q}", w, (0, 0, 0), (deg(45.0 * q), 0, 0))
                    b.cube(p, f"wheel{n + k}_{q}", (0, 0, 0), (2, 2 * WHEEL_R, 2 * WHEEL_R), "rim", kind="wheel", wheel=True)
                b.cube(w, f"hub{n + k}", (sx * 1.0, 0, 0), (1, 3, 3), "chrome" if has_chrome else "black", kind="hub")
    for sz in (-1, 1):
        for sx in (-1, 1):
            x0 = 6 * sx - 1
            z0, z1 = (half, half + 3) if sz > 0 else (-half - 3, -half)
            box(b, body, f"buffer_{sx}_{sz}", x0, x0 + 2, 7, 10, z0, z1, "black", kind="frame")
        box(b, body, f"coupler_{sz}", -1, 1, 7, 9, half + 1 if sz > 0 else -half - 4, half + 4 if sz > 0 else -half - 1, "black", kind="frame")


def handrail_ladder(b, body, x_sign, z, y0, y1):
    """Escalerita de manijas (canal 'Manijas')."""
    x0 = 13 if x_sign > 0 else -14
    box(b, body, f"ladder_{x_sign}_{z}", x0, x0 + 1, y0, y1, z, z + 1, "handle", kind="handle")
    box(b, body, f"ladder2_{x_sign}_{z}", x0, x0 + 1, y0, y1, z + 5, z + 6, "handle", kind="handle")
    for q in range(y0 + 3, y1 - 1, 5):
        box(b, body, f"step_{x_sign}_{z}_{q}", x0, x0 + 1, q, q + 1, z, z + 6, "handle", kind="handle")


def wagon(b, kind):
    """Vagón abierto de 14 m: piso, paredes (altura según el tipo), nervaduras, franja de acento y escalerillas."""
    body = b.part("body", b.root, (0, 0, 0))
    b.part("glass", b.root, (0, 0, 0))
    undercarriage(b, body, WAGON_BOGIE, WAGON_L)
    half = WAGON_L // 2
    top = {"coal": 27, "food": 21, "mineral": 31}[kind]
    box(b, body, "deck", -12, 12, 9, 11, -half, half, "interior", kind="floor")
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (11, 12) if sx > 0 else (-12, -11)
        if kind == "food":
            for q, (ya, yb) in enumerate(((11, 14), (15, 18), (19, 21))):
                box(b, body, f"wall_{nm}{q}", x0, x1, ya, yb, -half, half, "body", kind="seam")
            for zz in range(-half, half, 14):
                box(b, body, f"post_{nm}{zz}", x0 - (0 if sx > 0 else 1) + (1 if sx > 0 else 0), x1 + (1 if sx > 0 else 0) - (0 if sx > 0 else 1), 11, top + 1, zz, zz + 2, "details", kind="frame")
        else:
            box(b, body, f"wall_{nm}", x0, x1, 11, top, -half, half, "body", kind="seam")
            box(b, body, f"rim_{nm}", x0 - (1 if sx > 0 else 0), x1 + (0 if sx > 0 else 1), top, top + 1, -half, half, "details", kind="frame")
            xr0, xr1 = (12, 13) if sx > 0 else (-13, -12)
            for zz in range(-half + 6, half - 5, 14):
                box(b, body, f"rib_{nm}{zz}", xr0, xr1, 11, top, zz, zz + 3, "details", kind="frame")
            mid = (11 + top) // 2
            box(b, body, f"band_{nm}", xr0, xr1, mid, mid + 4, -half + 8, half - 8, "accent", kind="stripe")
    for sz, nm in ((1, "f"), (-1, "b")):
        z0, z1 = (half - 1, half) if sz > 0 else (-half, -half + 1)
        etop = top + (8 if kind == "food" else 0)
        box(b, body, f"end_{nm}", -11, 11, 11, etop, z0, z1, "body", kind="seam")
        box(b, body, f"endrim_{nm}", -12, 12, etop, etop + 1, z0 - (1 if sz < 0 else 0), z1 + (1 if sz > 0 else 0), "details", kind="frame")
    if kind == "mineral":
        for zz in (-28, 0, 28):
            box(b, body, f"brace_l{zz}", 11, 12, 11, top, zz, zz + 2, "accent", kind="stripe")
            box(b, body, f"brace_r{zz}", -12, -11, 11, top, zz, zz + 2, "accent", kind="stripe")
    if kind == "coal":
        sym(b, body, "skirt", 8, 12, 7, 11, -44, 44, "black", kind="frame")
    for sx in (1, -1):
        for zz in (-half + 4, half - 10):
            handrail_ladder(b, body, sx, zz, 11, top)


BUTTONS = {}     # V6.2.0: nombre -> (x, y, z) en metros del botón rojo del freno de emergencia (se genera TrainSeats.button)


def cab_desk(b, body, z0, z1, y0, name):
    """V6.2.0: pupitre de cabina: mesa, relojes, palancas de aceleración y freno y el botón rojo de emergencia (centro de la mesa)."""
    zc = (z0 + z1) // 2
    box(b, body, "console", -9, 9, y0, 21, z0, z1, "interior", kind="dash")
    box(b, body, "console_top", -9, 9, 21, 22, z0, z1, "details", kind="dash")
    box(b, body, "btn_housing", -2, 2, 22, 23, zc - 2, zc + 2, "black", kind="frame")
    box(b, body, "btn_red", -1, 1, 23, 25, zc - 1, zc + 1, "taillight", kind="light")
    for nm, x in (("g0", -8), ("g1", -5), ("g2", 4), ("g3", 7)):
        box(b, body, "gauge_" + nm, x, x + 2, 22, 24, zc - 1, zc, "chrome", kind="metal")
    box(b, body, "throttle_base", 5, 8, 22, 23, zc - 2, zc + 2, "black", kind="frame")
    box(b, body, "throttle", 6, 7, 23, 28, zc - 1, zc, "chrome", kind="metal")
    box(b, body, "brake_base", -8, -5, 22, 23, zc - 2, zc + 2, "black", kind="frame")
    box(b, body, "brake_lever", -7, -6, 23, 28, zc - 1, zc, "chrome", kind="metal")
    BUTTONS[name] = (0.0, 24.0 / 8.0, zc / 8.0)


def loco(b):
    """Locomotora diésel de carga de 20 m: cabina atrás, capó largo y nariz adelante."""
    body = b.part("body", b.root, (0, 0, 0))
    glass = b.part("glass", b.root, (0, 0, 0))
    undercarriage(b, body, LOCO_BOGIE, LOCO_L)
    box(b, body, "tank", -8, 8, 2, 6, -26, 26, "black", kind="frame")
    box(b, body, "deck", -12, 12, 10, 12, -80, 80, "details", kind="floor")
    # ---- cabina: z -80..-48, paredes con ventanas, techo con ventilador
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (11, 12) if sx > 0 else (-12, -11)
        box(b, body, f"cab_{nm}_low", x0, x1, 12, 22, -80, -48, "body", kind="seam")
        box(b, body, f"cab_{nm}_top", x0, x1, 33, 36, -80, -48, "body", kind="seam")
        box(b, body, f"cab_{nm}_p0", x0, x1, 22, 33, -80, -74, "body", kind="seam")
        box(b, body, f"cab_{nm}_p1", x0, x1, 22, 33, -54, -48, "body", kind="seam")
        box(b, glass, f"cab_{nm}_win", x0, x1, 22, 33, -74, -54, "glass", kind="glass")
        xs0, xs1 = (12, 13) if sx > 0 else (-13, -12)
        box(b, body, f"stripe_{nm}", xs0, xs1, 15, 19, -80, 74, "accent", kind="stripe")
    box(b, body, "cab_back", -11, 11, 12, 36, -80, -79, "body", kind="seam")
    box(b, body, "cab_front_low", -11, 11, 12, 22, -49, -48, "body", kind="seam")
    box(b, body, "cab_front_top", -11, 11, 34, 36, -49, -48, "body", kind="seam")
    box(b, glass, "cab_front_win", -11, 11, 22, 34, -49, -48, "glass", kind="glass")
    box(b, body, "cab_roof", -13, 13, 36, 38, -82, -46, "body", kind="roof")
    sym(b, body, "roof_stripe", 10, 13, 38, 39, -82, -46, "accent", kind="stripe")
    box(b, body, "cab_fan", -5, 5, 38, 41, -70, -56, "black", kind="fan")
    # interior: piso, pupitre, asiento
    box(b, body, "cab_floor", -11, 11, 12, 13, -79, -49, "interior", kind="floor")
    cab_desk(b, body, -54, -50, 13, "freight_loco")
    box(b, body, "seat", 3, 9, 13, 22, -68, -62, "interior", kind="seat")
    box(b, body, "seat_back", 3, 9, 22, 30, -69, -68, "interior", kind="seat")
    box(b, body, "seat2", -9, -3, 13, 22, -68, -62, "interior", kind="seat")
    box(b, body, "seat2_back", -9, -3, 22, 30, -69, -68, "interior", kind="seat")
    box(b, body, "cab_light", -3, 3, 35, 36, -66, -58, "headlight", kind="light")
    box(b, body, "cab_locker", -9, 9, 13, 30, -79, -78, "details", kind="frame")
    # ---- capó y nariz
    box(b, body, "hood", -10, 10, 12, 22, -48, 60, "body", kind="seam")
    box(b, body, "hood_top", -8, 8, 22, 24, -40, 52, "details", kind="vent")
    box(b, body, "nose", -11, 11, 12, 22, 60, 74, "body", kind="seam")
    box(b, body, "grille", -8, 8, 14, 22, 74, 75, "black", kind="grille")
    sym(b, body, "headlight", 7, 10, 21, 24, 74, 76, "headlight", kind="light")
    sym(b, body, "taillight", 6, 9, 14, 17, -82, -80, "taillight", kind="light")
    for zz in (0, 24):
        box(b, body, f"stack{zz}", -3, 3, 24, 30, zz, zz + 6, "black", kind="frame")
        box(b, body, f"stack_cap{zz}", -4, 4, 30, 31, zz - 1, zz + 7, "chrome", kind="metal")
    # ---- barandales a lo largo del capó
    for sx in (1, -1):
        x0 = 11 if sx > 0 else -12
        for zz in range(-44, 61, 16):
            box(b, body, f"post_{sx}_{zz}", x0, x0 + 1, 12, 21, zz, zz + 1, "handle", kind="handle")
        box(b, body, f"rail_{sx}", x0, x0 + 1, 21, 22, -44, 61, "handle", kind="handle")
        handrail_ladder(b, body, sx, -76, 12, 30)


# ---------------------------------------------------------------------------------------------- V5.0.0: tren de pasajeros
STEAM_L, COACH_L = 176, 160
STEAM_BOGIE, COACH_BOGIE = 56, 56          # ejes de los bogies a +- este valor del centro (14 m entre bogies)
SEATS = {"freight_loco": [(0.6, 2.15, -8.0), (-0.6, 2.15, -8.0)]}                                 # nombre -> lista de (x, y, z) en metros: dónde se sienta quien va montado


def wheel(b, body, n, x, z, r):
    """Rueda w<n> de radio r (unidades), con centro a esa altura sobre el riel; gira en x. Guarda su radio para la velocidad del giro."""
    w = b.part(f"w{n}", body, (x, r, z))
    steps = 2 if r <= 4 else 3
    for q in range(steps):
        p = b.part(f"w{n}_t{q}", w, (0, 0, 0), (deg(90.0 / steps * q), 0, 0))
        b.cube(p, f"wheel{n}_{q}", (0, 0, 0), (2, 2 * r, 2 * r), "rim", kind="wheel", wheel=True)
    b.cube(w, f"hub{n}", (0, 0, 0), (3, 3, 3), "chrome" if "chrome" in [e[0] for e in b.extras] else "black", kind="hub")
    if not hasattr(b, "wheel_radius"):
        b.wheel_radius = {}
    b.wheel_radius[f"w{n}"] = r


def round_box(b, body, name, yc, zc, s, length, mat, **opts):
    """Cilindro aproximado (caldera): tres cuadrados girados 30 grados entre sí."""
    for q in range(3):
        p = b.part(f"{name}_r{q}", body, (0, yc, zc), (0, 0, deg(30.0 * q)))
        b.cube(p, f"{name}_c{q}", (0, 0, 0), (s, s, length), mat, **opts)


def steam(b, modern):
    """Locomotora de vapor con ténder de 22 m: cabina atrás, caldera, ténder con el carbón. Clásica (chimenea, domos) o aerodinámica."""
    body = b.part("body", b.root, (0, 0, 0))
    b.part("glass", b.root, (0, 0, 0))
    glass = b.parts["glass"]
    # ---- chasis y ruedas: 3 pares motrices grandes, 1 par delantero, 4 pares del ténder
    box(b, body, "frame", -10, 10, 8, 11, -44, 86, "black", kind="frame")
    box(b, body, "tframe", -10, 10, 6, 9, -88, -46, "black", kind="frame")
    n = 0
    for z in (8, 26, 44):
        for sx in (1, -1):
            wheel(b, body, n, sx * 11, z, 7)
            n += 1
    for sx in (1, -1):
        wheel(b, body, n, sx * 6, 76, 4)
        n += 1
    for z in (-52, -62, -72, -82):
        for sx in (1, -1):
            wheel(b, body, n, sx * 11, z, 4)
            n += 1
    for sx in (1, -1):
        x0 = 12 if sx > 0 else -13
        box(b, body, f"siderod{sx}", x0, x0 + 1, 6, 8, 8, 44, "chrome", kind="metal")
        x1 = 13 if sx > 0 else -14
        box(b, body, f"mainrod{sx}", x1, x1 + 1, 6, 8, 26, 60, "chrome", kind="metal")
        xc = 11 if sx > 0 else -14
        box(b, body, f"cyl{sx}", xc, xc + 3, 5, 12, 56, 68, "details", kind="frame")
    # ---- plataforma
    box(b, body, "deck", -12, 12, 10, 12, -44, 20, "details", kind="floor")
    # ---- cabina: z -36..20
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (11, 12) if sx > 0 else (-12, -11)
        box(b, body, f"cab_{nm}_low", x0, x1, 12, 26, -36, 20, "body", kind="seam")
        box(b, body, f"cab_{nm}_top", x0, x1, 38, 44, -36, 20, "body", kind="seam")
        box(b, body, f"cab_{nm}_p0", x0, x1, 26, 38, -36, -26, "body", kind="seam")
        box(b, body, f"cab_{nm}_p1", x0, x1, 26, 38, 10, 20, "body", kind="seam")
        box(b, glass, f"cab_{nm}_win", x0, x1, 26, 38, -26, 10, "glass", kind="glass")
        xs0, xs1 = (12, 13) if sx > 0 else (-13, -12)
        box(b, body, f"stripe_{nm}", xs0, xs1, 18, 22, -36, 20, "accent", kind="stripe")
    box(b, body, "cab_roof", -13, 13, 44, 46, -40, 22, "body", kind="roof")
    sym(b, body, "roof_stripe", 10, 13, 46, 47, -40, 22, "accent", kind="stripe")
    box(b, body, "cab_floor", -11, 11, 12, 13, -35, 19, "interior", kind="floor")
    box(b, body, "backhead", -8, 8, 13, 28, 13, 19, "black", kind="frame")
    box(b, body, "gauge", -2, 2, 28, 31, 14, 18, "chrome", kind="metal")
    # V6.2.0: panel en la cara de la caldera: relojes, palancas y botón rojo de emergencia
    box(b, body, "btn_housing", -2, 2, 20, 24, 11, 13, "details", kind="frame")
    box(b, body, "btn_red", -1, 1, 21, 23, 9, 11, "taillight", kind="light")
    box(b, body, "gauge_l", -7, -4, 22, 26, 12, 13, "chrome", kind="metal")
    box(b, body, "gauge_r", 4, 7, 22, 26, 12, 13, "chrome", kind="metal")
    box(b, body, "regulator", 5, 6, 14, 20, 11, 13, "chrome", kind="metal")
    box(b, body, "brake_valve", -6, -5, 14, 20, 11, 13, "chrome", kind="metal")
    BUTTONS["steam_classic" if not modern else "steam_modern"] = (0.0, 22.0 / 8.0, 10.0 / 8.0)
    box(b, body, "seat", 3, 9, 13, 22, -12, -6, "interior", kind="seat")
    box(b, body, "seat_back", 3, 9, 22, 30, -13, -12, "interior", kind="seat")
    box(b, body, "seat2", -9, -3, 13, 22, -12, -6, "interior", kind="seat")
    box(b, body, "seat2_back", -9, -3, 22, 30, -13, -12, "interior", kind="seat")
    sym(b, body, "taillight", 6, 9, 14, 17, -90, -88, "taillight", kind="light")
    # ---- caldera y delantera
    if not modern:
        round_box(b, body, "boiler", 22, 42, 18, 44, "body", kind="seam")
        round_box(b, body, "smokebox", 22, 74, 19, 20, "black", kind="frame")
        for zz in (28, 44, 58):
            round_box(b, body, f"band{zz}", 22, zz, 20, 2, "accent", kind="stripe")
        box(b, body, "chimney", -3, 3, 33, 47, 70, 76, "black", kind="frame")
        box(b, body, "chimney_cap", -4, 4, 47, 49, 69, 77, "chrome", kind="metal")
        box(b, body, "dome1", -4, 4, 33, 43, 36, 44, "accent", kind="metal")
        box(b, body, "dome2", -3, 3, 33, 40, 52, 58, "chrome", kind="metal")
        box(b, body, "headlamp", -3, 3, 35, 40, 78, 82, "headlight", kind="light")
        box(b, body, "door", -8, 8, 15, 30, 84, 85, "black", kind="grille")
    else:
        box(b, body, "shroud", -10, 10, 12, 34, 20, 76, "body", kind="seam")
        box(b, body, "shroud_top", -7, 7, 34, 37, 20, 70, "body", kind="roof")
        box(b, body, "nose1", -9, 9, 12, 32, 76, 82, "body", kind="seam")
        box(b, body, "nose2", -7, 7, 14, 28, 82, 86, "body", kind="seam")
        box(b, body, "nose3", -4, 4, 16, 24, 86, 88, "black", kind="frame")
        sym(b, body, "skirt", 10, 13, 14, 24, 4, 76, "body", kind="seam")
        sym(b, body, "swoosh", 12, 13, 18, 22, 4, 80, "accent", kind="stripe")
        box(b, body, "vent", -3, 3, 37, 39, 56, 64, "black", kind="vent")
        box(b, body, "headlamp", -3, 3, 26, 30, 88, 90, "headlight", kind="light")
    # ---- ténder
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (11, 12) if sx > 0 else (-12, -11)
        box(b, body, f"tender_{nm}", x0, x1, 11, 28, -88, -46, "body", kind="seam")
        box(b, body, f"tender_rim_{nm}", x0 - (1 if sx > 0 else 0), x1 + (0 if sx > 0 else 1), 28, 29, -88, -46, "details", kind="frame")
        xs0, xs1 = (12, 13) if sx > 0 else (-13, -12)
        box(b, body, f"tender_stripe_{nm}", xs0, xs1, 16, 20, -86, -48, "accent", kind="stripe")
    box(b, body, "tender_deck", -12, 12, 9, 11, -88, -46, "interior", kind="floor")
    box(b, body, "tender_front", -11, 11, 11, 34, -47, -46, "body", kind="seam")
    box(b, body, "tender_back", -11, 11, 11, 26, -88, -87, "body", kind="seam")
    box(b, body, "drawbar", -2, 2, 9, 11, -46, -36, "black", kind="frame")
    box(b, body, "tender_ladder", 12, 13, 12, 28, -50, -49, "handle", kind="handle")
    for sx in (1, -1):
        handrail_ladder(b, body, sx, -34, 12, 30)
    # ---- topes y enganche
    for sz in (-1, 1):
        zz = 86 if sz > 0 else -91
        for sx in (-1, 1):
            x0 = 6 * sx - 1
            box(b, body, f"buffer_{sx}_{sz}", x0, x0 + 2, 7, 10, zz, zz + 3, "black", kind="frame")
        box(b, body, f"coupler_{sz}", -1, 1, 7, 9, zz + (0 if sz > 0 else 0), zz + 3, "black", kind="frame")
    box(b, body, "pilot", -10, 10, 5, 8, 88, 91, "black", kind="frame")
    SEATS["steam_classic" if not modern else "steam_modern"] = [(0.6, 2.15, -1.0), (-0.6, 2.15, -1.0)]


COACH_STYLE = {
    # alto de ventana (abajo, arriba), cuántas, ancho, paso, primera z
    "low": dict(win=(22, 31), n=8, wd=12, pitch=16, z0=-62),
    "mid": dict(win=(21, 33), n=6, wd=16, pitch=20, z0=-58),
    "high": dict(win=(20, 34), n=6, wd=18, pitch=22, z0=-64),
    "metro": dict(win=(20, 34), n=7, wd=14, pitch=18, z0=-60),
}


def seat_block(b, body, name, x0, x1, z, back_h, mat="seat"):
    """Un asiento: base negra, cojín y respaldo (mira hacia +z)."""
    box(b, body, f"{name}_base", x0 + 1, x1 - 1, 11, 13, z + 1, z + 5, "black", kind="frame")
    box(b, body, f"{name}_cush", x0, x1, 13, 16, z, z + 6, mat, kind="seat")
    box(b, body, f"{name}_back", x0, x1, 16, 16 + back_h, z - 1, z, mat, kind="seat")


def coach(b, cls):
    """Vagón de pasajeros de 20 m x 3 m: ventanas con vidrio, interior con asientos y tres clases (baja, media, alta)."""
    st = COACH_STYLE[cls]
    body = b.part("body", b.root, (0, 0, 0))
    b.part("glass", b.root, (0, 0, 0))
    glass = b.parts["glass"]
    undercarriage(b, body, COACH_BOGIE, COACH_L)
    half = COACH_L // 2
    box(b, body, "deck", -12, 12, 9, 11, -half, half, "interior", kind="floor")
    wl, wh = st["win"]
    zs = [st["z0"] + k * st["pitch"] for k in range(st["n"])]
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (11, 12) if sx > 0 else (-12, -11)
        box(b, body, f"low_{nm}", x0, x1, 11, wl, -half, half, "body", kind="seam")
        box(b, body, f"top_{nm}", x0, x1, wh, 36, -half, half, "body", kind="seam")
        # extremos con puerta (vidrio) y pilares entre ventanas
        box(b, body, f"endwall_{nm}f", x0, x1, wl, wh, st["z0"] + (st["n"] - 1) * st["pitch"] + st["wd"], half, "body", kind="seam")
        box(b, body, f"endwall_{nm}b", x0, x1, wl, wh, -half, st["z0"], "body", kind="seam")
        for k, z in enumerate(zs):
            box(b, glass, f"win_{nm}{k}", x0, x1, wl, wh, z, z + st["wd"], "glass", kind="glass")
            if k < len(zs) - 1:
                box(b, body, f"pil_{nm}{k}", x0, x1, wl, wh, z + st["wd"], zs[k + 1], "body", kind="seam")
        xs0, xs1 = (12, 13) if sx > 0 else (-13, -12)
        box(b, body, f"stripe_{nm}", xs0, xs1, 14, 17, -half + 2, half - 2, "accent", kind="stripe")
        if cls == "high":
            box(b, body, f"stripe2_{nm}", xs0, xs1, 35, 36, -half + 2, half - 2, "accent", kind="stripe")
    for sz, nm in ((1, "f"), (-1, "b")):
        z0, z1 = (half - 1, half) if sz > 0 else (-half, -half + 1)
        box(b, body, f"end_{nm}", -11, 11, 11, 36, z0, z1, "body", kind="seam")
        gz0, gz1 = (half, half + 2) if sz > 0 else (-half - 2, -half)
        box(b, body, f"gangway_{nm}", -8, 8, 12, 31, gz0, gz1, "black", kind="frame")
    box(b, body, "roof_base", -12, 12, 36, 38, -half, half, "body", kind="roof")
    box(b, body, "roof_mid", -9, 9, 38, 40, -half, half, "body", kind="roof")
    if cls == "high":
        for zz in (-48, -4, 40):
            box(b, body, f"ac{zz}", -5, 5, 40, 42, zz, zz + 12, "details", kind="vent")
    else:
        for zz in (-40, 28):
            box(b, body, f"vent{zz}", -2, 2, 40, 42, zz, zz + 8, "details", kind="vent")
    for sx in (1, -1):
        handrail_ladder(b, body, sx, 70, 11, 30)
        handrail_ladder(b, body, sx, -76, 11, 30)
    # ---- asientos
    seats = []
    fy = 1.5
    if cls == "low":
        rows = [-48 + 16 * k for k in range(7)]
        for k, z in enumerate(rows):
            for j, (x0, x1) in enumerate(((3, 7), (7, 11), (-7, -3), (-11, -7))):
                seat_block(b, body, f"s{k}_{j}", x0, x1, z, 8, "seat")
                seats.append(((x0 + x1) / 16.0, fy, (z + 3) / 8.0))
    elif cls in ("mid", "metro"):
        rows = [-52 + 18 * k for k in range(6)] if cls == "metro" else [-44 + 20 * k for k in range(5)]
        for k, z in enumerate(rows):
            for j, (x0, x1) in enumerate(((3, 7), (7, 11), (-7, -3), (-11, -7))):
                seat_block(b, body, f"s{k}_{j}", x0, x1, z, 11, "seat")
                box(b, body, f"s{k}_{j}_head", x0, x1, 27, 29, z - 1, z, "accent", kind="stripe")
                seats.append(((x0 + x1) / 16.0, fy, (z + 3) / 8.0))
    else:
        rows = [-52 + 22 * k for k in range(6)]
        for k, z in enumerate(rows):
            for j, (x0, x1) in enumerate(((4, 11), (-11, -4))):
                seat_block(b, body, f"s{k}_{j}", x0, x1, z, 12, "seat")
                box(b, body, f"s{k}_{j}_arm", x0 if x0 > 0 else x1 - 1, (x0 + 1) if x0 > 0 else x1, 15, 17, z, z + 6, "details", kind="frame")
                seats.append(((x0 + x1) / 16.0, fy, (z + 3) / 8.0))
            box(b, body, f"table{k}", 6, 11, 18, 19, z + 8, z + 14, "interior", kind="floor")
            box(b, body, f"table{k}_leg", 8, 9, 11, 18, z + 10, z + 11, "black", kind="frame")
    SEATS["metro_car" if cls == "metro" else "coach_" + cls] = seats


def metro_head(b):
    """Cabeza del metro de 20 m: carrocería blanca con franja azul, nariz de bala con visera de vidrio, pantógrafo en el techo."""
    body = b.part("body", b.root, (0, 0, 0))
    b.part("glass", b.root, (0, 0, 0))
    glass = b.parts["glass"]
    undercarriage(b, body, COACH_BOGIE, COACH_L)
    box(b, body, "deck", -12, 12, 9, 11, -80, 56, "interior", kind="floor")
    n, wd, pitch, z0 = 5, 14, 18, -68
    zs = [z0 + k * pitch for k in range(n)]
    for sx, nm in ((1, "l"), (-1, "r")):
        x0, x1 = (11, 12) if sx > 0 else (-12, -11)
        box(b, body, f"low_{nm}", x0, x1, 11, 20, -80, 52, "body", kind="seam")
        box(b, body, f"top_{nm}", x0, x1, 34, 36, -80, 52, "body", kind="seam")
        box(b, body, f"endw_{nm}b", x0, x1, 20, 34, -80, z0, "body", kind="seam")
        box(b, body, f"endw_{nm}f", x0, x1, 20, 34, zs[-1] + wd, 52, "body", kind="seam")
        for k, z in enumerate(zs):
            box(b, glass, f"win_{nm}{k}", x0, x1, 20, 34, z, z + wd, "glass", kind="glass")
            if k < n - 1:
                box(b, body, f"pil_{nm}{k}", x0, x1, 20, 34, z + wd, zs[k + 1], "body", kind="seam")
        xs0, xs1 = (12, 13) if sx > 0 else (-13, -12)
        box(b, body, f"stripe_{nm}", xs0, xs1, 14, 18, -78, 52, "accent", kind="stripe")
    box(b, body, "end_b", -11, 11, 11, 36, -80, -79, "body", kind="seam")
    box(b, body, "gangway_b", -8, 8, 12, 31, -82, -80, "black", kind="frame")
    # nariz de bala: escalones cada vez más angostos y bajos; arriba, la visera de vidrio
    stages = [(52, 60, 11, 10, 34), (60, 66, 10, 10, 31), (66, 71, 8, 10, 28), (71, 75, 6, 11, 25), (75, 78, 4, 12, 22)]
    for i, (za, zb, hw, ya, yt) in enumerate(stages):
        gl = yt - 12 if i < 4 else yt - 8
        box(b, body, f"nose{i}", -hw, hw, 11, gl, za, zb, "body", kind="seam")
        box(b, glass, f"nose{i}_visor", -hw, hw, gl, yt, za, zb, "glass", kind="glass")
        box(b, body, f"nose{i}_roof", -hw, hw, yt, yt + 2, za, zb, "body", kind="roof")
    sym(b, body, "headlight", 2, 5, 15, 18, 78, 80, "headlight", kind="light")
    box(b, body, "nose_stripe", -3, 3, 11, 13, 78, 80, "accent", kind="stripe")
    box(b, body, "roof_base", -12, 12, 36, 38, -80, 52, "body", kind="roof")
    box(b, body, "roof_mid", -9, 9, 38, 40, -80, 40, "body", kind="roof")
    sym(b, body, "taillight", 6, 9, 14, 17, -82, -80, "taillight", kind="light")
    # cabina
    box(b, body, "cab_floor", -11, 11, 11, 12, 28, 56, "interior", kind="floor")
    cab_desk(b, body, 52, 56, 12, "metro_head")
    for nm, (a, c) in (("seat", (3, 9)), ("seat2", (-9, -3))):
        box(b, body, nm, a, c, 12, 22, 34, 40, "interior", kind="seat")
        box(b, body, nm + "_back", a, c, 22, 30, 33, 34, "interior", kind="seat")
    box(b, body, "roof_vent", -4, 4, 40, 42, 6, 22, "details", kind="vent")
    # pantógrafo
    box(b, body, "panto_base", -6, 6, 40, 42, -26, -18, "black", kind="frame")
    box(b, body, "panto_arm1", -1, 1, 42, 46, -24, -22, "chrome", kind="metal")
    box(b, body, "panto_arm2", -1, 1, 46, 50, -22, -20, "chrome", kind="metal")
    box(b, body, "panto_head", -7, 7, 50, 51, -24, -18, "chrome", kind="metal")
    for sx in (1, -1):
        handrail_ladder(b, body, sx, -76, 11, 30)
    SEATS["metro_head"] = [(0.6, 2.05, 4.6), (-0.6, 2.05, 4.6)]


BUILD = {
    "freight_loco": lambda b: loco(b),
    "coal_wagon": lambda b: wagon(b, "coal"),
    "food_wagon": lambda b: wagon(b, "food"),
    "mineral_wagon": lambda b: wagon(b, "mineral"),
    "steam_classic": lambda b: steam(b, False),
    "steam_modern": lambda b: steam(b, True),
    "coach_low": lambda b: coach(b, "low"),
    "coach_mid": lambda b: coach(b, "mid"),
    "coach_high": lambda b: coach(b, "high"),
    "metro_head": lambda b: metro_head(b),
    "metro_car": lambda b: coach(b, "metro"),
}


def build(name):
    b = vc.new_builder()
    b.extras = SPECS[name]["extras"]
    BUILD[name](b)
    return b


# ---------------------------------------------------------------------------------------------- atlas
def pack_all(b, name):
    reps, dupes, first = [], [], {}
    for c in b.cubes:
        key = (int(c.w), int(c.h), int(c.d), c.mat, c.opts.get("kind"))
        if key in first:
            dupes.append((c, first[key]))
        else:
            first[key] = c
            reps.append(c)
    used = vc.pack(reps, TEX_W, H(name))
    for c, r in dupes:
        c.u, c.v = r.u, r.v
    return used


def paint_cube(spec, c, rng):
    rgb, _ = material(spec, c.mat)
    kind = c.opts.get("kind")
    gloss = c.mat in ("body", "accent")
    faces = vc.base_faces(c, rgb, rng, gloss=gloss, flat=kind in ("light", "glass"))
    dark = (0, 0, 0)
    if kind == "seam":
        for f in ("west", "east"):
            a = faces[f]
            for col in range(7, a.shape[1], 8):
                vline(a, col, dark, alpha=0.20)
            hline(a, a.shape[0] - 1, dark, alpha=0.18)
        for f in ("front", "back"):
            vline(faces[f], faces[f].shape[1] // 2, dark, alpha=0.12)
    elif kind == "floor":
        a = faces["top"]
        for r in range(2, a.shape[0], 3):
            hline(a, r, dark, alpha=0.22)
    elif kind == "grille":
        a = faces["front"]
        for r in range(1, a.shape[0], 2):
            hline(a, r, (150, 154, 164), alpha=0.55)
    elif kind == "vent":
        a = faces["top"]
        for col in range(1, a.shape[1], 2):
            vline(a, col, dark, alpha=0.5)
    elif kind == "fan":
        a = faces["top"]
        rows, cols = a.shape[:2]
        cy, cx = (rows - 1) / 2.0, (cols - 1) / 2.0
        for r in range(rows):
            for q in range(cols):
                if math.hypot(r - cy, q - cx) < 1.3:
                    a[r, q, :3] = (150, 154, 164)
    elif kind == "wheel":
        for f in ("west", "east"):
            a = faces[f]
            n = a.shape[0]
            cy = (n - 1) / 2.0
            for r in range(n):
                for q in range(n):
                    d = math.hypot(r - cy, q - cy)
                    if d > cy - 0.2:
                        a[r, q, :3] *= 0.55
                    elif d < 1.2:
                        a[r, q, :3] *= 1.25
    elif kind == "glass":
        for f in faces:
            a = faces[f]
            vshade(a, 1.15, 0.8)
    elif kind in ("light",):
        for f in faces:
            faces[f][..., :3] = rgb
    elif kind == "stripe":
        for f in faces:
            hline(faces[f], 0, (255, 255, 255), alpha=0.12)
    if kind in ("seam", "roof", "stripe") and c.mat in ("body", "accent"):
        for f in ("west", "east", "front", "back"):
            edges(faces[f], 0.04, 0.08)
    return faces, {}


def vshade(a, top, bottom):
    rows = a.shape[0]
    g = np.linspace(top, bottom, rows, dtype=np.float32)[:, None]
    a[..., :3] *= g[..., None]
    return a


def build_all(name):
    spec = SPECS[name]
    b = build(name)
    pack_all(b, name)
    atlas = np.zeros((H(name), TEX_W, 4), np.float32)
    main = np.zeros((H(name), TEX_W, 3), np.uint8)
    reg = np.zeros((H(name), TEX_W, 3), np.uint8)
    for i, c in enumerate(b.cubes):
        rng = np.random.default_rng(4800 + i)
        faces, _ = paint_cube(spec, c, rng)
        _, chan = material(spec, c.mat)
        for f, arr in faces.items():
            x0, y0, cols, rows = vc.face_rect(c, f)
            assert arr.shape[0] == rows and arr.shape[1] == cols, (c.name, f, arr.shape, rows, cols)
            atlas[y0:y0 + rows, x0:x0 + cols] = arr
            main[y0:y0 + rows, x0:x0 + cols] = 0
            reg[y0:y0 + rows, x0:x0 + cols] = 0
            if chan is None:
                continue
            if chan[0] == "main":
                main[y0:y0 + rows, x0:x0 + cols, chan[1]] = 100
            else:
                reg[y0:y0 + rows, x0:x0 + cols, 0] = chan[1]
                reg[y0:y0 + rows, x0:x0 + cols, 1] = 100
    tex = Image.fromarray(np.clip(atlas, 0, 255).astype(np.uint8), "RGBA")
    return b, tex, main, reg


# ---------------------------------------------------------------------------------------------- Java
def java_variant(name, b):
    body = vc.java_tree(b)
    # el nombre de las piezas se repite entre variantes: cada variante vive en su propio método, así que no choca
    return body


def java_all(builds):
    methods = []
    ratios = []
    for name, b in builds.items():
        fn = "".join(p.capitalize() for p in name.split("_"))
        methods.append(f'''    /** {name} */
    private static LayerDefinition create{fn}Layer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{java_variant(name, b)}
        return LayerDefinition.create(mesh, {TEX_W}, {H(name)});
    }}
''')
        wr = getattr(b, "wheel_radius", {})
        nw = len([n for n in b.parts if n.startswith("w") and n[1:].isdigit()])
        vals = ", ".join(f"{WHEEL_R / float(wr.get(f'w{i}', WHEEL_R)):.4f}f" for i in range(nw))
        ratios.append(f'        RATIOS.put("{name}", new float[] {{{vals}}});')
    cases = "\n".join(f'            case "{name}" -> create{"".join(p.capitalize() for p in name.split("_"))}Layer();' for name in builds)
    return f'''package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
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
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Locomotoras y vagones del tren de carga (V4.8.0) y del de pasajeros (V5.0.0). ARCHIVO GENERADO por tools/v480/trains.py: no editar a
 * mano, se pierde al regenerar. Atlas de {TEX_W}x{TEX_H}. 1 unidad = 1/{UNITS_PER_BLOCK} de bloque (el renderizador lo agranda {UNITS_PER_BLOCK // 2} veces).
 * Cada variante tiene dos árboles ("body" opaco y "glass" vidrios) y ruedas "w0".."wN" (hijas de "body") que giran en x.
 */
public class TrainCarModel extends EntityModel<Entity> {{
    /** Cuántas veces el renderizador agranda el modelo. */
    public static final float SCALE = {UNITS_PER_BLOCK / 8.0 * 2.0:.1f}f;
    /** Cuánto gira cada rueda respecto a una de radio 1/2 m (las motrices grandes giran más despacio). */
    private static final Map<String, float[]> RATIOS = new HashMap<>();

    static {{
{chr(10).join(ratios)}
    }}

    /** Capa del modelo del coche con ese id (el mismo de la variante). */
    public static ModelLayerLocation layer(String id) {{
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, id), "main");
    }}

    public static LayerDefinition create(String id) {{
        return switch (id) {{
{cases}
            default -> throw new IllegalArgumentException(id);
        }};
    }}

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final List<ModelPart> wheels = new ArrayList<>();
    private final float[] ratio;

    public TrainCarModel(ModelPart root, String id) {{
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.ratio = RATIOS.get(id);
        for (int i = 0; this.body.hasChild("w" + i); i++) {{
            this.wheels.add(this.body.getChild("w" + i));
        }}
    }}

{chr(10).join(methods)}
    /** Giro de las ruedas (rad). */
    public void pose(float wheelAngle) {{
        for (int i = 0; i < this.wheels.size(); i++) {{
            this.wheels.get(i).xRot = wheelAngle * (this.ratio != null && i < this.ratio.length ? this.ratio[i] : 1.0f);
        }}
    }}

    /** false = carrocería (opaca); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {{
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }}

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
    }}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {{
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }}
}}
'''


def java_seats():
    rows = []
    for name in sorted(SEATS):
        pts = ", ".join(f"{{{x:.3f}, {y:.3f}, {z:.3f}}}" for x, y, z in SEATS[name])
        rows.append(f'        SEATS.put("{name}", new double[][] {{{pts}}});')
    brows = [f'        BUTTONS.put("{n}", new double[] {{{x:.3f}, {y:.3f}, {z:.3f}}});' for n, (x, y, z) in sorted(BUTTONS.items())]
    return f'''package com.example.beautifuldecor.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Dónde se sienta quien va montado en cada coche del tren de pasajeros (x = izquierda, y = altura de los pies sobre el riel, z = hacia
 * delante, en metros desde el centro del coche). ARCHIVO GENERADO por tools/v480/trains.py junto con los modelos: no editar a mano.
 */
public final class TrainSeats {{
    private static final Map<String, double[][]> SEATS = new HashMap<>();
    private static final double[][] NONE = new double[0][];
    private static final Map<String, double[]> BUTTONS = new HashMap<>();

    static {{
{chr(10).join(rows)}
{chr(10).join(brows)}
    }}

    private TrainSeats() {{
    }}

    public static double[][] of(String variantId) {{
        return SEATS.getOrDefault(variantId, NONE);
    }}

    /** V6.2.0: posición (x, y, z en metros) del botón de emergencia del panel de la cabina, o null si el coche no tiene. */
    public static double[] button(String variantId) {{
        return BUTTONS.get(variantId);
    }}
}}
'''


VIEWS = [(35, 18), (215, 18), (90, 4), (0, 60)]
CENTER = (0, 20.0, 0)


def render_views(b, tex, name, scale=1.3, size=(420, 300)):
    from ..v200.softrender import Camera, render
    out = []
    for az, el in VIEWS:
        cam = Camera(az, el, scale, CENTER, size)
        out.append(render(b.root, tex, cam, (TEX_W, H(name)), glow=None, background=(78, 86, 98, 255), supersample=2, translucent_mats=("glass",)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--only", default="")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()
    names = [args.only] if args.only else list(SPECS)
    builds = {}
    for name in names:
        b, tex, main_mask, reg = build_all(name)
        used = max(c.v + int(c.h + c.d) for c in b.cubes)
        print(f"{name}: {len(b.cubes)} cajas, atlas usado hasta y={used}/{H(name)}")
        builds[name] = (b, tex, main_mask, reg)
        if args.preview:
            imgs = render_views(b, tex, name)
            out = Path(args.preview).with_name(f"{Path(args.preview).stem}_{name}.png")
            vc.sheet(imgs, cols=2).convert("RGB").save(out)
            print("vista previa:", out)
    if args.write:
        (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        for name, (b, tex, main_mask, reg) in builds.items():
            tex.save(ASSETS / f"textures/entity/{name}.png")
            Image.fromarray(main_mask, "RGB").save(ASSETS / f"textures/entity/{name}_paint.png")
            Image.fromarray(reg, "RGB").save(ASSETS / f"textures/entity/{name}_regions.png")
            icon = vc.make_icon(b, tex, None, (TEX_W, H(name)), 35, 20, CENTER, 1.3 if name in ("freight_loco", "coal_wagon", "food_wagon", "mineral_wagon") else 1.15, outline=(14, 14, 20, 255), brighten=1.05)
            icon.save(ASSETS / f"textures/item/{name}.png")
        if len(builds) == len(SPECS):
            (JAVA / "client/TrainCarModel.java").write_text(java_all({n: v[0] for n, v in builds.items()}), encoding="utf-8")
            (JAVA / "entity/TrainSeats.java").write_text(java_seats(), encoding="utf-8")
        print("escrito: texturas, máscaras, íconos" + (" y TrainCarModel.java" if len(builds) == len(SPECS) else ""))


if __name__ == "__main__":
    main()
