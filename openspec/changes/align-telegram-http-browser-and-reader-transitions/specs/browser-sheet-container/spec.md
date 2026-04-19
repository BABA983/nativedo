## MODIFIED Requirements

### Requirement: Browser shall support a bottom-up regular browser container
The system SHALL provide a regular browser container that opens from the bottom of the screen and behaves as the primary in-app browser surface.

#### Scenario: Open regular browser
- **WHEN** browser presentation resolves to the regular browser target
- **THEN** the browser container SHALL enter from the bottom as an overlay scene
- **AND** the previous scene SHALL remain recoverable behind it

#### Scenario: Dismiss regular browser
- **WHEN** the regular browser is dismissed
- **THEN** the sheet container SHALL animate downward out of view
- **AND** the previous scene SHALL resume as the active visible owner

### Requirement: Regular browser container shall settle as a full-size scene
The regular browser container SHALL rest at full size after opening, rather than exposing a persistent partial-height browser state.

#### Scenario: Browser open settles full-size
- **WHEN** the regular browser container completes its entry animation
- **THEN** it SHALL occupy the full available browser scene height
- **AND** the browser top bar, viewport padding, and system chrome SHALL settle together

#### Scenario: Partial height is not a resting browser mode
- **WHEN** the user drags the regular browser container downward but does not dismiss it
- **THEN** the container MAY pass through transient partial geometry during the gesture
- **AND** releasing the gesture below the dismiss threshold SHALL restore the browser to its full-size resting state

### Requirement: Regular browser container shall support container-specific gestures
The regular browser container SHALL support gestures that match Telegram-like browser semantics rather than route-push semantics.

#### Scenario: Drag down to dismiss
- **WHEN** the user drags the regular browser container downward in a dismissible state
- **THEN** the sheet SHALL follow the drag gesture
- **AND** releasing beyond the configured threshold SHALL dismiss the sheet

#### Scenario: Pull to refresh while sheet is open
- **WHEN** the active page is at the top and the regular browser refresh gesture is invoked
- **THEN** the regular browser SHALL support pull-to-refresh without collapsing into a persistent partial-height browser state

### Requirement: Regular browser container shall keep browser sessions continuous
The regular browser container SHALL remain the active browser surface across redirects and browserable complex flows instead of handing those flows off to another in-app browser presentation.

#### Scenario: Normal HTTP browser open uses regular browser container
- **WHEN** an eligible ordinary HTTP browser request is opened under the current routing rules
- **THEN** the regular browser container SHALL be used as the primary browser container
- **AND** browser chrome, history, and address behavior SHALL remain available within that container

#### Scenario: Redirect or browserable complex flow stays in-place
- **WHEN** the active browsing flow redirects, enters authentication, challenge handling, upload, popup, or sibling-subdomain states and routing still resolves to app-browser navigation
- **THEN** the system SHALL keep that flow in the same regular browser container
- **AND** the browser SHALL preserve continuous history, address ownership, and return semantics
