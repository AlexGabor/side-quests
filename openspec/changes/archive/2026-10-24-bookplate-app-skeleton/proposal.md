## Why

Bookplate is a new offline audiobook app for Android and iOS (see `bookplate/initial-specs.md` and
`bookplate/ROADMAP.md`). Every later milestone needs somewhere to land: modules that build for the two shipping
platforms (plus the JVM for fast unit tests), an app shell with DI, the Riso theme and root navigation, and a smoke
check that the app starts on both platforms. This is milestone M1.

## What Changes

- `build-logic`: two new convention plugins, `sidequests.mobile.library` and `sidequests.compose.mobile.library`,
  for Kotlin Multiplatform libraries that target Android and iOS, plus `jvm()` only so their tests can run as JVM
  unit tests. No `wasmJs` target. The existing `sidequests.multiplatform.library` and
  `sidequests.compose.multiplatform.library` keep exactly the targets and behaviour they have today.
- New `bookplate/sharedApp` (common `App`, Koin wiring, Riso theme, root navigation with `Home` and
  `Player(bookId)` destinations, both placeholder screens, iOS `MainViewController`).
- New `bookplate/androidApp` (application, activity, launcher icon from the system default, no `INTERNET`
  permission, no intent filters besides the launcher one).
- New `bookplate/iosApp` Xcode project modelled on `pacer/iosApp`, without URL types or associated domains.
- `settings.gradle.kts` registers `:bookplate:androidApp` and `:bookplate:sharedApp`.
- `bookplate/CLAUDE.md` (running, tests, Maestro), `bookplate/README.md` feature list started.
- `bookplate/maestro`: one smoke flow (launch, Home placeholder visible).
- No deep links: no URL handling, no `LaunchParameters` mapping. Navigation always starts at Home.

## Capabilities

### New Capabilities
- `bookplate-app`: the app shell — platforms it ships on, offline-only, starts at Home, no external links.
- `bookplate-navigation`: root destinations `Home` and `Player(bookId)`, opening Player and going back.

### Modified Capabilities
<!-- none -->

## Impact

- Modules: `build-logic/convention` (new plugins, shared helpers), new `bookplate/sharedApp`,
  `bookplate/androidApp`, `bookplate/iosApp`, `settings.gradle.kts`, `gradle/libs.versions.toml` (plugin aliases).
- Depends on existing `design/riso`, `design/navigation`, `lib/appstateurl/impl` (for the no-op `AppUrl` the
  navigation back stack needs on phones), Koin.
- Pacer is untouched; its modules keep using the existing plugins.
- No public link parameters are added or changed.
