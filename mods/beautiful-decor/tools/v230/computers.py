#!/usr/bin/env python3
"""Genera los 6 computadores de escritorio HS nuevos de la 2.3.0 (3 antiguos y 3 modernos).

Uso (desde la raíz del proyecto):  python3 -m tools.v230.computers
Escribe texturas, modelos (encendido/apagado), blockstates, loot, nombres en los dos idiomas y registra los ítems en
tools/v182/item_models_src.json (los íconos salen de:  python3 -m tools.v182.make_icons --only <ids> --write).
Los ids y las formas de colisión deben coincidir con ModBlocks.java / HsDesktopBlock.java.
Los modelos se pintan "recortando" cada textura desde la esquina superior izquierda (uv 0,0 -> ancho,alto de la cara).
"""
import json

from ..v180.common import (ASSETS, FACINGS, ITEMS, LANG, MODELS, STATES, Model, disc, draw_pattern, draw_text, fill_noise,
                           frame, mix, new, rect, save_tex, shade, write_json, write_loot)

IDS = ("hs_desktop_retro", "hs_desktop_compact", "hs_desktop_amber", "hs_desktop_slim", "hs_desktop_gaming", "hs_desktop_dual")
NAMES = {
    "hs_desktop_retro": ("HS Retro Desktop (90s)", "Computador HS retro (años 90)"),
    "hs_desktop_compact": ("HS Compact Desktop (80s)", "Computador HS compacto (años 80)"),
    "hs_desktop_amber": ("HS Classic Amber Desktop", "Computador HS clásico de pantalla ámbar"),
    "hs_desktop_slim": ("HS Slim All-in-One", "Computador HS todo en uno delgado"),
    "hs_desktop_gaming": ("HS Gaming Desktop", "Computador HS gamer"),
    "hs_desktop_dual": ("HS Dual-Screen Workstation", "Estación de trabajo HS de doble pantalla"),
}
NEW_ITEMS = {}
LANG_EN, LANG_ES = {}, {}

BEIGE = (212, 203, 178, 255)
BEIGE_D = (184, 175, 150, 255)
CREAM = (228, 221, 196, 255)
CREAM_D = (200, 192, 166, 255)
WHITE = (234, 236, 240, 255)
ALU = (186, 190, 198, 255)
ALU_D = (150, 154, 162, 255)
BLACK = (30, 32, 36, 255)
GRAPH = (52, 55, 62, 255)
AMBER = (255, 176, 0, 255)


def A(c):
    return c if len(c) == 4 else (*c, 255)


# ====================================================================================================== materiales
def plastic(name, base, seed, amp=3, vgrad=5):
    save_tex(name, fill_noise(new(16), base, amp, seed, vgrad=vgrad))


def materials():
    plastic("pc_beige", BEIGE, 3101)
    plastic("pc_beige_d", BEIGE_D, 3102)
    plastic("pc_cream", CREAM, 3103)
    plastic("pc_cream_d", CREAM_D, 3104)
    plastic("pc_white", WHITE, 3105, amp=2, vgrad=4)
    plastic("pc_black", BLACK, 3106, amp=2, vgrad=4)
    plastic("pc_graph", GRAPH, 3107, amp=2, vgrad=4)
    alu = fill_noise(new(16), ALU, 2, 3108, vgrad=4)                      # aluminio cepillado
    for y in range(0, 16, 2):
        rect(alu, 0, y, 15, y, shade(ALU, 6))
    save_tex("pc_alu", alu)
    plastic("pc_alu_d", ALU_D, 3109, amp=2)
    vent = fill_noise(new(16), GRAPH, 2, 3110)
    for y in range(2, 15, 3):
        rect(vent, 2, y, 13, y, (14, 15, 18, 255))
    save_tex("pc_vent", vent)


# ====================================================================================================== pantallas
def glass_off(im, x0, y0, x1, y1, base=(20, 25, 27)):
    px = im.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d = 16 if 2 <= (x - x0) + (y - y0) <= 3 else 0
            px[x, y] = A(shade(base, d))


def screen_win95(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1)
    rect(im, x0, y0, x1, y1, (0, 128, 128, 255))
    rect(im, x0 + 1, y0 + 1, x0 + 5, y0 + 4, (192, 192, 192, 255))
    rect(im, x0 + 1, y0 + 1, x0 + 5, y0 + 1, (0, 0, 128, 255))
    rect(im, x0 + 6, y0 + 2, x0 + 7, y0 + 3, (250, 250, 250, 255))       # icono
    rect(im, x0, y1, x1, y1, (192, 192, 192, 255))                       # barra de tareas
    rect(im, x0, y1, x0 + 1, y1, (40, 150, 40, 255))


def screen_mono(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1, (58, 66, 60))
    rect(im, x0, y0, x1, y1, (196, 222, 200, 255))
    draw_pattern(im, ["01010", "01010", "00000", "10001", "01110"], x0 + 1, y0 + 1, (20, 30, 22, 255))


def screen_amber(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1, (16, 12, 6))
    rect(im, x0, y0, x1, y1, (10, 7, 2, 255))
    for i, n in enumerate((6, 4, 7, 3)):
        y = y0 + 1 + i
        if y <= y1:
            rect(im, x0 + 1, y, x0 + n, y, AMBER)
    rect(im, x0 + 4, y1 - 1, x0 + 5, y1 - 1, shade(AMBER, 40))          # cursor


def screen_slim(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1, (14, 16, 20))
    px = im.load()
    w, h = x1 - x0 + 1, y1 - y0 + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = ((x - x0) / max(1, w - 1) * 0.7 + (y - y0) / max(1, h - 1) * 0.3)
            px[x, y] = A(mix((40, 110, 255, 255), (255, 120, 190, 255), t))
    for x in range(x0 + 4, x1 - 3, 2):                                   # dock
        rect(im, x, y1, x, y1, (255, 255, 255, 255))
    rect(im, x0, y0, x1, y0, (20, 20, 40, 255))


def screen_gaming(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1, (12, 10, 16))
    rect(im, x0, y0, x1, y1, (10, 6, 22, 255))
    colors = ((0, 230, 255, 255), (255, 60, 200, 255), (255, 230, 60, 255))
    heights = (3, 5, 2, 6, 4, 5, 3, 6)
    for i, hh in enumerate(heights):
        x = x0 + i
        if x <= x1:
            for k in range(min(hh, y1 - y0)):
                rect(im, x, y1 - k, x, y1 - k, colors[(i + k) % 3] if k > 1 else colors[i % 3])


def screen_code(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1, (14, 18, 26))
    rect(im, x0, y0, x1, y1, (18, 24, 40, 255))
    cols = ((120, 200, 255, 255), (255, 170, 90, 255), (150, 230, 140, 255), (220, 140, 255, 255))
    for i, (ind, n) in enumerate(((0, 4), (1, 5), (2, 3), (2, 4), (1, 2), (0, 5))):
        y = y0 + 1 + i
        if y <= y1:
            rect(im, x0 + 1 + ind, y, min(x1, x0 + ind + n), y, cols[i % 4])


def screen_hills(im, x0, y0, x1, y1, on):
    if not on:
        return glass_off(im, x0, y0, x1, y1, (14, 18, 22))
    px = im.load()
    h = y1 - y0 + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = A(mix((120, 190, 255, 255), (230, 240, 255, 255), (y - y0) / max(1, h - 1)))
    for x in range(x0, x1 + 1):                                          # montañas
        top = y1 - 1 - int(3 * abs(((x - x0) % 6) - 3) / 3.0)
        rect(im, x, top, x, y1, (40, 70, 110, 255))
    rect(im, x1 - 2, y0 + 1, x1 - 1, y0 + 2, (255, 240, 150, 255))      # sol


def monitor_front(name, w, h, bezel, inset, draw_screen, on, seed, led=None):
    """Frente de un monitor recortado desde (0,0): marco + pantalla + LED. inset = (x0, y0, x1, y1) de la pantalla."""
    im = fill_noise(new(16, 16, A(bezel)), bezel, 2, seed, box=(0, 0, w, h))
    x0, y0, x1, y1 = inset
    rect(im, x0 - 1, y0 - 1, x1 + 1, y1 + 1, shade(bezel, -46))
    draw_screen(im, x0, y0, x1, y1, on)
    if led:
        rect(im, led[0], led[1], led[0], led[1], (90, 255, 140, 255) if on else (70, 72, 78, 255))
    save_tex(name, im)


# ====================================================================================================== detalles
def keys(name, w, h, base, key, rgb=False, on=False):
    im = new(16, 16, A(base))
    px = im.load()
    for y in range(h):
        for x in range(w):
            if y < h - 1 and x % 2 == 0 or y == h - 1 and 1 < x < w - 2:
                c = key
                if rgb:
                    c = [(255, 70, 70, 255), (255, 200, 60, 255), (80, 255, 120, 255), (70, 200, 255, 255), (190, 90, 255, 255)][x % 5] \
                        if on else (70, 72, 80, 255)
                px[x, y] = A(c)
    save_tex(name, im)


def mouse(name, base, seam, glow=None):
    im = new(16, 16, A(base))
    px = im.load()
    for y in range(0, 2):
        px[0, y] = A(seam)
        px[1, y] = A(seam)
    px[0, 0] = A(glow or seam)
    save_tex(name, im)


def tower_front_retro():
    im = fill_noise(new(16, 16, BEIGE), BEIGE, 2, 3120, box=(0, 0, 4, 13))
    rect(im, 0, 0, 0, 12, shade(BEIGE, -24))
    rect(im, 3, 0, 3, 12, shade(BEIGE, -24))
    rect(im, 1, 1, 2, 1, (60, 58, 52, 255))                               # bahía 5,25"
    rect(im, 1, 3, 2, 4, (40, 40, 44, 255))                                # lector de CD
    rect(im, 1, 4, 2, 4, (150, 150, 156, 255))
    rect(im, 1, 6, 2, 6, (20, 20, 24, 255))                                # disquetera
    rect(im, 2, 7, 2, 7, (90, 255, 140, 255))
    rect(im, 1, 10, 2, 11, shade(BEIGE, -44))                              # botón de encendido
    rect(im, 1, 10, 2, 10, shade(BEIGE, -10))
    return im


def tower_front_retro_on():
    im = tower_front_retro()
    rect(im, 1, 12, 1, 12, (90, 255, 140, 255))
    return im


def retro_side():
    im = fill_noise(new(16, 16, BEIGE_D), BEIGE_D, 2, 3121, box=(0, 0, 12, 13))
    frame(im, 0, 0, 11, 12, shade(BEIGE_D, -20))
    rect(im, 3, 4, 8, 8, shade(BEIGE_D, -30))                              # placa con el logo
    draw_text(im, "HS", 4, 5, (30, 30, 34, 255))
    return im


def compact_front(on):
    im = fill_noise(new(16, 16, CREAM), CREAM, 2, 3130, box=(0, 0, 9, 14))
    frame(im, 0, 0, 8, 13, shade(CREAM, -22))
    rect(im, 0, 0, 8, 0, shade(CREAM, 10))
    x0, y0, x1, y1 = 1, 1, 7, 7
    rect(im, x0 - 1, y0 - 1, x1 + 1, y1 + 1, shade(CREAM, -50))
    screen_mono(im, x0, y0, x1, y1, on)
    rect(im, 2, 10, 6, 10, (30, 30, 34, 255))                              # ranura del disquete
    rect(im, 7, 10, 7, 10, (90, 255, 140, 255) if on else (80, 80, 84, 255))
    rect(im, 2, 12, 4, 12, shade(CREAM, -60))                              # placa del logo
    return im


def amber_base_front(on):
    im = fill_noise(new(16, 16, CREAM), CREAM, 2, 3140, box=(0, 0, 15, 4))
    rect(im, 0, 3, 14, 3, shade(CREAM, -26))
    for x0 in (2, 8):
        rect(im, x0, 1, x0 + 4, 1, (28, 28, 32, 255))                      # dos disqueteras
        rect(im, x0 + 4, 2, x0 + 4, 2, (90, 255, 140, 255) if on and x0 == 2 else (90, 90, 94, 255))
    rect(im, 13, 1, 13, 2, shade(CREAM, -50))
    return im


def slim_foot():
    return fill_noise(new(16), ALU_D, 2, 3150)


def gaming_front():
    im = fill_noise(new(16, 16, BLACK), BLACK, 2, 3160, box=(0, 0, 5, 14))
    for y in range(1, 13, 2):
        for x in range(1, 4, 2):
            im.putpixel((x, y), A((14, 14, 18)))
    return im


def gaming_front_on():
    im = gaming_front()
    for y in range(14):
        im.putpixel((0, y), A(mix((255, 60, 200, 255), (0, 220, 255, 255), y / 13)))
    return im


def gaming_glass(on):
    im = fill_noise(new(16, 16, (24, 28, 34, 255)), (24, 28, 34, 255), 2, 3161, box=(0, 0, 12, 14))
    frame(im, 0, 0, 11, 13, (10, 10, 14, 255))
    ring = ((255, 70, 70), (80, 255, 120), (70, 200, 255))
    for i, cy in enumerate((3.5, 8.0)):
        disc(im, 6.0, cy, 2.4, A((26, 30, 38)))
        for a in range(8):
            import math
            x = int(6 + 2.0 * math.cos(a * math.pi / 4))
            y = int(cy + 2.0 * math.sin(a * math.pi / 4))
            if 0 <= x < 12 and 0 <= y < 14:
                im.putpixel((x, y), A(ring[(a + i) % 3] if on else (70, 74, 82)))
        im.putpixel((6, int(cy)), A((200, 205, 215)))
    for x in range(1, 11):
        im.putpixel((x, 11), A(mix((255, 60, 200, 255), (0, 220, 255, 255), x / 10) if on else (60, 64, 72, 255)))
    rect(im, 1, 12, 10, 12, (14, 14, 18, 255))
    return im


def minipc_front(on):
    im = fill_noise(new(16, 16, WHITE), WHITE, 2, 3170, box=(0, 0, 3, 3))
    rect(im, 0, 2, 2, 2, shade(WHITE, -30))
    rect(im, 0, 0, 2, 0, shade(WHITE, -12))
    im.putpixel((1, 1), A((90, 255, 140) if on else (110, 112, 118)))
    return im


def textures():
    materials()
    for st, on in (("on", True), ("off", False)):
        monitor_front(f"pc_retro_front_{st}", 10, 10, BEIGE, (1, 1, 8, 7), screen_win95, on, 3180, led=(8, 8))
        save_tex(f"pc_retro_tower_{st}", tower_front_retro_on() if on else tower_front_retro())
        save_tex(f"pc_compact_front_{st}", compact_front(on))
        monitor_front(f"pc_amber_front_{st}", 10, 8, CREAM, (1, 1, 8, 6), screen_amber, on, 3181, led=None)
        save_tex(f"pc_amber_base_{st}", amber_base_front(on))
        monitor_front(f"pc_slim_front_{st}", 14, 10, (20, 22, 26, 255), (1, 1, 12, 7), screen_slim, on, 3182, led=(7, 9))
        monitor_front(f"pc_gaming_screen_{st}", 10, 9, (14, 14, 18, 255), (1, 1, 8, 7), screen_gaming, on, 3183, led=(5, 8))
        save_tex(f"pc_gaming_tower_{st}", gaming_front_on() if on else gaming_front())
        save_tex(f"pc_gaming_glass_{st}", gaming_glass(on))
        monitor_front(f"pc_dual_left_{st}", 7, 8, (230, 232, 236, 255), (1, 1, 5, 6), screen_code, on, 3184)
        monitor_front(f"pc_dual_right_{st}", 7, 8, (230, 232, 236, 255), (1, 1, 5, 6), screen_hills, on, 3185)
        save_tex(f"pc_minipc_{st}", minipc_front(on))
        keys(f"pc_keys_rgb_{st}", 8, 4, (16, 16, 20, 255), (60, 62, 70, 255), rgb=True, on=on)
    save_tex("pc_retro_side", retro_side())
    keys("pc_keys_beige", 8, 3, BEIGE_D, shade(BEIGE, 28))
    keys("pc_keys_cream", 11, 3, CREAM_D, shade(CREAM, 24))
    keys("pc_keys_slim", 9, 3, ALU_D, (240, 242, 246, 255))
    keys("pc_keys_white", 9, 3, (200, 204, 210, 255), (250, 250, 252, 255))
    mouse("pc_mouse_beige", BEIGE, BEIGE_D)
    mouse("pc_mouse_black", (24, 24, 28, 255), (60, 62, 70, 255))
    mouse("pc_mouse_white", WHITE, ALU_D)
    mouse("pc_mouse_rgb", (24, 24, 28, 255), (60, 62, 70, 255))
    save_tex("pc_pad", fill_noise(new(16), (30, 26, 44, 255), 2, 3190))


# ====================================================================================================== modelos
def crop(w, h):
    return [0, 0, w, h]


def face(m, key, w, h):
    return (m.tex_key(key), crop(w, h)) if False else ("#" + key, crop(w, h))


def bx(m, frm, to, default, **faces):
    """Caja con textura general y caras con recorte explícito: north=("#clave", ancho, alto)."""
    spec = {}
    for fn, v in faces.items():
        spec[fn] = (v[0], crop(v[1], v[2])) if isinstance(v, tuple) and len(v) == 3 else v
    return m.box(frm, to, default, **spec)


def model_retro(st):
    m = Model()
    c, d, s = m.tex("case", "pc_beige"), m.tex("dark", "pc_beige_d"), m.tex("vent", "pc_vent")
    front = m.tex("front", f"pc_retro_front_{st}")
    tower, side = m.tex("tower", f"pc_retro_tower_{st}"), m.tex("side", "pc_retro_side")
    keys_, mouse_ = m.tex("keys", "pc_keys_beige"), m.tex("mouse", "pc_mouse_beige")
    bx(m, [11.5, 0, 2], [15.5, 13, 14], c, north=(tower, 4, 13), east=(side, 12, 13))                  # torre
    bx(m, [3, 0, 7], [8, 1.5, 12], d)                                                                  # pie del monitor
    bx(m, [0.5, 1.5, 4], [10.5, 11.5, 7], c, north=(front, 10, 10))                                    # monitor CRT: marco
    bx(m, [2, 3, 7], [9, 10, 13], d, up=s)                                                              # tubo trasero
    bx(m, [1, 0, 0.5], [9, 1, 3.5], c, up=(keys_, 8, 3))                                               # teclado
    bx(m, [9.8, 0, 1], [11.2, 0.8, 3], c, up=(mouse_, 1.4, 2))                                         # mouse
    return m


def model_compact(st):
    m = Model()
    c, d = m.tex("case", "pc_cream"), m.tex("dark", "pc_cream_d")
    front = m.tex("front", f"pc_compact_front_{st}")
    keys_, mouse_ = m.tex("keys", "pc_keys_cream"), m.tex("mouse", "pc_mouse_beige")
    bx(m, [2.5, 0, 4], [11.5, 14, 12.5], c, north=(front, 9, 14))
    bx(m, [3.5, 8, 12.5], [10.5, 13, 14.5], d)
    bx(m, [3.5, 14, 6], [10.5, 14.8, 11], d)                                                           # asa
    bx(m, [1.5, 0, 0.5], [10.5, 1, 3.5], c, up=(keys_, 9, 3))
    bx(m, [12, 0, 1.5], [13.4, 0.8, 3.5], c, up=(mouse_, 1.4, 2))
    return m


def model_amber(st):
    m = Model()
    c, d, s = m.tex("case", "pc_cream"), m.tex("dark", "pc_cream_d"), m.tex("vent", "pc_vent")
    base, front = m.tex("base", f"pc_amber_base_{st}"), m.tex("front", f"pc_amber_front_{st}")
    keys_ = m.tex("keys", "pc_keys_cream")
    bx(m, [0.5, 0, 3.5], [15.5, 4, 13.5], c, north=(base, 15, 4))
    bx(m, [2, 4, 6.5], [12, 12, 13], c, north=(front, 10, 8))
    bx(m, [3.5, 12, 8], [10.5, 13, 12], d, up=s)
    bx(m, [2, 0, 0.3], [13, 1.4, 3.3], c, up=(keys_, 11, 3))
    return m


def model_slim(st):
    m = Model()
    a, ad = m.tex("alu", "pc_alu"), m.tex("alud", "pc_alu_d")
    front = m.tex("front", f"pc_slim_front_{st}")
    keys_, mouse_ = m.tex("keys", "pc_keys_slim"), m.tex("mouse", "pc_mouse_white")
    bx(m, [1, 3.5, 6.8], [15, 13.5, 7.8], a, north=(front, 14, 10))
    bx(m, [7.3, 0.6, 7.0], [8.7, 3.5, 7.6], ad)
    bx(m, [5.5, 0, 5.5], [10.5, 0.6, 9.5], ad)
    bx(m, [3.5, 0, 1.5], [12.5, 0.6, 4.5], a, up=(keys_, 9, 3))
    bx(m, [13, 0, 2], [14.5, 0.7, 4], a, up=(mouse_, 1.5, 2))
    return m


def model_gaming(st):
    m = Model()
    k, v, pad = m.tex("black", "pc_black"), m.tex("vent", "pc_vent"), m.tex("pad", "pc_pad")
    tower, glass = m.tex("tower", f"pc_gaming_tower_{st}"), m.tex("glass", f"pc_gaming_glass_{st}")
    screen = m.tex("screen", f"pc_gaming_screen_{st}")
    keys_, mouse_ = m.tex("keys", f"pc_keys_rgb_{st}"), m.tex("mouse", "pc_mouse_rgb")
    bx(m, [10.5, 0, 2], [15.5, 14, 14], k, north=(tower, 5, 14), west=(glass, 12, 14), up=v)
    bx(m, [0, 4, 7.5], [10, 12.5, 8.5], k, north=(screen, 10, 8.5))
    bx(m, [4, 0.3, 8.0], [6, 4, 8.5], k)
    bx(m, [2.5, 0, 6.5], [7.5, 0.6, 10], k)
    bx(m, [0, 0, 0], [10.4, 0.3, 5.5], pad)
    bx(m, [0.4, 0.3, 0.9], [7.9, 1.2, 4.2], k, up=(keys_, 8, 4))
    bx(m, [8.4, 0.3, 1.5], [9.7, 1.1, 3.5], k, up=(mouse_, 1.3, 2))
    return m


def model_dual(st):
    m = Model()
    w, wd = m.tex("white", "pc_white"), m.tex("whited", "pc_alu_d")
    left, right = m.tex("left", f"pc_dual_left_{st}"), m.tex("right", f"pc_dual_right_{st}")
    mini = m.tex("mini", f"pc_minipc_{st}")
    keys_, mouse_ = m.tex("keys", "pc_keys_white"), m.tex("mouse", "pc_mouse_white")
    bx(m, [0.3, 4.5, 7.5], [7.7, 12.5, 8.5], w, north=(left, 7.4, 8))
    bx(m, [8.3, 4.5, 7.5], [15.7, 12.5, 8.5], w, north=(right, 7.4, 8))
    for x in (3.5, 11.5):
        bx(m, [x, 0.6, 7.8], [x + 1, 4.5, 8.3], wd)
        bx(m, [x - 1.5, 0, 6.5], [x + 2.5, 0.6, 9.5], wd)
    bx(m, [6.5, 0, 9.5], [9.5, 3, 12.5], w, north=(mini, 3, 3))
    bx(m, [3, 0, 1.2], [12, 0.6, 4.2], w, up=(keys_, 9, 3))
    bx(m, [12.8, 0, 2], [14.2, 0.7, 4], w, up=(mouse_, 1.4, 2))
    return m


MODELS_FOR = {"hs_desktop_retro": model_retro, "hs_desktop_compact": model_compact, "hs_desktop_amber": model_amber,
              "hs_desktop_slim": model_slim, "hs_desktop_gaming": model_gaming, "hs_desktop_dual": model_dual}

ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
}


def gen_blocks():
    for bid in IDS:
        for st in ("on", "off"):
            MODELS_FOR[bid](st).save(f"{bid}_{st}", "pc_beige" if bid != "hs_desktop_gaming" else "pc_black")
        variants = {}
        for facing, rot in FACINGS.items():
            for on in ("false", "true"):
                entry = {"model": f"beautifuldecor:block/{bid}_{'on' if on == 'true' else 'off'}"}
                if rot:
                    entry["y"] = rot
                variants[f"facing={facing},on={on}"] = entry
        write_json(STATES / f"{bid}.json", {"variants": variants})
        item = {"parent": f"beautifuldecor:block/{bid}_on", "display": ITEM_DISPLAY}
        write_json(ITEMS / f"{bid}.json", item)
        NEW_ITEMS[bid] = item
        write_loot(bid)
        en, es = NAMES[bid]
        for kind in ("block", "item"):
            LANG_EN[f"{kind}.beautifuldecor.{bid}"] = en
            LANG_ES[f"{kind}.beautifuldecor.{bid}"] = es


def merge_lang():
    for code, extra in (("en_us", LANG_EN), ("es_es", LANG_ES)):
        path = LANG / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(extra)
        write_json(path, data)


def register_icon_sources():
    path = ASSETS.parents[4] / "tools/v182/item_models_src.json"
    src = json.loads(path.read_text(encoding="utf-8"))
    src.update(NEW_ITEMS)
    path.write_text(json.dumps(src, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    textures()
    gen_blocks()
    merge_lang()
    register_icon_sources()
    print(f"2.3.0: {len(IDS)} computadores HS generados")


if __name__ == "__main__":
    main()
