# bookplate-player Specification

## Purpose
Bookplate's playback contract and Player UI: the `BookPlayer` state and commands (book-wide positions, speed per book, progress saved to the library) in `bookplate/core/player/api`, the playback engine in `core/player/impl` (one timeline across a book's files, saving progress, Media3 on Android), the fake player in `core/player/test` bound where no engine exists yet, and the Player screen with its position ruler, controls, and speed and chapters sheets in `bookplate/feature/player`.
## Requirements
### Requirement: Playback state
`bookplate/core/player/api` SHALL expose a `BookPlayer` with a `PlaybackState` flow holding:
- the loaded book (id, title, author, cover path, total duration, chapters with sections), or none;
- the position in the whole book, from zero to the total duration;
- whether it is playing;
- the speed;
- the current chapter and its index among the playable chapters (chapters that are not sections), or none when the
  book has no chapters.

Positions and durations SHALL be book-wide: a book of several files is one timeline, and the engine maps a position to
a file and an offset in it. The current chapter SHALL be the last playable chapter whose start is at or before the
position.

#### Scenario: Nothing loaded
- **WHEN** no book has been loaded
- **THEN** the state has no book, position zero, is paused and has no chapter

#### Scenario: Current chapter
- **WHEN** a book has chapters starting at 0:00, 1:05 and 3:10 and the position is 2:00
- **THEN** the current chapter is the one starting at 1:05, with index 1

#### Scenario: Chapter inside a section
- **WHEN** a book has chapter `Opening` (0:00–1:05) and section `Disc 2` holding chapter `Middle` (1:05–3:10), and the
  position is 1:30
- **THEN** the current chapter is `Middle`, with index 1

### Requirement: Loading a book
`load(bookId)` SHALL load that book from the library at its saved position and speed, paused. Loading the book that
is already loaded SHALL change nothing. Loading another book while one plays SHALL pause it and save its position
first. Loading an id that is not in the library SHALL leave no book loaded.

#### Scenario: Resume where it was
- **WHEN** a book saved at 0:42 with speed 1.5× is loaded
- **THEN** the state has that book at 0:42, speed 1.5×, paused

#### Scenario: Same book again
- **WHEN** the loaded book is playing at 1:00 and it is loaded again
- **THEN** it keeps playing from 1:00

### Requirement: Play and pause
`play()` SHALL start advancing the position at the book's speed: one second of playback moves the position by the
speed in seconds. `pause()` SHALL stop it. Reaching the end of the book SHALL stop at the total duration, paused.
Playing with nothing loaded SHALL do nothing. Playing a book at its end SHALL start it from the beginning.

#### Scenario: Playing at speed
- **WHEN** a book at 0:10 plays for 10 seconds at 1.5×
- **THEN** its position is 0:25

#### Scenario: End of the book
- **WHEN** a 3:10 book plays past its end
- **THEN** its position is 3:10 and it is paused

### Requirement: Seeking and skipping
`seekTo(position)` SHALL move to that position, clamped to the book. `skipBack()` and `skipForward()` SHALL move
10 seconds back or forward, clamped to the start and the end. `jumpToChapter(index)` SHALL move to the start of that
playable chapter; an index out of range SHALL do nothing. None of them SHALL change whether it is playing, except that
reaching the end pauses.

#### Scenario: Skip near the start
- **WHEN** the position is 0:04 and the user skips back
- **THEN** the position is 0:00

#### Scenario: Skip forward
- **WHEN** the position is 1:00 and the user skips forward
- **THEN** the position is 1:10

#### Scenario: Jump to a chapter
- **WHEN** the user jumps to the chapter with index 1, starting at 1:05
- **THEN** the position is 1:05 and the current chapter index is 1

### Requirement: Speed
`setSpeed(speed)` SHALL set the speed of the loaded book, rounded to the nearest 0.1× and clamped to 0.3×–3×, and
SHALL save it as that book's speed. Each book SHALL keep its own speed.

#### Scenario: Snapping and clamping
- **WHEN** the speed is set to 1.26, then to 5
- **THEN** it is 1.3×, then 3.0×

#### Scenario: Per book
- **WHEN** book A is set to 2.0×, then book B (saved at 1.0×) is loaded
- **THEN** the speed is 1.0×, and loading A again gives 2.0×

### Requirement: Progress is saved to the library
While a book plays the player SHALL save its position to the library at least every 5 seconds of playback, and SHALL
save it whenever it pauses, seeks, skips, jumps to a chapter or loads another book. Starting and pausing playback SHALL
set the book's last-played time to now, so Home's order and the mini player follow it.

#### Scenario: Pause saves
- **WHEN** a book plays from 0:00 for 3 seconds and is paused
- **THEN** the library holds position 0:03 for it and its last-played time is now

#### Scenario: Played book is last played
- **WHEN** a book that was never played starts playing
- **THEN** the library's last-played book is that book

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
or regained on Android, an audio session interruption beginning or ending on iOS, headphones unplugged, the media
notification's controls). A stop made this way SHALL save the position and set the last-played time like `pause()`;
a playback error SHALL stop playback the same way.

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

### Requirement: iOS playback engine
On iOS, `bookplate/core/player/impl` SHALL play through AVFoundation with the same `EngineBookPlayer` as Android, so
the timeline, chapters, saving, restoring and end-of-book rules are the same on both. It SHALL:
- play the book's files one after another, moving into the next file without a restart or a seek back;
- change speed without changing pitch;
- use the `.playback` audio session category, and declare the `audio` background mode, so a book keeps playing with
  the app in the background and with the device locked;
- pause when an interruption (a call, Siri, another app's audio) begins, and resume when it ends if the system says
  playback should resume;
- pause when the route loses its output device (headphones unplugged);
- read the files only through `lib/documentaccess`, holding their security-scoped access while they are loaded or
  playing and stopping it when they are released, and never write, move or delete them.

#### Scenario: Background and locked
- **WHEN** a book plays and the user goes to the home screen, or locks the device, for a minute
- **THEN** the position has moved on by about a minute and audio kept playing

#### Scenario: Crossing into the next file
- **WHEN** a book's first file is 1:05 long and it plays from 1:00
- **THEN** after a few seconds the second file plays and the position reads past 1:05 without jumping back

#### Scenario: Resume after a restart
- **WHEN** a book played to 1:30 at 1.5× is paused, the app is terminated and started again, and the book is opened
- **THEN** Player shows it at 1:30, speed 1.5×, and playing continues from there at that speed

#### Scenario: A call comes in
- **WHEN** a book plays and an interruption begins, then ends with the system asking to resume
- **THEN** the state shows paused with the position saved during the interruption, and playing again afterwards

### Requirement: Fake player for the UI
`bookplate/core/player/test` SHALL provide `FakeBookPlayer`, implementing every requirement above with a ticking clock
instead of audio, reading and saving through `LibraryRepository`, for unit tests and previews. `sharedApp` SHALL bind
`EngineBookPlayer` as the app's `BookPlayer` on Android and on iOS; neither app SHALL include
`core/player/test`.

#### Scenario: Android uses the engine
- **WHEN** the Android app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

#### Scenario: iOS uses the engine
- **WHEN** the iOS app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

### Requirement: Player screen layout
`bookplate/feature/player` SHALL show, top to bottom:
- a top bar with icon buttons "Back" at the start, and "Chapters" and "Speed" at the end;
- the book's title and its author ("Unknown author" when it has none);
- the cover as a plain image (not printed through Riso inks), or a Riso placeholder with the title's first letter,
  square, as large as the width allows and shrinking so that every other element fits on screen;
- the current chapter's title, when the current chapter has one;
- the position on the left and the total duration on the right, as `m:ss` under an hour and `h:mm:ss` from an hour;
- a `Track` showing the position, with minutes as its items and 10-second subdivisions, ending exactly at the total
  duration (not at the next whole minute);
- a row with "Rewind 10 seconds" and "Forward 10 seconds" icon buttons around a Riso `Button` showing the play or
  pause icon, labelled "Play" or "Pause".
The cover SHALL fade with the page like Home's covers. The content SHALL stay within the safe drawing area.

#### Scenario: Content
- **WHEN** Player opens for `Sample Book` by `Sample Author`, 3:10 long, at 1:30
- **THEN** it shows "Sample Book", "Sample Author", "Middle", "1:30" and "3:10", and a "Play" button

#### Scenario: Small screen
- **WHEN** Player is shown on a small phone
- **THEN** the cover is smaller than on a large phone and the controls row is fully on screen

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

### Requirement: Player controls
Tapping "Play" SHALL play and the button SHALL become "Pause"; tapping "Pause" SHALL pause. "Rewind 10 seconds" and
"Forward 10 seconds" SHALL skip back and forward 10 seconds. "Back" and the platform's back SHALL return to the previous
screen without stopping playback.

#### Scenario: Play and pause
- **WHEN** the user taps "Play", waits two seconds and taps "Pause"
- **THEN** the position has moved on and stays put after the pause

#### Scenario: Leaving keeps playing
- **WHEN** a book is playing and the user goes back to Home
- **THEN** it keeps playing

### Requirement: Scrubbing the position track
Dragging the position `Track` SHALL show the position under the guideline in the position label while dragging, and,
when the track comes to rest, SHALL seek to it. While nobody drags it, the track SHALL follow the playback position:
while playing it SHALL glide continuously at the book's speed rather than step from one 10-second line to the next,
and while paused it SHALL rest at the position.

#### Scenario: Track ends at the book's end
- **WHEN** Player shows a book 3:10 long and the user drags the track all the way to its end
- **THEN** the ruler ends at 3:10, closed by a long line, does not scroll past it, and playback moves to 3:10

#### Scenario: Gliding while playing
- **WHEN** a book plays from 0:00 at 1.0× and its position reads 0:15
- **THEN** the guideline sits halfway between the 0:10 and 0:20 ruler lines

#### Scenario: Scrub
- **WHEN** the user drags the track until 2:00 is under the guideline and lets go
- **THEN** the position label reads "2:00" and playback continues from 2:00

### Requirement: Speed sheet
"Speed" SHALL open a Riso `BottomSheet` with the heading "Speed <x.y>×" and a `Track` from 0.3× to 3×, with whole speeds
as items and tenths as subdivisions. The track SHALL open at the book's speed. Moving it SHALL set the speed, snapped to
0.1×; it SHALL NOT go below 0.3×. The speed is saved per book, so reopening the sheet, or reopening the book after a
restart, SHALL show it again.

#### Scenario: Change speed
- **WHEN** the user opens the speed sheet at 1.0× and drags the track to 1.5×
- **THEN** the heading reads "Speed 1.5×" and the book plays at 1.5×

#### Scenario: Speed kept
- **WHEN** the user sets 1.5×, closes the sheet and opens it again
- **THEN** the heading reads "Speed 1.5×"

### Requirement: Chapters sheet
"Chapters" SHALL open a Riso `BottomSheet` with the heading "Chapters" and a scrollable list of the book's playable
chapters, each with its title (or "Chapter <n>" when it has none) and its duration. When the book has sections, each
section's title SHALL head its chapters. The current chapter SHALL be printed in the accent color and every other one
in the content color. Tapping a chapter SHALL jump to its start and close the sheet. The list SHALL open scrolled so the
current chapter is visible.

#### Scenario: Sections
- **WHEN** the chapters sheet opens for `Sample Book`
- **THEN** it lists "Opening" with "1:05", then the heading "Disc 2", then "Middle" with "2:05"

#### Scenario: Jump
- **WHEN** the user taps "Middle" in the sheet
- **THEN** the sheet closes, the position is 1:05 and the chapter title reads "Middle"

### Requirement: Player state survives recreation
Which sheet is open SHALL survive configuration changes and process death. The book shown SHALL be the one in the
navigation key.

#### Scenario: Rotation with the speed sheet open
- **WHEN** the speed sheet is open and the activity is recreated
- **THEN** Player shows the same book with the speed sheet open

