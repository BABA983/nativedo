# home-root-search Specification

## Purpose
TBD - created by archiving change align-telegram-home-search-scene-ownership. Update Purpose after archive.
## Requirements
### Requirement: Home search shall be owned by the home root scene
The system SHALL present home search as a scene-local mode inside `HomeRoute` instead of requiring a standalone root search route to search from the home scene.

#### Scenario: Open search from home
- **WHEN** the user opens search from the home root scene
- **THEN** the system SHALL switch the active home scene mode from feed to search inside `HomeRoute`
- **AND** the system SHALL NOT require a separate `NavHost` route transition to present home search

#### Scenario: Close home search
- **WHEN** the user closes home search
- **THEN** the system SHALL restore the home feed scene inside the same `HomeRoute`
- **AND** the previous home content state SHALL remain recoverable without rebuilding the entire root scene as a separate route

### Requirement: Home search shall preserve the home scaffold and chrome boundary
The system SHALL keep home search inside the same scaffold and top-bar ownership boundary as the normal home scene so layout and insets remain stable.

#### Scenario: Home search becomes visible
- **WHEN** home search is shown
- **THEN** the home scene SHALL remain mounted inside the same `NativeDoReaderScaffold` or equivalent root-scene scaffold
- **AND** the search header SHALL participate in that scaffold's top-bar contract instead of drawing as an unrelated full-screen branch

#### Scenario: Home search lays out under system bars
- **WHEN** home search is active
- **THEN** the search UI SHALL respect the same top inset and content padding contract as the normal home scene
- **AND** the search header or content SHALL NOT overlap the system status bar because it bypassed the scene scaffold

### Requirement: Root shell shall react to home-search activity
The system SHALL allow the root shell to react explicitly when home search is active so root-level chrome such as bottom tabs can coordinate with the home scene.

#### Scenario: Home search hides root bottom tabs
- **WHEN** home search becomes active
- **THEN** the home scene SHALL publish that active state to the shell
- **AND** the root bottom tabs SHALL hide while home search remains active

#### Scenario: Home search ends
- **WHEN** home search is dismissed
- **THEN** the shell SHALL receive the inactive state
- **AND** the root bottom tabs SHALL become eligible to reappear according to the normal root-scene rules

