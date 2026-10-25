## Why

Bookplate's Player and mini player run against `FakeBookPlayer`, a clock with no audio. M11 adds the real playback
engine: a common part that turns a book's files into one timeline and keeps progress in the library, and an Android
implementation on Media3 that plays in the background. M12 (iOS, AVFoundation) reuses the common part, and M13 builds
the media panel on the session added here.

## What Changes

- New module `bookplate/core/player/impl` (`sidequests.mobile.library`):
  - common: `BookTimeline` (book position ↔ file index + offset, files without a duration skipped), and
    `EngineBookPlayer`, the `BookPlayer` that runs every command in order, tracks the current chapter, skips ±10 s
    across file boundaries clamped to the book, restores the saved position and speed on load, saves position
    (every 5 s while playing, on pause, seek, skip, chapter jump, book switch and when the app goes to the
    background), speed and last-played time through `LibraryRepository`, and stops at the end of the book. It drives
    a platform `AudioOutput` and follows changes the platform makes on its own (audio focus, headphones unplugged,
    the media notification).
  - Android: `PlaybackService`, a Media3 `MediaSessionService` owning an `ExoPlayer` (playlist of the book's files,
    gapless between them, pitch-preserving speed, audio focus, becoming-noisy, wake lock) with the default media
    notification; `DocumentDataSource`, a Media3 `DataSource` reading files through `lib/documentaccess`;
    `Media3AudioOutput`, the `AudioOutput` talking to the service through a `MediaController`; `playerModule`.
  - Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, and the service with
    `foregroundServiceType="mediaPlayback"`. Still no `INTERNET`.
- `bookplate/sharedApp`: binds `EngineBookPlayer` on Android; iOS (and the JVM test target) keep `FakeBookPlayer` until
  M12. The dependency on `core/player/test` moves from `commonMain` to `iosMain`/`jvmMain`.
- Dependencies: `androidx.media3:media3-exoplayer` and `media3-session` 1.11.1, `androidx.lifecycle:lifecycle-process`.
- `feature/player`: opening Player for another book while one plays no longer pauses it. Player shows the other
  book from the library; "Play" takes over, and seeking, skipping, chapter jumps and speed change its saved progress.
  (Added after review of the first M11 run.) No change to `core/player/api` or `feature/home`.
- Docs: README feature list (real audio on Android, background playback, notification), `bookplate/CLAUDE.md`
  (module, test command), `known-deviations.md` row narrowed to iOS.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `bookplate-player`: adds the playback engine requirements (timeline across files, platform-made pauses, saving on
  background, Android engine with background playback, notification, focus, becoming-noisy and permissions) and
  changes "Fake player for the UI" so the fake is bound only where no engine exists yet (iOS), and "Player opens its
  book" so opening another book leaves the playing one playing.

## Impact

- New: `bookplate/core/player/impl`, included in `settings.gradle.kts`.
- Changed: `bookplate/sharedApp` (bindings, dependencies), `gradle/libs.versions.toml`, docs.
- Changed: `bookplate/feature/player` (`PlayerScreenState`).
- Unchanged: `bookplate/core/player/api`, `bookplate/core/player/test`, `bookplate/feature/home`, `bookplate/core/library/*`,
  `lib/documentaccess`, Pacer.
- Maestro flows now run against real audio on Android; timings may need adjusting.
