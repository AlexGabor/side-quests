## MODIFIED Requirements

### Requirement: Player screen layout
`bookplate/feature/player` SHALL show, top to bottom:
- a top bar with the icon button "Back" at the start, and at the end a "Change inks" swatch button followed by the
  "Chapters" and "Speed" icon buttons;
- the book's title and its author ("Unknown author" when it has none);
- the cover as a plain image (not printed through Riso inks), or the same Riso placeholder as Home's rows (the
  title's first letter and a border in the book's accent, over its accent variant at 40 % opacity), square, as
  large as the width allows and shrinking so that every other element fits on screen;
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

### Requirement: Media panel content
The platform's media panel SHALL show the book the player has loaded, the same one Player and the mini player
follow. The panel is Android's media notification and lock screen, and iOS's Now Playing on the lock screen and in
Control Center. It SHALL show:
- the book's title;
- a subtitle: the current chapter's title when the book has chapters and that chapter has a title other than the
  book's; otherwise the book's author; otherwise nothing;
- the cover from app storage, when the book has one; otherwise artwork printed in the book's inks, as described in
  "Ink artwork in the media panel";
- a seek bar over the whole book: the book's total duration and the position in the whole book, the same numbers
  Player shows;
- whether it is playing, and on iOS the speed it plays at.

When the current chapter changes, by playing on, seeking or a chapter jump, the subtitle SHALL change with it. When
no book is loaded the panel SHALL show nothing.

#### Scenario: Chapter as subtitle
- **WHEN** `Sample Book` by `Sample Author` plays at 1:30, in chapter `Middle`
- **THEN** the panel shows "Sample Book", "Middle", its cover, and a seek bar at 1:30 of 3:10

#### Scenario: Author when there are no chapters
- **WHEN** a book by `Sample Author` with no chapters plays
- **THEN** the panel's subtitle is "Sample Author"

#### Scenario: Chapter titled like the book
- **WHEN** `Single` by `Sample Author` has one chapter titled `Single`
- **THEN** the panel's subtitle is "Sample Author"

#### Scenario: Following chapters
- **WHEN** `Sample Book` plays from 1:00 past 1:05
- **THEN** the panel's subtitle changes from "Opening" to "Middle"

## ADDED Requirements

### Requirement: Ink artwork in the media panel
For a book without a cover, the media panel SHALL show generated square artwork on Android and on iOS. It SHALL show
the title's first letter, upper-cased, in the book's accent, over a paper background washed with the book's accent
variant at 40 % opacity, inside a border in the book's accent, matching the library placeholder. Its colors SHALL come
from the Riso inks the book's ink names stand for, falling back to the theme's accents for a name Riso doesn't know.
The system takes the panel's colors from it. A book with a cover SHALL keep showing its cover. The artwork SHALL be
made once per title letter and ink pair, not on every panel update.

#### Scenario: Coverless book
- **WHEN** `Single`, with no cover and inks Teal/Yellow, plays
- **THEN** the panel shows a yellow-washed square with a teal border and a teal "S", and its colors follow it

#### Scenario: Book with a cover
- **WHEN** `Sample Book`, which has a cover, plays
- **THEN** the panel shows its cover

### Requirement: Loaded book follows its inks
`PlayerBook` SHALL carry the book's inks. While a book is loaded, the player SHALL follow changes to that book's inks
in the library and publish them in its state, without reloading the book or changing its position, playing state or
speed. The media panel SHALL then show the new inks.

#### Scenario: Swatch while playing
- **WHEN** `Single` plays and the user taps Player's swatch, giving it Green/Orange
- **THEN** the player's state holds Green/Orange, playback goes on uninterrupted, and the panel's artwork is
  orange-washed with a green border
