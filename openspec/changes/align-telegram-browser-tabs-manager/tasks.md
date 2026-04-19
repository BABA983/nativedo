## 1. Shell-Owned Dock

- [x] 1.1 Add app-shell presentation slots and animated bottom spacing for the browser tabs dock in `AppRoot` or `NativeDoChromeScaffold` while keeping `BrowserHost` as the browser-family state owner.
- [x] 1.2 Move the collapsed browser tabs dock out of the current `BrowserHost` floating overlay path and make it publish occupied height to the shell layout.
- [x] 1.3 Rewire collapsed-dock behavior so one docked session restores directly and multiple docked sessions open the tabs manager instead of restoring immediately.

## 2. Tabs Manager Overlay

- [x] 2.1 Replace the current bottom `LazyColumn` overview with a dedicated Telegram-style tabs manager overlay that renders latest-first stacked preview cards.
- [x] 2.2 Implement tabs-manager interactions: tap card to restore, tap outside to dismiss, per-card close, horizontal swipe-to-dismiss, and centered `Close All Tabs`.
- [x] 2.3 Remove the old dock overview implementation once the tabs manager owns multi-session browsing and dismissal.

## 3. Preview And Transition Pipeline

- [x] 3.1 Add a browser-family preview and geometry contract so active browser and Instant View surfaces can publish app-root-relative bounds and preview payloads for dock or manager presentation.
- [x] 3.2 Implement one app-root transition overlay that handles minimize to dock, direct restore from dock, manager open or close, restore from manager, and manager swipe-dismiss motion.
- [x] 3.3 Remove the old generic dock-transition overlay and route all browser tabs motion through the new shared transition pipeline.

## 4. Browser/Reader Integration And Verification

- [x] 4.1 Update browser and Instant View chrome actions so collapse targets the shell-owned dock and destructive close semantics stay distinct across active, docked, and manager states.
- [x] 4.2 Preserve browser history continuity, reader progress, and source-browser restoration when sessions move through dock restore, manager restore, card close, and close-all flows.
- [ ] 4.3 Verify dock layout behavior, tabs-manager interactions, minimize or restore motion, and browser or reader continuity with Android build or test coverage plus manual smoke testing of the Telegram-parity flows.

## 5. Telegram Rendering-Parity Follow-Up

- [ ] 5.1 Replace the current stacked `LazyColumn` tabs-manager implementation with an overlay-owned preview renderer so manager transforms, clipping, outside-dismiss, vertical browse, and horizontal swipe-dismiss share one rendering and gesture owner.
- [ ] 5.2 Add a browser-family live-surface transition contract so active browser and reader surfaces can draw through the app-root overlay during minimize and restore instead of relying only on captured preview bitmaps.
- [ ] 5.3 Keep preview snapshots as parked-state and fallback assets for dock and manager cards, but decouple them from runtime session restoration so session continuity does not depend on bitmap capture.
- [ ] 5.4 Re-verify Telegram parity after the rendering-contract work by checking dock behavior, manager gestures, live-surface morph quality, browser sheet continuity, and reader return-path continuity together in one manual smoke pass.
