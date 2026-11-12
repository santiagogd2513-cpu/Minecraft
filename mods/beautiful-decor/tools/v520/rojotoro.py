"""V5.2.0: anuncio (video + audio) y póster de la marca parodia Rojo Toro. Reutiliza el kit de V4.1.0.
Uso: python3 -m tools.v520.rojotoro <carpeta_salida>   (escribe rojotoro.mp4 y posters/poster_rojotoro.png)"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *

RED, GOLD, NAVY = (205, 24, 34), (255, 205, 50), (20, 24, 62)


def logo_rojotoro(cv, cx, cy, s, a=255, color=(255, 255, 255)):
    """Cabeza de toro con cuernos y un rayo, y el nombre debajo."""
    cv.circ(cx, cy - s * 0.1, s * 0.62, RED + (a,), outline=GOLD + (a,), width=max(0.2, s * 0.06))
    cv.poly([(cx - s * 0.5, cy - s * 0.5), (cx - s * 0.95, cy - s * 0.95), (cx - s * 0.62, cy - s * 0.2)], GOLD + (a,))
    cv.poly([(cx + s * 0.5, cy - s * 0.5), (cx + s * 0.95, cy - s * 0.95), (cx + s * 0.62, cy - s * 0.2)], GOLD + (a,))
    cv.poly([(cx - s * 0.02, cy - s * 0.5), (cx + s * 0.2, cy - s * 0.5), (cx + s * 0.04, cy - s * 0.12), (cx + s * 0.2, cy - s * 0.12),
             (cx - s * 0.12, cy + s * 0.34), (cx - s * 0.04, cy - s * 0.04), (cx - s * 0.2, cy - s * 0.04)], (255, 255, 255, a))
    cv.text("ROJO TORO", cx, cy + s * 0.9, s * 0.36, color + (a,), face=FONT_BOLD)


def can(cv, cx, cy, k, rot=0.0, a=255):
    """Lata plateada con franja roja y el toro."""
    w = 9 * k
    h = 24 * k
    sway = math.sin(rot) * 2 * k
    cv.ellipse(cx - w - sway, cy + h - 1.5 * k, cx + w + sway, cy + h + 2 * k, (0, 0, 0, 60))
    cv.rect(cx - w, cy - h, cx + w, cy + h, (190, 196, 208, a), r=2 * k)
    cv.rect(cx - w, cy - h * 0.25, cx + w, cy + h * 0.55, RED + (a,))
    cv.rect(cx - w, cy - h * 0.25, cx + w, cy - h * 0.18, GOLD + (a,))
    cv.rect(cx - w, cy + h * 0.48, cx + w, cy + h * 0.55, GOLD + (a,))
    cv.rect(cx - w * 0.9, cy - h, cx - w * 0.55, cy + h, (255, 255, 255, 70), r=1 * k)
    cv.rect(cx - w, cy - h, cx + w, cy - h * 0.9, (150, 156, 170, a), r=1.5 * k)
    cv.circ(cx, cy + h * 0.15, 4.2 * k, (255, 255, 255, a))
    cv.poly([(cx - 0.4 * k, cy - 2.4 * k), (cx + 1.6 * k, cy - 2.4 * k), (cx + 0.4 * k, cy + 0.8 * k), (cx + 1.6 * k, cy + 0.8 * k),
             (cx - 1.4 * k, cy + 4.6 * k), (cx - 0.6 * k, cy + 1.2 * k), (cx - 1.8 * k, cy + 1.2 * k)], RED + (a,))


def runner(cv, x, y, k, t, glow):
    """Muñeco cuadrado que corre; con 'glow' las piernas van a toda velocidad."""
    ph = t * (22 if glow else 7)
    leg = 8 * k
    for sgn in (1, -1):
        ang = math.sin(ph + (0 if sgn > 0 else math.pi)) * (0.9 if glow else 0.35)
        fx = x + sgn * 1.5 * k + math.sin(ang) * leg
        fy = y + leg * math.cos(ang) * 0.9
        cv.line([(x + sgn * 1.5 * k, y), (fx, fy)], (40, 50, 110, 255), 2.2 * k)
        cv.rect(fx - 1.6 * k, fy - 0.4 * k, fx + 2.4 * k, fy + 1.2 * k, (240, 240, 240, 255), r=0.4 * k)
    cv.rect(x - 4 * k, y - 11 * k, x + 4 * k, y + 0.5 * k, (230, 60, 50, 255), r=0.8 * k)
    arm = math.sin(ph) * (1.0 if glow else 0.3)
    for sgn in (1, -1):
        cv.line([(x + sgn * 4 * k, y - 9 * k), (x + sgn * 4 * k + math.cos(arm * sgn) * 5 * k, y - 4 * k + math.sin(arm * sgn) * 5 * k)], (240, 200, 160, 255), 1.8 * k)
    cv.rect(x - 3.5 * k, y - 18 * k, x + 3.5 * k, y - 11.5 * k, (240, 200, 160, 255), r=0.8 * k)
    cv.rect(x - 3.5 * k, y - 18 * k, x + 3.5 * k, y - 16 * k, (60, 40, 30, 255), r=0.6 * k)
    cv.rect(x + 0.6 * k, y - 15 * k, x + 1.8 * k, y - 13.8 * k, (20, 20, 20, 255))
    cv.rect(x - 2.0 * k, y - 15 * k, x - 0.8 * k, y - 13.8 * k, (20, 20, 20, 255))
    if glow:
        cv.glow(x, y - 8 * k, 16 * k, (255, 220, 80), 0.5)


def frame_rojotoro(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_rojotoro(c, x, y, s * 0.8)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (150, 10, 24), (255, 80, 60), (60, 4, 14))
    if t >= T_INTRO:
        cv.gradient((24, 20, 70), (90, 30, 90), 0, 70)
        for i in range(22):
            cv.circ((i * 37) % 100, (i * 23) % 55, 0.35, (255, 255, 255, 170))
        for i in range(7):                                      # edificios
            bx = i * 16 - (t * 6) % 16 - 4
            hh = 22 + (i * 11) % 18
            cv.rect(bx, 70 - hh, bx + 12, 70, (30, 26, 64, 255))
            for wy in range(3):
                cv.rect(bx + 2, 70 - hh + 3 + wy * 6, bx + 4, 70 - hh + 5 + wy * 6, (255, 220, 120, 255))
        road(cv, 70, t, 160 if t > 5.0 else 30, col=(44, 44, 56))
        drink = t > 4.4
        px = 30
        runner(cv, px, 86, 1.25, t, drink)
        # la lata baja del cielo y se bebe
        if 3.0 < t < 5.0:
            p = seg(t, 3.0, 4.4)
            cy = lerp(-10, 46, ease_out(p)) + math.sin(t * 9) * 0.8
            can(cv, 66 - 28 * seg(t, 4.2, 5.0), cy, 0.9, t * 3)
        elif t >= 5.0 and t < 9.0:
            can(cv, 74, 40 + math.sin(t * 3) * 1.5, 1.1, t * 3)
        if t > 4.4:
            speed_lines(cv, t, 50, 95, (255, 220, 120, 150), 11)
            cv.glow(px, 80, 22, (255, 200, 60), 0.35)
        pop_text(cv, "¿Cansado?", 50, 12, 9.0, (255, 255, 255, 255), t, 2.6, hold_until=4.3, stroke=0.0)
        pop_text(cv, "¡Rojo Toro!", 50, 24, 9.0, GOLD + (255,), t, 3.1, hold_until=4.3, stroke=0.6)
        ribbon(cv, "Te da alas… o al menos patas*", 34, t, 5.2, RED + (255,), (255, 255, 255, 255), 4.4, hold_until=9.7)
        ribbon(cv, "Salto y velocidad al instante", 46, t, 6.4, GOLD + (255,), (40, 20, 0, 255), 4.0, hold_until=9.7)
        if t > 7.4:
            confetti(cv, t, 7.4, 30, ((255, 205, 50), (205, 24, 34), (255, 255, 255)), 3)
        corner_logo(cv, t, lambda c, x, y, s: logo_rojotoro(c, x, y * 0.9, s * 0.9), 5.0, x=14, y=12)
        flash(cv, t, T_INTRO, 0.25)
        flash(cv, t, 4.4, 0.3, (255, 230, 150))
    outro(cv, t, lambda c, x, y, s: logo_rojotoro(c, x, y, s * 0.95), (170, 14, 28), (70, 6, 16), "Rojo Toro — Dale patas a tu día.",
          "*No da alas reales. Parodia ficticia.", GOLD)
    fade_ends(cv, t)
    return cv.done()


def audio_rojotoro():
    tr = Track()
    beat = 60 / 132
    prog = [57, 53, 60, 55]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        for st in range(8):
            tr.add(note(r - 12 + (12 if st % 4 == 3 else 0), beat * 0.45, "saw", cutoff=900, r=0.05), t0 + st * beat / 2, 0.30)
        for st in range(4):
            tr.add(kick(), t0 + st * beat, 0.6)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.4)
        tr.add(snare(), t0 + beat, 0.5)
        tr.add(snare(), t0 + 3 * beat, 0.5)
        if bar >= 2:
            for st in range(4):
                tr.add(note(r + 12 + (st % 2) * 7, beat * 0.5, "square", cutoff=2200, r=0.05), t0 + st * beat, 0.16)
    tr.add(whoosh(1.0), 0.0, 0.45)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    tr.add(whoosh(1.2, up=False), 3.0, 0.5)               # la lata cae
    tr.add(sfx("elevator_ding"), 4.3, 0.5)                # se abre
    tr.add(whoosh(0.8), 4.4, 0.7)
    for i in range(8):
        tr.add(sparkle(84 + (i % 4) * 3, 0.3), 4.6 + i * 0.1, 0.4)
    tr.add(whoosh(0.8), 8.8, 0.6)
    for m, ts in ((57, 10.05), (61, 10.05), (64, 10.05), (69, 10.35)):
        tr.add(note(m, 1.3, "saw", cutoff=1800, r=0.4), ts, 0.28)
    return tr.master()


BRANDS["rojotoro"] = (frame_rojotoro, audio_rojotoro)
A.BRANDS["rojotoro"] = BRANDS["rojotoro"]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("rojotoro", out, posters), flush=True)
