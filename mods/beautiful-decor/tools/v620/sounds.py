#!/usr/bin/env python3
"""V6.2.0: sonidos de los trenes, hechos desde cero (síntesis con numpy) y los avisos de estación (espeak-ng + filtro de megáfono).
Todos los bucles son periódicos (se cierran sin clic). Salida: assets/beautifuldecor/sounds/*.ogg"""
import ctypes, subprocess, tempfile, wave
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/beautifuldecor/sounds"
SR = 32000
rng = np.random.default_rng(620)


def write_ogg(name, x, q=4):
    x = np.asarray(x, np.float64)
    x = x / max(1e-9, np.max(np.abs(x))) * 0.92
    pcm = (x * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / "a.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", str(q), str(OUT / f"{name}.ogg")], check=True)


def noise(n):
    return rng.standard_normal(n)


def band(x, lo, hi, order=2.0):
    """Filtro de banda circular (en frecuencia): mantiene la periodicidad del bucle."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    X = np.fft.rfft(x)
    g = np.ones_like(f)
    if lo:
        g *= 1.0 / (1.0 + (lo / np.maximum(f, 1e-9)) ** (2 * order))
    if hi:
        g *= 1.0 / (1.0 + (f / hi) ** (2 * order))
    return np.fft.irfft(X * g, n)


def add_wrapped(buf, start, sig):
    n = len(buf)
    idx = (np.arange(len(sig)) + start) % n
    np.add.at(buf, idx, sig)


def env_decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def clack(strength=1.0, bright=1.0, dur=0.11):
    n = int(dur * SR)
    t = np.arange(n) / SR
    hit = band(noise(n), 700 * bright, 3200 * bright) * env_decay(n, 0.018)
    thump = np.sin(2 * np.pi * 88 * t) * env_decay(n, 0.03)
    ring = np.sin(2 * np.pi * 1450 * bright * t) * env_decay(n, 0.012) * 0.25
    return strength * (hit * 1.0 + thump * 1.2 + ring)


def rumble(n, lo, hi, gain=1.0):
    return band(noise(n), lo, hi) * gain


def norm(x):
    return x / max(1e-9, np.max(np.abs(x)))


# ------------------------------------------------------------------------------------------ rodadura
def roll_freight():
    L = 1.72
    n = int(L * SR)
    base = norm(rumble(n, 25, 180)) * 0.55 + norm(rumble(n, 180, 900)) * 0.18
    out = base.copy()
    for joint in (0.0, 0.86):
        for off, s in ((0.0, 1.0), (0.085, 0.9), (0.43, 0.8), (0.515, 0.7)):
            add_wrapped(out, int((joint + off) * SR), clack(s * 0.9))
    return out


def roll_passenger():
    L = 1.44
    n = int(L * SR)
    out = norm(rumble(n, 30, 260)) * 0.40 + norm(rumble(n, 260, 1400)) * 0.14
    for joint in (0.0, 0.72):
        for off, s in ((0.0, 0.8), (0.07, 0.7), (0.36, 0.6), (0.43, 0.55)):
            add_wrapped(out, int((joint + off) * SR), clack(s * 0.65, bright=1.2, dur=0.09))
    return out


def roll_steam():
    L = 1.6
    n = int(L * SR)
    out = norm(rumble(n, 25, 140)) * 0.25 + norm(rumble(n, 300, 1800)) * 0.07
    for k in range(8):                                   # 8 escapes por vuelta de bucle; el 1º y el 5º más fuertes
        t0 = int(k * (L / 8) * SR)
        m = int(0.16 * SR)
        puff = band(noise(m), 160, 1700) * np.minimum(1, np.arange(m) / (0.012 * SR)) * env_decay(m, 0.045)
        puff += np.sin(2 * np.pi * 62 * np.arange(m) / SR) * env_decay(m, 0.05) * 0.8
        add_wrapped(out, t0, puff * (1.0 if k % 4 == 0 else 0.7))
    for k in range(2):                                   # traqueteo de bielas
        add_wrapped(out, int((0.1 + 0.8 * k) * SR), clack(0.35, bright=0.8))
    return out


def roll_metro():
    L = 2.0
    n = int(L * SR)
    t = np.arange(n) / SR
    out = norm(rumble(n, 40, 400)) * 0.20 + norm(rumble(n, 400, 2500)) * 0.07
    f0 = 240.0
    wob = 1.0 + 0.012 * np.sin(2 * np.pi * 3 * t / 1.0 * 1.0)             # 6 ciclos en 2 s: periódico
    for h, a in ((1, 0.30), (2, 0.20), (3, 0.14), (5, 0.07), (8, 0.04)):
        out += a * np.sin(2 * np.pi * h * f0 * t * wob + 0.0) * (1 + 0.15 * np.sin(2 * np.pi * 2 * t))
    out += 0.12 * np.sin(2 * np.pi * 1200 * t + 3 * np.sin(2 * np.pi * 4 * t))   # silbido de los inversores
    for joint in (0.0, 0.5, 1.0, 1.5):
        add_wrapped(out, int(joint * SR), clack(0.22, bright=1.1, dur=0.07))
    return out


# ------------------------------------------------------------------------------------------ frenos
def screech(L, partials, am_hz, noise_gain, wob_hz, wob_depth, drive=1.0):
    n = int(L * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for i, (f, a) in enumerate(partials):
        ph = rng.uniform(0, 2 * np.pi)
        wob = 1.0 + wob_depth * np.sin(2 * np.pi * wob_hz * (1 + 0.5 * i) * t + ph)
        out += a * np.sin(2 * np.pi * f * t * wob + 2.2 * np.sin(2 * np.pi * (wob_hz + i) * t))
    am = 0.62 + 0.38 * np.sin(2 * np.pi * am_hz * t) ** 2
    hiss = band(noise(n), 2200, 7500) * noise_gain
    out = (out + hiss) * am
    return np.tanh(out * drive)


def brake_squeal():
    return screech(2.0, [(1620, 0.30), (2410, 0.26), (3140, 0.2), (1180, 0.12), (4020, 0.08)], 30, 0.25, 3, 0.012, 1.6)


def brake_metro():
    n = int(2.0 * SR)
    t = np.arange(n) / SR
    hiss = band(noise(n), 1800, 6000) * 0.35
    tone = 0.30 * np.sin(2 * np.pi * 760 * t + 1.5 * np.sin(2 * np.pi * 2 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 1 * t) ** 2)
    return hiss + tone + norm(rumble(n, 30, 200)) * 0.25


def emergency():
    a = screech(2.0, [(1980, 0.34), (2960, 0.30), (3920, 0.24), (1490, 0.20), (5100, 0.14), (2480, 0.18)], 24, 0.45, 4, 0.02, 3.2)
    b = screech(2.0, [(2210, 0.3), (3340, 0.26), (4470, 0.2), (1730, 0.2)], 36, 0.4, 5, 0.03, 3.0)
    n = len(a)
    crack = np.zeros(n)
    for _ in range(90):                                  # chispas: crepitar
        t0 = int(rng.uniform(0, 2.0) * SR)
        m = int(rng.uniform(0.004, 0.018) * SR)
        add_wrapped(crack, t0, band(noise(m), 1500, 9000) * env_decay(m, 0.004) * rng.uniform(0.3, 1.0))
    rum = norm(rumble(n, 30, 180)) * 0.5
    return np.tanh((a + b) * 1.4 + crack * 1.2 + rum)


# ------------------------------------------------------------------------------------------ avisos
def bell(f, dur, amp=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for ratio, a, tau in ((1.0, 1.0, 0.55), (2.0, 0.45, 0.30), (2.76, 0.25, 0.22), (5.4, 0.1, 0.10)):
        s += a * np.sin(2 * np.pi * f * ratio * t) * env_decay(n, tau)
    s *= np.minimum(1, t / 0.004)
    return s * amp


def chime():
    n = int(1.9 * SR)
    out = np.zeros(n)
    for start, f in ((0.0, 659.3), (0.55, 523.3)):
        s = bell(f, 1.3)
        i = int(start * SR)
        out[i:i + len(s)] += s[: n - i]
    return out


def speak(text):
    import espeakng_loader as e
    lib = ctypes.CDLL(e.get_library_path())
    lib.espeak_Initialize.restype = ctypes.c_int
    sr = lib.espeak_Initialize(1, 0, e.get_data_path().encode(), 0)
    buf = []
    CB = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.POINTER(ctypes.c_short), ctypes.c_int, ctypes.c_void_p)

    def cb(w, n, ev):
        if w and n > 0:
            buf.append(np.ctypeslib.as_array(w, shape=(n,)).copy())
        return 0
    c = CB(cb)
    lib.espeak_SetSynthCallback(c)
    lib.espeak_SetVoiceByName(b"es")
    lib.espeak_SetParameter(1, 135, 0)       # velocidad
    lib.espeak_SetParameter(3, 38, 0)        # tono
    t = text.encode("utf-8")
    lib.espeak_Synth(t, len(t) + 1, 0, 0, 0, 1, None, None)
    lib.espeak_Synchronize()
    x = np.concatenate(buf).astype(np.float64) / 32768.0
    # a 32 kHz
    idx = np.arange(0, len(x) - 1, sr / SR)
    return np.interp(idx, np.arange(len(x)), x)


def megaphone(x):
    x = band(np.concatenate([np.zeros(int(0.05 * SR)), x, np.zeros(int(0.3 * SR))]), 420, 3300, 2.0)
    x = np.tanh(norm(x) * 2.6)
    x += band(noise(len(x)), 1000, 5000) * 0.015
    echo = np.zeros(len(x))
    d = int(0.045 * SR)
    echo[d:] = x[:-d] * 0.22
    return band(x + echo, 380, 3400)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    jobs = {"train_roll_freight": roll_freight(), "train_roll_passenger": roll_passenger(), "train_roll_steam": roll_steam(), "train_roll_metro": roll_metro(),
            "train_brake_squeal": brake_squeal(), "train_brake_metro": brake_metro(), "train_emergency": emergency(), "train_chime": chime()}
    for name, x in jobs.items():
        write_ogg(name, x)
    for name, text in (("train_say_next", "Próxima estación"), ("train_say_arrived", "Llegada"), ("train_say_departing", "Saliendo"),
                       ("train_say_final", "Estación final"), ("train_say_destination", "Destino")):
        write_ogg(name, megaphone(speak(text)), q=5)
    print("sonidos:", sorted(p.name for p in OUT.glob("train_*.ogg")))


if __name__ == "__main__":
    main()
