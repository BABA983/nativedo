## Why

NativeDo 已经建立了 Telegram 对齐的视觉方向，但顶栏、右上角菜单、浏览器 chrome 和图片查看器 chrome 仍然按各自方式演进，缺少 Telegram 那种“同一套基础能力、不同场景各自接管状态机”的一致交互层。现在补齐这层，可以避免 root/browser/media viewer 继续分叉，也为后续首页多选 action mode、浏览器历史面板和沉浸式 viewer 行为打下稳定边界。

## What Changes

- 为 root/home 顶栏引入 Telegram 风格的 contextual chrome，区分普通态、搜索态、action mode 和 anchored options panel。
- 重新明确浏览器 chrome 的状态机，覆盖 browse、search、address editing、history popup、overflow menu 等 scene，而不是把它退化成普通顶栏 slot。
- 将图片查看器 chrome 明确为 immersive overlay，补齐与 Telegram 一致的 auto-hide、menu suspend、top/bottom chrome 联动显隐要求。
- 收口 shared chrome primitive，包括 back/close morph、anchor highlight、anchored popup reveal/cascade motion、menu tone 和 scene-level visibility orchestration。

## Capabilities

### New Capabilities
- `root-contextual-chrome`: 定义首页/列表页顶栏在普通态、搜索态、action mode 和右上角 contextual options 下的行为与切换规则。
- `browser-scene-chrome`: 定义浏览器容器顶栏在浏览、搜索、地址编辑、历史与菜单场景下的结构、状态和交互。
- `immersive-viewer-chrome`: 定义图片查看器的沉浸式 overlay chrome、自动隐藏逻辑和右上角菜单行为。

### Modified Capabilities

None.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoChromeScaffold.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBrowserScaffold.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoPopupMenu.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/mediaviewer/*`
- Affected systems:
  - root navigation chrome
  - browser shell
  - media viewer shell
  - shared popup / motion primitives
- No API or backend contract changes are expected.
