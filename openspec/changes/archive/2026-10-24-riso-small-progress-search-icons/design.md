## Context

`design/riso` has a handful of components (`Button`, `FlatButton`, `Card`, `Toolbar`, `Track`, `ButtonGroup`) and a stroked icon set (`Back`, `Settings`, `Share`). The icons are built at runtime from SVG path strings in a 24-unit viewport, so that their stroke can be the theme's `lineWidth`. Riso has no text input and no progress indicator. It has one light palette (paper `#EFEBE1`, content vintage black, accent purple). Its color locals are internal, and nothing in it switches on the system dark theme.

Bookplate needs these components for Home and Player (M9 and M10), but they are general controls, so they are designed without Bookplate in mind. `design/risoDemo` is an Android app that today only hosts `RisoPrintDemo`.

## Goals / Non-Goals

**Goals:**
- `SmallProgress`, `SearchField` and nine icons, styled like the rest of Riso and printed in Riso ink.
- Work on every current `design/riso` target (Android, iOS, JVM, wasmJs). Everything goes in `commonMain`.
- A components page in `design/risoDemo`.
- Tests in the existing styles: `runComposeUiTest` behaviour tests in `commonTest`, and pixel checks through `ImageComposeScene` in `jvmTest`.

**Non-Goals:**
- A dark theme or a way to swap the palette. Riso has one light paper, and the demo shows components on it.
- The bottom sheet (M3).
- Using the components in Bookplate or Pacer screens.
- A general multi-line `TextField`. `SearchField` is the only input for now. A general field can be pulled out of it when a second use appears.

## Decisions

**`SmallProgress(progress: Float, modifier)`, drawn rather than composed.** One `Canvas`-style `drawBehind` with two rects keeps it cheap in long lazy lists (library rows). Its height is `RisoTheme.dimens.lineWidth`, the same weight as Riso's borders and toolbar rules, so it reads as "a line". It has square ends, so 0 % and 100 % fill exactly edge to edge, and a partial value meets at a clean seam. The node prints with `risoInk(accent, content, offsetScale = 0f)`, in register, because the drums' 3 dp registration error is wider than the 2 dp line and split the two parts apart on device: the accent part separates onto the accent drum, and the 50 % content part becomes a half-tone on the black drum, which is how a riso print shows a tint. Clamping is done once, with NaN treated as 0, so a book with unknown duration can't crash a row. Semantics use `progressBarRangeInfo`. Layout direction is respected by mirroring the completed rect. *Alternative:* a `Track`-style ruler. That would be too heavy for a list row, and the spec asks for a line.

**`SearchField` built on `BasicTextField` with a decoration box.** Foundation's `BasicTextField(value: String, …)` gives the input, IME and selection without Material's look. Riso may use Material internally, but `TextField`'s indicator and label animation fight the Riso style. The decoration is a `Row`: `Icon(Search)`, then the text with its placeholder, then `FlatButton(icon = Close)` while the value is non-empty. The border is `lineWidth` in `standardShape`, content-colored. While focused it is drawn like a selected `Card`: doubled, in `RisoMix(content to .5, accent to 1)`, animated with the same low-stiffness spring. The whole field prints with `risoInk` on that mix's drums. The placeholder is content at 70 %. The cursor is a `SolidColor(accent)`. Selection colors already come from `RisoTheme`. The API is controlled (`value`, `onValueChange`), plus `placeholder: String?`, `onSearch: ((String) -> Unit)?`, `clearContentDescription: String = "Clear"` and `modifier`. `singleLine = true` with `ImeAction.Search`. *Alternative:* the `TextFieldState` overload. It is newer and less settled across CMP targets, and a controlled `String` is simpler for a state holder to drive.

**Icons stay as stroked path strings, common to every platform.** The nine new glyphs go in `IconPaths.kt` (commonMain). Only `Share` differs per platform, because it mimics each platform's native glyph. Media glyphs are the same everywhere. The shapes reuse the existing geometry: content spans about 5–19 units, like `Back`, the stroke is round, and nothing is filled. The 10 s icons draw an open circular arrow (radius 7) with the digits "1" and "0" stroked inside. `Forward10` mirrors `Rewind10`'s arrow but not its digits. `Play` is an outlined triangle nudged left of center so it looks optically centred.

**The demo gets a second page, kept in the demo app.** `design/risoDemo` gets a `ComponentsDemo` composable (in the app module, not in `design/riso`, so the library's API doesn't grow a showcase), and a `ButtonGroup` at the top switches between "Print" and "Components". The components page shows every icon with its name, `SmallProgress` at 0 %, 40 % and 100 % plus an interactive one, `SearchField` empty, filled and live, and `FlatButton`s with the new icons, all on `risoPaper()`.

## Risks / Trade-offs

- [Light ink drawn at 50 % alpha separates to a half-tone that may look dotty on a 2 dp line] → This is how riso prints tints. Checked on device with effects on. If it reads poorly, the remaining part can be authored as `content.onRisoPaper()` at the same visual weight without changing the spec.
- [A text field inside a `risoInk` pass re-records the pass on every cursor blink] → The pass is clipped to the field's bounds (§5.4 of riso-paper-and-ink.md), so the cost is one small layer. Acceptable for a single search field.
- [Adding enum entries to `IconType` breaks an exhaustive `when` outside `design/riso`] → None exist today. Pacer and Bookplate builds are part of verification.
- [The "10" digits are small at 32 dp] → Strokes are 1.5 units wide in the viewport. Digits are 6 units tall, which is legible at 32 dp. Checked on device.
- [Found on device: an inked component nested inside a `risoInk` pass that wraps a scrolling list (`LazyColumn.risoInk(…)`) does not follow the scroll. It stays where it was first drawn until something redraws the outer pass] → This is an existing riso-paper bug and outside this change. The demo inks its labels one by one instead of the whole list. Screens built on these components (Bookplate Home's library list) must not wrap the list in one pass until the bug is fixed.
- [The placeholder is a tint of content, so it prints as a half-tone] → Raised from 50 % to 70 % after review on device, so its glyphs hold together.
