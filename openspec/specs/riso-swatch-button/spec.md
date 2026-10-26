# riso-swatch-button Specification

## Purpose
Defines `SwatchButton`, Riso's content-less button that shows an ink pair: its fill, its shape, its press and how it reports itself to accessibility.

## Requirements
### Requirement: Swatch button
`SwatchButton(accent, accentVariant, onClick, contentDescription, modifier)` SHALL draw a filled button with no
content, sized by its modifier. Its fill SHALL be the same Riso mix as `Button`'s, made from the given accent variant
and accent instead of the theme's. Its shape SHALL be the theme's small shape, and it SHALL show the same press offset
as `Button`. It SHALL be a button for accessibility, labelled with `contentDescription`. With effects off, it SHALL
draw flat colors.

#### Scenario: Tap
- **WHEN** the user taps a `SwatchButton` labelled "Change inks"
- **THEN** `onClick` runs once

#### Scenario: Shape
- **WHEN** a `SwatchButton` is drawn next to a `Button`
- **THEN** its corners use the small shape, which is rounder relative to its size than the `Button`'s standard shape

