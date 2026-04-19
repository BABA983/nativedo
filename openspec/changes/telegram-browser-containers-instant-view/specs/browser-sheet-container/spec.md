## ADDED Requirements

### Requirement: Browser shall support a sheet presentation container
The system SHALL provide a browser sheet container that opens from the bottom of the screen instead of requiring every lightweight browser interaction to occupy a full-screen route.

#### Scenario: Open sheet browser
- **WHEN** browser presentation resolves to the sheet target
- **THEN** the browser container SHALL enter from the bottom as an overlay scene
- **AND** the previous scene SHALL remain recoverable behind it

#### Scenario: Dismiss sheet browser
- **WHEN** the sheet browser is dismissed
- **THEN** the sheet container SHALL animate downward out of view
- **AND** the previous scene SHALL resume as the active visible owner

### Requirement: Sheet browser shall support half and fullscreen transitions
The browser sheet SHALL support a controlled transition between partial-height and fullscreen presentation.

#### Scenario: Enter fullscreen from sheet
- **WHEN** the user or browser state requests fullscreen presentation inside the browser sheet
- **THEN** the sheet SHALL animate from partial-height to fullscreen
- **AND** the browser top bar, viewport padding, and system chrome SHALL transition together

#### Scenario: Exit fullscreen within sheet
- **WHEN** the browser sheet exits fullscreen presentation
- **THEN** the sheet SHALL animate back to its partial-height state
- **AND** content interaction SHALL remain stable after the transition

### Requirement: Sheet browser shall support container-specific gestures
The browser sheet SHALL support gestures that match sheet semantics rather than route semantics.

#### Scenario: Drag down to dismiss
- **WHEN** the user drags the browser sheet downward in a dismissible state
- **THEN** the sheet SHALL follow the drag gesture
- **AND** releasing beyond the configured threshold SHALL dismiss the sheet

#### Scenario: Pull to refresh while sheet is open
- **WHEN** the active page is at the top and the sheet browser refresh gesture is invoked
- **THEN** the sheet browser SHALL support pull-to-refresh without collapsing into dismiss by default
