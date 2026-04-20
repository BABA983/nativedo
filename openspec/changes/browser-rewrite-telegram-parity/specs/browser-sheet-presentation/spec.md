## ADDED Requirements

### Requirement: Browser sheet shall open as a Telegram-style foreground surface
The system SHALL present the regular in-app browser inside a dedicated sheet surface that enters from the bottom edge and becomes the active foreground browser-family container above the current app scene.

#### Scenario: Open browser from app content
- **WHEN** the app opens a regular browser request
- **THEN** the browser SHALL animate in from the bottom edge inside the browser-family host
- **AND** the browser chrome and loaded page SHALL become the active foreground surface without routing through the native page stack

#### Scenario: Browser sheet becomes attached to fullscreen
- **WHEN** the active browser sheet is expanded or settles to its fullscreen anchor
- **THEN** the browser SHALL attach to the top edge as the foreground browser surface
- **AND** sheet-specific presentation details such as corner radius or handle emphasis SHALL reduce to match the attached state

### Requirement: Browser sheet shall support anchored drag presentation
The system SHALL let the active browser sheet move between its presentation anchors through direct manipulation instead of relying only on discrete open and close commands.

#### Scenario: User drags browser sheet upward
- **WHEN** the active browser sheet is not yet attached to fullscreen and the user drags it upward
- **THEN** the browser SHALL track the gesture in real time
- **AND** releasing the gesture SHALL settle the sheet to the nearest valid presentation anchor

#### Scenario: User drags browser sheet downward while content is at top
- **WHEN** the active browser sheet is allowed to dismiss and the content is already at its top boundary
- **THEN** downward drag SHALL drive the sheet presentation directly
- **AND** releasing the gesture SHALL either return the sheet to its prior anchor or continue toward dismissal based on distance or velocity

### Requirement: Browser sheet dismissal shall preserve scene ownership rules
The system SHALL dismiss the active browser sheet only through explicit close flow or a qualifying dismiss gesture, and dismissal SHALL return foreground ownership to the underlying app scene.

#### Scenario: User closes active browser sheet
- **WHEN** the user invokes close while the browser sheet is active
- **THEN** the browser-family host SHALL dismiss that browser sheet
- **AND** the previously covered app scene SHALL resume foreground ownership

#### Scenario: User cancels an incomplete dismiss gesture
- **WHEN** a browser sheet dismiss gesture ends without meeting the configured settle threshold
- **THEN** the browser SHALL animate back to its prior presentation anchor
- **AND** the current browser session SHALL remain active and unchanged
