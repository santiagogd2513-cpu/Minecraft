"""V6.15.0: anuncio (video + audio) y póster de Lotto Gordo, la lotería del mundo.
Uso: python3 -m tools.v6150.loteria <carpeta_salida>   (escribe loteria.mp4 y posters/poster_loteria.png)"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *

GREEN, GOLD, RED = (8, 120, 60), (250, 200, 30), (225, 40, 50)


def logo_loteria(cv, cx, cy, s, a=255, color=(255, 255, 255), name=True):
    """Una bola roja con trébol dorado sobre cinta verde."""
    cv.circ(cx, cy - s * 0.2, s * 0.78, (240, 240, 245, a), outline=GOLD + (a,), width=max(0.2, s * 0.08))
    cv.circ(cx, cy - s * 0.2, s * 0.6, RED + (a,))
    cv.text("7", cx, cy - s * 0.2, s * 0.95, (255, 255, 255, a), face=FONT_BOLD)
    cv.rect(cx - s * 1.0, cy + s * 0.5, cx + s * 1.0, cy + s * 0.95, GREEN + (a,), r=s * 0.12, outline=GOLD + (a,), width=max(0.2, s * 0.04))
    cv.text("LOTTO GORDO", cx, cy + s * 0.72, s * 0.3, GOLD + (a,), face=FONT_BOLD)


def ball(cv, x, y, r, digit, t, spin=False):
    cv.circ(x, y, r, (240, 240, 245, 255), outline=(150, 150, 160, 255), width=0.4)
    cv.circ(x, y, r * 0.72, RED + (255,))
    cv.text(digit, x, y, r * 1.1, (255, 255, 255, 255), face=FONT_BOLD)


def truck(cv, x, y, k):
    cv.rect(x - 14 * k, y - 10 * k, x + 4 * k, y + 5 * k, (250, 250, 252, 255), r=1 * k)           # caja
    cv.rect(x - 13 * k, y - 4 * k, x + 3 * k, y - 2 * k, (255, 150, 20, 255))                       # franja AMAZOOM
    cv.rect(x + 4 * k, y - 5 * k, x + 12 * k, y + 5 * k, (235, 235, 240, 255), r=1.2 * k)           # cabina
    cv.rect(x + 7 * k, y - 4 * k, x + 11 * k, y - 0.5 * k, (130, 190, 240, 255))
    for wx in (-9, 8):
        cv.circ(x + wx * k, y + 5.5 * k, 2.6 * k, (22, 22, 26, 255))
        cv.circ(x + wx * k, y + 5.5 * k, 1.1 * k, (190, 195, 205, 255))


def frame_loteria(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_loteria(c, x, y - s * 0.1, s * 0.9)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (10, 140, 70), (250, 210, 60), (4, 60, 34))
    if t >= T_INTRO:
        cv.gradient((12, 90, 52), (4, 40, 24), 0, 100)
        cv.rays(50, 40, 14, (250, 210, 60, 50), (250, 210, 60, 0), t * 0.3, 150)
        # tres bolas que se revelan una por una
        digits = ("4", "7", "2")
        for i, d in enumerate(digits):
            if t > 2.8 + i * 0.9:
                bounce = abs(math.sin((t - 2.8 - i * 0.9) * 6)) * 3 if t < 4.0 + i * 0.9 else 0
                ball(cv, 24 + i * 20, 36 - bounce, 8.5, d, t)
            else:
                ball(cv, 24 + i * 20, 36, 8.5, str(int((t * 14 + i * 3) % 10)), t)
        if t > 5.6:
            cv.circ(86, 36, 8.5, (240, 240, 245, 255), outline=(150, 150, 160, 255), width=0.4)
            cv.circ(86, 36, 6.1, (30, 90, 220, 255))
            cv.text("C", 86, 36, 9, (255, 255, 255, 255), face=FONT_BOLD)
        pop_text(cv, "¡GORDO!", 50, 58, 9, GOLD + (255,), t, 5.9, stroke=0.7, hold_until=9.6)
        # camión que llega por abajo
        road(cv, 78, t, 20, col=(40, 44, 52))
        tx = -20 + max(0.0, t - 6.2) * 22
        if t > 6.2:
            truck(cv, min(tx, 62), 74, 1.2)
        ribbon(cv, "Un sorteo cada día de tu mundo", 66, t, 3.4, GOLD + (255,), (40, 26, 0, 255), 4.2, hold_until=9.8)
        ribbon(cv, "¡El premio te llega en camión!", 89, t, 7.0, (255, 255, 255, 255), GREEN + (255,), 4.4, hold_until=9.8)
        if t > 6.0:
            confetti(cv, t, 6.0, 30, ((250, 200, 30), (225, 40, 50), (255, 255, 255)), 4)
        corner_logo(cv, t, lambda c, x, y, s: logo_loteria(c, x, y * 0.9, s * 0.8, name=False), 5.0, x=10, y=10)
        flash(cv, t, T_INTRO, 0.25)
    outro(cv, t, lambda c, x, y, s: logo_loteria(c, x, y - s * 0.1, s * 0.9), (10, 140, 70), (4, 60, 34),
          "Lotto Gordo — Tu suerte llega en camión.", "*Parodia ficticia. Juega con responsabilidad.", (250, 210, 60))
    fade_ends(cv, t)
    return cv.done()


def audio_loteria():
    tr = Track()
    beat = 60 / 120
    prog = [57, 53, 48, 55]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        for st in range(8):
            tr.add(note(r - 12, beat * 0.45, "saw", cutoff=1000, r=0.05), t0 + st * beat / 2, 0.26)
        for st in range(4):
            tr.add(kick(), t0 + st * beat, 0.5)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.3)
        tr.add(clap(), t0 + beat, 0.4)
        tr.add(clap(), t0 + 3 * beat, 0.4)
        if bar >= 1:
            for st, off in enumerate((0, 4, 7, 12)):
                tr.add(marimba(r + 12 + off), t0 + st * beat, 0.3)
    tr.add(whoosh(1.0), 0.0, 0.45)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    for i in range(3):
        tr.add(sfx("elevator_ding"), 2.8 + i * 0.9, 0.35)       # las bolas
    tr.add(whoosh(0.8), 5.8, 0.6)
    for m, ts in ((60, 10.05), (64, 10.05), (67, 10.05), (72, 10.35)):
        tr.add(note(m, 1.3, "saw", cutoff=1800, r=0.4), ts, 0.28)
    return tr.master()


BRANDS["loteria"] = (frame_loteria, audio_loteria)
A.BRANDS["loteria"] = BRANDS["loteria"]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("loteria", out, posters), flush=True)
