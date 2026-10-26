## Context

A book keeps references to the user's files: the picked folder or file (`books.source_ref`) and one per audio file
(`audio_files.source_ref`). Nothing in the app notices when they stop working. M7's `DocumentAccess.reachability`
already tells `Reachable`, `Missing` and `AccessLost` apart, on both platforms:

- **Android.** Document ids are paths, so any rename or move makes the root and every child missing. A revoked
  persisted grant is `AccessLost`.
- **iOS.** The root is a bookmark, and a child is the bookmark plus a relative path. Renaming or moving the picked
  item is followed, so the book stays available. Deleting it, or renaming a file inside a picked folder, makes it
  missing. Copying the folder elsewhere and deleting the original makes it missing too, because the bookmark can't
  follow a new inode.

M8 refuses duplicates by `source_ref`, or by a fingerprint (the picked name plus the audio files' paths and sizes),
because iOS bookmarks differ on every pick.

## Goals / Non-Goals

**Goals:**
- Detect unavailable books when the library loads and when playback starts, and tell missing apart from access lost.
- Home and Player show it; Player offers relink. Nothing crashes or plays silence.
- Relinking matches files with documented, deterministic rules, and asks before a partial result.
- Position, speed, chapters, cover and timestamps are always kept. The user's files are only read.
- Thorough JVM tests at the matcher, library, engine and state-holder levels, plus an end-to-end JVM test through
  the real library. Maestro flows on both platforms with host shell steps.

**Non-Goals:**
- Releasing the old Android grant after a relink, and refreshing stale iOS bookmarks. Both are noted for M15.
- Watching the file system. Checks run at the moments listed, never in the background.
- Deleting books, and relinking from Home directly (see decision 5).

## Decisions

### 1. Availability is derived and held in memory, not stored
`BookAvailability` (api) exposes `availability: StateFlow<Map<BookId, Availability>>`, plus `check(id)` and
`checkAll()`. `BookAvailabilityImpl` (impl) reads books through `LibraryRepository` and checks them through
`DocumentAccess`. A book with no entry hasn't been checked and counts as available.

*Why derived:* reachability describes the outside world, and it changes while the app isn't running: a USB drive
comes back, the user undoes a move. A stored flag would be stale at every launch and would have to be checked
again anyway. It would also add a write on every check, and a book could stay "unavailable" after its files came
back until something re-checked it. Deriving it costs one check per book per launch, which runs off the main thread
after Home has drawn.

*Alternative:* a stored column, which would show the last-known state at once on launch. That is not worth showing
data that may be stale. Right after launch the cost is a short window in which an unavailable book looks normal;
Player and play still check it.

### 2. When checks run
- **Library load:** `HomeState` launches `checkAll()` when it is created.
- **Player opens:** `PlayerScreenState` checks its book, and "Check again" checks it.
- **Playback starts:** the engine's `play()` checks first.
- **Playback error:** the engine checks after stopping.
- **After a relink:** the relinker checks the book.

`checkAll()` reads `books()` once and checks each book with a `Semaphore(4)` on `CoroutineDispatchers.IO`,
publishing as each finishes. A `Mutex.tryLock` makes an overlapping `checkAll` a no-op. Within one book, the root is
checked first, and when it isn't reachable that answer stands and saves a query per file on Android. Otherwise
each file that isn't left out is checked in order. Any `AccessLost` wins over `Missing`.

The check at playback start is a full check. On Android it is one `ContentResolver` query per file, which is
milliseconds for typical books. M15 can bound it further for very large books.

### 3. Engine behaviour
`EngineBookPlayer` takes `BookAvailability`.
- **`play()`:** `check(id)`; when the book isn't `Available`, it stays paused, plays nothing and keeps its position.
  Otherwise it re-reads `repository.book(id)`, and when the stored playable files differ from the timeline's (after
  a relink), it rebuilds the timeline and calls `output.load(...)` at the current position. Then it plays as before.
- **While playing:** every 30 s (`AvailabilityInterval`, counted in polls) the engine checks the book and pauses
  when it is unavailable. This came out of running the flows on the emulator: the sample files are small enough that
  ExoPlayer buffers the whole book at once (its buffered position reads the book's end), so deleting a later file
  never makes an error. Large books buffer about 50 s ahead, and an open descriptor keeps reading a deleted file. A
  check every 30 s costs one query per file, which is cheap next to decoding audio.
- **Output failure:** `reconcile` already stops on `isFailed`. It now also calls `check(id)`, once per failure.
- **Panel commands:** on Android these go straight to ExoPlayer, which fails to open a missing file, and the engine
  then stops and checks as above. On iOS they go through `BookPlayer.play()`. Neither path plays silence.

`FakeBookPlayer` mirrors this and adds `failPlayback()` for state-holder tests.

### 4. Matching rules (`RelinkMatcher`, pure, in impl)
The input is the stored files (index, relative path, size, duration), the candidates (listed entries) and a
`durationOf` suspend function that is cached and only called when a rule needs a duration. The output maps each file
index to an entry, records which rule matched it, and lists the unmatched files. The rules are:

1. **Relative path:** same path, sizes compatible. Sizes are compatible when equal or when either is unknown.
2. **Name:** same file name, ignoring case, sizes compatible. Tie-breaks, in order: same duration (≤ 0.5 s apart),
   then the same parent-folder name. The file matches only when exactly one candidate is left.
3. **Size and duration:** size known and equal, and duration the same. The file matches only when exactly one
   candidate is left.

Each rule runs as a pass over all files in book order, and a candidate is used at most once. Running path matching
first over every file keeps the name pass from stealing a candidate that a later file owns by path. Ambiguity means
the file is not found; the matcher never guesses. Single-file books use the same rules with one candidate (the
picked file): a moved file matches by path (its name), and a renamed one by size and duration.

*Why these tie-breaks:* a size check is free (the listing has it). A duration needs a parse, so it is only read to
split ties or to confirm a rename. A parent-folder name settles the `Disc 1/01.mp3` vs `Disc 2/01.mp3` case after a
move that also renamed the root.

### 5. Relink lives in Player; Home marks the book and leads there
Home's row says "Files missing · Tap to relink" and tapping opens Player, which holds the whole flow: the picker of
the right kind, progress, partial confirmation and the result. This keeps one flow and one set of tests. Feature
modules can't share UI code without a new module, and two copies would drift. The mini player shows "Unavailable"
instead of its button.

### 6. `BookRelinker.relink(id, pick, confirmMissing)` re-matches on every call
Matching only reads, so there is no plan object to keep between "needs confirmation" and "relink anyway". The
second call matches again and applies only when every file not found is in `confirmMissing`. If the location changed
in between and more files are missing, the user is asked again. This avoids a stale plan and keeps the api plain
data.

The order of the checks is the one in the spec. The reference check against other books runs before listing, so
picking another book's folder is refused even when it is unreachable. The fingerprint check runs after listing and
excludes the book itself, so an iOS re-pick of its own folder isn't refused. "Unchanged" needs the book to have been
available before. That way, re-picking the same folder after a lost grant (which, on Android, restores the grant
without changing anything stored) reports "relinked", which is what the user sees happen.

The apply step is one DAO transaction. It updates the book's `source_ref` and `fingerprint` (from the new pick, so
re-importing the new location is refused as a duplicate), and each file's `source_ref`, `relative_path`, `size` and
`missing`. A unique-constraint failure on `source_ref` (a race with an import) maps to another book's source.

### 7. Partial relink: files are left out, not deleted
Files not found get `audio_files.missing = 1` and keep everything else, so the timeline, chapters and positions stay
as they were. `BookTimeline` drops them like zero-duration files. A position inside a left-out file's span maps to
the start of the next played file (a small change to `locate`), so seeking or jumping into a gap plays on from there.
Availability ignores left-out files. A later relink that finds them clears the mark. Unmatched files that are
already left out don't need confirming again.

*Alternative:* deleting the rows and their chapters. That would shift the timeline and break saved positions, and
since the app can't delete books, a later relink could never restore the files.

### 8. Schema v2
`AudioFileEntity.missing` (`Boolean`, default `0`), with a manual `Migration(1, 2)`:
`ALTER TABLE audio_files ADD COLUMN missing INTEGER NOT NULL DEFAULT 0`. The schema JSON `2.json` is committed. A JVM
test builds a v1 file with the v1 create statements, opens it with Room v2, and reads the book back.

### 9. Test doubles
- `FakeDocumentAccess(followsRenames = false)`. With `true`, renaming a granted root records an alias, so references
  made from the old root resolve to the new path, as iOS bookmarks do. Renames inside a root aren't followed.
  `reachabilityChecks`, `peakConcurrentChecks` and `checkDelay` support the bounded-check tests.
- `FakeBookAvailability`: `set(id, availability)`, `checks`, `checkAllCount`.
- `FakeBookRelinker`: queued results, `calls`.

### 10. Maestro with shell steps
Maestro can't run host commands, so each device scenario is a set of step flows in `bookplate/maestro/relink/`
(outside `flows/`, so `maestro test bookplate/maestro` doesn't run them without their shell steps). The driver
`bookplate/scripts/relink-flows.sh android|ios` resets the sample audio, runs step A, makes the change from the host
(`adb shell mv/rm`, or `mv/rm` in the iOS `ios-path` folder), runs step B, and cleans up. To make a book
unavailable, Android moves the folder. iOS copies the folder to a new name and deletes the original (a move would be
followed), or renames a file inside the folder.

## Risks / Trade-offs

- **Unavailable books look normal for a moment after launch.** Play and Player check anyway, so nothing plays silence.
- **The play-start check costs one query per file.** That is fine for typical books; M15 can tune it.
- **The name pass is greedy in book order.** The tie-breaks make the order matter only for files that are truly
  ambiguous, and those go unmatched.
- **Old Android grants are not released after a relink.** Android allows 512 persisted grants (128 before Android
  11), so this is left to M15.
- **Mid-playback deletion is noticed within 30 s, not at once.** Until then, already-buffered audio keeps playing.
  That is real audio, not silence.

## Migration Plan

Room migrates v1 → v2 on first open. There is no rollback: an older build would refuse a v2 database, which is
normal for this app.

## Scenario → test map

Each spec scenario is listed in `tasks.md` § 9 with the test that covers it.
