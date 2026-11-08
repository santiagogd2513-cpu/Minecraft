"""Congelador horizontal (freezer_chest): usa ApplianceDoorBlock (facing + open) -> la tapa se levanta con clic derecho."""
import random

from .common import *

WHITE = (238, 241, 245, 255)
GASKET = (44, 47, 54, 255)


def textures():
    save_tex("fz_side", fill_noise(new(16), WHITE, 2, 101, vgrad=8))
    save_tex("fz_rim", fill_noise(new(16), (222, 226, 232, 255), 2, 102))
    save_tex("fz_handle", fill_noise(new(16), (204, 209, 216, 255), 3, 103))
    save_tex("fz_leg", fill_noise(new(16), (40, 42, 48, 255), 2, 104))

    # frente (región 32x20 px = 16x10 unidades): panel de control a la izquierda, placa a la derecha
    im = fill_noise(new(32), WHITE, 2, 105, vgrad=8)
    rect(im, 0, 0, 31, 0, shade(WHITE, -30))                  # sombra bajo la tapa
    rect(im, 0, 19, 31, 19, shade(WHITE, -34))
    rect(im, 1, 3, 15, 16, (60, 66, 76, 255))                 # panel de control hundido
    rect(im, 1, 3, 15, 3, (30, 33, 40, 255))
    rect(im, 2, 4, 14, 15, (30, 34, 42, 255))
    px = im.load()
    disc(im, 8, 7.4, 2.7, (176, 182, 192, 255))               # perilla del termostato
    disc(im, 8, 7.4, 1.9, (226, 230, 236, 255))
    px[8, 5] = (200, 40, 40, 255)                             # marca de la perilla
    px[8, 6] = (200, 40, 40, 255)
    px[3, 5] = (255, 70, 60, 255)                             # LED rojo
    px[4, 5] = (255, 70, 60, 255)
    draw_text(im, "-18", 3, 10, (140, 225, 255, 255))
    # placa de marca con copo de nieve a la derecha
    rect(im, 20, 4, 29, 14, (44, 100, 190, 255))
    rect(im, 20, 4, 29, 4, (90, 160, 240, 255))
    flake = ["1001001", "0101010", "0011100", "1111111", "0011100", "0101010", "1001001"]
    draw_pattern(im, flake, 21, 6, WHITE)
    save_tex("fz_front", im)

    # zócalo con rejilla de ventilación (región 30x3 px)
    im = fill_noise(new(32), (58, 62, 70, 255), 2, 106)
    for x in range(4, 28, 2):
        rect(im, x, 1, x, 4, (22, 24, 28, 255))
    save_tex("fz_base", im)

    # tapa cerrada vista desde arriba (región 32x28 px = 16x14 unidades)
    im = fill_noise(new(32), WHITE, 2, 107, vgrad=-6)
    frame(im, 1, 1, 30, 26, shade(WHITE, -22))
    frame(im, 2, 2, 29, 25, shade(WHITE, 10))
    draw_text(im, "FREEZER", 3, 3, (96, 150, 220, 255))
    save_tex("fz_lid_top", im)

    # interior de la tapa abierta (región 32x28 px): espuma aislante con junta oscura
    im = fill_noise(new(32), (232, 236, 240, 255), 2, 108)
    frame(im, 0, 0, 31, 27, GASKET)
    frame(im, 1, 1, 30, 26, GASKET)
    frame(im, 3, 3, 28, 24, (206, 212, 220, 255))
    save_tex("fz_lid_inner", im)

    # paredes interiores con escarcha
    rng = random.Random(109)
    im = fill_noise(new(16), (206, 226, 240, 255), 3, 109)
    px = im.load()
    for _ in range(46):
        px[rng.randrange(16), rng.randrange(16)] = (246, 250, 255, 255)
    for _ in range(26):
        px[rng.randrange(16), rng.randrange(16)] = (170, 200, 224, 255)
    save_tex("fz_inner", im)

    # contenido visto desde arriba (región 26x22 px = 13x11 unidades): bolsas de papas, costillas, ice
    im = fill_noise(new(32), (214, 232, 244, 255), 3, 110)
    px = im.load()
    packs = [
        ((1, 1, 9, 7), (240, 196, 54), (255, 226, 120)),   # papas fritas
        ((10, 1, 17, 9), (204, 70, 78), (236, 120, 126)),  # costillas
        ((18, 1, 25, 6), (74, 156, 78), (126, 206, 124)),  # verduras
        ((1, 8, 8, 14), (204, 70, 78), (236, 120, 126)),
        ((9, 10, 16, 16), (240, 196, 54), (255, 226, 120)),
        ((17, 7, 25, 13), (74, 130, 210), (130, 180, 240)), # bolsa azul
        ((1, 15, 10, 21), (74, 156, 78), (126, 206, 124)),
        ((17, 14, 25, 21), (240, 196, 54), (255, 226, 120)),
        ((11, 17, 16, 21), (204, 70, 78), (236, 120, 126)),
    ]
    for (x0, y0, x1, y1), base, hi in packs:
        rect(im, x0, y0, x1, y1, base + (255,))
        rect(im, x0, y0, x1, y0, hi + (255,))
        rect(im, x0, y0, x0, y1, hi + (255,))
        rect(im, x0, y1, x1, y1, shade(base + (255,), -50))
        rect(im, x1, y0, x1, y1, shade(base + (255,), -50))
        rect(im, x0 + 1, (y0 + y1) // 2, x1 - 1, (y0 + y1) // 2, shade(base + (255,), -26))
    for _ in range(60):
        px[rng.randrange(26), rng.randrange(22)] = (250, 252, 255, 255)
    save_tex("fz_contents", im)


def build(open_):
    m = Model()
    side = m.tex("side", "fz_side")
    front = m.tex("front", "fz_front")
    base = m.tex("base", "fz_base")
    leg = m.tex("leg", "fz_leg")
    lid_top = m.tex("lid_top", "fz_lid_top")
    lid_in = m.tex("lid_in", "fz_lid_inner")
    inner = m.tex("inner", "fz_inner")
    rim = m.tex("rim", "fz_rim")
    handle = m.tex("handle", "fz_handle")
    cont = m.tex("cont", "fz_contents")

    for x in (1, 13):
        for z in (2, 12):
            m.box([x, 0, z], [x + 2, 0.6, z + 2], leg)                       # patitas
    m.box([0.6, 0.6, 1.6], [15.4, 2, 14.4], base,
          north=(base, [0, 0, 14.8, 1.4]), up=base, down=base)               # zócalo

    if not open_:
        m.box([0, 2, 1], [16, 12, 15], side, north=(front, [0, 0, 16, 10]))   # cuerpo
        m.box([0.5, 11.8, 1.5], [15.5, 12.2, 14.5], GASKET_TEX(m))            # junta
        m.box([0, 12.2, 1], [16, 14.2, 15], side, up=(lid_top, [16, 14, 0, 0]))  # tapa cerrada
        m.box([4, 12.4, 0.3], [12, 13.4, 1], handle)                          # asa
        m.box([4.2, 12.2, 0.6], [4.8, 13.2, 1], handle)
        m.box([11.2, 12.2, 0.6], [11.8, 13.2, 1], handle)
    else:
        # paredes del cuerpo (huecas)
        m.box([0, 2, 1], [16, 12, 2.5], side, north=(front, [0, 0, 16, 10]), south=inner, up=rim)
        m.box([0, 2, 13.5], [16, 12, 15], side, north=inner, up=rim)
        m.box([0, 2, 2.5], [1.5, 12, 13.5], side, east=inner, up=rim)
        m.box([14.5, 2, 2.5], [16, 12, 13.5], side, west=inner, up=rim)
        m.box([1.5, 2, 2.5], [14.5, 3.5, 13.5], inner, up=inner)
        m.box([1.5, 3.5, 2.5], [14.5, 10.5, 13.5], inner, up=(cont, [0, 0, 13, 11]))   # contenido
        # tapa levantada contra el fondo (se ve su cara interior hacia el frente)
        m.box([0, 12, 12.6], [16, 26, 14.8], side, north=(lid_in, [0, 0, 16, 14]),
              south=(lid_top, [0, 0, 16, 14]))
        m.box([0.4, 12, 14.8], [3, 14, 15.4], leg)                            # bisagras
        m.box([13, 12, 14.8], [15.6, 14, 15.4], leg)
    return m


def GASKET_TEX(m):
    return m.tex("gasket", "fz_gasket")


def generate():
    textures()
    save_tex("fz_gasket", fill_noise(new(16), GASKET, 2, 111))
    build(False).save("freezer_chest", "fz_side")
    build(True).save("freezer_chest_open", "fz_side")
    facing_state("freezer_chest",
                 lambda e: "freezer_chest_open" if e["open"] == "true" else "freezer_chest",
                 [{"open": "false"}, {"open": "true"}])
    write_item_model("freezer_chest")
    names("freezer_chest", "Chest Freezer", "Congelador horizontal")


if __name__ == "__main__":
    generate()
