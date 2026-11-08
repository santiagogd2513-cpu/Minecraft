"""Impresora HS (rediseño de la v1.8.0): solo cambian modelos y texturas; el mecanismo (PrinterBlock,
PrinterBlockEntity, sonido, papel impreso) no se toca. El id del bloque sigue siendo "printer"."""
import random

from .common import *

BODY = (216, 220, 226, 255)
DARK = (50, 53, 60, 255)
BLUE = (60, 170, 255, 255)
BLUE_D = (28, 96, 190, 255)
WHITE = (250, 250, 252, 255)


def tex_body():
    im = fill_noise(new(32), BODY, 3, 11, vgrad=10)
    save_tex("hs_pr_body", im)
    im = fill_noise(new(32), DARK, 3, 12, vgrad=6)
    save_tex("hs_pr_dark", im)
    # goma de los pies
    save_tex("hs_pr_rubber", fill_noise(new(16), (24, 25, 28, 255), 2, 13))


def tex_front():
    """Frente del cuerpo medio (región 28x6 px = 14x3 unidades): ranura de salida + insignia HS + LED."""
    im = fill_noise(new(32), BODY, 3, 21, vgrad=6)
    rect(im, 0, 0, 27, 0, shade(BODY, 14))               # brillo superior
    rect(im, 0, 5, 27, 5, shade(BODY, -26))              # sombra inferior
    # ranura de salida de papel
    rect(im, 7, 1, 24, 4, (22, 23, 27, 255))
    rect(im, 7, 1, 24, 1, (8, 8, 10, 255))
    rect(im, 7, 4, 24, 4, shade(BODY, -8))
    # insignia HS azul
    rect(im, 1, 1, 5, 4, BLUE_D)
    rect(im, 1, 1, 5, 1, BLUE)
    draw_text(im, "HS", 1, 0, WHITE) if False else None
    px = im.load()
    for (x, y) in [(1, 2), (1, 3), (3, 2), (3, 3), (5, 2), (5, 3)]:
        pass
    # "HS" en 2x3 px muy pequeño: H = 3 columnas, S = 2 columnas dentro de 5x4
    for (x, y) in [(1, 2), (3, 2), (1, 3), (2, 3), (3, 3), (1, 4), (3, 4)]:
        px[x, y] = WHITE
    for (x, y) in [(26, 2), (27, 2)]:
        px[x, y] = (90, 95, 105, 255)
    px[26, 3] = (70, 255, 140, 255)                      # LED de red
    save_tex("hs_pr_front", im)


def tex_drawer():
    im = fill_noise(new(32), DARK, 2, 31, vgrad=4)
    rect(im, 0, 0, 27, 0, shade(DARK, 22))
    rect(im, 0, 6, 27, 6, shade(DARK, -20))
    rect(im, 9, 2, 18, 3, (20, 21, 25, 255))             # hendidura del asa
    rect(im, 9, 2, 18, 2, (8, 8, 10, 255))
    rect(im, 9, 4, 18, 4, shade(DARK, 30))
    draw_text(im, "A4", 1, 1, (150, 156, 168, 255))
    px = im.load()
    px[25, 2] = (70, 255, 140, 255)
    px[25, 3] = (50, 190, 100, 255)
    save_tex("hs_pr_drawer", im)


def tex_top():
    """Cristal del escáner (región 26x11 px = 13x5.5 unidades) con logo HS en azul."""
    im = new(32)
    px = im.load()
    for y in range(32):
        for x in range(32):
            base = (30, 34, 44)
            band = 14 if 0 <= (x + y) % 22 < 4 else 0                # reflejo diagonal
            px[x, y] = shade(base + (255,), band + 4 * (y / 31))
    frame(im, 0, 0, 25, 10, (150, 156, 168, 255))                   # marco metálico
    frame(im, 1, 1, 24, 9, (20, 22, 28, 255))
    rect(im, 2, 2, 5, 2, (230, 232, 238, 255))                      # guía de página (esquina)
    rect(im, 2, 2, 2, 5, (230, 232, 238, 255))
    # logo HS (letras 3x5 a escala 1, brillantes) centrado abajo
    draw_text(im, "HS", 17, 3, BLUE, scale=1, gap=1)
    px[24, 3] = (0, 0, 0, 0) if False else px[24, 3]
    save_tex("hs_pr_top", im)


def tex_lid_front():
    """Frente/costado de la tapa: gris oscuro con filo claro (región 14x2.2 unidades)."""
    im = fill_noise(new(32), (74, 78, 88, 255), 2, 41, vgrad=8)
    rect(im, 0, 0, 31, 0, (150, 156, 168, 255))
    save_tex("hs_pr_lid", im)


def tex_panel(printing):
    """Panel de control inclinado (textura 64x64; región 52x14 px = 13x3.6 unidades): LCD + botones."""
    im = fill_noise(new(64), (36, 38, 44, 255), 2, 51)
    frame(im, 0, 0, 51, 13, (74, 78, 88, 255))
    rect(im, 1, 1, 50, 1, (56, 60, 68, 255))
    # LCD con bisel
    rect(im, 3, 2, 31, 11, (8, 10, 14, 255))
    lcd_bg = (24, 78, 130, 255) if not printing else (96, 76, 12, 255)
    rect(im, 4, 3, 30, 10, lcd_bg)
    col = (170, 238, 255, 255) if not printing else (255, 232, 120, 255)
    draw_text(im, "BUSY" if printing else "READY", 6, 4, col)
    # barra inferior de la pantalla (progreso o wifi)
    if printing:
        rect(im, 6, 9, 28, 9, shade(lcd_bg, -30))
        rect(im, 6, 9, 17, 9, col)
    else:
        for i in range(4):
            rect(im, 26 + i, 9 - i, 26 + i, 9, col)
    # botones y LEDs
    px = im.load()
    def button(cx, cy, c):
        disc(im, cx, cy, 2.2, shade(c, -45))
        disc(im, cx, cy - 0.3, 1.7, c)
    button(37, 7, (70, 255, 140, 255) if printing else (50, 200, 110, 255))
    button(43, 7, (255, 84, 84, 255))
    button(49 - 0, 7, (236, 239, 245, 255))
    px[36, 3] = (70, 255, 140, 255) if printing else (70, 80, 80, 255)    # LED de estado
    px[40, 3] = (90, 175, 255, 255)                                         # LED wifi
    save_tex("hs_pr_panel_on" if printing else "hs_pr_panel", im)


def tex_ink():
    """Ventana lateral con cartuchos C M Y K (región 6.6x2.6 unidades = 13x5 px)."""
    im = fill_noise(new(32), (14, 15, 18, 255), 2, 61)
    colors = [(0, 170, 220), (220, 40, 140), (240, 210, 20), (40, 40, 44)]
    for i, c in enumerate(colors):
        x0 = 1 + i * 3
        rect(im, x0, 0, x0 + 1, 4, c + (255,))
        rect(im, x0, 0, x0, 4, shade(c + (255,), 40))
        rect(im, x0, 4, x0 + 1, 4, shade(c + (255,), -50))
    frame(im, 0, 0, 12, 4, (90, 94, 104, 255)) if False else None
    save_tex("hs_pr_ink", im)


def tex_back():
    im = fill_noise(new(32), BODY, 3, 71, vgrad=8)
    rect(im, 2, 1, 7, 4, (24, 25, 29, 255))               # toma de corriente
    rect(im, 3, 2, 6, 3, (60, 62, 68, 255))
    rect(im, 10, 2, 12, 4, (30, 31, 35, 255))             # USB
    rect(im, 14, 2, 17, 4, (30, 31, 35, 255))             # red
    px = im.load()
    px[15, 2] = (80, 255, 140, 255)
    for x in range(20, 27, 2):                            # rejilla de ventilación
        rect(im, x, 1, x, 4, (130, 135, 146, 255))
    save_tex("hs_pr_back", im)


def tex_paper():
    im = fill_noise(new(32), (246, 246, 248, 255), 2, 81)
    save_tex("hs_pr_paper", im)
    im = fill_noise(new(32), (246, 246, 248, 255), 2, 82)
    rect(im, 1, 1, 9, 2, BLUE)                            # encabezado azul
    for y in range(4, 28, 2):
        rect(im, 1, y, 22 if y % 6 else 15, y, (70, 74, 86, 255))
    save_tex("hs_pr_paper_printed", im)


def tex_tray():
    save_tex("hs_pr_tray", fill_noise(new(32), (196, 200, 208, 255), 3, 91))


def build(printing):
    m = Model()
    body = m.tex("body", "hs_pr_body")
    dark = m.tex("dark", "hs_pr_dark")
    rub = m.tex("rubber", "hs_pr_rubber")
    front = m.tex("front", "hs_pr_front")
    drawer = m.tex("drawer", "hs_pr_drawer")
    top = m.tex("top", "hs_pr_top")
    lid = m.tex("lid", "hs_pr_lid")
    panel = m.tex("panel", "hs_pr_panel_on" if printing else "hs_pr_panel")
    ink = m.tex("ink", "hs_pr_ink")
    back = m.tex("back", "hs_pr_back")
    tray = m.tex("tray", "hs_pr_tray")
    paper = m.tex("paper", "hs_pr_paper_printed" if printing else "hs_pr_paper")

    # --- pies de goma
    for x in (2, 12.5):
        for z in (3.5, 11):
            m.box([x, 0, z], [x + 1.5, 0.5, z + 1.5], rub)
    # --- cajón de papel inferior (oscuro)
    m.box([1, 0.5, 3], [15, 3.7, 13], dark,
          north=(drawer, [0, 0, 14, 3.2]), south=(dark, [0, 0, 14, 3.2]))
    # --- cuerpo medio (gris claro)
    m.box([1, 3.7, 3], [15, 6.7, 13], body,
          north=(front, [0, 0, 14, 3]), south=(back, [0, 0, 14, 3]))
    # --- bandeja de salida delantera
    m.box([3, 3.9, 1.2], [13, 4.25, 3], tray, uv={"up": [0, 0, 10, 1.8]})
    m.box([3, 4.25, 1.2], [3.4, 4.9, 3], tray)                      # bordes de la bandeja
    m.box([12.6, 4.25, 1.2], [13, 4.9, 3], tray)
    if printing:
        m.box([4, 4.25, 0.2], [12, 4.4, 3.1], paper, uv={"up": [8, 2.9, 0, 0]})   # hoja saliendo
    # --- panel de control inclinado
    m.box([1.5, 6.7, 3], [14.5, 7.5, 6.6], dark,
          up=(panel, [13, 3.6, 0, 0]), north=dark,
          rot={"origin": [8, 6.7, 3], "axis": "x", "angle": -22.5})
    # --- tapa del escáner
    m.box([1, 6.7, 6.6], [15, 8.9, 13], lid,
          up=body, north=(lid, [0, 0, 14, 2.2]), south=(lid, [0, 0, 14, 2.2]),
          east=(lid, [0, 0, 6.4, 2.2]), west=(lid, [0, 0, 6.4, 2.2]), down=None)
    m.box([1.5, 8.9, 7.1], [14.5, 9.3, 12.5], dark,
          up=(top, [13, 5.4, 0, 0]))
    # --- ventana de tintas (lado oeste)
    m.box([0.4, 3.9, 4.2], [1, 6.5, 10.8], dark, west=(ink, [0, 0, 6.6, 2.6]), up=dark, down=dark,
          north=dark, south=dark)
    m.box([0.6, 3.7, 4.0], [1, 3.9, 11.0], dark)                    # repisa inferior de la ventana
    return m


def generate():
    tex_body()
    tex_front()
    tex_drawer()
    tex_top()
    tex_lid_front()
    tex_panel(False)
    tex_panel(True)
    tex_ink()
    tex_back()
    tex_paper()
    tex_tray()
    build(False).save("printer", "hs_pr_body")
    build(True).save("printer_printing", "hs_pr_body")
    # mismo blockstate: facing x printing (el mecanismo Java no cambia)
    variants = {}
    for facing, rot in FACINGS.items():
        for printing in ("false", "true"):
            v = {"model": "beautifuldecor:block/printer_printing" if printing == "true"
                 else "beautifuldecor:block/printer"}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},printing={printing}"] = v
    write_json(STATES / "printer.json", {"variants": variants})
    write_json(ITEMS / "printer.json", {"parent": "beautifuldecor:block/printer"})
    LANG_ES["block.beautifuldecor.printer"] = "Impresora HS"
    LANG_ES["item.beautifuldecor.printer"] = "Impresora HS"
    LANG_EN["block.beautifuldecor.printer"] = "HS Printer"
    LANG_EN["item.beautifuldecor.printer"] = "HS Printer"


if __name__ == "__main__":
    generate()
