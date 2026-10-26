## ADDED Requirements

### Requirement: Playback checks the book
Starting playback SHALL first check the loaded book's availability through the library. When it is not available,
the player SHALL stay paused, keep its position and play nothing. When it is available and the library's files for
it differ from the loaded ones (after a relink), the player SHALL load the new files at the current position before
playing. While a book plays, the player SHALL check it again every 30 seconds, and when it is no longer available
SHALL pause and save as `pause()` does, even when the platform had already buffered what follows. When playback
stops on an error, the player SHALL stop as before and then check the book. Either way, a file that disappears during
playback leaves the player paused at the position it reached, with the book unavailable. This holds for the engine
and for the fake player alike, and for commands from the media panel.

#### Scenario: Play a missing book
- **WHEN** a loaded book's folder was moved and the user taps "Play"
- **THEN** the player stays paused at its position, the output never plays, and the book is missing

#### Scenario: File disappears during playback
- **WHEN** a book plays and its next file is deleted, so playback fails on reaching it
- **THEN** the player is paused at the position reached, that position is saved, and the book is missing

#### Scenario: Buffered when the file disappears
- **WHEN** a book plays and one of its files is deleted, but the platform already buffered it, so no error comes
- **THEN** within 30 seconds the player pauses, saves the position, and the book is missing

#### Scenario: Play after a relink
- **WHEN** a loaded book is relinked to a new folder and the user taps "Play"
- **THEN** the output is loaded with the files in the new folder at the same position and plays

### Requirement: Unavailable book in Player
Opening Player SHALL check its book's availability. While the book is unavailable, Player SHALL show its title,
author and cover, and in place of the chapter title, position, track and controls:
- "Files missing" with "Bookplate can't find this book's files. If you moved or renamed them, relink the book to
  where they are now.", or "Access lost" with "Bookplate is no longer allowed to open this book's files. Relink the
  book by picking them again.";
- a "Relink" button and a "Check again" button, which checks the book again.

The top bar SHALL keep "Back" and hide "Chapters" and "Speed". Nothing SHALL play from Player while the book is
unavailable. When the book becomes available, Player SHALL show it as usual.

#### Scenario: Missing book
- **WHEN** Player opens for a book whose folder was moved
- **THEN** it shows "Files missing", "Relink" and "Check again", and no "Play" button

#### Scenario: Access lost
- **WHEN** Player opens for a book whose grant was revoked
- **THEN** it shows "Access lost" and "Relink"

#### Scenario: Files came back
- **WHEN** Player shows a missing book, its files are put back, and the user taps "Check again"
- **THEN** Player shows the book with its controls, at its saved position

#### Scenario: Stopped during playback
- **WHEN** Player shows a playing book and playback stops because a file disappeared
- **THEN** Player shows "Files missing" with "Relink"

### Requirement: Relinking from Player
Tapping "Relink" SHALL open the platform picker for a folder when the book was added from a folder, and for an
audio file when it was added from a single file. A cancelled pick SHALL change nothing. While a relink runs, Player
SHALL show "Looking for the book's files in “<pick>”…" and SHALL ignore further picks. Player SHALL then show the
result until the user closes it or starts another relink:
- relinked: "Relinked “<title>”", or, with files left out, "Relinked “<title>”. <n> missing file(s) will be
  skipped.", and Player shows the book at the position it had, with its speed and chapters;
- needs confirmation: "Found <found> of <total> files in “<pick>”. Not found: <paths>. Parts of the book without
  their files will be skipped.", with "Relink anyway", which relinks again confirming those files, and "Cancel",
  which changes nothing;
- unchanged: "“<title>” is already linked to “<pick>”";
- no match: "None of this book's files are in “<pick>”";
- wrong kind: "“<title>” was added from a folder. Pick its folder." or "“<title>” was added from a single file.
  Pick the file.";
- another book's source: "“<pick>” is already the book “<other title>”";
- failed: "Couldn't open “<pick>”." followed by why, as Home words import failures.

#### Scenario: Relink a moved folder
- **WHEN** Player shows `Sample Book` missing at 0:42, the user taps "Relink" and picks the moved folder
- **THEN** Player shows "Relinked “Sample Book”", then the book at 0:42 with "Play"

#### Scenario: Folder or file picker
- **WHEN** the user taps "Relink" for a single-file book
- **THEN** the file picker opens for audio files

#### Scenario: Confirm a partial match
- **WHEN** a relink finds 1 of 2 files and the user taps "Relink anyway"
- **THEN** Player shows "Relinked “Sample Book”. 1 missing file will be skipped." and the book's controls

#### Scenario: Cancel a partial match
- **WHEN** a relink finds 1 of 2 files and the user taps "Cancel"
- **THEN** the book is still missing and Player shows "Files missing"

#### Scenario: Wrong folder
- **WHEN** the user picks a folder holding none of the book's files
- **THEN** Player shows "None of this book's files are in “<pick>”" and still shows the book missing

#### Scenario: Another book's folder
- **WHEN** the user picks the folder of the book `Other`
- **THEN** Player shows "“<pick>” is already the book “Other”"

## MODIFIED Requirements

### Requirement: One timeline across a book's files
`bookplate/core/player/impl` SHALL provide `EngineBookPlayer`, implementing every `BookPlayer` requirement over a
platform audio output. It SHALL play a book's files in their library order as one timeline: a book position SHALL map
to the file whose span (its start in the book to its start plus its duration) holds it and the offset into that file,
and a file and offset SHALL map back to the file's start plus the offset, clamped to the book. A position on the
boundary between two files SHALL map to the start of the later one. Files whose duration is zero (unreadable), and
files a partial relink left out, SHALL be left out of playback, keeping the other files' places. A position in the
span of a left-out file SHALL map to the start of the next played file, or to the end of the last one when none
follows. Seeking, skipping and chapter jumps SHALL move across file boundaries.

#### Scenario: Position in the second file
- **WHEN** a book has `01 - Opening.mp3` (1:05) and `02 - Middle.mp3` (2:05) and the player seeks to 1:30
- **THEN** the second file plays from 0:25 and the position reads 1:30

#### Scenario: Skip across a boundary
- **WHEN** the same book is at 1:00 and the user skips forward
- **THEN** the second file plays from 0:05 and the position reads 1:10

#### Scenario: Playback crosses into the next file
- **WHEN** the book plays from 1:00 for 10 seconds at 1.0×
- **THEN** the position reads about 1:10 and the current chapter is `Middle`

#### Scenario: Left-out file is skipped
- **WHEN** a book of three 1:00 files has its second file left out and the player seeks to 1:30
- **THEN** the third file plays from its start and the position reads 2:00

### Requirement: Fake player for the UI
`bookplate/core/player/test` SHALL provide `FakeBookPlayer`, implementing every requirement above with a ticking clock
instead of audio, reading and saving through `LibraryRepository` and checking availability like the engine, with a
way for a test to make playback fail, for unit tests and previews. `sharedApp` SHALL bind `EngineBookPlayer` as the
app's `BookPlayer` on Android and on iOS; neither app SHALL include `core/player/test`.

#### Scenario: Android uses the engine
- **WHEN** the Android app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

#### Scenario: iOS uses the engine
- **WHEN** the iOS app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

#### Scenario: Fake refuses an unavailable book
- **WHEN** the fake player plays a book that its availability reports missing
- **THEN** it stays paused
