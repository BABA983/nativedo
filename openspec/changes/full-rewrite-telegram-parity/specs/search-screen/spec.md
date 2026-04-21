## ADDED Requirements

### Requirement: Search screen with query input
The system SHALL provide a `SearchFragment` extending `BaseFragment` with an ActionBar in search mode. The user SHALL be able to type a query and submit it.

#### Scenario: Open search
- **WHEN** `SearchFragment` is presented
- **THEN** the ActionBar search field is focused and the keyboard appears

### Requirement: Combined topic and user results
Search results SHALL display both topic hits and user hits from `/search.json`. Topics SHALL be rendered using `TopicCell` and users using `UserCell`, separated by section headers.

#### Scenario: Search with both results
- **WHEN** the user searches "kotlin" and the API returns 10 topics and 3 users
- **THEN** the results list shows a "Topics" section header, 10 TopicCells, a "Users" section header, and 3 UserCells

#### Scenario: No results
- **WHEN** the user searches "xyznonexistent" and the API returns no results
- **THEN** an empty state message is displayed

### Requirement: Topic result tap
Tapping a topic result SHALL navigate to `TopicDetailFragment` for that topic.

#### Scenario: Tap search result topic
- **WHEN** the user taps a topic result with id=789
- **THEN** `TopicDetailFragment(789)` is presented

### Requirement: User result tap
Tapping a user result SHALL navigate to `ProfileFragment` for that user.

#### Scenario: Tap search result user
- **WHEN** the user taps a user result with username "alice"
- **THEN** `ProfileFragment("alice")` is presented

### Requirement: Debounced search
The system SHALL debounce search queries by 300ms to avoid excessive API calls while the user is typing.

#### Scenario: Rapid typing
- **WHEN** the user types "k", "ko", "kot", "kotl", "kotli", "kotlin" within 500ms
- **THEN** only one API request for "kotlin" is made (after 300ms of no typing)
