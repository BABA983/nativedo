# NativeDo 液态玻璃主题改造设计

**日期：** 2026-04-01  
**状态：** 已确认，开始实施  
**目标：** 在不引入重型视觉基础设施的前提下，让 NativeDo 的 Android UI 靠近 Telegram 近期开启的 iOS 风格液态玻璃方向，同时补齐可用的亮色 / 暗色主题。

---

## 背景

当前 Android 客户端已经可以运行，但视觉层仍停留在基础 Material 组件状态：

- 主题只有少量基础颜色，缺少 `chrome / glass / content` 分层
- 顶部栏、底部栏、搜索框、列表项都还是默认 Material 质感
- 亮色与暗色只做了基础背景切换，没有形成完整风格语言

参考 `Telegram` Android 项目后，可以确认它的核心不是单页配色，而是先定义玻璃语义，再统一应用到顶部栏、底部栏和容器层。

---

## 目标

- 建立更完整的 Compose 主题语义：基础背景、内容层、玻璃层、描边、强调色
- 提供亮色、暗色和跟随系统三种主题模式
- 重做顶栏与底栏，使其成为浮层式 glass chrome
- 重做首页 topic 列表项和搜索框，让内容风格与 chrome 统一
- 保持当前导航、业务状态和页面结构基本不变

## 非目标

- 不复刻 Telegram 的动态背景捕获、真实 backdrop blur 和液态折射实现
- 不新增设置页、复杂偏好系统或持久化层重构
- 不改动业务数据流、导航结构和登录流程

---

## 方案比较

### 方案一：只换颜色

优点：

- 改动最少

缺点：

- 无法真正靠近目标风格
- chrome 和内容层次仍然扁平

### 方案二：轻量 Compose 版 glass design system

优点：

- 能显著改善整体观感
- 改动主要集中在主题和 UI 容器，风险可控
- 保持现有 Compose 架构，不引入 Telegram 那套重型基础设施

缺点：

- 属于视觉近似，不是真实的后台模糊

### 方案三：自建 Telegram 式 blur / glass 基建

优点：

- 视觉还原度最高

缺点：

- 复杂度明显超出当前项目需要
- 会把本次任务从 UI 改造升级为图形基础设施开发

### 结论

采用**方案二：轻量 Compose 版 glass design system**。

---

## 设计决策

### 1. 主题分层

保留 Material3 作为基础，但补一层 NativeDo 自己的扩展颜色语义：

- `background`
- `backgroundElevated`
- `glassBackground`
- `glassBackgroundStrong`
- `glassStroke`
- `glassShadow`
- `accent`
- `accentMuted`
- `textPrimary`
- `textSecondary`

这样可以让 top bar、bottom bar、card、search field 使用相同语义，而不是直接依赖 `surface`。

### 2. 亮暗模式

引入三态主题模式：

- `System`
- `Light`
- `Dark`

首轮实现只要求在当前 app shell 内可切换并即时生效，不扩展到完整偏好系统。

### 3. Chrome 语言

顶部栏和底部栏采用以下语言：

- 半透明圆角容器
- 细描边
- 轻阴影
- 选中态胶囊背景
- 内容下沉，chrome 浮在背景之上

### 4. 内容层语言

首页和搜索页的内容容器遵循相同原则：

- topic item 改为卡片式信息块
- 搜索框改为圆角 glass field
- 页面背景增加柔和渐变和高光层

### 5. 实施边界

本轮直接编辑点：

- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt`

---

## 验证方式

- 单测覆盖主题模式的状态切换逻辑
- `assembleDebug` 确认 Compose UI 改造后仍可编译
- 如条件允许，执行定向 unit test

---

## 结论

本次改造的重点不是“把颜色换得更像 iOS”，而是给 NativeDo 建立一套轻量但明确的视觉语义层，让亮色、暗色、chrome 和内容卡片说同一种视觉语言。
