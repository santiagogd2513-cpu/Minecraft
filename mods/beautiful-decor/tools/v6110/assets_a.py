"""V6.11.0: recursos de los televisores de anuncios nuevos (3x2, 4x2, 6x4) y de las bocinas montadas en la pared. Uso: python3 -m tools.v6110.assets_a"""
import copy
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
SIZES = {"ad_tv_3x2": (3, 2, "3x2"), "ad_tv_4x2": (4, 2, "4x2"), "ad_tv_6x4": (6, 4, "6x4")}


def tvs():
    base_state = (A / "blockstates/ad_tv.json").read_text(encoding="utf-8")
    base_loot = (D / "loot_tables/blocks/ad_tv.json").read_text(encoding="utf-8")
    for name, (w, h, label) in SIZES.items():
        (A / f"blockstates/{name}.json").write_text(base_state, encoding="utf-8")
        (D / f"loot_tables/blocks/{name}.json").write_text(base_loot.replace("beautifuldecor:ad_tv", f"beautifuldecor:{name}"), encoding="utf-8")
        (A / f"models/item/{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"beautifuldecor:item/{name}"}}, indent=1), encoding="utf-8")
        # ícono: una pantalla con la proporción del televisor y un anuncio de colores
        im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        pw = 28
        ph = max(8, round(pw * h / w))
        x0, y0 = 2, (32 - ph) // 2
        d.rectangle([x0, y0, x0 + pw - 1, y0 + ph - 1], fill=(20, 20, 26, 255), outline=(70, 72, 84, 255))
        d.rectangle([x0 + 2, y0 + 2, x0 + pw - 3, y0 + ph - 3], fill=(26, 60, 130, 255))
        cx = x0 + pw // 2
        cy = y0 + ph // 2
        r = max(3, ph // 2 - 3)
        d.rectangle([cx - r, cy - r, cx + r, cy + r], fill=(230, 80, 60, 255))
        d.rectangle([cx - r + 1, cy - 1, cx + r - 1, cy], fill=(255, 205, 50, 255))
        d.text((x0 + 3, y0 + ph - 9), label, fill=(255, 255, 255, 255))
        im.save(A / f"textures/item/{name}.png")


def shift_model(name, new_name):
    """Copia el modelo de la bocina corriendo todo hacia atrás (+z) hasta que su espalda toque la pared (z = 16)."""
    m = json.loads((A / f"models/block/{name}.json").read_text(encoding="utf-8"))
    zmax = max(e["to"][2] for e in m["elements"])
    dz = 16 - zmax
    for e in m["elements"]:
        e["from"][2] = round(e["from"][2] + dz, 4)
        e["to"][2] = round(e["to"][2] + dz, 4)
        if "rotation" in e and "origin" in e["rotation"]:
            e["rotation"]["origin"][2] = round(e["rotation"]["origin"][2] + dz, 4)
    (A / f"models/block/{new_name}.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    return dz


def speakers():
    for n in ("pa_speaker", "radio_speaker"):
        dz = shift_model(n, n + "_wall")
        variants = {}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for wall, model in (("false", n), ("true", n + "_wall")):
                v = {"model": f"beautifuldecor:block/{model}"}
                if y:
                    v["y"] = y
                variants[f"facing={f},wall={wall}"] = v
        (A / f"blockstates/{n}.json").write_text(json.dumps({"variants": variants}, indent=2), encoding="utf-8")
        print(n, "desplazamiento en la pared:", dz)


def lang():
    add = {"es_es.json": {"block.beautifuldecor.ad_tv_3x2": "Televisor de anuncios (3x2, pared)", "block.beautifuldecor.ad_tv_4x2": "Televisor de anuncios (4x2, pared)",
                          "block.beautifuldecor.ad_tv_6x4": "Televisor de anuncios gigante (6x4, pared)"},
           "en_us.json": {"block.beautifuldecor.ad_tv_3x2": "Ad Television (3x2, wall)", "block.beautifuldecor.ad_tv_4x2": "Ad Television (4x2, wall)",
                          "block.beautifuldecor.ad_tv_6x4": "Giant Ad Television (6x4, wall)"}}
    for f, d in add.items():
        p = A / "lang" / f
        s = p.read_text(encoding="utf-8")
        if '"block.beautifuldecor.ad_tv_6x4"' in s:
            continue
        k = '  "itemGroup.beautifuldecor_parts"'
        assert k in s
        s = s.replace(k, "".join(f'  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)},\n' for a, b in d.items()) + k, 1)
        json.loads(s)
        p.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    tvs(); speakers(); lang()
    print("escrito: televisores de anuncios nuevos y bocinas en pared")
