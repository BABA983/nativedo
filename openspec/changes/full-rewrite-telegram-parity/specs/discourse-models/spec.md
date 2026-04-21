## ADDED Requirements

### Requirement: TopicSummary data class
The system SHALL provide a `TopicSummary` data class with fields: `id: Int`, `title: String`, `slug: String`, `categoryId: Int?`, `imageUrl: String?`, `replyCount: Int`, `viewCount: Int`, `likeCount: Int`, `createdAt: String`, `lastPostedAt: String?`, `pinned: Boolean`, `tags: List<String>`, `posters: List<PosterInfo>`, `excerpt: String?`, `lastPosterUsername: String?`. It SHALL be deserializable from Discourse `/latest.json` topic objects via kotlinx.serialization.

#### Scenario: Deserialize from JSON
- **WHEN** a topic JSON object from `/latest.json` is deserialized
- **THEN** all fields are correctly mapped including nested `posters` array

### Requirement: TopicDetail data class
The system SHALL provide a `TopicDetail` data class with fields: `id: Int`, `title: String`, `createdAt: String`, `lastPostedAt: String?`, `viewCount: Int`, `replyCount: Int`, `likeCount: Int`, `tags: List<String>`, `categoryId: Int?`, `posts: List<Post>`, `totalPosts: Int`, `streamPostIds: List<Int>`.

#### Scenario: Stream post IDs for pagination
- **WHEN** a TopicDetail is deserialized from `/t/{id}.json`
- **THEN** `streamPostIds` contains the full list of post IDs from `post_stream.stream`

### Requirement: Post data class
The system SHALL provide a `Post` data class with fields: `id: Int`, `postNumber: Int`, `username: String`, `displayName: String?`, `avatarTemplate: String`, `createdAt: String`, `updatedAt: String?`, `cooked: String`, `replyToPostNumber: Int?`, `likeCount: Int`, `replyCount: Int`, `reads: Int`, `userTrustLevel: Int?`, `userTitle: String?`.

#### Scenario: Post with reply reference
- **WHEN** a Post is deserialized with `reply_to_post_number: 3`
- **THEN** `post.replyToPostNumber` is 3

### Requirement: CategorySummary data class
The system SHALL provide a `CategorySummary` data class with fields: `id: Int`, `name: String`, `slug: String`, `color: String`, `textColor: String?`, `parentCategoryId: Int?`.

#### Scenario: Category color parsing
- **WHEN** a CategorySummary is created with color "0088CC"
- **THEN** the color string can be converted to an ARGB int (0xFF0088CC)

### Requirement: SearchResult data class
The system SHALL provide a `SearchResult` data class containing `topics: List<TopicSummary>` and `users: List<UserSearchHit>`.

#### Scenario: Search result deserialization
- **WHEN** `/search.json` response is deserialized
- **THEN** both topic hits and user hits are populated

### Requirement: UserProfile data class
The system SHALL provide a `UserProfile` data class with fields: `username: String`, `name: String?`, `avatarTemplate: String`, `trustLevel: Int`, `title: String?`, `bioCooked: String?`, `websiteName: String?`, `location: String?`, `createdAt: String`, `lastSeenAt: String?`, `badgeCount: Int`, `likesGiven: Int`, `likesReceived: Int`, `topicCount: Int`, `postCount: Int`.

#### Scenario: Profile deserialization
- **WHEN** `/u/{username}.json` response is deserialized
- **THEN** all profile fields are populated including statistics

### Requirement: AppError sealed hierarchy
The system SHALL provide `AppError` as a sealed class with variants: `Network(cause: Throwable)`, `Http(code: Int, message: String)`, `Parse(cause: Throwable)`, `SessionExpired`.

#### Scenario: Pattern matching on errors
- **WHEN** an API call returns an `AppError`
- **THEN** it can be matched with `when(error) { is AppError.Network -> ..., is AppError.Http -> ..., ... }`
