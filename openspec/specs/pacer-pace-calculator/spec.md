# pacer-pace-calculator Specification

## Purpose
Pacer's home screen: a running pace calculator where distance, pace and time are shown as scrollable rulers, one of them is computed from the other two, and results update while the user drags. Implemented in `pacer/feature/home`.

## Requirements

### Requirement: One computed metric
The calculator SHALL treat exactly one of distance, pace and time as the computed metric, derived from the other two, and SHALL let the user choose which one by tapping its card. The computed metric's ruler SHALL NOT be user-scrollable.

#### Scenario: Default run
- **WHEN** Pacer opens with no launch parameters
- **THEN** pace is the computed metric
- **AND** the run shows `Distance = 42.20 km`, `Pace = 6:00 min/km`, `Time = 4h 13m 12s`

#### Scenario: Selecting another metric
- **WHEN** the user taps the Time card
- **THEN** time becomes the computed metric and is recomputed from distance and pace
- **AND** the Time ruler can no longer be scrolled while distance and pace can

### Requirement: Live results
The computed value SHALL update while the user drags or flings a ruler, not only when the gesture ends, and SHALL reflect the final value the ruler settles on.

#### Scenario: Dragging distance with pace computed
- **WHEN** the user drags the distance ruler while pace is computed
- **THEN** the pace card and ruler update on every change of distance during the gesture

### Requirement: Degenerate inputs
The calculator SHALL NOT compute a distance from a zero pace, nor a pace from a zero distance, and SHALL never store non-finite or negative values.

#### Scenario: Zero distance with pace computed
- **WHEN** distance is set to 0 while pace is computed
- **THEN** pace keeps its previous value

### Requirement: Units
The user SHALL be able to switch between kilometers and miles. Switching units SHALL change only how the run is displayed; switching back SHALL restore exactly the original figures without rounding drift. Pace is shown per selected unit (`min/km` or `min/mi`).

#### Scenario: Switching to miles and back
- **WHEN** the default run is shown and the user selects `mi`
- **THEN** it shows `Distance = 26.22 mi`, `Pace = 9:39 min/mi` and `Time = 4h 13m 12s`
- **WHEN** the user selects `km` again
- **THEN** it shows `Distance = 42.20 km`, `Pace = 6:00 min/km` and `Time = 4h 13m 12s`

### Requirement: Out-of-range values
A value beyond the end of its ruler SHALL be kept exactly, the ruler SHALL park at its end, and the card SHALL mark the value with `>` (above the range) or `<` (below it) instead of `=`.

#### Scenario: Computed time past the ruler
- **WHEN** distance and pace produce a time greater than the time ruler can show
- **THEN** the Time card reads `Time > ...` with the exact computed time
- **AND** bringing the inputs back into range shows the true value rather than resuming from the ruler's end

### Requirement: Display format
Distance SHALL be displayed to the hundredth (`21.10 km`), pace as minutes and zero-padded seconds per unit (`5:00 min/km`), and time as `Hh MMm SSs` (`1h 45m 30s`).

#### Scenario: Card titles
- **WHEN** the run is 10 km in 50 minutes
- **THEN** the cards read `Distance = 10.00 km`, `Pace = 5:00 min/km`, `Time = 0h 50m 00s`

### Requirement: State survives recreation
The run (distance, pace, time, selected metric and unit) SHALL survive configuration changes and process recreation without rounding, and saved state from an older build with unknown metric or unit names SHALL fall back to pace and kilometers.

#### Scenario: Rotation in miles
- **WHEN** the device rotates while miles are selected
- **THEN** the same run, metric and unit are shown with no change to any figure

### Requirement: Distance presets
The calculator SHALL offer distance presets that follow the selected unit. In kilometres they SHALL be `5K`, `10K`, `HM` and `M`, setting the distance to 5.00 km, 10.00 km, 21.10 km and 42.20 km. In miles they SHALL be `5mi`, `10mi`, `HM` and `M`, setting it to 5.00 mi, 10.00 mi, 21.10 km and 42.20 km. `HM` and `M` are the same distances in both units. Selecting a preset SHALL behave like scrolling the distance ruler to that value: the computed metric is recomputed from the new distance and the other input is kept. The presets SHALL be hidden while Distance is the computed metric. Switching units SHALL NOT change the distance, even when the preset that set it is not offered in the new unit.

#### Scenario: Half marathon with pace computed
- **WHEN** the default run is shown and the user taps `HM`
- **THEN** it shows `Distance = 21.10 km`, `Time = 4h 13m 12s` and `Pace = 12:00 min/km`

#### Scenario: 10K with time computed
- **WHEN** the default run is shown, the user taps the Time card and then taps `10K`
- **THEN** it shows `Distance = 10.00 km`, `Pace = 6:00 min/km` and `Time = 1h 00m 00s`

#### Scenario: Presets in miles
- **WHEN** the user selects `mi`
- **THEN** the presets are `5mi`, `10mi`, `HM` and `M`
- **AND** `5K` and `10K` are not shown

#### Scenario: 10 miles with time computed
- **WHEN** the default run is shown, the user selects `mi`, taps the Time card and then taps `10mi`
- **THEN** it shows `Distance = 10.00 mi`, `Pace = 9:39 min/mi` and `Time = 1h 36m 34s`

#### Scenario: Half marathon in miles
- **WHEN** the user selects `mi` and taps `HM`
- **THEN** it shows `Distance = 13.11 mi`

#### Scenario: Switching units after a unit-specific preset
- **WHEN** the user taps `5K` and then selects `mi`
- **THEN** it shows `Distance = 3.11 mi`
- **WHEN** the user selects `km` again
- **THEN** it shows `Distance = 5.00 km`

#### Scenario: Hidden while distance is computed
- **WHEN** the user taps the Distance card
- **THEN** the presets are not shown
- **WHEN** the user taps the Pace card
- **THEN** the presets are shown again
