package io.github.baba983.nativedo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class BlurMode {
    TintOnly,
    Blurred,
    Liquid,
}

enum class NativeDoGlassRole {
    Chrome,
    Popup,
    Field,
    Card,
}

@Immutable
data class NativeDoGlassSpec(
    val role: NativeDoGlassRole,
    val blurMode: BlurMode = BlurMode.TintOnly,
    val containerColor: Color,
    val borderColor: Color,
    val highlightColor: Color = Color.Transparent,
    val shadowColor: Color = Color.Transparent,
    val shadowElevation: Dp = 0.dp,
    val tonalElevation: Dp = 0.dp,
)
