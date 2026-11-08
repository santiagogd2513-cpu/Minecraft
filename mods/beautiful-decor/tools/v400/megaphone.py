#!/usr/bin/env python3
"""V4.0.0: genera los bloques del sistema de megafonía: micrófono de mesa (base negra) y bocina de megafonía.

Escribe, en el proyecto:
  textures/block/pa_*.png (32x32), models/block/{microphone,microphone_talking,pa_speaker}.json, blockstates/{microphone,pa_speaker}.json,
  textures/item/{microphone,pa_speaker}.png (ícono dibujado desde el modelo, como los demás bloques), models/item/*.json, loot_tables/blocks/*.json.
Uso (desde la raíz del proyecto):  python3 -m tools.v400.megaphone [--write] [--preview hoja.png]
Sin --write solo calcula y compara (para la prueba de la versión).
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/beautifuldecor"
DATA = ROOT / "src/main/resources/data/beautifuldecor"
S = 32            # lado de las texturas
NS = "beautifuldecor"


# ------------------------------------------------------------------------------------------------ pintura de texturas
def canvas(rgb, noise=0.0, seed=0):
    rng = np.random.default_rng(seed)
    img = np.zeros((S, S, 4), np.float64)
    img[..., :3] = np.array(rgb, float)
    img[..., 3] = 255
    if noise:
        img[..., :3] += rng.normal(0, noise, (S, S, 1))
    return img


def to_img(a):
    return Image.fromarray(np.clip(np.round(a), 0, 255).astype(np.uint8), "RGBA")


def tex_base():
    """Goma / plástico negro mate con grano fino."""
    a = canvas((22, 22, 26), 2.2, 11)
    a[:2, :, :3] += 6          # reborde superior apenas más claro (da volumen en las caras de 1 unidad)
    return to_img(a)


def tex_chrome():
    """Cromo cepillado: rayas verticales finas con degradado."""
    rng = np.random.default_rng(12)
    a = canvas((150, 154, 162), 0, 12)
    for x in range(S):
        a[:, x, :3] += 28 * math.sin(x / S * math.pi * 2.0) + rng.normal(0, 5)
    a[:, :, :3] += np.linspace(14, -22, S)[:, None, None]
    return to_img(a)


def tex_mesh():
    """Rejilla del micrófono: fondo oscuro con puntitos plateados."""
    a = canvas((38, 40, 46), 1.5, 13)
    for y in range(1, S, 3):
        for x in range(1 + (y // 3 % 2) * 1, S, 3):
            a[y, x, :3] = (150, 154, 164)
            if y + 1 < S:
                a[y + 1, x, :3] = (84, 86, 94)
    return to_img(a)


def tex_led(on):
    a = canvas((70, 12, 14) if not on else (255, 60, 48), 3 if not on else 4, 14 if not on else 15)
    if on:
        a[..., :3] = np.clip(a[..., :3], 0, 255)
        a[:, :, 1:3] += 30
    return to_img(a)


def tex_ring(on):
    if not on:
        return tex_chrome()
    a = np.asarray(tex_chrome()).astype(np.float64)
    a[..., 0] = np.minimum(255, a[..., 0] * 1.05 + 30)
    a[..., 1] *= 0.42
    a[..., 2] *= 0.38
    return to_img(a)


def tex_cab():
    """Forro negro de bocina (tipo tolex): grano cruzado."""
    rng = np.random.default_rng(21)
    a = canvas((18, 18, 20), 2.0, 21)
    for y in range(S):
        for x in range(S):
            a[y, x, :3] += 3.0 * (((x // 2) + (y // 2)) % 2) + rng.normal(0, 0.8)
    return to_img(a)


def circle(a, cx, cy, r, color, soft=0.8):
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
    m = np.clip((r - d) / soft, 0, 1)[..., None]
    a[..., :3] = a[..., :3] * (1 - m) + np.array(color, float) * m
    return a


def ring(a, cx, cy, r0, r1, color, soft=0.7):
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
    m = (np.clip((d - r0) / soft, 0, 1) * np.clip((r1 - d) / soft, 0, 1))[..., None]
    a[..., :3] = a[..., :3] * (1 - m) + np.array(color, float) * m
    return a


def tex_front():
    """Frente de la bocina. El cuerpo ocupa x 3..13, y 0,6..14,6 (en unidades de 16); se pinta en el rectángulo UV [3,1.4,13,15.4]."""
    a = canvas((20, 20, 23), 1.6, 31)
    u = S / 16.0
    # rejilla de tela: tramado fino
    for y in range(S):
        for x in range(S):
            if (x + y) % 2 == 0:
                a[y, x, :3] += 5
    # zona del cuerpo (UV x 3..13 -> px 6..26; y 1.4..15.4 -> px 2.8..30.8)
    x0, x1, y0, y1 = 6, 26, 3, 31
    a[:, :x0, :3] = (18, 18, 20)
    a[:, x1:, :3] = (18, 18, 20)
    a[:y0, :, :3] = (18, 18, 20)
    a[y1:, :, :3] = (18, 18, 20)
    # marco interior de la rejilla (borde cromado fino)
    for x in range(x0 + 1, x1 - 1):
        a[y0 + 1, x, :3] = (96, 98, 106)
        a[y1 - 2, x, :3] = (60, 62, 68)
    for y in range(y0 + 1, y1 - 1):
        a[y, x0 + 1, :3] = (86, 88, 96)
        a[y, x1 - 2, :3] = (52, 54, 60)
    # tweeter (agudos) arriba
    tcx, tcy = 16.0, 8.4
    a = ring(a, tcx, tcy, 3.4, 4.5, (150, 154, 164))
    a = circle(a, tcx, tcy, 3.5, (14, 14, 18))
    a = circle(a, tcx, tcy, 2.2, (44, 46, 54))
    a = circle(a, tcx, tcy, 1.0, (120, 124, 134))
    # woofer (graves) abajo
    wcx, wcy = 16.0, 20.2
    a = ring(a, wcx, wcy, 7.4, 8.6, (140, 144, 154))          # aro cromado
    a = ring(a, wcx, wcy, 6.4, 7.5, (10, 10, 12))              # sombra
    a = circle(a, wcx, wcy, 6.5, (28, 29, 34))                 # suspensión
    a = ring(a, wcx, wcy, 4.6, 6.2, (52, 54, 62))             # borde de goma
    a = circle(a, wcx, wcy, 4.7, (36, 37, 44))                 # cono
    for r, c in ((4.2, 42), (3.4, 48), (2.6, 54)):             # anillos del cono
        a = ring(a, wcx, wcy, r - 0.5, r, (c, c + 2, c + 10))
    a = circle(a, wcx, wcy, 2.0, (22, 22, 26))                 # tapa antipolvo
    a = circle(a, wcx, wcy, 1.2, (70, 72, 82))
    a[int(wcy) - 2:int(wcy) - 1, int(wcx) - 1:int(wcx) + 1, :3] += 40     # brillo
    # tornillos en las esquinas del aro
    for dx, dy in ((-7.2, -7.2), (7.2, -7.2), (-7.2, 7.2), (7.2, 7.2)):
        a = circle(a, wcx + dx * 0.0 + dx * 0.98, wcy + dy * 0.0 + dy * 0.98, 0.8, (170, 174, 184), 0.5)
    # LED de encendido (verde) y placa del logo
    a = circle(a, 22.8, 4.6, 0.9, (60, 255, 110), 0.5)
    for x in range(12, 21):
        a[28, x, :3] = (150, 154, 164)
        a[29, x, :3] = (92, 94, 102)
    return to_img(a)


def tex_trim():
    a = np.asarray(tex_chrome()).astype(np.float64)
    a[..., :3] *= 0.72
    return to_img(a)


def tex_back():
    a = np.asarray(tex_cab()).astype(np.float64).copy()
    # placa de conexiones: speakon + interruptor, en el rectángulo de la cara trasera
    for y in range(18, 28):
        for x in range(10, 24):
            a[y, x, :3] = (74, 76, 84)
    for x in range(10, 24):
        a[18, x, :3] = (130, 134, 144)
        a[27, x, :3] = (46, 48, 54)
    a = circle(a, 14.5, 22.6, 2.8, (24, 24, 28))
    a = ring(a, 14.5, 22.6, 2.4, 3.2, (170, 174, 184))
    a = circle(a, 14.5, 22.6, 1.0, (190, 160, 70), 0.5)
    a = circle(a, 20.8, 22.6, 1.4, (30, 30, 34))
    a[22:23, 20:22, :3] = (230, 230, 236)
    return to_img(a)


TEXTURES = {
    "pa_mic_base": tex_base, "pa_mic_chrome": tex_chrome, "pa_mic_mesh": tex_mesh,
    "pa_mic_led_off": lambda: tex_led(False), "pa_mic_led_on": lambda: tex_led(True),
    "pa_mic_ring_off": lambda: tex_ring(False), "pa_mic_ring_on": lambda: tex_ring(True),
    "pa_spk_cab": tex_cab, "pa_spk_front": tex_front, "pa_spk_trim": tex_trim, "pa_spk_back": tex_back,
}


# ------------------------------------------------------------------------------------------------ modelos
def face(tex, uv=None, rot=None):
    d = {"texture": "#" + tex}
    if uv:
        d["uv"] = uv
    if rot:
        d["rotation"] = rot
    return d


def cube(frm, to, tex, uvs=None, rotation=None):
    """Caja con la misma textura en sus 6 caras (uvs: dict cara -> uv)."""
    faces = {f: face(tex, (uvs or {}).get(f)) for f in ("north", "south", "east", "west", "up", "down")}
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def mic_model(talking):
    ring_tex = "ring"
    led_tex = "led"
    tilt = {"angle": -22.5, "axis": "x", "origin": [8, 8.4, 8.0]}
    els = [
        # base negra, ancha y baja (octágono aproximado con dos cajas cruzadas)
        cube((4.0, 0.0, 5.0), (12.0, 0.9, 11.0), "base"),
        cube((5.0, 0.0, 4.0), (11.0, 0.9, 12.0), "base"),
        cube((5.6, 0.9, 5.6), (10.4, 1.3, 10.4), "base"),
        # lucecita al frente de la base
        cube((6.5, 0.25, 3.92), (9.5, 0.65, 4.0), led_tex),
        # tallo cromado fino y collar negro
        cube((7.55, 1.3, 7.55), (8.45, 6.3, 8.45), "chrome"),
        cube((7.2, 5.9, 7.2), (8.8, 6.8, 8.8), "base"),
        # cabeza del micrófono, inclinada hacia arriba y hacia el frente: cápsula de rejilla con dos aros finos
        cube((6.9, 7.4, 4.6), (9.1, 9.8, 8.2), "mesh", rotation=tilt),
        cube((6.75, 7.25, 5.5), (9.25, 9.95, 5.8), ring_tex, rotation=tilt),
        cube((6.75, 7.25, 7.0), (9.25, 9.95, 7.3), ring_tex, rotation=tilt),
        cube((6.75, 7.25, 8.0), (9.25, 9.95, 8.5), "base", rotation=tilt),
        cube((7.2, 7.7, 8.5), (8.8, 9.5, 9.1), "base", rotation=tilt),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/pa_mic_base", "base": f"{NS}:block/pa_mic_base", "chrome": f"{NS}:block/pa_mic_chrome",
            "mesh": f"{NS}:block/pa_mic_mesh", "led": f"{NS}:block/pa_mic_led_{'on' if talking else 'off'}",
            "ring": f"{NS}:block/pa_mic_ring_{'on' if talking else 'off'}",
        },
        "elements": els,
    }


def speaker_model():
    front_uv = [3, 1.4, 13, 15.4]
    side_uv = [4, 1.4, 12, 15.4]
    body = {
        "from": [3, 0.6, 4], "to": [13, 14.6, 12],
        "faces": {
            "north": face("front", front_uv), "south": face("back", front_uv),
            "east": face("cab", side_uv), "west": face("cab", side_uv),
            "up": face("cab", [3, 4, 13, 12]), "down": face("cab", [3, 4, 13, 12]),
        },
    }
    els = [body,
           cube((2.6, 14.6, 3.6), (13.4, 15.3, 12.4), "trim"),          # tapa cromada
           cube((2.8, 0.2, 3.8), (3.8, 1.2, 4.8), "trim"), cube((12.2, 0.2, 3.8), (13.2, 1.2, 4.8), "trim"),      # protectores de esquina
           cube((2.8, 0.2, 11.2), (3.8, 1.2, 12.2), "trim"), cube((12.2, 0.2, 11.2), (13.2, 1.2, 12.2), "trim"),
           cube((3.5, 0.0, 4.5), (5.5, 0.6, 6.5), "cab"), cube((10.5, 0.0, 4.5), (12.5, 0.6, 6.5), "cab"),       # patas de goma
           cube((3.5, 0.0, 9.5), (5.5, 0.6, 11.5), "cab"), cube((10.5, 0.0, 9.5), (12.5, 0.6, 11.5), "cab"),
           cube((5.0, 15.3, 7.0), (5.8, 16.0, 9.0), "trim"), cube((10.2, 15.3, 7.0), (11.0, 16.0, 9.0), "trim"),  # asa
           cube((5.0, 15.7, 7.0), (11.0, 16.0, 9.0), "trim"),
           ]
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/pa_spk_cab", "cab": f"{NS}:block/pa_spk_cab", "front": f"{NS}:block/pa_spk_front",
                     "trim": f"{NS}:block/pa_spk_trim", "back": f"{NS}:block/pa_spk_back"},
        "elements": els,
    }


YROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstate_mic():
    v = {}
    for f, y in YROT.items():
        for talking in (False, True):
            e = {"model": f"{NS}:block/microphone{'_talking' if talking else ''}"}
            if y:
                e["y"] = y
            v[f"facing={f},talking={'true' if talking else 'false'}"] = e
    return {"variants": v}


def blockstate_speaker():
    v = {}
    for f, y in YROT.items():
        e = {"model": f"{NS}:block/pa_speaker"}
        if y:
            e["y"] = y
        v[f"facing={f}"] = e
    return {"variants": v}


def loot(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def item_model(name):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}


def j(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def build_files():
    """{ruta relativa al proyecto: bytes o texto} de todo lo que genera esta herramienta, SIN los íconos (esos salen del modelo escrito)."""
    files = {}
    base = "src/main/resources/assets/beautifuldecor"
    for name, fn in TEXTURES.items():
        files[f"{base}/textures/block/{name}.png"] = fn()
    files[f"{base}/models/block/microphone.json"] = j(mic_model(False))
    files[f"{base}/models/block/microphone_talking.json"] = j(mic_model(True))
    files[f"{base}/models/block/pa_speaker.json"] = j(speaker_model())
    files[f"{base}/blockstates/microphone.json"] = j(blockstate_mic())
    files[f"{base}/blockstates/pa_speaker.json"] = j(blockstate_speaker())
    for name in ("microphone", "pa_speaker"):
        files[f"{base}/models/item/{name}.json"] = j(item_model(name))
        files[f"src/main/resources/data/beautifuldecor/loot_tables/blocks/{name}.json"] = j(loot(name))
    return files


def write_all():
    files = build_files()
    for rel, obj in files.items():
        p = ROOT / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(obj, Image.Image):
            obj.save(p)
        else:
            p.write_text(obj, encoding="utf-8")
    # íconos: se dibujan desde los modelos recién escritos
    from ..v182.icon_render import render_icon
    for name, model in (("microphone", f"{NS}:block/microphone"), ("pa_speaker", f"{NS}:block/pa_speaker")):
        icon = render_icon(model)
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ASSETS / f"textures/item/{name}.png")
    print("escrito:", len(files), "archivos + 2 íconos")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--preview")
    args = ap.parse_args()
    if args.write:
        write_all()
    if args.preview:
        from ..v182.icon_render import render_icon
        icons = [render_icon(f"{NS}:block/microphone"), render_icon(f"{NS}:block/microphone_talking"), render_icon(f"{NS}:block/pa_speaker")]
        sheet = Image.new("RGBA", (32 * 3 * 6, 32 * 6), (139, 139, 139, 255))
        for i, ic in enumerate(icons):
            sheet.alpha_composite(ic.resize((192, 192), Image.NEAREST), (i * 192 * 3 // 3 * 1 + i * 0, 0))
        sheet.convert("RGB").save(args.preview)
        print("vista previa:", args.preview)


if __name__ == "__main__":
    main()
