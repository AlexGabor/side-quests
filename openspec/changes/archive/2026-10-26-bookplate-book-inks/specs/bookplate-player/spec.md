## MODIFIED Requirements

### Requirement: Player screen layout
`bookplate/feature/player` SHALL show, top to bottom:
- a top bar with the icon button "Back" at the start, and at the end a "Change inks" swatch button followed by the
  "Chapters" and "Speed" icon buttons;
- the book's title and its author ("Unknown author" when it has none);
- the cover as a plain image (not printed through Riso inks), or a Riso placeholder with the title's first letter,
  square, as large as the width allows and shrinking so that every other element fits on screen;
- the current chapter's title, when the current chapter has one;
- the position on the left and the total duration on the right, as `m:ss` under an hour and `h:mm:ss` from an hour;
- a `Track` showing the position, with minutes as its items and 10-second subdivisions, ending exactly at the total
  duration (not at the next whole minute);
- a row with "Rewind 10 seconds" and "Forward 10 seconds" icon buttons around a Riso `Button` showing the play or
  pause icon, labelled "Play" or "Pause".
The cover SHALL fade with the page like Home's covers. The content SHALL stay within the safe drawing area.

#### Scenario: Content
- **WHEN** Player opens for `Sample Book` by `Sample Author`, 3:10 long, at 1:30
- **THEN** it shows "Sample Book", "Sample Author", "Middle", "1:30" and "3:10", and a "Play" button

#### Scenario: Small screen
- **WHEN** Player is shown on a small phone
- **THEN** the cover is smaller than on a large phone and the controls row is fully on screen

### Requirement: Chapters sheet
"Chapters" SHALL open a Riso `BottomSheet` with the heading "Chapters" and a scrollable list of the book's playable
chapters, each with its title (or "Chapter <n>" when it has none) and its duration. When the book has sections, each
section's title SHALL head its chapters. The current chapter SHALL be printed in the book's accent ink and every other
one in the content color. Tapping a chapter SHALL jump to its start and close the sheet. The list SHALL open scrolled
so the current chapter is visible.

#### Scenario: Sections
- **WHEN** the chapters sheet opens for `Sample Book`
- **THEN** it lists "Opening" with "1:05", then the heading "Disc 2", then "Middle" with "2:05"

## ADDED Requirements

### Requirement: Player prints in the book's inks
Player and its sheets SHALL print with the book's inks in place of the theme's accents. That covers the play/pause
`Button`'s mix, pressed icon buttons, the unavailable title and the current chapter. When the book's inks change,
Player SHALL show them at once.

#### Scenario: Play button
- **WHEN** Player opens for a book with Teal/Yellow
- **THEN** the play button's fill mixes yellow and teal

### Requirement: Ink swatch button
Player's top bar SHALL show a `SwatchButton`, labelled "Change inks", to the left of "Chapters", 24 dp square,
printed in a candidate pair from the ink picker. Tapping it SHALL set the book's inks to that candidate, and the button
SHALL then show a new candidate from the picker. The candidate SHALL survive configuration changes. The button SHALL
only show when "Chapters" does.

#### Scenario: Shuffle
- **WHEN** the button shows Green/Orange and the user taps it
- **THEN** the book's inks become Green/Orange, Player prints in them at once, and the button shows the picker's next
  pair

#### Scenario: Unavailable book
- **WHEN** Player shows an unavailable book
- **THEN** no swatch button is shown
