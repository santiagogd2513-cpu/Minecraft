#!/usr/bin/env python3
"""Sintetiza los sonidos de la 3.1.0 (carro RC eléctrico) y los codifica a .ogg mono con ffmpeg.

Uso (desde la raíz del proyecto):  python3 -m tools.v310.make_sounds
"""
import numpy as np
from scipy.signal import butter, sosfilt

from ..v200.make_sounds import SR, band_noise, save


def lowpass(x, fc, order=3):
    return sosfilt(butter(order, fc, btype="low", fs=SR, output="sos"), x)


def motor_loop():
    """Motorcito eléctrico: zumbido agudo con armónicos y arenilla de engranes. 1 s exacto (frecuencias enteras = bucle sin corte).
    El juego sube y baja el tono con la velocidad."""
    n = SR
    t = np.arange(n) / SR
    whine = (np.sin(2 * np.pi * 360 * t) + 0.55 * np.sin(2 * np.pi * 720 * t + 0.4)
             + 0.30 * np.sin(2 * np.pi * 1080 * t + 1.1) + 0.16 * np.sin(2 * np.pi * 1800 * t + 0.2))
    whine *= 1.0 + 0.10 * np.sin(2 * np.pi * 6 * t)                       # leve vibración
    gears = band_noise(n, 1200, 5200, 61) * (0.55 + 0.45 * np.sin(2 * np.pi * 24 * t + 0.5))   # engranes
    hum = np.sin(2 * np.pi * 120 * t) * 0.35                                # zumbido grave de la bobina
    x = whine / np.max(np.abs(whine)) + 0.32 * gears + hum
    x = np.tanh(1.2 * x / np.max(np.abs(x)))
    return x


def start_beeps():
    """Encendido (0,7 s): dos pitidos cortos y un zumbido que sube, como un control que se empareja con el carro."""
    total = int(0.7 * SR)
    t = np.arange(total) / SR
    out = np.zeros(total)
    for start, f in ((0.0, 1320.0), (0.13, 1760.0)):
        m = (t >= start) & (t < start + 0.09)
        tt = t[m] - start
        env = np.minimum(1.0, tt / 0.006) * np.minimum(1.0, (0.09 - tt) / 0.012)
        out[m] += 0.7 * env * (np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt))
    m = t >= 0.28
    tt = t[m] - 0.28
    ramp = np.clip(tt / 0.3, 0.0, 1.0)
    f = 200.0 + 520.0 * ramp ** 1.3
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.minimum(1.0, tt / 0.05) * np.where(tt > 0.34, np.maximum(0.0, 1 - (tt - 0.34) / 0.08), 1.0)
    out[m] += 0.45 * env * (np.sin(ph) + 0.5 * np.sin(2 * ph))
    out = lowpass(out, 7000)
    return np.tanh(1.1 * out / (np.max(np.abs(out)) + 1e-9))


def main():
    save("rc_car_motor", motor_loop())
    save("rc_car_start", start_beeps())


if __name__ == "__main__":
    main()
