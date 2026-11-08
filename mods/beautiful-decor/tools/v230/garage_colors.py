#!/usr/bin/env python3
"""V2.3.0: puerta de garaje con colores personalizables (texturas por capas + modelos con tintindex).

La puerta se ve IGUAL que antes con sus colores por defecto. Para poder cambiarle los colores cada textura se parte en capas:
  base      (opaca, tintada)  : lo que cambia con el color de "Puerta" (tintindex 0), "Marco" (2) o el interior (3)
  acento    (con alfa, tintada): la franja roja (tintindex 1)
  detalles  (con alfa, sin tinte): manija, cerradura, ventanas, sello de goma, bisagras, rodillos, luces
V3.6.0 ("Otros"): los detalles ya no van sin tinte, cada grupo es una capa con su propio tinte y se puede cambiar de color: ventanas
(tintindex 4 por fuera y 5 por dentro, que sale del color de las ventanas aclarado), manija y cerradura (6), bisagras y rodillos (7),
sello de goma (8) y rejillas del cajón (9). Esas capas guardan pixel / color_por_defecto (sin margen K: el color por defecto de cada
una es el más claro de sus píxeles) y el tinte vale el color elegido. Solo quedan sin tinte los 6 píxeles de brillo del indicador.
Un tinte solo puede oscurecer (multiplica 0..1), así que la textura base guarda  pixel / (color_por_defecto * K)  y el tinte
vale  color * K  (K deja margen para los brillos de la textura). Con el color por defecto sale el pixel original.
Las capas de acento y detalles van como elementos casi pegados (0.05 px por fuera) con render_type cutout.

Uso (desde la raíz del proyecto):
  python3 -m tools.v230.garage_colors --preview vista.png    # solo mira: hoja con la puerta en varios colores (no escribe nada)
  python3 -m tools.v230.garage_colors --write                # escribe texturas, modelos y blockstate (el loot y el ícono no cambian)
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from ..v180 import common as C
from ..v180.common import FACINGS, Model, write_json
from ..v200 import garage_door as G

# color por defecto de cada ranura y margen K de su textura (los mismos valores van en GarageColors.java)
D_BODY = (46, 49, 57)
D_ACCENT = (206, 28, 36)
D_FRAME = (30, 32, 37)
D_INSIDE = (120, 126, 137)
K_BODY, K_ACCENT, K_FRAME, K_INSIDE = 1.45, 1.0, 1.15, 1.2
# V3.6.0: color por defecto de las capas de "Otros" (el canal más claro de sus píxeles; ver GarageColors.java)
D_WINDOW = (96, 124, 152)
D_WINDOW_INSIDE = (204, 240, 255)
D_HANDLE = (214, 218, 226)
D_HARDWARE = (176, 180, 190)
D_SEAL = (32, 32, 36)
D_VENTS = (14, 14, 17)
INDICATOR_RECT = (88, 1, 90, 2)  # el brillo del indicador del cajón no cambia de color

OFF = 0.05                       # px que sobresalen las capas de acento y detalles
CUTOUT = "minecraft:cutout"
WINDOW_X = (8, 25, 42, 59, 76)

# clave de textura en el modelo -> tintindex (las que no están, van sin tinte)
TINT = {"ob": 0, "hb": 0, "oa": 1, "ha": 1, "ja": 1, "fb": 2, "jb": 2, "ib": 3,
        "ow": 4, "iw": 5, "oh": 6, "ih": 7, "os": 8, "is": 8, "hv": 9}
FILES = {          # clave -> archivo
    "ob": "gd_door_out_b", "oa": "gd_door_out_a", "ow": "gd_door_out_win", "oh": "gd_door_out_hdl", "os": "gd_door_out_seal",
    "ib": "gd_door_in_b", "iw": "gd_door_in_win", "ih": "gd_door_in_hw", "is": "gd_door_in_seal",
    "hb": "gd_hdr_b", "ha": "gd_hdr_a", "hv": "gd_hdr_vent", "hd": "gd_hdr_d",
    "fb": "gd_frame_b", "jb": "gd_jamb_b", "ja": "gd_jamb_a", "seal": "gd_seal",
}
OLD_FILES = ("gd_door_out_d", "gd_door_in_d")        # las capas de detalle de antes de la V3.6.0 (ya no se usan)


# ----------------------------------------------------------------------------------------------- máscaras
def _mask(shape, rects):
    m = np.zeros(shape, bool)
    for x0, y0, x1, y1 in rects:                 # límites inclusivos
        m[y0:y1 + 1, x0:x1 + 1] = True
    return m


def _discs(shape, centers, r):
    m = np.zeros(shape, bool)
    for cx, cy in centers:
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if 0 <= x < shape[1] and 0 <= y < shape[0] and (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    m[y, x] = True
    return m


def accent_mask(fn):
    """Píxeles que dibuja la función de textura con RED / RED_DARK (se detectan cambiando esos dos colores)."""
    base = np.asarray(fn()).astype(int)
    saved = (G.RED, G.RED_DARK)
    G.RED, G.RED_DARK = (1, 2, 3, 255), (4, 5, 6, 255)
    try:
        alt = np.asarray(fn()).astype(int)
    finally:
        G.RED, G.RED_DARK = saved
    return (base != alt).any(axis=2)


def window_rects():
    return [(x - 1, 8, x + 13, 19) for x in WINDOW_X]


def detail_mask_out():
    return _mask((96, 96), window_rects() + [(0, 92, 95, 95), (39, 80, 57, 82), (46, 86, 50, 90)])


def detail_mask_in():
    hinges = [(hx, jy - 3, hx + 7, jy + 3) for jy in (22, 46, 70) for hx in (12, 46, 80)]
    m = _mask((96, 96), window_rects() + [(0, 92, 95, 95)] + hinges)
    return m | _discs((96, 96), [(rx, jy) for jy in (22, 46, 70) for rx in (7, 89)], 2.6)


def detail_mask_hdr():
    rects = [(0, 5, 95, 5), (88, 1, 90, 2)] + [(x, 1, x, 3) for x in range(10, 28, 3)]
    return _mask((6, 96), rects)


# ----------------------------------------------------------------------------------------------- capas
def _a(im):
    return np.asarray(im.convert("RGBA")).astype(np.float64)


def base_layer(im, body_mask, d, k):
    """Textura base opaca: pixel / (d*k) donde hay color 'de base'; plana (= d) donde lo cubre otra capa."""
    a = _a(im)
    out = np.zeros(a.shape, np.uint8)
    dk = np.array(d, np.float64) * k
    ratio = np.clip(a[..., :3] / dk, 0.0, 1.0)
    flat = np.broadcast_to(np.array(d, np.float64) / dk, ratio.shape)
    ratio = np.where(body_mask[..., None], ratio, flat)
    out[..., :3] = np.round(ratio * 255.0)
    out[..., 3] = 255
    return Image.fromarray(out, "RGBA")


def accent_layer(im, mask):
    a = _a(im)
    out = np.zeros(a.shape, np.uint8)
    ratio = np.clip(a[..., :3] / (np.array(D_ACCENT, np.float64) * K_ACCENT), 0.0, 1.0)
    out[..., :3] = np.where(mask[..., None], np.round(ratio * 255.0), 0)
    out[..., 3] = np.where(mask, 255, 0)
    return Image.fromarray(out, "RGBA")


def window_mask():
    return _mask((96, 96), window_rects())


def handle_mask():
    return _mask((96, 96), [(39, 80, 57, 82), (46, 86, 50, 90)])


def seal_mask():
    return _mask((96, 96), [(0, 92, 95, 95)])


def indicator_mask():
    return _mask((6, 96), [INDICATOR_RECT])


def tinted_detail_layer(im, mask, d):
    """Capa de detalle que se tinta (V3.6.0): pixel / d donde hay detalle, alfa 0 en el resto."""
    a = _a(im)
    out = np.zeros(a.shape, np.uint8)
    ratio = np.clip(a[..., :3] / np.array(d, np.float64), 0.0, 1.0)
    out[..., :3] = np.where(mask[..., None], np.round(ratio * 255.0), 0)
    out[..., 3] = np.where(mask, 255, 0)
    return Image.fromarray(out, "RGBA")


def detail_layer(im, mask):
    a = _a(im).astype(np.uint8)
    out = np.zeros(a.shape, np.uint8)
    out[mask] = a[mask]
    out[..., 3] = np.where(mask, 255, 0)
    return Image.fromarray(out, "RGBA")


def layers():
    """Todas las texturas nuevas: {archivo: imagen}."""
    out_im, in_im = G.leaf_texture(True), G.leaf_texture(False)
    hdr_im, frame_im, jamb_im = G.header_texture(), G.frame_texture(), G.jamb_texture()

    acc_out = accent_mask(lambda: G.leaf_texture(True))
    det_out = detail_mask_out() & ~acc_out
    acc_hdr = accent_mask(G.header_texture)
    det_hdr = detail_mask_hdr() & ~acc_hdr
    acc_jamb = accent_mask(G.jamb_texture)
    det_in = detail_mask_in()
    win, seal = window_mask(), seal_mask()
    hdr_vent = det_hdr & ~indicator_mask()

    return {
        FILES["ob"]: base_layer(out_im, ~(acc_out | det_out), D_BODY, K_BODY),
        FILES["oa"]: accent_layer(out_im, acc_out),
        FILES["ow"]: tinted_detail_layer(out_im, det_out & win, D_WINDOW),
        FILES["oh"]: tinted_detail_layer(out_im, det_out & handle_mask(), D_HANDLE),
        FILES["os"]: tinted_detail_layer(out_im, det_out & seal, D_SEAL),
        FILES["ib"]: base_layer(in_im, ~det_in, D_INSIDE, K_INSIDE),
        FILES["iw"]: tinted_detail_layer(in_im, det_in & win, D_WINDOW_INSIDE),
        FILES["ih"]: tinted_detail_layer(in_im, det_in & ~win & ~seal, D_HARDWARE),
        FILES["is"]: tinted_detail_layer(in_im, det_in & seal, D_SEAL),
        FILES["hb"]: base_layer(hdr_im, ~(acc_hdr | det_hdr), D_BODY, K_BODY),
        FILES["ha"]: accent_layer(hdr_im, acc_hdr),
        FILES["hv"]: tinted_detail_layer(hdr_im, hdr_vent, D_VENTS),
        FILES["hd"]: detail_layer(hdr_im, det_hdr & indicator_mask()),
        FILES["fb"]: base_layer(frame_im, np.ones((16, 16), bool), D_FRAME, K_FRAME),
        FILES["jb"]: base_layer(jamb_im, ~acc_jamb, D_FRAME, K_FRAME),
        FILES["ja"]: accent_layer(jamb_im, acc_jamb),
    }


# ----------------------------------------------------------------------------------------------- modelos
def new_model():
    m = Model(render_type=CUTOUT)
    for key, name in FILES.items():
        m.tex(key, name)
    return m


def box(m, frm, to, **faces):
    """m.box + tintindex según la textura de cada cara."""
    el = m.box(frm, to, None, **faces)
    for face in el["faces"].values():
        key = face["texture"][1:]
        if key in TINT:
            face["tintindex"] = TINT[key]
    return el


def overlay(m, frm, to, spec):
    """Capas finas: solo las caras del frente y el fondo, OFF px por fuera."""
    f = [frm[0], frm[1], frm[2] - OFF]
    t = [to[0], to[1], to[2] + OFF]
    box(m, f, t, **spec)


def build_part(part, raise_):
    col, row = part % 3, part // 3
    m = new_model()
    top = G.HEADER_BOTTOM if row == 2 else 16
    z0, z1 = G.Z_JAMB

    if col in (0, 2):                                    # rieles laterales
        xa, xb = (14, 16) if col == 0 else (0, 2)
        outer = "east" if col == 0 else "west"
        inner = "west" if col == 0 else "east"
        ufront = [0, 0, 2, 16] if col == 0 else [2, 0, 0, 16]
        uback = [2, 0, 0, 16] if col == 0 else [0, 0, 2, 16]
        faces = {"north": ("#jb", ufront), "south": ("#jb", uback), outer: "#fb", inner: "#fb"}
        if row == 0:
            faces["down"] = "#fb"
        box(m, [xa, 0, z0], [xb, top, z1], **faces)
        overlay(m, [xa, 0, z0], [xb, top, z1], {"north": ("#ja", ufront), "south": ("#ja", uback)})

    if row == 2:                                         # cajón del motor
        fx0, fx1 = G.fx_range(col, 0, 16)
        north = [fx0 / 3.0, 0, fx1 / 3.0, 16]
        south = [(48 - fx1) / 3.0, 0, (48 - fx0) / 3.0, 16]
        faces = {"north": ("#hb", north), "south": ("#hb", south), "up": "#fb", "down": "#fb"}
        if col == 0:
            faces["east"] = "#fb"
        if col == 2:
            faces["west"] = "#fb"
        lo, hi = [0, G.HEADER_BOTTOM, z0], [16, 16, z1]
        box(m, lo, hi, **faces)
        overlay(m, lo, hi, {"north": ("#ha", north), "south": ("#ha", south)})
        overlay(m, lo, hi, {"north": ("#hv", north), "south": ("#hv", south)})
        overlay(m, lo, hi, {"north": ("#hd", north), "south": ("#hd", south)})

    yb = G.STEP_PX * raise_                              # hoja
    y_lo = max(16 * row, yb) - 16 * row
    if y_lo < top:
        xa, xb = G.leaf_x_range(col)
        yd0, yd1 = 16 * row + y_lo, 16 * row + top
        s0, s1 = yd0 - yb, yd1 - yb
        fx0, fx1 = G.fx_range(col, xa, xb)
        north = [fx0 / 3.0, (48 - s1) / 3.0, fx1 / 3.0, (48 - s0) / 3.0]
        south = [(48 - fx1) / 3.0, (48 - s1) / 3.0, (48 - fx0) / 3.0, (48 - s0) / 3.0]
        faces = {"north": ("#ob", north), "south": ("#ib", south)}
        if yb > 0 and 16 * row <= yb < 16 * row + 16:
            faces["down"] = "#seal"
        lo, hi = [xa, y_lo, G.LEAF_Z[0]], [xb, top, G.LEAF_Z[1]]
        box(m, lo, hi, **faces)
        overlay(m, lo, hi, {"north": ("#oa", north)})
        overlay(m, lo, hi, {"north": ("#ow", north), "south": ("#iw", south)})      # ventanas
        overlay(m, lo, hi, {"north": ("#oh", north), "south": ("#ih", south)})      # manija y cerradura / bisagras y rodillos
        overlay(m, lo, hi, {"north": ("#os", north), "south": ("#is", south)})      # sello de goma
    return m


def to_json(m):
    data = m.to_json("gd_door_out_b")                    # las partículas salen del color de la hoja (tinte 0)
    return data


def write_models():
    """Borra los modelos viejos de la puerta, escribe los nuevos (sin duplicados) y devuelve {(part, raise): nombre}."""
    for old in C.MODELS.glob("garage_door_p*_r*.json"):
        old.unlink()
    seen, names = {}, {}
    for part in range(9):
        for r in range(G.RAISE_MAX + 1):
            data = to_json(build_part(part, r))
            key = hashlib.sha1(json.dumps(data, sort_keys=True).encode()).hexdigest()
            if key not in seen:
                seen[key] = G.model_name(part, r)
                write_json(C.MODELS / f"{seen[key]}.json", data)
            names[(part, r)] = seen[key]
    return names


def generate():
    for name, im in layers().items():
        C.save_tex(name, im)
    for old in OLD_FILES:
        (C.TEX / f"{old}.png").unlink(missing_ok=True)
    names = write_models()
    G.write_blockstate(names)
    return names


# ----------------------------------------------------------------------------------------------- vista previa
def window_inside(window):
    """Color de las ventanas por dentro: el de fuera aclarado (con el color por defecto sale D_WINDOW_INSIDE). Igual que GarageColors.windowInside."""
    return [c + (255 - c) * (di - dw) / (255.0 - dw) for c, dw, di in zip(window, D_WINDOW, D_WINDOW_INSIDE)]


def composite(body, accent, frame, side="out", others=None):
    """Dibuja la hoja (96x96) y el cajón (96x6) con colores dados, como lo hará el juego (tinte * textura).

    others = {window, handle, seal, hardware, vents} (V3.6.0); lo que falte va con su color por defecto."""
    o = {"window": D_WINDOW, "handle": D_HANDLE, "seal": D_SEAL, "hardware": D_HARDWARE, "vents": D_VENTS}
    o.update(others or {})

    def tint_of(c, k):
        t = np.array(c, np.float64) * k
        if t.max() > 255:
            t *= 255.0 / t.max()
        return t / 255.0

    def tinted(im, t):
        a = _a(im)
        return a[..., :3] * t

    def put(dst, im, t=None):
        a = _a(im)
        rgb = a[..., :3] * (t if t is not None else 1.0)
        al = a[..., 3:4] / 255.0
        return dst * (1 - al) + rgb * al

    inside = [b + (255 - b) * (di - db) / (255.0 - db) for b, db, di in zip(body, D_BODY, D_INSIDE)]
    lay = {n: Image.open(C.TEX / f"{n}.png") for n in layers()}
    if side == "out":
        leaf = tinted(lay[FILES["ob"]], tint_of(body, K_BODY))
        leaf = put(leaf, lay[FILES["oa"]], tint_of(accent, K_ACCENT))
        leaf = put(leaf, lay[FILES["ow"]], tint_of(o["window"], 1.0))
        leaf = put(leaf, lay[FILES["oh"]], tint_of(o["handle"], 1.0))
        leaf = put(leaf, lay[FILES["os"]], tint_of(o["seal"], 1.0))
    else:
        leaf = tinted(lay[FILES["ib"]], tint_of(inside, K_INSIDE))
        leaf = put(leaf, lay[FILES["iw"]], tint_of(window_inside(o["window"]), 1.0))
        leaf = put(leaf, lay[FILES["ih"]], tint_of(o["hardware"], 1.0))
        leaf = put(leaf, lay[FILES["is"]], tint_of(o["seal"], 1.0))
    hdr = tinted(lay[FILES["hb"]], tint_of(body, K_BODY))
    hdr = put(hdr, lay[FILES["ha"]], tint_of(accent, K_ACCENT))
    hdr = put(hdr, lay[FILES["hv"]], tint_of(o["vents"], 1.0))
    hdr = put(hdr, lay[FILES["hd"]])
    jamb = tinted(lay[FILES["jb"]], tint_of(frame, K_FRAME))
    jamb = put(jamb, lay[FILES["ja"]], tint_of(accent, K_ACCENT))
    return leaf, hdr, jamb


def preview(path):
    generate()
    sets = [((46, 49, 57), (206, 28, 36), (30, 32, 37), None),            # por defecto
            ((255, 255, 255), (30, 90, 220), (200, 200, 205), None),
            ((230, 200, 60), (20, 20, 20), (90, 60, 30), None),
            ((60, 160, 90), (250, 120, 200), (20, 50, 30), None),
            # V3.6.0: con colores de "Otros"
            ((46, 49, 57), (206, 28, 36), (30, 32, 37), {"window": (230, 60, 60), "handle": (240, 200, 40), "seal": (200, 200, 200),
                                                         "hardware": (60, 200, 90), "vents": (250, 250, 250)}),
            ((240, 240, 240), (30, 90, 220), (40, 40, 44), {"window": (20, 20, 24), "handle": (30, 30, 30), "seal": (200, 40, 40),
                                                            "hardware": (240, 120, 30), "vents": (30, 90, 220)})]
    tiles = []
    for body, accent, frame, others in sets:
        leaf, hdr, jamb = composite(body, accent, frame, others=others)
        tile = np.zeros((96 + 6 + 4, 96 + 8, 3))
        tile[:] = 90
        tile[0:6, 4:100] = hdr
        tile[6:102, 4:100] = leaf
        tile[6:102, 0:4] = np.tile(jamb[:, :4], (6, 1, 1))[:96]
        tiles.append(np.clip(tile, 0, 255).astype(np.uint8))
    sheet = np.concatenate(tiles, axis=1)
    img = Image.fromarray(sheet, "RGB").resize((sheet.shape[1] * 3, sheet.shape[0] * 3), Image.NEAREST)
    img.save(path)
    print("vista previa:", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="hoja con la puerta en varios colores (escribe los recursos del bloque)")
    ap.add_argument("--write", action="store_true")
    a = ap.parse_args()
    if not (a.preview or a.write):
        sys.exit("usa --preview ARCHIVO o --write")
    if a.preview:
        preview(a.preview)
    if a.write:
        generate()
        print("escrito: texturas por capas, modelos con tinte y blockstate de la puerta de garaje")


if __name__ == "__main__":
    main()
