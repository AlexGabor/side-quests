## Context

M10 needs two modal sheets on the Player: one for speed (a `Track`) and one for chapters (a long list). Features may not use Material directly (`design-system.md`), so the sheet belongs in `design/riso`. Material 3's `ModalBottomSheet` already gets the behaviour right on every target: drag, fling and settle, nested scroll, back, predictive back on Android, tap outside, and blocking input behind the sheet. Its scrim, though, is a dark layer over the page, and that is exactly what the Riso look forbids.

How Riso prints, from `docs/architecture/riso-paper-and-ink.md`:
- A page is one sheet of paper (`risoPaper`). Ink passes (`risoInk`) multiply onto it.
- `risoFade(f)` runs the press lighter. Passes inside it separate their coverage at `f`, so the dots get smaller while the ink stays at full strength and the stock shows between them. Screens use it to thin out in the navigation crossfade (`RisoFadeNavEntryDecorator`), as do `RisoCrossfade` and `RisoAnimatedVisibility`.
- An alpha over finished print, which is what a scrim effectively is, gives full-size dots that look washed out: "a print seen through fog".

`design/risoDemo` is an Android-only app (`com.android.application`), so today the demo cannot be shown on iOS.

## Goals / Non-Goals

**Goals:**
- A public `BottomSheet` composable with Material's modal behaviour on Android, iOS, JVM and wasmJs.
- The page behind is printed lighter rather than covered, in a way that fits the paper-and-ink model.
- Drop-in: no host or setup for apps beyond `RisoTheme`.
- A demo page on Android and iOS: a short sheet with a `Track`, and a long scrolling sheet.

**Non-Goals:**
- A standard (non-modal) sheet, a half-open stop, or a sheet-state API. M10 doesn't need them, and they can be added later.
- Anything Bookplate-specific, and any change to Bookplate or Pacer screens.
- A dark theme.

## Decisions

### 1. Build on Material 3's `ModalBottomSheet`, internally
`BottomSheet` wraps `ModalBottomSheet` with these settings: `scrimColor = Transparent`, `containerColor = Transparent`, `tonalElevation = 0`, `dragHandle = null`, `contentWindowInsets = { WindowInsets(0) }`, `sheetMaxWidth = RisoTheme.dimens.contentMaxWidth`, and `rememberModalBottomSheetState(skipPartiallyExpanded = true)`. Riso draws everything visible inside the content slot. Material stays an `implementation` dependency, and no Material type appears in the public signature.

- *Alternative: write it from scratch* with `anchoredDraggable`, `PredictiveBackHandler` and an in-tree overlay. That means reimplementing nested-scroll handover, fling damping, the predictive back transform, accessibility actions and window handling on each platform. It is a lot of code to own for no visible gain, and `design/riso` already uses Material internally (ripple, `Slider` in the print demo).
- `skipPartiallyExpanded = true` gives "sizes to its content": a tall sheet opens to its full height (capped at the window) instead of stopping at half.

### 2. Dismissing from inside: `BottomSheetScope.dismiss()`
The content gets a `BottomSheetScope : ColumnScope` with `dismiss()`. It hides the sheet with the animation, then calls `onDismissRequest`. This lets a chapter row close its sheet without exposing Material's `SheetState`. Material's own drag, back and scrim dismissals already run the same path.

### 3. The backdrop: the press runs lighter over the page, not a scrim
On a real press, "pushing the page back" behind a sheet laid over it means printing that page with less ink. It doesn't mean laying grey film on top. Riso already has exactly this operation, `risoFade`, and screens use it in the crossfade. So:

- `RisoTheme` wraps its content in an internal **backdrop**: a `Box(propagateMinConstraints = true)` carrying `risoFade(backdrop.fade)`. The backdrop's state is provided through an internal `LocalRisoBackdrop`.
- An open `BottomSheet` registers with the nearest backdrop, reports how much of it is on screen (`shown ∈ 0..1`), and unregisters when it leaves. The backdrop's fade is `1 − (1 − BackdropFade) · max(shown)` over the open sheets, with `BackdropFade = 0.35`.
- `shown` is measured from the sheet panel's own position: `(windowHeight − top) / height`, clamped. So the page regains ink as the sheet is dragged down, and loses it as the sheet animates in. Nothing is timed separately.
- The paper is untouched. `risoFade` only changes coverage, so the stock stays as it is. Between smaller dots it is the same stock as before, which is why "the paper dots stay visible". Fades multiply, so a screen part-way through a crossfade behind a sheet thins further. That is correct: two lighter passes.
- The sheet itself is **a second sheet of paper laid on the first**. Material renders the sheet in its own dialog window on Android, or its own scene layer on iOS, JVM and web. That window is not inside the backdrop, so the sheet prints at full ink. It carries its own `risoPaper`, its passes separate against its stock, and it covers the page where it lies. Its top edge and sides are inked in the content colour, because paper on paper of the same stock would otherwise have no visible edge.
- **Un-inked content** (images, plain fills) keeps its strength behind the sheet unless it opts in with `risoFadeAsAlpha`. That is the existing `risoFade` contract and the same as in the crossfade. Bookplate's cover art will need it in M10.
- **Effects off:** `risoFade` turns into a layer alpha, and at the root of the page that would let the window behind show through the paper. So with effects off, the backdrop instead draws the theme's paper colour over its content at `1 − fade`. That is the flat stand-in for the same result: the content colour pulled toward the paper.

Alternatives:
- *Translucent paper-coloured scrim* over the page (with effects on). This is the fog `risoFade` exists to avoid: dots stay full-size and only look washed out. Rejected.
- *A host composable the app has to place* (`BottomSheetHost { … }`). It is explicit, but every app would have to remember it, and a sheet opened outside it would silently get no backdrop. `RisoTheme` already wraps every app's content, and the backdrop does nothing while no sheet is open (`risoFade(1f)` changes no pass).
- *Rendering the sheet in-tree (no window) inside the backdrop's parent.* That would need a portal to lift the sheet out of its caller's position, which loses the caller's composition locals, and the sheet would be cut off by the caller's bounds. Material's window keeps both, and it is what makes back and predictive back work on Android.

### 4. Look
- Shape: `RisoTheme.shapes.standardShape` on the top corners, square at the bottom.
- Edge: a stroke at `lineWidth` along the left, top and right of that shape, in its own `risoInk(content, offsetScale = 0)` pass, in register: the edge runs along the sheet's clip, so a drum's throw would push one side off the paper and leave a gap on the other. The pass is a sibling drawn behind the content, so the caller's content isn't printed through the edge's drum.
- Drag handle: 32 × 4 dp pill, content ink at 50 %, 12 dp vertical padding, content description "Drag handle". Material's handle is skipped, so it carries its own semantics.
- Insets: the paper reaches the bottom of the window, and the column inside is padded by the bottom safe-drawing inset. The sheet is held `TopGap` (48 dp) below the top safe-drawing inset. A sheet that ran under the status bar would leave nothing to tap outside it, and the gap keeps some of the thinned page in view.

### 5. Demo: split `design/risoDemo` into `androidApp`, `sharedApp` and `iosApp`
iOS needs a host. The demo follows the app layout in `modules.md`:
- `design/risoDemo/sharedApp` (`sidequests.compose.mobile.library`: Android, iOS, plus `jvm()` for tests) holds `DemoPages`, `ComponentsDemo`, the new `SheetsDemo` and `MainViewController`, and exports a static `RisoDemo` framework.
- `design/risoDemo/androidApp` is the existing Android app, now only the `Activity`.
- `design/risoDemo/iosApp` is an Xcode project like Bookplate's.

The demo is a tool for `design/riso`, not an app, so it doesn't get Koin, navigation or Maestro flows.

## Risks / Trade-offs

- [`RisoTheme` gains a layout node around every app's content] → It is a `Box` that passes min constraints through, so the content measures as before. While no sheet is open the fade is 1 and no pass changes. Pacer and Bookplate are built and their tests run as part of this change.
- [Material's `ModalBottomSheet` is `@ExperimentalMaterial3Api`, and our Material is an alpha] → It is used only inside `BottomSheet`, so an API change stays inside one file.
- [Two windows on Android: the halftone grid is anchored to each window's root] → Both windows are full screen and edge to edge, so their roots coincide.
- [Un-inked content behind the sheet doesn't thin] → The `risoFadeAsAlpha` contract is documented in the KDoc and README. M10 applies it to cover art.
- [The backdrop recomposes a small scope on every frame while the sheet moves] → It is the same pattern `RisoCrossfade` uses (an animated float passed to `risoFade`), and only the backdrop's own scope recomposes, not the app content.

- [On iOS a drag that *starts* outside the sheet dismisses it, like a tap there; on Android only a tap does] → Compose Multiplatform's iOS dialog reports any press outside its content as a click outside. Harmless and close to how iOS sheets behave, so it is left as the platform's.

## Migration Plan

Gradle path `:design:risoDemo` → `:design:risoDemo:androidApp`. Update `settings.gradle.kts`, `design/riso/CLAUDE.md` and `docs/architecture/riso-paper-and-ink.md`. Nothing else references it.

## Open Questions

None blocking. If M10 needs a half-open stop or a state object, they can be added to `BottomSheet` without breaking its signature.
