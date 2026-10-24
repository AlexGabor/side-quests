## 1. Module setup

- [x] 1.1 Create `bookplate/feature/home/build.gradle.kts` (`sidequests.compose.mobile.library`, namespace `com.alexgabor.bookplate.feature.home`), depending only on `design/riso`, `core/library/api`, `lib/documentaccess/api`, `lib/coroutine/dispatchers`, Koin Compose, okio, Compose resources; test modules in `commonTest` only
- [x] 1.2 Include it in `settings.gradle.kts`; `sharedApp` depends on it

## 2. State holder

- [x] 2.1 `HomeState`: query, library (`Loading` / `Empty` / `NoResults` / `Books`), `BookRow` with duration and percent text
- [x] 2.2 Add sheet open/closed; `onPicked` running the import with progress, results and close; picks ignored while running
- [x] 2.3 `rememberHomeState` with injected repository/importer, `rememberCoroutineScope` and a saver for the query and sheet
- [x] 2.4 JVM tests for the holder with `FakeLibraryRepository`, `FakeBookImporter` and `TestScopeRule`

## 3. Screen

- [x] 3.1 `HomeScreen`: toolbar, search field + add button, import status, list / empty / no-results, bottom bar slot
- [x] 3.2 Book row (rule-separated, not a `Card`, so the cover stays unprinted) with `BookCover` (okio + `decodeToImageBitmap`, placeholder letter) and `SmallProgress`
- [x] 3.3 Add-choice `BottomSheet` opening the folder or file picker
- [x] 3.4 Previews with sample data (no `test` modules in `commonMain`)

## 4. sharedApp

- [x] 4.1 Home entry renders `HomeScreen`, opening `Player(bookId)`; remove `HomePlaceholder` and `SampleBookId`
- [x] 4.2 Delete `debug/` in every source set, `debugPickStoreModule`, the DataStore (and okio) dependencies
- [x] 4.3 Update `RootNavigationTest` (fake library + documents, open a seeded book, back)

## 5. Docs and devices

- [x] 5.1 `sample-audio.sh`: album artist on the sample folder book
- [x] 5.2 Maestro flows: add a book, refuse the same book again, search, open a book; update `launch.yaml`
- [x] 5.3 `bookplate/CLAUDE.md` (remove debug lines, feature module, how flows get books and drive the picker) and README feature list

## 6. Verification (Android emulator and iOS simulator, as the milestone requires)

- [x] 6.1 `./gradlew :bookplate:feature:home:jvmTest :bookplate:sharedApp:jvmTest :bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest`
- [x] 6.2 Android: install, sample audio, all Maestro flows pass; library survives an app restart (Room on device)
- [x] 6.3 iOS: build, install, sample audio, all Maestro flows pass; library survives an app restart
- [x] 6.4 Pacer still builds (`:pacer:sharedApp` / `:pacer:androidApp` assemble)
