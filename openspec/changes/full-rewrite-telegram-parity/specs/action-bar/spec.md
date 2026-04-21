## ADDED Requirements

### Requirement: Custom top bar component
The system SHALL provide an `ActionBar` ViewGroup that renders a custom top bar with: back button (optional), title text, subtitle text (optional), and a menu area for action items. The ActionBar SHALL NOT use Material Toolbar.

#### Scenario: Basic ActionBar display
- **WHEN** an ActionBar is created with title "Latest Topics"
- **THEN** it displays the title text centered or left-aligned (configurable) with the app's theme action bar colors

#### Scenario: ActionBar with back button
- **WHEN** `actionBar.setBackButtonImage(R.drawable.ic_back)` is called
- **THEN** a back arrow icon appears on the left side of the ActionBar

### Requirement: ActionBar menu system
The system SHALL provide `ActionBarMenu` and `ActionBarMenuItem` classes for adding action items (icons and/or text) to the right side of the ActionBar. Menu items SHALL support click listeners and optional popup submenus.

#### Scenario: Add menu item
- **WHEN** `actionBar.createMenu().addItem(id, iconRes)` is called
- **THEN** an icon button appears on the right side of the ActionBar

#### Scenario: Menu item click
- **WHEN** the user taps a menu item
- **THEN** the registered `OnClickListener` is invoked

### Requirement: Search mode
The system SHALL support a search mode where the ActionBar title is replaced by a text input field with a clear button. Entering and exiting search mode SHALL be animated.

#### Scenario: Enter search mode
- **WHEN** `actionBar.openSearchField("")` is called
- **THEN** the title fades out, a search input field fades in, and the keyboard appears

#### Scenario: Exit search mode
- **WHEN** `actionBar.closeSearchField()` is called
- **THEN** the search input fades out, the title fades back in, and the keyboard is dismissed

### Requirement: Theme-aware rendering
The ActionBar SHALL read its background color from `NdTheme.getColor(key_actionBarDefault)`, title color from `NdTheme.getColor(key_actionBarDefaultTitle)`, and icon tint from `NdTheme.getColor(key_actionBarDefaultIcon)`. Colors SHALL update on theme change.

#### Scenario: Theme change updates ActionBar
- **WHEN** the theme switches from Light to Dark
- **THEN** the ActionBar's background, title, and icon colors animate to the new theme values
