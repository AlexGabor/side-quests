## 1. Build logic

- [x] 1.1 Extract `configureMultiplatformLibrary(web)` and `configureComposeLibrary()` helpers; keep `MultiplatformLibraryPlugin` and `ComposeMultiplatformLibraryPlugin` behaviour unchanged
- [x] 1.2 Add `MobileLibraryPlugin` (`sidequests.mobile.library`) and `ComposeMobileLibraryPlugin` (`sidequests.compose.mobile.library`), register them and add catalog aliases

## 2. Shared app

- [x] 2.1 Create `bookplate/sharedApp` on the compose mobile plugin, iOS framework `BookplateShared`, register in `settings.gradle.kts`
- [x] 2.2 Koin: `sharedModule` (includes `appUrlModule`), `initKoin`, iOS `setupKoin`
- [x] 2.3 `BookplateDestination` (`Home`, `Player(bookId)`) and `RootNavigation` on `rememberDeepLinkedBackStack`
- [x] 2.4 Placeholder Home (with "Open player") and Player (back, book id) screens; `App()` in `RisoTheme`; `MainViewController()`
- [x] 2.5 JVM tests: start at Home, open Player and back, destination serialization round trip

## 3. Platform apps

- [x] 3.1 `bookplate/androidApp`: application, activity, manifest without `INTERNET` or link intent filters, register in settings
- [x] 3.2 `bookplate/iosApp` Xcode project modelled on Pacer's, without URL types or entitlements

## 4. Docs and flows

- [x] 4.1 `bookplate/CLAUDE.md` (running, tests, Maestro) and README feature list
- [x] 4.2 `bookplate/maestro` config and smoke flow (launch, Home visible)

## 5. Verification (Android emulator, iOS simulator, JVM — as decided in the roadmap)

- [x] 5.1 `./gradlew :bookplate:sharedApp:jvmTest`
- [x] 5.2 Pacer still builds: `./gradlew :pacer:androidApp:assembleDebug :pacer:sharedApp:jvmTest`
- [x] 5.3 Android: install debug on an emulator, check merged manifest has no `INTERNET`, smoke flow passes
- [x] 5.4 iOS: build with `xcodebuild` for a simulator, install, launch, smoke flow passes
