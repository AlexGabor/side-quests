# Bookplate

Completely offline custom audiobook app with Riso theme.

Targets: Android and iOS. Shared modules also build for the JVM, only so their tests run as plain unit tests.

## Features

- Opens on Home: the library as a list, most recently updated first. Each book shows its cover (or a placeholder),
  title, author, total duration and progress as a percent.
- Search by title or author.
- Add a book from a folder or a single audio file with the + button. Import shows its progress and result; a book
  that is already in the library is refused. Books stay in the library across restarts.
- Tapping a book opens Player: title, author, cover, current chapter, position and duration over a ruler you can
  drag to scrub, and rewind 10 s / play-pause / forward 10 s. Back returns to Home and keeps playing.
- Speed from 0.3× to 3× in steps of 0.1× in a sheet, kept per book.
- Chapters in a sheet, grouped under their sections, with durations; the current one is highlighted and tapping one
  jumps to it.
- A mini player at the bottom of Home for the last-played book: title, progress and play/pause; tapping it opens
  Player. Hidden until a book has been played.
- Progress and speed are saved per book. There is no audio yet: playback is simulated by a clock until the real
  engines land.
- Works offline: no network access is requested.
- No links: the app only opens from its icon.

See [ROADMAP.md](ROADMAP.md) for what comes next.
