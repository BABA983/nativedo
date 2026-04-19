## ADDED Requirements

### Requirement: Browser popup requests shall resolve through browser navigation policy
The browser SHALL intercept popup or new-window requests triggered from browser pages and resolve them through the same browser navigation policy used for ordinary link navigation.

#### Scenario: Popup request targets a browserable URL
- **WHEN** the active page triggers a popup or new-window request with a browserable URL
- **THEN** the browser SHALL resolve that URL through its navigation policy
- **AND** the request SHALL open inside the current browser flow instead of silently delegating to default WebView popup behavior

#### Scenario: Popup request targets a native route or external target
- **WHEN** the active page triggers a popup or new-window request whose URL resolves to a native route or external target
- **THEN** the browser SHALL hand off using the resolved navigation decision
- **AND** the browser SHALL NOT create an uncontrolled second in-app WebView stack

### Requirement: Browser shall handle download requests explicitly
The browser SHALL intercept download requests initiated from browser pages and route them through an explicit app-controlled handoff.

#### Scenario: Standard download request is received
- **WHEN** the active page initiates a download for a supported downloadable resource
- **THEN** the browser SHALL trigger an explicit platform download or external handoff path
- **AND** the user SHALL receive browser-scoped feedback that the download action was handled

#### Scenario: Download request cannot be handled internally
- **WHEN** the browser receives a download request that cannot be completed through the preferred in-app handoff path
- **THEN** the browser SHALL fall back to a defined external or system-browser handoff path
- **AND** the request SHALL NOT fail silently

### Requirement: Browser shall broker supported Web permission requests
The browser SHALL handle supported Web permission requests through explicit app-controlled permission flow instead of relying on undefined default WebView behavior.

#### Scenario: Site requests supported media permission
- **WHEN** the active page requests a supported media capability such as camera or microphone
- **THEN** the browser SHALL coordinate the corresponding Android runtime permission if needed
- **AND** the Web permission request SHALL be granted or denied based on the resolved app permission result

#### Scenario: Site requests unsupported or denied permission
- **WHEN** the active page requests an unsupported capability or the app permission flow is denied
- **THEN** the browser SHALL deny the Web permission request explicitly
- **AND** the browser SHALL NOT leave the request unresolved indefinitely

### Requirement: Browser shall expose recoverable main-frame page failures
The browser SHALL expose a recoverable error surface for main-frame page failures so users can retry or leave the failed page intentionally.

#### Scenario: Main-frame network or HTTP failure occurs
- **WHEN** the active top-level page load fails with a recoverable network or HTTP error
- **THEN** the browser SHALL present a browser-scoped error surface
- **AND** the error surface SHALL expose recovery actions including retry and external open

#### Scenario: New top-level navigation starts after a failure
- **WHEN** the browser begins a new top-level page load after showing a recoverable error surface
- **THEN** the browser SHALL clear the previous recoverable error state
- **AND** the new navigation SHALL proceed normally
