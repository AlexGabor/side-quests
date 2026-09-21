## MODIFIED Requirements

### Requirement: Run parameters
A link SHALL accept these optional parameters:
- `distance`: decimal number in the link's unit
- `pace`: duration per link unit, as `M:SS`, `H:MM:SS` or plain seconds; the seconds may be fractional (`5:41.27`)
- `time`: duration, as `M:SS`, `H:MM:SS` or plain seconds; the seconds may be fractional (`3:59:59.5`)
- `metric`: `distance`, `pace` or `time` (case-insensitive), the computed metric
- `unit`: `kilometers` or `miles` (case-insensitive), defaulting to kilometers

Values that fail to parse SHALL be ignored as if absent, never causing a crash. Missing values SHALL fall back to the default run. Links written before this precision was added (`distance=42.20&pace=6:00`) SHALL keep opening the same run.

#### Scenario: Two values given
- **WHEN** Pacer opens `pacer://pacer?distance=10.00&time=50:00`
- **THEN** pace is the computed metric and shows `5:00 min/km`
- **AND** distance shows `10 km` and time `0h 50m 00s`

#### Scenario: Precise values given
- **WHEN** Pacer opens `pacer://pacer?distance=42.195&time=4:00:00`
- **THEN** distance shows `42.195 km` and the computed pace shows `5:41.27 min/km`

#### Scenario: Malformed value
- **WHEN** Pacer opens a link with `pace=abc`
- **THEN** pace is treated as absent and the app starts normally

#### Scenario: Durations are unbounded
- **WHEN** a link has `time=90:00`
- **THEN** time is ninety minutes

### Requirement: Consistent run from a link
The computed metric SHALL be `metric` when given; otherwise, when exactly two of distance, pace and time are given, the missing one; otherwise pace. The computed metric SHALL be recomputed from the other two even if the link supplied a value for it.

#### Scenario: Metric overrides a supplied value
- **WHEN** a link has `distance=10&pace=5:00&time=1:00:00&metric=time`
- **THEN** time is recomputed as `0h 50m 00s`

#### Scenario: Link in miles
- **WHEN** a link has `distance=13.11&pace=8:00&unit=miles`
- **THEN** miles are selected and distance and pace are read as miles and minutes per mile

### Requirement: Link replaces the open run
Opening a link while Pacer is already running SHALL replace the run on screen with the linked run.

#### Scenario: Warm start
- **WHEN** Pacer shows the default run and `pacer://pacer?distance=10.00&time=50:00` is opened
- **THEN** Pacer shows `Distance = 10 km`, `Time = 0h 50m 00s` and computed `Pace = 5:00 min/km`

### Requirement: URL tracks the settled run
Once the calculator comes to rest on a new run (no ruler moving, nothing changed for 300 ms), the app URL SHALL be replaced — not pushed — with all five parameters in fixed order `distance, pace, time, metric, unit`, in the unit on screen, with distance to the ten-thousandth and durations to the hundredth of a second, as the cards show them, without trailing zeros. The initial run SHALL NOT be written. Opening that URL SHALL restore the same run.

#### Scenario: Shareable URL after adjusting
- **WHEN** the user settles on 21.10 km at 5:00 min/km with time computed
- **THEN** the URL becomes `?distance=21.1&pace=5:00&time=1:45:30&metric=time&unit=kilometers`
- **AND** the browser history gains no new entry
