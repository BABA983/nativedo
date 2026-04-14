# NativeDo Compose Telegram 对齐分阶段计划

**日期：** 2026-04-13
**状态：** Phase A 已完成，Phase B 待实施
**目标：** 将 Compose 版 Telegram 对齐方案拆成 `Phase A / Phase B` 两阶段执行清单；先补齐 Compose 基座，再在必要时为 chrome 引入真实 backdrop glass substrate。

---

## 总策略

- `Phase A`：纯 Compose 基座统一
- `Phase B`：真实 glass substrate 试点

执行原则：

- 先改 theme / motion / primitive / shell
- 再接 feature
- backdrop blur 只给 chrome，不给正文和 feed 主体
- 保持现有 ViewModel、StateFlow、导航结构和业务状态基本稳定

## Review 预先修正

基于当前代码结构，先对原计划补三条边界约束，避免后续按错粒度实施：

- shell 只负责 chrome 组织、slot 和 transition，不接管 `NativeDoApp` / `AppBrowserRoute` 里的导航、session、pager、auth gate、bulletin state ownership
- primitive 先抽“已出现两个以上明确消费点”的核心件；`AnimatedLabel / BackIcon / GlassBar / GlassTab / PopupMenu` 优先，`Cell / FlickerSkeleton` 延后到接入阶段再决定是否公共化
- browser shell 迁移不能只改 `AppBrowserChrome.kt`；当前 overlay 结构还在 `AppBrowserScreen.kt`，Phase A 必须把它纳入改动范围
- `Theme.kt` 当前无条件写入透明 status/navigation bar 与 light/dark icon 规则；进入 `Phase B` 前必须先把这部分降成 screen-style 驱动的 system bar effect

---

## Phase A：Compose 基座统一

### 目标

- 建立可复用的 theme、motion、primitive、shell 层
- 不实现真实 backdrop blur
- 先让 root chrome、browser chrome、search、bulletin、popup、tabs 使用同一套视觉和动效语言

### Task 1：补齐主题材质语义

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Color.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoScreenStyle.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoGlassSpec.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoSystemBarStyle.kt`

**Step**

- [x] 为现有扩展色补 `GlassSpec`，区分 `chrome / popup / field / card`
- [x] 引入 `BlurMode`，先保留 `TintOnly` 为默认实现
- [x] 提供 `LocalNativeDoScreenStyle`
- [x] 为 `NativeDoScreenStyle` 预留 `system bar style` 能力，但 Phase A 先保持默认透明策略
- [x] 保持 `System / Light / Dark` 三态主题模式不变

**Expected**

- UI 代码不再直接散写 glass alpha 和 stroke 逻辑
- Browser / Reader / Root chrome 能局部覆盖样式

### Task 2：收口 motion scheme

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Motion.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoMotionScheme.kt`

**Step**

- [x] 定义统一的 motion preset
- [x] 将 `popup / label / tab / sheet / chrome` 的动画语义收口
- [x] 约定复杂过渡统一使用单一 `progress` 驱动多个属性

**Expected**

- Browser、MediaViewer、Home search mode 的复杂动画可共享节奏

### Task 3：抽 primitive 层

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoAnimatedLabel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoBackIcon.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoGlassBar.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoGlassTab.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoPopupMenu.kt`

**Step**

- [x] 先实现 `AnimatedLabel`
- [x] 再实现 `BackIcon`
- [x] 把 `GlassBar / GlassTab / PopupMenu` 抽到公共层
- [x] `Cell / FlickerSkeleton` 暂不强制在第一批创建，等 browser/home 接入时再根据复用面决定
- [x] 避免继续在 feature 内增加私有 chrome 组件

**Expected**

- [x] root / browser / search 先复用同一套核心 primitive

### Task 4：升级 glass surface 和 icon button

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoGlassSurface.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoChromeIconButton.kt`

**Step**

- [x] `NativeDoGlassSurface` 支持传入 `GlassSpec`
- [x] `NativeDoChromeIconButton` 改为读取统一 glass spec
- [x] 为 Phase B 预留 backdrop material 参数

**Expected**

- [x] 所有 glass surface 先统一 fake glass 视觉逻辑

### Task 5：抽 shell 层

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoChromeScaffold.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBrowserScaffold.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoReaderScaffold.kt`

**Step**

- [x] `NativeDoChromeScaffold` 承担 root top/bottom chrome、background、bulletin
- [x] `NativeDoBrowserScaffold` 承担 browser address/search/menu/subpanel 结构
- [x] `NativeDoReaderScaffold` 先预留给 Topic / Profile / 长内容页面
- [x] scaffold 仅提供 slot + layout + transition；导航、session、pager、auth gate、feature state 仍留在现有 owner

**Expected**

- [x] `NativeDoApp` 不再自己承载完整 chrome 绘制逻辑
- [x] browser chrome 不再停留在 feature 私有容器

### Task 6：接入共享控件

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoSearchField.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/Bulletin.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`

**Step**

- [x] Search field 接入 glass spec 和统一 motion
- [x] Bulletin 接入统一材质和进入/退出节奏
- [x] Root app shell 改为装配层，使用 `ChromeScaffold`
- [x] `NativeDoApp` 继续保留 nav/session/link-open/pager/auth gate 的 owner 身份

**Expected**

- [x] Search / Bulletin / Root chrome 视觉语义统一

### Task 7：改造 browser 为首批落地 feature

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserScreen.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`

**Step**

- [x] browser 状态继续留在 feature
- [x] 先把 `AppBrowserScreen` 里的 overlay / top-bar offset 结构迁移到 `NativeDoBrowserScaffold`
- [x] 再把 browser chrome 迁移到 `NativeDoBrowserScaffold`
- [x] 复用 popup、animated label、back icon、glass bar

**Expected**

- [x] browser 成为 Phase A 的第一批完整示范页

### Task 8：最小 feature 挂载

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeLoadingSkeleton.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/TopicListItem.kt`
- Optional Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoFlickerSkeleton.kt`

**Step**

- [x] Home search mode 继续保留，但改用统一 motion preset
- [x] skeleton 先对齐共享 palette / motion；只有 browser/home 至少出现两个真实消费者时才上提为 `NativeDoFlickerSkeleton`
- [x] topic list item 只做轻接入，不大改成玻璃卡
- [x] topic list item 保持内容容器语义，不为了 primitive 化而强行改造成 `glass cell`

**Expected**

- [x] Home 与新基座说同一种视觉语言，但不过度翻修正文内容

### Phase A 验收标准

- [x] root top bar / bottom bar 改为共享 shell + primitive
- [x] browser chrome 改为共享 shell + primitive
- [x] search field / bulletin / popup menu / tabs 使用统一 glass spec 和 motion scheme
- [x] 不引入真实 blur
- [x] 业务状态、导航、ViewModel 边界基本不变

## Phase A 细拆：按批次 / 提交粒度

### Batch A0：冻结 ownership 边界

**Files**

- Modify: `docs/superpowers/plans/2026-04-13-compose-telegram-alignment-phases.md`

**Scope**

- [x] 明确 shell 与 feature 的 ownership 边界
- [x] 明确 browser 迁移必须包含 `AppBrowserScreen.kt`
- [x] 明确 `Cell / Skeleton` 暂不先抽象

**Exit**

- [x] 后续实现不会把 state owner 误移入 scaffold

### Batch A1：theme 材质 token

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Color.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoScreenStyle.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoGlassSpec.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoSystemBarStyle.kt`

**Scope**

- [x] 把 `glassBackground / stroke / shadow` 收口成 `GlassSpec`
- [x] 引入 `BlurMode.TintOnly`
- [x] 暴露 `LocalNativeDoScreenStyle`
- [x] 给 screen style 增加 `status/navigation` icon appearance、contrast、background policy 的占位能力

**Exit**

- [x] `NativeDoGlassSurface` 与 chrome 组件可以只消费 spec，不再自己算 alpha

### Batch A2：motion registry

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Motion.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoMotionScheme.kt`

**Scope**

- [x] 抽出 `chrome / label / popup / tab / sheet / fullscreen` preset
- [x] 统一“多属性跟随单一 progress”的约定

**Exit**

- [x] browser 与 home search mode 不再继续定义私有节奏常量

### Batch A3：primitive 核心第一批

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoAnimatedLabel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoBackIcon.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoGlassBar.kt`

**Scope**

- [x] 先做标题切换、返回/关闭 morph、chrome bar 容器

**Exit**

- [x] root top bar 和 browser top bar 都能开始替换基础件

### Batch A4：primitive 核心第二批

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoGlassTab.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoPopupMenu.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoChromeIconButton.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoGlassSurface.kt`

**Scope**

- [x] 用统一 spec 改造 icon button / glass surface
- [x] 为 tab 与 popup 建立公共 primitive

**Exit**

- [x] popup、tab、icon button 全部走统一 chrome 材质入口

### Batch A5：root shell 抽离

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoChromeScaffold.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoReaderScaffold.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/Bulletin.kt`

**Scope**

- [x] 把背景、top/bottom chrome、bulletin host 的布局关系收进 `ChromeScaffold`
- [x] `NativeDoApp` 保留 nav/session/rootPager/link/auth gate

**Exit**

- [x] `NativeDoApp` 变成装配层，但状态 ownership 不变

### Batch A6：共享控件接入

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoSearchField.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/Bulletin.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`

**Scope**

- [x] SearchField 接统一材质和 motion
- [x] Bulletin 接统一材质和 motion
- [x] root shell 切到新 scaffold

**Exit**

- [x] search / bulletin / root chrome 视觉与节奏统一

### Batch A7：browser scaffold 迁移

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserScreen.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBrowserScaffold.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`

**Scope**

- [x] 先迁 layout 关系，再迁 bar/menu/search chrome
- [x] 保持 browser state 与 WebView 协调逻辑留在 feature

**Exit**

- [x] browser 成为第一批完整跑通的新 shell 示例

### Batch A8：home 轻量接入

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeLoadingSkeleton.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/TopicListItem.kt`
- Optional Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoFlickerSkeleton.kt`

**Scope**

- [x] home search mode 接新 motion
- [x] skeleton 先对齐 palette / shimmer 规范
- [x] topic item 只做轻接入，不玻璃化正文

**Exit**

- [x] home 与 browser/root 同语系，但 feed 主体仍保持克制

## Phase A 类 / 函数级 Checklist

### Batch A1：theme 材质 token

- `Color.kt`
  - [x] `NativeDoExtendedColors`：决定保留现有原始色字段，还是补充可推导 `GlassSpec` 的材质字段
  - [x] `NativeDoLightExtendedColors`：补全 `chrome / popup / field / card` 对应的材质来源
  - [x] `NativeDoDarkExtendedColors`：补全 `chrome / popup / field / card` 对应的材质来源
- `Theme.kt`
  - [x] `LocalNativeDoExtendedColors`：继续保留，不改 provider 语义
  - [x] `NativeDoThemeExtras`：新增 `screenStyle` 读取入口，必要时补 `glass` 读取入口
  - [x] `NativeDoTheme()`：提供默认 `NativeDoScreenStyle`
  - [x] `NativeDoSystemBars()`：Phase A 保持透明 system bars 逻辑，不提前耦合真实 blur
- `NativeDoGlassSpec.kt`
  - [x] 新建 `BlurMode`
  - [x] 新建 `NativeDoGlassRole`
  - [x] 新建 `NativeDoGlassSpec`
- `NativeDoScreenStyle.kt`
  - [x] 新建 `NativeDoScreenStyle`
  - [x] 新建 `LocalNativeDoScreenStyle`
  - [x] 提供 `root / browser / reader` 默认 style factory 或等价 helper
  - [x] 聚合 `glass` 与 `system bar style`，避免 Phase B 再把 window 行为散回 feature
- `NativeDoSystemBarStyle.kt`
  - [x] 新建 `NativeDoSystemBarStyle`
  - [x] 明确 `statusBarStyle / navigationBarStyle / enforceNavigationBarContrast / backgroundPolicy`

### Batch A2：motion registry

- `Motion.kt`
  - [x] `NativeDoDuration`：保留现有 duration 常量，补齐缺的语义项
  - [x] `NativeDoEasing`：保留现有 easing，避免 feature 再私建 browser/home 专用 easing
  - [x] `NativeDoMotionPreset`：决定保留为轻量 facade，或改为转发到新 scheme
- `NativeDoMotionScheme.kt`
  - [x] 新建 `NativeDoMotionScheme`
  - [x] 新建 `LocalNativeDoMotionScheme`
  - [x] 新建语义分组：`chrome / label / popup / tab / sheet / fullscreen`
  - [x] 明确单一 `progress` 驱动多属性过渡的 helper 或约定

### Batch A3：primitive 核心第一批

- `NativeDoAnimatedLabel.kt`
  - [x] 新建 `NativeDoAnimatedLabel()`
  - [x] 明确 title / subtitle / badge 文本切换的 enter/exit 规则
- `NativeDoBackIcon.kt`
  - [x] 新建 `NativeDoBackIcon()`
  - [x] 明确 `back <-> close` 或 `collapsed <-> expanded` 的 morph 参数
- `NativeDoGlassBar.kt`
  - [x] 新建 `NativeDoGlassBar()`
  - [x] 约定 leading / title / trailing slot
  - [x] 约定 bar 默认 shape、padding、height、spec role

### Batch A4：primitive 核心第二批

- `NativeDoGlassSurface.kt`
  - [x] `NativeDoGlassSurface()`：从 `strong: Boolean` 升级为消费 `NativeDoGlassSpec`
  - [x] 为 Phase B 预留 material / blur mode 参数，但 Phase A 默认 `TintOnly`
- `NativeDoChromeIconButton.kt`
  - [x] `NativeDoChromeIconButton()`：改为按 spec role 或 screen style 决定容器材质
  - [x] `nativeDoChromeIconButtonContentAlpha()`：保留 enabled/emphasized 语义
  - [x] `nativeDoChromeIconButtonContainerColor()`：避免继续直接读取 `glassBackgroundStrong`
- `NativeDoGlassTab.kt`
  - [x] 新建 `NativeDoGlassTab()`
  - [x] 新建 tab selected / pressed / idle 的统一视觉语义
- `NativeDoPopupMenu.kt`
  - [x] 新建 `NativeDoPopupMenu()`
  - [x] 新建 `NativeDoPopupMenuItem()` 或等价 item API
  - [x] 新建 popup divider / cascade / anchor highlight 的统一入口

### Batch A5：root shell 抽离

- `NativeDoChromeScaffold.kt`
  - [x] 新建 `NativeDoChromeScaffold()`
  - [x] 约定 `background / topBar / bottomBar / bulletinHost / content / overlay` slot
- `NativeDoReaderScaffold.kt`
  - [x] 新建 `NativeDoReaderScaffold()`
  - [x] 先只做 reader chrome 容器空壳，不提前接 feature state
- `NativeDoApp.kt`
  - [x] `NativeDoApp()`：迁移 root 布局组织到 `NativeDoChromeScaffold`
  - [x] `NativeDoTopBar()`：先保留为 root scaffold slot content
  - [x] `NativeDoBottomBar()`：先保留为 root scaffold slot content
  - [x] `NativeDoBottomBarFade()`：决定是否并入 scaffold overlay
  - [x] `NativeDoChromeState`：保留现有计算职责
  - [x] `sessionSnapshotForChrome()`：保留现有 session 映射职责
  - [x] `onOpenLink`、`navController`、`rootPagerState`、`AuthRestoreGate`：明确不迁入 scaffold
- `Bulletin.kt`
  - [x] `BulletinHost()`：确认 host 仍由 app root 持有，只调整布局插槽，不改 message ownership

### Batch A6：共享控件接入

- `NativeDoSearchField.kt`
  - [x] `NativeDoSearchField()`：改为消费 `NativeDoGlassSpec` / `NativeDoScreenStyle`
  - [x] `shouldShowNativeDoSearchFieldPlaceholder()`：逻辑保持简单，不混入动画状态
  - [x] `nativeDoSearchFieldReadOnlyText()`：保持只负责文本表现
  - [x] `nativeDoSearchFieldImeAction()`：不因视觉层改动而改变输入行为
- `Bulletin.kt`
  - [x] `BulletinHost()`：接统一材质与 motion scheme
  - [x] `BulletinHostUiState` / `BulletinHostState`：不扩展成复杂状态机
  - [x] `bulletinIconContainerColor()` / `bulletinIconTint()`：改为跟随材质 token，而不是零散 lerp
- `NativeDoApp.kt`
  - [x] `NativeDoTopBar()`：接 `NativeDoGlassBar / NativeDoAnimatedLabel / NativeDoBackIcon`
  - [x] `NativeDoBottomBar()`：接 `NativeDoGlassTab`
  - [x] `NativeDoBottomTab()`：统一选中/按压/双击视觉节奏
  - [x] `topBarTitle()` / `themeModeLabel()`：继续只负责文案映射
  - [x] `shouldRequestHomeRefreshFromTitleTap()` / `shouldRequestHomeRefreshFromBottomTabDoubleTap()`：明确不改交互业务语义

### Batch A7：browser scaffold 迁移

- `AppBrowserScreen.kt`
  - [x] `appBrowserTopBarOffset()`：迁入 scaffold 或由 scaffold 等价替代
  - [x] `AppBrowserScreen()`：从“手写 overlay 结构”改为消费 `NativeDoBrowserScaffold`
- `NativeDoBrowserScaffold.kt`
  - [x] 新建 `NativeDoBrowserScaffold()`
  - [x] 约定 `pageContent / sheetContent / overlayContent / topBar / popupAnchor` slot
- `AppBrowserChrome.kt`
  - [x] `AppBrowserChromeConfig.toChromeModel()`：旧 config 兼容层已删除，改为直接消费 shell model
  - [x] `AppBrowserChromeConfig.toSearchUiState()`：旧 config 兼容层已删除，改为直接消费 shell model
  - [x] `AppBrowserChromeConfig.toAddressUiState()`：旧 config 兼容层已删除，改为直接消费 shell model
  - [x] `dispatchAppBrowserChromeAction()`：旧 config 兼容层已删除，action 继续留在 feature 装配层
  - [x] `AppBrowserTopBar()` 两个重载：改为装配共享 primitive
  - [x] `AppBrowserAddressScene()`：接统一 bar / label / field 语义
  - [x] `AppBrowserChromeButton()`：优先复用 `NativeDoChromeIconButton`
  - [x] `AppBrowserTitlePill()`：优先复用 `NativeDoAnimatedLabel` / `NativeDoGlassBar`
  - [x] `AppBrowserSearchBar()`：优先复用 `NativeDoSearchField`
  - [x] `AppBrowserBackCloseGlyph()`：优先被 `NativeDoBackIcon` 替代
  - [x] `AppBrowserHistoryPopup()` / `AppBrowserActionPopup()`：优先被 `NativeDoPopupMenu` 覆盖
  - [x] `AppBrowserPopupDivider()` / `AppBrowserPopupItem()` / `AppBrowserHistoryPopupItem()`：只保留 browser 特有内容块
  - [x] `rememberAppBrowserPopupPalette()`：已由 `NativeDoPopupMenu` 共享 palette 派生替代
  - [x] `appBrowserPopupOpenDuration()` / `appBrowserPopupCascadeProgress()`：逐步迁到 motion scheme
- `AppBrowserRoute.kt`
  - [x] `AppBrowserRoute()`：只负责 wiring、WebView 协调、sheet/popup 状态持有
  - [x] `readAppBrowserHistoryEntries()` / `recordAppBrowserVisit()`：明确不属于本轮 UI 基座改动
  - [x] `PendingSslRequest`、`AppBrowserViewModelFactory`：明确不属于本轮 UI 基座改动

### Batch A8：home 轻量接入

- `HomeRoute.kt`
  - [x] `HomeRoute()`：把 search mode 的 enter/exit 改到统一 motion scheme
  - [x] `HomeFeedContent()`：只接收新的视觉 token，不改 feed 数据流
  - [x] `HomeSearchEntry()`：对齐 search field / list item 的节奏
  - [x] `HomeFeedbackCard()`：决定是否只做 token 对齐，不额外玻璃化
  - [x] `homeSearchCollapseProgress()`：保留 raw scroll progress，chrome 展示态由 `buildHomeSearchChromeState()` 统一派生
  - [x] `shouldShowHomeHeaderSearch()` / `shouldLoadMoreHomeFeed()` / `shouldScrollHomeFeedToTopOnRefreshRequest()`：明确不改业务判断
  - [x] `HomeViewModelFactory` / `shouldRefreshHomeForHandledAuthVersion()`：明确不属于本轮 UI 基座改动
- `HomeLoadingSkeleton.kt`
  - [x] `HomeTopicSkeletonItem()`：对齐共享 skeleton 视觉
  - [x] `rememberHomeSkeletonBrush()`：对齐统一 shimmer/motion 节奏
  - [x] `homeSkeletonPalette()`：迁到统一材质 palette 来源
  - [x] `HomeSkeletonBlock()`：如抽公共 primitive，保留为最小 wrapper 或删除
- `TopicListItem.kt`
  - [x] `TopicListItem()`：只调整 typography / spacing / divider / badge rhythm
  - [x] `TopicCategoryBadge()`：只对齐 pill token，不改分类业务含义
  - [x] `TopicStatsRow()` / `TopicStat()`：只对齐 icon/text 节奏
  - [x] `topicTagLine()` / `topicActivityLine()` / `topicRelativeTime()`：明确不改内容生成逻辑
  - [x] `discourseCategoryColor()`：继续保留 category 色来源，不强行收编为 glass token

---

## Phase B：真实 glass substrate

### 目标

- 为 chrome 引入 backdrop blur / liquid glass substrate
- 保持 Compose 为主，允许小范围 Android interop
- 默认只在 chrome 区域启用

### Task 1：建立 backdrop host 协议

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/backdrop/NativeDoBackdropRegistry.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/backdrop/NativeDoBackdropNode.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/backdrop/NativeDoBackdropHost.kt`

**Step**

- [ ] 定义 glass node 注册协议
- [ ] 允许 composable 通过 bounds 注册自己
- [ ] Host 只负责内容层 backdrop 管理

**Expected**

- 不依赖 feature 自己做 blur 计算

### Task 2：实现 Android substrate

**Files**

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/backdrop/NativeDoGlassEngine.android.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoSystemBarStyle.kt`
- Modify: `apps/android/app/src/main/res/values/themes.xml`
- Modify: `apps/android/app/src/main/res/values-night/themes.xml`

**Step**

- [ ] 采用双层 root host：scene layer + overlay layer
- [ ] backdrop capture 仅抓 scene，不抓 overlay
- [ ] 支持低分辨率截图、blur、材质裁切
- [ ] 把 `Theme.kt` 里的 `NativeDoSystemBars()` 从“无条件副作用”改成读取 `screen style` 的 system bar effect
- [ ] 让 root/browser/reader 可以分别声明 icon 明暗、透明策略和 contrast policy

**Expected**

- overlay glass surface 可以读取真实 backdrop
- [ ] backdrop host 与 system bar 行为不再互相抢 window 配置

### Task 3：让 glass surface 接入 substrate

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoGlassSurface.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBackground.kt`

**Step**

- [ ] `NativeDoGlassSurface` 增加 material / blur mode 支持
- [ ] 在 `TintOnly / Blurred / Liquid` 之间切换
- [ ] 保持 Phase A fake glass 作为降级路径

**Expected**

- glass surface 成为统一入口，不区分是否真实 blur

### Task 4：只在 chrome 范围启用

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/Bulletin.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoSearchField.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoPopupMenu.kt`

**Step**

- [ ] root top bar / bottom bar 接入 backdrop
- [ ] browser chrome 接入 backdrop
- [ ] bulletin / search field / popup menu 接入 backdrop
- [ ] 正文、feed item、post body 不接入

**Expected**

- UI 材质明显提升，但阅读主体仍保持克制

### Task 5：性能降级与开关

**Files**

- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/NativeDoScreenStyle.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/backdrop/NativeDoBackdropPerformance.kt`

**Step**

- [ ] 根据 Android 版本和性能条件做降级
- [ ] 提供 `TintOnly / Blurred / Liquid` 三档
- [ ] 滚动中、过渡中、低端机可退回轻量方案

**Expected**

- 不把真实 blur 强制应用到所有设备和所有时刻

### Task 6：Phase B 验证 spike

**Files**

- Test: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/backdrop/*`

**Step**

- [ ] root top bar / bottom bar 先完成第一轮试点
- [ ] 验证 popup 和 browser chrome 的表现
- [ ] 如性能不可接受，保持接口但默认关闭真实 blur

**Expected**

- backdrop substrate 是否继续深入，有明确结论

### Phase B 验收标准

- [ ] root chrome 可切换 `TintOnly / Blurred / Liquid`
- [ ] browser chrome 可使用真实 backdrop
- [ ] popup / bulletin / search field 可复用 backdrop substrate
- [ ] 正文和 feed 主体不参与 backdrop capture
- [ ] 性能不足时可稳定降级回 Phase A fake glass

---

## 实施顺序建议

严格建议按以下顺序推进：

1. Phase A / Task 1
2. Phase A / Task 2
3. Phase A / Task 3
4. Phase A / Task 4
5. Phase A / Task 5
6. Phase A / Task 6
7. Phase A / Task 7
8. Phase A / Task 8
9. Phase B / Task 1
10. Phase B / Task 2
11. Phase B / Task 3
12. Phase B / Task 4
13. Phase B / Task 5
14. Phase B / Task 6

原因：

- 先有 theme / motion / primitive / shell，backdrop substrate 才有稳定接入点
- 先让 browser 成为基座示范页，后续再扩到更多 feature
- Phase B 只适合在 Phase A 结构稳定之后做

---

## 文档验证

本次为 docs-only 记录，验证方式限定为：

- [ ] 文档命名、路径和当前仓库结构一致
- [ ] Phase A / Phase B 的边界清晰且不互相混淆
- [ ] 文件级改动范围与当前 Compose 架构相容

---

## 结论

后续推进应以 `Phase A` 为主线：

- 先统一 Compose 基座
- 再决定是否进入真实 blur substrate

只有这样，NativeDo 才能在不失控的前提下，逐步靠近 Telegram 的 UI 结构与材质表现。
