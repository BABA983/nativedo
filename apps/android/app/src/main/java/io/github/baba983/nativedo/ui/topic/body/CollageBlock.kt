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
import kotlin.math.max
import kotlin.math.min
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
    private val tiles = block.images.map { image ->
        val imageUrl = resolvePostImageUrl(context.baseUrl, image.src)
        val targetUrl = image.href?.let { resolvePostImageUrl(context.baseUrl, it) } ?: imageUrl
        val receiver = context.imagePool.obtain("collage:$targetUrl").apply {
            setRoundRadius(AndroidUtilities.dp(8f))
        }
        context.imagePool.setImage(receiver, imageUrl)
        Tile(
            image = image,
            imageUrl = imageUrl,
            targetUrl = targetUrl,
            receiver = receiver,
        )
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
        val maxHeight = availableWidth * MAX_SIZE_HEIGHT / MAX_SIZE_WIDTH
        val averageAspectRatio = (1f + firstAspect + secondAspect) / 2f
        val maxAspectRatio = MAX_SIZE_WIDTH / MAX_SIZE_HEIGHT
        val proportions = aspectKind(firstAspect).toString() + aspectKind(secondAspect)
        return if (proportions == "ww" && averageAspectRatio > 1.4f * maxAspectRatio && firstAspect - secondAspect < 0.2f) {
            val height = min(availableWidth / firstAspect, min(availableWidth / secondAspect, maxHeight / 2f)).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(96f))
            tiles[0].rect.set(0f, 0f, availableWidth.toFloat(), height.toFloat())
            tiles[1].rect.set(0f, (height + spacing).toFloat(), availableWidth.toFloat(), (height * 2 + spacing).toFloat())
            height * 2 + spacing
        } else if (proportions == "ww" || proportions == "qq") {
            val width = (availableWidth - spacing) / 2
            val height = min(width / firstAspect, min(width / secondAspect, maxHeight)).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(96f))
            tiles[0].rect.set(0f, 0f, width.toFloat(), height.toFloat())
            tiles[1].rect.set((width + spacing).toFloat(), 0f, availableWidth.toFloat(), height.toFloat())
            height
        } else {
            val secondWidth = max(0.4f * availableWidth, (availableWidth / firstAspect / (1f / firstAspect + 1f / secondAspect)).roundToInt().toFloat()).roundToInt()
                .coerceIn(AndroidUtilities.dp(80f), availableWidth - spacing - AndroidUtilities.dp(80f))
            val firstWidth = availableWidth - spacing - secondWidth
            val height = min(maxHeight, min(firstWidth / firstAspect, secondWidth / secondAspect)).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(96f))
            tiles[0].rect.set(0f, 0f, firstWidth.toFloat(), height.toFloat())
            tiles[1].rect.set((firstWidth + spacing).toFloat(), 0f, availableWidth.toFloat(), height.toFloat())
            height
        }
    }

    private fun layoutThree(availableWidth: Int): Int {
        val firstAspect = tiles[0].aspectRatio()
        val secondAspect = tiles[1].aspectRatio()
        val thirdAspect = tiles[2].aspectRatio()
        val maxHeight = availableWidth * MAX_SIZE_HEIGHT / MAX_SIZE_WIDTH
        return if (firstAspect < 0.85f) {
            val thirdHeight = min(maxHeight * 0.5f, (thirdAspect * availableWidth / (thirdAspect + secondAspect)).roundToInt().toFloat())
            val secondHeight = maxHeight - thirdHeight
            val rightWidth = max(AndroidUtilities.dp(80f).toFloat(), min(availableWidth * 0.5f, min(thirdHeight * thirdAspect, secondHeight * secondAspect).roundToInt().toFloat())).roundToInt()
            val leftWidth = min(maxHeight * firstAspect + AndroidUtilities.dp(40f), (availableWidth - rightWidth - spacing).toFloat()).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(80f))
            val height = maxHeight.roundToInt()
            val topRightHeight = secondHeight.roundToInt()
            tiles[0].rect.set(0f, 0f, leftWidth.toFloat(), height.toFloat())
            tiles[1].rect.set((leftWidth + spacing).toFloat(), 0f, availableWidth.toFloat(), topRightHeight.toFloat())
            tiles[2].rect.set((leftWidth + spacing).toFloat(), (topRightHeight + spacing).toFloat(), availableWidth.toFloat(), height.toFloat())
            height
        } else {
            val topHeight = min(availableWidth / firstAspect, maxHeight * 0.66f).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(120f))
            val halfWidth = (availableWidth - spacing) / 2
            val bottomHeight = min(maxHeight - topHeight, min(halfWidth / secondAspect, halfWidth / thirdAspect)).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(96f))
            tiles[0].rect.set(0f, 0f, availableWidth.toFloat(), topHeight.toFloat())
            tiles[1].rect.set(0f, (topHeight + spacing).toFloat(), halfWidth.toFloat(), (topHeight + spacing + bottomHeight).toFloat())
            tiles[2].rect.set((halfWidth + spacing).toFloat(), (topHeight + spacing).toFloat(), availableWidth.toFloat(), (topHeight + spacing + bottomHeight).toFloat())
            topHeight + spacing + bottomHeight
        }
    }

    private fun layoutFour(availableWidth: Int): Int {
        val firstAspect = tiles[0].aspectRatio()
        val maxHeight = availableWidth * MAX_SIZE_HEIGHT / MAX_SIZE_WIDTH
        if (firstAspect > 1.2f) {
            val topHeight = min(availableWidth / firstAspect, maxHeight * 0.66f).roundToInt()
                .coerceAtLeast(AndroidUtilities.dp(120f))
            var bottomHeight = (availableWidth / (tiles[1].aspectRatio() + tiles[2].aspectRatio() + tiles[3].aspectRatio())).roundToInt()
            bottomHeight = min(maxHeight.roundToInt() - topHeight, bottomHeight).coerceAtLeast(AndroidUtilities.dp(84f))
            val firstWidth = max(AndroidUtilities.dp(72f), min((availableWidth * 0.4f).roundToInt(), (bottomHeight * tiles[1].aspectRatio()).roundToInt()))
            val thirdWidth = max(max(AndroidUtilities.dp(72f), (availableWidth * 0.33f).roundToInt()), (bottomHeight * tiles[3].aspectRatio()).roundToInt())
                .coerceAtMost(availableWidth - spacing * 2 - firstWidth - AndroidUtilities.dp(72f))
            val secondWidth = availableWidth - spacing * 2 - firstWidth - thirdWidth
            tiles[0].rect.set(0f, 0f, availableWidth.toFloat(), topHeight.toFloat())
            val bottomTop = topHeight + spacing
            tiles[1].rect.set(0f, bottomTop.toFloat(), firstWidth.toFloat(), (bottomTop + bottomHeight).toFloat())
            tiles[2].rect.set((firstWidth + spacing).toFloat(), bottomTop.toFloat(), (firstWidth + spacing + secondWidth).toFloat(), (bottomTop + bottomHeight).toFloat())
            tiles[3].rect.set((firstWidth + spacing * 2 + secondWidth).toFloat(), bottomTop.toFloat(), availableWidth.toFloat(), (bottomTop + bottomHeight).toFloat())
            return bottomTop + bottomHeight
        }
        val rightWidth = max(AndroidUtilities.dp(80f), (maxHeight / (1f / tiles[1].aspectRatio() + 1f / tiles[2].aspectRatio() + 1f / tiles[3].aspectRatio())).roundToInt())
            .coerceAtMost((availableWidth * 0.45f).roundToInt())
        val leftWidth = availableWidth - spacing - rightWidth
        val height0 = min(0.33f, max(AndroidUtilities.dp(96f).toFloat(), rightWidth / tiles[1].aspectRatio()) / maxHeight)
        val height1 = min(0.33f, max(AndroidUtilities.dp(96f).toFloat(), rightWidth / tiles[2].aspectRatio()) / maxHeight)
        val height2 = 1f - height0 - height1
        val firstHeight = maxHeight.roundToInt()
        val rightHeight0 = (height0 * firstHeight).roundToInt()
        val rightHeight1 = (height1 * firstHeight).roundToInt()
        tiles[0].rect.set(0f, 0f, leftWidth.toFloat(), firstHeight.toFloat())
        tiles[1].rect.set((leftWidth + spacing).toFloat(), 0f, availableWidth.toFloat(), rightHeight0.toFloat())
        tiles[2].rect.set((leftWidth + spacing).toFloat(), (rightHeight0 + spacing).toFloat(), availableWidth.toFloat(), (rightHeight0 + spacing + rightHeight1).toFloat())
        tiles[3].rect.set((leftWidth + spacing).toFloat(), (rightHeight0 + rightHeight1 + spacing * 2).toFloat(), availableWidth.toFloat(), firstHeight.toFloat())
        return firstHeight
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

    private fun aspectKind(aspectRatio: Float): Char {
        return when {
            aspectRatio > 1.2f -> 'w'
            aspectRatio < 0.8f -> 'n'
            else -> 'q'
        }
    }

    private companion object {
        private const val MAX_TILES_PER_ROW = 3
        private const val MAX_SIZE_WIDTH = 1000f
        private const val MAX_SIZE_HEIGHT = 814f
    }
}
