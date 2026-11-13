"""V6.7.0: texturas, modelos, cartel (logo), botín, etiqueta y lang de Polisía Nasional. Uso: python3 -m tools.v670.polisia_assets"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from .polisia import frame_polisia, logo_polisia
from ..v410.adkit import Cv

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
NAVY, GOLD, STEEL = (16, 30, 84), (255, 205, 50), (200, 205, 215)


def handcuffs_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for cx in (4, 11):
        d.ellipse([cx - 3, 6, cx + 3, 12], outline=STEEL + (255,), width=2)
        d.point((cx, 5), fill=(140, 146, 160, 255))
    d.rectangle([7, 8, 8, 9], fill=(140, 146, 160, 255))
    d.line([(7, 4), (8, 3), (9, 4)], fill=(110, 116, 130, 255))
    d.point((4, 12), fill=(255, 255, 255, 255))
    im.save(A / "textures/item/handcuffs.png")


def taser_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([2, 4, 11, 7], fill=NAVY + (255,), outline=(8, 14, 50, 255))          # cañón
    d.rectangle([3, 5, 10, 5], fill=GOLD + (255,))
    d.rectangle([4, 8, 7, 14], fill=(40, 44, 60, 255), outline=(20, 22, 34, 255))     # mango
    d.rectangle([11, 5, 14, 6], fill=(255, 240, 120, 255))                              # puntas
    d.point((15, 4), fill=(140, 210, 255, 255)); d.point((15, 7), fill=(140, 210, 255, 255)); d.point((14, 5), fill=(255, 255, 255, 255))
    d.line([(12, 2), (13, 3), (12, 3), (13, 4)], fill=(140, 210, 255, 255))
    im.save(A / "textures/item/taser.png")


def painting():
    cv = Cv()
    cv.gradient((14, 26, 80), (6, 8, 30), 0, 100)
    cv.rays(50, 50, 16, (60, 110, 230, 90), (14, 26, 80, 0), 0.3, 150)
    logo_polisia(cv, 50, 44, 34)
    fr = cv.done().convert("RGB")
    crop = fr.crop((0, 48, 384, 336)).resize((128, 96), Image.LANCZOS)
    ImageDraw.Draw(crop).rectangle([0, 0, 127, 95], outline=(20, 20, 30))
    crop.save(A / "textures/block/painting_polisia.png")
    crop.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_polisia.png")
    src = "painting_valley_dawn"
    for rel in (f"models/block/{src}.json", f"blockstates/{src}.json", f"models/item/{src}.json"):
        (A / rel.replace(src, "painting_polisia")).write_text((A / rel).read_text(encoding="utf-8").replace(src, "painting_polisia"), encoding="utf-8")
    for n in ("handcuffs", "taser"):
        (A / f"models/item/{n}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{n}"}}, indent=2), encoding="utf-8")
    (D / "loot_tables/blocks/painting_polisia.json").write_text((D / "loot_tables/blocks/painting_valley_dawn.json").read_text(encoding="utf-8").replace("painting_valley_dawn", "painting_polisia"), encoding="utf-8")


def tags():
    p = ROOT / "src/main/resources/data/minecraft/tags/blocks/mineable/axe.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    if "beautifuldecor:painting_polisia" not in d["values"]:
        d["values"].append("beautifuldecor:painting_polisia")
        p.write_text(json.dumps(d, indent=2), encoding="utf-8")


def lang():
    add = {
        "es_es.json": {"block.beautifuldecor.painting_polisia": "Cartel Polisía Nasional", "item.beautifuldecor.handcuffs": "Esposas Polisía Nasional",
                       "item.beautifuldecor.taser": "Pistola eléctrica Polisía Nasional",
                       "message.beautifuldecor.cuffed": "¡Te pusieron las esposas! No puedes usar nada.", "message.beautifuldecor.uncuffed": "Te quitaron las esposas.",
                       "message.beautifuldecor.cuffed_other": "Esposaste a %s", "message.beautifuldecor.uncuffed_other": "Soltaste a %s"},
        "en_us.json": {"block.beautifuldecor.painting_polisia": "Polisía Nasional Poster", "item.beautifuldecor.handcuffs": "Polisía Nasional Handcuffs",
                       "item.beautifuldecor.taser": "Polisía Nasional Taser",
                       "message.beautifuldecor.cuffed": "You've been handcuffed! You can't use anything.", "message.beautifuldecor.uncuffed": "The handcuffs came off.",
                       "message.beautifuldecor.cuffed_other": "You handcuffed %s", "message.beautifuldecor.uncuffed_other": "You released %s"}}
    for f, d in add.items():
        p = A / "lang" / f
        s = p.read_text(encoding="utf-8")
        if '"item.beautifuldecor.taser"' in s:
            continue
        k = '  "itemGroup.beautifuldecor_parts"'
        assert k in s
        s = s.replace(k, "".join(f'  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)},\n' for a, b in d.items()) + k, 1)
        json.loads(s)
        p.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    handcuffs_icon(); taser_icon(); painting(); tags(); lang()
    print("escrito: esposas, pistola eléctrica y cartel de Polisía Nasional")
