"""V6.14.0: texturas de las skins de la bicicleta (montaña, competición, reparto, policía): mismo atlas que el original con otra pintura.
Uso (desde la raíz): python3 -m tools.v6140.bike_skins --write | --preview /tmp/x.png"""
import argparse
from pathlib import Path

from PIL import Image

from ..v210 import bike as B
from ..v210 import vcommon as vc

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/beautifuldecor/textures/entity"
VISIBLE = {1: ("mtb_fork", "mtb_frame"), 2: ("race_bars", "race_disc"), 3: ("del_front", "del_rear"), 4: ("cop_rear",)}


def render(skin):
    B.set_skin(skin)
    b = B.build()
    vc.pack(b.cubes, B.TEX_W, B.TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, B.TEX_W, B.TEX_H, B.paint_cube, seed=2110)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--preview")
    args = ap.parse_args()
    shots = []
    for skin, (name, *_c) in B.SKIN_DEFS.items():
        b, tex, glow = render(skin)
        if args.write:
            tex.save(OUT / f"bicycle_{name}.png")
        if args.preview:
            B.pose(b, 0.5, 0.0, False)
            for n in VISIBLE[skin]:
                b.parts[n].visible = True
            imgs = vc.views(b, tex, glow, (B.TEX_W, B.TEX_H), [(150, 12), (210, 12), (90, 4)], B.CENTER, 11.5, light_on=True, size=(380, 300))
            shots.append(vc.sheet(imgs).convert("RGB"))
    B.set_skin(0)
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
