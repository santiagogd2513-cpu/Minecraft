#!/usr/bin/env python3
"""Sintetiza los sonidos de los ascensores de la 2.2.0 (zumbido del motor, timbre "ding" y puertas) y los codifica a .ogg.

Uso (desde la raíz del proyecto):  python3 -m tools.v220.make_sounds
"""
import numpy as np
from scipy.signal import butter, sosfilt

from ..v200.make_sounds import SR, band_noise, save


def lowpass(x, fc, order=4):
    return sosfilt(butter(order, fc, btype="low", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=3):
    return sosfilt(butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def hum():
    """Motor de ascensor: ronroneo grave de 50 Hz con armónicos, silbido de cables y ruido suave. Exactamente 1 s y sin costura."""
    n = SR
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k, a in ((1, 1.0), (2, 0.8), (3, 0.5), (4, 0.34), (6, 0.2), (8, 0.12)):
        x += a * np.sin(2 * np.pi * 50 * k * t + 0.7 * k)
    x *= 1.0 + 0.12 * np.sin(2 * np.pi * 4 * t)                       # leve palpitar (4 vueltas por segundo)
    whine = np.sin(2 * np.pi * (420 * t + 6 * np.sin(2 * np.pi * 3 * t) / (2 * np.pi * 3) * 3))   # silbido con vibrato de 3 Hz
    x += 0.07 * whine
    x += 0.35 * band_noise(n, 60, 700, 220)
    return np.tanh(1.2 * x / np.max(np.abs(x)))


def ding():
    """Timbre de llegada: dos notas de campanilla (mi y do), la segunda más grave y un poco después."""
    n = int(1.3 * SR)
    x = np.zeros(n)
    for t0, f, g in ((0.0, 1318.5, 1.0), (0.30, 1046.5, 0.9)):
        k = n - int(t0 * SR)
        tau = np.arange(k) / SR
        note = np.sin(2 * np.pi * f * tau) * np.exp(-tau / 0.42)
        note += 0.35 * np.sin(2 * np.pi * f * 2.76 * tau) * np.exp(-tau / 0.14)
        note += 0.18 * np.sin(2 * np.pi * f * 5.4 * tau) * np.exp(-tau / 0.07)
        note *= np.minimum(1.0, tau / 0.002)
        x[n - k:] += g * note
    fade = np.ones(n)
    m = int(0.15 * SR)
    fade[-m:] = np.linspace(1.0, 0.0, m)
    return x * fade


def doors():
    """Puertas corredizas: deslizamiento de metal (ruido filtrado con vaivén) y un golpecito suave al terminar."""
    n = int(1.1 * SR)
    t = np.arange(n) / SR
    env = np.clip(np.sin(np.pi * np.clip((t - 0.04) / 0.74, 0, 1)), 0, 1) ** 1.3
    slide = bandpass(band_noise(n, 200, 3500, 330), 260, 2400) * env
    slide += 0.45 * lowpass(band_noise(n, 40, 300, 331), 220) * env
    slide *= 0.9 + 0.1 * np.sin(2 * np.pi * 9 * t)
    clunk = np.zeros(n)
    k0 = int(0.80 * SR)
    kk = int(0.12 * SR)
    tau = np.arange(kk) / SR
    clunk[k0:k0 + kk] = (np.sin(2 * np.pi * 120 * tau) * np.exp(-tau / 0.03)
                         + 0.5 * bandpass(band_noise(kk, 300, 3000, 332), 400, 2800) * np.exp(-tau / 0.012))
    start = np.zeros(n)
    ks = int(0.05 * SR)
    ts = np.arange(ks) / SR
    start[:ks] = 0.5 * bandpass(band_noise(ks, 300, 2500, 333), 400, 2200) * np.exp(-ts / 0.012)
    fade = np.ones(n)
    m = int(0.1 * SR)
    fade[-m:] = np.linspace(1.0, 0.0, m)
    return (slide / (np.max(np.abs(slide)) + 1e-9) * 0.8 + 0.9 * clunk + start) * fade


def main():
    save("elevator_hum", hum())
    save("elevator_ding", ding())
    save("elevator_door", doors())


if __name__ == "__main__":
    main()
