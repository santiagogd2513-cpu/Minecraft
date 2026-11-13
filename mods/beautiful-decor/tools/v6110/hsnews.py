"""V6.11.0: anuncio (video + audio) y póster de HS News, el canal de noticias de HS.
Uso: python3 -m tools.v6110.hsnews <carpeta_salida>   (escribe hsnews.mp4 y posters/poster_hsnews.png)"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *

HSBLUE, NRED, DARK = (0, 110, 220), (200, 30, 40), (12, 20, 52)


def logo_hsnews(cv, cx, cy, s, a=255, color=(255, 255, 255), name=True):
    """El cuadrado azul de HS con una franja roja NEWS debajo."""
    cv.rect(cx - s * 0.8, cy - s * 0.85, cx + s * 0.8, cy + s * 0.35, HSBLUE + (a,), r=s * 0.26, outline=(255, 255, 255, a), width=max(0.2, s * 0.05))
    cv.text("HS", cx, cy - s * 0.25, s * 0.9, (255, 255, 255, a), face=FONT_BOLD)
    cv.rect(cx - s * 0.8, cy + s * 0.2, cx + s * 0.8, cy + s * 0.7, NRED + (a,), r=s * 0.12)
    cv.text("NEWS", cx, cy + s * 0.45, s * 0.38, (255, 255, 255, a), face=FONT_BOLD)
    if name:
        cv.text("HS NEWS", cx, cy + s * 1.1, s * 0.27, color + (a,), face=FONT_BOLD)


def anchor(cv, x, y, k, t, talking=True):
    cv.rect(x - 9 * k, y - 4 * k, x + 9 * k, y + 16 * k, (24, 30, 60, 255), r=1.5 * k)
    cv.rect(x - 2.6 * k, y - 4 * k, x + 2.6 * k, y + 6 * k, (245, 245, 250, 255))
    cv.rect(x - 0.7 * k, y - 3 * k, x + 0.7 * k, y + 5 * k, NRED + (255,))
    cv.rect(x - 5 * k, y - 16 * k, x + 5 * k, y - 4.5 * k, (240, 200, 160, 255), r=1 * k)
    cv.rect(x - 5.6 * k, y - 18 * k, x + 5.6 * k, y - 13 * k, (70, 46, 34, 255), r=1 * k)
    for dx in (-2.8, 1.2):
        cv.rect(x + dx * k, y - 11.4 * k, x + (dx + 1.6) * k, y - 9.8 * k, (20, 20, 24, 255))
    open_ = 0.8 + (int(t * 9) % 2) * 1.4 if talking else 0.8
    cv.rect(x - 2 * k, y - 7.4 * k, x + 2 * k, y - (7.4 - open_) * k, (130, 40, 44, 255))


def frame_hsnews(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_hsnews(c, x, y - s * 0.2, s * 0.9)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (0, 70, 170), (60, 160, 250), (0, 30, 100))
    if t >= T_INTRO:
        cv.gradient((18, 40, 100), (8, 14, 44), 0, 100)
        for i in range(10):
            cv.rect(i * 10 + 1, 4, i * 10 + 9, 40, (14, 30, 80, 255))
            cv.rect(i * 10 + 1, 4, i * 10 + 9, 5, (40, 90, 170, 255))
        # pantalla grande detrás: mapa con alfileres que van apareciendo
        cv.rect(44, 8, 96, 44, (230, 236, 246, 255), r=1.5, outline=(255, 255, 255, 255), width=0.8)
        for gx in range(5):
            cv.line([(48 + gx * 11, 10), (48 + gx * 11, 42)], (200, 210, 228, 255), 0.3)
        for gy in range(4):
            cv.line([(46, 14 + gy * 9), (94, 14 + gy * 9)], (200, 210, 228, 255), 0.3)
        for i, (px, py, col) in enumerate(((56, 20, NRED), (74, 30, (255, 150, 30)), (86, 16, HSBLUE))):
            if t > 3.4 + i * 1.0:
                bounce = abs(math.sin((t - 3.4 - i) * 5)) * 2 if t < 4.6 + i else 0
                cv.circ(px, py - bounce, 2.3, col + (255,), outline=(255, 255, 255, 255), width=0.5)
        # estudio: escritorio y presentador
        cv.rect(0, 70, 100, 100, (112, 82, 52, 255))
        cv.rect(0, 70, 100, 72, (160, 120, 80, 255))
        anchor(cv, 24, 62, 1.15, t)
        cv.rect(10, 74, 44, 86, HSBLUE + (255,), r=1.2)
        cv.text("HS NEWS", 27, 80, 4.2, (255, 255, 255, 255), face=FONT_BOLD)
        # lluvia/tormenta sobre el mapa hacia el final
        if t > 6.8:
            for i in range(14):
                rx = 48 + (i * 7 + t * 20) % 46
                ry = 10 + ((i * 11 + t * 40) % 32)
                cv.line([(rx, ry), (rx - 0.8, ry + 2)], (120, 190, 255, 220), 0.4)
        # tira de titulares que corre
        cv.rect(0, 90, 100, 100, DARK + (255,))
        cv.rect(0, 90, 14, 100, NRED + (255,))
        cv.text("HS", 7, 95, 5, (255, 255, 255, 255), face=FONT_BOLD)
        cv.text("Nueva obra en X:120 Z:-45 • Probable lluvia en 4 min • Incendio forestal en el bosque • Un nuevo jugador llegó al mundo", 100 - (t - 2.2) * 30, 95, 4.2, (255, 255, 255, 255), anchor="lm")
        pop_text(cv, "¡ÚLTIMA HORA!", 66, 56, 6.6, (255, 255, 255, 255), t, 3.0, hold_until=5.6, stroke=0.6)
        ribbon(cv, "Tu mundo, en tu pared", 62, t, 5.0, NRED + (255,), (255, 255, 255, 255), 4.4, hold_until=9.8)
        ribbon(cv, "Clima, obras e incendios al instante", 73, t, 6.4, (255, 205, 50, 255), (30, 20, 0, 255), 3.6, hold_until=9.8)
        if t > 8.2:
            confetti(cv, t, 8.2, 26, ((0, 110, 220), (200, 30, 40), (255, 255, 255)), 6)
        corner_logo(cv, t, lambda c, x, y, s: logo_hsnews(c, x, y * 0.9, s * 0.8, name=False), 5.0, x=10, y=10)
        flash(cv, t, T_INTRO, 0.25)
        flash(cv, t, 5.0, 0.2, (190, 220, 255))
    outro(cv, t, lambda c, x, y, s: logo_hsnews(c, x, y - s * 0.2, s * 0.9), (0, 70, 170), (0, 30, 100),
          "HS News — Lo que pasa en tu mundo, en tu pared.", "*Parodia ficticia. Noticias 100% inventadas (y a veces ciertas).", (255, 205, 50))
    fade_ends(cv, t)
    return cv.done()


def audio_hsnews():
    tr = Track()
    beat = 60 / 124
    prog = [52, 48, 55, 50]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        for st in range(8):
            tr.add(note(r - 12, beat * 0.45, "saw", cutoff=900, r=0.05), t0 + st * beat / 2, 0.28)
        for st in range(4):
            tr.add(kick(), t0 + st * beat, 0.55)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.35)
        tr.add(snare(), t0 + beat, 0.45)
        tr.add(snare(), t0 + 3 * beat, 0.45)
        if bar >= 1:                                              # fanfarria de noticiero
            for st, off in enumerate((0, 7, 12, 7)):
                tr.add(note(r + 12 + off, beat * 0.5, "square", cutoff=2400, r=0.05), t0 + st * beat, 0.16)
    tr.add(whoosh(1.0), 0.0, 0.45)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    for i in range(3):
        tr.add(sfx("elevator_ding"), 3.4 + i * 1.0, 0.35)       # los alfileres del mapa
    tr.add(whoosh(0.8), 5.0, 0.6)
    for m, ts in ((57, 10.05), (61, 10.05), (64, 10.05), (69, 10.35)):
        tr.add(note(m, 1.3, "saw", cutoff=1800, r=0.4), ts, 0.28)
    return tr.master()


BRANDS["hsnews"] = (frame_hsnews, audio_hsnews)
A.BRANDS["hsnews"] = BRANDS["hsnews"]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("hsnews", out, posters), flush=True)
