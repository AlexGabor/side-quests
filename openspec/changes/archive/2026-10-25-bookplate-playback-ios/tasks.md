## 1. iOS engine

- [x] 1.1 `DocumentResourceLoader`: private-scheme URLs per load generation, content info (size, byte ranges, UTI from `AudioMetadataReader.detect`), chunked data answers with cancellation, sources held while in use and closed on release
- [x] 1.2 `AvAudioOutput` over `AVQueuePlayer`: two-item window, load / play / pause / seek within and across files with a pending seek, pitch-preserving speed, status (position, playing, ended, failed), `changes` from player and item notifications
- [x] 1.3 Audio session: `.playback` / `.spokenAudio` on play; interruption began / ended (resume when asked); route change `OldDeviceUnavailable` pauses
- [x] 1.4 iOS `playerModule` with the `UIApplicationDidEnterBackgroundNotification` background flow
- [x] 1.5 `AvAudioOutputTest` (`iosTest`): interruption and route-change notifications posted by hand, since the simulator can't produce them

## 2. App wiring and docs

- [x] 2.1 `sharedApp`: iOS binds `playerModule`; `core/player/test` dependency only on `jvmMain`; binding test still green
- [x] 2.2 `iosApp/Info.plist`: `UIBackgroundModes` = `audio`
- [x] 2.3 README feature list, `bookplate/CLAUDE.md`, `known-deviations.md` row removed

## 3. Verification

- [x] 3.1 `./gradlew :bookplate:core:player:impl:iosSimulatorArm64Test :bookplate:core:player:impl:jvmTest :bookplate:core:player:test:jvmTest :bookplate:core:player:api:jvmTest :bookplate:feature:player:jvmTest :bookplate:feature:home:jvmTest :bookplate:sharedApp:jvmTest :bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest`
- [x] 3.2 iOS app builds (`xcodebuild`); Android app still builds
- [x] 3.3 Simulator: multi-file book plays across the boundary; chapter and progress update in Player and on Home; speed; resume position and speed after terminate and relaunch; playback continues in the background and locked
- [x] 3.4 Simulator: interruption and route change, as far as the simulator allows
- [x] 3.5 Maestro flows on iOS (adjusted for real audio where needed) and on Android
- [x] 3.6 Pacer still builds
