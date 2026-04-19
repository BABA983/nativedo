# Autoresearch: Browser restore settle polish

## Objective
Polish the browser restore handoff after dock or manager restore.

The current implementation no longer replays the heavy fresh open path, but restore still lacks an explicit reactivation token and a dedicated in-place settle animation. The goal is to make restored browser sessions feel like the same live surface smoothly reclaiming foreground ownership.

## Metrics
- **Primary**: `browser_restore_settle_penalty` (unitless, lower is better)
  - Penalizes:
    - browser restore not incrementing `foregroundActivationKey`
    - reader restore not incrementing `foregroundActivationKey`
    - missing dedicated restore-settle helper in `AppBrowserSheetContainer`
    - missing restore-settle animation path in `AppBrowserSheetContainer`
- **Secondary**:
  - browser reactivation key penalty
  - reader reactivation key penalty
  - restore helper penalty
  - restore settle animation penalty

## How to Run
`./autoresearch.sh`

## Files in Scope
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/NativeDoAppStateTest.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainerTest.kt`

## Constraints
- Preserve previous keeps: minimal chrome, manager motion continuity, and no heavy fresh-open replay on restore.
- Avoid reintroducing bottom-up restore motion.
- Keep changes small and testable.

## Telegram Findings To Preserve
- Restore should feel like live-surface reactivation, not new scene creation.
- A tiny in-place settle is acceptable; a second bottom-up open is not.

## What's Been Tried
- `902be67`: Telegram-like dock/header chrome and lighter browser-sheet entry motion.
- `f23619c`: tabs-manager open/close continuity tuning.
- `1d2f490`: restored browser sessions bypass fresh bottom-up entry path.
- Remaining gap for this round:
  - restored browser entries still keep the same activation key, so there is no explicit reactivation signal
  - there is no dedicated in-place restore settle animation helper
