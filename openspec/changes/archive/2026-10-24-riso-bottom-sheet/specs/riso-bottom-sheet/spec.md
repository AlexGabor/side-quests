## ADDED Requirements

### Requirement: Bottom sheet is a modal sheet shown while it is composed
`BottomSheet` SHALL be a modal sheet anchored to the bottom edge of the window. It SHALL be open for as long as the caller keeps it in composition: when it enters composition it SHALL animate up from the bottom edge, and the caller removes it in `onDismissRequest`. It SHALL be app agnostic and SHALL NOT expose any Material type in its public API.

#### Scenario: Opening
- **WHEN** a screen starts composing `BottomSheet { … }`
- **THEN** the sheet slides up from the bottom edge and its content becomes visible

#### Scenario: Closing by the caller
- **WHEN** the caller stops composing the sheet after `onDismissRequest`
- **THEN** the sheet is gone and the page behind it is printed at full ink again

### Requirement: Bottom sheet sizes to its content
The sheet SHALL be as tall as its content, plus its drag handle and the bottom safe-drawing inset. It SHALL never come closer to the top of the window than the top safe-drawing inset plus `BottomSheetDefaults.TopGap` (48 dp), so some of the page always shows above it to be tapped. It SHALL open straight to its full height, with no half-open stop. Its width SHALL be the window width, capped at the theme's content max width and centred. Content taller than the space it has SHALL scroll inside the sheet when the caller makes it scrollable (for example with a lazy list).

#### Scenario: Short content
- **WHEN** the sheet holds a single slider row
- **THEN** the sheet is only as tall as that row, the handle and the bottom inset

#### Scenario: Long content
- **WHEN** the sheet holds a lazy list of 60 rows
- **THEN** the sheet reaches up to 48 dp below the top safe-drawing inset and the list scrolls inside it

### Requirement: Dragging the sheet down dismisses it
The sheet SHALL follow a vertical drag on it or on its drag handle. When the user releases it far enough down or flings it downwards, it SHALL animate off the bottom edge and then call `onDismissRequest`. Otherwise it SHALL settle back to fully open and SHALL NOT call `onDismissRequest`. A scrollable list inside the sheet SHALL scroll first; dragging down while the list is at its top SHALL drag the sheet.

#### Scenario: Drag past the threshold
- **WHEN** the user drags the open sheet down by more than half its height and releases
- **THEN** the sheet animates off screen and `onDismissRequest` is called once

#### Scenario: Short drag
- **WHEN** the user drags the open sheet down by a few dp and releases without a fling
- **THEN** the sheet settles back to fully open and `onDismissRequest` is not called

#### Scenario: Fling
- **WHEN** the user flings the sheet downwards
- **THEN** the sheet animates off screen and `onDismissRequest` is called once

#### Scenario: List at its top
- **WHEN** the sheet's list is scrolled to the top and the user drags down on the list
- **THEN** the sheet moves down with the finger

### Requirement: Back dismisses the sheet
A back action SHALL animate the sheet off screen and then call `onDismissRequest`. On Android the system back gesture SHALL be predictive: during the gesture the sheet SHALL scale with its progress, a cancelled gesture SHALL return the sheet to its resting size without dismissing it, and a committed gesture SHALL dismiss it.

#### Scenario: Back button
- **WHEN** the sheet is open on Android and the user presses back
- **THEN** the sheet animates off screen and `onDismissRequest` is called

#### Scenario: Cancelled predictive back
- **WHEN** the user starts a back swipe on Android and lets go without committing it
- **THEN** the sheet returns to its resting size and stays open

### Requirement: Tapping outside dismisses the sheet and nothing behind it takes input
While the sheet is open, a tap anywhere outside the sheet SHALL animate the sheet off screen and then call `onDismissRequest`. No pointer input SHALL reach content behind the sheet while it is open, including the tap that dismisses it.

#### Scenario: Tap outside
- **WHEN** the sheet is open and the user taps above it
- **THEN** the sheet animates off screen and `onDismissRequest` is called

#### Scenario: Button behind the sheet
- **WHEN** the sheet is open and the user taps where a button of the page sits outside the sheet
- **THEN** the button's action does not run and the sheet is dismissed

### Requirement: Content can dismiss its own sheet
The sheet's content SHALL be able to ask for the sheet to close through its scope's `dismiss()`. The sheet SHALL then animate off screen and call `onDismissRequest` once.

#### Scenario: Picking an item
- **WHEN** a row in the sheet calls `dismiss()` on tap
- **THEN** the sheet animates off screen and then `onDismissRequest` is called once

### Requirement: The page behind the sheet is printed lighter instead of covered by a scrim
The sheet SHALL NOT draw a scrim. While it is open, everything the press prints on the page behind it SHALL be printed at a lower ink strength, the backdrop fade (`BottomSheetDefaults.BackdropFade`, 0.35), the same way `risoFade` thins a screen in a crossfade: smaller dots of ink at full strength, with the stock showing between them. The paper itself SHALL stay as it is. The fade SHALL follow the share of the sheet that is on screen: full ink when it is off screen, the backdrop fade when it is fully open, and in proportion in between, including while it is dragged. Content behind the sheet that the press does not print SHALL keep its strength unless it opts in with `risoFadeAsAlpha`. When effects are disabled, the page SHALL instead be covered by the theme's paper color at `1 − fade` opacity. With no sheet open the page SHALL be drawn exactly as without the backdrop.

#### Scenario: Open sheet
- **WHEN** a sheet is fully open over an inked page
- **THEN** the page's ink is printed at 0.35 of its strength and the paper shows between the dots, with no dark overlay

#### Scenario: Dragging
- **WHEN** the user drags the sheet halfway down
- **THEN** the page behind is printed more strongly than when the sheet was fully open

#### Scenario: Effects disabled
- **WHEN** `RisoTheme(effectsEnabled = false)` and a sheet is fully open
- **THEN** a pixel of the page that was pure content color reads as the content color with paper laid over it at 0.65 opacity

#### Scenario: No sheet
- **WHEN** no sheet is open
- **THEN** the page is drawn exactly as it would be without the backdrop

### Requirement: Bottom sheet looks like a sheet of Riso paper
The sheet SHALL be a sheet of paper of its own (`risoPaper`) with the theme's standard shape on its top corners and square bottom corners. Its sides and top edge SHALL be outlined in the content ink at the theme's line width, printed in register so the line sits evenly on both sides. It SHALL show a drag handle centred at the top: a short rounded bar in the content ink at 50 % strength, with the content description "Drag handle". The paper SHALL reach the bottom edge of the window, and the content SHALL be padded above the bottom safe-drawing inset. Content inside SHALL print on the sheet's paper.

#### Scenario: Sheet edge
- **WHEN** a sheet is open over a page
- **THEN** its top edge and sides are drawn as an inked line and its top corners are rounded

#### Scenario: Handle
- **WHEN** a sheet is open
- **THEN** a node with the content description "Drag handle" is shown at its top
