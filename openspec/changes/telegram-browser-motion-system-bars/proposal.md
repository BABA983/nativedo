## Why

NativeDo 的 browser shell 已经进入 scene 化阶段，但动效、进度反馈、system bars 和 bookmark 仍停留在 v1 水位：入口/退出节奏偏慢、关闭手势过窄、progress 主要靠 fake tick、颜色与系统栏不跟随页面变化、bookmark 仍是占位。要继续向 Telegram 的 browser 体验靠近，下一步需要把“视觉结构已成形”的 browser 进一步做成“反馈和系统 chrome 都像 Telegram 一样利落”的 browser。

## What Changes

- 将 browser 的打开、关闭和右滑 dismiss 动效对齐到 Telegram 风格的短时长 `alpha + translationX` 节奏，并扩大或放开右滑关闭的触发区域。
- 将 browser 的进度反馈从“主要依赖 fake progress”升级为“真实进度驱动 + 平滑追赶”的 progress 表现，同时补齐搜索输入的 debounce。
- 为 browser 增加滚动收缩 header、页面 `theme-color` 颜色过渡，以及 scene 级 status/navigation bar 样式接管。
- 实现可持久化的 bookmark 能力，替换当前 address scene 中的 placeholder，并补齐 bookmark 动作与基础列表入口。
- 保持本 change 聚焦于 motion、feedback、system bars 和 bookmark，不引入 bottom sheet browser 或 Instant View 容器。

## Capabilities

### New Capabilities
- `browser-motion-feedback`: 定义 browser route 的进入/退出、dismiss 手势、progress 反馈、搜索 debounce 和滚动收缩 header 行为。
- `browser-system-bars`: 定义 browser scene 对状态栏、导航栏、页面主题色和 icon 明暗的控制规则。
- `browser-bookmarks`: 定义 browser 当前页的 bookmark 添加、移除、持久化与基础入口能力。

### Modified Capabilities
- None.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserState.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserWebView.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserViewModel.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoScreenStyle.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoSystemBarStyle.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/*`
- Affected systems:
  - browser route motion and gesture behavior
  - browser top bar progress and scroll behavior
  - scene-specific system bar ownership
  - local browser bookmark persistence
- No backend or API contract changes are expected.
