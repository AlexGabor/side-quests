# Tasks

## 1. Reproduce

- [x] 1.1 Capture baseline `:design:risoRecorder:snapshots` before touching the shader, to compare against afterwards.
- [x] 1.2 Add a failing regression test to `design/riso/src/jvmTest/.../paper/SheetRenderTest.kt`: a `risoPaper` whose inked content sits inside `Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`. Assert the un-inked interior is still the stock colour. Confirm it fails with the current shader, and that the failure is white.

## 2. Fix

- [x] 2.1 `InkPassShader.kt`: emit `a = 1 − min(T)` and premultiplied `rgb = T − min(T)` in place of `half4(half3(T), 1.0)`.
- [x] 2.2 `InkPassShader.kt`: rewrite the KDoc that states the pass returns opaque colour, and the inline comment that says nothing drawn "comes back as white below", to describe the new encoding and why it survives an isolating layer.
- [x] 2.3 `RisoInkModifier.kt`: update the comment at `blendMode = BlendMode.Multiply` to record the invariant — multiply is still exact on stock, and a pass now degrades gracefully when something isolates it.

## 3. Verify

- [x] 3.1 `:design:riso:jvmTest` — the new test passes; the existing `SheetRenderTest` cases still pass. Investigate any tolerance that has to move, and record why.
- [x] 3.2 `:design:riso:allTests`, plus compiling Android, iOS, desktop and web, since the shader text is shared.
- [x] 3.3 Re-run `:design:risoRecorder:snapshots` and diff against the baseline: printing on a painted sheet must be unchanged.
- [x] 3.4 Android: run Pacer, hold an overscroll stretch on the home list, and confirm the cards keep their paper. Same on a card's horizontal track. Screenshot both.
- [x] 3.5 Confirm `design/riso/docs/*.webp` are untouched.

## 4. Document

- [x] 4.1 Update the ink-meets-paper section of `docs/architecture/riso-paper-and-ink.md` for the new encoding, stating the isolating-layer behaviour as a property.
- [x] 4.2 File the Stamp follow-up: regenerate Pacer's icons, review the foreground's new alpha, and rewrite the KDoc in `IconLayers.kt` / `PacerIcon.kt` that documents the opaque-white press output.
