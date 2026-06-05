/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ChatMessageCell link-preview
 * measurement, drawing, and hit-testing patterns.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver

internal class OneboxBlock(
    private val block: CookedPostBlock.Onebox,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0F000000 }
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.accentColor }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12000000 }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = org.telegram.ui.ActionBar.Theme.multAlpha(context.accentColor, 0.12f) }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.mediaPlaceholderColor }
    private val resolvedHref = runCatching { resolvePostImageUrl(context.baseUrl, block.href) }.getOrDefault(block.href)
    private val siteIconUrl = block.siteIconUrl?.let { resolvePostImageUrl(context.baseUrl, it) }
    private val siteIconReceiver: ImageReceiver? = siteIconUrl?.let { url ->
        context.imagePool.obtain("onebox-icon:$resolvedHref:$url").apply {
            setRoundRadius(AndroidUtilities.dp(4f))
            context.imagePool.setImage(this, url)
        }
    }
    private val thumbnailUrl = block.thumbnailUrl?.let { resolvePostImageUrl(context.baseUrl, it) }
    private val thumbnailReceiver: ImageReceiver? = thumbnailUrl?.let { url ->
        context.imagePool.obtain("onebox:$resolvedHref:$url").apply {
            setRoundRadius(AndroidUtilities.dp(8f))
            context.imagePool.setImage(this, url)
        }
    }
    private val domainPaint = textPaint(context.secondaryTextColor, 13f)
    private val titlePaint = textPaint(context.textColor, 16f)
    private val descriptionPaint = textPaint(context.secondaryTextColor, 14f)
    private val buttonTextPaint = textPaint(context.accentColor, 14f)
    private var previewLayout: OneboxPreviewLayout? = null
    private var pressedTarget: OneboxHitTarget? = null

    private data class OneboxPreviewLayout(
        val cardBounds: RectF,
        val thumbnailBounds: RectF?,
        val domainLayout: PositionedLayout?,
        val titleLayout: PositionedLayout,
        val descriptionLayout: PositionedLayout?,
        val openButtonLayout: PositionedLayout,
        val openButtonBounds: RectF,
        val totalHeight: Int,
    ) {
        fun hitTarget(x: Float, y: Float): OneboxHitTarget? {
            if (openButtonBounds.contains(x, y)) return OneboxHitTarget.OpenButton
            if (thumbnailBounds?.contains(x, y) == true) return OneboxHitTarget.Thumbnail
            if (descriptionLayout?.bounds?.contains(x, y) == true) return OneboxHitTarget.Description
            if (titleLayout.bounds.contains(x, y) || domainLayout?.bounds?.contains(x, y) == true || cardBounds.contains(x, y)) {
                return OneboxHitTarget.Card
            }
            return null
        }
    }

    private data class PositionedLayout(
        val layout: StaticLayout,
        val x: Float,
        val y: Float,
    ) {
        val bounds: RectF = RectF(x, y, x + layout.width, y + layout.height)
    }

    private enum class OneboxHitTarget { Card, Thumbnail, Description, OpenButton }

    override fun measure(maxWidthPx: Int): Int {
        val padding = AndroidUtilities.dp(12f)
        val textLeft = padding + AndroidUtilities.dp(4f)
        val thumbSize = if (thumbnailReceiver != null) AndroidUtilities.dp(72f) else 0
        val iconInset = if (siteIconReceiver != null) AndroidUtilities.dp(20f) else 0
        val textWidth = (maxWidthPx - padding * 2 - AndroidUtilities.dp(4f) - iconInset - if (thumbSize > 0) thumbSize + AndroidUtilities.dp(10f) else 0)
            .coerceAtLeast(AndroidUtilities.dp(120f))
        val domainLayout = block.domain?.takeIf { it.isNotBlank() }?.let { text ->
            buildLayout(text, domainPaint, textWidth, maxLines = 1)
        }
        val titleLayout = buildLayout(block.title, titlePaint, textWidth, maxLines = 2)
        val descriptionLayout = block.description?.takeIf { it.isNotBlank() }?.let { text ->
            buildLayout(text, descriptionPaint, textWidth, maxLines = 3)
        }
        val openButtonText = context.host.context.getString(R.string.app_browser_open_in_browser)
        val openButtonTextWidth = (buttonTextPaint.measureText(openButtonText) + 1f).toInt().coerceIn(1, textWidth)
        val openButtonLayout = buildLayout(openButtonText, buttonTextPaint, openButtonTextWidth, maxLines = 1)

        var y = padding
        val textX = textLeft + iconInset
        val positionedDomain = domainLayout?.let { layout ->
            PositionedLayout(layout, textX.toFloat(), y.toFloat()).also {
                y += layout.height + AndroidUtilities.dp(3f)
            }
        }
        val positionedTitle = PositionedLayout(titleLayout, textX.toFloat(), y.toFloat()).also {
            y += titleLayout.height + AndroidUtilities.dp(4f)
        }
        val positionedDescription = descriptionLayout?.let { layout ->
            PositionedLayout(layout, textX.toFloat(), y.toFloat()).also {
                y += layout.height + AndroidUtilities.dp(10f)
            }
        }
        val openButtonHeight = AndroidUtilities.dp(34f)
        val openButtonBounds = RectF(
            textX.toFloat(),
            y.toFloat(),
            (textX + openButtonLayout.width + AndroidUtilities.dp(24f)).coerceAtMost(maxWidthPx - padding).toFloat(),
            (y + openButtonHeight).toFloat(),
        )
        val positionedButton = PositionedLayout(
            openButtonLayout,
            openButtonBounds.left + AndroidUtilities.dp(12f),
            openButtonBounds.top + ((openButtonHeight - openButtonLayout.height) / 2f),
        )
        y += openButtonHeight

        val totalHeight = maxOf(y + padding, if (thumbSize > 0) thumbSize + padding * 2 else 0)
        val thumbnailBounds = if (thumbSize > 0) {
            RectF(
                (maxWidthPx - padding - thumbSize).toFloat(),
                padding.toFloat(),
                (maxWidthPx - padding).toFloat(),
                (padding + thumbSize).toFloat(),
            )
        } else {
            null
        }
        previewLayout = OneboxPreviewLayout(
            cardBounds = RectF(0f, 0f, maxWidthPx.toFloat(), totalHeight.toFloat()),
            thumbnailBounds = thumbnailBounds,
            domainLayout = positionedDomain,
            titleLayout = positionedTitle,
            descriptionLayout = positionedDescription,
            openButtonLayout = positionedButton,
            openButtonBounds = openButtonBounds,
            totalHeight = totalHeight,
        )
        return totalHeight
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        val layout = previewLayout ?: return
        val radius = AndroidUtilities.dp(10f).toFloat()
        canvas.drawRoundRect(layout.cardBounds, radius, radius, backgroundPaint)
        canvas.drawRoundRect(0f, 0f, AndroidUtilities.dp(3f).toFloat(), layout.totalHeight.toFloat(), radius, radius, sidePaint)
        pressedTarget?.let { canvas.drawRoundRect(layout.cardBounds, radius, radius, pressedPaint) }
        siteIconReceiver?.let { receiver ->
            val iconSize = AndroidUtilities.dp(16f).toFloat()
            receiver.setImageCoords(AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(12f).toFloat(), iconSize, iconSize)
            receiver.draw(canvas)
        }
        thumbnailReceiver?.let { receiver ->
            layout.thumbnailBounds?.let { bounds ->
                canvas.drawRoundRect(bounds, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat(), placeholderPaint)
                receiver.setBounds(bounds)
            }
            receiver.draw(canvas)
        }
        layout.domainLayout?.draw(canvas)
        layout.titleLayout.draw(canvas)
        layout.descriptionLayout?.draw(canvas)
        canvas.drawRoundRect(layout.openButtonBounds, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), buttonPaint)
        layout.openButtonLayout.draw(canvas)
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val layout = previewLayout ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedTarget = layout.hitTarget(localX, localY) ?: return false
                return true
            }
            MotionEvent.ACTION_UP -> {
                val target = pressedTarget ?: return false
                pressedTarget = null
                if (layout.hitTarget(localX, localY) != target) return false
                when (target) {
                    OneboxHitTarget.Thumbnail -> context.delegate.onImageClick(
                        url = thumbnailUrl ?: resolvedHref,
                        gallery = thumbnailUrl?.let { listOf(GalleryImage(url = it, thumbUrl = it)) }.orEmpty(),
                        sourceBoundsInWindow = layout.thumbnailBounds?.let(::RectF),
                        hostView = context.host,
                    )
                    OneboxHitTarget.Card,
                    OneboxHitTarget.Description,
                    OneboxHitTarget.OpenButton -> context.delegate.onLinkClick(resolvedHref)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedTarget = null
                return true
            }
        }
        return pressedTarget != null
    }

    override fun onAttachedToWindow() {
        siteIconReceiver?.onAttachedToWindow()
        thumbnailReceiver?.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        siteIconReceiver?.onDetachedFromWindow()
        thumbnailReceiver?.onDetachedFromWindow()
    }

    private fun textPaint(color: Int, sizeSp: Float): TextPaint {
        return TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = AndroidUtilities.dp(sizeSp).toFloat()
        }
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout {
        val source = text.take(360)
        return StaticLayout.Builder.obtain(source, 0, source.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    private fun PositionedLayout.draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}
