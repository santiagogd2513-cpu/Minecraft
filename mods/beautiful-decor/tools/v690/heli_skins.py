"""V6.9.0: texturas de las skins del helicóptero (policía, bomberos, noticias): mismo atlas que el original con otra pintura y rotulado.
Uso (desde la raíz): python3 -m tools.v690.heli_skins --write | --preview /tmp/x.png"""
import argparse
from pathlib import Path

from PIL import Image

from ..v210 import vcommon as vc
from ..v300 import helicopter as H

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/beautifuldecor/textures/entity"
# nombre: (blanco/cuerpo, cuerpo sombra, azul/franja, franja oscura (texto), rojo/línea, puerta, rótulo cola, escala, letra aleta)
SKINS = {
    1: ("police", (236, 238, 244), (178, 182, 196), (16, 30, 84), (10, 18, 56), (225, 30, 40), "PN-1", "POLISIA", 1, "P"),
    2: ("fire", (205, 28, 32), (136, 14, 18), (255, 205, 50), (255, 226, 110), (245, 245, 250), "AG-6", "AGUACERO", 1, "A"),
    3: ("news", (242, 242, 248), (186, 188, 200), (0, 110, 220), (0, 60, 140), (200, 30, 40), "HS-6", "HS NEWS", 1, "H"),
}
VIS = {1: ("police", "bar", "bar_red", "bar_blue", "siren_btn"), 2: ("fire", "bar", "bar_red", "bar_blue", "siren_btn"), 3: ("news",)}


def render(skin):
    if skin:
        _, H.WHITE, H.WHITE_LO, H.NAVY, H.NAVY_LO, H.RED, H.DOOR_TXT, H.BOOM_TXT, H.BOOM_SCALE, H.FIN_TXT = SKINS[skin]
        H.MAT_COLOR["paint"], H.MAT_COLOR["navy"], H.MAT_COLOR["red"] = H.WHITE, H.NAVY, H.RED
    else:
        H.NAVY, H.NAVY_LO, H.RED = (26, 58, 140), (14, 30, 80), (200, 34, 40)
        H.DOOR_TXT, H.BOOM_TXT, H.BOOM_SCALE, H.FIN_TXT = "BH-6", "BOING", 2, "B"
        H.MAT_COLOR["navy"], H.MAT_COLOR["red"] = H.NAVY, H.RED
        H.set_paint()
    b = H.build()
    vc.pack(b.cubes, H.TEX_W, H.TEX_H)
    atlas, glow = vc.paint_atlas(b.cubes, H.TEX_W, H.TEX_H, H.paint_cube, seed=3250)
    return b, Image.fromarray(atlas, "RGBA"), Image.fromarray(glow, "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--preview")
    args = ap.parse_args()
    shots = []
    for skin in SKINS:
        b, tex, glow = render(skin)
        if args.write:
            tex.save(OUT / f"helicopter_{SKINS[skin][0]}.png")
        if args.preview:
            H.pose(b, 0.4, 0.0)
            for n in VIS[skin]:
                b.parts[n].visible = True
            imgs = vc.views(b, tex, glow, (H.TEX_W, H.TEX_H), [(145, 16), (215, 16), (90, 3)], H.CENTER, 2.4, light_on=True, size=(380, 300))
            shots.append(vc.sheet(imgs).convert("RGB"))
    render(0)
    if args.preview and shots:
        out = Image.new("RGB", (shots[0].width, sum(s.height for s in shots)))
        y = 0
        for s in shots:
            out.paste(s, (0, y))
            y += s.height
        out.save(args.preview)


if __name__ == "__main__":
    main()
