## ADDED Requirements

### Requirement: ActionBar must use the adopted Telegram header structure
The system SHALL provide an `ActionBar` whose adopted subset matches the Telegram header structure for the current host: back button area, title, subtitle, and right-side action area, without falling back to Material toolbar patterns.

#### Scenario: Title and back button render
- **WHEN** a screen creates an `ActionBar` with a title and enables the back button
- **THEN** the ActionBar MUST render a dedicated back button area on the left and the title in the Telegram-style header region rather than a generic toolbar row

#### Scenario: Subtitle is present
- **WHEN** a screen provides a subtitle
- **THEN** the ActionBar MUST render title and subtitle as distinct header text elements and hide the subtitle element when no subtitle is provided

### Requirement: ActionBar title and subtitle must render through SimpleTextView
The system SHALL render ActionBar title and subtitle with `SimpleTextView` rather than temporary `TextView` placeholders so the adopted subset stays aligned with Telegram's text layout model.

#### Scenario: Title text view implementation
- **WHEN** the ActionBar title is shown
- **THEN** the title MUST be measured and laid out by `SimpleTextView`

#### Scenario: Subtitle text view implementation
- **WHEN** the ActionBar subtitle is shown
- **THEN** the subtitle MUST be measured and laid out by `SimpleTextView`

### Requirement: ActionBar must react to runtime theme changes without placeholder drift
The system SHALL update ActionBar background, text, icon, and shadow presentation from `NdTheme` when the runtime theme changes, without relying on temporary host-only drift as the permanent implementation boundary.

#### Scenario: Theme changes while ActionBar is attached
- **WHEN** the runtime theme changes while an ActionBar is visible
- **THEN** the ActionBar MUST update its themed background, title/subtitle colors, icon tint, and shadow visibility for the new theme

### Requirement: Phase 2 ActionBar subset must explicitly bound menu and search behavior
The system SHALL make the current Phase 2 menu/search boundary explicit instead of leaving placeholder support ambiguous.

#### Scenario: No menu or search caller exists
- **WHEN** the current host screen only needs back/title/subtitle behavior
- **THEN** the ActionBar MUST render only the adopted subset it actually supports and MUST NOT depend on hidden placeholder search or menu behavior

#### Scenario: Right-side action area is needed
- **WHEN** the current adopted subset creates a right-side action area
- **THEN** that area MUST be represented by explicit ActionBar-owned structure rather than ad-hoc host-specific child views
