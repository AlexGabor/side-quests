## Why

Bookplate has a library (M8) and a Home screen (M9), but tapping a book still opens the M1 Player placeholder and
nothing can be played. M10 builds the whole player UI — Player screen, speed and chapter sheets, Home's mini player —
against a fake player, so the screens can be reviewed on both platforms before real audio engines land (M11 Android,
M12 iOS, M13 notification and lock screen).

## What Changes

- New `bookplate/core/player/api`: a `BookPlayer` interface with a `PlaybackState` flow (the loaded book, its chapters,
  position and duration across the whole book, playing or paused, speed, current chapter and its index) and commands
  (load a book, play, pause, seek, skip back / forward 10 s, set speed 0.3×–3× in tenths, jump to a chapter). Positions
  are book-wide; an engine maps them to files. Shaped so Media3 (M11), AVFoundation (M12) and the media panel (M13)
  can implement and drive it.
- New `bookplate/core/player/test`: `FakeBookPlayer`, which advances position on a ticking clock at the book's speed
  and saves position, speed and last-played time through `LibraryRepository`, plus a Koin module. `impl` is left for
  M11.
- New `bookplate/feature/player`, replacing `sharedApp`'s `PlayerPlaceholder`:
  - `PlayerScreen` with its `PlayerScreenState` state holder: top bar (back, chapters, speed), title and author, the
    cover as a plain image that shrinks so everything fits on small screens, the current chapter's title, position and
    duration above a scrubbable `Track`, and a rewind 10 s / play-pause / forward 10 s row.
  - Speed sheet: a Riso `BottomSheet` with a `Track` from 0.3× to 3× snapping to 0.1×; the speed is saved per book.
  - Chapters sheet: a `BottomSheet` listing chapters with their durations, grouped under section headings when the
    book has sections; the current chapter in accent; tapping one jumps to it.
- `bookplate/feature/home`: a mini player in the bottom bar for the last-played book (title, `SmallProgress`,
  play/pause), opening Player when tapped and hidden when nothing has been played. Home now depends on
  `core/player/api`.
- `bookplate/sharedApp`: binds `FakeBookPlayer` as `BookPlayer` (temporary, until M11/M12 bind real engines), the
  Player entry renders `feature/player`, `Placeholders.kt` goes.
- `design/riso`: `Button` gains an icon overload (for play/pause), `Icon` a `color` parameter, `TrackState` a
  fractional `moveTo` so the position ruler glides while playing, `Track` a `showSelectedLabel` switch, and
  `TrackState` an optional end inside its last item so the position ruler ends at the book's exact end.
- Maestro flows for opening a book, play/pause, skipping, the speed sheet, the chapters sheet and the mini player.
- `bookplate/README.md` feature list, `bookplate/CLAUDE.md` (tests, module notes), and a `known-deviations.md` entry for
  `sharedApp` depending on a `test` module while the fake is bound.

## Capabilities

### New Capabilities
- `bookplate-player`: the playback contract (state and commands, book-wide positions, speed range and persistence per
  book) and the Player screen with its speed and chapters sheets.

### Modified Capabilities
- `bookplate-home`: the bottom bar now holds the mini player for the last-played book, hidden when there is none.
- `bookplate-navigation`: Player shows the chosen book instead of its id, and also opens from the mini player.

## Impact

- New modules `bookplate/core/player/api`, `bookplate/core/player/test`, `bookplate/feature/player` and their
  `settings.gradle.kts` includes. `feature/player` depends on `design/riso`, `core/player/api` (which exposes
  `core/library/api` for `BookId` and `Chapter`), `lib/coroutine/dispatchers`, Koin Compose, okio and Compose resources
  (for the cover).
- `bookplate/feature/home` gains `core/player/api`; tests use `core/player/test`.
- `bookplate/sharedApp` gains `feature/player` and `core/player/test` (temporarily, for the fake binding); loses
  `Placeholders.kt`.
- `bookplate/maestro/flows`, `bookplate/README.md`, `bookplate/CLAUDE.md`, `docs/architecture/known-deviations.md`.
- `design/riso` (`Button`, `Icon`, `TrackState`, all additive) and `design/risoDemo`; `lib/` is unchanged. Pacer's
  behaviour and public link parameters are unaffected.
