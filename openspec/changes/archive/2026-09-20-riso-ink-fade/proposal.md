# Proposal

## Why

Fading inked content with a `graphicsLayer` alpha — which is what `fadeIn()`/`fadeOut()` and
`AnimatedVisibility` do — fades the *print* rather than the *printing*. The pass lays its ink down at
full coverage and the finished result is composited translucent, so full-size halftone dots go pale
and wash out. Animating an artwork colour toward transparent instead lowers coverage, because the ink
shader resolves coverage as the artwork's optical density times its alpha, and the dot screen then
prints smaller dots: ink thins out the way it does on a press. That second look is the one the design
system means, and today nothing reaches it except hand-animating a colour inside a component that
happens to expose one — which `FlatButton` and `Heading3` do not.

## What Changes

- Add `Modifier.risoFade(alpha)` to `design/riso`: a fade the press reads. Every ink pass beneath it
  applies the inherited fade to its recorded artwork *before* separation, so coverage falls and the
  screen prints smaller dots. Nested fades multiply.
- Add `Modifier.risoFadeAsAlpha()` to `design/riso`: content the press does not print — an image, a
  plain fill — has no ink to thin, so it opts into the enclosing fade as ordinary transparency and a
  region holding both inked and un-inked content disappears as one.
- Add `RisoAnimatedVisibility` to `design/riso`: enter and exit as that dissolve, content held in
  composition until the fade settles and dropped from layout afterwards. Same call shape as
  `AnimatedVisibility` for visible/content, without enter/exit transition parameters.
- Migrate `DistancePresets` in `pacer/feature/home` off `AnimatedVisibility` + `fadeIn()`/`fadeOut()`
  onto `RisoAnimatedVisibility`.
- Fix, as a consequence of that migration, the `Modifier.align(Alignment.End)` passed at
  `PacerScreen.kt:221`: it is currently swallowed by `AnimatedVisibility`'s own `Layout` (parent data
  only reaches the immediate parent layout), so the presets row is start-aligned in the left pane
  today and will sit at the end once the row is the `Column`'s child again.
- No change to Pacer's public link parameters.

## Capabilities

### New Capabilities

None. The fade is ink behaviour and belongs in the capability that already defines how ink prints.

### Modified Capabilities

- `riso-paper`: gains requirements for fading ink — a fade lowers coverage rather than compositing
  the print translucent, it is inherited by nested passes and multiplies, it reaches knockouts, and
  with effects disabled it degrades to a plain alpha on the authored colours. Also covers un-inked
  content opting into the fade as transparency, and what `RisoAnimatedVisibility` guarantees: content
  stays composed until the dissolve settles, then leaves layout.

## Impact

- `design/riso` — new `risoFade` and `risoFadeAsAlpha` modifiers and a `RisoAnimatedVisibility`
  composable; `RisoPassNode` (`risograph/inks/RisoInkModifier.kt`) reads the inherited fade at draw
  time and applies it to the artwork in its pass recording, before the ink shader separates it. No
  shader or uniform change.
- `pacer/feature/home` — `PaceCalculator.kt` (`DistancePresets`), affecting both call sites:
  `PacerScreen.kt` left pane and the single-column list.
- Public API addition only; nothing existing changes shape, and content outside a `risoFade` is
  unaffected.
