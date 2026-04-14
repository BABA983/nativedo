package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec
import io.github.baba983.nativedo.ui.theme.NativeDoMotionTheme
import io.github.baba983.nativedo.ui.theme.NativeDoExtendedColors
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlin.math.max
import kotlin.math.min

@Immutable
internal data class NativeDoPopupPalette(
    val text: Color,
    val secondaryText: Color,
    val icon: Color,
    val pressed: Color,
    val iconContainer: Color,
)

@Composable
internal fun rememberNativeDoPopupPalette(
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup),
): NativeDoPopupPalette {
    val colors = NativeDoThemeExtras.colors
    return remember(colors, glassSpec) {
        nativeDoPopupPalette(
            colors = colors,
            glassSpec = glassSpec,
        )
    }
}

internal fun nativeDoPopupPalette(
    colors: NativeDoExtendedColors,
    glassSpec: NativeDoGlassSpec,
): NativeDoPopupPalette {
    val isLightPopup = glassSpec.containerColor.luminance() > 0.5f
    return NativeDoPopupPalette(
        text = colors.textPrimary.copy(alpha = if (isLightPopup) 0.97f else 0.98f),
        secondaryText = colors.textSecondary.copy(alpha = 0.92f),
        icon = colors.textPrimary.copy(alpha = 0.90f),
        pressed = colors.textPrimary.copy(alpha = if (isLightPopup) 0.08f else 0.12f),
        iconContainer = if (glassSpec.highlightColor.alpha > 0f) {
            glassSpec.highlightColor.copy(alpha = if (isLightPopup) 0.14f else 0.20f)
        } else {
            colors.textPrimary.copy(alpha = if (isLightPopup) 0.10f else 0.14f)
        },
    )
}

@Composable
fun NativeDoPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    itemCount: Int = 0,
    margin: Dp = 12.dp,
    spacing: Dp = 10.dp,
    closeTranslationY: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup),
    popupPositionProvider: PopupPositionProvider = rememberNativeDoPopupPositionProvider(
        margin = margin,
        spacing = spacing,
    ),
    properties: PopupProperties = PopupProperties(
        focusable = true,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        clippingEnabled = false,
    ),
    content: @Composable ColumnScope.(popupProgress: Float) -> Unit,
) {
    val popupMotion = NativeDoMotionTheme.scheme.popup
    val density = LocalDensity.current
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }

    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    val openDuration = remember(itemCount, popupMotion) {
        nativeDoPopupOpenDuration(
            itemCount = itemCount,
            baseDuration = popupMotion.open.durationMillis,
            cascadeStepMillis = popupMotion.cascadeStepMillis,
        )
    }
    val transition = updateTransition(
        transitionState = transitionState,
        label = "native_do_popup_menu_transition",
    )
    val popupProgress by transition.animateFloat(
        transitionSpec = {
            androidx.compose.animation.core.tween(
                durationMillis = if (targetState) openDuration else popupMotion.close.durationMillis,
                easing = if (targetState) popupMotion.open.easing else popupMotion.close.easing,
            )
        },
        label = "native_do_popup_menu_progress",
    ) { visible ->
        if (visible) 1f else 0f
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Surface(
            modifier = modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .graphicsLayer {
                    alpha = popupProgress
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .clip(shape),
            shape = shape,
            color = glassSpec.containerColor,
            border = BorderStroke(
                width = 1.dp,
                color = glassSpec.borderColor,
            ),
            tonalElevation = glassSpec.tonalElevation,
            shadowElevation = glassSpec.shadowElevation,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
            ) {
                content(popupProgress)
            }
        }
    }
}

@Composable
fun NativeDoAnchoredPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    itemCount: Int = 0,
    margin: Dp = 12.dp,
    spacing: Dp = 10.dp,
    closeTranslationY: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup),
    popupPositionProvider: PopupPositionProvider = rememberNativeDoPopupPositionProvider(
        margin = margin,
        spacing = spacing,
        alignToAnchorTop = true,
    ),
    properties: PopupProperties = PopupProperties(
        focusable = true,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        clippingEnabled = false,
    ),
    content: @Composable ColumnScope.(popupProgress: Float) -> Unit,
) {
    NativeDoPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        itemCount = itemCount,
        margin = margin,
        spacing = spacing,
        closeTranslationY = closeTranslationY,
        shape = shape,
        contentPadding = contentPadding,
        glassSpec = glassSpec,
        popupPositionProvider = popupPositionProvider,
        properties = properties,
        content = content,
    )
}

@Composable
fun NativeDoContextualOptions(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    itemCount: Int = 0,
    margin: Dp = 12.dp,
    spacing: Dp = 10.dp,
    closeTranslationY: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(24.dp),
    surfaceTopInset: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(vertical = 10.dp),
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup),
    popupPositionProvider: PopupPositionProvider = rememberNativeDoPopupPositionProvider(
        margin = margin,
        spacing = spacing,
        alignToAnchorTop = true,
        horizontalOffset = 4.dp,
        verticalOffset = (-12).dp,
    ),
    properties: PopupProperties = PopupProperties(
        focusable = true,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        clippingEnabled = false,
    ),
    content: @Composable ColumnScope.(popupProgress: Float) -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }

    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    val transition = updateTransition(
        transitionState = transitionState,
        label = "native_do_contextual_options_transition",
    )
    val popupMotion = NativeDoMotionTheme.scheme.popup
    val openDuration = remember(itemCount, popupMotion) {
        nativeDoPopupOpenDuration(
            itemCount = itemCount,
            baseDuration = popupMotion.open.durationMillis,
            cascadeStepMillis = popupMotion.cascadeStepMillis,
        )
    }
    val closeTranslationPx = with(LocalDensity.current) { closeTranslationY.toPx() }
    val popupAlpha by transition.animateFloat(
        transitionSpec = {
            androidx.compose.animation.core.tween(
                durationMillis = if (targetState) popupMotion.open.durationMillis else popupMotion.close.durationMillis,
                easing = if (targetState) popupMotion.open.easing else popupMotion.close.easing,
            )
        },
        label = "native_do_contextual_options_popup_alpha",
    ) { visible ->
        if (visible) 1f else 0f
    }
    val containerScale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                spring(
                    dampingRatio = 0.72f,
                    stiffness = 560f,
                )
            } else {
                androidx.compose.animation.core.tween(
                    durationMillis = popupMotion.close.durationMillis,
                    easing = popupMotion.close.easing,
                )
            }
        },
        label = "native_do_contextual_options_popup_scale",
    ) { visible ->
        if (visible) 1f else 0.95f
    }
    val popupProgress by transition.animateFloat(
        transitionSpec = {
            androidx.compose.animation.core.tween(
                durationMillis = if (targetState) openDuration else popupMotion.close.durationMillis,
                easing = if (targetState) popupMotion.open.easing else popupMotion.close.easing,
            )
        },
        label = "native_do_contextual_options_reveal_progress",
    ) { visible ->
        if (visible) 1f else 0f
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Box(
            modifier = modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .graphicsLayer {
                    alpha = popupAlpha
                    translationY = -closeTranslationPx * (1f - popupProgress) * 0.28f
                    scaleX = containerScale
                    scaleY = containerScale
                    transformOrigin = TransformOrigin(1f, 1f)
                }
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = surfaceTopInset)
                    .clip(shape),
                shape = shape,
                color = glassSpec.containerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = glassSpec.borderColor.copy(
                        alpha = max(glassSpec.borderColor.alpha, 0.12f),
                    ),
                ),
                tonalElevation = glassSpec.tonalElevation,
                shadowElevation = glassSpec.shadowElevation,
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                ) {
                    content(popupProgress)
                }
            }
        }
    }
}

@Composable
internal fun NativeDoPopupMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    subtitle: String? = null,
    index: Int = 0,
    itemCount: Int = 1,
    popupProgress: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup),
    paletteOverride: NativeDoPopupPalette? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val palette = paletteOverride ?: rememberNativeDoPopupPalette(glassSpec = glassSpec)
    val rowProgress = remember(index, itemCount, popupProgress) {
        nativeDoPopupCascadeProgress(
            fullAnimationT = popupProgress,
            position = index.toFloat(),
            count = itemCount.toFloat(),
            waveLength = 4f,
        )
    }
    val rowAlpha = rowProgress * if (enabled) 1f else 0.42f
    val rowTranslationY = with(density) { 10.dp.toPx() } * (1f - rowProgress) * -1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rowAlpha
                translationY = rowTranslationY
            }
            .clip(RoundedCornerShape(14.dp))
            .background(
                color = if (pressed && enabled) {
                    palette.pressed
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) palette.icon else palette.icon.copy(alpha = 0.42f),
                modifier = Modifier.size(19.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) palette.text else palette.text.copy(alpha = 0.42f),
            )
            subtitle?.let { resolvedSubtitle ->
                Text(
                    text = resolvedSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) {
                        palette.secondaryText
                    } else {
                        palette.secondaryText.copy(alpha = 0.42f)
                    },
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun NativeDoPopupMenuDivider(
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    gapHeight: Dp = 6.dp,
    color: Color = NativeDoThemeExtras.colors.backgroundElevated.copy(alpha = 0.88f),
) {
    Spacer(modifier = Modifier.height(spacing))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(gapHeight)
            .background(color),
    )
    Spacer(modifier = Modifier.height(spacing))
}

@Composable
fun NativeDoPopupAnchorHighlight(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome),
) {
    val anchorMotion = NativeDoMotionTheme.scheme.anchor
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) anchorMotion.enter.tweenSpec() else anchorMotion.exit.tweenSpec(),
        label = "native_do_popup_anchor_highlight_alpha",
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = if (visible) anchorMotion.enter.tweenSpec() else anchorMotion.exit.tweenSpec(),
        label = "native_do_popup_anchor_highlight_scale",
    )

    if (alpha <= 0f) {
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                color = glassSpec.containerColor.copy(alpha = max(glassSpec.containerColor.alpha, 0.94f)),
                shape = shape,
            ),
    )
}

@Composable
fun rememberNativeDoPopupPositionProvider(
    margin: Dp = 12.dp,
    spacing: Dp = 10.dp,
    alignToAnchorTop: Boolean = false,
    horizontalOffset: Dp = 0.dp,
    verticalOffset: Dp = 0.dp,
): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density, margin, spacing, alignToAnchorTop, horizontalOffset, verticalOffset) {
        NativeDoPopupPositionProvider(
            margin = with(density) { margin.roundToPx() },
            spacing = with(density) { spacing.roundToPx() },
            alignToAnchorTop = alignToAnchorTop,
            horizontalOffset = with(density) { horizontalOffset.roundToPx() },
            verticalOffset = with(density) { verticalOffset.roundToPx() },
        )
    }
}

fun nativeDoPopupOpenDuration(
    itemCount: Int,
    baseDuration: Int,
    cascadeStepMillis: Int,
): Int = baseDuration + (cascadeStepMillis * itemCount.coerceAtLeast(0))

fun nativeDoPopupCascadeProgress(
    fullAnimationT: Float,
    position: Float,
    count: Float,
    waveLength: Float,
): Float {
    if (count <= 0f) {
        return fullAnimationT
    }
    val waveDuration = (1f / count) * min(waveLength, count)
    val waveOffset = (position / count) * (1f - waveDuration)
    return ((fullAnimationT - waveOffset) / waveDuration).coerceIn(0f, 1f)
}

fun calculateNativeDoPopupOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
    margin: Int,
    spacing: Int,
    alignToAnchorTop: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = 0,
): IntOffset {
    val maxX = (windowSize.width - popupSize.width - margin).coerceAtLeast(margin)
    val maxY = (windowSize.height - popupSize.height - margin).coerceAtLeast(margin)
    val preferredX = anchorBounds.right - popupSize.width + horizontalOffset
    val preferredY = if (alignToAnchorTop) {
        anchorBounds.top + verticalOffset
    } else {
        anchorBounds.bottom + spacing + verticalOffset
    }
    return IntOffset(
        x = preferredX.coerceIn(margin, maxX),
        y = preferredY.coerceIn(margin, maxY),
    )
}

private class NativeDoPopupPositionProvider(
    private val margin: Int,
    private val spacing: Int,
    private val alignToAnchorTop: Boolean,
    private val horizontalOffset: Int,
    private val verticalOffset: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        return calculateNativeDoPopupOffset(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            popupSize = popupContentSize,
            margin = margin,
            spacing = spacing,
            alignToAnchorTop = alignToAnchorTop,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
        )
    }
}
