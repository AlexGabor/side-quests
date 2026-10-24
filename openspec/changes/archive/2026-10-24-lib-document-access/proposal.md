## Why

Bookplate builds its library from audio files the user already has, so it has to reach files and folders it does not own,
keep that access across restarts, and read the files randomly for the parsers in `lib/audiometadata`. Both phones gate
this behind their own pickers and grants (Android's Storage Access Framework, iOS security-scoped URLs), and M8's
import and M14's relink need one common way to use them.

## What Changes

- New app-agnostic module `lib/documentaccess`, split into `api`, `impl` and `test` (`sidequests.compose.mobile.library`
  for `api` and `impl`, `sidequests.mobile.library` for `test`: Android, iOS and `jvm()` for tests, no `wasmJs`).
- **Picking**, launchable from Compose: the user picks a single file (optionally filtered by MIME type) or a folder.
  The result is a `DocumentRef` the app keeps.
  - Android: `OpenDocument` / `OpenDocumentTree` with `takePersistableUriPermission`.
  - iOS: `UIDocumentPickerViewController` (opened in place, not copied) and a bookmark of the picked URL.
- **References:** a `DocumentRef` survives app restarts and converts to and from a single string, so it can be stored
  in Room (M8). Files listed inside a picked folder get their own references.
- **Listing:** a folder reference lists every file under it, recursively, with its reference, relative path, name,
  size and last-modified time. Listing a file reference gives that one file.
  - Android: `DocumentsContract` child-document queries.
  - iOS: enumeration inside the folder's security scope.
- **Reading:** a reference opens as a `lib/audiometadata` `ByteSource` with random-access, bounded reads, which must be
  closed. On iOS the security scope is held for as long as the source is open.
- **Reachability:** a reference reports whether it is reachable, missing (deleted, moved or renamed), or no longer
  accessible (the grant was lost). M14 relink relies on telling these apart.
- Read only: the library never writes, moves or deletes the user's files.
- I/O runs on `CoroutineDispatchers.IO`.
- `lib/documentaccess/test`: an in-memory fake (files, folders, grants, queued picks, deletes, renames, revoking
  access), plus a JVM helper that loads a directory from disk into it, for M8's import tests.
- **Temporary debug entry** on Bookplate's Home placeholder: pick a file or a folder, see the audio files found with
  their sizes and durations parsed by `lib/audiometadata`, and their reachability. Picks are saved and listed again
  after a restart. It is marked temporary and M9 removes it.
- `bookplate/CLAUDE.md` documents how to put sample audio on the emulator and simulator (M9's Maestro flows need it).

## Capabilities

### New Capabilities
- `lib-document-access`: picking files and folders, persistable references and their string form, recursive listing,
  random-access reading as a `ByteSource`, reachability, the read-only guarantee, and the test fake.

### Modified Capabilities
- `bookplate-navigation`: Home's placeholder gains a temporary document-access debug section next to its existing
  "Open player" control (removed again in M9).

## Impact

- New: `lib/documentaccess/{api,impl,test}` and their `settings.gradle.kts` includes.
  - `api` depends on `lib/audiometadata` (for `ByteSource`).
  - `impl` depends on `lib/coroutine/dispatchers`, Koin and `androidx.activity:activity-compose` on Android.
- `bookplate/sharedApp`: binds `documentAccessModule`, adds the debug section to `Placeholders.kt` and a small DataStore
  file to keep the debug picks. Bookplate gains no permissions: SAF and the iOS picker need none, and the app stays
  offline.
- `bookplate/CLAUDE.md`: sample-audio instructions.
- Pacer and other `lib/` modules are unchanged; Pacer's public link parameters are unaffected.
