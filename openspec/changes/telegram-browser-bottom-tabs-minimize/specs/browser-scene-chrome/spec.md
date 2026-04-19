## MODIFIED Requirements

### Requirement: Browser chrome shall expose dedicated history and overflow surfaces
The system SHALL expose navigation history and more-actions as dedicated anchored surfaces instead of flattening them into the same generic toolbar layout, and active browser-family chrome MUST expose distinct collapse and close actions for dock-backed minimize behavior.

#### Scenario: Open back history
- **WHEN** the user long-presses or otherwise invokes browser back-history from the browser chrome
- **THEN** the system MUST present a history surface anchored to the back control
- **THEN** selecting a history entry MUST navigate to that entry and dismiss the history surface

#### Scenario: Open browser overflow actions
- **WHEN** the user opens the browser more-actions entry
- **THEN** the system MUST present an anchored popup from the top-right chrome area
- **THEN** the popup MUST expose browser-scoped actions such as forward, refresh, share, external open, minimize, and close based on current availability

#### Scenario: Collapse active browser-family surface from visible chrome
- **WHEN** the user taps the visible collapse action in active browser or Instant View chrome
- **THEN** the active browser-family session MUST minimize into the bottom tabs dock
- **THEN** the visible close action MUST remain available as a separate destructive action

#### Scenario: Close active browser-family surface from visible chrome
- **WHEN** the user taps the visible close action in active browser or Instant View chrome
- **THEN** the active browser-family session MUST close instead of minimizing into the dock
- **THEN** the destroyed session MUST no longer be restorable from dock UI

### Requirement: Browser host shall coordinate browser presentation modes
The system SHALL use a dedicated `BrowserHost` to coordinate an active browser-family surface, a bottom tabs dock with zero or more docked browser-family sessions, and a reader follow-up surface without routing those containers through the native page stack.

#### Scenario: Open active browser-family surface
- **WHEN** the app shell resolves a browser request to the regular browser-family path
- **THEN** `BrowserHost` MUST present that browser surface above root scenes and native pages
- **THEN** the browser request MUST NOT be represented as a `NativePageStackHost` entry

#### Scenario: Minimize active browser-family surface
- **WHEN** the active browser-family surface is minimized
- **THEN** `BrowserHost` MUST keep that browser-family session in the bottom tabs dock
- **THEN** the underlying root or native page scene MUST resume foreground ownership

#### Scenario: Restore docked browser-family surface
- **WHEN** the user reopens a docked browser-family session from the bottom tabs dock
- **THEN** `BrowserHost` MUST restore that same browser-family surface above the current scene
- **THEN** the selected docked session MUST leave the dock while it is active

#### Scenario: Open reader follow-up from browser-family surface
- **WHEN** browser content opens Instant View from an active browser-family surface
- **THEN** `BrowserHost` MUST present the reader follow-up within the same browser-family host
- **THEN** closing that reader surface MUST return control to the same source browser-family session when it still exists
