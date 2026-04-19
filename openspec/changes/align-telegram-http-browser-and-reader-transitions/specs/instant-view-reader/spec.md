## MODIFIED Requirements

### Requirement: Supported pages shall be able to open in Instant View
The browser SHALL allow supported pages to open in a reader-style Instant View scene.

#### Scenario: Instant View entry becomes available
- **WHEN** the current page has been loaded and satisfies the local Instant View support rules
- **THEN** the browser SHALL expose an Instant View entry for that page

#### Scenario: Instant View entry is unavailable
- **WHEN** the current page does not satisfy the local Instant View support rules
- **THEN** the browser SHALL keep normal browser actions available
- **AND** it SHALL NOT expose Instant View as a false-positive action

### Requirement: Instant View shall use a dedicated reader scene
Instant View SHALL render in a dedicated reader-style scene rather than as a browser subpanel.

#### Scenario: Open Instant View from browser
- **WHEN** the user opens Instant View from a supported browser page
- **THEN** the system SHALL present a reader scene with its own visible chrome owner
- **AND** the reader scene SHALL preserve a return path to the source browser page

#### Scenario: Close Instant View
- **WHEN** the user dismisses the reader scene
- **THEN** the system SHALL return to the originating browser or previous scene
- **AND** the source page context SHALL remain recoverable

### Requirement: Instant View shall optimize for reading interactions
The reader scene SHALL prioritize reading-oriented interactions over browser-oriented chrome density.

#### Scenario: Reader scene is active
- **WHEN** Instant View is active
- **THEN** the scene SHALL use reader-appropriate chrome and content layout
- **AND** browser-only interactions such as address editing SHALL NOT appear as if the user were still in normal browse mode

### Requirement: Instant View transition shall remain distinct from regular browser open
Instant View SHALL use its own follow-up scene transition semantics rather than reusing the ordinary browser container open animation.

#### Scenario: Browser enters Instant View
- **WHEN** the user opens Instant View from an already visible browser page
- **THEN** the reader scene SHALL transition in as a right-to-left follow-up scene
- **AND** the source browser scene SHALL remain the restoration target when the reader closes

#### Scenario: Reader closes back to browser
- **WHEN** the user exits the reader scene and the source browser scene is still available
- **THEN** the reader SHALL dismiss back toward the source browser context
- **AND** the ordinary browser container SHALL regain ownership without requiring a fresh browser open animation
