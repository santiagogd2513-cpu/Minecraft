"""V4.3.0: texturas, modelos, blockstates y loot del sistema de cámaras de seguridad."""
import json, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "beautifuldecor")
TEX = os.path.join(ROOT, "textures", "block")
ITEM = os.path.join(ROOT, "textures", "item")
DATA = os.path.join(ROOT, "..", "..", "data", "beautifuldecor", "loot_tables", "blocks")
random.seed(430)
PALETTE = {"black": (34, 36, 40), "white": (226, 228, 232)}


def jitter(c, a=4):
    return tuple(max(0, min(255, v + random.randint(-a, a))) for v in c)


def solid(size, c, a=4):
    im = Image.new("RGB", (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = jitter(c, a)
    return im


def head(color):
    base = PALETTE[color]
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    for y in range(32):
        for x in range(32):
            px[x, y] = jitter(base, 3) + (255,)
    d = ImageDraw.Draw(im)
    # visor: algo más oscuro/claro que el cuerpo
    shade = tuple(max(0, v - 28) for v in base)
    d.rectangle([0, 14, 31, 25], fill=shade + (255,))
    # lente: cara frontal del anillo (1,27) 3x3
    d.rectangle([1, 27, 3, 29], fill=(8, 10, 18, 255))
    d.point((1, 27), fill=(70, 90, 140, 255))
    d.point((2, 28), fill=(20, 28, 50, 255))
    # tapa trasera: LED rojo
    d.rectangle([14, 29, 16, 31], fill=shade + (255,))
    d.point((15, 30), fill=(210, 30, 30, 255))
    return im


def bracket(color):
    c = tuple(max(0, v - 10) for v in PALETTE[color])
    return solid(16, c, 3)


def frame():
    im = solid(16, (30, 32, 36), 3)
    ImageDraw.Draw(im).rectangle([0, 0, 15, 0], fill=(52, 56, 62))
    return im


def glass():
    im = solid(16, (8, 10, 12), 2)
    d = ImageDraw.Draw(im)
    d.line([(0, 15), (15, 0)], fill=(18, 22, 26))
    return im


def noise():
    im = Image.new("RGB", (64, 64))
    px = im.load()
    for y in range(64):
        for x in range(64):
            v = random.randint(20, 235)
            if y % 4 == 0:
                v = v // 2
            px[x, y] = (v, v, v)
    return im


def icon(kind):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if kind in ("black", "white"):
        body = PALETTE[kind]
        edge = (0, 0, 0) if kind == "white" else (120, 124, 130)
        d.rectangle([4, 24, 12, 28], fill=(110, 112, 118), outline=(50, 50, 56))
        d.rectangle([8, 18, 12, 24], fill=(110, 112, 118))
        d.rectangle([8, 8, 27, 18], fill=body, outline=edge)
        d.rectangle([5, 7, 10, 17], fill=tuple(max(0, v - 25) for v in body), outline=edge)
        d.ellipse([3, 9, 8, 14], fill=(8, 10, 18), outline=(80, 100, 150))
        d.point((24, 10), fill=(210, 30, 30))
    else:
        wall = kind == "screen"
        d.rectangle([2, 6, 29, 22], fill=(30, 32, 36), outline=(70, 74, 80))
        d.rectangle([4, 8, 27, 20], fill=(14, 40, 26))
        d.line([(6, 18), (12, 12), (17, 16), (25, 10)], fill=(90, 230, 130))
        d.point((26, 9), fill=(210, 30, 30))
        if not wall:
            d.rectangle([13, 22, 18, 25], fill=(50, 54, 60))
            d.rectangle([8, 25, 23, 27], fill=(50, 54, 60))
    return im


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def faces(tex):
    return {k: {"texture": tex} for k in ("north", "south", "east", "west", "up", "down")}


def model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def bracket_model(color, mount):
    t = {"particle": "#b", "b": "beautifuldecor:block/security_camera_bracket_" + color}
    if mount == "wall":
        els = [box([5, 5, 15], [11, 11, 16], faces("#b")), box([7.2, 4.6, 8.7], [8.8, 6.5, 15], faces("#b"))]
    else:
        els = [box([5, 15, 5], [11, 16, 11], faces("#b")), box([7.2, 13.5, 7.2], [8.8, 15, 8.8], faces("#b"))]
    return model(t, els)


def monitor_model(wall, right):
    t = {"particle": "#f", "f": "beautifuldecor:block/security_monitor_frame", "g": "beautifuldecor:block/security_monitor_glass"}
    if wall:
        body = ([0, 0, 14], [16, 16, 16])
        disp = ([1, 1.5, 13.8], [16, 14.5, 14]) if right else ([0, 1.5, 13.8], [15, 14.5, 14])
        els = [box(body[0], body[1], faces("#f")), box(disp[0], disp[1], {"north": {"texture": "#g"}})]
    else:
        disp = ([1, 4, 5.8], [16, 15, 6]) if right else ([0, 4, 5.8], [15, 15, 6])
        base = [10, 0, 4], [16, 1.5, 12]
        neck = [14, 1.5, 6.5], [16, 3, 9.5]
        if not right:
            base = [0, 0, 4], [6, 1.5, 12]
            neck = [0, 1.5, 6.5], [2, 3, 9.5]
        els = [box([0, 3, 6], [16, 16, 10], faces("#f")), box(disp[0], disp[1], {"north": {"texture": "#g"}}),
               box(base[0], base[1], faces("#f")), box(neck[0], neck[1], faces("#f"))]
    return model(t, els)


def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def main():
    for c in ("black", "white"):
        head(c).save(os.path.join(TEX, "security_camera_head_%s.png" % c))
        bracket(c).save(os.path.join(TEX, "security_camera_bracket_%s.png" % c))
        icon(c).save(os.path.join(ITEM, "security_camera_%s.png" % c))
    frame().save(os.path.join(TEX, "security_monitor_frame.png"))
    glass().save(os.path.join(TEX, "security_monitor_glass.png"))
    noise().save(os.path.join(TEX, "camera_noise.png"))
    icon("monitor").save(os.path.join(ITEM, "security_monitor.png"))
    icon("screen").save(os.path.join(ITEM, "security_screen.png"))
    md = os.path.join(ROOT, "models")
    for c in ("black", "white"):
        for m in ("wall", "ceiling"):
            wj(os.path.join(md, "block", "security_camera_%s_%s.json" % (c, m)), bracket_model(c, m))
        v = {}
        for f, y in ROT.items():
            for m in ("wall", "ceiling"):
                e = {"model": "beautifuldecor:block/security_camera_%s_%s" % (c, m)}
                if y:
                    e["y"] = y
                v["facing=%s,mount=%s" % (f, m)] = e
        wj(os.path.join(ROOT, "blockstates", "security_camera_%s.json" % c), {"variants": v})
    for name, wall in (("security_monitor", False), ("security_screen", True)):
        wj(os.path.join(md, "block", name + "_l.json"), monitor_model(wall, False))
        wj(os.path.join(md, "block", name + "_r.json"), monitor_model(wall, True))
        v = {}
        for f, y in ROT.items():
            for px in (0, 1):
                e = {"model": "beautifuldecor:block/%s_%s" % (name, "r" if px else "l")}
                if y:
                    e["y"] = y
                v["facing=%s,px=%d" % (f, px)] = e
        wj(os.path.join(ROOT, "blockstates", name + ".json"), {"variants": v})
    for n in ("security_camera_black", "security_camera_white", "security_monitor", "security_screen"):
        wj(os.path.join(md, "item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/" + n}})
    for n in ("security_camera_black", "security_camera_white"):
        wj(os.path.join(DATA, n + ".json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "beautifuldecor:" + n}],
                                                                                 "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for n in ("security_monitor", "security_screen"):
        wj(os.path.join(DATA, n + ".json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "beautifuldecor:" + n}],
                                                                                 "conditions": [{"condition": "minecraft:block_state_property", "block": "beautifuldecor:" + n, "properties": {"px": "0"}},
                                                                                                {"condition": "minecraft:survives_explosion"}]}]})


if __name__ == "__main__":
    main()
