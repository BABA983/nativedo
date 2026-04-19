## Context

NativeDo 当前已经有 `NativeDoChromeScaffold`、`NativeDoBrowserScaffold`、`NativeDoPopupMenu` 和 Compose 版 media viewer，但 root 顶栏、browser chrome、media viewer chrome 仍然按不同抽象层次演进。现状最大的问题不是“缺一个 top bar 组件”，而是缺少 Telegram 那种“共享 primitive + 场景专属状态机”的分层：root 顶栏还是静态 slot，browser 已经有专属 shell，viewer 也有自己的 overlay 生命周期，但三者没有统一的 chrome 语义和 popup 组织方式。

这个变更需要同时覆盖 `ui/`, `feature/browser/`, `core/ui/mediaviewer/` 三条线，是典型的 cross-cutting UI architecture change。另一个关键约束是当前仓库为 MIT，不能复制 Telegram GPL 代码，只能复刻行为模型、动效节奏和抽象边界。

## Goals / Non-Goals

**Goals:**
- 建立与 Telegram 一致的 chrome 分层：共享 primitive，场景专属 shell，状态机驱动交互。
- 明确 root/home、browser、media viewer 三个场景的右上角菜单和顶栏不是同一个范式，避免继续用单一 top bar 组件硬套。
- 为后续首页 action mode、browser history panel、viewer auto-hide/menu suspend 提供稳定实现边界。
- 保持当前 NativeDo glass 视觉方向，不牺牲已有 `NativeDoTheme`、`NativeDoGlassSpec` 和 scaffold 体系。

**Non-Goals:**
- 不复制 Telegram 的 Java 类层级、命名或具体实现。
- 不在这次变更里实现真实 backdrop blur substrate；继续复用当前 fake glass / tint-only 基座。
- 不处理后端、导航协议或登录态 contract 变更。
- 不要求一次性把所有页面的顶栏全部迁到新体系，只覆盖 root/home、browser、media viewer 这三类核心 chrome。

## Decisions

### 1. 采用 scene-specific chrome shells，而不是引入一个全局万能 ActionBar

NativeDo 将保留并强化三个独立 shell：
- `RootChrome`
- `BrowserChrome`
- `MediaViewerChrome`

共享的只会是 icon morph、popup motion、anchor highlight、chrome visibility orchestration 等 primitive。

原因：
- Telegram 的首页、browser、viewer 三处右上角 3 点本来就不是一个组件的三种皮肤，而是三套场景状态机。
- 强行合并成一个万能 `ActionBar` 会把 browse/search/address、viewer auto-hide、root action mode 这些互相冲突的生命周期揉在一起，后续只会不断加分支。

替代方案：
- 方案 A：统一成单个 `NativeDoActionBar`。
  - 放弃原因：会把场景行为压扁成 slot 和 flag，难以保持 Telegram 的状态切换节奏。
- 方案 B：完全各写各的，不共享 primitive。
  - 放弃原因：会继续造成 popup、按钮和 motion 语义漂移。

### 2. Popup 分成两类：AnchoredPopup 和 ContextualOptions

NativeDo 将明确两种弹层范式：
- `AnchoredPopup`：用于 browser menu、browser history、viewer more menu，视觉上贴近按钮，使用右上角 pivot reveal + cascade items。
- `ContextualOptions`：用于 root/home 顶栏 3 点和未来 action mode more，保留更强的 contextual/scrim/anchor 表达，参考 Telegram `ItemOptions` 行为。

原因：
- Telegram 自己就同时存在 `ActionBarPopupWindow` 和 `ItemOptions` 两个系统，首页 chat 默认更多菜单与 viewer/browser 的 anchored popup 视觉明显不同。
- root 场景需要更强的上下文感和扩展性，后续还要承载 action mode more；browser/viewer 更适合使用轻量 anchored popup。

替代方案：
- 统一只保留一种 popup presenter。
  - 放弃原因：要么 root 菜单太轻，要么 viewer/browser 菜单太重，都会失真。

### 3. Browser 继续使用 dedicated state machine，不退化成普通顶栏

`AppBrowserChrome` 将继续作为独立 scene chrome，状态至少包括：
- browse
- search
- address editing
- history popup
- overflow menu
- subpanel

关键约束：
- address/search 期间必须收起 overflow menu
- back/history 与 close 的 icon semantics 必须由状态驱动
- progress、title、host、forward/back availability 必须由 WebView 状态映射

原因：
- Telegram browser 的重点不是“一个标题栏”，而是 `browse/search/address/history` 之间的互斥切换。
- 当前 NativeDo browser 结构已经接近正确方向，只需要把规范沉淀清楚，而不是重回 root top bar 思路。

替代方案：
- 把 browser chrome 改成通用 `TopBar + SearchField + DropdownMenu`。
  - 放弃原因：会失去 scene-state cohesion，长按 back 历史、address mode、menu hiding 都会变弱。

### 4. Media viewer chrome 采用 immersive overlay 生命周期

`MediaViewerChrome` 将继续保持 top/bottom overlay 一起显隐，菜单依赖当前 chrome 生命周期，而不是常驻栏。

规则：
- chrome 默认可见，用户轻触可切换
- media idle / playback 时可自动隐藏
- menu 展开期间自动隐藏必须暂停
- top bar、thumbnail strip、bottom actions 必须共享同一显隐进度

原因：
- Telegram `PhotoViewer` 的感受来自“整层 overlay 一起进出”，不是单独把顶部菜单按钮藏起来。
- 这套模式也更符合 NativeDo 当前 `MediaViewerState` 的组织方式。

替代方案：
- 让 viewer 只隐藏顶栏，不隐藏底部 strip / actions。
  - 放弃原因：沉浸感不够，也会让 menu 的生命周期和 chrome 状态脱节。

### 5. Root chrome 新增显式状态：normal / search / action mode / contextual options

root/home 顶栏需要从当前静态 top bar 升级为状态机：
- `normal`
- `search`
- `action mode`
- `contextual options`

这里 action mode 不是 browser/search 那种 scene，而是 root 列表选择态的独立 chrome。其更多菜单应复用 `ContextualOptions`，而不是复用 browser anchored popup。

原因：
- Telegram 首页列表在普通态和多选态使用的是不同 chrome 语义，且 back icon、title、actions 会整体切换。
- NativeDo 未来如果要补 Telegram 风格首页交互，这层必须先定清楚。

替代方案：
- 把 action mode 只当作 root 列表内部状态，不进 chrome。
  - 放弃原因：会导致顶栏和列表状态分裂，无法形成 Telegram 那种整栏替换效果。

## Risks / Trade-offs

- [Root chrome 还没有现成 contextual options presenter] → 先把 presenter 作为 root 专用抽象落到 `ui/` / `core/ui/`，不要复用 browser popup 变体硬凑。
- [Browser 与 root 都会消费 popup primitive，容易发生 API 过度抽象] → primitive 只暴露 motion、anchor、palette、positioning；场景 state 和 entry mapping 保留在各自 feature/shell。
- [Viewer auto-hide 和 menu expanded 之间容易打架] → 在 viewer state 中显式建模 `menuExpanded` 对 auto-hide timer 的抑制关系，不通过多个局部 `remember` 推断。
- [1:1 追 Telegram 容易演变成直接抄实现] → design 和 tasks 中只复刻行为模型、状态边界和动画原则，避免类名和代码结构贴近 Telegram。
- [场景拆分后，视觉不统一的风险反而会上升] → 通过 shared primitive 统一按钮、popup、motion，而不是统一 scene state。

## Migration Plan

1. 先补 shared primitive 和 popup presenter 边界，保证 browser / viewer 可以继续消费现有实现。
2. 将 browser chrome 对齐到新规范，作为第一条验证 scene-state 的参考实现。
3. 收紧 media viewer chrome 的 auto-hide / menu suspend 规则，保证 immersive overlay 生命周期一致。
4. 最后再升级 root/home 顶栏，因为它对现有 navigation chrome 影响最大。
5. 若任一阶段效果退化，可先保留旧 root chrome，避免一次性切换三个场景。

## Open Questions

- root/home 的 contextual options 首版是否只覆盖普通态 3 点，还是同时为 action mode more 预留结构但不实现全部动作？
- browser back 长按历史首版是否直接复用现有 history 数据，还是仅做当前 session history？
- media viewer 首版是否需要把 action popup 项按图片/视频类型动态裁剪到与 Telegram 一样细的粒度？
