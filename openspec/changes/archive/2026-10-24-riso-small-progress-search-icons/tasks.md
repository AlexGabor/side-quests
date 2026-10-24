## 1. Icons

- [x] 1.1 Add `Play`, `Pause`, `Rewind10`, `Forward10`, `Chapters`, `Speed`, `Add`, `Search`, `Close` paths to `IconPaths.kt` (commonMain, 24-unit viewport, stroke only)
- [x] 1.2 Add the entries to `IconType` and map them in `Icon.kt`

## 2. SmallProgress

- [x] 2.1 Add `components/SmallProgress.kt`: clamped progress, accent / content-at-50 % line of `lineWidth` height, RTL-aware, `progressBarRangeInfo` semantics, `risoInk(accent, content)`, with a `@Preview`
- [x] 2.2 commonTest: semantics report the clamped value (0.25, 1.7 → 1, NaN → 0)
- [x] 2.3 jvmTest: pixel test with effects off at 0 %, 40 %, 100 % and RTL, checking accent vs content-at-50 % over paper

## 3. SearchField

- [x] 3.1 Add `components/SearchField.kt` on `BasicTextField`: search icon, placeholder, clear `FlatButton(Close)` when non-empty, accent border when focused, accent cursor, single line, `ImeAction.Search` → `onSearch`, with a `@Preview`
- [x] 3.2 commonTest: typing reports edits; placeholder only when empty; clear button absent when empty, present and clearing when not; IME search calls `onSearch`

## 4. Demo

- [x] 4.1 Add a `ComponentsDemo` page to `design/risoDemo` with every icon, `SmallProgress` at 0 %, partial, 100 % and interactive, and `SearchField` empty, filled and live
- [x] 4.2 Switch between the print playground and the components page with a `ButtonGroup`

## 5. Verification

- [x] 5.1 `./gradlew :design:riso:jvmTest` passes
- [x] 5.2 `design/riso` still compiles for every target: `:design:riso:compileKotlinWasmJs`, `:design:riso:compileKotlinIosSimulatorArm64`, Android
- [x] 5.3 Run `design/risoDemo` on the Android emulator and screenshot the components page (risoDemo is Android-only)
- [x] 5.4 Pacer and Bookplate still build: `:pacer:androidApp:assembleDebug`, `:bookplate:androidApp:assembleDebug`, plus `:bookplate:sharedApp:jvmTest`
