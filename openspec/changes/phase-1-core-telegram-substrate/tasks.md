## 1. Tighten Telegram-Derived Substrate

- [x] 1.1 Re-verify the Phase 1 Telegram-derived files against the approved GPL header template and current approved divergence register
- [x] 1.2 Remove low-value standalone substrate tests and keep only explicitly justified parity or regression tests
- [x] 1.3 Trim or freeze any no-caller public surface in `LayoutHelper.kt`, `AnimatedFloat.kt`, and `AnimatedColor.kt` instead of expanding it for parity completeness

## 2. Finalize NativeDo Runtime Substrate

- [x] 2.1 Audit `NdTheme.kt`, `LightTheme.kt`, `DarkTheme.kt`, `NdUiConfig.kt`, and `AppLifecycleMonitor.kt` against the Phase 1 runtime gate and fix any missing runtime behavior
- [x] 2.2 Ensure the retained runtime substrate stays project-specific and does not introduce Telegram-derived naming or helper drift

## 3. Verify Phase 1 Baseline

- [x] 3.1 Run focused unit tests that remain after substrate test cleanup
- [x] 3.2 Run `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet` to confirm the Phase 1 substrate still builds
