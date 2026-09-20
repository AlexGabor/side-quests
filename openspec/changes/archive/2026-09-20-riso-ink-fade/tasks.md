# Tasks

## 1. The fade the press reads

- [x] 1.1 Add `RisoFadeNode` and `Modifier.risoFade(alpha)` in `design/riso`
      (`risograph/inks/RisoFade.kt`): a `TraversableNode` under a `RisoFadeKey` holding a clamped
      alpha and a dependent set, mirroring `RisoSheetNode`'s `addDependent`/`removeDependent`/
      `redraw`. With `LocalRisoEffectsEnabled` false it returns `Modifier.graphicsLayer { this.alpha
      = fade }` instead. Verify it compiles and attaches with `./gradlew :design:riso:jvmTest`.
- [x] 1.2 Register the chain: in `onAttach`, `RisoFadeNode` finds its nearest fade ancestor and
      `RisoPassNode` finds and registers with its nearest fade ancestor; both deregister in
      `onDetach`. Verify no leak by checking a detached pass is dropped from the fade's dependents in
      a `:design:riso:jvmTest` unit test.
- [x] 1.3 Resolve the fade at draw time in `RisoPassNode.layDown` by walking the fade chain and
      multiplying, and apply it inside `pass.layer.record` as a full-bounds
      `drawRect(alpha = fade, blendMode = BlendMode.DstIn)` between `drawLayer(content)` and
      `punchKnockouts(index)`, skipped entirely when `fade == 1f`. Verify the existing
      `:design:riso:jvmTest` suite still passes unchanged, which is the `fade == 1f` fast path.
- [x] 1.4 Add `Modifier.risoFadeAsAlpha()` beside it: a `DrawModifierNode` that registers with its
      nearest `RisoFadeNode`, and at a fade below `1f` records its content into a `GraphicsLayer`
      with `CompositingStrategy.Offscreen` and draws it at that alpha. A no-op with no fade ancestor,
      at full fade, and with `LocalRisoEffectsEnabled` false. Verify with a `:design:riso:jvmTest`
      render test that an un-inked fill inside a fade is unchanged without it and drawn at the fade's
      alpha with it.
- [x] 1.5 Document `risoFade` and `risoFadeAsAlpha` in the `RisoInkModifier` KDoc idiom — what a
      fade means on a press, that nested fades multiply, that a knockout keeps cutting in full, and
      that `risoFadeAsAlpha` is for content the press does not print, never for inked content, which
      the fade would then thin twice. Verify by reading it beside the `risoInk`/`risoKnockout` docs
      for the same voice.
- [x] 1.6 Sync `design/riso/README.md` with the new public API, which `design/riso/CLAUDE.md`
      requires of any change to it: a Fading section and a line in "With effects disabled". Verify
      by reading it against the modifiers' KDoc.

## 2. Showing and hiding

- [x] 2.1 Add `RisoAnimatedVisibility(visible, modifier, animationSpec, content)` in `design/riso`
      (`components/RisoAnimatedVisibility.kt`): `animateFloatAsState` gated by
      `if (visible || fade > 0f)`, content wrapped in `Modifier.risoFade(fade)`. Verify with a
      `:design:riso:jvmTest` test that content is composed while fading out and absent once settled.
- [x] 2.2 Block input on the way out — consume pointer events at `PointerEventPass.Initial` while
      `!visible`. Verify with a test that a click on content mid-exit does not reach its `onClick`.
- [x] 2.3 Add a `RisoAnimatedVisibility` case to `RisoPrintDemo` so the dissolve is visible in the
      riso demo app, including un-inked content beside inked content — one taking
      `risoFadeAsAlpha()` and one not — so the difference is on screen. Verify by running
      `:design:risoDemo` and watching the dots shrink out while the un-inked fill behaves as each
      case asks.

## 3. Pacer call site

- [x] 3.1 Rewrite `DistancePresets` in `pacer/feature/home` (`PaceCalculator.kt`) to use
      `RisoAnimatedVisibility`, dropping the `AnimatedVisibility`/`fadeIn`/`fadeOut` imports. Verify
      with `./gradlew :pacer:feature:home:jvmTest`.
- [x] 3.2 Confirm the presets row now honours `Modifier.align(Alignment.End)` in the left pane
      (`PacerScreen.kt:221`) — it is the `Column`'s own child again. Verify in the wide-window
      preview that the row sits at the end, not the start.
- [ ] 3.3 Check `pacer/maestro/flows/distance_presets.yaml` still passes with the new transition, and
      update its waits if the dissolve changes the timing. Verify by running the flow against a
      debug build. **Not run: maestro is not installed on this machine.** Its assertions were
      driven by hand against the debug build on the emulator instead — tapping `HM` gives
      21.10 km / 4h 13m 12s / 12:00 min/km, `HM` leaves the hierarchy when the Distance card is
      selected and comes back with the Pace card — so no wait needs changing, but the flow itself
      still wants a run.
- [x] 3.4 Check whether `pacer/README.md`'s feature list needs a word about the presets' appearance
      (line 17) and update it only if the user-visible description is now wrong. Checked: it
      describes what the presets do, not how they arrive, so it stays as it is.

## 4. Verification

- [x] 4.1 Add render tests to `design/riso/src/jvmTest/.../SheetRenderTest.kt` (flat press, fixed
      density) covering the spec's scenarios: a fade of `1` matching an unfaded print pixel for
      pixel, a fade of `0` leaving bare stock, a fade of `0.5` printing less total ink than full
      strength while an inked pixel keeps its density, nested fades multiplying to `0.25`, and a
      knockout still reading as bare stock at a partial fade, an un-inked fill holding full
      strength inside a fade, and the same fill at the fade's alpha once it takes
      `risoFadeAsAlpha()`. Verify with `./gradlew :design:riso:jvmTest`.
- [x] 4.2 Run `./gradlew :design:riso:jvmTest :pacer:feature:home:jvmTest :pacer:sharedApp:jvmTest`
      and confirm all pass.
- [x] 4.3 Check the dissolve on an Android emulator — the `DstIn`-inside-a-`RenderNode` path — on the
      Pacer presets and the riso demo: dots shrink rather than pale, nothing flashes at the end of
      the fade, and unfaded screens are unchanged.
- [x] 4.4 Run `openspec validate riso-ink-fade --strict` and confirm the change is clean before
      `/opsx:archive`.
