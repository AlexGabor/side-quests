# Pacer

- What the app does and its targets: [README.md](README.md). Keep the feature list in sync.
- Module layout follows [modules.md](../docs/architecture/modules.md); state holders, navigation and deep links follow [architecture.md](../docs/architecture/architecture.md).
- Feature modules depend on `core/*/api`, never `impl`. Bindings live in `sharedApp`.
- `feature/*` depending on `core:settings:test` for previews is a known deviation; don't copy it. See [known-deviations.md](../docs/architecture/known-deviations.md).
- Launch/deep link parameters (`distance`, `pace`, `time`, `metric`, `unit`, `screen`) are public URLs: keep old links working when changing them. Parsing is in `sharedApp/.../DeepLinks.kt` and `feature/home/.../PacerLaunchArgs.kt`.

## Running

| Target  | Command                                                  |
|---------|----------------------------------------------------------|
| Web     | `pacer-web` preview in `.claude/launch.json` (port 8080) |
| Desktop | `./gradlew :pacer:desktopApp:run`                        |
| Android | `./gradlew :pacer:androidApp:installDebug`               |
| iOS     | Open `iosApp/iosApp.xcodeproj` in Xcode                  |

Tests: `./gradlew :pacer:feature:home:jvmTest :pacer:sharedApp:jvmTest`.
