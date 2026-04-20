## Why

当前 browser 实现把路由、WebView、chrome、reader、tabs 和子面板长期堆在一组 `AppBrowser*` 文件里，规模已经超过一万行且存在明确 bug，继续增量修补只会放大状态耦合和交互回归风险。既然项目已经明确要以 Telegram Android browser 为参考，对 browser 做一次完整重写并把后续 browser 工作统一到同一套架构下，比继续在旧实现上叠加 change 更合理。

## What Changes

- 以新的 `Browser*` 体系整体替换现有 `AppBrowser*` browser 实现，对齐 Telegram 的 sheet、dialog、action bar、tabs dock、tabs overlay 和 reader scene 分层。
- 重写 `BrowserHost`，让 browser family 由显式 session、docked tabs、restore 流程和 reader follow-up 协调，而不是继续依赖当前分散的 route 和 shell 逻辑。
- 重建 browser chrome、WebView runtime、tab snapshot、address/search/menu/history/bookmarks/settings 面板，以及 sheet 与 dock 之间的过渡动画。
- 保留现有 `LinkRouter`、cookie 同步、history/bookmark/search 持久化、reader 内容模型和主题基座，只改 browser feature 与 host 的接入方式。
- 删除旧的 `AppBrowser*` 实现文件，新的 `BrowserRoute`、`BrowserScreen` 和相关容器接管 browser 流程。
- **BREAKING** regular browser 的承载与最小化语义将从当前 full-screen route / bottom strip 假设，切换为 Telegram 风格的 sheet、dock 和 tabs overlay 行为。

## Capabilities

### New Capabilities
- `browser-sheet-presentation`: 定义 regular browser 的 Telegram 风格 sheet 容器、打开/关闭/拖拽/fullscreen 过渡和 dialog 承载。
- `browser-tabs-manager`: 定义 browser family 的 docked tabs、全屏 tabs overlay、标签恢复与销毁行为。
- `browser-web-runtime`: 定义 browser WebView 的运行时配置、页面状态同步、session snapshot、preview capture 和 edge-swipe back 行为。
- `browser-reader-scene`: 定义 Instant View / reader scene 的展示、返回路径和与 browser family 的联动。

### Modified Capabilities
- `browser-scene-chrome`: browser chrome requirement 改为 Telegram 风格 action bar，包含 browse/search/address editing/history/overflow/menu 同步和页面色彩自适应。
- `browser-family-minimize-overlay`: regular browser requirement 改为 sheet-based browser family、dock minimize/restore、多标签恢复和与 reader 的共享宿主模型。

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabs*.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/*`
- Affected systems:
  - browser family host and session lifecycle
  - browser chrome and subpanel state model
  - WebView runtime and session restore
  - dock, tabs overlay, and reader follow-up ownership
  - browser theme, progress, and transition behavior
- No backend or Discourse contract change is required for this rewrite.
