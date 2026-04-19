## MODIFIED Requirements

### Requirement: Browser chrome shall be driven by a dedicated scene state machine
The system SHALL implement browser chrome as a dedicated scene-state container that manages browse, search, address editing, history popup, overflow menu states, and browser-scoped address suggestions or quick actions that belong to address editing.

#### Scenario: Enter address editing
- **WHEN** the user activates the browser address/title region for editing
- **THEN** the browser chrome MUST enter address editing state
- **THEN** overflow menu presentation MUST be suspended until address editing is dismissed

#### Scenario: Enter browser search
- **WHEN** the user starts in-page or browser-level search from the browser chrome
- **THEN** the browser chrome MUST enter search state
- **THEN** browse-only controls MUST be hidden or disabled until search is dismissed

### Requirement: Browser chrome shall expose dedicated history and overflow surfaces
The system SHALL expose navigation history and more-actions as dedicated anchored surfaces instead of flattening them into the same generic toolbar layout.

#### Scenario: Open back history
- **WHEN** the user long-presses or otherwise invokes browser back-history from the browser chrome
- **THEN** the system MUST present a history surface anchored to the back control
- **THEN** selecting a history entry MUST navigate to that entry and dismiss the history surface

#### Scenario: Open browser overflow actions
- **WHEN** the user opens the browser more-actions entry
- **THEN** the system MUST present an anchored popup from the top-right chrome area
- **THEN** the popup MUST expose browser-scoped actions such as forward, refresh, share, external open, and close based on current availability

### Requirement: Browser chrome shall stay synchronized with page state
The system SHALL map page title, host, loading progress, and navigation affordances into the browser chrome in real time.

#### Scenario: Page loading updates chrome progress
- **WHEN** the active browser page reports loading progress changes
- **THEN** the browser chrome MUST update its progress presentation without leaving the current scene state

#### Scenario: Navigation availability changes
- **WHEN** the active browser page gains or loses back or forward availability
- **THEN** the browser chrome MUST update its navigation affordances and menu entries to match the current page state

### Requirement: Browser address editing shall provide a browser address surface
The browser SHALL treat address editing as a browser address surface rather than only a transient input field.

#### Scenario: Address editing becomes active with available suggestions
- **WHEN** address editing is active and the browser has suggestion or quick-action data available
- **THEN** the browser SHALL present browser-scoped address suggestions or quick actions within the address surface
- **AND** selecting one of them SHALL dismiss incompatible chrome overlays such as overflow menu or history popup

#### Scenario: Address editing selection is applied
- **WHEN** the user selects a browser address suggestion or quick action
- **THEN** the browser SHALL apply the suggestion according to its browser-specific semantics
- **AND** the browser SHALL keep address editing behavior mutually exclusive with browse-only chrome states
