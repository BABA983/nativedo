## ADDED Requirements

### Requirement: Bottom dock shall surface docked browser-family sessions at the app shell bottom edge
The system SHALL render minimized browser-family sessions in a bottom-attached dock above the current app scene while at least one docked session exists.

#### Scenario: First docked session appears
- **WHEN** the first browser-family session is minimized into the dock
- **THEN** the app shell SHALL show a collapsed bottom dock attached to the bottom edge
- **AND** the dock SHALL identify the minimized session well enough to reopen it later

#### Scenario: Empty dock is hidden
- **WHEN** the last docked browser-family session is restored or closed
- **THEN** the bottom dock SHALL leave the foreground shell
- **AND** it SHALL NOT keep an empty placeholder visible

### Requirement: Bottom dock shall adapt between direct reopen and overview behavior
The system SHALL switch dock interaction between direct reopen and overview presentation based on how many docked sessions exist.

#### Scenario: Single docked session reopens directly
- **WHEN** exactly one docked browser-family session exists and the user taps the collapsed dock
- **THEN** the system SHALL restore that docked session directly
- **AND** it SHALL NOT require an intermediate overview step

#### Scenario: Multiple docked sessions open overview
- **WHEN** more than one docked browser-family session exists and the user taps the collapsed dock
- **THEN** the system SHALL open a dock overview instead of restoring immediately
- **AND** the collapsed dock SHALL still represent the most recently minimized session as the foremost card

### Requirement: Dock overview shall support docked-session browsing and dismissal
The system SHALL provide an expanded dock overview that lets the user inspect, restore, and close docked browser-family sessions without leaving the current app scene.

#### Scenario: Overview lists docked sessions in latest-first order
- **WHEN** the dock overview opens
- **THEN** it SHALL present docked browser-family sessions in latest-first order
- **AND** each overview entry SHALL remain individually restorable and closable

#### Scenario: Restore selected session from overview
- **WHEN** the user selects a docked browser-family session from the dock overview
- **THEN** the system SHALL restore that specific live session to the foreground
- **AND** the restored session SHALL leave the dock while it is active

#### Scenario: Close docked session from dock UI
- **WHEN** the user closes a docked browser-family session from the collapsed dock or dock overview
- **THEN** the system SHALL remove that session from the dock without reopening it
- **AND** the remaining dock state SHALL update to reflect the new session count
