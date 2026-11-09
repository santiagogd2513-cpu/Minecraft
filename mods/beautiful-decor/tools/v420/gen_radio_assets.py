"""V4.2.0: texturas, modelos y blockstates de la radio de mesa y su bafle."""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "beautifuldecor")
TEX = os.path.join(ROOT, "textures", "block")
ITEM = os.path.join(ROOT, "textures", "item")
random.seed(42)

WOOD = [(122, 78, 44), (134, 88, 50), (112, 70, 38), (143, 97, 58)]
DARK = (36, 30, 28)
CREAM = (232, 214, 160)
LIT = (255, 226, 120)


def wood(w=16, h=16, grain=True):
    im = Image.new("RGB", (w, h))
    px = im.load()
    for y in range(h):
        base = WOOD[(y // 3) % len(WOOD)]
        for x in range(w):
            j = random.randint(-5, 5)
            px[x, y] = tuple(max(0, min(255, c + j)) for c in base)
    return im


def save(im, folder, name):
    im.save(os.path.join(folder, name + ".png"))


def radio_front(on):
    im = wood()
    d = ImageDraw.Draw(im)
    # marco oscuro de la zona útil (x 1..14, y 0..8)
    d.rectangle([1, 0, 14, 8], fill=(88, 56, 32))
    d.rectangle([1, 0, 14, 0], fill=(150, 104, 62))
    # rejilla del parlante
    d.rectangle([2, 1, 8, 7], fill=(28, 24, 22))
    for y in range(2, 7, 2):
        for x in range(3, 8, 2):
            d.point((x, y), fill=(92, 84, 70))
    # ventana del dial
    d.rectangle([9, 1, 14, 4], fill=LIT if on else CREAM)
    d.rectangle([9, 1, 14, 1], fill=(70, 50, 30))
    for x in range(10, 14):
        d.point((x, 3), fill=(120, 90, 40) if on else (150, 130, 90))
    d.line([(11, 2), (11, 4)], fill=(190, 40, 30))
    # perillas (se ven pintadas debajo de los cubos del modelo)
    d.ellipse([10, 5, 11, 7], fill=(20, 18, 18))
    d.ellipse([13, 5, 14, 7], fill=(20, 18, 18))
    return im


def trim():
    im = Image.new("RGB", (16, 16), DARK)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 1], fill=(70, 64, 60))
    return im


def spk_front():
    im = wood()
    d = ImageDraw.Draw(im)
    d.rectangle([3, 3, 12, 15], fill=(30, 26, 24))     # zona del modelo (u 3..13, v 3..16)
    d.ellipse([4, 8, 11, 15], fill=(52, 48, 44), outline=(96, 88, 78))   # woofer
    d.ellipse([6, 10, 9, 13], fill=(20, 18, 18))
    d.ellipse([6, 4, 9, 7], fill=(52, 48, 44), outline=(96, 88, 78))     # tweeter
    d.point((7, 5), fill=(150, 140, 120))
    return im


def spk_back():
    im = wood()
    d = ImageDraw.Draw(im)
    d.rectangle([5, 6, 10, 10], fill=(40, 36, 34))
    d.point((6, 8), fill=(200, 60, 50)); d.point((9, 8), fill=(180, 180, 180))
    return im


def icon(kind):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if kind == "radio":
        d.rectangle([3, 11, 28, 26], fill=(122, 78, 44), outline=(60, 38, 22))
        d.rectangle([5, 13, 15, 24], fill=(28, 24, 22))
        for y in range(15, 24, 3):
            for x in range(7, 15, 3):
                d.point((x, y), fill=(110, 100, 84))
        d.rectangle([17, 13, 26, 18], fill=CREAM, outline=(70, 50, 30))
        d.line([(21, 14), (21, 18)], fill=(190, 40, 30))
        d.ellipse([18, 20, 21, 23], fill=(20, 18, 18)); d.ellipse([23, 20, 26, 23], fill=(20, 18, 18))
        d.line([(24, 11), (27, 4)], fill=(190, 190, 190), width=1)
        d.ellipse([26, 3, 28, 5], fill=(190, 190, 190))
    else:
        d.rectangle([8, 4, 24, 28], fill=(122, 78, 44), outline=(60, 38, 22))
        d.rectangle([10, 6, 22, 26], fill=(30, 26, 24))
        d.ellipse([11, 15, 21, 25], fill=(52, 48, 44), outline=(110, 100, 88))
        d.ellipse([14, 18, 18, 22], fill=(20, 18, 18))
        d.ellipse([13, 7, 19, 13], fill=(52, 48, 44), outline=(110, 100, 88))
        d.point((16, 10), fill=(170, 160, 140))
    return im


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all_faces(tex, uv=None):
    return {k: {"texture": tex} for k in ("north", "south", "east", "west", "up", "down")}


def model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def radio_model(on):
    front = "#front"
    els = [
        box([1, 0, 4], [15, 9, 12], {
            "north": {"texture": front, "uv": [1, 0, 15, 9]},
            "south": {"texture": "#wood", "uv": [1, 0, 15, 9]},
            "east": {"texture": "#wood", "uv": [4, 0, 12, 9]},
            "west": {"texture": "#wood", "uv": [4, 0, 12, 9]},
            "up": {"texture": "#wood", "uv": [1, 4, 15, 12]},
            "down": {"texture": "#trim", "uv": [1, 4, 15, 12]},
        }),
        box([0.6, 0, 4.6], [1, 0.8, 5.4], all_faces("#trim")),
        box([15, 0, 4.6], [15.4, 0.8, 5.4], all_faces("#trim")),
        box([9.8, 1.8, 3.4], [11.2, 3.2, 4], all_faces("#trim")),     # perilla de sintonía
        box([12.3, 1.8, 3.4], [13.7, 3.2, 4], all_faces("#trim")),    # perilla de volumen
        box([5, 9, 7], [11, 10, 9], all_faces("#trim")),               # asa
        box([12.6, 9, 9.4], [13.2, 15.5, 10], all_faces("#trim")),     # antena
        box([12.4, 15.5, 9.2], [13.4, 16, 10.2], all_faces("#trim")),
    ]
    return model({"particle": "beautifuldecor:block/radio_table_wood", "wood": "beautifuldecor:block/radio_table_wood",
                  "front": "beautifuldecor:block/radio_table_front_on" if on else "beautifuldecor:block/radio_table_front",
                  "trim": "beautifuldecor:block/radio_table_trim"}, els)


def speaker_model():
    els = [
        box([3, 0, 4], [13, 13, 12], {
            "north": {"texture": "#front", "uv": [3, 3, 13, 16]},
            "south": {"texture": "#back", "uv": [3, 3, 13, 16]},
            "east": {"texture": "#wood", "uv": [4, 3, 12, 16]},
            "west": {"texture": "#wood", "uv": [4, 3, 12, 16]},
            "up": {"texture": "#wood", "uv": [3, 4, 13, 12]},
            "down": {"texture": "#trim", "uv": [3, 4, 13, 12]},
        }),
        box([3.4, 0, 4.4], [5, 0.6, 6], all_faces("#trim")),
        box([11, 0, 4.4], [12.6, 0.6, 6], all_faces("#trim")),
    ]
    return model({"particle": "beautifuldecor:block/radio_table_wood", "wood": "beautifuldecor:block/radio_table_wood",
                  "front": "beautifuldecor:block/radio_speaker_front", "back": "beautifuldecor:block/radio_speaker_back",
                  "trim": "beautifuldecor:block/radio_table_trim"}, els)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


def main():
    save(wood(), TEX, "radio_table_wood")
    save(radio_front(False), TEX, "radio_table_front")
    save(radio_front(True), TEX, "radio_table_front_on")
    save(trim(), TEX, "radio_table_trim")
    save(spk_front(), TEX, "radio_speaker_front")
    save(spk_back(), TEX, "radio_speaker_back")
    icon("radio").save(os.path.join(ITEM, "radio_table.png"))
    icon("speaker").save(os.path.join(ITEM, "radio_speaker.png"))
    md = os.path.join(ROOT, "models")
    write_json(os.path.join(md, "block", "radio_table.json"), radio_model(False))
    write_json(os.path.join(md, "block", "radio_table_on.json"), radio_model(True))
    write_json(os.path.join(md, "block", "radio_speaker.json"), speaker_model())
    for n in ("radio_table", "radio_speaker"):
        write_json(os.path.join(md, "item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/" + n}})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {}
    for f, y in rot.items():
        for on in ("false", "true"):
            m = "beautifuldecor:block/radio_table" + ("_on" if on == "true" else "")
            e = {"model": m}
            if y:
                e["y"] = y
            v["facing=%s,on=%s" % (f, on)] = e
    write_json(os.path.join(ROOT, "blockstates", "radio_table.json"), {"variants": v})
    v = {}
    for f, y in rot.items():
        e = {"model": "beautifuldecor:block/radio_speaker"}
        if y:
            e["y"] = y
        v["facing=" + f] = e
    write_json(os.path.join(ROOT, "blockstates", "radio_speaker.json"), {"variants": v})
    data = os.path.join(ROOT, "..", "..", "data", "beautifuldecor", "loot_tables", "blocks")
    for n in ("radio_table", "radio_speaker"):
        write_json(os.path.join(data, n + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "beautifuldecor:" + n}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # hoja de contacto para revisar
    sheet = Image.new("RGB", (16 * 6 * 4, 16 * 4), (60, 60, 60))
    for i, n in enumerate(("radio_table_wood", "radio_table_front", "radio_table_front_on", "radio_table_trim", "radio_speaker_front", "radio_speaker_back")):
        t = Image.open(os.path.join(TEX, n + ".png")).convert("RGB").resize((64, 64), Image.NEAREST)
        sheet.paste(t, (i * 64, 0))
    sheet.save(os.path.join(os.path.dirname(__file__), "radio_sheet.png"))


if __name__ == "__main__":
    main()
