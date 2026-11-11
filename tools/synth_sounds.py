"""Procedural sound effects for the Hex Staff (numpy only). Writes mono 44.1 kHz ogg files."""
import numpy as np, subprocess, os, sys, wave

SR = 44100
OUT = sys.argv[1]
TMP = sys.argv[2]
rng = np.random.default_rng(7)

def t_(dur): return np.arange(int(dur * SR)) / SR

def env(n, a, d, curve=4.0):
    """attack seconds, then exponential decay over the remainder"""
    x = np.arange(n) / SR
    e = np.where(x < a, x / max(a, 1e-6), np.exp(-(x - a) * curve / max(d, 1e-6)))
    return e

def band(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    m = ((f >= lo) & (f <= hi)).astype(float)
    # soft edges
    m = np.convolve(m, np.hanning(31) / np.hanning(31).sum(), 'same')
    return np.fft.irfft(X * m, len(x))

def noise(n): return rng.standard_normal(n)

def chime(dur, f0, ratios=(1, 2.76, 5.40, 8.93), decay=0.5, amp=1.0, bend=0.0):
    t = t_(dur); s = np.zeros_like(t)
    for i, r in enumerate(ratios):
        f = f0 * r * (1 + rng.uniform(-0.004, 0.004))
        ph = 2 * np.pi * np.cumsum(f * (1 + bend * t / dur)) / SR
        s += np.sin(ph) * np.exp(-t * (3 + i * 2.5) / decay) / (1 + i * 0.7)
    a = np.minimum(1, t / 0.002)
    return s * a * amp

def place(buf, sig, at):
    i = int(at * SR); j = min(len(buf), i + len(sig)); buf[i:j] += sig[: j - i]; return buf

def finish(x, name, peak_db=-1.5):
    x = x - np.mean(x)
    fade = int(0.01 * SR); x[-fade:] *= np.linspace(1, 0, fade)
    x = x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)
    wav = os.path.join(TMP, name + '.wav')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-9)
    print(f'{name:22s} {len(x)/SR:5.2f}s  rms {rms:6.1f} dBFS')

# ---- hex_form: cells snapping together, a cascade of small glass chimes over a soft bloom
for v in range(3):
    d = 0.9; buf = np.zeros(int(d * SR))
    for k in range(9):
        f0 = rng.choice([1320, 1480, 1660, 1760, 1980, 2220]) * (1 + 0.04 * v)
        place(buf, chime(0.45, f0, decay=0.35, amp=0.35 + 0.05 * k), 0.012 + k * 0.028 + rng.uniform(0, 0.012))
    bloom = band(noise(len(buf)), 1500, 7000) * env(len(buf), 0.05, 0.6, 5) * 0.12
    hum = np.sin(2 * np.pi * 330 * t_(d)) * env(len(buf), 0.08, 0.7, 4) * 0.25
    finish(buf + bloom + hum, f'hex_form{v+1}')

# ---- hex_hit: a cell taking a blow: glass tick plus a dull knock
for v in range(3):
    d = 0.35; n = int(d * SR)
    tick = chime(d, 2400 + v * 260, ratios=(1, 2.1, 3.9), decay=0.12, amp=0.8)
    knock = np.sin(2 * np.pi * (140 + v * 15) * t_(d) * (1 - 0.3 * t_(d))) * env(n, 0.001, 0.12, 6)
    click = band(noise(n), 2500, 9000) * env(n, 0.0005, 0.02, 8) * 0.6
    finish(tick + knock * 0.9 + click, f'hex_hit{v+1}')

# ---- hex_shatter: the whole construct bursts into falling cells
for v in range(2):
    d = 1.3; buf = np.zeros(int(d * SR))
    burst = band(noise(len(buf)), 900, 12000) * env(len(buf), 0.002, 0.25, 7) * 0.9
    buf += burst
    for k in range(34):
        at = rng.exponential(0.18) + 0.01
        if at > d - 0.4: continue
        place(buf, chime(0.35, rng.uniform(1800, 5200), ratios=(1, 2.4, 4.1), decay=rng.uniform(0.08, 0.2), amp=rng.uniform(0.15, 0.45)), at)
    low = np.sin(2 * np.pi * 90 * t_(d)) * env(len(buf), 0.003, 0.25, 6) * 0.7
    finish(buf + low, f'hex_shatter{v+1}')

# ---- drill_charge: one grain of the spinning drill; the game replays it with rising pitch
d = 0.45; n = int(d * SR); t = t_(d)
rot = 0.5 + 0.5 * np.sin(2 * np.pi * 38 * t)                      # rotor flutter
whir = band(noise(n), 500, 3500) * rot * 0.7
hum = (np.sin(2 * np.pi * 220 * t) + 0.5 * np.sin(2 * np.pi * 440 * t + 0.3) + 0.25 * np.sin(2 * np.pi * 663 * t)) * 0.5
sh = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 0.6
finish((whir + hum) * sh, 'drill_charge', -4)

# ---- drill_fire: release. Air ripped open, low boom, glassy tail
d = 1.5; n = int(d * SR); t = t_(d)
sweep = np.zeros(n)
nz = noise(n)
for i, (lo, hi) in enumerate([(300, 1200), (900, 3000), (2000, 7000), (4000, 12000)]):
    seg = band(nz, lo, hi) * np.exp(-((t - 0.05 - i * 0.05) ** 2) / (2 * 0.06 ** 2))
    sweep += seg
boom = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-t * 3) + 38) / SR) * env(n, 0.004, 0.6, 5) * 1.3
crack = band(noise(n), 1500, 10000) * env(n, 0.0005, 0.04, 8)
tail = chime(d, 1760, decay=0.9, amp=0.35, bend=-0.08)
finish(sweep * 1.4 + boom + crack * 0.8 + tail, 'drill_fire')

# ---- drill_grind: the drill biting into a target
for v in range(2):
    d = 0.5; n = int(d * SR); t = t_(d)
    am = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (52 + v * 9) * t))
    grit = band(noise(n), 700, 6000) * am
    saw = ((t * (110 + v * 20)) % 1 - 0.5) * 0.8
    sh = env(n, 0.005, 0.4, 3)
    finish((grit + saw) * sh, f'drill_grind{v+1}', -3)

# ---- reflect: a bright ping that bends upward, plus a short swish
d = 0.8; n = int(d * SR); t = t_(d)
ping = chime(d, 1500, ratios=(1, 2.0, 3.01, 4.2), decay=0.6, amp=1.0, bend=0.35)
swish = band(noise(n), 2000, 9000) * np.exp(-((t - 0.06) ** 2) / (2 * 0.03 ** 2)) * 0.9
finish(ping + swish, 'reflect')

# ---- launch: rising column of air under the platform
d = 1.0; n = int(d * SR); t = t_(d)
air = np.zeros(n)
nz = noise(n)
for i in range(6):
    lo = 200 * 1.6 ** i
    air += band(nz, lo, lo * 1.8) * np.exp(-((t - 0.08 - i * 0.07) ** 2) / (2 * 0.08 ** 2))
thump = np.sin(2 * np.pi * np.cumsum(55 + 140 * t) / SR) * env(n, 0.005, 0.35, 5)
finish(air * 1.3 + thump * 0.8 + chime(d, 990, decay=0.5, amp=0.3, bend=0.25), 'launch')

# ---- prison_close: four walls slamming shut, a resonant glass room
d = 1.6; n = int(d * SR); t = t_(d)
buf = np.zeros(n)
for k in range(4):
    place(buf, np.sin(2 * np.pi * 95 * t_(0.5)) * env(int(0.5 * SR), 0.002, 0.25, 6) * 0.8, 0.02 + k * 0.07)
    place(buf, band(noise(int(0.3 * SR)), 1500, 9000) * env(int(0.3 * SR), 0.001, 0.05, 8) * 0.5, 0.02 + k * 0.07)
drone = sum(np.sin(2 * np.pi * f * t) / (i + 1) for i, f in enumerate([110, 165, 247.5, 371])) * env(n, 0.15, 1.3, 3) * 0.5
finish(buf + drone, 'prison_close')

# ---- prison_crush: the walls meet
d = 1.4; n = int(d * SR); t = t_(d)
slam = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-t * 4) + 32) / SR) * env(n, 0.002, 0.7, 5) * 1.4
crunch = band(noise(n), 300, 5000) * env(n, 0.001, 0.25, 6)
glass = np.zeros(n)
for k in range(20):
    place(glass, chime(0.4, rng.uniform(1500, 4500), decay=0.15, amp=rng.uniform(0.1, 0.35)), rng.uniform(0.02, 0.4))
finish(slam + crunch + glass, 'prison_crush')

# ---- swarm_fire: one small drill leaving the ring
for v in range(3):
    d = 0.5; n = int(d * SR); t = t_(d)
    zip_ = band(noise(n), 1500 + v * 300, 8000) * np.exp(-((t - 0.03) ** 2) / (2 * 0.025 ** 2))
    tone = np.sin(2 * np.pi * np.cumsum(900 + 2200 * t + v * 120) / SR) * env(n, 0.003, 0.25, 6) * 0.4
    finish(zip_ + tone, f'swarm_fire{v+1}')

# ---- summon: the staff unfolding out of nowhere into the hand
d = 1.1; n = int(d * SR); t = t_(d)
rise = band(noise(n), 1200, 8000) * (t / d) ** 2 * np.exp(-np.maximum(t - 0.75, 0) * 20) * 0.6
buf = np.zeros(n)
for k, f in enumerate([880, 1108, 1318, 1760]):
    place(buf, chime(0.6, f, decay=0.5, amp=0.45), 0.55 + k * 0.05)
finish(rise + buf, 'summon')
