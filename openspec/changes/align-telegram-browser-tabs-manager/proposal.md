## Why

NativeDo already has a browser-family minimize path and a multi-session dock, but the current result still reads as a custom overlay rather than Telegram's browser tabs system. The remaining parity gap is structural: the minimized dock does not participate in app-shell layout, the multi-tab view is a bottom list instead of a dedicated tabs manager, and minimize or restore motion is still a generic card interpolation instead of a live surface morph.

## What Changes

- Move the minimized browser-family dock from a floating `BrowserHost` overlay into an app-shell-owned bottom layer that animates real bottom spacing and visibly pushes root content upward.
- Replace the current bottom-aligned list overview with a Telegram-style tabs manager overlay that opens when multiple docked sessions exist and supports stacked previews, direct restore, single-tab close, horizontal swipe-to-dismiss, outside-dismiss, and a centered `Close All Tabs` pill.
- Upgrade minimize and restore transitions so browser and Instant View surfaces morph between the active surface, dock card, and tabs-manager card using shared geometry and live preview drawing instead of only fading or interpolating a generic card shell.
- Keep the existing browser-family session continuity model, but extend it so dock cards and tabs-manager cards are presentation views over the same live browser or reader sessions.
- Align browser-family collapse or close behavior with the new tabs manager contract: one docked session restores directly from the dock, multiple docked sessions open the tabs manager first, and destructive close remains separate from collapse.

## Capabilities

### New Capabilities
- `browser-tabs-manager`: Defines the Telegram-style app-shell dock, dedicated multi-tab manager overlay, live tab preview interactions, and dock-to-manager-to-surface transition expectations for minimized browser-family sessions.

### Modified Capabilities
- `browser-family-minimize-overlay`: Change minimize or restore behavior from an optional bottom-strip entry to a Telegram-style bottom dock and live morph transition system shared by browser and Instant View.
- `browser-scene-chrome`: Change browser-family collapse or close expectations so collapse targets the shell-owned dock and multi-session reopen behavior routes through the tabs manager contract.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoChromeScaffold.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/RootTabsHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserReaderScene.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
- Affected systems:
  - App-shell bottom inset and overlay ownership
  - Browser-family dock layout, transitions, and session presentation
  - Multi-tab restore or dismissal interactions
  - Browser and Instant View minimize or restore motion parity
