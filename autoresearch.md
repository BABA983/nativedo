# Autoresearch: Browser restore continuity

## Objective
Eliminate the abrupt browser re-entry feel when restoring a docked browser-family session.

This round focuses on one concrete parity gap reported by the user:
- restoring a browser from the dock or tabs manager should not behave like a brand new bottom-up sheet open
- once the shared shell transition has already brought the live surface back toward the active position, the browser container should settle coherently instead of replaying a full-screen modal entry

## Metrics
- **Primary**: `browser_restore_continuity_penalty` (unitless, lower is better)
  - Penalizes:
    - missing restore marker on `BrowserHostBrowserEntry`
    - `restoreDockedSession()` not marking restored browser entries as restored-from-dock
    - `AppRoot` not passing restore marker into `AppBrowserSheetContainer`
    - `AppBrowserSheetContainer` still treating restored browser foreground activation like a fresh sheet open
- **Secondary**:
  - restore marker penalty
  - host restore wiring penalty
  - app root propagation penalty
  - sheet activation penalty

## How to Run
`./autoresearch.sh`

## Files in Scope
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/NativeDoAppStateTest.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainerTest.kt`

## Constraints
- Preserve current dock/header Telegram parity.
- Preserve manager motion improvements from the previous kept experiment.
- Do not rebuild browser runtime state from URL-only data.
- Keep correctness coverage in targeted unit tests.

## Telegram Findings To Preserve
- Restoring a docked browser should feel like resuming the same live surface, not creating a brand new browser sheet.
- The shell transition owns the visible morph; the browser container should not replay a second full modal entry once foreground ownership returns.

## What's Been Tried
- Kept experiment `902be67`: minimal Telegram dock/header chrome + lighter browser-sheet entry motion.
- Kept experiment `f23619c`: manager dismiss/open continuity tuning.
- Remaining gap:
  - browser restore path still lacks an explicit restored-from-dock semantic, so `AppBrowserSheetContainer` treats restored foreground activation too much like a fresh open.
