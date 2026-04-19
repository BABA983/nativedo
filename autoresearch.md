# Autoresearch: Telegram browser tabs manager parity

## Objective
Improve Telegram parity for the Android browser tabs manager and browser sheet presentation.

Focus areas:
- collapsed bottom dock interaction and structure
- tabs manager open/close feel and tab-card chrome
- dock/manager tab chrome parity with Telegram: left close, centered title, right expand icon, no extra buttons
- browser sheet opening motion so it feels less stiff and avoids the abrupt full-screen jump described by the user

The user explicitly asked for deeper Telegram analysis and 1:1 behavior alignment, not just superficial restyling.

## Metrics
- **Primary**: `telegram_parity_penalty` (unitless, lower is better)
  - Static parity score derived from the current code.
  - Penalizes:
    - extra dock/header controls beyond Telegram’s minimal left-close / title / right-expand structure
    - missing expand affordance in the preview header
    - overly modal browser-sheet entry motion constants that contribute to the current stiff feel
- **Secondary / diagnostics printed by benchmark**:
  - dock extra-icon penalty
  - manager header extra-icon penalty
  - transition dock extra-icon penalty
  - sheet motion penalty

## How to Run
`./autoresearch.sh`

The script outputs `METRIC telegram_parity_penalty=<number>`.

## Files in Scope
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsShell.kt`
  - collapsed dock, tabs manager overlay, preview header chrome
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsTransition.kt`
  - dock/manager transition preview surface
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
  - shell wiring, manager open/close orchestration
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
  - browser sheet entry/dismiss motion model
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserState.kt`
  - pure motion helpers and presentation state
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainerTest.kt`
  - sheet motion tests
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetMotionModelTest.kt`
  - presentation-state tests
- `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/BrowserTabsTransitionContractTest.kt`
  - transition contract tests

## Off Limits
- unrelated navigation, auth, search, or topic/profile features
- introducing new dependencies or DI frameworks
- changing browser runtime semantics unrelated to tabs-manager parity
- any benchmark cheating such as faking parity score without real source changes

## Constraints
- Do not overfit by gaming the benchmark without improving real Telegram parity.
- Keep verification through targeted Android unit tests.
- Preserve existing browser/session continuity behavior.
- Prefer small, reviewable changes.
- Work directly on `main` per user request.

## Telegram Findings To Preserve
- `BottomSheetTabs.click()` behavior:
  - 1 tab => direct reopen
  - multiple tabs => open overlay manager
- Dock tab chrome in Telegram is minimal and action-oriented:
  - left close `X`
  - centered title
  - right expand chevron
- `BottomSheetTabsOverlay.animateOpen()` uses a single overlay authority with 320ms ease-out-quint motion.
- Telegram’s smoothness comes from single-owner rendering during dock/manager transitions, not independent list item animations.

## What's Been Tried
- Initial analysis completed against:
  - Telegram `BottomSheetTabs.java`
  - Telegram `BottomSheetTabsOverlay.java`
  - Telegram `BotWebViewAttachedSheet.java`
- Current NativeDo gaps identified:
  - `BrowserTabsDockCard`, `BrowserTabsTransitionDockSurface`, and `BrowserTabsLargePreviewSurface` still render extra iconography compared with the requested minimal Telegram structure.
  - `AppBrowserSheetContainer` still uses a modal-feeling scale/alpha curve (`0.985 -> 1`, `0.92 -> 1`) that likely contributes to stiffness.
- First optimization direction:
  1. remove extra dock/header icons
  2. restore Telegram-like right expand affordance
  3. soften browser-sheet entry motion and attach feel
