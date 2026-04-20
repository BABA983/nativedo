## 1. State Model And Host Foundation

- [ ] 1.1 Introduce the new `BrowserTabState`, `BrowserSheetPresentationState`, `BrowserChromeState`, `BrowserUiState`, and `BrowserAction` models under `feature/browser`
- [ ] 1.2 Rewrite `ui/BrowserHost.kt` so it owns active browser-family sessions, docked sessions, reader follow-ups, and restore or close lifecycle
- [ ] 1.3 Rewire the browser entrypoint in `ui/NativeDoApp.kt` to the new host model without changing `LinkRouter`, cookie sync, or existing persistence stores

## 2. Web Runtime And Sheet Presentation

- [ ] 2.1 Replace `AppBrowserWebViewController` and `AppBrowserWebView` with `BrowserWebViewController` and `BrowserWebView` that support state save or restore, preview capture, browser-scoped callbacks, and edge-swipe back behavior
- [ ] 2.2 Implement `BrowserSheetDialog` and `BrowserSheet` with open, dismiss, fullscreen attach, nested scroll handoff, and gesture settle behavior
- [ ] 2.3 Add focused tests for sheet settle logic, tab restore state, and browser back-gesture branching

## 3. Chrome And Browser Panels

- [ ] 3.1 Build `BrowserActionBar` browse mode with adaptive page color handling, title or subtitle sync, progress, back or forward, and menu actions
- [ ] 3.2 Add `BrowserAddressBar` and `BrowserSearchBar` flows on top of the shared chrome scene state machine
- [ ] 3.3 Rebuild history, bookmarks, settings, and address suggestion panels against the new browser state and action model

## 4. Dock, Tabs Overlay, And Session Transitions

- [ ] 4.1 Implement `BrowserTabsDock` for minimized browser-family sessions with direct restore behavior for a single session
- [ ] 4.2 Implement `BrowserTabsOverlay` with multi-session preview, restore, swipe-to-dismiss, and close-all behavior
- [ ] 4.3 Add the sheet-to-dock and dock-to-sheet transition flow, including preview bitmap lifecycle and session identity preservation

## 5. Reader Scene And Route Assembly

- [ ] 5.1 Rewrite `BrowserReaderScene` as a browser-family follow-up surface with source-session restoration
- [ ] 5.2 Adapt Instant View loading or resolver integration and existing reader content models to the new browser host and session interfaces
- [ ] 5.3 Create the new `BrowserRoute` and `BrowserScreen` assembly that wires host, sheet, chrome, WebView, panels, downloads, file chooser, and permission flows end to end

## 6. Cleanup And Verification

- [ ] 6.1 Remove the legacy `AppBrowser*` implementation files after the new `Browser*` flow is fully wired
- [ ] 6.2 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- [ ] 6.3 Run `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet` and complete the manual browser rewrite smoke checklist
