## 1. Common

- [x] 1.1 `PanelInfo` + `PlaybackState.panelInfo()` (subtitle: chapter title unless blank or the book's title, else author) with JVM tests
- [x] 1.2 `PanelUpdates` (push on any non-position change, or when the position drifts more than 1 s from the extrapolation) with JVM tests on a `TestTimeSource`
- [x] 1.3 `EngineBookPlayer`: a reconcile triggered by `changes` saves the position; JVM tests for a platform seek while paused and while playing

## 2. Android

- [x] 2.1 One item per book: `BookMediaSourceFactory` (`ConcatenatingMediaSource2` of the files), `onAddMediaItems` resolving the `bookplate-book:` item, `Media3AudioOutput` sending one item and mapping file positions through the timeline's periods; watch `EVENT_POSITION_DISCONTINUITY`
- [x] 2.2 `PanelPlayer` (`ForwardingSimpleBasePlayer`): panel metadata (title, subtitle, cover `artworkUri`) for the matching book, no previous/next commands; `PlaybackService` feeds it from `BookPlayer.state`
- [x] 2.3 Session controls: 10 s seek increments, foreground timeout 0 and `stopSelf` on dismissal while paused, media button preferences (skip back/forward 10 in the back/forward slots), `onMediaButtonEvent` mapping next/previous/fast-forward/rewind to ±10 s
- [x] 2.4 Notification tap: `SHOW_PLAYING` session activity intent; `MainActivity` → `App(showPlaying)` → `RootNavigation` opens Player for the loaded book; `RootNavigationTest` cases

## 3. iOS

- [x] 3.1 `NowPlayingPanel`: Now Playing info through `PanelUpdates`, artwork cached per cover path, cleared with no book
- [x] 3.2 Remote commands: play, pause, togglePlayPause, skip ±10 s, changePlaybackPosition; next/previous track, seek and rate commands disabled; started from `playerModule`

## 4. Docs

- [x] 4.1 README feature list and `bookplate/CLAUDE.md`

## 5. Verification (Android emulator and iOS simulator, as the milestone asks)

- [x] 5.1 `./gradlew :bookplate:core:player:impl:jvmTest :bookplate:core:player:test:jvmTest :bookplate:core:player:api:jvmTest :bookplate:feature:player:jvmTest :bookplate:feature:home:jvmTest :bookplate:sharedApp:jvmTest :bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest :bookplate:core:player:impl:iosSimulatorArm64Test`
- [x] 5.2 Android emulator: notification and lock screen show cover, title, chapter; play/pause and ±10 s; seek bar; chapter change; notification tap opens Player; dismissal while paused; `adb shell input keyevent` media keys
- [x] 5.3 iOS simulator: Now Playing on the lock screen and in Control Center with artwork and chapter; remote commands as far as the simulator allows
- [x] 5.4 Maestro flows on Android and iOS; Pacer still builds
