#!/usr/bin/env python3
"""V3.0.0: tres cuadros de pared (valle al amanecer, persecución policial, foto aérea de un circuito).

Genera, para cada cuadro: la textura 128x96 (marco + lienzo), el ícono 32x32 del ítem, el modelo de bloque con marco
en relieve, el blockstate y la loot table. Los cuadros son bloques de pared (como el papel higiénico): 16 x 12 px,
2 px de grosor, pegados a la pared de atrás. El código del bloque está en block/PictureBlock.java.

Uso (desde la raíz del proyecto):
  python3 -m tools.v300.paintings --preview vista.png     # solo mira
  python3 -m tools.v300.paintings --write                 # escribe texturas, modelos, blockstates y loot tables
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.spatial import cKDTree

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"

TEX_W, TEX_H = 128, 96
BORDER = 8                       # 1 px del modelo = 8 texeles
CANVAS_W, CANVAS_H = TEX_W - 2 * BORDER, TEX_H - 2 * BORDER      # 112 x 80

NAMES = ["painting_valley_dawn", "painting_police_chase", "painting_circuit_aerial"]


# ----------------------------------------------------------------------------------------------- utilidades
def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def gradient(stops, t):
    """stops = [(pos, (r,g,b)), ...] con pos ascendente; t = arreglo con valores 0..1. Devuelve (...,3)."""
    t = np.asarray(t, dtype=np.float32)
    out = np.zeros(t.shape + (3,), dtype=np.float32)
    pos = [p for p, _ in stops]
    cols = [np.array(c, dtype=np.float32) for _, c in stops]
    for ch in range(3):
        out[..., ch] = np.interp(t, pos, [c[ch] for c in cols])
    return out


def value_noise(w, h, cell_x, cell_y, seed):
    """Ruido de valor suave (bicúbico) con celdas de tamaño cell_x x cell_y en px. Devuelve (h, w) en 0..1."""
    rng = np.random.default_rng(seed)
    gx, gy = int(w / cell_x) + 3, int(h / cell_y) + 3
    grid = rng.random((gy, gx)).astype(np.float32)
    ys = np.arange(h, dtype=np.float32) / cell_y
    xs = np.arange(w, dtype=np.float32) / cell_x
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = grid[y0][:, x0]
    b = grid[y0][:, x0 + 1]
    c = grid[y0 + 1][:, x0]
    d = grid[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]


def fbm(w, h, base_x, base_y, octaves, seed, gain=0.5):
    total = np.zeros((h, w), dtype=np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += amp * value_noise(w, h, max(1.0, base_x / (2 ** o)), max(1.0, base_y / (2 ** o)), seed + 31 * o)
        norm += amp
        amp *= gain
    return total / norm


def fbm1d(n, base, octaves, seed, gain=0.5):
    rng = np.random.default_rng(seed)
    out = np.zeros(n, dtype=np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        cell = max(1.0, base / (2 ** o))
        g = rng.random(int(n / cell) + 3).astype(np.float32)
        xs = np.arange(n, dtype=np.float32) / cell
        i0 = np.floor(xs).astype(int)
        f = xs - i0
        f = f * f * (3 - 2 * f)
        out += amp * (g[i0] + (g[i0 + 1] - g[i0]) * f)
        norm += amp
        amp *= gain
    return out / norm


def to_image(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


# ----------------------------------------------------------------------------------------------- 1. valle al amanecer
def valley_dawn():
    S = 4                                           # supermuestreo
    W, H = CANVAS_W * S, CANVAS_H * S
    xs = np.linspace(0, 1, W, dtype=np.float32)[None, :]
    ys = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    horizon = 0.585
    sun_x, sun_y = 0.50, horizon - 0.075
    rng = np.random.default_rng(7)

    # ---- cielo
    t = np.clip(ys / horizon, 0, 1) * np.ones_like(xs)
    sky = gradient([(0.00, (22, 28, 84)), (0.28, (70, 58, 130)), (0.52, (176, 96, 140)), (0.74, (246, 140, 108)),
                    (0.90, (255, 190, 112)), (1.00, (255, 224, 150))], t)
    dx = (xs - sun_x) * (W / H)
    dy = ys - sun_y
    dist = np.sqrt(dx * dx + dy * dy)
    glow = np.exp(-(dist / 0.28) ** 2)[..., None]
    sky = sky + glow * np.array([110, 62, 18], dtype=np.float32)
    core = np.exp(-(dist / 0.11) ** 2)[..., None]
    sky = sky + core * np.array([60, 52, 40], dtype=np.float32)

    # ---- nubes largas, iluminadas por abajo
    n1 = fbm(W, H, W * 0.55, H * 0.10, 5, 11)
    n2 = fbm(W, H, W * 0.30, H * 0.07, 4, 23)
    band = smoothstep(0.08, 0.20, ys) * (1 - smoothstep(0.40, 0.56, ys))
    cloud = smoothstep(0.50, 0.66, n1 * 0.7 + n2 * 0.5) * band
    shade = np.clip((ys - 0.10) / 0.40, 0, 1)                      # arriba oscura, abajo luminosa
    near_sun = np.exp(-(((xs - sun_x) * 1.2) ** 2) / 0.16)
    cloud_col = gradient([(0.0, (84, 70, 138)), (0.45, (200, 112, 140)), (0.8, (255, 170, 112)), (1.0, (255, 214, 150))],
                         np.clip(shade * 0.75 + near_sun * 0.35, 0, 1))
    sky = sky * (1 - cloud[..., None] * 0.9) + cloud_col * (cloud[..., None] * 0.9)
    # sol
    sun = smoothstep(0.052, 0.046, dist)[..., None]
    sky = sky * (1 - sun) + np.array([255, 250, 214], dtype=np.float32) * sun

    img = sky.copy()

    # ---- cordilleras lejanas y colinas por capas (más lejos = más claras y azuladas)
    def ridge(base_y, amp, freq, seed, valley_dip):
        n = fbm1d(W, W / freq, 5, seed)
        center = np.abs(np.linspace(0, 1, W, dtype=np.float32) - 0.5) * 2          # 0 en el centro, 1 en los bordes
        dip = smoothstep(0.10, 0.85, center)                                         # 0 = fondo plano del valle, 1 = ladera
        return (base_y - amp * (n - 0.5) * 2 + valley_dip * (1 - dip)) * H

    layers = [
        # (base_y, amp, freq, seed, dip, color_top, color_bottom, haze)
        (0.470, 0.050, 5.0, 3, 0.065, (150, 104, 150), (206, 138, 150), 0.00),
        (0.520, 0.045, 4.0, 5, 0.075, (118, 86, 140), (196, 124, 146), 0.10),
        (0.575, 0.050, 3.0, 9, 0.095, (88, 78, 122), (170, 112, 140), 0.14),
        (0.630, 0.060, 2.4, 13, 0.150, (56, 76, 104), (116, 104, 132), 0.18),
        (0.720, 0.070, 2.0, 17, 0.240, (36, 70, 70), (70, 98, 92), 0.22),
    ]
    fg_layer = (0.860, 0.060, 1.6, 21, 0.230, (22, 52, 44), (34, 70, 56), 0.26)
    yy = np.arange(H, dtype=np.float32)[:, None]
    xs_line = np.linspace(0, 1, W, dtype=np.float32)[None, :]
    ridge_tops = []

    def paint_layer(spec):
        nonlocal img
        (by, amp, freq, seed, dip, ctop, cbot, haze) = spec
        top = ridge(by, amp, freq, seed, dip)                                   # (W,)
        ridge_tops.append(top)
        soft = np.clip((yy - top[None, :]) / (1.5 * S) + 0.5, 0, 1)
        depth = np.clip((yy - top[None, :]) / (0.22 * H), 0, 1)
        col = gradient([(0.0, ctop), (1.0, cbot)], depth)
        # la luz del amanecer toca el lomo de las lomas que miran al sol
        lit = np.exp(-(((xs_line - 0.5) * 1.8) ** 2) / 0.25)
        rim = np.clip(1 - (yy - top[None, :]) / (0.035 * H), 0, 1) * lit * 0.60
        col = col + rim[..., None] * np.array([160, 100, 40], dtype=np.float32)
        # neblina del valle entre capas
        fog = np.clip(1 - (yy - top[None, :]) / (0.10 * H), 0, 1) * haze * 2.2
        fogc = np.array([232, 170, 160], dtype=np.float32)
        col = col * (1 - fog[..., None] * 0.55) + fogc * (fog[..., None] * 0.55)
        # textura de pasto/roca
        col = col * (0.88 + 0.24 * fbm(W, H, 4 * S, 3 * S, 3, seed + 100)[..., None])
        img = img * (1 - soft[..., None]) + col * soft[..., None]

    for spec in layers:
        paint_layer(spec)

    # ---- río que baja del sol hacia el espectador, por el fondo plano del valle
    xs_i = np.arange(W, dtype=np.float32)[None, :]
    prog = np.clip((yy - (horizon - 0.02) * H) / ((1.0 - horizon + 0.02) * H), 0, 1)
    meander = np.sin(prog * 6.4 + 0.3) * 0.085 * W * prog + np.sin(prog * 13.0 + 1.0) * 0.012 * W * prog
    cx = 0.5 * W + meander
    half = (0.003 + 0.085 * prog ** 1.4) * W
    river = smoothstep(1.0, 0.0, (np.abs(xs_i - cx) - half) / (1.2 * S)) * (yy > (horizon + 0.005) * H)
    river = river * (yy > ridge_tops[2][None, :] + 1.0)                  # nace detrás de la loma lejana
    rt = np.clip((yy - horizon * H) / ((1 - horizon) * H), 0, 1)
    refl = gradient([(0.0, (255, 220, 156)), (0.25, (246, 156, 122)), (0.6, (150, 96, 140)), (1.0, (62, 64, 112))], rt)
    streak = value_noise(W, H, W * 0.16, 1.4 * S, 77)
    refl = refl * (0.80 + 0.45 * streak[..., None])
    side = np.clip(np.abs(xs_i - cx) / np.maximum(half, 1), 0, 1)
    refl = refl * (1 - 0.35 * side[..., None] ** 3)
    sunstripe = np.exp(-(((xs_i - cx) / np.maximum(half, 1)) ** 2) * 3.0) * np.exp(-((yy / H - horizon) / 0.22) ** 2)
    refl = refl + sunstripe[..., None] * np.array([90, 60, 30], dtype=np.float32)
    img = img * (1 - river[..., None]) + refl * river[..., None]
    # ribera: borde claro de pasto junto al río
    bank = smoothstep(1.0, 0.0, (np.abs(xs_i - cx) - half - 1.5 * S) / (1.5 * S)) * (1 - river) * (yy > (horizon + 0.03) * H) * 0.25
    img = img * (1 - bank[..., None]) + np.array([210, 190, 120], dtype=np.float32) * bank[..., None]

    # ---- la loma del primer plano (tapa los bordes del río y del valle)
    paint_layer(fg_layer)

    # ---- pasto del primer plano con textura
    grass_noise = fbm(W, H, 3.0 * S, 2.2 * S, 3, 91)
    fg_top = ridge_tops[-1]
    fg_mask = (yy >= fg_top[None, :] + 6 * S).astype(np.float32)
    grass = gradient([(0.0, (30, 60, 46)), (1.0, (12, 30, 28))], np.clip((yy / H - 0.82) / 0.2, 0, 1))
    grass = grass * (0.78 + 0.5 * grass_noise[..., None])
    img = img * (1 - fg_mask[..., None] * 0.9) + grass * (fg_mask[..., None] * 0.9)

    # ---- pinos de los costados (siluetas) y un árbol suelto
    def pine(cx_, base_, hgt, color):
        wd = hgt * 0.34
        for k in range(6):
            tier = k / 5
            y0 = base_ - hgt * (1 - tier * 0.98)
            half_w = wd * (0.20 + 0.80 * tier) * 0.55
            ys_ = np.arange(int(max(0, y0)), int(min(H, y0 + hgt * 0.34)))
            for yv in ys_:
                frac = (yv - y0) / (hgt * 0.34)
                hw = half_w * (0.12 + frac)
                x0, x1 = int(max(0, cx_ - hw)), int(min(W, cx_ + hw + 1))
                img[yv, x0:x1] = color
        x0 = int(max(0, cx_ - hgt * 0.02))
        x1 = int(min(W, cx_ + hgt * 0.02 + 1))
        img[int(base_ - hgt * 0.02):int(min(H, base_ + hgt * 0.06)), x0:x1] = color

    for px, hh, py in [(0.07, 0.30, 0.90), (0.15, 0.40, 0.95), (0.23, 0.26, 0.86), (0.31, 0.19, 0.78),
                       (0.93, 0.34, 0.92), (0.86, 0.44, 0.97), (0.78, 0.24, 0.84), (0.70, 0.17, 0.77),
                       (0.03, 0.46, 0.99), (0.97, 0.50, 1.00)]:
        shade_ = 0.55 + 0.45 * (1 - hh)
        pine(px * W, py * H, hh * H, (np.array((14, 34, 36)) * (0.7 + shade_ * 0.6)).astype(np.float32))

    # ---- flores en el pasto
    for _ in range(170):
        fx = rng.uniform(0.0, 1.0)
        fy = rng.uniform(0.86, 0.995)
        c = [(250, 214, 96), (240, 120, 150), (255, 244, 220), (252, 168, 80)][rng.integers(0, 4)]
        x0, y0 = int(fx * W), int(fy * H)
        sz = max(1, int(S * (0.4 + fy - 0.86) * 3))
        img[y0:y0 + sz, x0:x0 + sz] = c
    # ---- aves
    for bx, by, bs in [(0.30, 0.20, 1.0), (0.36, 0.17, 0.8), (0.64, 0.15, 0.9), (0.70, 0.22, 0.7), (0.58, 0.26, 0.6)]:
        xx, yy_ = int(bx * W), int(by * H)
        L = int(5 * S * bs)
        for k in range(L):
            h = int(k * 0.6)
            for sgn in (-1, 1):
                x = xx + sgn * k
                y = yy_ - h + (0 if k < L // 2 else (k - L // 2) // 1)
                if 0 <= x < W and 0 <= y < H:
                    img[y:y + max(1, S // 2), x] = (40, 28, 64)

    # ---- bruma suave sobre todo el valle (capa horizontal rosada)
    haze = np.exp(-((ys - horizon) / 0.11) ** 2) * 0.22 * (0.7 + 0.5 * fbm(W, H, W * 0.4, H * 0.1, 3, 5))
    img = img * (1 - haze[..., None]) + np.array([255, 196, 160], dtype=np.float32) * haze[..., None]

    # ---- viñeta
    vx = (xs - 0.5) * 2
    vy = (ys - 0.5) * 2
    vig = 1 - 0.30 * np.clip(vx ** 2 * 0.8 + vy ** 2 * 0.8, 0, 1.4)
    img = img * vig[..., None]

    im = to_image(img).resize((CANVAS_W, CANVAS_H), Image.LANCZOS)
    # pincelada: un poco de grano
    arr = np.asarray(im).astype(np.float32)
    arr *= (1 + np.random.default_rng(5).uniform(-0.03, 0.03, arr.shape[:2] + (1,)))
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


# ----------------------------------------------------------------------------------------------- 2. persecución policial
def police_chase():
    W, H = CANVAS_W, CANVAS_H
    im = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(im)

    # ---- cielo de atardecer/noche
    sky = [(18, 20, 52), (34, 30, 78), (74, 44, 104), (140, 62, 110), (222, 104, 96)]
    for y in range(0, 52):
        t = y / 51
        seg = min(3, int(t * 4))
        f = t * 4 - seg
        c = tuple(int(lerp(sky[seg][k], sky[seg + 1][k], f)) for k in range(3))
        d.line([(0, y), (W, y)], fill=c)
    rng = np.random.default_rng(3)
    for _ in range(26):
        x, y = int(rng.integers(0, W)), int(rng.integers(0, 22))
        d.point((x, y), fill=(210, 214, 255))
    d.ellipse([86, 5, 98, 17], fill=(244, 240, 214))                       # luna
    d.ellipse([90, 4, 100, 14], fill=(30, 30, 76))                         # (la mella de la luna creciente)
    d.ellipse([86, 5, 98, 17], outline=(250, 246, 226))

    # ---- ciudad al fondo (dos planos)
    far = (46, 34, 84)
    for (x0, x1, top) in [(0, 9, 34), (9, 17, 28), (17, 27, 36), (27, 36, 26), (36, 44, 33), (44, 56, 24), (56, 64, 35),
                          (64, 75, 29), (75, 85, 37), (85, 95, 27), (95, 104, 33), (104, 112, 30)]:
        d.rectangle([x0, top, x1 - 1, 52], fill=far)
    near_b = (22, 18, 48)
    for (x0, x1, top) in [(0, 14, 22), (14, 26, 31), (26, 40, 18), (40, 52, 34), (52, 66, 21), (66, 80, 30),
                          (80, 94, 16), (94, 112, 27)]:
        d.rectangle([x0, top, x1 - 1, 54], fill=near_b)
        for wy in range(top + 3, 50, 4):
            for wx in range(x0 + 2, x1 - 3, 4):
                if rng.random() < 0.52:
                    d.rectangle([wx, wy, wx + 1, wy + 1], fill=(250, 214, 110) if rng.random() < 0.8 else (140, 204, 240))
    # antena con luz roja
    d.line([(33, 18), (33, 10)], fill=(22, 18, 48))
    d.point((33, 9), fill=(255, 60, 60))

    # ---- luces de patrulla en el fondo: resplandor rojo/azul sobre los edificios
    for (cx, cy, r, col) in [(20, 46, 18, (220, 40, 52)), (34, 46, 18, (50, 90, 235))]:
        glow = Image.new("RGB", (W, H), (0, 0, 0))
        gd = ImageDraw.Draw(glow)
        for rr in range(r, 0, -1):
            a = (1 - rr / r) ** 2 * 0.65
            gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=tuple(int(c * a) for c in col))
        im = Image.fromarray(np.clip(np.asarray(im).astype(np.int32) + np.asarray(glow), 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(im)

    # ---- calle: acera, bordillo y asfalto
    d.rectangle([0, 54, W, 60], fill=(98, 94, 120))                      # acera
    d.line([(0, 54), (W, 54)], fill=(142, 138, 164))
    d.rectangle([0, 60, W, 62], fill=(60, 58, 76))                       # bordillo/sombra
    d.rectangle([0, 62, W, H], fill=(42, 42, 56))                        # asfalto
    for x in range(-4, W, 18):
        d.rectangle([x, 74, x + 9, 75], fill=(214, 200, 118))             # línea discontinua
    for y in range(63, H):
        t = (y - 63) / (H - 63)
        d.line([(0, y), (W, y)], fill=tuple(int(c * (0.9 + 0.2 * t)) for c in (42, 42, 56)))
    for x in range(0, W, 7):
        d.line([(x, 55), (x, 59)], fill=(84, 80, 104))                    # juntas de la acera
    # farolas con su cono de luz
    for lx in (46, 104):
        d.line([(lx, 54), (lx, 28)], fill=(26, 22, 44), width=2)
        d.line([(lx, 28), (lx - 7, 28)], fill=(26, 22, 44), width=1)
        d.rectangle([lx - 10, 27, lx - 6, 28], fill=(255, 238, 160))
        for k in range(1, 26):
            alpha = (1 - k / 26) * 0.34
            y = 29 + k
            half_w = 1 + k * 0.55
            for x in range(int(lx - 8 - half_w), int(lx - 8 + half_w) + 1):
                if 0 <= x < W and y < 56:
                    r_, g_, b_ = im.getpixel((x, y))
                    im.putpixel((x, y), (min(255, int(r_ + alpha * 120)), min(255, int(g_ + alpha * 110)),
                                         min(255, int(b_ + alpha * 40))))
    d = ImageDraw.Draw(im)

    # ---- patrulla en el fondo (a la izquierda, más chica)
    def patrol_car(x, y, scale=1):
        w, h = int(22 * scale), int(7 * scale)
        d.rectangle([x, y + 2, x + w, y + h], fill=(236, 238, 246))            # carrocería blanca
        d.rectangle([x, y + h - 3, x + w, y + h], fill=(30, 52, 120))           # franja azul
        d.rectangle([x + 5, y - 2, x + w - 6, y + 2], fill=(216, 220, 232))      # cabina
        d.rectangle([x + 6, y - 1, x + 10, y + 1], fill=(120, 164, 206))        # vidrios
        d.rectangle([x + 12, y - 1, x + w - 7, y + 1], fill=(120, 164, 206))
        d.rectangle([x + 8, y - 4, x + 11, y - 3], fill=(235, 40, 50))          # barra de luces
        d.rectangle([x + 12, y - 4, x + 15, y - 3], fill=(60, 110, 255))
        d.ellipse([x + 3, y + h - 2, x + 8, y + h + 3], fill=(16, 16, 22))
        d.ellipse([x + w - 8, y + h - 2, x + w - 3, y + h + 3], fill=(16, 16, 22))
        d.point((x + w, y + 4), fill=(255, 244, 190))                           # faro

    patrol_car(6, 56)

    # ---- personajes (pixel art)
    def limb(p0, p1, color, width=3):
        d.line([p0, p1], fill=color, width=width)

    def runner(x, y, facing_right, kind):
        """x,y = pies; la figura mide ~26 px."""
        sgn = 1 if facing_right else -1
        skin = (236, 190, 150)
        if kind == "thief":
            shirt, stripe, pants, shoe, hair = (240, 240, 244), (24, 24, 30), (40, 40, 56), (24, 20, 24), (30, 22, 20)
        else:
            shirt, stripe, pants, shoe, hair = (44, 84, 190), None, (30, 44, 100), (16, 16, 20), (44, 30, 20)
        hip = (x, y - 12)
        shoulder = (x + sgn * 2, y - 21)
        # piernas (zancada)
        limb(hip, (x + sgn * 9, y - 5), pants)
        limb((x + sgn * 9, y - 5), (x + sgn * 11, y - 1), pants)
        d.rectangle([x + sgn * 10 - (0 if facing_right else 3), y - 1, x + sgn * 10 + (3 if facing_right else 0), y], fill=shoe)
        limb(hip, (x - sgn * 8, y - 8), pants)
        limb((x - sgn * 8, y - 8), (x - sgn * 12, y - 2), pants)
        d.rectangle([x - sgn * 14 + (0 if facing_right else 2), y - 2, x - sgn * 11 + (0 if facing_right else 2), y - 1],
                    fill=shoe)
        # torso inclinado
        torso = [(hip[0] - 3, hip[1]), (hip[0] + 3, hip[1]), (shoulder[0] + 3 + sgn * 2, shoulder[1]),
                 (shoulder[0] - 3 + sgn * 2, shoulder[1])]
        d.polygon(torso, fill=shirt)
        if stripe:
            for k in range(0, 10, 3):
                d.line([(hip[0] - 3 + sgn * 0, hip[1] - k), (hip[0] + 3 + sgn * 1, hip[1] - k - 1)], fill=stripe)
        # cabeza
        hx, hy = shoulder[0] + sgn * 3, shoulder[1] - 4
        d.ellipse([hx - 3, hy - 3, hx + 3, hy + 3], fill=skin)
        return hip, shoulder, (hx, hy)

    # ladrón (derecha) corriendo hacia la derecha con la bolsa
    t_hip, t_sh, t_head = runner(80, 69, True, "thief")
    d.rectangle([t_head[0] - 3, t_head[1] - 1, t_head[0] + 3, t_head[1] + 1], fill=(14, 14, 18))      # antifaz
    d.point((t_head[0] + 1, t_head[1]), fill=(250, 250, 250))
    d.point((t_head[0] + 3, t_head[1]), fill=(250, 250, 250))
    d.rectangle([t_head[0] - 3, t_head[1] - 4, t_head[0] + 3, t_head[1] - 3], fill=(30, 22, 20))      # gorro
    d.rectangle([t_head[0] - 4, t_head[1] - 3, t_head[0] + 4, t_head[1] - 3], fill=(120, 24, 30))
    # brazos del ladrón: uno hacia atrás con la bolsa de dinero
    d.line([(t_sh[0] - 1, t_sh[1] + 2), (t_sh[0] - 10, t_sh[1] + 8)], fill=(240, 240, 244), width=2)
    bag = (t_sh[0] - 17, t_sh[1] + 5)
    d.ellipse([bag[0], bag[1], bag[0] + 9, bag[1] + 9], fill=(150, 112, 56))
    d.line([(bag[0] + 3, bag[1]), (bag[0] + 6, bag[1])], fill=(80, 56, 28))
    d.text((bag[0] + 2, bag[1] + 1), "$", fill=(40, 120, 52))
    d.line([(t_sh[0] + 2, t_sh[1] + 3), (t_sh[0] + 9, t_sh[1] + 7)], fill=(240, 240, 244), width=2)
    # monedas y billetes volando
    for (cx, cy) in [(62, 52), (66, 58), (58, 59), (70, 50), (54, 55)]:
        d.rectangle([cx, cy, cx + 1, cy + 1], fill=(255, 218, 80))
    d.rectangle([60, 47, 63, 49], fill=(120, 196, 120))

    # policía (centro-izquierda) corriendo hacia la derecha tras él
    p_hip, p_sh, p_head = runner(42, 70, True, "cop")
    d.rectangle([p_head[0] - 4, p_head[1] - 5, p_head[0] + 4, p_head[1] - 3], fill=(24, 36, 84))        # gorra
    d.rectangle([p_head[0] - 5, p_head[1] - 3, p_head[0] + 5, p_head[1] - 3], fill=(14, 14, 20))         # visera
    d.point((p_head[0], p_head[1] - 4), fill=(250, 214, 80))                                             # insignia
    d.point((p_head[0] + 2, p_head[1]), fill=(40, 28, 20))
    d.line([(p_hip[0] - 3, p_hip[1]), (p_hip[0] + 3, p_hip[1])], fill=(18, 18, 24), width=1)            # cinturón
    d.point((p_sh[0] + 1, p_sh[1] + 5), fill=(250, 214, 80))                                             # placa
    # brazo adelante con la porra, el otro atrás
    d.line([(p_sh[0] + 3, p_sh[1] + 2), (p_sh[0] + 11, p_sh[1] + 1)], fill=(44, 84, 190), width=2)
    d.line([(p_sh[0] + 10, p_sh[1] + 1), (p_sh[0] + 17, p_sh[1] - 3)], fill=(20, 20, 26), width=1)
    d.line([(p_sh[0] - 1, p_sh[1] + 3), (p_sh[0] - 8, p_sh[1] + 8)], fill=(44, 84, 190), width=2)
    # silbato: rayitas de "¡alto!"
    d.line([(p_head[0] + 5, p_head[1] - 2), (p_head[0] + 9, p_head[1] - 4)], fill=(255, 250, 220))
    d.line([(p_head[0] + 6, p_head[1] + 1), (p_head[0] + 11, p_head[1] + 1)], fill=(255, 250, 220))

    # ---- líneas de velocidad y polvo
    for (x0, x1, y) in [(4, 24, 66), (8, 22, 71), (2, 18, 77), (30, 46, 77), (60, 74, 76), (52, 64, 72), (90, 104, 70)]:
        d.line([(x0, y), (x1, y)], fill=(86, 84, 110))
    for (x, y) in [(36, 70), (30, 74), (74, 71), (68, 75)]:
        d.ellipse([x - 2, y - 1, x + 2, y + 1], fill=(120, 116, 140))

    # ---- grano y viñeta
    arr = np.asarray(im).astype(np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    vig = 1 - 0.30 * np.clip(((xx / W - 0.5) * 2) ** 2 * 0.8 + ((yy / H - 0.5) * 2) ** 2 * 0.8, 0, 1.4)
    arr *= vig[..., None]
    arr *= (1 + np.random.default_rng(9).uniform(-0.025, 0.025, arr.shape[:2] + (1,)))
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


# ----------------------------------------------------------------------------------------------- 3. foto aérea de un circuito
def circuit_aerial():
    S = 4
    W, H = CANVAS_W * S, CANVAS_H * S
    rng = np.random.default_rng(12)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

    # ---- terreno: pasto con franjas de corte y manchones
    n_big = fbm(W, H, 60 * S, 40 * S, 4, 5)
    n_fine = fbm(W, H, 3 * S, 3 * S, 3, 8)
    stripes = (np.sin((xx * 0.55 + yy * 0.25) / S * 0.9) > 0).astype(np.float32)
    grass = gradient([(0.0, (46, 92, 40)), (0.5, (66, 118, 52)), (1.0, (92, 140, 64))], n_big)
    grass = grass * (0.93 + 0.14 * stripes[..., None]) * (0.88 + 0.24 * n_fine[..., None])
    img = grass.copy()

    # ---- trazado: spline cerrada de Catmull-Rom (coordenadas 0..1 de la imagen)
    ctrl = np.array([
        (0.20, 0.30), (0.42, 0.22), (0.66, 0.20), (0.82, 0.26), (0.88, 0.40), (0.80, 0.50), (0.64, 0.50), (0.58, 0.60),
        (0.70, 0.72), (0.62, 0.84), (0.40, 0.82), (0.30, 0.70), (0.38, 0.60), (0.26, 0.52), (0.12, 0.50), (0.10, 0.38),
    ], dtype=np.float32)
    n = len(ctrl)
    pts = []
    for i in range(n):
        p0, p1, p2, p3 = ctrl[(i - 1) % n], ctrl[i], ctrl[(i + 1) % n], ctrl[(i + 2) % n]
        for t in np.linspace(0, 1, 60, endpoint=False):
            t2, t3 = t * t, t * t * t
            p = 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
            pts.append(p)
    pts = np.array(pts, dtype=np.float32)
    path = np.stack([pts[:, 0] * W, pts[:, 1] * H], axis=1)
    seg = np.linalg.norm(np.roll(path, -1, axis=0) - path, axis=1)
    param = np.concatenate([[0], np.cumsum(seg)[:-1]])
    # curvatura (giro entre tramos)
    d1 = np.roll(path, -1, axis=0) - path
    ang = np.arctan2(d1[:, 1], d1[:, 0])
    turn = np.abs(np.angle(np.exp(1j * (np.roll(ang, -1) - ang))))
    turn = np.convolve(np.concatenate([turn[-8:], turn, turn[:8]]), np.ones(17) / 17, mode="same")[8:-8]
    cross = np.sign(np.sin(np.roll(ang, -1) - ang))
    cross = np.convolve(np.concatenate([cross[-8:], cross, cross[:8]]), np.ones(17) / 17, mode="same")[8:-8]

    tree = cKDTree(path)
    grid = np.stack([xx.ravel(), yy.ravel()], axis=1)
    dist, idx = tree.query(grid, workers=-1)
    dist = dist.reshape(H, W)
    idx = idx.reshape(H, W)
    hw = 3.9 * S                                     # semiancho del asfalto
    along = param[idx]
    curv = turn[idx] > 0.045                         # zonas de curva
    inner_side = (np.sign((grid[:, 0] - path[idx.ravel(), 0]) * -d1[idx.ravel(), 1]
                          + (grid[:, 1] - path[idx.ravel(), 1]) * d1[idx.ravel(), 0]).reshape(H, W))

    # zona de grava y escapatoria en las curvas
    gravel_w = 4.2 * S
    gravel = (dist > hw) & (dist < hw + gravel_w) & curv & (inner_side * cross[idx] > 0)
    gcol = np.array([176, 160, 118], dtype=np.float32) * (0.85 + 0.3 * n_fine[..., None])
    img = np.where(gravel[..., None], gcol, img)
    # banda de pasto recortado junto al asfalto
    verge = (dist > hw) & (dist < hw + 2.2 * S) & ~gravel
    img = np.where(verge[..., None], img * 1.12, img)

    # edificios del paddock y tribunas antes del asfalto (para que el asfalto quede encima si se cruzan)
    def building(x0, y0, w, h, col, shadow=True, roof_lines=0):
        x0, y0, w, h = int(x0 * S), int(y0 * S), int(w * S), int(h * S)
        if shadow:
            sx = np.s_[min(H, y0 + 2 * S):min(H, y0 + h + 2 * S), min(W, x0 + 2 * S):min(W, x0 + w + 2 * S)]
            img[sx] *= 0.62
        roof = np.s_[max(0, y0):min(H, y0 + h), max(0, x0):min(W, x0 + w)]
        img[roof] = col
        for k in range(roof_lines):
            yline = y0 + int((k + 1) * h / (roof_lines + 1))
            img[yline:yline + max(1, S // 3), max(0, x0):min(W, x0 + w)] = np.array(col) * 0.8

    # paddock (centro-izquierda dentro del circuito) y tribunas junto a la recta principal
    img[int(0.56 * H):int(0.70 * H), int(0.12 * W):int(0.30 * W)] = np.array([112, 114, 120], dtype=np.float32) * (0.9 + 0.2 * n_fine[int(0.56 * H):int(0.70 * H), int(0.12 * W):int(0.30 * W), None])
    building(15, 46, 12, 5, (226, 228, 234), roof_lines=2)
    building(15, 54, 8, 4, (200, 204, 214), roof_lines=1)
    building(26, 54, 6, 5, (232, 232, 238))
    for k in range(6):                                                         # coches estacionados en el paddock
        cx = (14 + k * 3) * S
        cy = 62 * S
        img[cy:cy + 2 * S, cx:cx + int(1.3 * S)] = [(210, 40, 40), (40, 90, 210), (230, 230, 230), (240, 190, 40), (60, 60, 64), (40, 160, 90)][k % 6]
    # tribunas: bandas de colores sobre la recta superior
    for k in range(7):
        x0 = (34 + k * 5) * S
        img[int(0.08 * H):int(0.15 * H), x0:x0 + 4 * S] = np.array([(196, 48, 48), (46, 82, 190), (232, 200, 60), (230, 230, 232)][k % 4], dtype=np.float32) * (0.85 + 0.2 * n_fine[int(0.08 * H):int(0.15 * H), x0:x0 + 4 * S, None])
    img[int(0.05 * H):int(0.08 * H), 34 * S:69 * S] = (170, 172, 178)

    # asfalto con grano, bordes blancos
    asphalt = (dist < hw)
    acol = gradient([(0.0, (54, 56, 62)), (1.0, (74, 76, 84))], fbm(W, H, 5 * S, 5 * S, 3, 21))
    # huella de goma en la trazada de carrera
    rubber = np.exp(-(((dist - 0.0) / (1.3 * S)) ** 2)) * 0.12
    acol = acol * (1 - rubber[..., None]) + np.array([30, 30, 34], dtype=np.float32) * rubber[..., None]
    img = np.where(asphalt[..., None], acol * (0.92 + 0.16 * n_fine[..., None]), img)
    edge = (dist > hw - 0.5 * S) & (dist < hw)
    img = np.where(edge[..., None], np.array([232, 232, 236], dtype=np.float32), img)

    # pianos (kerbs) rojos y blancos en el interior de las curvas
    kerb_zone = (dist > hw) & (dist < hw + 1.3 * S) & curv & (inner_side * cross[idx] < 0)
    stripe = ((along / (2.2 * S)).astype(int) % 2 == 0)
    kcol = np.where(stripe[..., None], np.array([214, 36, 40], dtype=np.float32), np.array([236, 236, 238], dtype=np.float32))
    img = np.where(kerb_zone[..., None], kcol, img)

    # pit lane: carril paralelo a la recta superior con boxes
    px0, px1 = int(0.30 * W), int(0.78 * W)
    py = int(0.20 * H) - int(8.6 * S) if False else None

    # línea de meta (a cuadros) sobre la recta
    start_idx = int(np.argmin(np.abs(path[:, 0] - 0.55 * W) + 1000 * (path[:, 1] > 0.30 * H)))
    sp = path[start_idx]
    nrm = np.array([-d1[start_idx, 1], d1[start_idx, 0]], dtype=np.float32)
    nrm /= np.linalg.norm(nrm) + 1e-6
    tan = d1[start_idx] / (np.linalg.norm(d1[start_idx]) + 1e-6)
    rel_x = (xx - sp[0]) * tan[0] + (yy - sp[1]) * tan[1]
    rel_n = (xx - sp[0]) * nrm[0] + (yy - sp[1]) * nrm[1]
    band = (np.abs(rel_x) < 1.2 * S) & (np.abs(rel_n) < hw - 0.6 * S)
    chequer = (((rel_x / (0.6 * S)).astype(int) + (rel_n / (0.6 * S)).astype(int)) % 2 == 0)
    img = np.where(band[..., None], np.where(chequer[..., None], np.array([238, 238, 240], dtype=np.float32), np.array([20, 20, 24], dtype=np.float32)), img)
    # pórtico de salida
    gantry = (np.abs(rel_x) < 0.8 * S) & (np.abs(rel_n) < hw + 1.5 * S) & (np.abs(rel_n) >= hw)
    img = np.where(gantry[..., None], np.array([200, 200, 206], dtype=np.float32), img)

    # coches de carreras (puntitos con sombra)
    car_cols = [(220, 30, 30), (250, 250, 250), (30, 90, 220), (250, 200, 40), (30, 30, 34)]
    for k, pi in enumerate([20, 70, 130, 330, 480]):
        p = path[pi % len(path)]
        tdir = d1[pi % len(path)] / (np.linalg.norm(d1[pi % len(path)]) + 1e-6)
        for s_ in np.linspace(-1.1, 1.1, 14) * S:
            for w_ in np.linspace(-0.45, 0.45, 5) * S:
                cx_ = int(p[0] + tdir[0] * s_ - tdir[1] * w_)
                cy_ = int(p[1] + tdir[1] * s_ + tdir[0] * w_)
                if 0 <= cx_ < W and 0 <= cy_ < H:
                    img[cy_, cx_] = car_cols[k]
                    sy, sx2 = min(H - 1, cy_ + 2), min(W - 1, cx_ + 1)
                    if dist[sy, sx2] < hw:
                        img[sy, sx2] *= 0.7

    # árboles (con sombra) lejos del asfalto
    tree_mask = np.zeros((H, W), dtype=bool)
    for _ in range(420):
        tx, ty = rng.integers(0, W), rng.integers(0, H)
        if dist[ty, tx] < hw + 7 * S:
            continue
        if 0.12 * W < tx < 0.30 * W and 0.55 * H < ty < 0.71 * H:
            continue
        if 0.07 * H < ty < 0.17 * H and 33 * S < tx < 70 * S:
            continue
        r = int(rng.uniform(1.0, 2.1) * S)
        y0, y1, x0, x1 = max(0, ty - r), min(H, ty + r + 1), max(0, tx - r), min(W, tx + r + 1)
        gy, gx = np.mgrid[y0:y1, x0:x1]
        m = (gx - tx) ** 2 + (gy - ty) ** 2 <= r * r
        sh = (gx - tx - r * 0.7) ** 2 + (gy - ty - r * 0.7) ** 2 <= r * r
        reg = img[y0:y1, x0:x1]
        reg[sh & ~m] *= 0.55
        light = 0.75 + 0.5 * ((gx - tx) * -0.5 + (gy - ty) * -0.5) / (r + 1e-6)
        reg[m] = (np.array([28, 78, 36], dtype=np.float32) * np.clip(light, 0.6, 1.3)[m][:, None])
        tree_mask[y0:y1, x0:x1] |= m

    # lago pequeño
    lake_c = (0.50 * W, 0.66 * H)
    lk = (((xx - lake_c[0]) / (7.5 * S)) ** 2 + ((yy - lake_c[1]) / (4.5 * S)) ** 2)
    lake = (lk < 1) & (dist > hw + 4 * S)
    lcol = gradient([(0.0, (70, 140, 176)), (1.0, (36, 92, 130))], np.clip(lk, 0, 1))
    shore = (lk > 0.82) & (lk < 1.0) & lake
    img = np.where(lake[..., None], lcol, img)
    img = np.where(shore[..., None], np.array([196, 190, 150], dtype=np.float32), img)

    # luz de foto: tono cálido, leve desenfoque, viñeta, grano
    im = to_image(img).filter(ImageFilter.GaussianBlur(0.9 * S / 2))
    im = im.resize((CANVAS_W, CANVAS_H), Image.LANCZOS)
    arr = np.asarray(im).astype(np.float32)
    arr[..., 0] *= 1.03
    arr[..., 2] *= 0.96
    yy2, xx2 = np.mgrid[0:CANVAS_H, 0:CANVAS_W]
    vig = 1 - 0.28 * np.clip(((xx2 / CANVAS_W - 0.5) * 2) ** 2 * 0.8 + ((yy2 / CANVAS_H - 0.5) * 2) ** 2 * 0.8, 0, 1.4)
    arr *= vig[..., None]
    arr *= (1 + np.random.default_rng(15).uniform(-0.03, 0.03, arr.shape[:2] + (1,)))
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")
    # borde blanco tipo "foto montada" (passepartout) de 3 px
    d = ImageDraw.Draw(out)
    for k in range(3):
        d.rectangle([k, k, CANVAS_W - 1 - k, CANVAS_H - 1 - k], outline=(240, 240, 236))
    d.rectangle([3, 3, CANVAS_W - 4, CANVAS_H - 4], outline=(176, 176, 172))
    return out


# ----------------------------------------------------------------------------------------------- marcos
def frame_pixels(kind):
    """Devuelve una imagen RGBA TEX_W x TEX_H con el marco en el borde (BORDER px) y el centro transparente."""
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    rng = np.random.default_rng(40 + len(kind))
    for y in range(TEX_H):
        for x in range(TEX_W):
            dl, dr, dt, db = x, TEX_W - 1 - x, y, TEX_H - 1 - y
            m = min(dl, dr, dt, db)                      # 0 = borde exterior, BORDER-1 = borde interior
            if m >= BORDER:
                continue
            n = 1 + rng.uniform(-0.04, 0.04)
            if kind == "gold":
                pal = [(92, 62, 22), (150, 104, 36), (212, 164, 58), (240, 200, 92), (226, 178, 66), (178, 128, 44),
                       (120, 82, 28), (58, 38, 14)]
                c = pal[m]
                # adorno: muescas cada 8 px en la banda central
                along = x if min(dt, db) < min(dl, dr) else y
                if m in (3, 4) and (along // 4) % 2 == 0:
                    c = tuple(int(v * 0.86) for v in c)
            elif kind == "black":
                pal = [(10, 10, 12), (26, 26, 30), (46, 46, 54), (66, 68, 78), (40, 40, 46), (22, 22, 26),
                       (150, 24, 30), (12, 12, 14)]
                c = pal[m]
            else:                                         # metal plateado
                pal = [(120, 124, 134), (196, 200, 210), (232, 236, 244), (210, 214, 224), (168, 172, 184),
                       (124, 128, 140), (90, 94, 104), (60, 62, 70)]
                c = pal[m]
            px[x, y] = (min(255, int(c[0] * n)), min(255, int(c[1] * n)), min(255, int(c[2] * n)), 255)
    return img


def compose(canvas, frame_kind):
    out = frame_pixels(frame_kind)
    out.paste(canvas.convert("RGBA"), (BORDER, BORDER))
    return out


def make_icon(tex):
    """Ícono 32x32 del ítem: el cuadro entero (28x21) centrado, con un contorno oscuro de 1 px."""
    small = tex.convert("RGBA").resize((28, 21), Image.LANCZOS)
    icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    icon.paste(small, (2, 5), small)
    a = np.asarray(icon).astype(np.float32)
    sil = a[..., 3] > 0
    pad = np.pad(sil, 1)
    near = np.zeros_like(sil)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        near |= pad[1 + dy:33 + dy, 1 + dx:33 + dx]
    edge = near & ~sil
    a[edge] = (24, 18, 14, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# ----------------------------------------------------------------------------------------------- modelo
def block_model(name):
    """Marco en relieve: panel de fondo + 4 barras de 1 px de ancho y 1 px de fondo. 16 x 12 px, pegado a la pared (z = 16).

    Las coordenadas UV del modelo van de 0 a 16 en ambos ejes sin importar el tamaño de la textura (128 x 96): el eje
    vertical cubre 12 px del modelo en 16 unidades de UV, por eso 1 px de alto = 1,333 unidades.
    """
    k = 16.0 / 12.0
    tex = f"beautifuldecor:block/{name}"
    top_bar = [0, 0, 16, round(k, 4)]
    bottom_bar = [0, round(16 - k, 4), 16, 16]
    left_bar = [0, round(k, 4), 1, round(16 - k, 4)]
    right_bar = [15, round(k, 4), 16, round(16 - k, 4)]
    inner_strip_h = [0, round(k - 0.17, 4), 16, round(k, 4)]
    inner_strip_h_b = [0, round(16 - k, 4), 16, round(16 - k + 0.17, 4)]
    inner_strip_v = [0.9, 4, 1, 12]
    inner_strip_v_r = [15, 4, 15.1, 12]
    edge_h = [0, 0, 16, 0.17]
    edge_v = [0, 4, 0.12, 12]

    def face(uv, rotation=None):
        f = {"uv": uv, "texture": "#p"}
        if rotation:
            f["rotation"] = rotation
        return f

    elements = [
        {   # panel de fondo con el lienzo
            "from": [0, 2, 15], "to": [16, 14, 16],
            "faces": {
                "north": face([0, 0, 16, 16]),
                "south": face([0, 0, 16, 16]),
                "up": face(edge_h), "down": face(edge_h),
                "east": face(edge_v), "west": face(edge_v),
            },
        },
        {   # barra de arriba
            "from": [0, 13, 14], "to": [16, 14, 15],
            "faces": {
                "north": face(top_bar), "up": face(edge_h), "down": face(inner_strip_h),
                "east": face(edge_v), "west": face(edge_v),
            },
        },
        {   # barra de abajo
            "from": [0, 2, 14], "to": [16, 3, 15],
            "faces": {
                "north": face(bottom_bar), "down": face(edge_h), "up": face(inner_strip_h_b),
                "east": face(edge_v), "west": face(edge_v),
            },
        },
        {   # barra del lado -x (a la DERECHA de quien mira el cuadro de frente: en la cara norte la u crece hacia -x)
            "from": [0, 3, 14], "to": [1, 13, 15],
            "faces": {
                "north": face(right_bar), "west": face(edge_v), "east": face(inner_strip_v_r),
                "up": face(edge_h), "down": face(edge_h),
            },
        },
        {   # barra del lado +x (a la IZQUIERDA de quien mira el cuadro de frente)
            "from": [15, 3, 14], "to": [16, 13, 15],
            "faces": {
                "north": face(left_bar), "east": face(edge_v), "west": face(inner_strip_v),
                "up": face(edge_h), "down": face(edge_h),
            },
        },
    ]
    return {"textures": {"p": tex, "particle": tex}, "elements": elements}


def blockstate(name):
    model = f"beautifuldecor:block/{name}"
    return {"variants": {
        "facing=north": {"model": model},
        "facing=east": {"model": model, "y": 90},
        "facing=south": {"model": model, "y": 180},
        "facing=west": {"model": model, "y": 270},
    }}


def loot_table(name):
    return {"type": "minecraft:block", "pools": [{
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"beautifuldecor:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def build_textures():
    return {
        "painting_valley_dawn": compose(valley_dawn(), "gold"),
        "painting_police_chase": compose(police_chase(), "black"),
        "painting_circuit_aerial": compose(circuit_aerial(), "metal"),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()

    textures = build_textures()
    if args.preview:
        sheet = Image.new("RGBA", (TEX_W * 3 * 3 + 40, TEX_H * 3 + 20), (60, 64, 74, 255))
        for i, name in enumerate(NAMES):
            big = textures[name].resize((TEX_W * 3, TEX_H * 3), Image.NEAREST)
            sheet.alpha_composite(big, (10 + i * (TEX_W * 3 + 10), 10))
        sheet.convert("RGB").save(args.preview)
        print("vista previa:", args.preview)
    if args.write:
        (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
        (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
        (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
        (DATA / "loot_tables/blocks").mkdir(parents=True, exist_ok=True)
        for name in NAMES:
            textures[name].save(ASSETS / f"textures/block/{name}.png")
            make_icon(textures[name]).save(ASSETS / f"textures/item/{name}.png")
            (ASSETS / f"models/block/{name}.json").write_text(json.dumps(block_model(name), indent=2) + "\n", encoding="utf-8")
            (ASSETS / f"models/item/{name}.json").write_text(json.dumps({
                "parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{name}"}}, indent=2) + "\n",
                encoding="utf-8")
            (ASSETS / f"blockstates/{name}.json").write_text(json.dumps(blockstate(name), indent=2) + "\n", encoding="utf-8")
            (DATA / f"loot_tables/blocks/{name}.json").write_text(json.dumps(loot_table(name), indent=2) + "\n", encoding="utf-8")
        print("escrito: texturas, íconos, modelos, blockstates y loot tables de", len(NAMES), "cuadros")


if __name__ == "__main__":
    main()
