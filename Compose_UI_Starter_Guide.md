# Kotlin + Jetpack Compose UI 基座方案（参考 Telegram 风格思路）

## 目标

- 借鉴 Telegram Android 项目的优点
- 完全使用 **Kotlin + Jetpack Compose** 重写
- 适合新项目起步
- 避免直接复制 GPL 代码

---

## 1. 推荐项目结构

```text
MyApp/
├─ app/
├─ core/
│  ├─ designsystem/
│  ├─ ui/
│  ├─ animation/
│  ├─ navigation/
│  ├─ model/
│  └─ common/
├─ feature/
│  ├─ home/
│  ├─ chat/
│  ├─ profile/
│  └─ settings/
└─ build-logic/   (可选)
```

### 分层职责

#### `core/designsystem`
- theme
- color tokens
- typography
- shapes
- spacing
- icon wrapper
- ripple / elevation 规范

#### `core/animation`
- easing
- spring
- press animation spec
- enter/exit animation spec

#### `core/ui`
- AppScaffold
- AppTopBar
- AppBottomSheet
- AppBulletin
- AppButton
- AppListItem
- AppTextField
- AppLoading
- AppEmptyState

#### `core/navigation`
- routes
- nav graph
- navigator helpers

#### `feature/*`
- 页面
- ViewModel
- uiState
- feature 内部组件

---

## 2. 设计系统：Color / Spacing / Shape / Theme

### `core/designsystem/theme/AppColors.kt`

```kotlin
package com.example.core.designsystem.theme

import androidx.compose.ui.graphics.Color

data class AppColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentOn: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val ripple: Color,
    val overlay: Color,
)
```

### `core/designsystem/theme/AppPalettes.kt`

```kotlin
package com.example.core.designsystem.theme

import androidx.compose.ui.graphics.Color

val LightAppColors = AppColors(
    bgPrimary = Color(0xFFF4F5F7),
    bgSecondary = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFDFDFD),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    textTertiary = Color(0xFF9CA3AF),
    accent = Color(0xFF2AABEE),
    accentOn = Color(0xFFFFFFFF),
    divider = Color(0xFFE5E7EB),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFEF4444),
    ripple = Color(0x14000000),
    overlay = Color(0x55000000),
)

val DarkAppColors = AppColors(
    bgPrimary = Color(0xFF18222D),
    bgSecondary = Color(0xFF222B33),
    surface = Color(0xFF222B33),
    surfaceElevated = Color(0xFF2A3642),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFFB6BDC6),
    textTertiary = Color(0xFF7B8794),
    accent = Color(0xFF4DB8FF),
    accentOn = Color(0xFF001018),
    divider = Color(0xFF334155),
    success = Color(0xFF22C55E),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFF87171),
    ripple = Color(0x22FFFFFF),
    overlay = Color(0x88000000),
)
```

### `core/designsystem/theme/AppSpacing.kt`

```kotlin
package com.example.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
)
```

### `core/designsystem/theme/AppShapes.kt`

```kotlin
package com.example.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class AppShapes(
    val small = RoundedCornerShape(8.dp),
    val medium = RoundedCornerShape(12.dp),
    val large = RoundedCornerShape(16.dp),
    val pill = RoundedCornerShape(999.dp),
)
```

### `core/designsystem/theme/AppTheme.kt`

```kotlin
package com.example.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("No AppColors provided")
}

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppShapes = staticCompositionLocalOf { AppShapes() }

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppSpacing provides AppSpacing(),
        LocalAppShapes provides AppShapes(),
    ) {
        MaterialTheme(
            content = content
        )
    }
}

object AppThemeTokens {
    val colors: AppColors
        @Composable get() = LocalAppColors.current

    val spacing: AppSpacing
        @Composable get() = LocalAppSpacing.current

    val shapes: AppShapes
        @Composable get() = LocalAppShapes.current
}
```

---

## 3. 统一动画规范

### `core/animation/AppEasing.kt`

```kotlin
package com.example.core.animation

import androidx.compose.animation.core.CubicBezierEasing

object AppEasing {
    val Default = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseBoth = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}
```

### `core/animation/AppMotion.kt`

```kotlin
package com.example.core.animation

object AppMotion {
    const val Fast = 120
    const val Normal = 220
    const val Medium = 320
    const val Slow = 400
}
```

---

## 4. 按压缩放 Modifier

### `core/ui/modifier/pressScaleClickable.kt`

```kotlin
package com.example.core.ui.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.core.animation.AppEasing
import com.example.core.animation.AppMotion

fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) 80 else AppMotion.Medium,
            easing = if (pressed) AppEasing.Default else AppEasing.EaseOutBack
        ),
        label = "pressScale"
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
```

---

## 5. 基础组件模板

### `core/ui/scaffold/AppScaffold.kt`

```kotlin
package com.example.core.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.core.designsystem.theme.AppThemeTokens

@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = AppThemeTokens.colors.bgPrimary
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppThemeTokens.colors.bgPrimary)
        ) {
            content(padding)
        }
    }
}
```

### `core/ui/topbar/AppTopBar.kt`

```kotlin
package com.example.core.ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.AppThemeTokens

@Composable
fun AppTopBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppThemeTokens.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            navigationIcon?.invoke()
        }

        Text(
            text = title,
            color = AppThemeTokens.colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { actions?.invoke(this) }
        )
    }
}
```

### `core/ui/button/AppButton.kt`

```kotlin
package com.example.core.ui.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.core.ui.modifier.pressScaleClickable
import com.example.core.designsystem.theme.AppThemeTokens

@Composable
fun AppButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppThemeTokens.colors.accent)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AppThemeTokens.colors.accentOn
        )
    }
}
```

### `core/ui/list/AppListItem.kt`

```kotlin
package com.example.core.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.AppThemeTokens
import com.example.core.ui.modifier.pressScaleClickable

@Composable
fun AppListItem(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.pressScaleClickable(scaleDown = 0.985f, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppThemeTokens.colors.surface)
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.padding(end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                leading()
            }
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = AppThemeTokens.colors.textPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = AppThemeTokens.colors.textSecondary
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}
```

### `core/ui/common/AppDivider.kt`

```kotlin
package com.example.core.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.AppThemeTokens

@Composable
fun AppDivider(startIndent: Int = 0) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent.dp)
            .height(1.dp)
            .background(AppThemeTokens.colors.divider)
    )
}
```

---

## 6. AppBottomSheet 模板

### `core/ui/sheet/AppBottomSheet.kt`

```kotlin
package com.example.core.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.theme.AppThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AppThemeTokens.colors.surface,
        scrimColor = AppThemeTokens.colors.overlay,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = AppThemeTokens.colors.textTertiary,
                        shape = AppThemeTokens.shapes.pill
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppThemeTokens.colors.surface)
                .padding(bottom = 16.dp),
            content = content
        )
    }
}
```

---

## 7. Bulletin 模板

### `core/ui/bulletin/BulletinState.kt`

```kotlin
package com.example.core.ui.bulletin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BulletinState {
    var currentMessage by mutableStateOf<String?>(null)
        private set

    fun show(message: String) {
        currentMessage = message
    }

    fun dismiss() {
        currentMessage = null
    }
}
```

### `core/ui/bulletin/AppBulletinHost.kt`

```kotlin
package com.example.core.ui.bulletin

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.core.animation.AppEasing
import com.example.core.animation.AppMotion
import com.example.core.designsystem.theme.AppThemeTokens

@Composable
fun rememberBulletinState(): BulletinState = remember { BulletinState() }

@Composable
fun AppBulletinHost(
    state: BulletinState,
    modifier: Modifier = Modifier
) {
    val message = state.currentMessage

    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            state.dismiss()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(
                    durationMillis = AppMotion.Medium,
                    easing = AppEasing.EaseOutQuint
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(
                    durationMillis = 180,
                    easing = AppEasing.EaseOut
                )
            ) + fadeOut()
        ) {
            if (message != null) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .background(
                            color = AppThemeTokens.colors.surfaceElevated,
                            shape = AppThemeTokens.shapes.large
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = message,
                        color = AppThemeTokens.colors.textPrimary
                    )
                }
            }
        }
    }
}
```

---

## 8. 首页示例

### `feature/home/HomeScreen.kt`

```kotlin
package com.example.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.ui.bulletin.AppBulletinHost
import com.example.core.ui.bulletin.rememberBulletinState
import com.example.core.ui.button.AppButton
import com.example.core.ui.common.AppDivider
import com.example.core.ui.list.AppListItem
import com.example.core.ui.scaffold.AppScaffold
import com.example.core.ui.topbar.AppTopBar

@Composable
fun HomeScreen() {
    val bulletinState = rememberBulletinState()

    AppScaffold(
        topBar = {
            AppTopBar(title = "Home")
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AppListItem(
                    title = "Profile",
                    subtitle = "View and edit your profile",
                    onClick = { bulletinState.show("Open Profile") }
                )
                AppDivider()

                AppListItem(
                    title = "Chats",
                    subtitle = "Go to your conversations",
                    onClick = { bulletinState.show("Open Chats") }
                )
                AppDivider()

                Spacer(modifier = Modifier.height(24.dp))

                AppButton(
                    text = "Show Bulletin",
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    bulletinState.show("Saved successfully")
                }
            }

            AppBulletinHost(state = bulletinState)
        }
    }
}
```

---

## 9. MainActivity

### `app/MainActivity.kt`

```kotlin
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.core.designsystem.theme.AppTheme
import com.example.feature.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                HomeScreen()
            }
        }
    }
}
```

---

## 10. 推荐迭代顺序

### 第 1 天
- 建项目
- 建 `core/designsystem`
- 建 `core/ui`
- 写 `AppTheme`
- 写 `AppScaffold`
- 写 `AppTopBar`

### 第 2 天
- 写 `AppButton`
- 写 `AppListItem`
- 写 `AppDivider`
- 写 `pressScaleClickable`

### 第 3 天
- 写 `AppBottomSheet`
- 写 `AppBulletinHost`
- 做 `HomeScreen`

### 第 4 天
- 接 `Navigation Compose`
- 建 `feature/home`
- 建 `feature/profile`

---

## 11. 开发原则

### 1）业务层不要直接写颜色

错误：

```kotlin
Text(color = Color.Gray)
```

正确：

```kotlin
Text(color = AppThemeTokens.colors.textSecondary)
```

### 2）不要每个页面自己写按钮样式
统一：
- `AppButton`
- `AppIconButton`
- `AppTextButton`

### 3）不要每个列表项都手搓
统一：
- `AppListItem`
- `SettingItem`
- `SectionHeader`

### 4）动画参数统一
统一使用：
- `AppEasing`
- `AppMotion`

### 5）弹层和提示条统一风格
统一：
- `AppBottomSheet`
- `AppBulletin`
- `AppDialog`

---

## 12. 第一版建议先做的组件

1. `AppTheme`
2. `AppScaffold`
3. `AppTopBar`
4. `AppButton`
5. `AppTextField`
6. `AppListItem`
7. `AppDivider`
8. `AppBottomSheet`
9. `AppBulletinHost`
10. `Loading / EmptyState`

---

## 13. 交互案例补充：In-App Browser / Photo Viewer / 菜单动效

这一节补充 Telegram 仓库里几个很值得借鉴的交互模式，适合在 Compose 项目中转译实现。

---

### 13.1 In-App Browser 需要区分两类容器

这一点很重要：Telegram 里“浏览内容”的容器不止一种。

如果你实际体验到的是：

- **从下往上打开**
- **关闭时整体向下收起**

那么你体验到的更接近：

- `BotWebViewSheet`
- `BotWebViewAttachedSheet`

也就是 **sheet 型浏览器 / webview overlay**。

而 `ArticleViewer` 更接近：

- 阅读器
- Instant View
- 长内容浏览层

它不应和 sheet browser 混为一谈。

---

### 13.2 Sheet 型浏览器（更接近 Telegram 常见 in-app browser）

代表实现：

- `TMessagesProj/src/main/java/org/telegram/ui/bots/BotWebViewSheet.java`
- `TMessagesProj/src/main/java/org/telegram/ui/bots/BotWebViewAttachedSheet.java`

#### 打开/关闭动画特征

这类浏览器容器更像 bottom sheet / fullscreen sheet：

- 打开时从底部抬起
- 关闭时整体向下收回
- 内部有独立的打开进度：
  - `openedProgress`
  - `setOpen(true / false)`
  - duration `220ms`
  - easing `EASE_OUT_QUINT`

因此它更准确的产品感受是：

> **一个浏览层从底部展开，必要时再切入全屏**

而不是普通页面横向 push。

#### Fullscreen 切换

`setFullscreen(fullscreen, animated)` 的联动非常完整：

- ActionBar alpha / translationY
- 内容容器 translationX / translationY
- fullscreenButtons alpha
- botButtons translationX
- viewport height offset 同步

核心参数：

- duration：`280ms`
- easing：`EASE_OUT_QUINT`

这类设计很适合 Compose 中做成：

- `AppBrowserSheet`
- 一个 `fullscreenProgress`
- 驱动 top bar / bottom bar / content padding / system bar style 联动

#### 颜色切换动画

浏览器主题变化不是硬切：

- 背景色切换：`200ms`
- 导航栏色切换：`200ms`
- ActionBar 色切换：`200ms`
- 基于 `blendARGB()` 做平滑过渡

Compose 中推荐：

- `animateColorAsState`
- 顶栏 / 底栏 / 背景统一渐变

---

### 13.3 Reader / Article 型容器

代表实现：

- `TMessagesProj/src/main/java/org/telegram/ui/ArticleViewer.java`

#### 打开/关闭动画特征

`ArticleViewer` 的进入/退出更克制，核心是：

- `windowView.alpha: 0 -> 1`
- `containerView.alpha: 0 -> 1`
- `windowView.translationX: dp(56) -> 0`
- duration 约 `150ms`

关闭时反向：

- `windowView.alpha -> 0`
- `containerView.alpha -> 0`
- `windowView.translationX: 0 -> dp(56)`
- duration 约 `150ms`

这更像：

> **轻量阅读层 / 二级内容层的横向入场**

适合：

- 长文阅读页
- Instant View
- 轻文档浏览页

#### 页面左右切换

`ArticleViewer` 内部页面切换不是 ViewPager，而是：

- 对 `pages[0] / pages[1]` 手动做 `translationX`
- duration 约 `320ms`
- 插值器：`EASE_OUT_QUINT`

顺序特征：

- 新页面从右进入：`screenWidth -> 0`
- 旧页面向右退出：`0 -> screenWidth`

这类实现适合 Compose 中的：

- `AnimatedContent`
- 自定义 `slideInHorizontally / slideOutHorizontally`

推荐参数：

- duration：`280 ~ 320ms`
- easing：`EaseOutQuint`

---

### 13.4 Bot WebView / Fullscreen Browser 模式

`BotWebViewSheet` 的交互比普通浏览更复杂，值得参考三点：

#### 1）打开进度与状态联动

文件中有一个独立的打开进度：

- `openedProgress`
- `setOpen(true/false)`
- `ValueAnimator`
- duration `220ms`
- easing `EASE_OUT_QUINT`

这类“打开进度变量”很适合 Compose：

- 用一个 `Float` 状态驱动多个 UI
- 同时控制：
  - 背景色
  - 导航栏色
  - 顶栏透明度
  - 底部操作区

#### 2）全屏切换动画

`setFullscreen(fullscreen, animated)` 的核心动作包括：

- ActionBar alpha/translationY 联动
- Web 容器 translationX / translationY 联动
- 全屏按钮 alpha 联动
- botButtons translationX 联动
- WebView viewport height offset 实时调整

核心参数：

- duration：`280ms`
- easing：`EASE_OUT_QUINT`

这说明 Telegram 的浏览器全屏不是瞬时切状态，而是：

> **布局几何变化 + 顶栏退场 + 按钮切换 + 视口高度同步**

在 Compose 中可以转成：

- 一个 `fullscreenProgress`
- 驱动：
  - top bar alpha
  - top bar offset
  - content padding
  - floating controls alpha
  - system bars style

#### 3）颜色切换动画

`BotWebViewSheet` 中背景色、导航栏色、ActionBar 色切换都不是硬切：

- `ValueAnimator.ofFloat(0, 1)`
- duration `200ms`
- `ColorUtils.blendARGB()`

这很值得加入你的设计系统：

- 页面主题变化时，用 `animateColorAsState`
- 系统栏和组件颜色尽量过渡，不要跳变

---

### 13.3 Photo Viewer（图片查看器）交互分析

代表实现：

- `TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java`

这个组件是仓库里最完整的媒体查看交互之一。

#### 打开动画

PhotoViewer 的进入不是简单 fade，而是：

1. 从原图所在位置取一个 `PlaceProviderObject`
2. 计算缩放、位移、裁切、圆角半径
3. 用 `ClippingImageView` 做形变过渡
4. 同时让：
   - 背景 alpha `0 -> 255`
   - container alpha `0 -> 1`
   - navigationBar alpha `0 -> 1`
5. duration `200ms`

本质上是：

> **从缩略图原位 morph 到全屏图**

这比普通图片预览更高级，因为它保留了“来源感”。

#### 关闭动画

关闭时会反向回到原始缩略图位置：

- 当前全屏图计算回缩略图目标位置
- 同步还原：
  - scale
  - translationX/Y
  - clipping progress
  - 圆角半径
  - 背景 alpha
  - container alpha

如果找不到目标缩略图，则退化成：

- 向上/向下平移离场
- 背景 fade out

#### 左右切换图片

这部分不是 ViewPager，而是自己管理：

- `translationX`
- `minX / maxX`
- `VelocityTracker`
- fling 阈值
- `goToNext()` / `goToPrev()`
- 最终调用 `animateTo(...)`

切换策略很成熟：

- 拖拽不够：回弹到边界
- 拖拽超阈值或 fling：切到下一张/上一张
- 切图时旁边图片会出现：
  - alpha 渐变
  - scale 微缩放差

这类“手势 + 回弹 + 惯性 + 预览邻图”的组合，是非常值得借鉴的。

#### 双击缩放 / 拖拽关闭 / fling

PhotoViewer 还包含：

- 双击：`1x <-> 3x` 缩放切换
- 捏合缩放
- 拖拽关闭（纵向）
- fling 惯性滚动
- 边界回弹

这说明它不是单一动画组件，而是完整的手势查看器。

---

### 13.4 右上角菜单动画（Popup / More Menu）

代表实现：

- `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarMenuItem.java`
- `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarPopupWindow.java`
- `TMessagesProj/src/main/res/anim/popup_in.xml`
- `TMessagesProj/src/main/res/anim/popup_context_in.xml`

#### 菜单弹出层特征

普通 popup：

- `popup_in.xml`：150ms alpha in
- `popup_out.xml`：150ms alpha out

context popup：

- `popup_context_in.xml`
- alpha `0 -> 1`
- scale `95% -> 100%`
- overshoot interpolator
- pivot 在右上/触发点附近

这类菜单非常适合：

- 右上角 more menu
- 长按 context menu
- 卡片局部操作菜单

#### 菜单项级联动画

`ActionBarPopupWindow.startAnimation()` 里还有一个很细的点：

- 容器背景展开
- 子项逐个 cascade
- 每个 item：
  - `translationY: -6dp -> 0`
  - `alpha: 0 -> 1`
- duration：`150 + 16 * visibleCount`

也就是说菜单不是整块一起出现，而是：

> **容器先出现，条目再轻微级联落位**

这会让菜单更“活”，但又不会太花哨。

#### 动态显示/隐藏子菜单项

`ActionBarMenuItem.showSubItem(id, animated)` 支持：

- 子项运行时动态显示
- alpha 150ms 过渡

这一点很适合复杂上下文菜单：

- 不同内容显示不同操作
- 某些动作延迟出现
- feature flag / 权限驱动菜单项

---

### 13.5 可以加入 Compose 基座的改进点

基于上面的分析，你的 Compose 基座里建议新增这些能力：

#### 1）统一页面转场预设

新增：

- `AppTransitions.PageSlideIn`
- `AppTransitions.PageSlideOut`
- `AppTransitions.PopupMenu`
- `AppTransitions.MediaOpen`

建议参数：

- 轻页面：150ms，fade + 轻位移
- 横向切页：320ms，`EaseOutQuint`
- popup：150~200ms，fade + 0.95 -> 1 scale
- 媒体打开：200ms，container fade + shared source morph

#### 2）自定义 `AppPopupMenu`

建议能力：

- anchored menu
- 支持右上角 pivot
- 子项 cascade enter
- 动态 show/hide item
- 支持分隔 gap
- 支持顶部补充卡片（top view）

#### 3）自定义 `AppMediaViewer`

建议能力：

- 从列表缩略图打开到全屏
- 关闭时回落到来源图
- 左右切换图片
- 双击缩放
- 捏合缩放
- 下拉关闭
- 顶栏/底栏自动显隐

#### 4）浏览器/阅读器模式的统一容器

建议抽象一个：

- `AppBrowserScaffold`

支持：

- 普通模式
- 全屏模式
- ActionBar alpha / translation 联动
- 内容区 viewport offset 联动
- 顶部/底部按钮渐隐渐现
- 背景色过渡

#### 5）颜色切换统一动画

在所有以下场景统一使用：

- top bar 背景切换
- nav bar 切换
- sheet 背景切换
- 浏览器主题切换
- viewer 背景切换

建议统一：

- `200ms`
- `Default` easing
- `animateColorAsState`

---

### 13.6 Compose 对应实现建议表

下面是把 Telegram 这些容器思路，映射到 Compose 项目中的建议实现。

| Telegram 交互容器 | Compose 建议组件 | 适用场景 | 建议支持能力 |
|---|---|---|---|
| Sheet 型浏览器 | `AppBrowserSheet` | WebView / H5 / bot 页 / 内嵌网页 | 底部进入、向下关闭、半屏到全屏、top bar 渐隐、颜色过渡、下拉关闭 |
| Reader / Article 型容器 | `AppReaderScreen` | 长文、图文、文章阅读、即时预览 | 页面级进入/退出、横向切页、顶部工具栏自动隐藏、阅读进度/搜索/目录 |
| 图片查看器 | `AppMediaViewer` | 图片、视频、画廊、相册预览 | 从缩略图打开、回落关闭、左右切换、双击缩放、捏合缩放、下拉关闭、顶底栏显隐 |
| 右上角更多菜单 | `AppPopupMenu` | More 菜单、上下文菜单、卡片操作菜单 | anchored popup、pivot 缩放、级联出现、动态显示/隐藏菜单项、分组/分隔 gap |

#### 1）`AppBrowserSheet`

建议 API 目标：

```kotlin
@Composable
fun AppBrowserSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    state: AppBrowserSheetState = rememberAppBrowserSheetState(),
    topBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
)
```

建议状态：

```kotlin
data class AppBrowserSheetUiState(
    val openedProgress: Float,
    val fullscreenProgress: Float,
    val topBarVisible: Boolean,
    val isFullscreen: Boolean,
    val backgroundColor: Color,
    val navigationBarColor: Color,
)
```

建议支持的动画与交互：

- 打开：`translationY + alpha`
- 关闭：`translationY + alpha`
- 全屏切换：
  - top bar alpha/offset
  - bottom controls alpha/offset
  - content viewport padding
- 背景 / nav bar / top bar color 过渡
- drag to dismiss
- 进入全屏时支持系统栏样式切换

推荐参数：

- open / close：`220ms`, `EaseOutQuint`
- fullscreen transition：`280ms`, `EaseOutQuint`
- color blend：`200ms`, `Default`

---

#### 2）`AppReaderScreen`

建议 API 目标：

```kotlin
@Composable
fun AppReaderScreen(
    visible: Boolean,
    onClose: () -> Unit,
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
)
```

建议支持能力：

- 轻量页面级进入/退出
- 横向切页
- 顶栏显隐
- 页内搜索 / 目录 / 分享
- 可选的文字选择菜单

建议动画：

- 整体打开：`alpha + translationX`
- 页切换：
  - `slideInHorizontally`
  - `slideOutHorizontally`
  - duration `320ms`
  - easing `EaseOutQuint`

适合你项目里的：

- 帮助文档页
- 长文章页
- “详情阅读”页面

---

#### 3）`AppMediaViewer`

这是最值得单独做的一块，建议不要只做简单 Dialog 图片预览。

建议 API 目标：

```kotlin
@Composable
fun AppMediaViewer(
    visible: Boolean,
    items: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    sourceProvider: MediaSourceProvider? = null,
)
```

其中 `sourceProvider` 用于做“从缩略图打开 / 回落关闭”的来源动画。

建议能力：

- 从列表缩略图 morph 到全屏
- 回落关闭到来源图
- 左右切换
- 双击缩放
- 捏合缩放
- 惯性拖拽
- 下拉关闭
- 顶栏/底栏自动显隐
- 图片/视频统一容器

建议状态：

```kotlin
data class AppMediaViewerState(
    val currentIndex: Int,
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val barsVisible: Boolean,
    val dragToDismissProgress: Float,
)
```

建议动画：

- open morph：`200ms`
- close morph：`200ms`
- switch next/prev：`250~320ms`
- zoom settle：`250ms`
- bar show/hide：`150ms`

建议手势优先级：

1. scale > 1 时，优先平移图片
2. scale == 1 时，优先横向切图 / 纵向关闭
3. fling 到阈值时切图
4. 拖拽不够时回弹

---

#### 4）`AppPopupMenu`

建议 API 目标：

```kotlin
@Composable
fun AppPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    items: List<AppPopupMenuItem>,
    header: (@Composable () -> Unit)? = null,
)
```

建议菜单项模型：

```kotlin
data class AppPopupMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
```

建议支持能力：

- anchored 定位
- 右上角 pivot 缩放
- alpha + scale 打开
- item cascade enter
- 动态显示/隐藏菜单项
- gap separator
- header/top view
- 点击外部 dismiss

建议动画：

- 容器：`150~200ms`, fade + `0.95 -> 1f scale`
- item：`translationY(-6dp -> 0)` + alpha
- 动态显示 item：`150ms alpha`

---

#### 5）建议加入 `core/ui` 的组件清单

基于这轮分析，建议你后续把这些加入基座：

```text
core/ui/
  browser/
    AppBrowserSheet.kt
    AppBrowserSheetState.kt
  reader/
    AppReaderScreen.kt
  media/
    AppMediaViewer.kt
    AppMediaViewerState.kt
  popup/
    AppPopupMenu.kt
    AppPopupMenuItem.kt
```

这样你的基座会形成比较完整的“高层交互容器”体系。

---

### 13.7 可直接加入 `core/animation` 的规范建议

可以在你的 Compose 工程中新增：

```kotlin
object AppMotionPreset {
    const val Popup = 150
    const val BrowserOpen = 150
    const val BrowserFullscreen = 280
    const val PageHorizontal = 320
    const val MediaOpen = 200
    const val MediaClose = 200
    const val ColorBlend = 200
}
```

以及：

```kotlin
object AppTransitions {
    val PopupEasing = AppEasing.Default
    val MediaEasing = AppEasing.EaseOutQuint
    val PageEasing = AppEasing.EaseOutQuint
}
```

---

## 14. 总结

这套方案适合：

- 新开 Kotlin + Compose 安卓项目
- 想借鉴 Telegram 的 UI 思路
- 但不直接复制其 Java/View 实现

核心思想是：

- 用 **设计 token** 代替散落颜色
- 用 **统一动效规范** 保证质感一致
- 用 **基础组件库** 提升复用性
- 用 **feature 分层** 保持项目可维护

如果后续继续扩展，可以增加：

- `AppTextField`
- `AppDialog`
- `AppIconButton`
- `SettingSwitchItem`
- `SectionHeader`
- `EmptyState`
- `LoadingState`
- `Navigation Compose` 封装
