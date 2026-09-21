## Why

Every Pacer figure is cut to its ruler's grain before it is shown: distance to the hundredth, pace and time to the whole second. The rulers need that grain to be draggable, but the cards don't, and the rounding hides what the calculator actually worked out — a computed pace of 5:41.27 reads as 5:41, and the marathon preset has to be the rounded 42.20 km rather than the real 42.195 km. The cards should show the precise run; the rulers can keep snapping.

## What Changes

- Card text shows distance to the **ten-thousandth** (so `21.0975 km` shows in full) and pace and time to the **hundredth of a second** (`5:41.27 min/km`, `1h 45m 30.5s`). Trailing zeros after the point are dropped, and the point with them: `42.2 km`, `6:00 min/km`, `4h 13m 12s`. The rulers keep their current grain (hundredths, seconds) and still round to it.
- Card text is **never clamped** to its ruler: a value past the end of a ruler shows its true figure, and the ruler parks at its end.
- **BREAKING (UI):** the card sign is always `=`. The `>`/`<` marks for out-of-range values are removed.
- The `HM` and `M` presets set the exact race distances, 21.0975 km and 42.195 km, instead of 21.10 km and 42.20 km.
- The share text follows the cards. The link and the URL the web app writes carry the same precision: `distance=21.0975&pace=5:41.27&time=2:00:00`, and a whole run still reads `distance=42.2&pace=6:00&time=4:13:12`.
- Link parameters: no new or renamed parameters. `distance`, `pace` and `time` are now **written** with a fractional part when they have one; the parser already accepts fractional values, so old links (`distance=42.20&pace=6:00`) keep working unchanged.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `pacer-pace-calculator`: display format, units, out-of-range values, distance presets and share-the-run requirements change to the precise readout, the always-`=` sign and the exact race presets.
- `pacer-deep-links`: the URL written for the settled run carries distance to the ten-thousandth and durations to the hundredth of a second; scenario strings change.

## Impact

- `pacer/feature/home`: `PaceCalculatorState` readout and titles (`Comparison` removed), `Distance.kt` rounding helpers, `DistancePreset`, `PacerLaunchArgs.toLaunchParameters`, slider companion KDocs, and their tests.
- `lib/launch`: `LaunchParameters.formatDuration` gains an opt-in number of fractional digits for the seconds, trailing zeros dropped; the default stays whole seconds.
- `pacer/sharedApp` tests and `pacer/maestro/flows` that assert card text.
- `pacer/README.md` feature list (out-of-range marks, link example).
