## MODIFIED Requirements

### Requirement: Browser chrome shall be driven by a dedicated scene state machine
The system SHALL keep browser chrome as a dedicated scene-state container inside the browser feature while `BrowserHost` owns browser-family entry selection, sheet presentation, minimize or restore lifecycle, and reader follow-up presentation.

#### Scenario: Enter address editing
- **WHEN** the user activates the browser title region for editing
- **THEN** the browser chrome MUST enter address editing state
- **THEN** overflow and history presentation MUST be suspended until address editing is dismissed

#### Scenario: Enter browser search
- **WHEN** the user starts in-page search from the browser chrome
- **THEN** the browser chrome MUST enter search state
- **THEN** browse-only controls MUST be hidden or disabled until search is dismissed

#### Scenario: Active browser-family surface is minimized
- **WHEN** the active browser-family surface is minimized into the dock
- **THEN** the browser feature MUST preserve its scene state for later restore
- **THEN** visible browser-only chrome MUST be suspended until that same surface returns to the foreground

### Requirement: Browser chrome shall expose dedicated history and overflow surfaces
The system SHALL expose navigation history and more-actions as dedicated anchored surfaces instead of flattening them into the same generic toolbar layout, and browser-family actions MUST keep minimize and close distinct across visible chrome and overflow surfaces.

#### Scenario: Open back history
- **WHEN** the user long-presses or otherwise invokes browser back-history from the browser chrome
- **THEN** the system MUST present a history surface anchored to the back control
- **THEN** selecting a history entry MUST navigate to that entry and dismiss the history surface

#### Scenario: Open browser overflow actions
- **WHEN** the user opens the browser more-actions entry
- **THEN** the system MUST present an anchored popup from the top-right chrome area
- **THEN** the popup MUST expose browser-scoped actions such as forward, refresh, Instant View when available, search, bookmark, share, external open, minimize, and close based on current availability

#### Scenario: Invoke visible minimize action
- **WHEN** the active browser-family surface exposes a visible minimize affordance and the user taps it
- **THEN** the browser-family host MUST minimize the active session into the dock
- **THEN** the action MUST NOT destroy the underlying browser-family session

### Requirement: Browser chrome shall stay synchronized with page state
The system SHALL map page title, host or subtitle, loading progress, navigation affordances, and browser-derived action bar color into the browser chrome in real time.

#### Scenario: Page loading updates chrome progress
- **WHEN** the active browser page reports loading progress changes
- **THEN** the browser chrome MUST update its progress presentation without leaving the current scene state
- **THEN** progress visibility MUST remain owned by the active page state until loading completes

#### Scenario: Navigation availability changes
- **WHEN** the active browser page gains or loses back or forward availability
- **THEN** the browser chrome MUST update its navigation affordances and menu entries to match the current page state
- **THEN** browser actions that depend on navigation availability MUST stay synchronized with the active session

#### Scenario: Page color changes browser chrome contrast
- **WHEN** the active browser page updates the action bar color signal used by browser chrome
- **THEN** the browser chrome MUST recompute foreground contrast for text and icons
- **THEN** the active chrome surface MUST remain readable against the derived background color

### Requirement: Browser host shall coordinate browser presentation modes
The system SHALL use a dedicated `BrowserHost` to coordinate an active browser-family sheet surface, a shell-owned dock, a tabs overlay for multi-session reopen, and a reader follow-up surface without routing those containers through the native page stack.

#### Scenario: Open active browser-family surface
- **WHEN** the app shell resolves a browser request to the regular browser-family path
- **THEN** `BrowserHost` MUST present that browser surface above root scenes and native pages
- **THEN** the browser request MUST NOT be represented as a `NativePageStackHost` entry

#### Scenario: Minimize active browser-family surface
- **WHEN** the active browser-family surface is minimized
- **THEN** `BrowserHost` MUST keep a restorable docked session in the shell-owned dock
- **THEN** the underlying root or native page scene MUST resume foreground ownership

#### Scenario: Restore one docked browser-family surface directly
- **WHEN** exactly one docked browser-family session exists and the user taps the dock
- **THEN** `BrowserHost` MUST restore that same browser-family surface above the current scene
- **THEN** the restored session MUST leave the dock while it is active

#### Scenario: Restore one of multiple docked browser-family surfaces
- **WHEN** multiple docked browser-family sessions exist and the user selects one session from the tabs overlay
- **THEN** `BrowserHost` MUST restore that same browser-family surface above the current scene
- **THEN** the selected session MUST leave the dock while all non-selected docked sessions remain docked

#### Scenario: Open reader follow-up from browser-family surface
- **WHEN** browser content opens Instant View from an active browser-family surface
- **THEN** `BrowserHost` MUST present the reader follow-up within the same browser-family host
- **THEN** closing that reader surface MUST return control to the same source browser-family entry when it still exists
