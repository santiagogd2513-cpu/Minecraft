"""Materiales de la marca Bugerati (negro, oro y plata con detalles de mármol, tela y cristal)."""
import math
import random

from .common import *

GOLD = (214, 172, 58, 255)
SILVER = (196, 203, 212, 255)
BLACK = (24, 25, 29, 255)


def brushed(name, base, hi, seed, amp=7):
    rng = random.Random(seed)
    im = new(32)
    px = im.load()
    cols = [rng.randint(-amp, amp) for _ in range(32)]
    for y in range(32):
        for x in range(32):
            c = shade(base, cols[x] + rng.randint(-2, 2))
            band = 1.0 - abs(((x + y * 0.5) % 32) - 9) / 6.0         # brillo diagonal
            if band > 0:
                c = mix(c, hi, band * 0.65)
            px[x, y] = c
    save_tex(name, im)


def marble():
    rng = random.Random(33)
    im = fill_noise(new(32), (238, 238, 242, 255), 3, 33)
    px = im.load()
    for _ in range(4):
        x, y = rng.randrange(32), rng.randrange(32)
        dx = rng.choice([-1, 1])
        for _ in range(40):
            px[x % 32, y % 32] = (150, 152, 164, 255)
            if rng.random() < 0.5:
                px[(x + 1) % 32, y % 32] = (190, 192, 200, 255)
            x += dx if rng.random() < 0.6 else 0
            y += rng.choice([0, 1])
    save_tex("bg_marble", im)


def shade_tex(name, base, glow):
    im = new(32)
    px = im.load()
    for y in range(32):
        for x in range(32):
            pleat = -12 if x % 4 == 0 else (6 if x % 4 == 1 else 0)
            lift = 18 * (1 - y / 31) if glow else 0
            px[x, y] = shade(base, pleat + lift)
    save_tex(name, im)


def globe(name, on):
    im = new(32)
    px = im.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 22.0
            if on:
                px[x, y] = (255, clamp(250 - d * 40), clamp(214 - d * 70), 255)
            else:
                px[x, y] = (clamp(236 - d * 14), clamp(238 - d * 14), clamp(244 - d * 12), 255)
    if not on:
        disc(im, 10, 9, 4, (250, 252, 255, 255))
    save_tex(name, im)


def crystal():
    rng = random.Random(44)
    im = fill_noise(new(16), (172, 226, 246, 255), 5, 44)
    px = im.load()
    for i in range(16):
        px[i, (i * 2) % 16] = (226, 246, 255, 255)
    for _ in range(8):
        px[rng.randrange(16), rng.randrange(16)] = (255, 255, 255, 255)
    save_tex("bg_crystal", im)


def small():
    save_tex("bg_candle", fill_noise(new(16), (250, 244, 224, 255), 3, 51))
    save_tex("bg_wick", fill_noise(new(16), (50, 40, 36, 255), 2, 52))
    im = new(16)
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 9.0) / 8.0
            px[x, y] = (255, clamp(236 - d * 90), clamp(130 - d * 120), 255)
    save_tex("bg_flame", im)
    save_tex("bg_bulb_off", fill_noise(new(16), (232, 228, 214, 255), 3, 53))
    # insignia Bugerati: placa negra con "B" dorada
    im = new(16, 16, (18, 18, 22, 255))
    frame(im, 0, 0, 15, 15, GOLD)
    frame(im, 1, 1, 14, 14, shade(GOLD, -60))
    draw_pattern(im, ["1110", "1001", "1110", "1001", "1110"], 4, 3, GOLD, scale=2) if False else None
    draw_text(im, "B", 6, 4, GOLD, scale=2)
    save_tex("bg_badge", im)
    # cadena
    im = fill_noise(new(16), (150, 118, 40, 255), 3, 54)
    for y in range(0, 16, 4):
        rect(im, 0, y, 15, y, (70, 54, 16, 255))
    save_tex("bg_chain", im)


def generate_materials():
    brushed("bg_gold", GOLD, (255, 236, 150, 255), 11)
    brushed("bg_silver", SILVER, (255, 255, 255, 255), 12, amp=5)
    brushed("bg_black", BLACK, (96, 98, 110, 255), 13, amp=3)
    marble()
    shade_tex("bg_shade_off", (236, 226, 200, 255), False)
    shade_tex("bg_shade_on", (255, 232, 160, 255), True)
    globe("bg_globe_off", False)
    globe("bg_globe_on", True)
    crystal()
    small()
