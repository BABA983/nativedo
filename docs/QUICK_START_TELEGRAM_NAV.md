# 快速开始：集成 Telegram 风格导航

## 5 分钟快速集成

### 步骤 1：验证基础动画（已完成 ✅）

你的 `NativeDoNavHost.kt` 已经配置了正确的动画参数：
- ✅ 48dp 位移
- ✅ 150ms 时长
- ✅ 淡入淡出效果

### 步骤 2：添加预测性返回支持（可选，推荐）

在任何需要支持手势返回的页面中，用 `TelegramStyleScaffold` 包装：

```kotlin
// 在 NativeDoNavHost.kt 中修改
composable("topic/{topicId}") { entry ->
    val topicId = entry.arguments?.getString("topicId")!!.toLong()
    NativeDoNavScenePadding(contentPadding = contentPadding) {
        // 添加这个包装器
        TelegramStyleScaffold(
            canNavigateBack = navController.previousBackStackEntry != null,
            onNavigateBack = { navController.popBackStack() }
        ) {
            TopicRoute(
                topicId = topicId,
                onHeaderTitleChanged = onTopicHeaderTitleChanged,
                onSessionRecoveryRequired = openLogin,
                onTopicClick = { linkedTopicId ->
                    navController.navigate("topic/$linkedTopicId")
                },
                onUserClick = { username ->
                    navController.navigate("user/$username")
                },
                onOpenLink = onOpenLink,
            )
        }
    }
}
```

### 步骤 3：测试效果

1. **测试标准转场**：
   - 打开任意话题页面
   - 观察页面从右侧 48dp 滑入
   - 点击返回，观察页面向右滑出

2. **测试预测性返回**（需要 Android 13+）：
   - 在系统设置中启用"预测性返回动画"
   - 从屏幕左边缘向右滑动
   - 观察页面跟随手指移动

## 完整示例

### 为所有二级页面添加手势返回

```kotlin
// NativeDoNavHost.kt
@Composable
internal fun NativeDoNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    // ... 其他参数
) {
    NavHost(
        navController = navController,
        startDestination = RootDestination.rootContainerRoute,
        // ... 动画配置
    ) {
        // 根容器（不需要返回手势）
        composable(RootDestination.rootContainerRoute) {
            // ... 现有代码
        }

        // 话题页面（添加返回手势）
        composable("topic/{topicId}") { entry ->
            val topicId = entry.arguments?.getString("topicId")!!.toLong()
            NativeDoNavScenePadding(contentPadding = contentPadding) {
                TelegramStyleScaffold(
                    canNavigateBack = true,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    TopicRoute(/* ... */)
                }
            }
        }

        // 用户页面（添加返回手势）
        composable("user/{username}") { entry ->
            NativeDoNavScenePadding(contentPadding = contentPadding) {
                TelegramStyleScaffold(
                    canNavigateBack = true,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    ProfileRoute(/* ... */)
                }
            }
        }

        // 浏览器页面（不添加返回手势，因为有自己的处理）
        composable(APP_BROWSER_ROUTE) { entry ->
            // ... 现有代码，不需要包装
        }

        // 登录页面（添加返回手势）
        composable("login") {
            NativeDoNavScenePadding(contentPadding = contentPadding) {
                TelegramStyleScaffold(
                    canNavigateBack = true,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    LoginWebViewRoute(/* ... */)
                }
            }
        }
    }
}
```

## 高级功能

### 1. 禁用特定页面的手势返回

```kotlin
TelegramStyleScaffold(
    canNavigateBack = false, // 禁用返回
    onNavigateBack = {}
) {
    // 内容
}
```

### 2. 自定义返回逻辑

```kotlin
TelegramStyleScaffold(
    canNavigateBack = true,
    onNavigateBack = {
        // 自定义逻辑，例如显示确认对话框
        if (hasUnsavedChanges) {
            showConfirmDialog()
        } else {
            navController.popBackStack()
        }
    }
) {
    // 内容
}
```

### 3. 条件性启用手势返回

```kotlin
val canGoBack by remember {
    derivedStateOf {
        !isEditing && navController.previousBackStackEntry != null
    }
}

TelegramStyleScaffold(
    canNavigateBack = canGoBack,
    onNavigateBack = { navController.popBackStack() }
) {
    // 内容
}
```

## 性能优化提示

### ✅ 推荐做法

```kotlin
// 使用 graphicsLayer 而非 offset
Box(
    modifier = Modifier.graphicsLayer {
        translationX = animatedValue
    }
)

// 缓存动画状态
val translationX = remember { Animatable(0f) }

// 使用 derivedStateOf 避免不必要的重组
val canGoBack by remember {
    derivedStateOf { navController.previousBackStackEntry != null }
}
```

### ❌ 避免的做法

```kotlin
// 不要使用 offset（会触发重新布局）
Box(modifier = Modifier.offset(x = animatedValue.dp))

// 不要在每次重组时创建新的 Animatable
val translationX = Animatable(0f) // 错误！

// 不要直接计算（会导致频繁重组）
val canGoBack = navController.previousBackStackEntry != null
```

## 故障排除

### 问题：动画不流畅

**解决方案**：
1. 确保使用 `graphicsLayer` 而非 `offset`
2. 检查是否有不必要的重组
3. 在真机上测试（模拟器性能较差）

### 问题：预测性返回不工作

**检查清单**：
- [ ] 设备是 Android 13 (API 33) 或更高版本
- [ ] 在系统设置中启用了"预测性返回动画"
- [ ] 应用的 targetSdk 设置为 34+
- [ ] 在 AndroidManifest.xml 中启用了 `android:enableOnBackInvokedCallback="true"`

### 问题：手势返回触发太灵敏

**调整方案**：
在 `TelegramStyleScaffold.kt` 中修改触发阈值：

```kotlin
// 当前默认：30%
if (progress > 0.3f) {
    onSwipeComplete()
}

// 调整为 40%（需要更大的滑动距离）
if (progress > 0.4f) {
    onSwipeComplete()
}
```

## 下一步

- 查看 [完整文档](./TELEGRAM_NAVIGATION.md) 了解所有功能
- 查看 [动画参数配置](../apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/TelegramAnimationSpec.kt) 了解可调整的参数
- 查看 [示例代码](../apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/TelegramNavExamples.kt) 了解更多用法
