## ADDED Requirements

### Requirement: Browser host shall coordinate browser presentation modes
The system SHALL use a dedicated `BrowserHost` to coordinate browser presentation in full-screen, sheet, and reader-style overlay modes without routing those containers through the native page stack.

#### Scenario: Open full-screen browser
- **WHEN** the app shell resolves a browser request to the full-screen browser target
- **THEN** `BrowserHost` MUST present a full-screen browser container above root scenes and native pages
- **THEN** the browser request MUST NOT be represented as a `NativePageStackHost` entry

#### Scenario: Open sheet browser
- **WHEN** the app shell resolves a browser request to the sheet browser target
- **THEN** `BrowserHost` MUST present the browser as a sheet above the current scene
- **THEN** the current root or native page scene MUST remain mounted beneath the sheet browser

#### Scenario: Open reader overlay from browser
- **WHEN** browser content opens a reader-style overlay
- **THEN** `BrowserHost` MUST present the reader overlay independently of whether the source browser is full-screen or sheet-based
- **THEN** closing the reader overlay MUST return control to the same source browser container

## MODIFIED Requirements

### Requirement: Browser chrome shall be driven by a dedicated scene state machine
The system SHALL keep browser chrome as a dedicated scene-state container inside the browser feature while `BrowserHost` owns presentation target selection and browser container lifecycle.

#### Scenario: Enter address editing
- **WHEN** the user activates the browser address or title region for editing
- **THEN** the browser chrome MUST enter address editing state
- **THEN** overflow menu presentation MUST be suspended until address editing is dismissed

#### Scenario: Enter browser search
- **WHEN** the user starts in-page or browser-level search from the browser chrome
- **THEN** the browser chrome MUST enter search state
- **THEN** browse-only controls MUST be hidden or disabled until search is dismissed

#### Scenario: Reuse browser chrome across container modes
- **WHEN** `BrowserHost` presents the browser in full-screen or sheet mode
- **THEN** the same browser chrome state machine MUST continue driving browse, search, and address-editing states
- **THEN** browser chrome behavior MUST remain consistent across browser container modes
