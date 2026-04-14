package io.github.baba983.nativedo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.BlurMode
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec
import io.github.baba983.nativedo.ui.theme.NativeDoCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun NativeDoGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NativeDoCardShape,
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Card),
    blurMode: BlurMode = glassSpec.blurMode,
    strong: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolvedGlassSpec = if (strong && glassSpec.role == NativeDoGlassRole.Card) {
        NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome)
    } else {
        glassSpec
    }

    @Suppress("UNUSED_VARIABLE")
    val resolvedBlurMode = blurMode

    Surface(
        modifier = modifier,
        shape = shape,
        color = resolvedGlassSpec.containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = resolvedGlassSpec.borderColor,
        ),
        shadowElevation = resolvedGlassSpec.shadowElevation,
        tonalElevation = resolvedGlassSpec.tonalElevation,
        content = content,
    )
}
