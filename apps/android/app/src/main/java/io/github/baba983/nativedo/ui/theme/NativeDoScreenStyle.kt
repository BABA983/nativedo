package io.github.baba983.nativedo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
data class NativeDoScreenStyle(
    val chromeGlass: NativeDoGlassSpec,
    val popupGlass: NativeDoGlassSpec,
    val fieldGlass: NativeDoGlassSpec,
    val cardGlass: NativeDoGlassSpec,
    val systemBarStyle: NativeDoSystemBarStyle,
) {
    fun glass(role: NativeDoGlassRole): NativeDoGlassSpec = when (role) {
        NativeDoGlassRole.Chrome -> chromeGlass
        NativeDoGlassRole.Popup -> popupGlass
        NativeDoGlassRole.Field -> fieldGlass
        NativeDoGlassRole.Card -> cardGlass
    }
}

val LocalNativeDoScreenStyle = staticCompositionLocalOf<NativeDoScreenStyle> {
    error("No NativeDoScreenStyle provided")
}

internal fun nativeDoRootScreenStyle(
    colors: NativeDoExtendedColors,
    darkTheme: Boolean,
): NativeDoScreenStyle {
    return NativeDoScreenStyle(
        chromeGlass = colors.chromeGlassSpec(),
        popupGlass = colors.popupGlassSpec(),
        fieldGlass = colors.fieldGlassSpec(),
        cardGlass = colors.cardGlassSpec(),
        systemBarStyle = nativeDoDefaultSystemBarStyle(darkTheme),
    )
}

internal fun nativeDoBrowserScreenStyle(
    colors: NativeDoExtendedColors,
    darkTheme: Boolean,
): NativeDoScreenStyle = nativeDoRootScreenStyle(
    colors = colors,
    darkTheme = darkTheme,
)

internal fun nativeDoBrowserScreenStyle(
    baseStyle: NativeDoScreenStyle,
    browserChromeColor: Color,
    browserNavigationBarColor: Color,
    presentationProgress: Float,
): NativeDoScreenStyle {
    val progress = presentationProgress.coerceIn(0f, 1f)
    if (progress <= 0f) {
        return baseStyle
    }

    val baseSystemBarStyle = baseStyle.systemBarStyle
    val resolvedStatusBarColor = lerp(
        start = baseSystemBarStyle.resolvedStatusBarColor(),
        stop = browserChromeColor,
        fraction = progress,
    )
    val resolvedNavigationBarColor = lerp(
        start = baseSystemBarStyle.resolvedNavigationBarColor(),
        stop = browserNavigationBarColor,
        fraction = progress,
    )
    return baseStyle.copy(
        systemBarStyle = NativeDoSystemBarStyle(
            statusBarColor = resolvedStatusBarColor,
            navigationBarColor = resolvedNavigationBarColor,
            useDarkStatusBarIcons = nativeDoUseDarkSystemBarIcons(resolvedStatusBarColor),
            useDarkNavigationBarIcons = nativeDoUseDarkSystemBarIcons(resolvedNavigationBarColor),
            enforceNavigationBarContrast = false,
            backgroundPolicy = NativeDoSystemBarBackgroundPolicy.Explicit,
        ),
    )
}

internal fun nativeDoReaderScreenStyle(
    colors: NativeDoExtendedColors,
    darkTheme: Boolean,
): NativeDoScreenStyle = nativeDoRootScreenStyle(
    colors = colors,
    darkTheme = darkTheme,
)
