## Why

NativeDo browser settings currently stop at history, cookies, and cache cleanup, while link routing still lacks user-configurable domain overrides or browser preferences. To get closer to Telegram’s browser behavior and give users control over how links open, the app needs persisted browser preferences such as never-open-in-app domains, default external link behavior, and configurable search-engine routing from the address surface.

## What Changes

- Add a persisted browser preference store wired through `AppContainer` for browser-scoped settings.
- Add support for a default external-link preference so browserable links can follow a user-controlled in-app or system-browser policy where appropriate.
- Add support for never-open-in-app domains that force matching links to bypass NativeDo browser and open externally.
- Add configurable search-engine preference used by browser address search submission when the input is treated as a search query.
- Expand the browser settings surface so users can inspect and modify these preferences inside the browser feature.

## Capabilities

### New Capabilities
- `browser-preferences`: Defines persisted browser preferences such as default external-link handling and address-search engine selection.
- `browser-restricted-domains`: Defines a user-managed domain override list that forces matching links to bypass the in-app browser.

### Modified Capabilities
- None.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/LinkOpenPolicy.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/ExternalAppLauncher.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSettingsPanel.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
- Affected systems:
  - Browser preference persistence and manual dependency wiring in `AppContainer`
  - Link routing overrides for external or system browser opening
  - Browser address search submission behavior
