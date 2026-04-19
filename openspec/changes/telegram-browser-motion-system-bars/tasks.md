## 1. Motion And Feedback Foundation

- [x] 1.1 Refactor browser route presentation state so enter, exit, and swipe dismiss drive layered alpha and translation feedback instead of only route offset
- [x] 1.2 Update dismiss gesture handling to support the chosen wider or full-width trigger region and keep adaptive settle timing
- [x] 1.3 Rework browser loading progress and find-in-page updates so progress animation is smooth and search dispatch is debounced
- [x] 1.4 Add raw scroll delta tracking and compact header state derivation for browser top bar collapse

## 2. Browser System Bars And Theme Color

- [x] 2.1 Introduce browser-local screen style wiring so browser route can override system bar colors and icon appearance without leaking to other scenes
- [x] 2.2 Extract or resolve page theme color and blend it into browser chrome and navigation bar presentation with fallback behavior
- [x] 2.3 Verify browser open, close, dismiss, scroll, and page transitions restore the correct prior scene system bar style

## 3. Browser Bookmark Capability

- [x] 3.1 Create bookmark persistence model and store separate from browser history
- [x] 3.2 Replace bookmark placeholder UI with real add or remove actions in browser chrome surfaces
- [x] 3.3 Add a minimal bookmark list surface and reopen flow for saved pages

## 4. Verification

- [x] 4.1 Add or update targeted tests for bookmark persistence and browser state derivation where practical
- [ ] 4.2 Manually smoke test browser open or close, swipe dismiss, progress behavior, search debounce, scroll collapse, theme-color fallback, and bookmark add or remove flows
- [x] 4.3 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest` and `./apps/android/gradlew -p apps/android :app:assembleDebug` before implementation handoff
