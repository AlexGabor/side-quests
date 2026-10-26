## Why

M11 gave Bookplate a real playback engine on Android, but the iOS app still plays through `FakeBookPlayer`, a clock
with no audio. M12 brings iOS to parity on the same common engine, so M13 can add Now Playing and remote commands on
both platforms on top of a working player.

## What Changes

- `bookplate/core/player/impl` gets an `iosMain` source set:
  - `AvAudioOutput`, the `AudioOutput` over an `AVQueuePlayer`: the book's files as a queue that plays from one file
    into the next, pitch-preserving speed (`audioTimePitchAlgorithm`), seeking within and across files, end of book
    and errors reported to the engine.
  - `DocumentResourceLoader`, an `AVAssetResourceLoaderDelegate` that feeds AVFoundation the files' bytes through
    `lib/documentaccess` (`DocumentAccess.open`), the same path Android's `DocumentDataSource` uses. An opened file
    holds its security scope, so access is held while a file is loaded or playing and stopped when it is released.
    Files are only read.
  - The audio session: `.playback` category with the spoken-audio mode, activated on play; interruptions (calls,
    Siri, other apps) pause and resume when the system says to; a route change that loses the output (headphones
    unplugged) pauses.
  - `playerModule` binding `EngineBookPlayer` with the iOS output, saving progress when the app enters the
    background.
- `EngineBookPlayer`, `BookTimeline` and `AudioOutput` (common) are reused unchanged.
- `bookplate/iosApp/Info.plist`: `UIBackgroundModes` = `audio`, so playback continues in the background and with the
  device locked.
- `bookplate/sharedApp`: binds the engine on iOS; `core/player/test` is no longer an iOS dependency (the JVM test
  target keeps the fake). The known-deviations row goes.
- Docs: README feature list (real audio on iOS), `bookplate/CLAUDE.md`, `known-deviations.md`.

Out of scope: Now Playing info, remote commands and headset buttons (M13); missing files and relinking (M14).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `bookplate-player`: adds the iOS playback engine requirement (AVFoundation, background audio, interruptions,
  route changes, security-scoped reads through `lib/documentaccess`), and changes "Fake player for the UI" so both
  apps bind the engine and the fake is for tests and previews only.

## Impact

- Changed: `bookplate/core/player/impl` (new `iosMain`; uses `lib/audiometadata` for content-type detection,
  already reachable through `lib/documentaccess/api`), `bookplate/sharedApp` (iOS binding, dependencies),
  `bookplate/iosApp/iosApp/Info.plist`, docs.
- Unchanged: `bookplate/core/player/api`, `bookplate/core/player/test`, the common engine, `bookplate/feature/*`,
  `bookplate/core/library/*`, `lib/documentaccess`, Pacer. No Pacer link parameters are touched.
- Maestro flows now run against real audio on iOS; timings may need adjusting.
