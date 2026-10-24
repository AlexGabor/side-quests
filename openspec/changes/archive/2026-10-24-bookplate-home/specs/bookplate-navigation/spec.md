## MODIFIED Requirements

### Requirement: Opening and leaving Player
Player SHALL open on top of Home when a book is chosen from Home's library, for that book's id, and back SHALL
return to Home.

#### Scenario: Opening Player from Home
- **WHEN** the user taps a book's row on Home
- **THEN** the Player screen opens and shows the id of that book

#### Scenario: Going back
- **WHEN** the user goes back from Player, with the back button on screen or the platform's back
- **THEN** Home is shown again

## REMOVED Requirements

### Requirement: Temporary document access debug on Home
**Reason**: It was a temporary M7 check of `lib/documentaccess` until the real Home screen existed. M9's Home adds
books through the same picker and import.
**Migration**: Use Home's add button (`bookplate-home`). The debug section's saved picks (`debug-picks.preferences_pb`)
are no longer read; the file is left in app storage unused.
