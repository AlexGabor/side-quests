## ADDED Requirements

### Requirement: Media panel content
The platform's media panel SHALL show the book the player has loaded, the same one Player and the mini player
follow. The panel is Android's media notification and lock screen, and iOS's Now Playing on the lock screen and in
Control Center. It SHALL show:
- the book's title;
- a subtitle: the current chapter's title when the book has chapters and that chapter has a title other than the
  book's; otherwise the book's author; otherwise nothing;
- the cover from app storage, when the book has one;
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

## MODIFIED Requirements

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
