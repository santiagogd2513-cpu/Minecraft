"""V7.2.0: radiola, audífonos, cuadro y canciones de 8 bits de Spotifly. Uso: python3 -m tools.v720.spotifly_assets"""
import json, subprocess, os
from pathlib import Path
from PIL import Image, ImageDraw
from ..v410.adkit import Cv, Track, write_wav, DUR
from ..v410 import adkit as K
from ..v6150.loteria_assets import txt, faces, el
from .spotifly import logo_spotifly, chip_song

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
INK, TEAL, VIOLET, WHITE, GREY = (22, 20, 46, 255), (20, 205, 170, 255), (120, 70, 235, 255), (245, 245, 248, 255), (120, 124, 134, 255)
SONGS = ("spotifly_song_1", "spotifly_song_2", "spotifly_song_3")


def save(im, name):
    im.save(A / "textures/block" / (name + ".png"))


def radio_textures():
    f = Image.new("RGBA", (32, 32), INK)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 31, 31], outline=(70, 66, 120, 255))
    d.rectangle([3, 4, 28, 9], fill=(10, 30, 28, 255), outline=TEAL)
    txt(d, "SPOTIFLY", 4, 5, TEAL, 1)
    for cx in (9, 23):
        d.ellipse([cx - 6, 13, cx + 6, 25], fill=(40, 38, 70, 255), outline=(150, 146, 200, 255))
        d.ellipse([cx - 3, 16, cx + 3, 22], fill=(15, 14, 30, 255), outline=VIOLET)
        d.point((cx, 19), fill=TEAL)
    d.rectangle([13, 28, 18, 29], fill=TEAL)
    save(f, "radiola_front")
    s = Image.new("RGBA", (32, 32), (36, 34, 70, 255))
    d = ImageDraw.Draw(s)
    d.rectangle([0, 0, 31, 31], outline=(70, 66, 120, 255))
    for y in range(4, 30, 4):
        d.line([4, y, 27, y], fill=(30, 28, 58, 255))
    save(s, "radiola_side")
    t = Image.new("RGBA", (32, 32), (50, 48, 90, 255))
    ImageDraw.Draw(t).rectangle([0, 0, 31, 31], outline=(70, 66, 120, 255))
    save(t, "radiola_top")
    on = Image.open(A / "textures/block/radiola_front.png").copy()
    d = ImageDraw.Draw(on)
    d.rectangle([3, 4, 28, 9], fill=(20, 80, 70, 255), outline=TEAL)
    txt(d, "SPOTIFLY", 4, 5, (255, 255, 255, 255), 1)
    for cx in (9, 23):
        d.ellipse([cx - 3, 16, cx + 3, 22], fill=TEAL, outline=(255, 255, 255, 255))
    save(on, "radiola_front_on")


def phones_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.arc([2, 1, 13, 12], 180, 360, fill=INK, width=2)
    d.rectangle([1, 7, 4, 13], fill=TEAL, outline=INK)
    d.rectangle([11, 7, 14, 13], fill=TEAL, outline=INK)
    d.point((2, 9), fill=WHITE); d.point((12, 9), fill=WHITE)
    im.save(A / "textures/item/spotifly_headphones.png")
    (A / "models/item/spotifly_headphones.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/spotifly_headphones"}}, indent=2), encoding="utf-8")


def models():
    base = lambda front: {"textures": {"front": f"beautifuldecor:block/{front}", "side": "beautifuldecor:block/radiola_side", "top": "beautifuldecor:block/radiola_top",
                                       "particle": "beautifuldecor:block/radiola_side"},
                          "elements": [el([1, 0, 4], [15, 9, 12], "front", "side", "top"), el([6, 9, 7], [10, 11, 9], "side", "side", "top")]}
    (A / "models/block/radiola.json").write_text(json.dumps(base("radiola_front"), indent=2), encoding="utf-8")
    (A / "models/block/radiola_on.json").write_text(json.dumps(base("radiola_front_on"), indent=2), encoding="utf-8")
    (A / "models/item/radiola.json").write_text(json.dumps({"parent": "beautifuldecor:block/radiola"}, indent=2), encoding="utf-8")
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    bs = {"variants": {}}
    for k, v in rot.items():
        for s in range(4):
            e = {"model": "beautifuldecor:block/radiola" + ("_on" if s else "")}
            if v:
                e["y"] = v
            bs["variants"][f"facing={k},song={s}"] = e
    (A / "blockstates/radiola.json").write_text(json.dumps(bs, indent=2), encoding="utf-8")
    for n in ("radiola", "painting_spotifly"):
        src = (D / "loot_tables/blocks/rojotoro_minibar.json").read_text(encoding="utf-8").replace("rojotoro_minibar", n)
        (D / f"loot_tables/blocks/{n}.json").write_text(src, encoding="utf-8")


def painting():
    cv = Cv()
    cv.gradient((24, 20, 70), (20, 205, 170), 0, 100)
    cv.rays(50, 50, 16, (255, 255, 255, 70), (20, 205, 170, 0), 0.3, 150)
    logo_spotifly(cv, 50, 40, 26)
    fr = cv.done().convert("RGB")
    crop = fr.crop((0, 48, 384, 336)).resize((128, 96), Image.LANCZOS)
    ImageDraw.Draw(crop).rectangle([0, 0, 127, 95], outline=(20, 20, 30))
    crop.save(A / "textures/block/painting_spotifly.png")
    crop.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_spotifly.png")
    src = "painting_valley_dawn"
    for rel in (f"models/block/{src}.json", f"blockstates/{src}.json", f"models/item/{src}.json"):
        (A / rel.replace(src, "painting_spotifly")).write_text((A / rel).read_text(encoding="utf-8").replace(src, "painting_spotifly"), encoding="utf-8")


def songs():
    m1 = [72, 0, 76, 79, 84, 0, 79, 76, 74, 0, 77, 81, 86, 0, 81, 77, 72, 0, 76, 79, 91, 0, 88, 84, 83, 0, 79, 74, 79, 0, 0, 0]
    m2 = [69, 72, 76, 72, 69, 72, 76, 81, 67, 71, 74, 71, 67, 71, 74, 79, 65, 69, 72, 69, 65, 69, 72, 77, 67, 71, 74, 79, 83, 79, 74, 71]
    m3 = [60, 0, 0, 67, 0, 0, 72, 0, 71, 0, 67, 0, 62, 0, 0, 69, 0, 0, 74, 0, 72, 0, 69, 0, 64, 0, 0, 71, 0, 0, 76, 0]
    b1 = [48, 48, 53, 53, 43, 43, 55, 55]
    b2 = [45, 45, 43, 43, 41, 41, 43, 43]
    b3 = [48, 0, 52, 0, 43, 0, 47, 0]
    out = A / "sounds"
    for name, mel, bass, bpm in (("spotifly_song_1", m1 * 6, b1 * 6, 124), ("spotifly_song_2", m2 * 6, b2 * 6, 138), ("spotifly_song_3", m3 * 6, b3 * 6, 100)):
        beat = 60 / bpm
        dur = len(mel) * beat / 2 + 0.5
        tr = Track(dur)
        chip_song(tr, 0.0, mel, bass, bpm, 0.2)
        wav = f"/tmp/{name}.wav"
        write_wav(wav, tr.master())
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", "-ac", "1", str(out / (name + ".ogg"))], check=True)
        print(name, round(dur), "s")
    p = A / "sounds.json"
    j = json.loads(p.read_text(encoding="utf-8"))
    for n in SONGS:
        j[n] = {"subtitle": "subtitles.beautifuldecor.spotifly_song", "sounds": [{"name": "beautifuldecor:" + n, "stream": True}]}
    p.write_text(json.dumps(j, indent=2), encoding="utf-8")


def tags():
    p = ROOT / "src/main/resources/data/minecraft/tags/blocks/mineable"
    for f, names in (("axe.json", ("painting_spotifly",)), ("pickaxe.json", ("radiola",))):
        q = p / f
        d = json.loads(q.read_text(encoding="utf-8"))
        for n in names:
            if "beautifuldecor:" + n not in d["values"]:
                d["values"].append("beautifuldecor:" + n)
        q.write_text(json.dumps(d, indent=2), encoding="utf-8")


ES = {"block.beautifuldecor.painting_spotifly": "Cartel Spotifly", "block.beautifuldecor.radiola": "Radiola Spotifly",
      "item.beautifuldecor.spotifly_headphones": "Audífonos Spotifly", "subtitles.beautifuldecor.spotifly_song": "Suena música de Spotifly",
      "message.beautifuldecor.radiola_song": "Radiola: canción %s", "message.beautifuldecor.radiola_off": "Radiola apagada",
      "message.beautifuldecor.phones_song": "Audífonos: canción %s", "message.beautifuldecor.phones_off": "Audífonos apagados"}
EN = {"block.beautifuldecor.painting_spotifly": "Spotifly Poster", "block.beautifuldecor.radiola": "Spotifly Radiola",
      "item.beautifuldecor.spotifly_headphones": "Spotifly Headphones", "subtitles.beautifuldecor.spotifly_song": "Spotifly music plays",
      "message.beautifuldecor.radiola_song": "Radiola: song %s", "message.beautifuldecor.radiola_off": "Radiola off",
      "message.beautifuldecor.phones_song": "Headphones: song %s", "message.beautifuldecor.phones_off": "Headphones off"}


def lang():
    for f, d in (("es_es.json", ES), ("en_us.json", EN)):
        p = A / "lang" / f
        s = p.read_text(encoding="utf-8")
        if '"block.beautifuldecor.radiola"' in s:
            continue
        k = '  "itemGroup.beautifuldecor_parts"'
        s = s.replace(k, "".join(f'  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)},\n' for a, b in d.items()) + k, 1)
        json.loads(s)
        p.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    radio_textures(); phones_icon(); models(); painting(); songs(); tags(); lang()
    print("escrito: radiola, audífonos, cuadro, canciones y textos de Spotifly")
