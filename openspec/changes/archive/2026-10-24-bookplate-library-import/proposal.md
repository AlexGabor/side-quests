## Why

Bookplate can now parse audio (`lib/audiometadata`, M4–M6) and reach the user's files (`lib/documentaccess`, M7), but
it has nowhere to keep a book. The Home screen (M9), the Player (M10) and the playback engines (M11–M12) all need one
data core: books stored in Room, built from a picked file or folder, with the library and per-book progress exposed
as flows.

## What Changes

- New core module `bookplate/core/library`, split into `api`, `impl` and `test` (`sidequests.mobile.library`: Android,
  iOS and `jvm()` for tests).
  - `api`: domain models (`Book`, `BookDetails`, `AudioFile`, `Chapter`, `BookId`), `LibraryRepository`,
    `BookImporter` with `ImportProgress` and `ImportResult`. No Room types.
  - `impl`: a Room (KMP) database with the bundled SQLite driver, built by a platform-specific builder (Android,
    iOS, JVM), queries on `CoroutineDispatchers.IO`, the importer, and the Koin module `libraryModule`.
  - `test`: in-memory `FakeLibraryRepository` and `FakeBookImporter` with `fakeLibraryModule`, for M9 and M10.
- **Storage:** books (title, author, narrator, total duration, root source reference, cover file, position, speed,
  imported-at, last-played-at), their audio files (source reference, relative path, size, duration, order, start in
  the book) and chapters with sections. The schema is exported to a committed folder for future migrations.
- **Import** from a picked file or folder: list audio files, natural sort by relative path, parse each with
  `lib/audiometadata`, then assemble one book:
  - title and author from tags (album; album artist or artist; narrator), else the folder or file name;
  - chapters from embedded chapters, else a sidecar `.cue`, else one chapter per file; sub-folders such as `Disc 1`
    become sections;
  - cover from embedded art, else `cover.*` / `folder.*` in the folder, copied into app storage.
  - Progress is reported; the result is success, already in library, no audio found, or failed with a reason.
  - A book whose source is already in the library is refused and nothing changes.
  - The user's files are only ever read.
- **Repository flows:** the library sorted by most recently updated (later of last played and imported), filtered by
  title or author; the last-played book; one book with its files and chapters; updates for position, speed and last
  played (stored now, driven by M11).
- Version catalog: Room 3, KSP and the bundled SQLite driver; KSP and Room plugins declared at the root.
- `bookplate/sharedApp` binds `libraryModule`. Bookplate's UI is unchanged; the M7 debug section stays until M9.

## Capabilities

### New Capabilities
- `bookplate-library`: what the library stores, how a picked file or folder becomes one book (files, order, title,
  author, chapters, sections, cover), the import result and duplicate refusal, the read-only guarantee, and the
  repository's flows and updates.

### Modified Capabilities
<!-- None: no existing spec's requirements change. -->

## Impact

- New: `bookplate/core/library/{api,impl,test}` and their `settings.gradle.kts` includes.
  - `api` depends on `lib/documentaccess/api` (for `DocumentRef` / `PickedDocument`) and coroutines.
  - `impl` depends on `lib/audiometadata`, `lib/coroutine/dispatchers`, Room, the bundled SQLite driver, okio and Koin.
- `gradle/libs.versions.toml` and the root `build.gradle.kts`: Room, KSP and SQLite entries.
- `bookplate/sharedApp`: depends on `core/library/impl` and includes `libraryModule`.
- Pacer and `lib/` modules are unchanged; Pacer's public link parameters are unaffected.
