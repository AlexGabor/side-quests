## ADDED Requirements

### Requirement: One timeline across a book's files
`bookplate/core/player/impl` SHALL provide `EngineBookPlayer`, implementing every `BookPlayer` requirement over a
platform audio output. It SHALL play a book's files in their library order as one timeline: a book position SHALL map
to the file whose span (its start in the book to its start plus its duration) holds it and the offset into that file,
and a file and offset SHALL map back to the file's start plus the offset, clamped to the book. A position on the
boundary between two files SHALL map to the start of the later one. Files whose duration is zero (unreadable) SHALL
be left out of playback. Seeking, skipping and chapter jumps SHALL move across file boundaries.

#### Scenario: Position in the second file
- **WHEN** a book has `01 - Opening.mp3` (1:05) and `02 - Middle.mp3` (2:05) and the player seeks to 1:30
- **THEN** the second file plays from 0:25 and the position reads 1:30

#### Scenario: Skip across a boundary
- **WHEN** the same book is at 1:00 and the user skips forward
- **THEN** the second file plays from 0:05 and the position reads 1:10

#### Scenario: Playback crosses into the next file
- **WHEN** the book plays from 1:00 for 10 seconds at 1.0×
- **THEN** the position reads about 1:10 and the current chapter is `Middle`

### Requirement: Pauses the platform makes
The engine's state SHALL follow playback when it stops or resumes without a `BookPlayer` command (audio focus lost
or regained, headphones unplugged, the media notification's controls). A stop made this way SHALL save the position and set the
last-played time like `pause()`; a playback error SHALL stop playback the same way.

#### Scenario: Headphones unplugged
- **WHEN** a book plays at 0:30 and the headphones are unplugged
- **THEN** it pauses, the state is paused, and the library holds position 0:30

#### Scenario: Paused from the notification
- **WHEN** the user taps pause in the media notification
- **THEN** Player and the mini player show "Play" and the position is saved

### Requirement: Progress is saved when the app goes to the background
While a book is loaded, the engine SHALL save its position when the app goes to the background, whether or not it
is playing.

#### Scenario: Leaving the app
- **WHEN** a book plays at 0:42 and the user goes to the home screen
- **THEN** the library holds position 0:42 for it, and playback continues

### Requirement: Android playback engine
On Android, `bookplate/core/player/impl` SHALL play through Media3 ExoPlayer inside a `MediaSessionService`, so a
book keeps playing with the screen off and the app in the background, and SHALL show the default media notification
with the book's title while it plays. It SHALL:
- play the book's files as a playlist without a gap or restart between files;
- change speed without changing pitch;
- request audio focus for speech, pause when focus is lost and resume after a transient loss;
- pause when the audio output becomes noisy (headphones unplugged);
- read the files only through `lib/documentaccess`, never writing them;
- declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `WAKE_LOCK`, and the service with the
  `mediaPlayback` foreground service type, and still SHALL NOT declare `INTERNET`.

#### Scenario: Screen off
- **WHEN** a book plays and the screen is turned off for a minute
- **THEN** the position has moved on by about a minute and audio kept playing

#### Scenario: Notification
- **WHEN** a book plays
- **THEN** a media notification shows its title, and tapping it opens the app

#### Scenario: Resume after a restart
- **WHEN** a book played to 1:30 at 1.5× is paused, the app is force-stopped and started again, and the book is opened
- **THEN** Player shows it at 1:30, speed 1.5×, and playing continues from there at that speed

## MODIFIED Requirements

### Requirement: Fake player for the UI
`bookplate/core/player/test` SHALL provide `FakeBookPlayer`, implementing every requirement above with a ticking clock
instead of audio, reading and saving through `LibraryRepository`. `sharedApp` SHALL bind `EngineBookPlayer` as the
app's `BookPlayer` on Android, and SHALL bind `FakeBookPlayer` on iOS until the iOS engine exists, so the Player and the
mini player can be used for real on both.

#### Scenario: Android uses the engine
- **WHEN** the Android app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

#### Scenario: iOS uses the fake
- **WHEN** the iOS app resolves `BookPlayer`
- **THEN** it gets one shared `FakeBookPlayer` backed by the app's library

### Requirement: Player opens its book
Opening Player for a book id SHALL show that book. When the player is playing another book, Player SHALL leave it
playing and show the book from the library, paused at its saved position and speed, without loading it. Otherwise it
SHALL load the book into the player, unless it is already loaded. Opening Player SHALL NOT start or stop playback by
itself.

While Player shows a book the player does not hold, tapping "Play" SHALL load it, pausing and saving the playing book,
and play it. Seeking, skipping, jumping to a chapter and changing the speed SHALL change that book's saved position or
speed in the library, and SHALL leave the playing book playing.

#### Scenario: Opening a playing book
- **WHEN** book A is playing and Player opens for A
- **THEN** A keeps playing and Player shows "Pause"

#### Scenario: Opening another book
- **WHEN** book A is playing and Player opens for book B, saved at 0:30
- **THEN** A keeps playing, and Player shows B at 0:30 with a "Play" button

#### Scenario: Playing the other book
- **WHEN** book A is playing, Player shows book B, and the user taps "Play"
- **THEN** A is paused and saved, and B plays from its saved position

#### Scenario: Skipping in the other book
- **WHEN** book A is playing, Player shows book B at 0:30, and the user skips forward
- **THEN** Player shows B at 0:40, B's saved position is 0:40, and A keeps playing

#### Scenario: Opening a book while nothing plays
- **WHEN** book A is loaded and paused and Player opens for book B
- **THEN** B is loaded, and Player shows it paused at its saved position
