## MODIFIED Requirements

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
the title's first letter, upper-cased, in the content ink, over a paper background washed with the book's accent
variant at 40 % opacity, inside a border in the book's accent, matching the library placeholder. Its colors SHALL come
from the Riso inks the book's ink names stand for, falling back to the theme's accents for a name Riso doesn't know.
The system takes the panel's colors from it. A book with a cover SHALL keep showing its cover. The artwork SHALL be
made once per title letter and ink pair, not on every panel update.

#### Scenario: Coverless book
- **WHEN** `Single`, with no cover and inks Teal/Yellow, plays
- **THEN** the panel shows a yellow-washed square with a teal border and "S", and its colors follow it

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
