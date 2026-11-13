"""V6.11.0: micrófono HS News, cartel con el logo, etiqueta, botín y textos (lang) del noticiero. Uso: python3 -m tools.v6110.hsnews_assets"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from .hsnews import logo_hsnews
from ..v410.adkit import Cv

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"


def mic_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([6, 2, 10, 6], fill=(40, 42, 52, 255), outline=(20, 22, 30, 255))        # cabeza
    d.rectangle([7, 3, 9, 5], fill=(110, 116, 130, 255))
    d.rectangle([5, 7, 11, 8], fill=(200, 30, 40, 255))                                  # anillo rojo
    d.rectangle([6, 9, 10, 14], fill=(0, 110, 220, 255), outline=(0, 60, 130, 255))      # mango azul HS
    d.rectangle([7, 10, 9, 11], fill=(255, 255, 255, 255))
    d.point((6, 3), fill=(255, 255, 255, 255))
    im.save(A / "textures/item/hsnews_mic.png")
    (A / "models/item/hsnews_mic.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/hsnews_mic"}}, indent=2), encoding="utf-8")


def painting():
    cv = Cv()
    cv.gradient((0, 70, 170), (0, 30, 100), 0, 100)
    cv.rays(50, 50, 16, (60, 160, 250, 90), (0, 70, 170, 0), 0.3, 150)
    logo_hsnews(cv, 50, 38, 26)
    fr = cv.done().convert("RGB")
    crop = fr.crop((0, 48, 384, 336)).resize((128, 96), Image.LANCZOS)
    ImageDraw.Draw(crop).rectangle([0, 0, 127, 95], outline=(20, 20, 30))
    crop.save(A / "textures/block/painting_hsnews.png")
    crop.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_hsnews.png")
    src = "painting_valley_dawn"
    for rel in (f"models/block/{src}.json", f"blockstates/{src}.json", f"models/item/{src}.json"):
        (A / rel.replace(src, "painting_hsnews")).write_text((A / rel).read_text(encoding="utf-8").replace(src, "painting_hsnews"), encoding="utf-8")
    (D / "loot_tables/blocks/painting_hsnews.json").write_text((D / "loot_tables/blocks/painting_valley_dawn.json").read_text(encoding="utf-8").replace("painting_valley_dawn", "painting_hsnews"), encoding="utf-8")


def tags():
    p = ROOT / "src/main/resources/data/minecraft/tags/blocks/mineable/axe.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    if "beautifuldecor:painting_hsnews" not in d["values"]:
        d["values"].append("beautifuldecor:painting_hsnews")
        p.write_text(json.dumps(d, indent=2), encoding="utf-8")


ES = {
    "block.beautifuldecor.painting_hsnews": "Cartel HS News", "item.beautifuldecor.hsnews_mic": "Micrófono HS News",
    "message.beautifuldecor.hsnews_interview": "Tu entrevista saldrá en el noticiero de HS News",
    "news.construction": "Nueva construcción de %s en X: %s, Z: %s",
    "news.wildfire": "¡Incendio forestal en X: %s, Z: %s! Se pide alejarse de la zona",
    "news.new_player": "Un nuevo jugador llegó al mundo: %s",
    "news.back": "%s volvió al mundo",
    "news.interview": "Entrevista exclusiva con %s: «%s»",
    "news.quote.0": "Yo solo pasaba por aquí",
    "news.quote.1": "Eso no estaba ahí ayer, lo juro",
    "news.quote.2": "Prometo no volver a prender nada",
    "news.quote.3": "Mis creepers son muy mansos",
    "news.quote.4": "Todo es culpa del lag",
    "news.quote.5": "Estaba construyendo, de verdad",
    "news.quote.6": "No sé de qué me habla, oficial",
    "news.quote.7": "Sin comentarios, mi abogado es un aldeano",
    "news.empty": "Sin novedades por ahora: el mundo duerme tranquilo",
    "news.live": "EN VIVO", "news.breaking": "ÚLTIMA HORA", "news.time": "Hora: %s",
    "news.weather.clear": "Cielo despejado", "news.weather.rain": "Llueve: escampa en %s min",
    "news.weather.rain_soon": "Probable lluvia en %s min", "news.weather.storm": "Tormenta eléctrica: ¡a resguardarse!",
}
EN = {
    "block.beautifuldecor.painting_hsnews": "HS News Poster", "item.beautifuldecor.hsnews_mic": "HS News Microphone",
    "message.beautifuldecor.hsnews_interview": "Your interview will air on HS News",
    "news.construction": "New build by %s at X: %s, Z: %s",
    "news.wildfire": "Forest fire at X: %s, Z: %s! Stay away from the area",
    "news.new_player": "A new player arrived in the world: %s",
    "news.back": "%s is back in the world",
    "news.interview": "Exclusive interview with %s: \"%s\"",
    "news.quote.0": "I was just passing by",
    "news.quote.1": "That wasn't there yesterday, I swear",
    "news.quote.2": "I promise not to light anything again",
    "news.quote.3": "My creepers are very tame",
    "news.quote.4": "It's all the lag's fault",
    "news.quote.5": "I was building, really",
    "news.quote.6": "I don't know what you mean, officer",
    "news.quote.7": "No comment, my lawyer is a villager",
    "news.empty": "Nothing new for now: the world sleeps peacefully",
    "news.live": "LIVE", "news.breaking": "BREAKING", "news.time": "Time: %s",
    "news.weather.clear": "Clear skies", "news.weather.rain": "Raining: clearing in %s min",
    "news.weather.rain_soon": "Rain likely in %s min", "news.weather.storm": "Thunderstorm: take cover!",
}


def lang():
    for f, d in (("es_es.json", ES), ("en_us.json", EN)):
        p = A / "lang" / f
        s = p.read_text(encoding="utf-8")
        if '"news.weather.storm"' in s:
            continue
        k = '  "itemGroup.beautifuldecor_parts"'
        assert k in s
        s = s.replace(k, "".join(f'  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)},\n' for a, b in d.items()) + k, 1)
        json.loads(s)
        p.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    mic_icon(); painting(); tags(); lang()
    print("escrito: micrófono, cartel y textos de HS News")
