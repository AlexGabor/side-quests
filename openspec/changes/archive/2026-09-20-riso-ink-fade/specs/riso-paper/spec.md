# Spec Delta

## ADDED Requirements

### Requirement: Ink fades by laying down less of it

Inked content SHALL be able to fade by coverage rather than by transparency. A fade of `a` applied
to a region SHALL make every pass inside it lay down the ink it would have laid at `a` times the
coverage, as though the artwork itself had been drawn that much fainter. The ink SHALL keep its full
strength where it lands, so a screened region prints smaller dots rather than paler ones, and the
stock between them SHALL be the stock, not a wash of ink over it. A fade of `1` SHALL print exactly
what no fade prints; a fade of `0` SHALL lay down no ink at all. Values outside `0..1` SHALL be
clamped.

#### Scenario: Halfway through a fade

- **WHEN** screened artwork is printed at a fade of `0.5`
- **THEN** its dots are smaller than at full strength
- **AND** the ink within a dot is as dark as it is at full strength
- **AND** the paper between the dots shows the stock unchanged

#### Scenario: Faded to nothing

- **WHEN** inked content is printed at a fade of `0`
- **THEN** nothing is laid down for it, and the sheet shows what it would show with the content
  absent

#### Scenario: Fade of one

- **WHEN** inked content is printed at a fade of `1`
- **THEN** the result matches the same content printed with no fade at all

### Requirement: A fade reaches every pass beneath it

A fade SHALL apply to all inked content inside the region it is applied to, including passes nested
within other passes, and SHALL NOT affect anything outside that region. Nested fades SHALL multiply,
so a region at `0.5` inside a region at `0.5` prints at `0.25`. A knockout inside a fading region
SHALL keep cutting its hole in full: what thins is the ink laid down, not the frisket that holds it
off the sheet.

#### Scenario: A component that owns its own ink

- **WHEN** a fade is applied around a component that prints on drums of its own
- **THEN** that component's ink fades with it

#### Scenario: Nested fades

- **WHEN** a region at a fade of `0.5` contains a region at a fade of `0.5`
- **THEN** the inner region prints at a coverage of `0.25`

#### Scenario: Neighbour outside the fade

- **WHEN** a fade is applied to one composable
- **THEN** inked content beside it prints unchanged

#### Scenario: Reversed-out artwork while fading

- **WHEN** a pass containing a knockout is printed at a partial fade
- **THEN** the knocked-out region shows bare stock, not partly inked stock
- **AND** the ink around it is thinner than at full strength

### Requirement: Content the press does not print can take the fade as transparency

A fade thins ink, and content that is not printed with Riso ink has no ink to thin — an image or a
plain fill inside a fading region SHALL therefore be unaffected by the fade unless it asks for it.
Such content SHALL be able to take the enclosing fade as plain transparency instead, so that a
region holding both inked and un-inked content can disappear as one. Content that asks for it
outside any fade SHALL be drawn unchanged. Inked content SHALL NOT ask for it: the fade would then
apply twice, once as coverage and once as transparency.

With effects disabled a fade is already plain transparency over everything inside it, so asking for
it SHALL do nothing rather than fade that content a second time.

#### Scenario: Photo in a fading region

- **WHEN** an image inside a fading region takes the fade as transparency
- **THEN** it is drawn at that alpha, reaching nothing when the fade reaches nothing

#### Scenario: Photo that does not ask

- **WHEN** an image inside a fading region does not take the fade
- **THEN** it is drawn at full strength however far the fade has gone

#### Scenario: Asking outside any fade

- **WHEN** content takes the fade as transparency with no enclosing fade
- **THEN** it is drawn unchanged

#### Scenario: Effects off

- **WHEN** Riso effects are turned off and content inside a fading region takes the fade as
  transparency
- **THEN** it is drawn at the fade's alpha once, not twice

### Requirement: Content fades in and out of the layout

The design system SHALL offer showing and hiding content as that dissolve. Content being shown SHALL
fade in from no ink; content being hidden SHALL fade out to none. While either is in progress the
content SHALL stay composed and keep its place in the layout, so nothing jumps part-way through.
Once hidden, it SHALL take up no space and SHALL NOT be composed. Content on its way out SHALL NOT
respond to pointer input, so a control that is nearly gone cannot be activated.

#### Scenario: Hiding

- **WHEN** content shown this way is hidden
- **THEN** its ink thins away to nothing
- **AND** its space in the layout is released only once the dissolve has finished

#### Scenario: Showing

- **WHEN** hidden content is shown
- **THEN** it takes its place in the layout and its ink comes up from nothing to full strength

#### Scenario: Tapping content that is leaving

- **WHEN** a button inside content that is fading out is tapped
- **THEN** nothing is activated

#### Scenario: Settled out of sight

- **WHEN** the fade out has finished
- **THEN** the content occupies no space and is not composed

### Requirement: Fading with effects disabled

With Riso effects disabled there is no coverage to thin, so a fade SHALL instead show the content at
that alpha in its authored colours, and hiding SHALL still end with the content gone from the layout.

#### Scenario: Effects off, halfway through

- **WHEN** Riso effects are turned off and content is shown at a fade of `0.5`
- **THEN** it is drawn in its own colours at half opacity

#### Scenario: Effects off, hidden

- **WHEN** Riso effects are turned off and content shown this way is hidden
- **THEN** it ends up absent from the layout, as it does with effects on
