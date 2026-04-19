## 1. Browser Container Model Simplification

- [x] 1.1 Remove `FullRoute` from the browser-family presentation policy so browserable HTTP requests resolve only to the regular browser container or Instant View
- [x] 1.2 Collapse `BrowserHost` ownership and root browser open paths so the in-app browser family no longer mounts a separate full-route browser scene
- [x] 1.3 Add or update tests that verify browser-versus-Instant View target selection and the absence of in-browser promotion to `FullRoute`

## 2. Telegram-Like Regular Browser Container Behavior

- [x] 2.1 Tighten `AppBrowserSheetContainer` so the regular HTTP browser opens bottom-up, settles full-size, and dismisses downward without a persistent half-height browser state
- [x] 2.2 Remove redirect or complex-flow container promotion so auth, challenge, upload, popup, and sibling-subdomain flows remain in the same browser session
- [x] 2.3 Ensure browser runtime hardening, address suggestions, bookmarks, history, settings, and browser chrome continue to work coherently inside the single regular browser container
- [x] 2.4 Replace separate browser open, dismiss, and fullscreen tweens with one browser motion owner or coordinate model that drives drag, settle, anchor changes, and restore semantics together

## 3. Reader Or Instant View Transition Alignment

- [x] 3.1 Rework browser-to-reader entry so supported pages open Instant View as a distinct right-to-left follow-up scene with stable source restoration
- [x] 3.2 Revalidate reader close behavior so it returns ownership cleanly to the source browser scene without replaying the regular browser open animation
- [x] 3.3 Consolidate reader drag and settle ownership so downward drag, release, and restore transitions no longer jitter between multiple gesture owners
- [x] 3.4 Add or update tests for reader support gating and browser-to-reader transition or restoration behavior
- [x] 3.5 Rework browser and reader top-attachment behavior around one attached-progress signal so radius, handle, header background, and top inset changes stay physically continuous

## 4. Chrome Surface Audit And Verification

- [x] 4.1 Audit browser address, history, bookmarks, settings, overflow, and back-history behavior against the single-browser-container semantics and remove any full-route-only assumptions
- [x] 4.2 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet` and `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- [ ] 4.3 Manually smoke test bottom-up full-size browser opens, sibling-subdomain redirects staying in-place, auth or challenge flows staying in the same browser, and right-to-left Instant View transitions before implementation handoff
- [x] 4.4 Revalidate active browser and reader transitions against a live-surface or draw-through contract, keeping snapshots only as fallback or parked-state presentation
