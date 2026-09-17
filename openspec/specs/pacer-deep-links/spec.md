# pacer-deep-links Specification

## Purpose
Pacer runs and screens are addressable by URL (`https://pacer.alexgabor.com/?...` on the web and as app links, `pacer://` on mobile) so a run can be shared and restored. Parsing lives in `lib/launch` (`LaunchParameters`), screen routing in `pacer/sharedApp` (`DeepLinks.kt`), and run construction in `pacer/feature/home` (`PacerLaunchArgs`, `PaceCalculatorState.launched`). These parameters are public: old links MUST keep working.

## Requirements

### Requirement: Run parameters
A link SHALL accept these optional parameters:
- `distance`: decimal number in the link's unit
- `pace`: duration per link unit, as `M:SS`, `H:MM:SS` or plain seconds
- `time`: duration, as `M:SS`, `H:MM:SS` or plain seconds
- `metric`: `distance`, `pace` or `time` (case-insensitive), the computed metric
- `unit`: `kilometers` or `miles` (case-insensitive), defaulting to kilometers

Values that fail to parse SHALL be ignored as if absent, never causing a crash. Missing values SHALL fall back to the default run.

#### Scenario: Two values given
- **WHEN** Pacer opens `pacer://pacer?distance=10.00&time=50:00`
- **THEN** pace is the computed metric and shows `5:00 min/km`
- **AND** distance shows `10.00 km` and time `0h 50m 00s`

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

### Requirement: Screen parameter
A link SHALL select a screen via `screen=pacer` or `screen=settings` (case-insensitive). For a custom-scheme URL the host names the screen (`pacer://settings`), and a `screen` query parameter wins over the host. For http(s) URLs the host is the website and the screen comes only from the query. Opening Settings from a link SHALL place Pacer beneath it so back returns to Pacer. An unknown screen falls back to Pacer.

#### Scenario: Custom scheme host
- **WHEN** Pacer opens `pacer://settings`
- **THEN** the Settings screen is shown
- **AND** back navigates to the Pacer screen

#### Scenario: Web query
- **WHEN** the web app loads `https://pacer.alexgabor.com/?screen=settings`
- **THEN** the Settings screen is shown

### Requirement: Link replaces the open run
Opening a link while Pacer is already running SHALL replace the run on screen with the linked run.

#### Scenario: Warm start
- **WHEN** Pacer shows the default run and `pacer://pacer?distance=10.00&time=50:00` is opened
- **THEN** Pacer shows `Distance = 10.00 km`, `Time = 0h 50m 00s` and computed `Pace = 5:00 min/km`

### Requirement: URL tracks the settled run
Once the calculator comes to rest on a new run (no ruler moving, nothing changed for 300 ms), the app URL SHALL be replaced — not pushed — with all five parameters in fixed order `distance, pace, time, metric, unit`, in the unit on screen, with distance to the hundredth and durations to the second. The initial run SHALL NOT be written. Opening that URL SHALL restore the same run.

#### Scenario: Shareable URL after adjusting
- **WHEN** the user settles on 21.10 km at 5:00 min/km with time computed
- **THEN** the URL becomes `?distance=21.10&pace=5:00&time=1:45:30&metric=time&unit=kilometers`
- **AND** the browser history gains no new entry
