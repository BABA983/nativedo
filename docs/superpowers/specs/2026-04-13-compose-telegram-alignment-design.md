# NativeDo Compose Telegram 对齐设计

**日期：** 2026-04-13
**状态：** 已确认，待实施
**目标：** 以 Jetpack Compose 为主实现路径，为 NativeDo 建立一套更接近 Telegram 的 UI 基座；优先对齐 Telegram Android 的容器结构、动效节奏和原子组件，再在第二阶段为 chrome 补上更真实的 liquid glass 背景能力。

---

## 背景

当前 Android 客户端已经具备一层可用的 Telegram-inspired glass 方向：

- 已有 `NativeDoTheme`、`NativeDoThemeExtras` 和 `System / Light / Dark` 主题模式
- 已有基础 glass surface、root top bar / bottom bar、browser chrome、bulletin、search field
- 已有一部分接近 Telegram 的 motion token 和 media viewer / browser 交互能力

但当前 UI 基座仍然偏“单点实现”：

- 主题扩展还停留在颜色语义层，没有 screen-level style override
- motion token 已存在，但复杂动画仍主要散落在 feature 内
- 原子组件还不完整，缺少 `AnimatedLabel`、`BackIcon`、`GlassTab`、`PopupMenu`、`Cell`、`Skeleton` 这一层
- shell 层尚未独立，root chrome、browser chrome、reader chrome 还没有统一容器抽象
- 现有 glass 更多是视觉近似，不是 Telegram 近期开启的真实 backdrop / liquid glass 基座

因此，这次设计不再继续按单页临摹推进，而是把目标收敛为分阶段基座建设。

---

## 目标

- 以 Compose 为主，建立统一的 UI 基座
- 优先复刻 Telegram Android 的容器结构、动效节奏和 primitive 层
- 仅将 liquid glass 重点覆盖在 chrome，而不是正文或 feed 主体
- 为真实 backdrop glass 预留第二阶段演进空间
- 保持现有导航、业务状态和 ViewModel 边界基本稳定

## 非目标

- 不复制 Telegram GPL 代码、类层级、命名或内部实现
- 不把全 app 所有内容容器玻璃化
- 不在第一阶段实现真实 backdrop blur
- 不在本轮引入大规模业务重构或模块拆分
- 不把 Compose 改造成高度依赖 View 系统的混合 UI

---

## 设计原则

### 1. Compose-first

默认所有 theme、motion、primitive、shell 都使用 Compose 实现。

只有“真实 backdrop capture / blur substrate”这一层允许使用小范围 Android interop。

### 2. Android 结构优先，iOS glass 只覆盖 chrome

复刻目标拆成两部分：

- 交互结构、容器组织、动画节奏，以 Telegram Android 为主
- 材质和液态玻璃质感，只在 top bar、bottom bar、popup、sheet、search field、bulletin、browser chrome 等 chrome 区域向 Telegram iOS 近况靠拢

### 3. 先统一基础层，再翻 feature

先做：

- theme registry
- motion registry
- primitive layer
- shell layer

后做：

- browser
- media viewer
- root tabs
- search
- profile
- topic
- home

### 4. 玻璃只用于 chrome

允许使用 glass 的区域：

- root top bar
- root bottom bar
- popup menu
- search field
- bulletin
- sheet header / footer
- browser chrome
- media viewer chrome
- floating anchor chip

不建议使用 glass 的区域：

- topic feed 主体 item
- 帖子正文大面积背景
- 长文阅读主容器
- 普通列表主体

### 5. GPL 边界明确

本项目只能借鉴 Telegram 的：

- 信息架构
- 组件职责划分
- 动效节奏
- 容器结构
- 主题语义和视觉层次

不能复制：

- Java / Swift 源码
- Telegram 特有类层级
- 内部实现细节
- 原始资源和命名

---

## 目标架构

```text
MainActivity
└── NativeDoRoot
    ├── Theme Layer
    │   ├── NativeDoTheme
    │   ├── NativeDoThemeExtras
    │   ├── NativeDoScreenStyle
    │   └── NativeDoGlassSpec
    ├── Motion Layer
    │   └── NativeDoMotionScheme
    ├── Backdrop Layer
    │   ├── Phase A: placeholder only
    │   └── Phase B: real backdrop host
    ├── Shell Layer
    │   ├── NativeDoChromeScaffold
    │   ├── NativeDoBrowserScaffold
    │   ├── NativeDoReaderScaffold
    │   └── NativeDoSheetScaffold
    └── Primitive Layer
        ├── NativeDoAnimatedLabel
        ├── NativeDoBackIcon
        ├── NativeDoGlassBar
        ├── NativeDoGlassTab
        ├── NativeDoPopupMenu
        ├── NativeDoCell
        ├── NativeDoFlickerSkeleton
        ├── NativeDoSearchField
        └── BulletinHost
```

---

## 分层设计

### Theme Layer

现有 `NativeDoExtendedColors` 继续保留，但需要再补一层 screen-level style。

建议新增：

- `NativeDoGlassSpec`
- `BlurMode`
- `NativeDoScreenStyle`
- `NativeDoSystemBarStyle`

职责：

- 将 `glassBackground / stroke / highlight / noise / shadow` 从单纯颜色扩展成材质语义
- 允许 Browser、MediaViewer、Reader 在不改全局主题的前提下覆盖局部风格
- 将目前散落在 feature 中的 alpha、强弱玻璃、描边差异收口到统一 token
- 将 status bar / navigation bar 的透明策略、icon 明暗和 contrast policy 从 `Theme.kt` 的硬编码副作用中拆出来，收口到 screen-level style

### Motion Layer

已有 `NativeDoDuration` 和 `NativeDoEasing` 是一个良好开端，但需要从“数值常量”升级为“统一交互语义”。

建议目标：

- 简单状态变化使用统一 color / alpha / scale preset
- 复杂容器变化统一由单一 `progress` 驱动
- browser、media viewer、popup、tab、sheet 的节奏从同一组 motion preset 派生

优先统一的 motion 语义：

- chrome reveal / dismiss
- popup open / close
- label swap
- tab select
- sheet enter / exit
- fullscreen enter / exit

### Primitive Layer

本次应把以下组件视为“Compose 版 Telegram primitive”：

- `NativeDoAnimatedLabel`
- `NativeDoBackIcon`
- `NativeDoGlassBar`
- `NativeDoGlassTab`
- `NativeDoPopupMenu`
- `NativeDoCell`
- `NativeDoFlickerSkeleton`

其中优先级最高的三个：

1. `NativeDoAnimatedLabel`
2. `NativeDoBackIcon`
3. `NativeDoGlassTab`

原因：

- 当前大量标题、badge、tab label 仍依赖普通 `Text`
- 当前 back / close 仍主要依赖 Material 默认 icon
- 当前底栏 tab 逻辑仍停留在 app shell 内部，不够 primitive 化

### Shell Layer

Shell 不是页面，而是 feature 共享的 chrome 容器。

建议目标：

- `NativeDoChromeScaffold`：root top bar / bottom bar / background / bulletin
- `NativeDoBrowserScaffold`：browser 顶栏、address/search scene、menu、history / settings 子面板
- `NativeDoReaderScaffold`：适合 Topic / Profile / 长内容阅读页
- `NativeDoSheetScaffold`：后续用于 address sheet、menu panel、历史页等

设计重点：

- shell 负责视觉组织和 transition
- feature 负责 state mapping 和事件处理
- ViewModel 边界保持不变

### Backdrop Layer

Phase A 不实现真实 blur，只提供空壳和接口预留。

Phase B 再进入真实 backdrop substrate：

- 单独建立 `BackdropHost`
- glass node 用 bounds 注册
- backdrop 只捕获内容层，不捕获 overlay 自身
- 仅为 chrome 输出 blur / liquid glass 纹理
- system bar effect 必须读取 `NativeDoScreenStyle` / `NativeDoSystemBarStyle`，不能继续由 `NativeDoTheme()` 无条件写死 window 配置

---

## Phase A / Phase B 拆分

### Phase A：Compose 基座统一

目标：

- 不做真实 blur
- 先统一 theme、motion、primitive、shell
- 让 app 在 fake glass 条件下先具备 Telegram 风格结构

交付后应达到：

- root chrome 使用共享 shell + primitive
- browser chrome 使用共享 shell + primitive
- search / bulletin / popup / tab 使用统一视觉语义
- feature 页面主要只保留内容与状态映射

### Phase B：真实 glass substrate

目标：

- 只为 chrome 补上 backdrop blur / liquid glass
- 仍然不将正文或 feed 主体纳入 backdrop capture

交付后应达到：

- top bar / bottom bar / popup / bulletin / browser chrome 可切换 `TintOnly / Blurred / Liquid`
- 支持性能降级
- backdrop capture 与 Compose 结构解耦

---

## 与现有代码的关系

本设计默认以当前这些文件为第一批改造入口：

- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Color.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Motion.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoGlassSurface.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoSearchField.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/Bulletin.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/NativeDoChromeIconButton.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`

其中：

- `Theme.kt / Color.kt / Motion.kt` 负责提供新基座
- `NativeDoGlassSurface.kt` 负责接入 glass spec 和 Phase B substrate
- `NativeDoApp.kt` 后续会被拆薄，转为 root 装配层
- `AppBrowserChrome.kt` 是最优先改造成共享 shell 的 feature

---

## 风险与权衡

### 1. 过早进入真实 blur

风险：

- 开发成本高
- 性能风险大
- 容易把注意力从 primitive / shell 抽象上带偏

结论：

- 必须延后到 Phase B

### 2. 过度玻璃化

风险：

- feed 和正文可读性下降
- 整体噪音比 Telegram 更高

结论：

- glass 只用于 chrome，不用于正文主体

### 3. 基座抽象不够彻底

风险：

- 看起来“像 Telegram”的部分会继续停留在 feature 私有实现
- 后续各页面又重新分叉

结论：

- Phase A 的重点必须是 primitive 和 shell，不是单页翻新

---

## 结论

对 NativeDo 来说，最合理的 Compose 路线不是“每页都做得像 Telegram”，而是：

1. 先用 Compose 建立 Telegram 风格的 `theme + motion + primitive + shell`
2. 再用一个小型 Android interop substrate 给 chrome 提供真实 liquid glass

这也是当前项目在风险、收益和长期维护性之间最均衡的路径。
