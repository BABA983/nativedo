## ADDED Requirements

### Requirement: App browser route enters with its own top bar
The in-app browser SHALL render its own top bar as part of the browser scene rather than relying on the root chrome slot. The browser top bar MUST remain visible while the page is entering and loading.

#### Scenario: Link opened from a topic
- **WHEN** a user taps a link in a topic and navigation resolves to `AppBrowserRoute`
- **THEN** the browser route SHALL render a top bar on entry
- **AND** the top bar SHALL remain visible while the initial page load is in progress

#### Scenario: Page title is not ready yet
- **WHEN** the browser route is visible and the page title has not yet been loaded from WebView state
- **THEN** the browser top bar SHALL show URL-based or host-based identity in the title container
- **AND** the title container SHALL update when page metadata becomes available

### Requirement: Browser top bar scenes are mutually exclusive
The browser top bar SHALL model browse, search, address editing, history popup, overflow menu, and subpanel as mutually exclusive scenes.

#### Scenario: Address editing suppresses overflow menu
- **WHEN** address editing becomes active
- **THEN** the overflow menu SHALL not remain expanded
- **AND** the top bar SHALL switch to the address-editing presentation

#### Scenario: Search suppresses history popup
- **WHEN** browser search becomes active
- **THEN** the history popup SHALL be dismissed
- **AND** the top bar SHALL switch to the search presentation

#### Scenario: Overflow menu is anchored to the trailing action
- **WHEN** the browser overflow menu is opened from the top bar
- **THEN** the menu SHALL be anchored to the trailing top bar action
- **AND** the browser SHALL remain in a single overflow-menu scene until the menu is dismissed or another scene takes ownership

### Requirement: Browser back and close semantics follow scene state
The browser top bar SHALL derive back-versus-close behavior from the active browser scene and WebView navigation state.

#### Scenario: No browser back stack
- **WHEN** the browser is in browse scene and the WebView cannot navigate back
- **THEN** the leading control SHALL represent closing the browser route

#### Scenario: Temporary browser scene is active
- **WHEN** the browser is in search, address editing, history popup, overflow menu, or subpanel scene
- **THEN** the leading control SHALL exit the active browser scene before dismissing the route

#### Scenario: Web history is available
- **WHEN** the browser is in browse scene and the WebView can navigate back
- **THEN** the leading control SHALL navigate browser history instead of immediately closing the route
