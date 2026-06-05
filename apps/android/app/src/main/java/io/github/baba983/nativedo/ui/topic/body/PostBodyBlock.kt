package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Canvas
import android.view.MotionEvent
import org.telegram.ui.Cells.TextSelectionHelper
import java.util.ArrayList

internal interface PostBodyBlock {
    fun measure(maxWidthPx: Int): Int
    fun draw(canvas: Canvas, parentWidth: Int)
    fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean = false
    fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>, x: Int, y: Int) = Unit
    fun onAttachedToWindow() = Unit
    fun onDetachedFromWindow() = Unit
    fun applyTheme() = Unit
}
