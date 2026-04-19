## Why

NativeDo 已经把 topic/profile 这类 pushed native page 收口到 `NativePageStackHost`，但返回动画还处于 split-brain 状态：

- 顶栏返回和系统普通 back 走的是一条很轻的 standard close，只做 `48dp / 150ms` 的退出，没有形成 Telegram 那种完整的 host close 观感。
- Android predictive back 已经有单独的进度驱动、缩放、pivot、圆角和阴影语义。
- 手写左边缘拖拽仍然使用 raw `dx` 直接驱动页面，但视觉又复用了 predictive back 的缩放分支。

结果是：

- 按钮返回和边缘返回看起来不像同一套 motion family；
- 边缘返回虽然更高级，但也没有真正对齐 Telegram；
- 当前仓库里关于 `TelegramStyleScaffold` 的一些旧文档已经不能准确描述 topic/profile 现在真实生效的返回链路。

这次 change 的目标不是“做一个差不多的 Telegram 风格返回”，而是把 pushed native page 的 back motion 语义精确收口到 Telegram Android 的两条真实路径：

- 顶栏返回 / 普通 back 对齐 Telegram `closeLastFragment()` 的 standard close
- 交互式边缘返回对齐 Telegram predictive back 的缩放、pivot、reveal 和 settle 细节

## What Changes

- 对齐 `NativePageStackHost` 的非交互关闭语义，让 pushed native page 的顶栏返回和系统普通 back 精确匹配 Telegram `ActionBarLayout` 的 standard close family，而不是继续停留在一个偏弱的局部退出。
- 对齐 `NativePageStackHost` 的交互式返回语义，让 Android predictive back 和手写左边缘拖拽都遵守 Telegram predictive back 的视觉细节：约 `56dp` 的进度位移、基于边缘和 `touchY` 的缩放锚点、host-owned reveal、圆角、阴影，以及 `320ms / 380ms` 的取消与完成动画。
- 统一交互式返回的 renderer，让手写左边缘拖拽不再使用 raw `dx` 的独立渲染模型，而是归一化成和系统 predictive back 一致的 progress-driven 语义。
- 明确返回动画 ownership 继续留在 `NativePageStackHost`，`TopicRoute`、`ProfileRoute` 等 pushed 页面只负责内容和 host chrome 数据发布，不再承担自己的 back motion。
- 把旧的 `TelegramStyleScaffold` 视为非 native-page-stack 路径的 legacy helper，不再把 pushed native page 的返回体验建立在那条旧路径的语义上。

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `native-page-stack`: 收紧 pushed native page 的 standard close 与 interactive back 语义，使其精确对齐 Telegram Android。

## Impact

- Affected code:
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativePageStackHost.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/TelegramAnimationSpec.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt`
  - `apps/android/app/src/main/java/io/github/baba983/nativedo/core/navigation/TelegramStyleScaffold.kt`
  - `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/NativePageStackStateTest.kt`
- Affected systems:
  - pushed native page close motion
  - predictive back and manual edge-swipe behavior
  - host-owned reveal/shadow/clipping semantics
  - root-scene continuity behind native pages
- External reference systems investigated for behavior modeling only:
  - `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java`
  - `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java`
  - `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`
- No backend or API contract changes are expected.
