## ADDED Requirements

### Requirement: Room database for offline data
The system SHALL provide a Room database (`NdDatabase`) with tables for: cached topic lists (per feed type), cached topic details with posts, and cached category data.

#### Scenario: Cache latest topics
- **WHEN** `/latest.json` response is received
- **THEN** the topic summaries are stored in the local database

#### Scenario: Load cached topics offline
- **WHEN** the app opens without network connectivity
- **THEN** the last cached topic list is displayed from the local database

### Requirement: Cache invalidation strategy
The system SHALL use a time-based cache invalidation: topic lists expire after 5 minutes, topic details expire after 10 minutes, category data expires after 1 hour. Expired cache SHALL still be displayed while a fresh network request is in flight (stale-while-revalidate).

#### Scenario: Stale-while-revalidate
- **WHEN** the cached topic list is 8 minutes old and a network request is in flight
- **THEN** the stale cached data is displayed immediately, then replaced when the fresh data arrives

### Requirement: Image disk cache
The system SHALL leverage Coil 3's built-in disk cache for images. The cache size SHALL be configurable (default: 250MB).

#### Scenario: Cached image loading
- **WHEN** an image has been previously loaded and is in disk cache
- **THEN** it loads from disk without a network request

### Requirement: Cache size management
The system SHALL provide a way to view total cache size and clear all cached data (database + image cache) from the settings screen.

#### Scenario: Clear all caches
- **WHEN** the user taps "Clear Cache" in settings
- **THEN** the Room database is cleared, Coil disk cache is cleared, and the displayed cache size resets to 0
