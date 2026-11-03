#!/usr/bin/env python3
"""
Sintetiza todos los sonidos propios de Arsenal Orbital (no usa sonidos de Minecraft).
Genera WAV con numpy y los convierte a OGG Vorbis mono con ffmpeg.

Uso: python3 tools/make_sounds.py <carpeta de salida>
"""
import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100
rng = np.random.default_rng(2026)


# ---------------------------------------------------------------- utilidades

def tt(d):
    return np.arange(int(d * SR)) / SR


def env(d, pts):
    """Envolvente por puntos [(tiempo, valor), ...]."""
    x = tt(d)
    ts, vs = zip(*pts)
    return np.interp(x, ts, vs)


def osc(freq, kind="sine"):
    """Oscilador con frecuencia variable (array) o fija."""
    ph = np.cumsum(2 * np.pi * np.asarray(freq) / SR)
    if kind == "sine":
        return np.sin(ph)
    if kind == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1) - 1
    if kind == "square":
        return np.sign(np.sin(ph))
    if kind == "tri":
        return 2 * np.abs(2 * ((ph / (2 * np.pi)) % 1) - 1) - 1
    raise ValueError(kind)


def noise(d):
    return rng.standard_normal(int(d * SR))


def shape(x, gain):
    """Filtro que cambia con el tiempo: gain(freqs, tiempo_normalizado) -> ganancia (STFT)."""
    n, hop = 2048, 512
    win = np.hanning(n)
    pad = np.concatenate([np.zeros(n), x, np.zeros(n)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    total = len(pad)
    for s in range(0, total - n, hop):
        seg = pad[s:s + n] * win
        spec = np.fft.rfft(seg)
        tc = np.clip((s - n + n / 2) / max(1, len(x)), 0, 1)
        spec *= gain(freqs, tc)
        out[s:s + n] += np.fft.irfft(spec) * win
        norm[s:s + n] += win * win
    out /= np.maximum(norm, 1e-6)
    return out[n:n + len(x)]


def band(fc, width):
    return lambda f, t: np.exp(-0.5 * ((np.log2(np.maximum(f, 1) / max(fc(t) if callable(fc) else fc, 1))) / width) ** 2)


def lowpass(cut, slope=2.0):
    return lambda f, t: 1 / (1 + (f / max(cut(t) if callable(cut) else cut, 1)) ** slope)


def highpass(cut, slope=2.0):
    return lambda f, t: 1 / (1 + (max(cut(t) if callable(cut) else cut, 1) / np.maximum(f, 1)) ** slope)


def reverb(x, seconds=2.5, mix=0.35, bright=0.5):
    """Reverberación de sala enorme (convolución con ruido que decae)."""
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * (6.9 / seconds))
    ir = shape(ir, lowpass(lambda t: 1500 + 9000 * bright * (1 - t)))
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x) + n]
    dry = np.concatenate([x, np.zeros(n)])
    return dry * (1 - mix) + wet * mix * 3


def thump(d, f0, f1, decay):
    x = tt(d)
    return osc(f1 + (f0 - f1) * np.exp(-x * 18)) * np.exp(-x / decay)


def bell(d, f0, ratios, decays, amps, beat=0.0):
    x = tt(d)
    out = np.zeros_like(x)
    for r, dc, a in zip(ratios, decays, amps):
        out += a * np.sin(2 * np.pi * f0 * r * x + beat * np.sin(2 * np.pi * 0.7 * x)) * np.exp(-x / dc)
    out *= 1 - np.exp(-x * 400)
    return out


def crackle(d, density, decay=0.002, hp=1500):
    n = int(d * SR)
    x = np.zeros(n)
    k = rng.random(n) < density / SR
    x[k] = rng.standard_normal(k.sum()) * 2
    ker = np.exp(-np.arange(int(0.02 * SR)) / (decay * SR))
    x = np.convolve(x, ker)[:n]
    return shape(x, highpass(hp))


def sat(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def fit(x, d):
    n = int(d * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


# ---------------------------------------------------------------- sonidos

def rush_wind():
    d = 6.0
    w = shape(noise(d), lambda f, t: band(lambda t: 250 + 900 * np.sin(np.pi * t) ** 2, 1.1)(f, t) + 0.5 * lowpass(180)(f, t))
    rumble = shape(noise(d), lowpass(90, 3)) * 2.5
    e = env(d, [(0, 0), (1.5, 1), (4.5, 0.9), (6, 0)])
    flutter = 1 + 0.25 * np.sin(2 * np.pi * tt(d) * (3 + 2 * tt(d)))
    return (w * flutter + rumble) * e


def charge_whine():
    d = 4.5
    x = tt(d)
    f = 110 * (16 ** (x / d) )
    tone = osc(f, "saw") * 0.35 + osc(f * 1.5) * 0.25 + osc(f * 2.01) * 0.15
    tone = shape(tone, lowpass(lambda t: 500 + 6000 * t))
    trem = 0.7 + 0.3 * np.sin(np.cumsum(2 * np.pi * (4 + 30 * (x / d) ** 2) / SR))
    sparks = crackle(d, 20 + 0) * np.linspace(0.1, 1.2, len(x))
    return (tone * trem * env(d, [(0, 0), (0.3, 0.6), (d - 0.05, 1), (d, 0)]) + sparks * 0.4)


def sonic_blast():
    d = 5.0
    crack = shape(noise(0.06), highpass(800)) * np.exp(-tt(0.06) * 60) * 2
    boom = thump(d, 90, 28, 1.4) * 1.6
    rumble = shape(noise(d), lowpass(lambda t: 900 * (1 - t) + 80, 2.5)) * np.exp(-tt(d) / 1.6) * 2
    return reverb(sat(mix(crack, boom, rumble), 1.8), 3.0, 0.3)


def cosmic_swell():
    d = 7.5
    x = tt(d)
    notes = [110, 164.8, 220, 277.2, 329.6, 493.9]
    pad = sum(osc(n * (1 + 0.003 * np.sin(2 * np.pi * 0.2 * x + i)), "saw") for i, n in enumerate(notes)) / len(notes)
    pad = shape(pad, lowpass(lambda t: 300 + 4000 * np.sin(np.pi * min(1, t * 1.2)) ** 2))
    shimmer = sum(osc(np.full(len(x), n * 4.0)) * 0.1 * (0.5 + 0.5 * np.sin(2 * np.pi * (0.3 + i * 0.13) * x)) for i, n in enumerate(notes[2:]))
    e = env(d, [(0, 0), (3.5, 1), (5.5, 0.8), (d, 0)])
    return reverb((pad + shimmer) * e, 4.0, 0.45, 0.8)


def power_down():
    d = 3.0
    x = tt(d)
    f = 900 * np.exp(-x * 1.4) + 40
    s = osc(f, "square") * 0.3 + osc(f * 0.5) * 0.6
    s = shape(s, lowpass(lambda t: 4000 * (1 - t) + 150))
    return reverb(s * env(d, [(0, 0), (0.02, 1), (2.2, 0.6), (d, 0)]), 2.0, 0.3)


def power_up():
    d = 3.0
    x = tt(d)
    f = 50 * (18 ** (x / d))
    s = osc(f, "square") * 0.25 + osc(f * 0.5) * 0.6 + osc(f * 2.003) * 0.15
    s = shape(s, lowpass(lambda t: 200 + 5000 * t))
    return reverb(s * env(d, [(0, 0), (0.1, 0.5), (2.8, 1), (d, 0)]), 2.0, 0.3)


def explosion():
    d = 5.5
    burst = shape(noise(d), lowpass(lambda t: 7000 * np.exp(-t * 7) + 120, 1.6)) * np.exp(-tt(d) / 0.9) * 2.2
    boom = thump(d, 110, 32, 0.9) * 1.8
    debris = crackle(d, 60, 0.004, 800) * env(d, [(0, 0), (0.2, 1), (3, 0.3), (d, 0)]) * 0.5
    return reverb(sat(mix(burst, boom, debris), 2.2), 3.0, 0.3)


def crystal_resonance():
    d = 5.0
    b = bell(d, 523, [1, 2.76, 5.4, 8.93, 13.3], [3.5, 2.2, 1.4, 0.9, 0.5], [1, 0.6, 0.35, 0.2, 0.1], beat=0.8)
    b2 = bell(d, 527, [1, 2.76], [3.0, 2.0], [0.6, 0.3])
    return reverb((b + b2) * 0.6, 3.5, 0.5, 1.0)


def ui_confirm():
    d = 1.2
    a = bell(0.5, 880, [1, 2, 3], [0.3, 0.15, 0.08], [1, 0.3, 0.1])
    b = bell(0.7, 1318.5, [1, 2, 3], [0.4, 0.2, 0.1], [1, 0.3, 0.1])
    return reverb(mix(a * 0.6, np.concatenate([np.zeros(int(0.09 * SR)), b * 0.6])), 1.5, 0.3, 1.0)


def crystal_chime():
    d = 3.0
    out = np.zeros(int(d * SR))
    for i, (st, f) in enumerate([(0, 1567), (0.11, 2093), (0.2, 1760), (0.33, 2637), (0.5, 2349)]):
        b = bell(d - st, f, [1, 2.76, 5.4], [1.2, 0.6, 0.3], [1, 0.4, 0.2])
        s = int(st * SR)
        out[s:s + len(b)] += b * (0.5 - i * 0.05)
    return reverb(out, 3.0, 0.5, 1.0)


def deep_thunder():
    d = 7.0
    x = tt(d)
    base = shape(noise(d), lowpass(lambda t: 600 * np.exp(-t * 3) + 70, 2.4)) * 2.5
    mod = np.clip(np.convolve(rng.standard_normal(len(x)), np.ones(4000) / 4000, "same") * 40 + 0.6, 0.1, 2)
    crack = shape(noise(0.4), highpass(1500)) * np.exp(-tt(0.4) * 12)
    e = env(d, [(0, 0), (0.08, 1), (2, 0.7), (d, 0)])
    return reverb(sat(mix(crack, base * mod * e), 1.6), 3.5, 0.35)


def plasma_whoosh():
    d = 2.2
    w = shape(noise(d), band(lambda t: 300 * 20 ** np.sin(np.pi * t), 0.6)) * 2.5
    sizzle = crackle(d, 300, 0.001, 3000) * 0.3
    return reverb((w + sizzle) * env(d, [(0, 0), (0.7, 1), (d, 0)]), 1.5, 0.25)


def planet_groan():
    d = 6.5
    x = tt(d)
    f = 38 + 10 * np.sin(2 * np.pi * 0.17 * x) - 8 * x / d
    g = sum(osc(f * m * (1 + 0.01 * k), "saw") for k, m in enumerate([1, 1.005, 2, 3.01])) / 3
    g = shape(sat(g, 3), lowpass(lambda t: 400 + 300 * np.sin(np.pi * t)))
    roar = shape(noise(d), band(120, 1.0)) * 0.8
    return reverb((g + roar) * env(d, [(0, 0), (1.5, 1), (4.5, 0.8), (d, 0)]), 3.0, 0.35)


def machine_hum():
    d = 5.0
    x = tt(d)
    hum = sum(a * osc(np.full(len(x), 50.0 * h)) for h, a in [(1, 1), (2, 0.6), (3, 0.35), (4, 0.2), (6, 0.1)])
    hum *= 0.85 + 0.15 * np.sin(2 * np.pi * 0.5 * x)
    fan = shape(noise(d), band(900, 1.3)) * 0.25
    whine = osc(3150 + 20 * np.sin(2 * np.pi * 0.3 * x)) * 0.04
    return (hum * 0.4 + fan + whine) * env(d, [(0, 0), (0.6, 1), (4.4, 1), (d, 0)])


def heartbeat():
    d = 1.6
    a = thump(0.5, 80, 42, 0.12)
    b = thump(0.5, 70, 38, 0.15) * 0.75
    out = mix(a, np.concatenate([np.zeros(int(0.28 * SR)), b]))
    return reverb(fit(sat(out * 1.4, 1.5), d), 1.2, 0.2)


def energy_crack():
    d = 3.0
    zap = crackle(0.8, 2500, 0.0008, 2000) * env(0.8, [(0, 1), (0.8, 0)]) * 1.5
    tone = osc(2400 * np.exp(-tt(0.8) * 4) + 80) * np.exp(-tt(0.8) * 5) * 0.5
    boom = thump(d, 70, 30, 0.8)
    return reverb(sat(mix(zap + tone, boom), 1.8), 2.5, 0.35)


def plasma_crackle():
    d = 4.5
    c = crackle(d, 120, 0.003, 600)
    hiss = shape(noise(d), band(2500, 1.2)) * 0.15
    roar = shape(noise(d), lowpass(250)) * 0.6
    return (c * 0.7 + hiss + roar) * env(d, [(0, 0), (0.5, 1), (4, 1), (d, 0)])


def station_online():
    d = 4.5
    out = np.zeros(int(d * SR))
    for i, f in enumerate([261.6, 329.6, 392, 523.3, 659.3]):
        b = bell(d - i * 0.18, f, [1, 2, 3, 4.01], [1.6, 0.8, 0.4, 0.2], [1, 0.4, 0.2, 0.1])
        s = int(i * 0.18 * SR)
        out[s:s + len(b)] += b * 0.35
    hum = machine_hum()[:len(out)] * env(d, [(0, 0), (1.5, 0.6), (d, 0)])
    return reverb(out + fit(hum, d), 2.5, 0.35, 0.9)


def void_drone():
    d = 8.0
    x = tt(d)
    dr = osc(np.full(len(x), 41.0)) * 0.6 + osc(np.full(len(x), 41.6)) * 0.6 + osc(np.full(len(x), 61.7)) * 0.3 + osc(82.3 + np.sin(2 * np.pi * 0.1 * x)) * 0.2
    wind = shape(noise(d), band(lambda t: 300 + 200 * np.sin(2 * np.pi * t), 0.8)) * 0.3
    return reverb((dr + wind) * env(d, [(0, 0), (2, 1), (6, 1), (d, 0)]), 4.0, 0.45, 0.4)


def titan_roar():
    d = 6.0
    x = tt(d)
    f = 55 * (1 - 0.15 * x / d)
    r = sum(osc(f * m * (1 + 0.007 * k), "saw") for k, m in enumerate([1, 1.01, 1.5, 2, 2.02, 3])) / 4
    r = shape(sat(r, 2.5), lowpass(lambda t: 300 + 2500 * np.sin(np.pi * t)))
    n = shape(noise(d), band(400, 1.2)) * env(d, [(0, 0), (1, 1), (d, 0)])
    return reverb((r + n * 0.7) * env(d, [(0, 0), (1.2, 1), (3.5, 0.9), (d, 0)]), 3.5, 0.4)


def capacitor_charge():
    d = 3.5
    x = tt(d)
    f = 200 * 10 ** (x / d)
    whine = osc(f) * 0.4 + osc(f * 2) * 0.15
    rate = 3 + 25 * (x / d) ** 2
    ticks = (np.sin(np.cumsum(2 * np.pi * rate / SR)) > 0.95).astype(float)
    ticks = np.convolve(ticks, np.exp(-np.arange(400) / 60))[:len(x)] * osc(np.full(len(x), 2200.0)) * 0.3
    return reverb((whine + ticks) * env(d, [(0, 0), (0.2, 0.5), (d - 0.05, 1), (d, 0)]), 1.5, 0.25)


def clank(d=1.5):
    b = bell(d, 340, [1, 2.32, 4.25, 6.8, 9.6], [0.5, 0.35, 0.2, 0.12, 0.08], [1, 0.7, 0.5, 0.3, 0.2])
    hit = shape(noise(0.05), highpass(1200)) * np.exp(-tt(0.05) * 80)
    return mix(b * 0.7, hit)


def mech_extend():
    d = 2.2
    x = tt(1.5)
    servo = osc(300 + 500 * x / 1.5, "saw") * 0.15 + osc(150 + 250 * x / 1.5) * 0.25
    servo = shape(servo, lowpass(2000)) * env(1.5, [(0, 0), (0.1, 1), (1.4, 1), (1.5, 0)])
    out = mix(servo, np.concatenate([np.zeros(int(1.45 * SR)), clank(0.75)]))
    return reverb(out, 1.5, 0.3)


def mech_retract():
    d = 2.2
    x = tt(1.5)
    servo = osc(800 - 500 * x / 1.5, "saw") * 0.15 + osc(400 - 250 * x / 1.5) * 0.25
    servo = shape(servo, lowpass(2000)) * env(1.5, [(0, 0), (0.1, 1), (1.4, 1), (1.5, 0)])
    out = mix(servo, np.concatenate([np.zeros(int(1.45 * SR)), clank(0.75) * 0.8]))
    return reverb(out, 1.5, 0.3)


def beam_impact():
    d = 4.0
    crack = shape(noise(0.15), highpass(1000)) * np.exp(-tt(0.15) * 25) * 2
    sizzle = crackle(d, 800, 0.001, 2500) * env(d, [(0, 1), (2.5, 0.2), (d, 0)]) * 0.6
    boom = thump(d, 100, 30, 1.0) * 1.5
    return reverb(sat(mix(crack, sizzle, boom), 2), 2.5, 0.3)


def countdown_tick():
    d = 0.7
    b = osc(np.full(int(0.12 * SR), 1046.5), "square") * 0.25 + osc(np.full(int(0.12 * SR), 2093.0)) * 0.2
    b *= env(0.12, [(0, 0), (0.005, 1), (0.1, 0.8), (0.12, 0)])
    b = shape(b, lowpass(4000))
    return reverb(fit(b, d), 0.8, 0.3, 1.0)


def singularity_birth():
    d = 6.5
    x = tt(d)
    rise = shape(noise(d), band(lambda t: 100 * 30 ** t, 0.7)) * (x / d) ** 2 * 2
    drone = osc(80 * np.exp(-x * 0.3) + 25, "saw") * 0.5
    drone = shape(drone, lowpass(400))
    boom = np.concatenate([np.zeros(int(5.2 * SR)), thump(1.3, 70, 25, 0.6) * 2])
    out = mix(rise * env(d, [(0, 0), (5.2, 1), (5.25, 0), (d, 0)]), drone * env(d, [(0, 0), (2, 1), (d, 0)]), boom)
    return reverb(sat(out, 1.5), 3.0, 0.4)


def warp_jump():
    d = 4.0
    x = tt(3.0)
    tone = osc(80 * 25 ** (x / 3), "saw") * 0.3
    tone = shape(tone, lowpass(lambda t: 300 + 8000 * t))
    whoosh = shape(noise(3.0), band(lambda t: 200 * 20 ** t, 0.6)) * 1.5
    lead = (tone + whoosh) * (x / 3) ** 1.5
    boom = thump(1.0, 120, 35, 0.4) * 1.5
    return reverb(mix(lead, np.concatenate([np.zeros(int(3.0 * SR)), boom])), 2.5, 0.35)


def missile_launch():
    d = 5.0
    ign = shape(noise(0.2), highpass(600)) * np.exp(-tt(0.2) * 15) * 1.5
    roar = shape(noise(d), band(lambda t: 250 + 150 * t, 1.6)) * 1.4
    rumble = shape(noise(d), lowpass(80, 3)) * 2
    turb = 1 + 0.3 * np.convolve(rng.standard_normal(int(d * SR)), np.ones(800) / 800, "same") * 25
    e = env(d, [(0, 0), (0.15, 1), (3, 0.8), (d, 0)])
    return reverb(sat(mix(ign, (roar * turb + rumble) * e), 1.5), 2.5, 0.3)


def laser_shot():
    d = 1.6
    x = tt(0.6)
    f = 2600 * np.exp(-x * 7) + 180
    z = osc(f, "saw") * 0.3 + osc(f * 1.5) * 0.3
    z = shape(z, lowpass(6000)) * np.exp(-x * 5)
    return reverb(fit(z, d), 1.4, 0.35, 0.9)


def cathedral_bell():
    d = 8.0
    b = bell(d, 110, [0.5, 1, 1.183, 1.506, 2, 2.514, 2.662, 3.011, 4.166], [6, 4.5, 3.5, 3, 2.5, 1.8, 1.5, 1.2, 0.8],
             [0.6, 1, 0.7, 0.5, 0.6, 0.35, 0.3, 0.25, 0.15], beat=0.5)
    hit = shape(noise(0.03), band(2000, 1)) * 0.5
    return reverb(mix(b * 0.6, hit), 4.5, 0.4, 0.6)


def bell_strike():
    d = 4.0
    b = bell(d, 330, [0.5, 1, 1.183, 1.506, 2, 2.514], [3, 2.2, 1.8, 1.5, 1.2, 0.8], [0.5, 1, 0.6, 0.4, 0.5, 0.3])
    return reverb(b * 0.6, 3.0, 0.35, 0.8)


def collapse():
    d = 6.0
    x = tt(d)
    rum = shape(noise(d), lowpass(lambda t: 800 * (1 - t) + 60, 2.4)) * 2.5
    drop = osc(150 * np.exp(-x * 0.6) + 25, "saw") * 0.4
    drop = shape(drop, lowpass(500))
    deb = crackle(d, 90, 0.004, 700) * 0.5
    sub = thump(d, 60, 22, 2.0) * 1.3
    return reverb(sat(mix(rum, drop, deb, sub) * env(d, [(0, 0), (0.1, 1), (4, 0.6), (d, 0)]), 1.8), 3.5, 0.35)


def alarm():
    d = 3.2
    x = tt(d)
    f = np.where((x * 2.5) % 1 < 0.5, 660.0, 880.0)
    a = osc(f, "square") * 0.25 + osc(f * 2) * 0.1
    a = shape(a, lowpass(3000))
    return reverb(a * env(d, [(0, 0), (0.02, 1), (3.1, 1), (d, 0)]), 1.5, 0.3)


def lock_on():
    d = 1.8
    out = np.zeros(int(d * SR))
    times = [0, 0.22, 0.4, 0.54, 0.65, 0.73, 0.8]
    for i, s0 in enumerate(times):
        b = osc(np.full(int(0.05 * SR), 1500.0 + i * 120)) * env(0.05, [(0, 0), (0.003, 1), (0.05, 0)]) * 0.4
        s = int(s0 * SR)
        out[s:s + len(b)] += b
    tone = osc(np.full(int(0.7 * SR), 2400.0)) * env(0.7, [(0, 0), (0.01, 1), (0.6, 1), (0.7, 0)]) * 0.35
    s = int(0.9 * SR)
    out[s:s + len(tone)] += tone[:len(out) - s]
    return reverb(out, 1.0, 0.25, 1.0)


def wormhole_open():
    d = 7.0
    x = tt(d)
    sw = shape(noise(d), lambda f, t: sum(band(lambda tt_, k=k: 200 * 2 ** k * (1.3 + 0.3 * np.sin(2 * np.pi * (tt_ * 5 + k * 0.25))), 0.25)(f, t)
                                             for k in range(5))) * 1.5
    chord = sum(osc(fr * (1 + 0.02 * x / d)) for fr in [146.8, 220, 293.7, 440]) * 0.12
    return reverb((sw + chord) * env(d, [(0, 0), (3, 1), (5.5, 0.9), (d, 0)]), 4.0, 0.45, 0.7)


def sky_burst():
    d = 3.5
    crack = shape(noise(0.08), highpass(700)) * np.exp(-tt(0.08) * 50) * 2
    boom = thump(d, 90, 40, 0.5)
    out = mix(crack, boom)
    for i in range(30):
        st = 0.15 + rng.random() * 1.6
        f = 2000 + rng.random() * 4000
        p = bell(0.6, f, [1, 2.3], [0.15, 0.08], [1, 0.3]) * 0.12
        s = int(st * SR)
        out = mix(out, np.concatenate([np.zeros(s), p]))
    return reverb(fit(out, d), 2.5, 0.35, 0.9)


def rune_glimmer():
    d = 2.6
    out = np.zeros(int(d * SR))
    for i in range(14):
        st = rng.random() * 1.4
        f = 1200 * 2 ** (rng.integers(0, 12) / 12 * 2)
        p = bell(1.0, f, [1, 2, 3], [0.4, 0.2, 0.1], [1, 0.25, 0.1]) * 0.15
        s = int(st * SR)
        out[s:s + len(p)] += p[:len(out) - s]
    return reverb(out, 2.5, 0.5, 1.0)


def thruster_burn():
    d = 4.5
    roar = shape(noise(d), band(300, 1.6)) * 1.5
    low = shape(noise(d), lowpass(70, 3)) * 2
    turb = 1 + 0.35 * np.convolve(rng.standard_normal(int(d * SR)), np.ones(1200) / 1200, "same") * 30
    return sat((roar * turb + low) * env(d, [(0, 0), (0.3, 1), (4, 1), (d, 0)]), 1.4)


def thruster_idle():
    d = 4.0
    roar = shape(noise(d), band(180, 1.2)) * 0.8
    hum = osc(np.full(int(d * SR), 62.0)) * 0.3
    return (roar + hum) * env(d, [(0, 0), (0.5, 1), (3.5, 1), (d, 0)])


SOUNDS = {
    "rush_wind": rush_wind, "charge_whine": charge_whine, "sonic_blast": sonic_blast, "cosmic_swell": cosmic_swell,
    "power_down": power_down, "power_up": power_up, "explosion": explosion, "crystal_resonance": crystal_resonance,
    "ui_confirm": ui_confirm, "crystal_chime": crystal_chime, "deep_thunder": deep_thunder, "plasma_whoosh": plasma_whoosh,
    "planet_groan": planet_groan, "machine_hum": machine_hum, "heartbeat": heartbeat, "energy_crack": energy_crack,
    "plasma_crackle": plasma_crackle, "station_online": station_online, "void_drone": void_drone,
    "titan_roar": titan_roar, "capacitor_charge": capacitor_charge, "mech_extend": mech_extend,
    "mech_retract": mech_retract, "beam_impact": beam_impact, "countdown_tick": countdown_tick,
    "singularity_birth": singularity_birth, "warp_jump": warp_jump, "missile_launch": missile_launch,
    "laser_shot": laser_shot, "cathedral_bell": cathedral_bell, "bell_strike": bell_strike, "collapse": collapse,
    "alarm": alarm, "lock_on": lock_on, "wormhole_open": wormhole_open, "sky_burst": sky_burst,
    "rune_glimmer": rune_glimmer, "thruster_burn": thruster_burn, "thruster_idle": thruster_idle,
}


def write(name, x, out):
    x = np.asarray(x, dtype=np.float64)
    fade = min(len(x) // 4, int(0.03 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    x[:64] *= np.linspace(0, 1, 64)
    peak = np.max(np.abs(x)) + 1e-9
    x = x / peak * 0.89
    wav = os.path.join(out, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(out, name + ".ogg")], check=True)
    os.remove(wav)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    only = sys.argv[2:]
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        write(name, fn(), out)
        print("ok", name)
