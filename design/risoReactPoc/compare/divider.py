"""How a printed rule's edges look: the ButtonGroup's divider, Compose vs React.

    python compare/divider.py <compose.png> <react.png>

Needs a ButtonGroup on both pages (see measure.py). Per row, the half-level crossings on either
side of the divider, sub-pixel: their spread is how much the edge wobbles, and the 10–90% width is
how soft it is.
"""
import sys
import numpy as np
from PIL import Image


def divider(path):
    a = np.asarray(Image.open(path).convert('L')).astype(float)
    paper = np.median(a[800:900, 1500:2000])
    # The group: the first run of dark rows below the button (y ≈ 345 at 2x).
    dark_rows = np.where((a[330:480, 40:700] < 120).any(1))[0] + 330
    y0 = dark_rows[0]
    rows = range(y0 + 16, y0 + 70)
    x = a[y0 + 16:y0 + 70, 150:230].mean(0).argmin() + 150
    left, right, widths, cores = [], [], [], []
    for y in rows:
        line = a[y, x - 8:x + 9]
        core = line.min()
        half = (paper + core) / 2
        dark = np.where(line < half)[0]
        i, j = dark.min(), dark.max()
        left.append(i - 1 + (line[i - 1] - half) / (line[i - 1] - line[i]))
        right.append(j + (line[j] - half) / (line[j] - line[j + 1]))
        cores.append(core)
        lo, hi = core + 0.1 * (paper - core), core + 0.9 * (paper - core)
        seg = line[j:]
        widths.append(np.argmax(seg > hi) - np.argmax(seg > lo) + 1)
    left, right = np.array(left), np.array(right)
    profile = a[y0 + 35:y0 + 41, x - 8:x + 8].mean(0).round().astype(int).tolist()
    return x, right - left, left.std(), right.std(), np.mean(cores), np.mean(widths), profile


for path in sys.argv[1:3]:
    x, width, sl, sr, core, soft, profile = divider(path)
    print(f'{path}\n   x={x} width {width.mean():.2f}px  edge wobble std L {sl:.3f} R {sr:.3f}px  '
          f'core {core:.1f}  edge 10-90% {soft:.2f}px\n   profile {profile}')
