# Bookplate

- What the app does and its targets: [README.md](README.md). Keep the feature list in sync.
- Milestones and settled decisions: [ROADMAP.md](ROADMAP.md). Original brief: [initial-specs.md](initial-specs.md).
- Module layout follows [modules.md](../docs/architecture/modules.md); state holders and navigation follow [architecture.md](../docs/architecture/architecture.md).
- Ships on Android and iOS only. Libraries use `sidequests.mobile.library` / `sidequests.compose.mobile.library`, which add `jvm()` for tests and no `wasmJs`. Don't add a desktop or web app.
- Offline: never add the `INTERNET` permission.
- No deep links: no URL schemes, associated domains, view intent filters or `LaunchParameters`. Root navigation always starts at `Home`; `LocalDeepLink` is never provided.
- Feature modules depend on `core/*/api`, never `impl`. Bindings live in `sharedApp`.
- The Home and Player screens in `sharedApp/.../Placeholders.kt` are temporary; they move to `feature/home` and `feature/player`.

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
