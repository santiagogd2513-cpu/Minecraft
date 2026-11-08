#!/usr/bin/env python3
"""Sintetiza los sonidos del avión BOING 7G7 Gigante (V3.8.0) y los codifica a .ogg mono con ffmpeg.

Turbinas: rugido ancho de aire (ruido filtrado) más el silbido agudo de las aspas y un zumbido grave; el juego sube el tono con las revoluciones.
Arranque: silbido que sube de ~90 Hz a ~500 Hz mientras el ruido crece, 3,4 s, y termina en el nivel de la turbina en marcha.
Uso (desde la raíz del proyecto):  python3 -m tools.v380.make_sounds
"""
import numpy as np

from ..v200.make_sounds import SR, band_noise, save
from ..v370.make_sounds import bandpass, lowpass, periodic


def engine_loop():
    """Turbina en marcha: exactamente 1 s y sin corte al repetirse (todo periódico en 1 s, tonos en Hz enteros)."""
    n = SR
    t = np.arange(n) / SR
    roar = periodic(lambda s: lowpass(s, 1800), band_noise(n, 60, 1800, 81))
    hiss = periodic(lambda s: bandpass(s, 1800, 7000), band_noise(n, 1800, 7000, 82))
    rumble = periodic(lambda s: lowpass(s, 140), band_noise(n, 25, 140, 83))
    whine = (np.sin(2 * np.pi * 1400 * t) + 0.55 * np.sin(2 * np.pi * 2800 * t + 0.6) + 0.3 * np.sin(2 * np.pi * 4200 * t + 1.9)
             + 0.35 * np.sin(2 * np.pi * 1000 * t + 2.2))
    buzz = np.sin(2 * np.pi * 96 * t) + 0.5 * np.sin(2 * np.pi * 192 * t + 0.8)
    swell = 1.0 + 0.10 * np.sin(2 * np.pi * 3 * t + 0.5) + 0.05 * np.sin(2 * np.pi * 7 * t + 1.3)     # el aire no es del todo parejo
    parts = [(roar, 1.0), (hiss, 0.55), (rumble, 0.8)]
    x = sum(a * p / (np.max(np.abs(p)) + 1e-9) for p, a in parts) * swell
    x = x / (np.max(np.abs(x)) + 1e-9) + 0.10 * whine / np.max(np.abs(whine)) + 0.12 * buzz / np.max(np.abs(buzz))
    return np.tanh(1.3 * x)


def engine_start():
    """Arranque de turbinas: el compresor empieza a girar (silbido que sube), entra el aire y el ruido crece hasta el ralentí. 3,4 s."""
    total = int(3.4 * SR)
    t = np.arange(total) / SR
    ramp = np.clip(t / 2.8, 0.0, 1.0)
    ease = ramp * ramp * (3 - 2 * ramp)
    freq = 90.0 + 420.0 * ease
    phase = np.cumsum(freq) / SR
    whine = np.sin(2 * np.pi * phase) + 0.5 * np.sin(2 * np.pi * 2 * phase + 0.4) + 0.3 * np.sin(2 * np.pi * 3 * phase + 1.1)
    whine += 0.35 * np.sin(2 * np.pi * np.cumsum(freq * 9.5) / SR)                       # silbido agudo de las aspas
    air = lowpass(band_noise(total, 60, 2600, 84), 2600)
    air /= np.max(np.abs(air)) + 1e-9
    hiss = bandpass(band_noise(total, 1800, 7000, 85), 1800, 7000)
    hiss /= np.max(np.abs(hiss)) + 1e-9
    lit = np.clip((t - 1.6) / 1.2, 0.0, 1.0)                                              # la combustión prende hacia la mitad
    lit = lit * lit * (3 - 2 * lit)
    rumble = lowpass(band_noise(total, 25, 140, 86), 140)
    rumble /= np.max(np.abs(rumble)) + 1e-9
    x = (0.35 + 0.5 * ease) * whine / np.max(np.abs(whine)) * 0.45
    x += (0.10 + 0.65 * ease) * air * (0.4 + 0.6 * lit) + 0.40 * lit * hiss * 0.5 + 0.7 * lit * rumble
    x *= np.minimum(1.0, t / 0.15) * np.where(t > 3.1, np.maximum(0.0, 1 - (t - 3.1) / 0.3), 1.0)
    return np.tanh(1.3 * x / (np.max(np.abs(x)) + 1e-9) * 1.2)


def main():
    save("airliner_engine", engine_loop())
    save("airliner_start", engine_start())


if __name__ == "__main__":
    main()
