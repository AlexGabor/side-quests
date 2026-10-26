## Context

`PanelInfo` (common, `core/player/impl`) feeds Android's `PanelPlayer` (Media3 metadata, `setArtworkUri(cover)`) and
iOS's `NowPlayingPanel` (`MPMediaItemArtwork` from the cover). A coverless book sends no artwork. `PlayerBook` is
built once per load from `BookDetails`, by `EngineBookPlayer`, by `FakeBookPlayer`, and in `PlayerScreenState`.
`core/player` doesn't depend on Riso, and books store ink names, not colors.

## Goals / Non-Goals

**Goals:**
- Coverless books get panel artwork printed in their inks on Android and iOS, so the system colors the panel from
  them.
- Ink changes reach the panel while the book stays loaded.

**Non-Goals:**
- Recoloring books that have a cover. The cover is shown and the system colors the panel from it.
- Setting the notification color with `setColor`/`setColorized`, which Android 13+ ignores for media.
- The Riso print effect (halftone, misregistration) in the artwork. It is drawn flat.

## Decisions

- **Ink names become colors through an injected `PanelInkColors`.** The lookup returns `PanelInks(paper, accent,
  accentVariant)` as ARGB `Int`s and lives in `core/player/impl` common. `sharedApp` binds it from
  `RisoColors`.
  - This keeps `core/player` free of Riso and Compose, the same way the library's `BookInkPicker` works.
- **Artwork is drawn with each platform's own 2D API**, from one common description: the layout constants and
  `PanelInks`.
  - Android draws with `android.graphics` into a 512 px `Bitmap`, encodes it as PNG, and passes it to
    `setArtworkData(bytes, PICTURE_TYPE_FRONT_COVER)`, keeping `setArtworkUri(null)`.
  - iOS draws a `UIImage` with `UIGraphicsImageRenderer` and wraps it in `MPMediaItemArtwork`.
  - Alternative: render with Compose in common and encode. Rejected, because it needs a font resolver and density
    outside composition, and per-platform encoding anyway.
- **Caching:** each panel keeps the last (letter, inks) key and its artwork. A `getState`/`show` call with the same
  key reuses it.
- **The engine follows ink changes.** While loaded, a job collects `repository.book(id)`, mapped to the book's inks
  with `distinctUntilChanged`, and updates `state.book.inks` through the command queue. Loading another book or
  clearing cancels the job. `FakeBookPlayer` does the same. `PanelInfo.inks` makes `PanelUpdates` and Android's
  `distinctUntilChangedBy` see the change.
- **Font:** the platform's bold monospace (Android `Typeface.MONOSPACE`, iOS `monospacedSystemFont`), close to Fira
  Code. Bundling Fira Code into `core/player` isn't worth it.

## Risks / Trade-offs

- [The system's palette extraction from a pale 40 % wash may come out muted] → The accent border and the wash still
  carry the hue. The look matches the library placeholder by request.
- [A large PNG over the Media3 session] → 512 px of flat color encodes to a few KB.
