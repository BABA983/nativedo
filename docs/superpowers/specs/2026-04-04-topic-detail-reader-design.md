# NativeDo Topic 详情页阅读体验设计

**日期：** 2026-04-04  
**状态：** 已确认，待进入 implementation plan  
**目标：** 在保持论坛阅读属性的前提下，重做 Android `Topic` 详情页的阅读体验：让每条回复边界更清楚，正文块与 inline entity 更接近 Telegram 的阅读秩序，并为后续的回复入口与阅读辅助工具留出稳定扩展位。

---

## 背景

当前 `Topic` 详情页已经具备基础可用性，但阅读体验仍然停留在“按数据顺序堆内容”的阶段：

- 页级结构仍然比较朴素，主要是标题、作者行、正文和 `divider`
- 每条回复之间的边界主要依赖间距和分割线，论坛阅读单元不够明确
- 正文内部已经开始原生化，但整体视觉语言还没有统一到页级阅读体验中
- 底部目前没有稳定的阅读辅助入口，后续的 `回到顶部 / 只看楼主 / 跳楼 / 热门回复 / 回复` 也没有合适的挂载点

对照目标体验后，可以明确两点：

1. 详情页不应该做成聊天气泡流，因为 `linux.do` 是论坛，不是真正的聊天软件  
2. 详情页又不能继续停留在首页那种纯列表语义，因为回复正文内部已经有更复杂的阅读结构

因此本次设计选择的方向不是“像 Telegram 聊天页”，而是“借 Telegram 的阅读秩序和块级统一感，做一版论坛阅读页”。

---

## 目标

- 保持 `Topic` 详情页的论坛阅读属性，不转成聊天气泡流
- 让每条回复成为清晰的阅读单元，但不过度做成重卡片信息流
- 统一 `quote / code / details / onebox / list` 的块级视觉语言
- 统一 `mention / link / inline code / emoji` 的 inline 风格
- 引入一个不常驻、按阅读状态出现的底部轻量工具浮层
- 为后续的 `回到顶部 / 只看楼主 / 跳楼 / 热门回复 / 回复` 预留稳定入口

## 非目标

- 不在本轮实现真正的发帖 / 回复编辑器
- 不把详情页改造成 Telegram 式聊天界面
- 不重写现有 `cooked HTML -> Block/Inline AST` 渲染链
- 不在本轮一次性补完所有 Discourse 插件块
- 不做大范围主题系统重构，只补当前任务需要的最小语义

---

## 方案比较

### 方案一：统一阅读壳 + 弱卡片回复流 + 条件浮层

优点：

- 最符合论坛阅读属性
- 每条回复边界清楚，但不会造成“卡片里再套卡片”的视觉噪音
- 最适合接住后续的浮层工具和筛选能力
- 与当前 `TopicRoute + HtmlPostBodyView + PostBodyBlockRenderers` 的边界最匹配

缺点：

- 需要同时调整页级结构和正文块样式，不是纯视觉换皮

### 方案二：Telegram 频道阅读面

优点：

- 连续阅读感最强
- 最接近 Telegram 内容阅读气质

缺点：

- 会削弱论坛里“每条回复是独立单元”的结构感
- 与用户要求的“边界更清楚”不完全一致

### 方案三：强卡片堆栈 + 工具导向详情页

优点：

- 回复单元边界最明显
- 后续扩展工具和筛选功能很方便

缺点：

- 页面会更像产品化论坛客户端，而不是稳定阅读页
- 容易抢走正文注意力

### 结论

采用**方案一：统一阅读壳 + 弱卡片回复流 + 条件浮层**。

---

## 核心设计

### 1. 页级结构

`Topic` 详情页拆成三个明确层次：

1. `TopicHeader`
2. `PostStream`
3. `BottomUtilityShelf`

#### TopicHeader

- 展示主题标题、帖子数量和必要的主题级信息
- 它属于整页信息，不属于某一条回复
- 视觉上是一个轻量阅读头，不使用重卡片
- 目标是帮助用户建立阅读上下文，但不抢正文注意力

#### PostStream

- 这是整页核心区域
- 每条回复是一个独立阅读单元
- 回复单元采用**弱卡片列表**而不是纯 divider 列表，也不是重阴影卡片流
- 主要靠以下手段建立边界：
  - 轻背景
  - 细描边
  - 中等圆角
  - 稳定上下间距
  - 很轻的阴影或几乎无阴影

#### BottomUtilityShelf

- 默认不常驻
- 用户离开底部、开始向上回滚时出现
- 回到底部或回到最新阅读位置后消失
- 它不是输入框，而是阅读辅助浮层

---

### 2. 回复单元设计

#### 总原则

- 保持论坛属性
- 边界清楚
- 不走聊天气泡
- 不做重型信息流卡片

#### 楼主贴

- 边界略强于普通回复
- 作为整页阅读起点，容器完整度更高
- 顶部作者信息更稳，主贴身份更明确
- 但不与普通回复形成完全不同的组件体系

#### 普通回复

- 仍然是独立弱卡片
- 视觉更克制
- 和楼主贴的差异只放在：
  - 容器强度
  - 顶部信息密度
  - 与前后回复的间距

#### 不采用的做法

- 不继续维持纯 `divider` 列表
- 不把每条回复做成 Telegram 发送/接收气泡
- 不给每条回复加厚重阴影和强浮层感

---

### 3. 正文块样式

正文继续使用当前的块级 AST 路线，不回退到整体 HTML 容器思路。

#### Quote / DiscourseQuote

- 保留左侧强调条语义
- 让引用块更像 Telegram 的引用内容区，而不是独立信息卡片
- 作者、标题和正文之间的密度更统一
- 不再额外叠加过重背景层级

#### CodeBlock

- 维持独立面板
- 更强调稳定的 monospaced slab 观感
- 语言标签更克制
- 内边距和块间距统一到整页阅读体系
- 不做 IDE 风格重装饰

#### Details

- 视觉上更像折叠内容条
- 标题行紧凑，展开后正文自然延续
- 不是通用 FAQ accordion

#### Onebox

- 收敛成紧凑预览卡
- 保留来源、标题、摘要、缩略图
- 控制高度和视觉重量，避免压过正文

#### List

- 列表继续保留列表语义
- 缩进、marker、段间距收敛到统一阅读节奏

---

### 4. Inline Entity 风格

inline 样式靠近 Telegram 的阅读秩序，而不是网页链接风格。

#### mention

- 不加下划线
- 比普通正文略高一档强调
- 在整段中一眼能识别，但不跳色过猛

#### 普通链接

- 保留点击能力
- 颜色和正文形成清晰区分，但不做“超链接蓝字网页感”

#### inline code

- 做成轻量胶囊底色
- 不加厚重描边
- 保持段落阅读连续性

#### emoji / site-icon

- 保持基线对齐
- 不破坏正文行高和段落节奏

#### heading / 大字号文本

- 层级清楚，但不能变成营销感或海报感
- 服务于阅读结构，而不是抢视觉注意力

---

### 5. Bottom Utility Shelf

#### 定位

- 它是阅读辅助浮层，不是输入栏
- 它是跨整页状态的工具层，不属于单条回复

#### 出现条件

- 用户离开底部、开始回滚时出现
- 用户回到底部后隐藏
- 键盘弹出、图片查看等沉浸状态下隐藏
- 需要显隐阈值，避免轻微滚动触发抖动

#### 第一版能力

主浮层只保留两个入口：

1. `回复`
2. `更多`

其中：

- `回复` 只作为入口，不直接常驻展开输入框
- `更多` 承载后续扩展能力

#### 后续挂载能力

`更多` 面板为以下能力保留入口：

- `回到顶部`
- `回到底部`
- `只看楼主`
- `跳转楼层`
- `热门回复`

这些能力都属于页级阅读状态切换，不应分散到每条回复里。

---

## 与当前代码的映射

### 继续保留

- [TopicRoute.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt)
  - 仍然作为 `Topic` 页入口
  - 负责页级滚动、状态和浮层显隐

- [HtmlPostBodyView.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt)
  - 继续作为正文入口
  - 不承担整条回复布局职责

- [PostBodyBlockRenderers.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockRenderers.kt)
  - 继续作为块级统一渲染落点

- [PostInlineText.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostInlineText.kt)
  - 继续作为 inline 风格统一落点

### 建议新增

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicPostCard.kt`
  - 封装单条回复阅读壳
  - 区分楼主贴与普通回复的容器强度

- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/BottomUtilityShelf.kt`
  - 封装页级条件浮层
  - 持有显隐状态与第一版入口布局

### 谨慎修改

- `theme` 只补本任务确实需要的最小语义，不做大改
- parser 层只在确认高频缺口时最小补充，不做大爆炸式重写

---

## 实施顺序

### Phase 1：Topic 页面骨架与回复单元

- 重构 `TopicRoute` 的页面结构
- 去掉当前强依赖 divider 的分隔方式
- 引入 `TopicHeader + PostStream + TopicPostCard`
- 先立住“弱卡片列表”的整体阅读秩序

### Phase 2：BottomUtilityShelf

- 基于 `LazyListState` 建立显隐条件
- 先只实现“离开底部出现”
- 第一版只放 `回复 + 更多`

### Phase 3：正文块与 inline 统一风格

- 收敛 `quote / code / details / onebox / list`
- 收敛 `mention / link / inline code / emoji`
- 保持现有 AST 结构不动摇

### Phase 4：扩展入口预留

- 在 `更多` 中挂出后续能力的结构位
- 为 `只看楼主 / 跳楼 / 热门回复` 留出稳定交互入口
- 这一阶段不要求一次做完全部行为

---

## 验证方式

### 视觉与行为验证

- `Topic` 页面在多回复场景下边界清晰
- 回复单元不会与正文内部结构抢层级
- 浮层只在离开底部后出现，不抖动、不遮挡主阅读路径

### 自动化验证

- 保持现有 parser 测试继续通过：
  - [HtmlPostBodyViewTest.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/test/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyViewTest.kt)
- 为浮层显隐条件新增纯逻辑测试
- 如涉及辅助筛选逻辑，为 helper 增加定向单测

### Android 验证命令

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
```

---

## 结论

本次改造不是把 `Topic` 详情页做成聊天软件页面，而是把它升级成一版更成熟的论坛阅读页：

- 页级连续阅读感更强
- 每条回复边界更清楚
- 正文块和 inline entity 更统一
- 底部工具入口不常驻，但具备稳定扩展能力

这条路线既符合当前 NativeDo 的 Android 架构，也能为后续阅读辅助功能提供清晰的演进路径。
