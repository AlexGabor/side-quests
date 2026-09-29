"""Compose vs React, measured on the two lossless 2x snapshots of the same page.

    python compare/measure.py ../risoRecorder/build/snapshots/desktop.png build/snapshots/react-desktop.png

Both are SnapshotPage (design/risoRecorder/.../Snapshots.kt) and its mirror, src/snapshot.tsx. The
windows below assume that layout. The ButtonGroup rows only print when the Compose page has one
(SnapshotPage doesn't by default: add `ButtonGroup(selected = OnOff.On, OnOff.On, OnOff.Off,
onSelect = {})` under the Button to compare it).
"""
import sys
import numpy as np
from PIL import Image

PINK = np.array([255, 72, 176]) / 255
PURPLE = np.array([118, 91, 167]) / 255
BLUE = np.array([0, 120, 191]) / 255


def load(path):
    return np.asarray(Image.open(path).convert('RGB')).astype(float)


def ext(mask):
    ys, xs = np.where(mask)
    return np.array([xs.min(), ys.min(), xs.max(), ys.max()])


def runs(idx):
    out, start = [], idx[0]
    for a, b in zip(idx, idx[1:]):
        if b != a + 1:
            out.append((start, a))
            start = b
    out.append((start, idx[-1]))
    return out


def dilate(mask, r):
    out = mask.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            out |= np.roll(np.roll(mask, dy, 0), dx, 1)
    return out


def ink_mass(a, box):
    """How much ink a block of text lays down: weight and ink density together."""
    x0, y0, x1, y1 = box
    sub = a[y0:y1, x0:x1]
    paper = np.median(sub.reshape(-1, 3), 0)
    return (paper.sum() - sub.sum(2)).clip(0).sum() / 1e5


def coverage(mean, paper, ink):
    """Murray–Davies: mean/paper = 1 − c(1 − ink), read on the ink's most absorbing channel."""
    ch = np.argmin(ink)
    return (1 - mean[ch] / paper[ch]) / (1 - max(ink[ch], 0.02))


def button(a, paper):
    sat = (a.max(2) - a.min(2)) > 40
    rows = np.where(sat[200:800, :400].any(1))[0] + 200
    y0, y1 = runs(rows)[0]
    x0, _, x1, _ = ext(np.pad(sat[y0:y1 + 1, :400], ((y0, 0), (0, 0))))
    mid = (y0 + y1) // 2
    purple = a[mid - 10:mid + 10, x0 + 3:x0 + 15].reshape(-1, 3).mean(0)
    pink = a[y0 + 12:y0 + 40, x1 - 14:x1 - 3].reshape(-1, 3).mean(0)
    band = np.zeros(a.shape[:2], bool)
    band[y0 + 25:y1 - 25, x0 + 40:x1 - 40] = True
    glyph = band & (a.min(2) > 215)
    rings = [a[(dilate(glyph, k) & ~dilate(glyph, k - 1)) & band].mean() for k in range(1, 6)]
    fill = a[band & ~dilate(glyph, 6)].mean()
    print(f'   button {x1 - x0}x{y1 - y0}px  purple-only c={coverage(purple, paper, PURPLE):.3f}  '
          f'pink-only c={coverage(pink, paper, PINK):.3f}')
    print(f'   knockout: lum 1..5px outside the glyphs {[round(float(v), 1) for v in rings]}, fill {fill:.1f}')
    return y1


def group(a, below):
    dark = a.sum(2) < 330
    rows = np.where(dark[below + 8:900, :700].any(1))[0] + below + 8
    g0, g1 = runs(rows)[0]
    region = a[g0 - 12:g1 + 12, 38:712]
    blue = (region[..., 2] - region[..., 1]) > 30
    if g1 - g0 < 60 or not blue.any():
        print('   (no ButtonGroup on this page)')
        return
    black = ext(np.pad(dark[g0:g1 + 1, :700], ((g0, 0), (0, 0))))
    purple = ext(blue) + np.array([38, g0 - 12, 38, g0 - 12])
    print(f'   group {black[2] - black[0]}x{black[3] - black[1]}px  purple vs black top-left '
          f'{(purple[:2] - black[:2]).tolist()}')


def swatches(a, paper):
    sat = (a.max(2) - a.min(2))[500:760, :1400] > 60
    names = iter(('pink', 'blue', 'yellow', 'pink+blue .6'))
    for x0, x1 in runs(np.where(sat.any(0))[0]):
        if x1 - x0 < 60:
            continue
        rows = np.where(sat[:, x0:x1].any(1))[0] + 500
        inner = a[rows.min() + 24:rows.max() - 24, x0 + 24:x1 - 24]
        name = next(names)
        extra = f'  blue drum c={coverage(inner.reshape(-1, 3).mean(0), paper, BLUE):.3f}' if '+' in name else ''
        print(f'   {name:13} mean {inner.reshape(-1, 3).mean(0).round(1)}  lum std {inner.sum(2).std():.1f}{extra}')


def text_core(a, box):
    x0, y0, x1, y1 = box
    sub = a[y0:y1, x0:x1].reshape(-1, 3)
    ink = sub[sub.sum(1) < 450]
    return ink[ink.sum(1) <= np.percentile(ink.sum(1), 25)].mean(0).round(1)


for label, path in zip(('compose', 'react'), sys.argv[1:3]):
    a = load(path)
    paper = np.median(a[1500:1700, 1500:2500].reshape(-1, 3), 0)
    print(f'== {label}: {path}  paper {paper}')
    print(f'   ink mass heading {ink_mass(a, (40, 40, 700, 140)):.1f}  body {ink_mass(a, (40, 150, 1400, 220)):.1f}'
          f'   darkest-quartile core heading {text_core(a, (40, 40, 700, 140))} body {text_core(a, (40, 150, 1400, 220))}')
    bottom = button(a, paper)
    group(a, bottom)
    swatches(a, paper)
