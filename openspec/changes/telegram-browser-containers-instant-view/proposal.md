## Why

当前 NativeDo browser 只有一种 full-screen route 容器，这对于登录、Cloudflare challenge 和复杂表单是稳定的，但距离 Telegram 的 browser 体系还差最关键的一层：sheet 型浏览器和 article/Instant View 型阅读容器。要继续向 Telegram 1:1 交互靠拢，必须把“browser 是一个页面”升级为“browser 是一组明确分工的内容容器”。

## What Changes

- 新增 bottom sheet 型 browser 容器，支持底部进入、向下关闭、半屏到全屏切换，以及 browser chrome 与 viewport 的联动。
- 为 browser 建立显式的 presentation target 规则，区分 full-screen route、sheet browser 和 Instant View / reader scene 的使用边界。
- 新增 Instant View / reader scene，用于在受支持页面上从 browser 切入更轻、更密、更像 Telegram ArticleViewer 的阅读体验。
- 在 sheet browser 内补齐与容器语义一致的手势和刷新能力，例如 drag-to-dismiss、可选 pull-to-refresh 与 fullscreen transition。
- 保持当前 full-screen route browser 作为 auth-sensitive 和复杂 Web 流程的稳定承载，不被 sheet 替代。

## Capabilities

### New Capabilities
- `browser-container-routing`: 定义 browser 内容在 full-screen route、sheet browser 与 Instant View / reader scene 之间的分发规则。
- `browser-sheet-container`: 定义 sheet 型 browser 的展示、关闭、半屏/全屏切换和容器级交互。
- `instant-view-reader`: 定义受支持页面的 Instant View / reader scene、入口条件与返回路径。

### Modified Capabilities
- None.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoReaderScaffold.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/*Sheet*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/*`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/*`
- Affected systems:
  - root overlay / scene host responsibilities
  - browser container selection rules
  - browser chrome reuse across multiple containers
  - reader scene ownership and return path
- Potential new local dependency area:
  - reader extraction or readability pipeline if Instant View requires local article extraction
- No backend contract change is required for the initial proposal.
