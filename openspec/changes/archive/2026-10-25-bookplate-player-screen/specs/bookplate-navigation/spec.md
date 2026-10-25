## MODIFIED Requirements

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
