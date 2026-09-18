# lib-share Specification

## Purpose
App-agnostic sharing of plain text through the platform share sheet (Android chooser, iOS activity sheet), with a no-op where there is none and a fake for tests. Implemented in `lib/share`.

## Requirements
### Requirement: Share text through the platform
`lib/share` SHALL expose an app-agnostic `Share` that hands plain text to the platform's share sheet: an `ACTION_SEND` chooser on Android, and a `UIActivityViewController` on iOS. It SHALL report through `isAvailable` whether the platform has a share sheet at all.

#### Scenario: Sharing on Android
- **WHEN** an Android app calls `text("hello")`
- **THEN** the system chooser opens offering to send the plain text `hello`

#### Scenario: Sharing on iOS
- **WHEN** an iOS app calls `text("hello")`
- **THEN** the system share sheet is presented over the app with the text `hello`
- **AND** on iPad it is anchored to the app's view rather than crashing for want of a popover source

#### Scenario: No share sheet
- **WHEN** the app runs on desktop (JVM) or the web (wasmJs)
- **THEN** `isAvailable` is false and `text` does nothing

### Requirement: Test double
`lib/share/test` SHALL provide a `FakeShare` that records every text shared, in order, and a Koin module that binds it. `FakeShare.isAvailable` SHALL be configurable.

#### Scenario: Recording shares
- **WHEN** a test shares `a` and then `b` through a `FakeShare`
- **THEN** its recorded texts are `a`, `b`
