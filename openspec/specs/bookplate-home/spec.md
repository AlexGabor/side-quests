# bookplate-home Specification

## Purpose
Bookplate's Home screen: the library as rows (cover, title, author, duration, progress) newest first, search by title or author, adding a book from a folder or file with import progress and results, empty and no-results states, opening a book in Player, marking books whose files are missing or no longer accessible, and the bottom bar kept for the mini player. Implemented in `bookplate/feature/home`.
## Requirements
### Requirement: Library rows
Home SHALL list every book in the library as one row in a vertical list. Each row SHALL show:
- the book's cover as a plain image (not printed through Riso inks), or, when the book has no cover or its image
  can't be read, a Riso placeholder of the same size showing the first letter of the title, filled with the book's
  accent variant at 40 % opacity and bordered in the book's accent;
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

#### Scenario: Placeholder in the book's inks
- **WHEN** a book with inks Teal/Yellow has no cover
- **THEN** its placeholder has a yellow background at 40 % opacity and a teal border, and the letter stays in the
  content color

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

### Requirement: Unavailable books
When Home is created it SHALL start checking the availability of every book in the library, in the background, so
the list shows at once and rows change as results arrive. The row of a book that is missing SHALL add the line
"Files missing · Tap to relink", and the row of a book whose access was lost "Access lost · Tap to relink", both in the
accent color. The row SHALL show everything else as before. Tapping it SHALL open Player for the book, where it can
be relinked. When a book becomes available again its row SHALL go back to normal.

#### Scenario: Missing book
- **WHEN** Home opens and the check finds `Sample Book`'s files missing
- **THEN** its row shows "Files missing · Tap to relink"

#### Scenario: Access lost
- **WHEN** the check finds that access to `Sample Book` was lost
- **THEN** its row shows "Access lost · Tap to relink"

#### Scenario: Checked when Home opens
- **WHEN** Home is created
- **THEN** a check of every book is started once

#### Scenario: Back to normal
- **WHEN** an unavailable book is relinked, or found again by a later check
- **THEN** its row no longer shows the unavailable line

#### Scenario: Tapping opens Player
- **WHEN** the user taps an unavailable book's row
- **THEN** Player opens for that book

### Requirement: Room for the mini player
Home SHALL lay out a bottom bar below the list, outside the scrolling content, holding the mini player. The bar SHALL
take the bottom safe-drawing inset when shown; when the mini player is hidden the bar SHALL be empty and take no space,
and the list SHALL run under the bottom inset.

#### Scenario: Nothing in the bar
- **WHEN** Home is shown and no book has been played
- **THEN** the list reaches the bottom of the screen and no bar is drawn

#### Scenario: Bar shown
- **WHEN** Home is shown after a book was played
- **THEN** the list ends above the mini player, which sits above the bottom inset

### Requirement: Mini player
Home's bottom bar SHALL show a mini player for the library's last-played book: its title, a `SmallProgress` at its
position over its duration, and a play/pause button labelled "Play" or "Pause". While the player has that book
loaded, the mini player SHALL follow the player's live position and playing state; otherwise it SHALL show the
position saved in the library, paused. Tapping the button SHALL play or pause that book, loading it first when the
player holds another book or none. Tapping anywhere else on the mini player SHALL open Player for that book. When no
book has been played, the mini player SHALL be hidden and take no space. When the book is unavailable, the mini
player SHALL show "Unavailable" in place of the button, and nothing SHALL play from it.

#### Scenario: Fresh install
- **WHEN** Home opens and no book has ever been played
- **THEN** no mini player is shown and the list reaches the bottom of the screen

#### Scenario: After playing
- **WHEN** the user plays `Sample Book` in Player and goes back to Home
- **THEN** the mini player shows "Sample Book", its progress, and "Pause"

#### Scenario: Play from Home after a restart
- **WHEN** the app restarts with `Sample Book` last played and the user taps "Play" on the mini player
- **THEN** `Sample Book` plays from its saved position and the button reads "Pause"

#### Scenario: Open Player
- **WHEN** the user taps the mini player's title
- **THEN** Player opens for that book

#### Scenario: Unavailable last-played book
- **WHEN** the last-played book is found missing
- **THEN** the mini player shows its title, its progress and "Unavailable", has no play button, and playing from it
  does nothing

### Requirement: Home prints each book in its inks
Each library row and the mini player SHALL print with their book's inks in place of the theme's accents. That covers
the `SmallProgress`, the "Unavailable" mark and the mini player's button. Elements not tied to a book, such as the
import panel, SHALL keep the theme's accents. When a book's inks change, its row and the mini player SHALL show the
new inks without leaving Home or reopening it.

#### Scenario: Rows differ
- **WHEN** the library holds `Dune` with Teal/Yellow and `Emma` with Red/Blue
- **THEN** `Dune`'s progress is drawn in teal and `Emma`'s in red

#### Scenario: Mini player follows the book
- **WHEN** the last-played book's inks are changed to Green/Orange in Player and the user goes back
- **THEN** the mini player's progress and its row's progress are drawn in green

