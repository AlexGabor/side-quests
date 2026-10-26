## Why

Every Bookplate book prints in the same global Riso accents, purple and fluorescent pink, so nothing tells one book
from another at a glance. Giving each book its own random pair of inks makes the library feel like a shelf of
differently printed books. A one-tap shuffle in Player lets the user pick a pair they like.

## What Changes

- Each book stores its own pair of Riso inks, an accent and an accent variant. An imported book gets a random pair.
  Existing books get a random pair when the database migrates from version 2 to version 3.
- Player, the book's library row and the mini player print in that book's inks instead of the global accents. The
  player's sheets do too.
- Player's top bar gets a swatch button to the left of "Chapters". It shows a random candidate pair. Tapping it gives
  the book that pair, and the button draws a new candidate. The change shows at once everywhere the book appears.
- The library row's cover placeholder for books without a cover gets a background in the book's accent variant at
  40 % opacity, and its border is now in the book's accent.
- Riso gains three things:
  - a public way to print a subtree with other accents (`RisoInks`);
  - `Inks.accents` and `Inks.named(name)`, for picking and looking up inks;
  - a new `SwatchButton` component: a content-less, small-shaped button printed in a given ink pair.

## Capabilities

### New Capabilities
- `riso-inks`: printing part of the UI with a given accent pair, the list of accent inks, and lookup by name.
- `riso-swatch-button`: `SwatchButton`, a content-less button that previews an ink pair.

### Modified Capabilities
- `bookplate-library`: books store an ink pair; database version 3 with migration; set a book's inks; the test
  doubles support inks.
- `bookplate-home`: library rows and the mini player print in the book's inks; the placeholder gets a tinted
  background and an accent border.
- `bookplate-player`: the top bar has the ink swatch button; Player and its sheets print in the book's inks.

## Impact

- `design/riso`: `attributes/Colors.kt`, new `RisoInks`, new `components/SwatchButton.kt`, `components/Button.kt`
  (its container is shared), `README.md`.
- `bookplate/core/library/api`: `Book.inks`, `BookInks`, `BookInkPicker`, `LibraryRepository.setInks`.
- `bookplate/core/library/impl`:
  - `BookEntity` columns, Room schema v3 with a 2→3 migration, a DAO update, `BookImporterImpl`, and the database
    builders.
- `bookplate/core/library/test`: fakes and `fakeBook`.
- `bookplate/feature/player` and `bookplate/feature/home`: UI state and screens.
- `bookplate/sharedApp`: binds the Riso-backed `BookInkPicker`.
- No external dependencies are added.
