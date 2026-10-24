## Context

`lib/audiometadata` (M4–M6) parses audio through a blocking, random-access `ByteSource` and leaves the platform side
to this milestone. Bookplate never owns the user's audio: the user points at a book's folder or a single file, the
app reads it in place, and M14 later relinks books whose files moved. Both phones only allow this through their own
pickers and grants:

- Android's Storage Access Framework hands back `content://` URIs whose read permission can be persisted, and lists a
  picked tree through `DocumentsContract`.
- iOS hands back security-scoped URLs. Access must be started and stopped around use, and only a bookmark of the URL
  survives a restart.

`lib/share/{api,impl,test}` is the model for a platform-backed lib: a common interface in `api`, an `expect val`
Koin module in `impl` with one `actual` per platform, a fake plus its module in `test`, and binding in the app's
`sharedApp`.

## Goals / Non-Goals

**Goals:**

- One common API for picking, persisting, listing, reading and checking files, implemented on Android and iOS.
- References that live in a database as strings (M8) and that tell missing apart from access lost (M14).
- A fake good enough for M8's import tests on the JVM.
- A temporary Home debug section that proves it on both phones.

**Non-Goals:**

- Writing, moving, deleting or copying user files.
- Watching folders for changes, or a desktop or web implementation (the JVM target gets a stand-in that has no
  documents).
- Re-creating stale iOS bookmarks or matching moved files; relinking is M14.
- Import, Room or any book model (M8).

## Decisions

### Public API

Package `com.alexgabor.lib.documentaccess` (like `lib/share`, one package across `api`, `impl` and `test`; the Gradle
namespaces carry the `.api` / `.impl` / `.test` suffix).

```kotlin
data class DocumentRef(val root: String, val child: String?, val isFolder: Boolean) {
    fun asString(): String
    companion object { fun parse(value: String): DocumentRef? }
}

data class PickedDocument(val ref: DocumentRef, val name: String)

data class DocumentEntry(
    val ref: DocumentRef, val relativePath: String, val name: String,
    val size: Long?, val lastModified: Long?, // epoch milliseconds
)

enum class Reachability { Reachable, Missing, AccessLost }

class DocumentUnavailableException(val reachability: Reachability, message: String) : Exception(message)

interface DocumentSource : ByteSource, AutoCloseable

interface DocumentAccess {
    suspend fun list(ref: DocumentRef): List<DocumentEntry>
    suspend fun open(ref: DocumentRef): DocumentSource
    suspend fun reachability(ref: DocumentRef): Reachability
}
suspend fun <T> DocumentAccess.read(ref: DocumentRef, block: (ByteSource) -> T): T // open, run on IO, close

interface DocumentPicker {
    @Composable fun rememberLauncher(onPicked: (PickedDocument?) -> Unit): DocumentPickerLauncher
}
interface DocumentPickerLauncher {
    fun pickFile(mimeTypes: List<String> = listOf("*/*"))
    fun pickFolder()
}
```

`impl` exposes `expect val documentAccessModule: Module` that binds both interfaces; `test` exposes
`FakeDocumentAccess` (implementing both) and `fakeDocumentAccessModule`.

### Picking and reading are separate interfaces

`DocumentPicker` is UI: it needs a composition to launch from. `DocumentAccess` is plain suspend I/O that M8's
importer and M11/M12's players use without Compose. Keeping them apart lets a core module depend on `DocumentAccess`
only. A feature reaches the picker as `koinInject<DocumentPicker>().rememberLauncher { … }`.

- *Alternative: a `suspend fun pick()` on `DocumentAccess`.* Android can only start the SAF activity for a result from
  an activity's `ActivityResultRegistry`, so a singleton would have to track the current activity and survive
  configuration changes by hand. `rememberLauncherForActivityResult` already does this, so picking stays in
  composition. The api module therefore applies the Compose mobile convention. It uses only the Compose runtime, has
  no platform code, and stays platform agnostic.

### `api` depends on `lib/audiometadata` for `ByteSource`

`DocumentSource` extends `ByteSource` directly, so callers pass an opened document straight to
`AudioMetadataReader`.

- *Alternative: our own random-access interface plus an adapter.* The adapter would be the same two members restated,
  and every caller (M8 import, M11/M12 players, M14 relink matching durations) would have to wrap. `ByteSource` has no
  audio semantics: it is a size and a positional read. `lib/audiometadata` is pure common Kotlin with no dependencies,
  so depending on it costs nothing. Both modules stay app agnostic, and the dependency only points one way.

The `ByteSource` contract (blocking reads, fewer bytes at the end, none past it) is kept. `close()` comes from
`AutoCloseable`, because `ByteSource` itself has no lifecycle.

### Shape of a reference

A reference is `(root, child, isFolder)`.

- `root` is the picked item's platform token.
- `child` (null for the picked item itself) says which file inside a picked folder is meant. Its meaning is per
  platform:

| Platform | `root`                                            | `child`                                             |
|----------|---------------------------------------------------|-----------------------------------------------------|
| Android  | the picked tree URI, or the picked document URI   | the child's document URI built with `buildDocumentUriUsingTree` |
| iOS      | the bookmark data, base64                          | the path relative to the picked folder               |
| Fake     | the picked path                                    | the path relative to the picked path                 |

Every reference is self-contained: a stored child reference can be opened without its parent row. The persisted
grant (Android) or bookmark (iOS) is always the root's. On Android the child keeps its own document URI, so opening a
listed file needs no walk from the root.

The string form is `docref1;<D|F>;<root>[;<child>]`. `%` and `;` inside parts are percent-escaped, so the form
round-trips any token, including URIs that already contain escapes. Parsing rejects anything else, so a corrupt row
reads as "not a reference" instead of crashing. The version prefix leaves room to change the format later.

- *Alternative: kotlinx.serialization JSON.* That adds a dependency and a plugin to save one line of splitting, and a
  JSON form is no easier to store in Room.

### Android

- **Picking:** `rememberLauncherForActivityResult(OpenDocument())` / `OpenDocumentTree()`. On a result it calls
  `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`, read only. The name comes from
  `OpenableColumns.DISPLAY_NAME`, or the tree's root document. Needs `androidx.activity:activity-compose` in
  `impl`'s `androidMain`.
- **Listing:** breadth-first over `buildChildDocumentsUriUsingTree`, querying only id, name, MIME type, size and last
  modified. Folders are recognised by `Document.MIME_TYPE_DIR`.
- **Reading:** `openFileDescriptor(uri, "r")`, then positional `FileChannel.read(buffer, position)`. The size is from
  `statSize`, falling back to the channel size.
- **Reachability:**
  - The root must be among `persistedUriPermissions` with read permission; otherwise `AccessLost`.
  - Then the document itself is queried. A `SecurityException` means `AccessLost`. No row, or any other failure
    (`FileNotFoundException`, `IllegalArgumentException` from the provider), means `Missing`.
  - The external-storage provider builds document ids from paths, so a renamed or moved file stops resolving and
    reads as missing.
- **Context:** the impl takes the application `Context` from Koin's `androidContext()`, like `lib/share`.

### iOS

- **Picking:**
  - `UIDocumentPickerViewController(forOpeningContentTypes:asCopy: false)`, presented over the topmost controller
    (as `lib/share` does). Folder picks use `UTTypeFolder`. File picks map MIME types through
    `UTType.typeWithMIMEType`, falling back to `UTTypeItem`.
  - The delegate is kept strongly while the picker is up.
  - On a pick, the URL's scope is started, a bookmark is made with `bookmarkDataWithOptions(0)` (iOS has no
    `withSecurityScope` option; a bookmark of a security-scoped URL carries its scope), and the scope is stopped again.
- **Resolving:** `URLByResolvingBookmarkData`. A bookmark that no longer resolves means `Missing`. A
  `startAccessingSecurityScopedResource()` that returns false means `AccessLost`. A child is the resolved root plus
  its relative path, and a child that does not exist on disk is `Missing`.
- **Listing:** `NSFileManager.enumeratorAtPath` on the resolved folder, inside its scope. It yields relative paths
  directly, with `fileAttributes` for type, size and modification date. Hidden entries are skipped.
- **Reading:** POSIX `open`, `fstat` and `pread` on the resolved path.
  - *Why not `NSFileHandle`:* its older read methods raise Objective-C exceptions that Kotlin cannot catch, and
    `pread` is positional and plain. The scope is started on open and stopped in `close()`; nested starts on the
    same URL are counted by the system, so concurrent sources are fine.

### Other targets and test

- **JVM:** `documentAccessModule` binds a stand-in that has no documents. Picks return nothing, listing and opening
  throw `AccessLost`, and reachability is `AccessLost`. It exists only so `sharedApp` and other common code compile and
  link for the JVM target.
- **`FakeDocumentAccess`** (`test`, common):
  - A map from path to bytes and last-modified time, plus a set of explicit folders and a set of granted roots.
  - Picks are queued with `enqueuePick(path)` / `enqueueCancel()`; a pick grants the path.
  - `grant(path)` returns a reference without a pick. `delete`, `rename` and `revoke` change the tree under live
    references.
  - It runs its I/O on `CoroutineDispatchers.IO` too, so `TestScopeRule` controls it.
  - `jvmMain` adds `addDirectory(java.io.File, at = path)`, so M8 can load fixture books from resources.

### Dispatching

Every `DocumentAccess` call does `withContext(CoroutineDispatchers.IO)`, reading the dispatcher when the call starts.
`DocumentSource.read` stays blocking per the `ByteSource` contract. `DocumentAccess.read { }` runs its block on IO, so
parsing does not reach the main thread.

### Temporary debug entry (Bookplate)

- `Placeholders.kt` gains a section headed "Debug: document access (temporary, removed in M9)" with "Pick file",
  "Pick folder", "Refresh" and "Clear" buttons and a scrolling list.
- For each pick it shows the name, its reachability, and, for each file `AudioMetadataReader.detect`s as audio, the
  relative path, size and duration.
- **State holder:** `DocumentAccessDebugState` with a `remember…` function, per the architecture docs. Saved
  instance state is not needed: the picks are re-read from storage whenever the holder is created.
- **Storage:** the picks' `asString()` values go into a Preferences DataStore file (`debug-picks.preferences_pb`).
  Its path is platform specific, so it sits in small `androidMain` / `iosMain` / `jvmMain` files in `sharedApp`.
- **Code layout:** all of it sits under `sharedApp/.../debug/` and in `Placeholders.kt`, so M9 deletes one folder and
  one call. The file pick is filtered to `audio/*`.
- **Tests:** `RootNavigationTest` binds `fakeDocumentAccessModule` and an in-memory pick store.

## Risks / Trade-offs

- [iOS bookmarks follow a renamed or moved *picked* item on the same volume] → On iOS, renaming a picked single file
  may still report reachable, at its new name. That is the better outcome for the user. Files *inside* a picked
  folder are addressed by relative path and do report missing when renamed. The difference is documented in the
  spec's scenarios (folder children) and left to M14.
- [Stale iOS bookmarks] → They still resolve. Refreshing the stored string needs a write path back to the database,
  which belongs to M8/M14; noted, not done.
- [Each iOS child reference repeats the root bookmark, about 1 KB of base64 per row] → Acceptable for a library of
  hundreds of books, and it keeps references self-contained. M8 can normalise if it ever matters.
- [Android providers differ] → Some providers do not report size or last modified (null is allowed), and some throw
  unexpected exceptions for gone documents (mapped to `Missing`). Only the external-storage provider is verified on
  the emulator.
- [Testing access lost on devices is hard] → There is no user-facing way to revoke a single SAF grant or iOS bookmark
  scope. The fake and unit tests cover `AccessLost`. On devices only missing is exercised.
- [The api module uses the Compose plugin] → It brings in the Compose runtime only through the convention. That is
  accepted for a picker that must live in composition.

## Migration Plan

New module, no migration. The debug entry and its DataStore file are deleted in M9. The stored debug picks are not
carried into M8's database.

## Open Questions

None blocking.
