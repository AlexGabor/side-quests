# Bookplate roadmap

Milestones that turn [initial-specs.md](initial-specs.md) into the app. Each one is sized for a single agent
session. They run one at a time, in order, and each is verified before the next starts.

## How each milestone runs

1. The agent reads the root `CLAUDE.md`, [docs/architecture](../docs/architecture/README.md), `bookplate/CLAUDE.md`
   (from M1 on) and this file.
2. `/opsx:propose <change-name>` with the scope below, then `/opsx:apply` in the same session. Spec capabilities are
   prefixed `bookplate-*`, `riso-*` or `lib-*`.
3. It stops when the "Done when" list holds, and reports what it could not verify.
4. After review: `/opsx:archive`, tick the milestone here.

Decisions already made:

- **Targets:** Android and iOS ship. Core and `lib` modules also build for `jvm()` so their tests run as fast
  unit tests; there is no desktop or web app.
- **Parsing:** our own metadata and chapter parsers in common Kotlin, tested on the JVM against fixture files.
- **Verification:** Android (emulator + Maestro), iOS (simulator + Maestro), JVM unit tests for core.
- **Storage:** Room (KMP) with the bundled SQLite driver. Original audio files are only ever read.
- **No deep links:** Bookplate has no public links or URL parameters; navigation is in-app only.
- **Cover art:** shown as a plain image (not printed through Riso inks).
- **Library:** a list of rows, not a grid.
- **Recently updated:** the later of a book's last-played and imported times.
- **Duplicates:** adding a book that is already in the library is refused.

## Module map (target)

```
bookplate/
  androidApp/  iosApp/  sharedApp/
  feature/home/            library, search, add, mini player
  feature/player/          player screen, speed + chapter sheets
  core/library/{api,impl,test}   Room database, books, import, relink
  core/player/{api,impl,test}    playback engine, per-book progress & speed
lib/
  audiometadata/           format detection, tags, chapters, cover art, duration (common Kotlin)
  documentaccess/{api,impl,test} picking files/folders, persistent access, random-access reads
design/riso/               SmallProgress, BottomSheet, SearchField, new icons
```

Module names are a proposal; the M1 and M3 agents may adjust them in their OpenSpec design.

---

## M1 — App skeleton and build targets ✅

**Change:** `bookplate-app-skeleton`

- Convention plugin support for an Android + iOS (+ `jvm()` for tests) library, without `wasmJs`. Either a new plugin
  or an option on the existing ones; Pacer's modules must not change behaviour.
- `bookplate/androidApp`, `bookplate/iosApp` (Xcode project, like Pacer's), `bookplate/sharedApp` with Koin, Riso
  theme, and root navigation with two destinations: `Home` and `Player(bookId)`. Both screens are placeholders.
  No deep links: no URL handling, intent filters or `LaunchParameters` mapping beyond what the shared navigation
  needs to start at Home.
- Offline: no `INTERNET` permission on Android.
- `bookplate/CLAUDE.md` (running, tests, Maestro commands), README feature list started.
- One Maestro smoke flow: app launches, Home placeholder visible.

**Done when:** both apps build and launch on emulator/simulator; Pacer still builds; smoke flow passes on both.

## M2 — Riso components: small progress, search field, icons ✅

**Change:** `riso-small-progress-search-icons`

- `SmallProgress`: a line, completed part in accent at full opacity, remaining part in content color at 50%.
- `SearchField`: single-line Riso text input with search icon and clear action (Riso has no text field yet).
- Icons: play, pause, rewind 10 s, forward 10 s, chapters, speed, add, search, close. Same style as the existing
  `IconPaths`.
- Showcased in `design/risoDemo`; `riso-*` specs.

**Done when:** demo shows each component (Riso has one light palette, no dark theme) and progress at 0 %, partial and
100 %; JVM/screenshot tests
where the design system already has them.

## M3 — Bottom sheet ✅

**Change:** `riso-bottom-sheet`

- Modal bottom sheet that behaves like Material's (drag to dismiss, back/scrim tap dismisses, sizes to content,
  predictive back on Android).
- The background is lowered in opacity rather than covered by a scrim, so the Riso paper dots show through.
- Showcased in `design/risoDemo`.

**Done when:** sheet works on Android and iOS in the demo, including drag, back and scrim tap.

## M4 — Metadata parser foundation + MP3 ✅

**Change:** `lib-audiometadata-mp3`

- New `lib/audiometadata` (app agnostic). A random-access byte source interface the platform layer will implement
  later; format detection by magic bytes, not extension.
- Result model: title, album, artist/album artist, narrator/composer, genre, year, track/disc numbers, duration,
  embedded cover (bytes + mime), chapters (title, start, end, nesting for sections).
- MP3: ID3v1, ID3v2.2/2.3/2.4 (text frames, APIC, CHAP, CTOC with nesting), duration from Xing/Info/VBRI header,
  CBR fallback.
- Fixtures generated with `ffmpeg` (short silent clips) plus a script to regenerate them; committed and small.

**Done when:** JVM tests cover each frame type and each duration path; malformed input returns a partial result
instead of throwing.

## M5 — MP4 / M4B parser  ✅

**Change:** `lib-audiometadata-mp4`

- M4A/M4B/MP4 (AAC, ALAC): `ilst` tags (incl. `covr`, `©nar`-style narrator if present), `mvhd`/`mdhd` duration,
  Nero `chpl` chapters and QuickTime chapter text tracks (prefer QuickTime when both exist).
- Large `mdat` handled without reading it.

**Done when:** JVM tests with fixtures for each chapter style; a multi-hour M4B fixture header (no audio payload
needed) parses quickly.

## M6 — FLAC, Ogg (Vorbis/Opus), WAV, AAC

**Change:** `lib-audiometadata-other-formats`

- FLAC: STREAMINFO duration, Vorbis comments, PICTURE, CUESHEET.
- Ogg Vorbis and Opus: comments, `METADATA_BLOCK_PICTURE`, `CHAPTERxxx` comments, duration from the last granule.
- WAV: RIFF `INFO` tags, duration from format + data size.
- Raw AAC (ADTS): duration only.
- Standalone `.cue` sheets as a chapter source for a single file.

**Done when:** JVM tests per format; the supported-format list is in the `lib-audiometadata` spec.

## M7 — Document access (platform files)

**Change:** `lib-document-access`

- New `lib/documentaccess/{api,impl,test}`: pick a single file or a folder; keep access across restarts (Android:
  SAF with persistable URI permissions; iOS: security-scoped bookmarks); list a folder recursively; open a reference
  as the M4 random-access source; check whether a reference is still reachable.
- Never writes to, moves or deletes the user's files.
- Fake in `test` for core tests.

**Done when:** a temporary debug entry in Bookplate Home (removed in M9) picks a file/folder and lists its audio
files on Android and iOS, still readable after an app restart.

## M8 — Library storage and import

**Change:** `bookplate-library-import`

- `bookplate/core/library/{api,impl,test}`: Room database (books, files, chapters/sections, source references,
  per-book position, speed, last-played and updated timestamps, cover path). Schema exported for future migrations.
- Import from a file or folder: enumerate audio files, natural sort, parse each with `lib/audiometadata`, then
  assemble one book:
  - title/author from tags (album, album artist), falling back to folder or file name;
  - chapters from embedded chapters; otherwise one chapter per file; sub-folders (e.g. `Disc 1`, `Part 2`) become
    sections;
  - cover from embedded art, else `cover.*`/`folder.*` in the folder; copied into app storage.
- Adding a book that is already in the library (same source file/folder) is refused with a distinct result the UI
  can show; nothing in the library changes.
- Repository flows: library sorted by most recently updated (the later of last played and imported), filter by
  title/author, last-played book.

**Done when:** JVM tests import fixture books (single M4B, multi-file MP3 folder, nested sections, no tags) through
the fake document access and read them back from Room.

## M9 — Home screen

**Change:** `bookplate-home`

- `bookplate/feature/home`: library as a list of rows with cover (plain image, placeholder when missing), title,
  author, total duration, percent progress with `SmallProgress`; sorted by recently updated; `SearchField` filtering
  by title/author; add button next to it opening the picker and running the import with progress and error states,
  including "already in library"; empty state.
- Tapping a book opens `Player(bookId)` (still a placeholder).
- Remove the M7 debug entry.
- Maestro flows: add a book, search, open a book. Flows need a way to get fixture books onto the
  emulator/simulator; the agent sets that up and documents it in `bookplate/CLAUDE.md`.

**Done when:** flows pass on Android and iOS; library survives an app restart.

## M10 — Player screen and sheets (UI against a fake player)

**Change:** `bookplate-player-screen`

- `bookplate/core/player/api` (+ `test` fake): state (book, position, duration, playing, speed, current chapter) and
  commands (play, pause, seek, skip ±10 s, set speed, jump to chapter).
- `bookplate/feature/player`: top bar (back, chapters, speed), title and author, cover (plain image) that shrinks so everything fits
  on small screens, chapter title when present, position/duration above a scrubbable `Track`, rewind 10 / play-pause /
  forward 10 row.
- Speed sheet: `Track` from 0.3x to 3x, snapping to 0.1x. Chapters sheet: title and duration per chapter, current one
  in accent, tap to jump.
- Mini player in Home's bottom bar: last played book, title, `SmallProgress`, play/pause; opens Player; hidden when
  there is no last-played book.
- Bound to the fake in `sharedApp` for now, so it is fully clickable.

**Done when:** UI matches the spec on a small and a large phone on both platforms; state-holder tests; Maestro flows
for sheets and mini player.

## M11 — Playback engine, Android

**Change:** `bookplate-playback-android`

- `bookplate/core/player/impl` common part: a book's files as one timeline (book position ↔ file + offset), chapter
  tracking, saving position (periodically, on pause, on background) and speed per book, updating last-played and
  updated timestamps. Tests on JVM.
- Android: Media3 ExoPlayer inside a `MediaSessionService` (background playback, the default media notification),
  pitch-preserving speed, audio focus and becoming-noisy handling, reading through `lib/documentaccess`.
- `sharedApp` binds the real player on Android; iOS stays on the fake.

**Done when:** on Android a multi-file book plays across file boundaries, resumes at the saved position and speed
after a restart, and keeps playing with the screen off.

## M12 — Playback engine, iOS

**Change:** `bookplate-playback-ios`

- iOS implementation with AVFoundation (`AVQueuePlayer` or equivalent) using the same common timeline and
  persistence; background audio mode and audio session; interruptions (calls, Siri) and route changes (headphones
  unplugged); security-scoped access while playing.

**Done when:** the M11 checks pass on iOS.

## M13 — Notification and lock screen panel

**Change:** `bookplate-media-panel`

- Android: media notification and lock screen with cover, book title, chapter, seek bar, play/pause and ±10 s.
- iOS: Now Playing info (cover, title, chapter, elapsed, rate) and remote commands (play/pause, skip ±10 s,
  scrubbing).
- Bluetooth/headset buttons on both.

**Done when:** controls from the panel and from headset buttons work on both platforms and the panel follows chapter
changes.

## M14 — Relink moved files

**Change:** `bookplate-relink`

- Detect books whose files are gone or no longer accessible (on library load and when playback starts); show them as
  unavailable in Home and Player.
- Relink: user picks the new file/folder; files are matched (relative path, name, size, duration) and the user is told
  when the match is partial; progress, speed and chapters are kept.

**Done when:** on both platforms, moving or renaming a book's folder, then relinking it, resumes at the same
position; JVM tests for the matching rules.

## M15 — Hardening

**Change:** `bookplate-hardening`

- Large libraries (hundreds of books) and very long books: scrolling, search and import performance; cover
  thumbnails.
- Process death and configuration changes on Player and Home.
- Edge cases: book with no chapters, zero-length or corrupt files, unsupported files inside a folder.
- Full Maestro pass on both platforms; README feature list complete; `known-deviations.md` updated.

**Done when:** all flows and tests green on both platforms.
