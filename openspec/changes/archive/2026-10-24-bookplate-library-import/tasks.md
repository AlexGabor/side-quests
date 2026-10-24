## 1. Build setup

- [x] 1.1 Add Room 3 (`room3-runtime`, `room3-compiler`, plugin `androidx.room3`), the KSP plugin and `sqlite-bundled` to `gradle/libs.versions.toml`; declare the KSP and Room plugins `apply false` in the root `build.gradle.kts`
- [x] 1.2 Create `bookplate/core/library/{api,impl,test}` build files (`sidequests.mobile.library`, namespaces `com.alexgabor.bookplate.core.library.{api,impl,test}`) and include them in `settings.gradle.kts`
- [x] 1.3 In `impl`: apply KSP and Room, run the Room compiler for `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`, export the schema to `impl/schemas`

## 2. API

- [x] 2.1 `BookId`, `Book` (with `updatedAt`), `AudioFile`, `Chapter`, `BookDetails`
- [x] 2.2 `LibraryRepository` (books with query, last played, book by id, position / speed / last-played updates)
- [x] 2.3 `BookImporter`, `ImportProgress`, `ImportResult`, `FailureReason`

## 3. Room storage

- [x] 3.1 Entities `books`, `audio_files`, `chapters` with indexes and cascades; `LibraryDao`; `LibraryDatabase` with `@ConstructedBy`
- [x] 3.2 `libraryStorageModule` expect/actuals: Android, iOS and JVM builders with the bundled driver and `CoroutineDispatchers.IO`; covers directory per platform
- [x] 3.3 `LibraryRepositoryImpl`: entity ↔ model mapping (chapter tree, cover path), SQL ordering, case-insensitive filtering, updates on IO
- [x] 3.4 Keep the exported schema `1.json` in the repo (committed with the change)

## 4. Import

- [x] 4.1 Natural sort comparator and audio-extension filter
- [x] 4.2 Book assembly: timeline, per-file chapters (embedded / cue sidecar / one per file), folder sections, title / author / narrator fallbacks
- [x] 4.3 Cover: embedded art or `cover.*` / `folder.*`, written to the covers directory with okio
- [x] 4.4 `BookImporterImpl`: duplicate checks (reference, fingerprint), progress, result mapping, one-transaction save, cover clean-up on failure
- [x] 4.5 `libraryModule` binding repository, importer and `Clock.System`

## 5. Fakes

- [x] 5.1 `FakeLibraryRepository`, `FakeBookImporter`, `fakeLibraryModule`, with a small common test

## 6. Tests (JVM, `impl`)

- [x] 6.1 Natural sort unit tests
- [x] 6.2 Import tests through `FakeDocumentAccess` and Room: single M4B with chapters; multi-file MP3 folder in natural order; `Disc` sections; untagged folder; `.cue` sidecar; embedded cover and `folder.jpg`; duplicate refused; no audio; missing source; progress; sources closed
- [x] 6.3 Repository tests: recently-updated order, filtering by title and author, last played, position and speed updates

## 7. Wiring and verification

- [x] 7.1 Bind `libraryModule` in `bookplate/sharedApp` (UI unchanged)
- [x] 7.2 `./gradlew :bookplate:core:library:impl:jvmTest :bookplate:core:library:test:jvmTest :bookplate:sharedApp:jvmTest`
- [x] 7.3 Build Bookplate Android (`:bookplate:androidApp:assembleDebug`) and iOS (`xcodebuild` simulator build), proving Room and KSP on both
- [x] 7.4 Check Pacer still builds (`:pacer:androidApp:assembleDebug`, `:pacer:sharedApp:jvmTest`)
