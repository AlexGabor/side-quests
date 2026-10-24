## ADDED Requirements

### Requirement: Small progress draws completed and remaining parts
`SmallProgress` SHALL draw a single straight line, the theme's line width thick, across its full width. The part from the start up to the progress fraction SHALL be the theme's accent color at full opacity. The rest SHALL be the theme's content color at 50 % opacity. The completed part SHALL start at the layout's start edge, which is the right edge in right-to-left layouts.

#### Scenario: Partial progress
- **WHEN** `SmallProgress` is shown with progress 0.4
- **THEN** the first 40 % of its width is accent color at full opacity, and the remaining 60 % is content color at 50 % opacity

#### Scenario: No progress
- **WHEN** `SmallProgress` is shown with progress 0
- **THEN** its whole width is content color at 50 % opacity, with no accent

#### Scenario: Complete
- **WHEN** `SmallProgress` is shown with progress 1
- **THEN** its whole width is accent color at full opacity

#### Scenario: Right-to-left
- **WHEN** `SmallProgress` is shown with progress 0.4 in a right-to-left layout
- **THEN** the accent part covers the rightmost 40 % of its width

### Requirement: Out-of-range progress is clamped
`SmallProgress` SHALL treat progress below 0 as 0, above 1 as 1, and NaN as 0. It SHALL NOT throw for any float value.

#### Scenario: Progress above one
- **WHEN** `SmallProgress` is shown with progress 1.7
- **THEN** it draws the same as progress 1

#### Scenario: Not a number
- **WHEN** `SmallProgress` is shown with progress `Float.NaN`
- **THEN** it draws the same as progress 0

### Requirement: Small progress is announced as a progress bar
`SmallProgress` SHALL expose its clamped value to accessibility services as determinate progress in the range 0 to 1.

#### Scenario: Semantics
- **WHEN** `SmallProgress` is shown with progress 0.25
- **THEN** its semantics report progress 0.25 in the range 0..1

### Requirement: Small progress prints in Riso ink
With print effects on, `SmallProgress` SHALL print in Riso ink on the accent and content drums, like the theme's other components. With effects off it SHALL draw the flat colors above.

#### Scenario: Effects off
- **WHEN** `SmallProgress` is shown inside `RisoTheme(effectsEnabled = false)` on Riso paper
- **THEN** the completed part shows the accent color and the remaining part shows content at 50 % over the paper, with no ink shader applied
