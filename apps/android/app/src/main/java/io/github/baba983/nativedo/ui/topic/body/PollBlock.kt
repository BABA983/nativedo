package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.CubicBezierInterpolator

internal class PollBlock(
    private val block: CookedPostBlock.Poll,
    private val context: PostBodyBuildContext,
    private val state: PostBodyBlockState? = null,
    private val stateIndex: Int = -1,
) : PostBodyBlock {
    private data class OptionLayout(
        val text: StaticLayout,
        val percent: StaticLayout,
        val rect: RectF,
        val percentValue: Float,
    )

    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.accentColor }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.pollBarColor }
    private val selectedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = org.telegram.ui.ActionBar.Theme.multAlpha(context.accentColor, 0.28f)
    }
    private val radioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(2f).toFloat()
    }
    private val radioFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = AndroidUtilities.dp(2f).toFloat()
        color = 0xffffffff.toInt()
    }
    private val titlePaint = TextPaint(context.paragraphPaint).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val statusPaint = TextPaint(context.quotePaint).apply {
        color = context.accentColor
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val optionTextPaint = TextPaint(context.quotePaint)
    private val footerPaint = TextPaint(context.quotePaint).apply {
        color = context.secondaryTextColor
        textSize = AndroidUtilities.dp(13f).toFloat()
    }
    private val checkPath = Path()
    private var titleLayout: StaticLayout? = null
    private var statusLayout: StaticLayout? = null
    private var optionLayouts: List<OptionLayout> = emptyList()
    private var footerLayout: StaticLayout? = null
    private var measuredHeight = 0
    private var contentWidth = 0
    private var selectedOption = state?.selectedPollOptionsByIndex?.get(stateIndex) ?: -1
    private var percentAnimations: List<AnimatedFloat> = emptyList()

    override fun measure(maxWidthPx: Int): Int {
        val width = maxWidthPx.coerceAtLeast(AndroidUtilities.dp(120f))
        val padding = AndroidUtilities.dp(12f)
        val spacing = AndroidUtilities.dp(8f)
        val textLeft = textLeft()
        contentWidth = (width - textLeft - AndroidUtilities.dp(18f)).coerceAtLeast(AndroidUtilities.dp(80f))
        val statusText = block.status?.takeIf { it.isNotBlank() }
        val statusWidth = statusText?.let { statusPaint.measureText(it).toInt() + AndroidUtilities.dp(8f) } ?: 0
        val titleWidth = (contentWidth - statusWidth).coerceAtLeast(AndroidUtilities.dp(60f))
        val title = block.name?.takeIf { it.isNotBlank() } ?: "投票"
        titleLayout = layout(title, titlePaint, titleWidth, maxLines = 2)
        statusLayout = statusText?.let { layout(it, statusPaint, statusWidth.coerceAtLeast(AndroidUtilities.dp(40f)), maxLines = 1) }
        var y = padding + maxOf(titleLayout?.height ?: 0, statusLayout?.height ?: 0) + spacing
        val options = block.options.ifEmpty { listOf("暂无选项") }
        val percents = optionPercents(options.size)
        if (percentAnimations.size != options.size) {
            percentAnimations = List(options.size) {
                AnimatedFloat(0f, context.host, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT)
            }
        }
        optionLayouts = options.mapIndexed { index, option ->
            val percent = percents[index]
            val text = layout(option, optionTextPaint, contentWidth - AndroidUtilities.dp(72f), maxLines = 3)
            val percentLayout = layout("${(percent * 100).toInt()}%", footerPaint, AndroidUtilities.dp(42f), maxLines = 1)
            val optionHeight = maxOf(text.height, AndroidUtilities.dp(24f)) + AndroidUtilities.dp(14f)
            OptionLayout(
                text = text,
                percent = percentLayout,
                rect = RectF(textLeft.toFloat(), y.toFloat(), (textLeft + contentWidth).toFloat(), (y + optionHeight).toFloat()),
                percentValue = percent,
            ).also {
                y += optionHeight + AndroidUtilities.dp(6f)
            }
        }
        val footer = buildString {
            append(block.voters?.let { "$it 票" } ?: "投票")
            block.type?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        }
        footerLayout = layout(footer, footerPaint, contentWidth, maxLines = 2)
        y += spacing - AndroidUtilities.dp(6f)
        y += footerLayout?.height ?: 0
        measuredHeight = y + padding
        return measuredHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val textLeft = textLeft()
        canvas.drawRect(
            AndroidUtilities.dp(18f).toFloat(),
            AndroidUtilities.dp(6f).toFloat(),
            AndroidUtilities.dp(20f).toFloat(),
            (measuredHeight - AndroidUtilities.dp(6f)).toFloat(),
            sidePaint,
        )
        val padding = AndroidUtilities.dp(12f)
        titleLayout?.let { layout ->
            canvas.save()
            canvas.translate(textLeft.toFloat(), padding.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
        statusLayout?.let { layout ->
            canvas.save()
            canvas.translate((textLeft + contentWidth - layout.width).toFloat(), padding.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
        optionLayouts.forEachIndexed { index, option ->
            val selected = index == selectedOption
            val radius = AndroidUtilities.dp(6f).toFloat()
            val bar = RectF(option.rect)
            val animatedPercent = percentAnimations.getOrNull(index)?.set(option.percentValue) ?: option.percentValue
            bar.right = bar.left + option.rect.width() * animatedPercent
            canvas.drawRoundRect(bar, radius, radius, if (selected) selectedBarPaint else barPaint)

            val cy = option.rect.centerY()
            val cx = option.rect.left + AndroidUtilities.dp(12f)
            val radioRadius = AndroidUtilities.dp(9f).toFloat()
            radioPaint.color = if (selected) context.accentColor else context.secondaryTextColor
            canvas.drawCircle(cx, cy, radioRadius, radioPaint)
            if (selected) {
                radioFillPaint.color = context.accentColor
                canvas.drawCircle(cx, cy, radioRadius - AndroidUtilities.dp(2f), radioFillPaint)
                checkPath.reset()
                checkPath.moveTo(cx - AndroidUtilities.dp(4f), cy)
                checkPath.lineTo(cx - AndroidUtilities.dp(1f), cy + AndroidUtilities.dp(3f))
                checkPath.lineTo(cx + AndroidUtilities.dp(5f), cy - AndroidUtilities.dp(4f))
                canvas.drawPath(checkPath, checkPaint)
            }
            canvas.save()
            canvas.translate(option.rect.left + AndroidUtilities.dp(30f), option.rect.top + AndroidUtilities.dp(7f))
            option.text.draw(canvas)
            canvas.restore()
            canvas.save()
            canvas.translate(option.rect.right - option.percent.width, option.rect.top + AndroidUtilities.dp(7f))
            option.percent.draw(canvas)
            canvas.restore()
        }
        footerLayout?.let { layout ->
            canvas.save()
            canvas.translate(textLeft.toFloat(), (measuredHeight - padding - layout.height).toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val index = optionLayouts.indexOfFirst { it.rect.contains(localX, localY) }
        if (index < 0) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            selectedOption = if (selectedOption == index) -1 else index
            if (selectedOption >= 0) {
                state?.selectedPollOptionsByIndex?.put(stateIndex, selectedOption)
            } else {
                state?.selectedPollOptionsByIndex?.remove(stateIndex)
            }
            context.host.invalidate()
        }
        return event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP
    }

    private fun optionPercents(optionCount: Int): List<Float> {
        if (optionCount <= 0) return emptyList()
        val selected = selectedOption.takeIf { it in 0 until optionCount }
        if (selected != null) {
            return List(optionCount) { index -> if (index == selected) 1f else 0f }
        }
        return List(optionCount) { 0f }
    }

    private fun textLeft(): Int = AndroidUtilities.dp(18f + 14f)

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }
}
