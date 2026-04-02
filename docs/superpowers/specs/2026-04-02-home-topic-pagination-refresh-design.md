# NativeDo 首页与 Topic 分页刷新设计

**日期：** 2026-04-02  
**状态：** 已确认，待进入实现计划  
**目标：** 为 Android 首页 feed 和 topic 详情页补齐下拉刷新与滚动加载能力，同时保持当前单模块、`Repository + ViewModel + StateFlow` 的简单结构。

---

## 背景

当前首页与 topic 详情都只能读取首屏数据：

- 首页仓库层把 feed 请求固定在 `page = 0`，切 tab 和刷新本质上都是重新拿第一页
- 首页 UI 已经是 `LazyColumn`，但没有接近底部后的增量加载触发
- topic 详情页只请求一次 `GET /t/{id}.json`，并把首批 `post_stream.posts` 直接映射到 UI
- topic 模型没有保留继续补拉楼层需要的 `stream / post ids / 是否还有更多` 元数据

这导致两个直接问题：

- 首页只能看第一页主题，无法继续向下阅读
- topic 在长帖中只能看到首批楼层，也没有统一的手动刷新入口

---

## 目标

- 首页 `Latest / New / Top` 支持滚动加载更多主题
- 首页支持下拉刷新，语义是重新同步当前 tab 的第一页
- topic 详情支持下拉刷新，语义是重新同步该主题当前最新状态
- topic 详情支持滚动加载后续楼层，不用一次拉完整个长帖
- 保持现有架构边界清晰，避免引入 Paging 3、DI 框架或额外模块拆分

## 非目标

- 不引入离线缓存、已读进度持久化或预加载策略
- 不把分页能力扩展到搜索、profile 等其他页面
- 不把 topic 默认改成 `print=true` 全量下载模式
- 不重做导航、主题或列表样式，只在必要位置增加加载与刷新状态展示

---

## 方案比较

### 方案一：首页分页，topic 使用全量拉取

做法：

- 首页按 `page` 追加
- topic 刷新时重新请求 `/t/{id}.json?print=true` 或等价全量接口

优点：

- 实现最直接
- topic 不需要维护增量状态

缺点：

- 长帖请求体会明显变重
- topic 的常规阅读成本会被放大，不适合作为默认行为

### 方案二：首页分页，topic 按 stream 增量补拉

做法：

- 首页继续走 `latest/new/top?page={page}`
- topic 首次请求 `GET /t/{id}.json`
- 后续根据返回的 `post_stream.stream` 分批请求 `/t/{id}/posts.json?post_ids[]=...`

优点：

- 符合 Discourse topic 的常规加载方式
- 长帖场景下网络与内存更可控
- 可以自然接入底部加载更多

缺点：

- 需要扩展 topic contract、DTO 和 UI state

### 方案三：直接引入 Paging 3

优点：

- 首页分页框架化程度更高

缺点：

- 当前项目规模下偏重
- topic 详情并不是标准列表翻页源，反而会让实现更拧巴

### 结论

采用**方案二：首页分页，topic 按 stream 增量补拉**。

这是满足当前需求的最短路径：首页直接扩展现有 `page` 能力；topic 在必要处补齐分页元数据与增量接口，不额外引入重型基础设施。

---

## 设计决策

### 1. 首页分页模型

首页数据层从“只返回第一页列表”改成“返回一页结果 + 下一页信息”。

建议新增轻量结果模型，例如：

- `items: List<TopicSummary>`
- `nextPage: Int?`
- `hasMore: Boolean`

实现上优先解析 Discourse 返回的 `topic_list.more_topics_url`；若其为空则视为没有下一页。为此需要同步扩展：

- `contracts/discourse/endpoints.md`
- `fixtures/discourse/latest-topics.json`
- `TopicListDto`

这样首页分页依据来自服务端，不靠“空列表就是没了”这类弱约定。

### 2. 首页 ViewModel 状态机

`HomeUiState` 需要补齐：

- `isLoadingMore`
- `nextPage`
- `hasMore`

`HomeViewModel` 保留当前 `refresh(tab, initial)` 结构，但拆出：

- `refreshCurrentTab()`
- `loadNextPage()`

约束：

- 正在 `isLoading`、`isRefreshing` 或 `isLoadingMore` 时不再并发发起新请求
- 切换 tab 时重置当前分页状态
- 刷新成功后用第一页替换列表，并重置 `nextPage`
- 追加成功后按 `topic.id` 去重拼接，避免接口重叠导致重复项
- 追加失败时保留已有列表，只暴露底部错误状态或 toast 级提示，不打断阅读

### 3. 首页 UI 触发方式

首页继续使用现有 `LazyColumn`。

新增两类触发：

- 下拉刷新：使用 Compose Material 3 的 `pull-to-refresh` API，直接绑定 `uiState.isRefreshing`
- 滚动加载：基于当前 `LazyListState`，当用户滚动到“距离底部 3 到 5 项”时触发 `loadNextPage()`

底部表现只需要最小状态：

- 加载中时显示一个尾部 `CircularProgressIndicator`
- 追加失败时显示一个轻量重试 item

不额外引入悬浮按钮、分页分隔条或复杂空态。

### 4. Topic 分页模型

当前 `TopicDetail` 只有 `id/title/posts`，不足以支持后续楼层加载。需要扩展为包含：

- 已加载的 `posts`
- `streamPostIds` 或等价完整 post id 序列
- `loadedPostIds`
- `hasMore`

不要求把这些字段全部直接暴露给 UI，但至少要在 repository / ViewModel 可用。

网络层同步扩展：

- `TopicResponseDto.postStream` 增加 `stream`
- 新增 `GET /t/{id}/posts.json`
- 新增“按一组 `post_ids` 获取帖子”的 DTO 与映射

contract 与 fixture 也要同步更新，保证 topic 增量加载不是只存在于 Android 实现里的隐含行为。

### 5. Topic ViewModel 状态机

`TopicUiState` 建议补齐：

- `isRefreshing`
- `isLoadingMore`
- `hasMore`

`TopicViewModel` 保留 `refresh()`，再增加：

- `loadMore()`

语义：

- 首次进入页时加载 topic 首批内容
- 下拉刷新时重新请求 topic 首屏，并重置已加载楼层
- `loadMore()` 每次从未加载的 `streamPostIds` 中取固定批次，例如 20 条
- 增量加载成功后去重拼接到现有 `posts`
- 增量加载失败时保留当前内容，仅清除 `isLoadingMore`

不在本轮加入楼中楼定位、滚动位置恢复或“跳到最新回复”。

### 6. Topic UI 触发方式

topic 页面仍然使用单个 `LazyColumn`。

新增：

- 顶部下拉刷新
- 底部加载更多触发
- 列表尾部 loading / retry item

顶部“`N posts`”文案改为优先展示服务端总数；如果暂时拿不到总数，再退回已加载数量。这样能减少用户误以为“当前已全部加载”的误导。

---

## 实施边界

本轮改动限定在以下范围：

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/domain/*`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/*`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/*`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/*`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/*`
- `contracts/discourse/*`
- `fixtures/discourse/*`

不触碰根配置、依赖升级、模块拆分和其他 feature 的列表页。

---

## 验证方式

- 单测：
  - 首页 ViewModel 刷新与追加状态流转
  - topic ViewModel 首次加载、刷新重置、增量补拉
  - 网络层 DTO 映射新增字段与增量接口解析
- Android 验证：
  - `./apps/android/gradlew -p apps/android :app:testDebugUnitTest`
  - `./apps/android/gradlew -p apps/android :app:assembleDebug`

---

## 结论

本次改动应当把首页和 topic 都补到“可连续阅读”的基本可用状态，但实现策略不应一刀切：首页用标准页码分页，topic 用 Discourse 原生的 stream 增量补拉。这样既能解决首屏限制，也不会把当前项目拖进过度设计。
