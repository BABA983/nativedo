## 1. Telegram top bar reference audit

- [x] 1.1 Read and summarize the Telegram reference points that drive NativeDo top bar alignment, including `ActionBar`, `ActionBarMenuItem`, and representative screens such as `DialogsActivity` and `SettingsActivity`
- [x] 1.2 Record the specific layout and behavior signals to mirror in NativeDo, including leading/title/trailing geometry, overlay title behavior, search expansion, and anchored options organization

## 2. Scene top bar ownership contract

- [x] 2.1 Define the top bar owner matrix for root, topic/reader, and browser scenes in NativeDo so only one scene owns the visible top bar at a time
- [x] 2.2 Refactor `NativeDoApp` route-based top bar visibility logic so `APP_BROWSER_ROUTE` hands ownership to browser chrome instead of simply clearing the root top bar slot
- [x] 2.3 Ensure topic and other reader-style routes participate in the same ownership model without breaking existing back navigation or title mapping

## 3. Browser top bar handoff and shell behavior

- [x] 3.1 Update `AppBrowserRoute`, `AppBrowserScreen`, and `NativeDoBrowserScaffold` so browser chrome is visible from route entry and remains the active owner through initial page load
- [x] 3.2 Fix the topic-to-browser transition so opening a link from a topic never shows a blank top bar state and never renders two competing top bars
- [x] 3.3 Ensure browser close and route dismissal restore the previous scene top bar cleanly on return
- [ ] 3.4 Rework browser chrome into a single motion owner so title, subtitle, address edit, search, background, and divider transitions are coordinated instead of animated independently

## 4. Telegram-aligned browser top bar structure

- [x] 4.1 Align browser top bar layout to Telegram-style geometry with a leading navigation region, central title container, and trailing action anchor
- [x] 4.2 Keep browser browse, search, address editing, history popup, overflow menu, and subpanel scenes mutually exclusive in the top bar presentation
- [x] 4.3 Ensure overflow menu and history popup remain anchored to the top bar controls and that back-versus-close semantics follow browser scene state and WebView history availability
- [ ] 4.4 Add attach/fullscreen geometry handling so browser chrome can transition between resting, attached, and fullscreen states without devolving into simple fade or visibility swaps

## 5. Verification

- [x] 5.1 Add or update targeted tests for route ownership or browser scene behavior where practical in the current Android test setup
- [ ] 5.2 Manually smoke test root → topic → app browser transitions, browser dismissal back to topic/root, browser search/address/menu scene switching, and browser attach/fullscreen top-bar continuity
- [x] 5.3 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest` and `./apps/android/gradlew -p apps/android :app:assembleDebug` before implementation handoff is considered ready
