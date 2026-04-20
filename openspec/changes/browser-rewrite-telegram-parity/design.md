## Context

当前 browser feature 由一组体量很大的 `AppBrowser*` 文件承载，路由、chrome、WebView、reader、tabs、面板和状态模型长期混在一起，已经出现明确的动画、手势和状态同步问题。`docs/superpowers/specs/2026-04-19-browser-rewrite-telegram-parity.md` 已经把目标收敛为一次完整重写：保留 `LinkRouter`、cookie 同步、history/bookmark/search 持久化、reader 内容模型和主题基座，但丢弃现有 browser feature 的实现方式，改为 Telegram 风格的 `BrowserHost + BrowserSheetDialog + BrowserSheet + BrowserActionBar + BrowserTabsDock/Overlay + BrowserReaderScene` 分层。

这次设计还需要处理一个额外约束：仓库现有 canonical spec 把 regular browser 定义成 full-screen browser-family surface，而新方案明确要回到 Telegram 风格的 sheet/dock/overlay 体系。因此这不是一次局部 UI 调整，而是 browser family 宿主模型、chrome contract、session 生命周期和动画模型的整体替换。

## Goals / Non-Goals

**Goals:**
- 用新的 `Browser*` 体系替换现有 `AppBrowser*` browser 实现，并让代码分层直接映射到 Telegram browser 的主要容器。
- 让 `BrowserHost` 明确拥有 active tab、docked tabs、reader follow-up、restore 和 close 生命周期，避免 session 状态继续散落在 route、sheet 和 UI shell 之间。
- 用显式状态模型承载 chrome scene、sheet presentation、tab snapshot 和 WebView runtime，同一类职责只在一个地方维护。
- 保留现有 linux.do 相关的路由、cookie、持久化和 reader 解析能力，不引入新的网络协议或登录策略。
- 让后续 implementation 可以按 phase 拆分，每个 phase 都能独立编译和验证，而不是要求一次性替换全部行为。

**Non-Goals:**
- 不改 Discourse API、auth contract 或 cookie 存储协议。
- 不引入新的 app module、DI 框架或跨平台抽象。
- 不把 browser 做成通用 Discourse SDK 组件；实现仍然服务于 linux.do 客户端。
- 不在这次 change 中新增发帖、AI 辅助或与 browser rewrite 无关的功能。

## Decisions

### 1. 用全新 `Browser*` 文件集做替换，而不是在 `AppBrowser*` 上继续重构

重写会直接新建 `Browser*` 命名体系，并在接入完成后删除旧的 `AppBrowser*` 文件。重写过程允许短期并存，但不会要求新旧状态模型长期兼容。

原因：
- 现有文件已经是“大一统容器”，继续在原文件上重构会长期处于高耦合和高回归风险状态。
- 新命名可以把“旧实现即将废弃”这件事表达得非常明确，降低后续开发者误入旧路径的概率。

替代方案：
- 在原文件上拆分和重命名。
  - 放弃原因：迁移周期长，而且很容易为了兼容旧状态而保留错误边界。

### 2. `BrowserHost` 作为唯一 browser family 宿主，拥有会话而不是只做壳层转发

`BrowserHost` 将持有 `BrowserTabState` 列表、active tab、docked tabs、reader follow-up 引用以及 minimize/restore/close 操作。`BrowserViewModel` 只负责当前 active surface 的 feature 状态，例如 chrome scene、address/search/menu、page-derived UI 和 panel state。

原因：
- session 生命周期是浏览器家族的核心，放在 host 里比散落在 route、screen 和 overlay 中更自然。
- Telegram 的 browser 体验本质上是“宿主调度多个 surface”，而不是单页面 route。

替代方案：
- 让 `BrowserHost` 只负责显示，session 继续由 route/ViewModel 各自保存。
  - 放弃原因：最小化、恢复、多标签和 reader 返回路径会继续跨层耦合。

### 3. 以 `BrowserSheetDialog + BrowserSheet` 作为 regular browser 主容器，不使用 Material sheet 语义

regular browser 由 dialog window 包裹，再由专门的 `BrowserSheet` 负责 open/dismiss/fullscreen progress、drag、nested scroll handoff、shadow、corner 和 scrim。实现不会依赖 `ModalBottomSheet` 或 `BottomSheetScaffold` 的默认手势模型。

原因：
- Telegram browser 的关键不是“底部弹层”，而是容器几何、顶部贴附、拖拽和 WebView viewport 联动。
- WebView、reader、address/search chrome 和 dock 过渡需要共享同一套 presentation state。

替代方案：
- 用 Compose Material sheet 组件快速搭建。
  - 放弃原因：默认容器抽象不足以稳定表达 Telegram 式的 drag/fullscreen/dismiss 行为。

### 4. chrome 使用显式 scene state machine，并把地址、搜索、历史、菜单和子面板都建模成独立场景

`BrowserChromeScene` 将至少包含 `Browse`、`Search`、`AddressEditing`、`HistoryPopup`、`OverflowMenu` 和 `Subpanel`。`BrowserActionBar`、`BrowserAddressBar`、`BrowserSearchBar` 与各面板只消费统一的 `BrowserChromeModel` / `BrowserUiState`，不再各自推导页面状态。

原因：
- 浏览器顶栏不是简单 toolbar，而是多个互斥场景的容器。
- 旧实现的主要问题之一就是 chrome 逻辑与页面状态混在一起，导致模式切换和面板显示互相污染。

替代方案：
- 继续由各 composable 根据零散状态自行判断当前模式。
  - 放弃原因：状态切换很难穷举验证，也不利于单测。

### 5. WebView 生命周期通过 `BrowserWebViewController` 与 `BrowserTabState` 连接，最小化时保存快照而不是保活完整视图层

`BrowserWebViewController` 负责 save/restore bundle、preview capture、JS evaluate、progress/title/favicon/page callbacks、target blank 处理和 edge-swipe back。`BrowserTabState` 保存页面恢复所需的最小快照，包括 URL、标题、导航能力、action bar color、preview bitmap、WebView bundle 和 reader/source metadata。最小化时优先保留可恢复快照，而不是把 live WebView 长时间留在 dock 中。

原因：
- docked tabs 的目标是可恢复，而不是继续占用完整 WebView 资源。
- WebView lifecycle、preview capture 和 restore 需要统一入口，否则 minimize/restore 很难稳定。

替代方案：
- 每个标签始终持有活跃 WebView 实例。
  - 放弃原因：内存和生命周期成本过高，也更难管理 overlay/sheet 切换。

### 6. dock、tabs overlay 和 sheet↔dock 动画作为 host 层职责实现

`BrowserTabsDock`、`BrowserTabsOverlay` 和 `BrowserTransition` 属于 host 层。dock 只呈现已最小化 session 的摘要，overlay 负责多标签浏览、恢复和销毁，transition 负责 preview bitmap、spring 插值和 fullscreen/dock rect 之间的几何过渡。

原因：
- 这些能力属于 session 之间的切换，而不是某个单标签 feature 内部行为。
- 只有 host 层能同时看到 active tab、docked tabs 和容器几何信息。

替代方案：
- 把 dock/overlay 动画塞回 `BrowserRoute` 或 `BrowserActionBar`。
  - 放弃原因：职责不对，而且会重新出现 UI 与 session 状态交错。

### 7. reader scene 保持为 browser family 内的独立 surface，通过来源 tab 恢复返回路径

`BrowserReaderScene` 会复用现有 reader 内容模型，但作为 browser family 中的独立 surface 呈现，进入和返回使用右侧滑入/滑出动画。reader close 后恢复同一 source tab，而不是新建或重新请求 browser session。

原因：
- Instant View 在体验上是 browser 的 follow-up surface，不是 browser 子面板。
- 保留来源 tab 能让 share、history、back 和 minimize/restore 语义保持一致。

替代方案：
- 把 reader 当成 browser 内部普通页面或单独 native route。
  - 放弃原因：会破坏返回路径和 browser family 的统一宿主模型。

### 8. 实施按 phase 推进，但 phase 之间用最终命名和最终边界编码

implementation 顺序将沿用规划文档中的八个 phase：先状态模型与 Web runtime，再 sheet、chrome、tabs、host、reader、子面板，最后 route 组装和旧文件清理。即使分阶段落地，每个阶段也直接采用最终的 `Browser*` 命名和宿主边界，不引入临时兼容层。

原因：
- 可以分批验证，又不会把临时架构带进最终代码。
- 用户已经明确不希望在旧实现上继续叠加兼容性 hacks。

替代方案：
- 先做临时桥接层，最后再统一改名和收口。
  - 放弃原因：会增加过渡代码，和这次“完全重写”的目标冲突。

## Risks / Trade-offs

- [Compose 中实现 Telegram 式 sheet 几何与 tabs overlay 透视效果复杂] → 先把状态机、几何和手势边界固定，必要时对 3D 预览局部使用 `AndroidView` 或较轻的 `graphicsLayer` 近似。
- [最小化后释放 live WebView 可能带来恢复抖动] → 通过 `saveState(Bundle)`、preview bitmap 和延迟 restore 减少观感跳变，优先保证正确恢复而不是假装始终常驻。
- [browser rewrite 与现有 piecemeal browser changes 存在范围重叠] → 把本 change 作为新的总 source of truth，后续实现按本 change 的 phase 和 capability 收口。
- [sheet 嵌套滚动、WebView 手势和 edge-swipe back 容易互相冲突] → 把 sheet dismiss、page back 和 reader close 明确为三条独立的 settle 路径，并为纯状态逻辑补单测。
- [一次重写涉及文件多，切换点多] → 先并存新旧文件，等 `NativeDoApp` 和 `BrowserHost` 完成切换后再集中删除旧文件。

## Migration Plan

1. 新建 proposal 对应的 `Browser*` 状态模型、Web runtime 和 spec contract，不修改现有 public entrypoint。
2. 引入 `BrowserSheetDialog`、`BrowserSheet`、`BrowserActionBar`、dock/overlay 与 `BrowserHost` 新状态，先让新宿主可以独立承载 browser session。
3. 将 `NativeDoApp` browser 接入点切到新的 `BrowserHost`，并让 `BrowserRoute` / `BrowserScreen` 只负责新体系的装配。
4. 接入 reader scene、子面板和 sheet↔dock 过渡，完成多标签和恢复闭环。
5. 删除旧的 `AppBrowser*` 文件，并用编译、单测和手动验证清单确认切换完成。

如果某个 phase 出现阻塞，回退方式是暂时保留旧 entrypoint，不做“半切换”发布；一旦新 host 接管入口，就以修正新实现为主，不再回到旧架构上补丁。

## Open Questions

- regular browser 首版是否所有入口都直接进入 sheet，还是少数 auth-sensitive 流程在 implementation 阶段仍需单独保留窗口策略？
- tabs overlay 的 3D 预览是否需要在首版做到完全 1:1 透视，还是先接受 Compose 可维护的近似实现？
- WebView preview capture 在 API 28/29 边界上是否统一走 `PixelCopy`，还是对部分机型保留 `draw(Canvas)` 兜底作为常规路径？
