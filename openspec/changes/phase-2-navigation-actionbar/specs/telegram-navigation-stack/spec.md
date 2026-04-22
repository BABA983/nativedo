## ADDED Requirements

### Requirement: ActionBarLayout must satisfy the adopted Telegram motion contract
The system SHALL provide an `ActionBarLayout` that preserves the adopted Telegram navigation motion contract for the current host subset: push/pop displacement, previous-page left shift, left-edge swipe-back, `35%` distance dismiss, and velocity-based dismiss.

#### Scenario: Push transition
- **WHEN** a fragment is presented with animation from a non-empty stack
- **THEN** the new fragment MUST slide in from the right while the previous fragment shifts left and remains visible during the transition

#### Scenario: Pop transition
- **WHEN** the top fragment is closed with animation
- **THEN** the top fragment MUST slide out to the right, the previous fragment MUST return to its resting position, and the previous fragment MUST become fully visible again

#### Scenario: Swipe-back completes by distance
- **WHEN** the user drags the top fragment from the left edge past `35%` of the container width and releases
- **THEN** the current fragment MUST be dismissed and removed from the stack

#### Scenario: Swipe-back completes by velocity
- **WHEN** the user releases a left-edge swipe with sufficient horizontal velocity even before `35%` distance
- **THEN** the current fragment MUST still be dismissed and removed from the stack

#### Scenario: Swipe-back cancels
- **WHEN** the user releases a left-edge swipe without crossing the dismiss threshold and without sufficient dismiss velocity
- **THEN** the current fragment MUST animate back to its resting position and the previous fragment MUST return to its background offset state

### Requirement: Navigation transitions must keep fragment and system-bar state in sync
The system SHALL keep fragment lifecycle hooks, transition hooks, and host system-bar state synchronized with the top fragment during navigation transitions.

#### Scenario: Fragment becomes covered
- **WHEN** a new fragment is pushed on top of the current fragment
- **THEN** the previous fragment MUST receive pause/transition callbacks consistent with no longer being the top fragment

#### Scenario: Fragment becomes top again
- **WHEN** a pop or completed swipe-back reveals the previous fragment
- **THEN** that fragment MUST receive resume/transition completion callbacks and become the host system-bar source again

### Requirement: ActionBarLayout must support minimal stack save and restore
The system SHALL persist a minimal descriptor and args bundle for each fragment in the current navigation stack so the stack can be restored after configuration change or process recreation.

#### Scenario: Configuration change restore
- **WHEN** the host activity is recreated with previously saved navigation state
- **THEN** `ActionBarLayout` MUST restore the fragment stack in order and resume the top fragment without requiring a fresh bootstrap push

#### Scenario: Restored root prevents duplicate bootstrap
- **WHEN** a saved fragment stack is restored before bootstrap runs
- **THEN** the bootstrap path MUST NOT push a duplicate root fragment on top of the restored stack
