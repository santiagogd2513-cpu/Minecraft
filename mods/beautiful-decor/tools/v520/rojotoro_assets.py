"""V5.2.0: texturas y modelos de Rojo Toro: lata (ítem), minibar (bloque) y cartel de pared (cuadro). Uso: python3 -m tools.v520.rojotoro_assets"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from .rojotoro import frame_rojotoro

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
RED, GOLD, SILVER, DARK = (205, 24, 34), (255, 205, 50), (190, 196, 208), (30, 30, 38)


def can_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 2, 11, 14], fill=SILVER + (255,))
    d.rectangle([4, 2, 11, 3], fill=(150, 156, 170, 255))
    d.rectangle([4, 14, 11, 14], fill=(130, 136, 150, 255))
    d.rectangle([4, 6, 11, 11], fill=RED + (255,))
    d.rectangle([4, 6, 11, 6], fill=GOLD + (255,))
    d.rectangle([4, 11, 11, 11], fill=GOLD + (255,))
    d.rectangle([5, 3, 5, 13], fill=(255, 255, 255, 90))
    for p in ((8, 7), (9, 7), (8, 8), (7, 9), (8, 9), (7, 10)):
        d.point(p, fill=(255, 255, 255, 255))
    d.rectangle([3, 1, 3, 1], fill=(0, 0, 0, 0))
    im = im.resize((16, 16), Image.NEAREST)
    im.save(A / "textures/item/rojotoro_can.png")


def minibar_textures():
    f = Image.new("RGBA", (32, 32), RED + (255,))
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 31, 31], outline=(120, 10, 18, 255))
    d.rectangle([3, 3, 28, 17], fill=(40, 60, 90, 255), outline=SILVER + (255,))      # ventana con latas
    for i in range(5):
        x = 5 + i * 5
        d.rectangle([x, 8, x + 3, 16], fill=SILVER + (255,))
        d.rectangle([x, 11, x + 3, 13], fill=RED + (255,))
        d.rectangle([x, 8, x + 3, 8], fill=(150, 156, 170, 255))
    d.rectangle([3, 19, 28, 20], fill=GOLD + (255,))
    for p in ((15, 22), (16, 22), (15, 23), (14, 24), (15, 24), (14, 25), (15, 26)):
        d.point(p, fill=(255, 255, 255, 255))
    d.rectangle([26, 22, 27, 29], fill=SILVER + (255,))
    d.rectangle([4, 27, 12, 28], fill=GOLD + (255,))
    f.save(A / "textures/block/rojotoro_minibar_front.png")
    s = Image.new("RGBA", (32, 32), (170, 18, 28, 255))
    d = ImageDraw.Draw(s)
    d.rectangle([0, 0, 31, 31], outline=(120, 10, 18, 255))
    d.rectangle([2, 14, 29, 16], fill=GOLD + (255,))
    s.save(A / "textures/block/rojotoro_minibar_side.png")
    t = Image.new("RGBA", (32, 32), SILVER + (255,))
    d = ImageDraw.Draw(t)
    d.rectangle([0, 0, 31, 31], outline=(130, 136, 150, 255))
    d.rectangle([4, 4, 27, 27], outline=(150, 156, 170, 255))
    t.save(A / "textures/block/rojotoro_minibar_top.png")


def faces(n, s, u, d_):
    return {k: {"uv": [0, 0, 16, 16], "texture": v} for k, v in (("north", n), ("south", s), ("east", s), ("west", s), ("up", u), ("down", d_))}


def minibar_models():
    body = {"textures": {"front": "beautifuldecor:block/rojotoro_minibar_front", "side": "beautifuldecor:block/rojotoro_minibar_side",
                         "top": "beautifuldecor:block/rojotoro_minibar_top", "particle": "beautifuldecor:block/rojotoro_minibar_side"},
            "elements": [{"from": [1, 0, 2], "to": [15, 14, 14], "faces": faces("#front", "#side", "#top", "#side")}]}
    (A / "models/block/rojotoro_minibar.json").write_text(json.dumps(body, indent=2), encoding="utf-8")
    (A / "models/item/rojotoro_minibar.json").write_text(json.dumps({"parent": "beautifuldecor:block/rojotoro_minibar"}, indent=2), encoding="utf-8")
    bs = {"variants": {f"facing={f}": ({"model": "beautifuldecor:block/rojotoro_minibar"} | ({"y": y} if y else {}))
                       for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
    (A / "blockstates/rojotoro_minibar.json").write_text(json.dumps(bs, indent=2), encoding="utf-8")
    (A / "models/item/rojotoro_can.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/rojotoro_can"}}, indent=2), encoding="utf-8")


def painting():
    fr = frame_rojotoro(8.0).convert("RGB")
    crop = fr.crop((0, 48, 384, 336)).resize((128, 96), Image.LANCZOS)
    # marco fino oscuro para que se lea como cuadro
    d = ImageDraw.Draw(crop)
    d.rectangle([0, 0, 127, 95], outline=(30, 20, 20))
    crop.save(A / "textures/block/painting_rojotoro.png")
    crop.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_rojotoro.png")
    src = "painting_valley_dawn"
    m = (A / f"models/block/{src}.json").read_text(encoding="utf-8").replace(src, "painting_rojotoro")
    (A / "models/block/painting_rojotoro.json").write_text(m, encoding="utf-8")
    (A / "blockstates/painting_rojotoro.json").write_text((A / f"blockstates/{src}.json").read_text(encoding="utf-8").replace(src, "painting_rojotoro"), encoding="utf-8")
    (A / "models/item/painting_rojotoro.json").write_text((A / f"models/item/{src}.json").read_text(encoding="utf-8").replace(src, "painting_rojotoro"), encoding="utf-8")


def loot(name):
    (D / f"loot_tables/blocks/{name}.json").write_text((D / "loot_tables/blocks/painting_valley_dawn.json").read_text(encoding="utf-8").replace("painting_valley_dawn", name), encoding="utf-8")


def tags():
    for tag, name in (("axe", "painting_rojotoro"), ("pickaxe", "rojotoro_minibar")):
        p = ROOT / f"src/main/resources/data/minecraft/tags/blocks/mineable/{tag}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        v = f"beautifuldecor:{name}"
        if v not in d["values"]:
            d["values"].append(v)
            p.write_text(json.dumps(d, indent=2), encoding="utf-8")


if __name__ == "__main__":
    can_icon(); minibar_textures(); minibar_models(); painting(); loot("painting_rojotoro"); loot("rojotoro_minibar"); tags()
    print("escrito: lata, minibar y cartel de Rojo Toro")
