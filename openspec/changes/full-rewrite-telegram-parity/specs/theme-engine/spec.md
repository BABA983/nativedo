## ADDED Requirements

### Requirement: Color-key based theme system
The system SHALL provide a `NdTheme` object that maps string color keys to ARGB integer values. All UI components SHALL read colors from `NdTheme.getColor(key: String): Int` instead of using hardcoded colors or resource references.

#### Scenario: Read a theme color
- **WHEN** a TopicCell calls `NdTheme.getColor(key_topic_title)`
- **THEN** it receives the current theme's ARGB value for topic titles

#### Scenario: Unknown color key
- **WHEN** `NdTheme.getColor("nonexistent_key")` is called
- **THEN** the function returns a fallback color (e.g., Color.RED for debug visibility)

### Requirement: Built-in Light and Dark themes
The system SHALL include at least two built-in themes: Light and Dark. Each theme SHALL define values for all color keys used by the application.

#### Scenario: Light theme colors
- **WHEN** the Light theme is active
- **THEN** `key_windowBackgroundWhite` returns a white/near-white value and `key_actionBarDefault` returns the app's primary brand color

#### Scenario: Dark theme colors
- **WHEN** the Dark theme is active
- **THEN** `key_windowBackgroundWhite` returns a dark value and text colors have sufficient contrast

### Requirement: System theme mode
The system SHALL support three theme modes: `Light`, `Dark`, and `System`. When `System` mode is active, the theme SHALL follow the device's dark mode setting. The selected mode SHALL be persisted to `SharedPreferences`.

#### Scenario: System mode follows device
- **WHEN** theme mode is `System` and the device switches to dark mode
- **THEN** the app theme changes to Dark automatically

#### Scenario: Mode persistence
- **WHEN** the user selects Dark mode and restarts the app
- **THEN** the app starts in Dark mode

### Requirement: Runtime theme switching with animation
The system SHALL support switching themes at runtime with a smooth crossfade animation. All visible UI components SHALL update their colors during the transition.

#### Scenario: Animated theme switch
- **WHEN** the user switches from Light to Dark theme
- **THEN** all visible colors transition smoothly over ~300ms (no hard flash)

### Requirement: Discourse-specific color keys
The system SHALL define color keys specific to Discourse content rendering including: `key_topic_title`, `key_topic_excerpt`, `key_topic_categoryBadge`, `key_topic_metadata`, `key_post_body`, `key_post_codeBlock`, `key_post_codeBlockBackground`, `key_post_blockquote`, `key_post_blockquoteBorder`, `key_post_link`, `key_post_mentionText`, `key_post_mentionBackground`, `key_trust_level_badge`.

#### Scenario: Code block styling
- **WHEN** a code block is rendered in a post
- **THEN** it uses `key_post_codeBlock` for text color and `key_post_codeBlockBackground` for background color

### Requirement: Theme-aware Paint and Drawable helpers
The system SHALL provide helper methods to create `Paint` and `Drawable` instances that automatically use theme colors: `createPaint(key: String): Paint`, `getThemedDrawable(key: String): Drawable`.

#### Scenario: Themed paint creation
- **WHEN** `NdTheme.createPaint(key_topic_title)` is called
- **THEN** the returned Paint has its color set to the current theme's topic title color
