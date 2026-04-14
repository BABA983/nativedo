package io.github.baba983.nativedo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlin.math.min

private val PullRefreshIndicatorSize = 58.dp
private val PullRefreshGlowSize = 28.dp
private val PullRefreshHighlightWidth = 24.dp
private val PullRefreshHighlightHeight = 4.dp
private val PullRefreshContentSize = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDoPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val pullProgress = min(state.distanceFraction, 1f)
    val containerScale by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else 0.86f + (pullProgress * 0.14f),
        animationSpec = tween(durationMillis = 180),
        label = "pull_refresh_container_scale",
    )
    val arrowScale by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else 0.82f + (pullProgress * 0.18f),
        animationSpec = tween(durationMillis = 180),
        label = "pull_refresh_arrow_scale",
    )
    val glassSpec = if (isRefreshing || pullProgress > 0.92f) {
        NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome)
    } else {
        NativeDoThemeExtras.glass(NativeDoGlassRole.Card)
    }

    Box(
        modifier = modifier
            .pullToRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                threshold = PullToRefreshDefaults.PositionalThreshold,
                shape = NativeDoPillShape,
                containerColor = Color.Transparent,
                elevation = 0.dp,
            )
            .graphicsLayer {
                scaleX = containerScale
                scaleY = containerScale
            },
        contentAlignment = Alignment.Center,
    ) {
        NativeDoGlassSurface(
            modifier = Modifier.size(PullRefreshIndicatorSize),
            shape = NativeDoPillShape,
            glassSpec = glassSpec,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.glassBackgroundStrong.copy(alpha = 0.82f),
                                colors.glassBackground.copy(alpha = 0.64f),
                            ),
                        ),
                        shape = NativeDoPillShape,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 7.dp)
                        .size(PullRefreshHighlightWidth, PullRefreshHighlightHeight)
                        .clip(NativeDoPillShape)
                        .background(colors.glassStrokeStrong.copy(alpha = 0.58f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(PullRefreshGlowSize)
                        .clip(NativeDoPillShape)
                        .background(
                            colors.glowPrimary.copy(
                                alpha = if (isRefreshing) 0.26f else 0.12f + (pullProgress * 0.14f),
                            ),
                        ),
                )
                Crossfade(
                    targetState = isRefreshing,
                    animationSpec = tween(durationMillis = 180),
                    modifier = Modifier.align(Alignment.Center),
                    label = "pull_refresh_content",
                ) { refreshing ->
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(PullRefreshContentSize),
                            strokeWidth = 2.25.dp,
                            color = colors.accent,
                            trackColor = colors.accentMuted.copy(alpha = 0.34f),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = colors.accent.copy(alpha = 0.92f),
                            modifier = Modifier
                                .size(PullRefreshContentSize)
                                .graphicsLayer {
                                    scaleX = arrowScale
                                    scaleY = arrowScale
                                    alpha = 0.48f + (pullProgress * 0.52f)
                                },
                        )
                    }
                }
            }
        }
    }
}
