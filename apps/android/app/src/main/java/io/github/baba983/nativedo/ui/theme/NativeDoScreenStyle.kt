package io.github.baba983.nativedo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

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

internal fun nativeDoReaderScreenStyle(
    colors: NativeDoExtendedColors,
    darkTheme: Boolean,
): NativeDoScreenStyle = nativeDoRootScreenStyle(
    colors = colors,
    darkTheme = darkTheme,
)
