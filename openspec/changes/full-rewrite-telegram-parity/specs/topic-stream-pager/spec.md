## ADDED Requirements

### Requirement: Stream-based post pagination
The system SHALL provide a `TopicStreamPager` that manages the Discourse post stream pagination model: (1) receive the full `streamPostIds: List<Int>` from topic detail, (2) chunk the IDs into groups of 20, (3) fetch each chunk via `GET /t/{id}/posts.json?post_ids[]=...`.

#### Scenario: Initial load
- **WHEN** a TopicStreamPager is initialized with topicId=123 and streamPostIds=[1,2,...,100]
- **THEN** the first chunk (IDs 1-20) is fetched and returned as `List<Post>`

#### Scenario: Load next page
- **WHEN** `pager.loadNextPage()` is called after the initial load
- **THEN** the next chunk (IDs 21-40) is fetched and appended

#### Scenario: All posts loaded
- **WHEN** all chunks have been fetched
- **THEN** `pager.hasMore` returns false and `loadNextPage()` is a no-op

### Requirement: Jump to specific post
The system SHALL support jumping to a specific post number by calculating which chunk contains that post's ID and fetching it directly.

#### Scenario: Jump to post #50
- **WHEN** `pager.jumpToPost(50)` is called
- **THEN** the chunk containing post #50 is fetched, and the UI scrolls to that post's position

### Requirement: Caching loaded chunks
The system SHALL cache loaded post chunks in memory so that scrolling back to previously loaded posts does not trigger re-fetching.

#### Scenario: Scroll back to loaded posts
- **WHEN** the user scrolls back to posts that were previously loaded
- **THEN** the cached posts are displayed without a network request
