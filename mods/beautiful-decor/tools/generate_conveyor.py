#!/usr/bin/env python3
"""Cinta transportadora (V2.6.0): texturas (cinta animada de 8 cuadros, marco metalico, icono), modelos recto/esquina izquierda/esquina
derecha, estados de bloque, botin, receta y textos es/en.

Medidas en pixeles de bloque (x este, z sur). La cinta recta mira al norte: marco de 0 a 3, superficie de la cinta a 4, rieles
laterales (2 px) hasta 5. Las esquinas parten la superficie en una zona de entrada (rotada) y otra de salida.
"""
import json
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'src', 'main')
ASSETS = os.path.join(SRC, 'resources', 'assets', 'beautifuldecor')
DATA = os.path.join(SRC, 'resources', 'data', 'beautifuldecor')
random.seed(26)
NS = 'beautifuldecor'
PERIOD = 8


def clamp(v):
    return max(0, min(255, int(v)))


def belt_texture():
    """16 de ancho x 8 cuadros de 16: el dibujo sube (hacia arriba de la imagen) un pixel por cuadro."""
    base = []
    for r in range(PERIOD):
        row = []
        for x in range(16):
            if r in (0, 1):                      # listón claro
                c = (88, 88, 96) if r == 0 else (74, 74, 82)
            elif r == 2:
                c = (30, 30, 34)                 # sombra bajo el listón
            else:
                c = (44, 44, 49)
            n = random.randint(-3, 3)
            edge = -10 if x in (0, 15) else (-5 if x in (1, 14) else 0)
            row.append(tuple(clamp(v + n + edge) for v in c))
        base.append(row)
    im = Image.new('RGBA', (16, 16 * PERIOD))
    px = im.load()
    for k in range(PERIOD):
        for y in range(16):
            for x in range(16):
                px[x, k * 16 + y] = base[(y + k) % PERIOD][x] + (255,)
    im.save(os.path.join(ASSETS, 'textures', 'block', 'conveyor_belt.png'))
    with open(os.path.join(ASSETS, 'textures', 'block', 'conveyor_belt.png.mcmeta'), 'w') as f:
        json.dump({'animation': {'frametime': 1}}, f, indent=1)


def frame_texture():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            n = random.randint(-4, 4) + (y % 4 == 0 and 3 or 0)
            px[x, y] = tuple(clamp(v + n) for v in (122, 128, 138)) + (255,)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(70, 74, 82, 255))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):                      # remaches
        d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(168, 172, 180, 255))
        d.point((x + 1, y + 1), fill=(60, 64, 70, 255))
    d.rectangle([0, 7, 15, 8], fill=(98, 103, 112, 255))                     # franja amarilla de aviso
    for x in range(0, 16, 4):
        d.rectangle([x, 7, x + 1, 8], fill=(214, 170, 40, 255))
    im.save(os.path.join(ASSETS, 'textures', 'block', 'conveyor_frame.png'))


def icon():
    """Icono de 32x32: tramo de cinta en perspectiva."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # cara lateral del marco
    d.polygon([(2, 20), (14, 27), (14, 22), (2, 15)], fill=(104, 109, 118, 255), outline=(60, 64, 72, 255))
    d.polygon([(14, 27), (30, 15), (30, 10), (14, 22)], fill=(138, 144, 154, 255), outline=(60, 64, 72, 255))
    # superficie de la cinta
    d.polygon([(2, 15), (14, 22), (30, 10), (18, 3)], fill=(46, 46, 52, 255), outline=(70, 74, 82, 255))
    # rieles
    d.line([(2, 15), (18, 3)], fill=(178, 184, 192, 255), width=2)
    d.line([(14, 22), (30, 10)], fill=(178, 184, 192, 255), width=2)
    # listones
    for t in (0.2, 0.4, 0.6, 0.8):
        ax, ay = 2 + (18 - 2) * t, 15 + (3 - 15) * t
        bx, by = 14 + (30 - 14) * t, 22 + (10 - 22) * t
        d.line([(ax + 1, ay + 1), (bx - 1, by - 1)], fill=(92, 92, 100, 255), width=1)
    im.save(os.path.join(ASSETS, 'textures', 'item', 'conveyor_belt.png'))


def box(frm, to, faces):
    return {'from': frm, 'to': to, 'faces': faces}


def metal(frm, to, rail=False):
    """Pieza de marco: lados con solo la franja de textura que les toca (sin deformar)."""
    full = {'texture': '#frame', 'uv': [0, 0, 16, 16]}
    faces = {f: dict(full) for f in ('north', 'east', 'south', 'west', 'up', 'down')}
    h = 2 if rail else 3
    faces['north']['uv'] = [0, 0, frm_len(frm, to, 'x'), h]
    faces['south']['uv'] = [0, 0, frm_len(frm, to, 'x'), h]
    faces['east']['uv'] = [0, 0, frm_len(frm, to, 'z'), h]
    faces['west']['uv'] = [0, 0, frm_len(frm, to, 'z'), h]
    faces['up']['uv'] = [0, 0, frm_len(frm, to, 'x'), frm_len(frm, to, 'z')]
    faces['down']['uv'] = [0, 0, frm_len(frm, to, 'x'), frm_len(frm, to, 'z')]
    return box(frm, to, faces)


def frm_len(frm, to, axis):
    i = 0 if axis == 'x' else 2
    return to[i] - frm[i]


def belt(frm, to, up_uv, rotation=0, ends=()):
    """Superficie de la cinta (1 px de alto). Solo la cara de arriba y los extremos abiertos."""
    up = {'texture': '#belt', 'uv': up_uv}
    if rotation:
        up['rotation'] = rotation
    faces = {'up': up}
    for f in ends:
        faces[f] = {'texture': '#belt', 'uv': [2, 0, 14, 1]}
    return box(frm, to, faces)


def model(elements):
    return {'parent': 'minecraft:block/block',
            'textures': {'particle': NS + ':block/conveyor_frame', 'frame': NS + ':block/conveyor_frame', 'belt': NS + ':block/conveyor_belt'},
            'elements': elements}


def straight():
    return model([
        metal([0, 0, 0], [16, 3, 16]),
        metal([0, 3, 0], [2, 5, 16], True),
        metal([14, 3, 0], [16, 5, 16], True),
        belt([2, 3, 0], [14, 4, 16], [2, 0, 14, 16], 0, ('north', 'south')),
    ])


def curve_left():
    # la cinta que alimenta esta a la izquierda (oeste): entra por el oeste, avanza al este y sale por el norte
    return model([
        metal([0, 0, 0], [16, 3, 16]),
        metal([0, 3, 14], [16, 5, 16], True),          # riel sur (exterior)
        metal([14, 3, 0], [16, 5, 14], True),          # riel este (exterior)
        metal([0, 3, 0], [2, 5, 2], True),             # poste interior noroeste
        belt([0, 3, 2], [8, 4, 14], [2, 0, 14, 8], 90, ('west',)),      # entrada: avanza hacia el este
        belt([8, 3, 0], [14, 4, 14], [8, 0, 14, 14], 0, ('north',)),    # salida: hacia el norte
        belt([2, 3, 0], [8, 4, 2], [2, 0, 8, 2], 0, ('north',)),
    ])


def curve_right():
    # espejo: entra por el este, avanza al oeste y sale por el norte
    return model([
        metal([0, 0, 0], [16, 3, 16]),
        metal([0, 3, 14], [16, 5, 16], True),
        metal([0, 3, 0], [2, 5, 14], True),
        metal([14, 3, 0], [16, 5, 2], True),
        belt([8, 3, 2], [16, 4, 14], [2, 0, 14, 8], 270, ('east',)),
        belt([2, 3, 0], [8, 4, 14], [2, 0, 8, 14], 0, ('north',)),
        belt([8, 3, 0], [14, 4, 2], [8, 0, 14, 2], 0, ('north',)),
    ])


def write_json(path, obj):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=1)
        f.write('\n')


def models():
    for name, m in (('conveyor_straight', straight()), ('conveyor_curve_left', curve_left()), ('conveyor_curve_right', curve_right())):
        os.makedirs(os.path.join(ASSETS, 'models', 'block'), exist_ok=True)
        with open(os.path.join(ASSETS, 'models', 'block', name + '.json'), 'w') as f:
            f.write('{\n "parent": "%s",\n "textures": %s,\n "elements": [\n' % (m['parent'], json.dumps(m['textures'])))
            f.write(',\n'.join('  ' + json.dumps(e, separators=(',', ':')) for e in m['elements']))
            f.write('\n ]\n}\n')
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for shape, mdl in (('straight', 'conveyor_straight'), ('curve_left', 'conveyor_curve_left'), ('curve_right', 'conveyor_curve_right')):
            v = {'model': NS + ':block/' + mdl}
            if y:
                v['y'] = y
            variants['facing=%s,shape=%s' % (facing, shape)] = v
    write_json(os.path.join(ASSETS, 'blockstates', 'conveyor_belt.json'), {'variants': variants})
    write_json(os.path.join(ASSETS, 'models', 'item', 'conveyor_belt.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + ':item/conveyor_belt'}})


def data():
    write_json(os.path.join(DATA, 'loot_tables', 'blocks', 'conveyor_belt.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + ':conveyor_belt'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write_json(os.path.join(DATA, 'recipes', 'conveyor_belt.json'), {
        'type': 'minecraft:crafting_shaped',
        'pattern': ['LLL', 'IRI'],
        'key': {'L': {'item': 'minecraft:leather'}, 'I': {'item': 'minecraft:iron_ingot'}, 'R': {'item': 'minecraft:redstone'}},
        'result': {'item': NS + ':conveyor_belt', 'count': 4}})


def lang():
    texts = {'es_es': 'Cinta transportadora', 'en_us': 'Conveyor Belt'}
    for lg, text in texts.items():
        path = os.path.join(ASSETS, 'lang', lg + '.json')
        s = open(path, encoding='utf-8').read()
        if '"block.beautifuldecor.conveyor_belt"' in s:
            continue
        anchor = '  "block.beautifuldecor.pipe_iron"'
        add = '  "block.beautifuldecor.conveyor_belt": %s,\n  "item.beautifuldecor.conveyor_belt": %s,\n' % (json.dumps(text, ensure_ascii=False), json.dumps(text, ensure_ascii=False))
        assert anchor in s
        s = s.replace(anchor, add + anchor, 1)
        json.loads(s)
        open(path, 'w', encoding='utf-8').write(s)


if __name__ == '__main__':
    belt_texture()
    frame_texture()
    icon()
    models()
    data()
    lang()
    print('cinta transportadora generada')
