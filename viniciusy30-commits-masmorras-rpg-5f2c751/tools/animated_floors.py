#!/usr/bin/env python3
"""Pisos animados: 32 quadros 64x64 em laço perfeito (tira 2048x64). Uso: animated_floors.py <pasta_saida>"""
import sys, os
import numpy as np
from PIL import Image
dst = sys.argv[1]
S, N = 64, 32
TAU = 2 * np.pi
yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
U, V = xx / S, yy / S

def sw(a, b, ph, s, u, v, t):          # onda periódica no espaço e no tempo (a,b,s inteiros => laço perfeito)
    return np.sin(TAU * (a * u + b * v + s * t / N) + ph)

def warp(t, amp=.035, f=2):
    u = U + amp * sw(0, f, 0, 1, U, V, t) + amp * .6 * sw(f + 1, 1, 1.3, -1, U, V, t)
    v = V + amp * sw(f, 0, 2.1, 1, U, V, t) + amp * .6 * sw(1, f + 1, .4, -1, U, V, t)
    return u, v

def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.array(a, np.float32) * (1 - t) + np.array(b, np.float32) * t

def blobs(t, pts, rad, rise=1):
    out = np.zeros((S, S), np.float32)
    for (bx, by, ph, sp) in pts:
        y = (by - rise * t / N * sp) % 1.0
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                d2 = (U - bx - dx) ** 2 + (V - y - dy) ** 2
                out += np.exp(-d2 / (rad * rad)) * (.6 + .4 * np.sin(TAU * (t / N * 2 + ph)))
    return out

def water(t):
    u, v = warp(t, .05, 1)
    c1 = sw(1, 1, 0, 1, u, v, t) + sw(1, -1, 1.7, -1, u, v, t) + sw(2, 0, .6, 1, u, v, t) + sw(0, 2, 2.2, -1, u, v, t)
    c2 = sw(2, 1, .9, -1, u, v, t) + sw(-1, 2, 2.5, 1, u, v, t) + sw(2, -2, 1.1, 1, u, v, t)
    line1 = np.exp(-(c1 * .55) ** 2 * 2.2)                      # cáusticas largas e suaves
    line2 = np.exp(-(c2 * .75) ** 2 * 2.0)
    depth = (sw(1, 0, 0, 1, U, V, t) * .4 + sw(0, 1, 1, -1, U, V, t) * .4 + sw(1, 1, 2, 1, U, V, t) * .2 + 1) / 2
    img = mix((.03, .20, .50), (.10, .52, .80), depth)
    img += (line1 * .30 + line2 * .18)[..., None] * np.array([.30, .45, .40])
    glint = np.clip((sw(3, 2, 0, 1, u, v, t) * sw(2, -3, 1, -1, u, v, t) - .90) * 12, 0, 1)
    img += glint[..., None] * np.array([.55, .6, .5])
    img *= (.95 + .05 * np.sin(TAU * (V - t / N)))[..., None]
    return img

def lava(t):
    u, v = warp(t, .045, 2)
    n1 = (sw(1, 2, 0, 1, u, v, t) + sw(2, -1, 1.2, 1, u, v, t) + sw(-1, 3, 2.3, -1, u, v, t)) / 3
    plates = np.clip((n1 - .02) * 6, 0, 1)                      # placas de crosta que derivam
    crack = np.exp(-(n1 * 5.5) ** 2)
    base = mix((.55, .08, .02), (1.0, .50, .06), (sw(3, 1, 0, 2, u, v, t) + 1) / 2)
    hot = np.clip(crack * 1.2, 0, 1)
    img = base * (1 - hot[..., None]) + hot[..., None] * np.array([1.0, .92, .40])
    crust = mix((.13, .045, .04), (.30, .10, .06), (sw(4, 2, 0, 0, U, V, t) + 1) / 2)
    img = img * (1 - plates[..., None]) + crust * plates[..., None]
    img += blobs(t, [(.2, .3, 0, 1), (.7, .6, .3, 1), (.45, .85, .6, 2), (.9, .1, .8, 1)], .06)[..., None] * np.array([.8, .55, .1])
    return img

def slime(t):
    u, v = warp(t, .03, 3)
    n = (sw(2, 1, 0, 1, u, v, t) + sw(1, -3, 1, -1, u, v, t) + sw(-3, 2, 2, 1, u, v, t)) / 3
    img = mix((.10, .42, .06), (.52, .90, .22), (n + 1) / 2)
    img += np.exp(-(n * 4) ** 2)[..., None] * np.array([.18, .24, .05])
    b = blobs(t, [(.15, .5, 0, 1), (.5, .2, .25, 1), (.8, .7, .5, 1), (.35, .9, .75, 2), (.65, .45, .1, 2)], .05)
    img += np.clip(b, 0, 1)[..., None] * np.array([.55, .55, .40])
    return img

def magma(t):
    u, v = warp(t, .02, 1)
    veins = (sw(1, 2, 0, 0, U, V, 0) + sw(2, -1, 1, 0, U, V, 0) + sw(-2, 3, 2, 0, U, V, 0) + sw(3, 1, .7, 0, U, V, 0)) / 4
    glow = np.exp(-(veins * 4.5) ** 2)
    pulse = .62 + .38 * np.sin(TAU * (t / N) + veins * 4)
    rock = mix((.07, .06, .08), (.21, .17, .16), (sw(3, 2, 0, 0, U, V, 0) + sw(5, -3, 1, 0, U, V, 0) + 2) / 4)
    hotc = mix((.85, .20, .03), (1.0, .78, .22), glow * pulse)
    img = rock * (1 - glow[..., None]) + hotc * (glow * pulse)[..., None] + (glow * .10)[..., None]
    shimmer = np.clip((sw(3, 3, 0, 2, u, v, t) - .6) * 3, 0, 1) * glow
    return img + shimmer[..., None] * .22

def rune(t):
    rock = mix((.12, .12, .18), (.25, .25, .33), (sw(3, 2, 0, 0, U, V, 0) + 1) / 2)
    cx = cy = S / 2
    dx, dy = xx - cx + .5, yy - cy + .5
    d = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    rot = TAU * t / N / 5                                          # estrela de 5 pontas gira 1/5 de volta por laço
    ring1 = np.exp(-((d - 24) / 1.2) ** 2)
    ring2 = np.exp(-((d - 15) / 1.0) ** 2)
    dash = (np.sin((ang + TAU * t / N / 10) * 10) > -.2).astype(np.float32)   # tracejado que gira
    star = np.exp(-((np.abs(np.sin((ang - rot) * 2.5)) * d) / 1.5) ** 2) * (d < 23) * (d > 4)
    glyph = np.clip(ring1 * dash + ring2 + star * .85, 0, 1)
    pulse = .65 + .35 * np.sin(TAU * t / N)
    halo = np.exp(-((d - 24) / 7) ** 2) * .20 * pulse
    col = np.array([.45, .78, 1.0], np.float32)
    return rock + (glyph * pulse)[..., None] * col + halo[..., None] * col

def dress(img):
    img = img * (1.05 - .10 * (xx + yy) / (2 * S))[..., None]
    img[:1] = img[:1] * .9 + .1; img[:, :1] = img[:, :1] * .92 + .08
    img[-1:] *= .8; img[:, -1:] *= .86
    return np.clip(img, 0, 1)

os.makedirs(dst, exist_ok=True)
for name, fn in [('water', water), ('lava', lava), ('slime', slime), ('magma', magma), ('rune', rune)]:
    strip = np.concatenate([dress(fn(t)) for t in range(N)], 1)
    Image.fromarray((strip * 255 + .5).astype(np.uint8), 'RGB').convert('RGBA').save(os.path.join(dst, f'floor_{name}.png'))
print('animados ok', N, 'quadros')
