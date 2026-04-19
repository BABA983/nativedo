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

### Requirement: Browser chrome shall adapt to the simplified browser-family container semantics
The browser chrome SHALL remain coherent when the regular HTTP browser uses one bottom-up full-size browser container and Instant View behaves as a distinct follow-up scene.

#### Scenario: Address, history, bookmarks, and settings are shown inside regular browser container
- **WHEN** the regular HTTP browser is active inside the regular browser container
- **THEN** the browser chrome SHALL continue to expose address editing, history, bookmarks, settings, and overflow behavior without assuming full-route-only or auxiliary-sheet-only ownership
- **AND** temporary chrome states SHALL remain mutually exclusive inside that container

#### Scenario: Reader transition follows browser scene
- **WHEN** the user enters or exits Instant View from the browser
- **THEN** the browser chrome SHALL suspend browser-only states while the reader scene is active
- **AND** the source browser chrome SHALL restore coherently when the reader closes

### Requirement: Browser host shall coordinate simplified browser-family presentation modes
The system SHALL use a dedicated `BrowserHost` to coordinate the single regular browser container and the reader-style follow-up scene without preserving a separate full-route browser scene inside the browser family.

#### Scenario: Open regular browser container
- **WHEN** the app shell resolves a browser request to the regular browser target
- **THEN** `BrowserHost` MUST present the regular browser container as the active in-app browser surface above root scenes and native pages
- **AND** the browser request MUST NOT be remapped to a separate full-route browser scene solely because the flow becomes auth-sensitive, challenge-heavy, upload-capable, or hosted on a sibling subdomain

#### Scenario: Open reader follow-up from browser
- **WHEN** browser content opens a reader-style Instant View follow-up scene
- **THEN** `BrowserHost` MUST present the reader scene relative to the active source browser container
- **AND** closing the reader scene MUST return control to that same source browser container
