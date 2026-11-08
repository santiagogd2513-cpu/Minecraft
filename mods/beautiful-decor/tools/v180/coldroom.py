"""Cámara frigorífica (cold_room): estructura de 3 de ancho x 3 de alto x 3 de fondo (27 piezas).

Convención (igual que el televisor grande): el bloque donde haces clic es la esquina inferior IZQUIERDA del frente;
la cámara se extiende hacia tu derecha, hacia arriba y hacia el fondo. part = px + 3*py + 9*pz.
Los modelos se crean mirando al norte (frente hacia -z); px crece hacia -x y pz crece hacia +z.
Solo se entra por la puerta (px=1, pz=0, py=0 y 1). La pieza (1,2,1) lleva el bombillo del techo.
"""
import random

from .common import *

T = 2.0                       # grosor de paredes, piso y techo (px)
DOOR_PARTS = (1, 4)           # part de la puerta baja y alta
LIGHT_PART = 16               # px=1, py=2, pz=1
DISPLAY_PART = 2 + 3 * 1 + 9 * 0   # px=2, py=1, pz=0 (a la derecha de la puerta)
EVAP_PART = 1 + 3 * 2 + 9 * 2      # px=1, py=2, pz=2 (techo, fondo)


# ------------------------------------------------------------------ texturas
def panel_seams(im, dark, light, step=16):
    px = im.load()
    for x in range(0, im.width, step):
        for y in range(im.height):
            px[x, y] = shade(px[x, y], dark)
            if x + 1 < im.width:
                px[x + 1, y] = shade(px[x + 1, y], light)


def textures():
    rng = random.Random(201)
    # exterior de panel aislante
    im = fill_noise(new(32), (234, 238, 242, 255), 2, 201)
    panel_seams(im, -34, 8)
    save_tex("cr_out", im)
    # interior escarchado
    im = fill_noise(new(32), (206, 224, 238, 255), 3, 202)
    px = im.load()
    for _ in range(120):
        px[rng.randrange(32), rng.randrange(32)] = (244, 250, 255, 255)
    for _ in range(70):
        px[rng.randrange(32), rng.randrange(32)] = (172, 202, 226, 255)
    panel_seams(im, -30, 6)
    save_tex("cr_in", im)
    # piso de aluminio antideslizante
    im = fill_noise(new(32), (150, 156, 166, 255), 3, 203)
    px = im.load()
    for gy in range(0, 32, 4):
        for gx in range(0, 32, 4):
            if ((gx + gy) // 4) % 2 == 0:
                for d in range(3):
                    px[gx + d, gy + d] = (206, 212, 220, 255)
            else:
                for d in range(3):
                    px[gx + 2 - d, gy + d] = (206, 212, 220, 255)
    save_tex("cr_floor_in", im)
    # cielo raso blanco con paneles
    im = fill_noise(new(32), (240, 243, 247, 255), 2, 204)
    panel_seams(im, -26, 6)
    for y in (0, 16):
        for x in range(32):
            im.putpixel((x, y), shade(im.getpixel((x, y)), -26))
    save_tex("cr_ceiling_in", im)
    # acero inoxidable cepillado (esquinas, cornisa, marcos)
    im = new(32)
    px = im.load()
    for y in range(32):
        row = rng.randint(-5, 5)
        for x in range(32):
            px[x, y] = shade((182, 188, 196, 255), row + rng.randint(-2, 2))
    save_tex("cr_trim", im)
    # techo exterior galvanizado con nervaduras
    im = fill_noise(new(32), (150, 156, 166, 255), 3, 205)
    px = im.load()
    for x in range(0, 32, 4):
        for y in range(32):
            px[x, y] = shade(px[x, y], -24)
            px[x + 1, y] = shade(px[x + 1, y], 16)
    save_tex("cr_roof", im)
    # zócalo / junta de goma
    save_tex("cr_kick", fill_noise(new(32), (62, 66, 74, 255), 3, 206))
    save_tex("cr_bar", fill_noise(new(16), (60, 64, 70, 255), 2, 207))
    save_tex("cr_chrome", fill_noise(new(16), (212, 217, 224, 255), 4, 208))
    save_tex("cr_knob", fill_noise(new(16), (204, 36, 36, 255), 3, 209))

    # hoja de puerta: exterior inferior (región 26x28 px) y superior (26x30 px)
    def door_out(h, upper):
        im = fill_noise(new(32), (238, 241, 245, 255), 2, 210 + h)
        frame(im, 0, 0, 25, h - 1, (194, 200, 208, 255))
        rect(im, 3, 3, 22, h - 4, (226, 231, 237, 255))                 # panel hundido
        rect(im, 3, 3, 22, 3, (200, 206, 214, 255))
        rect(im, 3, 3, 3, h - 4, (200, 206, 214, 255))
        rect(im, 22, 3, 22, h - 4, (248, 250, 252, 255))
        rect(im, 3, h - 4, 22, h - 4, (248, 250, 252, 255))
        pxx = im.load()
        for y in (4, h - 7):                                             # bisagras al lado derecho de la textura = este
            rect(im, 0, y, 1, y + 3, (110, 116, 126, 255))
        if upper:
            cx, cy = 11, 10                                              # termómetro de dial
            disc(im, cx, cy, 5.6, (76, 80, 90, 255))
            disc(im, cx, cy, 4.6, (250, 250, 252, 255))
            for ang in range(-60, 241, 30):
                import math
                x = cx + 3.8 * math.cos(math.radians(ang))
                y = cy + 3.8 * math.sin(math.radians(ang))
                pxx[int(x), int(y)] = (60, 120, 220, 255) if ang < 100 else (210, 50, 50, 255)
            for t in range(0, 4):
                pxx[cx - t, cy - t] = (214, 40, 40, 255)                 # aguja
            draw_text(im, "FRIO", 6, 18, (52, 100, 170, 255))
        else:
            rect(im, 0, h - 3, 25, h - 1, (170, 176, 186, 255))          # guardapiés
        return im

    save_tex("cr_door_low_out", door_out(28, False))
    save_tex("cr_door_up_out", door_out(30, True))

    def door_in(h, seed):
        im = fill_noise(new(32), (206, 224, 238, 255), 3, seed)
        pxx = im.load()
        for _ in range(60):
            pxx[rng.randrange(26), rng.randrange(h)] = (244, 250, 255, 255)
        frame(im, 0, 0, 25, h - 1, (34, 36, 42, 255))
        frame(im, 1, 1, 24, h - 2, (34, 36, 42, 255))
        return im

    save_tex("cr_door_in_low", door_in(28, 215))
    save_tex("cr_door_in_up", door_in(30, 216))

    # termostato digital de pared (región 14x12 px = 7x6 unidades)
    im = fill_noise(new(32), (40, 44, 52, 255), 2, 220)
    frame(im, 0, 0, 13, 11, (96, 102, 112, 255))
    rect(im, 1, 1, 12, 7, (16, 18, 20, 255))
    draw_text(im, "-20", 2, 2, (255, 70, 60, 255))
    px = im.load()
    px[11, 2] = (255, 70, 60, 255)                                       # grado
    rect(im, 2, 9, 3, 10, (90, 255, 140, 255))
    rect(im, 5, 9, 6, 10, (230, 232, 238, 255))
    rect(im, 8, 9, 9, 10, (230, 232, 238, 255))
    save_tex("cr_display", im)

    # bombillo encendido
    im = new(16)
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            px[x, y] = (255, 255 - int(d * 2), 232 - int(d * 8), 255)
    save_tex("cr_bulb_on", im)

    # evaporador: fondo con dos ventiladores y laterales con rejilla
    im = fill_noise(new(32), (206, 211, 218, 255), 3, 221)
    for cx in (8, 24):
        disc(im, cx, 16, 6.5, (70, 74, 82, 255))
        disc(im, cx, 16, 5.6, (34, 36, 42, 255))
        for k in range(-5, 6):
            px = im.load()
            px[cx + k, 16 + k // 2] = (96, 102, 112, 255)
            px[cx - k // 2, 16 + k] = (96, 102, 112, 255)
        disc(im, cx, 16, 1.2, (150, 156, 166, 255))
    frame(im, 0, 0, 31, 31, (130, 136, 146, 255))
    save_tex("cr_evap", im)
    im = fill_noise(new(16), (214, 219, 226, 255), 2, 222)
    for y in range(2, 14, 2):
        rect(im, 1, y, 14, y, (120, 126, 136, 255))
    save_tex("cr_evap_side", im)


# ------------------------------------------------------------------ geometría por pieza
def exists(px, py, pz):
    return 0 <= px <= 2 and 0 <= py <= 2 and 0 <= pz <= 2


class PartBuilder:
    def __init__(self, px, py, pz):
        self.px, self.py, self.pz = px, py, pz
        self.m = Model()
        for key, name in [("out", "cr_out"), ("in", "cr_in"), ("floor", "cr_floor_in"), ("ceil", "cr_ceiling_in"),
                          ("trim", "cr_trim"), ("roof", "cr_roof"), ("kick", "cr_kick"), ("bar", "cr_bar"),
                          ("chrome", "cr_chrome"), ("knob", "cr_knob"), ("bulb", "cr_bulb_on"),
                          ("dlow", "cr_door_low_out"), ("dup", "cr_door_up_out"), ("dinl", "cr_door_in_low"),
                          ("dinu", "cr_door_in_up"), ("disp", "cr_display"), ("evap", "cr_evap"),
                          ("evaps", "cr_evap_side")]:
            self.m.tex(key, name)

    def neighbor(self, face):
        d = {"east": (-1, 0, 0), "west": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
             "south": (0, 0, 1), "north": (0, 0, -1)}[face]
        return exists(self.px + d[0], self.py + d[1], self.pz + d[2])

    def box(self, frm, to, **faces):
        """Como Model.box, pero omite caras pegadas al borde del bloque cuando hay otra pieza al otro lado."""
        for fn in list(faces):
            if faces[fn] is None:
                continue
            at_edge = {"east": to[0] == 16, "west": frm[0] == 0, "up": to[1] == 16, "down": frm[1] == 0,
                       "south": to[2] == 16, "north": frm[2] == 0}[fn]
            if at_edge and self.neighbor(fn):
                faces[fn] = None
        for fn in FACE_NAMES:
            faces.setdefault(fn, None)
        self.m.box(frm, to, None, **faces)


def build_part(part, open_):
    px, rem = part % 3, part // 3
    py, pz = rem % 3, rem // 3
    b = PartBuilder(px, py, pz)
    floor_, ceil_ = py == 0, py == 2
    left, right = px == 0, px == 2
    front, back = pz == 0, pz == 2
    door = px == 1 and pz == 0 and py <= 1
    y0 = T if floor_ else 0.0
    y1 = 16 - T if ceil_ else 16.0
    zf = T if front else 0.0
    zb = 16 - T if back else 16.0

    if floor_:
        b.box([0, 0, 0], [16, T, 16], up="#floor", down="#kick", north="#kick", south="#kick", east="#kick",
              west="#kick")
    if ceil_:
        b.box([0, 16 - T, 0], [16, 16, 16], up="#roof", down="#ceil", north="#trim", south="#trim", east="#trim",
              west="#trim")
    if left:      # lado +x
        b.box([16 - T, y0, zf], [16, y1, zb], east="#out", west="#in")
    if right:     # lado -x
        b.box([0, y0, zf], [T, y1, zb], west="#out", east="#in")
    if back:
        b.box([0, y0, 16 - T], [16, y1, 16], south="#out", north="#in", east="#trim", west="#trim")
    if front and not door:
        b.box([0, y0, 0], [16, y1, T], north="#out", south="#in", east="#trim", west="#trim")
    if front and part == DISPLAY_PART:
        b.box([4.5, 4, -0.7], [11.5, 10, 0], north=("#disp", [0, 0, 7, 6]), south="#trim", east="#trim",
              west="#trim", up="#trim", down="#trim")

    if door:
        lower = py == 0
        ya = T if lower else 0.0
        yb = 16.0 if lower else 15.0
        h = yb - ya
        b.box([0, y0, 0], [1.5, 16, T], north="#trim", south="#trim", east="#trim", west="#trim")
        b.box([14.5, y0, 0], [16, 16, T], north="#trim", south="#trim", east="#trim", west="#trim")
        if not lower:
            b.box([1.5, 15, 0], [14.5, 16, T], north="#trim", south="#trim", down="#trim")
        out_tex = "#dlow" if lower else "#dup"
        in_tex = "#dinl" if lower else "#dinu"
        if not open_:
            b.box([1.5, ya, -0.6], [14.5, yb, 3], north=(out_tex, [0, 0, 13, h]), south=(in_tex, [0, 0, 13, h]),
                  east="#kick", west="#kick", up=None, down=None)
            if lower:
                b.box([2.6, 4.5, -1.9], [4.0, 11.5, -0.6], north="#chrome", south="#chrome", east="#chrome",
                      west="#chrome", up="#chrome", down="#chrome")
                b.box([2.8, 5, -0.6], [3.8, 6, -0.4], up="#chrome", north="#chrome")
                b.box([2.8, 10, -0.6], [3.8, 11, -0.4], up="#chrome", north="#chrome")
                b.box([2.8, 8.5, 3], [4.4, 10.1, 4.6], north="#knob", south="#knob", east="#knob", west="#knob",
                      up="#knob", down="#knob")
        else:
            # hoja abierta hacia afuera: bisagra en el lado este (x=14.5)
            b.box([10.9, ya, -13.6], [14.5, yb, -0.6], east=(out_tex, [0, 0, 13, h]), west=(in_tex, [0, 0, 13, h]),
                  north="#kick", south="#kick", up=None, down=None)
            if lower:
                b.box([15.0, 4.5, -13.0], [15.9, 11.5, -11.6], east="#chrome", north="#chrome", south="#chrome",
                      up="#chrome", down="#chrome")
                b.box([14.5, 5, -12.7], [15.0, 6, -11.9], east="#chrome", up="#chrome")
                b.box([14.5, 10, -12.7], [15.0, 11, -11.9], east="#chrome", up="#chrome")
                b.box([9.3, 8.5, -12.8], [10.9, 10.1, -11.2], west="#knob", north="#knob", south="#knob",
                      up="#knob", down="#knob")

    if part == LIGHT_PART:
        b.box([6, 13.4, 6], [10, 14, 10], down="#bar", north="#bar", south="#bar", east="#bar", west="#bar")
        b.box([6.9, 9.4, 6.9], [9.1, 13.4, 9.1], north="#bulb", south="#bulb", east="#bulb", west="#bulb",
              down="#bulb")
        for (cx, cz) in [(6.2, 6.2), (9.3, 6.2), (6.2, 9.3), (9.3, 9.3)]:
            b.box([cx, 9.2, cz], [cx + 0.5, 13.4, cz + 0.5], north="#bar", south="#bar", east="#bar", west="#bar")
        b.box([6.2, 8.9, 6.2], [9.8, 9.4, 9.8], north="#bar", south="#bar", east="#bar", west="#bar", down="#bar",
              up="#bar")
    if part == EVAP_PART:
        b.box([2, 8.5, 10], [14, 14, 14], down=("#evap", [0, 0, 12, 4]), north="#trim", east="#evaps",
              west="#evaps", south="#trim")
    return b.m


def item_model():
    m = Model()
    out = m.tex("out", "cr_out")
    kick = m.tex("kick", "cr_kick")
    trim = m.tex("trim", "cr_trim")
    roof = m.tex("roof", "cr_roof")
    dlow = m.tex("dup", "cr_door_up_out")
    chrome = m.tex("chrome", "cr_chrome")
    disp = m.tex("disp", "cr_display")
    evap = m.tex("evap", "cr_evap")
    m.box([0.5, 0, 1.5], [15.5, 1.2, 14.5], kick)
    m.box([0.5, 1.2, 1.5], [15.5, 12.8, 14.5], out)
    m.box([0.3, 12.8, 1.3], [15.7, 13.8, 14.7], trim, up=roof)
    m.box([3, 13.8, 4], [13, 15.6, 12], trim, up=roof, north=evap)
    m.box([4.2, 1.2, 1.0], [10.8, 11.2, 1.5], out, north=(dlow, [0, 0, 13, 15]))
    m.box([4.4, 4, 0.4], [5.2, 8.5, 1.0], chrome)
    m.box([11.6, 5.2, 1.0], [14.6, 8.2, 1.5], out, north=(disp, [0, 0, 7, 6]))
    m.save("cold_room_item", "cr_out")


def generate():
    textures()
    for part in range(27):
        build_part(part, False).save(f"cold_room_p{part}", "cr_out")
    for part in DOOR_PARTS:
        build_part(part, True).save(f"cold_room_p{part}_open", "cr_out")
    item_model()
    variants = {}
    for facing, rot in FACINGS.items():
        for part in range(27):
            for open_ in ("false", "true"):
                suffix = "_open" if (open_ == "true" and part in DOOR_PARTS) else ""
                v = {"model": f"beautifuldecor:block/cold_room_p{part}{suffix}"}
                if rot:
                    v["y"] = rot
                variants[f"facing={facing},open={open_},part={part}"] = v
    write_json(STATES / "cold_room.json", {"variants": variants})
    write_item_model("cold_room", "cold_room_item")
    names("cold_room", "Walk-in Cold Room", "Cámara frigorífica")


if __name__ == "__main__":
    generate()
