## Context

NativeDo 目前的链接打开链路是：`TopicRoute` / post body 内部点击链接 → `onOpenLink(url)` → `NativeDoApp` 中的统一 link router → `buildAppBrowserRoute()` → `NativeDoNavHost` 装配 `AppBrowserRoute`。在 root 装配层里，`NativeDoApp` 会在 `currentRoute == APP_BROWSER_ROUTE` 时直接把 `NativeDoChromeScaffold` 的 `topBar` 置空，而 browser 场景再通过 `AppBrowserScreen -> NativeDoBrowserScaffold -> AppBrowserTopBar` 自己绘制顶栏。

这意味着当前 top bar handoff 的核心规则是“root top bar 先消失，再期待 browser top bar 自己接上”。一旦 browser route 的顶栏装配、过渡动画或层级出现任何延迟，用户就会看到 topic 打开地址后顶部栏空白。这个问题本质上不是单个 composable 的显示 bug，而是缺少 scene-level top bar ownership contract。

Telegram 的实现给了一个稳定参考：`ActionBar` 是跟随 fragment / screen 的 scene owner，而不是一个全局万能 top bar；标题容器、返回按钮、右侧菜单、搜索展开和 contextual options 都围绕这个 scene owner 组织。`ActionBar#createMenu()`、`ActionBarMenuItem#setIsSearchField()`、`setAllowOverlayTitle(true)`、`setUseContainerForTitles()`，以及 `ItemOptions.makeOptions(...)` / `ActionBarPopupWindow` 说明 Telegram 的重点是“统一结构 + 场景接管”，而不是所有页面共用一个同构 slot bar。

这个 change 需要在不复制 GPL 代码的前提下，把 Telegram 的布局语义、元素排列和 top bar ownership 复刻到 NativeDo 的 Compose 架构中，同时保留现有 `NativeDoTheme`、`NativeDoGlassSpec`、`NativeDoChromeScaffold`、`NativeDoBrowserScaffold` 与单模块结构。

## Goals / Non-Goals

**Goals:**
- 修复 topic/post 打开 app browser 时 top bar 消失或交接空白的问题。
- 为 root、topic/reader、browser 三类 scene 建立显式的 top bar ownership contract，保证任意时刻只有一个 top bar owner。
- 让 NativeDo top bar 在布局、元素排列、信息密度和状态切换节奏上尽量贴近 Telegram。
- 保持 browser 继续作为 dedicated scene chrome，而不是退回普通 `TopBar + DropdownMenu`。
- 让后续 contextual options、action mode、reader header 和 browser chrome 能在同一结构语义上继续演进。

**Non-Goals:**
- 不复制 Telegram 的 `ActionBar`、`ActionBarMenuItem`、`ItemOptions` 或相关 Java 代码。
- 不在本次 change 里处理 media viewer chrome、底栏体系或真实 blur substrate。
- 不要求一次性把所有页面的 header 都升级到最终形态；首批只覆盖 root/topic/browser 的 top bar contract 与 browser handoff。
- 不改动后端、导航协议、登录态 contract 或 WebView 核心能力边界。

## Decisions

### 1. Top bar ownership 采用 scene owner 模型，而不是继续用 root 侧布尔隐藏

NativeDo 将把 top bar 的显示责任建模为 scene owner：至少区分 `RootTopBarOwner`、`ReaderTopBarOwner`、`BrowserTopBarOwner` 这三类语义 owner。`APP_BROWSER_ROUTE` 激活时，系统不能进入“没有 top bar owner”的中间状态；browser route 必须成为唯一可见 owner。

原因：
- Telegram 的 `ActionBar` 是每个 fragment / scene 自己拥有，而不是 root 先把全局 bar 删掉再等待下一个界面补上。
- 当前 `NativeDoApp` 里的 `if (!isAppBrowserRoute) topBar else null` 过于粗糙，只表达“root bar 不画了”，没有表达“谁来接管”。

替代方案：
- 保持现在的 root gating 逻辑，仅在 browser 侧补动画或 z-order。
  - 放弃原因：问题会继续表现为时序 bug，owner 关系仍不明确。
- 做一个单例全局 top bar，根据 route 填不同 slot。
  - 放弃原因：会把 browser address/search/history/menu 这些 scene state 压扁成 flag，偏离 Telegram 的场景接管模式。

### 2. 共享的是 top bar 几何语义，不是万能 ActionBar 组件

NativeDo 将统一 top bar 的结构语义：
- 左侧 leading navigation slot
- 中央标题容器 / overlay title 容器
- 右侧 trailing actions / options anchor
- 背景 / 材质层与内容层分离

但 root、reader/topic、browser 仍保留各自 scene-specific shell 与状态映射。

原因：
- Telegram 的不同页面虽然视觉上高度一致，但不是把所有行为塞进一个万能 ActionBar 组件；它统一的是几何与行为范式。
- 当前 NativeDo 已有 `NativeDoChromeScaffold`、`NativeDoBrowserScaffold`，适合承载“共享结构 + 场景专属状态机”的分层。

替代方案：
- 每个 scene 完全各写各的 top bar。
  - 放弃原因：很难做到 1:1 对齐 Telegram 的一致排列，也不利于修 bug。
- 完整克隆 Telegram ActionBar API。
  - 放弃原因：既不必要，也有 GPL 风险，还会与 Compose 架构冲突。

### 3. Browser top bar 必须作为 route shell 的一部分从首帧接管

`AppBrowserRoute` / `AppBrowserScreen` / `NativeDoBrowserScaffold` 将被视为 browser scene 的完整 chrome owner。浏览器 route 一旦开始进入，必须有 browser top bar 的可见承载；不能出现 root 顶栏已经撤掉、browser 顶栏尚未建立的空白阶段。

关键要求：
- route 进入时 browser top bar 立即可见或被确定性地过渡到可见
- route 退出时 previous scene top bar 明确恢复
- browser browse/search/address/history/overflow 继续保持互斥 scene

原因：
- 用户报告的 bug 正发生在 topic → browser 的 handoff 边界。
- Telegram 的 screen-owned ActionBar 模式天然避免这种“全局 bar 先消失”的空窗。

替代方案：
- 保持 browser top bar 完全 feature-local，不纳入 handoff 设计。
  - 放弃原因：bug 只会在另一个层级继续出现，无法系统性修复。

### 3.1 Browser top bar 要作为自有 motion surface，而不是被动工具栏
NativeDo 的 browser top bar 不应只是在 route 里占一个静态 header 槽位。它应像 Telegram `WebActionBar` 一样，作为 browser chrome 的自有 motion owner，统一协调：
- 标题 / 副标题切换
- 地址浏览态与编辑态切换
- 搜索显隐
- 背景色与分隔线过渡
- leading / title / trailing 三段区域的几何稳定性

原因：
- Telegram 的浏览器顺滑，不只是因为有一个顶栏，而是顶栏内部的文字、颜色、按钮和布局都由同一 owner 协调。
- 如果地址栏、标题、菜单、背景各自独立做动画，即使 owner handoff 正确，视觉上仍会显得像多个层在补帧。
- 这也是 browser top bar 和普通 root top bar 最重要的差别之一。

替代方案：
- 继续把 browser top bar 当成多个 composable 状态切换的组合结果。
  - 放弃原因：会持续暴露 scene 切换时的节奏不一致问题。

### 4. Browser scene state 继续留在 feature 层，并保持显式互斥

`AppBrowserChromeScene` 继续作为 browser 顶栏状态的核心模型，至少保留：
- browse
- search
- address editing
- history popup
- overflow menu
- subpanel

浏览器的 back/close 语义、菜单显隐、地址编辑与搜索切换，仍由 feature state 决定，而不是散落在 composable 内部的临时 `remember` 状态。

原因：
- Telegram 的浏览器/列表搜索都不是纯 UI slot 切换，而是 scene state 切换。
- NativeDo 已经有 `AppBrowserChromeScene` 和 `AppBrowserViewModel`，应继续沿着这个方向收口。

替代方案：
- 把 overflow、history、address 这些状态尽量改成本地 composable 状态。
  - 放弃原因：会重新制造 handoff 和恢复时序问题。

### 4.1 Top bar attach 与 fullscreen 属于几何状态，不只是显隐状态
当 browser surface 向顶部 attach 或进入 fullscreen / fullsize 时，top bar 应将其视为几何状态变化，而不是单独隐藏或显示某几个控件。至少这些信号应共享同一条进度链路：
- 背景接管强度
- 顶部圆角或 clip 变化
- handle 存在感弱化
- title / subtitle 的对齐与密度
- leading / trailing actions 的位移与 alpha

原因：
- Telegram 在这些阶段的平滑感来自“整个 chrome attached 到了另一个空间”，而不是某个 bar 突然淡入淡出。
- 这类状态如果拆成多个 visibility flag，会让 fullscreen、attach、reader restore 看起来彼此脱节。

替代方案：
- 继续把 attach、fullscreen、search、address edit 看作独立显隐分支，各自管理动画。
  - 放弃原因：难以达到 Telegram 那种统一的结构感。

### 5. Telegram 对齐以行为模型和排列密度为界，不复制材质或类结构

这次对齐会参考 Telegram 的这些信号：
- `ActionBar` 的 per-scene owner 角色
- `createMenu()` / `ActionBarMenuItem` 的右侧动作组织
- `setUseContainerForTitles()` 的标题容器语义
- `setAllowOverlayTitle(true)` 的 overlay title 行为
- `ItemOptions.makeOptions(...)` / `ActionBarPopupWindow` 的锚定菜单组织
- Settings / Dialogs 等页面中“透明内容层 + 独立背景层”的做法

但 NativeDo 继续使用自身的 Compose 结构、glass 主题、motion token 与命名。

原因：
- 用户目标是尽量 1:1 保持样式和元素排列，而不是照搬 Telegram 工程。
- 项目许可证和现有架构都要求我们只复刻行为边界与几何语义。

## Risks / Trade-offs

- [与既有 `align-telegram-chrome-menu` change 存在范围重叠] → 这次 change 只聚焦 top bar contract 与 browser handoff，把 media viewer / broader contextual menu 留在原 change 或后续 change 中。
- [1:1 对齐 Telegram 可能削弱 NativeDo 现有 glass identity] → 只对齐布局、排列、密度和状态切换，不直接复制 Telegram 的背景、颜色或类 API。
- [scene owner 引入后，topic 属于 root 还是 reader 可能暂时模糊] → 规格先定义 owner 语义，再允许首版仍通过现有 root slot 承载 topic 的 reader geometry。
- [route 交接阶段可能出现双 top bar 或闪烁] → 用显式 owner 规则和 targeted verification 验证“唯一 owner / 无空白 / 无双重渲染”。
- [browser state 如果分散在 route、ViewModel、composable 多层，仍可能复发时序 bug] → 菜单、历史、搜索、地址场景优先收口到 `AppBrowserUiState` / `AppBrowserChromeScene`。
- [browser 顶栏内部动画即使 owner 正确，仍可能缺少 Telegram 的“结构连续性”] → 把 attach/fullscreen/search/address 的几何变化明确收口到 browser chrome motion owner，而不是继续拆分到多个局部动画里。

## Migration Plan

1. 先定义 top bar owner matrix，明确 root、topic/reader、browser 各自何时拥有 top bar，以及 `APP_BROWSER_ROUTE` 激活时的唯一 owner 规则。
2. 在不大改视觉的前提下，收紧 `NativeDoApp` 与 browser shell 的 handoff，消除 topic → browser 空白顶栏问题。
3. 把 top bar 的共享几何语义抽象清楚，再逐步把 root/topic/browser 的 leading、title、trailing 排列对齐到 Telegram。
4. 让 browser chrome 成为自有 motion surface，统一协调标题、副标题、地址编辑、搜索、背景与分隔线变化。
5. 保持 browser scene state 在 feature 层，避免为了修 handoff 重新退回通用 top bar slot 模式。
6. 为 topic → browser、browser → topic、browser 内部 scene 切换，以及 attach/fullscreen 状态切换添加验证和手动 smoke checklist。
7. 如果效果退化，可先保留现有视觉细节，只回退局部 handoff 或 owner 映射，而不是撤销整个 browser shell。

## Open Questions

- 首版 topic 是否要显式落成 `ReaderTopBarOwner`，还是继续由 root scaffold 承载 reader 风格顶栏但遵守同一 owner contract？
- topic → browser 的 handoff 首版需要 crossfade / morph，还是只要确保“无空白、无双栏”即可？
- 哪几个 Telegram 页面应作为 root/topic/browser 的主参考基线：`DialogsActivity`、`SettingsActivity`，还是还要补充 `ui/web/*` 的浏览器相关页面？
- trailing actions 的排序是否需要在 root/topic/browser 三类 scene 中严格固定，还是允许在共享几何下按场景微调？
