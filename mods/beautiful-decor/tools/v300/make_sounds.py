#!/usr/bin/env python3
"""Sintetiza los sonidos de la 3.0.0 (camión de reparto diésel y helicóptero) y los codifica a .ogg mono con ffmpeg.

Uso (desde la raíz del proyecto):  python3 -m tools.v300.make_sounds
"""
import numpy as np
from scipy.signal import butter, sosfilt

from ..v200.make_sounds import SR, band_noise, save


def lowpass(x, fc, order=4):
    return sosfilt(butter(order, fc, btype="low", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=3):
    return sosfilt(butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def place(out, start, tmpl, amp, wrap):
    n = len(out)
    for j in range(len(tmpl)):
        i = start + j
        if i >= n:
            if not wrap:
                break
            i -= n
        out[i] += amp * tmpl[j]


# ----------------------------------------------------------------------------------------------- camión diésel
def diesel_pulse():
    """Una explosión diésel: golpe grave y seco con "clac" metálico de la inyección (0,08 s)."""
    k = int(0.08 * SR)
    tau = np.arange(k) / SR
    thump = np.sin(2 * np.pi * 52 * tau) * np.exp(-tau / 0.022) + 0.5 * np.sin(2 * np.pi * 104 * tau) * np.exp(-tau / 0.012)
    clack = bandpass(band_noise(k, 900, 3800, 91), 900, 3800) * np.exp(-tau / 0.006)
    puff = lowpass(band_noise(k, 50, 900, 92), 800, 2) * np.exp(-tau / 0.03)
    return thump + 0.34 * clack + 0.6 * puff


DIESEL_PATTERN = np.array([1.0, 0.82, 0.95, 0.78, 1.0, 0.85])      # seis cilindros en línea, algo irregulares


def diesel_loop():
    """Ralentí de un diésel de seis cilindros: 36 explosiones por segundo, exactamente 1 s y sin corte al repetirse."""
    n = SR
    rate = 36
    out = np.zeros(n)
    tmpl = diesel_pulse()
    rng = np.random.default_rng(41)
    for i in range(rate):
        start = int(i * n / rate + rng.uniform(-0.0008, 0.0008) * SR) % n
        place(out, start, tmpl, DIESEL_PATTERN[i % 6] * rng.uniform(0.9, 1.0), True)
    big = lowpass(np.tile(out, 3), 2200)[n:2 * n]
    rumble = lowpass(band_noise(n, 30, 160, 43), 160, 2)
    x = big / (np.max(np.abs(big)) + 1e-9) + 0.22 * rumble
    return np.tanh(1.4 * x)


def diesel_start():
    """Marcha lenta y pesada (0,9 s) y el diésel que prende, humea un poco y se estabiliza (2,6 s)."""
    total = int(2.6 * SR)
    t = np.arange(total) / SR
    out = np.zeros(total)
    tmpl = diesel_pulse()
    crank_end = 0.9
    rate = np.where(t < crank_end, 6.0 + 2.5 * (t / crank_end), 0.0)
    rate_run = 24 + 90 * np.exp(-((t - (crank_end + 0.32)) ** 2) / 0.035) + 38 * np.clip((t - crank_end) / 0.22, 0, 1)
    rate = np.where(t < crank_end, rate, rate_run)
    phase = np.cumsum(rate) / SR
    last = -1
    rng = np.random.default_rng(42)
    pat = 0
    for i in range(total):
        idx = int(phase[i])
        if idx != last:
            last = idx
            amp = DIESEL_PATTERN[pat % 6] * (0.95 if t[i] < crank_end else 1.1 if t[i] < crank_end + 0.35 else 1.0)
            pat += 1
            place(out, i, tmpl, amp * rng.uniform(0.9, 1.0), False)
    out = lowpass(out, 2200)
    whine = np.sin(2 * np.pi * (190 + 90 * np.clip(t / crank_end, 0, 1)) * t) * (t < crank_end) * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * t))
    whine *= np.minimum(1.0, t / 0.03) * np.where(t > crank_end - 0.05, np.maximum(0.0, 1 - (t - (crank_end - 0.05)) / 0.12), 1.0)
    out = out / (np.max(np.abs(out)) + 1e-9) + 0.2 * whine
    out *= np.minimum(1.0, t / 0.02) * np.where(t > 2.3, np.maximum(0.0, 1 - (t - 2.3) / 0.3), 1.0)
    return np.tanh(1.4 * out)


def truck_horn():
    """Bocina de aire de camión: dos tonos graves (casi 260 y 330 Hz) con mucho cuerpo, 1,3 s."""
    n = int(1.3 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, a in ((262.0, 1.0), (329.0, 0.9)):
        for k, ak in ((1, 1.0), (2, 0.7), (3, 0.55), (4, 0.35), (5, 0.25), (6, 0.15), (7, 0.1)):
            x += a * ak * np.sin(2 * np.pi * k * f * t + 0.4 * k)
    x *= 1.0 + 0.08 * np.sign(np.sin(2 * np.pi * 85 * t))
    x += 0.1 * lowpass(band_noise(n, 200, 3000, 47), 3000, 2)                 # un poco de aire
    env = np.minimum(1.0, t / 0.04) * np.where(t > 1.1, np.maximum(0.0, 1 - (t - 1.1) / 0.18), 1.0)
    return np.tanh(1.7 * x / np.max(np.abs(x))) * env


# ----------------------------------------------------------------------------------------------- helicóptero
def slap_template(k):
    """Golpe de pala: soplo grave + "tac" del aire cortado (k muestras)."""
    tau = np.arange(k) / SR
    thump = np.sin(2 * np.pi * 70 * tau) * np.exp(-tau / 0.012)
    slap = bandpass(band_noise(k, 300, 2500, 61), 300, 2500) * np.exp(-tau / 0.007)
    return thump + 0.9 * slap


def rotor_loop():
    """Rotor + turbina en régimen: 24 golpes de pala por segundo, silbido de turbina (880 y 1760 Hz), 1 s exacto."""
    n = SR
    t = np.arange(n) / SR
    out = np.zeros(n)
    tmpl = slap_template(int(0.05 * SR))
    rng = np.random.default_rng(51)
    rate = 24
    for i in range(rate):
        place(out, int(i * n / rate) % n, tmpl, rng.uniform(0.88, 1.0) * (1.0 if i % 2 == 0 else 0.86), True)
    chop = lowpass(np.tile(out, 3), 3200)[n:2 * n]
    chop = chop / (np.max(np.abs(chop)) + 1e-9)
    air = band_noise(n, 400, 4200, 52) * (0.55 + 0.45 * np.sin(2 * np.pi * 24 * t))        # el viento del rotor, que late
    whine = np.sin(2 * np.pi * 880 * t) + 0.5 * np.sin(2 * np.pi * 1760 * t + 0.6) + 0.25 * np.sin(2 * np.pi * 2640 * t)
    turbine = band_noise(n, 2800, 6800, 53)
    x = chop + 0.24 * air + 0.07 * whine + 0.10 * turbine
    return np.tanh(1.3 * x)


def heli_start():
    """Arranque de turbina y rotor (2,6 s): el silbido sube, las palas van de lentas a rápidas y queda en régimen."""
    total = int(2.6 * SR)
    t = np.arange(total) / SR
    out = np.zeros(total)
    tmpl = slap_template(int(0.05 * SR))
    ramp = np.clip(t / 2.1, 0.0, 1.0)
    rate = 2.0 + 22.0 * ramp ** 1.6
    phase = np.cumsum(rate) / SR
    last = -1
    rng = np.random.default_rng(52)
    for i in range(total):
        idx = int(phase[i])
        if idx != last:
            last = idx
            place(out, i, tmpl, (0.35 + 0.65 * ramp[i]) * rng.uniform(0.9, 1.0), False)
    out = lowpass(out, 3200)
    out = out / (np.max(np.abs(out)) + 1e-9)
    f = 180.0 + 720.0 * ramp ** 0.9
    whine = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.4 * np.sin(2 * np.pi * np.cumsum(2 * f) / SR)
    air = band_noise(total, 400, 4200, 54) * (0.2 + 0.8 * ramp)
    x = out + 0.14 * whine * (0.2 + 0.8 * ramp) + 0.2 * air
    x *= np.minimum(1.0, t / 0.05) * np.where(t > 2.3, np.maximum(0.0, 1 - (t - 2.3) / 0.3), 1.0)
    return np.tanh(1.3 * x)


def main():
    save("delivery_truck_engine", diesel_loop())
    save("delivery_truck_start", diesel_start())
    save("delivery_truck_horn", truck_horn())
    save("helicopter_rotor", rotor_loop())
    save("helicopter_start", heli_start())


if __name__ == "__main__":
    main()
