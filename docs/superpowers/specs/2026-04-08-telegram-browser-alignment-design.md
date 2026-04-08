# NativeDo Telegram 风格浏览器对齐设计

**日期：** 2026-04-08  
**状态：** 已确认，待拆实现计划  
**目标：** 将当前 `AppBrowser` 从“可用 WebView 页面”升级为更接近 Telegram browser 的完整子系统，补齐历史、设置、页面栈感、主题自适应菜单与大面板交互。

---

## 背景

当前 NativeDo 的浏览器能力还停留在首版壳层：

- `AppBrowserRoute` 负责单个 WebView 承载
- `AppBrowserChrome` 负责顶部栏与右上角菜单
- `History` 仍然过度依赖当前 WebView back stack
- `Search` / `Settings` 还是占位弹窗
- 菜单与历史展示仍偏轻量 popup，而不是 Telegram 那种真正的浏览器子页面

对照 Telegram Android 可以确认，它的 browser 不是几个动作菜单，而是一套完整结构：

- 当前页 chrome：`WebActionBar`
- 当前页 back stack / pages stack：`ArticleViewer`
- 全局 browser history：`BrowserHistory`
- 大面板历史页：`HistoryFragment`
- 浏览器设置页：`WebBrowserSettings`
- 地址栏 / 搜索 / recent / bookmarks 综合列表：`AddressBarList`

参考位置：

- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/web/WebActionBar.java`
- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/ArticleViewer.java`
- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/web/BrowserHistory.java`
- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/web/HistoryFragment.java`
- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/web/WebBrowserSettings.java`
- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/web/AddressBarList.java`

当前仓库只能借鉴行为、信息架构、状态边界和视觉方向，不能复制 Telegram 的 GPL 代码。

---

## 目标

- 菜单里的 `History` 改为真正的大面板历史页，不再使用小 popup
- 历史页支持日期分组、搜索过滤、点击重新打开历史 URL
- 明确区分两类历史：
  - 当前页 back stack，用于长按返回
  - 全局 browser history，用于菜单里的 `History`
- 增加真正的浏览器设置面板，而不是占位 `AlertDialog`
- 菜单样式继续向 Telegram 对齐：
  - 亮暗主题自适应
  - 分组明确
  - 文本、图标、底色一起切换
- 浏览器整体层级与交互往 Telegram 靠拢：
  - 单一 browser route 承载
  - 内部切换 history/settings 等大面板
  - 保持“浏览器是一个子系统”，不是散落在 app 根导航里

## 非目标

- 不复制 Telegram 的 Java 类层级与实现细节
- 不在本轮直接实现完整多 tab 浏览器
- 不在本轮引入 Chrome Custom Tabs
- 不把 browser 变成通用 Discourse SDK 组件
- 不在本轮优先做 bookmarks / restricted domains / search engine 的完整产品闭环

---

## 差距分析

### 功能差距

- 当前菜单 `History` 绑定的是当前页 back stack，不是全局 browser history
- 当前 `History` 是小 popup，没有日期分组，也没有搜索
- 当前 `Settings` 不是设置页，只是占位
- 当前没有 browser 级的持久历史模型
- 当前没有 browser 内部“大面板页面”概念

### 结构差距

- 当前 browser 的状态主要散落在 `AppBrowserRoute` 本地
- 当前没有 browser 级 store 去承载跨页面历史和后续设置能力
- 当前 root 只知道“打开 browser route”，不知道 browser 内部有 history/settings 子页面

### 样式差距

- 当前 popup 太轻，承载不了 Telegram 那种浏览器子页面感
- 当前缺少面板级 top bar、搜索栏、分组标题、空状态
- 当前虽然已经开始做主题自适应菜单，但还没有形成完整 browser 内部视觉系统

---

## 方案比较

### 方案一：继续在现有 popup 上打补丁

优点：

- 改动最少

缺点：

- 做不出 Telegram 的大面板历史页
- 会继续把浏览器能力塞进一个越来越重的 popup
- 结构上仍然不清晰

### 方案二：单一 browser route + 内部大面板子页面

优点：

- 最贴近 Telegram browser 的组织方式
- root 导航边界保持稳定
- 浏览器内部能力可以独立扩展
- 适合后续继续加 address/search/bookmarks/settings

缺点：

- 比局部补丁重
- 需要新增 browser 内部状态和多个 composable 面板

### 方案三：把 history/settings 都提升为 app 主导航 route

优点：

- Jetpack Navigation 层面简单直接

缺点：

- 会把 browser 子系统打散到根导航
- 不像 Telegram 的“浏览器内部页”
- 后续 address/search/bookmarks 会更难组织

### 结论

采用 **方案二：单一 browser route + 内部大面板子页面**。

---

## 总体设计

### 1. Browser 承载模型

保留现有 `app-browser?url=...` root route，不新增 `history` / `settings` 的独立主导航 route。

`AppBrowserRoute` 升级为浏览器主容器，内部承载三类内容状态：

- `Page`
- `History`
- `Settings`

后续可继续扩展：

- `AddressSearch`
- `Bookmarks`

这样可以保持：

- root 只负责打开和关闭浏览器
- 浏览器内部能力在浏览器子系统内闭环

### 2. 状态边界

#### `AppBrowserHistoryStore`

新增 browser 级 store，负责全局历史记录。

记录项至少包含：

- `url`
- `title`
- `host`
- `visitedAtMillis`

本轮应支持：

- 追加访问记录
- URL 去重并把最近访问提到最前
- 暴露 `StateFlow<List<AppBrowserHistoryRecord>>`

推荐加入本地持久化，使历史在 app 重启后仍可用。

#### `AppBrowserBackStack`

仍然由当前 `WebView.copyBackForwardList()` 驱动。

只负责：

- 顶部返回按钮的当前页返回能力
- 长按 back 呼出“当前页 back stack”

不再承担菜单 `History` 的数据源职责。

#### `AppBrowserPanelState`

新增 route 内部面板状态，例如：

```kotlin
sealed interface AppBrowserPanel {
    data object Page : AppBrowserPanel
    data object History : AppBrowserPanel
    data object Settings : AppBrowserPanel
}
```

`AppBrowserRoute` 通过该状态决定当前展示 WebView 还是内部面板。

### 3. 历史体验

菜单里的 `History` 不再打开 popup，而是打开一个浏览器内部的大面板。

这个面板需要：

- 独立 top bar
- 搜索输入
- 按日期分组
- 支持空状态
- 点击历史项后：
  - 若该 URL 在当前 WebView back stack 中，可直接回退/前进
  - 否则在当前 WebView 中重新加载

日期分组至少包含：

- Today
- Yesterday
- 更早日期

搜索过滤至少支持：

- title
- host
- url

### 4. 设置体验

菜单里的 `Settings` 不再显示占位弹窗，而是打开一个浏览器内部的大面板。

本轮设置页最小范围：

- Open history
- Clear history
- Clear cookies
- Clear cache
- Browser 功能预留项

可延后到下一阶段的内容：

- Restricted domains
- Search engine
- Bookmark 管理
- “永不在应用内打开此域名”

### 5. 菜单与交互分工

#### 顶部返回按钮

- 单击：当前页返回；没有 back stack 时关闭浏览器
- 长按：打开当前页 back stack 列表

#### 菜单 `History`

- 打开全局 browser history 面板
- 不再直接依赖当前页 back stack

#### 菜单 `Settings`

- 打开 browser settings 面板

#### 菜单 `Search`

本轮不再使用占位 dialog。
如果不做完整 address/search overlay，建议先把它移动到下一阶段，避免保留假入口。

### 6. 样式方向

浏览器整体继续沿用当前 NativeDo glass 语言，但组织和层级向 Telegram 对齐：

- 顶部 chrome 仍保持无明显硬边框
- 菜单继续做亮暗主题自适应
- `History` / `Settings` 面板采用更大的承载区域
- 面板内部有明确：
  - section title
  - search field
  - grouped list
  - empty state
  - divider / gap

动效建议：

- `History` / `Settings` 面板从右向左轻量滑入
- 关闭时短时长滑出 + 淡出
- 保持 Telegram 那种短、克制、利落的反馈

---

## 分阶段实施

### Phase 1：浏览器骨架对齐

本阶段目标是让 browser 从“首版工具页”升级为“像 Telegram 的完整浏览器壳层”。

范围：

- 增加 `AppBrowserHistoryStore`
- 历史持久化
- `History` 大面板
- 历史搜索过滤
- `Settings` 大面板
- 菜单入口重组
- 分离 back stack 与 browser history
- 去掉 `Search` / `Settings` 占位弹窗

完成标准：

- 从任何入口打开 browser，菜单 `History` 都基于全局 browser history 工作
- 历史页有日期分组和搜索
- `Settings` 不再是 placeholder
- 长按 back 和菜单 `History` 明确分工

### Phase 2：高级浏览器体验对齐

范围：

- Address / search overlay
- Recent search
- Bookmarks
- Restricted domains
- Search engine
- 更强的页面栈感 / tabs-like 扩展能力

完成标准：

- 更接近 Telegram browser 的完整产品体验

---

## 文件边界建议

### 新增

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserState.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserHistoryPanel.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSettingsPanel.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/AppBrowserHistoryStorePersistence.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/browser/AppBrowserStateTest.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/browser/AppBrowserHistoryPanelStateTest.kt`

### 修改

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserChrome.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`

### 后续阶段新增

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserAddressPanel.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserBookmarksPanel.kt`

---

## 验证方式

### 单元测试

- `AppBrowserHistoryStore` 去重、排序、持久化恢复
- 菜单 `History` 启用条件使用 browser history，而不是当前页 back stack
- 历史搜索过滤逻辑
- 历史分组逻辑

### Android 验证

- `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

### 人工验证

- 从 topic/profile/quick access 打开 browser
- 打开多个页面后检查：
  - back 长按显示当前页 back stack
  - 菜单 `History` 打开大面板
  - 历史支持搜索过滤
  - 点击历史项可回到对应页面
  - 关闭并重启 app 后历史仍可见

---

## 风险与权衡

### 1. 范围膨胀

browser 很容易从一个 feature 变成半个子应用。

处理方式：

- 第一阶段只做 browser 骨架和核心体验
- bookmarks / restricted domains / address overlay 放到第二阶段

### 2. 状态混杂

若把全局历史、当前页 back stack、面板状态都堆进 `AppBrowserRoute`，文件会迅速失控。

处理方式：

- store、panel、route 三层分清职责

### 3. 视觉过拟合 Telegram

不能为了像 Telegram 而直接复制其实现。

处理方式：

- 借鉴信息架构、交互节奏、主题自适应逻辑
- 保持 NativeDo 当前 glass 语义

---

## 结论

本次对齐不应继续沿着“修一个菜单项、补一个 popup”推进，而应把 `AppBrowser` 明确升级成浏览器子系统。

推荐先完成第一阶段：

- 全局 browser history
- 大面板 history
- 大面板 settings
- back stack / browser history 分离
- 菜单与样式继续 Telegram 化

这样做完后，NativeDo 的 browser 才会从“有个 WebView”变成真正接近 Telegram 的浏览器体验。
