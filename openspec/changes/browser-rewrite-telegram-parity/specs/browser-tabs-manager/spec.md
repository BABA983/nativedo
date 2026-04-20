## ADDED Requirements

### Requirement: Browser tabs dock shall represent minimized browser-family sessions
The system SHALL render minimized browser-family sessions in a dedicated bottom dock that remains attached to the app shell and reflects the minimized session count.

#### Scenario: First browser-family session is minimized
- **WHEN** the first browser or reader session is minimized
- **THEN** the app shell SHALL show a browser tabs dock above the bottom system area
- **AND** the dock SHALL present an identifiable preview for the minimized session

#### Scenario: Additional browser-family sessions are minimized
- **WHEN** more browser-family sessions are minimized while the dock already exists
- **THEN** the dock SHALL continue to represent the minimized session stack
- **AND** the dock presentation SHALL indicate that multiple sessions can be reopened

### Requirement: Browser tabs dock shall choose direct restore or tabs overlay by session count
The system SHALL route dock interaction by the number of minimized sessions so single-session restore remains fast while multi-session reopen uses a dedicated manager.

#### Scenario: Single minimized session restores directly
- **WHEN** exactly one minimized browser-family session exists and the user taps the dock
- **THEN** the browser-family host SHALL restore that same session directly
- **AND** the dock SHALL disappear if no minimized sessions remain afterward

#### Scenario: Multiple minimized sessions open tabs overlay
- **WHEN** more than one minimized browser-family session exists and the user taps the dock
- **THEN** the system SHALL open a dedicated tabs overlay
- **AND** the overlay SHALL present the minimized sessions as individually restorable cards

### Requirement: Browser tabs overlay shall support restore and destructive dismissal
The system SHALL provide a full-screen tabs overlay that lets the user preview minimized sessions, restore one, dismiss one, dismiss all, or leave the overlay without changing session state.

#### Scenario: Restore a session from tabs overlay
- **WHEN** the tabs overlay is visible and the user selects a minimized session card
- **THEN** the browser-family host SHALL restore that same session to the foreground
- **AND** non-selected minimized sessions SHALL remain docked

#### Scenario: Dismiss one session from tabs overlay
- **WHEN** the tabs overlay is visible and the user swipes away a minimized session card or taps its close affordance
- **THEN** the system SHALL remove that session from the dock
- **AND** that dismissed session SHALL no longer be restorable

#### Scenario: Dismiss all sessions from tabs overlay
- **WHEN** the tabs overlay is visible and the user invokes the close-all action
- **THEN** the system SHALL destroy every minimized browser-family session
- **AND** the dock SHALL disappear after the overlay closes

#### Scenario: Leave tabs overlay without restore or dismissal
- **WHEN** the tabs overlay is visible and the user dismisses it without choosing a session
- **THEN** the overlay SHALL close back to the collapsed dock
- **AND** the minimized session set SHALL remain unchanged
