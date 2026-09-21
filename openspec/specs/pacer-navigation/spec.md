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

### Requirement: Screens crossfade by thinning ink
Moving between screens SHALL crossfade them with the design system's crossfade easing instead of sliding: the screen being left thins its ink away while the screen being opened prints in, both on the same paper. This SHALL apply to opening a screen, going back, and predictive back. During predictive back the fade SHALL follow the gesture's progress. The screen being left SHALL NOT respond to taps.

#### Scenario: Opening settings
- **WHEN** the user taps the settings control on the Pacer screen
- **THEN** Pacer's ink thins, slowing around half, while Settings starts printing in over it, and Pacer is gone once the crossfade settles

#### Scenario: Going back
- **WHEN** the user goes back from Settings
- **THEN** Settings thins away and Pacer prints in the same way

#### Scenario: Predictive back half-way
- **WHEN** the user is part-way through a predictive back gesture from Settings
- **THEN** both screens show at the ink matching the gesture's progress
- **AND** cancelling the gesture brings Settings back to full ink

#### Scenario: Tapping the screen being left
- **WHEN** the user taps a control on the screen that is fading out
- **THEN** nothing is activated
