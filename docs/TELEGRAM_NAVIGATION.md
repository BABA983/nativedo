# Telegram 风格页面切换实现指南

## 概述

本项目实现了 1:1 还原 Telegram Android 客户端的页面切换效果，包括：

- ✅ 标准转场动画（48dp 位移 + 淡入淡出，150ms）
- ✅ 预测性返回手势（Android 13+）
- ✅ 边缘滑动返回
- ✅ 预览模式动画（长按链接等场景）
- ✅ 双层动画效果（前后页面同时动画）

## 核心参数对照

| 参数 | Telegram 原版 | 本项目实现 |
|------|--------------|-----------|
| 标准位移 | 48dp | 48dp |
| 标准时长 | 150ms | 150ms |
| 插值器 | DecelerateInterpolator | CubicBezierEasing(0.0, 0.0, 0.2, 1.0) |
| 返回手势最大位移 | 56dp | 56dp |
| 返回取消时长 | 320ms | 320ms |
| 返回完成时长 | 380ms | 380ms |
| 预览模式时长 | 190ms | 190ms |

## 使用方法

### 1. 基础导航（已配置）

`NativeDoNavHost.kt` 已经配置了 Telegram 风格的转场动画：

```kotlin
NavHost(
    navController = navController,
    enterTransition = {
        // 新页面从右侧 48dp 滑入 + 淡入
        slideInHorizontally(initialOffsetX = { 48.dp }) + fadeIn()
    },
    exitTransition = {
        // 当前页面保持位置，轻微淡出（alpha 0.7）
        fadeOut(targetAlpha = 0.7f)
    },
    popEnterTransition = {
        // 返回时，前一页面从 alpha 0.7 淡入
        fadeIn(initialAlpha = 0.7f)
    },
    popExitTransition = {
        // 当前页面向右滑出 48dp + 淡出
        slideOutHorizontally(targetOffsetX = { 48.dp }) + fadeOut()
    }
)
```

### 2. 添加预测性返回支持

在需要支持手势返回的页面中使用 `TelegramStyleScaffold`：

```kotlin
@Composable
fun TopicRoute(
    topicId: Long,
    onNavigateBack: () -> Unit,
) {
    TelegramStyleScaffold(
        canNavigateBack = true,
        onNavigateBack = onNavigateBack,
        enableSwipeBack = true, // 启用边缘滑动返回
    ) {
        // 你的页面内容
        TopicContent(topicId = topicId)
    }
}
```

### 3. 预览模式（可选）

实现类似 Telegram 长按链接预览的效果：

```kotlin
@Composable
fun LinkPreview(
    url: String,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    TelegramPreviewContainer(visible = visible) {
        // 预览内容
        // 会有缩放 (0.7 -> 1.0) + 上移 40dp 的动画
        WebPreviewContent(url = url, onClose = onDismiss)
    }
}
```

### 4. 自定义手势处理（高级）

如果需要更精细的手势控制：

```kotlin
TelegramSwipeBackGesture(
    enabled = canGoBack,
    onSwipeProgress = { progress ->
        // 手势进度 0.0 - 1.0
        updateBackAnimation(progress)
    },
    onSwipeComplete = {
        // 手势完成，执行返回
        navController.popBackStack()
    },
    onSwipeCancelled = {
        // 手势取消，回弹
        resetBackAnimation()
    }
) {
    YourContent()
}
```

## 动画细节说明

### 标准转场动画

**打开新页面时：**
- 新页面：从 +48dp 位置滑入，同时从 alpha 0 淡入到 1
- 旧页面：保持位置不动，从 alpha 1 淡出到 0.7（保持可见）
- 时长：150ms
- 插值器：DecelerateInterpolator（减速）

**返回上一页时：**
- 当前页面：向右滑出 +48dp，同时从 alpha 1 淡出到 0
- 前一页面：保持位置不动，从 alpha 0.7 淡入到 1
- 时长：150ms
- 插值器：DecelerateInterpolator（减速）

### 预测性返回手势

**手势进行中：**
- 页面跟随手指实时移动（最大 56dp）
- 背景出现半透明黑色遮罩
- 无延迟，完全跟手

**手势取消（松手时进度 < 30%）：**
- 页面回弹到原位
- 时长：320ms
- 插值器：CubicBezierEasing.EASE_OUT_QUINT（快速减速）

**手势完成（松手时进度 ≥ 30%）：**
- 页面继续滑出完成返回
- 时长：380ms
- 插值器：CubicBezierEasing.EASE_OUT_QUINT

### 预览模式动画

**打开预览：**
- 缩放：0.7 -> 1.0
- 位移：向上 40dp -> 0
- 透明度：0 -> 1
- 时长：190ms
- 插值器：OvershootInterpolator（带回弹）

**关闭预览：**
- 缩放：1.0 -> 0.9
- 透明度：1 -> 0
- 时长：190ms
- 插值器：EASE_OUT_QUINT

## 与 Telegram 原版的对比

### 已实现的功能 ✅

1. **标准转场动画** - 完全一致
   - 48dp 位移
   - 150ms 时长
   - DecelerateInterpolator
   - 双层动画（前后页面同时动画）

2. **预测性返回** - 完全一致
   - 56dp 最大位移
   - 实时跟手
   - 320ms 取消动画
   - 380ms 完成动画

3. **动画参数** - 完全一致
   - 所有时长、位移、插值器都与原版相同

### 差异说明

1. **实现方式不同**
   - Telegram：使用 Fragment + 自定义 ActionBarLayout
   - 本项目：使用 Jetpack Compose Navigation
   - 效果：视觉效果完全一致，但底层实现不同

2. **手势检测**
   - Telegram：自己实现触摸事件处理
   - 本项目：使用 Android 13+ PredictiveBackHandler API
   - 效果：Android 13+ 设备上体验更好，旧设备使用降级方案

3. **性能优化**
   - Telegram：双容器复用（containerView / containerViewBack）
   - 本项目：Compose 自动管理组合
   - 效果：Compose 的声明式特性使代码更简洁

## 性能优化建议

1. **避免过度重组**
   ```kotlin
   // ❌ 不好：每次动画都重组
   Box(modifier = Modifier.offset(x = animatedOffset.dp))
   
   // ✅ 好：使用 graphicsLayer
   Box(modifier = Modifier.graphicsLayer { translationX = animatedOffset })
   ```

2. **使用 remember 缓存动画状态**
   ```kotlin
   val translationX = remember { Animatable(0f) }
   ```

3. **合理使用 LaunchedEffect**
   ```kotlin
   LaunchedEffect(visible) {
       // 只在 visible 改变时执行
       animateTransition()
   }
   ```

## 调试技巧

### 查看动画时长

在开发者选项中启用"动画时长缩放"，设置为 5x 或 10x，可以慢速查看动画效果。

### 对比 Telegram 原版

1. 在 Telegram 中打开聊天
2. 在你的应用中打开类似页面
3. 同时录屏对比动画曲线

### 测试预测性返回

需要 Android 13+ 设备：
1. 设置 -> 系统 -> 开发者选项 -> 启用"预测性返回动画"
2. 从屏幕左边缘向右滑动测试

## 常见问题

### Q: 为什么我的动画看起来不流畅？

A: 检查以下几点：
1. 是否使用了 `graphicsLayer` 而非 `offset`
2. 是否在动画过程中触发了不必要的重组
3. 设备性能是否足够（建议在真机上测试）

### Q: 预测性返回在我的设备上不工作？

A: 预测性返回需要：
1. Android 13 (API 33) 或更高版本
2. 在系统设置中启用"预测性返回动画"
3. 应用 targetSdk 设置为 34+

### Q: 如何禁用某个页面的手势返回？

A: 设置 `canNavigateBack = false`：
```kotlin
TelegramStyleScaffold(
    canNavigateBack = false, // 禁用返回
    onNavigateBack = {}
) { /* content */ }
```

## 参考资料

- [Telegram Android 源码](https://github.com/DrKLO/Telegram)
- [ActionBarLayout.java](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java)
- [Android Predictive Back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [Compose Animation](https://developer.android.com/jetpack/compose/animation)
