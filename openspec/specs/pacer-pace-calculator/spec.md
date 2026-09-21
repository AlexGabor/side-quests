# pacer-pace-calculator Specification

## Purpose
Pacer's home screen: a running pace calculator where distance, pace and time are shown as scrollable rulers, one of them is computed from the other two, and results update while the user drags. Implemented in `pacer/feature/home`.

## Requirements

### Requirement: One computed metric
The calculator SHALL treat exactly one of distance, pace and time as the computed metric, derived from the other two, and SHALL let the user choose which one by tapping its card. The computed metric's ruler SHALL NOT be user-scrollable.

#### Scenario: Default run
- **WHEN** Pacer opens with no launch parameters
- **THEN** pace is the computed metric
- **AND** the run shows `Distance = 42.2 km`, `Pace = 6:00 min/km`, `Time = 4h 13m 12s`

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
- **THEN** it shows `Distance = 26.2219 mi`, `Pace = 9:39.36 min/mi` and `Time = 4h 13m 12s`
- **WHEN** the user selects `km` again
- **THEN** it shows `Distance = 42.2 km`, `Pace = 6:00 min/km` and `Time = 4h 13m 12s`

### Requirement: Out-of-range values
A value beyond the end of its ruler SHALL be kept exactly and the ruler SHALL park at its end. The card SHALL show the exact value, with `=` like any other value; it SHALL NOT be clamped to the ruler.

#### Scenario: Computed time past the ruler
- **WHEN** time is computed and the user sets 500 km at 20:00 min/km
- **THEN** the Time card reads `Time = 166h 40m 00s`
- **AND** the time rulers are parked at their end
- **AND** bringing the inputs back into range shows the true value rather than resuming from the ruler's end

### Requirement: Display format
Card text SHALL be more precise than the rulers. Distance SHALL be displayed to the ten-thousandth (`21.0975 km`), pace as minutes and zero-padded seconds, to the hundredth of a second, per unit (`5:41.27 min/km`), and time as `Hh MMm SSs`, also to the hundredth of a second (`1h 45m 30.5s`). Trailing zeros after the decimal point SHALL NOT be shown, nor the point itself when nothing follows it (`21.1 km`, `5:00 min/km`, `1h 45m 30s`). The rulers SHALL keep their own grain — distance to the hundredth, pace and time to the second — and a value between their lines SHALL rest on the nearest one while its card shows the precise figure.

#### Scenario: Card titles
- **WHEN** the run is 10 km in 50 minutes
- **THEN** the cards read `Distance = 10 km`, `Pace = 5:00 min/km`, `Time = 0h 50m 00s`

#### Scenario: Computed value between ruler lines
- **WHEN** pace is computed from 42.195 km in 4h 00m 00s
- **THEN** the Pace card reads `Pace = 5:41.27 min/km`
- **AND** the pace rulers rest on 5:41

### Requirement: State survives recreation
The run (distance, pace, time, selected metric and unit) SHALL survive configuration changes and process recreation without rounding, and saved state from an older build with unknown metric or unit names SHALL fall back to pace and kilometers.

#### Scenario: Rotation in miles
- **WHEN** the device rotates while miles are selected
- **THEN** the same run, metric and unit are shown with no change to any figure

### Requirement: Distance presets
The calculator SHALL offer distance presets that follow the selected unit. In kilometres they SHALL be `5K`, `10K`, `HM` and `M`, setting the distance to 5 km, 10 km, 21.0975 km and 42.195 km. In miles they SHALL be `5mi`, `10mi`, `HM` and `M`, setting it to 5 mi, 10 mi, 21.0975 km and 42.195 km. `HM` and `M` are the same exact race distances in both units; the distance ruler rests on its nearest hundredth. Selecting a preset SHALL behave like scrolling the distance ruler to that value: the computed metric is recomputed from the new distance and the other input is kept. The presets SHALL be hidden while Distance is the computed metric. Switching units SHALL NOT change the distance, even when the preset that set it is not offered in the new unit.

#### Scenario: Half marathon with pace computed
- **WHEN** the default run is shown and the user taps `HM`
- **THEN** it shows `Distance = 21.0975 km`, `Time = 4h 13m 12s` and `Pace = 12:00.09 min/km`

#### Scenario: Marathon
- **WHEN** the user taps `M`
- **THEN** it shows `Distance = 42.195 km`
- **AND** the distance rulers rest on 42.20

#### Scenario: Preset during a fling
- **WHEN** the distance rulers are still flinging and the user taps `HM`
- **THEN** the fling stops and the distance rulers rest on 21.10
- **AND** it shows `Distance = 21.0975 km`

#### Scenario: 10K with time computed
- **WHEN** the default run is shown, the user taps the Time card and then taps `10K`
- **THEN** it shows `Distance = 10 km`, `Pace = 6:00 min/km` and `Time = 1h 00m 00s`

#### Scenario: Presets in miles
- **WHEN** the user selects `mi`
- **THEN** the presets are `5mi`, `10mi`, `HM` and `M`
- **AND** `5K` and `10K` are not shown

#### Scenario: 10 miles with time computed
- **WHEN** the default run is shown, the user selects `mi`, taps the Time card and then taps `10mi`
- **THEN** it shows `Distance = 10 mi`, `Pace = 9:39.36 min/mi` and `Time = 1h 36m 33.64s`

#### Scenario: Half marathon in miles
- **WHEN** the user selects `mi` and taps `HM`
- **THEN** it shows `Distance = 13.1094 mi`

#### Scenario: Switching units after a unit-specific preset
- **WHEN** the user taps `5K` and then selects `mi`
- **THEN** it shows `Distance = 3.1069 mi`
- **WHEN** the user selects `km` again
- **THEN** it shows `Distance = 5 km`

#### Scenario: Hidden while distance is computed
- **WHEN** the user taps the Distance card
- **THEN** the presets are not shown
- **WHEN** the user taps the Pace card
- **THEN** the presets are shown again

### Requirement: Share the run
On Android and iOS the Pacer header SHALL show a Share icon, left of the Settings icon. Its glyph SHALL follow the platform: on Android, one dot branching to two dots; on iOS, an up arrow rising out of an open box. Platforms without a share sheet (web and desktop) SHALL NOT show the icon. Tapping it SHALL open the platform share sheet with plain text of four lines:
1. The Time card title.
2. The Distance card title.
3. The Pace card title.
4. A link to `https://pacer.alexgabor.com/` whose query holds the run's `distance`, `pace`, `time`, `metric` and `unit`.

Each title SHALL be exactly as the card displays it. The link SHALL use the same parameters and formatting the web app writes to its own address, so opening it shows the same run. Values travel at the precision of the cards: the distance to the ten-thousandth, and pace and time to the hundredth of a second, without trailing zeros. Opening the link then recomputes the computed metric from those rounded inputs, so it MAY differ from the shared text by that rounding.

#### Scenario: Sharing the default run
- **WHEN** Pacer opens with no launch parameters and the user taps Share
- **THEN** the share sheet receives:
  ```
  Time = 4h 13m 12s
  Distance = 42.2 km
  Pace = 6:00 min/km
  https://pacer.alexgabor.com/?distance=42.2&pace=6:00&time=4:13:12&metric=pace&unit=kilometers
  ```

#### Scenario: Sharing in miles
- **WHEN** the user selects `mi` and taps Share
- **THEN** the text reads `Distance = 26.2219 mi` and `Pace = 9:39.36 min/mi`
- **AND** the link ends with `unit=miles`

#### Scenario: Sharing an out-of-range value
- **WHEN** the computed time is past the end of the time ruler and the user taps Share
- **THEN** the shared text's first line is the Time card title with the exact time and `=`

#### Scenario: The link opens the same run
- **WHEN** the shared link is opened in the web app, or as a deep link on Android or iOS
- **THEN** the two input cards show the same titles as the shared text
- **AND** the same metric is computed and the same unit is selected

#### Scenario: Computed value recomputed from rounded inputs
- **WHEN** the user selects `mi`, taps the Time card, taps `HM` and shares
- **THEN** the shared text reads `Time = 2h 06m 35.1s`, `Distance = 13.1094 mi` and `Pace = 9:39.36 min/mi`
- **AND** opening the link shows `Pace = 9:39.36 min/mi` and `Distance = 13.1094 mi`, with a time recomputed from those rounded inputs

#### Scenario: No share sheet
- **WHEN** Pacer runs on the web or on desktop
- **THEN** the header shows only the Settings icon
