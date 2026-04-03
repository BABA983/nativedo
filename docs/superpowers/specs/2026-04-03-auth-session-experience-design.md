# NativeDo 登录态与会话体验重构设计

**日期：** 2026-04-03  
**分支：** main  
**目标：** 以 `fluxdo` 为主要参考，吸收 `Telegram` 的根状态边界设计，重构 NativeDo 的登录态恢复与会话管理，解决“明明已有登录态但界面仍提示登录”的体验问题。

---

## 背景与问题

当前项目已经具备 `CookieStore`、`SessionCoordinator`、`LoginWebViewRoute` 等基础能力，但登录态体验仍然不稳定，核心表现是：

- 冷启动或页面切换后，明明 cookie 仍有效，`Profile` 仍可能先显示“Login required”
- 登录成功后，部分页面依赖 `sessionRestoreTrigger` 之类的补丁式刷新，状态收口不统一
- 页面会主动触发 session restore，导致恢复职责分散在 `NativeDoApp`、`ProfileRoute`、`LoginWebViewRoute` 等多个入口
- 会话失效、Cloudflare challenge、重新登录等流程还没有完全成为根层统一处理的 app flow

问题本质不是“单个页面写错了文案”，而是当前登录态仍然偏页面驱动，缺少 app 级唯一状态源。

---

## 设计目标

### 目标

- 将登录态恢复提升为 app 级基础设施，而不是页面级补丁逻辑
- 冷启动时避免把“尚未恢复”误判为“未登录”
- 登录成功、登出、会话过期、重新认证都走统一收口
- 优先对齐 `fluxdo` 的 WebView + cookie + snapshot 方案
- 仅吸收 `Telegram` 的根状态边界思路，不引入与当前体量不匹配的重型账户体系
- 保持 Android / Kotlin 代码边界清晰，可读、可维护，不过度封装

### 非目标

- 不引入 Telegram 式多账号体系
- 不引入 Hilt / Koin 或复杂 DI 基础设施
- 不在本次设计中扩展发帖、回复等写路径能力
- 不把 Cloudflare 自动续期做成复杂后台系统

---

## 方案比较

### 方案一：全屏 Auth Gate

冷启动先阻塞主界面，恢复完成后再进入 app。

优点：

- 状态最稳
- 最容易避免“假未登录”

缺点：

- 启动等待感明显
- 与当前产品形态相比偏重

### 方案二：无 Gate，完全页面内处理

App 先渲染，页面各自根据需要显示 loading / login / retry。

优点：

- 启动表面看起来更快

缺点：

- 最容易继续演化成当前的分散恢复模型
- 登录态一致性差

### 方案三：短 Gate + 快照优先

冷启动先读取本地 cookie 和用户快照；若存在可用快照，则短暂显示恢复态并直接渲染已登录基础壳；后台完成网络校验后再升级为已验证状态。

优点：

- 兼顾体验与一致性
- 最适合当前 `linux.do + WebView + cookie` 形态
- 与 `fluxdo` 的成功路径最接近

缺点：

- 需要新增快照存储和根层状态机

### 结论

选择**方案三：短 Gate + 快照优先**。

这是当前项目最合适的平衡点：

- 思路上对齐 `fluxdo`
- 边界上吸收 `Telegram`
- 复杂度仍然控制在当前 Android 单端项目可维护范围内

---

## 设计原则

### 1. 登录态属于 app 根状态

`SessionCoordinator` 升级为唯一的 auth state producer。页面只订阅状态，不自己决定是否恢复。

### 2. 凭证与显示快照分离

- cookie 是认证真源
- snapshot 只服务冷启动显示与快速恢复，不作为鉴权依据

### 3. 未确认不显示未登录

冷启动初期不允许把“尚未恢复”展示成“请登录”。

### 4. 登录与失效走统一收口

登录成功、登出、被动失效、Cloudflare 回退都由根层协调，业务页只上抛事件。

### 5. 页面不再持有恢复补丁逻辑

`sessionRestoreTrigger`、页面内主动 `restoreSession()` 之类机制应逐步移除。

---

## 状态模型

建议将当前 `AppSessionState` 调整为更明确的 auth 状态机：

```text
Unknown
-> Restoring(snapshot?)
-> Authenticated(snapshot, freshness = Cached | Verified)
-> Anonymous
-> Expired(reason)
```

建议模型：

- `Unknown`
  - app 刚启动，尚未读取本地凭证
- `Restoring(snapshot: SessionSnapshot?)`
  - 已发现可能存在登录态，正在做网络确认
- `Authenticated(snapshot: SessionSnapshot, freshness: SessionFreshness)`
  - 已登录，`snapshot` 可直接驱动 UI
- `Anonymous`
  - 明确无有效登录态
- `Expired(reason: SessionExpiredReason)`
  - 明确会话失效，需要用户重新登录

辅助枚举：

- `SessionFreshness`
  - `Cached`
  - `Verified`
- `SessionExpiredReason`
  - `ServerLoggedOut`
  - `CookieMissing`
  - `ChallengeRequired`
  - `RecoveryFailed`

---

## 存储设计

### CookieStore

保留为认证真源，继续负责：

- `_t`
- `_forum_session`
- `cf_clearance`

它只回答“凭证是否存在、是否可用于请求”，不直接承担 UI 恢复职责。

### SessionSnapshotStore

将当前仅存 `username` 的 `SessionStore` 升级为 `SessionSnapshotStore`，最小字段建议如下：

- `username`
- `displayName`
- `avatarTemplate`
- `lastValidatedAt`

`SessionSnapshot` 只用于：

- 冷启动短 gate 展示
- 顶部 / 底部用户信息快速渲染
- `Profile(Self)` 的 skeleton 过渡

它不作为最终认证依据。

---

## 冷启动恢复流程

推荐流程：

```text
App start
-> Unknown
-> read CookieStore + SessionSnapshotStore
-> no session cookie -> Anonymous
-> has session cookie + optional snapshot -> Restoring(snapshot)
-> try /session/current
-> fallback bootstrap html if needed
-> fetch profile only when snapshot missing or stale
-> success -> Authenticated(snapshot, Verified)
-> transient network / challenge issue -> keep Restoring or Authenticated(Cached)
-> definite failure -> Expired
```

### 关键约束

- 冷启动恢复只允许从根入口触发一次
- `ProfileRoute`、`TopicRoute` 不再主动调用 `restoreSession()`
- 冷启动恢复主路径不强制先 `syncFromWebView()`
- 冷启动恢复主路径不强制先刷新 CSRF

### 说明

`syncFromWebView()` 只应在明确边界时机触发：

- 登录成功
- Cloudflare challenge 完成

CSRF 也不应成为冷启动恢复必经步骤。对于当前以读为主的 app，CSRF 应按需获取，或在未来写操作前刷新。

---

## 登录、登出与失效收口

### 登录成功

推荐收口顺序：

1. WebView 检测到登录完成
2. 触发一次 `CookieSyncBridge.syncFromWebView()`
3. 通过轻量接口确认当前用户
4. 写入 `SessionSnapshotStore`
5. `SessionCoordinator` 发出 `Authenticated`
6. 根层根据 auth event 触发必要刷新

### 登出

推荐顺序：

1. 清除 cookie
2. 清除 session snapshot
3. 清除内存 auth state
4. 发出 `LogoutCompleted`
5. 根层刷新依赖登录态的页面和 chrome

### 被动失效

当网络层明确识别：

- 服务端已登出
- cookie 已丢失
- challenge 需要重新认证

应统一进入：

1. 清理本地凭证与 snapshot
2. 状态切为 `Expired`
3. 根层决定是否拉起登录流程

业务页不直接处理登录页跳转，只上抛“需要重新认证”事件。

---

## 页面与导航收口

### 根层职责

`NativeDoApp` / 根导航层负责：

- 冷启动恢复
- 短 gate 展示
- 登录成功后的全局刷新
- 被动失效后的统一跳转

### 页面职责

页面只做两件事：

- 订阅 `AppAuthState`
- 上抛 `requiresReauth` 一类事件

页面不再负责：

- 主动恢复 session
- 维护 `sessionRestoreTrigger`
- 登录成功后手动补刷一批页面

### Profile 页面行为

- `Unknown / Restoring`：显示 profile skeleton 或“正在确认账号状态”
- `Authenticated`：正常显示个人资料
- `Anonymous / Expired`：显示登录按钮或重新登录提示

### Topic 页面行为

- 正常浏览不依赖单独 auth gate
- 若请求明确返回重新认证需要，则上抛给根层
- 登录成功后的刷新由全局 auth event 驱动，而不是路由参数补刷

---

## 全局刷新机制

参考 `fluxdo` 的 auth-state broadcast 思路，建议增加轻量事件流：

- `LoginCompleted`
- `LogoutCompleted`
- `SessionExpired`
- `SessionVerified`

根层监听这些事件，统一触发有限范围刷新：

- `Profile(Self)`
- 当前打开且依赖登录态的页面
- 顶部标题 / 头像
- 底部 tab 个人头像

不引入泛化的全局 manager；只保留当前任务确实需要的刷新范围。

---

## 网络层调整

当前网络层已能识别 Cloudflare challenge，但仍缺少更完整的会话失效语义。

建议新增统一 session invalidation 入口，用于识别：

- 明确的服务端登出信号
- cookie 缺失
- challenge 需要 WebView 恢复
- 后续写请求中的 CSRF 失效

此外建议增加“会话代”或等价机制：

- 登录 / 登出 / 失效时推进 generation
- 旧请求响应不再允许覆盖新状态

这部分概念参考 `fluxdo`，但只实现当前需要的最小能力。

---

## UI 过渡态设计

冷启动过渡态采用“短 gate + 现有背景保留”的形式：

- 保留当前主题背景
- 中间显示轻量 glass card
- 主文案：`正在恢复登录态`
- 副文案：`正在同步 Linux.do 会话`

若存在本地 snapshot：

- 可直接渲染头像与用户名占位
- 顶部 / 底部 chrome 不再闪回匿名态

原则：

- 未确认前不出现 `Login required`
- 已明确失效后再出现重新登录文案

---

## 验证策略

### 单元测试

- `SessionCoordinator` 状态迁移测试
- `SessionSnapshotStore` 读写测试
- 冷启动恢复成功 / 失败 / challenge / cookie 缺失场景测试
- 登录成功、登出、过期事件测试

### UI / 路由测试

- `Profile(Self)` 在 `Restoring` 时不显示登录按钮
- `Authenticated(Cached)` 到 `Authenticated(Verified)` 不发生错误文案闪烁
- 登录完成后个人信息与头像正确刷新

### 回归验证

- 冷启动已有 cookie 时不再误显示未登录
- 登录完成后返回上一页，Profile/Topic 不再依赖 `sessionRestoreTrigger`
- 会话失效后进入统一重新登录路径

---

## 迁移顺序

建议按以下顺序改造：

1. 引入新的 auth 状态机与 `SessionSnapshotStore`
2. 将冷启动恢复收口到根入口
3. 移除页面内主动 `restoreSession()` 逻辑
4. 移除 `sessionRestoreTrigger` 路由补丁
5. 登录成功改为统一写 snapshot 并发 auth event
6. 增加被动失效与会话代保护
7. 补充测试与 UI 验证

---

## 预期结果

完成改造后，登录态体验应达到以下效果：

- 冷启动不再出现“有 cookie 但先显示未登录”的错误反馈
- `Profile` 不再承担 session restore 入口职责
- 登录成功、登出、会话失效行为都可从根层统一追踪
- 代码结构更接近 `fluxdo` 的稳定闭环，同时保持符合当前项目体量的简单实现

