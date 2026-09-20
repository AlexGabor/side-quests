# Spec Delta

## ADDED Requirements

### Requirement: Ink prints the same through an isolating layer
An ink pass SHALL print the same result whether it composites directly onto its sheet or into a layer an ancestor put between the two. Content that an ancestor draws into a layer of its own — a scroll container stretching at its end, a fade, a predictive-back transition, any explicitly offscreen layer — SHALL NOT lose its stock or gain a flat fill where no ink was laid.

#### Scenario: Overscroll stretch
- **WHEN** a list printed with Riso ink is pulled past its end on Android and the stretch is held
- **THEN** the content keeps its stock behind it, with nothing filled in where no drum reached
- **AND** it prints as it did before the stretch began

#### Scenario: Isolated layer around inked content
- **WHEN** inked content is drawn inside a layer that composites offscreen
- **THEN** bare paper still shows through wherever no ink was laid
- **AND** the inked artwork prints its ink on that stock

### Requirement: A pass leaves nothing where no ink was laid
Where a drum lays no ink, a pass SHALL leave what is behind it untouched rather than covering it. On a sheet that paints no stock (`RisoPaper.None`), what comes off the press SHALL therefore be the inks alone, with the areas no drum reached left clear for whatever the print is composited over.

#### Scenario: Print on an unpainted sheet
- **WHEN** artwork is printed with Riso ink on `RisoPaper.None` and the result is composited over a background
- **THEN** the background shows wherever no drum reached
- **AND** the ink prints over it where the drums did reach
