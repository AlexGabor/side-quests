## ADDED Requirements

### Requirement: Search field is a single-line Riso text input
`SearchField` SHALL be a controlled text input: it SHALL show the `value` it is given and report edits through `onValueChange`. It SHALL keep its text on one line. It SHALL use the theme's body typography and content color, a border of the theme's line width in the theme's standard shape, and the accent color for the cursor.

#### Scenario: Typing
- **WHEN** the user types "dune" into an empty `SearchField`
- **THEN** `onValueChange` receives the new text and the field shows whatever `value` the caller passes back

#### Scenario: Newline is not inserted
- **WHEN** the user presses Enter in the field
- **THEN** no line break is added to the value

#### Scenario: Pasted line breaks
- **WHEN** text containing line breaks is pasted or typed in, e.g. "dune\nmessiah"
- **THEN** each line break is reported as a single space ("dune messiah")

### Requirement: Search field shows a search icon and a placeholder
`SearchField` SHALL show the `Search` icon at its start edge. When the value is empty it SHALL show the caller's placeholder text, if any, in the content color at 70 % opacity. The placeholder SHALL disappear as soon as there is text.

#### Scenario: Empty field
- **WHEN** `SearchField` is shown with an empty value and placeholder "Search"
- **THEN** the search icon and the text "Search" are visible

#### Scenario: Field with text
- **WHEN** `SearchField` is shown with value "dune"
- **THEN** "dune" is visible and the placeholder is not

### Requirement: Search field has a clear action
When the value is not empty, `SearchField` SHALL show a `Close` icon button at its end edge. Activating it SHALL call `onValueChange("")`. When the value is empty the clear action SHALL NOT be shown. The button SHALL carry the content description the caller passes, defaulting to "Clear".

#### Scenario: Clearing
- **WHEN** the field holds "dune" and the user taps the clear button
- **THEN** `onValueChange` is called with an empty string

#### Scenario: Nothing to clear
- **WHEN** the field is empty
- **THEN** no clear button is present

### Requirement: Search field offers a search keyboard action
`SearchField` SHALL ask for the keyboard's search action. When the user triggers it, the field SHALL call `onSearch` with the current value, if the caller passed one.

#### Scenario: Keyboard search
- **WHEN** the field holds "dune" and the user presses the keyboard's search action
- **THEN** `onSearch` is called with "dune"

### Requirement: Focus is shown like a selected card
While `SearchField` has focus, its border SHALL be drawn the way a selected `Card`'s is: twice the theme's line width, printed as the accent at full strength over the content color at half strength (`RisoMix(content to 0.5, accent to 1)`). Otherwise it SHALL be the theme's line width in the content color. The change SHALL animate with the same low-stiffness spring as `Card`.

#### Scenario: Focused
- **WHEN** the user taps into the field
- **THEN** its border thickens to twice the line width and changes from content color to the card's selected mix of accent over half-strength content
