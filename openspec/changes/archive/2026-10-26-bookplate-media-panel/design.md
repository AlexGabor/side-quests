## Context

M11 and M12 built one common `EngineBookPlayer` (commands in order, a 250 ms poll while playing, saving, end of book)
over a platform `AudioOutput` that plays a list of files and reports a `FilePosition` (file index + offset). The
engine *reconciles* platform-made changes: the output only signals `changes`, and the engine reads `status()` now.

- Android: `PlaybackService` (a `MediaSessionService`) owns ExoPlayer and a `MediaSession`; `Media3AudioOutput`
  drives it through a `MediaController`. The book is a playlist of one `MediaItem` per file, each titled with the
  book. So Media3's default notification shows per-file previous/next, the current *file's* progress, and no cover
  or chapter.
- iOS: `AvAudioOutput` over an `AVQueuePlayer`, `.playback` session, background audio. Nothing fills Now Playing and
  no remote command is handled, so the lock screen shows nothing.

`PlaybackState` already holds what a panel needs: the book (title, author, cover path in app storage, duration,
chapters), the book-wide position, playing and speed, and the current chapter.

## Goals / Non-Goals

**Goals:**
- The system panel on both platforms shows the same book, chapter, position and playing state as Player and the
  mini player, and its controls and headset buttons act on the same player.
- What the panel shows (title, subtitle rule, when to resync time) is common code tested on the JVM.
- No change to `core/player/api`, the fake or the feature modules.

**Non-Goals:**
- Android Auto, Wear, media browsing or resumption from a cold start (`onPlaybackResumption`): no library tree is
  exposed.
- Speed controls in the panel.
- iOS opening Player when Now Playing is tapped: the system just brings the app forward, on whatever screen it was.

## Decisions

### 1. The seek bar covers the whole book

The panel's seek bar shows the position in, and the duration of, the whole book, not the current chapter.

- Player's `Track` and its position/duration labels are book-wide; the panel must show the same state, and a
  chapter-wide bar would put a different "position" and "duration" in front of the user for the same moment.
- Every book has a whole-book timeline, but not every book has meaningful chapters (one per file, or none).
- The chapter is still in front of the user as the subtitle, and chapter jumps stay in the app's chapters sheet.

The cost is coarse scrubbing on a very long book; the ±10 s buttons cover fine moves. Applied on both platforms.

### 2. Common panel model: `PanelInfo` and `PanelUpdates`

`core/player/impl/commonMain`:

```kotlin
data class PanelInfo(bookId, title, subtitle: String?, coverPath: String?, duration, position, isPlaying, speed)
fun PlaybackState.panelInfo(): PanelInfo?   // null with no book
```

`subtitle` is the current chapter's title when it has one that isn't blank and isn't the book's title, else the
author, else `null`. The scope asks for "chapter, or the author when the book has no chapters"; the two extra
cases cover what the importer really produces: a single-file book gets one chapter usually named like the book, and
untitled chapters exist. Showing the book's title twice, or nothing while an author is known, would be worse than the
author.

`PanelUpdates(timeSource)` decides when a panel that extrapolates time itself (Now Playing) must be told again:
`next(info)` returns the info to push, or `null` when the last pushed one still holds. It pushes when anything but the
position changed (book, title, subtitle, cover, duration, playing, speed), or when the position is more than 1 s away
from where the last push extrapolates it (last position + elapsed × speed while playing). A poll every 250 ms thus
pushes only after seeks, skips, jumps and stalls. Tested on the JVM with a `TestTimeSource`.

### 3. Android: one media item per book (`ConcatenatingMediaSource2`)

To give Media3's notification and the platform session a whole-book timeline, ExoPlayer plays a book as **one**
`MediaItem` whose source is a `ConcatenatingMediaSource2` of the files. ExoPlayer then reports the book's duration
and position, its seek bar spans the book, `seekBack`/`seekForward` cross files, and there is no next/previous item.
Files still play back to back with no gap: they are periods of one window, the same path a playlist uses.

- `Media3AudioOutput.load` sends one item: media id `book`, the book's title/author metadata, and the files (refs and
  parsed durations) in `RequestMetadata.extras`, which survive the controller → session hop.
- `PlaybackService.onAddMediaItems` gives the item a private `bookplate-book:` URI; a `BookMediaSourceFactory`
  wrapping the default factory turns such an item into a `ConcatenatingMediaSource2`, one child per file
  (`documentref:` URI through `DocumentDataSource`, as before), each with its parsed duration as the placeholder.
- The `AudioOutput` port and the engine are unchanged: the output maps a `FilePosition` to a window position and
  back. It uses the session timeline's periods (each period's `positionInWindow`) so the offset into each file is
  exact even when the decoder's durations differ a little from the parsed ones; until the timeline is known it uses
  the parsed durations. The file is found from the position and those starts, not from `currentPeriodIndex`: right
  after a seek the controller already reports the new position but can still report the old period, which put a
  paused chapter jump to 1:05 back at 1:04 in the previous chapter.

*Alternative:* keep one item per file and wrap ExoPlayer in a player that fakes a single book-wide item. Rejected:
the app's own `MediaController` talks to the same session player, so every playlist call would need translating
both ways; the concatenating source is Media3's own answer to "several files, one item".

### 4. Android: `PanelPlayer` over ExoPlayer for metadata and commands

The session's player is `PanelPlayer : ForwardingSimpleBasePlayer(exoPlayer)`. It overrides `getState()` only:

- the current item's `MediaMetadata` becomes the panel's: title = book title, artist and subtitle = `PanelInfo`'s
  subtitle, album = book title, `artworkUri` = `file://` of the cover in app storage (Media3's bitmap loader decodes
  it in-process and hands the bitmap to the notification and the platform session, so System UI never reads our
  files). The engine publishes a new book's state just before it loads the book into the output, so for that
  moment the old item carries the new labels; it is paused then, and the item changes right after;
- the available commands lose `COMMAND_SEEK_TO_PREVIOUS(_MEDIA_ITEM)` and `COMMAND_SEEK_TO_NEXT(_MEDIA_ITEM)`.

`PlaybackService` collects `BookPlayer.state.map { it.panelInfo() }` (only the fields that matter on Android:
book, title, subtitle, cover) with `distinctUntilChanged` on the main thread and hands it to `PanelPlayer`, which
invalidates its state. So the chapter shown follows the engine's own chapter rule, the same one Player uses.

Controls: ExoPlayer gets `setSeekBackIncrementMs`/`setSeekForwardIncrementMs` = `SkipInterval` (10 s), and the
session's media button preferences are two `CommandButton`s, `ICON_SKIP_BACK_10` with `COMMAND_SEEK_BACK` in
`SLOT_BACK` and `ICON_SKIP_FORWARD_10` with `COMMAND_SEEK_FORWARD` in `SLOT_FORWARD`. Play/pause and the seek bar
(`COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM`) are Media3's defaults.

The panel's commands go straight to ExoPlayer, as M11's pause button already did, and the engine follows through
`changes` (decision 6). *Alternative:* route them to `BookPlayer`. Rejected: the session can't tell the app's own
controller from System UI's without per-request controller checks, and the reconcile path already exists and is
tested.

### 5. Android: media buttons, tap and dismissal

- Media buttons: `MediaSession.Callback.onMediaButtonEvent` maps `KEYCODE_MEDIA_NEXT` and `KEYCODE_MEDIA_FAST_FORWARD`
  to `seekForward()`, and `KEYCODE_MEDIA_PREVIOUS` and `KEYCODE_MEDIA_REWIND` to `seekBack()`, on key down. Everything
  else (play, pause, play/pause, headset hook) is Media3's default handling. Bluetooth AVRCP and wired headsets
  arrive as these key events.
- Tap: the session activity is an explicit intent for the launcher activity with action
  `com.alexgabor.bookplate.action.SHOW_PLAYING` and `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_NEW_TASK`. It is
  explicit, so no intent filter is added, and it carries no data. `MainActivity` turns that action (in `onCreate`
  without saved state, and in `onNewIntent`) into an emission on a `Channel`, passed as `App(showPlaying = ...)` to
  `RootNavigation`, which opens `Player(id)` for `BookPlayer.state.value.book` unless Player already shows that book.
  This is why `sharedApp` and `androidApp` change: only the navigation layer can push Player, and only the activity
  receives the intent. It is not a deep link: there is no URL, no parameter, no `LaunchParameters`, and the back
  stack still starts at Home (its own saved state wins on recreation, the request is consumed once).
- Dismissal: by default Media3 keeps a paused service in the foreground for 10 minutes
  (`DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS`), and a foreground notification can't be dismissed. The service sets
  `setForegroundServiceTimeoutMs(0)`, so pausing leaves the foreground at once and the paused panel can be dismissed
  (on Android 16 the system UI's "Hide" for the media control). Media3 then hides the notification but leaves the
  service *started*; `onStartCommand` sees its dismissal intent (the `NOTIFICATION_DISMISSED_EVENT_KEY` extra)
  and, if the player isn't asked to play, calls `stopSelf()`. The app's controller may still be bound, so the
  session lives on until it lets go, and the next play from the app starts and promotes the service again.
  *Trade-off:* resuming from a headset button long after a pause, with the app in the background, has to start a
  foreground service from the background; Android allows that for media button events, and Media3 falls back to
  `onForegroundServiceStartNotAllowedException` otherwise.

### 6. Engine: saving moves the platform makes

A seek from the panel or a headset changes the output's position without a `BookPlayer` command. The Android
output now also signals `changes` on `EVENT_POSITION_DISCONTINUITY`, and the engine's reconcile, when it runs for a
`changes` signal (not a poll), saves the position. It doesn't compare with the state's position first: a poll may
already have caught up with the seek. Changes are rare (play, pause, seek, buffering), so the extra writes are few. So a seek while paused shows in Player and the library
at once, and one while playing is saved without waiting for the 5 s save. Reaching the end through a panel seek is
the existing ended path. iOS's remote commands call `BookPlayer` directly, so they are saved as app commands.

### 7. iOS: `NowPlayingPanel`

`core/player/impl/iosMain` gets `NowPlayingPanel(player: BookPlayer)`, started by `playerModule` with the engine.

- Info: it collects `player.state`, maps to `panelInfo()`, filters through `PanelUpdates`, and on the main queue sets
  `MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo`: `MPMediaItemPropertyTitle`, `MPMediaItemPropertyArtist`
  (subtitle), `MPMediaItemPropertyAlbumTitle` (book title), `MPMediaItemPropertyPlaybackDuration`,
  `MPNowPlayingInfoPropertyElapsedPlaybackTime`, `MPNowPlayingInfoPropertyPlaybackRate` (speed or 0),
  `MPNowPlayingInfoPropertyDefaultPlaybackRate` (speed), `MPNowPlayingInfoPropertyMediaType` audio, and
  `MPMediaItemPropertyArtwork` from `UIImage(contentsOfFile = coverPath)`, kept while the cover path doesn't change.
  With no book it sets `nil`. `playbackState` is set to playing/paused too.
- Commands: `MPRemoteCommandCenter.sharedCommandCenter` targets are added once: play → `play()`, pause → `pause()`,
  togglePlayPause → by the current state, skipBackward/skipForward (`preferredIntervals = [10]`) →
  `skipBack()`/`skipForward()`, changePlaybackPosition → `seekTo(positionTime)`. Each returns
  `NoActionableNowPlayingItem` with no book loaded. nextTrack, previousTrack, seekForward/seekBackward and
  changePlaybackRate are disabled.
- Headsets: the wired remote's centre click and Bluetooth play/pause arrive as these commands. Next/previous arrive as
  nextTrack/previousTrack, which are disabled, so they do nothing; iOS doesn't map them to skip intervals.

Because the commands go through `BookPlayer`, the state, saving and clamping are the app's; the panel then follows
the state like Player does.

## Risks / Trade-offs

- [`ConcatenatingMediaSource2` placeholder durations differ from decoded ones] → the panel's total may change by
  milliseconds once a file is prepared; the output maps per period, so book positions stay exact per file.
- [Media3 may route a media button through a path that skips `onMediaButtonEvent`] → checked on the emulator with
  `adb shell input keyevent` and `cmd media_session dispatch`; next/previous are the only keys that rely on it.
- [Large covers] → Media3's bitmap loader decodes the file; iOS loads the image once per cover path.
- [The simulator has no headset and no Bluetooth] → remote commands are exercised from Control Center and the lock
  screen only; headset buttons are reported as not verified on iOS.
- [The Android emulator has no Bluetooth headset] → media key events (`input keyevent`) stand in for it.

## Migration Plan

No data migration. Rolling back restores per-file items and the default notification.

## Open Questions

None.
