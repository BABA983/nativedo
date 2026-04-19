## 1. Browser-Family Session Model

- [x] 1.1 Replace the single minimized-strip state in `BrowserHost` with an active session plus ordered docked-session model.
- [x] 1.2 Add stable browser-family session identities and keep browser or reader runtime holders attached to those sessions while docked.
- [x] 1.3 Update minimize, restore, and true-close actions so they target docked sessions instead of the old single-entry strip contract.

## 2. Bottom Dock UI

- [x] 2.1 Implement the collapsed bottom dock in the app shell with single-session direct reopen and multi-session overview entry behavior.
- [x] 2.2 Implement the expanded dock overview with latest-first browsing, tap-to-restore, tap-to-close, and overview dismissal.
- [x] 2.3 Add the shared overlay transition layer that animates active browser-family surfaces into and out of dock cards.

## 3. Browser And Reader Integration

- [x] 3.1 Add visible collapse and close actions to regular browser and Instant View chrome, keeping collapse and destructive close distinct.
- [x] 3.2 Move browser and reader containers onto the dock-backed host flow and publish the geometry or preview state needed for dock animations.
- [x] 3.3 Preserve browser history continuity, reader progress, and browser-to-reader return paths when sessions are restored from the dock.

## 4. Verification

- [x] 4.1 Verify the single-session path: open browser, minimize into the dock, restore it, and close it without leaving stale dock UI.
- [x] 4.2 Verify the multi-session path: dock multiple browser or reader sessions, open the overview, restore one entry, and close another while the remaining dock state stays correct.
- [x] 4.3 Run Android verification for the browser-family changes and confirm the docked minimize flows build cleanly.
