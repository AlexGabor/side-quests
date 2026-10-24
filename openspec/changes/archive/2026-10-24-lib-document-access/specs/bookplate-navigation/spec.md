## ADDED Requirements

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
