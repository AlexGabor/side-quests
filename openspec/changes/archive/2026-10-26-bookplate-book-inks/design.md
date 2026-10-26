## Context

Riso's `Colors` holds global `accent` (purple) and `accentVariant` (fluorescent pink). `LocalColors` is internal and
`RisoTheme` always provides `RisoColors`, so apps can't recolor a subtree today. `Button`'s fill is the only use of
`accentVariant`. `FlatButton`, `SmallProgress` and text in `colors.accent` use the accent. All twelve inks in
`Inks.all` are loaded on the press, so `risoInk` prints any of them correctly.

Bookplate's library is Room schema v2 (`bookplate/core/library/impl`). Its api is free of Compose. Home rows, the mini
player (`lastPlayed`) and Player (`book(id)`) all read through Room flows, which re-emit on any write.

## Goals / Non-Goals

**Goals:**
- Each book has a persisted, random ink pair, and Player, its Home row and the mini player print with it.
- A swatch button in Player reassigns the pair, and every screen showing the book updates at once.
- A reusable Riso API for scoped accents and a `SwatchButton` component.

**Non-Goals:**
- Choosing specific inks by hand (no picker sheet).
- Recoloring Player's own `BookCover` placeholder.
- Text selection colors, which stay hard-wired to the global accent in `RisoTheme`.

## Decisions

- **Ink names, not ARGB, in the library.** `BookInks(accent: String, accentVariant: String)` holds `NamedInk.name`
  values.
  - This keeps `core/library/api` free of Compose, and a stored value stays tied to a real press ink.
  - The UI maps a name back with `Inks.named(name)` and falls back to the theme accent when the name is unknown.
  - Alternative: store ARGB values. That would drift if hex approximations change and can't be printed exactly.
- **`BookInkPicker` interface in the library api, bound in `sharedApp`.**
  - The library can't depend on Riso, so `sharedApp`, which already knows both, binds `RisoBookInkPicker`, which takes
    two different inks from `RisoColors.inks.accents`.
  - Player's state injects the same picker for its candidate.
  - Tests use `FakeBookInkPicker` for determinism.
- **Migration 2→3 backfills with the picker.**
  - The columns are added `NOT NULL DEFAULT 'Purple'` / `'Fluorescent Pink'` (today's look).
  - Then each existing row is updated with a picked pair inside the migration, so every book has its own pair from
    first launch.
  - The migration becomes a function of the picker passed to `buildLibraryDatabase`.
  - Alternative: nullable columns assigned lazily by the UI. Rejected, because it scatters writes into the UI layer.
- **`RisoInks(accent, accentVariant, content)`** provides `LocalColors.current.copy(...)`. `LocalColors` stays internal,
  as `design-system.md` requires. Components need no changes.
- **`SwatchButton`** is a new component that shares `Button`'s press and print logic.
  - `ButtonContainer` becomes `internal` and takes `shape` and `fill`.
  - It differs from `Button` in three ways: no content, an explicit ink pair, and `smallShape`.
- **Instant updates come from the flows.** `setInks` is a single `UPDATE`. Room re-emits `books`, `book(id)` and
  `lastPlayed`, and each screen state maps the inks into its UI model. There is no extra event bus.
- **The private `BookInks` helper is duplicated per feature module**, like `BookCover`, because features don't share a
  UI module. Player wraps both the screen and the sheet content, since `BottomSheet` may render outside the screen's
  subtree.

## Risks / Trade-offs

- [The picker may give a pair that is too similar or low-contrast, such as yellow on paper] → Accept for now; tapping
  the swatch rerolls it.
- [Renaming an ink in Riso orphans stored names] → `named()` falls back to the theme accents. Renames should be avoided,
  or migrated.
- [A migration failure would lose the library] → `LibraryMigrationTest` covers v2→v3 from the exported `2.json`.

## Migration Plan

The schema goes from v2 to v3 with the backfill described above. Rolling back an app version needs a reinstall, the
same as for the earlier migration.
