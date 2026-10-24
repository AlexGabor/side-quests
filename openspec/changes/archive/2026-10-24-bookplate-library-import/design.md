## Context

M4–M6 gave Bookplate common-Kotlin parsers (`lib/audiometadata`) that read tags, durations, covers, chapters and
`.cue` sheets from a `ByteSource`. M7 gave it `lib/documentaccess`: a picked file or folder becomes a persistable
`DocumentRef`, folders list recursively, files open as `ByteSource`s, and a fake runs the same behaviour on the JVM.
Nothing stores a book yet. M9 (Home) lists and imports books, M10 (Player) and its mini player read one book and the
last-played one, M11 saves position, speed and last played, and M14 relinks books whose files moved.

`pacer/core/settings/{api,impl,test}` is the model for a core module: an interface in `api`, an implementation plus an
`expect val` storage module in `impl` with one `actual` per platform, a fake in `test`, IO on
`CoroutineDispatchers.IO`, binding in `sharedApp`.

## Goals / Non-Goals

**Goals:**

- A Room schema that holds everything M9–M14 need about a book, exported for migrations.
- One import path from a `PickedDocument` to a stored book, tested on the JVM with real fixture audio.
- Flows for the library, search, the last-played book and one book; updates for playback state.
- Fakes good enough for M9 and M10's state-holder tests.

**Non-Goals:**

- UI (M9), playback or the timeline maths beyond each file's start (M11), relinking or reachability (M14).
- Deleting or editing books, re-importing changed folders, thumbnails (M15).
- Refreshing stale iOS bookmarks.

## Decisions

### Room 3 with the bundled driver

Room 3 (`androidx.room3`, 3.0.3) is the KMP-first line of Room; it only talks to SQLite through `SQLiteDriver`, so
Android, iOS and the JVM share one code path. The bundled driver (`androidx.sqlite:sqlite-bundled` 2.7.1) ships the
same SQLite everywhere and runs on the JVM for tests. KSP 2.3.8 (already in the catalog for build-logic) runs the Room
compiler per target: `kspAndroid`, `kspJvm`, `kspIosArm64`, `kspIosSimulatorArm64`.

*Alternative:* Room 2.8 KMP. Rejected: Room 3 is the current line and is what later Room fixes land in; the API is
the same shape (`@ConstructedBy`, `RoomDatabaseConstructor`, `setDriver`).

The Room and KSP plugins are applied in `core/library/impl`'s build file rather than through a new convention plugin:
it is the only Room module, and a plugin can be extracted when a second one appears.

Schema JSON goes to `bookplate/core/library/impl/schemas/` (committed), via the Room Gradle plugin's
`schemaDirectory`.

### Database builders per platform

`impl` has `internal expect val libraryStorageModule: Module`, as Pacer's `settingsStoreModule`:

- Android: `Room.databaseBuilder<LibraryDatabase>(context, getDatabasePath("library.db"))`, covers in
  `filesDir/covers`.
- iOS: `Application Support/library.db`, covers in `Application Support/covers`.
- JVM: only tests run there, so an in-memory database and a temporary covers directory.

Each sets `BundledSQLiteDriver()` and `setQueryCoroutineContext(CoroutineDispatchers.IO)`. Koin builds the database
lazily, so the dispatcher is read when the work starts. The repository also wraps writes in
`withContext(CoroutineDispatchers.IO)` and flows in `flowOn(CoroutineDispatchers.IO)`.

### Public API (`com.alexgabor.bookplate.core.library`)

```kotlin
@JvmInline value class BookId(val value: Long)

data class Book(
    val id: BookId, val title: String, val author: String?, val narrator: String?,
    val duration: Duration, val coverPath: String?,      // absolute path of the copy in app storage
    val source: DocumentRef,
    val position: Duration, val speed: Float,
    val importedAt: Instant, val lastPlayedAt: Instant?,
) { val updatedAt: Instant }                              // later of lastPlayedAt and importedAt

data class AudioFile(
    val ref: DocumentRef, val relativePath: String, val size: Long?,
    val duration: Duration, val index: Int, val start: Duration, // start in the book's timeline
)

data class Chapter(
    val title: String?, val start: Duration, val end: Duration,  // in the book's timeline
    val children: List<Chapter> = emptyList(),                   // non-empty for a section
) { val isSection: Boolean }

data class BookDetails(val book: Book, val files: List<AudioFile>, val chapters: List<Chapter>)

interface LibraryRepository {
    fun books(query: String = ""): Flow<List<Book>>
    val lastPlayed: Flow<Book?>
    fun book(id: BookId): Flow<BookDetails?>
    suspend fun setPosition(id: BookId, position: Duration)
    suspend fun setSpeed(id: BookId, speed: Float)
    suspend fun setLastPlayed(id: BookId, at: Instant)
}

interface BookImporter {
    suspend fun import(document: PickedDocument, onProgress: (ImportProgress) -> Unit = {}): ImportResult
}

sealed interface ImportProgress { Listing; Reading(done, total); Saving }
sealed interface ImportResult {
    Success(bookId); AlreadyInLibrary(bookId); NoAudioFound; Failed(reason: FailureReason, message: String?)
}
enum class FailureReason { SourceMissing, AccessLost, Unreadable }
```

`api` depends on `lib/documentaccess/api` for `DocumentRef` and `PickedDocument`; M14 needs the reference as a value,
not a string. Import takes a `PickedDocument` because a `DocumentRef` carries no display name, and the folder name is
the title fallback. Chapter titles stay nullable (embedded chapters may have none); the UI picks a label.

`setLastPlayed` takes the time from the caller (M11), so the repository needs no clock; the importer gets a
`kotlin.time.Clock` for `importedAt`, `Clock.System` in Koin and a fixed clock in tests.

### Schema (version 1)

```
books        id INTEGER PK autoincrement, source_ref TEXT UNIQUE, fingerprint TEXT (indexed),
             title TEXT, author TEXT?, narrator TEXT?, duration_ms INTEGER, cover_file TEXT?,
             position_ms INTEGER, speed REAL, imported_at INTEGER, last_played_at INTEGER?
audio_files  id PK, book_id → books ON DELETE CASCADE, ordinal INTEGER, source_ref TEXT,
             relative_path TEXT, size INTEGER?, duration_ms INTEGER, start_ms INTEGER;
             UNIQUE (book_id, ordinal)
chapters     id PK, book_id → books CASCADE, parent_id → chapters CASCADE (null at top level),
             ordinal INTEGER (pre-order across the book), title TEXT?, start_ms INTEGER, end_ms INTEGER;
             INDEX (book_id, ordinal), INDEX (parent_id)
```

Times are epoch milliseconds and durations milliseconds, so no type converters are needed. A book with its files and
chapters is written in one transaction; the tree is rebuilt from `parent_id` and `ordinal` when read.

### Covers stored by file name

The cover row holds a file name (`<uuid>.<ext>`) under the covers directory, and the repository maps it to an absolute
path. iOS moves the app container on updates, so absolute paths must not be stored. The cover is written with okio
before the book row is inserted; if the insert fails (or loses a duplicate race), the file is deleted again. The
extension comes from the MIME type (`jpg`, `png`, `webp`, `gif`, `bmp`) or, for a folder image, its own extension.

### Duplicates: reference or fingerprint

The spec refuses "the same source". A `DocumentRef` string is stable for re-picks on Android (tree URIs) and in the
fake, but on iOS each pick creates new bookmark data, so the same folder can produce a different string. Import
therefore also stores a fingerprint: an FNV-1a 64-bit hash of the picked name plus each audio file's relative path and
size, in natural order. Either match refuses the import. Both checks run before any file is parsed, and the unique
index on `source_ref` catches a concurrent duplicate.

*Alternative:* only the reference. Rejected because the refusal would silently not work on iOS.

### Assembling a book

1. Look up `source_ref`; if found → `AlreadyInLibrary`.
2. `list(ref)`; `DocumentUnavailableException` → `Failed(SourceMissing | AccessLost)`.
3. Keep entries with audio extensions, natural sort; none → `NoAudioFound`. Fingerprint check.
4. Read each file (`documentAccess.read(ref) { AudioMetadataReader.read(it) }`), reporting `Reading(i, n)`. Files the
   parser doesn't recognise are dropped; if none remain → `NoAudioFound`. An open or read failure →
   `Failed(Unreadable)` naming the file.
5. Read each `.cue` in a folder that holds an audio file without embedded chapters; pair by stem, else by a `FILE`
   entry naming the audio file.
6. Build the timeline, chapters and sections (directories after stripping the prefix all files share), title, author,
   narrator and cover, then save in one transaction.

Natural sort compares path segments; within a segment, digit runs compare by value (leading zeros ignored, then fewer
zeros first) and text case-insensitively, with the raw string as the last tie-break.

### Filtering in Kotlin, sorting in SQL

The library query orders by `MAX(imported_at, IFNULL(last_played_at, 0)) DESC, imported_at DESC, id DESC`. The
title/author filter runs on the mapped list with `contains(ignoreCase = true)`: SQLite's `LIKE` and `lower()` only fold
ASCII without ICU, and the bundled driver has none. A library is hundreds of rows at most (M15 can revisit).

### Fakes

`FakeLibraryRepository` keeps `BookDetails` in a `MutableStateFlow` and has `add(...)` to seed books.
`FakeBookImporter` returns queued `ImportResult`s (and progress) and, for `Success`, adds a queued book to the fake
repository. `fakeLibraryModule` binds both.

### Tests

`impl/jvmTest` runs the real importer and Room (in-memory, bundled driver) against `FakeDocumentAccess`. The
`lib/audiometadata` fixtures are added as a test resource folder of `impl`, so files are reused rather than copied;
small variants (ID3v1 tags with chosen titles on the ID3v1-only MP3) are made in the test by rewriting its last
128 bytes.

## Risks / Trade-offs

- [Room 3 / KSP with Kotlin 2.4 and AGP 9's KMP Android plugin may not cooperate] → Verified by building both apps and
  the JVM tests; if KSP can't see the `android` target, fall back to registering `kspAndroid` explicitly.
- [Fingerprint refuses a genuinely different book with identical file names and sizes] → Practically never happens;
  such a copy is the same book.
- [Room's query context is a test dispatcher in tests] → Room suspends rather than blocks on it, so `runTest` drives
  it; if a flow never settles, tests use a real dispatcher for Room only.
- [Covers keep their original size] → Plain images per the roadmap; thumbnails are M15.
- [A file whose duration can't be read counts as zero length] → It still plays; chapter offsets after it are off until
  M15's edge-case pass.

## Migration Plan

New database, version 1; nothing to migrate. Future versions add `Migration`s or auto-migrations against the exported
schema.

## Open Questions

- Whether iOS bookmark strings for the same folder are stable across picks is not known; the fingerprint covers it
  either way.
