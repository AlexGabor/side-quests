# pacer-navigation Specification

## Purpose
Top-level navigation between Pacer's screens, implemented in `pacer/sharedApp` (`RootNavigation.kt`) on top of the deep-linked back stack from `design/navigation`.

## Requirements

### Requirement: Root screens
The app SHALL have two root destinations, Pacer (the calculator, start destination) and Settings, reachable from Pacer's settings control and left with back.

#### Scenario: Opening settings
- **WHEN** the user taps the settings control on the Pacer screen
- **THEN** the Settings screen opens on top of Pacer
- **AND** back returns to Pacer with its run unchanged

### Requirement: Screen named in the address
The current screen SHALL be reflected in the app address using the same `screen` names that links accept (`pacer`, `settings`), so a visited screen and a linked one read alike.

#### Scenario: Navigating on the web
- **WHEN** the user opens Settings in the web app
- **THEN** the address names the `settings` screen

### Requirement: Screen state independent of launch arguments
The Pacer screen's saved state SHALL be keyed by the screen, not by the launch arguments it was opened with, and stored navigation keys from an older build with unknown metric or unit names SHALL degrade to defaults instead of failing.

#### Scenario: Restoring an old back stack
- **WHEN** a saved back stack contains a Pacer key with an unrecognized unit name
- **THEN** the Pacer screen opens in kilometers
