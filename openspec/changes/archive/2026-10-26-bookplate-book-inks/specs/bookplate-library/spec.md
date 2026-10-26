## MODIFIED Requirements

### Requirement: Stored books
The library SHALL keep each book in a Room database with the bundled SQLite driver, on Android, iOS and the JVM. A
book SHALL hold its title, author and narrator when known, its total duration, the source reference the user picked
(a folder or a single file, as `DocumentRef.asString()`), the name of its cover in app storage when it has one, its
playback position, its speed, its ink pair (the Riso names of an accent and an accent variant), when it was imported
and when it was last played. Each of its audio files SHALL hold
its own source reference, its path relative to the picked folder, its size, its duration, its order in the book,
where it starts in the book, and whether a partial relink left it out. Its chapters SHALL be stored with their
titles, start and end in the book, and their nesting, so sections hold chapters. The database schema SHALL be
exported to a committed folder so later versions can migrate it. Version 2 adds the left-out mark, and a database at
version 1 SHALL be migrated to it keeping every book, with no file left out. Version 3 adds the ink pair, and a
database at version 2 SHALL be migrated to it keeping every book, each given a pair from the ink picker.

#### Scenario: Read back after import
- **WHEN** a folder book is imported and read back by its id
- **THEN** the book, its files in order and its chapters with their sections are returned as they were assembled

#### Scenario: New book defaults
- **WHEN** a book has just been imported
- **THEN** its position is zero, its speed is 1.0, it has never been played, its imported time is the import time,
  no file is left out, and its inks are the pair the ink picker gave

#### Scenario: Migration from version 1
- **WHEN** a version 1 database holding a book is opened by this version
- **THEN** the book reads back with its files, none left out

#### Scenario: Migration from version 2
- **WHEN** a version 2 database holding two books is opened by this version with a picker giving Teal/Yellow, then
  Red/Blue
- **THEN** both books read back unchanged, one with Teal/Yellow and the other with Red/Blue

### Requirement: Playback state updates
The repository SHALL let a caller set a book's position, its speed, its last-played time and its inks. Each update
SHALL only change that field of that book and SHALL be reflected in the flows.

#### Scenario: Played book becomes last played
- **WHEN** a book's last-played time is set
- **THEN** the last-played flow emits that book, and the library moves it first

#### Scenario: Position and speed
- **WHEN** a book's position is set to 90 s and its speed to 1.5
- **THEN** reading the book back gives position 90 s and speed 1.5

#### Scenario: Inks
- **WHEN** a book's inks are set to Green/Orange
- **THEN** the library, the book and, if it is the last played, the last-played flow emit it with Green/Orange,
  without changing its place in the library

### Requirement: Test doubles
`bookplate/core/library/test` SHALL provide an in-memory repository and importer that behave like the real ones for
the library flows, the updates, and scripted import results. It SHALL also provide a fake availability, whose
results a test sets per book and which records the checks made, a fake relinker that returns queued results and
records its calls, and a fake ink picker that returns a fixed sequence of pairs. A Koin module SHALL bind them all.

#### Scenario: Scripted import
- **WHEN** a test queues an already-in-library result on the fake importer and imports
- **THEN** the importer returns that result and the fake library is unchanged

#### Scenario: Scripted availability
- **WHEN** a test sets a book missing on the fake availability and checks it
- **THEN** the check returns missing, the availability flow holds it, and the check is recorded

#### Scenario: Fake ink picker
- **WHEN** the fake ink picker is asked for a pair twice
- **THEN** it returns its first pair, then its second

## ADDED Requirements

### Requirement: Ink picker
The library SHALL take a `BookInkPicker` that returns an ink pair whenever asked. The app SHALL bind one that picks
two different inks at random from Riso's accent inks. Import and the version 3 migration SHALL use it.

#### Scenario: Two different accents
- **WHEN** the app's picker is asked for a pair many times
- **THEN** every pair holds two different names, each one of Riso's accent inks, never Black
