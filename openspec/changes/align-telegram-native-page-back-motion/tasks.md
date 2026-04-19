## 1. Telegram back-motion reference capture

- [x] 1.1 Re-audit the Telegram source points that define pushed-page standard close and predictive back, including `ActionBarLayout.closeLastFragment()`, `startLayoutAnimation(false, ...)`, `onBackStarted()`, `onBackProgress()`, `animateBackEndAnimation(...)`, and `drawChild()`
- [x] 1.2 Record the exact Telegram timing, distance, pivot, reveal, shadow, and clipping signals that NativeDo must mirror for native page back motion

## 2. Standard close alignment

- [x] 2.1 Split `NativePageStackHost` non-interactive close from the current interactive-back state path
- [x] 2.2 Align pushed top bar back and system ordinary back to Telegram standard close semantics, including the `48dp` close family, timing/easing, and host-owned previous-scene restore
- [x] 2.3 Ensure `TopicRoute` and `ProfileRoute` remain content owners only and do not regain local back-animation responsibility

## 3. Interactive predictive-back alignment

- [x] 3.1 Unify Android predictive back and manual left-edge drag under a single interactive back renderer in `NativePageStackHost`
- [x] 3.2 Replace manual raw-`dx` rendering semantics with a normalized progress model that matches Telegram predictive back
- [x] 3.3 Align interactive back details to Telegram, including max gesture-era visual translation, edge-aware pivotX, touchY-driven pivotY, host-owned scale, clipping, shadow, cancel timing, and completion timing
- [x] 3.4 Remove or quarantine stale pushed-page dependencies on `TelegramStyleScaffold` semantics so `NativePageStackHost` becomes the only source of truth for native page back motion

## 4. Verification

- [x] 4.1 Add or update targeted tests around `NativePageStackHost` close-state selection, progress normalization, and Telegram motion parameters where practical in the current test setup
- [ ] 4.2 Manually compare NativeDo pushed-page back motion against Telegram for top bar back, predictive back cancel, predictive back complete, and manual left-edge drag
- [ ] 4.3 Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet` and `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet` before implementation handoff is considered ready
  Verification note: `:app:assembleDebug --quiet` passed on 2026-04-17. `:app:testDebugUnitTest --quiet` ran on 2026-04-17 but the suite still has 6 unrelated failures in `AppBrowserInstantViewResolverTest`, so this task remains open.
