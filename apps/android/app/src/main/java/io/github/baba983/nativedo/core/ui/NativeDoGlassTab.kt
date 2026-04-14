package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec
import io.github.baba983.nativedo.ui.theme.NativeDoMotionTheme
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeDoGlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDoubleClick: (() -> Unit)? = null,
    shape: Shape = NativeDoPillShape,
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
    selectedContentColor: Color = MaterialTheme.colorScheme.primary,
    idleContentColor: Color = NativeDoThemeExtras.colors.textSecondary,
    content: @Composable ColumnScope.(selectedProgress: Float, contentColor: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tabMotion = NativeDoMotionTheme.scheme.tab
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tabMotion.tweenSpec(),
        label = "native_do_glass_tab_selected",
    )
    val pressedProgress by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tabMotion.tweenSpec(),
        label = "native_do_glass_tab_pressed",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> idleContentColor.copy(alpha = 0.42f)
            selected -> selectedContentColor
            else -> idleContentColor
        },
        animationSpec = tabMotion.tweenSpec(),
        label = "native_do_glass_tab_content",
    )
    val highlightAlpha = (0.18f * selectedProgress) + (0.08f * pressedProgress)
    val highlightScale = 0.88f + (selectedProgress * 0.12f) + (pressedProgress * 0.04f)
    val baseHighlightColor = glassSpec.containerColor.copy(
        alpha = min(glassSpec.containerColor.alpha, 0.18f) * pressedProgress,
    )
    val selectedHighlightColor = selectedContainerColor.copy(
        alpha = selectedContainerColor.alpha * highlightAlpha,
    )

    Box(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .graphicsLayer {
                    scaleX = highlightScale
                    scaleY = highlightScale
                }
                .clip(shape)
                .background(color = baseHighlightColor, shape = shape)
                .background(color = selectedHighlightColor, shape = shape),
        )
        Column(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = { content(selectedProgress, contentColor) },
        )
    }
}
