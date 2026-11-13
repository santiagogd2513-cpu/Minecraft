"""V6.8.0: texturas de las skins de la camioneta (policía, reparto, construcción, bomberos): mismo atlas que el original con otra pintura y rotulado.
Uso (desde la raíz): python3 -m tools.v680.pickup_skins --write | --preview /tmp/x.png"""
import argparse
from pathlib import Path

from PIL import Image

from ..v210 import truck as T
from ..v210 import vcommon as vc

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/beautifuldecor/textures/entity"
# nombre, (pintura, brillo, sombra)
SKINS = {1: ("police", (236, 238, 244), (252, 253, 255), (168, 174, 190)),
         2: ("delivery", (26, 40, 92), (60, 84, 156), (12, 20, 56)),
         3: ("construction", (244, 188, 18), (255, 222, 80), (176, 124, 8)),
         4: ("fire", (200, 24, 28), (236, 74, 62), (116, 10, 14))}


def render(skin):
    """Devuelve el atlas (imagen RGBA) de la skin."""
    T.SKIN = skin
    if skin:
        _, T.BODY, T.BODY_HI, T.BODY_LO = SKINS[skin]
        T.MAT_COLOR["paint"] = T.BODY
    else:
        T.set_paint()
    b = T.build()
    vc.pack(b.cubes, T.TEX_W, T.TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, T.TEX_W, T.TEX_H, T.paint_cube, seed=2150)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--preview")
    args = ap.parse_args()
    shots = []
    for skin, (name, *_c) in SKINS.items():
        b, tex, glow = render(skin)
        if args.write:
            tex.save(OUT / f"ford_f150_{name}.png")
        if args.preview:
            T.pose(b, 0.3, 0.0)
            vis = {1: ("police", "bar", "bar_red", "bar_blue", "siren_btn"), 2: ("delivery",), 3: ("construction",), 4: ("fire", "bar", "bar_red", "bar_blue", "siren_btn")}[skin]
            for n in vis:
                b.parts[n].visible = True
            imgs = vc.views(b, tex, glow, (T.TEX_W, T.TEX_H), [(145, 14), (215, 14), (90, 3)], T.CENTER if hasattr(T, "CENTER") else (0, 15.0, 0), 4.6, light_on=True, size=(380, 300))
            shots.append(vc.sheet(imgs).convert("RGB"))
    T.SKIN = 0
    T.set_paint()
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
