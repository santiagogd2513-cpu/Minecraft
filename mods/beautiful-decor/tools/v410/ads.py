"""V4.1.0: genera los 6 anuncios (video + audio) de las marcas parodia del mod.
Uso: python3 -m tools.v410.ads <carpeta_salida> [marca ...]"""
import os, sys, subprocess, shutil, math
from concurrent.futures import ProcessPoolExecutor
from .adkit import *
from .vehicles import *

SND = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "beautifuldecor", "sounds")
T_INTRO, T_SCENE, T_FEAT, T_OUT = 2.2, 2.5, 7.0, 10.0


def sfx(name):
    return load_ogg(os.path.join(SND, name + ".ogg"))


def burst_bg(cv, t, c1, c2, c3):
    cv.gradient(c1, c3)
    cv.rays(50, 50, 16, c2 + (110,), c1 + (0,), t * 0.5, 150)
    cv.glow(50, 50, 60, (255, 255, 255), 0.35)


def intro(cv, t, logo, c1, c2, c3):
    """0-2.2 s: explosión de rayos + logo grande; luego se encoge hacia la esquina."""
    p = seg(t, 2.0, 2.5)
    burst_bg(cv, t, c1, c2, c3)
    if t > 2.0:
        cv.rect(0, 0, 100, 100, (0, 0, 0, 0))
    s = back_out(seg(t, 0.25, 0.85)) * 21 * (1 - 0.0 * p)
    if s > 0.5:
        logo(cv, 50, 50, s)
    flash(cv, t, 0.2, 0.3)


def corner_logo(cv, t, logo, s=6.0, x=13, y=11, panel=(0, 0, 0, 110)):
    a = clamp(seg(t, T_INTRO, T_INTRO + 0.5))
    if a <= 0:
        return
    cv.rect(x - s * 1.4, y - s * 1.2, x + s * 1.4, y + s * 1.3, panel[:3] + (int(panel[3] * a),), r=2)
    logo(cv, x, y, s * a)


def outro(cv, t, logo, bg1, bg2, slogan, fine, accent=(255, 255, 255)):
    p = clamp(seg(t, T_OUT, T_OUT + 0.45))
    if p <= 0:
        return
    cv.rect(0, 0, 100, 100, bg1 + (int(255 * p),))
    cv.gradient(bg1, bg2, 0, 100) if p >= 1 else None
    s = back_out(seg(t, T_OUT + 0.1, T_OUT + 0.7)) * 17
    if s > 0.5:
        logo(cv, 50, 40, s)
    if t > T_OUT + 0.7:
        a = int(255 * seg(t, T_OUT + 0.7, T_OUT + 1.1))
        sz = min(5.6, 5.6 * 90 / max(1, cv.text_width(slogan, 5.6)))
        cv.text(slogan, 50, 76, sz, accent + (a,))
    fine_print(cv, fine, t, T_OUT + 1.0)


def speed_lines(cv, t, y0, y1, col=(255, 255, 255, 90), n=7):
    for i in range(n):
        y = y0 + (y1 - y0) * ((i * 0.37) % 1)
        x = ((i * 53 - t * 260 * (0.6 + 0.1 * i)) % 160) - 30
        cv.line([(x, y), (x + 14 + 4 * (i % 3), y)], col, 0.5)


def enter_x(t, t0, t1, x0, x1):
    return lerp(x0, x1, ease_out(seg(t, t0, t1)))


# ================================================================ FURD
def frame_furd(t):
    cv = Cv()
    logo = logo_furd
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (20, 60, 150), (50, 110, 210), (10, 30, 90))
    if t >= T_INTRO:
        cv.gradient((95, 170, 240), (200, 230, 250), 0, 66)
        cv.circ(80, 18, 8, (255, 240, 170, 255))
        clouds(cv, t, 3, 10)
        hills(cv, 56, t, 10, (90, 150, 110, 255), 7, 0.06)
        hills(cv, 64, t, 24, (60, 125, 85, 255), 5, 0.10)
        road(cv, 66, t, 90)
        x = enter_x(t, T_INTRO + 0.2, T_INTRO + 1.4, -40, 42)
        if t > 8.6:
            x = lerp(42, 160, ease_in_out(seg(t, 8.6, 9.9)) ** 1.6)
        bob = math.sin(t * 17) * 0.35
        pickup(cv, x, 86 + bob, 0.62, (20, 85, 190, 255), t)
        speed_lines(cv, t, 60, 95)
        pop_text(cv, "FURD RANCHERA", 50, 32, 8.4, (255, 255, 255, 255), t, 3.0, hold_until=6.4, face=FONT_SERIF, stroke=0.9, stroke_fill=(10, 40, 110, 255))
        pop_text(cv, "¡Fuerza de campo!", 50, 44, 6.2, (255, 235, 120, 255), t, 3.5, hold_until=6.4, stroke=0.7, stroke_fill=(10, 40, 110, 255))
        ribbon(cv, "Caja grande, sueños grandes", 36, t, 6.9, (255, 255, 255, 255), (15, 55, 140, 255), 5.0, hold_until=9.7)
        ribbon(cv, "Aguanta todo. Menos la dieta.", 48, t, 7.6, (255, 215, 60, 255), (60, 40, 0, 255), 4.6, hold_until=9.7)
        corner_logo(cv, t, logo, 5.5, panel=(10, 30, 90, 150))
        flash(cv, t, T_INTRO, 0.25)
    outro(cv, t, logo, (10, 40, 110), (5, 20, 60), "Furd — Ve más lejos.", "*Los sueños no incluyen gasolina. Parodia ficticia.")
    fade_ends(cv, t)
    return cv.done()


def audio_furd():
    tr = Track()
    beat = 60 / 108
    prog = [(45, 'm'), (41, 'M'), (48, 'M'), (43, 'M')]  # Am F C G
    for bar in range(7):
        t0 = 0.15 + bar * beat * 4
        root, q = prog[bar % 4]
        third = 3 if q == 'm' else 4
        for st in range(8):
            ts = t0 + st * beat / 2
            ch = [root + 12, root + 12 + third, root + 19, root + 24]
            for j, m in enumerate(ch[:3 if st % 2 else 4]):
                tr.add(pluck(m, 0.5, 0.994, 0.7), ts + j * 0.012, 0.18)
        for st in range(4):
            tr.add(note(root - 12 if st % 2 == 0 else root - 5, beat * 0.9, "saw", cutoff=420, r=0.15), t0 + st * beat, 0.30)
            tr.add(kick(), t0 + st * beat if st % 2 == 0 else t0 + st * beat, 0.5 if st % 2 == 0 else 0.0)
            if st % 2:
                tr.add(snare(), t0 + st * beat, 0.5)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.35)
    tr.add(whoosh(1.0), 0.0, 0.5)
    tr.add(kick(0.5), 0.25, 1.0)
    tr.add(sfx("pickup_start"), 2.3, 0.7)
    tr.add(loop_to(sfx("pickup_engine"), 6.0, 1.0), 3.3, 0.35)
    tr.add(sfx("pickup_horn"), 6.2, 0.8)
    tr.add(whoosh(0.8), 8.6, 0.6)
    tr.add(chime(79, 1.4), 7.0, 0.30)
    tr.add(chime(84, 1.4), 7.6, 0.30)
    for m, ts in ((57, 10.05), (64, 10.05), (72, 10.05), (76, 10.45)):
        tr.add(note(m, 1.5, "saw", cutoff=1800, a=0.02, r=0.5), ts, 0.14)
    return tr.master()


# ================================================================ AMAZOOM
def frame_amazoom(t):
    cv = Cv()
    logo = lambda cv_, x, y, s, a=255: logo_amazoom(cv_, x, y, s, a)
    if t < T_INTRO + 0.3:
        intro(cv, t, lambda c, x, y, s: logo_amazoom(c, x, y, s * 0.85), (255, 153, 0), (255, 200, 90), (230, 110, 0))
    if t >= T_INTRO:
        cv.gradient((255, 250, 235), (255, 225, 170), 0, 100)
        # ciudad
        for i in range(8):
            bx = (i * 15 - (t * 14) % 15) - 5
            h = 18 + (i * 7) % 14
            cv.rect(bx, 66 - h, bx + 11, 66, (255, 214, 150, 255))
            for wy in range(3):
                cv.rect(bx + 2, 66 - h + 3 + wy * 6, bx + 4, 66 - h + 5 + wy * 6, (255, 240, 200, 255))
                cv.rect(bx + 7, 66 - h + 3 + wy * 6, bx + 9, 66 - h + 5 + wy * 6, (255, 240, 200, 255))
        road(cv, 68, t, 80, col=(70, 60, 55))
        x = enter_x(t, T_INTRO + 0.2, T_INTRO + 1.5, -50, 48)
        if t > 8.7:
            x = lerp(48, 170, ease_in_out(seg(t, 8.7, 10.0)) ** 1.6)
        delivery_truck(cv, x, 86 + math.sin(t * 15) * 0.3, 0.7, t)
        # cajas que saltan
        for i in range(4):
            tb = 4.0 + i * 0.55
            if t > tb and t < 7.0:
                p = seg(t, tb, tb + 0.9)
                bx = lerp(x + 6, 78 + i * 5, p)
                by = 76 - math.sin(p * math.pi) * 28 - p * 4
                cv.rect(bx - 3, by - 3, bx + 3, by + 3, (200, 150, 90, 255), r=0.6)
                cv.rect(bx - 0.5, by - 3, bx + 0.5, by + 3, (255, 230, 170, 255))
        pop_text(cv, "¡Pídelo hoy!", 50, 30, 9.0, (30, 40, 80, 255), t, 3.0, hold_until=6.8, stroke=0.0)
        pop_text(cv, "Llega ayer*", 50, 42, 7.2, (255, 130, 0, 255), t, 3.8, hold_until=6.8, stroke=0.0)
        ribbon(cv, "Envío gratis en cajas sorpresa", 34, t, 7.0, (30, 40, 80, 255), (255, 255, 255, 255), 4.4, hold_until=9.7)
        ribbon(cv, "Cada día es un día de entrega", 46, t, 7.7, (255, 153, 0, 255), (30, 20, 0, 255), 4.4, hold_until=9.7)
        corner_logo(cv, t, lambda c, x, y, s: logo_amazoom(c, x, y, s * 0.95), 5.0, x=16, panel=(255, 255, 255, 190))
        flash(cv, t, T_INTRO, 0.25)
    outro(cv, t, lambda c, x, y, s: logo_amazoom(c, x, y, s * 0.9, 255, (255, 255, 255)), (255, 153, 0), (225, 100, 0), "amazoom — Todo en una cajita.", "*Ayer es una forma de hablar. Parodia ficticia.", (30, 40, 80))
    fade_ends(cv, t)
    return cv.done()


def audio_amazoom():
    tr = Track()
    beat = 60 / 124
    prog = [60, 65, 67, 62]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        arp = [r, r + 4, r + 7, r + 12, r + 7, r + 4, r + 12, r + 16]
        for st in range(8):
            tr.add(marimba(arp[st] + (12 if bar % 2 else 0), 0.3), t0 + st * beat / 2, 0.45)
        for st in range(4):
            tr.add(pluck(r - 24 + (7 if st % 2 else 0), 0.4, 0.99, 0.8), t0 + st * beat, 0.5)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.35)
        tr.add(clap(), t0 + beat, 0.45)
        tr.add(clap(), t0 + 3 * beat, 0.45)
        tr.add(kick(), t0, 0.6)
        tr.add(kick(), t0 + 2 * beat, 0.6)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    tr.add(whoosh(1.0), 0.0, 0.4)
    tr.add(sfx("delivery_truck_start"), 2.3, 0.7)
    tr.add(loop_to(sfx("delivery_truck_engine"), 6.2, 1.0), 3.3, 0.35)
    for i in range(4):
        tr.add(sparkle(84 + i * 3, 0.4), 4.0 + i * 0.55 + 0.7, 0.4)
    tr.add(sfx("delivery_truck_horn"), 7.2, 0.7)
    tr.add(sfx("doorbell"), 7.0, 0.5)
    tr.add(whoosh(0.8), 8.7, 0.6)
    for m, ts in ((60, 10.05), (64, 10.05), (67, 10.05), (72, 10.35)):
        tr.add(marimba(m + 12, 0.6), ts, 0.5)
    return tr.master()


# ================================================================ LAMBORGONZO
def frame_lambor(t):
    cv = Cv()
    logo = logo_lambor
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (18, 18, 22), (255, 200, 0), (45, 40, 10))
    if t >= T_INTRO:
        cv.gradient((20, 20, 28), (55, 45, 15), 0, 100)
        # rejilla de neón
        off = (t * 30) % 12
        for i in range(-1, 10):
            y = 62 + i * 12 - off + 12
            if 62 < y < 100:
                cv.line([(0, y), (100, y)], (255, 200, 0, 60), 0.4)
        for i in range(-6, 7):
            cv.line([(50 + i * 4, 62), (50 + i * 14, 100)], (255, 200, 0, 60), 0.4)
        cv.rect(0, 61.5, 100, 62.5, (255, 200, 0, 200))
        cv.glow(50, 62, 50, (255, 200, 0), 0.25)
        for i in range(6):
            sx = (i * 29 + 10) % 100
            cv.circ(sx, 8 + (i * 13) % 40, 0.5 + (math.sin(t * 4 + i) + 1) * 0.4, (255, 255, 255, 220))
        x = enter_x(t, T_INTRO + 0.2, T_INTRO + 1.2, -50, 46)
        if t > 8.7:
            x = lerp(46, 170, ease_in_out(seg(t, 8.7, 10.0)) ** 1.6)
        y = 78 + math.sin(t * 20) * 0.25
        if 4.4 < t < 6.6:
            y -= math.sin(seg(t, 4.4, 6.6) * math.pi) * 16
        cv.glow(x, y - 10, 24, (255, 200, 0), 0.25)
        rc_car(cv, x, y + 8, 0.66, t)
        speed_lines(cv, t, 64, 96, (255, 200, 0, 110))
        pop_text(cv, "HURACANITO RC", 50, 28, 8.4, (255, 215, 0, 255), t, 3.0, hold_until=6.9, stroke=0.8, stroke_fill=(0, 0, 0, 255))
        pop_text(cv, "¡Pequeño. Furioso!", 50, 40, 6.0, (255, 255, 255, 255), t, 3.5, hold_until=6.9, stroke=0.7, stroke_fill=(0, 0, 0, 255))
        ribbon(cv, "1:10 de velocidad, 10:10 de furia", 34, t, 7.0, (255, 200, 0, 255), (20, 20, 20, 255), 4.2, hold_until=9.7)
        ribbon(cv, "A control remoto. Sin licencia.", 46, t, 7.7, (255, 255, 255, 255), (20, 20, 20, 255), 4.4, hold_until=9.7)
        corner_logo(cv, t, logo, 5.0, panel=(0, 0, 0, 160))
        flash(cv, t, T_INTRO, 0.25)
        flash(cv, t, 5.4, 0.18, (255, 215, 0))
    outro(cv, t, logo, (18, 18, 22), (50, 42, 10), "Lamborgonzo — Furia en miniatura.", "*Pilas no incluidas. Parodia ficticia.", (255, 200, 0))
    fade_ends(cv, t)
    return cv.done()


def audio_lambor():
    tr = Track()
    beat = 60 / 128
    prog = [57, 53, 60, 55]
    # riser
    tr.add(swell(2.1, 120, 1400, "saw", 3500), 0.1, 0.5)
    for i in range(8):
        tr.add(snare(), 1.0 + i * beat / 2 * 0.8, 0.12 * (i + 1) / 8 * 3)
    for bar in range(5):
        t0 = T_INTRO + 0.05 + bar * beat * 4
        r = prog[bar % 4]
        for st in range(4):
            tr.add(kick(), t0 + st * beat, 0.9)
            tr.add(hat(open_=True), t0 + st * beat + beat / 2, 0.45)
            tr.add(note(r - 24, beat * 0.45, "saw", cutoff=260, r=0.05), t0 + st * beat + beat / 2, 0.55)
        tr.add(clap(), t0 + beat, 0.6)
        tr.add(clap(), t0 + 3 * beat, 0.6)
        for j, iv in enumerate((0, 3, 7)):
            tr.add(note(r + iv, beat * 1.9, "saw", cutoff=2600, a=0.005, r=0.1), t0 + (0 if bar % 2 == 0 else beat * 2) + (0 if True else 0), 0.10)
            tr.add(note(r + iv + 12, beat * 0.6, "square", cutoff=3500, a=0.002, r=0.05), t0 + beat * 1.5, 0.07)
            tr.add(note(r + iv + 12, beat * 0.6, "square", cutoff=3500, a=0.002, r=0.05), t0 + beat * 3.5, 0.07)
    tr.add(kick(0.6), 0.0, 0.0)
    tr.add(kick(0.7), 2.2, 1.0)
    tr.add(sfx("rc_car_start"), 2.3, 0.8)
    tr.add(loop_to(sfx("rc_car_motor"), 6.5, 1.15), 3.3, 0.5)
    tr.add(whoosh(0.9), 4.3, 0.6)
    tr.add(kick(0.8), 5.4, 1.0)
    tr.add(whoosh(0.8), 8.6, 0.6)
    for m, ts in ((57, 10.05), (60, 10.05), (64, 10.05), (69, 10.4)):
        tr.add(note(m, 1.4, "saw", cutoff=2200, a=0.01, r=0.6), ts, 0.16)
    return tr.master()


# ================================================================ BOING
def frame_boing(t):
    cv = Cv()
    logo = logo_boing
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (10, 70, 190), (120, 200, 250), (5, 30, 110))
    if t >= T_INTRO:
        cv.gradient((40, 120, 225), (190, 225, 250), 0, 100)
        cv.circ(18, 20, 7, (255, 245, 190, 255))
        cv.glow(18, 20, 22, (255, 245, 190), 0.4)
        clouds(cv, t, 12, 18, n=6)
        clouds(cv, t + 9, 20, 55, (255, 255, 255, 200), n=4)
        if t < 7.0:
            hx = enter_x(t, T_INTRO + 0.2, T_INTRO + 1.5, -40, 48)
            hy = 74 + math.sin(t * 2.3) * 1.4
            if t > 6.2:
                hx = lerp(48, 170, ease_in(seg(t, 6.2, 7.0))) if False else lerp(48, 170, seg(t, 6.2, 7.0) ** 2)
            helicopter(cv, hx, hy + 12, 0.72, t)
        if t >= 6.8 and t < 10.5:
            ax = lerp(-60, 170, seg(t, 6.8, 10.3))
            ay = 64 - seg(t, 6.8, 10.3) * 22
            for i in range(10):
                cv.line([(ax - 62 - i * 3, ay - 32), (ax - 50 - i * 3, ay - 32)], (255, 255, 255, 110 - i * 9), 1.2)
            airliner(cv, ax, ay + 40, 0.7, t)
        pop_text(cv, "HELICÓPTERO HX-6", 50, 24, 6.8, (255, 255, 255, 255), t, 3.0, hold_until=6.3, stroke=0.8, stroke_fill=(10, 60, 160, 255))
        pop_text(cv, "¡Aspas que sí aspean!", 50, 33, 5.0, (255, 235, 120, 255), t, 3.6, hold_until=6.3, stroke=0.7, stroke_fill=(10, 60, 160, 255))
        ribbon(cv, "Jet 7G7: el cielo es de todos", 24, t, 7.0, (255, 255, 255, 255), (10, 60, 160, 255), 4.6, hold_until=9.7)
        ribbon(cv, "Con equipaje y sin turbulencias*", 36, t, 7.7, (10, 60, 160, 255), (255, 255, 255, 255), 4.2, hold_until=9.7)
        corner_logo(cv, t, lambda c, x, y, s: logo_boing(c, x, y, s * 0.75), 5.0, x=14, panel=(255, 255, 255, 120))
        flash(cv, t, T_INTRO, 0.25)
    outro(cv, t, logo, (10, 70, 190), (5, 35, 120), "BOING — Vuela con nosotros.", "*Las turbulencias son cosa de los pilotos. Parodia ficticia.")
    fade_ends(cv, t)
    return cv.done()


def audio_boing():
    tr = Track()
    beat = 60 / 92
    prog = [(57, 'm'), (53, 'M'), (48, 'M'), (55, 'M')]
    tr.add(swell(2.1, 90, 500, "saw", 1800), 0.1, 0.5)
    for bar in range(6):
        t0 = T_INTRO + bar * beat * 4 - beat * 0.0
        r, q = prog[bar % 4]
        third = 3 if q == 'm' else 4
        for iv in (0, third, 7, 12):
            tr.add(note(r + iv, beat * 4, "saw", a=0.4, d=0.5, s=0.7, r=0.5, cutoff=1500), t0, 0.07)
        tr.add(kick(0.6), t0, 0.9)
        tr.add(kick(0.6), t0 + beat * 1.5, 0.7)
        tr.add(kick(0.6), t0 + beat * 2, 0.9)
        for st in range(8):
            tr.add(pluck(r + 24 + (0, third, 7, 12)[st % 4], 0.5, 0.994, 0.6), t0 + st * beat / 2, 0.16)
    tr.add(swell(2.1, 60, 300, "saw", 900), 0.0, 0.0)
    tr.add(kick(1.0), 0.25, 1.0)
    tr.add(sparkle(91, 1.0), 0.3, 0.6)
    tr.add(sfx("helicopter_start"), 2.3, 0.7)
    tr.add(loop_to(sfx("helicopter_rotor"), 4.2, 1.0), 3.3, 0.55)
    tr.add(sfx("airliner_start"), 6.9, 0.55)
    tr.add(loop_to(sfx("airliner_engine"), 3.4, 1.0), 7.6, 0.5)
    tr.add(chime(88, 1.5), 7.0, 0.3)
    for m, ts in ((53, 10.05), (60, 10.05), (65, 10.05), (69, 10.05), (77, 10.4)):
        tr.add(note(m, 1.6, "saw", cutoff=1800, a=0.04, r=0.7), ts, 0.13)
    return tr.master()


# ================================================================ BUGERATI
def frame_bugerati(t):
    cv = Cv()
    logo = logo_bugerati
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (95, 15, 35), (190, 60, 80), (45, 5, 20))
    if t >= T_INTRO:
        cv.gradient((90, 14, 32), (40, 6, 18), 0, 100)
        for i in range(0, 100, 10):
            cv.line([(i, 0), (i, 72)], (235, 190, 90, 30), 0.3)
        cv.rect(0, 72, 100, 100, (30, 8, 14, 255))
        cv.rect(0, 72, 100, 73, (235, 190, 90, 200))
        lit = 0.7 + 0.3 * math.sin(t * 9) * (1 if (t * 7) % 3 < 2.5 else 0.2)
        drop = ease_out(seg(t, T_INTRO + 0.2, T_INTRO + 1.6))
        chandelier(cv, 50, -34 + drop * 40, 1.15, t, lit=1.0 if t < 3.5 else lit)
        # lámparas laterales
        lampL = clamp(seg(t, 4.3, 4.7))
        lampR = clamp(seg(t, 5.1, 5.5))
        table_lamp(cv, 12, 88, 0.8, lit=lampL)
        table_lamp(cv, 88, 88, 0.8, lit=lampR)
        # neones
        nz = 0.55 if (t * 5) % 2 < 0.2 else 1.0
        if t > 6.0:
            neon_heart(cv, 27, 52, 10, (255, 60, 150, 255), nz)
            neon_ring(cv, 73, 52, 7, (60, 220, 255, 255), nz)
        pop_text(cv, "BUGERATI", 50, 62, 7.5, (235, 190, 90, 255), t, 3.0, hold_until=6.3, face=FONT_SERIF, stroke=0.6, stroke_fill=(30, 5, 10, 255))
        pop_text(cv, "Luz con clase", 50, 69, 4.4, (255, 235, 200, 255), t, 3.5, hold_until=6.3, face=FONT_SERIF, stroke=0.5, stroke_fill=(30, 5, 10, 255))
        ribbon(cv, "Arañas · Neones · Lámparas", 36, t, 7.0, (235, 190, 90, 255), (50, 8, 20, 255), 4.8, hold_until=9.7, face=FONT_SERIF)
        ribbon(cv, "Brilla sin pedir permiso", 82, t, 7.7, (20, 4, 10, 255), (235, 190, 90, 255), 4.8, hold_until=9.7, face=FONT_SERIF)
        corner_logo(cv, t, lambda c, x, y, s: logo_bugerati(c, x, y, s * 0.95), 5.0, x=15, panel=(0, 0, 0, 130))
        flash(cv, t, T_INTRO, 0.25, (255, 230, 170))
    outro(cv, t, logo, (70, 10, 26), (30, 4, 12), "Bugerati — Brilla sin pedir permiso.", "*No incluye electricista. Parodia ficticia.", (235, 190, 90))
    fade_ends(cv, t)
    return cv.done()


def audio_bugerati():
    tr = Track()
    beat = 60 / 84
    prog = [(50, 'm'), (55, 'M'), (53, 'M'), (57, 'm')]
    for bar in range(6):
        t0 = 0.2 + bar * beat * 4
        r, q = prog[bar % 4]
        third = 3 if q == 'm' else 4
        for j, iv in enumerate((0, third, 7, 10 if q == 'm' else 11)):
            tr.add(epiano(r + 12 + iv, 1.6), t0 + j * 0.03, 0.22)
        for st in range(2):
            tr.add(epiano(r + 24 + (third, 7)[st], 0.9), t0 + (2 + st * 0.75) * beat, 0.14)
        for st in range(4):
            tr.add(pluck(r - 12 if st % 2 == 0 else r - 5, 0.6, 0.995, 0.8), t0 + st * beat, 0.45)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.25)
        tr.add(snare(), t0 + beat, 0.18)
        tr.add(snare(), t0 + 3 * beat, 0.18)
        tr.add(kick(), t0, 0.5)
    tr.add(chime(86, 2.0), 0.3, 0.35)
    tr.add(sfx("elevator_ding"), 2.3, 0.6)
    tr.add(whoosh(1.2), 2.4, 0.3)
    for i, ts in enumerate((4.3, 5.1)):
        tr.add(chime(79 + i * 5, 1.2), ts, 0.3)
    for i, ts in enumerate((6.0, 6.5, 6.9)):
        tr.add(sparkle(91 + i * 3, 0.5), ts, 0.4)
    tr.add(chime(91, 1.5), 7.0, 0.3)
    for m, ts in ((50, 10.05), (57, 10.05), (62, 10.05), (66, 10.4), (74, 10.7)):
        tr.add(epiano(m + 12, 1.6), ts, 0.3)
    return tr.master()


# ================================================================ BUGOTTI
def frame_bugotti(t):
    cv = Cv()
    logo = logo_bugotti
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (8, 14, 45), (40, 90, 220), (2, 4, 20))
    if t >= T_INTRO:
        cv.gradient((6, 10, 40), (30, 50, 120), 0, 70)
        for i in range(30):
            cv.circ((i * 37) % 100, (i * 17) % 55, 0.35 + 0.3 * math.sin(t * 3 + i), (255, 255, 255, 200))
        # ciudad nocturna
        for i in range(9):
            bx = (i * 13 - (t * 9) % 13) - 4
            h = 12 + (i * 11) % 18
            cv.rect(bx, 68 - h, bx + 9, 68, (12, 18, 50, 255))
            for wy in range(0, int(h) - 4, 5):
                if (i * 3 + wy) % 4:
                    cv.rect(bx + 2, 68 - h + 2 + wy, bx + 4, 68 - h + 4 + wy, (255, 220, 120, 255))
        road(cv, 68, t, 140, col=(20, 22, 34), line=(120, 160, 255, 255))
        cv.rect(0, 68, 100, 69, (80, 140, 255, 220))
        x = enter_x(t, T_INTRO + 0.2, T_INTRO + 1.2, -50, 44)
        if t > 8.7:
            x = lerp(44, 175, ease_in_out(seg(t, 8.7, 10.0)) ** 1.7)
        cv.glow(x + 56 * 0.68, 84, 16, (200, 230, 255), 0.35)
        supercar(cv, x, 88 + math.sin(t * 22) * 0.2, 0.68, t)
        speed_lines(cv, t, 70, 96, (140, 190, 255, 120))
        pop_text(cv, "BUGOTTI CHILLÓN", 50, 24, 7.8, (240, 215, 130, 255), t, 3.0, hold_until=6.9, face=FONT_SERIF, stroke=0.7, stroke_fill=(5, 8, 30, 255))
        pop_text(cv, "¡Escúchalo antes de verlo!", 50, 34, 4.6, (255, 255, 255, 255), t, 3.5, hold_until=6.9, stroke=0.6, stroke_fill=(5, 8, 30, 255))
        ribbon(cv, "Más rápido que tu excusa", 30, t, 7.0, (240, 215, 130, 255), (10, 14, 50, 255), 4.8, hold_until=9.7, face=FONT_SERIF)
        ribbon(cv, "Silencio: opcional. Ruido: elegante.", 42, t, 7.7, (255, 255, 255, 255), (10, 14, 50, 255), 3.9, hold_until=9.7, face=FONT_SERIF)
        corner_logo(cv, t, lambda c, x, y, s: logo_bugotti(c, x, y - 1, s * 0.7), 5.0, x=14, panel=(0, 0, 0, 140))
        flash(cv, t, T_INTRO, 0.25, (180, 210, 255))
    outro(cv, t, logo, (6, 10, 40), (2, 4, 20), "Bugotti — El ruido es elegancia.", "*Los oídos no incluidos. Parodia ficticia.", (240, 215, 130))
    fade_ends(cv, t)
    return cv.done()


def audio_bugotti():
    tr = Track()
    beat = 60 / 100
    prog = [45, 41, 43, 40]
    for bar in range(6):
        t0 = 0.2 + bar * beat * 4
        r = prog[bar % 4]
        tr.add(note(r - 12, beat * 3.8, "sine", a=0.05, d=0.3, s=0.9, r=0.3), t0, 0.7)
        for iv in (0, 7, 12, 15):
            tr.add(note(r + iv + 12, beat * 4, "saw", a=0.6, d=0.5, s=0.7, r=0.5, cutoff=1400), t0, 0.06)
        for st in range(8):
            tr.add(note(r + 24 + (0, 7, 12, 7)[st % 4], 0.25, "tri", a=0.005, r=0.1), t0 + st * beat / 2, 0.10)
        tr.add(kick(0.5), t0, 0.7)
        tr.add(kick(0.5), t0 + beat * 2.5, 0.6)
        tr.add(snare(), t0 + beat * 2, 0.4)
    tr.add(swell(2.0, 60, 400, "saw", 1500), 0.1, 0.45)
    tr.add(kick(1.0), 0.25, 1.1)
    tr.add(sfx("bugotti_chillon_start"), 2.3, 0.8)
    tr.add(loop_to(sfx("bugotti_chillon_engine"), 6.3, 1.0), 3.3, 0.5)
    tr.add(sfx("bugotti_chillon_horn"), 5.4, 0.7)
    tr.add(whoosh(0.8), 8.7, 0.6)
    tr.add(chime(88, 1.5), 7.0, 0.25)
    for m, ts in ((45, 10.05), (52, 10.05), (57, 10.05), (64, 10.4)):
        tr.add(note(m, 1.6, "saw", cutoff=1600, a=0.03, r=0.7), ts, 0.14)
    return tr.master()


# ================================================================ HS
def frame_hs(t):
    cv = Cv()
    logo = logo_hs
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (0, 70, 170), (60, 160, 250), (0, 30, 100))
    if t >= T_INTRO:
        cv.gradient((205, 232, 252), (240, 247, 255), 0, 72)
        for i in range(10):
            for j in range(6):
                cv.circ(5 + i * 10.5, 6 + j * 11, 0.45, (150, 190, 230, 150))
        cv.rect(0, 72, 100, 100, (150, 102, 62, 255))
        cv.rect(0, 72, 100, 74, (190, 140, 95, 255))
        for i in range(0, 100, 17):
            cv.line([(i + (t * 0) % 1, 74), (i - 6, 100)], (130, 88, 52, 255), 0.4)
        off = ease_in_out(seg(t, 8.8, 9.9)) * 170
        op = ease_out(seg(t, T_INTRO + 0.3, T_INTRO + 1.5))
        lx = 36 - off
        # ondas wifi
        if t > 5.0:
            for i in range(3):
                a = int(200 * (0.5 + 0.5 * math.sin(t * 5 - i * 0.9)))
                r = 5 + i * 4
                cv.line([(lx + math.cos(math.radians(a2)) * r, 36 - math.sin(math.radians(a2)) * r) for a2 in range(40, 141, 10)], (0, 140, 255, a), 0.7)
        laptop(cv, lx, 88, 1.1, op, t)
        if t > 6.9:
            px = lerp(160, 86, ease_out(seg(t, 6.9, 7.8))) - off
            printer(cv, px, 88, 0.9, t, 6.9)
        pop_text(cv, "PORTÁTIL AZUL HS", 50, 12, 7.0, (0, 70, 170, 255), t, 3.0, hold_until=6.7, stroke=0.0)
        pop_text(cv, "¡Abre y listo!", 50, 22, 5.6, (255, 130, 0, 255), t, 3.6, hold_until=6.7, stroke=0.0)
        ribbon(cv, "Navegador HS: la web, sin esperar*", 12, t, 7.0, (0, 110, 220, 255), (255, 255, 255, 255), 3.7, hold_until=9.7)
        ribbon(cv, "Impresora HS: papel que sí sale", 24, t, 7.7, (255, 255, 255, 255), (0, 70, 170, 255), 4.2, hold_until=9.7)
        flash(cv, t, T_INTRO, 0.25)
    outro(cv, t, logo, (0, 80, 190), (0, 35, 110), "HS — Inventamos el clic.", "*Esperar es opcional. Parodia ficticia.")
    fade_ends(cv, t)
    return cv.done()


def audio_hs():
    tr = Track()
    beat = 60 / 118
    prog = [60, 57, 65, 67]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        arp = [r, r + 4, r + 7, r + 12, r + 16, r + 12, r + 7, r + 4]
        for st in range(8):
            tr.add(epiano(arp[st] + 12, 0.45), t0 + st * beat / 2, 0.30)
        for st in range(4):
            tr.add(note(r - 24 + (7 if st == 3 else 0), beat * 0.8, "square", cutoff=500, r=0.1), t0 + st * beat, 0.22)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.3)
        tr.add(kick(), t0, 0.55)
        tr.add(kick(), t0 + 2 * beat, 0.55)
        tr.add(clap(), t0 + beat, 0.4)
        tr.add(clap(), t0 + 3 * beat, 0.4)
    tr.add(whoosh(1.0), 0.0, 0.4)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    for i, m in enumerate((72, 76, 79, 84)):
        tr.add(chime(m, 1.0), 2.3 + i * 0.12, 0.30)        # sonido de arranque
    tr.add(sfx("elevator_ding"), 3.4, 0.5)
    for i in range(6):
        tr.add(sparkle(84 + (i % 3) * 4, 0.3), 5.0 + i * 0.12, 0.4)
    tr.add(sfx("printer"), 7.6, 0.8)
    tr.add(whoosh(0.8), 8.8, 0.6)
    for m, ts in ((60, 10.05), (64, 10.05), (67, 10.05), (72, 10.4)):
        tr.add(epiano(m + 12, 1.6), ts, 0.35)
    return tr.master()


BRANDS = {
    "hs": (frame_hs, audio_hs),
    "furd": (frame_furd, audio_furd),
    "amazoom": (frame_amazoom, audio_amazoom),
    "lamborgonzo": (frame_lambor, audio_lambor),
    "boing": (frame_boing, audio_boing),
    "bugerati": (frame_bugerati, audio_bugerati),
    "bugotti": (frame_bugotti, audio_bugotti),
}


def _render(args):
    brand, i, outdir = args
    fn = BRANDS[brand][0]
    fn(i / FPS).save(os.path.join(outdir, "f%04d.png" % i))
    return i


def render_brand(brand, outroot, posters):
    frames = os.path.join(outroot, "frames_" + brand)
    shutil.rmtree(frames, ignore_errors=True)
    os.makedirs(frames)
    n = int(DUR * FPS)
    with ProcessPoolExecutor(2) as ex:
        list(ex.map(_render, [(brand, i, frames) for i in range(n)], chunksize=8))
    wav = os.path.join(outroot, brand + ".wav")
    write_wav(wav, BRANDS[brand][1]())
    mp4 = os.path.join(outroot, brand + ".mp4")
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-framerate", str(FPS), "-i", os.path.join(frames, "f%04d.png"), "-i", wav,
                    "-c:v", "libx264", "-profile:v", "baseline", "-level", "3.0", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "26",
                    "-g", "40", "-bf", "0", "-c:a", "aac", "-profile:a", "aac_low", "-ar", "44100", "-ac", "1", "-b:a", "64k",
                    "-movflags", "+faststart", "-shortest", mp4], check=True)
    from PIL import Image
    # póster: fotograma del logo/héroe
    Image.open(os.path.join(frames, "f%04d.png" % int(10.9 * FPS))).save(os.path.join(posters, "poster_%s.png" % brand))
    # hoja de contacto para revisar
    sheet = Image.new("RGB", (6 * 192, 2 * 192))
    for k in range(12):
        im = Image.open(os.path.join(frames, "f%04d.png" % min(n - 1, int((k + 0.5) * n / 12)))).resize((192, 192))
        sheet.paste(im, ((k % 6) * 192, (k // 6) * 192))
    sheet.save(os.path.join(outroot, "sheet_%s.png" % brand))
    shutil.rmtree(frames)
    return mp4


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    for b in (sys.argv[2:] or list(BRANDS)):
        print(b, render_brand(b, out, posters), flush=True)
