## Why

Bookplate's Home (library rows, search, mini player) and Player (transport controls, top bar) need controls Riso does not have yet: a thin progress line, a text input for search, and media and list icons. They are general controls that define the look of the design system, so they belong in `design/riso`, and they come first so M9 and M10 can build on them.

## What Changes

- New `SmallProgress` component in `design/riso`: a thin line whose completed part is the accent color at full opacity and whose remaining part is the content color at 50 % opacity.
- New `SearchField` component in `design/riso`: Riso's first text input. Single line, a search icon at the start and a clear action at the end that shows only when there is text.
- Nine new `IconType`s drawn in the existing stroked 24-unit `IconPaths` style: `Play`, `Pause`, `Rewind10`, `Forward10`, `Chapters`, `Speed`, `Add`, `Search`, `Close`.
- `design/risoDemo` gets a components page that shows each new component on Riso's paper, with progress at 0 %, partial and 100 %, next to the existing print playground.
- Riso has a single light palette. No dark theme is added.

## Capabilities

### New Capabilities
- `riso-small-progress`: how the small progress line draws its value, clamps out-of-range values, and reports itself to accessibility.
- `riso-search-field`: the single-line search input, its icon, placeholder, clear action and keyboard action.
- `riso-icons`: the icon set, its shared stroke style and viewport, and the new glyphs.

### Modified Capabilities
<!-- none: riso-paper is unchanged -->

## Impact

- `design/riso` (commonMain components, `IconPaths`, `Icon`; commonTest and jvmTest tests). The public API gains `SmallProgress`, `SearchField` and new `IconType` entries. Nothing existing changes.
- `design/risoDemo`: a second page for the components.
- No changes to Pacer or Bookplate code. Both must keep building, and `IconType` gaining entries must not break any exhaustive `when` in them.
- All existing `design/riso` targets stay: Android, iOS, JVM and wasmJs.
