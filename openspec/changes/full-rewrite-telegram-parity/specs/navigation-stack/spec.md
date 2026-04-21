## ADDED Requirements

### Requirement: Fragment-based navigation stack
The system SHALL provide an `ActionBarLayout` ViewGroup that manages a stack of `BaseFragment` instances. Only the topmost fragment's view SHALL be visible at any time. The layout SHALL be the root content view of `MainActivity`.

#### Scenario: Present a fragment
- **WHEN** `actionBarLayout.presentFragment(TopicDetailFragment(topicId))` is called
- **THEN** the new fragment's view is created, animated in from the right, and placed on top of the stack

#### Scenario: Pop a fragment
- **WHEN** `actionBarLayout.closeLastFragment(animated = true)` is called
- **THEN** the topmost fragment animates out to the right, is destroyed, and the previous fragment becomes visible

### Requirement: Swipe-back gesture
The system SHALL support an edge swipe-back gesture (from the left edge of the screen) that allows the user to drag the current fragment off-screen, revealing the previous fragment underneath. The gesture SHALL follow the user's finger position in real-time.

#### Scenario: Swipe-back completes
- **WHEN** the user swipes from the left edge past 35% of the screen width and releases
- **THEN** the current fragment animates off-screen and is removed from the stack

#### Scenario: Swipe-back cancels
- **WHEN** the user swipes from the left edge but releases before 35% of the screen width
- **THEN** the current fragment animates back to its full-screen position

#### Scenario: Swipe velocity threshold
- **WHEN** the user swipes from the left edge with high velocity (>1000dp/s) even if distance is less than 35%
- **THEN** the current fragment is removed (velocity-based dismissal)

### Requirement: Fragment lifecycle management
The system SHALL call `onFragmentCreate()` when a fragment is added to the stack, `onResume()` when it becomes the topmost fragment, `onPause()` when it is covered by another fragment, and `onFragmentDestroy()` when it is removed from the stack.

#### Scenario: Fragment create and resume
- **WHEN** a new fragment is presented
- **THEN** `onFragmentCreate()` is called, then `createView()` is called, then `onResume()` is called

#### Scenario: Fragment pause on cover
- **WHEN** a new fragment is presented on top of the current one
- **THEN** the current fragment receives `onPause()`

### Requirement: BaseFragment abstract class
The system SHALL provide a `BaseFragment` abstract class with: `createView(context: Context): View` (abstract), `onFragmentCreate(): Boolean`, `onFragmentDestroy()`, `onResume()`, `onPause()`, `onBackPressed(): Boolean`, and access to the parent `ActionBarLayout` via `parentLayout`.

#### Scenario: Custom fragment implementation
- **WHEN** a developer extends `BaseFragment` and overrides `createView()`
- **THEN** the returned View is displayed as the fragment's content within the ActionBarLayout

### Requirement: ActionBar integration with navigation
Each `BaseFragment` SHALL have an optional `ActionBar` property. When present, the `ActionBarLayout` SHALL include the ActionBar at the top of the fragment's view and handle its back button to trigger `closeLastFragment()`.

#### Scenario: ActionBar back button
- **WHEN** the user taps the ActionBar's back button
- **THEN** `actionBarLayout.closeLastFragment(animated = true)` is called

### Requirement: Fragment stack access
The system SHALL provide `getFragmentStack(): List<BaseFragment>` to access the current stack, and `addFragmentToStack(fragment, position)` to insert fragments at arbitrary positions.

#### Scenario: Access fragment stack
- **WHEN** `actionBarLayout.getFragmentStack()` is called with 3 fragments presented
- **THEN** a list of 3 BaseFragment instances is returned in stack order (bottom to top)
