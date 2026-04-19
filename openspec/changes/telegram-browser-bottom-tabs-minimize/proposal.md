## Why

NativeDo now has a browser-family minimize path, but it still ends in a single bottom strip entry instead of Telegram's bottom tabs dock. That leaves minimize, restore, close, and multi-session behavior visibly different from Telegram even after the browser itself opens full-screen from the bottom.

## What Changes

- Replace the current single minimized bottom strip entry with a Telegram-style bottom tabs dock that stays attached to the bottom of the app shell.
- Let minimized browser-family sessions accumulate in that dock as dock entries instead of collapsing all minimize state into one shared strip item.
- Support Telegram-style dock interactions: tap to reopen the latest minimized entry, expand into a dock overview when multiple entries exist, and close docked entries without reopening them first.
- Route regular browser and Instant View minimize flows through the same dock-backed session model so both surfaces use the same minimize, restore, and close system.
- Update browser-family host and chrome contracts so minimize restores the same live session from the dock while `X` remains true close.

## Capabilities

### New Capabilities
- `browser-bottom-tabs-dock`: Defines the bottom dock, dock entry model, single-entry and multi-entry presentation, and reopen or close interactions for minimized browser-family sessions.

### Modified Capabilities
- `browser-family-minimize-overlay`: Browser-family minimize and restore requirements change from a single bottom strip entry to a dock-backed bottom tabs system shared by browser and Instant View.
- `browser-scene-chrome`: Browser-family chrome requirements change so collapse actions target the bottom tabs dock and close remains a separate destructive action for active or docked entries.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/RootTabsHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserReaderScene.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserAction.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserState.kt`
- Affected systems:
  - Browser-family host ownership and minimized session storage
  - Bottom dock overlay layout, animation, and gesture handoff
  - Browser and Instant View minimize, restore, and true close semantics
  - Restorable browser-family session continuity across multiple docked entries
