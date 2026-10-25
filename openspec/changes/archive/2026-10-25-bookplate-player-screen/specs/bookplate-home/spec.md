## ADDED Requirements

### Requirement: Mini player
Home's bottom bar SHALL show a mini player for the library's last-played book: its title, a `SmallProgress` at its
position over its duration, and a play/pause button labelled "Play" or "Pause". While the player has that book
loaded, the mini player SHALL follow the player's live position and playing state; otherwise it SHALL show the
position saved in the library, paused. Tapping the button SHALL play or pause that book, loading it first when the
player holds another book or none. Tapping anywhere else on the mini player SHALL open Player for that book. When no
book has been played, the mini player SHALL be hidden and take no space.

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

## MODIFIED Requirements

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
