## Why

当前 NativeDo Android 客户端基于 Kotlin + Jetpack Compose 构建，使用 Material 3 组件和 Compose Navigation。这套技术栈在性能关键场景（长列表滚动、复杂手势转场、Canvas 绘制密集的帖子渲染）中存在根本性瓶颈，无法达到 Telegram 级别的 60fps 流畅体验。

本次完全重写以 Telegram Android 开源代码库为架构蓝本，用 Kotlin 重新实现其核心基础设施（自定义导航栈、主题引擎、Canvas Cell 渲染、手势系统），同时构建 Discourse 论坛客户端特有的业务层（cookie session、cooked HTML 原生渲染、帖子流分页）。目标是打造一个在交互品质上对标 Telegram、在功能覆盖上满足 linux.do 用户日常使用的原生客户端。

## Execution Status

`full-rewrite-telegram-parity` 现在保留为 umbrella reference，用来说明总体目标、原始范围和历史 backlog。自 `split-telegram-parity-phases` 起，新的实现工作必须进入对应的 phase batch，而不是继续把本 change 当作唯一执行清单自由铺开。

## What Changes

### 架构层面
- **BREAKING**: 废弃 Compose Navigation，改用 Telegram 风格的 `ActionBarLayout`（手写导航栈 + 手势驱动转场）
- **BREAKING**: 废弃 Material 3 组件库，所有 UI 组件自定义绘制（ActionBar、Cell、BottomSheet、TabBar 等）
- **BREAKING**: 废弃 `NativeDoTheme` / `NativeDoThemeExtras`，改用 Telegram 风格 color-key 映射主题引擎
- **BREAKING**: 废弃 Hilt/Dagger DI，改用 Telegram 风格的轻量级手动依赖管理（`AccountInstance` 模式）
- **BREAKING**: 重组包结构为 `core/` + `ui/` + `discourse/` 三层
- **BREAKING**: `feature/refactor` 分支直接在 `apps/android` 上做 clean-slate 重构，不保留仓库内 `android-bak` 或 legacy/rewrite 双 root；旧实现以 `main` 分支作为快照溯源

### 基础设施层（core/）
- 新增 `NdApp.kt` — 应用级 bootstrap / service locator 入口，对标 Telegram `ApplicationLoader` 的职责边界，但只保留单站点客户端所需的初始化
- 新增 `LaunchBootstrap.kt` — 冷启动首屏决策入口，对标 Telegram `LaunchActivity` 启动职责的精简版，负责触发 session restore 并决定先进入 Topics 还是 Login
- 新增 `AndroidUtilities.kt` — dp/lerp/屏幕指标等全局工具
- 新增 `Utilities.kt` / `DispatchQueue.kt` / `FileLog.kt` — 轻量线程与日志基元，对标 Telegram `Utilities` / `DispatchQueue` / `FileLog`，但不引入 native/加密/MTProto 相关能力
- 新增 `CubicBezierInterpolator.kt` / `AnimatedFloat.kt` — 动画基元
- 新增 `NotificationCenter.kt` — 全局事件总线（替代部分 Flow 事件）
- 新增 `ImageReceiver.kt` — 图片加载/缓存管道（可基于 Coil 3 底层实现）
- 新增 `DiscourseClient.kt` — OkHttp + Retrofit 统一请求入口
- 新增 `CookieSessionStore.kt` — `_t` / `_forum_session` / `cf_clearance` 生命周期管理
- 新增 `AuthSessionManager.kt` — cookie 驱动的鉴权状态机，负责 login completion、session restore、logout、expired/recovery 流程；命名上对齐 Telegram 风格，不使用 `Coordinator`
- 新增 `CsrfTokenProvider.kt` — `/session/csrf` + bootstrap HTML 双源 CSRF
- 新增 `CloudflareInterceptor.kt` — CF 拦截检测 + WebView 降级通道
- 新增 `BootstrapParser.kt` — 首页 HTML 解析（用户身份/CSRF/分类数据）
- 新增 `TopicStreamPager.kt` — Discourse `post_stream.stream` 分段加载器
- 新增最小 `LinkManager.kt` — 只负责 URL 分类（topic/user/app browser/external/system），不承担 screen router 职责
- 新增共享 WebView 基础设施 — 登录页、内置浏览器、RawHtml fallback 共用的 WebView settings/factory/cookie 同步基元

### UI 框架层（ui/）
- 新增 `ActionBarLayout.kt` — 导航栈引擎（swipe-back、自定义转场、页面内存管理）
- 新增 `BaseFragment.kt` — 页面基类（生命周期、ActionBar 绑定、主题感知）
- 新增 `ActionBar.kt` — 自定义顶栏（标题、返回按钮、菜单、搜索模式）
- 新增 `Theme.kt` — color-key 主题引擎（运行时切换、Light/Dark/System 三模式）
- 新增 `LayoutHelper.kt` — Frame/Linear/ScrollView 的轻量布局参数工厂，对标 Telegram `LayoutHelper`
- 新增 `SimpleTextView.kt` — 轻量文本绘制组件，对标 Telegram `SimpleTextView`
- 新增 `RecyclerListView.kt` — 增强型 RecyclerView（sectioned adapter、快速滚动、item 动画）
- 新增 `BaseCell.kt` — Canvas 绘制的列表项基类
- 新增 `BottomSheet.kt` — 底部弹窗框架
- 新增 `AvatarDrawable.kt` — 头像绘制（首字母/颜色/Discourse trust level 徽章）
- 新增 `CookedHtmlRenderer.kt` — Discourse cooked HTML → 原生 Canvas 渲染块

### 功能页面层（feature/）
- 重建 `TopicsFragment` — 首页话题列表（Latest/New/Top 三 tab + 下拉刷新 + 分页）
- 重建 `TopicDetailFragment` — 帖子详情（基于 CookedHtmlRenderer 的原生渲染）
- 重建 `BrowserFragment` — 内置浏览器（参考 Telegram `web/` 包）
- 重建 `ProfileFragment` — 个人中心
- 重建 `SearchFragment` — 搜索页
- 重建 `LoginFragment` — WebView 登录（保持 cookie-based 方案），与 `BrowserFragment` 分离为独立 scene，但复用同一套 WebView 基础设施

### Compose 保留范围
- 设置页面表单
- 简单弹窗内容
- 非性能关键的静态 UI
- 通过 `ComposeView` 嵌入 Fragment 内使用

## Capabilities

### New Capabilities
- `core-utilities`: 全局工具类（dp 转换、lerp 插值、屏幕指标、颜色工具、数学工具），对标 Telegram `AndroidUtilities`
- `animation-primitives`: 动画基元（CubicBezierInterpolator、AnimatedFloat、AnimatedColor、弹簧动画），对标 Telegram 动画工具集
- `event-bus`: 全局事件总线 NotificationCenter，类型安全的 publish-subscribe 模式，对标 Telegram `NotificationCenter`
- `theme-engine`: color-key 映射主题引擎，支持 Light/Dark/System 三模式运行时切换，自定义调色板，对标 Telegram `Theme`
- `navigation-stack`: 手势驱动的自定义导航栈 ActionBarLayout，支持 swipe-back、自定义转场动画、页面栈管理，对标 Telegram `ActionBarLayout` + `BaseFragment`
- `action-bar`: 自定义顶栏组件，支持标题/副标题、返回按钮、菜单项、搜索模式、动画过渡，对标 Telegram `ActionBar`
- `recycler-list-view`: 增强型列表组件，sectioned adapter、快速滚动条、item 进入动画、边缘效果，对标 Telegram `RecyclerListView`
- `canvas-cells`: Canvas 绘制的高性能列表项体系（BaseCell、TopicCell、UserCell、DividerCell 等），对标 Telegram `Cells/`
- `bottom-sheet`: 自定义底部弹窗框架，手势拖拽、嵌套滚动、多档位停靠，对标 Telegram `BottomSheet`
- `avatar-drawable`: 头像绘制系统（首字母头像、颜色映射、圆角裁剪、Discourse trust level 徽章），对标 Telegram `AvatarDrawable`
- `image-pipeline`: 图片加载管道（内存/磁盘缓存、渐进加载、圆角/模糊变换），可基于 Coil 3 底层
- `discourse-client`: Discourse API 统一客户端（Retrofit service 定义、请求拦截、错误标准化）
- `cookie-session`: Cookie-based 会话管理（`_t`/`_forum_session`/`cf_clearance` 持久化、OkHttp-WebView 双向同步、过期检测）
- `csrf-provider`: CSRF token 双源供给（`/session/csrf` API 优先 + bootstrap HTML fallback）
- `cloudflare-guard`: Cloudflare 拦截检测与 WebView 降级通道
- `bootstrap-parser`: Discourse 首页 HTML 解析器（当前用户身份、CSRF token、站点分类数据提取）
- `cooked-html-renderer`: Discourse cooked HTML 原生渲染引擎，将 `<p>`/`<pre>`/`<blockquote>`/`<img>`/`<a>`/emoji 等 HTML 块转为 Canvas 绘制的原生渲染块，对标 Telegram `ArticleViewer` 的 Instant View 思路
- `topic-stream-pager`: Discourse 帖子流分段加载器（基于 `post_stream.stream` ID 数组的 chunk fetch 分页模型）
- `discourse-models`: Discourse 数据模型集（TopicSummary、TopicDetail、Post、CategorySummary、UserProfile、SearchResult、AppError），kotlinx.serialization 序列化
- `offline-cache`: 离线缓存层（Room 数据库、已读话题/帖子缓存、分类数据缓存）
- `topics-screen`: 首页话题列表页（Latest/New/Top 三 tab、下拉刷新、分页加载、Canvas TopicCell 渲染）
- `topic-detail-screen`: 帖子详情页（CookedHtmlRenderer 原生渲染、帖子流分段加载、楼层跳转、回复引用折叠）
- `browser-screen`: 内置浏览器页（WebView 容器、自定义 ActionBar、阅读模式、前进后退手势），对标 Telegram `web/`
- `profile-screen`: 个人中心页（头像/等级/徽章展示、用户统计、最近活动列表）
- `search-screen`: 搜索页（话题搜索 + 用户搜索、搜索结果列表）
- `login-screen`: WebView 登录页（cookie 抓取、会话同步、Cloudflare challenge 通过）

### Modified Capabilities
_无现有 spec，首次全量创建。_

## Impact

### 代码影响
- `feature/refactor` 分支中的 `apps/android/app/src/main/java/io/github/baba983/nativedo/` 将被 clean-slate 重建；旧实现不在当前工作树内长期共存，回溯依赖 `main` 分支与 git 历史
- 现有 `core/`、`feature/`、`ui/`、`navigation/` 包将被重组
- 现有 Compose UI 组件（`NativeDoGlassSurface`、`NativeDoPullToRefreshIndicator`、`HtmlPostBodyView` 等）将被新的 View/Canvas 实现替代

### 依赖变更
- 移除：Compose Navigation、Material 3 组件库、Hilt/Dagger（如有）
- 保留：OkHttp、Retrofit、kotlinx.serialization、Coil 3、Jsoup、Room
- 保留：Compose runtime（仅用于 ComposeView 嵌入场景）
- 新增：可能需要 Lottie（动画资源播放）

### 外部系统
- Discourse API 端点不变（`contracts/discourse/` 中定义的所有端点保持不变）
- Cookie session 流程不变（`auth-flow.md` 中定义的流程保持不变）
- 构建系统：Gradle 配置需更新 `compileSdk`/`minSdk`/依赖声明

### 风险
- 工作量巨大，需要严格分 phase 推进，每个 phase 应产出可运行的 APK
- ActionBarLayout 移植是最大技术风险点（3500 行核心导航逻辑的 Kotlin 化）
- CookedHtmlRenderer 是最大产品风险点（Discourse HTML 多样性远超 Telegram Instant View）
- clean-slate 切换后缺少仓库内并存 fallback，需要依赖 `main` 分支快照与阶段性可运行 APK 作为回退保障
