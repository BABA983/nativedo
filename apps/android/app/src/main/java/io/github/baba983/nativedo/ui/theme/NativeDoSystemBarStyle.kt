package io.github.baba983.nativedo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class NativeDoSystemBarBackgroundPolicy {
    Transparent,
    Explicit,
}

@Immutable
data class NativeDoSystemBarStyle(
    val statusBarColor: Color = Color.Transparent,
    val navigationBarColor: Color = Color.Transparent,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
    val enforceNavigationBarContrast: Boolean = false,
    val backgroundPolicy: NativeDoSystemBarBackgroundPolicy = NativeDoSystemBarBackgroundPolicy.Transparent,
) {
    fun resolvedStatusBarColor(): Color = when (backgroundPolicy) {
        NativeDoSystemBarBackgroundPolicy.Transparent -> Color.Transparent
        NativeDoSystemBarBackgroundPolicy.Explicit -> statusBarColor
    }

    fun resolvedNavigationBarColor(): Color = when (backgroundPolicy) {
        NativeDoSystemBarBackgroundPolicy.Transparent -> Color.Transparent
        NativeDoSystemBarBackgroundPolicy.Explicit -> navigationBarColor
    }
}

internal fun nativeDoDefaultSystemBarStyle(darkTheme: Boolean): NativeDoSystemBarStyle {
    return NativeDoSystemBarStyle(
        useDarkStatusBarIcons = !darkTheme,
        useDarkNavigationBarIcons = !darkTheme,
        enforceNavigationBarContrast = false,
    )
}
