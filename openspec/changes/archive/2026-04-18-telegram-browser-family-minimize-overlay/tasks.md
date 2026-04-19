## 1. Browser-Family Host State

- [x] 1.1 Replace `BrowserHost`'s current `sheetRequest` and dialog-style reader split with a browser-family host model that can represent one active entry and one minimized entry
- [x] 1.2 Move Instant View ownership into the browser-family host and wire active, minimized, restored, and closed transitions through the same host path
- [x] 1.3 Add or update host-level tests for browser-family open, minimize, restore, and true close behavior

## 2. Full-Screen Browser And Session Continuity

- [x] 2.1 Rework `AppBrowserSheetContainer` so the regular browser still opens bottom-up but rests only as a full-screen surface without persistent half-height geometry or browser scroll-collapse behavior
- [x] 2.2 Introduce stable browser-family entry identity so browser and reader runtime state survive minimize and restore instead of reopening from URL alone
- [x] 2.3 Add or update tests that verify restored browser history continuity and restored Instant View source or reading continuity

## 3. Chrome Actions And Minimized Bottom Strip

- [x] 3.1 Add separate minimize and close actions to browser and reader chrome, including overflow or top-bar action wiring where needed
- [x] 3.2 Implement the minimized bottom-strip entry with reopen and true close behavior for the single minimized browser-family slot
- [x] 3.3 Consolidate reader drag or settle ownership so restore and minimize transitions stop jittering, and ensure normal browser header interactions no longer drive reading-style progress changes

## 4. Verification

- [x] 4.1 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- [x] 4.2 Run `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- [x] 4.3 Manually smoke test browser open, browser minimize, browser restore, Instant View minimize, Instant View restore, and true close from both expanded and minimized states
