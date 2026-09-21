## Context

`PaceCalculatorState` holds the run exactly (distance in kilometres as a `Double`, pace and time as `Duration`). Everything shown goes through one quantiser per metric in the slider companions — `DistanceSliderState.hundredths`, `PaceSliderState.seconds`, `TimeSliderState.seconds` — which both place the rulers and format the card text, and which clamp to the ruler's end. `Comparison` (`=`/`>`/`<`) exists only to admit that the clamped text isn't the real value. `PacerLaunchArgs.toLaunchParameters` rounds the link to the same grain, via `LaunchParameters.formatDuration` (whole seconds) and a private `hundredths()`.

## Goals / Non-Goals

**Goals:**
- Card text, share text and link at a finer grain than the rulers: ten-thousandths of a distance unit, hundredths of a second, with trailing zeros dropped.
- Card text is the true value, unclamped; the sign is always `=`.
- `HM`/`M` presets at their exact race distances.

**Non-Goals:**
- Changing ruler grain, ruler range or how the rulers park at their ends.
- New link parameters, or reading links differently — the parser already takes fractional values.
- Changing the stored state or the saver.

## Decisions

- **Two quantisers per metric: one for the rulers, one for the text.** The slider companions keep `ticks`/`hundredths`/`seconds` for placing rulers (still clamped). The readout quantises from the exact state itself: `(distance * 10000).roundToLong()` and a new `Duration.roundedHundredths(): Long` next to `roundedSeconds()` in `Distance.kt`. `Long`, because the text is no longer clamped and a computed time can be arbitrarily large. The "single quantiser so they cannot disagree" KDocs are rewritten: they now intentionally differ, and the ruler is always the text rounded to its grain.
  - *Alternative:* finer rulers (more subdivisions). Rejected — the rulers are for dragging, and 10000 lines per km or 100 per second isn't draggable.
- **Remove `Comparison` outright** rather than keep an always-`Equal` enum. Titles become `"Time = $displayedTime"`. `ticks()` stays because the rulers' clamp is built on it.
- **Trailing zeros dropped** (`42.2`, `6:00`, `5:41.27`), and the point with them when nothing is left after it. A whole run reads as it always did, and the extra digits only appear when there is something to say. The integer parts keep their zero-padding (`0h 05m 07s`), so only the tail of the text moves as you drag.
  - *Alternative:* fixed width (`42.2000`, `6:00.00`). Rejected — the extra zeros are noise on nearly every run, which mostly sits on ruler lines.
- **Link uses the card format.** `LaunchParameters.formatDuration` gains an opt-in `fractionDigits: Int = 0`, with trailing zeros dropped; the default stays whole seconds, so `lib/launch` stays app-agnostic and existing behaviour is unchanged for any other caller. The value is rounded before it is split into h/m/s, so `59.996s` carries to `1:00`. Distance is written by the same helper the card uses, replacing `hundredths()` in `PacerLaunchArgs.kt`. A whole run's link is therefore unchanged in spirit — `distance=42.2&pace=6:00&time=4:13:12`.
- **Presets exact.** `HalfMarathon = Distance(21.0975)`, `Marathon = Distance(42.195)`. The ruler rounds them to 21.10 / 42.20 (half-up via `roundToInt`); the card reads `21.0975` / `42.195`. `DefaultDistance` stays 42.20 km: it was chosen so 6:00/km gives exactly 4:13:12, and the default run is not a preset.

## Risks / Trade-offs

- [Ruler and text visibly disagree in the last digit, and a value past a ruler has no mark any more] → Accepted by design; the parked ruler is the only out-of-range hint.
- [Float noise from km↔mi shows up in the fourth decimal] → Rounded to the ten-thousandth, the stored value is exact and conversion is a single multiply/divide; the unit-switch round trip is covered by existing tests, updated to the new strings.
- [Longer URLs] → Negligible; old links still parse.
