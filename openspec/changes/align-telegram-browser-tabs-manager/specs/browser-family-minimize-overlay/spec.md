## MODIFIED Requirements

### Requirement: Browser-family host shall support minimize and restore for active browser-family surfaces
The system SHALL allow the active browser-family surface to minimize into a shell-owned bottom dock and later restore the same live surface either directly from that dock or through the tabs manager when multiple docked sessions exist.

#### Scenario: Minimize regular browser
- **WHEN** the user invokes minimize while the regular browser is active
- **THEN** the browser-family host SHALL animate that browser surface into a dock entry in the shell-owned bottom dock
- **AND** the underlying app scene SHALL resume as the visible foreground owner

#### Scenario: Minimize Instant View
- **WHEN** the user invokes minimize while Instant View is active
- **THEN** the browser-family host SHALL minimize the active reader surface into the same shell-owned bottom dock
- **AND** the docked entry SHALL preserve a restoration path back to that reader surface

#### Scenario: Restore single docked browser-family entry
- **WHEN** exactly one docked browser-family session exists and the user reopens it from the collapsed dock
- **THEN** the browser-family host SHALL restore that same browser or reader surface to the foreground
- **AND** the restore transition SHALL reuse the existing browser-family session instead of creating a fresh open request

#### Scenario: Restore one of multiple docked browser-family entries
- **WHEN** multiple docked browser-family sessions exist and the user selects one session from the tabs manager
- **THEN** the browser-family host SHALL restore that same browser or reader surface to the foreground
- **AND** only the selected session SHALL leave the dock while the others remain docked

### Requirement: Minimized browser-family entry shall remain identifiable and resumable
The system SHALL keep each docked browser-family session identifiable across the collapsed dock and tabs manager and SHALL preserve the corresponding live session state while that session remains docked.

#### Scenario: Docked browser entry keeps session continuity
- **WHEN** a regular browser entry is restored after being minimized into the dock
- **THEN** the browser SHALL resume the same page, history continuity, and browser-scoped chrome state
- **AND** it SHALL NOT reopen as a fresh browser session for the same URL

#### Scenario: Docked Instant View entry keeps reading continuity
- **WHEN** an Instant View entry is restored after being minimized into the dock
- **THEN** the reader SHALL resume the same document and reading position
- **AND** the source browser context SHALL remain recoverable when the reader later closes

### Requirement: Browser-family close shall remain distinct from minimize
The system SHALL destroy browser-family state only when the user invokes true close from the active surface, collapsed dock, or tabs manager rather than minimize.

#### Scenario: Close active browser-family surface
- **WHEN** the user invokes close while a browser or reader surface is active
- **THEN** the browser-family host SHALL dismiss that active surface as closed
- **AND** the host SHALL release the corresponding browser-family session instead of storing it in the dock

#### Scenario: Close docked browser-family entry
- **WHEN** the user closes a docked browser-family entry from the collapsed dock or tabs manager
- **THEN** the browser-family host SHALL remove that entry from the dock
- **AND** restoring that closed entry SHALL no longer be possible

#### Scenario: Close all docked browser-family entries
- **WHEN** the user invokes `Close All Tabs` while docked browser-family entries exist
- **THEN** the browser-family host SHALL release every docked browser-family session
- **AND** the shell-owned dock SHALL disappear after the final docked entry is removed
