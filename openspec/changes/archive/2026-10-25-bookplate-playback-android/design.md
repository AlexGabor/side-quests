## Context

M10 settled the playback contract in `bookplate/core/player/api` (`BookPlayer`: one `PlaybackState` flow,
fire-and-forget commands run in order, book-wide positions) and bound `FakeBookPlayer` from `core/player/test` in
`sharedApp` on both platforms. The library (`core/library`) stores, per book, its files in playback order with each
file's `start` in the book and its parsed `duration`, its chapters on the book timeline, and `position`, `speed` and
`lastPlayedAt`, written through `LibraryRepository.setPosition/setSpeed/setLastPlayed`. Files are reached through
`lib/documentaccess` (`DocumentRef`, `DocumentAccess.open` giving a blocking random-access `DocumentSource`).

M11 adds the engine on Android. M12 adds the iOS one on top of the same common code, and M13 adds the media panel and
headset buttons on top of the Android media session, so the session added here must be the one M13 extends.

Constraints: offline (no `INTERNET`), original files only read, minSdk 33 / targetSdk 37.

## Goals / Non-Goals

**Goals:**
- One common engine holding all playback rules (timeline, chapters, clamping, saving, restoring, end of book), tested
  on the JVM, that M12 reuses unchanged.
- A thin Android audio output on Media3 with background playback and the default notification.
- No change to `core/player/api` or the feature modules.

**Non-Goals:**
- Notification/lock-screen layout, custom commands, cover in the panel, headset buttons beyond Media3's defaults (M13).
- Detecting missing files and relinking (M14); here a file that can't be read stops playback like an error.
- iOS playback (M12).

## Decisions

### 1. Common `EngineBookPlayer` over a small `AudioOutput` port

`core/player/impl/commonMain` holds `EngineBookPlayer(repository, output, scope, clock, backgrounded)` and the
`AudioOutput` interface the platform implements:

```kotlin
interface AudioOutput {
    val changes: Flow<Unit>                       // "something changed on your side"
    suspend fun load(files: List<OutputFile>, at: FilePosition, speed: Float)   // paused
    suspend fun play(); suspend fun pause()
    suspend fun seekTo(at: FilePosition); suspend fun setSpeed(speed: Float)
    suspend fun clear()
    suspend fun status(): OutputStatus            // file index, offset, isPlaying, isEnded, failed
}
```

`EngineBookPlayer` works like the fake: commands go into a channel and run one at a time on
`CoroutineDispatchers.Default`; while playing a ticker enqueues a poll every 250 ms that reads `status()` and moves the
position on; every 20th poll (5 s of playback) saves it. The state is the engine's own; the output is only asked to
play, pause and seek.

*Why a port rather than the engine calling Media3:* the rules are what M12 must reuse and what the tests need to reach;
the output becomes a few lines per call on each platform. *Alternative:* an `expect class` per platform. Rejected: it
splits the rules across platforms and can't be faked in common tests.

### 2. Platform-made changes are reconciled, not replayed

Audio focus, becoming noisy, the notification's buttons and errors change playback without a `BookPlayer` command.
The output only signals `changes`; the engine enqueues a reconcile that reads `status()` *now* and compares it with
its state: playing → paused saves position and last-played (like `pause()`); paused → playing sets last-played and
starts the ticker; ended → position = duration, paused, saved. Reading the current status rather than carrying the
new value in the event means a stale "now playing" signal that arrives after a later `pause()` command does nothing.

An output reports `isPlaying` as "wants to play and isn't suppressed": a transient focus loss (a call) shows as paused
and saved, and when Media3 resumes after it, the state shows playing again.

### 3. Timeline

`BookTimeline(files)` keeps the files with a positive duration, in library order, with their library `start`.
`locate(position)` returns the last kept file whose start is at or before the position (so a boundary goes to the later
file) and `position - start`, the offset clamped to the file's duration; `positionOf(index, offset)` is
`start + offset`, clamped to `[start, next start]` and to the book's duration. The clamp matters because the
decoder's duration can be a few milliseconds longer than the parsed one; positions never run past the next file's
start or the end. The library's `start`s are used as they are rather than recomputed, so positions stay consistent
with the chapters the library stored.

Files with zero duration (the parser couldn't read one) are left out of the playlist. Their audio, if any, would
otherwise play without a place on the timeline. M15 revisits corrupt and zero-length files.

### 4. Android: ExoPlayer in a `MediaSessionService`, driven through a `MediaController`

`PlaybackService : MediaSessionService` creates the `ExoPlayer` and the `MediaSession` in `onCreate` and releases both
in `onDestroy`. `Media3AudioOutput` (the app-wide `AudioOutput`) connects a `MediaController` to the service's
`SessionToken` on first use, on `CoroutineDispatchers.Main`, and calls it for every port method.

*Why a controller:* this is the Media3 way to have the service started, promoted to the foreground when playback
starts (and demoted when it stops), and kept alive while the app is in the background, with the right foreground
service start exemptions for a user-started play. The default `MediaNotification` comes with it, and M13 customises
the same session. The notification's commands reach the same `ExoPlayer`, and the controller's listener reports them
back as `changes`. *Alternative:* an app-wide `ExoPlayer` used directly, with the service only wrapping it in a session.
Rejected: the service then has to be started and promoted by hand, which is what Media3's controller binding already
does correctly.

If the controller disconnects (the service was destroyed), the next call reconnects and loads the last playlist again
at the last known file position and speed, so the engine never sees it.

ExoPlayer is built with:
- `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH)` and `handleAudioFocus = true`: focus is requested on play,
  a permanent loss pauses, a transient loss pauses and resumes (speech content pauses rather than ducks);
- `setHandleAudioBecomingNoisy(true)`: unplugging headphones pauses;
- `setWakeMode(C.WAKE_MODE_LOCAL)` (needs `WAKE_LOCK`): the CPU keeps decoding with the screen off;
- the book's files as one playlist, which ExoPlayer plays gaplessly and loads ahead;
- `setPlaybackSpeed(speed)`: `PlaybackParameters` keep pitch 1.0, so Sonic time-stretches without changing pitch;
- `DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)`, so CBR MP3s without a seek table still seek.

Each `MediaItem` carries the file's `DocumentRef.asString()` as its media id and title/artist metadata (book title,
author) for the notification. `MediaSession.Callback.onAddMediaItems` turns the media id into a `documentref:` URI the
data source understands, so the items don't depend on `localConfiguration` surviving the controller hop. The session
activity opens the launcher activity, so tapping the notification opens the app.

### 5. Reading files through `lib/documentaccess`, not raw content URIs

`DocumentDataSource` is a Media3 `BaseDataSource` that parses the `DocumentRef` from the URI, opens it with
`DocumentAccess.open` (blocking on ExoPlayer's loader thread with `runBlocking`; the call itself hops to
`CoroutineDispatchers.IO`) and serves `read` from `DocumentSource.read(position, length)`.

*Why not hand ExoPlayer the content URI:* `DocumentRef`'s parts are documented as private to the `DocumentAccess`
implementation (on Android the child is a tree document URI, but on iOS it's bookmark data), so the player would be
reading another module's internals. Going through the port keeps the persisted-permission and reachability rules in one
place, which M14 relies on, and the same "open a ref" path M12 needs. A missing or inaccessible file throws
`DocumentUnavailableException`, mapped to a Media3 `DataSourceException` (`ERROR_CODE_IO_FILE_NOT_FOUND` /
`ERROR_CODE_IO_NO_PERMISSION`); the engine sees a failed status and pauses. *Cost:* a `ByteArray` per read; ExoPlayer
reads in tens of kilobytes, so this is negligible for audio.

### 6. Saving when the app goes to the background

`EngineBookPlayer` takes `backgrounded: Flow<Unit>` and saves the loaded book's position on each emission. On Android
it is `ProcessLifecycleOwner`'s `ON_STOP` (`lifecycle-process`), collected on `CoroutineDispatchers.Main`; M12 gives
it the iOS equivalent. Collection starts with the first command, so dispatchers are read when work starts.

### 7. Manifest and dependencies

`core/player/impl/src/androidMain/AndroidManifest.xml` declares `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` (required for the `mediaPlayback` type since API 34), `WAKE_LOCK`, and
`PlaybackService` exported with `foregroundServiceType="mediaPlayback"` and the
`androidx.media3.session.MediaSessionService` intent filter (so system UI and, in M13, Bluetooth controllers can
connect). `POST_NOTIFICATIONS` isn't requested: media-session notifications are exempt from it. The merged manifest is
checked to have no `INTERNET`; `media3-exoplayer` only touches the network through HTTP data sources we never build.
`media3-exoplayer`'s manifest adds `ACCESS_NETWORK_STATE` (bandwidth estimates for streaming); it is removed with
`tools:node="remove"`, and Media3 treats the network type as unknown without it.

Dependencies: `media3-exoplayer` and `media3-session` 1.11.1 (latest stable), `lifecycle-process` 2.11.0.
Media3's unstable APIs (`DataSourceException`, `BaseDataSource`, extractor flags) are opted into per file.

### 8. Bindings

`core/player/impl/androidMain` exposes `playerModule` binding one `EngineBookPlayer` (app-lifetime scope, like the
fake) with a `Media3AudioOutput`. `sharedApp` gets an `expect val platformPlayerModule`: Android → `playerModule`,
iOS and the JVM test target → `fakePlayerModule`. So the dependency on `core/player/test` moves out of `commonMain`
into `iosMain` and `jvmMain`, and the known-deviations row now covers iOS only. The api, the fake and the feature
modules are unchanged.

### 9. Opening another book while one plays

M10 had Player load its book on open, so opening B paused a playing A. Now `PlayerScreenState` also reads the book
from `LibraryRepository` and shows that, paused at its saved position and speed, whenever the player holds a
different book. It only calls `load` when the player isn't playing another book (nothing loaded, or a paused one,
where loading stops nothing). While it previews:
- "Play" calls `load(B)` then `play()`; the engine pauses and saves A as before;
- seek, skip, chapter jump and speed write B's position or speed straight to the library (`setPosition`,
  `setSpeed` with `snapSpeed`), and the preview follows the library flow.

*Why in the state holder, not the engine:* `BookPlayer` plays one book; previewing is a screen concern, and the
library already holds everything the preview shows. *Alternative:* a `BookPlayer.prepare(id)` that keeps a second
book. Rejected: the API, the fake, the engine and M12 would all carry a second book for one screen's sake.

## Risks / Trade-offs

- [Parsed and decoded durations differ slightly] → positions are clamped to each file's span; a drift of
  milliseconds at a boundary is invisible in `m:ss`.
- [VBR MP3 without a Xing/VBRI table seeks approximately] → constant-bitrate seeking is the best Media3 offers without
  index seeking, which would read the whole file before a far seek. The parser's duration still drives the timeline.
- [`runBlocking` in `DataSource.open`] → runs on ExoPlayer's loader thread, never the main thread.
- [The emulator's audio and a 2.5 GB RAM image make timing flaky] → Maestro flows keep using `waitToSettleTimeoutMs`
  and assert movement rather than exact times.
- [Default notification shows ExoPlayer's per-file "next/previous"] → acceptable until M13 replaces the commands.

## Migration Plan

No data migration: the engine reads and writes the same library fields as the fake. Rolling back is binding
`fakePlayerModule` again on Android.

## Open Questions

None.
