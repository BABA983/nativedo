## 1. Container Routing And Host Foundation

- [x] 1.1 Define browser presentation target types and the rules that choose between full route, sheet browser, and Instant View
- [x] 1.2 Add the root-level host or overlay structure needed to present browser sheet and reader scenes without breaking existing route navigation
- [x] 1.3 Keep full-route browser as the canonical container for auth-sensitive and complex WebView flows

## 2. Browser Sheet Container

- [x] 2.1 Create the dedicated sheet browser scaffold and presentation state model for open, dismiss, and fullscreen progress
- [x] 2.2 Reuse or adapt browser chrome so sheet browser preserves scene-state behavior without leaking route-only assumptions
- [x] 2.3 Implement drag-to-dismiss, partial or fullscreen transitions, and pull-to-refresh behavior for eligible sheet pages

## 3. Instant View Or Reader Scene

- [x] 3.1 Define the support rules that determine when Instant View is available for a loaded page
- [x] 3.2 Build a reader-scene owner on top of `NativeDoReaderScaffold` with open and close flow from browser
- [x] 3.3 Implement source restoration so closing Instant View returns to the originating browser or previous scene

## 4. Verification

- [x] 4.1 Add targeted tests for presentation target resolution and any pure state derivation introduced by the new containers
- [ ] 4.2 Manually smoke test full-route browser, sheet browser, fullscreen transition, drag dismissal, pull-to-refresh, Instant View entry gating, and reader return path
- [x] 4.3 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest` and `./apps/android/gradlew -p apps/android :app:assembleDebug` before implementation handoff
