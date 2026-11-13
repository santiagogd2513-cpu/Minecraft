"""V6.7.0: anuncio (video + audio) y póster de la marca parodia Polisía Nasional.
Uso: python3 -m tools.v670.polisia <carpeta_salida>   (escribe polisia.mp4 y posters/poster_polisia.png)"""
import os, sys, math
from ..v410 import ads as A
from ..v410.ads import *

NAVY, GOLD, RED, BLUE = (16, 30, 84), (255, 205, 50), (225, 30, 40), (40, 110, 255)
ZAP = os.path.join(os.path.dirname(__file__), "..", "..", "src/main/resources/assets/beautifuldecor/sounds/taser_zap.ogg")


def logo_polisia(cv, cx, cy, s, a=255, color=(255, 255, 255), name=True):
    """Escudo azul marino con borde dorado, estrella y las letras PN; debajo el nombre."""
    pts = [(cx - s * 0.62, cy - s * 0.7), (cx + s * 0.62, cy - s * 0.7), (cx + s * 0.62, cy + s * 0.1), (cx, cy + s * 0.78), (cx - s * 0.62, cy + s * 0.1)]
    cv.poly(pts, GOLD + (a,))
    k = 0.86
    pts2 = [(cx + (x - cx) * k, cy + (y - cy) * k) for x, y in pts]
    cv.poly(pts2, NAVY + (a,))
    star = []
    for i in range(10):
        r = s * (0.3 if i % 2 == 0 else 0.13)
        ang = -math.pi / 2 + i * math.pi / 5
        star.append((cx + math.cos(ang) * r, cy - s * 0.22 + math.sin(ang) * r))
    cv.poly(star, GOLD + (a,))
    cv.text("PN", cx, cy + s * 0.3, s * 0.34, (255, 255, 255, a), face=FONT_BOLD)
    if name:
        cv.text("POLISÍA NASIONAL", cx, cy + s * 1.05, s * 0.26, color + (a,), face=FONT_BOLD)


def patrol(cv, x, y, k, t, siren=True):
    """Patrulla de lado con barra roja y azul que parpadea."""
    cv.ellipse(x - 17 * k, y + 5.5 * k, x + 17 * k, y + 8 * k, (0, 0, 0, 70))
    cv.rect(x - 16 * k, y - 4 * k, x + 16 * k, y + 5 * k, (245, 245, 250, 255), r=2 * k)
    cv.rect(x - 16 * k, y - 0.5 * k, x + 16 * k, y + 2 * k, NAVY + (255,))
    cv.poly([(x - 8 * k, y - 4 * k), (x - 4 * k, y - 10 * k), (x + 6 * k, y - 10 * k), (x + 10 * k, y - 4 * k)], (245, 245, 250, 255))
    cv.poly([(x - 6 * k, y - 4.5 * k), (x - 3.2 * k, y - 9 * k), (x + 0.8 * k, y - 9 * k), (x + 0.8 * k, y - 4.5 * k)], (120, 170, 220, 255))
    cv.poly([(x + 2 * k, y - 4.5 * k), (x + 2 * k, y - 9 * k), (x + 5.4 * k, y - 9 * k), (x + 8 * k, y - 4.5 * k)], (120, 170, 220, 255))
    phase = int(t * 6) % 2
    cv.rect(x - 3.5 * k, y - 12 * k, x + 0.2 * k, y - 10 * k, RED + (255,) if phase == 0 or not siren else (90, 20, 24, 255), r=0.4 * k)
    cv.rect(x + 0.6 * k, y - 12 * k, x + 4.3 * k, y - 10 * k, BLUE + (255,) if phase == 1 or not siren else (20, 40, 100, 255), r=0.4 * k)
    if siren:
        col = RED if phase == 0 else BLUE
        cv.glow(x + (-1.6 if phase == 0 else 2.4) * k, y - 11 * k, 20 * k, col, 0.6)
    cv.rect(x + 14 * k, y - 2.5 * k, x + 16 * k, y - 0.8 * k, (255, 240, 170, 255))
    for wx in (-9, 9):
        cv.circ(x + wx * k, y + 5 * k, 3.6 * k, (22, 22, 26, 255))
        cv.circ(x + wx * k, y + 5 * k, 1.7 * k, (190, 195, 205, 255))
    cv.text("PN", x - 1 * k, y + 0.9 * k, 2.4 * k, (255, 255, 255, 255))


def cop(cv, x, y, k, arm_out):
    cv.rect(x - 3.5 * k, y - 11 * k, x + 3.5 * k, y + 0.5 * k, NAVY + (255,), r=0.8 * k)
    cv.rect(x - 1.8 * k, y, x - 0.2 * k, y + 7 * k, (20, 20, 40, 255))
    cv.rect(x + 0.2 * k, y, x + 1.8 * k, y + 7 * k, (20, 20, 40, 255))
    cv.rect(x - 3.2 * k, y - 18 * k, x + 3.2 * k, y - 11.5 * k, (240, 200, 160, 255), r=0.8 * k)
    cv.rect(x - 4 * k, y - 20 * k, x + 4 * k, y - 17.5 * k, NAVY + (255,), r=0.6 * k)
    cv.circ(x, y - 19 * k, 0.9 * k, GOLD + (255,))
    cv.rect(x - 2 * k, y - 15 * k, x - 0.8 * k, y - 13.8 * k, (20, 20, 20, 255))
    cv.rect(x + 0.8 * k, y - 15 * k, x + 2 * k, y - 13.8 * k, (20, 20, 20, 255))
    cv.rect(x + 3 * k, y - 9 * k, x + (7 + 4 * arm_out) * k, y - 7.5 * k, NAVY + (255,))
    cv.rect(x + (7 + 4 * arm_out) * k, y - 9.6 * k, x + (11 + 4 * arm_out) * k, y - 7 * k, (60, 60, 70, 255), r=0.5 * k)


def thief(cv, x, y, k, t, stunned):
    sh = math.sin(t * 60) * 0.7 * k if stunned else 0
    ph = t * 14
    for sgn in (1, -1):
        ang = 0 if stunned else math.sin(ph + (0 if sgn > 0 else math.pi)) * 0.8
        fx = x + sh + sgn * 1.5 * k + math.sin(ang) * 7 * k
        fy = y + 7 * k * math.cos(ang) * 0.9
        cv.line([(x + sh + sgn * 1.5 * k, y), (fx, fy)], (30, 30, 40, 255), 2.2 * k)
    cv.rect(x + sh - 4 * k, y - 11 * k, x + sh + 4 * k, y + 0.5 * k, (240, 240, 240, 255), r=0.8 * k)
    for i in range(4):
        cv.rect(x + sh - 4 * k, y - 10 * k + i * 2.8 * k, x + sh + 4 * k, y - 8.8 * k + i * 2.8 * k, (20, 20, 20, 255))
    cv.rect(x + sh - 3.4 * k, y - 18 * k, x + sh + 3.4 * k, y - 11.5 * k, (240, 200, 160, 255), r=0.8 * k)
    cv.rect(x + sh - 3.6 * k, y - 16 * k, x + sh + 3.6 * k, y - 13.6 * k, (20, 20, 20, 255))
    cv.rect(x + sh - 2 * k, y - 15.4 * k, x + sh - 0.8 * k, y - 14.4 * k, (255, 255, 255, 255))
    cv.rect(x + sh + 0.8 * k, y - 15.4 * k, x + sh + 2 * k, y - 14.4 * k, (255, 255, 255, 255))
    cv.rect(x + sh + 3 * k, y - 9 * k, x + sh + 9 * k, y - 2 * k, (150, 110, 50, 255), r=1 * k)     # bolsa
    cv.text("$", x + sh + 6 * k, y - 5.5 * k, 4.5 * k, (255, 230, 90, 255))


def bolt(cv, x0, y0, x1, y1, t, w=0.9):
    n = 9
    pts = [(x0, y0)]
    for i in range(1, n):
        p = i / n
        pts.append((lerp(x0, x1, p) + math.sin(t * 90 + i * 2.3) * 2.2, lerp(y0, y1, p) + math.cos(t * 77 + i * 1.7) * 2.2))
    pts.append((x1, y1))
    cv.line(pts, (120, 200, 255, 140), w * 3.2)
    cv.line(pts, (255, 255, 255, 255), w)


def cuffs(cv, x, y, k, a=255):
    for dx in (-3.2, 3.2):
        cv.circ(x + dx * k, y, 2.5 * k, (0, 0, 0, 0), outline=(200, 205, 215, a), width=0.9 * k)
    cv.line([(x - 0.8 * k, y), (x + 0.8 * k, y)], (200, 205, 215, a), 0.9 * k)


def frame_polisia(t):
    cv = Cv()
    logo = lambda c, x, y, s: logo_polisia(c, x, y - s * 0.25, s * 0.9)
    if t < T_INTRO + 0.3:
        intro(cv, t, logo, (14, 26, 80), (60, 110, 230), (6, 10, 36))
    if t >= T_INTRO:
        cv.gradient((10, 12, 40), (40, 30, 90), 0, 70)
        for i in range(24):
            cv.circ((i * 41) % 100, (i * 19) % 50, 0.3, (255, 255, 255, 160))
        for i in range(7):
            bx = i * 16 - (t * 5) % 16 - 4
            hh = 20 + (i * 13) % 20
            cv.rect(bx, 70 - hh, bx + 12, 70, (22, 22, 52, 255))
            for wy in range(3):
                cv.rect(bx + 2, 70 - hh + 3 + wy * 6, bx + 4, 70 - hh + 5 + wy * 6, (255, 220, 120, 255))
        stun = 5.6 <= t < 8.0
        road(cv, 70, t, 0 if stun else 120, col=(38, 38, 50))
        tx = 72 if t < 5.6 else 72 - (t - 5.6) * 1.2 if stun else 70
        if t < 5.6:
            tx = 80 - seg(t, 2.4, 5.6) * 8
        thief(cv, tx, 83, 1.25, t, stun)
        carx = lerp(-25, 26, ease_out(seg(t, 2.4, 5.0)))
        patrol(cv, carx, 74, 1.1, t)
        if t >= 5.0:
            copx = carx + 20
            cop(cv, copx, 86, 1.1, seg(t, 5.0, 5.4))
        if 5.5 <= t < 7.6:
            bolt(cv, carx + 36, 78, tx - 2, 76, t)
            cv.glow(tx, 76, 18, (140, 210, 255), 0.55)
            for i in range(6):
                a = t * 40 + i * 1.05
                cv.line([(tx, 76), (tx + math.cos(a) * 9, 76 + math.sin(a) * 9)], (255, 255, 200, 230), 0.5)
        if t >= 7.6:
            cuffs(cv, tx + 6 * 1.25, 78, 1.4, a=int(255 * seg(t, 7.6, 8.0)))
        # destello rojo/azul en toda la pantalla
        ph = int(t * 6) % 2
        cv.rect(0, 0, 100, 100, (RED if ph == 0 else BLUE) + (28,))
        pop_text(cv, "¿Alguien se lo llevó?", 50, 12, 8.0, (255, 255, 255, 255), t, 2.6, hold_until=5.3, stroke=0.0)
        pop_text(cv, "¡Polisía Nasional!", 50, 24, 8.5, GOLD + (255,), t, 3.0, hold_until=5.3, stroke=0.6)
        ribbon(cv, "Pistola eléctrica: ¡quieto ahí!", 30, t, 5.4, BLUE + (255,), (255, 255, 255, 255), 4.2, hold_until=9.7)
        ribbon(cv, "Esposas de serie: sin manos, sin travesuras", 41, t, 7.4, GOLD + (255,), (30, 20, 0, 255), 3.6, hold_until=9.7)
        if t > 8.0:
            confetti(cv, t, 8.0, 26, ((225, 30, 40), (40, 110, 255), (255, 255, 255)), 4)
        corner_logo(cv, t, lambda c, x, y, s: logo_polisia(c, x, y * 0.9, s * 0.8, name=False), 5.0, x=14, y=12)
        flash(cv, t, T_INTRO, 0.25)
        flash(cv, t, 5.6, 0.2, (180, 220, 255))
    outro(cv, t, lambda c, x, y, s: logo_polisia(c, x, y - s * 0.2, s * 0.9), (14, 26, 80), (6, 8, 30),
          "Polisía Nasional — Tú quieto, nosotros mandamos.", "*Parodia ficticia. Ninguna esposa fue lastimada.", GOLD)
    fade_ends(cv, t)
    return cv.done()


def audio_polisia():
    tr = Track()
    beat = 60 / 120
    prog = [45, 41, 48, 43]
    for bar in range(6):
        t0 = 0.1 + bar * beat * 4
        r = prog[bar % 4]
        for st in range(8):
            tr.add(note(r - 12, beat * 0.45, "saw", cutoff=700, r=0.05), t0 + st * beat / 2, 0.30)
        for st in range(4):
            tr.add(kick(), t0 + st * beat, 0.6)
            tr.add(hat(), t0 + st * beat + beat / 2, 0.35)
        tr.add(snare(), t0 + beat, 0.5)
        tr.add(snare(), t0 + 3 * beat, 0.5)
    for s in range(3):                                           # sirena que sube y baja
        tr.add(swell(0.6, 600, 1000, "saw", 3000), 2.4 + s * 1.2, 0.16)
        tr.add(swell(0.6, 1000, 600, "saw", 3000), 3.0 + s * 1.2, 0.16)
    tr.add(whoosh(1.0), 0.0, 0.45)
    tr.add(sparkle(96, 0.8), 0.3, 0.5)
    try:
        tr.add(load_ogg(ZAP), 5.6, 0.9)                         # el zap
    except Exception:
        tr.add(whoosh(0.4), 5.6, 0.6)
    tr.add(sfx("elevator_ding"), 7.7, 0.45)                      # click de esposas
    for m, ts in ((57, 10.05), (60, 10.05), (64, 10.05), (69, 10.35)):
        tr.add(note(m, 1.3, "saw", cutoff=1800, r=0.4), ts, 0.28)
    return tr.master()


BRANDS["polisia"] = (frame_polisia, audio_polisia)
A.BRANDS["polisia"] = BRANDS["polisia"]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    posters = os.path.join(out, "posters")
    os.makedirs(posters, exist_ok=True)
    print(A.render_brand("polisia", out, posters), flush=True)
