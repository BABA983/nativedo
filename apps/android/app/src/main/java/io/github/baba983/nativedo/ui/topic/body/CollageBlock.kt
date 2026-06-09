/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockCollageCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import kotlin.math.roundToInt

internal class CollageBlock(
    private val block: CookedPostBlock.Collage,
    private val gallery: List<GalleryImage>,
    private val context: PostBodyBuildContext,
) : PostBodyBlock {
    private data class Tile(
        val image: CookedPostBlock.Image,
        val imageUrl: String,
        val targetUrl: String,
        val receiver: ImageReceiver,
        val rect: RectF = RectF(),
    )

    private val spacing = AndroidUtilities.dp(4f)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.mediaPlaceholderColor }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.pressedOverlayColor }
    private val tiles = block.images.map { image ->
        val imageUrl = resolvePostImageUrl(context.baseUrl, image.src)
        val targetUrl = image.href?.let { resolvePostImageUrl(context.baseUrl, it) } ?: imageUrl
        val receiver = context.imagePool.obtain("collage:$targetUrl").apply {
            setRoundRadius(AndroidUtilities.dp(8f))
        }
        context.imagePool.setImage(receiver, imageUrl)
        Tile(image = image, imageUrl = imageUrl, targetUrl = targetUrl, receiver = receiver)
    }
    private var measuredHeight = 0
    private var pressedTileIndex = -1

    override fun measure(maxWidthPx: Int): Int {
        if (tiles.isEmpty()) {
            measuredHeight = 0
            return measuredHeight
        }
        val availableWidth = maxWidthPx.coerceAtLeast(AndroidUtilities.dp(120f))
        measuredHeight = when (tiles.size) {
            1 -> layoutSingle(availableWidth)
            2 -> layoutTwo(availableWidth)
            3 -> layoutThree(availableWidth)
            4 -> layoutFour(availableWidth)
            else -> layoutRows(availableWidth)
        }
        return measuredHeight
    }

    private fun layoutSingle(availableWidth: Int): Int {
        val height = (availableWidth / tiles[0].aspectRatio()).roundToInt()
            .coerceIn(AndroidUtilities.dp(140f), AndroidUtilities.dp(420f))
        tiles[0].rect.set(0f, 0f, availableWidth.toFloat(), height.toFloat())
        return height
    }

    private fun layoutTwo(availableWidth: Int): Int {
        val firstAspect = tiles[0].aspectRatio()
        val secondAspect = tiles[1].aspectRatio()
        val height = ((availableWidth - spacing) / (firstAspect + secondAspect)).roundToInt()
            .coerceIn(AndroidUtilities.dp(120f), AndroidUtilities.dp(260f))
        val firstWidth = (height * firstAspect).roundToInt().coerceIn(AndroidUtilities.dp(80f), availableWidth - spacing - AndroidUtilities.dp(80f))
        val secondWidth = availableWidth - spacing - firstWidth
        tiles[0].rect.set(0f, 0f, firstWidth.toFloat(), height.toFloat())
        tiles[1].rect.set((firstWidth + spacing).toFloat(), 0f, availableWidth.toFloat(), height.toFloat())
        return height
    }

    private fun layoutThree(availableWidth: Int): Int {
        val firstAspect = tiles[0].aspectRatio()
        return if (firstAspect < 0.85f) {
            val height = AndroidUtilities.dp(260f)
            val leftWidth = (availableWidth * 0.48f).roundToInt()
            val rightWidth = availableWidth - spacing - leftWidth
            val halfHeight = (height - spacing) / 2
            tiles[0].rect.set(0f, 0f, leftWidth.toFloat(), height.toFloat())
            tiles[1].rect.set((leftWidth + spacing).toFloat(), 0f, availableWidth.toFloat(), halfHeight.toFloat())
            tiles[2].rect.set((leftWidth + spacing).toFloat(), (halfHeight + spacing).toFloat(), availableWidth.toFloat(), height.toFloat())
            height
        } else {
            val topHeight = (availableWidth / firstAspect).roundToInt().coerceIn(AndroidUtilities.dp(130f), AndroidUtilities.dp(220f))
            val bottomHeight = AndroidUtilities.dp(120f)
            val halfWidth = (availableWidth - spacing) / 2
            tiles[0].rect.set(0f, 0f, availableWidth.toFloat(), topHeight.toFloat())
            tiles[1].rect.set(0f, (topHeight + spacing).toFloat(), halfWidth.toFloat(), (topHeight + spacing + bottomHeight).toFloat())
            tiles[2].rect.set((halfWidth + spacing).toFloat(), (topHeight + spacing).toFloat(), availableWidth.toFloat(), (topHeight + spacing + bottomHeight).toFloat())
            topHeight + spacing + bottomHeight
        }
    }

    private fun layoutFour(availableWidth: Int): Int {
        val tileWidth = (availableWidth - spacing) / 2
        val tileHeight = tileWidth.coerceIn(AndroidUtilities.dp(96f), AndroidUtilities.dp(190f))
        tiles.forEachIndexed { index, tile ->
            val row = index / 2
            val col = index % 2
            val left = col * (tileWidth + spacing)
            val top = row * (tileHeight + spacing)
            val right = if (col == 1) availableWidth else left + tileWidth
            tile.rect.set(left.toFloat(), top.toFloat(), right.toFloat(), (top + tileHeight).toFloat())
        }
        return tileHeight * 2 + spacing
    }

    private fun layoutRows(availableWidth: Int): Int {
        val targetRowHeight = AndroidUtilities.dp(180f)
        val minRowHeight = AndroidUtilities.dp(96f)
        val maxRowHeight = AndroidUtilities.dp(260f)
        var index = 0
        var y = 0
        while (index < tiles.size) {
            var rowAspectSum = 0f
            var rowEnd = index
            while (rowEnd < tiles.size && rowEnd - index < MAX_TILES_PER_ROW) {
                rowAspectSum += tiles[rowEnd].aspectRatio()
                rowEnd++
                val projectedHeight = ((availableWidth - spacing * (rowEnd - index - 1)) / rowAspectSum).roundToInt()
                if (projectedHeight <= targetRowHeight || rowEnd == tiles.size) break
            }
            val rowCount = rowEnd - index
            val rowWidth = availableWidth - spacing * (rowCount - 1)
            val rowHeight = (rowWidth / rowAspectSum).roundToInt().coerceIn(minRowHeight, maxRowHeight)
            var x = 0
            var remainingWidth = rowWidth
            for (tileIndex in index until rowEnd) {
                val isLast = tileIndex == rowEnd - 1
                val tileWidth = if (isLast) remainingWidth else (rowHeight * tiles[tileIndex].aspectRatio()).roundToInt().coerceAtLeast(AndroidUtilities.dp(64f))
                tiles[tileIndex].rect.set(x.toFloat(), y.toFloat(), (x + tileWidth).toFloat(), (y + rowHeight).toFloat())
                x += tileWidth + spacing
                remainingWidth -= tileWidth
            }
            y += rowHeight
            index = rowEnd
            if (index < tiles.size) y += spacing
        }
        return y
    }

    override fun draw(canvas: Canvas, parentWidth: Int) {
        tiles.forEachIndexed { index, tile ->
            val hidden = context.hiddenImageUrlProvider() == tile.targetUrl
            if (!hidden) {
                canvas.drawRoundRect(tile.rect, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat(), placeholderPaint)
                tile.receiver.setBounds(tile.rect)
                tile.receiver.draw(canvas)
            }
            if (index == pressedTileIndex && !hidden) {
                canvas.drawRoundRect(tile.rect, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat(), pressedPaint)
            }
        }
    }

    override fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val index = tiles.indexOfFirst { it.rect.contains(localX, localY) }
        if (index < 0) {
            if (pressedTileIndex != -1 && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP)) {
                pressedTileIndex = -1
                context.host.invalidate()
            }
            return false
        }
        val tile = tiles[index]
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pressedTileIndex = index
            context.host.invalidate()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            pressedTileIndex = -1
            context.host.invalidate()
            context.delegate.onImageClick(
                url = tile.targetUrl,
                gallery = gallery,
                sourceBoundsInBlock = RectF(tile.rect),
                hostView = context.host,
                sourceBoundsProvider = { imageUrl -> tileBoundsInCell(imageUrl) },
                sourceImageReceiverProvider = { imageUrl -> tiles.firstOrNull { it.targetUrl == imageUrl }?.receiver },
                sourceBitmapProvider = { imageUrl -> tiles.firstOrNull { it.targetUrl == imageUrl }?.receiver?.getBitmapSafe() },
                sourceRadiusProvider = { imageUrl -> tiles.firstOrNull { it.targetUrl == imageUrl }?.receiver?.getRoundRadius(true)?.copyOf() },
            )
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pressedTileIndex = -1
            context.host.invalidate()
            return true
        }
        return pressedTileIndex == index
    }

    override fun onAttachedToWindow() {
        tiles.forEach { it.receiver.onAttachedToWindow() }
    }

    override fun onDetachedFromWindow() {
        tiles.forEach { it.receiver.onDetachedFromWindow() }
    }

    fun imageBoundsInCell(index: Int): RectF? = tiles.getOrNull(index)?.rect?.let(::RectF)

    override fun imageBoundsInBlock(imageUrl: String): RectF? {
        return tileBoundsInCell(imageUrl)
    }

    override fun imageReceiverForUrl(imageUrl: String): ImageReceiver? {
        return tiles.firstOrNull { it.targetUrl == imageUrl }?.receiver
    }

    private fun tileBoundsInCell(imageUrl: String): RectF? {
        return tiles.firstOrNull { tile -> tile.targetUrl == imageUrl }?.rect?.let(::RectF)
    }

    private fun Tile.aspectRatio(): Float = image.aspectRatio?.coerceIn(0.45f, 2.4f) ?: 1f

    private companion object {
        private const val MAX_TILES_PER_ROW = 3
    }
}
