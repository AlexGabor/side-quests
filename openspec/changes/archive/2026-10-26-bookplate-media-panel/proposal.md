## Why

Bookplate plays for real on both platforms (M11, M12), but outside the app all it shows is Media3's default
notification on Android (per-file next/previous, the file's own progress, no cover or chapter) and nothing at all on
iOS. The initial spec asks for a "Platform Panel in notification and lockscreen": M13 makes the system's media panel
and headset buttons a second remote for the same player the app shows.

## What Changes

- `bookplate/core/player/impl` (common): `PanelInfo`, what a media panel shows for the loaded book (title, the
  current chapter or else the author, cover path, book-wide duration and position, playing, speed), built from
  `PlaybackState`, and `PanelUpdates`, which decides when a panel that extrapolates time must be told again.
- `EngineBookPlayer`: a seek the platform makes (the panel's seek bar or ±10 s, a headset button) is saved like a
  seek made in the app.
- Android (`core/player/impl/androidMain`):
  - a book plays as **one** media item: its files are concatenated (`ConcatenatingMediaSource2`) so the panel's seek
    bar, position and duration are the whole book's, and seeking crosses files; `Media3AudioOutput` maps file
    positions to and from it;
  - the session wraps ExoPlayer in a `PanelPlayer` that shows the book title, the chapter (or author) and the cover
    from app storage, updates them when the chapter changes, and offers play/pause and seek ±10 s instead of
    previous/next;
  - media buttons (Bluetooth, wired headsets, `KEYCODE_MEDIA_*`): play/pause/toggle, and next/previous or fast
    forward/rewind skip 10 s;
  - tapping the notification opens Player for the playing book; swiping it away while paused removes it and takes
    the service out of the foreground.
- iOS (`core/player/impl/iosMain`): `NowPlayingPanel` fills `MPNowPlayingInfoCenter` (title, chapter or author,
  artwork, duration, elapsed, rate) and handles `MPRemoteCommandCenter` play, pause, togglePlayPause, skip ±10 s and
  changePlaybackPosition, with next/previous track disabled. Headset play/pause arrives through the same commands.
- `bookplate/sharedApp` and `bookplate/androidApp`: a request to show the playing book (from the notification's tap)
  opens Player for it on top of the back stack. It carries no URL or parameters; root navigation still starts at
  Home and no deep link is provided.
- Docs: README feature list, `bookplate/CLAUDE.md`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `bookplate-player`: adds "Media panel content", "Android media panel", "iOS Now Playing" and "Headset and media
  buttons"; "Pauses the platform makes" also covers seeks the platform makes; "Android playback engine" plays a book
  as one item and no longer shows the default notification's per-file controls.
- `bookplate-navigation`: Player also opens for the playing book when the Android media notification is tapped.

## Impact

- Changed: `bookplate/core/player/impl` (common panel model, engine save on platform seeks, Android service/output,
  new iOS panel), `bookplate/sharedApp` (`RootNavigation` takes the show-playing requests), `bookplate/androidApp`
  (`MainActivity` turns the notification's intent into such a request), README, `bookplate/CLAUDE.md`.
- Unchanged: `bookplate/core/player/api`, `bookplate/core/player/test`, `bookplate/feature/*`, `core/library`,
  `lib/*`, Pacer. No Pacer link parameters are touched. No new permissions; still no `INTERNET`.
- No new dependencies: Media3 1.11.1 (`media3-exoplayer`, `media3-session`) and MediaPlayer.framework.
