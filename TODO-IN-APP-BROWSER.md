# TODO: In-App Browser

将 NativeDo 的外链与站内网页跳转统一收口，优先在 app 内完成打开与兜底，减少频繁跳出系统浏览器。

这个方案**参考 Telegram 的行为设计、界面组织、交互细节、代码抽象层次与动效节奏**，但**不直接复制 Telegram 代码**。Telegram Android 源码是 GPL v2+，当前仓库是 MIT，只能借鉴思路与实现方法，不能照搬源码实现。

---

## 目标

- 对用户来说：
  - 常见外链优先在 app 内打开
  - 已有原生页面能力的站内链接继续走 native 导航
  - 尚未原生实现的 `linux.do` 页面在 app 内 WebView 兜底
- 对代码来说：
  - 所有链接点击统一走一个入口
  - 跳转决策放在 root 层执行，不把 `NavController` 和 WebView 状态散落到各个组件

## 首版边界

### 首版要做

- 建立统一的 `LinkRouter`
- 引入全屏 `AppBrowserRoute`
- 让站内未覆盖页面走 app 内浏览器兜底
- 支持外部专属 App 检测
- 支持 `linux.do` 登录态在浏览器内可用

### 首版不做

- 不做 BottomSheet 版本，首版直接使用全屏 route
- 不做 Chrome Custom Tabs
- 不做“永不在应用内打开某域名”的用户级黑名单
- 不强行把 `/c/*`、`/tag/*`、`/search?q=` 立即映射到 native 页面

## Telegram 参考边界

### 可以参考

- 浏览器 chrome 的信息架构
- 顶部操作区的布局方式
- 菜单入口与菜单项组织
- 页面切换、打开、关闭时的动效节奏
- 路由器的职责划分
- 浏览器与外部 App、系统浏览器之间的分发策略

### 不可以做

- 不复制 Telegram 现有 Java 代码
- 不保留 Telegram 特有命名、类层级或实现细节
- 不把 Telegram 的大而全 browser 基建原样搬进当前项目

当前项目只吸收对 NativeDo 有直接价值的部分：样式方向、交互模式、抽象边界和动效参考。

### 命名约定

代码命名统一使用 `AppBrowser*` 风格，例如：

- `AppBrowserRoute`
- `AppBrowserState`
- `LinkOpenTarget.AppBrowser`

`in-app browser` 只作为产品语义描述，不作为类名或状态名的一部分。

---

## 模块一：LinkRouter

所有链接点击统一走 `LinkRouter`，替换当前散落的 `LocalUriHandler.openUri()`、`Intent.ACTION_VIEW` 和默认 `URLSpan` 行为。

### 设计原则

- 底层文本/图片组件只负责抛出“打开这个 URL”
- 真正的路由执行放在 root host
- `LinkRouter` 本身负责**分类**，root 负责**执行**

建议输出一个统一结果，例如：

```kotlin
sealed interface LinkOpenTarget {
    data class NativeTopic(val topicId: Long) : LinkOpenTarget
    data class NativeUser(val username: String) : LinkOpenTarget
    data class ExternalApp(val url: String) : LinkOpenTarget
    data class AppBrowser(val url: String) : LinkOpenTarget
    data class SystemBrowser(val url: String) : LinkOpenTarget
}
```

### 首版决策优先级

1. **Native 内部页面**
   - `/t/{slug}/{id}`、`/t/{id}`
   - `/u/{username}`
2. **外部专属 App**
   - `intent://`
   - `mailto:`
   - `tel:`
   - 或能被非浏览器 App 处理的普通 URL
3. **App Browser**
   - 其余 `linux.do` 站内链接
   - 没有专属 App 的普通网页链接
4. **系统浏览器**
   - 用户在浏览器内主动点“在系统浏览器中打开”
   - 或未来设置项明确要求默认系统浏览器

### 关键取舍

`linux.do` 站内链接首版**不要急着全部 native 化**。

当前项目真正已有的 native 路由只有：

- `topic/{topicId}`
- `user/{username}`
- `search`
- `login`

所以像 `/c/*`、`/tag/*`、`/search?q=` 这类站内链接，首版更合理的策略不是“补 parser case”，而是：

- 先认定为 `linux.do` 内链
- 再交给 `AppBrowserRoute` 兜底

这样才能满足“某些功能还不完善时，用户仍可在 app 里使用”的目标。

### 外部 App 检测逻辑

参考 Telegram 的策略，但自己实现：

- **Android 11+ (API 30)**：
  - 使用 `Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER`
  - 如果抛出 `ActivityNotFoundException`，说明没有专属 App
- **Android 10 及以下**：
  - 查询浏览器包列表
  - 查询目标 URL 的 handler 列表
  - 做差集，剩余的视为专属 App

参考位置：

- `Browser.openInExternalApp()` [Telegram Browser.java]
- `Browser.hasAppToOpen()` [Telegram Browser.java]

---

## 模块二：全局承载方式

`LinkRouter` 不应该直接存在于单个 feature 内。

当前项目的导航与全局 UI 都在 root 层：

- `NativeDoApp.kt`
- `NativeDoNavHost.kt`

首版建议：

- 在 root 维护一个 `appBrowserState`
- 为浏览器增加独立 route，例如 `app-browser?url=...`
- 所有 feature 通过统一回调 `onOpenLink(url)` 把事件抛给 root

### 为什么不用 BottomSheet

首版直接做全屏 route，原因：

- WebView 需要完整承载登录、Cloudflare challenge、表单与多步跳转
- 需要和已有 `login` route 一样稳定地参与返回栈
- 顶部地址栏、进度条、分享、外部打开等 chrome 更适合全屏
- BottomSheet 会增加键盘、手势冲突、嵌套滚动和高度适配成本

---

## 模块三：AppBrowserRoute

实现一个全屏 WebView 页面，风格对齐当前 liquid-glass 设计。

### UI 组成

- 顶部栏
  - 返回
  - 当前域名
  - 安全状态占位
  - 右上角菜单入口
- 顶部线性进度条
- 页面内容区
- 右上角菜单
  - 前进
  - 刷新
  - 分享
  - 在系统浏览器中打开
  - 复制链接
  - 关闭

### 顶部操作区要求

- 参考 Telegram 的浏览器 chrome，把主要操作收敛到**右上角菜单**
- 不使用底部工具栏作为首版主方案
- 返回保留在左侧
- 当前域名和安全状态保留在中间主标题区域

这样更贴近 Telegram 的使用习惯，也更符合当前 app 顶栏语言

### WebView 配置

- 启用 JavaScript
- 启用 DOM Storage
- 复用现有 `userAgent`
- 允许 cookie / third-party cookies

### 必须补齐的能力

- `shouldOverrideUrlLoading`
  - 所有新链接继续经过 `LinkRouter`
- `WebChromeClient.onCreateWindow`
  - 处理 `window.open` / `target="_blank"`
  - 首版统一在当前浏览器 route 内打开，不创建第二个浏览器栈
- 页面进度监听
- 页面标题变化监听

### 菜单与交互参考

- 菜单入口位于右上角，作为浏览器操作的主入口
- 菜单弹层从右上角按钮锚定展开
- 菜单项优先展示浏览器内语义最强的动作，而不是把所有操作平铺在界面上
- 菜单样式、信息密度、打开方式可以参考 Telegram，但视觉材质要保持 NativeDo 当前 glass 方向

### 动效要求

浏览器页面本身，以及右上角菜单，都应参考 Telegram 的“短、克制、明确”的动效风格：

- **浏览器打开**
  - 使用轻量进入转场
  - 避免沉重的 Material 默认页面切换
- **浏览器关闭**
  - 参考 Telegram `ArticleViewer.close()` 的节奏
  - 以短时长的 `fade + 轻微水平位移` 为主
  - 不做夸张缩放或 sheet 式下坠
- **右上角菜单展开/收起**
  - 使用锚点明确的 `fade + scale` 小动效
  - 菜单关闭要干净，不拖泥带水

动效目标不是“像 Telegram 一模一样”，而是借鉴它那种高密度工具界面里依然很利落的反馈感。

### 与登录态相关的关键点

现有 `CookieSyncBridge` 只有：

- `WebView -> CookieStore`

首版浏览器接入前必须补成双向同步：

1. **打开浏览器前**
   - 从 `CookieStore` 把 `linux.do` 相关 cookie 回写到 WebView
2. **浏览器内完成跳转后**
   - 在同域页面完成时，把 WebView cookie 再同步回 `CookieStore`
3. **关闭浏览器时**
   - 再做一次 `syncFromWebView()`，确保会话变化不会丢失

如果不补这一步，冷启动后原生层已恢复登录态，但新开的 WebView 仍可能是未登录状态。

### Cloudflare

- 如果浏览器内触发 Cloudflare challenge，允许用户在当前 WebView 原地完成
- challenge 通过后，同步 cookie 回原生层

---

## 模块四：当前需要替换的调用点

首版不能只改 onebox。

### 明确替换点

- `PostBodyBlockRenderers.kt`
  - `OneboxBlockView` 当前直接 `uriHandler.openUri(block.targetUrl)`
- `PostImageViewer.kt`
  - `OpenExternal` 当前直接 `Intent.ACTION_VIEW`
- `PostBodyInternalLinks.kt`
  - 当前只替换内部链接，外部链接仍保留系统默认行为
- `PostInlineText.kt`
  - `PostInlineUrlSpan` 现在会直接走系统浏览器
- `PostTextDocumentView.kt`
  - 复用 `buildPostBodyLinkSpan()`，也会受同样问题影响

### 改造方向

把文本链路统一成：

- `buildPostBodyText(...)` 支持 `onOpenLink(url)`
- `buildPostBodyLinkSpan(...)` 支持 `onOpenLink(url)`
- 对于 native 已支持的 topic / user，仍然保留直接回调
- 对其余 URL，不再依赖系统默认 `URLSpan`

这样 `HtmlTextBlock`、`PostInlineText`、`PostTextDocumentView` 才能表现一致。

---

## 模块五：内部链接策略

### 首版 native 识别范围

- `/t/{slug}/{id}`
- `/t/{id}`
- `/u/{username}`

### 首版先不 native 的路径

- `/c/{category}/{id}`
- `/tag/{tag}`
- `/search?q=...`
- 其他 `linux.do` 站内页面

这些路径首版统一走 `AppBrowserRoute`。

### 第二阶段再做 native 扩展

只有在下列条件成立时再加 native 映射：

- 已存在清晰的目标 route
- 已有可用页面承载
- 用户体验明显优于 WebView 兜底

例如：

- `/search?q=...`
  - 等 `SearchRoute` 支持 query 注入后再接
- `/c/*`
  - 等分类页面存在后再接
- `/tag/*`
  - 等标签页存在后再接

---

## 模块六：设置项

设置项移到第二阶段。

原因：

- 当前项目没有独立 `feature/settings`
- 也没有现成的偏好存储承载浏览器开关
- 首版目标是先把 app 内浏览器接通，而不是同步建设完整设置系统

第二阶段可考虑：

- `externalLinkPreference`
  - `AppBrowser`
  - `SystemBrowser`
- `neverOpenInAppDomains`
  - 参考 Telegram 的 `RestrictedDomainsList`

---

## 实施顺序

1. **补 CookieSyncBridge 双向同步**
   - 增加 `CookieStore -> WebView`
   - 保留现有 `WebView -> CookieStore`
2. **建立 LinkRouter 分类模型**
   - 只识别首版真正支持的 native 页面
3. **在 root 接入浏览器 route**
   - `NativeDoApp` / `NativeDoNavHost` 持有全局承载
4. **实现 AppBrowserRoute**
   - 全屏 WebView
   - 顶部 chrome + 右上角菜单
   - 进度、分享、系统浏览器跳转
   - 参考 Telegram 的打开/关闭动效
5. **替换所有现有链接出口**
   - onebox
   - 文本 span
   - TextDocument span
   - image viewer
6. **接入外部专属 App 检测**
   - API 30+ 使用 `REQUIRE_NON_BROWSER`
   - API 28-29 使用差集查询
7. **第二阶段**
   - 设置项
   - restricted domains
   - 更多 native 内链映射

---

## 验证建议

实现时至少覆盖以下验证：

- `PostBodyInternalLinksTest`
  - 保持 topic / user 识别不回退
- 新增 `LinkRouter` 单测
  - `linux.do` topic -> native
  - `linux.do` category/tag/search -> app browser
  - `mailto:` / `tel:` / `intent://` -> external app
- 新增 `CookieSyncBridge` 单测
  - `CookieStore -> WebView`
  - `WebView -> CookieStore`
- Android 验证
  - `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
  - `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

---

## 参考代码

### 当前项目

- `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/auth/ui/LoginWebViewRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/CookieSyncBridge.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyInternalLinks.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineText.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostTextDocumentView.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostImageViewer.kt`

### Telegram 参考

- `TMessagesProj/src/main/java/org/telegram/messenger/browser/Browser.java`
  - `openInExternalApp()`
  - `hasAppToOpen()`
  - `isInternalUri()`
- `TMessagesProj/src/main/java/org/telegram/ui/ArticleViewer.java`
  - 顶部操作区与右上角菜单组织
  - 历史项/操作项的弹出方式
  - 关闭时的短时长淡出与位移动画
- `TMessagesProj/src/main/java/org/telegram/ui/web/RestrictedDomainsList.java`

仅参考行为、样式方向、交互模式、抽象思路与动效节奏，不复制实现代码。
