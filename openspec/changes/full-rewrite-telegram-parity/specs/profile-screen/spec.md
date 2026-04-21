## ADDED Requirements

### Requirement: User profile display
The system SHALL provide a `ProfileFragment` extending `BaseFragment` that displays a user's Discourse profile with: large avatar, username, display name, user title, bio (rendered cooked HTML), trust level, join date, last seen date, and statistics (topics created, posts, likes given, likes received).

#### Scenario: View own profile
- **WHEN** the user navigates to their own profile
- **THEN** all profile fields are displayed from `/u/{username}.json` response

#### Scenario: View other user profile
- **WHEN** the user taps on another user's avatar or username
- **THEN** `ProfileFragment(username)` is presented with that user's profile data

### Requirement: Profile header with collapsible avatar
The profile header SHALL show a large avatar (72dp) and user info. Scrolling down SHALL collapse the header, shrinking the avatar and transitioning the username to the ActionBar title.

#### Scenario: Scroll collapse animation
- **WHEN** the user scrolls down on the profile page
- **THEN** the avatar smoothly shrinks and the username appears in the ActionBar

### Requirement: Statistics section
The profile SHALL display a statistics section with: Topics Created, Posts, Likes Given, Likes Received, Days Visited, in a grid or list layout.

#### Scenario: Statistics display
- **WHEN** a profile is loaded with 42 topics, 567 posts, 890 likes received
- **THEN** these numbers are displayed in formatted form (e.g., "567" or "0.9k")

### Requirement: Bio rendering
The user's bio (if present) SHALL be rendered using `CookedHtmlRenderer` since Discourse bios are cooked HTML.

#### Scenario: Bio with links
- **WHEN** a user's bio contains `<p>Visit my <a href="...">blog</a></p>`
- **THEN** the bio is rendered natively with a tappable link

### Requirement: Logout action
When viewing the own profile, the ActionBar menu SHALL include a "Logout" option that clears the session and navigates to the login screen.

#### Scenario: Logout flow
- **WHEN** the user taps "Logout"
- **THEN** `AuthSessionManager` performs logout/clear-session work, and the app navigates to `LoginFragment`
