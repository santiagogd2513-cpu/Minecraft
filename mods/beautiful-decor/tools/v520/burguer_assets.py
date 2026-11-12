"""V5.3.0: texturas y modelos de Burguer Reino: hamburguesa (ítem), mostrador (bloque), corona (bloque) y cartel de menú (cuadro)."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from .burguer import frame_burguer

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
ORANGE, RED, GOLD, BROWN = (240, 130, 20), (200, 40, 30), (255, 205, 50), (150, 90, 40)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def burger_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([2, 3, 13, 6], fill=(210, 140, 60, 255)); d.rectangle([3, 2, 12, 2], fill=(210, 140, 60, 255))
    for p in ((4, 3), (7, 4), (10, 3), (6, 3), (9, 5)):
        d.point(p, fill=(255, 240, 200, 255))
    d.rectangle([2, 7, 13, 7], fill=(80, 180, 70, 255))
    d.rectangle([3, 8, 12, 8], fill=(255, 205, 50, 255))
    d.rectangle([2, 9, 13, 10], fill=(110, 60, 30, 255))
    d.rectangle([3, 11, 12, 13], fill=(190, 120, 50, 255))
    d.point((8, 1), fill=GOLD + (255,)); d.point((6, 1), fill=GOLD + (255,)); d.point((10, 1), fill=GOLD + (255,))
    im.save(A / "textures/item/burguer_royal.png")


def menu_painting():
    im = Image.new("RGB", (128, 96), (120, 25, 15))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 127, 95], outline=(30, 10, 5))
    d.rectangle([4, 4, 123, 22], fill=ORANGE)
    d.text((64, 13), "BURGUER REINO", font=ImageFont.truetype(FONT, 11), fill=(90, 20, 10), anchor="mm")
    f = ImageFont.truetype(FONT, 8)
    for i, (name, price) in enumerate((("Reina doble", "2 esm."), ("Reina", "1 esm."), ("Plebeya", "gratis*"))):
        y = 30 + i * 20
        d.rectangle([8, y, 36, y + 14], fill=(210, 140, 60))
        d.rectangle([8, y + 5, 36, y + 6], fill=(80, 180, 70))
        d.rectangle([8, y + 7, 36, y + 9], fill=(110, 60, 30))
        d.text((42, y + 7), name, font=f, fill=(255, 235, 200), anchor="lm")
        d.text((120, y + 7), price, font=f, fill=GOLD, anchor="rm")
    d.text((64, 90), "*la corona no se come", font=ImageFont.truetype(FONT, 6), fill=(255, 200, 150), anchor="mm")
    im.save(A / "textures/block/painting_burguerreino.png")
    im.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_burguerreino.png")
    src = "painting_valley_dawn"
    for rel in (f"models/block/{src}.json", f"blockstates/{src}.json", f"models/item/{src}.json"):
        (A / rel.replace(src, "painting_burguerreino")).write_text((A / rel).read_text(encoding="utf-8").replace(src, "painting_burguerreino"), encoding="utf-8")


def counter_textures():
    f = Image.new("RGBA", (32, 32), ORANGE + (255,))
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 31, 31], outline=(140, 60, 10, 255))
    d.rectangle([3, 3, 28, 12], fill=RED + (255,), outline=GOLD + (255,))
    for x, h in ((7, 4), (12, 6), (16, 8), (20, 6), (25, 4)):
        d.polygon([(x - 2, 11), (x, 11 - h + 4), (x + 2, 11)], fill=GOLD + (255,))
    d.rectangle([4, 17, 27, 27], fill=(90, 20, 10, 255), outline=GOLD + (255,))
    d.rectangle([9, 20, 22, 21], fill=(210, 140, 60, 255)); d.rectangle([9, 22, 22, 23], fill=(110, 60, 30, 255)); d.rectangle([9, 24, 22, 25], fill=(190, 120, 50, 255))
    f.save(A / "textures/block/burguer_counter_front.png")
    s = Image.new("RGBA", (32, 32), (200, 100, 15, 255)); ImageDraw.Draw(s).rectangle([0, 0, 31, 31], outline=(140, 60, 10, 255)); s.save(A / "textures/block/burguer_counter_side.png")
    t = Image.new("RGBA", (32, 32), (230, 225, 215, 255)); ImageDraw.Draw(t).rectangle([0, 0, 31, 31], outline=(160, 155, 145, 255)); t.save(A / "textures/block/burguer_counter_top.png")


def faces(n, s, u, dn):
    return {k: {"uv": [0, 0, 16, 16], "texture": v} for k, v in (("north", n), ("south", s), ("east", s), ("west", s), ("up", u), ("down", dn))}


def facing_state(name):
    bs = {"variants": {f"facing={f}": ({"model": f"beautifuldecor:block/{name}"} | ({"y": y} if y else {})) for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
    (A / f"blockstates/{name}.json").write_text(json.dumps(bs, indent=2), encoding="utf-8")
    (A / f"models/item/{name}.json").write_text(json.dumps({"parent": f"beautifuldecor:block/{name}"}, indent=2), encoding="utf-8")


def counter_models():
    m = {"textures": {"front": "beautifuldecor:block/burguer_counter_front", "side": "beautifuldecor:block/burguer_counter_side",
                      "top": "beautifuldecor:block/burguer_counter_top", "particle": "beautifuldecor:block/burguer_counter_side"},
         "elements": [{"from": [0, 0, 2], "to": [16, 13, 15], "faces": faces("#front", "#side", "#top", "#side")}]}
    (A / "models/block/burguer_counter.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    facing_state("burguer_counter")


def crown_model():
    gold = "minecraft:block/gold_block"
    def box(x0, y0, z0, x1, y1, z1, tex):
        return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {k: {"uv": [0, 0, 16, 16], "texture": tex} for k in ("north", "south", "east", "west", "up", "down")}}
    el = [box(1, 0, 5, 15, 4, 11, "#gold"), box(1, 4, 5, 3, 10, 11, "#gold"), box(4.5, 4, 5, 6.5, 8, 11, "#gold"), box(7, 4, 5, 9, 12, 11, "#gold"),
          box(9.5, 4, 5, 11.5, 8, 11, "#gold"), box(13, 4, 5, 15, 10, 11, "#gold"), box(7, 1, 4.5, 9, 3, 5, "#ruby"),
          box(1.5, 10, 7, 2.5, 11, 9, "#pearl"), box(13.5, 10, 7, 14.5, 11, 9, "#pearl"), box(7.5, 12, 7, 8.5, 13, 9, "#pearl")]
    m = {"textures": {"gold": gold, "ruby": "minecraft:block/redstone_block", "pearl": "minecraft:block/white_concrete", "particle": gold}, "elements": el}
    (A / "models/block/burguer_crown.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    facing_state("burguer_crown")


def loot(name):
    (D / f"loot_tables/blocks/{name}.json").write_text((D / "loot_tables/blocks/painting_valley_dawn.json").read_text(encoding="utf-8").replace("painting_valley_dawn", name), encoding="utf-8")


def lang():
    es = {"block.beautifuldecor.painting_burguerreino": "Cartel de menú Burguer Reino", "block.beautifuldecor.burguer_counter": "Mostrador Burguer Reino",
          "block.beautifuldecor.burguer_crown": "Corona Burguer Reino", "item.beautifuldecor.burguer_royal": "Hamburguesa Reina",
          "message.beautifuldecor.burguer_counter_price": "Una Hamburguesa Reina cuesta 1 esmeralda"}
    en = {"block.beautifuldecor.painting_burguerreino": "Burguer Reino Menu Board", "block.beautifuldecor.burguer_counter": "Burguer Reino Counter",
          "block.beautifuldecor.burguer_crown": "Burguer Reino Crown", "item.beautifuldecor.burguer_royal": "Queen Burger",
          "message.beautifuldecor.burguer_counter_price": "A Queen Burger costs 1 emerald"}
    for f, d_ in (("es_es.json", es), ("en_us.json", en)):
        p = A / "lang" / f
        d = json.loads(p.read_text(encoding="utf-8")); d.update(d_)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def tags():
    for tag, names in (("axe", ["painting_burguerreino"]), ("pickaxe", ["burguer_counter", "burguer_crown"])):
        p = ROOT / f"src/main/resources/data/minecraft/tags/blocks/mineable/{tag}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        for n in names:
            v = f"beautifuldecor:{n}"
            if v not in d["values"]:
                d["values"].append(v)
        p.write_text(json.dumps(d, indent=2), encoding="utf-8")


if __name__ == "__main__":
    burger_icon(); menu_painting(); counter_textures(); counter_models(); crown_model()
    for n in ("painting_burguerreino", "burguer_counter", "burguer_crown"):
        loot(n)
    lang(); tags()
    (A / "models/item/burguer_royal.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/burguer_royal"}}, indent=2), encoding="utf-8")
    print("escrito: Burguer Reino")
