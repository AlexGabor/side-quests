## Why

Bookplate can store and import books (`bookplate/core/library`, M8) and reach the user's files
(`lib/documentaccess`, M7), but Home is still the M1 placeholder with the M7 debug section on it. M9 makes Home the
first real screen: the user's library, search, and adding books. It is also the first time the Room database runs on
a device.

## What Changes

- New feature module `bookplate/feature/home` (`sidequests.compose.mobile.library`), depending only on
  `bookplate/core/library/api`, `lib/documentaccess/api`, `lib/coroutine/dispatchers` and `design/riso`. The Home
  placeholder moves out of `sharedApp` into it.
  - A `HomeState` state holder in the style of `architecture.md`: the search query (saved across recreation), the
    library as rows, the empty / no-results states, the add choice and the running or finished import.
  - The library is a list of rows: cover as a plain image (a Riso placeholder when the book has none), title,
    author, total duration, percent progress with `SmallProgress`. Sorted by recently updated, as the repository
    already returns it.
  - A `SearchField` at the top filters by title or author; next to it an add button opens a choice of folder or
    single file, then the system picker, then runs the import, showing progress and the result: added, already in
    library, no audio found, or failed.
  - Empty library and no-results states.
  - Tapping a row opens `Player(bookId)`, still a placeholder (M10).
  - A bottom-bar slot is left for the mini player (built in M10); nothing fills it yet.
- `bookplate/sharedApp`: Home's entry renders `feature/home`; the sample book id and "Open player" control go.
- **Removal** of the temporary M7 document access debug section: `sharedApp/.../debug/` (all source sets), the
  `debugPickStoreModule` binding, the DataStore dependency in `sharedApp`, its test and its lines in
  `bookplate/CLAUDE.md`.
- `bookplate/scripts/sample-audio.sh`: the sample folder book gets an album-artist tag so rows show an author and the
  search-by-author flow has something to match.
- Maestro flows in `bookplate/maestro/flows`: add a book through the system picker, refuse the same book again, search,
  open a book. `bookplate/CLAUDE.md` documents how they get sample books and drive the picker.
- README feature list updated.

## Capabilities

### New Capabilities
- `bookplate-home`: the Home screen — library rows (cover, title, author, duration, progress), ordering, search,
  adding a book with progress and results, empty and no-results states, opening a book, and the space reserved for
  the mini player.

### Modified Capabilities
- `bookplate-navigation`: Player is opened from a book row on Home instead of a placeholder "Open player" control;
  the temporary document access debug requirement is removed.

## Impact

- New: `bookplate/feature/home` and its `settings.gradle.kts` include. Dependencies: `design/riso`,
  `bookplate/core/library/api`, `lib/documentaccess/api`, `lib/coroutine/dispatchers`, Koin Compose, okio and Compose
  resources (to read and decode the cover file). Tests use `bookplate/core/library/test`,
  `lib/documentaccess/test` and `lib/coroutine/test`.
- `bookplate/sharedApp`: depends on `feature/home`; loses `debug/`, `datastore-preferences-core` (and okio, if
  nothing else uses it); `RootNavigation` and its test change. `libraryModule` (the real Room library) is already
  bound and stays.
- `bookplate/maestro/flows`, `bookplate/scripts/sample-audio.sh`, `bookplate/CLAUDE.md`, `bookplate/README.md`.
- Pacer, `design/` and `lib/` are unchanged; Pacer's public link parameters are unaffected.
