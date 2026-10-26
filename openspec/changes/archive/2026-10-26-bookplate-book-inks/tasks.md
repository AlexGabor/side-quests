## 1. Riso

- [x] 1.1 Add `Inks.accents` and `Inks.named(name)` in `attributes/Colors.kt`
- [x] 1.2 Add public `RisoInks(accent, accentVariant, content)` providing a copy of `LocalColors`
- [x] 1.3 Make `ButtonContainer` internal with `shape` and `fill` params, keeping `Button` unchanged on top of the local edits
- [x] 1.4 Add `components/SwatchButton.kt` (small shape, explicit ink pair, button semantics) with a preview
- [x] 1.5 Document `RisoInks`, `Inks.accents`/`named` and `SwatchButton` in `design/riso/README.md`

## 2. Library

- [x] 2.1 Add `BookInks`, `Book.inks`, `BookInkPicker` and `LibraryRepository.setInks` in `core/library/api`
- [x] 2.2 Add ink columns to `BookEntity`, bump the database to v3, add the `migration2To3(picker)` backfill, and pass the picker through `buildLibraryDatabase` and the platform builders
- [x] 2.3 Add the DAO update, map the inks in `toBook()`, and implement `setInks`
- [x] 2.4 Inject the picker into `BookImporterImpl` and set the inks on new books; wire it in `LibraryModule`
- [x] 2.5 Export `schemas/.../3.json`
- [x] 2.6 Update the fakes: `fakeBook(inks)`, `FakeLibraryRepository.setInks`, `FakeBookInkPicker`, and bind it in `FakeLibraryModule`
- [x] 2.7 Tests: v2→v3 migration, importer inks, `setInks` re-emits the flows, fake picker

## 3. App binding

- [x] 3.1 Add `RisoBookInkPicker` in `bookplate/sharedApp` and bind it as `BookInkPicker`, with a test that pairs are two different accents

## 4. Player

- [x] 4.1 Add `inks` to `PlayerUi`, an injected picker, a saved candidate pair and `onInksClick` in `PlayerScreenState`
- [x] 4.2 Wrap the Player content and sheets in a private `BookInks` helper; add the 24 dp `SwatchButton` before "Chapters"
- [x] 4.3 Tests in `PlayerScreenStateTest` for the inks in the UI and for `onInksClick`

## 5. Home

- [x] 5.1 Add `inks` to `BookRow` and `MiniPlayerUi`
- [x] 5.2 Wrap `BookRowItem` and `MiniPlayer` in a private `BookInks` helper; give the placeholder a 40 % `accentVariant` background and an `accent` border
- [x] 5.3 Tests in `HomeStateTest` and `MiniPlayerStateTest` for the inks and updates after `setInks`

## 6. Docs and verification

- [x] 6.1 Update `bookplate/CLAUDE.md` (schema v3) and the `bookplate/README.md` feature list
- [x] 6.2 Run `./gradlew :bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest :bookplate:feature:home:jvmTest :bookplate:feature:player:jvmTest :bookplate:sharedApp:jvmTest`
- [x] 6.3 Verify on Android, the platform the user chose:
  - after the migration, each row has its own inks and the placeholders are tinted;
  - Player prints in the book's inks;
  - the swatch recolors at once and rerolls;
  - the row and the mini player follow.
