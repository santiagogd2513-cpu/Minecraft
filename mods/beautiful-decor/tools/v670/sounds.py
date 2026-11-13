#!/usr/bin/env python3
"""V6.7.0: sirena de policía (bucle, silbido que sube y baja) y descarga de la pistola eléctrica; sintetizados desde cero."""
import numpy as np
from ..v620 import sounds as S
from ..v620.sounds import SR, write_ogg, noise


def siren():
    L = 3.2
    n = int(SR * L)
    t = np.arange(n) / SR
    # barrido suave 650 <-> 1450 Hz (periodico: la fase se integra de una frecuencia periodica)
    f = 1050 + 400 * np.sin(2 * np.pi * t / L) * 1.0
    f = f + 60 * np.sin(2 * np.pi * 2 * t / L)
    ph = 2 * np.pi * np.cumsum(f) / SR
    ph -= ph[0]
    # corrige el salto final para que el bucle cierre sin clic
    ph = ph - (ph[-1] - ph[0]) * np.arange(n) / n + 2 * np.pi * np.round((ph[-1] - ph[0]) / (2 * np.pi)) * np.arange(n) / n
    x = np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.22 * np.sin(3 * ph)
    x = np.tanh(1.6 * x)
    return x


def zap():
    L = 0.9
    n = int(SR * L)
    t = np.arange(n) / SR
    x = noise(n) * np.exp(-t * 3.0)
    crack = np.sign(np.sin(2 * np.pi * 60 * t)) * (np.random.default_rng(5).random(n) > 0.55)
    x = 0.7 * x + 0.9 * crack * np.exp(-t * 2.2)
    buzz = np.sin(2 * np.pi * 180 * t + 3 * np.sin(2 * np.pi * 37 * t)) * np.exp(-t * 2.0)
    x = x + 0.6 * buzz
    x[: int(0.01 * SR)] *= np.linspace(0, 1, int(0.01 * SR))
    x[-int(0.15 * SR):] *= np.linspace(1, 0, int(0.15 * SR))
    return np.tanh(1.8 * x)


if __name__ == "__main__":
    write_ogg("vehicle_siren", siren(), q=5)
    write_ogg("taser_zap", zap(), q=5)
    print("escrito: vehicle_siren, taser_zap")
