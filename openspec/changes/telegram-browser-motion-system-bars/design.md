## Context

当前 browser 的 scene 结构、top bar 和 popup 组织已经基本成形，但用户能直接感知到的“手感层”还明显停留在首版：
- route 打开使用 `380ms` 的纯横向滑入，关闭使用 `300ms` 的纯横向滑出
- dismiss 手势仅限左侧 `24dp` 区域
- progress 采用 `start -> fake tick 到 0.7 -> 等真实进度 -> 延迟消失` 的策略
- 搜索每次输入都直接触发 `findAllAsync(query)`，没有 debounce
- top bar 只消费归一化后的 `scrollProgress` 做背景 fill，没有 Telegram 风格的滚动收缩
- `topBarGlassColor` 仍是静态色，browser route 也还没有真正通过 `NativeDoScreenStyle` 接管 system bars
- bookmark 仍是 address scene 的占位卡片，没有状态、没有持久化

同时，仓库已经具备本轮所需的基础：
- `AppBrowserRoutePresentationState` 和 swipe settle 规则可以继续扩展
- `NativeDoDuration` / `NativeDoEasing` 已经有 browser motion token
- `NativeDoScreenStyle` / `NativeDoSystemBarStyle` 已预留 browser / reader 的 scene style 工厂
- browser chrome 已经有 dedicated scene state machine，适合继续承接 feedback 与 system chrome

这使得本 change 更适合被视为“browser top bar contract 之后的第二阶段 polish”，而不是另起一套 browser 架构。

## Goals / Non-Goals

**Goals:**
- 把 browser route 的进入、退出和 dismiss 手感对齐到 Telegram 风格的短、明确、连续反馈。
- 让 progress、search、防抖、scroll collapse 和 page theme color 形成一套一致的 browser feedback 模型。
- 让 browser scene 真正接管 status bar / navigation bar 的颜色与 icon 明暗，而不是继续使用全局默认策略。
- 实现 bookmark 的最小可用闭环：添加、移除、持久化、基础入口。
- 保持本 change 可以在现有 full-screen route browser 内独立完成，不依赖 sheet browser 或 Instant View。

**Non-Goals:**
- 不在本次 change 中引入 bottom sheet browser、半屏/全屏 Web 容器或 reader/Instant View scene。
- 不在本次 change 中实现真实 blur substrate 或改写全局 backdrop host。
- 不要求把 browser 全部动画都切成 Telegram 源码级参数复刻；重点是行为节奏和反馈模型对齐。
- 不改动 link router 的大方向，不新增后端接口或远程 bookmark 服务。

## Decisions

### 1. Browser presentation 继续围绕 route-level progress 建模，但改成 layered feedback

browser 的打开、关闭和 swipe dismiss 不再只是“一个横向 offset 值”，而是围绕统一的 presentation progress 同时驱动：
- route alpha
- route translationX
- backdrop alpha
- top bar alpha / translation
- dismiss settle duration

进入/退出将采用更 Telegram 化的 `alpha + translationX` 节奏，swipe dismiss 继续保留按剩余距离自适应时长的规则，并把最小时长维持在 `250ms` 地板。

原因：
- Telegram `ArticleViewer` 的高级感来自 layered transition，而不是单一位移。
- NativeDo 已有 `AppBrowserRoutePresentationState`，适合继续扩展，而不必改成全新容器。

替代方案：
- 继续保留当前 route 级单轴位移动画。
  - 放弃原因：手感会继续显得慢、钝，而且很难把 top bar/system bars 一起纳入同一节奏。
- 直接改成 spring route 动画。
  - 放弃原因：full-screen route 本身更接近 `ArticleViewer` 而不是 `BotWebViewSheet`，纯 spring 容易把容器语义做混。

### 2. Scroll collapse 采用原始 scroll delta 驱动，而不是继续只看归一化进度

为了接近 Telegram browser header 的“即时收缩”体感，browser 需要额外保存原始 `scrollY` 差值，并在 route 层派生：
- `headerExpandedHeight`
- `headerCollapsedHeight`
- `headerCollapseProgress`

top bar 的几何、标题缩放和 progress 位置以这个 collapse progress 为准；原先的 `scrollProgress` 仍保留给背景 fill 或次级视觉效果。

原因：
- `scrollProgress` 只能表达“滚了多少比例”，不能表达“这一帧在往上还是往下滚”。
- Telegram 的 header 收缩更像直接消费 delta，而不是页面阅读进度条。

替代方案：
- 继续只使用当前 `scrollProgress`。
  - 放弃原因：无法得到收缩/恢复的即时反馈，只能做弱视觉填充。

### 3. Browser theme color 与 system bars 通过 scene-local screen style 接管

browser route 会在自身内部提供 browser-scoped `NativeDoScreenStyle`，而不是继续完全依赖 app 根级默认 style。该 style 将由以下输入驱动：
- fallback browser chrome color
- page resolved theme color
- luminance-based icon appearance
- route presentation progress

页面主题色优先从当前文档的 `meta[name=\"theme-color\"]` 获取；获取失败时回退到 NativeDo browser 默认 palette。状态栏和导航栏颜色通过 `NativeDoProvideScreenStyle()` 在 browser scene 内局部接管，并在 route 退出时自然恢复到上一个 scene。

原因：
- 仓库已经预留 `nativeDoBrowserScreenStyle()`，但目前还没有真实 consumer。
- 把 scene-specific system bars 留在根主题副作用里，会持续与 browser route 的局部状态冲突。

替代方案：
- 继续在根 `NativeDoTheme()` 内统一写死 system bars。
  - 放弃原因：无法做到 browser 页面级颜色联动，也无法安全恢复前一个 scene 的样式。
- 直接在 `AppBrowserRoute` 里手写 window side effect。
  - 放弃原因：会绕开现有 screen style 体系，后续 reader/sheet scene 复用困难。

### 4. Progress 与 page find 反馈都收口为“真实状态驱动 + UI 平滑”

progress 不再强调 fake tick 本身，而是采用“真实进度为主、UI 可平滑追赶”的模型。具体上会保留初始占位，但把视觉主导权交给真实 page progress；search 则改为显式 debounce 后再触发 `findAllAsync()`，并忽略过时查询的匹配回调。

原因：
- Telegram 的 progress 看起来顺，不是因为纯 fake，而是因为状态和动画之间有平滑层。
- search 无 debounce 会导致 WebView find 频繁重算，匹配计数闪烁也更明显。

替代方案：
- 只调 fake tick 参数，不改状态模型。
  - 放弃原因：仍然很难解决“输入过快、进度突变、回调时序互相覆盖”的问题。

### 5. Bookmark 单独建 store，不与 history 复用同一模型

bookmark 将引入独立的本地 store，记录至少：
- canonical url
- title
- host
- visited or saved time
- optional favicon snapshot

overflow menu 和 address scene 都可消费同一 bookmark 状态，但 history 继续保持“最近访问记录”的语义，不和 bookmark 混用。

原因：
- history 是时间流，bookmark 是显式收藏，语义不同。
- 当前 address scene 已有 bookmark 卡片入口，适合作为 bookmark 状态的第二展示面。

替代方案：
- 直接在 history store 里给记录打 bookmark flag。
  - 放弃原因：会让“访问记录”和“收藏记录”的生命周期、排序和清空策略纠缠在一起。

## Risks / Trade-offs

- [WebView 主题色提取在部分页面上可能拿不到稳定值] → 回退到 browser 默认 palette，并把提取失败视为正常路径。
- [scroll collapse 可能在 WebView 回弹或重排时抖动] → 对 scroll delta 做阈值/夹紧处理，并在 address/search/subpanel scene 下禁用收缩。
- [browser route 的 system bar style 可能与根级主题副作用互相覆盖] → 统一走 `NativeDoProvideScreenStyle()`，避免两层分别直接写 window。
- [bookmark store 会增加一份本地持久化状态] → 采用与 history 相同的本地 persistence pattern，并限制首版字段。
- [一轮 change 同时覆盖 motion、system bars、bookmark，跨度较大] → 任务按 motion → system bars → bookmark 顺序拆分，并要求逐步 smoke 验证。

## Migration Plan

1. 先收口 route presentation / swipe dismiss / search debounce / progress 状态模型，确保 browser 基本反馈稳定。
2. 再接入 scroll collapse 和 page theme color 提取，建立 browser-local screen style。
3. 完成 bookmark store、menu/address scene 的 bookmark 动作与基础列表入口。
4. 最后做联调与验证：browser open/close、swipe dismiss、search、scroll collapse、theme-color、system bar restoration、bookmark persistence。
5. 若某部分手感退化，可单独回退 motion 参数或 system bar override，而不必回退 bookmark persistence。

## Open Questions

- dismiss 手势首版是直接扩到全屏水平拖拽，还是先从 `24dp` 提升到 `40dp`/`48dp` 的 widened edge region？
- bookmark 的基础入口首版放在 overflow menu、address scene 还是额外增加独立 subpanel？
- 页面 `theme-color` 是否需要在 loading 中途持续更新，还是只在 `page finished` 后更新一次即可？
