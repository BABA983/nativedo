## ADDED Requirements

### Requirement: Browserable HTTP pages shall open in the regular browser container
The system SHALL treat the regular browser container as the default in-app browser surface for browserable HTTP pages.

#### Scenario: Eligible HTTP page is opened
- **WHEN** a browserable HTTP page is requested and link routing resolves to in-app browser navigation
- **THEN** the system SHALL choose the regular browser container
- **AND** the page SHALL enter as the regular browser surface instead of a separate full-route browser scene

#### Scenario: Complex HTTP browser flow stays in the same container
- **WHEN** the active browser flow enters login, challenge handling, upload, sibling-subdomain redirect, or multi-step form states and routing still resolves to app-browser navigation
- **THEN** the system SHALL keep that flow in the same regular browser container
- **AND** the browser family SHALL NOT reopen the flow in a separate full-route browser scene

### Requirement: Regular HTTP browser container shall use bottom-up full-size presentation semantics
The regular HTTP browser container SHALL open from the bottom, settle full-size, and dismiss downward so its interaction model matches a Telegram-like browser surface instead of a route push or half-sheet resting state.

#### Scenario: Open regular HTTP browser
- **WHEN** the regular HTTP browser container becomes visible
- **THEN** it SHALL animate upward from the bottom edge as an overlay scene
- **AND** it SHALL settle as a full-size browser scene with the previous scene recoverable behind it

#### Scenario: Dismiss regular HTTP browser
- **WHEN** the regular HTTP browser container is dismissed by gesture or browser close action
- **THEN** it SHALL animate downward out of view
- **AND** the previous scene SHALL resume without a blank owner interval

#### Scenario: Browser does not rest half-height
- **WHEN** the regular browser container completes open or recovers from a non-dismiss drag
- **THEN** it SHALL return to its full-size resting state
- **AND** partial-height geometry SHALL remain transient rather than a persistent browser presentation mode

### Requirement: Instant View shall use a right-to-left follow-up scene transition
Instant View or reader entry SHALL behave as a distinct follow-up scene with right-to-left transition semantics instead of reusing the ordinary browser open animation.

#### Scenario: Open Instant View from supported browser page
- **WHEN** the user enters Instant View from a supported browser page
- **THEN** the reader scene SHALL transition in from the right relative to the source browser context
- **AND** dismissing it SHALL restore the source browser scene

#### Scenario: Instant View is unavailable
- **WHEN** the current page does not satisfy local reader support rules
- **THEN** the browser SHALL keep the ordinary browser container active
- **AND** it SHALL NOT imply a reader transition is available when it is not
