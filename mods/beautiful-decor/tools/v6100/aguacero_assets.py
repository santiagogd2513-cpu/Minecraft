"""V6.10.0: texturas, modelos, cartel (logo), botín, etiqueta y lang de Aguacero Bomberos. Uso: python3 -m tools.v6100.aguacero_assets"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from .aguacero import logo_aguacero
from ..v410.adkit import Cv

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
NAVY, GOLD, STEEL = (16, 30, 84), (255, 205, 50), (200, 205, 215)


def extinguisher_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([5, 5, 10, 14], fill=(205, 30, 30, 255), outline=(110, 10, 14, 255))
    d.rectangle([5, 8, 10, 9], fill=(245, 245, 250, 255))
    d.rectangle([6, 3, 9, 4], fill=(60, 62, 70, 255))
    d.rectangle([9, 2, 13, 3], fill=(60, 62, 70, 255))
    d.line([(13, 3), (14, 6)], fill=(90, 92, 100, 255))
    d.rectangle([6, 6, 6, 13], fill=(255, 255, 255, 70))
    im.save(A / "textures/item/fire_extinguisher.png")


def hose_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([2, 3, 11, 12], outline=(255, 205, 50, 255), width=2)
    d.ellipse([5, 6, 8, 9], outline=(200, 150, 20, 255))
    d.rectangle([10, 10, 14, 12], fill=(60, 62, 70, 255))
    d.rectangle([13, 9, 14, 13], fill=(205, 30, 30, 255))
    d.point((15, 11), fill=(140, 205, 255, 255)); d.point((15, 9), fill=(140, 205, 255, 255)); d.point((15, 13), fill=(140, 205, 255, 255))
    d.point((4, 4), fill=(255, 255, 255, 255))
    im.save(A / "textures/item/fire_hose.png")


def painting():
    cv = Cv()
    cv.gradient((16, 70, 160), (6, 22, 60), 0, 100)
    cv.rays(50, 50, 16, (90, 190, 255, 90), (16, 70, 160, 0), 0.3, 150)
    logo_aguacero(cv, 50, 40, 30)
    fr = cv.done().convert("RGB")
    crop = fr.crop((0, 48, 384, 336)).resize((128, 96), Image.LANCZOS)
    ImageDraw.Draw(crop).rectangle([0, 0, 127, 95], outline=(20, 20, 30))
    crop.save(A / "textures/block/painting_aguacero.png")
    crop.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_aguacero.png")
    src = "painting_valley_dawn"
    for rel in (f"models/block/{src}.json", f"blockstates/{src}.json", f"models/item/{src}.json"):
        (A / rel.replace(src, "painting_aguacero")).write_text((A / rel).read_text(encoding="utf-8").replace(src, "painting_aguacero"), encoding="utf-8")
    for n in ("fire_extinguisher", "fire_hose"):
        (A / f"models/item/{n}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{n}"}}, indent=2), encoding="utf-8")
    (D / "loot_tables/blocks/painting_aguacero.json").write_text((D / "loot_tables/blocks/painting_valley_dawn.json").read_text(encoding="utf-8").replace("painting_valley_dawn", "painting_aguacero"), encoding="utf-8")


def tags():
    p = ROOT / "src/main/resources/data/minecraft/tags/blocks/mineable/axe.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    if "beautifuldecor:painting_aguacero" not in d["values"]:
        d["values"].append("beautifuldecor:painting_aguacero")
        p.write_text(json.dumps(d, indent=2), encoding="utf-8")


def lang():
    add = {
        "es_es.json": {"block.beautifuldecor.painting_aguacero": "Cartel Aguacero Bomberos", "item.beautifuldecor.fire_extinguisher": "Extintor Aguacero Bomberos",
                       "item.beautifuldecor.fire_hose": "Manguera Aguacero Bomberos"},
        "en_us.json": {"block.beautifuldecor.painting_aguacero": "Aguacero Firefighters Poster", "item.beautifuldecor.fire_extinguisher": "Aguacero Fire Extinguisher",
                       "item.beautifuldecor.fire_hose": "Aguacero Fire Hose"}}
    for f, d in add.items():
        p = A / "lang" / f
        s = p.read_text(encoding="utf-8")
        if '"item.beautifuldecor.fire_hose"' in s:
            continue
        k = '  "itemGroup.beautifuldecor_parts"'
        assert k in s
        s = s.replace(k, "".join(f'  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)},\n' for a, b in d.items()) + k, 1)
        json.loads(s)
        p.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    extinguisher_icon(); hose_icon(); painting(); tags(); lang()
    print("escrito: extintor, manguera y cartel de Aguacero Bomberos")
