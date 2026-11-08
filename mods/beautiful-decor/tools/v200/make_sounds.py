#!/usr/bin/env python3
"""Sintetiza los sonidos de la 2.0.0 (moto y puerta de garaje) con numpy y los codifica a .ogg mono con ffmpeg.

Uso (desde la raíz del proyecto):  python3 -m tools.v200.make_sounds
"""
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/beautifuldecor/sounds"
SR = 32000
RNG = np.random.default_rng(2000)


def band_noise(n, lo, hi, seed):
    """Ruido filtrado y PERIÓDICO (se hace en frecuencia con exactamente n muestras): sirve para bucles sin corte."""
    rng = np.random.default_rng(seed)
    spec = rng.normal(size=n // 2 + 1) + 1j * rng.normal(size=n // 2 + 1)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    mask = ((freqs >= lo) & (freqs <= hi)).astype(float)
    x = np.fft.irfft(spec * mask, n)
    return x / (np.max(np.abs(x)) + 1e-9)


def save(name, x, peak=0.9):
    x = np.asarray(x, dtype=np.float64)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        wavfile.write(wav, SR, (x * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1", "-c:a", "libvorbis",
                        "-q:a", "3", str(OUT / f"{name}.ogg")], check=True)
    print(f"{name}.ogg  {len(x) / SR:.2f} s")


def engine_loop():
    """Motor de moto de cuatro cilindros: 1 s exacto, f0 = 100 Hz (el juego sube/baja el tono)."""
    n = SR
    t = np.arange(n) / SR
    f0 = 100.0
    x = np.zeros(n)
    for k in range(1, 26):
        amp = 1.0 / (k ** 0.8)
        if k % 2 == 0:
            amp *= 1.25                       # armónicos pares: más "metálico", típico de un 4 en línea
        x += amp * np.sin(2 * np.pi * k * f0 * t + 0.7 * k)
    x += 0.9 * np.sin(2 * np.pi * (f0 / 2) * t + 0.3)      # retumbo grave
    ripple = 1.0 + 0.18 * np.sin(2 * np.pi * 5 * t) + 0.12 * np.sin(2 * np.pi * 13 * t + 1.1)   # cilindros desiguales
    x *= ripple
    x += 0.45 * band_noise(n, 800, 6000, 1) * (0.6 + 0.4 * np.sin(2 * np.pi * 10 * t))        # admisión
    x += 0.18 * np.sin(2 * np.pi * 1500 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 3 * t))         # silbido
    x = np.tanh(1.4 * x / np.max(np.abs(x)))
    return x


def horn():
    n = int(0.75 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, a in ((392.0, 1.0), (494.0, 0.9)):
        for k, ak in ((1, 1.0), (2, 0.5), (3, 0.35), (4, 0.2), (5, 0.12)):
            x += a * ak * np.sin(2 * np.pi * k * f * t)
    x *= 1.0 + 0.08 * np.sign(np.sin(2 * np.pi * 110 * t))           # zumbido de la bocina eléctrica
    env = np.minimum(1.0, t / 0.015) * np.where(t > 0.6, np.maximum(0.0, 1 - (t - 0.6) / 0.15), 1.0)
    x = np.tanh(1.8 * x / np.max(np.abs(x))) * env
    return x


def start():
    """Marcha: arranque del motor (0,5 s) y el motor que "prende" y se acelera un poco (1,1 s)."""
    n1, n2 = int(0.5 * SR), int(1.2 * SR)
    t1 = np.arange(n1) / SR
    crank = np.sin(2 * np.pi * 70 * t1) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * t1)))
    crank += 0.6 * np.sin(2 * np.pi * 420 * t1) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t1))
    crank += 0.3 * band_noise(n1, 500, 3000, 3)
    crank *= np.minimum(1.0, t1 / 0.02)
    t2 = np.arange(n2) / SR
    f = 55 + 70 * np.minimum(1.0, t2 / 0.35) + 55 * np.exp(-((t2 - 0.55) ** 2) / 0.02) - 25 * np.clip((t2 - 0.8) / 0.4, 0, 1)
    phase = 2 * np.pi * np.cumsum(f) / SR
    eng = np.zeros(n2)
    for k in range(1, 22):
        eng += (1.0 / k ** 0.8) * (1.25 if k % 2 == 0 else 1.0) * np.sin(k * phase + 0.7 * k)
    eng += 0.4 * band_noise(n2, 800, 6000, 4) * np.minimum(1.0, t2 / 0.2)
    eng = np.tanh(1.3 * eng / np.max(np.abs(eng)))
    eng *= np.minimum(1.0, t2 / 0.05) * np.where(t2 > 0.9, np.maximum(0.0, 1 - (t2 - 0.9) / 0.3), 1.0)
    return np.concatenate([0.8 * crank / np.max(np.abs(crank)), eng])


def garage_door():
    """Puerta seccional subiendo/bajando: motor + rodillos + golpecitos de los paneles (1,25 s, lo que dura la animación)."""
    n = int(1.25 * SR)
    t = np.arange(n) / SR
    motor = np.sin(2 * np.pi * (95 + 18 * np.sin(2 * np.pi * 0.6 * t)) * t) + 0.5 * np.sin(2 * np.pi * 190 * t)
    motor *= np.minimum(1.0, t / 0.1) * np.where(t > 1.0, np.maximum(0.0, 1 - (t - 1.0) / 0.25), 1.0)
    roll = band_noise(n, 120, 900, 5) * (0.55 + 0.45 * np.sin(2 * np.pi * 7 * t))
    clacks = np.zeros(n)
    for i, ts in enumerate(np.arange(0.1, 1.08, 0.15)):
        idx = int(ts * SR)
        k = int(0.03 * SR)
        click = band_noise(k, 300, 4000, 10 + i) * np.exp(-np.arange(k) / (0.006 * SR))
        clacks[idx:idx + k] += click * (0.9 if i % 2 == 0 else 0.6)
    x = 0.55 * motor + 0.7 * roll + 0.9 * clacks
    # golpe final (el tope de la puerta)
    k = int(0.16 * SR)
    thud = np.sin(2 * np.pi * 70 * np.arange(k) / SR) * np.exp(-np.arange(k) / (0.04 * SR))
    thud += 0.5 * band_noise(k, 200, 2500, 99) * np.exp(-np.arange(k) / (0.02 * SR))
    x[n - k - int(0.02 * SR):n - int(0.02 * SR)] += 1.1 * thud
    return x


def main():
    save("motorcycle_engine", engine_loop())
    save("motorcycle_horn", horn())
    save("motorcycle_start", start())
    save("garage_door", garage_door())


if __name__ == "__main__":
    main()
