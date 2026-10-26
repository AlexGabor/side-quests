# bookplate-player Specification

## Purpose
Bookplate's playback contract and Player UI: the `BookPlayer` state and commands (book-wide positions, speed per book, progress saved to the library) in `bookplate/core/player/api`, the playback engine in `core/player/impl` (one timeline across a book's files, saving progress, Media3 on Android), the fake player in `core/player/test` bound where no engine exists yet, playback refusing or stopping when a book's files are unavailable, and the Player screen with its position ruler, controls, speed and chapters sheets, its unavailable state and relinking a book from it in `bookplate/feature/player`.
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
boundary between two files SHALL map to the start of the later one. Files whose duration is zero (unreadable), and
files a partial relink left out, SHALL be left out of playback, keeping the other files' places. A position in the
span of a left-out file SHALL map to the start of the next played file, or to the end of the last one when none
follows. Seeking, skipping and chapter jumps SHALL move across file boundaries.

#### Scenario: Position in the second file
- **WHEN** a book has `01 - Opening.mp3` (1:05) and `02 - Middle.mp3` (2:05) and the player seeks to 1:30
- **THEN** the second file plays from 0:25 and the position reads 1:30

#### Scenario: Skip across a boundary
- **WHEN** the same book is at 1:00 and the user skips forward
- **THEN** the second file plays from 0:05 and the position reads 1:10

#### Scenario: Playback crosses into the next file
- **WHEN** the book plays from 1:00 for 10 seconds at 1.0×
- **THEN** the position reads about 1:10 and the current chapter is `Middle`

#### Scenario: Left-out file is skipped
- **WHEN** a book of three 1:00 files has its second file left out and the player seeks to 1:30
- **THEN** the third file plays from its start and the position reads 2:00

### Requirement: Playback checks the book
Starting playback SHALL first check the loaded book's availability through the library. When it is not available,
the player SHALL stay paused, keep its position and play nothing. When it is available and the library's files for
it differ from the loaded ones (after a relink), the player SHALL load the new files at the current position before
playing. While a book plays, the player SHALL check it again every 30 seconds, and when it is no longer available
SHALL pause and save as `pause()` does, even when the platform had already buffered what follows. When playback
stops on an error, the player SHALL stop as before and then check the book. Either way, a file that disappears during
playback leaves the player paused at the position it reached, with the book unavailable. This holds for the engine
and for the fake player alike, and for commands from the media panel.

#### Scenario: Play a missing book
- **WHEN** a loaded book's folder was moved and the user taps "Play"
- **THEN** the player stays paused at its position, the output never plays, and the book is missing

#### Scenario: File disappears during playback
- **WHEN** a book plays and its next file is deleted, so playback fails on reaching it
- **THEN** the player is paused at the position reached, that position is saved, and the book is missing

#### Scenario: Buffered when the file disappears
- **WHEN** a book plays and one of its files is deleted, but the platform already buffered it, so no error comes
- **THEN** within 30 seconds the player pauses, saves the position, and the book is missing

#### Scenario: Play after a relink
- **WHEN** a loaded book is relinked to a new folder and the user taps "Play"
- **THEN** the output is loaded with the files in the new folder at the same position and plays

### Requirement: Pauses the platform makes
The engine's state SHALL follow playback when it stops, resumes or moves without a `BookPlayer` command (audio focus
lost or regained on Android, an audio session interruption beginning or ending on iOS, headphones unplugged, the
media panel's controls and headset buttons). A stop made this way SHALL save the position and set the last-played
time like `pause()`; a playback error SHALL stop playback the same way. A move made this way (the panel's seek bar
or skip buttons) SHALL be saved like `seekTo()`.

#### Scenario: Headphones unplugged
- **WHEN** a book plays at 0:30 and the headphones are unplugged
- **THEN** it pauses, the state is paused, and the library holds position 0:30

#### Scenario: Paused from the notification
- **WHEN** the user taps pause in the media notification
- **THEN** Player and the mini player show "Play" and the position is saved

#### Scenario: Seeked from the notification
- **WHEN** a book is paused at 0:30 and the user taps "Forward 10 seconds" in the media notification
- **THEN** Player shows 0:40 and the library holds position 0:40

### Requirement: Progress is saved when the app goes to the background
While a book is loaded, the engine SHALL save its position when the app goes to the background, whether or not it
is playing.

#### Scenario: Leaving the app
- **WHEN** a book plays at 0:42 and the user goes to the home screen
- **THEN** the library holds position 0:42 for it, and playback continues

### Requirement: Android playback engine
On Android, `bookplate/core/player/impl` SHALL play through Media3 ExoPlayer inside a `MediaSessionService`, so a
book keeps playing with the screen off and the app in the background, with the media panel described in "Android
media panel" while it plays. It SHALL:
- play the book's files as one item, one after another without a gap or restart between files;
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
instead of audio, reading and saving through `LibraryRepository` and checking availability like the engine, with a
way for a test to make playback fail, for unit tests and previews. `sharedApp` SHALL bind `EngineBookPlayer` as the
app's `BookPlayer` on Android and on iOS; neither app SHALL include `core/player/test`.

#### Scenario: Android uses the engine
- **WHEN** the Android app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

#### Scenario: iOS uses the engine
- **WHEN** the iOS app resolves `BookPlayer`
- **THEN** it gets one shared `EngineBookPlayer` backed by the app's library

#### Scenario: Fake refuses an unavailable book
- **WHEN** the fake player plays a book that its availability reports missing
- **THEN** it stays paused

### Requirement: Player screen layout
`bookplate/feature/player` SHALL show, top to bottom:
- a top bar with the icon button "Back" at the start, and at the end a "Change inks" swatch button followed by the
  "Chapters" and "Speed" icon buttons;
- the book's title and its author ("Unknown author" when it has none);
- the cover as a plain image (not printed through Riso inks), or the same Riso placeholder as Home's rows (the
  title's first letter and a border in the book's accent, over its accent variant at 40 % opacity), square, as
  large as the width allows and shrinking so that every other element fits on screen;
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

### Requirement: Unavailable book in Player
Opening Player SHALL check its book's availability. While the book is unavailable, Player SHALL show its title,
author and cover, and in place of the chapter title, position, track and controls:
- "Files missing" with "Bookplate can't find this book's files. If you moved or renamed them, relink the book to
  where they are now.", or "Access lost" with "Bookplate is no longer allowed to open this book's files. Relink the
  book by picking them again.";
- a "Relink" button and a "Check again" button, which checks the book again.

The top bar SHALL keep "Back" and hide "Chapters" and "Speed". Nothing SHALL play from Player while the book is
unavailable. When the book becomes available, Player SHALL show it as usual.

#### Scenario: Missing book
- **WHEN** Player opens for a book whose folder was moved
- **THEN** it shows "Files missing", "Relink" and "Check again", and no "Play" button

#### Scenario: Access lost
- **WHEN** Player opens for a book whose grant was revoked
- **THEN** it shows "Access lost" and "Relink"

#### Scenario: Files came back
- **WHEN** Player shows a missing book, its files are put back, and the user taps "Check again"
- **THEN** Player shows the book with its controls, at its saved position

#### Scenario: Stopped during playback
- **WHEN** Player shows a playing book and playback stops because a file disappeared
- **THEN** Player shows "Files missing" with "Relink"

### Requirement: Relinking from Player
Tapping "Relink" SHALL open the platform picker for a folder when the book was added from a folder, and for an
audio file when it was added from a single file. A cancelled pick SHALL change nothing. While a relink runs, Player
SHALL show "Looking for the book's files in “<pick>”…" and SHALL ignore further picks. Player SHALL then show the
result until the user closes it or starts another relink:
- relinked: "Relinked “<title>”", or, with files left out, "Relinked “<title>”. <n> missing file(s) will be
  skipped.", and Player shows the book at the position it had, with its speed and chapters;
- needs confirmation: "Found <found> of <total> files in “<pick>”. Not found: <paths>. Parts of the book without
  their files will be skipped.", with "Relink anyway", which relinks again confirming those files, and "Cancel",
  which changes nothing;
- unchanged: "“<title>” is already linked to “<pick>”";
- no match: "None of this book's files are in “<pick>”";
- wrong kind: "“<title>” was added from a folder. Pick its folder." or "“<title>” was added from a single file.
  Pick the file.";
- another book's source: "“<pick>” is already the book “<other title>”";
- failed: "Couldn't open “<pick>”." followed by why, as Home words import failures.

#### Scenario: Relink a moved folder
- **WHEN** Player shows `Sample Book` missing at 0:42, the user taps "Relink" and picks the moved folder
- **THEN** Player shows "Relinked “Sample Book”", then the book at 0:42 with "Play"

#### Scenario: Folder or file picker
- **WHEN** the user taps "Relink" for a single-file book
- **THEN** the file picker opens for audio files

#### Scenario: Confirm a partial match
- **WHEN** a relink finds 1 of 2 files and the user taps "Relink anyway"
- **THEN** Player shows "Relinked “Sample Book”. 1 missing file will be skipped." and the book's controls

#### Scenario: Cancel a partial match
- **WHEN** a relink finds 1 of 2 files and the user taps "Cancel"
- **THEN** the book is still missing and Player shows "Files missing"

#### Scenario: Wrong folder
- **WHEN** the user picks a folder holding none of the book's files
- **THEN** Player shows "None of this book's files are in “<pick>”" and still shows the book missing

#### Scenario: Another book's folder
- **WHEN** the user picks the folder of the book `Other`
- **THEN** Player shows "“<pick>” is already the book “Other”"

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
While the position `Track` is dragged, the position label SHALL keep showing the playback position, and, when the
track comes to rest, Player SHALL seek to the position under the guideline. While nobody drags it, the track SHALL follow the playback position:
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
section's title SHALL head its chapters. The current chapter SHALL be printed in the book's accent ink and every other
one in the content color. Tapping a chapter SHALL jump to its start and close the sheet. The list SHALL open scrolled
so the current chapter is visible.

#### Scenario: Sections
- **WHEN** the chapters sheet opens for `Sample Book`
- **THEN** it lists "Opening" with "1:05", then the heading "Disc 2", then "Middle" with "2:05"

### Requirement: Player state survives recreation
Which sheet is open SHALL survive configuration changes and process death. The book shown SHALL be the one in the
navigation key.

#### Scenario: Rotation with the speed sheet open
- **WHEN** the speed sheet is open and the activity is recreated
- **THEN** Player shows the same book with the speed sheet open

### Requirement: Media panel content
The platform's media panel SHALL show the book the player has loaded, the same one Player and the mini player
follow. The panel is Android's media notification and lock screen, and iOS's Now Playing on the lock screen and in
Control Center. It SHALL show:
- the book's title;
- a subtitle: the current chapter's title when the book has chapters and that chapter has a title other than the
  book's; otherwise the book's author; otherwise nothing;
- the cover from app storage, when the book has one; otherwise artwork printed in the book's inks, as described in
  "Ink artwork in the media panel";
- a seek bar over the whole book: the book's total duration and the position in the whole book, the same numbers
  Player shows;
- whether it is playing, and on iOS the speed it plays at.

When the current chapter changes, by playing on, seeking or a chapter jump, the subtitle SHALL change with it. When
no book is loaded the panel SHALL show nothing.

#### Scenario: Chapter as subtitle
- **WHEN** `Sample Book` by `Sample Author` plays at 1:30, in chapter `Middle`
- **THEN** the panel shows "Sample Book", "Middle", its cover, and a seek bar at 1:30 of 3:10

#### Scenario: Author when there are no chapters
- **WHEN** a book by `Sample Author` with no chapters plays
- **THEN** the panel's subtitle is "Sample Author"

#### Scenario: Chapter titled like the book
- **WHEN** `Single` by `Sample Author` has one chapter titled `Single`
- **THEN** the panel's subtitle is "Sample Author"

#### Scenario: Following chapters
- **WHEN** `Sample Book` plays from 1:00 past 1:05
- **THEN** the panel's subtitle changes from "Opening" to "Middle"

### Requirement: Android media panel
On Android the media session SHALL present the loaded book as one item spanning the whole book, so the media
notification and the lock screen show the content above with a seek bar over the whole book, and SHALL offer:
- play/pause;
- "Rewind 10 seconds" and "Forward 10 seconds" in place of previous and next, moving 10 seconds within the whole book
  and across files, clamped like `skipBack()` and `skipForward()`;
- seeking by dragging the seek bar, to that position in the whole book.

It SHALL NOT offer previous or next track. Tapping the notification SHALL open the app on Player for the loaded
book. Swiping the notification away while paused SHALL remove it and take the playback service out of the
foreground; playing again from the app SHALL bring it back.

#### Scenario: Skipping from the notification
- **WHEN** a book plays at 1:00 and the user taps "Forward 10 seconds" in the notification
- **THEN** it plays from 1:10, in the second file, and Player shows 1:10

#### Scenario: Seeking from the lock screen
- **WHEN** the book is paused and the user drags the lock screen's seek bar to 2:00
- **THEN** Player shows 2:00, still paused, and the library holds position 2:00

#### Scenario: Tapping the notification
- **WHEN** `Sample Book` plays, Home is shown, and the user taps the notification
- **THEN** the app shows Player for `Sample Book`, and back returns to Home

#### Scenario: Dismissing while paused
- **WHEN** the book is paused and the user swipes the notification away
- **THEN** the notification is gone, the service is no longer in the foreground, and the book stays paused at its
  position

### Requirement: iOS Now Playing
On iOS the player SHALL fill `MPNowPlayingInfoCenter` with the content above: title, subtitle (as the artist),
artwork, the book's duration, the elapsed time in the book, and the playback rate (the speed while playing, zero while
paused). It SHALL update the elapsed time and the rate when playback starts or pauses, after a seek, skip or chapter
jump, and when the speed changes, so the system's extrapolated time keeps agreeing with Player.

It SHALL handle `MPRemoteCommandCenter`'s play, pause, togglePlayPause, skipBackward and skipForward (with a
preferred interval of 10 seconds) and changePlaybackPosition (scrubbing) through the same `BookPlayer` commands the
app uses, and SHALL disable nextTrack and previousTrack.

#### Scenario: Lock screen
- **WHEN** `Sample Book` plays at 1:30 at 1.5× and the device is locked
- **THEN** the lock screen shows "Sample Book", "Middle", the cover, 1:30 of 3:10 advancing at 1.5×, and skip 10 s
  back and forward buttons

#### Scenario: Scrubbing from Control Center
- **WHEN** the user drags Control Center's position to 2:00
- **THEN** the player seeks to 2:00 and Player shows 2:00

#### Scenario: Speed change
- **WHEN** the speed is set to 2.0× while playing
- **THEN** Now Playing's rate is 2.0 and its elapsed time is the position at that moment

### Requirement: Headset and media buttons
Bluetooth and wired headset buttons SHALL control the player on both platforms: play, pause and play/pause toggle
it. On Android, next and fast forward SHALL skip forward 10 seconds and previous and rewind SHALL skip back 10
seconds. On iOS, buttons reach the app as the remote commands above, so play/pause works and next/previous do
nothing, since next and previous track are disabled.

#### Scenario: Play/pause button
- **WHEN** a book plays and the headset's play/pause button is pressed
- **THEN** it pauses, Player shows "Play" and the position is saved

#### Scenario: Next on Android
- **WHEN** a book plays at 1:00 on Android and the headset's next button is pressed
- **THEN** it plays from 1:10

### Requirement: Player prints in the book's inks
Player and its sheets SHALL print with the book's inks in place of the theme's accents. That covers the play/pause
`Button`'s mix, pressed icon buttons, the unavailable title and the current chapter. When the book's inks change,
Player SHALL show them at once.

#### Scenario: Play button
- **WHEN** Player opens for a book with Teal/Yellow
- **THEN** the play button's fill mixes yellow and teal

### Requirement: Ink swatch button
Player's top bar SHALL show a `SwatchButton`, labelled "Change inks", to the left of "Chapters", 24 dp square,
printed in a candidate pair from the ink picker. Tapping it SHALL set the book's inks to that candidate, and the button
SHALL then show a new candidate from the picker. The candidate SHALL survive configuration changes. The button SHALL
only show when "Chapters" does.

#### Scenario: Shuffle
- **WHEN** the button shows Green/Orange and the user taps it
- **THEN** the book's inks become Green/Orange, Player prints in them at once, and the button shows the picker's next
  pair

#### Scenario: Unavailable book
- **WHEN** Player shows an unavailable book
- **THEN** no swatch button is shown

### Requirement: Ink artwork in the media panel
For a book without a cover, the media panel SHALL show generated square artwork on Android and on iOS. It SHALL show
the title's first letter, upper-cased, in the book's accent, over a paper background washed with the book's accent
variant at 40 % opacity, inside a border in the book's accent, matching the library placeholder. Its colors SHALL come
from the Riso inks the book's ink names stand for, falling back to the theme's accents for a name Riso doesn't know.
The system takes the panel's colors from it. A book with a cover SHALL keep showing its cover. The artwork SHALL be
made once per title letter and ink pair, not on every panel update.

#### Scenario: Coverless book
- **WHEN** `Single`, with no cover and inks Teal/Yellow, plays
- **THEN** the panel shows a yellow-washed square with a teal border and a teal "S", and its colors follow it

#### Scenario: Book with a cover
- **WHEN** `Sample Book`, which has a cover, plays
- **THEN** the panel shows its cover

### Requirement: Loaded book follows its inks
`PlayerBook` SHALL carry the book's inks. While a book is loaded, the player SHALL follow changes to that book's inks
in the library and publish them in its state, without reloading the book or changing its position, playing state or
speed. The media panel SHALL then show the new inks.

#### Scenario: Swatch while playing
- **WHEN** `Single` plays and the user taps Player's swatch, giving it Green/Orange
- **THEN** the player's state holds Green/Orange, playback goes on uninterrupted, and the panel's artwork is
  orange-washed with a green border

