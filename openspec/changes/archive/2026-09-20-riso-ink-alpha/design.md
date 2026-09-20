# Design

## Context

`RisoSheetNode` paints the stock and then draws its content on top. `RisoPassNode` records its content once and replays it per drum into a `GraphicsLayer` with `blendMode = BlendMode.Multiply`, `compositingStrategy = CompositingStrategy.Offscreen` and the pass shader as its `renderEffect`. The shader returns opaque colour: the printed transmittance `T = mix(1, ink, coverage)`, which is white where `coverage == 0`.

That pairing only works when the pass lands directly on the stock. Skia's separable multiply is

```
co = cs·(1 − ab) + cb·(1 − as) + cs·cb
```

With an opaque backdrop (`ab = 1`, `cb = P`) and an opaque source (`as = 1`, `cs = T`) it reduces to `co = P·T` — ink on paper. With a **transparent** backdrop (`ab = 0`, `cb = 0`) it reduces to `co = cs` — the pass simply paints `T`, and `T` is white wherever no drum reached.

Compose's `StretchOverscrollNode` supplies exactly that transparent backdrop. While nothing is stretched it draws content into the parent canvas (`else -> { drawContent(); return }`), but for as long as a stretch is live it calls `renderNode.beginRecording()`, draws the content into that new and therefore empty `RenderNode`, and then `canvas.drawRenderNode(renderNode)`. Same for any other ancestor that isolates a layer.

## Goals / Non-Goals

**Goals**
- A pass composites correctly whatever it lands on.
- Zero change to what a print looks like on a painted sheet, at any drum count.
- No new layer, buffer, clip or blend path.

**Non-Goals**
- Changing the blend mode, the layer structure or the knockout mechanism.
- Regenerating Stamp's icons or reworking `drawPacerMask` — follow-up.
- Anything about the single-pass-per-ink-node idea, still out of scope.

## Decisions

### D1 — Encode the pass premultiplied, with the smallest valid alpha

The shader emits, for `T = mix(1, ink, coverage)` and `m = min(T.r, T.g, T.b)`:

```
a   = round₈(1 − m)     // rounded to the 8-bit buffer first
rgb = T − (1 − a)       // then measured from what that rounding left
```

Rounding `a` before deriving `rgb` is what keeps `rgb + (1 − a) == T` at the value actually stored, which is the identity the multiply relies on. See the quantisation note under Risks for the measurements behind it.

**On a painted sheet (`ab = 1`, `cb = P`)** — the multiply gives

```
co = P·(1 − a) + rgb·P = P·(m + T − m) = P·T
```

identical to today, for every drum, because the destination stays opaque as passes accumulate.

**Where no ink** — `T = 1`, so `m = 1`, `a = 0`, `rgb = 0`. Fully transparent: the pass leaves the destination alone instead of covering it with white. This is what fixes the bug.

**Inside an isolating layer (`ab = 0`)** — the pass lands in the layer as `(rgb, a)`, and the layer is later composited (SrcOver) onto the stock:

```
P·(1 − a) + rgb = P·m + T − m
```

against an ideal `P·T`, so the print comes out light by `(1 − P)·(T − m)`. Zero for a neutral ink (`T − m = 0`), about a level for `vintageBlack`, and up to ~20 levels out of 255 on the strongest channel of a saturated ink like `blue` — measured, not estimated.

**This is the best any scalar alpha can do.** Keeping the multiply exact forces `rgb = T − 1 + a`, and substituting that into the layer path leaves an error of `(1 − P)·rgb` — so exactness through a layer would need `a = 1 − T` per channel, which a single alpha cannot express for a coloured ink. The error is monotonic in `a` for every channel at once, so the smallest `a` that keeps `rgb ≥ 0`, namely `a = 1 − m`, minimises it in all three simultaneously. `a = 1` is today's behaviour and the bug; anything between is strictly worse on every channel.

### D2 — Keep `BlendMode.Multiply`

Multiply is still what puts ink on stock, and is still exact there. The alternative — emitting `stock × T` and compositing SrcOver — would make a pass paint its own stock over anything beneath it, break overprint between drums (the second drum would replace the first rather than multiply with it) and require the ink to reproduce the sheet's surface shading exactly. `Modulate` is worse still: it drops the `(1 − ab)` and `(1 − as)` terms, so an isolated pass would come out black rather than white.

### D3 — Knockouts are untouched

A knockout punches `BlendMode.DstOut` into the pass's recorded content, which the shader then reads as `u_image`. A punched pixel arrives with `src.a == 0`, so `coverage` is 0, `T` is 1 and the pass emits transparent — a no-op over the stock, exactly what opaque white was. Multi-drum overprint inside an isolating layer compounds D1's approximation, since each pass then composites onto the previous one's partial alpha rather than onto opaque stock; it stays exact for neutral inks. Over the stock, overprint is exact at any drum count.

### D4 — `RisoPaper.None` changes, and that is the point

A sheet that paints no stock *is* a transparent backdrop, so today ink on `RisoPaper.None` already hits the degenerate case and comes off as an opaque white square. That is the documented basis for Stamp's adaptive-icon foreground and its `drawPacerMask` cut-back. After D1 the same print carries alpha, which is what a launcher compositing it over the background layer actually wants. Stamp keeps working — the mask still cuts to the ink's shape — but its exported PNGs change. Handled separately, as the proposal says.

## Risks / Trade-offs

- **Quantisation.** The buffer holds two rounded terms (`rgb`, `1 − a`) where it held one (`T`), so the print drifts slightly where rounding has the most to do — screened and mottled edges. Measured against the recorder's snapshots: **at most 2 levels out of 255, on 0.4–1.9% of pixels**; bare paper is byte-identical. Deriving `rgb` from the already-rounded alpha (D1) rather than from the exact value is what holds it there; taken from the exact value it is 3 levels on nearly twice as many pixels, and rounding the alpha up instead of to nearest is worse again. Two rounded terms cannot beat one, so this is the floor rather than something left on the table. Every existing `SheetRenderTest` case still passes at its original tolerance, including the three at `levels = 1` and the one at `levels = 0`.
- **A saturated ink prints light inside an isolating layer** — up to ~20/255, quantified in D1, and provably the floor for this approach. Visible as a slight lightening while an overscroll stretch is held, on saturated artwork only. Pacer prints its text and borders with neutral inks, where the error is about a level. Judged worth it against the alternative, which is a white card.
- **Shared shader text.** `INK_PASS_SKSL` compiles as both AGSL and SkSL; no new language features are used, but the change is verified on more than the JVM.
- **Performance.** Three extra ALU ops per fragment and nothing else: the pass already forces an offscreen buffer and a dst-read blend, so carrying alpha costs no fast path it had.

## Migration Plan

None. No public API changes, and no behaviour changes on a painted sheet.

## Open Questions

None.
