"""V4.1.0: herramientas para dibujar (PIL) y sonorizar (numpy) los anuncios de las marcas del mod. Todo es original del mod."""
import math
import subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import signal

SIZE = 384          # lado del video en píxeles
SS = 2              # supersampling
FPS = 20
DUR = 12.0
SR = 44100

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_COND = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"
FONT_SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
_fonts = {}


def font(path, size):
    key = (path, int(size))
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(path, max(4, int(size)))
    return _fonts[key]


def clamp(x, a=0.0, b=1.0):
    return a if x < a else b if x > b else x


def seg(t, a, b):
    """Avance 0..1 de t entre a y b."""
    return clamp((t - a) / (b - a)) if b > a else (1.0 if t >= a else 0.0)


def ease_out(p):
    return 1 - (1 - p) ** 3


def ease_in_out(p):
    return 3 * p * p - 2 * p * p * p


def back_out(p, s=1.70158):
    p -= 1
    return p * p * ((s + 1) * p + s) + 1


def lerp(a, b, p):
    return a + (b - a) * p


def mix_col(c1, c2, p):
    return tuple(int(lerp(c1[i], c2[i], p)) for i in range(3))


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Cv:
    """Lienzo cuadrado con coordenadas de 0 a 100."""

    def __init__(self):
        self.px = SIZE * SS
        self.img = Image.new("RGB", (self.px, self.px), (0, 0, 0))
        self.d = ImageDraw.Draw(self.img, "RGBA")
        self.k = self.px / 100.0

    def u(self, v):
        return v * self.k

    def pts(self, points):
        return [(x * self.k, y * self.k) for x, y in points]

    def gradient(self, top, bottom, y0=0, y1=100):
        a0, a1 = int(y0 * self.k), int(y1 * self.k)
        for y in range(a0, a1, 2):
            p = (y - a0) / max(1, a1 - a0)
            col = mix_col(top, bottom, p)
            self.d.rectangle([0, y, self.px, y + 2], fill=col + (255,))

    def rect(self, x0, y0, x1, y1, fill, r=0, outline=None, width=0):
        box = [x0 * self.k, y0 * self.k, x1 * self.k, y1 * self.k]
        if r > 0:
            self.d.rounded_rectangle(box, radius=r * self.k, fill=fill, outline=outline, width=int(width * self.k))
        else:
            self.d.rectangle(box, fill=fill, outline=outline, width=int(width * self.k))

    def poly(self, points, fill, outline=None, width=0):
        self.d.polygon(self.pts(points), fill=fill, outline=outline)
        if outline and width:
            self.d.line(self.pts(points + [points[0]]), fill=outline, width=int(width * self.k), joint="curve")

    def line(self, points, fill, width=1.0):
        self.d.line(self.pts(points), fill=fill, width=max(1, int(width * self.k)), joint="curve")

    def circ(self, cx, cy, r, fill, outline=None, width=0):
        self.d.ellipse([(cx - r) * self.k, (cy - r) * self.k, (cx + r) * self.k, (cy + r) * self.k], fill=fill, outline=outline,
                       width=int(width * self.k))

    def ellipse(self, x0, y0, x1, y1, fill, outline=None, width=0):
        self.d.ellipse([x0 * self.k, y0 * self.k, x1 * self.k, y1 * self.k], fill=fill, outline=outline, width=int(width * self.k))

    def glow(self, cx, cy, r, color, strength=0.5, steps=14):
        for i in range(steps):
            p = i / steps
            rr = r * (1 - p)
            a = int(255 * strength * (p ** 2) / steps * 3)
            self.circ(cx, cy, rr, color[:3] + (min(255, a),))

    def text(self, s, cx, cy, size, fill, face=FONT_BOLD, anchor="mm", stroke=0, stroke_fill=(0, 0, 0, 255), scale=1.0):
        f = font(face, size * self.k * scale)
        self.d.text((cx * self.k, cy * self.k), s, font=f, fill=fill, anchor=anchor, stroke_width=int(stroke * self.k),
                    stroke_fill=stroke_fill)

    def text_width(self, s, size, face=FONT_BOLD):
        return font(face, size * self.k).getlength(s) / self.k

    def rays(self, cx, cy, n, c1, c2, rot, length=150):
        for i in range(n):
            a0 = rot + i * 2 * math.pi / n
            a1 = a0 + math.pi / n
            col = c1 if i % 2 == 0 else c2
            self.poly([(cx, cy), (cx + math.cos(a0) * length, cy + math.sin(a0) * length), (cx + math.cos(a1) * length, cy + math.sin(a1) * length)], col)

    def done(self):
        return self.img.resize((SIZE, SIZE), Image.LANCZOS).convert("RGB")


# ---------------------------------------------------------------- piezas comunes

def wheel(cv, cx, cy, r, rot, rim=(190, 195, 205, 255), tire=(22, 22, 26, 255)):
    cv.circ(cx, cy, r, tire)
    cv.circ(cx, cy, r * 0.62, rim)
    cv.circ(cx, cy, r * 0.42, (45, 48, 56, 255))
    for i in range(5):
        a = rot + i * 2 * math.pi / 5
        cv.line([(cx, cy), (cx + math.cos(a) * r * 0.6, cy + math.sin(a) * r * 0.6)], rim, 0.7)
    cv.circ(cx, cy, r * 0.16, rim)


def road(cv, y, t, speed, col=(48, 50, 58), line=(240, 240, 240, 255), h=100):
    cv.rect(0, y, 100, h, col + (255,))
    cv.rect(0, y, 100, y + 1.2, (255, 255, 255, 120))
    off = (t * speed) % 20
    for i in range(-1, 7):
        x = i * 20 - off
        cv.rect(x, y + 9, x + 10, y + 11.5, line)


def hills(cv, y, t, speed, col, amp=6, freq=0.09, h=100):
    pts = [(0, h)]
    for x in range(0, 105, 5):
        pts.append((x, y - amp * (0.5 + 0.5 * math.sin((x + t * speed) * freq))))
    pts.append((100, h))
    cv.poly(pts, col)


def clouds(cv, t, speed, y0, col=(255, 255, 255, 235), n=4):
    for i in range(n):
        x = ((i * 37 + 50 - t * speed * (0.7 + 0.2 * i)) % 140) - 20
        y = y0 + (i * 13) % 22
        for dx, dy, r in ((0, 0, 5), (5, 1, 4), (-5, 1, 4), (2, -2, 4)):
            cv.circ(x + dx, y + dy, r, col)


def pop_text(cv, s, cx, cy, size, fill, t, t0, face=FONT_BOLD, stroke=0.8, stroke_fill=(0, 0, 0, 200), hold_until=99):
    if t < t0 or t > hold_until:
        return
    p = back_out(seg(t, t0, t0 + 0.32))
    a = 255
    if t > hold_until - 0.25:
        a = int(255 * clamp((hold_until - t) / 0.25))
    cv.text(s, cx, cy, size, fill[:3] + (a,), face=face, stroke=stroke, stroke_fill=stroke_fill[:3] + (min(stroke_fill[3], a),), scale=max(0.01, p))


def ribbon(cv, s, y, t, t0, bg, fg, size=6.5, hold_until=99, face=FONT_BOLD):
    if t < t0 or t > hold_until:
        return
    p = ease_out(seg(t, t0, t0 + 0.35))
    q = 1.0 if t < hold_until - 0.3 else clamp((hold_until - t) / 0.3)
    w = cv.text_width(s, size, face) + 8
    x1 = lerp(-10, w / 2 + 50 + 0, p)
    cx = 50 + (1 - p) * 120 * (1 if True else -1) * 0
    off = (1 - p) * 110
    cv.rect(50 - w / 2 - off, y - size * 0.85, 50 + w / 2 - off, y + size * 0.85, bg[:3] + (int(255 * q),), r=1.2)
    cv.text(s, 50 - off, y, size, fg[:3] + (int(255 * q),), face=face)


def fine_print(cv, s, t, t0, color=(255, 255, 255, 190)):
    if t < t0:
        return
    a = int(color[3] * seg(t, t0, t0 + 0.4))
    cv.text(s, 50, 96, 3.1, color[:3] + (a,), face=FONT_COND)


def vignette(cv, strength=0.45):
    steps = 10
    for i in range(steps):
        p = i / steps
        a = int(255 * strength * (p ** 2.2))
        w = 3 + p * 0
        cv.d.rectangle([i * cv.k * 0.9, i * cv.k * 0.9, cv.px - i * cv.k * 0.9, cv.px - i * cv.k * 0.9], outline=(0, 0, 0, int(a * 0.25)), width=int(cv.k))


def flash(cv, t, t0, dur=0.25, col=(255, 255, 255)):
    if t0 <= t <= t0 + dur:
        a = int(200 * (1 - (t - t0) / dur))
        cv.rect(0, 0, 100, 100, col + (a,))


def fade_ends(cv, t, dur=DUR):
    if t < 0.25:
        cv.rect(0, 0, 100, 100, (0, 0, 0, int(255 * (1 - t / 0.25))))
    if t > dur - 0.3:
        cv.rect(0, 0, 100, 100, (0, 0, 0, int(255 * clamp((t - (dur - 0.3)) / 0.3))))


def confetti(cv, t, t0, n=40, colors=((255, 80, 80), (80, 200, 255), (255, 220, 80), (120, 255, 140)), seed=1):
    if t < t0:
        return
    rng = np.random.default_rng(seed)
    for i in range(n):
        x0 = rng.uniform(5, 95)
        vx = rng.uniform(-8, 8)
        vy = rng.uniform(-30, -5)
        c = colors[i % len(colors)]
        dt = t - t0
        x = x0 + vx * dt
        y = 20 + vy * dt + 40 * dt * dt
        if 0 <= y <= 100:
            a = rng.uniform(0, 6.28) + dt * 6
            s = rng.uniform(1.0, 1.8)
            cv.poly([(x + math.cos(a) * s, y + math.sin(a) * s), (x + math.cos(a + 2) * s, y + math.sin(a + 2) * s),
                     (x + math.cos(a + 4) * s, y + math.sin(a + 4) * s)], c + (230,))


# ---------------------------------------------------------------- sonido

def tt(dur):
    return np.arange(int(dur * SR)) / SR


def adsr(n, a=0.01, d=0.1, s=0.7, r=0.1):
    e = np.ones(n)
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    na = min(na, n)
    if na:
        e[:na] = np.linspace(0, 1, na)
    nd = min(nd, n - na)
    if nd > 0:
        e[na:na + nd] = np.linspace(1, s, nd)
    e[na + nd:] = s
    nr = min(nr, n)
    if nr:
        e[n - nr:] *= np.linspace(1, 0, nr)
    return e


def osc(f, dur, kind="sine", glide=None):
    t = tt(dur)
    if glide is not None:
        fr = np.linspace(f, glide, len(t))
        ph = 2 * np.pi * np.cumsum(fr) / SR
    else:
        ph = 2 * np.pi * f * t
    if kind == "sine":
        return np.sin(ph)
    if kind == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1) - 1
    if kind == "square":
        return np.sign(np.sin(ph))
    if kind == "tri":
        return 2 * np.abs(2 * ((ph / (2 * np.pi)) % 1) - 1) - 1
    raise ValueError(kind)


def lowpass(x, fc, order=2):
    b, a = signal.butter(order, min(0.99, fc / (SR / 2)), "low")
    return signal.lfilter(b, a, x)


def highpass(x, fc, order=2):
    b, a = signal.butter(order, min(0.99, fc / (SR / 2)), "high")
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi):
    b, a = signal.butter(2, [lo / (SR / 2), min(0.99, hi / (SR / 2))], "band")
    return signal.lfilter(b, a, x)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def note(m, dur, kind="saw", a=0.01, d=0.1, s=0.6, r=0.1, cutoff=None):
    x = osc(midi(m), dur, kind)
    if cutoff:
        x = lowpass(x, cutoff)
    return x * adsr(len(x), a, d, s, r)


def pluck(m, dur, decay=0.996, bright=0.5):
    f = midi(m)
    n = int(SR / f)
    rng = np.random.default_rng(int(m * 13))
    buf = rng.uniform(-1, 1, n) * bright + rng.uniform(-1, 1, n) * (1 - bright) * 0.3
    out = np.zeros(int(dur * SR))
    for i in range(len(out)):
        out[i] = buf[i % n]
        buf[i % n] = decay * 0.5 * (buf[i % n] + buf[(i + 1) % n])
    return out


def marimba(m, dur=0.35):
    t = tt(dur)
    f = midi(m)
    x = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 30) + 0.2 * np.sin(2 * np.pi * f * 9.2 * t) * np.exp(-t * 60)
    return x * np.exp(-t * 9)


def epiano(m, dur=0.8):
    t = tt(dur)
    f = midi(m)
    mod = np.sin(2 * np.pi * f * 3.0 * t) * 1.8 * np.exp(-t * 4)
    x = np.sin(2 * np.pi * f * t + mod) * np.exp(-t * 2.6)
    return x + 0.25 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t * 5)


def chime(m, dur=1.2):
    t = tt(dur)
    f = midi(m)
    x = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * dc) for r, a, dc in ((1, 1, 3.0), (2.76, 0.5, 4.5), (5.4, 0.3, 7.0), (8.9, 0.15, 11)))
    return x


def kick(dur=0.3):
    t = tt(dur)
    f = 120 * np.exp(-t * 18) + 42
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9) * 1.2


def snare(dur=0.22):
    t = tt(dur)
    n = np.random.default_rng(3).uniform(-1, 1, len(t))
    return (highpass(n, 1500) * 0.8 + np.sin(2 * np.pi * 190 * t) * 0.5) * np.exp(-t * 18)


def hat(dur=0.07, open_=False):
    t = tt(0.25 if open_ else dur)
    n = np.random.default_rng(5).uniform(-1, 1, len(t))
    return highpass(n, 6500) * np.exp(-t * (12 if open_ else 55)) * 0.6


def clap(dur=0.18):
    t = tt(dur)
    n = np.random.default_rng(7).uniform(-1, 1, len(t))
    e = np.exp(-t * 22) + 0.6 * np.exp(-((t - 0.012) ** 2) * 9000)
    return bandpass(n, 900, 3500) * e * 1.4


def whoosh(dur=0.7, up=True):
    n = np.random.default_rng(11).uniform(-1, 1, int(dur * SR))
    out = np.zeros_like(n)
    chunks = 14
    L = len(n) // chunks
    for i in range(chunks):
        p = (i + 0.5) / chunks
        fc = 300 + (4200 if up else 4200 - 3900 * p * 0 ) * (p if up else (1 - p))
        out[i * L:(i + 1) * L] = lowpass(n[i * L:(i + 1) * L], max(200, fc), 2)
    e = np.sin(np.pi * np.linspace(0, 1, len(n))) ** 1.5
    return out * e


def swell(dur, f0, f1, kind="saw", cutoff=2500):
    x = osc(f0, dur, kind, glide=f1)
    return lowpass(x, cutoff) * np.linspace(0, 1, len(x)) ** 2


def sparkle(m, dur=0.5):
    t = tt(dur)
    return np.sin(2 * np.pi * midi(m) * t) * np.exp(-t * 7) * 0.5


def load_ogg(path, rate=SR):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(rate), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def loop_to(x, dur, pitch=1.0):
    if pitch != 1.0:
        idx = np.arange(0, len(x) - 1, pitch)
        x = np.interp(idx, np.arange(len(x)), x)
    reps = int(np.ceil(dur * SR / len(x))) + 1
    xf = np.tile(x, reps)[: int(dur * SR)]
    n = int(0.02 * SR)
    xf[:n] *= np.linspace(0, 1, n)
    xf[-n:] *= np.linspace(1, 0, n)
    return xf


class Track:
    def __init__(self, dur=DUR):
        self.buf = np.zeros(int(dur * SR) + SR)
        self.dur = dur

    def add(self, sig, t0, gain=1.0):
        i = int(t0 * SR)
        if i < 0 or i >= len(self.buf):
            return
        n = min(len(sig), len(self.buf) - i)
        self.buf[i:i + n] += sig[:n] * gain

    def master(self, gain=0.9):
        x = self.buf[: int(self.dur * SR)].copy()
        x = np.tanh(x * 1.1)
        peak = max(1e-6, np.max(np.abs(x)))
        x = x / peak * gain
        n = int(0.03 * SR)
        x[:n] *= np.linspace(0, 1, n)
        m = int(0.35 * SR)
        x[-m:] *= np.linspace(1, 0, m)
        return x


def write_wav(path, x):
    import wave
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
