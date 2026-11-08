#!/usr/bin/env python3
"""Nintendo Switch OLED (Joy-Con neon) para BeautifulDecor.
Genera models/block/nintendo_switch.json y las texturas switch_*.png (64x64, misma resolucion de siempre).
La consola queda en modo sobremesa: tablet inclinada 22.5 grados apoyada en su soporte trasero ancho."""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'beautifuldecor')
TEX = os.path.join(ROOT, 'textures', 'block')
MODEL = os.path.join(ROOT, 'models', 'block', 'nintendo_switch.json')
random.seed(7)

BACK, BEZEL, RAIL, BUTTON = (46, 49, 54), (13, 14, 17), (22, 24, 28), (72, 77, 84)
BLUE, RED = (10, 188, 232), (255, 64, 44)

def clamp(v):
    return max(0, min(255, int(v)))

def solid(c, amt=2):
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 255))
    px = im.load()
    for y in range(64):
        for x in range(64):
            n = random.randint(-amt, amt)
            px[x, y] = (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), 255)
    return im

# ---------------- texturas ----------------
LEFT = dict(x0=0.4, stick=(1.45, 4.5), dpad=(1.5, 2.0), minus=(2.0, 5.85), capture=(1.9, 0.75))
RIGHT = dict(x0=13.5, stick=(14.25, 2.35), abxy=(14.8, 4.5), plus=(14.15, 5.85), home=(14.1, 0.95))

def controls(cfg, base):
    im = solid(base, 2)
    d = ImageDraw.Draw(im)
    P = lambda c: ((c[0] - cfg['x0']) * 10, (6.4 - c[1]) * 10)
    def disc(c, r, fill):
        x, y = P(c)
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill)
    def btn(c):
        disc(c, 2.9, (22, 24, 28))
        disc(c, 2.5, (44, 47, 52))
        x, y = P(c)
        d.point((x - 1, y - 1), fill=(96, 102, 110))
    disc(cfg['stick'], 6.2, (20, 22, 26))
    disc(cfg['stick'], 4.9, (56, 60, 66))
    disc(cfg['stick'], 3.7, (34, 37, 41))
    sx, sy = P(cfg['stick'])
    d.point((sx - 1, sy - 1), fill=(90, 96, 104))
    if 'dpad' in cfg:
        cx, cy = cfg['dpad']
        for dx, dy in ((0, .5), (0, -.5), (-.5, 0), (.5, 0)):
            btn((cx + dx, cy + dy))
        x, y = P(cfg['minus'])
        d.rectangle([x - 2, y - 0.5, x + 2, y + 0.5], fill=(22, 24, 28))
        x, y = P(cfg['capture'])
        d.rectangle([x - 2.5, y - 2.5, x + 2.5, y + 2.5], fill=(22, 24, 28))
        d.rectangle([x - 1.2, y - 1.2, x + 1.2, y + 1.2], fill=(52, 56, 62))
    else:
        cx, cy = cfg['abxy']
        for dx, dy in ((0, .5), (0, -.5), (-.5, 0), (.5, 0)):
            btn((cx + dx, cy + dy))
        x, y = P(cfg['plus'])
        d.rectangle([x - 2.2, y - 0.6, x + 2.2, y + 0.6], fill=(22, 24, 28))
        d.rectangle([x - 0.6, y - 2.2, x + 0.6, y + 2.2], fill=(22, 24, 28))
        disc(cfg['home'], 2.9, (22, 24, 28))
        disc(cfg['home'], 1.7, (60, 64, 70))
    edge = tuple(int(v * 0.8) for v in base) + (255,)
    d.rectangle([0, 0, 20, 63], outline=edge)
    return im

def screen():
    im = Image.new('RGBA', (64, 64), (6, 7, 10, 255))
    px = im.load()
    for y in range(36):
        for x in range(64):
            v = 3 + int(5 * y / 36)
            g = 6 if abs((x - y * 1.3) - 42) < 4 else 0   # brillo diagonal
            px[x, y] = (clamp(6 + v + g), clamp(8 + v + g), clamp(14 + v + g), 255)
    d = ImageDraw.Draw(im)
    d.ellipse([2, 1, 7, 6], fill=(205, 208, 220))                      # avatar
    d.rectangle([29, 2, 35, 4], fill=(180, 184, 196))                  # hora
    d.rectangle([55, 2, 60, 5], outline=(230, 232, 240))               # bateria
    d.rectangle([56, 3, 58, 4], fill=(230, 232, 240))
    for i, h in enumerate((1, 2, 3)):                                  # wifi
        d.rectangle([46 + i * 3, 5 - h, 47 + i * 3, 5], fill=(200, 204, 214))
    tiles = [((222, 52, 52), 'c'), ((52, 112, 224), 't'), ((240, 202, 44), 'r'), ((62, 182, 92), 'x'), ((170, 90, 210), 'c')]
    for i, (col, shape) in enumerate(tiles):
        x0 = 4 + i * 14
        d.rectangle([x0, 11, x0 + 11, 22], fill=col)
        d.rectangle([x0, 11, x0 + 11, 12], fill=tuple(min(255, v + 40) for v in col))
        cx, cy = x0 + 5.5, 17
        if shape == 'c':
            d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(255, 255, 255))
        elif shape == 't':
            d.polygon([(cx - 3, cy + 3), (cx + 3, cy + 3), (cx, cy - 3)], fill=(255, 255, 255))
        elif shape == 'r':
            d.rectangle([cx - 3, cy - 2, cx + 3, cy + 2], fill=(255, 255, 255))
        else:
            d.line([cx - 3, cy - 3, cx + 3, cy + 3], fill=(255, 255, 255))
            d.line([cx + 3, cy - 3, cx - 3, cy + 3], fill=(255, 255, 255))
    d.rectangle([3, 10, 16, 23], outline=(255, 255, 255))              # tile seleccionado
    for i, col in enumerate(((235, 235, 240), (255, 130, 40), (200, 205, 215), (200, 205, 215), (235, 235, 240), (200, 205, 215))):
        cx = 7 + i * 10
        d.ellipse([cx - 3, 26, cx + 3, 32], fill=col)
        d.ellipse([cx - 1, 28, cx + 1, 30], fill=(40, 44, 52))
    return im

def make_textures():
    solid(BACK, 2).save(os.path.join(TEX, 'switch_back.png'))
    solid(BEZEL, 1).save(os.path.join(TEX, 'switch_bezel.png'))
    solid(RAIL, 1).save(os.path.join(TEX, 'switch_rail.png'))
    solid(BUTTON, 2).save(os.path.join(TEX, 'switch_button.png'))
    solid(BLUE, 2).save(os.path.join(TEX, 'switch_joycon_blue.png'))
    solid(RED, 2).save(os.path.join(TEX, 'switch_joycon_red.png'))
    controls(LEFT, BLUE).save(os.path.join(TEX, 'switch_joycon_left_controls.png'))
    controls(RIGHT, RED).save(os.path.join(TEX, 'switch_joycon_right_controls.png'))
    screen().save(os.path.join(TEX, 'switch_screen.png'))

# ---------------- modelo ----------------
DZ = -1.0                      # centra la consola en el bloque
ZJ0, ZJB = 6.5, 7.7            # frente / fondo Joy-Con
ZT0, ZTB = 6.62, 7.55          # frente / fondo tablet
TILT_A = 22.5
TILT = {'origin': [8, 0, ZJB + DZ], 'axis': 'x', 'angle': TILT_A}
E = []

def r(v):
    return round(v, 4)

def faces(default, **over):
    out = {}
    for f in ('north', 'east', 'south', 'west', 'up', 'down'):
        spec = over.get(f, default)
        tex, uv = spec if isinstance(spec, tuple) else (spec, None)
        d = {'texture': '#' + tex}
        if uv:
            d['uv'] = uv
        out[f] = d
    return out

def box(u1, u2, y1, y2, z1, z2, fc, rot=None):
    """u = coordenada vista desde el frente (0 = izquierda del jugador)."""
    el = {'from': [r(16 - u2), r(y1), r(z1 + DZ)], 'to': [r(16 - u1), r(y2), r(z2 + DZ)], 'faces': fc}
    if rot:
        el['rotation'] = rot
    return el

def add(*a):
    E.append(box(*a))

def octa(cx, cy, w, t, tex):
    for ww, hh in ((w, .62 * w), (.62 * w, w), (.88 * w, .88 * w)):
        add(cx - ww / 2, cx + ww / 2, cy - hh / 2, cy + hh / 2, ZJ0 - t, ZJ0, faces(tex), TILT)

def bump(cx, cy, w, t, tex):
    add(cx - w / 2, cx + w / 2, cy - w / 2, cy + w / 2, ZJ0 - t, ZJ0, faces(tex), TILT)

def joycon(side):
    cfg = LEFT if side == 'L' else RIGHT
    if side == 'L':
        a, b, ca, cb, body, front = 0.4, 2.5, 0.55, 2.5, 'blue', 'left_controls'
    else:
        a, b, ca, cb, body, front = 13.5, 15.6, 13.5, 15.45, 'red', 'right_controls'
    add(a, b, 0.25, 6.15, ZJ0, ZJB, faces(body, north=(front, [0, 0.625, 5.25, 15.375])), TILT)
    add(ca, cb, 6.15, 6.4, ZJ0, ZJB, faces(body), TILT)           # esquinas redondeadas
    add(ca, cb, 0.0, 0.25, ZJ0, ZJB, faces(body), TILT)
    octa(*cfg['stick'], 1.1, 0.06, 'rail')                         # palanca: base
    octa(*cfg['stick'], 0.72, 0.24, 'button')                      # palanca: tapa
    cx, cy = cfg['dpad'] if side == 'L' else cfg['abxy']
    for dx, dy in ((0, .5), (0, -.5), (-.5, 0), (.5, 0)):
        bump(cx + dx, cy + dy, 0.44, 0.1, 'button')                # cruceta / ABXY
    if side == 'L':
        add(1.8, 2.2, 5.8, 5.9, ZJ0 - .08, ZJ0, faces('rail'), TILT)  # menos
        bump(*cfg['capture'], 0.38, 0.08, 'rail')                  # captura
        bump(*cfg['capture'], 0.2, 0.11, 'button')
        sh = (0.6, 2.4); tr = (0.7, 2.3)
    else:
        px_, py_ = cfg['plus']                                     # mas
        add(px_ - .25, px_ + .25, py_ - .065, py_ + .065, ZJ0 - .08, ZJ0, faces('rail'), TILT)
        add(px_ - .065, px_ + .065, py_ - .25, py_ + .25, ZJ0 - .08, ZJ0, faces('rail'), TILT)
        bump(*cfg['home'], 0.42, 0.08, 'rail')                     # home
        bump(*cfg['home'], 0.26, 0.11, 'button')
        sh = (13.7, 15.5); tr = (13.8, 15.4)
    add(sh[0], sh[1], 6.4, 6.7, 6.9, 7.5, faces(body), TILT)       # boton L / R
    add(tr[0], tr[1], 4.6, 6.35, ZJB, ZJB + .25, faces('rail'), TILT)  # gatillo ZL / ZR

def build():
    joycon('L'); joycon('R')
    # rieles entre tablet y Joy-Con
    add(2.5, 2.6, 0.25, 6.15, ZJ0, ZTB, faces('rail'), TILT)
    add(13.4, 13.5, 0.25, 6.15, ZJ0, ZTB, faces('rail'), TILT)
    # tablet + pantalla OLED (bordes finos)
    add(2.6, 13.4, 0.0, 6.4, ZT0, ZTB, faces('back', north='bezel'), TILT)
    add(3.0, 13.0, 0.39, 6.015, ZT0 - .04, ZT0, faces('bezel', north=('screen', [0, 0, 16, 9])), TILT)
    # arriba: encendido, volumen, ranura de juegos, auriculares
    add(3.1, 4.0, 6.4, 6.52, 6.85, 7.3, faces('button'), TILT)
    add(4.3, 4.9, 6.4, 6.52, 6.85, 7.3, faces('button'), TILT)
    add(5.1, 5.7, 6.4, 6.52, 6.85, 7.3, faces('button'), TILT)
    add(10.2, 11.8, 6.4, 6.47, 6.85, 7.3, faces('rail'), TILT)
    add(12.4, 12.8, 6.4, 6.45, 6.95, 7.2, faces('rail'), TILT)
    # rejillas traseras y bisagra del soporte
    add(4.4, 12.6, 5.55, 5.65, ZTB, ZTB + .03, faces('rail'), TILT)
    add(4.4, 12.6, 5.85, 5.95, ZTB, ZTB + .03, faces('rail'), TILT)
    add(3.0, 13.0, 4.55, 4.95, ZTB, ZTB + .3, faces('rail'), TILT)
    # soporte ancho del OLED, abierto y apoyado en la mesa
    a = math.radians(TILT_A)
    pz = ZJB
    ry, rz = 4.75, ZTB + .3 - pz
    hy = ry * math.cos(a) - rz * math.sin(a)
    hz = pz + ry * math.sin(a) + rz * math.cos(a)
    L = hy / math.cos(a) - 0.06
    rot = {'origin': [8, r(hy), r(hz + DZ)], 'axis': 'x', 'angle': -22.5}
    add(2.95, 13.05, hy - L, hy, hz - .14, hz + .14, faces('back'), rot)
    add(2.95, 13.05, hy - L, hy - L + .3, hz - .2, hz + .2, faces('rail'), rot)   # pie de goma

def bounds():
    xs, ys, zs = [], [], []
    for e in E:
        rot = e.get('rotation')
        for x in (e['from'][0], e['to'][0]):
            for y in (e['from'][1], e['to'][1]):
                for z in (e['from'][2], e['to'][2]):
                    if rot:
                        oy, oz = rot['origin'][1], rot['origin'][2]
                        t = math.radians(rot['angle'])
                        ry_, rz_ = y - oy, z - oz
                        y, z = oy + ry_ * math.cos(t) - rz_ * math.sin(t), oz + ry_ * math.sin(t) + rz_ * math.cos(t)
                    xs.append(x); ys.append(y); zs.append(z)
    return (min(xs), max(xs)), (min(ys), max(ys)), (min(zs), max(zs))

def main():
    make_textures()
    build()
    textures = {'particle': 'switch_back', 'back': 'switch_back', 'bezel': 'switch_bezel', 'screen': 'switch_screen',
                'blue': 'switch_joycon_blue', 'red': 'switch_joycon_red', 'rail': 'switch_rail',
                'left_controls': 'switch_joycon_left_controls', 'right_controls': 'switch_joycon_right_controls',
                'button': 'switch_button'}
    textures = {k: 'beautifuldecor:block/' + v for k, v in textures.items()}
    with open(MODEL, 'w') as f:
        f.write('{\n "parent": "minecraft:block/block",\n "textures": ' + json.dumps(textures) + ',\n "elements": [\n')
        f.write(',\n'.join('  ' + json.dumps(e, separators=(',', ':')) for e in E))
        f.write('\n ]\n}\n')
    print(len(E), 'elementos', bounds())

if __name__ == '__main__':
    main()
