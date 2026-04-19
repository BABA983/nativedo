## Why

NativeDo already has browser address editing, but it still behaves more like a standalone input scene than a mature browser address surface: it mainly exposes recent links and a few cards, without mixed suggestions, recent searches, or richer quick actions. Since the browser chrome state machine is already in place, this is the right point to deepen address editing so it feels more useful and more Telegram-like without changing browser container architecture.

## What Changes

- Introduce a typed browser address-suggestion model that can merge recent links, recent searches, bookmarks, and quick browser actions into one address-editing surface.
- Add lightweight browser search-history persistence and feed it into address suggestions.
- Upgrade browser address editing so selecting a suggestion, bookmark, or quick action follows explicit browser semantics instead of ad-hoc scene-local behavior.
- Refine the address scene layout so preview, copy-link, bookmark, and suggestion sections behave as one cohesive address surface.

## Capabilities

### New Capabilities
- `browser-address-suggestions`: Defines the sources, presentation, and selection behavior for browser address suggestions and quick actions.

### Modified Capabilities
- `browser-scene-chrome`: Address editing requirements expand from simple scene switching to a richer address surface with browser suggestions and quick actions.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserViewModel.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserUiState.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/*`
- Affected systems:
  - Browser address-editing scene behavior and suggestion composition
  - Lightweight browser search-history persistence
  - Browser bookmark and history reuse inside address suggestions
