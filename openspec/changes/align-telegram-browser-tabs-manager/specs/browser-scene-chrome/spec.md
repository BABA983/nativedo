## MODIFIED Requirements

### Requirement: Browser chrome shall expose dedicated history and overflow surfaces
The system SHALL expose navigation history and more-actions as dedicated anchored surfaces instead of flattening them into the same generic toolbar layout, and browser-family actions MUST keep collapse and close distinct across visible chrome and overflow surfaces.

#### Scenario: Open back history
- **WHEN** the user long-presses or otherwise invokes browser back-history from the browser chrome
- **THEN** the system MUST present a history surface anchored to the back control
- **THEN** selecting a history entry MUST navigate to that entry and dismiss the history surface

#### Scenario: Open browser overflow actions
- **WHEN** the user opens the browser more-actions entry
- **THEN** the system MUST present an anchored popup from the top-right chrome area
- **THEN** the popup MUST expose browser-scoped actions such as forward, refresh, share, external open, collapse, and close based on current availability

#### Scenario: Invoke visible collapse action
- **WHEN** the active browser-family surface exposes a visible collapse affordance and the user taps it
- **THEN** the browser-family host MUST minimize the active session into the shell-owned dock
- **THEN** the action MUST NOT destroy the underlying browser-family session

### Requirement: Browser host shall coordinate browser presentation modes
The system SHALL use a dedicated `BrowserHost` to coordinate an active browser-family surface, a shell-owned minimized dock, a tabs manager overlay for multi-session reopen, and a reader follow-up surface without routing those containers through the native page stack.

#### Scenario: Open active browser-family surface
- **WHEN** the app shell resolves a browser request to the regular browser-family path
- **THEN** `BrowserHost` MUST present that browser surface above root scenes and native pages
- **THEN** the browser request MUST NOT be represented as a `NativePageStackHost` entry

#### Scenario: Minimize active browser-family surface
- **WHEN** the active browser-family surface is minimized
- **THEN** `BrowserHost` MUST keep a restorable docked session in the shell-owned bottom dock
- **THEN** the underlying root or native page scene MUST resume foreground ownership

#### Scenario: Restore one docked browser-family surface directly
- **WHEN** exactly one docked browser-family session exists and the user taps the collapsed dock
- **THEN** `BrowserHost` MUST restore that same browser-family surface above the current scene
- **THEN** the restored session MUST leave the dock while it is active

#### Scenario: Restore one of multiple docked browser-family surfaces
- **WHEN** multiple docked browser-family sessions exist and the user selects one session from the tabs manager
- **THEN** `BrowserHost` MUST restore that same browser-family surface above the current scene
- **THEN** the selected session MUST leave the dock while all non-selected docked sessions remain docked

#### Scenario: Open reader follow-up from browser-family surface
- **WHEN** browser content opens Instant View from an active browser-family surface
- **THEN** `BrowserHost` MUST present the reader follow-up within the same browser-family host
- **THEN** closing that reader surface MUST return control to the same source browser-family entry when it still exists
