#!/usr/bin/env python3
"""Sintetiza los sonidos de la 2.1.0 (timbre de la bici y camioneta F-150 V8) y los codifica a .ogg mono con ffmpeg.

Uso (desde la raíz del proyecto):  python3 -m tools.v210.make_sounds
"""
import numpy as np
from scipy.signal import butter, sosfilt

from ..v200.make_sounds import SR, band_noise, save

V8_PATTERN = np.array([1.0, 0.7, 0.92, 0.62, 1.0, 0.78, 0.86, 0.66])     # el "burbujeo" de un V8 de cigüeñal en cruz


def lowpass(x, fc, order=4):
    return sosfilt(butter(order, fc, btype="low", fs=SR, output="sos"), x)


def pulse_template():
    """Una explosión de cilindro: golpe grave amortiguado + soplo de escape filtrado (0,06 s)."""
    k = int(0.06 * SR)
    tau = np.arange(k) / SR
    thump = np.sin(2 * np.pi * 78 * tau) * np.exp(-tau / 0.014) + 0.45 * np.sin(2 * np.pi * 156 * tau) * np.exp(-tau / 0.008)
    puff = lowpass(band_noise(k, 60, 1800, 77), 1400, 2) * np.exp(-tau / 0.018)
    return thump + 0.7 * puff


def place(out, start, tmpl, amp, wrap):
    n = len(out)
    for j in range(len(tmpl)):
        i = start + j
        if i >= n:
            if not wrap:
                break
            i -= n
        out[i] += amp * tmpl[j]


def v8_loop():
    """Ralentí de un V8: 80 explosiones por segundo (patrón de 8), exactamente 1 s y sin corte al repetirse."""
    n = SR
    rate = 80
    out = np.zeros(n)
    tmpl = pulse_template()
    rng = np.random.default_rng(31)
    for i in range(rate):
        start = int(i * n / rate + rng.uniform(-0.0006, 0.0006) * SR) % n
        place(out, start, tmpl, V8_PATTERN[i % 8] * rng.uniform(0.92, 1.0), True)
    big = lowpass(np.tile(out, 3), 2600)[n:2 * n]                      # filtrado en un bloque triple: sin costura
    rasp = band_noise(n, 300, 1500, 8) * (0.5 + 0.5 * np.sin(2 * np.pi * 10 * np.arange(n) / SR))
    x = big / (np.max(np.abs(big)) + 1e-9) + 0.06 * rasp
    return np.tanh(1.5 * x)


def v8_start():
    """Marcha (0,75 s: el motor de arranque gira el motor con golpes lentos) y el V8 que prende y se estabiliza en ralentí."""
    total = int(2.3 * SR)
    t = np.arange(total) / SR
    out = np.zeros(total)
    tmpl = pulse_template()
    # --- giro con el motor de arranque
    crank_end = 0.78
    rate = np.where(t < crank_end, 9.0 + 3.0 * (t / crank_end), 0.0)
    # --- el motor prende: sube de golpe y baja al ralentí
    rate_run = 28 + 120 * np.exp(-((t - (crank_end + 0.28)) ** 2) / 0.03) + 52 * np.clip((t - crank_end) / 0.18, 0, 1)
    rate = np.where(t < crank_end, rate, rate_run)
    phase = np.cumsum(rate) / SR
    last = -1
    rng = np.random.default_rng(33)
    pat = 0
    for i in range(total):
        idx = int(phase[i])
        if idx != last:
            last = idx
            amp = V8_PATTERN[pat % 8]
            pat += 1
            if t[i] < crank_end:
                amp *= 0.9                                              # compresión lenta: golpes secos
            else:
                amp *= 1.15 if t[i] < crank_end + 0.3 else 1.0
            place(out, i, tmpl, amp * rng.uniform(0.92, 1.0), False)
    out = lowpass(out, 2400)
    # zumbido del motor de arranque
    whine = np.sin(2 * np.pi * (260 + 120 * np.clip(t / crank_end, 0, 1)) * t) * (t < crank_end) * (0.7 + 0.3 * np.sin(2 * np.pi * 10 * t))
    whine *= np.minimum(1.0, t / 0.03) * np.where(t > crank_end - 0.05, np.maximum(0.0, 1 - (t - (crank_end - 0.05)) / 0.12), 1.0)
    out = out / (np.max(np.abs(out)) + 1e-9) + 0.22 * whine
    out *= np.minimum(1.0, t / 0.02) * np.where(t > 2.0, np.maximum(0.0, 1 - (t - 2.0) / 0.3), 1.0)
    return np.tanh(1.5 * out)


def pickup_horn():
    """Claxon de camioneta: dos tonos graves y nasales (casi 400 y 500 Hz) con zumbido de bocina eléctrica."""
    n = int(1.0 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, a in ((398.0, 1.0), (503.0, 0.9)):
        for k, ak in ((1, 1.0), (2, 0.6), (3, 0.5), (4, 0.28), (5, 0.2), (6, 0.1)):
            x += a * ak * np.sin(2 * np.pi * k * f * t + 0.3 * k)
    x *= 1.0 + 0.1 * np.sign(np.sin(2 * np.pi * 100 * t))
    env = np.minimum(1.0, t / 0.012) * np.where(t > 0.82, np.maximum(0.0, 1 - (t - 0.82) / 0.16), 1.0)
    return np.tanh(1.7 * x / np.max(np.abs(x))) * env


def bicycle_bell():
    """Timbre de bicicleta: dos "ring" seguidos, cada uno con parciales metálicos y batido (0,95 s)."""
    n = int(0.95 * SR)
    x = np.zeros(n)
    partials = ((2350.0, 1.0, 0.55), (2368.0, 0.9, 0.5), (5620.0, 0.5, 0.25), (6100.0, 0.3, 0.18), (3980.0, 0.35, 0.3))
    for t0, g in ((0.0, 1.0), (0.17, 0.85)):
        k = n - int(t0 * SR)
        tau = np.arange(k) / SR
        ring = np.zeros(k)
        for f, a, decay in partials:
            ring += a * np.sin(2 * np.pi * f * tau) * np.exp(-tau / decay)
        click = band_noise(int(0.01 * SR), 2500, 9000, 5)
        ring[:len(click)] += 0.5 * click * np.exp(-np.arange(len(click)) / (0.002 * SR))
        ring *= np.minimum(1.0, tau / 0.0015)
        x[n - k:] += g * ring
    fade = np.ones(n)
    m = int(0.12 * SR)
    fade[-m:] = np.linspace(1.0, 0.0, m)
    return x * fade


def main():
    save("pickup_engine", v8_loop())
    save("pickup_start", v8_start())
    save("pickup_horn", pickup_horn())
    save("bicycle_bell", bicycle_bell())


if __name__ == "__main__":
    main()
