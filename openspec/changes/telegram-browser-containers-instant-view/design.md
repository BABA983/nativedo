## Context

NativeDo 当前的 browser 明确是“单一 full-screen route 容器”：
- `NativeDoNavHost` 通过 `APP_BROWSER_ROUTE` 打开 `AppBrowserRoute`
- root 侧没有 browser overlay host
- `NativeDoReaderScaffold` 已存在，但只是一个薄壳，还没有 reader-specific scene state
- 最早的 browser 文档也明确写了首版不做 bottom sheet browser

这条路线让登录态同步、Cloudflare、文件上传和复杂 WebView 流程很稳定，但也带来两个明显差距：
- 轻量浏览内容依旧要切到 full-screen page route，缺少 Telegram 常见的 sheet browser 语义
- “阅读器 / Instant View” 仍然没有独立容器，只能在 browser 和现有 native page 之间二选一

与此同时，仓库又已经有一些前置条件：
- `NativeDoReaderScaffold` 和 `NativeDoScreenStyle` 为 reader scene 预留了骨架
- browser chrome 已经是 scene state machine，而不是普通 toolbar，具备跨容器复用的潜力
- 设计文档里已经把 `NativeDoSheetScaffold`、reader scene 和 browser scene 的分工写进了 Phase B 方向

因此，本 change 更适合作为“容器层升级”：不是修改单个 browser route 的视觉，而是补齐 browser family 的容器矩阵。

## Goals / Non-Goals

**Goals:**
- 在现有 full-screen route browser 之外，增加符合 Telegram 语义的 sheet browser 容器。
- 建立显式的 browser presentation target 规则，明确何时使用 full-screen route、sheet browser 或 Instant View / reader scene。
- 为受支持页面增加 Instant View / reader scene，提供更轻的阅读型承载和返回路径。
- 保持 full-screen route browser 继续负责 auth-sensitive、challenge、表单和复杂多步 Web 流程。
- 让后续 system bars、glass substrate 和 scene owner 模型可以自然扩展到 sheet / reader 容器。

**Non-Goals:**
- 不要求首版就实现 Telegram 那种服务端模板驱动的通用 Instant View 体系。
- 不要求一次性把所有 URL 或页面都自动分配到最佳容器；首版允许从保守规则起步。
- 不在本次 change 中重写 browser 的 scene state machine 或重做 bookmark / progress 等 feedback 逻辑。
- 不引入新的 app module 或跨平台抽象。

## Decisions

### 1. 引入显式 browser presentation target，而不是继续把所有打开都等价成 route navigation

browser 家族将至少区分三种承载目标：
- `FullRoute`
- `Sheet`
- `InstantView`

link router 或 browser 内部动作不会直接“打开一个 composable”，而是先解析为 presentation target，再由 root scene host 执行承载。

原因：
- Telegram 的 browser 体验不是单一容器的不同动画，而是多种容器协同。
- 只靠 `NavHost` route 很难表达 sheet overlay 和 reader overlay 的层级与恢复关系。

替代方案：
- 继续让所有 browser 打开都走 `APP_BROWSER_ROUTE`，再在 route 内部硬切模式。
  - 放弃原因：会把容器语义压平，sheet/reader 的手势和返回路径会非常别扭。

### 2. Full-screen route browser 保持为复杂 Web 流程的 canonical container

`AppBrowserRoute` 继续承担：
- 登录相关跳转
- Cloudflare challenge
- 文件上传
- 多步表单
- 任何需要稳定回到系统返回栈的 WebView 流程

sheet browser 首版主要服务于轻量浏览和内容消费，而不是替代所有 route browser 行为。

原因：
- 当前项目明确依赖 WebView 承载登录态和复杂站内页面。
- 过早让所有流程都进入 sheet，会把 keyboard、insets、window restore、target blank 等问题叠在一起。

替代方案：
- 新容器上线后默认所有链接优先走 sheet。
  - 放弃原因：对 auth-sensitive flow 风险过高。

### 3. Sheet browser 采用 root overlay host + dedicated scaffold，而不是直接套 Material bottom sheet

sheet browser 将采用 root-level overlay host 和专门的 scaffold，至少显式建模：
- `openedProgress`
- `fullscreenProgress`
- drag offset
- viewport inset / top bar offset
- system bar style handoff

它可以复用 browser chrome 的 scene state，但不复用 Material 默认 modal sheet 的容器和手势规则。

原因：
- Telegram `BotWebViewSheet` 的重点是容器几何与 viewport 联动，而不是一个普通弹层。
- WebView、top bar、bottom controls、system bars 需要共同过渡，Material 默认 sheet 抽象太浅。

替代方案：
- 直接使用 `ModalBottomSheet` 或 `BottomSheetScaffold`。
  - 放弃原因：很难做 Telegram 风格的 fullscreen transition 和 drag-to-dismiss 细节。

### 4. Instant View 首版限定为“本地可判定支持的页面”，不是通用网页阅读器

首版 Instant View / reader scene 的入口只在“当前页已通过本地规则判定为可进入 reader”时出现。支持判定可以来自：
- linux.do 站内受支持页面
- 当前文档可本地提取出稳定的 reader 内容

若页面不支持，则不展示 Instant View 入口，也不影响普通 browser。

原因：
- Telegram 的 Instant View 本身就是 capability-gated，而不是每个网页都能进入。
- 在当前项目里，做“支持时可进、不支持时不出现”比强行做通用阅读器更稳。

替代方案：
- 首版尝试对所有网页做通用 reader extraction。
  - 放弃原因：复杂度和失败率都过高，不适合作为第一版容器升级。

### 5. Reader scene 使用 dedicated owner，并通过返回路径恢复 browser or previous scene

Instant View / reader scene 会有独立 owner，使用 `NativeDoReaderScaffold` 作为基础骨架；从 browser 打开 reader 时，返回动作默认恢复到来源 browser scene，而不是直接丢失来源上下文。

原因：
- reader scene 不是 browser 子面板，而是独立内容容器。
- root / browser / reader owner 模型已经在现有 top bar change 中确立了方向。

替代方案：
- 把 reader 当作 browser 内部 subpanel。
  - 放弃原因：会让返回、search、share 和 system bars 语义全部变得混乱。

## Risks / Trade-offs

- [root overlay host 会引入新的 scene 层级复杂度] → 明确区分 route scene 与 overlay scene，只让 root host 负责承载，不吞掉 feature state。
- [sheet browser 和 full route browser 共享同一套 chrome 时容易互相污染状态] → 把 scene model 与 container presentation 分层，chrome state 仍由 feature owner 提供。
- [Instant View 支持判定可能在首版覆盖率不高] → 允许首版只覆盖明确支持的页面，并把“不显示入口”当作正常行为。
- [WebView 放进 sheet 后的 keyboard、drag、nested scroll 更复杂] → 首版限制 sheet browser 的适用场景，不先吞并 auth-sensitive flow。
- [presentation target 规则如果过早复杂化，会影响落地] → 从显式但保守的规则起步，优先保证 predictability。

## Migration Plan

1. 先定义 browser presentation target 和 root host/overlay host 的 ownership。
2. 在不影响 `AppBrowserRoute` 的前提下，新增 sheet container 和最小的展示/关闭闭环。
3. 接入 sheet browser 的 fullscreen transition、drag-to-dismiss 和可选 pull-to-refresh。
4. 再引入 Instant View / reader scene 及其支持判定、入口与返回路径。
5. 最后补 smoke 验证：full route、sheet、reader 三类容器的打开、关闭、恢复和 system bar handoff。

## Open Questions

- 首版哪些页面最值得进入 sheet browser，哪些页面必须坚持走 full route？
- Instant View 的“支持判定”首版更偏向 linux.do 站内页面，还是偏向外链文章的 readability extraction？
- sheet browser 是否需要独立底部 controls，还是首版只保留 top chrome 即可？
