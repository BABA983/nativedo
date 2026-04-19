## ADDED Requirements

### Requirement: Topic replies entry SHALL open a bottom sheet style reply panel
The topic screen SHALL let the user open a reply panel from the post footer replies entry. The panel SHALL behave as a bottom sheet style surface that enters from the bottom, opens at a partial height, and supports scrolling within its content.

#### Scenario: Open replies sheet
- **WHEN** the user taps the replies entry in a topic post footer
- **THEN** the system SHALL present a bottom sheet style reply panel from the bottom edge
- **AND** the panel SHALL open at a partial height rather than replacing the full topic screen immediately

#### Scenario: Replies sheet content exceeds available height
- **WHEN** the reply panel content exceeds the initially visible height
- **THEN** the reply panel SHALL allow the user to scroll its content
- **AND** the panel MAY support further expansion without losing the bottom sheet interaction model

### Requirement: Replies sheet SHALL optimize for scan-first reply discovery
The replies sheet SHALL present each reply as a compact scan-first item that helps the user decide whether to jump to the full reply in context. Each item SHALL show author identity, timing, and a bounded reply summary rather than rendering the replies sheet as a second full post screen.

#### Scenario: Render reply item in sheet
- **WHEN** a reply is listed inside the replies sheet
- **THEN** the item SHALL show author identity and relative time
- **AND** it SHALL show a bounded reply summary suitable for scan-first reading

#### Scenario: Reply item contains more content than the bounded summary
- **WHEN** a reply exceeds the sheet summary limit
- **THEN** the replies sheet SHALL truncate the visible summary
- **AND** it SHALL provide an affordance to open the full reply in topic context

### Requirement: Replies sheet SHALL preserve navigation back to topic context
The replies sheet SHALL act as a lightweight secondary surface over the topic screen rather than replacing the topic context.

#### Scenario: User opens original reply from the sheet
- **WHEN** the user chooses to view a listed reply in full
- **THEN** the system SHALL navigate to or focus the original reply in the topic screen
- **AND** the replies sheet SHALL dismiss or yield focus so the topic context becomes primary again

#### Scenario: User dismisses the sheet without opening a reply
- **WHEN** the user dismisses the replies sheet
- **THEN** the underlying topic screen SHALL remain available in its prior state
