#!/usr/bin/env python3
"""Derive Pacer's web icons from the flat iOS print.

The mark itself is drawn by the :stamp app; this only reframes and downscales what it exported, so
run it after the copy steps in README.md rather than pointing it at anything else.
"""

from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "pacer/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
OUT = ROOT / "pacer/webApp/src/wasmJsMain/resources"

# 48 is what a hidpi tab asks for; browsers pick the closest of the three themselves.
ICO_SIZES = [16, 32, 48]
APPLE_TOUCH_SIZE = 180

# A home screen wants the app icon's own safe area, but a tab is already padded by the browser, so
# the mark is cropped back to its own bounds plus this much breathing room before it is scaled down.
TAB_MARGIN = 0.04


def cropped_to_mark(icon: Image.Image) -> Image.Image:
    """The icon reframed to a square around the printed mark, dropping the icon-grid padding."""
    paper = icon.getpixel((0, 0))
    inked = ImageChops.difference(icon, Image.new("RGB", icon.size, paper))
    # The print is grainy and the paper is not perfectly flat, so ignore what is nearly paper.
    bounds = inked.convert("L").point(lambda p: 255 if p > 12 else 0).getbbox()
    if bounds is None:
        raise SystemExit(f"{SOURCE} is blank paper")

    left, top, right, bottom = bounds
    side = max(right - left, bottom - top) * (1 + 2 * TAB_MARGIN)
    center_x, center_y = (left + right) / 2, (top + bottom) / 2
    # Clamping keeps the crop inside the print; the mark stays centred as long as it fits, which it
    # does for any mark the icon grid would accept.
    side = min(side, icon.width, icon.height)
    center_x = min(max(center_x, side / 2), icon.width - side / 2)
    center_y = min(max(center_y, side / 2), icon.height - side / 2)
    box = (center_x - side / 2, center_y - side / 2, center_x + side / 2, center_y + side / 2)
    return icon.resize((icon.width, icon.height), Image.LANCZOS, box=box)


def main() -> None:
    mark = Image.open(SOURCE).convert("RGB")
    cropped_to_mark(mark).save(OUT / "favicon.ico", sizes=[(s, s) for s in ICO_SIZES])
    mark.resize((APPLE_TOUCH_SIZE, APPLE_TOUCH_SIZE), Image.LANCZOS).save(
        OUT / "apple-touch-icon.png"
    )


if __name__ == "__main__":
    main()
