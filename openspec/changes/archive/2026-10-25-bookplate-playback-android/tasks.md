## 1. Module and common engine

- [x] 1.1 Add `bookplate/core/player/impl` (`sidequests.mobile.library`), include it in `settings.gradle.kts`; Media3 and lifecycle-process in `libs.versions.toml`
- [x] 1.2 `BookTimeline` (locate / positionOf, zero-duration files skipped, clamping) with JVM tests
- [x] 1.3 `AudioOutput` port and `EngineBookPlayer` (ordered commands, polling ticker, reconcile on changes, save every 5 s / pause / seek / skip / jump / switch / background, restore on load, end of book)
- [x] 1.4 `FakeAudioOutput` in commonTest and JVM tests: load and restore, play across a file boundary, skip and clamp across files, chapter tracking and jumps, platform pause / resume / end / failure, saving (periodic, pause, seek, background), speed per book, end of book and play from the end

## 2. Android engine

- [x] 2.1 `DocumentDataSource` over `DocumentAccess`
- [x] 2.2 `PlaybackService` (`MediaSessionService`, ExoPlayer with speech focus, becoming-noisy, wake lock, extractors; session callback resolving media ids; session activity)
- [x] 2.3 `Media3AudioOutput` over a `MediaController` (connect, reconnect with the last playlist, status, changes)
- [x] 2.4 Manifest: foreground-service permissions, `WAKE_LOCK`, service with `mediaPlayback` type; `playerModule` with the `ProcessLifecycleOwner` background flow

## 3. App wiring and docs

- [x] 3.1 `sharedApp`: `platformPlayerModule` (Android engine; iOS and JVM fake), dependencies moved; binding test still green
- [x] 3.2 README feature list, `bookplate/CLAUDE.md` (module, test command), `known-deviations.md` row narrowed to iOS

## 4. Verification (Android emulator, iOS simulator for the fake path)

- [x] 4.1 `./gradlew :bookplate:core:player:impl:jvmTest :bookplate:core:player:test:jvmTest :bookplate:core:player:api:jvmTest :bookplate:feature:player:jvmTest :bookplate:feature:home:jvmTest :bookplate:sharedApp:jvmTest :bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest`
- [x] 4.2 Merged Android manifest has no `INTERNET`; `./gradlew :bookplate:androidApp:assembleDebug` and the iOS framework build
- [x] 4.3 Emulator: multi-file book plays across the boundary; chapter and progress update in Player and on Home; speed; resume position and speed after force-stop; playback continues with the screen off and in the background; notification shows
- [x] 4.4 Maestro flows on Android (adjusted for real audio where needed) and on iOS (fake path)
- [x] 4.5 Pacer still builds

## 5. Opening another book while one plays

- [x] 5.1 `PlayerScreenState`: preview a book the player doesn't hold while another plays (library flow), load otherwise; "Play" loads and plays; seek/skip/jump/speed write to the library while previewing
- [x] 5.2 State-holder tests for the preview (shows B, A keeps playing, Play takes over, skip/seek/jump/speed saved to B, loads when nothing plays); `feature/player` and `feature/home` JVM tests
- [x] 5.3 Emulator: A playing, open B → A keeps playing; Play on B switches; Maestro on Android and iOS
