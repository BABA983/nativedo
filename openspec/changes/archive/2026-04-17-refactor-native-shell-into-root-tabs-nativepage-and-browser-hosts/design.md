## Context

NativeDo 当前的 Android shell 同时存在两套导航与容器语义：

- `NativeDoNavHost` 承担 root route 与 login route。
- `NativeDoApp` 同时持有全局 top bar、bottom bar、link router、browser overlay、session gate。
- `NativeDoNavigationLayout` 维护自定义 pushed stack，并实现 predictive back、edge-swipe、前后景联动动画。
- `AppBrowserRoute` 仍然自己承担 full-screen browser 的进入、退出和侧滑关闭动画。

这让 app shell 的 ownership 分散在 app root、nav host、自定义 stack host 和 browser feature 之间。结果是：

- root header 与 native pushed scene 的边界不稳定，容易出现首页 top bar 在详情页背后继续存在的结构问题；
- browser 既是 feature，又承担 presentation 容器职责，导致 full-screen、sheet、reader overlay 的责任难以收敛；
- 命名上混合了 `App`、`Layout`、`Overlay`、`Scaffold` 等不同层级概念，阅读和后续扩展成本较高。

Telegram 的可借鉴点不是类名，而是 ownership：root tabs 是一层，native pushed page 是一层，browser/article viewer 又是独立呈现系统。此次设计沿用这个分层思想，但保持 Compose、单 app module 和现有 feature 业务逻辑。

## Goals / Non-Goals

**Goals:**

- 将 app shell 重构为清晰的容器层级：`AppRoot`、`RootTabsHost`、`NativePageStackHost`、`BrowserHost`。
- 让 root scene 自己拥有自己的 header 和背景，不再依赖 app-level 全局 root top bar。
- 让 native pushed page 的 push/pop、predictive back、edge-swipe 动画继续集中在单一 host 中。
- 让 browser 的 full-screen、sheet、reader overlay 统一由单一 browser host 协调。
- 统一容器、状态、栈元素、route、screen 的命名规则，降低后续维护成本。
- 采用可分批落地的迁移方案，优先调整 ownership，不重写现有 feature 的 ViewModel 和业务逻辑。

**Non-Goals:**

- 不复制 Telegram 的 Java `ActionBarLayout` / `BaseFragment` / `ArticleViewer` 类层级。
- 不在本次改动里重新设计 Home、Topic、Profile、Browser 的视觉风格。
- 不将所有导航都强行并入单一 `NavHost`，也不引入新的导航框架或 DI 框架。
- 不在本次改动里重写 browser WebView runtime、topic/profile 业务状态机或 auth contract。

## Decisions

### 1. App shell 拆成四个明确 host，而不是继续扩展 `NativeDoApp`

最终的 shell 分层为：

- `AppRoot`
  - 装配 theme、session、bulletin、auth restore gate、link router 和各 host 的状态。
- `RootTabsHost`
  - 负责 root pager、bottom tabs、root-scene 选择。
- `NativePageStackHost`
  - 负责 root 之上的 native pushed page 栈。
- `BrowserHost`
  - 负责 browser 的 full-screen、sheet、reader overlay 呈现。

这样可以把装配层、root scene 容器、native page 容器、browser 容器分别收口，避免一个文件同时做所有事情。

替代方案：

- 继续沿用 `NativeDoApp + NativeDoNavHost + NativeDoNavigationLayout` 的混合结构。
  - 放弃原因：ownership 已经分散，继续加逻辑只会让根因更难拆。
- 直接照搬 Telegram 的 `ActionBarLayout` 体系。
  - 放弃原因：Compose 下语义不自然，也不符合现有工程结构。

### 2. Root scene 自己拥有 header/background，app root 不再维护全局 root top bar

`Home`、`Profile`，以及保留为 root scene 的其他页面，各自负责自己的 header、sticky chrome 和背景。`RootTabsHost` 只负责 tabs 与 scene 容器，不再试图提供一个跨所有 root scene 复用的全局 top bar。

这意味着：

- 首页搜索入口、feed tabs、profile header 等都回到 feature 自己的 scene 内；
- native pushed page 出现后，root top bar 不会再作为 app-level chrome 保留在背后；
- root scene 间的 chrome 差异用 scene-specific header 解决，而不是用一个 app-level root top bar 状态机硬撑。

替代方案：

- 保留全局 root top bar，只修个别透出问题。
  - 放弃原因：这会保留错误的 ownership，后续 action mode、search、profile header 仍会互相牵扯。

### 3. `NativePageStackHost` 只负责 native pushed page，browser 不再作为 stack entry

`NativePageStackHost` 只收纳 native page，例如：

- `Topic`
- `ProfileViewer.Other`
- 未来的原生二级/三级页面

它不再持有 browser full-screen entry。这样可以让 stack host 专注在：

- stack entry 生命周期；
- 标题回传；
- predictive back / edge-swipe；
- Telegram 风格前后景联动动画。

替代方案：

- 继续让 browser 和 topic/profile 共用一个 pushed stack。
  - 放弃原因：browser 有独立的 overlay、sheet、reader 和 web runtime 语义，不属于同一种容器。

### 4. `BrowserHost` 统一 browser 呈现，`AppBrowserRoute` 退回 feature runtime 身份

`BrowserHost` 统一协调：

- full-screen browser
- sheet browser
- article reader / instant-view style overlay

`AppBrowserRoute` 保留以下职责：

- browser ViewModel 与 WebView runtime
- browser chrome state machine
- browser 页面内交互

但 full-screen 进出、sheet 宿主、reader overlay 的 ownership 从 feature route 移到 host。

替代方案：

- 保持 `AppBrowserRoute` 既做 feature，又做容器和动画 owner。
  - 放弃原因：full-screen/sheet/overlay 三种呈现方式本来就是容器职责，继续混在 feature route 里会让 browser 更难维护。

### 5. 命名统一采用 `Host / State / Entry / Route / Screen / Scaffold`

本次重构后的命名约束：

- `Host`: 容器 owner，负责挂载、切换、返回和转场。
- `State`: 容器状态或 scene 状态。
- `Entry`: 栈元素或 presentation 元素。
- `Route`: feature 级装配入口，连接 ViewModel、依赖和回调。
- `Screen`: 纯 UI 组合。
- `Scaffold`: 只承担 slot layout，不承担导航 ownership。

对应到本次变更：

- `NativeDoNavigationLayout` -> `NativePageStackHost`
- `NativeDoNavigationState` -> `NativePageStackState`
- `NativeDoStackEntry` -> `NativePageEntry`
- `NativeDoBrowserOverlayHost` -> `BrowserHost`
- `NativeDoBrowserOverlayState` -> `BrowserHostState`

替代方案：

- 延续当前混合命名。
  - 放弃原因：读代码时很难一眼判断某个类型是 layout、容器、路由还是 feature state。

### 6. 采用分批迁移，先调整 ownership，再逐步拆 route/screen

迁移顺序按风险从低到高推进：

1. 建立新的 host/state 命名骨架，保留现有 feature route。
2. 把 root tabs、link router、browser host、native page stack wiring 收进新 shell。
3. 下沉 Home/Profile 的 header ownership，删除全局 root top bar 保留逻辑。
4. 将 browser 从 native page stack 移出，统一由 `BrowserHost` 管理。
5. 最后再拆 `Route` / `Screen` / `Header` 等文件，清理旧命名和旧路径。

这样可以优先稳定容器边界，而不是在第一步就大规模改动 feature UI。

## Risks / Trade-offs

- [Risk] 中间迁移阶段同时存在新旧 host 路径，容易造成 back priority 冲突
  - Mitigation: 明确返回优先级为 `BrowserHost > NativePageStackHost > NavHost global route`，每一批迁移后立即删除旧入口。

- [Risk] root header 下沉后，Home/Profile 的搜索或 action mode 行为可能出现回归
  - Mitigation: 先迁 ownership，不改原有 feature 状态机；由 feature 继续复用现有 search/action mode state。

- [Risk] browser host 抽离时，full-screen/browser sheet 的交互可能出现重复动画
  - Mitigation: 第一版仅迁移 presentation ownership，保留 `AppBrowserRoute` 内部页面逻辑和 chrome state，不重写 WebView runtime。

- [Risk] 命名和文件拆分同时进行，diff 会较大
  - Mitigation: 先完成 host/state 重命名与 wiring，再按 feature 拆文件，避免一次性把行为变更和文件移动混在一起。

## Migration Plan

1. 新增 `AppRoot` 装配层与 app shell state，接管 root/container 级依赖组装。
2. 引入 `RootTabsHost`，把 root pager 和 bottom tabs 从 `NativeDoApp` 中收口。
3. 将 `NativeDoNavigationLayout` 重命名并收敛为 `NativePageStackHost`，同时移除 browser stack entry。
4. 引入 `BrowserHost`，统一承接 full-screen browser、sheet browser、reader overlay 的宿主职责。
5. 把 Home/Profile 的 header/background ownership 迁入 feature scene，删除 app-level root top bar 保留逻辑。
6. 最后按 feature 拆分 `Route` / `Screen` / `Header` 文件，删除旧命名和旧 helper。

回滚策略：

- 迁移期间保持每一批都可单独回退；
- 在 `BrowserHost` 与 `NativePageStackHost` 稳定前，不删除 feature 内部状态机与核心业务逻辑；
- 若某一批出现交互回归，可回退该批 host wiring 变更而不影响数据模型或接口契约。

## Open Questions

- `SearchRoute` 是否长期保留为独立 root scene，还是在后续收敛为 `Home` 内部模式切换，本次变更暂不强行统一。
