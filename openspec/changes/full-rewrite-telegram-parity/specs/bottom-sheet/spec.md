## ADDED Requirements

### Requirement: Custom bottom sheet dialog
The system SHALL provide a `BottomSheet` class (extending Dialog or as a standalone ViewGroup) that slides up from the bottom of the screen with a drag handle, shadow overlay, and gesture-based dismissal.

#### Scenario: Show bottom sheet
- **WHEN** `bottomSheet.show()` is called
- **THEN** the sheet animates up from the bottom edge with a dim overlay behind it

#### Scenario: Dismiss by swipe down
- **WHEN** the user swipes the sheet downward past 40% of its height
- **THEN** the sheet animates off-screen and is dismissed

#### Scenario: Dismiss by tapping overlay
- **WHEN** the user taps the dim overlay area
- **THEN** the sheet is dismissed

### Requirement: Nested scroll support
The system SHALL handle nested scrollable content (e.g., a RecyclerView inside the sheet). Dragging the sheet down SHALL only trigger sheet dismissal when the inner content is scrolled to the top.

#### Scenario: Nested scroll at top
- **WHEN** the inner RecyclerView is scrolled to the top and the user swipes down
- **THEN** the sheet begins to drag downward toward dismissal

#### Scenario: Nested scroll not at top
- **WHEN** the inner RecyclerView is not at the top and the user swipes down
- **THEN** the inner RecyclerView scrolls up (sheet stays in place)

### Requirement: Theme-aware styling
The bottom sheet background SHALL use `NdTheme.getColor(key_dialogBackground)` and the dim overlay SHALL use `NdTheme.getColor(key_dialogBackgroundGray)`.

#### Scenario: Dark theme bottom sheet
- **WHEN** the dark theme is active and a BottomSheet is shown
- **THEN** the sheet background is dark and the overlay is a dark semi-transparent color
