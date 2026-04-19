## Why

NativeDo already has separate route, sheet, and reader browser building blocks, but the overall HTTP browsing experience still does not match Telegram’s regular browser family. Telegram’s Android browser does not present ordinary browsing and complex web flows as two different in-app browser containers. Instead, it keeps browserable web flows inside one bottom-up browser surface, while Instant View or reader remains a distinct right-to-left follow-up scene. NativeDo currently splits that responsibility across `FullRoute`, `Sheet`, and reader targets, which is why sibling subdomain hops, auth redirects, and challenge-heavy flows can still feel like container switches instead of one continuous browser session.

Now that browser runtime hardening, preferences, and address suggestions have landed, the next step is to realign the browser family around a single regular browser container plus reader follow-up semantics instead of continuing to evolve multiple in-app browser containers in parallel.

## What Changes

- Collapse the in-app browser family to one regular browser container plus Instant View or reader follow-up semantics, removing `FullRoute` as a separate browser presentation target.
- Keep auth-sensitive, challenge-heavy, upload-capable, redirect-heavy, and sibling-subdomain web flows inside the same regular browser container instead of promoting them into another in-app browser scene.
- Align browser open or close and dismiss motion with Telegram’s regular HTTP browser family: bottom-up open for the main browser container, full-size resting state for that container, downward dismissal from that container, and right-to-left reader or Instant View transitions.
- Continue aligning browser chrome, address, history, bookmarks, settings, and menu surfaces with Telegram’s regular browser interactions, while preserving NativeDo’s Compose architecture and design system.
- Reuse the shared browser runtime for popup handling, file chooser, permissions, SSL recovery, downloads, and error handling without tying those capabilities to a separate browser presentation container.

## Capabilities

### New Capabilities
- `telegram-http-browser-alignment`: Defines Telegram-like container semantics, transitions, and interaction expectations for the regular HTTP browser family in NativeDo.

### Modified Capabilities
- `browser-container-routing`: Refine how browserable URLs choose between the regular browser container and Instant View or reader target inside the browser family.
- `browser-sheet-container`: Repurpose the existing bottom-up browser host so it becomes the only regular in-app browser container and settles as a full-size scene.
- `instant-view-reader`: Refine reader transition and source-restoration behavior so Instant View behaves like a distinct right-to-left follow-up scene.
- `browser-scene-chrome`: Extend browser chrome expectations so browser address, history, bookmarks, settings, and overflow behavior remain coherent after the browser-family simplification.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/AppBrowserPresentationPolicy.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/LinkOpenPolicy.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserReaderScene.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/*History*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/*Bookmarks*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/*Settings*`
- Affected systems:
  - Browser presentation policy and overlay host ownership
  - Browser and reader transition model
  - Browser runtime continuity across redirects, auth, challenge, upload, and sibling-subdomain flows
  - Browser chrome behavior after container realignment
