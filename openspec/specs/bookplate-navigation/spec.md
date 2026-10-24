# bookplate-navigation Specification

## Purpose
Top-level navigation between Bookplate's screens, implemented in `bookplate/sharedApp` (`RootNavigation.kt`) on top of the back stack from `design/navigation`.

## Requirements

### Requirement: Root destinations
The app SHALL have two root destinations: `Home` (the start destination) and `Player(bookId)`, which identifies
the book to play. Destinations SHALL be serializable navigation keys so the back stack survives process death and
configuration changes.

#### Scenario: Start destination
- **WHEN** the app starts without a saved back stack
- **THEN** the back stack holds only Home

#### Scenario: Restoring the back stack
- **WHEN** the app is recreated while Player is open
- **THEN** Player is shown again for the same book

### Requirement: Opening and leaving Player
Player SHALL open on top of Home, and back SHALL return to Home. Until the library exists (M9), Home SHALL offer
a placeholder control that opens Player for a sample book id.

#### Scenario: Opening Player from Home
- **WHEN** the user taps the placeholder "Open player" control on Home
- **THEN** the Player screen opens and shows the book id it was opened for

#### Scenario: Going back
- **WHEN** the user goes back from Player, with the back button on screen or the platform's back
- **THEN** Home is shown again

### Requirement: Screens crossfade by thinning ink
Moving between root screens SHALL use the design system's Riso navigation, so screens crossfade by thinning ink
instead of sliding.

#### Scenario: Opening Player
- **WHEN** Player opens on top of Home
- **THEN** Home's ink thins away while Player prints in on the same paper

### Requirement: Temporary document access debug on Home
Until the Home screen exists (M9, which removes it), the Home placeholder SHALL offer a section, clearly labelled as
temporary, with one control to pick a file and one to pick a folder. For each pick it SHALL show the pick's name and
reachability and, when reachable, the audio files found in it with their sizes and the durations read by
`lib/audiometadata`. Picks SHALL be saved and shown again after the app restarts.

#### Scenario: Picking a folder
- **WHEN** the user taps "Pick folder" and chooses a folder holding audio files
- **THEN** Home lists those audio files with sizes and durations

#### Scenario: After a restart
- **WHEN** the app is restarted after a pick
- **THEN** Home lists the same pick and its audio files again, read from the files

#### Scenario: A file goes away
- **WHEN** a picked file is deleted (or, on Android, renamed) and Home is refreshed
- **THEN** the pick is shown as missing
