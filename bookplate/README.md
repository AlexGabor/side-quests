# Bookplate

Completely offline custom audiobook app with Riso theme.

Targets: Android and iOS. Shared modules also build for the JVM, only so their tests run as plain unit tests.

## Features

- Opens on Home: the library as a list, most recently updated first. Each book shows its cover (or a placeholder),
  title, author, total duration and progress as a percent.
- Search by title or author.
- Add a book from a folder or a single audio file with the + button. Import shows its progress and result; a book
  that is already in the library is refused. Books stay in the library across restarts.
- Tapping a book opens Player, which is a placeholder for now; back returns to Home.
- Works offline: no network access is requested.
- No links: the app only opens from its icon.

See [ROADMAP.md](ROADMAP.md) for what comes next.
