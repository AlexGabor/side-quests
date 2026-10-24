# Bookplate

- What the app does and its targets: [README.md](README.md). Keep the feature list in sync.
- Milestones and settled decisions: [ROADMAP.md](ROADMAP.md). Original brief: [initial-specs.md](initial-specs.md).
- Module layout follows [modules.md](../docs/architecture/modules.md); state holders and navigation follow [architecture.md](../docs/architecture/architecture.md).
- Ships on Android and iOS only. Libraries use `sidequests.mobile.library` / `sidequests.compose.mobile.library`, which add `jvm()` for tests and no `wasmJs`. Don't add a desktop or web app.
- Offline: never add the `INTERNET` permission.
- No deep links: no URL schemes, associated domains, view intent filters or `LaunchParameters`. Root navigation always starts at `Home`; `LocalDeepLink` is never provided.
- Feature modules depend on `core/*/api`, never `impl`. Bindings live in `sharedApp`.
- The Home and Player screens in `sharedApp/.../Placeholders.kt` are temporary; they move to `feature/home` and `feature/player`.
- Home's "Debug: document access" section (`sharedApp/.../debug/`, its `debugPickStoreModule` binding and the DataStore
  dependency in `sharedApp`) is a temporary M7 check of `lib/documentaccess`; M9 deletes it.

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

Tests: `./gradlew :bookplate:sharedApp:jvmTest`.

UI flows ([Maestro](https://github.com/mobile-dev-inc/maestro)) live in `maestro/flows` and run against a booted emulator or simulator. Flows use visible text, not test tags.

| Target  | Command                                                                                                                |
|---------|------------------------------------------------------------------------------------------------------------------------|
| Android | `./gradlew :bookplate:androidApp:installDebug && maestro test -e APP_ID=com.alexgabor.bookplate.debug bookplate/maestro` |
| iOS     | Build and install the Debug app on the simulator, then `maestro test -e APP_ID=com.alexgabor.bookplate bookplate/maestro` |

## Sample audio on devices

[`scripts/sample-audio.sh`](scripts/sample-audio.sh) generates a tiny library with `ffmpeg` (tones, so durations are
real) and puts it where the system pickers can see it:

| Target  | Command                                     | Where it lands                                              |
|---------|---------------------------------------------|-------------------------------------------------------------|
| Android | `bookplate/scripts/sample-audio.sh android` | `/sdcard/Audiobooks` on the running emulator (`adb push`)   |
| iOS     | `bookplate/scripts/sample-audio.sh ios`     | Files › On My iPhone › Audiobooks on the booted simulator   |

It holds `Sample Book/` (`01 - Opening.mp3` 1:05, `Disc 2/02 - Middle.mp3` 2:05, `notes.txt`) and `Single.m4b`
(0:42).

- iOS copies into the LocalStorage file provider's app-group container; `sample-audio.sh ios-path` prints it. If no
  such container exists yet, open the Files app on the simulator once.
- To make files go missing, change them from the host:
  - Android: `adb shell mv` / `adb shell rm` under `/sdcard/Audiobooks`.
  - iOS: `mv` / `rm` inside the `ios-path` folder.
- On iOS, renaming or moving the *picked* file or folder is followed by its bookmark and stays reachable. Deleting it,
  or renaming a file inside a picked folder, makes it missing. On Android, any rename makes it missing.
- The system pickers can be driven from the shell:
  - Android: `adb shell uiautomator dump` then `adb shell input tap`.
  - iOS: simulator taps. The picker opens on Recents; go to Browse › On My iPhone.

