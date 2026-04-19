## MODIFIED Requirements

### Requirement: Browser chrome shall be driven by a dedicated scene state machine
The system SHALL keep browser chrome as a dedicated scene-state container inside the browser feature while `BrowserHost` owns browser-family entry selection, minimize or restore lifecycle, and reader follow-up presentation.

#### Scenario: Enter address editing
- **WHEN** the user activates the browser address or title region for editing
- **THEN** the browser chrome MUST enter address editing state
- **THEN** overflow menu presentation MUST be suspended until address editing is dismissed

#### Scenario: Enter browser search
- **WHEN** the user starts in-page or browser-level search from the browser chrome
- **THEN** the browser chrome MUST enter search state
- **THEN** browse-only controls MUST be hidden or disabled until search is dismissed

#### Scenario: Minimized browser-family entry suspends visible chrome
- **WHEN** the active browser-family entry is minimized
- **THEN** the browser feature MUST preserve its scene state for later restore
- **THEN** visible browser-only chrome MUST be suspended until that entry returns to the foreground

### Requirement: Browser chrome shall expose dedicated history and overflow surfaces
The system SHALL expose navigation history and more-actions as dedicated anchored surfaces instead of flattening them into the same generic toolbar layout, and browser-family actions MUST keep minimize and close distinct.

#### Scenario: Open back history
- **WHEN** the user long-presses or otherwise invokes browser back-history from the browser chrome
- **THEN** the system MUST present a history surface anchored to the back control
- **THEN** selecting a history entry MUST navigate to that entry and dismiss the history surface

#### Scenario: Open browser overflow actions
- **WHEN** the user opens the browser more-actions entry
- **THEN** the system MUST present an anchored popup from the top-right chrome area
- **THEN** the popup MUST expose browser-scoped actions such as forward, refresh, share, external open, minimize, and close based on current availability

### Requirement: Browser host shall coordinate browser presentation modes
The system SHALL use a dedicated `BrowserHost` to coordinate an active browser-family surface, an optional minimized bottom-strip entry, and a reader follow-up surface without routing those containers through the native page stack.

#### Scenario: Open active browser-family surface
- **WHEN** the app shell resolves a browser request to the regular browser-family path
- **THEN** `BrowserHost` MUST present that browser surface above root scenes and native pages
- **THEN** the browser request MUST NOT be represented as a `NativePageStackHost` entry

#### Scenario: Minimize active browser-family surface
- **WHEN** the active browser-family surface is minimized
- **THEN** `BrowserHost` MUST keep a restorable minimized entry in the bottom strip
- **THEN** the underlying root or native page scene MUST resume foreground ownership

#### Scenario: Restore minimized browser-family surface
- **WHEN** the user reopens a minimized browser-family entry from the bottom strip
- **THEN** `BrowserHost` MUST restore that same browser-family surface above the current scene
- **THEN** the minimized entry MUST leave the bottom strip while it is active

#### Scenario: Open reader follow-up from browser-family surface
- **WHEN** browser content opens Instant View from an active browser-family surface
- **THEN** `BrowserHost` MUST present the reader follow-up within the same browser-family host
- **THEN** closing that reader surface MUST return control to the same source browser-family entry when it still exists
