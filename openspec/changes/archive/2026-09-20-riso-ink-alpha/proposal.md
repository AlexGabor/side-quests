# Proposal

## Why

Pull Pacer's list past its end on Android and the cards go opaque white until the stretch releases.

An ink pass is composited with `BlendMode.Multiply` and its shader returns **opaque** colour — white where the drum laid no ink. White multiplying onto the stock is a no-op, which is what lets bare paper show through a pass with no seam. But Compose's stretch overscroll records the scrolling content into a fresh, empty `RenderNode` for as long as a stretch is active, and multiply against a transparent backdrop collapses to plain source: every pass stops multiplying and paints its white instead. The sheet is drawn at the navigation root, outside that `RenderNode`, so it never gets to show through.

Overscroll is only the first thing to trip this. `Modifier.alpha`, an `AnimatedVisibility` fade, predictive back, or any `CompositingStrategy.Offscreen` between the sheet and the ink does the same. The `riso-paper` capability says ink prints onto the stock; it does not say that only holds when nothing sits in between, and today it only holds then.

## What Changes

- **An ink pass carries its own alpha.** Instead of opaque white meaning "no ink", a pass is transparent where no ink was laid and premultiplied where it was, encoded so that multiplying it onto the stock gives exactly the result it gives today.
- **Ink prints correctly through an isolating layer.** A pass that lands in a separate layer instead of on the stock now composites into that layer and reaches the stock when the layer does, rather than covering it.
- **BREAKING (visual, `RisoPaper.None` only):** ink printed on a sheet that paints no stock used to come off as an opaque white square carrying the inks' transmittance. It now carries alpha, so the areas no drum reached are transparent. This is what a launcher compositing an adaptive icon over its background layer wants, but it changes what `tools/stamp` exports.
- **Not changed:** what a print looks like on a painted sheet. The composite is algebraically identical over an opaque stock, at any drum count, so no existing screen changes.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `riso-paper`: adds a requirement that an ink pass prints the same whether or not an ancestor isolates its content into a layer, and states what a pass on an unpainted sheet leaves behind.

## Impact

- **`design/riso`** (`commonMain`): `risograph/inks/InkPassShader.kt` — the pass shader's output encoding. `risograph/inks/RisoInkModifier.kt` — comments only; the layer setup, blend mode and knockout punches are unchanged. New regression test in `jvmTest`.
- **`docs/architecture/riso-paper-and-ink.md`**: the section on how ink meets paper.
- **`tools/stamp`**: not edited here. Its adaptive-icon foreground is built around the old opaque-white output (`IconLayers.IconForeground`, `PacerIcon.drawPacerMask`), so its exported PNGs change the next time it runs. Regenerating the icons and revisiting that mask is follow-up work.
- **`pacer/feature/home`, `pacer/feature/settings`, `pacer/sharedApp`**: not edited; they are where the bug shows and where it is verified.
- **Unchanged:** Pacer's public link parameters, the Riso public API, and every other `riso-paper` requirement.
