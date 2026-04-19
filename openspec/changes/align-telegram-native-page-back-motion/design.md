## Context

NativeDo 当前的 pushed native page 返回交互已经不再走旧的 `TelegramStyleScaffold` 路径，真正生效的是 `NativePageStackHost`。这条 host 路径已经承担了：

- native page stack ownership
- pushed top bar ownership
- Android predictive back
- 手写左边缘拖拽
- native page close / pop

但它现在把三种不同来源的返回混进了一套不一致的 motion model：

1. 顶栏返回 / 系统普通 back
   - 通过 `BackHandler` 触发 `closeCurrentEntry(useDragOffset = false)`
   - 当前页只做标准 `48dp / 150ms` 退出
   - 没有 Telegram 普通 close 那种更完整的 host reveal 语义

2. Android predictive back
   - 通过 `PredictiveBackHandler` 驱动 `dragOffsetPx = 56dp * progress`
   - 当前页已经有缩放、pivot、圆角和阴影

3. 手写左边缘拖拽
   - 通过 `pointerInput` 直接用 raw `dx` 推动 `dragOffsetPx`
   - 视觉却复用了 predictive back 的渲染分支

Telegram Android 的实际结构更清楚：

- 顶栏返回按钮最终进入 `ActionBarLayout.onBackPressed() -> closeLastFragment(true)`，走的是普通 fragment close 动画，不是 predictive shrink。
- Android 14 predictive back 会先 `prepareForMoving()` 挂上上一页，再由 `onBackProgress()` 驱动一个很小的横移，真正的缩小发生在 `drawChild()` 的 host 级 canvas transform 中。
- predictive back 的缩放锚点不是固定中心，而是由边缘方向和 `touchY` 决定，X 侧靠近边缘反侧约 `82dp`，Y 侧跟随手指。

用户希望“动画的细节和 Telegram 一样”，这意味着这次 design 不能只做“更顺一点”的 motion polish，而必须把两条语义边界写清楚：

- Standard close 必须像 Telegram standard close
- Interactive back 必须像 Telegram predictive back

## Goals / Non-Goals

**Goals:**

- 让 pushed native page 的顶栏返回、系统普通 back、Android predictive back、手写左边缘拖拽在 owner 和语义上都与 Telegram 对齐。
- 明确区分 standard close family 与 interactive predictive family，而不是继续把两者混成一套。
- 让 Android predictive back 与手写左边缘拖拽共用同一个 interactive back renderer，只保留输入来源差异。
- 保持 back motion 的 ownership 在 `NativePageStackHost`，不把动画逻辑下沉回 `TopicRoute` / `ProfileRoute`。
- 用 Telegram 的真实参数、时长、pivot 和 reveal 关系作为目标，而不是只保留“感觉像 Telegram”。

**Non-Goals:**

- 不重写 topic/profile 的业务内容布局。
- 不修改 root tab、browser、login 的导航边界。
- 不复制 Telegram 的 Java 类结构或 GPL 代码。
- 不把普通顶栏返回强行做成 predictive back 的缩小效果。
- 不保留现在这种“手写边缘拖拽像老 swipe、视觉又像 predictive”的混合状态。

## Telegram Motion Reference

### Telegram 视觉细节对照表

| Detail | Telegram implementation | NativeDo design constraint | Not acceptable |
|--------|-------------------------|----------------------------|----------------|
| Previous scene mounting | `prepareForMoving()` 先把上一页挂到 `containerViewBack`，当前页继续留在 `containerView` | 任何 interactive back 的第一个可见 progress 帧之前，host 必须已经持有前后景两页 | 当前页先开始缩放/位移，底页随后才补挂 |
| Standard close family | `closeLastFragment()` 进入 `startLayoutAnimation(false, ...)`；`containerViewBack.alpha = 1 - interpolated`，`containerViewBack.translationX = 48dp * interpolated`，`150ms`，`decelerate` | 顶栏返回 / 普通 back 必须明确属于 `48dp / 150ms / decelerate` 这一族，并由 host 承接上一页 | 按钮返回复用 predictive shrink，或仍然只有当前页做一个偏弱退出 |
| Predictive progress translation | `onBackProgress()` 只给 `dx = 56dp * progress`；`drawChild()` 在 progress 期把视觉位移约束在 `<= 56dp` | 跟手阶段必须是小位移模型，progress 满程也不能把页面拖离很远 | raw `dx` 直接驱动画面，拖到 1/3 屏宽才算完成 |
| Predictive scale curve | `scale = lerp(1f, lerp(0.90f, 0.85f, 1f - alpha), clamp01(translationX / 56dp))` | 跟手阶段满程目标约 `0.90f`；进入完成出场后才允许随着 alpha 下降继续逼近 `0.85f` | 一上手就缩到 `0.85f`，或全程只缩到 `0.96f` 这类弱缩放 |
| Pivot X | `predictiveBackLeft ? rect.right - 82dp : rect.left + 82dp` | 以起始边缘反侧的 `82dp` inset 作为 X 锚点，不使用中心锚点 | 固定中心、固定右下角、或永远写死左侧 |
| Pivot Y | `predictiveBackY` 在 `onBackStarted()` 捕获并直接用于 `drawChild()` | Y 锚点必须取交互起始 Y，并在整次 interactive back 中保持稳定 | 固定屏幕中心，或跟随当前 `dx` 人工漂移 |
| Current-page clipping | `drawChild()` 读取 `WindowInsets` 圆角并对当前页 `clipPath` | host 需要在当前页 surface 上做系统圆角或主题等价圆角裁切 | 给页面内容层随便加个卡片圆角，不裁 host surface |
| Reveal geometry | `containerViewBack` 的 `clipRight = translationX + 1dp`，`containerView` 的 `clipLeft = translationX` | 底页露出必须来自 host 裁切几何关系，而不是 crossfade | 前后景互相 fade，或底页自己平移进场 |
| Edge shadow | `layerShadowDrawable` 紧贴当前页边缘，alpha 随 `widthOffset / 20dp` 提升 | host 需要一条贴边阴影，强度随 reveal 增加 | 无边缘分离感，或做成大片悬浮投影 |
| Back scrim | `scrimPaint` 作用在 `containerViewBack`，暗化强度上限约为 `120 * 0.8` alpha | 底页需要轻量暗化，而且必须由 host 绘制 | 全屏重 dim，或完全没有底页层次 |
| Predictive cancel settle | `animateBackEndAnimation(true)` 在 predictive path 下最低 `320ms`，`EaseOutQuint` | interactive cancel 不能复用 standard close；应明显更慢、更柔和 | `150ms` 回弹、spring 回弹、松手即 snap back |
| Predictive complete settle | `animateBackEndAnimation(false)` 在 predictive path 下最低 `380ms`，`EaseOutQuint`，当前页继续滑到屏外后再 pop | 释放完成后必须“继续向右滑走”再出栈 | 释放瞬间直接 pop，或只补一个很短的 fade-out |

### Telegram parity acceptance checklist

- 点击左上角返回时，不出现 predictive shrink、pivotY 缩放或动态圆角；观感应是 Telegram 标准 close，而不是 Telegram predictive back。
- 从屏幕上部、中部、下部触发 interactive back 时，当前页的缩小锚点要随起始 Y 明显变化，不能始终像从中心缩。
- interactive back 跟手过程中，当前页应以“小位移 + 明显缩放 + 底页露出”为主，而不是“页面被拖出去很远”。
- interactive cancel 必须明显慢于 standard close；interactive complete 必须在松手后继续向右滑出，而不是立刻消失。
- shadow、scrim、clipping 属于首版 Telegram 对齐范围，不是可以延后的 polish 项。

## Decisions

### 1. Native page back motion split into two explicit families

`NativePageStackHost` 将显式区分两类返回动画：

- `StandardClose`
- `InteractiveBack`

`StandardClose` 负责：

- pushed top bar 返回按钮
- 系统普通 back
- 代码主动触发的非交互 pop

`InteractiveBack` 负责：

- Android predictive back
- 手写左边缘拖拽

原因：

- Telegram 的顶栏返回和 predictive back 本来就是两条不同语义。
- 当前 NativeDo 的主要问题之一正是这两条路径边界不清，导致“按钮弱、边缘高级但不纯”。

替代方案：

- 所有 back 都统一成一个缩小 + reveal 动画。
  - 放弃原因：这不符合 Telegram 的普通 close 语义，也会让顶栏返回显得过度戏剧化。

### 2. Standard close 必须精确对齐 Telegram 普通 close，而不是复用 interactive renderer

`StandardClose` 的目标语义：

- 当前页使用 Telegram 普通 close 的 `48dp` 退出族
- 时长和 easing 对齐 Telegram 普通 close
- 上一页由 host 恢复和承接，不依赖内容页自己做返回动画
- 不使用 predictive back 的缩小、touchY pivot、动态圆角

NativeDo 应把这条路径实现成一个完整的 host close，而不是继续停留在“只有当前页动一下”的弱版本。

原因：

- 用户主观上觉得按钮返回明显更弱，本质上是因为它缺少完整的 host close family，而不是因为它没做 predictive shrink。
- Telegram 顶栏返回本来就不是 predictive back 的缩小效果。

替代方案：

- 让按钮返回直接复用 interactive predictive renderer。
  - 放弃原因：这会破坏 Telegram 的普通 close 语义边界。

### 3. Interactive back 必须精确对齐 Telegram predictive back

`InteractiveBackRenderer` 的目标语义：

- 跟手阶段的视觉位移上限约为 `56dp`
- 跟手阶段满程 scale 目标约为 `0.90f`，完成出场时才允许继续逼近 `0.85f`
- 当前页缩小、裁切圆角、投影和 reveal 都由 host 统一渲染
- X pivot 依据返回边缘方向选择反侧 `82dp` inset 位置
- Y pivot 使用交互开始时捕获的 `touchY`，而不是固定中心点
- 取消时使用 Telegram predictive cancel 的 `320ms`
- 完成时使用 Telegram predictive complete 的 `380ms`

原因：

- 用户明确要的是 Telegram 那种“整体缩小然后露出底页”的交互，这对应的正是 Telegram predictive back，而不是 Telegram 老 swipe-back。
- 只有把视觉主导因素从“页面拖很远”切回“位移很小但 scale/pivot 正确”，才能真正贴近 Telegram。

替代方案：

- 继续保留现在的 raw `dx` 驱动，只微调 scale。
  - 放弃原因：交互底层仍然是老 swipe 语义，出来的效果不会真正像 Telegram predictive back。

### 4. 手写左边缘拖拽只作为输入源，不再决定独立的渲染语义

手写左边缘拖拽仍然需要保留，用于：

- 没有系统 predictive back 进度的设备或路径
- 让旧设备也拥有接近 Telegram predictive 的交互

但它不再直接把 raw `dx` 当成最终视觉位移。新的关系应是：

```text
manual dx -> normalized progress -> InteractiveBackRenderer
system predictive progress -> InteractiveBackRenderer
```

也就是：

- 手写拖拽与系统 predictive back 只在输入来源上不同
- 两者在视觉渲染语义上完全一致

原因：

- 当前 manual edge drag 和 system predictive back 的混搭正是主观割裂感的根源。
- 用户要求“细节和 Telegram 一样”，在 NativeDo 的 Compose 结构里，最合理的做法是让所有 interactive back 都对齐 Telegram predictive path。

### 5. Previous scene reveal、scrim、shadow 和 clipping 必须全部归 host 所有

返回动画期间，上一页的露出、当前页的裁切圆角、阴影边和背景 scrim 都应由 `NativePageStackHost` 统一承担。

这些细节属于首版 Telegram 对齐范围，而不是“先把位移做好，阴影和 scrim 以后再补”的二期 polish。

这意味着：

- `TopicRoute` / `ProfileRoute` 不负责自己的返回动画
- pushed scene 只发布 title/subtitle 等 host chrome 数据
- host 决定上一页是否静止、何时露出、如何加 shadow / scrim

原因：

- Telegram 的这部分也在 `ActionBarLayout.drawChild()` 和容器层完成。
- 只有 host 拥有前后景两页，才能稳定实现 reveal continuity。

替代方案：

- 让每个 pushed 页面自己加 back-motion modifier。
  - 放弃原因：会打碎 owner 边界，也不可能稳定复刻 Telegram 的前后景联动。

### 6. `TelegramStyleScaffold` 对 pushed native page path 只保留 legacy 地位

本 change 之后，`TelegramStyleScaffold` 不再被视为 pushed native page back motion 的参考实现。topic/profile 的返回体验以 `NativePageStackHost` 为唯一 source of truth。

原因：

- topic/profile 现在真实已经不走那条路。
- 继续把旧 helper 当作主参考会污染设计判断，也会让文档和实际实现继续漂移。

## Architecture Sketch

```text
AppRoot
└─ NativePageStackHost
   ├─ StandardCloseEngine
   │  ├─ top bar back
   │  └─ system back
   ├─ InteractiveBackInput
   │  ├─ Android predictive back
   │  └─ manual left-edge drag
   ├─ InteractiveBackRenderer
   │  ├─ max 56dp visual translation
   │  ├─ Telegram pivotX/pivotY semantics
   │  ├─ host-owned scale / clip / shadow
   │  └─ previous scene reveal
   └─ NativePageEntryContent
      ├─ TopicRoute
      └─ ProfileRoute
```

关键边界：

- `NativePageStackHost` 拥有返回动画
- `NativePageEntryContent` 只拥有内容和 host chrome 数据发布
- 旧 `TelegramStyleScaffold` 不再作为 native page back motion 的 owner

## Risks / Trade-offs

- [Compose `graphicsLayer` 不等于 Telegram `drawChild()` canvas 变换]  
  Mitigation: 以 Telegram 的视觉参数和 host ownership 为目标，必要时在 host 层拆分更多前后景绘制语义，而不是退回页面内 modifier。

- [把 manual edge drag 改成 progress-normalized 后，旧设备上的“拖得很远”手感会变化]  
  Mitigation: 明确这是刻意对齐 Telegram predictive 语义，不继续保留旧 swipe 的 raw-distance 模型。

- [Standard close 与 InteractiveBack 分家后，代码路径会变得更显式]  
  Mitigation: 这正是设计目标，显式边界比继续混合更容易维护和调试。

- [旧文档仍然声称当前实现“已经完全和 Telegram 一致”]  
  Mitigation: 在 implementation change 中同步清理或修正文档，避免继续误导后续开发。

## Migration Plan

1. 先把 Telegram 普通 close 和 predictive back 的关键参考点写入本 change，避免实现时再凭印象微调。
2. 先在实现前把本 design 的参数表和验收 checklist 固化成开发对照口径。
3. 在 `NativePageStackHost` 中拆出 `StandardClose` 与 `InteractiveBack` 两类明确状态和渲染入口。
4. 先对齐顶栏返回 / 普通 back 的 standard close family，再对齐 interactive predictive family。
5. 把 manual left-edge drag 改成 progress-normalized 输入源，接入同一套 interactive renderer。
6. 清理 pushed native page path 上对旧 `TelegramStyleScaffold` 语义的依赖和误导性文档。
7. 用 targeted tests、Telegram 录屏逐帧对比和本 design 的 checklist 一起做 smoke check。

## Open Questions

- 对于 Android 34 以下设备，manual edge drag 是否需要保留极小的触发容忍度差异，还是完全按 predictive back 的阈值语义统一？
- 验证阶段是否要把 Telegram 对比录屏或参数表沉淀为项目内长期维护的 motion checklist，避免后续再次漂移？
