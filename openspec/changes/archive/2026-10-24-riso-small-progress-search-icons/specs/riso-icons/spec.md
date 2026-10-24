## ADDED Requirements

### Requirement: Icons share one stroked style
Every `IconType` SHALL be drawn from stroked paths in a 24 × 24 viewport, with round caps and joins, in the theme's content color. The stroke SHALL be the theme's line width at the icon's size, and the icon SHALL be the theme's icon size, so that all icons carry the same weight as Riso's borders and rules. No icon SHALL be filled.

#### Scenario: Consistent weight
- **WHEN** any two icons are shown side by side at the theme's icon size
- **THEN** their strokes have the same width as each other and as the theme's line width

### Requirement: Media and list icons are available
`IconType` SHALL provide `Play`, `Pause`, `Rewind10`, `Forward10`, `Chapters`, `Speed`, `Add`, `Search` and `Close`, next to the existing `Back`, `Settings` and `Share`. Each SHALL be recognisable as:
- `Play`: a right-pointing triangle.
- `Pause`: two vertical bars.
- `Rewind10`: an arrow circling counter-clockwise with "10" inside.
- `Forward10`: an arrow circling clockwise with "10" inside, mirroring `Rewind10`.
- `Chapters`: a bulleted list of three lines.
- `Speed`: a gauge with a needle.
- `Add`: a plus.
- `Search`: a magnifying glass.
- `Close`: a cross.

Each new glyph SHALL be the same on every platform.

#### Scenario: All icons render
- **WHEN** each `IconType` is shown with `Icon`
- **THEN** it renders without error at the theme's icon size, inside the 24-unit viewport

#### Scenario: Usable in icon buttons
- **WHEN** any new `IconType` is passed to `FlatButton(icon = …)`
- **THEN** the button shows that glyph with the content description given
