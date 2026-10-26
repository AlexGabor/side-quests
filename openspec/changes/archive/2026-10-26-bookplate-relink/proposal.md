## Why

Bookplate keeps references to the user's files and never copies them, so a book breaks when its folder or file is
moved, renamed or deleted, or when the platform drops the app's grant. Today such a book fails silently: Home shows
it as usual and Player tries to play it. The original brief asks that the app "allows relink to original files if
they are moved or renamed". This is roadmap milestone M14.

## What Changes

- **Detection.** A book's root reference and files are checked for reachability when the library loads (bounded,
  off the main thread, results arrive after Home shows) and when playback starts. A book whose files can't be
  reached is *unavailable*, either because files are **missing** or because **access was lost**. Availability is
  derived and held in memory, not stored. A book whose files come back is available again at the next check.
- **Playback.** Starting playback of an unavailable book doesn't play. A playback error re-checks the book, so a
  file that disappears during playback stops it and marks the book unavailable. Files a partial relink left out are
  skipped on the timeline.
- **Home.** Unavailable rows say why ("Files missing" / "Access lost") and that tapping them relinks. The mini
  player shows an unavailable book without a play button.
- **Player.** An unavailable book shows its state, a "Relink" action and "Check again" instead of the controls. The
  relink flow runs here: it picks a folder for a folder book or a file for a single-file book, asks to confirm a
  partial match, and shows the result.
- **Relink** (`core/library`). The new location's audio files are matched to the stored ones by relative path,
  then by name, then by size and duration. The results are:
  - full match: references updated, nothing else changes;
  - partial match: the user confirms or cancels. Files not found are kept in the book but *left out*, and a later
    relink can bring them back;
  - no match: refused;
  - wrong kind (a folder picked for a file book, or the reverse): refused;
  - another book's source: refused, consistent with the duplicate rule;
  - the same location of an available book: a no-op.

  Position, speed, chapters, cover and timestamps are never touched, and the user's files are only read.
- **Storage.** The library database goes to schema v2: `audio_files.missing`, with a migration from v1.
- **Test doubles.** `FakeDocumentAccess` can follow renames of the picked item (iOS bookmark behaviour) and can
  slow down and count reachability checks. `core/library/test` gains a `FakeBookAvailability` and a
  `FakeBookRelinker`. `FakeBookPlayer` checks availability like the engine does.
- **Maestro.** Relink flows run in steps, with host shell steps in between to move, rename and delete files. A
  driver script, `bookplate/scripts/relink-flows.sh`, runs them on Android and iOS.
- The README feature list mentions unavailable books and relinking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `bookplate-library`: book availability (detection rules), relinking (kinds, matching rules, results, partial
  confirmation, duplicate refusal, what is kept), files left out by a partial relink, schema v2, and test doubles.
- `bookplate-home`: unavailable rows, the check when the library loads, and the mini player for an unavailable book.
- `bookplate-player`: playback checks availability at start and on errors, the timeline skips left-out files, the
  Player's unavailable state and its relink flow, and the fake player.
- `lib-document-access`: the test double follows renames of the picked item when asked, and can count and slow
  down reachability checks.

## Impact

- `bookplate/core/library/api`: new `BookAvailability`, `Availability`, `BookRelinker`, `RelinkResult`;
  `AudioFile.isMissing`.
- `bookplate/core/library/impl`: `BookAvailabilityImpl`, `BookRelinkerImpl`, a pure `RelinkMatcher`, DAO updates,
  schema v2 JSON and the 1→2 migration.
- `bookplate/core/library/test`: the new fakes; `fakeLibraryModule` binds them.
- `bookplate/core/player/{api,impl,test}`: `EngineBookPlayer` and `FakeBookPlayer` take `BookAvailability`, and
  `BookTimeline` leaves out missing files. Android and iOS `playerModule` pass it in.
- `bookplate/feature/home`: `HomeState` and `MiniPlayerState` take `BookAvailability`, plus row and mini player UI.
- `bookplate/feature/player`: `PlayerScreenState` takes `BookAvailability` and `BookRelinker`, plus the unavailable
  and relink UI and the picker.
- `lib/documentaccess/test`: `FakeDocumentAccess` options.
- `bookplate/maestro/relink/` flows, `bookplate/scripts/relink-flows.sh`, `bookplate/CLAUDE.md`, `bookplate/README.md`.
- No new permissions, no network, and no deep links. Pacer is untouched.
