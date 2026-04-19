# Autoresearch: Browser tabs transition fidelity

## Objective
Improve shared transition fidelity for browser tabs minimize/restore flows.

This round focuses on the app-root transition overlay itself:
- active live-surface transitions should not dim the browser/reader content during minimize/restore
- `CloseManager` overlay timing should match the tabs-manager close cadence, avoiding geometry/motion mismatch between manager content and transition overlay

## Metrics
- **Primary**: `browser_tabs_transition_fidelity_penalty` (unitless, lower is better)
  - Penalizes:
    - `CloseManager` transition overlay duration shorter than manager close duration
    - transitions involving `Active` surface still using reduced alpha instead of full-opacity live-surface fidelity
- **Secondary**:
  - close-duration mismatch penalty
  - active-surface alpha penalty

## How to Run
`./autoresearch.sh`

## Files in Scope
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsTransition.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/BrowserTabsTransitionContractTest.kt`

## Constraints
- Preserve all previous keeps.
- Do not rework unrelated browser runtime logic.
- Prefer pure helper changes with tests first.

## Telegram Findings To Preserve
- Shared transition should read as one continuous surface when active browser/reader content participates.
- Overlay timing should be coherent across dock, manager, and transition owner.

## What's Been Tried
- `902be67`: Telegram-like dock/header chrome and lighter browser-sheet entry motion.
- `f23619c`: tabs-manager open/close continuity tuning.
- `1d2f490`: restored browser sessions bypass fresh bottom-up entry path.
- `64f31a9`: restore reactivation key plus tiny in-place settle.
- Remaining gap for this round:
  - transition overlay still uses `CloseManager -> 220ms` while manager close itself is 320ms
  - transition frame alpha logic still dims active-surface minimize/restore instead of preserving full-opacity live-surface continuity
