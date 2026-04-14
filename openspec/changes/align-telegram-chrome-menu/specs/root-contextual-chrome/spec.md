## ADDED Requirements

### Requirement: Root chrome shall expose scene-specific header states
The system SHALL provide a dedicated root chrome that distinguishes normal, search, action mode, and contextual options states instead of treating the home header as a static top bar.

#### Scenario: Enter search from root header
- **WHEN** the user activates search from the root header
- **THEN** the root chrome MUST transition into the search state
- **THEN** normal trailing actions MUST be hidden until search is dismissed

#### Scenario: Enter action mode from root content selection
- **WHEN** the user enters a multi-select flow from the root content area
- **THEN** the root chrome MUST switch into action mode chrome
- **THEN** the back affordance, title area, and available actions MUST reflect the selection context instead of the normal home header

### Requirement: Root contextual options shall use a contextual anchored panel
The system SHALL present root more-actions through a contextual options panel that is anchored to the triggering control and dismissed independently from other chrome states.

#### Scenario: Open root more-actions
- **WHEN** the user opens the root more-actions entry from the normal header
- **THEN** the system MUST show a contextual options panel anchored to the header control
- **THEN** the panel MUST dim or contextualize the surrounding chrome instead of appearing as an unscoped dropdown

#### Scenario: Dismiss root contextual options
- **WHEN** the user taps outside the contextual options panel or presses back
- **THEN** the panel MUST close
- **THEN** the root chrome MUST remain in its prior non-menu state
