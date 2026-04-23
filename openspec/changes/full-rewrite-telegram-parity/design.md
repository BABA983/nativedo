## Context

NativeDo 是 linux.do（基于 Discourse 的中文技术社区）的原生 Android 客户端。当前 v1 基于 Kotlin + Jetpack Compose + Material 3 + Compose Navigation 构建，已实现话题列表、帖子详情、搜索、个人中心、WebView 登录等核心功能。

当前实现的核心痛点：
1. **Compose LazyColumn 在复杂帖子列表中性能不足**：帖子包含 cooked HTML（代码块、引用、图片、emoji），Compose 的 recomposition 开销大
2. **Compose Navigation 不支持手势驱动转场**：无法实现 Telegram 级别的 swipe-back 和连续手势拖拽
3. **Material 3 组件无法定制到 Telegram 的交互精度**：动画曲线、触摸反馈、长按菜单等都受限于 M3 的预设行为
4. **Discourse 的 cookie-based session 和 Cloudflare 防护增加了网络层复杂度**

Telegram Android 开源代码库（GPL v2）提供了一套经过 10 年打磨的高性能 Android UI 框架，其核心设计（自定义导航栈、Canvas Cell 渲染、color-key 主题引擎）可以被移植到 Kotlin 中，作为 NativeDo 的基础设施。

### Execution Status

本 design 继续作为 rewrite 总体设计参考，但不再单独承担执行切分职责。自 `split-telegram-parity-phases` 起，后续实现必须遵循 Phase 0 audit、phase batch、strict parity governance 和 gate 顺序，不再按单一大 backlog 自由推进。

### 约束
- **语言**：Kotlin（不回退到 Java）
- **最低 SDK**：API 28 (Android 9)
- **编译 SDK**：35
- **JVM**：17
- **许可证**：Telegram 源码 GPL v2，移植代码需遵守
- **后端**：linux.do 是标准 Discourse 站点，API 端点固定
- **认证**：WebView cookie-based，不可改变
- **约束**：在有 Telegram 文件可以参考的情况下，不要自由发挥

## Goals / Non-Goals

**Goals:**
- 将 Telegram 的核心 UI 框架（导航、主题、列表、动画）移植为 Kotlin 实现，作为 NativeDo 的基础设施层
- 构建 Discourse 专用的业务基础设施（session、HTML 渲染、帖子流分页）
- 在新框架上重建所有功能页面，达到 Telegram 级别的交互流畅度
- 保留 Compose 作为非性能关键场景的 UI 工具（设置页、弹窗内容等）
- 每个 Phase 产出可运行的 APK，支持渐进式迁移

**Non-Goals:**
- 不做“整仓无差别”的 Telegram 1:1 Java→Kotlin 机械搬运；但对当前 change 明确 adopt 的 Telegram substrate，采用 parity-first 迁移：优先保持同名类/方法的公开 API 形状与行为语义，只裁剪当前未使用的子集
- 不以“参考 Telegram 思路”为名重新包装同名基础类；若因为 Android 平台限制、linux.do 场景裁剪、或 Telegram 的 native/MTProto/多账号依赖而必须偏离，必须先在 change artifacts 中写明偏离理由，再实现
- 不实现 Telegram 的消息/通话/故事等通讯功能
- 不引入 Telegram 的 native C++ 层（tgnet、SQLite JNI）
- 不支持多账号（Discourse 单站点客户端，不需要 Telegram 的 `AccountInstance` 多实例模式）
- 不实现发帖/回帖等写操作（v1 scope 限制）
- 不做 iOS 端（本次仅 Android）

## Decisions

### D0: Telegram parity-first for adopted substrate APIs

**选择**：对当前 change 中明确采用 Telegram 同名类/方法的基础设施，默认按 Telegram 源码的公开 API 形状与行为语义直接移植；“受 Telegram 启发但自由改写”不再作为默认路径。

**硬规则**：
- 若当前阶段决定采用 Telegram 的同名类/方法，就优先直接 port 对应公开 API；可以只引入当前真正需要的子集，但不能把同名 API 改造成项目私有 convenience wrapper
- 若 Telegram 没有对应公共类，且当前 phase 里也没有真实调用方，不预先新造 `*Utils` / `*Helper` / wrapper 文件来“补齐底座”
- 若确实需要偏离 Telegram 语义，必须先在 artifacts 里写明偏离原因、边界和受影响类，再允许实现
- 后续任务评审以"是否仍然对齐 Telegram 对应 API"作为默认标准，而不是"看起来功能差不多即可"

**`Telegram-derived` / `Telegram-inspired project-specific` 分类与 GPL 边界**：
- 对当前 change 中明确采用 Telegram 同名类/方法并以其公开 API 作为默认契约的基础设施，默认视为 `Telegram-derived`，而不是“仅参考 Telegram 思路”
- `Telegram-derived`：直接 Java→Kotlin 翻译、结构保持的改写、或按 Telegram 同名公开 API 做 parity-first port 的文件；每个文件都要使用 repo 内的 Telegram-derived GPL v2 文件头模板，并写明 upstream class/path、source reference、Telegram + NativeDo copyright、SPDX
- `Telegram-inspired project-specific`：只借鉴 Telegram 的职责划分、宿主边界或架构思路，但实现本身是为 NativeDo 场景新写的文件；这类文件不默认使用 derived 文件头，也不宣称来自某个 Telegram 同名实现
- 混合文件按“从严”处理：只要文件内存在实质性的 Telegram 译写/摘录实现块，就按 `Telegram-derived` 处理
- 若某个同名类想被视为 project-specific 而不是 derived，必须先在 artifacts 中显式写明理由和边界

**当前 adopted 子集边界（1.x / 2.x / 3.x audit 修订）**：
- `AndroidUtilities`：当前 phase adopt 的不仅是 `dp` / `dpf2` / `lerp` / display metrics / keyboard helpers，也包括 Telegram 风格的 UI-thread scheduling 入口 `runOnUIThread()` / `cancelRunOnUIThread()`
- `Utilities`：当前 phase 只保留数学与少量 non-UI-thread helper（例如 `clamp()` / `clamp01()`）；不再把 Telegram 的 UI-thread scheduling 契约迁入 `Utilities`
- `DispatchQueue`：当前 adopt 的是 Telegram 的命名串行队列 public surface，本 change 不再额外包一层项目私有 queue helper
- `FileLog`：当前 adopt 的是 Telegram 风格日志基元；对于 adopted subset，优先保留 Telegram 主入口 API 形状。若未来确实需要 NativeDo-only 的 tag-first overload，需先在 artifacts 中写明它是附加能力而非替代默认入口
- `LayoutHelper`：Phase 0 adopted subset 明确为 `MATCH_PARENT`、`WRAP_CONTENT`，以及当前真实调用方需要的 `createFrame(...)`、`createLinear(...)`、`createScroll(...)` 家族；`createRelative()`、`measureSpecExactly*()`、gravity helper 等未 adopt 成员保持 deferred，直到真实 caller 出现再引入
- `CubicBezierInterpolator`：当前 artifacts 至少要求对齐 Telegram 的预置常量集合与当前真实调用方所需构造入口；若后续要省略或补回更多 public 成员，需先在 artifacts 中列出 adopted/excluded member 边界

**Java→Kotlin 适配分级规则**（详见 `specs/core-utilities/spec.md` "Java-to-Kotlin adaptation" requirement）：
- **语法级**（`object`、`apply{}`、`val/var`、expression body）：自由使用，不需文档
- **类型级**（Android 继承层级里的 layout params 等在 Kotlin 中仍指向同一 SDK concrete type）：可保持等价类型，不改变调用方语义
- **重载折叠**（Kotlin default params 替代 Java overload fan）：仅在参数类型完全一致时允许折叠；若 Java 用不同参数类型区分行为（如 `int` margin vs `float` margin），必须保留为独立函数
- **平台级**（移除 Telegram 独有依赖如 `LocaleController`、`AccountInstance`）：需在 artifacts 中文档记录移除内容和填补方案
- **语义级**（改返回类型、加副作用、改参数含义、合并不同行为的方法）：禁止，除非先写明偏离理由

**已记录平台偏离**：Android SDK 的 `android.jar` 不暴露 `ScrollView.LayoutParams` concrete 类型，因此 `LayoutHelper.createScroll()` 使用 SDK 等价的 `FrameLayout.LayoutParams`，但保持 Telegram 的 gravity / margin 语义与重载区分。

**当前 change 的 reference source set**：
- `org.telegram.messenger.AndroidUtilities`
- `org.telegram.messenger.Utilities`
- `org.telegram.messenger.DispatchQueue`
- `org.telegram.messenger.FileLog`
- `org.telegram.messenger.NotificationCenter`
- `org.telegram.ui.Components.LayoutHelper`
- `org.telegram.ui.Components.CubicBezierInterpolator`
- `org.telegram.ui.Components.AnimatedFloat`
- `org.telegram.ui.Components.AnimatedColor`

### D1: 导航系统 — 移植 ActionBarLayout 而非使用 Compose Navigation 或 AndroidX Navigation

**选择**：将 Telegram 的 `ActionBarLayout` + `BaseFragment` + `INavigationLayout` 移植为 Kotlin 实现

**为什么不用 Compose Navigation**：
- 不支持手势驱动的连续转场（手指跟随的 swipe-back）
- 页面间动画受限于 `AnimatedNavHost` 的预设过渡
- 无法精确控制页面栈的内存（Compose 的 `NavBackStackEntry` 生命周期不够灵活）

**为什么不用 AndroidX Navigation + Fragment**：
- Fragment 转场动画 API 过于抽象，无法做到 Telegram 级别的 ActionBar 联动过渡
- 每个 Fragment 的 View 生命周期管理复杂

**移植策略**：
- `INavigationLayout` → Kotlin interface，保持 API 形状
- `ActionBarLayout` → Kotlin class，核心逻辑 1:1 翻译，去除 Telegram 特有功能（preview mode、bubble mode、drawer 等）
- `BaseFragment` → Kotlin abstract class，保留 `createView()`/`onFragmentCreate()`/`onFragmentDestroy()` 生命周期，去除 Telegram 账号相关方法
- 页面容器：单 Activity（`MainActivity`）+ `ActionBarLayout` 作为根容器；`MainActivity` 直接承担 Telegram `LaunchActivity` 的简化 host 角色，负责系统生命周期、返回、intent、window inset、系统栏等桥接，不额外发明 `RootActivity` / `WindowHost` 抽象
- 导航执行：不再引入额外的 screen router；页面跳转统一由 `ActionBarLayout.presentFragment()` 驱动

**简化点**（vs Telegram 原版）：
- 去掉多账号支持（`currentAccount` 参数）
- 去掉 Drawer（linux.do 不需要侧栏）
- 去掉 preview/bubble mode
- 去掉 `pulledDialogs`（消息预览功能）
- 保留 swipe-back、转场动画、ActionBar 联动

### D2: 主题系统 — color-key 映射引擎

**选择**：移植 Telegram `Theme.java` 的 color-key 架构，用 Kotlin 重写

**设计**：
```kotlin
object NdTheme {
    // color-key 常量
    const val key_windowBackgroundWhite = "windowBackgroundWhite"
    const val key_actionBarDefault = "actionBarDefault"
    const val key_actionBarDefaultTitle = "actionBarDefaultTitle"
    const val key_chats_nameMessage = "chats_nameMessage"
    // ... Discourse 特有 key
    const val key_topic_title = "topic_title"
    const val key_topic_excerpt = "topic_excerpt"
    const val key_topic_categoryBadge = "topic_categoryBadge"
    const val key_post_codeBlock = "post_codeBlock"
    const val key_post_blockquote = "post_blockquote"

    // 当前调色板
    private var currentColors: HashMap<String, Int> = HashMap()

    fun getColor(key: String): Int { ... }
    fun setColor(key: String, value: Int) { ... }
    fun applyTheme(theme: ThemeInfo) { ... }
}
```

**为什么不用 Compose MaterialTheme**：
- Material Theme 只支持编译时静态 token
- 无法在运行时动态切换单个颜色
- 无法支持用户自定义主题（未来功能）

**内置主题**：Light、Dark、System（跟随系统），通过 `SharedPreferences` 持久化选择

### D3: 列表渲染 — RecyclerView + Canvas Cell

**选择**：使用 RecyclerView（增强版）+ 自定义 Canvas Cell，不用 Compose LazyColumn

**TopicCell 渲染策略**（对标 Telegram `DialogCell`）：
- 继承 `BaseCell`（→ `View`），重写 `onMeasure` + `onDraw`
- 直接用 `Canvas.drawText()`/`Canvas.drawBitmap()`/`Canvas.drawRoundRect()` 绘制：
  - 帖子标题（多行截断）
  - 分类徽章（颜色圆点 + 文字）
  - 作者头像（圆形裁剪）
  - 回复数/浏览数
  - 时间戳
  - 可选缩略图
- 优势：零 View 层级、零 layout pass、零 recomposition

**RecyclerListView 增强**（对标 Telegram `RecyclerListView`）：
- 内置 sectioned adapter 支持
- 内置快速滚动条
- 内置 item 点击/长按处理（避免每个 Cell 设置 listener）
- 内置边缘效果定制

### D4: Discourse cooked HTML 渲染 — CookedHtmlRenderer

**选择**：用 Jsoup 解析 HTML → 转为 `RenderBlock` 序列 → Canvas/View 混合渲染

**设计思路**（参考 Telegram `ArticleViewer` 的 Instant View 方案）：

```
cooked HTML string
    ↓ Jsoup parse
HTML DOM tree
    ↓ walk & convert
List<RenderBlock> + TextDocument/InlineEntity
    ↓ RecyclerView adapter
Canvas 绘制 / WebView fallback
```

**块级 `RenderBlock` 类型**：
```kotlin
sealed interface RenderBlock {
    data class Paragraph(val content: TextDocument) : RenderBlock
    data class Heading(val level: Int, val content: TextDocument) : RenderBlock
    data object HorizontalRule : RenderBlock
    data class CodeBlock(val code: String, val language: String?) : RenderBlock
    data class Blockquote(val children: List<RenderBlock>) : RenderBlock
    data class DiscourseQuote(
        val authorDisplayName: String,
        val authorUsername: String?,
        val avatarUrl: String?,
        val title: TextDocument?,
        val children: List<RenderBlock>,
        val sourceTopicId: Long?,
        val sourcePostNumber: Int?,
        val isExpandable: Boolean,
        val isInitiallyExpanded: Boolean,
    ) : RenderBlock
    data class Callout(
        val style: CalloutStyle,
        val title: TextDocument?,
        val children: List<RenderBlock>,
    ) : RenderBlock
    data class Image(val url: String, val alt: String?, val width: Int?, val height: Int?) : RenderBlock
    data class LightboxImage(
        val previewUrl: String,
        val originalUrl: String?,
        val filename: String?,
        val information: String?,
        val width: Int?,
        val height: Int?,
    ) : RenderBlock
    data class ImageGrid(val images: List<Image>, val columns: Int) : RenderBlock
    data class List(val items: List<ListItem>, val ordered: Boolean) : RenderBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : RenderBlock
    data class Onebox(val url: String, val title: String?, val description: String?, val thumbnail: String?) : RenderBlock
    data class Details(val summary: TextDocument, val children: List<RenderBlock>, val expandedByDefault: Boolean) : RenderBlock
    data class Spoiler(val children: List<RenderBlock>, val revealedByDefault: Boolean = false) : RenderBlock
    data class Poll(
        val title: String?,
        val options: List<PollOption>,
        val chartType: String?,
        val isPublic: Boolean,
        val resultsVisibility: String?,
        val status: String?,
    ) : RenderBlock
    data class RawHtml(val html: String) : RenderBlock  // fallback：不认识的 HTML 用小型 WebView
}

data class TextDocument(
    val text: String,
    val entities: List<InlineEntity>,
)

sealed interface InlineEntity {
    data class Bold(val start: Int, val end: Int) : InlineEntity
    data class Italic(val start: Int, val end: Int) : InlineEntity
    data class Strike(val start: Int, val end: Int) : InlineEntity
    data class Link(val start: Int, val end: Int, val url: String) : InlineEntity
    data class Mention(val start: Int, val end: Int, val username: String) : InlineEntity
    data class Hashtag(val start: Int, val end: Int, val slug: String) : InlineEntity
    data class Emoji(val start: Int, val end: Int, val shortcode: String, val imageUrl: String?) : InlineEntity
    data class InlineCode(val start: Int, val end: Int) : InlineEntity
}
```

**迁移基线**：
- 新的 `CookedHtmlRenderer` 不能比当前实现退化
- 当前 parser 已经支持 `DetailsBlock`、`CalloutBlock`、`DiscourseQuoteBlock`、`LightboxBlock`、`ImageGridBlock`
- 具体可参考 [PostBodyBlocks.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt) 和 [PostBodyBlockParser.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt)

**为什么不全用 WebView**：
- WebView 每个帖子一个实例，内存暴增
- 无法与原生列表滚动无缝融合
- 无法应用自定义主题颜色

**为什么不全用 Canvas**：
- 部分 Discourse 插件生成的 HTML 极其复杂（嵌入视频、交互式图表）
- 用 `RawHtml` fallback 保底

### D5: 依赖管理 — 手动 bootstrap + 单账号 runtime

**选择**：不用 Hilt/Dagger/Koin，采用手动 Service Locator 模式

**设计**：
```kotlin
object NdApp {
    lateinit var runtime: NdRuntime

    fun init(context: Context) {
        runtime = NdRuntime(context)
    }
}

class NdRuntime(context: Context) {
    val discourseClient: DiscourseClient = ...
    val sessionStore: CookieSessionStore = ...
    val csrfProvider: CsrfTokenProvider = ...
    val theme: NdTheme = ...
    val database: NdDatabase = ...
}
```

**为什么不用 Hilt**：
- Telegram 不用 DI 框架，所有依赖手动管理
- 对于单 Activity 应用，DI 框架增加编译时间和 APK 大小，但收益有限
- 手动管理更容易理解和调试

**注意**：
- 这与 Telegram 的 `AccountInstance` 模式类似，但因为我们不需要多账号，简化为单个 `NdRuntime`
- `NdApp` 只负责 bootstrap，不承载几十个裸 `lateinit` 全局服务
- 现有 [AppContainer.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt) 的集中式依赖形状可以作为 `NdRuntime` 的直接演化基线

### D6: 网络层 — OkHttp + Retrofit + kotlinx.serialization

**选择**：保持现有网络栈，不引入 Telegram 的 `ConnectionsManager`（那是 Telegram 自有协议的 native 实现）

**架构**：
```
DiscourseClient
  ├── OkHttpClient (共享实例)
  │     ├── CookieJar (CookieSessionStore)
  │     ├── CloudflareInterceptor
  │     ├── CsrfInterceptor
  │     └── UserAgentInterceptor
  ├── Retrofit (JSON endpoints)
  │     └── DiscourseApi interface
  └── Jsoup (HTML endpoints)
        └── BootstrapParser
```

### D7: 图片加载 — Coil 3 底层 + 自定义 ImageReceiver

**选择**：保留 Coil 3 作为底层加载/缓存引擎，但在 Cell 内通过 `ImageReceiver` 包装使用

**为什么不直接用 Telegram 的 `ImageLoader`**：
- Telegram 的 `ImageLoader` 深度绑定其私有文件系统（`FileLoader`/`FileLoadOperation`）
- Coil 3 已经是项目依赖，替换成本高且无明显收益
- `ImageReceiver` 只需要包装 Coil 的 `ImageRequest` 并管理 Canvas 绘制回调

### D8: Compose 保留策略

**选择**：保留 Compose runtime 依赖，在特定场景通过 `ComposeView` 嵌入使用

**适用场景**：
| 场景 | 用 Compose？ | 原因 |
|------|-------------|------|
| 话题列表 | ✗ | Canvas Cell 性能更好 |
| 帖子详情 | ✗ | RecyclerView + RenderBlock Cell |
| 内置浏览器 | ✗ | WebView + 自定义 ActionBar |
| 搜索结果 | ✗ | Canvas Cell |
| 设置页 | ✓ | 静态表单，性能不敏感 |
| 登录页 | ✓ | WebView + 简单 UI |
| About / 关于 | ✓ | 静态内容 |
| BottomSheet 内容 | ✓ | 简单内容填充 |

**嵌入方式**：
```kotlin
class SettingsFragment : BaseFragment() {
    override fun createView(context: Context): View {
        return ComposeView(context).apply {
            setContent {
                NdComposeTheme { // 桥接 NdTheme → Compose Theme
                    SettingsScreen(...)
                }
            }
        }
    }
}
```

### D9: V0 必须前置的 Telegram substrate

**选择**：V0 先实现 Telegram 风格的“基础设施底座”，而不是并行铺开所有组件。优先级按依赖链决定：

```
NdApp / app bootstrap
    ↓
AndroidUtilities + Utilities + DispatchQueue + FileLog
    ↓
NotificationCenter + NdTheme + LayoutHelper
    ↓
SimpleTextView + ActionBar + BaseFragment + ActionBarLayout
    ↓
RecyclerListView(minimal) + BaseCell + ImageReceiver + AvatarDrawable
    ↓
DiscourseClient + CookieSessionStore + BootstrapParser
```

**为什么这样拆**：
- Telegram 原版很多基础类被业务层反向污染，不能整包照搬
- `ActionBarLayout`、`RecyclerListView`、`ImageReceiver` 都依赖前面的工具/主题/调度基元
- 如果 V0 缺少 `NdApp`、线程队列、日志、布局辅助，后续组件会各自长出一套简化版，最后很难收敛回 Telegram 风格架构

**V0 应该先做的类**：
- `NdApp`：应用 bootstrap、全局 context、service registry、主线程 handler
- `NdRuntime`：单账号 service/runtime 持有者，集中组织 session、network、theme、storage、media 这类长期存活依赖
- `AndroidUtilities`：dp/lerp/系统栏/键盘/屏幕指标，以及 Telegram 风格的 `runOnUIThread()` / `cancelRunOnUIThread()`
- `Utilities`：只保留通用数学与少量 non-UI-thread helper，例如 `clamp()`；不承载 UI-thread scheduling，命名后台队列能力由 `DispatchQueue` 提供
- `DispatchQueue`：少量命名串行后台队列，供日志、主题 I/O、图片相关工作使用
- `FileLog`：异步日志记录，便于早期移植排错
- `NotificationCenter`：全局观察者分发，但只承载跨页面、跨层级的全局事件
- `AppLifecycleMonitor`：最小前后台 / 网络可用性监控，服务于 offline banner、session restore、Cloudflare recovery 这类 truly global 行为
- `NdTheme`：先做精简版 color-key 引擎，不移植 Telegram `Theme.java` 的壁纸/动态主题/编辑器部分，但 V0 就要提供 selector/ripple、圆角背景、header shadow 这类主题辅助 drawable 工厂
- `NdUiConfig`：只保留 V0 真正需要的全局 UI 开关，作为 Telegram `SharedConfig` 的瘦身替代，例如 `animationsEnabled`、`drawActionBarShadow`、`sectionShadows`
- `LayoutHelper`：轻量布局参数工厂
- `SimpleTextView`：ActionBar 和少量高频 UI 的轻量文本组件
- `BackDrawable`：最小可用的返回箭头/关闭态 animated drawable；`MenuDrawable` 只在 V0 的搜索或菜单状态切换明确需要时再一起落地
- `ActionBar` / `BaseFragment` / `ActionBarLayout`：先做最小导航闭环，其中 `BaseFragment` 至少要预留 `isSwipeBackEnabled()`、`onSlideProgress()`、`onTransitionAnimationStart/End()`、`onBecomeFullyVisible()`、`getNavigationBarColor()`、`hasForceLightStatusBar()` 这些转场和系统栏 hook，避免后续页面接入时返工接口
- `BaseFragment` 的 page scope：每个页面自带 coroutine/request scope，在 `onFragmentDestroy()` 或 swipe-back 真正完成时统一 cancel，避免 topic load、HTML parse、图片请求残留
- `ActionBarLayout` 的最小状态恢复：V0 就支持“fragment 标识 + args bundle”的 stack save/restore，至少覆盖配置变更和进程重建后的冷恢复
- 重资源对象生命周期约束：`ImageReceiver`、共享 WebView、HTML renderer host 都要有 attach/detach/release 三段式边界，避免后面再补资源清理协议

**当前 2.x / 3.x 实现审视结论**：

| 当前文件 | 分类 | Telegram 参考 | 许可证要求 | 处置 / audit note |
|---|---|---|---|---|
| `AndroidUtilities.kt` | `Telegram-derived` parity subset | `org.telegram.messenger.AndroidUtilities.java` | 每文件使用 derived GPL 头 | 保留最小子集实现方向；`runOnUIThread()` / `cancelRunOnUIThread()` 仍归 `AndroidUtilities`，不再迁入 `Utilities` |
| `Utilities.kt` | `Telegram-derived` slim subset | `org.telegram.messenger.Utilities.java` | 每文件使用 derived GPL 头 | 保留“瘦身但同名语义对齐”的方向；当前只保留 `clamp*` 等 non-UI-thread helper |
| `DispatchQueue.kt` | `Telegram-derived` parity subset | `org.telegram.messenger.DispatchQueue.java` | 每文件使用 derived GPL 头 | 保留现有最小串行队列方向，不额外包装第二层 queue helper |
| `FileLog.kt` | `Telegram-derived` parity subset | `org.telegram.messenger.FileLog.java` | 每文件使用 derived GPL 头 | 保留异步日志基元方向；adopted subset 的主入口 API 继续以 Telegram 风格为准，tag-first overload 不是默认替代 |
| `AnimatedFloat.kt` | `Telegram-derived` parity subset | `org.telegram.ui.Components.AnimatedFloat.java` | 每文件使用 derived GPL 头 | 保留，后续继续以 Telegram 的 `set()` / `force()` / `getValue()` 语义为准 |
| `AnimatedColor.kt` | `Telegram-derived` parity subset | `org.telegram.ui.Components.AnimatedColor.java` | 每文件使用 derived GPL 头 | 保留，后续继续以 Telegram 的 `set()` / `force()` 语义为准 |
| `NotificationCenter.kt` | `Telegram-derived` simplified subset | `org.telegram.messenger.NotificationCenter.java` | 若继续以同名 parity port 维护，则每文件使用 derived GPL 头 | 保留当前全局事件总线方向，但边界继续限制为 truly global cross-cutting events |
| `AppLifecycleMonitor.kt` | `Telegram-inspired project-specific` | 无同名 Telegram 公共类；参考 Telegram app/network 全局状态职责拆分 | 不默认使用 derived GPL 头 | 保留为 NativeDo 特有全局基础设施，但不再为它额外衍生 typed event wrapper |
| `CubicBezierInterpolator.kt` | `Telegram-derived` parity subset | `org.telegram.ui.Components.CubicBezierInterpolator.java` | 每文件使用 derived GPL 头 | 保留数学实现方向，但回退公开常量集合与命名到 Telegram 原接口：`DEFAULT`、`EASE_OUT`、`EASE_OUT_QUINT`、`EASE_IN`、`EASE_BOTH`、`EASE_OUT_BACK` |
| `LayoutHelper.kt` | `Telegram-derived` parity subset | `org.telegram.ui.Components.LayoutHelper.java` | 每文件使用 derived GPL 头 | 回退并按 Telegram 子集重做；Phase 0 只 adopt 当前 caller 需要的 `createFrame` / `createLinear` / `createScroll` 家族，且 `createScroll()` 语义必须与 Telegram 保持一致 |
| `SpringAnimationUtils.kt` | `Telegram-inspired project-specific` 候选，但当前无 caller | 无 Telegram 对应公共类 | 不使用 derived GPL 头 | 回退；在首个真实 consumer 出现前，不保留 standalone spring helper 文件 |
| `AndroidUtilitiesTest.kt` | 非 parity 产物 | 无 Telegram parity 价值 | 不适用 | 回退/删除；除非后续转化为已复现回归的保护用例，否则不作为默认模式 |
| `NotificationCenterTest.kt` | 非 parity 产物 | 无 Telegram parity 价值 | 不适用 | 回退/删除；除非后续转化为已复现回归的保护用例，否则不作为默认模式 |

**不应该塞进 V0 的 Telegram 能力**：
- Telegram 原版 `Theme.java` 的完整能力
- Telegram 原版 `SharedConfig` 的完整能力
- `LocaleController` 的整套语言/RTL 体系
- `ImageLoader` 的完整文件系统和缓存体系
- `SizeNotifierFrameLayout` 的 blur-behind 链路
- `Bulletin` / `UndoView` 这类全局浮层反馈体系
- `ViewPagerFixed` / `ScrollSlidingTextTabStrip` 这类标签容器
- `BackupImageView`
- `BottomSheet` 的复杂嵌套滚动和多态内容能力
- `RecyclerListView` 的全部 sticky header / fast scroll / blur / overlay 能力

**对当前 change 的影响**：
- Phase 0 应先补齐上述 substrate，再进入完整 UI 组件和业务页面
- `BottomSheet`、高级 `RecyclerListView` 特性、复杂主题能力更适合放到 Phase 1 之后

### D10: 测试策略遵循 Telegram 的“高风险契约优先”

**选择**：不追求传统 UI 基础设施的单元测试覆盖率，而是把测试集中在高风险、强契约、容易静默回归的逻辑上。

**Telegram 参考结论**：
- Telegram 仓库几乎没有常规 `src/test`
- 测试集中在独立的 `TMessagesProj_AppTests/src/androidTest` 模块
- 绝大部分测试都在验证 TL 协议对象的序列化/反序列化 round-trip
- 少量额外测试覆盖 native 协议边界和数据库 migration
- `ActionBarLayout`、`Theme`、`RecyclerListView`、`BottomSheet`、`AvatarDrawable` 这类视觉/交互基础设施没有成体系的单元测试

**因此本项目应测试的内容**：
- JSON 反序列化和接口契约
- cooked HTML 解析
- cookie/session/csrf 恢复逻辑
- topic stream 分页和 jump-to-post 逻辑
- error normalization
- Room / cache schema 和 migration

**因此本项目默认不写单测的内容**：
- `AndroidUtilities` / `LayoutHelper` 这类基础 helper
- `NotificationCenter` / `AppLifecycleMonitor` 这类 integration primitive
- `ActionBarLayout` / `ActionBar` / `RecyclerListView` / `BottomSheet`
- `AvatarDrawable` / `SimpleTextView` / `ImageReceiver` 的视觉细节
- 依赖手势、动画、布局和绘制时序的 UI substrate

**这些基础设施如何验证**：
- 用最小宿主页面跑通
- 通过 HelloFragment / mock screen 做手工 smoke test
- 在 Phase 完成时做端到端运行验证，而不是为每个 UI 类单独补测试
- 当前已经出现的 `AndroidUtilitiesTest.kt`、`NotificationCenterTest.kt` 不应作为后续默认模式；只有当它们被重写为复现过的回归保护用例时才值得保留

### D11: App 级 host、鉴权命名与链接分发

**选择**：App 级基础设施沿用 Telegram 的职责划分思路，但命名不使用 `Coordinator`。优先使用更贴近 Telegram 代码风格的 `MainActivity` / `NdApp` / `*Manager` / `*Bootstrap`。

**命名约定**：
- 启动首屏决策层使用 `LaunchBootstrap`，负责冷启动时触发 session restore，并决定先进入 `TopicsFragment` 还是 `LoginFragment`；但它只在真正出现 session-dependent startup routing 时引入，不是 demo-root phase 的前置要求
- cookie 驱动的鉴权状态机使用 `AuthSessionManager`，负责 login completion、session restore、logout、expired/recovery 流程
- 链接分类使用最小 `LinkManager`，只负责 `url -> target` 解析，不承担页面导航栈职责
- `NdApp` / `NdRuntime` / `MainActivity` / `AppLifecycleMonitor` 属于 `Telegram-inspired project-specific` host/integration 层；`LaunchBootstrap` 只在后续 owner phase 引入后归入同一层。它们参考 Telegram 的职责划分，但不宣称自己是 `ApplicationLoader` / `LaunchActivity` / 某个 Telegram 同名类的直接移植

**为什么不用 `StartupCoordinator` / `SessionCoordinator`**：
- Telegram 代码库几乎不用 `Coordinator` 这一套命名
- 启动职责主要分布在 `ApplicationLoader` 和 `LaunchActivity`
- 会话/账号/网络状态主要分布在 `UserConfig`、`MessagesController`、`ConnectionsManager`、`NotificationCenter`
- 对本项目来说，用 `LaunchBootstrap` + `AuthSessionManager` 更接近 Telegram 的风格，也更容易让 Android 开发者一眼看懂职责

**最小 `LinkManager` 边界**：
- 只处理 URL 分类，不处理 fragment push/pop
- 首版只识别：站内 topic、站内 user、站内其他页面（走 app 内浏览器）、站外可外部 App 打开、站外系统浏览器
- `BrowserFragment`、cooked HTML link、mention/hashtag/onebox 后续都统一走这条分类链

**登录与浏览器的关系**：
- `LoginFragment` 不是“普通 Browser 打开 `/login`”
- `LoginFragment` 与 `BrowserFragment` 是两个独立 scene
- 二者共享同一套 WebView 基础设施：`CookieSyncBridge`、共同的 WebView settings / factory、linux.do 域名策略
- 登录成功后由 `AuthSessionManager` 把 WebView cookie 转为 app 的 authenticated session，而不是由浏览器页自己决定 app 导航

### D12: clean-slate root host 策略 — 保持单 `MainActivity`，不再保留 legacy/rewrite 双 root

**选择**：不引入单独的 rewrite/debug Activity，也不在当前工作树里继续维护 legacy Compose root。保留单个 `MainActivity` 作为唯一 launcher/intent host，并让新的 `ActionBarLayout` 成为默认且唯一的运行时 root。`LaunchBootstrap` 只在后续 startup/auth phase 真正需要条件分流时再进入主链路。

**为什么这样做**：
- 去掉 dual-root 带来的宿主复杂度、状态分叉、测试噪音和代码搜索污染
- 当前仓库已有 `main` 分支作为重构前快照，不需要额外保留 `android-bak` 或 in-repo fallback
- 可以把 `apps/android` 明确定义为重构工作区，避免为了迁移期兼容而反复修改边界代码

**执行规则**：
- `MainActivity` 始终是唯一 launcher activity
- 当前分支的 `apps/android` 只承载新架构，不保留 legacy Compose 主流程开关
- 若后续仍需 Compose，只作为新架构内部的局部工具（`ComposeView` / 非性能关键静态页面），而不是旧 root 保留策略
- 旧实现的回滚与溯源依赖 `main` 分支、git 历史、阶段性可运行 APK，而不是仓库内并存目录

**结果**：
- 宿主模型收敛为单 root、单启动路径
- 不需要额外发明 `RewriteActivity` / `SandboxHostActivity`
- 不需要在 repo 中保留 `apps/android-bak`

## Risks / Trade-offs

### R1: ActionBarLayout 移植工作量巨大
**风险**：Telegram 的 `ActionBarLayout.java` 有 3489 行，涉及手势处理、动画插值、页面栈管理等复杂逻辑
**缓解**：
- 先移植简化版（仅 push/pop + swipe-back），约 1500 行
- 渐进式添加高级功能（ActionBar 联动、自定义转场）
- 每个阶段保持可编译可运行

### R2: CookedHtmlRenderer 的 HTML 多样性
**风险**：Discourse 用户 HTML 多样性远超 Telegram Instant View（Telegram 只需处理 Telegram 自己生成的 IV 格式）
**缓解**：
- `RawHtml` fallback 保底（不认识的 HTML 用 WebView 渲染）
- 优先覆盖 80% 常见格式：`<p>`, `<pre>`, `<blockquote>`, `<img>`, `<a>`, `<ul>/<ol>`, `<h1-6>`, Discourse emoji
- 持续迭代支持更多格式

### R3: clean-slate 切换后缺少仓库内 fallback
**风险**：当前分支不再保留 legacy/rewrite 双实现并存，若新宿主路径有回归，不能靠 repo 内开关立即切回旧 root
**缓解**：
- 以 `main` 分支作为重构前快照，不在当前工作树里复制 `android-bak`
- 每个阶段都保持 `assembleDebug` 和关键单测可通过，必要时产出可安装 APK
- 先更新 artifacts，再做删除和新建，避免设计与实现脱节

### R4: GPL v2 许可证合规
**风险**：Telegram 源码 GPL v2 授权，移植代码需遵守
**缓解**：
- NativeDo 仓库保持开源
- 每个 `Telegram-derived` 文件头部都使用统一模板，标注 upstream class/path、source reference、版权和 SPDX
- `Telegram-inspired project-specific` 文件不滥贴 derived 头，避免把职责参考误写成源码移植

### R5: 不用 DI 框架的可测试性
**风险**：手动 Service Locator 在测试中不如 Hilt 方便 mock
**缓解**：
- 关键服务通过 interface 定义，测试时可替换实现
- `NdApp` 提供 `initForTesting()` 方法注入 mock

## Migration Plan

### Phase 0: Foundation（基础设施层）
1. 先实现 Telegram substrate：`NdApp`、`NdRuntime`、`AndroidUtilities`、`Utilities`、`DispatchQueue`、`FileLog`、`NotificationCenter`、`AppLifecycleMonitor`、`NdTheme`、`LayoutHelper`
2. 新建 `ui/actionbar/` 包，实现 `SimpleTextView`、ActionBar、ActionBarLayout + BaseFragment 最小闭环，并补上 page scope cancel 与最小 stack save/restore
3. 新建 `ui/components/` 和 `core/media/` 包，实现最小 `RecyclerListView`、`BaseCell`、`ImageReceiver`、`AvatarDrawable`，同步确定 attach/detach/release 约束
4. 新建 `discourse/` 包，实现 DiscourseClient + session 管理
5. 让 `MainActivity` 直接承载新的默认 root（先直接挂 `ActionBarLayout`，后续需要时再接入 `LaunchBootstrap`），不新增第二个 launcher host，也不保留 legacy root 开关
6. **验证**：用一个 HelloWorld Fragment 跑通 ActionBarLayout 导航，并验证日志、主题、事件、线程队列、page scope cancel 可用

### Phase 1: Core Components（核心组件层）
1. 补全 `RecyclerListView` 高级能力、BottomSheet、ActionBar 搜索模式等非 V0 必需组件
2. 实现 CookedHtmlRenderer 基础版（覆盖 p/pre/blockquote/img/a）
3. **验证**：用 mock 数据展示一个完整的话题列表 + 帖子详情

### Phase 2: Features（功能重建）
1. 逐个用新框架重建页面：Topics → TopicDetail → Browser → Profile → Search → Login
2. 每完成一个页面，直接接入新的默认主流程并保持构建/关键验证通过，不再依赖 feature flag 切换旧实现
3. **验证**：每个页面独立可测试

### Phase 3: Polish（打磨）
1. swipe-back 手势微调
2. 共享元素转场
3. 离屏渲染优化
4. 删除所有遗留自旧实现的包、资源、依赖和临时兼容层
5. **验证**：完整 APK，无旧代码残留

### Rollback
- 每个 Phase 独立 git branch
- `main` 分支作为重构前 Android 快照，可随时对照或回退
- 不在当前工作树中保留 legacy root 开关或 `android-bak`

## Resolved Questions

1. **Lottie**：暂不引入，简单动画用 Canvas 自绘
2. **离线缓存**：使用 Room 缓存话题列表和帖子内容，采用 stale-while-revalidate 模式。帖子编辑通过 `version` 字段对比检测，新版本直接 REPLACE 旧缓存
3. **Discourse 插件 HTML**：从 linux.do 抓取真实帖子的 `cooked` HTML 作为测试数据，保存到 `fixtures/discourse/cooked-*.html`
4. **主题色调**：以 linux.do 品牌绿色为主色调，后续添加自定义调色板选项
5. **测试策略**：遵循 Telegram 的“高风险契约优先”思路，只为有实际意义的逻辑写测试（JSON 反序列化、HTML 解析、Session 逻辑、分页逻辑、startup/auth/navigation 主链路等），不为视觉/手势 substrate 和琐碎 helper 写单测。现有测试不做一次性清空，保留仍然表达有效契约的网络/session/html parsing 测试，待新实现与替代测试落地后再删除；`AndroidUtilitiesTest.kt`、`NotificationCenterTest.kt` 这类 standalone substrate test 不作为后续默认模式，应在 parity audit 中回退或删除，除非转成已复现回归的保护用例
6. **root host**：保持单个 `MainActivity` 作为唯一 launcher，但当前分支先只保留新的默认 `ActionBarLayout` 主链路；`LaunchBootstrap` 留到真实 startup/auth phase 再接入，不再维护 legacy Compose root 开关，也不引入第二个 rewrite Activity

## Open Questions

1. **linux.do 独有的 Discourse 插件清单？** 当前已从真实 cooked HTML 样本确认以下形态需要纳入 `CookedHtmlRenderer` 设计范围：
   - `details/summary` 折叠块
   - `div.spoiler` 剧透块
   - `div.poll` 投票块（至少支持只读渲染）
   - `div.lightbox-wrapper > a.lightbox > img + div.meta` 图片灯箱包装（原图 URL、下载链接、文件名、尺寸信息）
   - `a.onebox` / `a.inline-onebox` 链接型 onebox
   - `a.hashtag-cooked` 标签链接
   - `aside.quote` 的完整元数据：`data-topic`、`data-post`、`data-username`、`data-display-name`、头像、来源标题、修改态/可展开状态
   - blockquote 中的 markdown 风格 callout 标记（例如 `[!warning]`）
2. **哪些节点应该视为装饰性包装而不是 `RawHtml`？** 当前样本里已出现 `div.md-table`、`div.lightbox-wrapper`、装饰性 `svg.d-icon`、空 `blockquote`、`div[data-theme-toc=true]`，这些更适合作为解析期归一化或忽略规则，而不是直接回退到 WebView
3. **还缺哪些 cooked 样本？** 建议继续补充 `d-image-grid` 图片宫格、非图片附件、视频/iframe/embed、数学公式等样本，再决定是否继续扩展 `RenderBlock`
