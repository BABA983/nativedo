## ADDED Requirements

### Requirement: Browser tabs dock shall be owned by the app shell bottom edge
The system SHALL render minimized browser-family sessions in a shell-owned bottom dock that participates in bottom layout and inset behavior instead of floating as an isolated feature overlay.

#### Scenario: First docked browser-family session appears
- **WHEN** the first browser or Instant View session is minimized into the dock
- **THEN** the app shell SHALL show a collapsed browser tabs dock attached to the bottom edge
- **AND** the shell SHALL update its visible bottom spacing to account for the dock height

#### Scenario: Browser tabs dock disappears when empty
- **WHEN** the last docked browser-family session is restored or closed
- **THEN** the browser tabs dock SHALL leave the app shell foreground
- **AND** the shell SHALL release the bottom spacing previously occupied by the dock

### Requirement: Browser tabs dock shall switch between direct restore and tabs-manager behavior
The system SHALL route dock interaction by docked-session count so one docked session restores directly and multiple docked sessions open a dedicated tabs manager.

#### Scenario: Single docked session restores directly
- **WHEN** exactly one docked browser-family session exists and the user taps the collapsed dock
- **THEN** the system SHALL restore that docked session directly
- **AND** it SHALL NOT open the tabs manager first

#### Scenario: Multiple docked sessions open tabs manager
- **WHEN** more than one docked browser-family session exists and the user taps the collapsed dock
- **THEN** the system SHALL open a dedicated tabs manager overlay
- **AND** the manager SHALL present the most recently docked session as the foremost preview card

### Requirement: Browser tabs manager shall support preview browsing and destructive dismissal
The system SHALL provide a dedicated tabs manager overlay that lets the user inspect docked browser-family sessions, restore one, dismiss one, dismiss all, or leave the manager without changing dock state.

#### Scenario: Restore docked session from tabs manager
- **WHEN** the tabs manager is visible and the user taps a docked session card
- **THEN** the system SHALL restore that browser-family session to the foreground
- **AND** the restored session SHALL leave the dock

#### Scenario: Dismiss one docked session from tabs manager
- **WHEN** the tabs manager is visible and the user swipes a docked session card away or taps its close affordance
- **THEN** the system SHALL remove that docked session from the dock
- **AND** restoring that dismissed session SHALL no longer be possible

#### Scenario: Dismiss all docked sessions from tabs manager
- **WHEN** the tabs manager is visible and the user taps the `Close All Tabs` control
- **THEN** the system SHALL destroy every docked browser-family session
- **AND** the collapsed dock SHALL disappear after the manager closes

#### Scenario: Leave tabs manager without restoring or closing
- **WHEN** the tabs manager is visible and the user taps outside the manager surface or otherwise dismisses it
- **THEN** the tabs manager SHALL close back to the collapsed dock
- **AND** the current docked sessions SHALL remain unchanged
