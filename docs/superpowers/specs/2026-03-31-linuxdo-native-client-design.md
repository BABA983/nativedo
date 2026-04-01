# NativeDo for Linux.do 原生移动客户端设计

**日期：** 2026-03-31
**分支：** feature/smarthome
**目标：** 设计一个先落地 Android、未来可扩展到 iOS 的 `linux.do` 原生移动客户端，`v1` 优先保证阅读体验与登录链路稳定。

---

## 背景与约束

`linux.do` 本质上是一个 Discourse 社区。参考 `fluxdo` 的实现后，可以确认它并不是调用某套独立的“移动端私有 API”，而是基于以下能力构建客户端：

- Discourse 标准 JSON 接口（如 `/latest.json`、`/t/{id}.json`、`/search.json`）
- Cookie Session 登录态（核心是 `_t` 等 cookie）
- 写操作前的 CSRF 获取（`/session/csrf`）
- 首页 HTML 里的 bootstrap / preloaded 数据
- Cloudflare challenge / `cf_clearance` 兜底
- 可选的 MessageBus 长轮询能力

这个项目的真实复杂度不在“页面数量”，而在“登录态、会话同步、状态建模和扩展边界”。因此设计目标不是一开始做一个功能最全的论坛客户端，而是先把可长期演进的底座搭稳。

额外约束：`linux.do` 首页明确声明禁止 AI 生成站内内容。`v1` 不接入任何 AI 发帖/回帖辅助能力，也不把“生成内容并发布”纳入设计范围。

---

## 目标与非目标

### 目标

- 使用原生技术栈构建客户端：Android 使用 `Kotlin + Jetpack Compose`
- 仓库从第一天就为 iOS 原生版本预留结构
- `v1` 形成完整的“阅读 + 登录”闭环
- 采用可迁移到 iOS 的模块边界与状态模型
- 让 `v2` 可以自然扩展到社区核心交互（回复、点赞、收藏、通知）

### 非目标

- `v1` 不并行开发 iOS 客户端
- `v1` 不做发主题、回复、图片上传、私信、通知中心、实时消息
- `v1` 不接入 `credit.linux.do`、`cdk.linux.do` 等子系统
- `v1` 不追求离线优先和复杂同步系统

---

## 方案比较

### 方案一：Android first，完全按单端产品设计

优点：起步最快，最容易拿到可用版本。

缺点：未来补 iOS 时，协议、状态和流程需要重新翻译，容易形成双端分叉。

### 方案二：Android first，但按双端原生 monorepo 设计

优点：当前只实现 Android，不拖慢 `v1`，同时共享契约、状态认知和工程资产；后续补 iOS 时不需要重新设计产品和协议。

缺点：比方案一多一些前期设计成本，需要更克制的边界划分。

### 方案三：Android / iOS 双端并行

优点：双端一致性最好。

缺点：开发最慢，side project 风险最高；WebView 登录、cookie、Cloudflare 等复杂度会在双端同时出现。

### 结论

选择**方案二：Android first，但按双端原生 monorepo 设计**。

这条路线兼顾了三个现实目标：

- `v1` 尽快做出 Android 成品
- 原生代码保持纯粹，不强行做共享业务层
- 为未来的 `Swift + SwiftUI` 版本提前沉淀契约、状态和流程文档

---

## 仓库结构

建议仓库根目录采用以下结构：

```text
linuxdo-native/
  apps/
    android/
    ios/
  contracts/
    discourse/
  fixtures/
  docs/
    decisions/
  scripts/
  .github/
```

职责划分如下：

- `apps/android`：Android 原生代码，当前唯一实现端
- `apps/ios`：未来的 iOS 原生代码骨架
- `contracts/discourse`：跨端共享的接口、模型、登录与会话契约
- `fixtures`：脱敏接口样例与错误样例
- `docs`：产品范围、架构设计、决策记录
- `scripts`：格式化、fixture 抓取、构建辅助脚本

共享重点不是“共享代码”，而是：

- 共享 API 契约
- 共享状态定义
- 共享错误语义
- 共享登录与会话流程
- 共享页面信息架构

---

## 平台内模块边界

### Android

`v1` 不需要一开始拆很多 Gradle module，先在单 app 内按目录分层即可：

```text
apps/android/
  app/
    src/main/java/.../
      core/
        network/
        model/
        storage/
        designsystem/
      feature/
        home/
        topic/
        search/
        profile/
        auth/
        settings/
      navigation/
```

职责建议：

- `core/network`：HTTP、cookie、CSRF、错误映射、请求拦截器
- `core/model`：Topic、Post、User、Session 等稳定模型
- `core/storage`：DataStore、cookie 持久化、轻量缓存
- `core/designsystem`：主题、颜色、排版、通用组件
- `feature/home`：`Latest / New / Top` feed
- `feature/topic`：帖子详情和楼层流
- `feature/search`：搜索输入、结果页
- `feature/profile`：用户页和个人页
- `feature/auth`：WebView 登录和会话恢复
- `feature/settings`：偏好设置和关于页
- `navigation`：顶层路由和页面进入点

### iOS

未来 iOS 目录应尽量镜像 Android 的领域边界：

```text
apps/ios/
  App/
  Core/
    Network/
    Model/
    Storage/
    DesignSystem/
  Features/
    Home/
    Topic/
    Search/
    Profile/
    Auth/
    Settings/
  Navigation/
```

重点不是代码对齐，而是认知对齐，方便未来把 Android 已验证的设计映射到 Swift 版本。

---

## 命名规则

命名风格参考 `stripe-android` 的职责划分，而不是泛化的 `xxxManager`。

### 推荐命名

- 发请求：`DiscourseApiClient`
- 远端访问：`DiscourseRemoteDataSource`
- 聚合数据：`TopicRepository`、`UserRepository`、`SearchRepository`
- 持久化：`CookieStore`、`SessionStore`
- 提供 token：`CsrfTokenProvider`
- 协调跨模块流程：`SessionCoordinator`、`AuthFlowCoordinator`
- 页面状态：`HomeUiState`、`TopicUiState`、`SearchUiState`、`ProfileUiState`
- 页面状态持有者：`HomeViewModel`、`TopicViewModel` 等

### 不推荐命名

- `ApiManager`
- `DataManager`
- `AuthManager`
- `NetworkManager`

原因是这些名字边界太模糊，后期很容易变成杂物桶。

`Manager` 只适用于真正长期持有并协调账户生命周期的对象；本项目优先使用 `Store`、`Repository`、`Coordinator` 这类职责更明确的后缀。

---

## V1 功能范围

`v1` 的定位是：

- 一个以阅读体验为主、支持登录态接入的 `linux.do` Android 原生客户端

### V1 包含

1. 首页阅读流
2. 帖子详情阅读
3. 搜索
4. 用户页 / 个人页
5. 登录与会话恢复
6. 轻量设置页

### 首页

首页先只做 3 个 feed：

- `Latest`
- `New`
- `Top`

每个 topic item 展示：

- 标题
- 分类
- 作者
- 回复数
- 浏览数
- 最后活跃时间
- 轻量已读状态

### 帖子详情页

优先把“阅读体验”做完整：

- 主题标题和基础信息
- 楼层流
- 作者信息
- `cooked HTML` 渲染
- 图片预览
- 外链跳转
- 引用 / 回复关系的基础展示

### 搜索页

先做最小但可用的版本：

- 搜索输入框
- 主题结果列表
- 用户结果列表
- 最近搜索词本地记忆

### 用户页 / 个人页

同一套页面骨架支持两种视角：

- `My Profile`
- `User Profile`

展示信息先收敛为：

- 头像
- 用户名 / display name
- bio
- 基础统计
- 最近主题或活动入口

### 设置页

- 登录状态
- 退出登录
- 外链打开方式
- 图片加载策略
- 关于页
- 实验功能开关

### V1 明确不做

- 发主题
- 回复发帖
- 点赞 / 收藏
- 通知中心
- 私信
- 图片上传
- 草稿
- MessageBus 实时刷新
- 子系统 OAuth 能力

这些能力统一归入 `v2+`，避免 `v1` 范围失控。

---

## 页面架构

推荐导航结构：

```text
Bottom Tabs
- Home
- Search
- Profile

Push Screens
- Topic Detail
- User Profile
- Settings
- Login WebView
```

优点：

- 一级入口非常稳定
- Android 和 iOS 都容易镜像
- 登录和详情作为二级流转，不污染主导航

---

## 登录与会话设计

`v1` 采用**网页登录，原生持有会话**的方案：

- 在 app 内 `WebView` 完成 `linux.do` 登录
- 登录后把关键 cookie 同步到 app 自己的网络层
- 普通阅读接口和用户接口都走原生 HTTP
- 必要时再退回 WebView 完成验证或重新认证

### 核心模块

- `AuthWebView`：展示网页登录页，监听登录完成信号
- `CookieStore`：持久化 `_t`、`_forum_session`、`cf_clearance` 等 cookie
- `SessionStore`：保存已登录用户摘要和会话元信息
- `SessionCoordinator`：驱动登录、恢复、过期、退出等流程
- `CsrfTokenProvider`：在写操作前提供 `/session/csrf` 结果
- `AuthenticatedHttpClient`：自动附带 cookie、CSRF 和必要 header

### 推荐流程

```text
Anonymous
-> 打开 Login WebView
-> 登录成功并出现关键 cookie
-> 同步 cookie 到 CookieStore
-> 原生请求轻量接口确认会话有效
-> 获取 CSRF
-> 进入 Authenticated
```

### 过期与异常兜底

- 会话过期：清理本地会话，提示用户重新登录
- CSRF 失效：刷新 token 后重试一次
- Cloudflare challenge：跳回 WebView 完成验证，再恢复原流程

`v1` 不追求自动续期 `cf_clearance` 的复杂方案，但必须提供可靠的人工验证兜底入口。

---

## 数据流与状态设计

状态设计参考 `stripe-android`：

- ViewModel 内持有可变状态
- UI 只读 `StateFlow<UiState>`
- `UiState` 允许承载派生属性
- 跨页面流程通过 `Coordinator` 协调

总体数据流：

```text
Screen
-> ViewModel
-> UseCase
-> Repository
-> ApiClient / Store
-> Result
-> ViewModel
-> UiState
-> Screen
```

### 全局状态

推荐只维护少量稳定全局状态：

- `AppSessionState`
  - `Anonymous`
  - `Authenticating`
  - `Authenticated(userSummary)`
  - `Expired`
- `AppNetworkState`
  - `Online`
  - `Offline`
  - `Restricted`
- `AppPreferences`
  - 图片策略
  - 外链打开方式
  - 实验开关

### 页面状态

示例：

- `HomeUiState`
  - `feedTab`
  - `items`
  - `isRefreshing`
  - `isAppending`
  - `error`
  - `isLoggedIn`
- `TopicUiState`
  - `topicMeta`
  - `posts`
  - `isLoading`
  - `isLoadingMore`
  - `error`
  - `canLoadMore`
  - `shouldShowLoginPrompt`
- `SearchUiState`
  - `query`
  - `phase = Idle | Searching | Result`
  - `topics`
  - `users`
  - `recentQueries`
- `ProfileUiState`
  - `viewer = self | other`
  - `profile`
  - `recentTopics`
  - `isLoggedIn`

### 状态建模原则

- 页面不直接感知 cookie、CSRF、HTTP 细节
- 页面只消费可渲染状态
- 派生状态优先由已有状态计算，不重复存储相同事实
- UI 层不直接组合业务规则，规则尽量沉到 `UiState` 或 ViewModel 中

---

## 错误处理与缓存策略

### 错误语义

对 UI 层只暴露用户可理解的错误类别：

- `NetworkError`
- `Unauthorized`
- `ChallengeRequired`
- `EmptyResult`
- `UnknownError`

这套语义以后应写进 `contracts/discourse/errors.md`，保证 Android / iOS 口径一致。

### 缓存策略

`v1` 使用轻量缓存，不做离线优先：

- 首页 feed：内存 + 简单持久化缓存
- 帖子详情：缓存最近阅读的主题
- 用户信息：短时缓存
- 搜索结果：不做重缓存

目标是冷启动更快、页面返回更顺，而不是支持完整离线使用。

---

## 测试与验证策略

设计阶段就约束测试边界，避免未来写成碎片化小测试：

- ViewModel：覆盖状态流转的关键路径测试
- Repository：只测试明显复杂的数据映射和错误分支
- 登录链路：优先做高价值集成测试或假数据回归测试
- UI：首版以少量截图测试或关键交互测试为主，不做过度铺量

高风险行为优先测试：

- 登录成功后的会话恢复
- 会话过期后的状态回退
- 首页 / 详情页的空态、错误态、刷新态
- 搜索结果和用户结果切换

---

## V2 扩展位

`v2` 在 `v1` 稳定后再扩展社区核心交互：

- 回复
- 点赞
- 收藏
- 通知中心
- 关注关系

`v3+` 再考虑：

- 发主题
- 图片上传
- 草稿
- 私信
- MessageBus 实时刷新
- 子系统集成

这种分期方式的核心目的是让 `v1` 先成为一个长期可用的阅读客户端，而不是半成品的“全功能客户端”。

---

## 首阶段实施顺序

1. 建立 monorepo 骨架与 `contracts / fixtures / docs` 目录
2. 搭建 Android app 基础结构与 design system
3. 实现匿名阅读链路：首页、详情、搜索、用户页
4. 接入 WebView 登录与 cookie 同步
5. 建立 `SessionCoordinator` 与全局会话状态
6. 补齐设置页、缓存、错误态和关键测试
7. 稳定后再进入 `v2` 的交互能力设计

---

## 结论

本设计的核心决策是：

- 先做 Android 原生客户端
- 从第一天按双端原生 monorepo 组织
- 不共享业务代码，只共享契约和状态认知
- `v1` 聚焦阅读 + 登录
- 用明确的 `Store / Repository / Coordinator / UiState / ViewModel` 边界控制复杂度

这条路线既能保证 side project 足够快地落地，也能避免未来补 iOS 或扩展互动能力时推翻重来。
