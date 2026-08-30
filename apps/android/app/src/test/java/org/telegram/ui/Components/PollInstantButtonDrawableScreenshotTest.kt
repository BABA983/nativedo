package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.view.View
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.poll.buttons.PollInstantButtonDrawable

internal class PollInstantButtonDrawableScreenshotTest {
    // The loading spinner arc is scheduled from SystemClock.elapsedRealtime()
    // inside the forced-exact CircularProgressDrawable, so a captured frame
    // lands on an arbitrary animation tick and the spinner sweeps between
    // renders (~0.04% of pixels in practice). maxPercentDifference uses the
    // same percent units; 0.5 absorbs any spinner frame while layout, text,
    // and colors stay strictly verified.
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(
        SystemAppearance.entries,
        maxPercentDifference = 0.5,
    )

    @Test
    fun idleAndLoadingStates() {
        paparazziRule.snapshot(::PollButtonStatesView)
    }
}

private class PollButtonStatesView(context: Context) : View(context) {
    private val idle = PollInstantButtonDrawable(this)
    private val loading = PollInstantButtonDrawable(this)

    init {
        val color = Theme.getColor(Theme.key_chat_inPreviewInstantText)
        idle.setButtonText("查看投票", false)
        idle.setButtonTextColor(color)
        loading.setButtonText("提交投票", false)
        loading.setButtonTextColor(color)
        loading.setLoading(true, false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(112f))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        val inset = AndroidUtilities.dp(16f)
        idle.setBounds(inset, inset, width - inset, AndroidUtilities.dp(48f))
        loading.setBounds(inset, AndroidUtilities.dp(64f), width - inset, AndroidUtilities.dp(96f))
        idle.draw(canvas)
        loading.draw(canvas)
    }
}
