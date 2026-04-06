# NativeDo Topic 回复头像 Flair 与 Profile 入口设计

**日期：** 2026-04-06  
**状态：** 已确认方向，待进入 implementation plan  
**目标：** 让 Android `Topic` 详情页中的回复头部更贴近 Web：`flair` 从第二行文字链移到头像右下角显示，并补上回复头部进入用户 profile 的直接入口。

---

## 背景

当前 `TopicPostCard` 已经补上了：

- 回复头第一行的 `OP / reply / edit / time`
- 第二行的作者身份链 `@username · title`
- `flair` 数据映射与渲染

但现状还有两个明显问题：

1. `flair` 现在跟在第二行文字链后面，语义上更像“头像状态”，位置不对
2. 回复头部本身还不能直接跳用户 profile，只有正文里的 `@username` mention 才能跳转

Web 里 `flair` 更接近“头像角标”，而不是作者文本元数据的一部分。  
因此这轮应当把 `flair` 放回头像区域，同时把回复头部补成一个可直接进 profile 的入口。

---

## 目标

- 将回复中的 `flair` 改为头像右下角角标
- 保持第二行更纯净，只展示 `@username · title`
- 在回复头部补上进入用户 profile 的点击入口
- 复用现有 `onUserClick(username)` 导航链路，不新增导航协议
- 改动范围尽量局部，避免把 `UserAvatar` 演进成过度抽象的全局组件

## 非目标

- 不接入 `status` 数据源
- 不把 flair / badge 体系抽成完整通用组件
- 不改首页、搜索页或 profile 页里的头像系统
- 不调整现有 topic reply 的正文渲染逻辑

---

## 方案比较

### 方案一：在 `TopicPostCard` 本地实现头像角标与 profile 点击

优点：

- 改动最小，只影响 `Topic` 回复头部
- 与当前需求完全对齐，不会把简单需求扩成头像系统重构
- `flair` 可以按 Web 语义直接叠在头像右下角

缺点：

- 如果后续其他页面也想复用同样角标，需要再抽一层

### 方案二：扩展 `UserAvatar` 为支持 overlay / click 的通用组件

优点：

- 后续其他页面可直接复用
- 头像扩展能力更完整

缺点：

- 当前需求只在 `TopicPostCard`，现在做成通用组件偏重
- 容易把本轮从局部 UI 调整升级成公共组件设计

### 方案三：只补 profile 点击，不改 flair 位置

优点：

- 改动最少

缺点：

- 不能解决 `flair` 位置不符合 Web 语义的问题

### 结论

采用**方案一：在 `TopicPostCard` 本地实现头像角标与 profile 点击**。

---

## 核心设计

### 1. Flair 位置调整

- `flair` 不再显示在第二行 `@username · title` 链中
- `flair` 改为叠加在头像右下角
- 角标尺寸应明显小于头像主体，视觉上是“附属状态”而不是第二头像
- 若 `flairUrl` 为空，则头像保持现状，不留占位

### 2. Profile 点击区域

- 回复头部的 `头像 + 名字块` 作为统一点击区
- 点击后直接调用现有 `onUserClick(post.authorUsername)`
- 右侧 `OP / reply / edit / time` 不加入点击区，避免误触

### 3. 第二行身份信息收敛

- 第二行只保留：
  - `@username`
  - `title`
- `flair` 移除后，这一行重新回到“弱身份文字链”角色
- 特殊 `title` 的动画样式继续保留，不在本轮改动

---

## 交互与视觉细节

### 头像角标

- 角标贴头像外圆右下侧
- 允许轻微超出头像圆边，但整体仍应保持整洁
- 若 `flair` 是透明底图标，应直接显示，不额外加重背景
- 若 `flair` 过小不可读，可增加一个非常轻的底托，但第一版优先保持简单

### Profile 点击反馈

- 点击区保持轻量 clickable 反馈即可
- 不单独给第二行文字做独立点击目标
- 不给右侧 meta 区域增加 profile 点击，避免和 reply pill 点击冲突

---

## 数据与架构影响

当前数据已足够支撑：

- `Post.authorUsername`
- `Post.authorAvatarTemplate`
- `Post.authorFlairName`
- `Post.authorFlairUrl`

本轮不需要新增字段，也不需要改 `TopicRoute` 的导航协议。  
只需要把 `TopicPostCard` 已有的 `onUserClick` 真正接入回复头部。

---

## 代码范围

预计只改：

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicPostCard.kt`

可能少量涉及：

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/UserAvatar.kt`
  - 仅当局部实现无法干净完成时，才补最小扩展

测试范围：

- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoutePaginationTest.kt`
  - 若新增纯函数 helper
- 以及现有 Android 模块验证

---

## 验证方式

- `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

人工检查重点：

- 有 `flair` 的回复：角标是否稳定贴在头像右下角
- 无 `flair` 的回复：头像是否保持原样
- 点击头像或名字块：是否进入对应 profile
- 点击 reply pill：是否仍只执行跳楼，不误触 profile
- 第二行在移除 `flair` 后是否明显更干净
