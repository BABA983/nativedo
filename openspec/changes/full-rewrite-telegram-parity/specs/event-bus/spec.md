## ADDED Requirements

### Requirement: Type-safe event registration
The system SHALL provide a `NotificationCenter` singleton with methods to register and unregister observers for specific event IDs. Events SHALL be identified by integer constants. Observers SHALL implement a `NotificationCenterDelegate` interface with a `didReceivedNotification(id: Int, vararg args: Any?)` method.

#### Scenario: Register observer
- **WHEN** an observer calls `NotificationCenter.addObserver(observer, eventId)`
- **THEN** the observer receives callbacks when that event is posted

#### Scenario: Unregister observer
- **WHEN** an observer calls `NotificationCenter.removeObserver(observer, eventId)`
- **THEN** the observer no longer receives callbacks for that event

### Requirement: Event posting
The system SHALL provide a `postNotificationName(id: Int, vararg args: Any?)` method that synchronously delivers the event to all registered observers on the main thread.

#### Scenario: Post event with arguments
- **WHEN** `NotificationCenter.postNotificationName(topicsDidLoad, topicList)` is called
- **THEN** all observers registered for `topicsDidLoad` receive the callback with `topicList` as an argument

#### Scenario: Post to unobserved event
- **WHEN** an event is posted with no registered observers
- **THEN** no error occurs and the call completes silently

### Requirement: Discourse-specific event constants
The system SHALL define event constants for Discourse domain events including but not limited to: `topicsDidLoad`, `topicDetailDidLoad`, `sessionDidChange`, `sessionDidExpire`, `csrfTokenDidRefresh`, `themeDidChange`, `categoryDataDidLoad`, `searchResultsDidLoad`, `profileDidLoad`.

#### Scenario: Session expiry notification
- **WHEN** the session expires (cookie `_t` becomes invalid)
- **THEN** `NotificationCenter.postNotificationName(sessionDidExpire)` is called

### Requirement: Observer lifecycle safety
The system SHALL NOT deliver events to observers that have been removed. The system SHALL support removing all observers for a given delegate via `removeObserver(observer: NotificationCenterDelegate)` (removes from all events).

#### Scenario: Remove all observations
- **WHEN** `NotificationCenter.removeObserver(fragment)` is called
- **THEN** the fragment receives no further event callbacks for any event

### Requirement: NotificationCenter stays a global cross-cutting bus
The system SHALL use `NotificationCenter` only for truly global, cross-cutting events and SHALL NOT require screen-local loading or view state to flow through it.

#### Scenario: Screen-local state stays local
- **WHEN** a page needs to track its own loading, selection, or transient render state
- **THEN** that state remains inside the page/controller instead of being promoted to `NotificationCenter`
