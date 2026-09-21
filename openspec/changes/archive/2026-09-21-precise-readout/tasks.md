## 1. Link formatting (`lib/launch`)

- [x] 1.1 Add an opt-in `tenths` parameter to `LaunchParameters.formatDuration` that rounds to tenths of a second, carries correctly and appends `.d`; default stays whole seconds
- [x] 1.2 Add `LaunchParametersTest` cases for tenths (under an hour, over an hour, carry from `59.96s`, round trip through `duration()`)

## 2. Precise readout (`pacer/feature/home`)

- [x] 2.1 Add `Duration.roundedTenths(): Long` beside `roundedSeconds()` in `Distance.kt`, with the same non-finite guard
- [x] 2.2 Rewrite `displayedDistance`/`displayedPace`/`displayedTime` to quantise the exact state to ten-thousandths/tenths, unclamped, fixed-width
- [x] 2.3 Remove `Comparison`, the `*Comparison` properties and `comparison()`; titles always use `=`
- [x] 2.4 Update the slider companions' KDocs: `hundredths`/`seconds` are the rulers' quantiser only; drop the `>`/`=` wording from `ticks()`
- [x] 2.5 `PacerLaunchArgs.toLaunchParameters`: distance to four places, durations via `formatDuration(..., tenths = true)`; update the KDoc example
- [x] 2.6 `DistancePreset`: `HalfMarathon = Distance(21.0975)`, `Marathon = Distance(42.195)`; rewrite the KDoc

## 3. Tests and flows

- [x] 3.1 Update expected strings in `ShareTextTest`, `PaceCalculatorStateTest`, `PaceCalculatorLaunchTest`, `DistancePresetTest`, `SettledRunReachesTheUrlTest`, `SettledValuesTest`, `PaceCalculatorSyncTest`, and `pacer/sharedApp` tests (`RootNavigationTest`, `PacerDeepLinkTest`)
- [x] 3.2 Replace `Comparison` assertions with: the card shows the exact out-of-range value with `=` while the ruler quantiser sits at `MaxTicks`
- [x] 3.3 Add a test that a computed value between ruler lines reads precisely (42.195 km in 4:00:00 → `5:41.3`) while `PaceSliderState.seconds` is 341
- [x] 3.4 Add a test that an old-format link (`distance=42.20&pace=6:00`) still opens the same run, and that a precise link round-trips
- [x] 3.5 Update Maestro flows in `pacer/maestro/flows` (`unit_switching`, `metric_switching`, `distance_presets`, `deeplink_cold_start`, `deeplink_warm_start`) to the new strings
- [x] 3.6 Update `pacer/README.md`: out-of-range bullet (no more `>`/`<`), link example, presets at exact race distances

## 5. Hundredths of a second, trailing zeros dropped

- [x] 5.1 `LaunchParameters.formatDuration`: replace `tenths` with `fractionDigits: Int = 0`, dropping trailing zeros and the point when nothing follows; update `LaunchParametersTest`
- [x] 5.2 Readout: pace and time to the hundredth (`Duration.roundedHundredths()`), distance to the ten-thousandth, all without trailing zeros; link uses the same helpers
- [x] 5.3 Update test expectations, Maestro flows and README to the trimmed format (`42.2 km`, `6:00 min/km`, `9:39.36 min/mi`, `5:41.27 min/km`)

## 4. Verification (Android)

- [x] 4.1 `./gradlew :lib:launch:jvmTest :pacer:feature:home:jvmTest :pacer:sharedApp:jvmTest`
- [x] 4.2 Install the debug app on an Android emulator and run `maestro test -e APP_ID=com.alexgabor.pacer.debug pacer/maestro/flows`
- [x] 4.3 On the emulator: drag distance with pace computed (pace text shows hundredths, pace ruler on whole seconds); tap `M` (`42.195 km`, ruler on 42.20); switch to mi and back (no drift); push time past the ruler (text exact with `=`)
