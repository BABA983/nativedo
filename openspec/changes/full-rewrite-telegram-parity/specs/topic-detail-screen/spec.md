## ADDED Requirements

### Requirement: Native post rendering
The system SHALL display topic detail as a scrollable list of posts rendered using `CookedHtmlRenderer`. Each post SHALL show: author avatar, username, display name, trust level badge, creation time, cooked HTML body (natively rendered), and action bar (like count, reply count).

#### Scenario: Display topic with 5 posts
- **WHEN** `TopicDetailFragment(topicId=123)` is presented and the topic has 5 posts
- **THEN** a list of 5 post items is displayed, each with author info and natively rendered HTML body

### Requirement: Topic header
The system SHALL display a topic header at the top of the post list with: topic title, category badge, tags, view count, reply count, and creation date.

#### Scenario: Topic header display
- **WHEN** a topic detail is loaded
- **THEN** the header shows the topic title, category, tags, and statistics above the first post

### Requirement: Stream-based post pagination
The system SHALL use `TopicStreamPager` to load posts incrementally. The first chunk SHALL be loaded immediately from the topic detail response. Subsequent chunks SHALL be loaded as the user scrolls down.

#### Scenario: Scroll to load more posts
- **WHEN** the user scrolls past the last loaded post in a 50-post topic
- **THEN** the next chunk of posts is loaded and appended to the list

### Requirement: Reply-to reference
Posts that reply to another post SHALL display a "replied to @username" indicator. Tapping it SHALL scroll to the referenced post.

#### Scenario: Tap reply reference
- **WHEN** the user taps "replied to @alice" on post #10 (which replies to post #3)
- **THEN** the list scrolls to post #3 (loading it first if not yet loaded)

### Requirement: Post author avatar and info
Each post SHALL display the author's avatar (via AvatarDrawable/ImageReceiver), username, optional display name, optional user title, and trust level badge.

#### Scenario: Post with full author info
- **WHEN** a post by "alice" with display name "Alice L.", title "Regular", trust level 3 is rendered
- **THEN** the avatar, "alice", "Alice L.", and trust level 3 badge are all displayed

### Requirement: ActionBar with topic title
The TopicDetailFragment's ActionBar SHALL display the topic title (truncated if long). Scrolling down SHALL optionally collapse the header and keep the ActionBar visible.

#### Scenario: ActionBar title
- **WHEN** a topic with title "如何在 Kotlin 中实现高性能列表" is displayed
- **THEN** the ActionBar shows the truncated title

### Requirement: Open in browser
The ActionBar menu SHALL include an "Open in Browser" option that opens the topic URL in the built-in browser.

#### Scenario: Open in browser
- **WHEN** the user taps "Open in Browser" menu item
- **THEN** `actionBarLayout.presentFragment(BrowserFragment("https://linux.do/t/slug/123"))` is called
