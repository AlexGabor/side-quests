## 1. Player API and fake

- [x] 1.1 Add `bookplate/core/player/api` (`sidequests.mobile.library`) with `BookPlayer`, `PlaybackState`, `PlayerBook`, `SpeedRange` / `snapSpeed`, `SkipInterval`, current-chapter derivation; include it in `settings.gradle.kts`
- [x] 1.2 Add `bookplate/core/player/test` with `FakeBookPlayer` (ordered commands, 250 ms ticker, saves through `LibraryRepository`, injected `Clock`) and `fakePlayerModule`
- [x] 1.3 JVM tests for the api's derived values and the fake: load, play at speed, end of book, skip clamping, seek, chapter jump, speed snapping and per-book speed, saving position and last played

## 2. Player feature

- [x] 2.1 Add `bookplate/feature/player` module with `PlayerScreenState` (load on create, ui mapping, scrubbing, saved sheet, commands) and `rememberPlayerScreenState`
- [x] 2.2 `PlayerScreen` layout: top bar, title/author, shrinking cover (copied `BookCover`), chapter title, position labels, position `Track` with scrub sync, controls row
- [x] 2.3 Speed sheet with a 0.3×–3× `Track` in tenths
- [x] 2.4 Chapters sheet with section headings, durations, accent current row, jump and dismiss
- [x] 2.5 JVM tests for `PlayerScreenState` against `FakeBookPlayer` + `FakeLibraryRepository`
- [x] 2.6 Play/pause as a Riso `Button` (icon overload in `design/riso`, `Icon` color, demo)
- [x] 2.7 Position ruler glides while playing (`TrackState.moveTo(ticks: Float)`)

## 3. Mini player

- [x] 3.1 `MiniPlayerState` in `feature/home` combining last played and player state, with play/pause that loads first
- [x] 3.2 `MiniPlayer` composable in Home's bottom bar (title, `SmallProgress`, play/pause, tap to open), hidden when nothing played; bottom inset handling
- [x] 3.3 JVM tests for `MiniPlayerState`; keep `HomeStateTest` green

## 4. App wiring and docs

- [x] 4.1 `sharedApp`: depend on `feature/player` and `core/player/test`, include `fakePlayerModule`, render `PlayerScreen`, delete `Placeholders.kt`; binding test
- [x] 4.2 `known-deviations.md` row for the fake binding; `bookplate/CLAUDE.md` (tests, Player location); README feature list

## 5. Verification (Android emulator and iOS simulator)

- [x] 5.1 `./gradlew :bookplate:core:player:test:jvmTest :bookplate:feature:player:jvmTest :bookplate:feature:home:jvmTest :bookplate:sharedApp:jvmTest :bookplate:core:library:test:jvmTest`
- [x] 5.2 Maestro flows: open-book (updated), play-pause, skip, speed-sheet, chapters-sheet, mini-player
- [x] 5.3 Run all flows on the Android emulator and the iOS simulator
- [x] 5.4 Screenshots of Player, both sheets and Home with the mini player on a small and a large phone, both platforms
- [x] 5.5 Pacer still builds (`./gradlew :pacer:androidApp:assembleDebug` and the iOS framework link)
