## ADDED Requirements

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

## MODIFIED Requirements

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
