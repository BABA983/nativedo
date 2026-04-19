## Why

NativeDo's current browser family still behaves like a dismissible sheet plus a separate reader dialog, which does not match Telegram's full-screen bottom-up browser or its minimize-to-bottom behavior. The next gap is no longer simple motion tuning: the app needs one browser-family host that can open full-screen from the bottom, minimize into a bottom strip, and later restore the same browser or Instant View session instead of always closing it.

## What Changes

- Add a Telegram-like browser-family minimize overlay so the regular browser and Instant View can move between `expanded`, `minimized`, and `closed` states.
- Keep regular browser open motion bottom-up but make its visible resting state full-screen instead of sheet-like half height.
- Separate `Minimize` from true `Close` in browser and reader chrome so tapping `X` closes, while the dedicated collapse action minimizes into a bottom strip.
- Preserve active browser or reader session state when minimized, so restoring from the bottom strip resumes the same page, history, and reading position instead of reopening a fresh route.
- Move reader ownership into the same browser-family host instead of treating Instant View as an unrelated dialog-level surface.

## Capabilities

### New Capabilities
- `browser-family-minimize-overlay`: Defines the shared browser-family host states, full-screen bottom-up presentation, bottom-strip minimization, and session restoration for browser and Instant View surfaces.

### Modified Capabilities
- `browser-scene-chrome`: Browser and reader chrome requirements change so minimize and close are distinct actions and chrome stays coherent while a browser-family entry is active, minimized, restored, or fully closed.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserAction.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserReaderScene.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserState.kt`
- Affected systems:
  - Browser-family host ownership and overlay lifecycle
  - Browser and Instant View entry, minimize, restore, and close transitions
  - Browser chrome action model and overflow menu semantics
  - Session continuity while browser or reader is minimized
