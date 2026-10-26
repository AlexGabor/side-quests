## Context

M11 put every playback rule in `bookplate/core/player/impl/commonMain`: `EngineBookPlayer` (ordered commands, a
250 ms poll while playing, saving every 5 s / on pause / seek / switch / background, restoring position and speed on
load, end of book) over a small `AudioOutput` port, and `BookTimeline` (book position ↔ file index + offset). The
engine *reconciles* platform-made changes: the output only signals `changes`, and the engine reads `status()` now. An
output reports `isPlaying` as "asked to play and not held back", so a transient loss shows as paused and saved, and
the state follows when the platform resumes by itself. Android implements the port with Media3 and reads files
through `lib/documentaccess` (`DocumentDataSource`), because a `DocumentRef`'s parts are private to the
`DocumentAccess` implementation.

On iOS, `IosDocumentAccess.open(ref)` resolves the bookmark, starts the security scope and returns a `pread`-backed
`DocumentSource` that holds the scope until it is closed. The iOS app's deployment target is iOS 16.

M13 adds Now Playing info and remote commands on top of this output, so the player and audio session set up here are
the ones it extends.

## Goals / Non-Goals

**Goals:**
- An iOS `AudioOutput` behaving like the Android one towards the engine: same status semantics, same end-of-book and
  error handling, same reconcile path for interruptions and route changes.
- No change to `EngineBookPlayer`, `BookTimeline`, `AudioOutput`, `core/player/api`, the fake or the feature modules.
- Background and locked-screen playback.

**Non-Goals:**
- `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`, headset buttons (M13). Background playback with an `AVPlayer`
  and the `.playback` category needs neither.
- Missing files and relinking (M14); an unreadable file fails the item and the engine pauses, as on Android.

## Decisions

### 1. `AVQueuePlayer` with a two-item window

`AvAudioOutput` owns one `AVQueuePlayer` (`actionAtItemEnd = advance`). Loading or seeking to file *i* replaces the
queue with items for files *i* and *i+1*; when an item finishes (`AVPlayerItemDidPlayToEndTimeNotification`) the
queue moves on by itself and the output appends the item after the new current one. So the next file is always
queued and preloaded, and moving into it needs no seek or player switch, while a book of hundreds of files never has
hundreds of items. Items are created fresh each time (an `AVPlayerItem` can't be put back once removed); the assets
are cheap to recreate.

A seek within the current file is `seekToTime` with zero tolerance. A seek to another file rebuilds the queue and
then seeks. Until a seek completes, `status()` reports the requested position, so a poll in between can't save the
old one.

*Alternative:* one `AVPlayer` and `replaceCurrentItemWithPlayerItem` at each file end. Rejected: the swap happens
after the end notification, so there is an audible gap and the next file starts loading only then.

Speed: each item gets `audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain` (meant for speech; the default
on iOS can vary), so speed changes keep pitch. The rate is `defaultRate` (iOS 16), which `play()` uses, and also
`rate` while playing.

### 2. Status semantics

The output keeps `wantsToPlay` (set by `play()`, cleared by `pause()`, `load`, `clear`, the end of the book, a
non-resuming interruption end and a lost route).

- `position`: the current item's file index and `currentTime`, or the pending seek.
- `isPlaying`: `wantsToPlay && timeControlStatus != paused` and not ended or failed. Waiting for data counts as
  playing. During an interruption the system pauses the player, so it reads as paused while `wantsToPlay` stays.
- `isEnded`: the last file's item played to its end (the queue is then empty). Reported at the last file's end.
- `isFailed`: the current item's or the player's status is failed (for example a file `DocumentAccess` can't open).

`changes` emits on the item-end and failed-to-play notifications, `AVPlayerRateDidChangeNotification`, and the
audio session notifications below. All of them are observed on the main queue, and every port call hops to
`CoroutineDispatchers.Main`, where AVFoundation's state is read and changed.

### 3. Reading files through `lib/documentaccess` with a resource loader

Each item's asset is an `AVURLAsset` on a private URL scheme, `bookplate-doc://<generation>/<index>`, whose
`resourceLoader` delegate is a `DocumentResourceLoader` on a serial dispatch queue. The generation changes on each
`load`, so a late request for the previous book is refused. On the first request for a file it calls
`DocumentAccess.open(ref)` (with `runBlocking`, on the loader's queue, never the main thread) and keeps the
`DocumentSource` open:

- a content-information request gets the file's size, byte-range support and its type, a UTI from the format
  `AudioMetadataReader.detect` reads from the bytes (MP3 → `public.mp3`, MP4 → `public.mpeg-4-audio`, AAC →
  `public.aac-audio`, WAV → `com.microsoft.waveform-audio`, FLAC → `org.xiph.flac`), like the rest of the app, which
  never trusts names;
- a data request is answered in 256 KB chunks from `DocumentSource.read`, stopping when the request is cancelled.
  AVFoundation asks for up to the end of a file on its first read, so each request is served on its own work item and
  checked for cancellation between chunks, so a later request after a seek isn't stuck behind it.

An open `DocumentSource` holds the file's security scope, so access is held exactly while the file is in use: sources
open as their items load and are closed when the item leaves the queue's window, on `load` of another book and on
`clear`. A file that can't be opened finishes the loading request with an error, the item fails, and the engine
pauses.

*Why not give AVFoundation the file URL:* that needs the resolved bookmark, which only `IosDocumentAccess` may read
(the same reason Android didn't use content URIs), and M14's reachability rules stay in one place. M11's design
already pointed M12 at the same "open a ref" path. *Cost:* bytes are copied from a `ByteArray` into `NSData`; audio
reads are small next to that, and the reads are local `pread`s.

### 4. Audio session, interruptions and route changes

On first `play()`, `AVAudioSession.sharedInstance` gets category `.playback` with mode `.spokenAudio` and is
activated. `.playback` keeps audio going with the app in the background and the device locked, given `audio` in
`UIBackgroundModes`; `.spokenAudio` lets the system pause rather than duck us for other spoken audio, the right call
for audiobooks.

- `AVAudioSessionInterruptionNotification`, type began: the system has already paused the player; `wantsToPlay`
  stays, so the engine pauses its state and saves without pausing the output (the same path as Android's transient
  focus loss). Type ended with `AVAudioSessionInterruptionOptionShouldResume` and `wantsToPlay`: reactivate the session
  and `play()`; the engine sees playing again. Ended without it: `wantsToPlay` is cleared.
- `AVAudioSessionRouteChangeNotification` with reason `OldDeviceUnavailable` (headphones unplugged): pause the player
  and clear `wantsToPlay`; the engine pauses and saves.

### 5. Background save and bindings

`iosMain`'s `playerModule` binds one `EngineBookPlayer` (app-lifetime scope) with an `AvAudioOutput(documentAccess)`,
and `backgrounded` from `UIApplicationDidEnterBackgroundNotification`. `sharedApp`'s iOS `platformPlayerModule`
becomes `playerModule`, and `core/player/impl` moves from `androidMain` to `commonMain` dependencies of `sharedApp`
(the JVM target still binds the fake and keeps `core/player/test` in `jvmMain`; it compiles `impl`'s common code
only). The known-deviations row goes.

`Info.plist` gains `UIBackgroundModes` = `[audio]`. Nothing else in the plist (no URL schemes: the resource loader's
scheme is private to AVFoundation and never registered with the system).

### 6. Shared code

None of the common engine changes, and no API changes. `AudioMetadataReader.detect` comes from `lib/audiometadata`,
which `lib/documentaccess/api` already exposes, so no dependency is added.

## Risks / Trade-offs

- [VBR MP3s without a seek table seek approximately] → same as Android's constant-bitrate seeking; precise timing
  (`AVURLAssetPreferPreciseDurationAndTimingKey`) would read whole files before a far seek, so it stays off. The
  parsed durations still drive the timeline.
- [Resource loader requests read more than playback needs] → chunked answers stop on cancellation; memory is checked
  on the simulator with a longer sample.
- [The simulator can't place a call or unplug headphones, and doesn't interrupt one app's audio for another's] →
  `AvAudioOutputTest` (`iosTest`, the repo's first) posts the interruption and route-change notifications by hand
  and checks the output's status; the system's own pause at an interruption's start can't be reproduced there.
- [AVFoundation's file-end notification and the poll race] → the engine reconciles from the current status, so the
  order doesn't matter, as on Android.

## Migration Plan

No data migration. Rolling back is binding `fakePlayerModule` on iOS again.

## Open Questions

None.
