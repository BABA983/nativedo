## ADDED Requirements

### Requirement: Enhanced RecyclerView
The system SHALL provide a `RecyclerListView` extending `RecyclerView` with built-in support for: item click/long-click listeners set at the list level (not per-ViewHolder), sectioned adapters, and theme-aware divider drawing.

#### Scenario: List-level item click
- **WHEN** `recyclerListView.setOnItemClickListener { view, position -> }` is called
- **THEN** tapping any item triggers the listener with the correct view and adapter position

#### Scenario: List-level long click
- **WHEN** `recyclerListView.setOnItemLongClickListener { view, position -> }` is called
- **THEN** long-pressing any item triggers the listener

### Requirement: Fast scroll support
The system SHALL support an optional fast scroll bar (thumb indicator on the right edge) that appears when the user scrolls and allows dragging to quickly navigate long lists.

#### Scenario: Fast scroll activation
- **WHEN** the user touches and drags the fast scroll thumb
- **THEN** the list scrolls proportionally to the thumb position

### Requirement: Sectioned adapter support
The system SHALL support adapters that define sections with headers. Section headers SHALL be sticky (optionally) and the adapter SHALL provide `getSectionCount()`, `getCountForSection(section)`, and `isSection(position)` methods.

#### Scenario: Section headers
- **WHEN** a sectioned adapter is set with 3 sections
- **THEN** section header views appear at the correct positions separating each section's items

### Requirement: Item enter animations
The system SHALL support configurable item enter animations (fade-in, slide-up) when items first appear in the viewport during initial load or after data changes.

#### Scenario: Initial load animation
- **WHEN** the list first loads with data
- **THEN** items animate in with a staggered fade-up effect
