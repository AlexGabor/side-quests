## Why

Bookplate's Player (M10) opens two sheets, one for playback speed and one for the chapter list. Riso has no sheet, and features may not use Material directly. Material's modal sheet also covers the screen with a dark scrim, which hides the paper and the halftone dots Riso is built around. This is milestone M3 in `bookplate/ROADMAP.md`.

## What Changes

- New `RisoBottomSheet` in `design/riso`, a modal bottom sheet that behaves like Material's:
  - it sizes to its content and scrolls content that is taller than the screen
  - dragging or flinging it down dismisses it; a short drag settles back
  - back dismisses it, with predictive back on Android
  - tapping outside it dismisses it
  - nothing behind it receives input while it is open
- No scrim. While a sheet is open, the page behind it is printed lighter (the same `risoFade` that screens use in the crossfade), so the paper and smaller ink dots stay visible. How light follows how much of the sheet is on screen. With effects off, a paper-colored veil does the same job.
- `RisoTheme` gains an internal backdrop around its content, so every app gets this without setting anything up.
- The sheet is laid on as a second sheet of paper: its own `risoPaper`, a drag handle and an inked edge along its top.
- `design/risoDemo` gets a "Sheets" page with a short sheet (a `Track`, like a speed control) and a long scrollable sheet (like a chapter list).
- `design/risoDemo` is split into `androidApp`, `sharedApp` and a new `iosApp`, so the demo also runs on the iOS simulator. **BREAKING** for tooling only: the Gradle path `:design:risoDemo` becomes `:design:risoDemo:androidApp`.

## Capabilities

### New Capabilities
- `riso-bottom-sheet`: the modal bottom sheet, how it opens, is dismissed and blocks the page behind it, and how the page behind is thinned instead of covered by a scrim.

### Modified Capabilities
None.

## Impact

- `design/riso`: new `components/BottomSheet.kt`, an internal backdrop in `RisoTheme`, README and docs. Uses Material 3's `ModalBottomSheet` internally; no Material type is public. All current targets (Android, iOS, JVM, wasmJs) keep building.
- `design/risoDemo`: becomes `androidApp` + `sharedApp` + `iosApp`. References to `:design:risoDemo` in `design/riso/CLAUDE.md` and `docs/architecture/riso-paper-and-ink.md` are updated.
- Pacer and Bookplate: no source changes. They pick up the `RisoTheme` backdrop, which does nothing while no sheet is open.
- No changes to Pacer's public link parameters.
