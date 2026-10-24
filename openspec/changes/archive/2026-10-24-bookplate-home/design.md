## Context

M8 gave Bookplate `core/library`: `LibraryRepository.books(query)` (sorted by `Book.updatedAt`, filtered by title or
author), `BookImporter.import(PickedDocument, onProgress)` with `Listing` / `Reading(done, total)` / `Saving` progress
and `Success` / `AlreadyInLibrary` / `NoAudioFound` / `Failed(reason)` results, and fakes in `core/library/test`.
`libraryModule` (Room, bundled SQLite) is already bound in `sharedApp`, but nothing on screen has resolved it, so it
has never opened on a device. M7 gave `lib/documentaccess`: `DocumentPicker.rememberLauncher` with `pickFile` /
`pickFolder`, faked by `FakeDocumentAccess`. M2–M3 gave Riso `SearchField`, `SmallProgress`, the `Add` / `Close`
icons and `BottomSheet`.

Home is still `HomePlaceholder` in `sharedApp/Placeholders.kt`, carrying the M7 debug section (`sharedApp/debug/`,
backed by DataStore). `pacer/feature/home` is the model for a feature module, except its dependency on `test` modules
from `commonMain` (a known deviation, not to copy).

## Goals / Non-Goals

**Goals:**

- `bookplate/feature/home` with a tested state holder and the Home screen of `bookplate-home`.
- Add a book from a device through the system picker, end to end through Room, on Android and iOS.
- Remove every trace of the M7 debug section.

**Non-Goals:**

- The mini player (M10); only its slot.
- Unavailable/missing books and relinking (M14), deleting or editing books, thumbnails and large-library tuning (M15).
- Keeping an import running when Home leaves composition or the process dies (M15 looks at process death).

## Decisions

### Module

`bookplate/feature/home`, plugin `sidequests.compose.mobile.library` (Android, iOS, `jvm()` for tests), package
`com.alexgabor.bookplate.feature.home`. `commonMain` depends on `design/riso`, `core/library/api`,
`lib/documentaccess/api`, `lib/coroutine/dispatchers`, Koin Compose, okio and Compose resources. Test modules
(`core/library/test`, `lib/documentaccess/test`, `lib/coroutine/test`) are `commonTest` only. Previews take explicit
sample data rather than fakes, so `commonMain` never sees a `test` module.

Public API: `HomeScreen(onOpenBook: (BookId) -> Unit, modifier, bottomBar: @Composable () -> Unit = {}, state =
rememberHomeState(), picker: DocumentPicker = koinInject())`. `sharedApp` maps `BookId` to
`BookplateDestination.Player(id.value.toString())`.

### State holder

`HomeState(scope, repository, importer, query)` per `architecture.md`; `rememberHomeState()` injects the repository
and importer with `koinInject()`, takes `rememberCoroutineScope()`, and uses `rememberSaveable` with a saver that keeps
the query and whether the add sheet is open. Exposed as read-only `State`:

- `query: State<String>`; `onQueryChange(String)`.
- `library: State<Library>`: `Loading`, `Empty`, `NoResults(query)` or `Books(rows)`. It is collected in `init` from
  a `MutableStateFlow` of the query through `flatMapLatest { repository.books(it) }`. A `MutableStateFlow` rather
  than `snapshotFlow`, so the holder works in plain JVM tests without a Compose snapshot observer.
- `rows` are `BookRow(id, title, author, duration, progress, percent, coverPath)` with formatting done in the holder
  (`durationText`, `percentText`), so tests check the text the screen shows.
- `addSheetOpen: State<Boolean>`; `onAddClick()`, `onAddSheetDismissed()`.
- `importStatus: State<ImportStatus?>`: `Running(name, step)` with `step` as text and an optional fraction, or
  `Finished(message, isError)`. `onPicked(PickedDocument?)` ignores `null` and ignores picks while running; otherwise
  launches the import in `scope`. `onProgress` is called from the importer's IO dispatcher, so it hands each step back
  into `scope` with `scope.launch` before writing state. For success and already-in-library, the title comes from
  `repository.book(id).first()`, falling back to the pick's name. `onStatusClosed()` clears a finished status.

The import runs in the composition's scope: navigating to Player cancels a running import (Room's transaction rolls
back, so the library is untouched). Imports take a moment and start from Home, so that is acceptable for M9.

### Screen layout

`Column(safeDrawing)`: `Toolbar` with "Bookplate"; a `Row` with `SearchField` (weight 1) and
`FlatButton(IconType.Add, "Add book")`; the import status (body text, `SmallProgress` while reading, a close
`FlatButton` once finished); then a `LazyColumn` (weight 1) of rows keyed by book id, or the empty/no-results text;
then `bottomBar()`. Content width is capped at `RisoTheme.dimens.contentMaxWidth`.

Rows are full-width clickable rows separated by a printed rule, with the cover (64 dp square) on the start side and the
text and `SmallProgress` on the other. Not Riso `Card`s: a `Card` prints everything inside it through its ink pass,
which would tint the cover, and a knockout would show bare paper instead of the image. In a row only the text, the
rule and the placeholder carry `risoInk`, so the cover stays outside every pass. Errors are printed in the accent ink
so they stand out without a new color.

The add choice is a Riso `BottomSheet` with two `Button`s; choosing one calls `dismiss()` and then the launcher.
`pickFile` asks for `audio/*`; on the Android emulator and the iOS simulator `Single.m4b` is selectable with it, so
there is no `*/*` fallback.

### Covers

`Book.coverPath` is an absolute path in app storage. `BookCover` reads it with okio's `FileSystem.SYSTEM` on
`CoroutineDispatchers.IO` and decodes with Compose resources' `ByteArray.decodeToImageBitmap()`, inside
`produceState` keyed by the path. It is drawn with `Image` and `ContentScale.Crop`, without `risoInk`, since covers are
plain images. With no ink to thin, a cover would hold full strength through Riso fades, so it takes `risoFadeAsAlpha()`:
the enclosing `risoFade` of the navigation crossfade or the bottom sheet's backdrop becomes its alpha. A missing or undecodable file shows the placeholder: a bordered square in content ink with the title's
first letter. Thumbnails are M15.

*Alternative:* an image-loading library (Coil 3). Rejected for now: one dependency for a handful of local files;
M15 revisits with thumbnails.

### Removing the debug section

Delete `sharedApp/src/*/kotlin/com/alexgabor/bookplate/debug/`, `debugPickStoreModule` from `sharedModule`, the
`datastore-preferences-core`, `okio` and `lib/coroutine/dispatchers` dependencies of `sharedApp` (only the debug
section used them), the debug test in
`RootNavigationTest`, and the debug bullet in `bookplate/CLAUDE.md`. The DataStore file already written on test
devices stays unread; it is not worth code to delete it.

### Navigation

`RootNavigation`'s Home entry calls `HomeScreen(onOpenBook = { nav.open(Player(it.value.toString())) })`.
`SampleBookId` and `HomePlaceholder` go; `PlayerPlaceholder` stays in `Placeholders.kt` until M10.
`RootNavigationTest` binds `fakeLibraryModule` and `fakeDocumentAccessModule`, seeds a book, and opens it.

### Maestro and sample books

`sample-audio.sh` already pushes `Sample Book/` (two MP3s in sections) and `Single.m4b` to where the pickers see
them; the folder book gets `album_artist=Sample Author`. Flows drive the system picker by visible text:

- Android (DocumentsUI): open the drawer or path, go to `Audiobooks`, then "Use this folder" / "Allow" for a folder,
  or tap the file.
- iOS: the picker opens on Recents; tap "Browse", "On My iPhone", "Audiobooks", then the item, then "Open" for a
  folder.

Picker steps differ per platform, so they live in `bookplate/maestro/subflows` with `runFlow` blocks under
`when: platform:` conditions, and flows call them. Flows start
with `clearState: true` so the library is empty; on iOS `clearState` reinstalls data but the sample files live in
the Files provider, so they survive.

## Risks / Trade-offs

- [Room on a device for the first time: KSP-generated constructor or the bundled driver failing at runtime on iOS or
  Android] → Verify on both by adding a book, then restarting the app; fix in `core/library/impl` if needed and note it.
- [System picker UI text varies by OS version] → Flows use the texts seen on the current emulator/simulator images;
  `bookplate/CLAUDE.md` records which.
- [Large covers decoded at full size] → Acceptable for M9's handful of books; thumbnails in M15.
- [Import cancelled by leaving Home] → Documented non-goal; nothing half-written thanks to the transaction.
