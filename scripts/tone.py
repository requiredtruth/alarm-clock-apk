"""Generate the bundled alarm tone with no third-party dependencies."""
import math
import pathlib
import struct
import sys
import wave

output = pathlib.Path(sys.argv[1])
output.parent.mkdir(parents=True, exist_ok=True)
rate = 24_000
notes = ((0.00, 659.25), (0.32, 783.99), (0.64, 987.77), (1.16, 783.99))

with wave.open(str(output), "wb") as tone:
    tone.setparams((1, 2, rate, 0, "NONE", "not compressed"))
    frames = bytearray()
    for index in range(2 * rate):
        time = index / rate
        amplitude = 0.0
        for start, frequency in notes:
            phase = time - start
            if 0 <= phase < 0.27:
                envelope = min(1.0, phase / 0.025, (0.27 - phase) / 0.09)
                angle = 2 * math.pi * frequency * phase
                amplitude += envelope * (math.sin(angle) + 0.18 * math.sin(2 * angle))
        frames.extend(struct.pack("<h", round(amplitude * 21_000)))
    tone.writeframes(frames)
