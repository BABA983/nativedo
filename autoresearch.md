# Autoresearch: Telegram browser tabs manager motion smoothness

## Objective
Improve Telegram-like motion smoothness for the Android browser tabs manager.

This round focuses on interaction continuity rather than static chrome parity:
- manager open/close continuity between dock and overlay
- manager dismiss returning cleanly toward dock instead of only fading away
- manager presentation tuning so the open state feels less heavy and less detached
- preserve the previous round’s minimal Telegram dock/header chrome

## Metrics
- **Primary**: `telegram_motion_penalty` (unitless, lower is better)
  - Static motion-parity score derived from the current code.
  - Penalizes:
    - missing `CloseManager` transition wiring on manager dismiss
    - manager close duration shorter than Telegram-like overlay timing
    - oversized manager launch translation
    - overly dim manager entry alpha
- **Secondary**:
  - close-manager wiring penalty
  - close-duration penalty
  - launch-translation penalty
  - list-alpha penalty

## How to Run
`./autoresearch.sh`

## Files in Scope
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsShell.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsTransition.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/BrowserTabsShellMotionModelTest.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/NativeDoAppStateTest.kt`

## Off Limits
- unrelated feature logic
- auth/session/network contracts
- artificial benchmark tricks that do not correspond to real motion improvements

## Constraints
- Keep current Telegram-like minimal header chrome from the previous kept experiment.
- Targeted unit tests must pass.
- Prefer pure helper extraction for motion tuning so behavior is testable.

## Telegram Findings To Preserve
- `BottomSheetTabsOverlay.animateOpen()` uses one overlay owner and a 320ms ease-out-quint timing in both directions.
- Dismissing the manager should visually return authority to the dock instead of simply dropping the overlay state.
- Manager stack presentation should feel present immediately, not start too low or too faded.

## What's Been Tried
- Previous kept experiment `902be67`:
  - removed extra leading icons from dock / transition dock / manager header
  - restored right-side expand chevron
  - softened browser sheet entry scale/alpha/scrim
- Remaining gap for this round:
  - `dismissBrowserTabsManager()` still does not enqueue `CloseManager`
  - manager close duration is still shorter than Telegram overlay timing
  - manager opens with relatively large lift (`72.dp`) and low initial list alpha (`0.6f`)
