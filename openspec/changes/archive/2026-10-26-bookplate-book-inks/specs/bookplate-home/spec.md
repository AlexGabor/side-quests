## MODIFIED Requirements

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

## ADDED Requirements

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
