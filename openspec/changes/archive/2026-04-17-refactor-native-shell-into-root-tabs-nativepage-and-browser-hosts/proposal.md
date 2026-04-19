## Why

NativeDo 目前已经部分引入 Telegram 风格的转场和容器，但 root chrome、native pushed page、browser/full-screen browser、sheet browser 的 ownership 仍然分散在 `NativeDoApp`、`NativeDoNavHost`、`NativeDoNavigationLayout` 和各个 feature route 中。这个分层会持续制造顶部栏透出、重复返回逻辑、browser 与 native page 容器交叉等问题，因此需要先把 app shell 重构成清晰、稳定、可扩展的容器体系。

## What Changes

- Rebuild the Android app shell around explicit container roles: `AppRoot`, `RootTabsHost`, `NativePageStackHost`, and `BrowserHost`.
- Move root scene ownership down into root features so `Home` and `Profile` own their own header/background instead of relying on a global root top bar.
- Restrict the native pushed stack to native pages such as topic and profile detail, and keep predictive back / edge-swipe transitions centralized in the stack host.
- Move browser presentation ownership into a dedicated browser host that coordinates full-screen browser, sheet browser, and reader-style overlays instead of letting browser transitions live inside feature routes.
- Rename navigation/container types to match their responsibilities and remove generic or misleading names such as `NativeDoNavigationLayout` and `NativeDoBrowserOverlayHost`.
- Keep current feature business logic and screen UI mostly intact during the shell migration so the refactor stays incremental and verifiable.

## Capabilities

### New Capabilities
- `native-page-stack`: Defines the native pushed page stack above root tabs, including stack entries, host ownership, and Telegram-style back interactions for native pages.

### Modified Capabilities
- `root-contextual-chrome`: Root chrome ownership changes from a global app-level top bar to root-scene-owned chrome coordinated by the app shell.
- `browser-scene-chrome`: Browser chrome and browser container ownership change from mixed route/overlay responsibilities to a dedicated browser host coordinating all browser presentation modes.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoNavigationLayout.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBrowserOverlayHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/*`
- Affected systems:
  - Root shell composition and chrome ownership
  - Native page navigation and predictive back handling
  - Browser presentation routing and overlay coordination
  - Naming conventions for app shell, host, state, and entry types
