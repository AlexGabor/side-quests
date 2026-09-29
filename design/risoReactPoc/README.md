# Riso for React (prototype)

The [Riso](../riso) paper and ink, ported to React DOM. It checks how close a web version can get to the Compose one. It is not a module of the Gradle build and nothing depends on it.

Compose prints ink by post-processing a recorded subtree with a runtime shader (`INK_PASS_SKSL`). The DOM can't feed rendered elements into a shader, so each pass here is an **SVG filter** applied with CSS `filter`, laid onto the paper with `mix-blend-mode: multiply`. Text stays real, selectable DOM text.

```bash
npm install
npx playwright install chromium-headless-shell   # only for `npm run snapshot`
npm run serve        # http://localhost:8123/main.html: Compose snapshot beside the live components
npm run snapshot     # build/snapshots/react-desktop.png, the mirror of Snapshots.kt's desktop shot
npm run typecheck
```

`main.html` shows the Compose reference if `./gradlew :design:risoRecorder:snapshots` has been run; `build.sh` copies it in.

## Layout

| File | What it ports |
|---|---|
| `src/riso/press.ts` | `Inks`, `Press`, `risoInkForSlot`, `risoOverprint`, `separationRows` |
| `src/riso/tiles.ts` | the dot screen, mottle and grain as repeating tiles, and the paper's surface as a displacement map, built from the tiles `design/riso` ships |
| `src/riso/Ink.tsx` | `Modifier.risoInk` (`<Ink>`) and `risoKnockout` (`<Knockout>`) |
| `src/riso/components.tsx` | `risoPaper` (`<Paper>`), typography, `Button`, `ButtonGroup` |
| `src/snapshot.tsx` | `SnapshotPage`, for pixel comparison |
| `compare/` | the measurements behind the notes below (`pip install -r compare/requirements.txt`) |

## How a pass maps onto filter primitives

One `<Ink inks={[…]}>` is one pass with up to three drums. Per drum:

| `INK_PASS_SKSL` | Filter |
|---|---|
| `densityOfRgb` | `feComponentTransfer` tables of `−ln(x / paper) − floor`, scaled by `MAX_DENSITY` |
| `dot(u_row, D) · alpha` | `feColorMatrix` alpha row, then `in SourceAlpha` |
| warp by the sheet's surface | `feDisplacementMap` over the shipped fine tile, anchored where the drum lands |
| ink gain | `feFuncA type="gamma"` |
| `screenDots` | dot tile, then `feComposite arithmetic`: the soft threshold is linear in coverage and field |
| `inkTexture` | mottle × grain tile, then `arithmetic k1` |
| `mix(1, u_ink, c)` + Multiply | `feFlood` (ink floored at `MIN_TRANSMITTANCE`), `in`, and the element's `mix-blend-mode` |
| pass layer translation | whole device pixels with `feOffset`, the fraction with a bilinear 2×2 `feConvolveMatrix` |
| drums stacking | `feBlend mode="multiply"` |

## Where the web needs a different answer

- **Passes don't nest.** A CSS filter always covers its whole subtree, so a child pass would be printed again by its parent. A component names every drum it prints on, once, at its root. Separation then splits its content between those drums, e.g. ButtonGroup is one pass on `[vintageBlack, purple]`.
- **Knockouts are a punch copy.** `<Ink>` renders its subtree a second time, positioned over the pass and hidden except for its `<Knockout>`s, which draw in the paper colour. Inside the pass the knockout is transparent, so the fill runs on under it as Compose leaves a knockout out of the pass's artwork. The hole is pinned to the page and keeps the glyphs' own antialiasing.
- **Borders take no room.** Compose's `Modifier.border` draws over the content; a CSS `border` pushes it in and hides the fill's misregistration under it. ButtonGroup draws its rules in an SVG over the segments, multiplied so a rule crossing the fill separates as both inks.
- **Rules aren't snapped.** Chrome snaps box edges to device pixels, while Compose draws a rule at its fractional layout position ("On" is 18.46 dp wide). SVG geometry isn't snapped, which gives the same part-covered edge columns.
- **Type is drawn as Compose draws it.** Riso registers Fira Code VF without a weight, so Compose renders every style at wght 400, and styles of 600 and up get Skia's fake bold. `type()` copies that with a `-webkit-text-stroke` of the same width. The line height is the font's own 2400/1950 em, unrounded; Chrome's `normal` rounds the ascent and descent separately.
- **Tiles repeat only on whole pixels.** A rotated dot screen repeats only at angles with a rational tangent. `dotTile` picks the nearest such angle whose tile stays small, and nudges the dot size so the period is whole pixels. `feTile` only repeats the part of its input inside the filter region, so a tile that can't fit whole twice over is laid as merged copies instead. Every copy is snapped to device pixels in the filter's own space; a copy between pixels leaves a seam.
- **The paper is flat by default**, as the default stock is: its front and back are one colour, so the lighting cancels, and the texture only shows through the ink it warps. `<Paper texture>` lays paper.design's `PaperTexture` instead, which darkens the page by about 3 levels.

## How close it is

Measured on `SnapshotPage` at 2× (Compose / React), with a ButtonGroup added to the Compose page:

| | Compose | React |
|---|---|---|
| paper | 239, 235, 225 | 239, 235, 225 |
| text ink mass, heading / body | 26.4 / 33.6 | 26.8 / 33.3 |
| black text core | 73, 67, 55 | 74, 68, 56 |
| pink+blue 0.6, blue drum coverage | 0.681 | 0.676 |
| button tints, purple / pink | 0.734 / 0.804 | 0.763 / 0.794 |
| lum 1 px outside the knockout glyphs, then the fill | 147.9 → 105.6 | 148.8 → 105.4 |
| ButtonGroup purple vs black | (−7, −3) px | (−7, −3) px |
| divider edge, 10–90% | 2.13 px | 2.00 px |

Button tints are sampled in a 12×20 px strip, and one mottle blotch is 16 px across, so they differ by more than the swatches do.

**Still different:**
- Compose's divider has small ink clusters along its right edge (edge wobble std 0.14 px against 0.03 px here). Halftoning of the part-covered edge column doesn't explain them: that column is 92% covered and screens solid.
- Layout drifts by up to 1.5 dp down the page. That's text box heights, not ink.

Checked in Chromium only. Firefox and Safari, and the filters' cost with many inked elements, are not verified.
