# bookplate-library Specification

## Purpose
Bookplate's library: books stored in Room (KMP, bundled SQLite) with their audio files, chapters and sections, cover, position, speed and timestamps; import of a picked file or folder through `lib/documentaccess` and `lib/audiometadata` into one book, with duplicate refusal and a read-only guarantee; whether a book's files are still reachable (missing or access lost); relinking a moved or unreachable book to a newly picked folder or file, matching its files and leaving out the ones not found; and the repository flows and playback-state updates Home and Player build on. Implemented in `bookplate/core/library/{api,impl,test}`.

## Requirements
### Requirement: Stored books
The library SHALL keep each book in a Room database with the bundled SQLite driver, on Android, iOS and the JVM. A
book SHALL hold its title, author and narrator when known, its total duration, the source reference the user picked
(a folder or a single file, as `DocumentRef.asString()`), the name of its cover in app storage when it has one, its
playback position, its speed, when it was imported and when it was last played. Each of its audio files SHALL hold
its own source reference, its path relative to the picked folder, its size, its duration, its order in the book,
where it starts in the book, and whether a partial relink left it out. Its chapters SHALL be stored with their
titles, start and end in the book, and their nesting, so sections hold chapters. The database schema SHALL be
exported to a committed folder so later versions can migrate it. Version 2 adds the left-out mark, and a database at
version 1 SHALL be migrated to it keeping every book, with no file left out.

#### Scenario: Read back after import
- **WHEN** a folder book is imported and read back by its id
- **THEN** the book, its files in order and its chapters with their sections are returned as they were assembled

#### Scenario: New book defaults
- **WHEN** a book has just been imported
- **THEN** its position is zero, its speed is 1.0, it has never been played, its imported time is the import time,
  and no file is left out

#### Scenario: Migration from version 1
- **WHEN** a version 1 database holding a book is opened by this version
- **THEN** the book reads back with its files, none left out

### Requirement: Audio files of a book
Import SHALL list every file below the picked folder (or the picked file itself), keep those with an audio extension
(`mp3`, `m4a`, `m4b`, `mp4`, `aac`, `flac`, `ogg`, `oga`, `opus`, `wav`, case-insensitive) whose content
`lib/audiometadata` recognises, and ignore all other files. Files SHALL be ordered by their relative path in natural
order: path segments are compared one by one, runs of digits by their numeric value and other text ignoring case.

#### Scenario: Natural order
- **WHEN** a folder holds `10.mp3`, `2.mp3` and `1.mp3`
- **THEN** the book's files are `1.mp3`, `2.mp3`, `10.mp3` in that order

#### Scenario: Non-audio files ignored
- **WHEN** a folder holds `01.mp3`, `notes.txt` and `cover.jpg`
- **THEN** the book has one audio file, `01.mp3`

### Requirement: Title, author and narrator
The book's title SHALL be the first album tag found in file order; for a picked single file, its title tag when it
has no album; else the title of a sidecar cue sheet; else the picked folder's name, or the picked file's name without
its extension. The author SHALL be the first album artist, else the first artist, else a cue sheet's performer, else
unknown. The narrator SHALL be the first narrator tag, else unknown. Blank tags count as missing.

#### Scenario: Tagged book
- **WHEN** a book's files carry album `Dune` and album artist `Frank Herbert`
- **THEN** the book is titled `Dune` by `Frank Herbert`

#### Scenario: Untagged folder
- **WHEN** a picked folder named `My Book` holds audio files without tags
- **THEN** the book is titled `My Book` and has no author

#### Scenario: Untagged single file
- **WHEN** a picked file `Story.mp3` has no tags
- **THEN** the book is titled `Story`

### Requirement: Chapters and sections
The book SHALL have one timeline: each file starts where the previous one ends. Each file SHALL contribute, in order:
its embedded chapters (keeping their nesting) when it has any; otherwise the chapters of a sidecar `.cue` paired with
it; otherwise one chapter spanning the file, titled by its title tag or else its file name without extension. A `.cue`
in the same folder is paired with an audio file when it has the same name without extension, or when one of its
`FILE` entries names that audio file. When the book's files are in more than one folder, each sub-folder below the
folder they share SHALL become a section titled by the folder name, holding the chapters of its files (and deeper
sub-folders as nested sections). A section's start and end SHALL span its chapters.

#### Scenario: Single file with chapters
- **WHEN** an M4B with three embedded chapters is imported
- **THEN** the book has those three chapters with their titles, starts and ends

#### Scenario: One chapter per file
- **WHEN** a folder of three MP3 files without embedded chapters is imported
- **THEN** the book has three chapters, one per file, titled from each file's title tag or name, each starting where
  the previous file ended

#### Scenario: Disc sections
- **WHEN** a folder holds `Disc 1/01.mp3`, `Disc 1/02.mp3` and `Disc 2/01.mp3`
- **THEN** the book has two sections, `Disc 1` with two chapters and `Disc 2` with one

#### Scenario: Cue sidecar
- **WHEN** a folder holds `book.mp3` without embedded chapters and `book.cue` with three tracks
- **THEN** the book's chapters are the cue sheet's three tracks

### Requirement: Cover
The cover SHALL be the first embedded picture found in file order; otherwise an image named `cover.*` or `folder.*`
(case-insensitive; `jpg`, `jpeg`, `png`, `webp`, `gif` or `bmp`) at the top of the picked folder or in the folder all
its audio files share, preferring `cover`, then the shallower one. Images that are larger than 20 MB are skipped. The picture
SHALL be copied into the app's own storage, and the book SHALL expose the path of that copy. Nothing SHALL be written
next to the user's files. A book without either has no cover.

#### Scenario: Embedded cover
- **WHEN** a book's first file has an embedded PNG
- **THEN** the book's cover path names a file in app storage holding those bytes

#### Scenario: Folder image
- **WHEN** a folder's audio has no embedded art and the folder holds `folder.jpg`
- **THEN** the book's cover is a copy of `folder.jpg` in app storage

### Requirement: Import result and progress
Import SHALL take a picked document (its reference and display name), report progress while it lists and reads files,
and return one of: success with the new book's id; already in library with the existing book's id; no audio found; or
failed with a reason (the source is missing, access to it was lost, or a file could not be read). Only success SHALL
change the library.

#### Scenario: Folder without audio
- **WHEN** a folder holding only `notes.txt` and `cover.jpg` is imported
- **THEN** the result is no audio found and the library is unchanged

#### Scenario: Missing source
- **WHEN** a reference whose folder was deleted is imported
- **THEN** the result is failed because the source is missing

#### Scenario: Progress
- **WHEN** a folder of three audio files is imported
- **THEN** progress is reported for listing and for each of the three files read, before the result

### Requirement: Duplicates are refused
Adding a book that is already in the library SHALL be refused: when a book with the same source reference, or with
the same picked name and the same audio files (relative paths and sizes), is already stored, import SHALL return
already in library with that book's id and SHALL change nothing, including its cover and timestamps.

#### Scenario: Same folder twice
- **WHEN** a folder is imported and then imported again
- **THEN** the second import returns already in library with the first book's id, and the library still holds one book

### Requirement: Read only
Import SHALL only list and read the user's files through `lib/documentaccess`; it SHALL never create, write, move,
rename or delete anything in the picked file or folder, and SHALL close every source it opens.

#### Scenario: Sources closed
- **WHEN** a folder book is imported
- **THEN** every file opened for reading has been closed when import returns

### Requirement: Book availability
The library SHALL tell, for each book, whether its files can be reached: **available**, **missing** (deleted,
moved or renamed away) or **access lost** (the platform no longer lets the app at them). A book SHALL be checked
by asking `lib/documentaccess` about its source reference and then about each of its files that a partial relink
has not left out. The book is access lost when any reference is access lost. Otherwise it is missing when any
reference is missing, and otherwise it is available. Availability SHALL be derived when checked and held in memory,
not stored. A book that has not been checked yet SHALL count as available. A book that was unavailable SHALL become
available at the next check that finds its files, without a relink.

Checking every book SHALL read the library once, check at most 4 books at a time on `CoroutineDispatchers.IO`,
and publish each book's result as soon as it is known. A check of every book that starts while another is running
SHALL do nothing. Checking SHALL never throw.

#### Scenario: Folder moved
- **WHEN** a folder book's folder is moved or renamed (as on Android) and the book is checked
- **THEN** the book is missing

#### Scenario: One file deleted
- **WHEN** one file of a folder book is deleted and the book is checked
- **THEN** the book is missing

#### Scenario: Grant revoked
- **WHEN** the app's grant on a book's folder is revoked and the book is checked
- **THEN** the book is access lost, not missing

#### Scenario: Picked item renamed on iOS
- **WHEN** the picked folder is renamed where references follow the picked item (iOS bookmarks) and the book is checked
- **THEN** the book is available

#### Scenario: Files come back
- **WHEN** a missing book's files are put back where they were and the book is checked again
- **THEN** the book is available

#### Scenario: Left-out files are not checked
- **WHEN** a partial relink left out a file and the book is checked
- **THEN** the book is available while its other files are reachable

#### Scenario: Checking the library
- **WHEN** the library holds six books and every book is checked, with each check taking a while
- **THEN** no more than 4 books are checked at once, all on the IO dispatcher, and every book has a result at the end

### Requirement: Relinking a book
The library SHALL let the user point a book at a new location of its files through a picked document. Relinking
SHALL only list and read the user's files, SHALL change only the book's source reference, its fingerprint and its
files' references, relative paths, sizes and left-out marks, and SHALL keep its position, speed, chapters, cover,
title, author, duration, and imported and last-played times. The checks SHALL run in this order and SHALL return
the first that applies:

1. no such book: nothing changes;
2. **wrong kind**: a folder book needs a folder and a single-file book needs a file; nothing changes;
3. **another book's source**: the pick's reference is another book's source, or the pick's fingerprint (its name
   and its audio files' relative paths and sizes, as at import) is another book's; nothing changes;
4. **failed**: the pick can't be listed, because it is missing or access was lost; nothing changes;
5. **no match**: none of the book's files are found in the pick (matching below); nothing changes;
6. **needs confirmation**: some files are found but others, not already left out, are not, and they are not all
   in the set of files the caller confirmed. The result names the files not found and how many were found;
   nothing changes;
7. **unchanged**: applying the match would change nothing stored and the book was available; nothing is written;
8. **relinked**: the match is saved in one transaction. Files found point at the pick, files not found are left out
   (see "Files left out by a partial relink"), and the result names the files left out.

After a relink or an unchanged result the book SHALL be checked again, so its availability follows.

#### Scenario: Whole folder moved
- **WHEN** a folder book saved at 0:42 at 1.5× has its folder renamed to `Moved` and is relinked to `Moved`
- **THEN** the result is relinked with nothing left out, every file points into `Moved`, the book is available,
  and its position, speed, chapters, cover and timestamps are as before

#### Scenario: Partial match asks first
- **WHEN** a folder book's folder is moved without one of its files and is relinked to the new folder
- **THEN** the result is needs confirmation naming that file, and nothing in the library changed

#### Scenario: Partial match confirmed
- **WHEN** the same relink is made again confirming that file
- **THEN** the result is relinked, that file is left out, the others point at the new folder, and the book is available

#### Scenario: Partial match cancelled
- **WHEN** the user cancels after needs confirmation
- **THEN** the book still points at its old folder and is still missing

#### Scenario: Wrong folder
- **WHEN** a book is relinked to a folder holding none of its files
- **THEN** the result is no match and nothing changed

#### Scenario: Folder for a single-file book
- **WHEN** a single-file book is relinked to a folder
- **THEN** the result is wrong kind, saying a file is needed, and nothing changed

#### Scenario: File for a folder book
- **WHEN** a folder book is relinked to a single file
- **THEN** the result is wrong kind, saying a folder is needed, and nothing changed

#### Scenario: Another book's folder
- **WHEN** book A is relinked to the folder that is book B's source
- **THEN** the result is another book's source with B's id, and nothing changed

#### Scenario: Access restored by picking the same folder
- **WHEN** a book whose grant was revoked is relinked to the same folder, which grants it again
- **THEN** the result is relinked and the book is available, with its files unchanged

#### Scenario: Relinking an available book to where it is
- **WHEN** an available book is relinked to its own folder
- **THEN** the result is unchanged and nothing was written

#### Scenario: Unreachable pick
- **WHEN** a book is relinked to a folder that was deleted after it was picked
- **THEN** the result is failed because the source is missing, and nothing changed

### Requirement: Matching files when relinking
A book's stored files SHALL be matched to candidate files in the pick. For a folder, the candidates are every listed
file with an audio extension. For a file, the candidate is the picked file. A candidate SHALL be matched to at most
one stored file. Sizes are *compatible* when they are equal or either is unknown. Durations are *the same* when
both are known and positive and differ by at most 0.5 s. A candidate's duration SHALL be read through
`lib/audiometadata` only when a rule needs it, at most once per candidate. The rules SHALL run as passes over the
stored files in book order, each pass only on files and candidates still unmatched:

1. **relative path**: the candidate with the same relative path and a compatible size;
2. **name**: the candidates with the same file name, ignoring case, and a compatible size. When there are several,
   only those with the same duration are kept. When there are still several, only those whose parent folder has
   the stored file's parent folder name are kept. A file is matched when exactly one candidate is left;
3. **size and duration**: the candidates whose size is known and equal to the stored file's and whose duration is
   the same. A file is matched when exactly one candidate is left.

A file that is still unmatched after the three passes is not found. An ambiguous file is never matched by guessing.

#### Scenario: Renamed file inside the folder
- **WHEN** `01 - Opening.mp3` was renamed to `01 - Intro.mp3` in the book's folder
- **THEN** it is matched by size and duration, and the other files by relative path

#### Scenario: Nested sections moved together
- **WHEN** a folder holding `Disc 1/01.mp3`, `Disc 1/02.mp3` and `Disc 2/01.mp3` is moved
- **THEN** all three are matched by relative path

#### Scenario: Sub-folder renamed
- **WHEN** `Disc 2` is renamed to `CD 2` inside the book's folder
- **THEN** `Disc 2/01.mp3` is matched to `CD 2/01.mp3` by name, and the `Disc 1` files by relative path

#### Scenario: Same name, different size
- **WHEN** a stored `01.mp3` is not at its path and the pick holds `A/01.mp3` of another size and `B/01.mp3` of its size
- **THEN** it is matched to `B/01.mp3`

#### Scenario: Same name and size, different duration
- **WHEN** the two candidates named `01.mp3` have the same size but only one has the stored file's duration
- **THEN** it is matched to that one

#### Scenario: Ambiguous name
- **WHEN** two candidates named `01.mp3` have the same size and duration and neither is in a folder with the stored
  parent folder's name
- **THEN** the file is not found

#### Scenario: Single file renamed
- **WHEN** a single-file book's `Single.m4b` is renamed to `Renamed.m4b` and that file is picked
- **THEN** it is matched by size and duration

#### Scenario: Different single file
- **WHEN** a single-file book is relinked to a file of another size
- **THEN** nothing is matched

### Requirement: Files left out by a partial relink
A file that a confirmed partial relink did not find SHALL stay in the book, keeping its reference, relative path,
size, duration and place on the timeline, and SHALL be marked left out. A left-out file SHALL not count when the
book's availability is checked, and SHALL not be played. A later relink that finds it SHALL clear the mark, and when
every file that relink doesn't find is already left out, it SHALL not ask for confirmation again.

#### Scenario: Found again later
- **WHEN** a book with a left-out file is relinked to a folder that holds all its files
- **THEN** the result is relinked with nothing left out

#### Scenario: No second confirmation
- **WHEN** a book with a left-out file is relinked to a folder holding its other files only
- **THEN** the result is relinked without asking, and that file stays left out

### Requirement: Library flows
The repository SHALL expose, as flows that update when the database changes:
- the library, newest first by updated time, which is the later of last played and imported (ties by newest import);
- the library filtered by a query: books whose title or author contains it, ignoring case; a blank query keeps all;
- the last-played book, or nothing when no book has been played;
- one book with its files and chapters, or nothing when there is no such book.

Queries and updates SHALL run on `CoroutineDispatchers.IO`.

#### Scenario: Recently updated first
- **WHEN** book A is imported, then book B, then A is played
- **THEN** the library lists A before B

#### Scenario: Filter by author
- **WHEN** the library holds `Dune` by `Frank Herbert` and `Emma` by `Jane Austen` and the query is `austen`
- **THEN** only `Emma` is listed

#### Scenario: No played book
- **WHEN** no book has been played
- **THEN** the last-played book is nothing

### Requirement: Playback state updates
The repository SHALL let a caller set a book's position, its speed and its last-played time. Each update SHALL only
change that field of that book and SHALL be reflected in the flows.

#### Scenario: Played book becomes last played
- **WHEN** a book's last-played time is set
- **THEN** the last-played flow emits that book, and the library moves it first

#### Scenario: Position and speed
- **WHEN** a book's position is set to 90 s and its speed to 1.5
- **THEN** reading the book back gives position 90 s and speed 1.5

### Requirement: Test doubles
`bookplate/core/library/test` SHALL provide an in-memory repository and importer that behave like the real ones for
the library flows, the updates, and scripted import results. It SHALL also provide a fake availability, whose
results a test sets per book and which records the checks made, and a fake relinker that returns queued results and
records its calls. A Koin module SHALL bind them all.

#### Scenario: Scripted import
- **WHEN** a test queues an already-in-library result on the fake importer and imports
- **THEN** the importer returns that result and the fake library is unchanged

#### Scenario: Scripted availability
- **WHEN** a test sets a book missing on the fake availability and checks it
- **THEN** the check returns missing, the availability flow holds it, and the check is recorded
