#!/usr/bin/env python3
"""Máscaras de color de los vehículos (V3.2.0): <textura>_paint.png con la sensibilidad de cada píxel a cada canal.

Uso (desde la raíz del proyecto):
  python3 -m tools.v320.paint_masks            # comprueba y muestra un resumen, no escribe nada
  python3 -m tools.v320.paint_masks --write    # escribe src/main/resources/assets/beautifuldecor/textures/entity/*_paint.png

Canales: R = pintura de la carrocería, G = manijas de las puertas, B = rines. El valor del byte es k * 100 (k = 0..2,55).
En el juego: salida = original + k * (color_nuevo - color_de_referencia). Ver tools/v320/paintkit.py.
"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from . import paintkit as pk
from ..v200 import moto_gen, moto_paint
from ..v210 import bike, truck
from ..v300 import delivery_truck, helicopter
from ..v370 import supercar
from ..v380 import airliner

ROOT = Path(__file__).resolve().parents[2]
ENTITY_DIR = ROOT / "src/main/resources/assets/beautifuldecor/textures/entity"

# nombre de la textura -> (módulo con set_paint/REFS/raw_atlas, módulo que tiene set_paint)
VEHICLES = {
    "motorcycle": (moto_gen, moto_paint),
    "bicycle": (bike, bike),
    "ford_f150": (truck, truck),
    "delivery_truck": (delivery_truck, delivery_truck),
    "helicopter": (helicopter, helicopter),
    "bugotti_chillon": (supercar, supercar),
    "airliner": (airliner, airliner),
}
SCALE = 100.0


def raw(gen, painter, **deltas):
    painter.set_paint(**deltas)
    try:
        return gen.raw_atlas()
    finally:
        painter.set_paint()


def measure(gen, painter):
    """Devuelve ({canal: k (H, W)}, referencia (H, W, 4), peor desvío respecto a una recta perfecta)."""
    ref = raw(gen, painter)
    ks = {}
    worst = 0.0
    for ch, rc in painter.REFS.items():
        d = np.array(pk.probe_delta(rc), np.float64)
        test = raw(gen, painter, **{ch: tuple(d)})
        diff = (test - ref)[..., :3].astype(np.float64)
        k = diff @ d / float(d @ d)
        resid = np.abs(diff - k[..., None] * d).max()
        worst = max(worst, float(resid))
        assert np.abs((test - ref)[..., 3]).max() == 0, "el canal alfa no debe depender del color"
        k[np.abs(k) < 0.02] = 0.0
        ks[ch] = k
    return ks, ref, worst


def to_png(ks, shape):
    out = np.zeros(shape[:2] + (3,), np.uint8)
    for i, ch in enumerate(pk.CHANNELS):
        if ch in ks:
            out[..., i] = np.clip(np.round(ks[ch] * SCALE), 0, 255).astype(np.uint8)
    return out


def apply(base_u8, mask_u8, refs, news):
    """Lo mismo que hace el juego: original + k * (nuevo - referencia)."""
    out = base_u8[..., :3].astype(np.float64)
    for i, ch in enumerate(pk.CHANNELS):
        if ch in refs and ch in news:
            k = mask_u8[..., i].astype(np.float64) / SCALE
            out += k[..., None] * (np.array(news[ch], np.float64) - np.array(refs[ch], np.float64))
    res = base_u8.copy()
    res[..., :3] = np.clip(np.round(out), 0, 255).astype(np.uint8)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()
    ok = True
    for name, (gen, painter) in VEHICLES.items():
        ks, ref, worst = measure(gen, painter)
        mask = to_png(ks, ref.shape)
        base = np.clip(ref, 0, 255).astype(np.uint8)
        alpha = base[..., 3] > 0
        counts = {ch: int(((mask[..., i] > 0) & alpha).sum()) for i, ch in enumerate(pk.CHANNELS)}
        print(f"{name}: píxeles por canal {counts}; mayor desvío lineal {worst:.3f}; k máx "
              + ", ".join(f"{ch}={ks[ch].max():.2f}" for ch in ks))
        # Prueba: pintar el canal con otro color usando el generador de verdad y comparar con la fórmula del juego.
        tests = {"body": (200, 30, 30), "handle": (240, 200, 40), "rim": (40, 200, 90)}
        for ch, new in tests.items():
            if ch not in painter.REFS:
                continue
            d = tuple(new[i] - painter.REFS[ch][i] for i in range(3))
            truth = np.clip(np.round(raw(gen, painter, **{ch: d})), 0, 255).astype(np.uint8)
            got = apply(base, mask, painter.REFS, {ch: new})
            err = np.abs(truth[..., :3].astype(int) - got[..., :3].astype(int))
            # Los reflejos que la textura de siempre ya tenía recortados en 255 pierden un poco de brillo al repintarlos
            # (la textura guardada no recuerda cuánto se pasaban); se miden aparte.
            clipped = ((ref[..., :3] > 255.0) | (ref[..., :3] < 0.0)).any(axis=-1)
            exact = err[alpha & ~clipped]
            print(f"    prueba {ch}->{new}: error medio {err[alpha].mean():.2f}; sin recortes: máximo {exact.max()}; "
                  f"píxeles recortados: {int((alpha & clipped).sum())}")
            if exact.max() > 6 or err[alpha].mean() > 1.5:
                ok = False
        if args.write:
            Image.fromarray(mask, "RGB").save(ENTITY_DIR / f"{name}_paint.png")
    if not ok:
        print("ATENCION: algún canal no sigue la fórmula lineal", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
