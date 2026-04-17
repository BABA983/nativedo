package io.github.baba983.nativedo.debug

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.TelegramAnimationSpec
import io.github.baba983.nativedo.ui.theme.TelegramEasing
import kotlinx.coroutines.launch

/**
 * Debug tool to compare animation parameters with Telegram
 *
 * Usage: Add this to your debug menu or development builds
 *
 * ```
 * if (BuildConfig.DEBUG) {
 *     TelegramAnimationDebugger()
 * }
 * ```
 */
@Composable
fun TelegramAnimationDebugger() {
    var isAnimating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Telegram 动画调试工具",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "动画参数对比",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 参数显示
        AnimationParameterRow("标准位移", "${TelegramAnimationSpec.STANDARD_SLIDE_OFFSET_DP}dp")
        AnimationParameterRow("标准时长", "${TelegramAnimationSpec.STANDARD_DURATION}ms")
        AnimationParameterRow("返回最大位移", "${TelegramAnimationSpec.PREDICTIVE_BACK_MAX_OFFSET_DP}dp")
        AnimationParameterRow("返回取消时长", "${TelegramAnimationSpec.PREDICTIVE_BACK_CANCEL_DURATION}ms")
        AnimationParameterRow("返回完成时长", "${TelegramAnimationSpec.PREDICTIVE_BACK_COMPLETE_DURATION}ms")

        Spacer(modifier = Modifier.height(24.dp))

        // 动画预览
        Text(
            text = "动画预览",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isAnimating = true
            },
            enabled = !isAnimating
        ) {
            Text("播放标准转场动画")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 动画演示区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            if (isAnimating) {
                StandardTransitionDemo(
                    onAnimationEnd = { isAnimating = false }
                )
            } else {
                Text(
                    text = "点击按钮查看动画",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 插值器对比
        Text(
            text = "插值器曲线对比",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        EasingComparisonChart()
    }
}

@Composable
private fun AnimationParameterRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StandardTransitionDemo(
    onAnimationEnd: () -> Unit
) {
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { TelegramAnimationSpec.STANDARD_SLIDE_OFFSET_DP.dp.toPx() }

    val alpha = remember { Animatable(0f) }
    val translationX = remember { Animatable(slideOffsetPx) }

    val animationSpec: AnimationSpec<Float> = tween(
        durationMillis = TelegramAnimationSpec.STANDARD_DURATION,
        easing = TelegramEasing.Decelerate
    )

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(1f, animationSpec)
        }
        launch {
            translationX.animateTo(0f, animationSpec)
            onAnimationEnd()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationX = translationX.value
            }
            .background(MaterialTheme.colorScheme.primary)
            .padding(16.dp)
    ) {
        Text(
            text = "新页面\n从 +48dp 滑入\n150ms 减速动画",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun EasingComparisonChart() {
    Column {
        EasingCurveRow("Decelerate", TelegramEasing.Decelerate)
        EasingCurveRow("EaseOutQuint", TelegramEasing.EaseOutQuint)
        EasingCurveRow("Overshoot", TelegramEasing.Overshoot)
    }
}

@Composable
private fun EasingCurveRow(name: String, easing: androidx.compose.animation.core.Easing) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall
        )

        // 简单的曲线可视化
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) {
            for (i in 0..20) {
                val input = i / 20f
                val output = easing.transform(input)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height((output * 40).dp)
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

/**
 * Performance monitor for navigation animations
 */
class NavigationAnimationMonitor {
    private val frameTimes = mutableListOf<Long>()
    private var lastFrameTime = 0L

    fun onFrame() {
        val currentTime = System.nanoTime()
        if (lastFrameTime != 0L) {
            val frameTime = (currentTime - lastFrameTime) / 1_000_000 // Convert to ms
            frameTimes.add(frameTime)
        }
        lastFrameTime = currentTime
    }

    fun getAverageFrameTime(): Float {
        return if (frameTimes.isEmpty()) 0f else frameTimes.average().toFloat()
    }

    fun getDroppedFrames(targetFrameTime: Float = 16.67f): Int {
        return frameTimes.count { it > targetFrameTime }
    }

    fun reset() {
        frameTimes.clear()
        lastFrameTime = 0L
    }

    fun getReport(): String {
        val avgFrameTime = getAverageFrameTime()
        val droppedFrames = getDroppedFrames()
        val totalFrames = frameTimes.size

        return """
            动画性能报告:
            - 平均帧时间: ${String.format("%.2f", avgFrameTime)}ms
            - 目标帧时间: 16.67ms (60 FPS)
            - 总帧数: $totalFrames
            - 掉帧数: $droppedFrames
            - 掉帧率: ${String.format("%.1f", droppedFrames * 100f / totalFrames)}%

            ${if (avgFrameTime <= 16.67f) "✅ 性能良好" else "⚠️ 性能需要优化"}
        """.trimIndent()
    }
}
