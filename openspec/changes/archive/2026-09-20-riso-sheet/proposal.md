# Proposal

## Why

Today `risoPaper` is a render effect that post-processes everything drawn inside it. Every pixel in its layer is read as ink transmittance. This has several costs:

- **It is hard to use.** Photos and other plain content need `risoBypass`. Anything drawn without `risoInk` is still multiplied into the stock. Ink separates against the theme's paper colour instead of the sheet it is printed on.
- **It is expensive when applied to a whole screen,** which is how the apps use it. It keeps a full-screen offscreen buffer, and any change inside it re-runs the shader over every pixel.
- **The paper surface is baked per layout size.** On desktop that bake is a CPU raster that takes most of a second. The web can't bake at all and computes the surface on every frame. Changing the paper colour also triggers a re-bake.

See [docs/architecture/riso-paper-and-ink.md](../../../docs/architecture/riso-paper-and-ink.md), §5, §7 and §11.

## What Changes

- **The paper is drawn behind the content, not over it.** `Modifier.risoPaper(paper)` keeps its signature. It now paints the stock and its surface as a background, and makes itself findable by the `risoInk` passes inside it. It no longer wraps its content in a render effect.
- **Content without `risoInk` draws exactly as authored**, on top of the paper. It is not multiplied into the stock and not warped.
- **The coupling between paper and ink becomes explicit.** `risoInk` passes find their enclosing sheet and do three things with it:
  - separate colours against that sheet's actual stock colour
  - sample its surface to warp their own artwork
  - multiply onto it
  
  The printed result for inked content is the same as before (stock × ink transmittance).
- **The paper surface becomes tileable and anchored in dp.** It is baked once per surface shape and density, and never per layout size:
  - Colours, and the strengths of contrast, roughness, fiber and fade, are draw-time parameters. Changing them never re-bakes.
  - The default stock's tiles ship pre-baked as resources.
  - Runtime bakes are cached in memory, and on disk where the platform has one.
- **One paper path on every platform.** The web's per-frame inline surface goes away, and so does the desktop's settle delay and per-size re-bake.
- **Coloured and nested papers.** Any stock colour works without a re-bake. A `risoPaper` nested inside another prints its own stock, and the ink inside it separates against that stock.
- **New API:** `@Composable RisoPaper.isSurfaceReady()` tells code that captures single frames (Stamp's exporter) when the surface has landed.
- **BREAKING:** `Modifier.risoBypass` and `RisoBypassRect` are removed, because content opts out of printing simply by not using `risoInk`.
- **BREAKING (visual):** content drawn inside a paper without `risoInk` now keeps its own colour. Colours pre-multiplied by the stock (for example `onRisoPaper()` backgrounds) come out lighter than before, because the stock is no longer applied twice.
- **BREAKING (visual):** the paper grain now has a fixed physical size, measured in dp. Before, it was scaled to the layer, so large windows now show more, finer grain rather than stretched grain.
- **Out of scope, separate change:** drawing each ink node in a single pass instead of one layer per drum.
- **Out of scope:** Pacer keeps its current Riso-effects defaults per platform (spec `pacer-settings` is unchanged).

## Capabilities

### New Capabilities
- `riso-paper`: how the Riso paper renders under content, and how ink printed on it behaves. Covers stock colour, the surface and its warp, nesting, the effects-off fallback, and consistency across platforms.

### Modified Capabilities
<!-- None: pacer-settings defaults stay as they are. -->

## Impact

- **`design/riso`** (all source sets):
  - `risograph/paper/*` is rewritten.
  - `risograph/region/*` (bypass and the region host) is deleted.
  - `risograph/inks/RisoInkModifier.kt` and `InkPassShader.kt` gain sheet lookup and the surface warp.
  - The `inlinePaperMain` and `bakedPaperMain` source sets are removed; `metalBakeMain` and a new `rasterBakeMain` hold the bake surfaces.
  - New pre-baked tile resources are added under `composeResources`.
- **`design/riso` docs:** `README.md`, `docs/architecture/riso-paper-and-ink.md` and `docs/architecture/design-system.md` are updated.
- **`design/riso` build:** gains a `bakeTiles` task that produces the shipped tile resources.
- **`design/risoRecorder`:** gains a `snapshots` task for before/after comparisons. The `docs/*.webp` images are regenerated.
- **`pacer/sharedApp`:** `RootNavigation` keeps `risoPaper()`. Screens are audited for content that relied on being multiplied by the stock.
- **`tools/stamp`:**
  - Its two `risoBypass` uses are removed.
  - The `IconLayers` sheets are re-checked, especially the white-under-paper and `RisoPaper.None` cases.
- **Unchanged:** Pacer's public link parameters, and the `pacer-settings` behaviour.
