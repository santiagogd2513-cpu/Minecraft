#!/usr/bin/env python3
"""V1.8.2: genera un ícono propio (32x32) para cada ítem-bloque del mod y su modelo de ítem.

Uso (desde la raíz del proyecto):
  python3 -m tools.v182.make_icons --sheet-only hoja.png --start 0 --count 60   # solo mira, no escribe nada
  python3 -m tools.v182.make_icons --only door_glass lamp_x --sheet-only hoja.png
  python3 -m tools.v182.make_icons --write                                       # genera todo en el proyecto

Qué hace con --write, por cada modelo de ítem que apunta a un modelo de bloque ("parent": "beautifuldecor:block/xxx"):
  textures/item/<id>.png   el ícono dibujado desde el modelo de bloque
  models/item/<id>.json    {"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/<id>"}}
Los ítems que ya eran sprites (drone, drone_remote, tab_icon) no se tocan. El resultado es idempotente.
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

from .icon_render import ASSETS, render_icon

ITEM_MODELS = ASSETS / "models/item"
ITEM_TEX = ASSETS / "textures/item"
SLOT = (139, 139, 139, 255)


SOURCE = Path(__file__).resolve().parent / "item_models_src.json"
SPRITES = ("drone", "drone_remote", "tab_icon")      # ya eran sprites dibujados a mano


def block_items():
    """{id: modelo de ítem ORIGINAL} de todos los ítems cuyo modelo era solo "cómo se ve puesto".

    La primera vez se guardan los modelos originales en item_models_src.json; después de generar los íconos los
    archivos de models/item ya son sprites, así que las siguientes ejecuciones parten de ese respaldo.
    """
    if not SOURCE.exists():
        src = {}
        for f in sorted(ITEM_MODELS.glob("*.json")):
            data = json.loads(f.read_text(encoding="utf-8"))
            if str(data.get("parent", "")).startswith("beautifuldecor:block/"):
                src[f.stem] = data
        SOURCE.write_text(json.dumps(src, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    return json.loads(SOURCE.read_text(encoding="utf-8"))


def item_model_json(item_id):
    return json.dumps({"parent": "minecraft:item/generated",
                       "textures": {"layer0": f"beautifuldecor:item/{item_id}"}}, indent=2) + "\n"


def contact_sheet(icons, path, cols=10, zoom=3):
    cw, ch = 32 * zoom + 16, 32 * zoom + 28
    rows = (len(icons) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch), (40, 44, 52, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(icons):
        x, y = (i % cols) * cw, (i // cols) * ch
        d.rectangle([x + 6, y + 4, x + 6 + 32 * zoom + 3, y + 4 + 32 * zoom + 3], fill=SLOT, outline=(55, 55, 55, 255))
        if im is not None:
            sheet.alpha_composite(im.resize((32 * zoom, 32 * zoom), Image.NEAREST), (x + 8, y + 6))
        d.text((x + 4, y + 32 * zoom + 12), name[:19], fill=(235, 235, 235, 255))
    sheet.convert("RGB").save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="escribe texturas y modelos de ítem en el proyecto")
    ap.add_argument("--sheet-only", metavar="PNG", help="solo dibuja una hoja de contacto (no escribe)")
    ap.add_argument("--only", nargs="*", help="solo estos ids")
    ap.add_argument("--start", type=int, default=0)
    ap.add_argument("--count", type=int, default=10 ** 9)
    ap.add_argument("--cols", type=int, default=10)
    a = ap.parse_args()

    items = sorted(block_items().items())
    if a.only:
        items = [it for it in items if it[0] in set(a.only)]
    items = items[a.start:a.start + a.count]
    icons, failed = [], []
    for item_id, model in items:
        try:
            im = render_icon(model)
        except Exception as exc:                      # modelo roto: se informa y se sigue
            print(f"  ! {item_id}: {exc}")
            im = None
        if im is None:
            failed.append(item_id)
        icons.append((item_id, im))

    if a.sheet_only:
        contact_sheet(icons, a.sheet_only, cols=a.cols)
        print(f"hoja guardada: {a.sheet_only} ({len(icons)} íconos, {len(failed)} fallidos)")
    if a.write:
        if failed:
            print("No se escribe nada: fallaron", failed)
            sys.exit(1)
        ITEM_TEX.mkdir(parents=True, exist_ok=True)
        for item_id, im in icons:
            im.save(ITEM_TEX / f"{item_id}.png")
            (ITEM_MODELS / f"{item_id}.json").write_text(item_model_json(item_id), encoding="utf-8")
        print(f"v1.8.2: {len(icons)} íconos de ítem generados")


if __name__ == "__main__":
    main()
