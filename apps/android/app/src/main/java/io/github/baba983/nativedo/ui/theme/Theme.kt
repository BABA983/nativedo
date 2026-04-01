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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
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
}

@Composable
fun NativeDoTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDarkTheme(systemInDarkTheme = isSystemInDarkTheme())
    val extendedColors = if (darkTheme) {
        NativeDoDarkExtendedColors
    } else {
        NativeDoLightExtendedColors
    }

    NativeDoSystemBars(darkTheme = darkTheme)

    CompositionLocalProvider(LocalNativeDoExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = NativeDoTypography,
            shapes = NativeDoShapes,
            content = content,
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun NativeDoSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
