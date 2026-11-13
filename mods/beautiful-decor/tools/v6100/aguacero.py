"""V6.10.0: anuncio (video + audio) y póster de la marca parodia Aguacero Bomberos.
Uso: python3 -m tools.v6100.aguacero <carpeta_salida>   (escribe aguacero.mp4 y posters/poster_aguacero.png)"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *

BLUE, DEEP, ORANGE, RED = (40, 140, 235), (14, 50, 120), (255, 150, 30), (205, 30, 30)


def logo_aguacero(cv, cx, cy, s, a=255, color=(255, 255, 255), name=True):
    """Gota azul con una llama dentro y una A; debajo el nombre."""
    drop = [(cx, cy - s * 0.85), (cx + s * 0.18, cy - s * 0.5), (cx + s * 0.5, cy - s * 0.05), (cx + s * 0.55, cy + s * 0.25), (cx + s * 0.4, cy + s * 0.58),
            (cx, cy + s * 0.72), (cx - s * 0.4, cy + s * 0.58), (cx - s * 0.55, cy + s * 0.25), (cx - s * 0.5, cy - s * 0.05), (cx - s * 0.18, cy - s * 0.5)]
    cv.poly(drop, (255, 255, 255, a))
    k = 0.88
    cv.poly([(cx + (x - cx) * k, cy + 0.04 * s + (y - cy) * k) for x, y in drop], BLUE + (a,))
    flame = [(cx, cy - s * 0.42), (cx + s * 0.2, cy - s * 0.1), (cx + s * 0.3, cy + s * 0.18), (cx + s * 0.18, cy + s * 0.4), (cx - s * 0.18, cy + s * 0.4),
             (cx - s * 0.3, cy + s * 0.18), (cx - s * 0.16, cy - s * 0.02), (cx - s * 0.08, cy - s * 0.2)]
    cv.poly(flame, ORANGE + (a,))
    cv.poly([(cx, cy - s * 0.1), (cx + s * 0.1, cy + s * 0.14), (cx + s * 0.07, cy + s * 0.34), (cx - s * 0.07, cy + s * 0.34), (cx - s * 0.1, cy + s * 0.14)], (255, 225, 90, a))
    if name:
        cv.text("AGUACERO BOMBEROS", cx, cy + s * 1.15, s * 0.25, color + (a,), face=FONT_BOLD)


def flames(cv, x, y, w, h, t, f):
    """Llamas sobre una ventana o techo; f = intensidad 0..1."""
    if f <= 0.02:
        return
    n = 5
    for i in range(n):
        fx = x + (i + 0.5) * w / n
        hh = h * f * (0.7 + 0.3 * math.sin(t * 9 + i * 1.7))
        cv.poly([(fx - w / n * 0.6, y), (fx - w / n * 0.2, y - hh * 0.55), (fx + math.sin(t * 7 + i) * 1.2, y - hh), (fx + w / n * 0.25, y - hh * 0.5), (fx + w / n * 0.6, y)], (255, 110, 20, 235))
        cv.poly([(fx - w / n * 0.3, y), (fx, y - hh * 0.6), (fx + w / n * 0.3, y)], (255, 220, 80, 240))
    cv.glow(x + w / 2, y - h * 0.4 * f, h * 1.1 * f + 4, (255, 140, 40), 0.45 * f)


def truck(cv, x, y, k, t):
    cv.ellipse(x - 20 * k, y + 5.5 * k, x + 20 * k, y + 8 * k, (0, 0, 0, 70))
    cv.rect(x - 19 * k, y - 8 * k, x + 8 * k, y + 5 * k, RED + (255,), r=1.2 * k)
    cv.rect(x + 8 * k, y - 4 * k, x + 19 * k, y + 5 * k, RED + (255,), r=1.5 * k)
    cv.poly([(x + 9 * k, y - 4 * k), (x + 12 * k, y - 9 * k), (x + 17 * k, y - 9 * k), (x + 18.5 * k, y - 4 * k)], RED + (255,))
    cv.poly([(x + 10.5 * k, y - 4.5 * k), (x + 12.5 * k, y - 8 * k), (x + 16 * k, y - 8 * k), (x + 17 * k, y - 4.5 * k)], (150, 205, 245, 255))
    cv.rect(x - 19 * k, y - 1 * k, x + 8 * k, y + 1 * k, (255, 255, 255, 255))
    cv.rect(x - 19 * k, y + 1 * k, x + 8 * k, y + 2 * k, (255, 205, 50, 255))
    cv.rect(x - 18 * k, y - 12 * k, x + 7 * k, y - 8 * k, (200, 205, 215, 255))          # escalera
    for i in range(7):
        cv.rect(x - 17 * k + i * 3.5 * k, y - 12 * k, x - 16.2 * k + i * 3.5 * k, y - 8 * k, (120, 125, 135, 255))
    phase = int(t * 6) % 2
    cv.rect(x + 11 * k, y - 11 * k, x + 14 * k, y - 9 * k, RED + (255,) if phase == 0 else (110, 20, 20, 255))
    cv.rect(x + 14 * k, y - 11 * k, x + 17 * k, y - 9 * k, BLUE + (255,) if phase == 1 else (20, 50, 110, 255))
    cv.glow(x + (12.5 if phase == 0 else 15.5) * k, y - 10 * k, 18 * k, RED if phase == 0 else BLUE, 0.5)
    cv.text("AGUACERO", x - 5.5 * k, y - 4.2 * k, 3.1 * k, (255, 255, 255, 255))
    for wx in (-12, 12):
        cv.circ(x + wx * k, y + 5 * k, 3.8 * k, (22, 22, 26, 255))
        cv.circ(x + wx * k, y + 5 * k, 1.8 * k, (190, 195, 205, 255))


def fighter(cv, x, y, k, aim):
    cv.rect(x - 3.5 * k, y - 11 * k, x + 3.5 * k, y + 0.5 * k, (230, 160, 30, 255), r=0.8 * k)
    cv.rect(x - 3.5 * k, y - 6 * k, x + 3.5 * k, y - 5 * k, (230, 230, 235, 255))
    cv.rect(x - 1.8 * k, y, x - 0.2 * k, y + 7 * k, (60, 50, 40, 255))
    cv.rect(x + 0.2 * k, y, x + 1.8 * k, y + 7 * k, (60, 50, 40, 255))
    cv.rect(x - 3.2 * k, y - 18 * k, x + 3.2 * k, y - 11.5 * k, (240, 200, 160, 255), r=0.8 * k)
    cv.rect(x - 4.4 * k, y - 20 * k, x + 4.4 * k, y - 17 * k, (255, 205, 50, 255), r=0.8 * k)
    cv.rect(x + 0.8 * k, y - 15 * k, x + 2 * k, y - 13.8 * k, (20, 20, 20, 255))
    cv.rect(x - 2 * k, y - 15 * k, x - 0.8 * k, y - 13.8 * k, (20, 20, 20, 255))
    cv.line([(x + 3 * k, y - 8 * k), (x + 8 * k, y - 8 * k - aim * 2 * k)], (200, 205, 215, 255), 1.8 * k)


def frame_aguacero(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_aguacero(c, x, y - s * 0.25, s * 0.9)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (16, 70, 160), (90, 190, 255), (6, 22, 60))
    if t >= T_INTRO:
        cv.gradient((10, 12, 40), (60, 30, 40), 0, 70)
        for i in range(24):
            cv.circ((i * 43) % 100, (i * 17) % 45, 0.3, (255, 255, 255, 150))
        fire = 1.0 if t < 5.6 else max(0.0, 1.0 - (t - 5.6) / 3.0)
        # edificio en llamas
        cv.rect(62, 28, 92, 70, (46, 40, 72, 255))
        for r in range(4):
            for c in range(3):
                wx, wy = 65 + c * 9, 31 + r * 9
                lit = fire > 0.35 and (r + c) % 2 == 0
                cv.rect(wx, wy, wx + 5, wy + 5, (255, 170, 50, 255) if lit else (70, 90, 130, 255))
        flames(cv, 62, 28, 30, 22, t, fire)
        flames(cv, 66, 52, 12, 10, t + 1.3, fire * 0.9)
        road(cv, 70, t, 0 if t > 4.4 else 90, col=(38, 38, 50))
        tx = lerp(-25, 26, ease_out(seg(t, 2.4, 4.4)))
        truck(cv, tx, 74, 1.05, t)
        if t >= 4.4:
            fighter(cv, 48, 86, 1.1, seg(t, 4.4, 5.2))
        if 5.2 <= t < 9.2:                                         # chorro de agua
            for i in range(26):
                p = ((i / 26.0) + t * 2.4) % 1.0
                px = lerp(55.0, 66.0, p)
                py = lerp(78.0, 60.0, p) - math.sin(p * math.pi) * 9
                cv.circ(px, py, 0.9, (140, 205, 255, 230))
            cv.glow(66, 60, 10, (140, 205, 255), 0.4)
        if t > 7.5:                                               # humo
            for i in range(5):
                sy = 34 - (t - 7.5) * 4 - i * 4
                cv.circ(70 + i * 5 + math.sin(t * 2 + i) * 2, sy, 3.2 + i * 0.5, (150, 150, 160, 120))
        pop_text(cv, "¿Fuego?", 50, 12, 9.0, (255, 255, 255, 255), t, 2.6, hold_until=5.3, stroke=0.0)
        pop_text(cv, "¡Aguacero Bomberos!", 50, 24, 7.5, (255, 205, 50, 255), t, 3.0, hold_until=5.3, stroke=0.6)
        ribbon(cv, "Extintor portátil: ¡pshhh!", 30, t, 5.4, BLUE + (255,), (255, 255, 255, 255), 4.2, hold_until=9.7)
        ribbon(cv, "Manguera de bolsillo para todos", 41, t, 7.2, (255, 205, 50, 255), (30, 20, 0, 255), 3.8, hold_until=9.7)
        if t > 8.4:
            confetti(cv, t, 8.4, 26, ((90, 190, 255), (255, 150, 30), (255, 255, 255)), 5)
        corner_logo(cv, t, lambda c, x, y, s: logo_aguacero(c, x, y * 0.9, s * 0.8, name=False), 5.0, x=14, y=12)
        flash(cv, t, T_INTRO, 0.25)
        flash(cv, t, 5.2, 0.2, (190, 230, 255))
    outro(cv, t, lambda c, x, y, s: logo_aguacero(c, x, y - s * 0.2, s * 0.9), (16, 70, 160), (6, 22, 60),
          "Aguacero Bomberos — Apagamos hasta tus ganas de quemar.", "*Parodia ficticia. No apague velas de cumpleaños.", (255, 205, 50))
    fade_ends(cv, t)
    return cv.done()


def audio_aguacero():
    tr = Track()
    beat = 60 / 126
    prog = [50, 55, 53, 48]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        for st in range(8):
            tr.add(note(r - 12, beat * 0.45, "saw", cutoff=800, r=0.05), t0 + st * beat / 2, 0.30)
        for st in range(4):
            tr.add(kick(), t0 + st * beat, 0.6)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.35)
        tr.add(snare(), t0 + beat, 0.5)
        tr.add(snare(), t0 + 3 * beat, 0.5)
        if bar >= 2:
            for st in range(4):
                tr.add(note(r + 12 + (st % 2) * 7, beat * 0.5, "square", cutoff=2200, r=0.05), t0 + st * beat, 0.15)
    for s in range(2):                                           # sirena de camión de bomberos
        tr.add(swell(0.7, 500, 900, "saw", 2600), 2.4 + s * 1.4, 0.15)
        tr.add(swell(0.7, 900, 500, "saw", 2600), 3.1 + s * 1.4, 0.15)
    tr.add(whoosh(1.0), 0.0, 0.45)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    tr.add(whoosh(2.8, up=False), 5.2, 0.45)                     # el chorro de agua
    tr.add(sfx("elevator_ding"), 8.9, 0.45)
    for m, ts in ((57, 10.05), (62, 10.05), (66, 10.05), (69, 10.35)):
        tr.add(note(m, 1.3, "saw", cutoff=1800, r=0.4), ts, 0.28)
    return tr.master()


BRANDS["aguacero"] = (frame_aguacero, audio_aguacero)
A.BRANDS["aguacero"] = BRANDS["aguacero"]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("aguacero", out, posters), flush=True)
