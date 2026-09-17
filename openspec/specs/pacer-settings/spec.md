# pacer-settings Specification

## Purpose
Pacer's Settings screen and persisted preferences. The screen lives in `pacer/feature/settings`; persistence is `SettingsRepository` in `pacer/core/settings/api`, backed by DataStore in `pacer/core/settings/impl`.

## Requirements

### Requirement: Riso effects toggle
Settings SHALL offer a "Riso effects" On/Off control. The choice SHALL be persisted across launches and SHALL apply app-wide to the Riso theme immediately.

#### Scenario: Turning effects off
- **WHEN** the user selects Off for Riso effects
- **THEN** the Riso theme renders without effects
- **AND** effects remain off after the app is restarted

### Requirement: Platform defaults
When the user has not chosen, Riso effects SHALL default to on for Android and iOS and off for Desktop (JVM) and Web (wasmJs).

#### Scenario: First launch on web
- **WHEN** the web app is opened for the first time
- **THEN** Riso effects are off

### Requirement: No flash of wrong theme
The app SHALL NOT show navigation content until the stored settings have loaded; until then it shows a blank paper background.

#### Scenario: Cold start
- **WHEN** the app starts
- **THEN** screens appear only after the Riso effects preference is known

### Requirement: Settings screen content
The Settings screen SHALL show a "Settings" heading with a back control returning to the previous screen, and a "Made by Alex Gabor ↗" link opening `https://alexgabor.com`.

#### Scenario: Back from settings
- **WHEN** the user taps back on the Settings screen
- **THEN** the previous screen is shown
