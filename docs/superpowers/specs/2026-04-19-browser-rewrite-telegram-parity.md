# NativeDo In-App Browser 全面重写 — Telegram 1:1 对齐规格

**日期：** 2026-04-19
**状态：** 规格定稿，待实施
**目标：** 丢弃现有 browser feature 中所有存在 bug 的实现，以 Telegram Android 浏览器为 1:1 参考，使用 Jetpack Compose 从零重写全部组件。

---

## 一、现状评估

### 现有文件清单（全部丢弃重写）

| 文件 | 行数 | 重写原因 |
|---|---|---|
| `AppBrowserRoute.kt` | 1776 | 路由/WebView 协调混杂，状态散落 |
| `AppBrowserChrome.kt` | 1954 | 地址栏/搜索/菜单混在一个文件 |
| `AppBrowserReaderScene.kt` | 2452 | Reader 模式独立 sheet 嵌套复杂 |
| `AppBrowserState.kt` | 2236 | 状态模型+Reader 解析+历史混杂 |
| `AppBrowserSheetContainer.kt` | 280 | Sheet 动画/手势有 bug |
| `AppBrowserScreen.kt` | 68 | 简单装配层，随重写自动替代 |
| `AppBrowserViewModel.kt` | 248 | 职责偏轻，需要扩充 |
| `AppBrowserWebView.kt` | 283 | WebView 配置需对齐 Telegram |
| `AppBrowserWebViewController.kt` | 64 | 接口过于简陋 |
| `AppBrowserUiState.kt` | 131 | 状态模型需重构 |
| `AppBrowserAction.kt` | 35 | 动作集需要扩充 |
| `AppBrowserAddressSheet.kt` | 172 | 地址建议面板 |
| `AppBrowserAddressResolver.kt` | 58 | 地址解析 |
| `AppBrowserAddressSuggestions.kt` | 164 | 建议生成 |
| `AppBrowserHistoryPanel.kt` | 350 | 历史面板 |
| `AppBrowserBookmarksPanel.kt` | 89 | 书签面板 |
| `AppBrowserBookmarkStore.kt` | 133 | 书签存储 |
| `AppBrowserSettingsPanel.kt` | 133 | 设置面板 |
| `AppBrowserInstantViewLoader.kt` | 148 | Instant View 加载 |
| `AppBrowserInstantViewResolver.kt` | 980 | Instant View 解析 |
| `BrowserHost.kt`（ui 层） | 548 | Session host 管理 |
| `BrowserTabsShell.kt`（ui 层） | 1022 | Tab dock / manager |
| **合计** | **~13,300** | |

### 保留不动的部分

- `core/navigation/LinkRouter` 及 `LinkOpenPolicy` 路由决策
- `core/session/CookieSyncBridge` cookie 同步
- `core/storage/AppBrowserHistoryStorePersistence` 历史持久化
- `core/storage/AppBrowserBookmarkStorePersistence` 书签持久化
- `core/storage/AppBrowserRecentSearchStore` 搜索记录
- `core/navigation/AppBrowserReaderContent` / `AppBrowserReaderDocument` reader 内容模型
- `core/ui/PostTextDocumentView` / `PostImageView` / `HtmlPostBodyView` 渲染组件
- 所有 `ui/theme/` 主题基座（Phase A/B 产物）
- 所有 `ui/NativeDoGlassSurface` / `NativeDoBrowserScaffold` 等 shell 层
- `ui/NativeDoApp.kt` root 装配层（只改 browser host 接入点）

---

## 二、目标架构（Telegram 1:1 对齐）

### 架构层次

```
LinkRouter (路由决策)
    ↓
BrowserHost (全局 session 管理，多标签持有)
    ↓
┌─────────────────────────────────────────────────────┐
│ BrowserSheetDialog (Dialog 窗口容器)                   │
│   └─ BrowserSheet (Sheet 物理容器 + 手势 + 动画)       │
│       ├─ BrowserActionBar (顶栏 chrome)              │
│       ├─ BrowserWebContainer (WebView 容器)          │
│       └─ BrowserReaderScene (Instant View 渲染)      │
├─────────────────────────────────────────────────────┤
│ BrowserTabsDock (底部标签条)                           │
│ BrowserTabsOverlay (全屏标签管理器)                     │
└─────────────────────────────────────────────────────┘
```

### 核心类映射

| Telegram 原始类 | NativeDo Compose 对应 | 职责 |
|---|---|---|
| `Browser.java` 路由部分 | `LinkRouter`（已有，保留） | 链接分类决策 |
| `ArticleViewer.java` → Sheet | `BrowserSheet` | Sheet 容器 + open/close/dismiss 动画 |
| `ArticleViewer.java` → PageLayout | `BrowserWebContainer` + `BrowserReaderScene` | 页面内容渲染 |
| `WebActionBar.java` | `BrowserActionBar` | 顶栏 chrome (标题/地址/搜索/菜单) |
| `BottomSheetTabs.java` | `BrowserTabsDock` | 底部标签条（dock） |
| `BottomSheetTabsOverlay.java` | `BrowserTabsOverlay` | 全屏标签管理器 |
| `BottomSheetTabDialog.java` | `BrowserSheetDialog` | Dialog 窗口包装 |
| `BotWebViewContainer.java` WebView部分 | `BrowserWebContainer` | WebView 生命周期 |
| `BrowserHistory.java` | `AppBrowserHistoryStorePersistence`（已有） | 历史存储 |
| `WebBrowserSettings.java` | `BrowserSettingsStore`（新建） | 浏览器设置 |
| `WebTabData` | `BrowserTabState` | 标签状态快照 |

---

## 三、详细组件规格

### 3.1 BrowserTabState — 标签状态快照

```kotlin
data class BrowserTabState(
    val id: Long,
    val url: String,
    val title: String,
    val subtitle: String?,
    val isHttps: Boolean,
    val favicon: Bitmap?,
    val previewBitmap: Bitmap?,
    val webViewBundle: Bundle?,
    val scrollPosition: Int,
    val actionBarColor: Color,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isErrorPage: Boolean,
)
```

**对齐 Telegram `WebTabData`**:
- 保存 WebView Bundle 状态用于标签恢复
- `previewBitmap`: API 29+ 优先用 `RenderNode`，否则用 `PixelCopy`
- `actionBarColor`: 用于自适应主题

### 3.2 BrowserSheet — Sheet 容器

**对齐 Telegram `ArticleViewer.Sheet`**

#### 动画规格

| 动画 | 时长 | 插值器 | 属性 |
|---|---|---|---|
| 打开 | 320ms | `EASE_OUT_QUINT` | `openProgress` 0→1, `translationY` screenHeight→0 |
| 关闭 | 250ms | `EASE_OUT_QUINT` | `dismissProgress` 0→1, `translationY` 0→screenHeight |
| 圆角 | 跟随 openProgress | 线性 | 16dp → 0dp（贴顶后消失） |
| 手势拖拽 dismiss | 跟随手指 | — | `dismissProgress` = dragY / sheetHeight |

#### 视觉规格

- 阴影: `shadowLayer(18dp blur, -3dp offset, alpha 0.26 black)`
- 拖拽把手: 32dp 宽 pill，贴顶后增长到 48dp
- 圆角: 顶部 16dp `RoundedCornerShape`，`openProgress` 1.0 时变为 0dp
- 背景遮罩: 跟随 `openProgress`，`alpha = 0.10 * openProgress`

#### 手势规格

- 嵌套滚动: WebView/LazyColumn 到顶后，下滑驱动 `dismissProgress`
- Dismiss 阈值: > 25% sheetHeight 自动关闭
- 速度阈值: > 900px/s 向下直接关闭; > -900px/s 向上直接全屏

#### 状态管理

```kotlin
sealed interface BrowserSheetAnchor {
    data object Closed : BrowserSheetAnchor
    data object Partial : BrowserSheetAnchor  // 40% 屏幕高度
    data object Fullscreen : BrowserSheetAnchor
}
```

### 3.3 BrowserActionBar — 顶栏 Chrome

**对齐 Telegram `WebActionBar`**

#### 布局

```
┌──────────────────────────────────────────────┐
│ [← Back]   Title / Subtitle      [→] [⋮]    │  56dp 高度
│ ──── LinearProgressIndicator (2dp) ────────  │
└──────────────────────────────────────────────┘
```

- Back 按钮: 54×56dp 触摸区
- Forward 按钮: 54×56dp，仅在 `canGoForward` 时显示
- Menu 按钮: 54×56dp
- 进度条: 2dp `LinearProgressIndicator` 在底边

#### 标题切换动画

- 双 slot `title[0]` / `title[1]`，导航切换时水平滑动
- 离场标题: 半透明遮罩 `0x60000000`
- 使用 `AnimatedTextDrawable` 风格的逐字符渐变（可简化为 crossfade + slideIn/Out）

#### 自适应主题

```kotlin
fun computePerceivedBrightness(color: Color): Float {
    // RGB 加权亮度
    return (color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f)
}

// 阈值 0.721
val isDark = computePerceivedBrightness(actionBarColor) <= 0.721f
val textColor = if (isDark) Color.White else Color.Black
val iconColor = textColor.copy(alpha = 0.55f)
```

#### 地址栏模式

- 触发: 长按标题 → haptic feedback → pill 形 input
- 动画: 360ms `EASE_OUT_QUINT`
- Forward/Menu 按钮向右偏移为 input 腾空间
- pill 形状: 50dp 圆角

#### 搜索模式

- 动画: 320ms `EASE_OUT_QUINT`
- 覆盖在标题区域
- IME action: `IME_ACTION_SEARCH`

#### 菜单项（Web 模式）

1. 在外部应用打开
2. 前进（if canGoForward）
3. Instant View（if available）
4. 刷新
5. 搜索
6. 书签（切换）
7. 分享
8. 历史记录
9. 书签列表
10. 设置

### 3.4 BrowserWebContainer — WebView 容器

**对齐 Telegram `BotWebViewContainer` WebView 配置部分**

#### WebView Settings

```kotlin
settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    supportMultipleWindows = true
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
    useWideViewPort = true
    loadWithOverviewMode = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = true
    }
}
```

#### User Agent 定制

- 移除 `"; wv)"` WebView 标识，替换为 `")"`
- 简化 Android 设备信息为 `(Linux; Android {VERSION}; K)`
- 移除 `Version/x.x.x ` 信息

#### WebViewClient 回调

| 回调 | 处理 |
|---|---|
| `shouldOverrideUrlLoading` | 经 `LinkRouter` 重新分类 |
| `onPageStarted` | 更新 URL、显示进度、更新导航按钮 |
| `onPageFinished` | 隐藏进度、触发 Instant View 探测、同步 cookie |
| `onReceivedError` | 显示错误页面 |
| `onReceivedSslError` | 显示 SSL 错误对话框 |

#### WebChromeClient 回调

| 回调 | 处理 |
|---|---|
| `onProgressChanged` | 更新进度条 |
| `onReceivedTitle` | 更新标题 |
| `onReceivedIcon` | 更新 favicon |
| `onCreateWindow` | `target="_blank"` 处理：在当前 sheet 内打开 |
| `onShowFileChooser` | 文件选择器 |
| `onPermissionRequest` | 权限请求对话框 |

#### Edge Swipe 后退

- 水平滑动阈值: `0.4cm + dx/3 > dy`
- 快速滑动: > 3500px/s velocity 直接触发
- 位移确认: > 30% 屏幕宽度
- 动画时长: `max(420/width*distance, 250)ms, EASE_OUT_QUINT`
- 两种模式: 页面导航后退 vs sheet 关闭

### 3.5 BrowserTabsDock — 底部标签条

**对齐 Telegram `BottomSheetTabs`**

#### 布局

- 位于导航栏上方
- 单标签: 60dp 高度
- 多标签: 68dp 高度
- 卡片: 50dp 高度，10dp 圆角
- 卡片内容: Close X (左) / Expand ↑ (右) / favicon 24×24dp / title 17sp bold

#### 堆叠效果

- 最多显示 3 层预览
- 垂直偏移: `position * -8dp`
- 缩放: `lerp(1.0, 0.95, |position|)`
- 阴影: 2.33dp blur / 1dp offset

#### 颜色自适应

- 使用 `computePerceivedBrightness()` 阈值 0.721
- 暗色标签: 白色文字/图标
- 亮色标签: 黑色文字/图标

#### 点击行为

- 1 个标签 → 直接打开 sheet
- 2+ 个标签 → 打开全屏标签管理器

#### 动画

- 出现/消失: keyboard interpolator 保持与键盘节奏一致

### 3.6 BrowserTabsOverlay — 全屏标签管理器

**对齐 Telegram `BottomSheetTabsOverlay`**

#### 背景

- 高斯模糊底层内容 (blur radius 14)
- 饱和度调整: 暗色 -0.08, 亮色 +0.25

#### 卡片

- 最大宽度: 340dp
- 高度: 75% 屏幕高度
- 居中显示
- 3D 透视变换: `matrix.setPolyToPoly` — 底部比顶部窄 (0.83x 宽, 0.6x 高)
- 标题栏: 50dp，包含 close/expand/favicon/title
- 径向渐变暗角叠加在预览上
- 阴影: 30dp blur, 10dp offset
- 按压效果: `ButtonBounce` 0.99x scale

#### 打开/关闭动画

- 时长: 320ms `EASE_OUT_QUINT`

#### 滚动

- `OverScroller` + fling
- 滚动步长: 200dp
- 最多 3 张可见卡片
- 橡皮筋过度滚动阻尼

#### 水平滑动关闭标签

- `dismissProgress = deltaX / 300dp`
- 旋转: `dismissProgress * 20°`，支点在 `(centerX + 50dp*dismiss, bottom + 350dp)`
- Alpha: 30% 位移后开始淡出
- 弹回: `applySpring(285, 20)`
- 飞出: `EASE_OUT_QUINT`

#### 上拉关闭管理器

- 超过 15% scroll window 上拉 → 关闭标签视图

### 3.7 Sheet ↔ Dock 过渡动画

**对齐 Telegram `ArticleViewer` sheet ↔ dock 过渡**

#### 最小化 (Sheet → Dock)

1. 保存 WebView 状态
2. 截图: `PixelCopy` (API 26+, async)
3. Spring 动画 (220, 30) × 1.1 duration
4. Lerp: fullscreen rect → dock rect
5. 完成后: 释放 WebView，dock 显示 preview

#### 恢复 (Dock → Sheet)

1. 查找 dock 中 `TabDrawable`
2. Spring 动画 (260, 30)
3. Lerp: dock rect → fullscreen rect
4. 完成后: 恢复 WebView Bundle

### 3.8 BrowserReaderScene — Instant View

**对齐 Telegram `ArticleViewer` Instant View 渲染**

#### 内容模型

保留现有 `AppBrowserReaderContent` / `AppBrowserReaderDocument` / `AppBrowserReaderBlock`。

#### 进入动画

- 从右侧滑入, 320ms `EASE_OUT_QUINT`
- `translationX = screenWidth → 0`
- `alpha = 0.92 → 1.0`

#### 返回动画

- 向右滑出, 220ms `EASE_OUT_QUINT`
- `translationX = 0 → screenWidth`
- `alpha = 1.0 → 0.92`

#### 恢复定位动画

- 180ms settle
- `alpha = 0.985 → 1.0`

---

## 四、任务分解

### Phase 1: 状态模型与基础设施

#### Task 1.1: 新状态模型 (BrowserTabState + BrowserSheetState)

**新建文件:**
- `feature/browser/BrowserTabState.kt`
- `feature/browser/BrowserSheetState.kt`

**内容:**
- `BrowserTabState` 数据类 (对齐 WebTabData)
- `BrowserSheetAnchor` sealed interface
- `BrowserSheetPresentationState` 数据类
- `BrowserSheetSettle` 结算逻辑
- Sheet 动画参数常量

**验收:** 编译通过，单测覆盖 settle 逻辑

#### Task 1.2: 新 UI 状态模型

**新建文件:**
- `feature/browser/BrowserChromeState.kt`

**改写文件:**
- `feature/browser/AppBrowserUiState.kt` → 重命名为 `BrowserUiState.kt`
- `feature/browser/AppBrowserAction.kt` → 重命名为 `BrowserAction.kt`

**内容:**
- `BrowserChromeScene` enum (Browse / Search / AddressEditing / HistoryPopup / OverflowMenu / Subpanel)
- `BrowserChromeModel` 数据类
- `BrowserUiState` 数据类
- `BrowserAction` sealed interface (扩充所有动作)
- 自适应主题计算: `computePerceivedBrightness` + 颜色派生

**验收:** 编译通过

#### Task 1.3: 新 ViewModel

**改写文件:**
- `feature/browser/AppBrowserViewModel.kt` → 重命名为 `BrowserViewModel.kt`

**内容:**
- 持有 `MutableStateFlow<BrowserUiState>`
- 处理所有 `BrowserAction`
- 地址栏建议管理
- 搜索状态管理
- Chrome model 更新

**验收:** 编译通过，单测覆盖核心 action 处理

#### Task 1.4: 新 WebView 控制器

**改写文件:**
- `feature/browser/AppBrowserWebViewController.kt` → 重命名为 `BrowserWebViewController.kt`
- `feature/browser/AppBrowserWebView.kt` → 重命名为 `BrowserWebView.kt`

**内容:**
- `BrowserWebViewController` 接口扩充: `saveState()` / `restoreState(Bundle)` / `capturePreview()` / `evaluateJavascript()`
- WebView settings 对齐 Telegram 配置
- User Agent 定制
- WebViewClient / WebChromeClient 回调完整实现
- Edge swipe 后退手势

**验收:** 编译通过，能打开 URL 并显示

---

### Phase 2: Sheet 容器与动画

#### Task 2.1: BrowserSheet 容器

**新建文件:**
- `feature/browser/BrowserSheet.kt`

**删除文件:**
- `feature/browser/AppBrowserSheetContainer.kt`

**内容:**
- `@Composable BrowserSheet(...)` — 物理 sheet 容器
- `openProgress` 动画: 320ms `EASE_OUT_QUINT`
- `dismissProgress` 动画: 250ms `EASE_OUT_QUINT`
- 阴影: `shadowLayer(18dp, -3dp, 0.26f)`
- 圆角: `lerp(16dp, 0dp, attachedProgress)`
- 拖拽把手: `lerp(32dp, 48dp, attachedProgress)`
- 背景遮罩: `alpha = 0.10f * openProgress`
- 嵌套滚动联动 (WebView/LazyColumn 到顶后驱动 dismissProgress)
- Settle 逻辑: 25% 阈值 dismiss / 900px/s velocity

**验收:** Sheet 能以正确动画打开/关闭/手势拖拽

#### Task 2.2: BrowserSheetDialog 窗口包装

**新建文件:**
- `feature/browser/BrowserSheetDialog.kt`

**内容:**
- Dialog 窗口包装器，给 Sheet 正确的窗口层级
- 处理 status bar / navigation bar inset
- 处理 back press dispatch

**验收:** Sheet 在独立 Dialog 中运行，不影响底层 UI

---

### Phase 3: ActionBar 顶栏

#### Task 3.1: BrowserActionBar 核心

**新建文件:**
- `feature/browser/BrowserActionBar.kt`

**删除文件:**
- `feature/browser/AppBrowserChrome.kt`

**内容:**
- 56dp 高度顶栏布局
- Back 按钮 (54×56dp)
- Forward 按钮 (54×56dp, 条件显示)
- Menu 按钮 (54×56dp)
- 标题/副标题区域 (双 slot 切换动画)
- 2dp `LinearProgressIndicator`
- 自适应主题 (亮度 0.721 阈值)

**验收:** Browse 模式下顶栏正确显示

#### Task 3.2: 地址栏模式

**改写文件:**
- `feature/browser/BrowserActionBar.kt` (追加)

**新建文件:**
- `feature/browser/BrowserAddressBar.kt`

**删除文件:**
- `feature/browser/AppBrowserAddressSheet.kt`

**内容:**
- 长按标题触发地址编辑
- Haptic feedback
- Pill 形 input (50dp radius)
- 360ms `EASE_OUT_QUINT` 进入动画
- 清除按钮
- IME action: `IME_ACTION_GO`
- 建议列表

**验收:** 地址栏模式能正确进入/退出/提交

#### Task 3.3: 搜索模式

**新建文件:**
- `feature/browser/BrowserSearchBar.kt`

**内容:**
- 覆盖标题区域
- 320ms `EASE_OUT_QUINT` 进入动画
- IME action: `IME_ACTION_SEARCH`
- 匹配数显示
- 上/下导航按钮

**验收:** 搜索模式能正确进入/退出/查找/导航

#### Task 3.4: 菜单系统

**新建文件:**
- `feature/browser/BrowserMenu.kt`

**内容:**
- 复用 `NativeDoAnchoredPopup` + `NativeDoPopupMenuItem`
- 自适应明暗: 暗色 `#1F1F1F`, 亮色 `#FFFFFF`
- 菜单项: Open External / Forward / Instant View / Refresh / Search / Bookmark / Share / History / Bookmarks / Settings
- 锚点: 右上角 menu 按钮
- 动画: `fade + scale`

**验收:** 菜单显示正确，所有项可触发

---

### Phase 4: Tab Dock 与 Tab Manager

#### Task 4.1: BrowserTabsDock

**新建文件:**
- `ui/BrowserTabsDock.kt`

**删除文件:**
- `ui/BrowserTabsShell.kt` 中 dock 部分

**内容:**
- 导航栏上方定位
- 单标签 60dp / 多标签 68dp 高度
- 卡片 50dp 高度, 10dp 圆角
- 3 层堆叠预览: -8dp offset, scale `lerp(1, 0.95, position)`
- 阴影: 2.33dp blur, 1dp offset
- 颜色自适应 (亮度 0.721)
- Close/Expand/favicon/title

**验收:** Dock 正确显示已最小化标签

#### Task 4.2: BrowserTabsOverlay

**新建文件:**
- `ui/BrowserTabsOverlay.kt`

**删除文件:**
- `ui/BrowserTabsShell.kt` 中 overlay 部分

**内容:**
- 全屏覆盖层
- 背景高斯模糊 (radius 14)
- 卡片: max 340dp 宽, 75% 屏幕高度
- 3D 透视 `setPolyToPoly` (底部 0.83x 宽, 0.6x 高)
- 标题栏 50dp
- 径向渐变暗角
- 阴影 30dp blur, 10dp offset
- 按压 `ButtonBounce` 0.99x scale
- 打开/关闭 320ms `EASE_OUT_QUINT`
- 滚动: `OverScroller` + 200dp 步长 + 橡皮筋

**验收:** 标签管理器正确显示，可滚动、可交互

#### Task 4.3: 标签滑动关闭

**修改文件:**
- `ui/BrowserTabsOverlay.kt` (追加)

**内容:**
- 水平滑动: `dismissProgress = dx / 300dp`
- 旋转 20° + alpha 衰减
- Spring 弹回: `(285, 20)`
- 飞出: `EASE_OUT_QUINT`

**验收:** 滑动关闭标签动画正确

#### Task 4.4: Sheet ↔ Dock 过渡动画

**新建文件:**
- `ui/BrowserTransition.kt`

**内容:**
- 最小化: `PixelCopy` 截图 → spring(220, 30) → lerp(fullscreen → dock)
- 恢复: spring(260, 30) → lerp(dock → fullscreen) → restore WebView
- 预览 bitmap 管理

**验收:** 最小化/恢复过渡动画流畅

---

### Phase 5: BrowserHost 重写

#### Task 5.1: BrowserHost 核心

**改写文件:**
- `ui/BrowserHost.kt` (全面重写)

**内容:**
- `BrowserHostState`: 持有 `activeTabs: List<BrowserTabState>`, `activeTabId`, `dockedTabs`
- Tab 创建/关闭/切换
- 最小化/恢复逻辑
- Preview 管理 (`RenderNode` API 29+ / `PixelCopy` API 26+)
- Tab 去重: `tryReopenTab` 按 URL 匹配

**验收:** 多标签管理正常，标签切换/最小化/恢复正常

#### Task 5.2: BrowserHost 与 NativeDoApp 接入

**修改文件:**
- `ui/NativeDoApp.kt` (browser host 接入点)

**内容:**
- 替换现有 `BrowserHost` 调用
- 连接 `BrowserTabsDock` 到底部
- 连接 `BrowserTabsOverlay` 到覆盖层

**验收:** 完整浏览器流程可用

---

### Phase 6: Reader Scene 重写

#### Task 6.1: BrowserReaderScene 重写

**改写文件:**
- `feature/browser/AppBrowserReaderScene.kt` → 重命名为 `BrowserReaderScene.kt`

**内容:**
- 进入动画: 320ms `EASE_OUT_QUINT`, `translationX = screenWidth → 0`, `alpha = 0.92 → 1.0`
- 返回动画: 220ms, `translationX = 0 → screenWidth`, `alpha = 1.0 → 0.92`
- 嵌套滚动: 贴顶后 header 折叠 (56dp → 32dp)
- Reader 内容渲染 (复用 PostTextDocumentView / PostImageView)
- Media viewer 联动
- Sheet 拖拽联动

**验收:** Instant View 正确渲染，动画流畅

---

### Phase 7: 子面板与辅助

#### Task 7.1: 历史面板

**改写文件:**
- `feature/browser/AppBrowserHistoryPanel.kt` → 重命名为 `BrowserHistoryPanel.kt`

**内容:** 对齐新 scaffold slot，使用新 action/state 模型

#### Task 7.2: 书签面板

**改写文件:**
- `feature/browser/AppBrowserBookmarksPanel.kt` → 重命名为 `BrowserBookmarksPanel.kt`

**内容:** 同上

#### Task 7.3: 设置面板

**改写文件:**
- `feature/browser/AppBrowserSettingsPanel.kt` → 重命名为 `BrowserSettingsPanel.kt`

**内容:** 同上

#### Task 7.4: Instant View 加载器

**改写文件:**
- `feature/browser/AppBrowserInstantViewLoader.kt` → 保留核心逻辑
- `feature/browser/AppBrowserInstantViewResolver.kt` → 保留核心逻辑

**内容:** 只改接口适配新 state 模型，解析逻辑不变

#### Task 7.5: 地址建议

**改写文件:**
- `feature/browser/AppBrowserAddressResolver.kt`
- `feature/browser/AppBrowserAddressSuggestions.kt`

**内容:** 只改接口适配新 state 模型

---

### Phase 8: 组装路由与清理

#### Task 8.1: BrowserRoute 组装

**新建文件:**
- `feature/browser/BrowserRoute.kt`

**删除文件:**
- `feature/browser/AppBrowserRoute.kt`

**内容:**
- ViewModel 实例化
- WebView 协调
- Sheet/Popup/面板 状态持有
- Cookie 同步 (open/close/navigate)
- 下载管理
- 文件选择器
- SSL 错误对话框
- 权限请求

**验收:** 完整浏览器功能可用

#### Task 8.2: BrowserScreen 组装

**改写文件:**
- `feature/browser/AppBrowserScreen.kt` → 重命名为 `BrowserScreen.kt`

**内容:** 装配 `NativeDoBrowserScaffold` + 新 ActionBar + 新内容区

#### Task 8.3: 旧文件清理

**删除所有 `App` 前缀旧文件：**
- `AppBrowserRoute.kt`
- `AppBrowserChrome.kt`
- `AppBrowserScreen.kt`
- `AppBrowserSheetContainer.kt`
- `AppBrowserState.kt` (保留 reader 解析部分，移到 `BrowserReaderParser.kt`)
- `AppBrowserUiState.kt`
- `AppBrowserAction.kt`
- `AppBrowserViewModel.kt`
- `AppBrowserWebView.kt`
- `AppBrowserWebViewController.kt`
- `AppBrowserAddressSheet.kt`

---

## 五、实施顺序

```
Phase 1 (Task 1.1 → 1.4)  — 状态模型与基础设施
    ↓
Phase 2 (Task 2.1 → 2.2)  — Sheet 容器与动画
    ↓
Phase 3 (Task 3.1 → 3.4)  — ActionBar 顶栏 (可部分并行)
    ↓
Phase 4 (Task 4.1 → 4.4)  — Tab Dock 与 Manager
    ↓
Phase 5 (Task 5.1 → 5.2)  — BrowserHost 重写
    ↓
Phase 6 (Task 6.1)        — Reader Scene 重写
    ↓
Phase 7 (Task 7.1 → 7.5)  — 子面板与辅助 (可并行)
    ↓
Phase 8 (Task 8.1 → 8.3)  — 组装路由与清理
```

**可并行的任务:**
- Task 3.1–3.4 内部可部分并行（3.1 先行，3.2/3.3/3.4 可并行）
- Task 4.1 和 4.2 可并行
- Task 7.1–7.5 全部可并行

---

## 六、验证策略

### 单测

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet
```

**必须覆盖:**
- `BrowserSheetSettle` settle 逻辑 (各锚点切换)
- `computePerceivedBrightness` 亮度阈值
- `BrowserTabState` 序列化/反序列化
- `BrowserViewModel` action 处理
- Reader 解析逻辑 (已有，保留)

### 编译验证

```bash
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
```

每个 Phase 完成后必须通过编译。

### 手动验证清单

- [ ] 打开外部链接 → Sheet 从底部弹出 (320ms)
- [ ] 手势下拉 → 正确 dismiss
- [ ] 顶栏自适应深色/浅色网页
- [ ] 长按标题 → 地址栏模式
- [ ] 搜索 → 页内查找
- [ ] 菜单 → 所有项可用
- [ ] 最小化 → Dock 显示标签
- [ ] Dock 点击 → 恢复 Sheet
- [ ] 多标签 → 标签管理器
- [ ] 标签管理器滑动关闭标签
- [ ] Instant View → 从右侧滑入
- [ ] Cookie 同步 → linux.do 登录态保持

---

## 七、命名约定

- 新文件统一使用 `Browser*` 前缀（不再使用 `AppBrowser*`）
- 数据类保持 `data class`
- 状态 sealed interface
- Composable 函数 `@Composable internal fun Browser*()`
- 动画常量 `private const val Browser*DurationMillis`
- 尺寸常量 `private val Browser*Height`

---

## 八、风险与取舍

| 风险 | 缓解 |
|---|---|
| 3D 透视变换 Compose 中实现复杂 | 使用 `graphicsLayer { transformOrigin; rotationX/Y }` 或 `AndroidView` 包装 Canvas `setPolyToPoly` |
| 高斯模糊背景性能 | 复用 Phase B `BackdropHost` 已有基座；低端机降级为 `TintOnly` |
| WebView 嵌套滚动冲突 | 参考 Telegram 的 `NestedScrollingParent` 实现，转译为 Compose `NestedScrollConnection` |
| PixelCopy 异步截图 | API 26+ 保证；截图失败时用 `webView.draw(Canvas)` 兜底 |
| 文件数量大 (~22 个文件) | 分 Phase 实施，每 Phase 独立编译验证 |
