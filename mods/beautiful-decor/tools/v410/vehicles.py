"""V4.1.0: siluetas (vista lateral, mirando a la derecha) de los productos de cada marca, y sus logotipos propios del mod."""
import math
from .adkit import *


def _P(x, y, k):
    return lambda pts: [(x + px * k, y + py * k) for px, py in pts]


def shade(c, f=0.65):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3] if len(c) > 3 else 255)


def pickup(cv, x, y, k, body, t, glass=(150, 200, 235, 255)):
    P = _P(x, y, k)
    dark = shade(body)
    cv.ellipse(x - 56 * k, y - 3 * k, x + 58 * k, y + 3 * k, (0, 0, 0, 70))
    cv.poly(P([(-52, -14), (-52, -36), (-14, -36), (-14, -14)]), body)
    cv.poly(P([(-52, -36), (-14, -36), (-14, -33), (-52, -33)]), dark)
    cv.poly(P([(-14, -14), (-14, -56), (14, -56), (30, -40), (54, -38), (56, -26), (56, -14)]), body)
    cv.poly(P([(-9, -52), (12, -52), (25, -41), (-9, -41)]), glass)
    cv.line(P([(-14, -30), (56, -28)]), (255, 255, 255, 120), 0.5 * k)
    cv.rect(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(52, -34), (57, -28)])), (255, 235, 150, 255))
    cv.poly(P([(54, -24), (58, -24), (58, -14), (54, -14)]), (60, 64, 72, 255))
    cv.poly(P([(-56, -14), (-52, -14), (-52, -18), (-56, -18)]), (200, 205, 212, 255))
    for wx in (-33, 36):
        cv.circ(x + wx * k, y - 14 * k, 11 * k, shade(body, 0.45))
        wheel(cv, x + wx * k, y - 9 * k, 9.2 * k, -t * 9)


def delivery_truck(cv, x, y, k, t, box=(250, 250, 252, 255), cab=(255, 153, 0, 255)):
    P = _P(x, y, k)
    cv.ellipse(x - 56 * k, y - 3 * k, x + 58 * k, y + 3 * k, (0, 0, 0, 70))
    cv.poly(P([(-54, -16), (-54, -72), (8, -72), (8, -16)]), box)
    cv.poly(P([(-54, -34), (8, -34), (8, -27), (-54, -27)]), cab)
    cv.poly(P([(10, -16), (10, -52), (30, -52), (46, -34), (56, -32), (56, -16)]), cab)
    cv.poly(P([(14, -48), (29, -48), (42, -35), (14, -35)]), (150, 200, 235, 255))
    cv.rect(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(52, -30), (57, -24)])), (255, 245, 170, 255))
    cv.text("amazoom", x - 23 * k, y - 55 * k, 7.4 * k, (30, 40, 70, 255))
    cv.line(P([(-45, -47), (-35, -42), (-18, -42), (-8, -47)]), (255, 153, 0, 255), 1.1 * k)
    cv.poly(P([(-12, -49), (-7, -47), (-12, -44)]), (255, 153, 0, 255))
    for wx in (-38, -20, 38):
        cv.circ(x + wx * k, y - 14 * k, 11 * k, (30, 30, 34, 255))
        wheel(cv, x + wx * k, y - 9 * k, 9.2 * k, -t * 9)


def rc_car(cv, x, y, k, t, body=(255, 214, 0, 255)):
    P = _P(x, y, k)
    cv.ellipse(x - 56 * k, y - 3 * k, x + 58 * k, y + 3 * k, (0, 0, 0, 70))
    cv.poly(P([(-50, -12), (-50, -20), (-30, -26), (-8, -34), (14, -34), (36, -24), (56, -20), (56, -12)]), body)
    cv.poly(P([(-6, -31), (12, -31), (26, -24), (-18, -24)]), (40, 44, 60, 255))
    cv.poly(P([(-2, -17), (30, -17), (34, -13), (-2, -13)]), shade(body, 0.7))
    cv.line(P([(-52, -26), (-52, -34)]), (30, 30, 34, 255), 1.2 * k)
    cv.rect(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(-58, -36), (-44, -33)])), (30, 30, 34, 255))
    cv.line(P([(-34, -26), (-38, -50)]), (60, 60, 66, 255), 0.7 * k)
    cv.circ(x - 38 * k, y - 51 * k, 1.8 * k, (255, 60, 60, 255))
    cv.line(P([(8, -33), (30, -23)]), (255, 255, 255, 140), 0.6 * k)
    for wx in (-33, 34):
        wheel(cv, x + wx * k, y - 8 * k, 8.2 * k, -t * 14, rim=(255, 120, 0, 255))


def supercar(cv, x, y, k, t, body=(20, 70, 200, 255)):
    P = _P(x, y, k)
    cv.ellipse(x - 58 * k, y - 3 * k, x + 60 * k, y + 3 * k, (0, 0, 0, 80))
    cv.poly(P([(-56, -12), (-54, -22), (-36, -27), (-16, -38), (8, -40), (28, -29), (52, -25), (58, -18), (58, -12)]), body)
    cv.poly(P([(-14, -36), (6, -37), (20, -29), (-24, -28)]), (25, 30, 48, 255))
    cv.line(P([(-50, -23), (-30, -26), (-12, -35), (8, -37)]), (255, 215, 90, 255), 0.7 * k)
    cv.line(P([(-20, -22), (30, -20)]), shade(body, 0.55), 0.8 * k)
    cv.poly(P([(-12, -20), (6, -20), (4, -15), (-14, -15)]), shade(body, 0.5))
    cv.arc = None
    for a in range(-60, 61, 12):
        r = math.radians(a)
        cv.line(P([(55 + 0.0, -19), (55 + math.cos(r) * 3, -19 + math.sin(r) * 6)]), (255, 215, 90, 255), 0.5 * k)
    cv.rect(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(50, -26), (57, -23)])), (200, 230, 255, 255))
    for wx in (-34, 35):
        wheel(cv, x + wx * k, y - 9.5 * k, 9.8 * k, -t * 12, rim=(235, 205, 90, 255))


def helicopter(cv, x, y, k, t, body=(235, 240, 250, 255), accent=(20, 90, 200, 255)):
    P = _P(x, y, k)
    cv.poly(P([(-60, -50), (-18, -46), (-18, -37), (-60, -44)]), body)
    cv.poly(P([(-60, -48), (-66, -64), (-56, -64), (-52, -47)]), accent)
    cv.ellipse(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(-24, -57), (26, -22)])), body)
    cv.poly(P([(8, -52), (22, -48), (26, -38), (4, -38)]), (150, 205, 240, 255))
    cv.rect(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(-22, -42), (6, -40)])), accent)
    cv.line(P([(0, -58), (0, -64)]), (60, 64, 72, 255), 1.6 * k)
    ph = t * 38
    for sgn, base in ((1, 0), (-1, 0)):
        L = 56 * abs(math.cos(ph + base))
        cv.line(P([(0, -64), (sgn * L, -64.5)]), (40, 44, 56, 200), 1.1 * k)
    cv.ellipse(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(-56, -65), (56, -62.6)])), (255, 255, 255, 40))
    cv.circ(x - 62 * k, y - 55 * k, 6 * k * (0.6 + 0.4 * abs(math.sin(ph * 1.7))), (40, 44, 56, 120))
    cv.line(P([(-18, -22), (-18, -14), (20, -14)]), (60, 64, 72, 255), 1.2 * k)
    cv.line(P([(-24, -14), (28, -14)]), (60, 64, 72, 255), 1.4 * k)
    cv.text("HX-6", x - 8 * k, y - 36 * k, 5 * k, accent)


def airliner(cv, x, y, k, t, body=(246, 248, 252, 255), accent=(20, 90, 200, 255)):
    P = _P(x, y, k)
    cv.poly(P([(-72, -52), (-60, -52), (-76, -80), (-82, -80)]), accent)
    cv.poly(P([(-70, -54), (50, -54), (50, -36), (-70, -40)]), body)
    cv.ellipse(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(30, -54), (72, -36)])), body)
    cv.ellipse(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(14, -64), (58, -48)])), body)
    cv.poly(P([(-6, -40), (14, -40), (-26, -10), (-40, -10)]), shade(body, 0.86))
    cv.ellipse(*(lambda a, b: (a[0], a[1], b[0], b[1]))(*P([(-12, -22), (8, -14)])), (150, 156, 168, 255))
    cv.poly(P([(-70, -42), (50, -40), (50, -37), (-70, -39)]), accent)
    for i in range(14):
        cv.circ(x + (-56 + i * 7.5) * k, y - 47 * k, 1.3 * k, (60, 90, 140, 255))
    for i in range(5):
        cv.circ(x + (20 + i * 7) * k, y - 58 * k, 1.1 * k, (60, 90, 140, 255))
    cv.poly(P([(64, -48), (70, -48), (72, -43), (62, -43)]), (150, 205, 240, 255))


def chandelier(cv, cx, top, k, t, gold=(235, 190, 90, 255), lit=1.0):
    cv.line([(cx, top), (cx, top + 12 * k)], gold, 0.7 * k)
    cv.circ(cx, top + 13 * k, 2.2 * k, gold)
    for tier, (w, y) in enumerate(((22, 20), (14, 12))):
        yy = top + (y + 4) * k
        pts = []
        for i in range(21):
            a = math.pi * i / 20
            pts.append((cx + math.cos(a) * w * k, yy + math.sin(a) * 7 * k))
        cv.line(pts, gold, 0.8 * k)
        for i in range(0 if tier == 0 else 1, 21, 4):
            bx, by = pts[i]
            cv.line([(bx, by), (bx, by - 4 * k)], gold, 0.5 * k)
            cv.glow(bx, by - 6 * k, 6 * k, (255, 220, 140, 255), 0.7 * lit)
            cv.circ(bx, by - 5.5 * k, 1.4 * k, (255, 245, 210, 255))
    cv.glow(cx, top + 24 * k, 26 * k, (255, 220, 150, 255), 0.35 * lit)
    for i in range(7):
        a = i * math.pi / 6
        lx, ly = cx + math.cos(a) * 12 * k, top + 29 * k + math.sin(a) * 4 * k
        cv.line([(lx, ly), (lx, ly + (4 + 2 * math.sin(i * 2 + t * 3)) * k)], (255, 255, 255, 160), 0.4 * k)


def neon_ring(cv, cx, cy, r, color, lit=1.0):
    cv.glow(cx, cy, r * 1.7, color, 0.5 * lit)
    cv.circ(cx, cy, r, None, outline=color[:3] + (255,), width=1.6)
    cv.circ(cx, cy, r, None, outline=(255, 255, 255, int(200 * lit)), width=0.5)


def neon_heart(cv, cx, cy, s, color, lit=1.0):
    pts = []
    for i in range(41):
        a = 2 * math.pi * i / 40
        x = 16 * math.sin(a) ** 3
        y = -(13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))
        pts.append((cx + x * s / 16, cy + y * s / 16))
    cv.glow(cx, cy, s * 1.5, color, 0.4 * lit)
    cv.line(pts + [pts[0]], color[:3] + (255,), 1.5)
    cv.line(pts + [pts[0]], (255, 255, 255, int(190 * lit)), 0.5)


def table_lamp(cv, cx, base, k, shade_col=(250, 235, 200, 255), vase=(60, 110, 170, 255), lit=1.0):
    cv.glow(cx, base - 34 * k, 34 * k, (255, 225, 160, 255), 0.5 * lit)
    cv.poly([(cx - 5 * k, base), (cx + 5 * k, base), (cx + 9 * k, base - 16 * k), (cx + 4 * k, base - 24 * k), (cx - 4 * k, base - 24 * k),
             (cx - 9 * k, base - 16 * k)], vase)
    cv.line([(cx, base - 24 * k), (cx, base - 30 * k)], (200, 200, 205, 255), 0.7 * k)
    cv.poly([(cx - 8 * k, base - 30 * k), (cx + 8 * k, base - 30 * k), (cx + 14 * k, base - 46 * k), (cx - 14 * k, base - 46 * k)], shade_col)


# ---------------------------------------------------------------- logotipos propios del mod

def logo_furd(cv, cx, cy, s, a=255):
    pts = [(cx + math.cos(math.pi / 6 + i * math.pi / 3) * s, cy + math.sin(math.pi / 6 + i * math.pi / 3) * s * 0.78) for i in range(6)]
    cv.poly(pts, (10, 40, 110, a), outline=(220, 228, 245, a), width=1.2)
    cv.text("Furd", cx, cy + s * 0.02, s * 0.62, (245, 248, 255, a), face=FONT_SERIF, stroke=0.0)
    for dx in (-0.45, 0, 0.45):
        cv.circ(cx + dx * s, cy + s * 0.5, s * 0.05, (220, 228, 245, a))


def logo_amazoom(cv, cx, cy, s, a=255, color=(25, 35, 70)):
    cv.text("amazoom", cx, cy - s * 0.12, s * 0.48, color + (a,), face=FONT_BOLD)
    pts = []
    for i in range(21):
        p = i / 20
        pts.append((cx - s * 0.78 + p * s * 1.56, cy + s * 0.22 + math.sin(p * math.pi) * s * 0.16))
    cv.line(pts, (255, 153, 0, a), s * 0.075)
    ex, ey = pts[-1]
    cv.poly([(ex + s * 0.1, ey - s * 0.12), (ex + s * 0.02, ey - s * 0.01), (ex - s * 0.12, ey - s * 0.04)], (255, 153, 0, a))
    cv.rect(cx - s * 0.62, cy + s * 0.34, cx - s * 0.42, cy + s * 0.46, (255, 153, 0, a), r=0.6)


def logo_lambor(cv, cx, cy, s, a=255):
    pts = [(cx - s * 0.7, cy - s * 0.7), (cx + s * 0.7, cy - s * 0.7), (cx + s * 0.7, cy + s * 0.1), (cx, cy + s * 0.78), (cx - s * 0.7, cy + s * 0.1)]
    cv.poly(pts, (16, 16, 20, a), outline=(255, 200, 0, a), width=1.4)
    horn = [(cx - s * 0.5, cy - s * 0.42), (cx - s * 0.18, cy - s * 0.05), (cx, cy - s * 0.12), (cx + s * 0.18, cy - s * 0.05), (cx + s * 0.5, cy - s * 0.42),
            (cx + s * 0.28, cy + s * 0.05), (cx, cy + s * 0.22), (cx - s * 0.28, cy + s * 0.05)]
    cv.poly(horn, (255, 200, 0, a))
    cv.circ(cx, cy - s * 0.02, s * 0.07, (16, 16, 20, a))


def logo_boing(cv, cx, cy, s, a=255):
    cv.rect(cx - s * 0.95, cy - s * 0.45, cx + s * 0.95, cy + s * 0.45, (14, 70, 190, a), r=s * 0.25)
    zz = [(cx - s * 0.84 + i * s * 0.12, cy + (-s * 0.2 if i % 2 else s * 0.2)) for i in range(5)]
    cv.line(zz, (255, 255, 255, a), s * 0.06)
    cv.text("BOING", cx + s * 0.3, cy, s * 0.3, (255, 255, 255, a), face=FONT_BOLD)


def logo_bugerati(cv, cx, cy, s, a=255):
    gold = (235, 190, 90, a)
    cv.text("BUGERATI", cx, cy + s * 0.28, s * 0.4, gold, face=FONT_SERIF)
    for i, dx in enumerate((-0.4, 0, 0.4)):
        h = 0.5 if i == 1 else 0.38
        cv.rect(cx + dx * s - s * 0.045, cy - s * h, cx + dx * s + s * 0.045, cy - s * 0.02, gold, r=0.4)
        cv.glow(cx + dx * s, cy - s * (h + 0.12), s * 0.14, (255, 220, 140, 255), 0.8)
    cv.line([(cx - s * 0.5, cy - s * 0.02), (cx + s * 0.5, cy - s * 0.02)], gold, s * 0.04)


def logo_bugotti(cv, cx, cy, s, a=255):
    cv.ellipse(cx - s * 0.62, cy - s * 0.78, cx + s * 0.62, cy + s * 0.78, (170, 20, 40, a), outline=(235, 205, 120, a), width=1.2)
    for i in range(24):
        ang = i * math.pi / 12
        cv.circ(cx + math.cos(ang) * s * 0.52, cy + math.sin(ang) * s * 0.69, s * 0.025, (245, 245, 250, a))
    cv.text("B", cx, cy, s * 0.95, (245, 245, 250, a), face=FONT_SERIF)
    cv.text("BUGOTTI", cx, cy + s * 1.0, s * 0.3, (235, 205, 120, a), face=FONT_SERIF)


# ---------------------------------------------------------------- HS (computadores, navegador, impresora)

def logo_hs(cv, cx, cy, s, a=255):
    cv.rect(cx - s * 0.8, cy - s * 0.8, cx + s * 0.8, cy + s * 0.8, (0, 110, 220, a), r=s * 0.28, outline=(255, 255, 255, a), width=max(0.2, s * 0.05))
    cv.text("HS", cx, cy - s * 0.06, s * 0.95, (255, 255, 255, a), face=FONT_BOLD)
    cv.rect(cx - s * 0.5, cy + s * 0.46, cx + s * 0.5, cy + s * 0.56, (255, 255, 255, a), r=0.3)


def laptop(cv, cx, base, k, open_p, t):
    """Portátil azul HS con un navegador en la pantalla. open_p: 0 cerrado, 1 abierto."""
    cv.ellipse(cx - 38 * k, base - 1.5 * k, cx + 38 * k, base + 2.5 * k, (0, 0, 0, 60))
    cv.poly([(cx - 31 * k, base - 4 * k), (cx + 31 * k, base - 4 * k), (cx + 36 * k, base), (cx - 36 * k, base)], (176, 190, 214, 255))
    cv.line([(cx - 8 * k, base - 1.6 * k), (cx + 8 * k, base - 1.6 * k)], (120, 135, 160, 255), 0.5 * k)
    h = 31 * k * clamp(open_p)
    if h < 1.5:
        return
    top = base - 4 * k - h
    cv.rect(cx - 25 * k, top, cx + 25 * k, base - 4 * k, (0, 110, 220, 255), r=1.5 * k)
    cv.rect(cx - 23.5 * k, top + 1.2 * k, cx + 23.5 * k, base - 5.2 * k, (18, 22, 32, 255), r=0.8 * k)
    if open_p < 0.95:
        return
    x0, x1 = cx - 22.5 * k, cx + 22.5 * k
    y0, y1 = top + 2.4 * k, base - 6.4 * k
    cv.rect(x0, y0, x1, y1, (246, 249, 253, 255))
    cv.rect(x0, y0, x1, y0 + 4 * k, (214, 224, 240, 255))
    for i, c in enumerate(((255, 95, 90), (255, 190, 50), (60, 200, 90))):
        cv.circ(x0 + (2.2 + i * 2.6) * k, y0 + 2 * k, 0.8 * k, c + (255,))
    cv.rect(x0 + 10 * k, y0 + 0.9 * k, x1 - 2 * k, y0 + 3.1 * k, (255, 255, 255, 255), r=1 * k)
    load = seg(t, 3.6, 5.0)
    cv.rect(x0 + 10 * k, y0 + 2.7 * k, x0 + 10 * k + (x1 - x0 - 12 * k) * load, y0 + 3.1 * k, (0, 140, 255, 255))
    by = y0 + 5 * k
    if load < 1.0:
        gx, gy = (x0 + x1) / 2, (by + y1) / 2
        for i in range(8):
            ang = t * 7 + i * math.pi / 4
            cv.circ(gx + math.cos(ang) * 5 * k, gy + math.sin(ang) * 5 * k, (0.5 + i * 0.1) * k, (0, 110, 220, 60 + i * 24))
    else:
        p = seg(t, 5.0, 5.6)
        gx, gy = x0 + 8 * k, by + 8 * k
        cv.circ(gx, gy, 6 * k * ease_out(p), (0, 120, 230, 255))
        if p > 0.5:
            cv.line([(gx - 6 * k, gy), (gx + 6 * k, gy)], (255, 255, 255, 200), 0.4 * k)
            cv.ellipse(gx - 2.6 * k, gy - 6 * k, gx + 2.6 * k, gy + 6 * k, None, outline=(255, 255, 255, 200), width=0.4 * k)
        for i, c in enumerate(((255, 153, 0), (0, 190, 130), (240, 80, 120))):
            q = back_out(seg(t, 5.2 + i * 0.25, 5.8 + i * 0.25))
            cx0 = x0 + (17 + i * 9.4) * k
            cv.rect(cx0, by + 2 * k, cx0 + 8 * k, by + (2 + 11 * q) * k, c + (255,), r=0.8 * k)
        for i in range(3):
            q = ease_out(seg(t, 5.8 + i * 0.2, 6.3 + i * 0.2))
            cv.rect(x0 + 2 * k, y1 - (7 - i * 2.2) * k, x0 + (2 + 40 * q) * k, y1 - (6 - i * 2.2) * k, (200, 210, 228, 255))


def printer(cv, cx, base, k, t, t0):
    """Impresora HS que va sacando una hoja."""
    cv.ellipse(cx - 20 * k, base - 1 * k, cx + 20 * k, base + 2 * k, (0, 0, 0, 60))
    p = clamp(seg(t, t0 + 0.4, t0 + 1.8))
    if p > 0:
        cv.rect(cx - 9 * k, base - 12 * k - 2 * k, cx + 9 * k, base - 2 * k + 14 * k * p * 0.0 + 0, (255, 255, 255, 255))
    cv.rect(cx - 16 * k, base - 14 * k, cx + 16 * k, base, (60, 95, 150, 255), r=2 * k)
    cv.rect(cx - 16 * k, base - 14 * k, cx + 16 * k, base - 11 * k, (96, 138, 200, 255), r=1.5 * k)
    cv.rect(cx - 11 * k, base - 8 * k, cx + 11 * k, base - 6 * k, (14, 20, 34, 255))
    cv.circ(cx + 12 * k, base - 12.5 * k, 0.7 * k, (80, 255, 140, 255) if int(t * 4) % 2 == 0 else (30, 120, 70, 255))
    if p > 0:
        h = 15 * k * ease_out(p)
        top = base - 6 * k
        cv.rect(cx - 10 * k, top, cx + 10 * k, top + h, (255, 255, 255, 255))
        cv.rect(cx - 10 * k, top, cx + 10 * k, top + h, None, outline=(190, 200, 215, 255), width=0.2 * k)
        for i in range(4):
            if h > (3 + i * 3) * k:
                cv.rect(cx - 7.5 * k, top + (2 + i * 3) * k, cx + (7.5 - i * 1.5) * k, top + (3 + i * 3) * k, (0, 110, 220, 255))
