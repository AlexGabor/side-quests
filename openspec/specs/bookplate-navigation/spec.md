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
