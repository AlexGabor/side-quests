## ADDED Requirements

### Requirement: Ships on Android and iOS
Bookplate SHALL ship as an Android app and an iOS app built from one shared Compose app (`bookplate/sharedApp`).
There SHALL be no desktop or web app.

#### Scenario: Launching on Android
- **WHEN** the user opens Bookplate from the Android launcher
- **THEN** the app starts and shows the Home screen

#### Scenario: Launching on iOS
- **WHEN** the user opens Bookplate from the iOS home screen
- **THEN** the app starts and shows the Home screen

### Requirement: Offline only
Bookplate SHALL work without network access. The Android app SHALL NOT declare the `INTERNET` permission.

#### Scenario: Android manifest
- **WHEN** the merged Android manifest of the debug app is inspected
- **THEN** it contains no `android.permission.INTERNET` permission

### Requirement: No external links
Bookplate SHALL NOT open from URLs or links. It SHALL declare no URL schemes, associated domains or view
intent filters, and every launch SHALL start at Home.

#### Scenario: Plain launch
- **WHEN** the app is launched by any means
- **THEN** it opens on the Home screen

### Requirement: Riso theme
The whole app SHALL be drawn inside the Riso theme, on Riso paper.

#### Scenario: Home on paper
- **WHEN** the Home screen is shown
- **THEN** it is drawn with the Riso theme's colors and paper
