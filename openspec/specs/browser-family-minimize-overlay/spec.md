# browser-family-minimize-overlay Specification

## Purpose
TBD - created by archiving change telegram-browser-family-minimize-overlay. Update Purpose after archive.
## Requirements
### Requirement: Browser-family host shall present the regular browser as a full-screen bottom-up surface
The system SHALL present the regular in-app browser from the bottom of the screen and settle it as a full-screen browser-family surface instead of leaving it in a sheet-like half-height resting state.

#### Scenario: Open regular browser from app content
- **WHEN** the app opens a regular browser-family request
- **THEN** the browser-family host SHALL animate the browser in from the bottom edge
- **AND** the browser SHALL settle at full size as the active foreground surface

#### Scenario: Regular browser remains full-size while active
- **WHEN** the regular browser is the active browser-family surface
- **THEN** the browser SHALL NOT expose a persistent half-height resting state or visible sheet handle
- **AND** browser-only header behavior SHALL remain stable instead of using reader-style scroll-collapse motion

### Requirement: Browser-family host shall support minimize and restore for active browser-family surfaces
The system SHALL allow the active browser-family surface to minimize into a bottom-strip entry and later restore the same live surface.

#### Scenario: Minimize regular browser
- **WHEN** the user invokes minimize while the regular browser is active
- **THEN** the browser-family host SHALL animate that browser surface into a minimized bottom-strip entry
- **AND** the underlying app scene SHALL resume as the visible foreground owner

#### Scenario: Minimize Instant View
- **WHEN** the user invokes minimize while Instant View is active
- **THEN** the browser-family host SHALL minimize the active reader surface into the same bottom-strip system
- **AND** the minimized entry SHALL preserve a restoration path back to that reader surface

#### Scenario: Restore minimized browser-family entry
- **WHEN** the user reopens the minimized bottom-strip entry
- **THEN** the browser-family host SHALL restore that same browser or reader surface to the foreground
- **AND** the restore transition SHALL reuse the existing browser-family entry instead of creating a fresh open request

### Requirement: Minimized browser-family entry shall remain identifiable and resumable
The system SHALL keep the minimized browser-family entry identifiable enough for the user to understand what will be restored and SHALL preserve the corresponding live session state while the entry remains minimized.

#### Scenario: Minimized browser entry keeps session continuity
- **WHEN** a regular browser entry is restored after being minimized
- **THEN** the browser SHALL resume the same page, history continuity, and browser-scoped chrome state
- **AND** it SHALL NOT reopen as a fresh browser session for the same URL

#### Scenario: Minimized Instant View keeps reading continuity
- **WHEN** an Instant View entry is restored after being minimized
- **THEN** the reader SHALL resume the same document and reading position
- **AND** the source browser context SHALL remain recoverable when the reader later closes

### Requirement: Browser-family close shall remain distinct from minimize
The system SHALL destroy browser-family state only when the user invokes true close rather than minimize.

#### Scenario: Close active browser-family surface
- **WHEN** the user invokes close while a browser or reader surface is active
- **THEN** the browser-family host SHALL dismiss that active surface as closed
- **AND** the host SHALL release the corresponding browser-family entry instead of storing it in the bottom strip

#### Scenario: Close minimized browser-family entry
- **WHEN** the user closes the minimized bottom-strip entry
- **THEN** the browser-family host SHALL remove that entry from the bottom strip
- **AND** restoring that closed entry SHALL no longer be possible

