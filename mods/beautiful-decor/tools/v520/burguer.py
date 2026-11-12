"""V5.3.0: anuncio (video + audio) y póster de la marca parodia Burguer Reino. Reutiliza el kit de V4.1.0.
Uso: python3 -m tools.v520.burguer <carpeta_salida>"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *
from .rojotoro import runner

ORANGE, RED, BROWN, GOLD = (240, 130, 20), (200, 40, 30), (150, 90, 40), (255, 205, 50)


def crown(cv, cx, cy, s, a=255):
    pts = [(cx - s, cy), (cx - s, cy - s * 0.9), (cx - s * 0.5, cy - s * 0.4), (cx, cy - s * 1.1), (cx + s * 0.5, cy - s * 0.4), (cx + s, cy - s * 0.9), (cx + s, cy)]
    cv.poly(pts, GOLD + (a,), outline=(150, 100, 10, a), width=max(0.2, s * 0.08))
    cv.rect(cx - s, cy, cx + s, cy + s * 0.25, (230, 170, 30, a))
    for dx, col in ((-0.5, RED), (0, (40, 120, 220)), (0.5, RED)):
        cv.circ(cx + dx * s, cy + s * 0.12, s * 0.07, col + (a,))
    for dx in (-1, 0, 1):
        cv.circ(cx + dx * s * 0.95 if dx else cx, cy - s * (0.95 if dx else 1.15), s * 0.1, (255, 240, 170, a))


def logo_burguer(cv, cx, cy, s, a=255, color=(255, 255, 255)):
    cv.rect(cx - s * 1.0, cy - s * 0.35, cx + s * 1.0, cy + s * 0.65, ORANGE + (a,), r=s * 0.3, outline=RED + (a,), width=max(0.2, s * 0.06))
    crown(cv, cx, cy - s * 0.45, s * 0.42, a)
    cv.text("BURGUER REINO", cx, cy + s * 0.22, s * 0.25, (90, 20, 10, a) if color == (255, 255, 255) else color + (a,), face=FONT_BOLD)


def burger(cv, cx, cy, k, a=255, bite=0.0):
    w = 14 * k
    cv.ellipse(cx - w, cy + 8 * k, cx + w, cy + 12 * k, (0, 0, 0, 50))
    cv.rect(cx - w, cy + 4 * k, cx + w, cy + 9 * k, (190, 120, 50, a), r=2.5 * k)                       # pan de abajo
    cv.rect(cx - w * 1.02, cy + 1 * k, cx + w * 1.02, cy + 4.2 * k, (110, 60, 30, a), r=1.8 * k)         # carne
    cv.poly([(cx - w, cy + 0.5 * k), (cx + w, cy + 0.5 * k), (cx + w * 0.6, cy + 3.4 * k), (cx + w * 0.2, cy + 1.2 * k), (cx - w * 0.2, cy + 3.4 * k)], (255, 205, 50, a))   # queso
    pts = [(cx - w * 1.05 + i * w * 2.1 / 12, cy - 1.2 * k + (1.0 * k if i % 2 else -0.4 * k)) for i in range(13)]
    cv.line(pts, (80, 180, 70, a), 2.2 * k)                                                             # lechuga
    cv.rect(cx - w * 0.9, cy - 2.2 * k, cx + w * 0.9, cy - 0.2 * k, (210, 50, 40, a), r=1 * k)           # tomate
    cv.ellipse(cx - w, cy - 11 * k, cx + w, cy - 1.5 * k, (210, 140, 60, a))                            # pan de arriba
    cv.ellipse(cx - w * 0.8, cy - 10.4 * k, cx + w * 0.4, cy - 6 * k, (235, 175, 90, a))
    for i in range(9):
        cv.ellipse(cx - w * 0.7 + (i * 5.3 % 20) * k * 0.9, cy - 9.5 * k + (i * 7 % 5) * k, cx - w * 0.7 + (i * 5.3 % 20) * k * 0.9 + 1.4 * k,
                   cy - 9.5 * k + (i * 7 % 5) * k + 0.8 * k, (255, 240, 200, a))
    if bite > 0:
        cv.circ(cx + w * 0.9, cy - 4 * k, bite * 7 * k, (255, 233, 190, 0))


def frame_burguer(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_burguer(c, x, y, s * 0.8)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (200, 70, 10), (255, 190, 60), (110, 30, 5))
    if t >= T_INTRO:
        cv.gradient((255, 214, 120), (255, 150, 70), 0, 70)
        cv.circ(80, 18, 9, (255, 245, 190, 255))
        clouds(cv, t, 3, 10, (255, 255, 255, 200), 4)
        cv.rect(0, 70, 100, 100, (120, 190, 90, 255))
        for i in range(0, 100, 10):
            cv.line([(i, 70), (i - 3, 76)], (90, 160, 70, 255), 0.6)
        # hamburguesa que baja del cielo y rebota
        p = seg(t, 3.0, 4.2)
        by = lerp(-20, 58, ease_out(p)) - abs(math.sin(max(0.0, t - 4.2) * 5)) * 6 * (1 - seg(t, 4.2, 6.0)) if t < 9.0 else 58
        crown_y = by - 18
        if t > 3.0:
            cv.glow(72, by - 4, 22, (255, 220, 120), 0.3)
            burger(cv, 72, by, 1.7)
            crown(cv, 72, crown_y + 2, 7.0)
        # el muñeco intenta morderla y no cabe
        px = lerp(20, 44, ease_in_out(seg(t, 5.0, 6.4))) + math.sin(t * 10) * 0.6 * seg(t, 6.4, 8.0)
        runner(cv, px, 90, 1.25, t, False)
        if t > 6.4 and t < 8.6:
            cv.circ(px, 72, 3.4, (60, 20, 20, 255))                           # boca abierta enorme
            for i in range(3):
                cv.text("?", px + 8 + i * 5, 58 - i * 4 - (t * 6) % 3, 4.2, (90, 20, 10, 255))
        pop_text(cv, "¿Hambre?", 50, 12, 9.0, (255, 255, 255, 255), t, 2.6, hold_until=5.2, stroke=0.8, stroke_fill=(120, 40, 10, 255))
        pop_text(cv, "¡Reina!", 50, 24, 10.0, (200, 40, 30, 255), t, 3.2, hold_until=5.2, stroke=0.8, stroke_fill=(255, 255, 255, 255))
        ribbon(cv, "Hamburguesa Reina: doble de todo*", 34, t, 6.0, RED + (255,), (255, 255, 255, 255), 4.2, hold_until=9.7)
        ribbon(cv, "Con papas… y con corona", 46, t, 7.0, GOLD + (255,), (90, 20, 10, 255), 4.4, hold_until=9.7)
        if t > 8.0:
            confetti(cv, t, 8.0, 28, ((255, 205, 50), (200, 40, 30), (255, 255, 255)), 5)
        corner_logo(cv, t, lambda c, x, y, s: logo_burguer(c, x, y * 0.9, s * 0.9), 5.0, x=15, y=12)
        flash(cv, t, T_INTRO, 0.25)
    outro(cv, t, lambda c, x, y, s: logo_burguer(c, x, y, s * 0.95), (200, 60, 10), (110, 25, 5), "Burguer Reino — Aquí el hambre es plebeya.",
          "*La corona no es comestible. Parodia ficticia.", (255, 235, 170))
    fade_ends(cv, t)
    return cv.done()


def audio_burguer():
    tr = Track()
    beat = 60 / 112
    prog = [60, 65, 62, 67]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        arp = [r, r + 4, r + 7, r + 12, r + 7, r + 4, r + 12, r + 16]
        for st in range(8):
            tr.add(marimba(arp[st] + 12, 0.3), t0 + st * beat / 2, 0.4)
        for st in range(4):
            tr.add(pluck(r - 24 + (7 if st % 2 else 0), 0.45, 0.99, 0.8), t0 + st * beat, 0.5)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.3)
        tr.add(kick(), t0, 0.55)
        tr.add(kick(), t0 + 2 * beat, 0.55)
        tr.add(clap(), t0 + beat, 0.4)
        tr.add(clap(), t0 + 3 * beat, 0.4)
    tr.add(whoosh(1.0), 0.0, 0.4)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    tr.add(whoosh(1.2, up=False), 3.0, 0.5)
    tr.add(kick(0.5), 4.2, 0.9)                           # la hamburguesa cae
    for i in range(4):
        tr.add(marimba(55 + i, 0.3), 6.4 + i * 0.25, 0.4)
    tr.add(sfx("doorbell"), 7.2, 0.5)
    for i in range(6):
        tr.add(sparkle(84 + (i % 3) * 4, 0.3), 8.0 + i * 0.12, 0.4)
    tr.add(whoosh(0.8), 8.8, 0.6)
    for m, ts in ((60, 10.05), (64, 10.05), (67, 10.05), (72, 10.35)):
        tr.add(marimba(m + 12, 0.7), ts, 0.5)
    return tr.master()


A.BRANDS["burguerreino"] = (frame_burguer, audio_burguer)

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("burguerreino", out, posters), flush=True)
