"""Cuatro cajas para la cámara frigorífica: vacía, papas fritas congeladas, costillas de cerdo y pelotas de colores."""
import random

from .common import *

CARD = (198, 154, 102, 255)
TAPE = (226, 196, 140, 255)

BALL_COLORS = {
    "red": (226, 52, 56), "blue": (50, 110, 230), "yellow": (250, 210, 40),
    "green": (60, 190, 80), "pink": (240, 100, 180), "orange": (250, 140, 30),
    "cyan": (60, 210, 230), "purple": (150, 80, 220),
}


def cardboard(name, base=CARD, seed=1):
    im = fill_noise(new(16), base, 3, seed, vgrad=6)
    px = im.load()
    for y in range(1, 16, 4):                       # corrugado muy leve
        for x in range(16):
            px[x, y] = shade(px[x, y], -8)
    save_tex(name, im)


def snowflake(im, x, y, color):
    draw_pattern(im, ["1001001", "0101010", "0011100", "1111111", "0011100", "0101010", "1001001"], x, y, color)


def label_side():
    """Costado de la caja: etiqueta blanca con -18C, copo de nieve y código de barras (región 24x24 px)."""
    im = fill_noise(new(32), CARD, 3, 7, vgrad=6)
    rect(im, 3, 3, 20, 20, (244, 244, 246, 255))
    frame(im, 3, 3, 20, 20, (170, 170, 176, 255))
    snowflake(im, 5, 5, (60, 130, 220, 255))
    for i, x in enumerate(range(14, 19)):
        rect(im, x, 5, x, 11, (30, 30, 34, 255) if i % 2 == 0 else (120, 120, 126, 255))
    draw_text(im, "-18C", 4, 13, (30, 60, 140, 255))
    save_tex("crate_label", im)


def print_fries():
    """Frente impreso (región 56x48 px): "PAPAS FRITAS" congeladas."""
    im = new(64, 64, (130, 196, 238, 255))
    rect(im, 0, 0, 55, 11, (26, 66, 150, 255))
    draw_text(im, "PAPAS", 8, 1, (255, 255, 255, 255), scale=2)
    sticks = [(15, 15, 26), (19, 13, 26), (23, 16, 26), (27, 12, 26), (31, 15, 26), (35, 13, 26), (39, 16, 26)]
    for x, top, bottom in sticks:
        rect(im, x, top, x + 3, bottom, (250, 206, 64, 255))
        rect(im, x, top, x, bottom, (255, 232, 130, 255))
        rect(im, x + 3, top, x + 3, bottom, (214, 150, 30, 255))
        rect(im, x, top, x + 3, top, (255, 240, 170, 255))
    for y in range(23, 33):                                   # cartón rojo (trapecio)
        inset = (y - 23) // 3
        rect(im, 12 + inset, y, 43 - inset, y, (206, 40, 44, 255))
    rect(im, 12, 23, 43, 24, (232, 64, 66, 255))
    rect(im, 21, 26, 34, 30, (255, 244, 240, 255))
    snowflake(im, 24, 25, (60, 130, 220, 255)) if False else None
    draw_text(im, "FRITAS", 17, 35, (26, 66, 150, 255))
    snowflake(im, 3, 17, (255, 255, 255, 255))
    snowflake(im, 46, 17, (255, 255, 255, 255))
    rect(im, 0, 41, 55, 47, (26, 66, 150, 255))
    draw_text(im, "CONGELADO", 10, 42, (255, 255, 255, 255))
    frame(im, 0, 0, 55, 47, (16, 40, 100, 255))
    save_tex("crate_print_fries", im)


def print_ribs():
    """Frente impreso (región 56x48 px): "COSTILLAS DE CERDO"."""
    im = new(64, 64, (240, 190, 182, 255))
    rect(im, 0, 0, 55, 11, (128, 24, 36, 255))
    draw_text(im, "COSTILLAS", 10, 3, (255, 244, 230, 255))
    # costillar: carne + huesos
    rect(im, 10, 16, 45, 28, (176, 66, 48, 255))
    rect(im, 10, 16, 45, 17, (214, 104, 76, 255))
    rect(im, 10, 28, 45, 28, (120, 40, 32, 255))
    for x in range(14, 45, 6):
        rect(im, x, 18, x, 27, (126, 44, 34, 255))         # surcos entre costillas
        rect(im, x - 2, 29, x + 1, 36, (248, 236, 214, 255))  # hueso
        rect(im, x - 2, 29, x - 2, 36, (255, 250, 238, 255))
        rect(im, x + 1, 29, x + 1, 36, (206, 188, 160, 255))
        rect(im, x - 2, 36, x + 1, 36, (206, 188, 160, 255))
    for x in range(12, 44, 5):
        px = im.load()
        px[x, 19] = (240, 150, 120, 255)                   # brillo del glaseado
    rect(im, 0, 42, 55, 47, (128, 24, 36, 255))
    draw_text(im, "DE CERDO", 12, 43, (255, 244, 230, 255))
    draw_text(im, "-18C", 40, 38, (128, 24, 36, 255)) if False else None
    frame(im, 0, 0, 55, 47, (86, 14, 22, 255))
    save_tex("crate_print_ribs", im)


def top_tape():
    im = fill_noise(new(32), CARD, 3, 17, vgrad=-4)
    rect(im, 0, 15, 27, 15, shade(CARD, -30))               # unión de las solapas
    rect(im, 11, 0, 16, 27, TAPE)                           # cinta de embalar
    rect(im, 11, 0, 11, 27, shade(TAPE, 16))
    rect(im, 16, 0, 16, 27, shade(TAPE, -26))
    save_tex("crate_top_tape", im)


def plastic_textures():
    red, dark = (200, 48, 52, 255), (132, 28, 32, 255)
    im = fill_noise(new(32), red, 3, 23, vgrad=10)
    for y0 in (4, 17):                                      # ranuras horizontales
        rect(im, 3, y0, 24, y0 + 3, dark)
        rect(im, 3, y0, 24, y0, shade(dark, -34))
    for x in range(2, 26, 6):
        rect(im, x, 0, x, 27, shade(red, -26))              # costillas verticales
    rect(im, 8, 9, 19, 11, dark)                            # agarradera
    rect(im, 0, 27, 27, 27, shade(red, -40))
    save_tex("crate_plastic_side", im)
    save_tex("crate_plastic_in", fill_noise(new(16), dark, 3, 24))
    save_tex("crate_plastic_rim", fill_noise(new(16), (230, 80, 84, 255), 3, 25))


def balls_top():
    """Pelotas vistas desde arriba (región 24x24 px)."""
    rng = random.Random(5)
    im = new(32, 32, (46, 24, 24, 255))
    names_ = list(BALL_COLORS)
    r = 3.0
    row = 0
    y = 3.0
    while y < 25:
        x = 3.0 + (3.0 if row % 2 else 0.0)
        while x < 25:
            c = BALL_COLORS[rng.choice(names_)] + (255,)
            disc(im, x, y, r, shade(c, -46))
            disc(im, x - 0.4, y - 0.4, r - 0.8, c)
            disc(im, x - 1.1, y - 1.1, 1.0, shade(c, 80))
            x += 6.0
        y += 5.2
        row += 1
    save_tex("crate_balls_top", im)


def ball_textures():
    for name, c in BALL_COLORS.items():
        im = new(16)
        c4 = c + (255,)
        disc(im, 8, 8, 7.4, shade(c4, -50))
        disc(im, 7.6, 7.6, 6.4, c4)
        disc(im, 6, 5.8, 2.4, shade(c4, 55))
        disc(im, 5.6, 5.4, 1.1, (255, 255, 255, 255))
        save_tex(f"crate_ball_{name}", im)


def textures():
    cardboard("crate_card", CARD, 31)
    cardboard("crate_card_in", (150, 112, 70, 255), 32)
    label_side()
    print_fries()
    print_ribs()
    top_tape()
    plastic_textures()
    balls_top()
    ball_textures()


def closed_box(printed, name):
    m = Model()
    card = m.tex("card", "crate_card")
    prt = m.tex("print", printed)
    top = m.tex("top", "crate_top_tape")
    lab = m.tex("label", "crate_label")
    m.box([1, 0, 1], [15, 12, 15], card,
          north=(prt, [0, 0, 14, 12]), south=(prt, [0, 0, 14, 12]),
          east=(lab, [0, 0, 12, 12]), west=(lab, [0, 0, 12, 12]), up=(top, [0, 0, 14, 14]))
    m.box([0.8, 10.6, 0.8], [15.2, 11, 15.2], card, up=None, down=None)          # borde de las solapas
    m.save(name, "crate_card")


def empty_box():
    m = Model()
    card = m.tex("card", "crate_card")
    inn = m.tex("inn", "crate_card_in")
    m.box([1, 0, 1], [15, 1, 15], card, up=inn)
    m.box([1, 1, 1], [15, 12, 2], card, south=inn, up=card)
    m.box([1, 1, 14], [15, 12, 15], card, north=inn, up=card)
    m.box([1, 1, 2], [2, 12, 14], card, east=inn, up=card)
    m.box([14, 1, 2], [15, 12, 14], card, west=inn, up=card)
    # solapas abiertas hacia afuera
    m.box([1, 12, 0.6], [15, 17, 1.4], card, south=inn,
          rot={"origin": [8, 12, 1], "axis": "x", "angle": -45})
    m.box([1, 12, 14.6], [15, 17, 15.4], card, north=inn,
          rot={"origin": [8, 12, 15], "axis": "x", "angle": 45})
    m.box([0.6, 12, 2], [1.4, 17, 14], card, east=inn,
          rot={"origin": [1, 12, 8], "axis": "z", "angle": 45})
    m.box([14.6, 12, 2], [15.4, 17, 14], card, west=inn,
          rot={"origin": [15, 12, 8], "axis": "z", "angle": -45})
    m.save("crate_empty", "crate_card")


def balls_box():
    m = Model(render_type="minecraft:cutout")
    side = m.tex("side", "crate_plastic_side")
    inn = m.tex("inn", "crate_plastic_in")
    rim = m.tex("rim", "crate_plastic_rim")
    top = m.tex("balls", "crate_balls_top")
    full = [0, 0, 14, 12]
    m.box([1, 0, 1], [15, 1, 15], inn, down=inn)
    m.box([1, 1, 1], [15, 12, 2.2], side, north=(side, full), south=inn, up=rim)
    m.box([1, 1, 12.8], [15, 12, 15], side, south=(side, full), north=inn, up=rim)
    m.box([1, 1, 2.2], [2.2, 12, 12.8], side, west=(side, [0, 0, 10.6, 12]), east=inn, up=rim)
    m.box([12.8, 1, 2.2], [15, 12, 12.8], side, east=(side, [0, 0, 10.6, 12]), west=inn, up=rim)
    m.box([2.2, 1, 2.2], [12.8, 11, 12.8], inn, up=(top, [0, 0, 12, 12]))
    # pelotas que sobresalen (cubitos con círculo sombreado; sin esquinas por el recorte)
    rng = random.Random(9)
    colors = list(BALL_COLORS)
    spots = [(3.3, 3.6), (6.9, 3.2), (10.2, 4.0), (4.4, 7.4), (8.3, 7.0), (11.0, 8.4), (3.6, 10.4), (7.4, 10.6), (10.6, 11.2)]
    for i, (x, z) in enumerate(spots):
        c = colors[(i * 3) % len(colors)] if False else colors[[0, 4, 1, 5, 2, 6, 3, 7, 0][i % 9]]
        key = m.tex(f"b{i}", f"crate_ball_{c}")
        y0 = 10.6 + (0.6 if i % 3 == 0 else 0)
        cx, cz = x + 2.6, z + 2.6
        m.box([cx - 1.5, y0, cz - 1.5], [cx + 1.5, y0 + 3, cz + 1.5], key)
        m.box([cx - 1.5, y0, cz - 1.5], [cx + 1.5, y0 + 3, cz + 1.5], key,
              rot={"origin": [cx, y0 + 1.5, cz], "axis": "y", "angle": 45})
    m.save("crate_balls", "crate_plastic_side")


def generate():
    textures()
    closed_box("crate_print_fries", "crate_fries")
    closed_box("crate_print_ribs", "crate_ribs")
    empty_box()
    balls_box()
    for bid, en, es in [
        ("crate_empty", "Empty Crate", "Caja vacía"),
        ("crate_fries", "Box of Frozen French Fries", "Caja de papas fritas congeladas"),
        ("crate_ribs", "Box of Pork Ribs", "Caja de costillas de cerdo"),
        ("crate_balls", "Crate of Colored Balls", "Caja de pelotas de colores"),
    ]:
        facing_state(bid, lambda e, b=bid: b)
        write_item_model(bid)
        names(bid, en, es)


if __name__ == "__main__":
    generate()
