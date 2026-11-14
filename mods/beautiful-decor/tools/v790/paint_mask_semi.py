#!/usr/bin/env python3
"""V7.9.0: máscara de pintura de la tractomula (semi_truck_paint.png), con el mismo método de tools/v320/paint_masks.py.
Solo escribe la de este vehículo: las demás no se tocan."""
import sys

import numpy as np
from PIL import Image

from ..v320 import paint_masks as pm
from ..v320 import paintkit as pk
from . import semi_truck as st


def main():
    ks, ref, worst = pm.measure(st, st)
    mask = pm.to_png(ks, ref.shape)
    base = np.clip(ref, 0, 255).astype(np.uint8)
    alpha = base[..., 3] > 0
    counts = {ch: int(((mask[..., i] > 0) & alpha).sum()) for i, ch in enumerate(pk.CHANNELS)}
    print("píxeles por canal", counts, "desvío lineal %.3f" % worst)
    ok = True
    for ch, new in {"body": (30, 90, 200), "handle": (240, 200, 40), "rim": (40, 200, 90)}.items():
        d = tuple(new[i] - st.REFS[ch][i] for i in range(3))
        truth = np.clip(np.round(pm.raw(st, st, **{ch: d})), 0, 255).astype(np.uint8)
        got = pm.apply(base, mask, st.REFS, {ch: new})
        err = np.abs(truth[..., :3].astype(int) - got[..., :3].astype(int))
        clipped = ((ref[..., :3] > 255.0) | (ref[..., :3] < 0.0)).any(axis=-1)
        exact = err[alpha & ~clipped]
        print(f"  {ch}: error medio {err[alpha].mean():.2f}, máximo sin recortes {exact.max()}")
        if exact.max() > 6 or err[alpha].mean() > 1.5:
            ok = False
    Image.fromarray(mask, "RGB").save(pm.ENTITY_DIR / "semi_truck_paint.png")
    if not ok:
        print("ATENCION: algún canal no sigue la fórmula lineal", file=sys.stderr)
        sys.exit(1)
    print("escrito semi_truck_paint.png")


if __name__ == "__main__":
    main()
