"""V5.2.1: recursos del televisor de anuncios (ad_tv): modelo de techo, estado de bloque, ícono y botín. Uso: python3 -m tools.v520.adtv"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"


def face(tex, u=(0, 0, 16, 16)):
    return {"uv": list(u), "texture": tex}


def ceiling_model():
    m = {"textures": {"particle": "minecraft:block/black_concrete", "all": "minecraft:block/black_concrete",
                      "metal": "minecraft:block/iron_block", "front": "beautifuldecor:block/ad_screen_off"},
         "elements": [
             {"from": [0, 2, 6.5], "to": [16, 14, 9.5],
              "faces": {"north": face("#front", (0, 2, 16, 14)), "south": face("#all"), "east": face("#all", (0, 0, 3, 12)),
                        "west": face("#all", (13, 0, 16, 12)), "up": face("#all", (0, 0, 16, 3)), "down": face("#all", (0, 0, 16, 3))}},
             {"from": [2, 14, 7], "to": [4, 16, 9], "faces": {k: face("#metal", (0, 0, 2, 2)) for k in ("north", "south", "east", "west", "up", "down")}},
             {"from": [12, 14, 7], "to": [14, 16, 9], "faces": {k: face("#metal", (0, 0, 2, 2)) for k in ("north", "south", "east", "west", "up", "down")}},
         ]}
    (A / "models/block/ad_tv_ceiling.json").write_text(json.dumps(m, indent=1), encoding="utf-8")


def blockstate():
    parts = []
    for ceil, model in (("false", "ad_panel"), ("true", "ad_tv_ceiling")):
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            apply = {"model": f"beautifuldecor:block/{model}"}
            if y:
                apply["y"] = y
            parts.append({"when": {"facing": f, "ceiling": ceil}, "apply": apply})
    (A / "blockstates/ad_tv.json").write_text(json.dumps({"multipart": parts}, indent=1), encoding="utf-8")


def icon():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([2, 9, 29, 24], fill=(20, 20, 26, 255), outline=(60, 62, 72, 255))
    d.rectangle([4, 11, 27, 22], fill=(26, 60, 130, 255))
    d.rectangle([11, 11, 20, 22], fill=(230, 80, 60, 255))
    d.rectangle([12, 14, 19, 15], fill=(255, 205, 50, 255))
    d.rectangle([6, 5, 7, 8], fill=(150, 154, 164, 255))
    d.rectangle([24, 5, 25, 8], fill=(150, 154, 164, 255))
    d.rectangle([4, 4, 27, 5], fill=(120, 124, 134, 255))
    im.save(A / "textures/item/ad_tv.png")
    (A / "models/item/ad_tv.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/ad_tv"}}, indent=1), encoding="utf-8")


def loot():
    s = (D / "loot_tables/blocks/ad_screen_small.json").read_text(encoding="utf-8").replace("ad_screen_small", "ad_tv")
    (D / "loot_tables/blocks/ad_tv.json").write_text(s, encoding="utf-8")


if __name__ == "__main__":
    ceiling_model(); blockstate(); icon(); loot()
    for lang, f, n in (("es", "es_es.json", "Televisor de anuncios (2x1, pared o techo)"), ("en", "en_us.json", "Ad Television (2x1, wall or ceiling)")):
        p = A / "lang" / f
        d = json.loads(p.read_text(encoding="utf-8"))
        d["block.beautifuldecor.ad_tv"] = n
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("escrito: televisor de anuncios")
