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
- Opening another book while one plays leaves it playing: the other book shows at its saved place, where you can
  move around in it, and tapping Play switches to it.
- Speed from 0.3× to 3× in steps of 0.1× in a sheet, kept per book.
- Chapters in a sheet, grouped under their sections, with durations; the current one is highlighted and tapping one
  jumps to it.
- A mini player at the bottom of Home for the last-played book: title, progress and play/pause; tapping it opens
  Player. Hidden until a book has been played.
- Progress and speed are saved per book, and a book resumes where it was, at its speed, after a restart.
- On Android, books play for real: the files of a book play back to back as one, at any speed without changing
  pitch, and keep playing with the screen off or the app in the background, with a media notification. Playback
  pauses for calls and other apps' audio, and when headphones are unplugged. On iOS playback is still simulated by a
  clock until its engine lands.
- Works offline: no network access is requested.
- No links: the app only opens from its icon.

See [ROADMAP.md](ROADMAP.md) for what comes next.
