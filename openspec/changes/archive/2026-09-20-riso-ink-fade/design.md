# Design

## Context

See `proposal.md` — Why. What shapes the approach is where the fade has to land relative to the
press:

- `InkPassShader` resolves `coverage = density(rgb) * alpha` from the artwork it is handed, then
  `screenDots` thresholds that coverage against the dot screen. Alpha in the artwork is therefore
  already a coverage control; nothing new has to be invented, only routed.
- `RisoPassNode` records its content into a `GraphicsLayer`, replays that recording once per drum
  into a pass layer, and hangs the ink shader on the pass layer as a `RenderEffect`. Anything applied
  outside the node — `fadeOut()`, `Modifier.alpha`, `AnimatedVisibility` — necessarily acts on the
  finished print.
- Nested passes do not draw through their ancestors' layers: the outermost pass lays them down
  itself via `layDown`, so an intermediate layer between two passes never touches the inner one.
  A fade therefore cannot be an ordinary layer if it is to reach a component that owns its ink.
- Layer properties are read when a layer is *replayed*, not when it is recorded — the reason
  `punches` already exists one-per-drum. Any per-use value has to be baked into a recording rather
  than set on a shared layer.

## Goals / Non-Goals

**Goals:**

- One fade that any subtree can apply, correct across nesting, knockouts and components whose ink
  the caller does not own.
- Draw-phase only: an animating fade must not recompose the content it fades.
- No change to the shader, its uniforms or any per-platform code.

**Non-Goals:**

- Size or position animation. `RisoAnimatedVisibility` dissolves; a caller that needs
  `expandVertically`/`shrinkVertically` keeps using `AnimatedVisibility`.
- Migrating other transitions (`Crossfade` in `pacer/sharedApp/App.kt`) — separate change.
- Fading *paper*. The stock is not ink and does not thin.

## Decisions

### The fade is applied inside the pass recording, as a `DstIn` over the artwork

In `layDown`, the pass records `drawLayer(content)` and then `punchKnockouts(index)` into the pass
layer. The fade goes between those two, as a full-bounds `drawRect(alpha = fade, blendMode = DstIn)`,
skipped entirely at `fade == 1f`.

`DstIn` scales premultiplied colour and alpha by the same factor, so the shader's unpremultiply
(`rgb / alpha`) recovers the artwork's original colour at a lower alpha — literally "the same artwork
drawn fainter", which is the effect the design system already gets from animating a colour toward
transparent. Coverage falls, the screen prints smaller dots, and ink keeps its density where it
lands. Its position before `punchKnockouts` is what keeps friskets fully cut: the hole is not ink.
The offscreen buffer this needs is already required and already named — `pass.layer` sets
`CompositingStrategy.Offscreen` for the punches.

*Alternative: `content.alpha = fade` on the recorded content layer.* Rejected on two counts. The
layer is replayed both as this pass's artwork and (for a knockout) as a punch, and a layer property
is read at replay, so one alpha cannot serve both. And with the default `Auto` strategy the alpha is
modulated per draw operation, so a component drawing a background under its own text would fade the
overlap twice.

*Alternative: a `u_fade` uniform multiplying coverage in the shader.* Rejected: it spreads across
`InkPassShader`, `ShaderUniforms` and the drum spec for a result the buffer already gives, and the
shader is the one place in the system where a mistake costs every platform at once.

*Alternative: leave it outside, as today.* That is the wash the proposal exists to replace.

### Inheritance by node traversal, mirroring `RisoSheetNode`

`Modifier.risoFade(alpha)` installs a `RisoFadeNode` (`TraversableNode`, key `RisoFadeKey`). At
attach, a `RisoPassNode` finds its nearest fade ancestor and registers as a dependent, exactly as it
already does with its sheet; a `RisoFadeNode` registers with its own nearest fade ancestor so nested
fades chain. A pass resolves its fade at draw time by walking that chain and multiplying, so nesting
multiplies by construction. When a fade's value changes it invalidates its dependents' draws.

*Alternative: a `CompositionLocal`.* Rejected: an animating float read in composition recomposes the
whole faded subtree every frame, and `risoInk` is `@ReadOnlyComposable` — it resolves at composition,
which is the wrong phase for something that changes per frame.

With effects disabled `risoFade` returns `Modifier.graphicsLayer { alpha = fade }` instead, matching
how `risoInk` stands the press down: there is no coverage to thin, so the plain alpha is the honest
fallback.

### Un-inked content opts in with `Modifier.risoFadeAsAlpha()`

The `DstIn` lives inside a pass, so it reaches only what the press prints. An image or a plain fill
— the riso-paper spec's own "Photo on paper" case — would hold full strength through the whole exit
and then pop when the gate flips. `Modifier.risoFadeAsAlpha()` resolves the same fade chain and
applies it as an ordinary layer alpha: a `DrawModifierNode` that registers with its nearest
`RisoFadeNode` for invalidation, records its content into a `GraphicsLayer` with
`CompositingStrategy.Offscreen` and draws it at the resolved fade. No fade ancestor, or a fade of
`1f`, means it draws content directly and allocates nothing.

It is a free modifier rather than a receiver on a `RisoAnimatedVisibility` content scope: it then
works under a hand-applied `risoFade` too, and avoids introducing a scope type whose only member
would be a modifier. `Offscreen` over `ModulateAlpha` because un-inked content that overlaps itself
would otherwise fade at the overlap twice — the same trap the content-layer alpha fell into above.

Two things it must not do. It must be a no-op with effects disabled, since `risoFade` is *already* a
plain layer alpha over the whole subtree there and a second one would fade twice. And it must not be
applied to inked content, for the same reason on the other path — documented on the modifier, not
enforced, in the same spirit as `risoInk` trusting the caller to name a drum rather than a printed
colour.

### `RisoAnimatedVisibility` is a gate around an animated float, not `AnimatedVisibility`

```
val fade by animateFloatAsState(if (visible) 1f else 0f, animationSpec)
if (visible || fade > 0f) { Box(Modifier.risoFade(fade)) { content() } }
```

Once the ink owns the dissolve, `AnimatedVisibility`'s remaining jobs are holding content composed
through the exit and releasing its layout afterwards, which the gate does directly —
`animateFloatAsState` lands exactly on its target, so the content leaves layout on the frame the
dissolve finishes. It also avoids `AnimatedVisibility`'s own `Layout` swallowing parent data such as
`ColumnScope.align`, which is the bug this change picks up at `PacerScreen.kt:221`.

Content on the way out consumes pointer events at `PointerEventPass.Initial` while `!visible`, so a
nearly-faded control cannot be tapped — a guarantee `AnimatedVisibility` does not give either.

The composable takes `visible`, `modifier`, an `animationSpec` defaulting to the theme's usual
spring, and the content. No enter/exit parameters: there is one transition, and that is the point.

## Risks / Trade-offs

- **`DstIn` inside a recording behaves differently per platform** → The blend already runs there for
  punches on every target. Covered by a JVM render test in `SheetRenderTest`'s harness (fade 0, 0.5,
  1) and a visual check on Android and web before archiving.
- **A fading subtree re-records its content every frame**, since `draw()` records before it lays
  down → Accepted: it is the same cost any per-frame draw invalidation carries, and fades are
  short-lived and small. The `fade == 1f` fast path keeps unfaded screens untouched.
- **Dots vanish rather than shrinking to nothing at the very end**, because coverage below the
  shader's `FAINT_COVERAGE` skips screening → Invisible in practice at that coverage; not worth a
  shader change.
- **Two fades over the same content multiply**, which is right for nesting but surprising if a caller
  puts `risoFade` inside `RisoAnimatedVisibility` → Documented on both.
- **`risoFadeAsAlpha()` on inked content double-fades it**, as coverage and again as transparency →
  Documented on the modifier and paired with the no-op-when-effects-are-off rule, which is the same
  mistake on the other path. Nothing in the API can tell whether its content is printed, so this
  stays a matter of the KDoc rather than an assertion.

## Migration Plan

Additive. `risoFade` and `RisoAnimatedVisibility` are new API, `RisoPassNode` gains a no-op path at
full fade, and the only behavioural change to existing screens is `DistancePresets`, which swaps its
transition and gains the end alignment it was already asking for. Rollback is reverting the call
site; the riso additions are inert until used.
