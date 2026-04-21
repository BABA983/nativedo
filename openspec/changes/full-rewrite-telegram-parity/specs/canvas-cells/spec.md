## ADDED Requirements

### Requirement: BaseCell abstract View
The system SHALL provide a `BaseCell` abstract class extending `View` that serves as the base for all Canvas-drawn list items. BaseCell SHALL provide `onMeasure()` with a default full-width measurement and abstract `onDraw(canvas: Canvas)`.

#### Scenario: Cell measurement
- **WHEN** a BaseCell is measured by RecyclerListView
- **THEN** it measures to full parent width and its calculated content height

### Requirement: TopicCell for topic list items
The system SHALL provide a `TopicCell` extending `BaseCell` that renders a Discourse topic summary entirely via Canvas drawing. It SHALL display: topic title (multi-line, truncated), category badge (colored dot + name), author avatar (circular), reply count, view count, last activity time, and optional thumbnail image.

#### Scenario: Topic with all fields
- **WHEN** a TopicCell is bound with a TopicSummary containing title, category, avatar, 42 replies, 1.2k views, and a thumbnail
- **THEN** all fields are drawn in a single `onDraw()` pass with no child Views

#### Scenario: Topic without thumbnail
- **WHEN** a TopicCell is bound with a TopicSummary that has no `image_url`
- **THEN** the cell renders without the thumbnail area, with the text area taking the full width

### Requirement: UserCell for user list items
The system SHALL provide a `UserCell` extending `BaseCell` that renders a user list item with: avatar, username, display name (optional), and trust level badge.

#### Scenario: User cell rendering
- **WHEN** a UserCell is bound with a user having avatar, username "alice", and trust level 3
- **THEN** the cell draws the avatar circle, username text, and a trust level indicator

### Requirement: DividerCell for section separators
The system SHALL provide a `DividerCell` extending `BaseCell` that draws a thin horizontal line using the theme's divider color.

#### Scenario: Divider rendering
- **WHEN** a DividerCell is drawn
- **THEN** a 1dp-height line is drawn using `NdTheme.getColor(key_divider)`

### Requirement: Cell theme awareness
All Cell implementations SHALL read colors from `NdTheme` and SHALL update their appearance when the theme changes (via invalidation triggered by theme change notification).

#### Scenario: Theme change invalidation
- **WHEN** the theme changes
- **THEN** all visible cells are invalidated and redrawn with new theme colors
