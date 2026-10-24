## ADDED Requirements

### Requirement: Library rows
Home SHALL list every book in the library as one row in a vertical list. Each row SHALL show:
- the book's cover as a plain image (not printed through Riso inks), or, when the book has no cover or its image
  can't be read, a Riso placeholder of the same size showing the first letter of the title;
- the title;
- the author, or "Unknown author" when the book has none;
- the total duration, as `m:ss` under an hour and `h:mm:ss` from an hour;
- the progress as a whole percent, rounded down, of the position over the total duration (0 % when the duration is
  zero), with a `SmallProgress` at that fraction.

#### Scenario: Row content
- **WHEN** the library holds `Dune` by `Frank Herbert`, 2:00:00 long, played to 0:30:00
- **THEN** its row shows "Dune", "Frank Herbert", "2:00:00" and "25%", and its `SmallProgress` is at 0.25

#### Scenario: No author, no cover
- **WHEN** a book titled `Single` has no author and no cover
- **THEN** its row shows "Unknown author" and a placeholder with "S" where the cover goes

#### Scenario: Cover
- **WHEN** a book has a cover in app storage
- **THEN** its row shows that image, unprinted

#### Scenario: Cover fades with the page
- **WHEN** Home crossfades to or from Player, or the add sheet opens over Home
- **THEN** each cover fades as transparency by the same amount the ink around it thins

### Requirement: Recently updated first
Rows SHALL be in the order the library returns: most recently updated first, where updated is the later of last
played and imported. The list SHALL update when the library changes.

#### Scenario: Played book moves up
- **WHEN** book A is imported, then book B, then A is played
- **THEN** A's row is above B's

#### Scenario: New book appears
- **WHEN** a book is added while Home is shown
- **THEN** its row appears at the top without leaving Home

### Requirement: Search
Home SHALL show a `SearchField` above the list with the placeholder "Search title or author". Typing SHALL filter the
rows to books whose title or author contains the text, ignoring case; clearing it SHALL show every book again. The
query SHALL survive configuration changes and process death.

#### Scenario: Filter by author
- **WHEN** the library holds `Dune` by `Frank Herbert` and `Emma` by `Jane Austen` and the user types "austen"
- **THEN** only the `Emma` row is shown

#### Scenario: Clear
- **WHEN** the user clears the search field
- **THEN** every book's row is shown again

### Requirement: Empty and no-results states
When the library is empty and the query is blank, Home SHALL show "Your library is empty" with a hint to add a book
from a folder or a single file. When the query is not blank and no book matches, Home SHALL show
"No books match “<query>”".

#### Scenario: Empty library
- **WHEN** Home opens with no books
- **THEN** "Your library is empty" is shown and no rows

#### Scenario: Nothing matches
- **WHEN** the library has books and the user searches for "zzz"
- **THEN** "No books match “zzz”" is shown and no rows

### Requirement: Adding a book
Next to the search field Home SHALL show an add button labelled "Add book" for accessibility. Tapping it SHALL open a
Riso bottom sheet offering "Add a folder" and "Add a single file". Choosing one SHALL dismiss the sheet and open the
platform picker for a folder or an audio file. A cancelled pick SHALL change nothing. A pick SHALL be imported as one
book. While an import runs, picks SHALL be ignored.

#### Scenario: Add a folder
- **WHEN** the user taps "Add book", then "Add a folder", and picks a folder of audio files
- **THEN** the folder is imported and its book's row appears

#### Scenario: Cancelled pick
- **WHEN** the user opens the picker and cancels
- **THEN** no import runs and Home is unchanged

### Requirement: Import progress and results
While a pick is imported, Home SHALL show a status above the list naming the pick and the current step: looking for
audio, reading file *n* of *m* (with a `SmallProgress` at *n*/*m*), or saving. When the import ends the status SHALL
show its result until the user closes it or the next import starts:
- success: "Added “<title>”";
- already in library: "“<title>” is already in your library", naming the existing book, and the library is unchanged;
- no audio found: "No audio found in “<pick name>”";
- failed: "Couldn't add “<pick name>”", followed by why: the file or folder is gone, access to it was lost, or a file
  couldn't be read.

#### Scenario: Progress
- **WHEN** a folder of three audio files is being imported and the second has been read
- **THEN** the status reads "Reading file 2 of 3" with its progress at 2/3

#### Scenario: Already in library
- **WHEN** the user adds a folder that is already the book `Sample Book`
- **THEN** the status reads "“Sample Book” is already in your library" and the library still holds one such book

#### Scenario: No audio
- **WHEN** the user adds a folder named `Notes` with no audio
- **THEN** the status reads "No audio found in “Notes”"

#### Scenario: Closing the result
- **WHEN** a result is shown and the user taps its close button
- **THEN** the status disappears

### Requirement: Opening a book
Tapping a row SHALL open Player for that book's id.

#### Scenario: Tap a row
- **WHEN** the user taps the row of the book with id 7
- **THEN** Player opens for book "7"

### Requirement: Room for the mini player
Home SHALL lay out a bottom bar below the list, outside the scrolling content, for the mini player. Until the mini
player exists (M10) the bar SHALL be empty and take no space.

#### Scenario: Nothing in the bar
- **WHEN** Home is shown
- **THEN** the list reaches the bottom of the screen and no bar is drawn
