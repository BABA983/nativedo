## ADDED Requirements

### Requirement: Letter-based avatar generation
The system SHALL provide an `AvatarDrawable` that generates a circular avatar with the user's first letter on a colored background. The background color SHALL be deterministically derived from the username (consistent color for the same user).

#### Scenario: Generate avatar for user "alice"
- **WHEN** `AvatarDrawable` is created with username "alice"
- **THEN** it draws a circle with a deterministic color and the letter "A" centered in white

#### Scenario: Consistent color mapping
- **WHEN** `AvatarDrawable` is created for "alice" multiple times
- **THEN** the background color is always the same

### Requirement: Remote avatar loading
The system SHALL support loading a user's Discourse avatar URL into a circular image. When the remote image is loading or unavailable, the letter-based avatar SHALL be shown as a placeholder.

#### Scenario: Avatar with remote URL
- **WHEN** an avatar is configured with username "alice" and avatar URL "https://linux.do/user_avatar/..."
- **THEN** the letter avatar is shown immediately, then replaced by the remote image once loaded

### Requirement: Discourse trust level badge overlay
The system SHALL optionally render a small badge overlay on the avatar indicating the user's Discourse trust level (0-4) or special roles (admin, moderator).

#### Scenario: Trust level 3 badge
- **WHEN** an avatar is drawn for a user with trust level 3 (Regular)
- **THEN** a small badge indicator appears at the bottom-right of the avatar circle

### Requirement: Configurable sizes
The system SHALL support multiple avatar sizes (small: 28dp, medium: 40dp, large: 56dp, xlarge: 72dp) for use in different contexts (list items, profile headers, post authors).

#### Scenario: Small avatar in topic list
- **WHEN** a TopicCell renders an avatar at size small (28dp)
- **THEN** the avatar is drawn at 28x28dp with appropriately scaled letter text
