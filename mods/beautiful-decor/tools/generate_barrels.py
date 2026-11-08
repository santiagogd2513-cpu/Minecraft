#!/usr/bin/env python3
"""Barriles de liquidos para BeautifulDecor: gasolina, agua, diesel y petroleo.
Cuerpo redondo de 16 lados (placas giradas 22.5 grados), cada uno con modelo, textura 256x128 y detalles propios."""
import json, math, os, random
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'src', 'main')
ASSETS = os.path.join(SRC, 'resources', 'assets', 'beautifuldecor')
DATA = os.path.join(SRC, 'resources', 'data', 'beautifuldecor')
JAVA = os.path.join(SRC, 'java', 'com', 'example', 'beautifuldecor')
random.seed(11)
W, H = 256, 128
Q = {'lid': [2, 2, 6, 6], 'dark': [10, 2, 14, 6], 'accent': [2, 10, 6, 14], 'metal': [10, 10, 14, 14]}

def R(y):
    return int(round((16 - y) * 8))

def clamp(v):
    return max(0, min(255, int(v)))

def font(sz):
    for p in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf'):
        if os.path.exists(p):
            return ImageFont.truetype(p, sz)
    return ImageFont.load_default()

# bandas: (tipo, y1, y2, apotema, pintura). 'body' = placa maciza hasta el centro, 'ring' = aro fino
SPECS = {
    'gasoline': dict(es='Barril de gasolina', en='Gasoline Barrel', text='GASOLINA', symbol='flame',
        body=(196, 38, 32), rib=(140, 22, 20), rim=(64, 66, 72), panel=(244, 244, 236), ink=(170, 24, 22),
        lid=(112, 26, 24), dark=(24, 24, 28), accent=(240, 200, 40), metal=(156, 160, 166),
        bands=[('ring', 0, .9, 6.5, 'rim'), ('body', .9, 15.1, 6.0, 'body'), ('ring', 3.6, 4.6, 6.35, 'rib'),
               ('ring', 11.4, 12.4, 6.35, 'rib'), ('ring', 15.1, 16, 6.5, 'rim')], label=(4.6, 11.4)),
    'water': dict(es='Barril de agua', en='Water Barrel', text='AGUA', symbol='drop',
        body=(38, 112, 205), rib=(66, 146, 232), rim=(28, 84, 160), panel=(240, 246, 252), ink=(28, 92, 180),
        lid=(34, 100, 192), dark=(18, 56, 112), accent=(242, 246, 250), metal=(172, 178, 186),
        bands=[('ring', 0, .9, 6.5, 'rim'), ('body', .9, 15.1, 6.0, 'body'), ('ring', 2.6, 3.6, 6.35, 'rib'),
               ('ring', 12.4, 13.4, 6.35, 'rib'), ('ring', 15.1, 16, 6.5, 'rim')],
        label=(3.6, 12.4)),
    'diesel': dict(es='Barril de diésel', en='Diesel Barrel', text='DIESEL', symbol='pump',
        body=(236, 190, 36), rib=(30, 30, 33), rim=(30, 30, 33), panel=(26, 26, 30), ink=(240, 200, 40),
        lid=(214, 168, 28), dark=(22, 22, 25), accent=(240, 200, 40), metal=(150, 154, 160),
        bands=[('ring', 0, .9, 6.5, 'rim'), ('body', .9, 15.1, 6.0, 'body'), ('ring', 3.6, 4.6, 6.35, 'rib'),
               ('ring', 11.4, 12.4, 6.35, 'rib'), ('ring', 15.1, 16, 6.5, 'rim')], label=(4.6, 11.4)),
    'oil': dict(es='Barril de petróleo', en='Oil Barrel', text='PETROLEO', symbol='drop',
        body=(34, 34, 38), rib=(58, 58, 64), rim=(70, 56, 46), panel=(232, 116, 24), ink=(16, 16, 18),
        lid=(10, 9, 12), dark=(8, 8, 10), accent=(232, 116, 24), metal=(110, 76, 52),
        bands=[('ring', 0, .9, 6.5, 'rim'), ('body', .9, 13.4, 6.0, 'body'), ('ring', 2.8, 3.6, 6.35, 'rib'),
               ('ring', 11.6, 12.4, 6.35, 'rib'), ('ring', 13.4, 16, 6.5, 'rim')], label=(3.6, 11.6)),
}

# ---------------- texturas ----------------
def fill_rows(im, y1, y2, color, amt=3):
    px = im.load()
    for y in range(R(y2), R(y1)):
        for x in range(W):
            n = random.randint(-amt, amt)
            px[x, y] = (clamp(color[0] + n), clamp(color[1] + n), clamp(color[2] + n), 255)

def shade_row(im, y, delta):
    px = im.load()
    for x in range(W):
        r, g, b, a = px[x, y]
        px[x, y] = (clamp(r + delta), clamp(g + delta), clamp(b + delta), 255)

def symbol(d, kind, cx, cy, spec):
    ink = spec['ink']
    if kind == 'flame':
        d.polygon([(cx, cy - 11), (cx + 6, cy - 2), (cx + 8, cy + 4), (cx + 4, cy + 10), (cx - 4, cy + 10), (cx - 8, cy + 4), (cx - 5, cy - 3), (cx - 2, cy - 6)], fill=(250, 130, 20))
        d.polygon([(cx, cy - 2), (cx + 4, cy + 4), (cx + 2, cy + 9), (cx - 2, cy + 9), (cx - 4, cy + 4)], fill=(255, 214, 60))
    elif kind == 'drop':
        d.polygon([(cx, cy - 11), (cx - 6, cy + 1), (cx + 6, cy + 1)], fill=ink)
        d.ellipse([cx - 6, cy - 3, cx + 6, cy + 9], fill=ink)
        d.ellipse([cx - 3, cy, cx - 1, cy + 3], fill=(255, 255, 255))
    else:  # surtidor
        d.rectangle([cx - 7, cy - 10, cx + 3, cy + 10], fill=ink)
        d.rectangle([cx - 5, cy - 8, cx + 1, cy - 2], fill=(26, 26, 30))
        d.line([cx + 3, cy - 6, cx + 8, cy - 6, cx + 8, cy + 4], fill=ink, width=2)

def side_texture(name, spec):
    im = Image.new('RGBA', (W, H), spec['body'] + (255,))
    for kind, y1, y2, a, paint in spec['bands']:
        pass
    for kind, y1, y2, a, paint in spec['bands']:           # cuerpo primero, aros encima
        if kind == 'body':
            fill_rows(im, 0, 16, spec['body'])
    for kind, y1, y2, a, paint in spec['bands']:
        if kind == 'ring':
            fill_rows(im, y1, y2, spec[paint], 2)
            shade_row(im, R(y2), 28)
            shade_row(im, R(y1) - 1, -28)
    d = ImageDraw.Draw(im)
    if name == 'diesel':                                    # franja de peligro
        for y in range(R(14.6), R(12.6)):
            for x in range(W):
                if ((x + y) // 6) % 2:
                    d.point((x, y), fill=(30, 30, 33, 255))
    if name == 'gasoline':                                  # franja amarilla
        for y in range(R(14.2), R(12.6)):
            for x in range(W):
                d.point((x, y), fill=(240 + random.randint(-3, 3), 200, 40, 255))
    if name == 'water':                                     # relieve vertical del plastico
        for x in range(0, W, 8):
            for y in range(R(15.1), R(.9)):
                r, g, b, a = im.getpixel((x, y))
                d.point((x, y), fill=(clamp(r + 10), clamp(g + 10), clamp(b + 12), 255))
    # etiqueta centrada en el frente (norte = columna 8)
    ly1, ly2 = spec['label']
    top, bot = R(ly2) + 4, R(ly1) - 4
    x0, x1 = 90, 182
    d.rectangle([x0, top, x1, bot], fill=spec['panel'] + (255,), outline=tuple(int(v * .6) for v in spec['panel']) + (255,))
    cx, cy = (x0 + x1) // 2, top + 17
    symbol(d, spec['symbol'], cx, cy, spec)
    sz = 16
    while sz > 7:
        f = font(sz)
        w = d.textlength(spec['text'], font=f)
        if w <= (x1 - x0) - 10:
            break
        sz -= 1
    f = font(sz)
    w = d.textlength(spec['text'], font=f)
    d.text((cx - w / 2, bot - sz - 5), spec['text'], font=f, fill=spec['ink'] + (255,))
    # desgaste
    for _ in range(70):
        x, y = random.randint(0, W - 1), random.randint(R(15), R(1))
        r, g, b, a = im.getpixel((x, y))
        c = 18 if random.random() < .5 else -18
        d.line([x, y, x + random.randint(-3, 3), y + random.randint(1, 5)], fill=(clamp(r + c), clamp(g + c), clamp(b + c), 255))
    if name == 'oil':                                       # oxido y chorreras
        for _ in range(60):
            x, y = random.randint(0, W - 1), random.randint(R(12), R(1))
            d.point((x, y), fill=(120 + random.randint(-15, 15), 62, 30, 255))
        for x in range(0, W, 19):
            for y in range(R(13.4), R(13.4) + random.randint(6, 18)):
                d.point((x + 3, y), fill=(14, 13, 16, 255))
    im.save(os.path.join(ASSETS, 'textures', 'block', 'barrel_%s.png' % name))

def part_texture(name, spec):
    im = Image.new('RGBA', (16, 16))
    for (x, y, c) in ((0, 0, spec['lid']), (8, 0, spec['dark']), (0, 8, spec['accent']), (8, 8, spec['metal'])):
        for i in range(8):
            for j in range(8):
                im.putpixel((x + i, y + j), c + (255,))
    im.save(os.path.join(ASSETS, 'textures', 'block', 'barrel_%s_part.png' % name))

# ---------------- modelo ----------------
def fc(**faces):
    out = {}
    for f, v in faces.items():
        tex, uv = v
        out[f] = {'texture': '#' + tex, 'uv': uv}
    return out

def rr(v):
    return round(v, 4)

def plate_elements(spec):
    E = []
    top_body = max(y2 for kind, y1, y2, a, p in spec['bands'] if kind == 'body')
    for kind, y1, y2, a, paint in spec['bands']:
        r0 = 0.0 if kind == 'body' else a - 0.45
        c = 2 * a * math.tan(math.pi / 16) * 1.06
        for k in range(16):
            phi = 22.5 * k
            mraw = int(round(phi / 90.0))
            alpha = phi - 90 * mraw
            m = mraw % 4
            if m == 0:
                fr, to, out, inn = [8 + r0, y1, 8 - c / 2], [8 + a, y2, 8 + c / 2], 'east', 'west'
            elif m == 1:
                fr, to, out, inn = [8 - c / 2, y1, 8 + r0], [8 + c / 2, y2, 8 + a], 'south', 'north'
            elif m == 2:
                fr, to, out, inn = [8 - a, y1, 8 - c / 2], [8 - r0, y2, 8 + c / 2], 'west', 'east'
            else:
                fr, to, out, inn = [8 - c / 2, y1, 8 - a], [8 + c / 2, y2, 8 - r0], 'north', 'south'
            col = (20 - k) % 16
            faces = {out: ('side', [col, rr(16 - y2), col + 1, rr(16 - y1)])}
            if kind == 'ring':
                faces['up'] = ('part', Q['metal'])
                faces['down'] = ('part', Q['dark'])
                faces[inn] = ('part', Q['lid'])
            elif abs(y2 - top_body) < 1e-6:
                faces['up'] = ('part', Q['lid'])
            el = {'from': [rr(v) for v in fr], 'to': [rr(v) for v in to], 'faces': fc(**faces)}
            if alpha != 0:
                el['rotation'] = {'origin': [8, 0, 8], 'axis': 'y', 'angle': -alpha}
            # comprobacion geometrica: el centro de la cara exterior debe quedar en direccion phi
            ox = {0: (to[0], 8), 1: (8, to[2]), 2: (fr[0], 8), 3: (8, fr[2])}[m]
            dx, dz = ox[0] - 8, ox[1] - 8
            t = math.radians(-alpha)
            nx, nz = dx * math.cos(t) + dz * math.sin(t), -dx * math.sin(t) + dz * math.cos(t)
            assert abs(nx - a * math.cos(math.radians(phi))) < 1e-6 and abs(nz - a * math.sin(math.radians(phi))) < 1e-6, (k, alpha)
            E.append(el)
    return E

def bx(x1, y1, z1, x2, y2, z2, q):
    return {'from': [x1, y1, z1], 'to': [x2, y2, z2], 'faces': fc(**{f: ('part', Q[q]) for f in ('north', 'east', 'south', 'west', 'up', 'down')})}

def octa(cx, cz, w, y1, y2, q):
    return [bx(cx - ww / 2, y1, cz - hh / 2, cx + ww / 2, y2, cz + hh / 2, q)
            for ww, hh in ((w, .62 * w), (.62 * w, w), (.88 * w, .88 * w))]

def extras(name):
    E = []
    if name == 'gasoline':                      # dos tapones metalicos y un asa de tapa
        E += octa(5.0, 8.0, 2.6, 15.1, 15.7, 'metal') + octa(10.8, 8.0, 1.8, 15.1, 15.7, 'metal')
        E += octa(5.0, 8.0, 1.4, 15.7, 15.9, 'dark')
    elif name == 'water':                       # tapa grande de rosca y grifo frontal
        E += octa(8.0, 8.0, 7.2, 15.1, 15.65, 'accent') + octa(8.0, 8.0, 5.4, 15.65, 15.95, 'accent')
        E += [bx(7.3, 1.5, .7, 8.7, 2.6, 2.0, 'metal'), bx(7.3, .5, .7, 8.7, 1.6, 1.8, 'metal'),
              bx(7.6, 2.6, 1.0, 8.4, 3.0, 1.6, 'accent')]
    elif name == 'diesel':                      # tapon y bomba manual con boquilla
        E += octa(4.8, 8.0, 2.2, 15.1, 15.6, 'metal') + octa(10.5, 8.0, 2.8, 15.1, 15.5, 'dark')
        E += octa(10.5, 8.0, 1.4, 15.5, 16.0, 'metal')
        E += [bx(6.2, 15.5, 7.6, 10.5, 16.0, 8.4, 'metal')]
    else:                                       # petroleo: barril abierto con goteo en el borde
        E += [bx(5.4, 11.0, 1.2, 6.6, 14.0, 1.5, 'dark'), bx(5.7, 10.2, 1.2, 6.3, 11.0, 1.5, 'dark'),
              bx(9.2, 11.8, 1.2, 10.0, 14.2, 1.5, 'dark')]
        E += octa(11.0, 10.0, 1.6, 13.4, 13.5, 'metal')
    return E

def write_model(name, spec):
    E = plate_elements(spec) + extras(name)
    tex = {'particle': 'beautifuldecor:block/barrel_%s' % name, 'side': 'beautifuldecor:block/barrel_%s' % name,
           'part': 'beautifuldecor:block/barrel_%s_part' % name}
    with open(os.path.join(ASSETS, 'models', 'block', 'barrel_%s.json' % name), 'w') as f:
        f.write('{\n "parent": "minecraft:block/block",\n "textures": ' + json.dumps(tex) + ',\n "elements": [\n')
        f.write(',\n'.join('  ' + json.dumps(e, separators=(',', ':')) for e in E))
        f.write('\n ]\n}\n')
    return len(E)

# ---------------- registro ----------------
def write_assets(name, spec):
    bid = 'barrel_' + name
    states = {'variants': {'facing=north': {'model': 'beautifuldecor:block/' + bid},
                           'facing=east': {'model': 'beautifuldecor:block/' + bid, 'y': 90},
                           'facing=south': {'model': 'beautifuldecor:block/' + bid, 'y': 180},
                           'facing=west': {'model': 'beautifuldecor:block/' + bid, 'y': 270}}}
    json.dump(states, open(os.path.join(ASSETS, 'blockstates', bid + '.json'), 'w'), indent=2)
    json.dump({'parent': 'beautifuldecor:block/' + bid}, open(os.path.join(ASSETS, 'models', 'item', bid + '.json'), 'w'), indent=2)
    loot = open(os.path.join(DATA, 'loot_tables', 'blocks', 'nintendo_switch.json')).read().replace('nintendo_switch', bid)
    open(os.path.join(DATA, 'loot_tables', 'blocks', bid + '.json'), 'w').write(loot)

def patch(path, old, new, once=True):
    s = open(path).read()
    if new in s:
        return
    assert s.count(old) == 1, (path, old)
    open(path, 'w').write(s.replace(old, new + old))

def register():
    blocks, items = '', ''
    for name in SPECS:
        bid = name.upper()
        blocks += ('    public static final RegistryObject<Block> BARREL_%s = BLOCKS.register("barrel_%s",\n'
                   '            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)\n'
                   '                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),\n'
                   '                    Shapes.box(1.4 / 16.0, 0.0, 1.4 / 16.0, 14.6 / 16.0, 1.0, 14.6 / 16.0)));\n\n') % (bid, name)
        items += ('    public static final RegistryObject<Item> BARREL_%s = ITEMS.register("barrel_%s",\n'
                  '            () -> new BlockItem(ModBlocks.BARREL_%s.get(), new Item.Properties()));\n') % (bid, name, bid)
    patch(os.path.join(JAVA, 'init', 'ModBlocks.java'), '    public static final RegistryObject<Block> XBOX_ONE', blocks)
    patch(os.path.join(JAVA, 'init', 'ModItems.java'), '    public static final RegistryObject<Item> XBOX_ONE', items)
    patch(os.path.join(JAVA, 'init', 'ModCategory.java'), 'p.startsWith("concrete_ramp")', 'p.startsWith("barrel_") || ')
    for lang, key in (('es_es', 'es'), ('en_us', 'en')):
        lines = ''.join('  "block.beautifuldecor.barrel_%s": "%s",\n  "item.beautifuldecor.barrel_%s": "%s",\n' % (n, s[key], n, s[key]) for n, s in SPECS.items())
        patch(os.path.join(ASSETS, 'lang', lang + '.json'), '  "category.beautifuldecor.stairs"', lines)

def main():
    for name, spec in SPECS.items():
        side_texture(name, spec)
        part_texture(name, spec)
        print(name, write_model(name, spec), 'elementos')
        write_assets(name, spec)
    register()

if __name__ == '__main__':
    main()
