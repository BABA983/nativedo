## 1. Rework Navigation Motion Contract

- [x] 1.1 Rework `ActionBarLayout.kt` so animated push/pop and swipe-back match the adopted Telegram motion contract, including previous-page left shift, `35%` dismiss threshold, and velocity dismiss behavior
- [x] 1.2 Ensure `ActionBarLayout.kt` keeps fragment lifecycle hooks, transition hooks, and host system-bar ownership in sync during push/pop/swipe transitions
- [x] 1.3 Add minimal navigation stack save/restore and wire `MainActivity.kt` / `LaunchBootstrap.kt` so restored stacks do not duplicate the bootstrap root

## 2. Rework ActionBar Structure

- [x] 2.1 Add the adopted `SimpleTextView.kt` subset needed by `ActionBar.kt` title and subtitle rendering
- [x] 2.2 Rework `ActionBar.kt` to use Telegram-like back/title/subtitle/right-area structure instead of the current `TextView` placeholder layout and helper drift
- [x] 2.3 Make the current Phase 2 menu/search boundary explicit in `ActionBar.kt` and adjacent classes, keeping unsupported behavior absent instead of placeholder-backed

## 3. Verify Phase 2 Baseline

- [x] 3.1 Expand focused tests under `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/actionbar/` to cover navigation motion/restore and the adopted ActionBar structure
- [x] 3.2 Run focused ActionBar/navigation unit tests
- [x] 3.3 Run `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet` to confirm the Phase 2 navigation/actionbar baseline still builds
