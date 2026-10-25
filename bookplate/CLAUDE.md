# Bookplate

- What the app does and its targets: [README.md](README.md). Keep the feature list in sync.
- Milestones and settled decisions: [ROADMAP.md](ROADMAP.md). Original brief: [initial-specs.md](initial-specs.md).
- Module layout follows [modules.md](../docs/architecture/modules.md); state holders and navigation follow [architecture.md](../docs/architecture/architecture.md).
- Ships on Android and iOS only. Libraries use `sidequests.mobile.library` / `sidequests.compose.mobile.library`, which add `jvm()` for tests and no `wasmJs`. Don't add a desktop or web app.
- Offline: never add the `INTERNET` permission.
- No deep links: no URL schemes, associated domains, view intent filters or `LaunchParameters`. Root navigation always starts at `Home`; `LocalDeepLink` is never provided.
- Feature modules depend on `core/*/api`, never `impl`. Bindings live in `sharedApp`.
- Home lives in `feature/home` (`HomeScreen`, state holders `HomeState` and `MiniPlayerState`). Player lives in
  `feature/player` (`PlayerScreen`, state holder `PlayerScreenState`, speed and chapters sheets).
- Playback goes through `core/player/api`'s `BookPlayer`: one `PlaybackState` flow, fire-and-forget commands,
  book-wide positions. `core/player/impl` holds `EngineBookPlayer` (common: timeline across files, chapters,
  saving and restoring progress) over a platform `AudioOutput`. On Android that is Media3 in `PlaybackService`
  (a `MediaSessionService`), reading files through `lib/documentaccess`. `sharedApp` binds the engine on Android and,
  until M12, `FakeBookPlayer` from `core/player/test` (a ticking clock) on iOS; see `known-deviations.md`.

## Running

| Target  | Command                                                                                     |
|---------|---------------------------------------------------------------------------------------------|
| Android | `./gradlew :bookplate:androidApp:installDebug`                                              |
| iOS     | Open `iosApp/iosApp.xcodeproj` in Xcode, or build from the command line (below)             |

iOS from the command line, on a booted simulator:

```
xcodebuild -project bookplate/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=<simulator>' -derivedDataPath bookplate/iosApp/build build
xcrun simctl install booted bookplate/iosApp/build/Build/Products/Debug-iphonesimulator/Bookplate.app
```

Tests: `./gradlew :bookplate:feature:home:jvmTest :bookplate:feature:player:jvmTest :bookplate:sharedApp:jvmTest
:bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest :bookplate:core:player:api:jvmTest
:bookplate:core:player:test:jvmTest :bookplate:core:player:impl:jvmTest`.

The library database (`core/library/impl`, Room 3) exports its schema to `core/library/impl/schemas`; commit the new
JSON whenever the schema version changes, and add a migration for it.

UI flows ([Maestro](https://github.com/mobile-dev-inc/maestro)) live in `maestro/flows` and run against a booted emulator or simulator. Flows use visible text, not test tags.

| Target  | Command                                                                                                                |
|---------|------------------------------------------------------------------------------------------------------------------------|
| Android | `./gradlew :bookplate:androidApp:installDebug && maestro test -e APP_ID=com.alexgabor.bookplate.debug bookplate/maestro` |
| iOS     | Build and install the Debug app on the simulator, then `maestro test -e APP_ID=com.alexgabor.bookplate bookplate/maestro` |

While a book plays, Player's position ruler glides, so the screen never settles: give taps made during playback
`waitToSettleTimeoutMs`, and don't assert an exact position after one (on a busy emulator a step can take seconds).

Every flow except `launch` adds books, so put the sample audio (below) on the device first. Flows start from
`clearState: true`, so the library is empty; the sample files live outside the app and survive that. Shared steps are
in `maestro/subflows` (outside `flows/`, so they don't run on their own):

- `add-sample-folder.yaml` / `add-single-file.yaml`: Home's add sheet, then the system picker.
- The pickers open wherever they were last, so each platform's subflow first gets to the `Audiobooks` folder:
  - Android (DocumentsUI): tap the `Audiobooks` breadcrumb or folder, or go through "Show roots" and the storage root;
    a folder needs "USE THIS FOLDER" then "ALLOW".
  - iOS (Files picker): tap "Browse" (again if "Locations" isn't showing), "On My iPhone", then the folder cell.
    Folder and file cells are labelled "<name>, <n> items" and "<name>, <ext>, …"; tapping the bare name doesn't
    open them. A folder needs "Open"; a file is picked on tap.

## Sample audio on devices

[`scripts/sample-audio.sh`](scripts/sample-audio.sh) generates a tiny library with `ffmpeg` (tones, so durations are
real) and puts it where the system pickers can see it:

| Target  | Command                                     | Where it lands                                              |
|---------|---------------------------------------------|-------------------------------------------------------------|
| Android | `bookplate/scripts/sample-audio.sh android` | `/sdcard/Audiobooks` on the running emulator (`adb push`)   |
| iOS     | `bookplate/scripts/sample-audio.sh ios`     | Files › On My iPhone › Audiobooks on the booted simulator   |

It holds `Sample Book/` by `Sample Author` (`01 - Opening.mp3` 1:05, `Disc 2/02 - Middle.mp3` 2:05, `notes.txt`,
`cover.jpg`) and `Single.m4b` (0:42, no tags but its title).

- iOS copies into the LocalStorage file provider's app-group container; `sample-audio.sh ios-path` prints it. If no
  such container exists yet, open the Files app on the simulator once.
- To make files go missing, change them from the host:
  - Android: `adb shell mv` / `adb shell rm` under `/sdcard/Audiobooks`.
  - iOS: `mv` / `rm` inside the `ios-path` folder.
- On iOS, renaming or moving the *picked* file or folder is followed by its bookmark and stays reachable. Deleting it,
  or renaming a file inside a picked folder, makes it missing. On Android, any rename makes it missing.
- The system pickers can also be driven from the shell:
  - Android: `adb shell uiautomator dump` then `adb shell input tap`.
  - iOS: `maestro hierarchy` to see the picker's labels, then simulator taps.

