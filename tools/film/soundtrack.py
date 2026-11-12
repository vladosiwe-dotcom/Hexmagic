import numpy as np, subprocess, sys, os, wave
SR = 44100
SND = '/home/user/Hexmagic/src/main/resources/assets/hexmagic/sounds'
OUT = sys.argv[1]; DUR = float(sys.argv[2])
LEAD = 12  # ticks of film before tick 0 in the cut

def load(name):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', f'{SND}/{name}.ogg', '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(raw, '<i2').astype(np.float32) / 32768

cache = {}
def snd(name):
    if name not in cache: cache[name] = load(name)
    return cache[name]

mix = np.zeros(int((DUR + 2) * SR), np.float32)
def at(tick, name, vol=1.0, pitch=1.0):
    s = snd(name)
    if pitch != 1.0:
        idx = np.arange(0, len(s) - 1, pitch)
        s = np.interp(idx, np.arange(len(s)), s).astype(np.float32)
    i = int((tick + LEAD) / 20 * SR)
    j = min(len(mix), i + len(s))
    if i < len(mix): mix[i:j] += s[: j - i] * vol

at(11, 'summon', 0.9)
at(61, 'hex_form1', 1.0)
at(96, 'hex_hit1', 0.8); at(126, 'hex_hit2', 0.9, 0.9)
at(215, 'reflect', 1.0)
for k, tk in enumerate(range(271, 332, 6)):
    c = min(1.0, (tk - 270) / 60)
    at(tk, 'drill_charge', 0.25 + 0.5 * c, 0.6 + 1.2 * c)
at(333, 'drill_fire', 1.0, 0.95)
at(336, 'hex_shatter1', 0.4, 1.3)
at(338, 'drill_grind1', 0.8); at(348, 'drill_grind2', 0.8)
at(406, 'hex_form2', 0.6, 1.2)
at(419, 'drill_fire', 0.7, 1.25)
at(426, 'hex_hit3', 0.9); at(427, 'hex_shatter2', 0.8)
at(474, 'launch', 1.0)
at(506, 'hex_form3', 0.8, 1.1)
at(571, 'hex_hit1', 0.8, 0.7)
for k in range(5): at(657 + 7 * k, f'swarm_fire{k % 3 + 1}', 0.8, 0.9 + 0.08 * k)
for k in range(5): at(663 + 7 * k, 'drill_grind2', 0.35, 1.6)
at(721, 'hex_form1', 0.9, 0.75)
at(730, 'prison_close', 1.0)
for k in range(6): at(870 + 10 * k, 'drill_grind1', 0.35, 0.5 + 0.08 * k)
for k, tk in enumerate(range(851, 882, 6)):
    c = (tk - 850) / 60
    at(tk, 'drill_charge', 0.25 + 0.5 * c, 0.6 + 1.2 * c)
at(883, 'drill_fire', 0.8, 1.1)
at(887, 'drill_grind1', 0.8); at(897, 'drill_grind2', 0.8)
at(930, 'prison_crush', 1.0)
at(979, 'hex_form2', 0.9)
at(1006, 'hex_hit2', 1.0, 0.95)
at(1030, 'hex_shatter1', 1.0)

# gentle wind bed so silences don't feel dead
rng = np.random.default_rng(3)
n = len(mix); X = np.fft.rfft(rng.standard_normal(n)); f = np.fft.rfftfreq(n, 1 / SR)
X *= (f > 80) & (f < 900)
wind = np.fft.irfft(X, n).astype(np.float32)
wind *= 0.012 / (np.std(wind) + 1e-9) * (0.7 + 0.3 * np.sin(np.arange(n) / SR * 0.6))
mix += wind
mix /= max(1.0, np.max(np.abs(mix)) / 0.89)
with wave.open(OUT, 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((mix * 32767).astype('<i2').tobytes())
print('soundtrack', round(len(mix) / SR, 1), 's')
