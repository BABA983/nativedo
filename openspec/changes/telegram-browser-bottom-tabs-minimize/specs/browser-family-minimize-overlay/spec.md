## MODIFIED Requirements

### Requirement: Browser-family host shall support minimize and restore for active browser-family surfaces
The system SHALL allow active browser-family surfaces to minimize into a bottom tabs dock and later restore the same live surface from that dock.

#### Scenario: Minimize regular browser
- **WHEN** the user invokes minimize while the regular browser is active
- **THEN** the browser-family host SHALL animate that browser surface into a docked bottom-tabs entry
- **AND** the underlying app scene SHALL resume as the visible foreground owner

#### Scenario: Minimize Instant View
- **WHEN** the user invokes minimize while Instant View is active
- **THEN** the browser-family host SHALL minimize the active reader surface into the same bottom tabs dock
- **AND** the minimized reader session SHALL preserve a restoration path back to that same reader surface

#### Scenario: Minimize while dock already contains sessions
- **WHEN** the user minimizes an active browser-family surface while the bottom tabs dock already contains other docked sessions
- **THEN** the browser-family host SHALL keep the existing docked sessions
- **AND** the newly minimized session SHALL become the latest docked entry

#### Scenario: Restore docked browser-family session
- **WHEN** the user reopens a docked browser-family session from the collapsed dock or dock overview
- **THEN** the browser-family host SHALL restore that same browser or reader surface to the foreground
- **AND** the restore transition SHALL reuse the existing browser-family session instead of creating a fresh open request

### Requirement: Minimized browser-family entry shall remain identifiable and resumable
The system SHALL keep each docked browser-family session identifiable enough for the user to understand what will be restored and SHALL preserve the corresponding live session state while that session remains docked.

#### Scenario: Minimized browser entry keeps session continuity
- **WHEN** a regular browser session is restored after being docked
- **THEN** the browser SHALL resume the same page, history continuity, and browser-scoped chrome state
- **AND** it SHALL NOT reopen as a fresh browser session for the same URL

#### Scenario: Minimized Instant View keeps reading continuity
- **WHEN** an Instant View session is restored after being docked
- **THEN** the reader SHALL resume the same document and reading position
- **AND** the source browser context SHALL remain recoverable when the reader later closes

#### Scenario: Most recently minimized session becomes the dock front
- **WHEN** multiple browser-family sessions are docked
- **THEN** the most recently minimized session SHALL become the foremost dock presentation
- **AND** older docked sessions SHALL remain distinguishable and restorable behind it

### Requirement: Browser-family close shall remain distinct from minimize
The system SHALL destroy browser-family state only when the user invokes true close instead of minimize, whether the session is active or already docked.

#### Scenario: Close active browser-family surface
- **WHEN** the user invokes close while a browser or reader surface is active
- **THEN** the browser-family host SHALL dismiss that active surface as closed
- **AND** the host SHALL release the corresponding browser-family session instead of storing it in the dock

#### Scenario: Close docked browser-family session
- **WHEN** the user closes a docked browser-family session from the collapsed dock or dock overview
- **THEN** the browser-family host SHALL remove that session from the dock
- **AND** restoring that closed session SHALL no longer be possible

#### Scenario: Close one docked session keeps other docked sessions available
- **WHEN** one docked browser-family session is closed while other docked sessions remain
- **THEN** the remaining docked sessions SHALL stay available in the dock
- **AND** the dock presentation SHALL update without closing the entire dock
