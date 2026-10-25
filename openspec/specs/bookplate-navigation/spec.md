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
Player SHALL open on top of Home for a book's id when a book is chosen from Home's library or when the mini player is
tapped, and back SHALL return to Home.

#### Scenario: Opening Player from Home
- **WHEN** the user taps a book's row on Home
- **THEN** the Player screen opens and shows that book's title

#### Scenario: Opening Player from the mini player
- **WHEN** the user taps the mini player
- **THEN** the Player screen opens for the last-played book

#### Scenario: Going back
- **WHEN** the user goes back from Player, with the back button on screen or the platform's back
- **THEN** Home is shown again

### Requirement: Screens crossfade by thinning ink
Moving between root screens SHALL use the design system's Riso navigation, so screens crossfade by thinning ink
instead of sliding.

#### Scenario: Opening Player
- **WHEN** Player opens on top of Home
- **THEN** Home's ink thins away while Player prints in on the same paper

