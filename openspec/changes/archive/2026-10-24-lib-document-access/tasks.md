## 1. Modules

- [x] 1.1 Create `lib/documentaccess/{api,impl,test}` build files (namespaces `com.alexgabor.lib.documentaccess.{api,impl,test}`) and include them in `settings.gradle.kts`; add `androidx-activity-compose` to `impl`'s `androidMain`
- [x] 1.2 Leave the stale, untracked `lib/documents/*/build` folders alone (not part of the build; the user can delete them)

## 2. API

- [x] 2.1 `DocumentRef` with `asString()` / `parse()` (versioned, percent-escaped form)
- [x] 2.2 `PickedDocument`, `DocumentEntry`, `Reachability`, `DocumentUnavailableException`, `DocumentSource`
- [x] 2.3 `DocumentAccess` (list, open, reachability) and the `read { }` extension on IO
- [x] 2.4 `DocumentPicker` / `DocumentPickerLauncher` (Compose)
- [x] 2.5 JVM tests for the reference string form (round trips, escapes, garbage input)

## 3. Fake

- [x] 3.1 `FakeDocumentAccess` in-memory tree: files, folders, grants, queued picks, delete, rename, revoke; I/O on `CoroutineDispatchers.IO`
- [x] 3.2 `fakeDocumentAccessModule`; JVM `addDirectory(File, at)`
- [x] 3.3 Tests: picking, listing (nested, hidden, single file), reading bounds, reachability (missing, renamed, access lost), errors, parsing a fixture through `AudioMetadataReader`, `addDirectory`

## 4. Platform implementations

- [x] 4.1 `documentAccessModule` expect/actuals; JVM stand-in with no documents
- [x] 4.2 Android picker (`OpenDocument` / `OpenDocumentTree`, persistable read permission, display name)
- [x] 4.3 Android `DocumentAccess`: `DocumentsContract` listing, file-descriptor source, reachability
- [x] 4.4 iOS picker (`UIDocumentPickerViewController`, bookmark creation)
- [x] 4.5 iOS `DocumentAccess`: bookmark resolution, scoped enumeration, POSIX `pread` source with scope start/stop, reachability

## 5. Bookplate debug entry (temporary)

- [x] 5.1 Bind `documentAccessModule` in `bookplate/sharedApp`
- [x] 5.2 Debug pick store (Preferences DataStore, platform paths) under `sharedApp/.../debug/`
- [x] 5.3 `DocumentAccessDebugState` + section in `HomePlaceholder`: pick file (`audio/*`), pick folder, refresh, clear; list audio files with size and duration, reachability per pick
- [x] 5.4 `RootNavigationTest` binds the fake; add a test that a queued folder pick lists its audio file

## 6. Docs

- [x] 6.1 `bookplate/CLAUDE.md`: generating sample audio with ffmpeg and pushing it to the emulator and simulator; removing/renaming files there
- [x] 6.2 Note the debug entry is temporary in `bookplate/CLAUDE.md`

## 7. Verification

Platforms: JVM unit tests, Android emulator, iOS simulator (as set by the milestone brief).

- [x] 7.1 `./gradlew :lib:documentaccess:api:jvmTest :lib:documentaccess:test:jvmTest :bookplate:sharedApp:jvmTest :lib:audiometadata:jvmTest`
- [x] 7.2 Compile all targets: `:lib:documentaccess:impl` Android + iOS simulator, `:bookplate:androidApp:assembleDebug`, iOS app `xcodebuild`, and Pacer (`:pacer:androidApp:assembleDebug`, `:pacer:desktopApp:compileKotlin`, `:pacer:webApp` compile)
- [x] 7.3 Android emulator: pick a folder and a single file, see audio files and durations; restart, still listed and readable; delete or rename a file, see it reported missing
- [x] 7.4 iOS simulator: same three checks
