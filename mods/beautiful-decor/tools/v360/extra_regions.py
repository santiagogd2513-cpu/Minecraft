#!/usr/bin/env python3
"""Colores de "Otros" de los vehículos (V3.6.0): <textura>_regions.png y el cuadro de regiones en Java.

Además de los tres canales de siempre (pintura, manijas, rines; ver tools/v320) cada vehículo deja cambiar el color de TODAS sus demás
partes (vidrios, asientos, luces, cromados...) con el botón "Otros" del menú de la tecla R. Lo único que no se puede cambiar: los
neumáticos en los vehículos terrestres y las hélices o el rotor en el helicóptero.

Cómo se sabe qué píxel es de qué parte: la geometría de cada vehículo (los cubos de su generador) dice a qué material pertenece cada cara
de la textura. Un grupo de materiales = una región = un color que el jugador puede cambiar. La máscara guarda por píxel:
  R = número de región (1..14, 0 = ninguna)   G = k * 100  (cuánto sigue ese píxel al color de su región)
En el juego es la misma cuenta que la de los otros canales (ver client/PaintMath.java):
    salida = original + k * (color_nuevo - color_de_referencia),   k = luminosidad del píxel / luminosidad del color de referencia
El color de referencia de una región es el promedio de sus píxeles (así con el color de referencia sale la textura de siempre).
Los píxeles que ya dependen de pintura, manijas o rines no entran en ninguna región.

Uso (desde la raíz del proyecto):
  python3 -m tools.v360.extra_regions            # comprueba y muestra un resumen, no escribe nada
  python3 -m tools.v360.extra_regions --write    # escribe *_regions.png y entity/VehiclePaintExtras.java
"""
import argparse
import collections
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from ..v200 import moto_model, moto_paint
from ..v210 import bike, truck, vcommon as vc
from ..v300 import delivery_truck, helicopter
from ..v370 import supercar
from ..v380 import airliner

ROOT = Path(__file__).resolve().parents[2]
ENTITY_DIR = ROOT / "src/main/resources/assets/beautifuldecor/textures/entity"
JAVA_FILE = ROOT / "src/main/java/com/example/beautifuldecor/entity/VehiclePaintExtras.java"
MAX_REGIONS = 14
K_SCALE = 100.0

# Lo que NO se puede cambiar: neumáticos (terrestres) y hélices/rotor (helicóptero: aspas, buje y mástil del rotor). Ningún grupo puede nombrarlos.
FORBIDDEN_MATS = {
    "motorcycle": {"tire"}, "bicycle": {"tire"}, "ford_f150": {"tire"}, "delivery_truck": {"tire"},
    "helicopter": {"blade", "hub", "tip"}, "bugotti_chillon": {"tire"},
    "airliner": {"tire", "fan"},          # V3.8.0: neumáticos y aspas de las turbinas
}
# La última región de cada vehículo recoge todo lo demás que no es neumático ni rotor (logotipos, detalles dentro de la carrocería...).
DETAILS_KEY = "details"

# textura -> [(clave, [materiales])]; el orden es el del menú. La clave da el nombre: gui.beautifuldecor.paint.<clave>
GROUPS = {
    "motorcycle": [
        ("black", ["black", "black_gloss", "rubber", "frame"]), ("engine", ["engine", "engine_dark", "radiator", "exhaust", "spring"]),
        ("brakes", ["disc"]), ("chrome", ["chrome"]), ("glass", ["glass"]), ("headlight", ["lens"]),
        ("taillight", ["taillight"]), ("seat", ["seat"]), ("dash", ["dash"]), ("plate", ["plate"]),
    ],
    "bicycle": [
        ("chrome", ["chrome", "hub"]), ("black", ["black"]), ("saddle", ["saddle"]), ("chain", ["chain"]),
        ("headlight", ["lamp"]), ("taillight", ["taillight"]), ("bell", ["bell"]),
    ],
    "ford_f150": [
        ("black", ["dark", "mirror"]), ("chrome", ["chrome"]), ("bed", ["liner"]), ("interior", ["carpet", "trim"]),
        ("dash", ["dash"]), ("seat", ["seat"]), ("grille", ["grille"]), ("headlight", ["lens"]),
        ("taillight", ["taillight"]), ("glass", ["glass"]),
    ],
    "delivery_truck": [
        ("black", ["dark", "mirror"]), ("chrome", ["chrome"]), ("tank", ["tank"]), ("bumper", ["bumper"]),
        ("interior", ["carpet", "trim"]), ("dash", ["dash"]), ("seat", ["seat"]), ("grille", ["grille"]),
        ("headlight", ["lens"]), ("taillight", ["taillight"]), ("marker", ["marker"]), ("glass", ["glass"]),
    ],
    "helicopter": [
        ("skid", ["skid"]), ("interior", ["carpet"]), ("dash", ["dash"]), ("seat", ["seat"]),
        ("accent", ["navy"]), ("black", ["dark"]), ("glass", ["glass"]),
    ],
    # V3.7.0: superdeportivo Bugotti Chillón
    "bugotti_chillon": [
        ("black", ["dark", "mirror"]), ("carbon", ["carbon"]), ("chrome", ["chrome"]), ("interior", ["carpet", "trim"]),
        ("dash", ["dash"]), ("seat", ["seat"]), ("intake", ["intake", "grille"]), ("headlight", ["lens"]),
        ("taillight", ["taillight"]), ("glass", ["glass"]),
    ],
    # V3.8.0: avión BOING 7G7 Gigante (las franjas rojas y los textos van pintados sobre cajas de pintura: caen en "details")
    "airliner": [
        ("accent", ["navy"]), ("black", ["dark"]), ("chrome", ["metal", "hub"]), ("glass", ["glass"]),
        ("seat", ["seat"]), ("interior", ["carpet", "trim"]), ("dash", ["dash"]), ("headlight", ["lens"]),
        ("taillight", ["taillight"]),
    ],
}


def geometry(name):
    """(cubos con su posición en el atlas, ancho, alto)."""
    if name == "motorcycle":
        b = moto_model.build()
        moto_paint.pack(b.cubes)
        return b.cubes
    mod = {"bicycle": bike, "ford_f150": truck, "delivery_truck": delivery_truck, "helicopter": helicopter,
           "bugotti_chillon": supercar, "airliner": airliner}[name]
    b = mod.build()
    if hasattr(mod, "pack_all"):          # el avión comparte trozos del atlas entre cajas iguales
        mod.pack_all(b)
    else:
        vc.pack(b.cubes, mod.TEX_W, mod.TEX_H)
    return b.cubes


def face_rects(c):
    u, v, w, h, d = c.u, c.v, int(c.w), int(c.h), int(c.d)
    return [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
            (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def compute(name):
    """Devuelve (máscara RGB uint8, [(clave, referencia 0xRRGGBB, píxeles)], resumen de lo que quedó sin región)."""
    base = np.asarray(Image.open(ENTITY_DIR / f"{name}.png").convert("RGBA"))
    main = (np.asarray(Image.open(ENTITY_DIR / f"{name}_paint.png").convert("RGB")) > 0).any(axis=2)
    alpha = base[..., 3] > 0
    cubes = geometry(name)
    groups = list(GROUPS[name]) + [(DETAILS_KEY, [])]
    assert len(groups) <= MAX_REGIONS, f"{name}: demasiadas regiones"
    forbidden = FORBIDDEN_MATS[name]
    mat_to_region = {}
    for i, (key, mats) in enumerate(groups):
        for m in mats:
            assert m not in forbidden, f"{name}: {m} no se puede cambiar"
            assert m not in mat_to_region, f"{name}: {m} está en dos grupos"
            mat_to_region[m] = i + 1
    details_id = len(groups)
    region = np.zeros(alpha.shape, np.uint8)
    skipped = collections.Counter()
    for c in cubes:
        if c.mat in forbidden:
            for x0, y0, cw, ch in face_rects(c):
                skipped[c.mat] += int((alpha[y0:y0 + ch, x0:x0 + cw] & ~main[y0:y0 + ch, x0:x0 + cw]).sum())
            continue
        rid = mat_to_region.get(c.mat, details_id)
        for x0, y0, cw, ch in face_rects(c):
            sl = (slice(y0, y0 + ch), slice(x0, x0 + cw))
            usable = alpha[sl] & ~main[sl]
            region[sl][usable] = rid
    leftover = skipped
    refs, summary = [], []
    mask = np.zeros(base.shape[:2] + (3,), np.uint8)
    rgb = base[..., :3].astype(np.float64)
    for i, (key, mats) in enumerate(groups):
        sel = region == i + 1
        n = int(sel.sum())
        assert n > 0, f"{name}: la región {key} no tiene píxeles"
        ref = tuple(int(round(v)) for v in rgb[sel].mean(axis=0))
        refs.append((key, (ref[0] << 16) | (ref[1] << 8) | ref[2], n))
        l = max(lum(ref), 1.0)
        k = np.clip(np.round((rgb[sel] @ np.array([0.299, 0.587, 0.114]) / l) * K_SCALE), 0, 255)
        mask[..., 0][sel] = i + 1
        mask[..., 1][sel] = k.astype(np.uint8)
    return mask, refs, leftover


def java_source(table):
    table = {name: ([k for k, _, _ in refs], [r for _, r, _ in refs]) for name, refs in table.items()}
    keys_cases = []
    refs_cases = []
    for name, (keys, vals) in table.items():
        keys_cases.append(f'            case "{name}" -> new String[] {{{", ".join(chr(34) + k + chr(34) for k in keys)}}};')
        refs_cases.append(f'            case "{name}" -> new int[] {{{", ".join("0x%06X" % v for v in vals)}}};')
    return f'''package com.example.beautifuldecor.entity;

/**
 * Las demás partes que se pueden pintar de cada vehículo con el botón "Otros" (V3.6.0): vidrios, asientos, luces, cromados...
 * ARCHIVO GENERADO por tools/v360/extra_regions.py: no editar a mano. Cada vehículo tiene hasta {MAX_REGIONS} regiones; el color de
 * referencia es el promedio de los píxeles de la región (con él la textura queda como siempre). La clave da el nombre:
 * gui.beautifuldecor.paint.CLAVE. Lo único que no tiene región: los neumáticos y las hélices o el rotor.
 */
public final class VehiclePaintExtras {{
    /** Tope de regiones por vehículo. */
    public static final int MAX = {MAX_REGIONS};
    private static final String[] NONE_KEYS = new String[0];
    private static final int[] NONE_REFS = new int[0];

    private VehiclePaintExtras() {{
    }}

    /** Claves de las regiones del vehículo con esa textura (en el orden del menú y de la máscara: región 1 = la primera). */
    public static String[] keys(String texture) {{
        return switch (texture) {{
{chr(10).join(keys_cases)}
            default -> NONE_KEYS;
        }};
    }}

    /** Colores de referencia (0xRRGGBB) de las regiones, en el mismo orden que {{@link #keys}}. */
    public static int[] refs(String texture) {{
        return switch (texture) {{
{chr(10).join(refs_cases)}
            default -> NONE_REFS;
        }};
    }}
}}
'''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()
    table = {}
    for name in GROUPS:
        mask, refs, leftover = compute(name)
        table[name] = refs
        total = sum(n for _, _, n in refs)
        print(f"{name}: {len(refs)} regiones, {total} píxeles")
        for key, ref, n in refs:
            print(f"    {key:<10} ref #{ref:06X}  {n:>5} px")
        print("    sin cambio posible (píxeles):", dict(leftover))
        if args.write:
            Image.fromarray(mask, "RGB").save(ENTITY_DIR / f"{name}_regions.png")
    if args.write:
        JAVA_FILE.write_text(java_source(table), encoding="utf-8")
        print("escrito: *_regions.png y", JAVA_FILE.relative_to(ROOT))


if __name__ == "__main__":
    main()
