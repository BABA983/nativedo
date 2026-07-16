package io.github.baba983.nativedo.ui.topic.body

import android.text.SpannableString
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.google.common.truth.Truth.assertThat
import java.util.Stack
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.spoilers.SpoilerEffect

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SpoilerTextLayoutTest {
    @Test
    fun collect_addsSpoilerEffectsForTelegramSpoilerSpan() {
        val layout = spoilerLayout()
        val pool = Stack<SpoilerEffect>()
        val spoilers = arrayListOf<SpoilerEffect>()

        SpoilerTextLayout.collect(FakeView(), layout, pool, spoilers, false)

        assertThat(spoilers).isNotEmpty()
        val bounds = spoilers.first().bounds
        assertThat(bounds.width()).isGreaterThan(0)
        assertThat(bounds.height()).isGreaterThan(0)
        assertThat(
            SpoilerTextLayout.contains(
                spoilers,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
            ),
        ).isTrue()
    }

    @Test
    fun collect_doesNotAddSpoilerEffectsWhenAlreadyRevealed() {
        val layout = spoilerLayout()
        val pool = Stack<SpoilerEffect>()
        val spoilers = arrayListOf<SpoilerEffect>()

        SpoilerTextLayout.collect(FakeView(), layout, pool, spoilers, true)

        assertThat(spoilers).isEmpty()
    }

    private fun spoilerLayout(): StaticLayout {
        val text = SpannableString("Visible hidden text after")
        val start = text.indexOf("hidden")
        val end = start + "hidden text".length
        val run = TextStyleSpan.TextStyleRun().apply {
            flags = TextStyleSpan.FLAG_STYLE_SPOILER
        }
        text.setSpan(TextStyleSpan(run), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return StaticLayout(
            text,
            TextPaint().apply { textSize = 16f },
            320,
            Layout.Alignment.ALIGN_NORMAL,
            1f,
            0f,
            false,
        )
    }

    private class FakeView : View(RuntimeEnvironment.getApplication())
}
