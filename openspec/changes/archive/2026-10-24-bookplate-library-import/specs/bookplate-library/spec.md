## ADDED Requirements

### Requirement: Stored books
The library SHALL keep each book in a Room database with the bundled SQLite driver, on Android, iOS and the JVM. A
book SHALL hold its title, author and narrator when known, its total duration, the source reference the user picked
(a folder or a single file, as `DocumentRef.asString()`), the name of its cover in app storage when it has one, its
playback position, its speed, when it was imported and when it was last played. Each of its audio files SHALL hold
its own source reference, its path relative to the picked folder, its size, its duration, its order in the book and
where it starts in the book. Its chapters SHALL be stored with their titles, start and end in the book, and their
nesting, so sections hold chapters. The database schema SHALL be exported to a committed folder so later versions can
migrate it.

#### Scenario: Read back after import
- **WHEN** a folder book is imported and read back by its id
- **THEN** the book, its files in order and its chapters with their sections are returned as they were assembled

#### Scenario: New book defaults
- **WHEN** a book has just been imported
- **THEN** its position is zero, its speed is 1.0, it has never been played and its imported time is the import time

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
the library flows, the updates, and scripted import results, with a Koin module binding them.

#### Scenario: Scripted import
- **WHEN** a test queues an already-in-library result on the fake importer and imports
- **THEN** the importer returns that result and the fake library is unchanged
