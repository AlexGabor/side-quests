## Context

M8 gave Bookplate a library (`core/library`, Room) with per-book `position`, `speed` and `lastPlayedAt` and the
`setPosition` / `setSpeed` / `setLastPlayed` writes. M9 built Home with an empty `bottomBar` slot. Player is still the
M1 placeholder in `sharedApp/Placeholders.kt`. M11 (Media3), M12 (AVFoundation) and M13 (notification / lock screen)
will implement and drive a player API that does not exist yet. This change defines that API, a fake that behaves like a
player, and every player UI on top of it.

## Goals / Non-Goals

**Goals:**
- A `BookPlayer` API that the two engines can implement and the media panel can drive without changes to the UI.
- A fake realistic enough that Home's progress, order and the mini player behave as they will with real audio.
- Player screen, speed and chapters sheets, mini player, all following `architecture.md` state holders.

**Non-Goals:**
- Audio, `core/player/impl`, background playback, audio focus, media sessions (M11–M13).
- Missing or unavailable books (M14), process-death of playback itself, very long books' performance (M15).

## Decisions

### `BookPlayer`: one state flow and fire-and-forget commands

```kotlin
interface BookPlayer {
    val state: StateFlow<PlaybackState>
    fun load(id: BookId)
    fun play(); fun pause()
    fun seekTo(position: Duration)
    fun skipBack(); fun skipForward()          // 10 s, SkipInterval
    fun setSpeed(speed: Float)                 // snapped to 0.1, 0.3..3.0
    fun jumpToChapter(index: Int)              // index into playableChapters
}

data class PlaybackState(val book: PlayerBook?, val position: Duration, val isPlaying: Boolean, val speed: Float) {
    val duration; val chapterIndex: Int?; val chapter: Chapter?   // derived
}
data class PlayerBook(val id: BookId, val title: String, val author: String?, val coverPath: String?,
                      val duration: Duration, val chapters: List<Chapter>) { val playableChapters }
```

- Commands are plain functions, not `suspend`: a media session callback (M13), a Swift remote-command handler (M12) and
  a click handler can all call them directly. Each implementation runs them in order on its own scope.
- State is a single `StateFlow`, the shape `MediaSession` / `MPNowPlayingInfoCenter` need: one snapshot with title,
  cover, chapter, position, duration, rate and playing.
- Positions are book-wide `Duration`s. The file mapping is engine-internal (M11's common timeline), so UI and panel
  never see files. `PlayerBook` carries chapters with sections (reusing `core/library`'s `Chapter`) so the chapters
  sheet can group them; `chapterIndex` indexes `playableChapters`, matching `jumpToChapter`.
- Current chapter, `SpeedRange`, `snapSpeed` and `SkipInterval` live in the api as small pure functions, so every
  engine agrees on them and they are tested once.
- The player owns its own loaded book (not the screen): Home's mini player, Player and the panel share one instance.
- Alternative considered: `suspend` commands returning results. Rejected — callers would need scopes everywhere, and
  the result is already visible in `state`.
- Alternative considered: separate flows per field. Rejected — consumers combine them anyway, and a single snapshot
  can't tear (e.g. new book with old position).

### `FakeBookPlayer` in `core/player/test`

- Commands go into an unbounded channel; one coroutine, started on the first command, runs them in order on
  `CoroutineDispatchers.Default` (read at launch), so `load` then `play` from the mini player is ordered and tests
  using `TestScopeRule` drive it with virtual time.
- While playing, a ticker job advances the position every 250 ms by `250 ms × speed`, stops at the end, and saves the
  position every 5 s of book time. Pause, seek, skip, chapter jumps and switching books save immediately; play and
  pause also `setLastPlayed(clock.now())`. The `Clock` is injected.
- Constructor: `FakeBookPlayer(repository, scope, clock = Clock.System)`. `fakePlayerModule` binds a singleton with an
  app-lifetime `CoroutineScope(SupervisorJob())`.

### Binding the fake in `sharedApp` (temporary deviation)

`sharedApp` `commonMain` depends on `:bookplate:core:player:test` and includes `fakePlayerModule`, as the milestone
asks, so the UI is clickable before M11. This is production code depending on a `test` module, which
`known-deviations.md` already flags for Pacer; a new row records it for Bookplate, to be removed when M11 binds the
Android engine and M12 the iOS one. Alternative considered: a `core/player/fake` production module — rejected as a
module that exists for two milestones and then would be deleted anyway.

### `feature/player`

- `PlayerScreen(bookId, onBackClick, state = rememberPlayerScreenState(bookId))`. `PlayerScreenState(scope, bookId,
  player, sheet)` calls `player.load(bookId)` on creation (a no-op for the loaded book), and exposes:
  - `ui: State<PlayerUi?>` — `null` until the player holds `bookId`; then title, author, cover path, chapter title,
    position / duration labels, the position in 10-second ticks, playing, speed, and the chapter list rows.
  - `scrubbing: State<Duration?>` — set while the user drags the track, shown in the position label instead of the
    playback position.
  - `sheet: State<PlayerSheet?>` (`Speed`, `Chapters`) — saved with `rememberSaveable`, like Home's add sheet.
  - Commands delegating to the player: `onPlayPause`, `onSkipBack`, `onSkipForward`, `onScrub`, `onScrubEnd`,
    `onSpeedChange`, `onChapterClick`, `onSpeedClick`, `onChaptersClick`, `onSheetDismissed`.
- Layout: a `Column` inside the safe-drawing area and `contentWidth`. The cover is in a `Box(Modifier.weight(1f))`
  holding a square (`aspectRatio(1f, matchHeightConstraintsFirst = true)`), so it takes whatever height is left after
  the fixed elements and shrinks on small screens, never pushing the controls off.
- Cover: a copy of Home's `BookCover` (okio read, `decodeToImageBitmap`, `risoFadeAsAlpha`, placeholder letter). Two
  feature modules need it, but it is app-specific (reads app storage paths) so it can't go in `design/riso`; a shared
  Bookplate UI module for one composable isn't worth it yet. Kept internal in each feature.
- Position `Track`: items are whole minutes `0..floor(duration / 1 min)`, subdivisions 6 (10 s), item size 60 dp, and
  `endTick = duration / 10 s`: a new optional `TrackState` end, inside the last item and between lines if need be. The
  ruler is drawn up to it and closed by a long line, its end padding lets it scroll exactly that far, and
  `TrackState.isAtEnd` lets a drag to the very end seek to the duration rather than the last 10-second line.
  The ruler labels whole minutes (`m:00` / `h:mm:00`) and prints nothing at the guideline (`showSelectedLabel = false`,
  a new defaulted `Track` parameter): resting between lines, a guideline label would sit next to the ruler's own; the
  text label above carries the exact position. Sync follows Pacer's sliders:
  `trackUserScroll()`; while scrolling, report the tick to `onScrub`; when scrolling stops after a user scroll,
  `onScrubEnd` seeks. While the user isn't scrolling the ruler follows the position. Paused, it moves there (animated).
  Playing, it glides: from each reported position it advances by elapsed × speed one pixel at a time (about 3 moves a
  second at 1×), until the next report restarts it.
- Gliding is drawn, not laid out: `moveTo` scrolls the list to the whole line and shifts the items by the remaining
  fraction in a graphics layer, so the layout only moves once per line. Compose reports layout moves to accessibility
  services, and Android UI automation (Maestro) waits for them to go quiet before each read; a ruler scrolling several
  times a second never lets them. The ruler is also hidden from accessibility (`clearAndSetSemantics`); the position
  label reads the position. This needs `TrackState.moveTo(ticks: Float, animate)` in `design/riso`, a fractional version of
  `moveToTick` (which now delegates to it): an engine reporting every 250 ms (or every second) would otherwise step a
  10-second line at a time.
- `Track` gains `showSelectedLabel` (default `true`, so Pacer and the speed sheet are unchanged).
- Play/pause is a Riso `Button`. `Button` only took text, so `design/riso` gains an icon overload
  (`Button(icon, contentDescription, onClick)`, the glyph knocked out of the fill like the text), and `Icon` a `color`
  parameter (default content) so it can be drawn in the paper color. Shown in `risoDemo`'s player-like row.
- Speed sheet: like `risoDemo`'s `SheetsDemo` — items `0..3`, subdivisions 10, tick = tenths, clamped up to 3, and
  each settled tick sent to `onSpeedChange(tick / 10f)`. The heading reads the player's speed.
- Chapters sheet: a `LazyColumn` of section headings and chapter rows built by the state holder (`ChapterRow(title,
  duration, index, isCurrent, depth)` / section heading), scrolled to the current chapter on open; a row tap jumps and
  `dismiss()`es the sheet.

### Mini player in `feature/home`

- `MiniPlayerState(scope, repository, player)` combines `repository.lastPlayed` with `player.state`: when the player
  holds the last-played book, its live position and playing state; otherwise the library's saved position, paused.
  `mini: State<MiniPlayerUi?>` (`bookId`, title, progress, `isPlaying`), `null` hides it. `onPlayPause()` loads the
  book first when the player holds another one.
- `HomeScreen` renders it in its bottom bar instead of taking a `bottomBar` slot from `sharedApp`; tapping it calls
  the same `onOpenBook`. The list's bottom inset padding moves to the bar while the bar is shown.
- Home depends on `core/player/api` only.

## Risks / Trade-offs

- [Fake's ticking writes to Room every 5 s while playing] → cheap at this size; M11 replaces it with the engine's own
  save policy.
- [Track with one item per minute is long for 20-hour books] → lazy, so it lays out only what's visible; scrubbing far
  relies on fling. Revisit in M15 with real long books if it's too slow.
- [Speed sheet drags snap only when the fling settles, so intermediate speeds are applied while dragging] → each one is
  valid and saved; the last one wins.
- [sharedApp depends on a `test` module] → recorded in `known-deviations.md`, removed by M11/M12.
- [Maestro can't drag a ruler to an exact value] → the speed flow reads the heading after a swipe with `copyTextFrom`
  and checks the same text after reopening, instead of asserting a fixed speed.

## Migration Plan

No data migration: the schema is unchanged; the fake uses the existing `position`, `speed` and `lastPlayedAt` columns.
