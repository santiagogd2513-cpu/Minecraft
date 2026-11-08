#!/usr/bin/env python3
"""Tuberias conectables de hierro, acero y aluminio (64x64). Modelo multipart: nucleo + brazo por direccion."""
import json, os, random

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'src', 'main')
ASSETS = os.path.join(SRC, 'resources', 'assets', 'beautifuldecor')
DATA = os.path.join(SRC, 'resources', 'data', 'beautifuldecor')
JAVA = os.path.join(SRC, 'java', 'com', 'example', 'beautifuldecor')
random.seed(5)
UVR = {'body': [1, 1, 7, 7], 'flange': [9, 1, 15, 7], 'joint': [1, 9, 7, 15], 'dark': [9, 9, 15, 15]}

SPECS = {
    'iron': dict(es='Tubería de hierro', en='Iron Pipe',   # hierro blanco con detalles gris oscuro, bridas cuadradas
        body=(222, 224, 227), flange=(72, 74, 79), joint=(206, 208, 212), dark=(50, 52, 56), rivet=(150, 152, 158),
        stripe=(92, 94, 99), bw=6.4, fw=8.0, fsq=True, fl=1.4, collars=[(2.2, 2.6), (3.4, 3.8)]),
    'steel': dict(es='Tubería de acero', en='Steel Pipe',  # acero plateado, bridas octogonales y collar
        body=(178, 184, 192), flange=(108, 114, 122), joint=(150, 156, 164), dark=(64, 68, 74), rivet=(206, 210, 216),
        stripe=None, bw=6.0, fw=7.6, fsq=False, fl=1.2, collars=[(2.4, 3.0)]),
    'aluminum': dict(es='Tubería de aluminio', en='Aluminum Pipe',  # aluminio naranja grisaceo, fina con acople grueso
        body=(194, 148, 108), flange=(126, 118, 110), joint=(172, 130, 94), dark=(70, 62, 56), rivet=(96, 88, 80),
        stripe=None, bw=4.8, fw=7.4, fsq=False, fl=2.0, collars=[]),
}

def clamp(v):
    return max(0, min(255, int(v)))

def quad(im, qx, qy, c, amt=3, brushed=False):
    px = im.load()
    for y in range(32):
        row = random.randint(-4, 4) if brushed else 0
        for x in range(32):
            n = random.randint(-amt, amt) + row
            px[qx + x, qy + y] = (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), 255)

def texture(name, s):
    im = Image.new('RGBA', (64, 64))
    quad(im, 0, 0, s['body'], 2, brushed=True)
    quad(im, 32, 0, s['flange'], 2)
    quad(im, 0, 32, s['joint'], 2)
    quad(im, 32, 32, s['dark'], 2)
    d = ImageDraw.Draw(im)
    if s['stripe']:                                           # franjas gris oscuro del hierro
        for y in range(4, 32, 9):
            d.rectangle([0, y, 31, y + 1], fill=s['stripe'] + (255,))
    for (ox, oy) in ((0, 0), (32, 0)):                         # remaches en cuerpo/brida
        for (x, y) in ((4, 4), (27, 4), (4, 27), (27, 27)):
            if ox == 32:
                d.ellipse([ox + x - 2, oy + y - 2, ox + x + 2, oy + y + 2], fill=s['rivet'] + (255,))
    for (x, y) in ((6, 6), (25, 6), (6, 25), (25, 25)):        # pernos del nudo
        d.ellipse([x - 2, 32 + y - 2, x + 2, 32 + y + 2], fill=s['dark'] + (255,))
        d.point((x - 1, 32 + y - 1), fill=s['rivet'] + (255,))
    d.rectangle([0, 32, 31, 63], outline=tuple(int(v * .75) for v in s['joint']) + (255,))
    im.save(os.path.join(ASSETS, 'textures', 'block', 'pipe_%s.png' % name))

def box(x1, y1, z1, x2, y2, z2, tex):
    return {'from': [round(x1, 3), round(y1, 3), round(z1, 3)], 'to': [round(x2, 3), round(y2, 3), round(z2, 3)],
            'faces': {f: {'texture': '#pipe', 'uv': UVR[tex]} for f in ('north', 'east', 'south', 'west', 'up', 'down')}}

def oct_z(w, z1, z2, tex, square=False):
    if square:
        return [box(8 - w / 2, 8 - w / 2, z1, 8 + w / 2, 8 + w / 2, z2, tex)]
    return [box(8 - ww / 2, 8 - hh / 2, z1, 8 + ww / 2, 8 + hh / 2, z2, tex)
            for ww, hh in ((w, .62 * w), (.62 * w, w), (.88 * w, .88 * w))]

def arm(s):
    E = oct_z(s['bw'], s['fl'], 4.6, 'body')
    E += oct_z(s['fw'], 0.0, s['fl'], 'flange', s['fsq'])
    for z1, z2 in s['collars']:
        E += oct_z(s['bw'] + 1.0, z1, z2, 'flange', s['fsq'])
    return E

def core():
    return [box(8 - a / 2, 8 - b / 2, 8 - c / 2, 8 + a / 2, 8 + b / 2, 8 + c / 2, 'joint')
            for a, b, c in ((7.2, 6.0, 6.0), (6.0, 7.2, 6.0), (6.0, 6.0, 7.2), (6.6, 6.6, 6.6))]

def mirror_z(E):
    out = []
    for e in E:
        f, t = e['from'], e['to']
        out.append({'from': [f[0], f[1], round(16 - t[2], 3)], 'to': [t[0], t[1], round(16 - f[2], 3)], 'faces': e['faces']})
    return out

def write_model(path, name, E):
    tex = {'particle': 'beautifuldecor:block/pipe_' + name, 'pipe': 'beautifuldecor:block/pipe_' + name}
    with open(path, 'w') as f:
        f.write('{\n "parent": "minecraft:block/block",\n "textures": ' + json.dumps(tex) + ',\n "elements": [\n')
        f.write(',\n'.join('  ' + json.dumps(e, separators=(',', ':')) for e in E))
        f.write('\n ]\n}\n')

def patch(path, old, new):
    s = open(path).read()
    if new in s:
        return
    assert s.count(old) == 1, (path, old)
    open(path, 'w').write(s.replace(old, new + old))

def main():
    for name, s in SPECS.items():
        pid = 'pipe_' + name
        texture(name, s)
        m = os.path.join(ASSETS, 'models', 'block')
        write_model(os.path.join(m, pid + '_core.json'), name, core())
        write_model(os.path.join(m, pid + '_arm.json'), name, arm(s))
        write_model(os.path.join(m, pid + '_item.json'), name, core() + arm(s) + mirror_z(arm(s)))
        ref = lambda suffix: 'beautifuldecor:block/%s_%s' % (pid, suffix)
        parts = [{'apply': {'model': ref('core')}}]
        for prop, rot in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}),
                          ('up', {'x': 270}), ('down', {'x': 90})):
            parts.append({'when': {prop: 'true'}, 'apply': dict({'model': ref('arm')}, **rot)})
        json.dump({'multipart': parts}, open(os.path.join(ASSETS, 'blockstates', pid + '.json'), 'w'), indent=1)
        json.dump({'parent': ref('item')}, open(os.path.join(ASSETS, 'models', 'item', pid + '.json'), 'w'), indent=2)
        loot = open(os.path.join(DATA, 'loot_tables', 'blocks', 'nintendo_switch.json')).read().replace('nintendo_switch', pid)
        open(os.path.join(DATA, 'loot_tables', 'blocks', pid + '.json'), 'w').write(loot)
    blocks, items = '', ''
    for name in SPECS:
        blocks += ('    public static final RegistryObject<Block> PIPE_%s = BLOCKS.register("pipe_%s",\n'
                   '            () -> new PipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)\n'
                   '                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));\n\n') % (name.upper(), name)
        items += ('    public static final RegistryObject<Item> PIPE_%s = ITEMS.register("pipe_%s",\n'
                  '            () -> new BlockItem(ModBlocks.PIPE_%s.get(), new Item.Properties()));\n') % (name.upper(), name, name.upper())
    patch(os.path.join(JAVA, 'init', 'ModBlocks.java'), '    public static final RegistryObject<Block> XBOX_ONE', blocks)
    patch(os.path.join(JAVA, 'init', 'ModBlocks.java'), 'import com.example.beautifuldecor.block.LightSwitchBlock;', 'import com.example.beautifuldecor.block.PipeBlock;\n')
    patch(os.path.join(JAVA, 'init', 'ModItems.java'), '    public static final RegistryObject<Item> XBOX_ONE', items)
    patch(os.path.join(JAVA, 'init', 'ModCategory.java'), 'p.startsWith("barrel_")', 'p.startsWith("pipe_") || ')
    for lang, key in (('es_es', 'es'), ('en_us', 'en')):
        lines = ''.join('  "block.beautifuldecor.pipe_%s": "%s",\n  "item.beautifuldecor.pipe_%s": "%s",\n' % (n, s[key], n, s[key]) for n, s in SPECS.items())
        patch(os.path.join(ASSETS, 'lang', lang + '.json'), '  "category.beautifuldecor.stairs"', lines)
    print('ok')

from PIL import Image, ImageDraw
if __name__ == '__main__':
    main()
