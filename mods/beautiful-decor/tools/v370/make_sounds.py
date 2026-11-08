#!/usr/bin/env python3
"""Sintetiza los sonidos del superdeportivo Bugotti Chillón (V3.7.0) y los codifica a .ogg mono con ffmpeg.

Motor agudo de muchos cilindros (rugido que el juego sube de tono con la velocidad), arranque con marcha y acelerón, y claxon de dos tonos.
Uso (desde la raíz del proyecto):  python3 -m tools.v370.make_sounds
"""
import numpy as np
from scipy.signal import butter, sosfilt

from ..v200.make_sounds import SR, band_noise, save


def lowpass(x, fc, order=4):
    return sosfilt(butter(order, fc, btype="low", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def periodic(fn, x):
    """Aplica un filtro a una señal que se repite cada 1 s sin dejar corte en el empalme (se filtran tres copias y se toma la del medio)."""
    n = len(x)
    return fn(np.tile(x, 3))[n:2 * n]


def harmonic_stack(phase, n_max, tilt, rng):
    """Suma de armónicos de una fase acumulada (en ciclos): el rugido de un motor. Amplitud ~ 1/n^tilt con una pizca de desorden por armónico."""
    x = np.zeros_like(phase)
    offsets = rng.uniform(0, 2 * np.pi, n_max + 1)
    for n in range(1, n_max + 1):
        x += (1.0 / n ** tilt) * np.sin(2 * np.pi * n * phase + offsets[n])
    return x


def engine_loop():
    """Motor a media carga: exactamente 1 s y sin corte al repetirse (todo periódico en 1 s)."""
    n = SR
    t = np.arange(n) / SR
    rng = np.random.default_rng(71)
    f0 = 80.0                                            # frecuencia de encendido (el juego la sube hasta 2x)
    x = harmonic_stack(f0 * t, 34, 0.85, rng)
    x *= 1.0 + 0.10 * np.sin(2 * np.pi * 8 * t + 0.4) + 0.06 * np.sin(2 * np.pi * 16 * t + 1.1)     # irregularidad de los cilindros
    growl = periodic(lambda s: bandpass(s, 380, 1500), x)
    x = periodic(lambda s: lowpass(s, 5200), x) + 0.9 * growl
    intake = periodic(lambda s: bandpass(s, 1500, 5200), band_noise(n, 1500, 5200, 72)) * (0.6 + 0.4 * np.sin(2 * np.pi * 16 * t))
    turbo = np.sin(2 * np.pi * 2400 * t) + 0.4 * np.sin(2 * np.pi * 4800 * t + 0.7)
    x = x / (np.max(np.abs(x)) + 1e-9) + 0.16 * intake / (np.max(np.abs(intake)) + 1e-9) + 0.035 * turbo
    return np.tanh(1.5 * x)


def engine_start():
    """Marcha (0,8 s), el motor prende, un acelerón corto y se estabiliza en ralentí (2,4 s)."""
    total = int(2.4 * SR)
    t = np.arange(total) / SR
    rng = np.random.default_rng(73)
    crank_end = 0.8
    rate = np.where(t < crank_end, 7.0 + 5.0 * t / crank_end,                              # girando con la marcha
                    58.0 + 140.0 * np.exp(-((t - (crank_end + 0.5)) ** 2) / 0.05) + 22.0 * np.clip((t - crank_end) / 0.3, 0, 1))
    phase = np.cumsum(rate) / SR
    x = harmonic_stack(phase, 30, 0.85, rng)
    x = lowpass(x, 5200) + 0.9 * bandpass(x, 380, 1500)
    x /= np.max(np.abs(x)) + 1e-9
    on = np.clip((t - crank_end) / 0.12, 0.0, 1.0) + 0.45 * (t < crank_end)
    x *= on
    whine = np.sin(2 * np.pi * np.cumsum(260 + 220 * np.clip(t / crank_end, 0, 1)) / SR) * (t < crank_end + 0.05)
    whine *= np.where(t > crank_end - 0.05, np.maximum(0.0, 1 - (t - (crank_end - 0.05)) / 0.12), 1.0)
    air = bandpass(band_noise(total, 1500, 5200, 74), 1500, 5200) * np.clip((t - crank_end) / 0.3, 0, 1) * 0.12
    out = x + 0.22 * whine + air
    out *= np.minimum(1.0, t / 0.02) * np.where(t > 2.1, np.maximum(0.0, 1 - (t - 2.1) / 0.3), 1.0)
    return np.tanh(1.5 * out)


def horn():
    """Claxon deportivo de dos tonos agudos (mi y sol sostenido), 0,9 s."""
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, a in ((659.0, 1.0), (831.0, 0.9)):
        for k, ak in ((1, 1.0), (2, 0.6), (3, 0.45), (4, 0.25), (5, 0.15)):
            x += a * ak * np.sin(2 * np.pi * k * f * t + 0.3 * k)
    x *= 1.0 + 0.05 * np.sin(2 * np.pi * 7 * t)
    env = np.minimum(1.0, t / 0.03) * np.where(t > 0.72, np.maximum(0.0, 1 - (t - 0.72) / 0.18), 1.0)
    return np.tanh(1.6 * x / np.max(np.abs(x))) * env


def main():
    save("bugotti_chillon_engine", engine_loop())
    save("bugotti_chillon_start", engine_start())
    save("bugotti_chillon_horn", horn())


if __name__ == "__main__":
    main()
