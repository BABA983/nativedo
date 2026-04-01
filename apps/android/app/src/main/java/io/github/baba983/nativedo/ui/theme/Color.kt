package io.github.baba983.nativedo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

internal val NativeDoLightPrimary = Color(0xFF1E7755)
internal val NativeDoLightOnPrimary = Color(0xFFFFFFFF)
internal val NativeDoLightBackground = Color(0xFFF4F0EA)
internal val NativeDoLightOnBackground = Color(0xFF17211D)
internal val NativeDoLightSurface = Color(0xFFFFFBF5)
internal val NativeDoLightOnSurface = Color(0xFF17211D)
internal val NativeDoLightSurfaceVariant = Color(0xFFE6E2D9)
internal val NativeDoLightOnSurfaceVariant = Color(0xFF56625B)
internal val NativeDoLightOutline = Color(0xFFBBC4BD)
internal val NativeDoLightOutlineVariant = Color(0xFFDCE2DB)
internal val NativeDoLightScrim = Color(0x33111614)

internal val NativeDoDarkPrimary = Color(0xFF8ADDB8)
internal val NativeDoDarkOnPrimary = Color(0xFF0D1713)
internal val NativeDoDarkBackground = Color(0xFF0B1211)
internal val NativeDoDarkOnBackground = Color(0xFFF0F4EF)
internal val NativeDoDarkSurface = Color(0xFF141C1A)
internal val NativeDoDarkOnSurface = Color(0xFFF0F4EF)
internal val NativeDoDarkSurfaceVariant = Color(0xFF26302D)
internal val NativeDoDarkOnSurfaceVariant = Color(0xFFB7C4BE)
internal val NativeDoDarkOutline = Color(0xFF42504B)
internal val NativeDoDarkOutlineVariant = Color(0xFF293330)
internal val NativeDoDarkScrim = Color(0x66000000)

@Immutable
data class NativeDoExtendedColors(
    val backgroundBase: Color,
    val backgroundElevated: Color,
    val glassBackground: Color,
    val glassBackgroundStrong: Color,
    val glassStroke: Color,
    val glassStrokeStrong: Color,
    val glassShadow: Color,
    val accent: Color,
    val accentMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
)

internal val NativeDoLightExtendedColors = NativeDoExtendedColors(
    backgroundBase = Color(0xFFF4F0EA),
    backgroundElevated = Color(0xFFFFFBF5),
    glassBackground = Color(0xCCFFFFFF),
    glassBackgroundStrong = Color(0xE6FFFDF8),
    glassStroke = Color(0x99FFFFFF),
    glassStrokeStrong = Color(0x66FFFFFF),
    glassShadow = Color(0x160A1713),
    accent = Color(0xFF1E7755),
    accentMuted = Color(0xFFDCEFE4),
    textPrimary = Color(0xFF17211D),
    textSecondary = Color(0xFF56625B),
    glowPrimary = Color(0x4DCBE8D6),
    glowSecondary = Color(0x40E8D7BE),
)

internal val NativeDoDarkExtendedColors = NativeDoExtendedColors(
    backgroundBase = Color(0xFF0B1211),
    backgroundElevated = Color(0xFF141C1A),
    glassBackground = Color(0xCC18201E),
    glassBackgroundStrong = Color(0xE61C2623),
    glassStroke = Color(0x33FFFFFF),
    glassStrokeStrong = Color(0x4DFFFFFF),
    glassShadow = Color(0x40000000),
    accent = Color(0xFF8ADDB8),
    accentMuted = Color(0xFF1F3930),
    textPrimary = Color(0xFFF0F4EF),
    textSecondary = Color(0xFFB7C4BE),
    glowPrimary = Color(0x40337F64),
    glowSecondary = Color(0x333A5579),
)
