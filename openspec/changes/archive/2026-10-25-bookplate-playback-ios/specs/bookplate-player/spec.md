## ADDED Requirements

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

## MODIFIED Requirements

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
