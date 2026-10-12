# Synthesises the repeat bell: two decaying sine partials, 22050 Hz mono 16-bit, about 0.7 s.
# Own work, no samples. Writes a WAV; then: ffmpeg -i bell.wav -ac 2 -c:a vorbis -strict -2 -q:a 4 app/src/main/res/raw/repeat_bell.ogg
import math, struct, sys, wave
rate = 22050
dur = 0.7
n = int(rate * dur)
frames = bytearray()
for i in range(n):
    t = i / rate
    env = math.exp(-5.5 * t)
    s = 0.55 * math.sin(2 * math.pi * 1318.5 * t) * env
    s += 0.30 * math.sin(2 * math.pi * 2637.0 * t) * math.exp(-9.0 * t)
    s += 0.15 * math.sin(2 * math.pi * 3951.0 * t) * math.exp(-14.0 * t)
    attack = min(1.0, t / 0.004)
    v = int(max(-1.0, min(1.0, s * attack)) * 32000)
    frames += struct.pack('<h', v)
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(bytes(frames))
print('wrote', sys.argv[1], n, 'frames')
