## 1. App Shell Skeleton

- [x] 1.1 Introduce `AppRoot` shell wiring and extract app-level assembly state from `NativeDoApp`
- [x] 1.2 Add `RootTabsHost` and move root pager plus bottom-tab coordination into the host
- [x] 1.3 Rename the existing native pushed-stack types to `NativePageStackHost`, `NativePageStackState`, and `NativePageEntry` while preserving current transitions
- [x] 1.4 Add `BrowserHostState` and a dedicated `BrowserHost` wrapper around existing browser presentation entry points

## 2. Root Scene Ownership

- [x] 2.1 Move Home header, search entry, and sticky chrome ownership fully into the home scene
- [x] 2.2 Move Profile header ownership into the profile scene and keep title publication local to the profile feature
- [x] 2.3 Remove app-level root top bar retention logic so root chrome no longer remains active behind native pages
- [x] 2.4 Update root contextual options wiring so panels are scoped to the active root scene header

## 3. Native Page Stack Migration

- [x] 3.1 Remove browser entries from `NativePageStackHost` and keep only native page entries such as topic and profile detail
- [x] 3.2 Update topic and profile detail routes to publish host chrome data through the native page stack host contract
- [x] 3.3 Keep predictive back, left-edge swipe, and close animation ownership centralized in `NativePageStackHost`
- [x] 3.4 Verify back-priority ordering between browser overlays, native page stack, and global nav routes

## 4. Browser Host Migration

- [x] 4.1 Route full-screen browser opening through `BrowserHost` instead of the native page stack
- [x] 4.2 Route sheet browser and reader-style overlays through `BrowserHost` with a shared browser host state
- [x] 4.3 Remove full-screen browser presentation ownership from `AppBrowserRoute` and keep browser feature runtime plus chrome state local to the feature
- [x] 4.4 Ensure browser chrome behavior remains consistent across full-screen and sheet browser containers

## 5. Cleanup And Verification

- [x] 5.1 Rename remaining shell helpers, state holders, and overlay types to the new `Host / State / Entry / Route / Screen` taxonomy
- [x] 5.2 Split oversized shell files where necessary so app shell, root tabs, native page stack, and browser host code are independently readable
- [x] 5.3 Remove obsolete shell paths and duplicated ownership logic left from `NativeDoApp`, `NativeDoNavHost`, and the old browser overlay implementation
- [x] 5.4 Run targeted Android verification for navigation, browser, and root-scene regressions before closing the change
