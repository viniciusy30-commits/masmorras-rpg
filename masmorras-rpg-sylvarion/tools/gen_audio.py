#!/usr/bin/env python3
"""
Gera todos os sons do jogo (efeitos, jingles e músicas) por síntese e grava em
app/src/main/res/raw/*.ogg.   Uso:  python3 tools/gen_audio.py [pasta_de_saida]
Requer numpy, scipy e ffmpeg (libvorbis).
"""
import os
import subprocess
import sys
import wave

import numpy as np
from scipy import signal

SR = 32000
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
TMP = "/tmp/masmorras_wav"
os.makedirs(OUT, exist_ok=True)
os.makedirs(TMP, exist_ok=True)
rng = np.random.default_rng(20261028)


# ───────────────────────── utilidades ─────────────────────────
class A(np.ndarray):
    """Array 1-D que, ao somar com outro de tamanho diferente, completa o menor com zeros."""

    def __add__(self, o):
        o = np.asarray(o)
        if o.ndim == 1 and self.ndim == 1 and o.shape != self.shape:
            n = max(len(self), len(o))
            a = np.zeros(n)
            b = np.zeros(n)
            a[: len(self)] = np.asarray(self)
            b[: len(o)] = o
            return (a + b).view(A)
        return np.ndarray.__add__(self, o)

    __radd__ = __add__


def _a(x):
    return np.asarray(x, dtype=np.float64).view(A)


def T(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.standard_normal(int(d * SR))


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def xs(f0, f1, d):
    """Varredura exponencial de f0 a f1 durante d segundos (array de frequências)."""
    t = T(d)
    return f0 * (f1 / f0) ** (t / d)


def ad(d, a=0.003, tau=0.1):
    t = T(d)
    return np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / tau)


def swell(d, peak=0.5, p=1.5):
    """Envelope em sino assimétrico: sobe até 'peak' (0..1 da duração) e desce."""
    x = np.linspace(0, 1, int(d * SR), endpoint=False)
    return np.where(x < peak, (x / max(peak, 1e-3)) ** p, ((1 - x) / max(1 - peak, 1e-3)) ** p)


def phase(freq):
    return 2 * np.pi * np.cumsum(freq) / SR


def sine(freq, d=None):
    if np.isscalar(freq):
        freq = np.full(int(d * SR), float(freq))
    return np.sin(phase(freq))


def svf(x, fc, q=1.0, mode="bp"):
    """Filtro de variável de estado (TPT), frequência de corte pode variar no tempo."""
    n = len(x)
    if np.isscalar(fc):
        fc = np.full(n, float(fc))
    fc = np.clip(fc, 20, SR * 0.45)
    g = np.tan(np.pi * fc / SR)
    k = 1.0 / q
    out = np.zeros(n)
    ic1 = 0.0
    ic2 = 0.0
    for i in range(n):
        gi = g[i]
        a1 = 1.0 / (1.0 + gi * (gi + k))
        a2 = gi * a1
        a3 = gi * a2
        v3 = x[i] - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        if mode == "bp":
            out[i] = v1 * k
        elif mode == "lp":
            out[i] = v2
        else:
            out[i] = x[i] - k * v1 - v2
    return out


def lp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, min(f, SR * 0.45) / (SR / 2), "low", output="sos"), x)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f / (SR / 2), "high", output="sos"), x)


def bp(x, f1, f2, order=2):
    return signal.sosfilt(signal.butter(order, [f1 / (SR / 2), min(f2, SR * 0.45) / (SR / 2)], "band", output="sos"), x)


def place(dst, src, at=0.0, gain=1.0):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    j = min(len(dst), i + len(src))
    dst[i:j] += src[: j - i] * gain
    return dst


def canvas(d):
    return np.zeros(int(d * SR))


def reverb(x, tau=0.3, wet=0.25, damp=3500, seed=7):
    r = np.random.default_rng(seed)
    n = int(min(tau * 4, 2.5) * SR)
    t = np.arange(n) / SR
    ir = r.standard_normal(n) * np.exp(-t / tau)
    ir = lp(ir, damp, 1)
    ir[0] = 0
    wetsig = signal.fftconvolve(x, ir)[: len(x)]
    wetsig *= np.max(np.abs(x)) / (np.max(np.abs(wetsig)) + 1e-9)
    return x * (1 - wet * 0.5) + wetsig * wet


def thump(d, f0, f1, tau, amp=1.0):
    t = T(d)
    f = f1 + (f0 - f1) * np.exp(-t / (tau * 0.5))
    return _a(np.sin(phase(f)) * np.exp(-t / tau) * amp)


def burst(d, tau, lo=None, hi=None, amp=1.0):
    x = noise(d) * ad(d, 0.0006, tau)
    if lo and hi:
        x = bp(x, lo, hi)
    elif hi:
        x = lp(x, hi)
    elif lo:
        x = hp(x, lo)
    return _a(x * amp)


def ring(d, f, tau, partials=(1.0, 2.76, 5.4, 8.93), amps=(1.0, 0.5, 0.28, 0.14), amp=1.0):
    t = T(d)
    y = np.zeros_like(t)
    for p, a in zip(partials, amps):
        y += a * np.sin(2 * np.pi * f * p * t) * np.exp(-t / (tau / (0.6 + 0.4 * p)))
    return _a(y * amp)


def whoosh(d, f0, f1, q=1.2, shape=0.45, amp=1.0):
    n = noise(d)
    y = svf(n, xs(f0, f1, d), q, "bp")
    return _a(y * swell(d, shape) * amp)


def tick(f, tau=0.012, d=0.08, amp=1.0):
    t = T(d)
    return _a(np.sin(2 * np.pi * f * t) * np.exp(-t / tau) * amp)


def make_loop(x, fade):
    """Faz o início e o fim emendarem sem estalo (crossfade de potência igual)."""
    n = int(fade * SR)
    body = x[: len(x) - n].copy()
    tail = x[len(x) - n:]
    w = np.linspace(0, np.pi / 2, n)
    body[:n] = body[:n] * np.sin(w) + tail * np.cos(w)
    return body


def save(name, x, peak=0.9, stereo=False, q=3, fade_out=0.006):
    x = np.asarray(x, dtype=np.float64)
    if not np.all(np.isfinite(x)):
        raise ValueError("NaN em " + name)
    m = np.max(np.abs(x)) + 1e-9
    x = x / m * peak
    if fade_out > 0:
        n = min(len(x), int(fade_out * SR))
        x[-n:] *= np.linspace(1, 0, n).reshape((-1,) + (1,) * (x.ndim - 1))
    n_in = min(len(x), int(0.0005 * SR))
    x[:n_in] *= np.linspace(0, 1, n_in).reshape((-1,) + (1,) * (x.ndim - 1))
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    wavp = os.path.join(TMP, name + ".wav")
    with wave.open(wavp, "wb") as w:
        w.setnchannels(2 if stereo else 1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    oggp = os.path.join(OUT, name + ".ogg")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", wavp, "-c:a", "libvorbis", "-q:a", str(q), oggp],
        check=True,
    )
    print("%-18s %5.2fs %6.1f KB" % (name, x.shape[0] / SR, os.path.getsize(oggp) / 1024))


# ───────────────────────── efeitos ─────────────────────────
SFX = {}


def sfx(fn):
    SFX[fn.__name__] = fn
    return fn


# --- interface
@sfx
def ui_click():
    d = 0.09
    return tick(1500, 0.010, d) * 0.7 + tick(780, 0.018, d) * 0.6 + burst(d, 0.004, lo=2500) * 0.25


@sfx
def ui_confirm():
    d = 0.7
    x = canvas(d)
    place(x, ring(d, 880, 0.35, amps=(1, .4, .2, .1)), 0.0, 0.8)
    place(x, ring(d - 0.08, 1320, 0.35, amps=(1, .4, .2, .1)), 0.08, 0.7)
    place(x, whoosh(0.25, 800, 4000, 1.3, 0.6), 0.0, 0.25)
    place(x, thump(0.2, 140, 70, 0.08), 0.0, 0.4)
    return reverb(x, 0.25, 0.3)


@sfx
def ui_back():
    d = 0.12
    return tick(900, 0.012, d) * 0.7 + tick(450, 0.02, d) * 0.7 + burst(d, 0.005, lo=1800) * 0.15


@sfx
def ui_error():
    d = 0.22
    t = T(d)
    x = (signal.sawtooth(2 * np.pi * 110 * t) + signal.sawtooth(2 * np.pi * 117 * t)) * 0.5
    return lp(x, 900) * ad(d, 0.004, 0.09)


@sfx
def ui_ping():
    d = 0.5
    x = canvas(d)
    place(x, ring(d, 1568, 0.18, partials=(1, 2, 3), amps=(1, .3, .1)), 0.0, 0.7)
    place(x, ring(d - 0.06, 2093, 0.2, partials=(1, 2, 3), amps=(1, .3, .1)), 0.06, 0.6)
    return reverb(x, 0.2, 0.25)


@sfx
def place_block():
    d = 0.1
    return tick(520, 0.018, d) + tick(1040, 0.01, d) * 0.5 + thump(d, 160, 90, 0.04) * 0.6


@sfx
def erase_block():
    d = 0.16
    return whoosh(d, 2500, 700, 1.0, 0.25) * 0.6 + tick(300, 0.02, d) * 0.5


# --- passos e pulos
def _step(seed, f, lpf):
    d = 0.13
    r = np.random.default_rng(seed)
    n = r.standard_normal(int(d * SR))
    x = lp(n * ad(d, 0.0008, 0.018), lpf)
    x += thump(d, f * 1.3, f, 0.035) * 0.9
    x += hp(n * ad(d, 0.0003, 0.004), 2500) * 0.25
    return x


@sfx
def step1():
    return _step(1, 105, 900)


@sfx
def step2():
    return _step(2, 95, 750)


@sfx
def step3():
    return _step(3, 112, 1000)


@sfx
def jump():
    d = 0.22
    x = canvas(d)
    place(x, sine(xs(250, 520, d)) * ad(d, 0.01, 0.07), 0, 0.25)
    place(x, whoosh(d, 500, 1600, 1.0, 0.3), 0, 0.5)
    place(x, burst(0.06, 0.02, hi=1200), 0, 0.5)
    return x


@sfx
def jump_air():
    d = 0.4
    x = canvas(d)
    place(x, whoosh(0.32, 600, 4200, 1.2, 0.4), 0, 0.8)
    place(x, ring(0.35, 1760, 0.12, partials=(1, 2), amps=(1, .3)), 0.02, 0.18)
    place(x, ring(0.3, 2637, 0.1, partials=(1, 2), amps=(1, .3)), 0.07, 0.14)
    return x


@sfx
def land_soft():
    d = 0.16
    return lp(noise(d) * ad(d, 0.001, 0.035), 700) + thump(d, 130, 70, 0.05) * 0.9


@sfx
def land_hard():
    d = 0.3
    x = lp(noise(d) * ad(d, 0.001, 0.07), 600) + thump(d, 110, 45, 0.1) * 1.1
    place(x, burst(0.12, 0.03, lo=2000), 0, 0.15)
    return x


@sfx
def roll():
    d = 0.5
    x = canvas(d)
    place(x, whoosh(d, 300, 1300, 0.9, 0.4), 0, 0.6)
    cloth = bp(noise(d), 1500, 4500) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * T(d)) ** 2) * swell(d, 0.5)
    place(x, cloth, 0, 0.3)
    place(x, burst(0.08, 0.02, hi=900) + thump(0.08, 120, 70, 0.03), 0.0, 0.4)
    place(x, burst(0.08, 0.025, hi=1100), 0.34, 0.5)
    return x


@sfx
def hop():
    d = 0.42
    x = canvas(d)
    place(x, whoosh(0.34, 700, 2800, 1.1, 0.4), 0, 0.7)
    place(x, bp(noise(0.3), 3000, 7000) * swell(0.3, 0.4), 0.02, 0.18)
    place(x, burst(0.07, 0.02, hi=1000), 0, 0.4)
    return x


@sfx
def dash():
    d = 0.4
    x = canvas(d)
    place(x, whoosh(0.32, 250, 1800, 1.6, 0.3), 0, 1.0)
    place(x, thump(0.2, 140, 60, 0.08), 0, 0.7)
    place(x, burst(0.12, 0.04, hi=1500), 0, 0.4)
    return x


@sfx
def leap_charge():
    d = 0.8
    t = T(d)
    n = svf(noise(d), xs(150, 1300, d), 2.2, "bp") * np.linspace(0.2, 1, len(t)) ** 1.5
    saw = lp(signal.sawtooth(phase(xs(70, 190, d))), 700) * np.linspace(0.2, 1, len(t))
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * 11 * t)
    return (n * 0.8 + saw * 0.6) * trem


@sfx
def leap_launch():
    d = 0.5
    x = canvas(d)
    place(x, whoosh(d, 200, 2200, 1.0, 0.25), 0, 0.9)
    place(x, thump(0.4, 120, 40, 0.15), 0, 1.0)
    place(x, burst(0.2, 0.06, hi=1200), 0, 0.5)
    return x


@sfx
def leap_land():
    d = 1.1
    x = canvas(d)
    place(x, thump(1.0, 75, 26, 0.4), 0, 1.2)
    place(x, lp(noise(0.7) * ad(0.7, 0.002, 0.2), 500), 0, 0.9)
    place(x, burst(0.25, 0.05, lo=1500), 0, 0.35)
    r = np.random.default_rng(5)
    for i in range(9):
        place(x, tick(r.uniform(500, 1800), 0.01, 0.06), 0.12 + r.uniform(0, 0.55), r.uniform(0.1, 0.35))
    return reverb(x, 0.45, 0.35, 2500)


# --- armas corpo a corpo
def _ringy(d, f, amp):
    return ring(d, f, 0.18, partials=(1, 2.4, 4.1), amps=(1, .4, .2), amp=amp)


@sfx
def sword1():
    d = 0.3
    x = canvas(d)
    place(x, whoosh(0.24, 800, 3600, 1.1, 0.5), 0, 1.0)
    place(x, whoosh(0.24, 250, 900, 0.8, 0.5), 0, 0.4)
    place(x, _ringy(d, 2600, 0.06), 0.06, 1.0)
    return x


@sfx
def sword2():
    d = 0.3
    x = canvas(d)
    place(x, whoosh(0.24, 3400, 700, 1.1, 0.5), 0, 1.0)
    place(x, whoosh(0.24, 900, 250, 0.8, 0.5), 0, 0.4)
    place(x, _ringy(d, 2200, 0.06), 0.06, 1.0)
    return x


@sfx
def sword3():
    d = 0.4
    x = canvas(d)
    place(x, whoosh(0.32, 400, 2800, 1.3, 0.55), 0, 1.0)
    place(x, whoosh(0.32, 150, 700, 0.8, 0.55), 0, 0.6)
    place(x, thump(0.2, 130, 70, 0.06), 0.1, 0.4)
    place(x, _ringy(d, 3000, 0.08), 0.09, 1.0)
    return x


@sfx
def spear_thrust():
    d = 0.28
    x = canvas(d)
    place(x, whoosh(0.2, 1300, 4800, 1.7, 0.6), 0, 1.0)
    place(x, burst(0.08, 0.02, lo=3000), 0.1, 0.25)
    return x


@sfx
def spear_sweep():
    d = 0.42
    x = canvas(d)
    place(x, whoosh(0.36, 500, 2800, 1.2, 0.5), 0, 1.0)
    place(x, whoosh(0.36, 180, 800, 0.8, 0.5), 0, 0.4)
    return x


@sfx
def heavy_swing():
    d = 0.6
    x = canvas(d)
    place(x, whoosh(0.5, 180, 1900, 0.9, 0.55), 0, 1.0)
    place(x, thump(0.45, 90, 38, 0.18), 0.18, 0.7)
    place(x, _ringy(d, 2400, 0.07), 0.2, 0.8)
    return x


@sfx
def spear_throw():
    d = 0.5
    x = canvas(d)
    place(x, whoosh(0.4, 900, 3600, 1.3, 0.3), 0, 0.9)
    place(x, thump(0.25, 160, 60, 0.08), 0, 0.6)
    place(x, ring(0.4, 330, 0.25, partials=(1, 2, 3), amps=(1, .5, .25)), 0.0, 0.25)
    return x


@sfx
def spear_wall():
    d = 0.7
    x = canvas(d)
    place(x, ring(d, 1900, 0.25, partials=(1, 2.7, 5.2), amps=(1, .6, .3)), 0, 0.55)
    place(x, tick(500, 0.02) + thump(0.1, 200, 90, 0.04), 0, 0.9)
    place(x, ring(0.6, 520, 0.4, partials=(1, 2, 3), amps=(1, .3, .1)) * (0.6 + 0.4 * np.sin(2 * np.pi * 22 * T(0.6))), 0.02, 0.22)
    return x


@sfx
def spear_pickup():
    d = 0.8
    x = canvas(d)
    for i, m in enumerate((72, 76, 79, 84)):
        place(x, ring(0.5, hz(m), 0.25, partials=(1, 2, 3), amps=(1, .35, .12)), i * 0.07, 0.6)
    place(x, whoosh(0.35, 1500, 5500, 1.0, 0.6), 0, 0.2)
    return reverb(x, 0.3, 0.25)


@sfx
def weapon_swap():
    d = 0.45
    x = canvas(d)
    place(x, whoosh(0.3, 1500, 6000, 1.5, 0.35), 0, 0.6)
    place(x, ring(0.4, 3200, 0.2, partials=(1, 2.3, 3.7), amps=(1, .5, .3)), 0.1, 0.35)
    place(x, tick(1100, 0.01) + tick(700, 0.015), 0.22, 0.5)
    return x


@sfx
def axe1():
    d = 0.4
    x = canvas(d)
    place(x, whoosh(0.32, 300, 1700, 1.0, 0.5), 0, 1.0)
    place(x, whoosh(0.32, 110, 500, 0.8, 0.5), 0, 0.6)
    place(x, thump(0.15, 120, 60, 0.05), 0.14, 0.35)
    return x


@sfx
def axe_slam():
    d = 0.9
    x = canvas(d)
    place(x, thump(0.8, 85, 30, 0.3), 0, 1.1)
    place(x, lp(noise(0.5) * ad(0.5, 0.002, 0.14), 700), 0, 0.8)
    place(x, burst(0.2, 0.04, lo=1200), 0, 0.4)
    place(x, ring(0.7, 700, 0.35, partials=(1, 2.4, 4.0), amps=(1, .5, .25)), 0.0, 0.14)
    return reverb(x, 0.35, 0.3, 2500)


# --- impactos
@sfx
def hit_blade():
    d = 0.3
    x = canvas(d)
    place(x, burst(0.12, 0.03, lo=1200), 0, 0.8)
    place(x, thump(0.18, 200, 80, 0.06), 0, 0.8)
    place(x, ring(0.25, 2300, 0.1, partials=(1, 2.4, 3.8), amps=(1, .5, .3)), 0, 0.3)
    place(x, tick(1250, 0.006, 0.05) + tick(2350, 0.004, 0.05) * 0.6, 0.005, 0.5)
    return x


@sfx
def hit_heavy():
    d = 0.5
    x = canvas(d)
    place(x, thump(0.4, 120, 42, 0.14), 0, 1.2)
    place(x, lp(noise(0.25) * ad(0.25, 0.001, 0.06), 1500), 0, 0.9)
    place(x, tick(900, 0.01, 0.06) + tick(1700, 0.007, 0.06) * 0.7, 0.004, 0.6)
    place(x, burst(0.1, 0.02, lo=2500), 0, 0.25)
    return reverb(x, 0.2, 0.2)


@sfx
def hit_crit():
    d = 0.7
    x = canvas(d)
    place(x, burst(0.14, 0.03, lo=1200), 0, 0.8)
    place(x, thump(0.4, 180, 50, 0.12), 0, 1.0)
    place(x, ring(0.6, 2900, 0.28, partials=(1, 2.4, 3.8, 6.1), amps=(1, .6, .4, .2)), 0, 0.4)
    place(x, ring(0.5, 1568, 0.2, partials=(1, 2, 3), amps=(1, .4, .2)), 0.02, 0.25)
    place(x, whoosh(0.25, 2000, 7000, 1.2, 0.3), 0, 0.2)
    return reverb(x, 0.25, 0.25)


@sfx
def hit_arrow():
    d = 0.25
    x = canvas(d)
    place(x, thump(0.15, 260, 100, 0.04), 0, 1.0)
    place(x, burst(0.08, 0.015, lo=1000, hi=4000), 0, 0.6)
    place(x, tick(1300, 0.008, 0.05), 0, 0.5)
    place(x, ring(0.2, 600, 0.1, partials=(1, 2), amps=(1, .3)) * (0.7 + 0.3 * np.sin(2 * np.pi * 30 * T(0.2))), 0.01, 0.18)
    return x


@sfx
def hit_magic():
    d = 0.5
    x = canvas(d)
    place(x, sine(xs(1000, 280, 0.2)) * ad(0.2, 0.002, 0.05), 0, 0.7)
    place(x, burst(0.1, 0.02, lo=1500), 0, 0.5)
    for i, f in enumerate((1800, 2700, 3600)):
        place(x, ring(0.4, f, 0.12, partials=(1, 2), amps=(1, .25)), 0.01 * i, 0.22)
    return reverb(x, 0.25, 0.3)


@sfx
def arrow_wall():
    d = 0.5
    x = canvas(d)
    place(x, tick(650, 0.02, 0.09) + tick(1300, 0.012, 0.09) * 0.6 + thump(0.1, 240, 120, 0.03) * 0.8, 0, 1.0)
    place(x, ring(0.45, 480, 0.25, partials=(1, 2, 3), amps=(1, .35, .15)) * (0.6 + 0.4 * np.sin(2 * np.pi * 26 * T(0.45))), 0.01, 0.22)
    return x


@sfx
def spell_wall():
    d = 0.35
    x = canvas(d)
    place(x, burst(0.12, 0.025, lo=1200), 0, 0.7)
    place(x, sine(xs(800, 200, 0.2)) * ad(0.2, 0.002, 0.05), 0, 0.5)
    place(x, ring(0.3, 2200, 0.1, partials=(1, 2), amps=(1, .3)), 0, 0.2)
    return x


@sfx
def player_hurt():
    d = 0.4
    t = T(d)
    f = xs(165, 95, d)
    src = signal.sawtooth(phase(f)) + 0.4 * signal.sawtooth(phase(f * 1.01))
    v = svf(src, 700, 3.5, "bp") + 0.7 * svf(src, 1150, 4.0, "bp") + 0.3 * svf(src, 2500, 4.0, "bp")
    v *= ad(d, 0.012, 0.13)
    x = v * 0.9
    place(x, thump(0.2, 150, 55, 0.07), 0, 1.0)
    place(x, burst(0.1, 0.02, lo=800), 0, 0.5)
    return x


@sfx
def heartbeat():
    d = 0.7
    x = canvas(d)
    place(x, thump(0.25, 80, 42, 0.07), 0, 1.0)
    place(x, thump(0.25, 70, 38, 0.08), 0.22, 0.75)
    return lp(x, 400)


@sfx
def enemy_die():
    d = 1.0
    x = canvas(d)
    r = np.random.default_rng(11)
    ti = 0.0
    gap = 0.07
    k = 0
    while ti < 0.75:
        f = r.uniform(600, 2400)
        place(x, tick(f, r.uniform(0.008, 0.02), 0.08) + tick(f * 1.5, 0.006, 0.08) * 0.4, ti, r.uniform(0.4, 1.0) * (1 - ti / 1.1))
        ti += gap * (0.93 ** k) + r.uniform(0, 0.02)
        k += 1
    place(x, thump(0.4, 130, 50, 0.12), 0, 0.8)
    place(x, lp(noise(0.5) * ad(0.5, 0.002, 0.15), 1200), 0, 0.5)
    place(x, whoosh(0.5, 900, 200, 0.8, 0.2), 0, 0.2)
    return reverb(x, 0.25, 0.2)


@sfx
def enemy_alert():
    d = 0.6
    x = canvas(d)
    r = np.random.default_rng(13)
    for i in range(14):
        place(x, tick(r.uniform(900, 2200), 0.01, 0.06), 0.03 * i + r.uniform(0, 0.015), r.uniform(0.3, 0.9))
    g = svf(signal.sawtooth(phase(xs(110, 80, 0.5))), 400, 3, "bp") * swell(0.5, 0.3) * 0.5
    place(x, g, 0.05, 0.9)
    return x


@sfx
def enemy_swing():
    d = 0.35
    x = canvas(d)
    place(x, whoosh(0.28, 500, 1700, 1.2, 0.5), 0, 0.8)
    place(x, tick(1500, 0.008, 0.05) + tick(950, 0.01, 0.05), 0.04, 0.4)
    place(x, tick(1900, 0.006, 0.05), 0.09, 0.3)
    return x


# --- mago
@sfx
def spell_charge():
    d = 0.4
    t = T(d)
    x = (sine(xs(500, 1300, d)) * 0.4 + svf(noise(d), xs(1000, 4500, d), 2.5, "bp") * 0.6) * swell(d, 0.9, 1.2)
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 20 * t)
    return x


@sfx
def spell_cast():
    d = 0.55
    x = canvas(d)
    place(x, whoosh(0.35, 800, 4200, 1.3, 0.3), 0, 0.8)
    place(x, sine(xs(400, 1500, 0.3)) * ad(0.3, 0.004, 0.12), 0, 0.45)
    place(x, thump(0.2, 220, 90, 0.06), 0, 0.5)
    for i, f in enumerate((2200, 3300)):
        place(x, ring(0.4, f, 0.12, partials=(1, 2), amps=(1, .3)), 0.03 * i, 0.15)
    return reverb(x, 0.2, 0.2)


@sfx
def teleport_out():
    d = 0.6
    x = canvas(d)
    fm = np.sin(phase(xs(2600, 300, d)) + 3 * np.sin(phase(xs(600, 90, d))))
    place(x, fm * ad(d, 0.003, 0.2), 0, 0.6)
    place(x, whoosh(d, 6000, 500, 1.0, 0.15), 0, 0.5)
    for i, f in enumerate((3500, 2800, 2100)):
        place(x, ring(0.35, f, 0.1, partials=(1, 2), amps=(1, .3)), 0.04 * i, 0.15)
    return reverb(x, 0.25, 0.3)


@sfx
def teleport_in():
    d = 0.6
    x = canvas(d)
    fm = np.sin(phase(xs(300, 2600, 0.3)) + 3 * np.sin(phase(xs(90, 600, 0.3))))
    place(x, fm * swell(0.3, 0.9, 1.2), 0, 0.5)
    place(x, whoosh(0.3, 500, 6000, 1.0, 0.85), 0, 0.5)
    place(x, thump(0.3, 200, 70, 0.1), 0.28, 0.8)
    place(x, burst(0.1, 0.02, lo=2000), 0.28, 0.4)
    for i, f in enumerate((2100, 2800, 3500)):
        place(x, ring(0.4, f, 0.12, partials=(1, 2), amps=(1, .3)), 0.3 + 0.04 * i, 0.2)
    return reverb(x, 0.25, 0.3)


@sfx
def fly_start():
    d = 0.6
    x = canvas(d)
    place(x, whoosh(d, 300, 2500, 1.0, 0.7), 0, 0.8)
    place(x, sine(xs(300, 900, d)) * swell(d, 0.7), 0, 0.15)
    place(x, ring(0.4, 2400, 0.15, partials=(1, 2), amps=(1, .3)), 0.35, 0.15)
    return x


@sfx
def fly_loop():
    d = 1.6
    t = T(d)
    base = svf(noise(d), 700 + 250 * np.sin(2 * np.pi * 1.25 * t), 1.4, "bp")
    flap = 0.65 + 0.35 * np.sin(2 * np.pi * 5 * t) ** 2
    shimmer = bp(noise(d), 2500, 6500) * (0.3 + 0.2 * np.sin(2 * np.pi * 10 * t))
    x = (base * flap + shimmer * 0.25)
    return make_loop(x, 0.25)


@sfx
def fly_end():
    d = 0.5
    return whoosh(d, 1800, 250, 1.0, 0.3) * 0.8 + sine(xs(700, 250, d)) * swell(d, 0.3) * 0.1


# --- arqueiro
@sfx
def bow_draw():
    d = 0.7
    t = T(d)
    creak = svf(signal.sawtooth(phase(xs(80, 140, d))) + noise(d) * 0.3, xs(350, 900, d), 5, "bp")
    creak *= 0.6 + 0.4 * lp(noise(d), 25, 1) / (np.max(np.abs(lp(noise(d), 25, 1))) + 1e-9)
    creak *= swell(d, 0.9, 1.0)
    string = sine(xs(220, 330, d)) * swell(d, 0.95, 1.5) * 0.08
    x = creak * 0.7 + string
    place(x, burst(0.05, 0.01, lo=1500), 0.0, 0.1)
    return x


def _twang(d, f, tau, amp=1.0):
    t = T(d)
    y = np.zeros_like(t)
    fm = f * (1 + 0.025 * np.exp(-t / 0.04))
    ph = 2 * np.pi * np.cumsum(fm) / SR
    for h in range(1, 9):
        y += np.sin(ph * h) / h ** 1.1 * np.exp(-t / (tau / (0.55 + 0.45 * h)))
    return y * amp


@sfx
def bow_shot():
    d = 0.7
    x = canvas(d)
    place(x, _twang(d, 196, 0.35), 0, 0.7)
    place(x, tick(2200, 0.004, 0.04) + burst(0.04, 0.008, lo=2500) * 0.5, 0, 0.7)
    place(x, whoosh(0.3, 3500, 1500, 1.2, 0.15), 0.01, 0.5)
    place(x, thump(0.12, 150, 80, 0.04), 0, 0.5)
    return x


@sfx
def bow_shot_strong():
    d = 0.95
    x = canvas(d)
    place(x, _twang(d, 150, 0.55), 0, 0.8)
    place(x, tick(1800, 0.005, 0.05) + burst(0.06, 0.012, lo=2000) * 0.6, 0, 0.9)
    place(x, whoosh(0.45, 4200, 1200, 1.3, 0.1), 0.01, 0.7)
    place(x, thump(0.25, 130, 55, 0.08), 0, 0.9)
    return reverb(x, 0.2, 0.15)


@sfx
def rain_cast():
    d = 0.8
    x = canvas(d)
    place(x, whoosh(d, 400, 3500, 1.2, 0.85), 0, 0.7)
    for i, m in enumerate((79, 83, 86, 91)):
        place(x, ring(0.5, hz(m), 0.18, partials=(1, 2), amps=(1, .25)), 0.12 * i + 0.1, 0.22)
    return reverb(x, 0.3, 0.3)


@sfx
def rain_release():
    d = 1.2
    x = canvas(d)
    place(x, _twang(0.6, 170, 0.4), 0, 0.6)
    place(x, whoosh(1.0, 1200, 5500, 1.0, 0.2), 0, 0.9)
    r = np.random.default_rng(21)
    for i in range(8):
        place(x, whoosh(0.3, r.uniform(2000, 4500), r.uniform(900, 1800), 1.4, 0.2), 0.05 + 0.05 * i, 0.12)
    return reverb(x, 0.3, 0.25)


@sfx
def rain_land():
    d = 0.2
    x = canvas(d)
    place(x, tick(900, 0.01, 0.1) + tick(1850, 0.006, 0.1) * 0.5, 0, 1.0)
    place(x, burst(0.05, 0.01, lo=1500, hi=5000), 0, 0.5)
    place(x, thump(0.08, 200, 100, 0.025), 0, 0.5)
    return x


@sfx
def thunder_cast():
    d = 0.7
    t = T(d)
    x = canvas(d)
    r = np.random.default_rng(31)
    dens = np.linspace(3, 60, 40)
    tt = 0.0
    for i in range(70):
        tt += 1.0 / (6 + 90 * (tt / d) ** 1.5)
        if tt >= d - 0.03:
            break
        place(x, burst(0.03, 0.006, lo=2500) * r.uniform(0.3, 1.0), tt, 1.0)
    place(x, sine(xs(180, 1100, d)) * swell(d, 0.95, 1.4), 0, 0.3)
    place(x, svf(noise(d), xs(500, 4000, d), 2.0, "bp") * swell(d, 0.95, 1.4), 0, 0.5)
    return x


@sfx
def thunder_fire():
    d = 1.4
    x = canvas(d)
    place(x, burst(0.3, 0.05, lo=300) * 1.0, 0, 1.0)
    place(x, signal.sawtooth(phase(xs(5000, 150, 0.18))) * ad(0.18, 0.001, 0.06), 0, 0.5)
    place(x, signal.sawtooth(phase(xs(3500, 220, 0.3))) * ad(0.3, 0.001, 0.1), 0.03, 0.3)
    place(x, lp(noise(1.2) * ad(1.2, 0.01, 0.35), 350), 0.02, 1.1)
    place(x, thump(0.9, 70, 28, 0.3), 0.0, 1.0)
    r = np.random.default_rng(41)
    for i in range(6):
        place(x, burst(0.02, 0.005, lo=3000), 0.05 + r.uniform(0, 0.4), r.uniform(0.2, 0.6))
    return reverb(x, 0.55, 0.4, 2500)


@sfx
def thunder_hit():
    d = 0.55
    x = canvas(d)
    zap = signal.sawtooth(phase(xs(3000, 400, 0.25) * (1 + 0.15 * np.sin(2 * np.pi * 90 * T(0.25))))) * ad(0.25, 0.001, 0.09)
    place(x, zap, 0, 0.5)
    r = np.random.default_rng(43)
    for i in range(10):
        place(x, burst(0.02, 0.005, lo=2500), r.uniform(0, 0.25), r.uniform(0.3, 0.9))
    place(x, thump(0.25, 150, 60, 0.08), 0, 0.7)
    place(x, lp(noise(0.4) * ad(0.4, 0.004, 0.12), 900), 0, 0.5)
    return reverb(x, 0.25, 0.25)


@sfx
def trap_throw():
    d = 0.4
    x = canvas(d)
    place(x, whoosh(0.28, 600, 2500, 1.1, 0.4), 0, 0.7)
    r = np.random.default_rng(51)
    for i in range(5):
        place(x, ring(0.15, r.uniform(2500, 4200), 0.04, partials=(1, 2.6), amps=(1, .4)), 0.03 * i, 0.2)
    return x


@sfx
def trap_land():
    d = 0.6
    x = canvas(d)
    place(x, thump(0.2, 140, 60, 0.06), 0, 1.0)
    place(x, ring(0.55, 900, 0.22, partials=(1, 2.7, 4.9, 7.3), amps=(1, .7, .4, .2)), 0, 0.5)
    place(x, burst(0.08, 0.015, lo=1500), 0, 0.4)
    place(x, ring(0.3, 1700, 0.12, partials=(1, 2.2), amps=(1, .4)), 0.1, 0.25)
    return x


@sfx
def trap_snap():
    d = 0.8
    x = canvas(d)
    place(x, burst(0.06, 0.008, lo=1200), 0, 1.0)
    place(x, ring(0.7, 1100, 0.3, partials=(1, 2.76, 5.4, 8.9), amps=(1, .8, .5, .3)), 0, 0.7)
    place(x, ring(0.5, 1750, 0.2, partials=(1, 2.4, 4.7), amps=(1, .6, .3)), 0.0, 0.5)
    place(x, thump(0.2, 180, 70, 0.07), 0, 0.9)
    place(x, ring(0.4, 900, 0.1, partials=(1, 2.6), amps=(1, .5)), 0.06, 0.4)
    place(x, _twang(0.5, 330, 0.2), 0.0, 0.2)
    return reverb(x, 0.2, 0.2)


# --- vitória / derrota (jingles)
def _horn(d, f, amp=1.0, vib=0.0):
    t = T(d)
    fv = f * (1 + vib * np.sin(2 * np.pi * 5.5 * t))
    x = np.zeros_like(t)
    ph = 2 * np.pi * np.cumsum(fv) / SR
    for h in range(1, 9):
        x += np.sin(ph * h) / h ** 1.15
    x = lp(x, 3200)
    e = np.minimum(1, t / 0.04) * np.minimum(1, (d - t) / 0.18) * (0.8 + 0.2 * np.minimum(1, t / 0.3))
    return x * e * amp


@sfx
def victory():
    d = 3.6
    x = canvas(d)
    seq = [(62, 0.0, 0.24), (66, 0.2, 0.24), (69, 0.4, 0.24), (74, 0.6, 0.5), (73, 1.12, 0.2), (74, 1.3, 0.2), (78, 1.55, 0.35), (81, 1.9, 1.5)]
    for m, st, du in seq:
        place(x, _horn(du + 0.25, hz(m), 0.6, 0.004), st, 1.0)
    for m in (50, 57, 62, 66, 69):
        place(x, _horn(2.0, hz(m), 0.3, 0.003), 1.9, 1.0)
    for i, m in enumerate((86, 90, 93, 98, 93, 90)):
        place(x, ring(1.0, hz(m), 0.3, partials=(1, 2, 3), amps=(1, .3, .1)), 1.95 + 0.09 * i, 0.15)
    place(x, thump(0.6, 80, 40, 0.2), 0.0, 0.5)
    place(x, thump(0.8, 70, 35, 0.3), 1.9, 0.6)
    return reverb(x, 0.9, 0.35, 3000)


@sfx
def defeat():
    d = 3.4
    x = canvas(d)
    seq = [(62, 0.0, 0.5), (60, 0.55, 0.5), (57, 1.1, 0.5), (50, 1.65, 1.6)]
    for m, st, du in seq:
        place(x, _horn(du + 0.3, hz(m), 0.55, 0.006), st, 1.0)
    for m in (38, 45):
        place(x, _horn(2.2, hz(m), 0.5, 0.003), 1.65, 1.0)
    place(x, thump(1.0, 60, 28, 0.4), 1.65, 0.9)
    place(x, svf(noise(2.0), 300, 1.0, "bp") * swell(2.0, 0.2, 1.0), 1.65, 0.25)
    return reverb(x, 1.0, 0.45, 2200)


# ───────────────────────── música ─────────────────────────
def circ_reverb(x, tau, wet, damp, seed):
    """Reverb circular: a cauda do fim volta para o início, então o loop é perfeito."""
    n = len(x)
    r = np.random.default_rng(seed)
    m = int(min(tau * 3.5, n / SR * 0.6) * SR)
    t = np.arange(m) / SR
    ir = lp(r.standard_normal(m) * np.exp(-t / tau), damp, 1)
    ir[0] = 0
    irp = np.zeros(n)
    irp[:m] = ir
    w = np.real(np.fft.ifft(np.fft.fft(x) * np.fft.fft(irp)))
    w *= np.max(np.abs(x)) / (np.max(np.abs(w)) + 1e-9)
    return x * (1 - wet * 0.4) + w * wet


class Track:
    def __init__(self, seconds, tail=6.0):
        self.D = seconds
        self.L = np.zeros(int((seconds + tail) * SR))
        self.R = np.zeros(int((seconds + tail) * SR))

    def add(self, sig, at, gain=1.0, pan=0.0):
        """pan: -1 (esquerda) .. 1 (direita)."""
        gl = gain * np.cos((pan + 1) * np.pi / 4)
        gr = gain * np.sin((pan + 1) * np.pi / 4)
        i = int(at * SR)
        j = min(len(self.L), i + len(sig))
        if i >= len(self.L):
            return
        self.L[i:j] += sig[: j - i] * gl
        self.R[i:j] += sig[: j - i] * gr

    def wrap(self):
        n = int(self.D * SR)
        L = self.L[:n].copy()
        R = self.R[:n].copy()
        k = len(self.L) - n
        L[:k] += self.L[n:]
        R[:k] += self.R[n:]
        return L, R


def pad_voice(d, f, detune=0.0, bright=1500, attack=2.5, release=3.0, harm=8):
    t = T(d)
    fd = f * (1 + detune)
    x = np.zeros_like(t)
    ph = 2 * np.pi * fd * t
    for h in range(1, harm + 1):
        x += np.sin(ph * h + h * 0.7) / h ** 1.25
    x = lp(x, bright, 2)
    e = np.minimum(1, t / attack) ** 1.6 * np.minimum(1, (d - t) / release) ** 1.6
    return x * e


def pluck(f, d=2.5, tau=0.9, bright=1.0):
    t = T(d)
    y = np.zeros_like(t)
    for p, a in ((1, 1.0), (2, 0.5), (3, 0.28), (4.01, 0.16), (5.03, 0.1)):
        y += a * np.sin(2 * np.pi * f * p * t + p) * np.exp(-t / (tau / (0.55 + 0.45 * p)))
    y *= np.minimum(1, t / 0.003)
    return lp(y, 2600 * bright, 1)


def bell(f, d=4.0, tau=1.8):
    return ring(d, f, tau, partials=(1, 2.0, 2.76, 4.07, 5.43), amps=(1, .5, .35, .2, .12)) * np.minimum(1, T(d) / 0.004)


def drum(f0=100, f1=48, tau=0.22, d=1.0):
    return thump(d, f0, f1, tau) + lp(noise(d) * ad(d, 0.001, 0.04), 350) * 0.5


def wind(d, f0, f1, seed):
    r = np.random.default_rng(seed)
    n = r.standard_normal(int(d * SR))
    t = T(d)
    y = svf(n, f0 + (f1 - f0) * (0.5 + 0.5 * np.sin(2 * np.pi * t / d * 2)), 1.5, "bp")
    return y * (0.5 + 0.5 * np.sin(2 * np.pi * t / d * 3 + 1)) ** 2


def music_menu():
    bpm = 72
    beat = 60.0 / bpm
    bar = beat * 4
    bars = 16
    D = bar * bars  # ≈ 53,3 s
    tr = Track(D)
    chords = [  # Dm  Bb  Gm  A
        ([38 + 12, 45 + 12, 50 + 12, 53 + 12, 57 + 12], 38),
        ([34 + 12, 41 + 12, 46 + 12, 50 + 12, 53 + 12], 34),
        ([31 + 12, 38 + 12, 43 + 12, 46 + 12, 50 + 12], 31),
        ([33 + 12, 40 + 12, 45 + 12, 49 + 12, 52 + 12], 33),
    ]
    span = bar * 4
    for ci, (notes, root) in enumerate(chords):
        st = ci * span
        for ni, m in enumerate(notes):
            dur = span + 4.5
            tr.add(pad_voice(dur, hz(m), -0.0035, 1300 + 100 * ni), st - 2.0 if st > 0 else 0.0, 0.16, -0.5 + 0.25 * ni)
            tr.add(pad_voice(dur, hz(m), +0.0035, 1300 + 100 * ni), st - 2.0 if st > 0 else 0.0, 0.16, 0.5 - 0.25 * ni)
        tr.add(pad_voice(span + 3.0, hz(root), 0, 420, 1.5, 2.5, 5), st, 0.35, 0.0)
    # tema em harpa: escala de ré menor
    D_MIN = [62, 65, 67, 69, 70, 72, 74, 77, 79]  # D4 F4 G4 A4 Bb4 C5 D5 F5 G5
    r = np.random.default_rng(100)
    motifs = [
        [0, 2, 3, 5, 3, 2, 0, 1],
        [4, 3, 2, 0, 2, 3, 4, 2],
        [2, 3, 5, 6, 5, 3, 2, 0],
        [3, 2, 1, 0, 1, 2, 3, 3],
    ]
    for b in range(bars):
        mot = motifs[(b // 1) % 4] if b % 4 != 3 else motifs[3]
        for i, idx in enumerate(mot):
            if r.random() < 0.28:
                continue
            at = b * bar + i * beat * 0.5
            m = D_MIN[idx] + (12 if (b % 8 == 7 and i > 4) else 0)
            tr.add(pluck(hz(m), 3.2, 1.0), at, r.uniform(0.18, 0.32), (-0.6 + 1.2 * ((i + b) % 5) / 4))
    for b in range(bars):
        tr.add(drum(95, 46, 0.25), b * bar, 0.55, 0)
        if b % 2 == 1:
            tr.add(drum(120, 60, 0.16, 0.6), b * bar + beat * 2.5, 0.28, 0.2)
    for k in range(4):
        tr.add(bell(hz(86 if k % 2 == 0 else 81), 5.0, 2.2), k * span + bar * 2 + beat, 0.09, -0.4 + 0.3 * k)
    tr.add(wind(D + 2, 500, 1100, 3), 0, 0.05, 0)
    L, R = tr.wrap()
    L = circ_reverb(L, 1.6, 0.45, 3000, 1)
    R = circ_reverb(R, 1.6, 0.45, 3000, 2)
    return np.stack([L, R], 1)


def music_game():
    bpm = 84
    beat = 60.0 / bpm
    bar = beat * 4
    bars = 24
    D = bar * bars  # ≈ 68,6 s
    tr = Track(D)
    # drone grave em lá
    tr.add(pad_voice(D + 6, hz(33), 0, 380, 4.0, 4.0, 6) * 1.0, 0, 0.45, 0)
    tr.add(pad_voice(D + 6, hz(45), 0.002, 520, 4.0, 4.0, 6), 0, 0.22, -0.3)
    tr.add(pad_voice(D + 6, hz(45), -0.002, 520, 4.0, 4.0, 6), 0, 0.22, 0.3)
    # acordes lentos: Am, F, Dm, E (frígio/dominante)
    chords = [[57, 60, 64], [53, 57, 60], [50, 53, 57], [52, 56, 59]]
    span = bar * 6
    for ci, ch in enumerate(chords):
        st = ci * span
        for ni, m in enumerate(ch):
            at = max(0.0, st - 2.5)
            tr.add(pad_voice(span + 5.5, hz(m), -0.004, 900 + 150 * ni, 3.0, 3.5, 7), at, 0.13, -0.6 + 0.4 * ni)
            tr.add(pad_voice(span + 5.5, hz(m), +0.004, 900 + 150 * ni, 3.0, 3.5, 7), at, 0.13, 0.6 - 0.4 * ni)
    # pulsação de tambor: bum . . bum-bum, com pausas a cada 4 compassos
    for b in range(bars):
        if b % 6 == 5:
            continue
        base = b * bar
        tr.add(drum(100, 44, 0.3, 1.2), base, 0.9, 0)
        tr.add(drum(100, 44, 0.3, 1.2), base + beat * 2.5, 0.55, 0.1)
        if b % 2 == 1:
            tr.add(drum(130, 60, 0.2, 0.8), base + beat * 3.25, 0.4, -0.1)
        for h in range(8):
            tr.add(burst(0.05, 0.012, lo=5000) * 0.1, base + h * beat * 0.5, 1.0, 0.3 if h % 2 else -0.3)
    # sinos sombrios esparsos (lá frígio)
    r = np.random.default_rng(200)
    scale = [81, 82, 84, 88, 89, 93]  # A5 Bb5 C6 E6 F6 A6
    for i in range(11):
        at = 4.0 + i * (D - 8) / 11 + r.uniform(-1.0, 1.0)
        m = scale[r.integers(0, len(scale))]
        tr.add(bell(hz(m), 6.0, 2.6), at, r.uniform(0.07, 0.14), r.uniform(-0.8, 0.8))
    # baixo em notas longas que sobe a cada ciclo
    for ci, m in enumerate((33, 29, 26, 28)):
        tr.add(pad_voice(span + 1.0, hz(m + 12), 0, 330, 0.6, 1.5, 4), ci * span + 0.2, 0.26, 0)
    # vento
    tr.add(wind(D + 3, 300, 800, 9), 0, 0.10, -0.5)
    tr.add(wind(D + 3, 400, 1000, 10), 0, 0.10, 0.5)
    L, R = tr.wrap()
    L = circ_reverb(L, 1.2, 0.35, 2600, 3)
    R = circ_reverb(R, 1.2, 0.35, 2600, 4)
    return np.stack([L, R], 1)


def main():
    for name, fn in SFX.items():
        save(name, fn(), peak=0.9)
    save("music_menu", music_menu(), peak=0.85, stereo=True, q=2, fade_out=0)
    save("music_game", music_game(), peak=0.85, stereo=True, q=2, fade_out=0)
    print("OK", len(SFX) + 2, "arquivos em", OUT)


if __name__ == "__main__":
    main()
