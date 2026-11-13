"""V6.14.0: texturas de las skins del superdeportivo (carreras, policía, callejero, taxi): mismo atlas que el original con otra pintura y rotulado.
Uso (desde la raíz): python3 -m tools.v6140.car_skins --write | --preview /tmp/x.png"""
import argparse
from pathlib import Path

from PIL import Image

from ..v210 import vcommon as vc
from ..v370 import supercar as S

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/beautifuldecor/textures/entity"
VISIBLE = {1: ("racing",), 2: ("cop", "bar", "bar_red", "bar_blue", "siren_btn"), 3: ("street",), 4: ("taxi",)}


def render(skin):
    S.set_skin(skin)
    b = S.build()
    vc.pack(b.cubes, S.TEX_W, S.TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, S.TEX_W, S.TEX_H, S.paint_cube, seed=3700)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--preview")
    args = ap.parse_args()
    shots = []
    base_glow = None
    for skin, (name, *_c) in S.SKIN_DEFS.items():
        b, tex, glow = render(skin)
        if args.write:
            tex.save(OUT / f"bugotti_chillon_{name}.png")
        if args.preview:
            S.pose(b, 0.3, 0.0)
            for n in VISIBLE[skin]:
                b.parts[n].visible = True
            imgs = vc.views(b, tex, glow, (S.TEX_W, S.TEX_H), [(145, 14), (215, 14), (90, 3)], S.CENTER, 4.6, light_on=True, size=(380, 300))
            shots.append(vc.sheet(imgs).convert("RGB"))
    S.set_skin(0)
    if args.preview and shots:
        w = shots[0].width
        out = Image.new("RGB", (w, sum(s.height for s in shots)))
        y = 0
        for s in shots:
            out.paste(s, (0, y))
            y += s.height
        out.save(args.preview)


if __name__ == "__main__":
    main()
