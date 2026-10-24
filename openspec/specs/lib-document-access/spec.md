# lib-document-access Specification

## Purpose
App-agnostic access to files and folders the user picks: picking from Compose, persistable references with a string form, recursive listing, random-access reading as a `lib/audiometadata` `ByteSource`, reachability (missing vs access lost), read only, with a fake for tests. Implemented in `lib/documentaccess` over Android's Storage Access Framework and iOS security-scoped bookmarks.

## Requirements
### Requirement: Picking a file or a folder
`lib/documentaccess` SHALL let an app, from Compose, ask the user to pick either a single file or a folder through
the platform picker, and SHALL hand back a `DocumentRef` for the pick together with its display name, or nothing when
the user cancels. A file pick MAY be narrowed to MIME types. The picked item SHALL be opened in place, never copied
into the app.

- Android: `ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`, taking a persistable read permission on the result.
- iOS: `UIDocumentPickerViewController` opened in place, keeping a bookmark of the picked URL.

#### Scenario: Picking a folder
- **WHEN** the app launches a folder pick and the user chooses a folder
- **THEN** the result holds a folder reference and the folder's name

#### Scenario: Picking a file
- **WHEN** the app launches a file pick filtered to `audio/*` and the user chooses a file
- **THEN** the result holds a file reference and the file's name

#### Scenario: Cancelling
- **WHEN** the user dismisses the picker without choosing
- **THEN** the result is empty and nothing is granted

### Requirement: Persistable references
A `DocumentRef` SHALL identify a picked file or folder, or a file listed inside a picked folder, and SHALL keep
working after the app restarts for as long as the platform keeps the grant. It SHALL convert to a single string and
back without loss, and two references to the same item made the same way SHALL be equal. A string that is not a
reference SHALL parse to nothing rather than throw.

#### Scenario: Round trip through a string
- **WHEN** a reference is converted to a string and parsed back
- **THEN** the parsed reference equals the original, including whether it is a folder

#### Scenario: Not a reference
- **WHEN** an arbitrary string such as `hello` is parsed
- **THEN** no reference is returned

#### Scenario: After a restart
- **WHEN** the app stores a picked reference's string, is restarted and parses it again
- **THEN** the reference can still be listed and read without picking again

### Requirement: Listing a folder
Listing a folder reference SHALL return every file below it, recursively, each with its own reference, its path
relative to the picked folder (segments joined by `/`), its name, its size in bytes and its last-modified time when
the platform knows them. Folders themselves are not returned; hidden files (names starting with `.`) are skipped.
Listing a file reference SHALL return that one file, with its name as the relative path. Android SHALL query
`DocumentsContract` children directly rather than walking `DocumentFile`s.

#### Scenario: Nested folder
- **WHEN** a picked folder `Book` holds `01.mp3` and `Disc 2/02.mp3`
- **THEN** listing it returns `01.mp3` and `Disc 2/02.mp3` with their names, sizes and last-modified times

#### Scenario: Single file
- **WHEN** a file reference to `book.m4b` is listed
- **THEN** the result is one entry, `book.m4b`

#### Scenario: Unreachable folder
- **WHEN** a folder reference that is missing or no longer accessible is listed
- **THEN** listing fails with an error that says which of the two it is

### Requirement: Reading a reference
A file reference SHALL open as a `lib/audiometadata` `ByteSource` that also must be closed. Reads SHALL be random
access and bounded: a read at a position returns at most the requested number of bytes, fewer at the end, none at or
past the end, and never loads the whole file. On iOS the security scope of the picked item SHALL be held while the
source is open and released when it is closed.

#### Scenario: Random access
- **WHEN** a 100-byte file is opened and 10 bytes are read at position 95
- **THEN** 5 bytes are returned, and a read at position 100 returns none

#### Scenario: Parsing through the source
- **WHEN** an audio file reference is opened and passed to `AudioMetadataReader`
- **THEN** its format and duration are read without the file being copied

#### Scenario: Opening an unreachable file
- **WHEN** a missing or no-longer-accessible file reference is opened
- **THEN** opening fails with an error that says which of the two it is

### Requirement: Reachability
The library SHALL report, for any reference, whether it is reachable, missing (deleted, moved or renamed away), or
access-lost (the platform no longer grants the app access). Checking SHALL not throw.

#### Scenario: Deleted file
- **WHEN** a listed file is deleted and its reference is checked
- **THEN** it is reported missing

#### Scenario: Renamed picked item on iOS
- **WHEN** on iOS the picked file or folder itself is renamed or moved on the same volume
- **THEN** its bookmark follows it and it stays reachable at its new location

#### Scenario: Renamed file in a folder
- **WHEN** a file inside a picked folder is renamed and its old reference is checked
- **THEN** it is reported missing

#### Scenario: Grant lost
- **WHEN** the app's access to a picked folder is revoked and a reference under it is checked
- **THEN** it is reported access-lost, not missing

### Requirement: Read only
The library SHALL never create, write, move, rename or delete the user's files or folders, and SHALL request read
access only.

#### Scenario: Read-only grant
- **WHEN** the user picks a file or folder on Android
- **THEN** only a read permission is taken and persisted

### Requirement: Off the main thread
Listing, opening and reachability checks SHALL run on `CoroutineDispatchers.IO`.

#### Scenario: Listing from the main thread
- **WHEN** a listing is started from a coroutine on the main dispatcher
- **THEN** the queries run on the IO dispatcher and the caller resumes with the result

### Requirement: Test double
`lib/documentaccess/test` SHALL provide a fake document access backed by an in-memory tree of files and folders. A test
SHALL be able to add files and folders, queue the result of the next pick, grant a reference directly, delete or
rename items, and revoke a grant, and SHALL see the same listing, reading and reachability behaviour as on a device. On
the JVM it SHALL be able to load a directory from disk into the tree. A Koin module SHALL bind it.

#### Scenario: Picking in a test
- **WHEN** a test queues the folder `Books/Dune` and launches a folder pick
- **THEN** the pick returns a folder reference named `Dune` whose listing holds the files under `Books/Dune`

#### Scenario: Revoking in a test
- **WHEN** a test revokes the grant on `Books/Dune`
- **THEN** references under it are reported access-lost
