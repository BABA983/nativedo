## MODIFIED Requirements

### Requirement: Browser-family host shall present the regular browser as a full-screen bottom-up surface
The system SHALL present the regular in-app browser from the bottom edge as a Telegram-style sheet surface that can move between browser-owned presentation anchors instead of remaining a route-only full-screen surface.

#### Scenario: Open regular browser from app content
- **WHEN** the app opens a regular browser-family request
- **THEN** the browser-family host SHALL animate the browser in from the bottom edge
- **AND** the browser SHALL become the active foreground browser-family surface inside the host

#### Scenario: Regular browser changes sheet anchor while active
- **WHEN** the active regular browser surface is dragged or otherwise settles between its supported anchors
- **THEN** the browser-family host SHALL preserve the same live browser session during that presentation change
- **AND** the surface SHALL remain part of the same active browser-family entry rather than reopening as a new route

### Requirement: Browser-family host shall support minimize and restore for active browser-family surfaces
The system SHALL allow the active browser-family surface to minimize into a docked browser tabs entry and later restore the same live surface or restorable session.

#### Scenario: Minimize regular browser
- **WHEN** the user invokes minimize while the regular browser is active
- **THEN** the browser-family host SHALL animate that browser surface into the dock
- **AND** the underlying app scene SHALL resume as the visible foreground owner

#### Scenario: Minimize Instant View
- **WHEN** the user invokes minimize while Instant View is active
- **THEN** the browser-family host SHALL minimize the active reader surface into the same dock system
- **AND** the minimized entry SHALL preserve a restoration path back to that reader surface

#### Scenario: Restore minimized browser-family entry
- **WHEN** the user reopens a minimized browser-family entry from the dock or tabs overlay
- **THEN** the browser-family host SHALL restore that same browser or reader surface to the foreground
- **AND** the restore transition SHALL reuse the existing browser-family entry instead of creating a fresh open request

### Requirement: Minimized browser-family entry shall remain identifiable and resumable
The system SHALL keep each minimized browser-family entry identifiable enough for the user to understand what will be restored and SHALL preserve the corresponding browser-family session continuity while the entry remains docked.

#### Scenario: Minimized browser entry keeps session continuity
- **WHEN** a regular browser entry is restored after being minimized
- **THEN** the browser SHALL resume the same page, history continuity, and browser-scoped chrome state
- **AND** it SHALL NOT reopen as a fresh browser session for the same URL

#### Scenario: Multiple minimized entries remain distinguishable
- **WHEN** more than one browser-family entry is docked at the same time
- **THEN** the dock or tabs overlay SHALL present each entry with enough preview identity to choose the intended restore target
- **AND** restoring one entry SHALL NOT destroy the other docked entries

### Requirement: Browser-family close shall remain distinct from minimize
The system SHALL destroy browser-family state only when the user invokes true close rather than minimize.

#### Scenario: Close active browser-family surface
- **WHEN** the user invokes close while a browser or reader surface is active
- **THEN** the browser-family host SHALL dismiss that active surface as closed
- **AND** the host SHALL release the corresponding browser-family entry instead of storing it in the dock

#### Scenario: Close minimized browser-family entry
- **WHEN** the user closes a docked browser-family entry from the dock or tabs overlay
- **THEN** the browser-family host SHALL remove that entry from the dock
- **AND** restoring that closed entry SHALL no longer be possible
