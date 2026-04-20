## ADDED Requirements

### Requirement: Browser runtime shall preserve tab session continuity
The system SHALL capture enough browser runtime state to restore a minimized or temporarily released tab without reopening it as a fresh session.

#### Scenario: Minimize active browser tab
- **WHEN** the active browser tab is minimized into the dock
- **THEN** the browser runtime SHALL preserve the tab identity, current URL, navigation continuity, and browser-owned restore state
- **AND** the dock preview SHALL remain associated with that same restorable tab session

#### Scenario: Restore minimized browser tab
- **WHEN** the user restores a previously minimized browser tab
- **THEN** the browser SHALL resume the same session instead of creating a new tab for the same URL
- **AND** page-specific chrome state such as title, history availability, and progress ownership SHALL recover for the restored tab

### Requirement: Browser runtime shall keep browser-scoped secondary windows inside the browser family
The system SHALL handle browser-originated secondary window requests inside the in-app browser family instead of silently losing them or forcing an unexpected external handoff.

#### Scenario: Web content requests a secondary window
- **WHEN** the active page triggers a browser secondary-window flow such as `target=\"_blank\"`
- **THEN** the browser-family host SHALL open that request inside the in-app browser family
- **AND** the user SHALL remain within browser-owned navigation and session handling

#### Scenario: Browser-scoped callback requires user mediation
- **WHEN** the active page triggers browser-owned callbacks such as file chooser or permission request
- **THEN** the browser SHALL surface that callback through the active browser session
- **AND** completing or cancelling the callback SHALL return control to the same session

### Requirement: Browser runtime shall differentiate page-back and surface-close gestures
The system SHALL interpret browser back gestures according to the active session state so page navigation back and container dismissal remain distinct behaviors.

#### Scenario: Edge back gesture while page can navigate backward
- **WHEN** the active browser session supports page-level back navigation and the user performs the configured back gesture
- **THEN** the browser SHALL navigate the page backward within the same session
- **AND** the active browser surface SHALL remain open

#### Scenario: Edge back gesture while page cannot navigate backward
- **WHEN** the active browser session cannot navigate backward and the user performs the configured back gesture
- **THEN** the browser-family host SHALL treat that gesture as a surface-level close or dismiss action
- **AND** the underlying app scene SHALL resume foreground ownership if the surface closes
