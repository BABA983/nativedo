## Why

NativeDo 已经有自己的 glass chrome 方向，但 root/topic/browser 三处 top bar 还没有像 Telegram 那样形成统一的结构语义与交接边界。现在 topic 内打开链接进入 `AppBrowserRoute` 时，root top bar 会被隐藏，而 browser top bar 的接管时机与布局语义没有被明确建模，导致出现“顶部栏消失”的感知 bug，也让后续 1:1 对齐 Telegram 顶栏样式和元素排列缺少稳定基座。

## What Changes

- 对照 Telegram 的 `ActionBar`、`ActionBarMenuItem`、`ItemOptions` 和 browser-like scene 组织方式，明确 NativeDo top bar 的共享结构语义：左侧返回/关闭，中间标题容器，右侧搜索/更多动作，以及锚定弹层入口。
- 为 root、topic/reader、browser 三类 scene 建立一致的 top bar handoff 规则，保证 scene 切换时始终只有一个 top bar owner 可见，避免 topic 打开站内地址进入 app browser 时顶部栏空白。
- 收口 browser top bar 的显示责任，明确 `AppBrowserRoute` / `NativeDoBrowserScaffold` / `AppBrowserTopBar` 在 route 进入、退出、地址编辑、搜索和菜单状态下的可见性与层级。
- 将 Telegram 1:1 样式对齐限定在“布局、元素排列、信息密度、状态切换节奏”层面，不复制 Telegram 的 GPL 类结构或实现代码。
- 为后续继续推进更完整的 Telegram chrome 对齐提供一套可以扩展到 contextual menu、action mode 和 reader top bar 的 top bar contract。

## Capabilities

### New Capabilities
- `scene-topbar-contract`: 定义 root、reader/topic、browser 三类 scene 的 top bar ownership、可见性切换、元素布局与交接规则。
- `browser-topbar-handoff`: 定义从 topic/post 链接打开 app browser 时，browser top bar 的接管、显示与返回交互要求，避免 top bar 消失或双重渲染。

### Modified Capabilities
- None.

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoChromeScaffold.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBrowserScaffold.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserScreen.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
- Affected systems:
  - root navigation chrome
  - topic/reader header behavior
  - in-app browser shell and route transitions
  - shared popup / top bar motion semantics
- External reference systems investigated for behavior modeling only:
  - `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java`
  - `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarMenuItem.java`
  - Telegram screens such as `DialogsActivity` and `SettingsActivity`
- No backend or API contract changes are expected.
