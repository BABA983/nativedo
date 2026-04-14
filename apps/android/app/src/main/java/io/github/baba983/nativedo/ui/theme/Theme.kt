package io.github.baba983.nativedo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = NativeDoLightPrimary,
    onPrimary = NativeDoLightOnPrimary,
    background = NativeDoLightBackground,
    onBackground = NativeDoLightOnBackground,
    surface = NativeDoLightSurface,
    onSurface = NativeDoLightOnSurface,
    surfaceVariant = NativeDoLightSurfaceVariant,
    onSurfaceVariant = NativeDoLightOnSurfaceVariant,
    outline = NativeDoLightOutline,
    outlineVariant = NativeDoLightOutlineVariant,
    scrim = NativeDoLightScrim,
)

private val DarkColorScheme = darkColorScheme(
    primary = NativeDoDarkPrimary,
    onPrimary = NativeDoDarkOnPrimary,
    background = NativeDoDarkBackground,
    onBackground = NativeDoDarkOnBackground,
    surface = NativeDoDarkSurface,
    onSurface = NativeDoDarkOnSurface,
    surfaceVariant = NativeDoDarkSurfaceVariant,
    onSurfaceVariant = NativeDoDarkOnSurfaceVariant,
    outline = NativeDoDarkOutline,
    outlineVariant = NativeDoDarkOutlineVariant,
    scrim = NativeDoDarkScrim,
)

private val LocalNativeDoExtendedColors = staticCompositionLocalOf<NativeDoExtendedColors> {
    error("No NativeDoExtendedColors provided")
}

object NativeDoThemeExtras {
    val colors: NativeDoExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNativeDoExtendedColors.current

    val screenStyle: NativeDoScreenStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalNativeDoScreenStyle.current

    val systemBarStyle: NativeDoSystemBarStyle
        @Composable
        @ReadOnlyComposable
        get() = screenStyle.systemBarStyle

    @Composable
    @ReadOnlyComposable
    fun glass(role: NativeDoGlassRole): NativeDoGlassSpec = screenStyle.glass(role)
}

@Composable
fun NativeDoTheme(
    themeMode: ThemeMode = ThemeMode.System,
    screenStyle: NativeDoScreenStyle? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDarkTheme(systemInDarkTheme = isSystemInDarkTheme())
    val extendedColors = if (darkTheme) {
        NativeDoDarkExtendedColors
    } else {
        NativeDoLightExtendedColors
    }
    val resolvedScreenStyle = screenStyle ?: remember(extendedColors, darkTheme) {
        nativeDoRootScreenStyle(
            colors = extendedColors,
            darkTheme = darkTheme,
        )
    }

    CompositionLocalProvider(LocalNativeDoExtendedColors provides extendedColors) {
        NativeDoProvideScreenStyle(screenStyle = resolvedScreenStyle) {
            MaterialTheme(
                colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
                typography = NativeDoTypography,
                shapes = NativeDoShapes,
                content = content,
            )
        }
    }
}

@Composable
fun NativeDoProvideScreenStyle(
    screenStyle: NativeDoScreenStyle,
    content: @Composable () -> Unit,
) {
    NativeDoSystemBars(systemBarStyle = screenStyle.systemBarStyle)

    CompositionLocalProvider(LocalNativeDoScreenStyle provides screenStyle) {
        content()
    }
}

@Suppress("DEPRECATION")
@Composable
private fun NativeDoSystemBars(systemBarStyle: NativeDoSystemBarStyle) {
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = systemBarStyle.resolvedStatusBarColor().toArgb()
        window.navigationBarColor = systemBarStyle.resolvedNavigationBarColor().toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = systemBarStyle.enforceNavigationBarContrast
        }

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = systemBarStyle.useDarkStatusBarIcons
            isAppearanceLightNavigationBars = systemBarStyle.useDarkNavigationBarIcons
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
