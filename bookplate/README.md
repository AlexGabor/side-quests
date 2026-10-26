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
- Each book is printed in its own pair of Riso inks, picked at random when it is added: in Player, in its row on
  Home and in the mini player. A swatch in Player's top bar shows another pair; tapping it gives the book that pair
  and shows a new one. A book without a cover shows artwork in its inks in the system media panel, which
  takes its colors from it.
- Speed from 0.3× to 3× in steps of 0.1× in a sheet, kept per book.
- Chapters in a sheet, grouped under their sections, with durations; the current one is highlighted and tapping one
  jumps to it.
- A mini player at the bottom of Home for the last-played book: title, progress and play/pause; tapping it opens
  Player. Hidden until a book has been played.
- Progress and speed are saved per book, and a book resumes where it was, at its speed, after a restart.
- Books play for real: the files of a book play back to back as one, at any speed without changing pitch, and keep
  playing with the screen off or locked and the app in the background (on Android with a media notification).
  Playback pauses for calls and other apps' audio (and resumes after a call on iOS), and when headphones are
  unplugged.
- The system media panel follows the playing book: on Android the notification and lock screen, on iOS Now Playing
  on the lock screen and in Control Center. It shows the cover, the title, the current chapter (or the author when
  there is no chapter title) and a seek bar over the whole book, with play/pause and rewind/forward 10 s. On Android
  tapping it opens Player for that book, and a paused notification can be swiped away.
- Headset and Bluetooth buttons play and pause; on Android next/previous skip 10 s.
- Books whose files were moved, renamed or deleted, or that Bookplate may no longer open, are noticed when the
  library loads, when a book opens in Player and while it plays: Home marks them ("Files missing" or "Access lost"),
  and Player shows why instead of playing, with "Relink" and "Check again". Files that come back are found again
  without a relink.
- Relink a book by picking where its folder or file is now. Files are matched by their path in the folder, then by
  name, then by size and length. When only some are found, Bookplate says which are missing and asks first. Missing
  parts are then skipped, and a later relink can bring them back. Progress, speed, chapters and cover are kept, and
  a folder that is already another book is refused. The original files are only ever read.
- Works offline: no network access is requested.
- No links: the app only opens from its icon.

See [ROADMAP.md](ROADMAP.md) for what comes next.
