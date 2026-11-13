"""V7.2.0: anuncio (video + audio) y póster de Spotifly, la música que despega.
Uso: python3 -m tools.v720.spotifly <carpeta_salida>   (escribe spotifly.mp4 y posters/poster_spotifly.png)"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *

TEAL, VIOLET, INK = (20, 205, 170), (120, 70, 235), (14, 12, 34)


def plane(cv, x, y, s, a=255, rot=0.0, col=(255, 255, 255)):
    """Avioncito de papel apuntando a la derecha."""
    c, sn = math.cos(rot), math.sin(rot)
    def p(px, py):
        return (x + (px * c - py * sn) * s, y + (px * sn + py * c) * s)
    cv.poly([p(1.0, 0.0), p(-0.9, -0.62), p(-0.45, 0.0)], col + (a,))
    cv.poly([p(1.0, 0.0), p(-0.45, 0.0), p(-0.9, 0.62)], tuple(int(v * 0.78) for v in col) + (a,))
    cv.poly([p(-0.45, 0.0), p(-0.9, 0.62), p(-0.15, 0.3)], tuple(int(v * 0.6) for v in col) + (a,))


def logo_spotifly(cv, cx, cy, s, a=255, color=(255, 255, 255), name=True):
    cv.circ(cx, cy - s * 0.15, s * 0.85, TEAL + (a,), outline=(255, 255, 255, a), width=max(0.2, s * 0.07))
    for k, r in enumerate((0.3, 0.5, 0.68)):                            # ondas
        cv.line([(cx - s * r, cy - s * 0.15 + s * (0.1 + k * 0.05)), (cx, cy - s * 0.15 - s * (0.12 + k * 0.1)), (cx + s * r, cy - s * 0.15 + s * (0.1 + k * 0.05))],
                (14, 12, 34, int(a * 0.55)), width=max(0.2, s * 0.07))
    plane(cv, cx + s * 0.05, cy - s * 0.2, s * 0.45, a, -0.35)
    if name:
        cv.text("SPOTIFLY", cx, cy + s * 0.92, s * 0.36, (255, 255, 255, a), face=FONT_BOLD)


def note_sym(cv, x, y, s, col, a=255):
    cv.circ(x, y, s * 0.5, col + (a,))
    cv.rect(x + s * 0.3, y - s * 1.6, x + s * 0.55, y, col + (a,))
    cv.poly([(x + s * 0.55, y - s * 1.6), (x + s * 1.2, y - s * 1.2), (x + s * 0.55, y - s * 0.9)], col + (a,))


def person(cv, x, y, k, bounce=0.0, happy=False, col=(250, 190, 150)):
    cv.rect(x - 3 * k, y - 4 * k - bounce, x + 3 * k, y + 6 * k - bounce, (60, 70, 160, 255), r=1 * k)
    cv.circ(x, y - 7 * k - bounce, 3 * k, col + (255,))
    cv.rect(x - 3.4 * k, y - 8.6 * k - bounce, x + 3.4 * k, y - 7.6 * k - bounce, (30, 30, 40, 255), r=0.4 * k)        # audífonos
    if happy:
        cv.line([(x - 1.4 * k, y - 6 * k - bounce), (x, y - 5.3 * k - bounce), (x + 1.4 * k, y - 6 * k - bounce)], (120, 40, 40, 255), width=0.6 * k)


def city(cv, t, lit):
    cv.gradient((24, 20, 70), (210, 90, 150), 0, 100)
    for i in range(14):
        bx = i * 8 - 4
        h = 26 + ((i * 37) % 30)
        cv.rect(bx, 78 - h, bx + 7, 78, (22, 20, 46, 255))
        for wy in range(int(78 - h + 3), 76, 6):
            for wx in range(int(bx + 1.5), int(bx + 6), 3):
                on = (bx + 3.5) < lit
                cv.rect(wx, wy, wx + 1.4, wy + 2.4, (255, 225, 110, 255) if on else (50, 46, 90, 255))
    cv.rect(0, 78, 100, 100, (30, 28, 56, 255))


def frame_spotifly(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_spotifly(c, x, y - s * 0.1, s * 0.9)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, TEAL, VIOLET, INK)
    if t >= T_INTRO:
        if t < 4.6:
            # escena 1: el oyente aburrido
            cv.gradient((120, 124, 134), (70, 74, 84), 0, 100)
            cv.rect(0, 72, 100, 100, (84, 70, 62, 255))
            person(cv, 50, 60, 3.2)
            sx = 50 - 10
            cv.rect(sx + 22, 36, sx + 32, 50, (240, 240, 245, 255), r=1.2)
            cv.rect(sx + 24, 38, sx + 30, 41, (200, 90, 90, 255))
            ribbon(cv, "¿Tu día va a ras de tierra?", 90, t, 2.2, (255, 255, 255, 255), INK + (255,), 4.6, hold_until=4.5)
        elif t < 8.2:
            # escena 2: el avioncito de papel despega de los audífonos
            p = seg(t, 4.6, 8.2)
            cv.gradient((40, 26, 100), (20, 160, 170), 0, 100)
            cv.rays(50, 70, 14, (255, 255, 255, 40), (255, 255, 255, 0), t * 0.3, 150)
            clouds(cv, t, 6, 18)
            px = 14 + p * 78
            py = 66 - math.sin(p * math.pi) * 40
            for i in range(10):
                q = max(0.0, p - i * 0.03)
                note_sym(cv, 14 + q * 78 - 3, 66 - math.sin(q * math.pi) * 40 + 3 + (i % 3) * 3, 1.7, ((255, 255, 255), TEAL, (255, 210, 80))[i % 3], int(210 - i * 18))
            plane(cv, px, py, 8, 255, -0.5 + p * 0.9)
            pop_text(cv, "¡DESPEGA!", 50, 16, 9, (255, 255, 255, 255), t, 5.0, stroke=0.8, hold_until=8.0)
        else:
            # escena 3: la ciudad se enciende y baila
            lit = (t - 8.2) * 55
            city(cv, t, lit)
            for i in range(5):
                person(cv, 14 + i * 18, 83, 2.0, abs(math.sin(t * 7 + i)) * 2.6, True)
            plane(cv, 20 + ((t - 8.2) * 22) % 90, 30 - ((t - 8.2) * 3), 5.5, 255, -0.1)
            if t > 8.4:
                confetti(cv, t, 8.4, 30, (TEAL, VIOLET, (255, 255, 255)), 5)
            ribbon(cv, "Cada nota enciende una ventana", 68, t, 8.6, (255, 230, 120, 255), INK + (255,), 4.3, hold_until=11.2)
        corner_logo(cv, t, lambda c, x, y, s: logo_spotifly(c, x, y * 0.9, s * 0.8, name=False), 5.0, x=10, y=10)
        flash(cv, t, T_INTRO, 0.25)
        flash(cv, t, 4.6, 0.2)
        flash(cv, t, 8.2, 0.2)
    outro(cv, t, lambda c, x, y, s: logo_spotifly(c, x, y - s * 0.1, s * 0.9), TEAL, INK,
          "Spotifly — Despega con tu música.", "*Parodia ficticia. Cuida tus oídos.", (255, 255, 255))
    fade_ends(cv, t)
    return cv.done()


def chip_song(tr, t0, melody, bass, bpm, lead_gain=0.3):
    """Canción de 8 bits: melodía cuadrada, bajo triangular y percusión simple."""
    beat = 60 / bpm
    for i, m in enumerate(melody):
        if m:
            tr.add(note(m, beat * 0.45, "square", a=0.003, d=0.05, s=0.5, r=0.04), t0 + i * beat / 2, lead_gain)
    for i, m in enumerate(bass):
        if m:
            tr.add(note(m, beat * 0.9, "tri", a=0.005, d=0.1, s=0.7, r=0.05), t0 + i * beat, 0.4)
    for i in range(len(bass)):
        tr.add(kick(), t0 + i * beat, 0.35)
        tr.add(hat(), t0 + i * beat + beat / 2, 0.2)
        if i % 2 == 1:
            tr.add(snare(), t0 + i * beat, 0.22)


def audio_spotifly():
    tr = Track()
    chip_song(tr, 0.1, [72, 0, 76, 79, 84, 0, 79, 76, 74, 0, 77, 81, 86, 0, 81, 77] * 3,
              [48, 48, 53, 53, 43, 43, 55, 55] * 3, 120, 0.22)
    tr.add(whoosh(1.0), 0.0, 0.45)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    tr.add(whoosh(0.9), 4.5, 0.55)
    tr.add(whoosh(0.8), 8.1, 0.5)
    for m, ts in ((67, 10.05), (71, 10.05), (74, 10.05), (79, 10.35)):
        tr.add(note(m, 1.3, "saw", cutoff=1800, r=0.4), ts, 0.28)
    return tr.master()


BRANDS["spotifly"] = (frame_spotifly, audio_spotifly)
A.BRANDS["spotifly"] = BRANDS["spotifly"]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("spotifly", out, posters), flush=True)
