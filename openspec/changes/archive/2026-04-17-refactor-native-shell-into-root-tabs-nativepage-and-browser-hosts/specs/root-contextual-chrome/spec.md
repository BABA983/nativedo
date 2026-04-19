## MODIFIED Requirements

### Requirement: Root chrome shall expose scene-specific header states
The system SHALL scope root chrome to the active root scene so each root scene owns its own header and background behavior, and the app shell SHALL NOT render a separate global root top bar above active native pages.

#### Scenario: Enter search from home header
- **WHEN** the user activates search from the home root scene header
- **THEN** the home scene chrome MUST transition into its search state
- **THEN** normal trailing actions in the home scene header MUST be hidden until search is dismissed

#### Scenario: Switch active root scene
- **WHEN** the user switches from one root scene to another such as Home to Profile
- **THEN** the newly active root scene MUST become the owner of foreground root chrome
- **THEN** the previously active root scene header MUST stop rendering as the active root header

#### Scenario: Present native page above root scene
- **WHEN** `NativePageStackHost` presents a native page above the current root scene
- **THEN** the active root scene chrome MUST stop owning the foreground top bar
- **THEN** the root scene chrome MUST resume only after the native page stack returns to root

### Requirement: Root contextual options shall use a contextual anchored panel
The system SHALL present root more-actions from the active root scene header as a contextual anchored panel that is scoped to the current root scene and dismissed independently from other chrome states.

#### Scenario: Open root more-actions
- **WHEN** the user opens more-actions from the active root scene header
- **THEN** the system MUST show a contextual options panel anchored to that root scene header control
- **THEN** the panel MUST remain scoped to the active root scene instead of a global app-level toolbar

#### Scenario: Dismiss root contextual options
- **WHEN** the user taps outside the contextual options panel or presses back
- **THEN** the panel MUST close
- **THEN** the current root scene MUST remain in its prior non-menu state

#### Scenario: Native page interrupts contextual options
- **WHEN** a native page is presented while the root contextual options panel is open
- **THEN** the contextual options panel MUST close before the native page takes the foreground
- **THEN** the native page MUST open without the root panel remaining visible behind it
