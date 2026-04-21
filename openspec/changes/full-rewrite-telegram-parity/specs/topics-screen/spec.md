## ADDED Requirements

### Requirement: Three-tab topic feed
The system SHALL provide a Topics screen as the app's main entry point with three tabs: Latest, New, and Top. Each tab SHALL display a scrollable list of topic summaries using `RecyclerListView` with `TopicCell` items.

#### Scenario: Default tab on app launch
- **WHEN** the app launches (authenticated)
- **THEN** the Topics screen displays with the Latest tab selected

#### Scenario: Switch between tabs
- **WHEN** the user taps the "New" tab
- **THEN** the topic list updates to show new topics from `/new.json`

### Requirement: Pull-to-refresh
The system SHALL support pull-to-refresh on each topic tab. Pulling down past a threshold SHALL trigger a fresh API call and replace the current list.

#### Scenario: Pull to refresh latest
- **WHEN** the user pulls down on the Latest tab
- **THEN** a refresh indicator appears, `/latest.json?page=0` is fetched, and the list is updated

### Requirement: Infinite scroll pagination
The system SHALL support infinite scroll pagination. When the user scrolls near the bottom of the list, the next page SHALL be automatically loaded if `more_topics_url` is present.

#### Scenario: Load page 2
- **WHEN** the user scrolls to the bottom of page 1 and `more_topics_url` exists
- **THEN** a loading indicator appears at the bottom, the next page is fetched, and new topics are appended

#### Scenario: No more pages
- **WHEN** the user scrolls to the bottom and no `more_topics_url` exists
- **THEN** no loading indicator is shown and no request is made

### Requirement: Topic item tap navigation
The system SHALL navigate to the topic detail screen when a user taps a topic item.

#### Scenario: Tap topic
- **WHEN** the user taps a TopicCell for topic ID 12345
- **THEN** `actionBarLayout.presentFragment(TopicDetailFragment(12345))` is called

### Requirement: Category badge display
Each TopicCell SHALL display the topic's category as a colored badge (colored dot + category name). The color SHALL come from `CategorySummary.color` and the name from `CategorySummary.name`.

#### Scenario: Topic with category
- **WHEN** a topic belongs to category "开发调优" with color "25AAE2"
- **THEN** the TopicCell shows a blue dot followed by "开发调优" text

### Requirement: Pinned topic handling
Pinned topics SHALL appear at the top of the Latest tab with a pin indicator icon. They SHALL NOT appear at the top of New or Top tabs.

#### Scenario: Pinned topics in latest
- **WHEN** the Latest tab loads and 2 topics are pinned
- **THEN** the 2 pinned topics appear first with pin icons, followed by normal topics

### Requirement: Offline fallback
The system SHALL display cached topic data when the network is unavailable, with a banner indicating offline mode.

#### Scenario: Offline load
- **WHEN** the app opens without network and cached topics exist
- **THEN** cached topics are displayed with an "Offline mode" banner at the top
