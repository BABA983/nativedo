package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicDetailHostLayoutTest {
    private lateinit var host: TopicDetailHostLayout
    private lateinit var fixedTopView: View

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        host = TopicDetailHostLayout(activity)
        fixedTopView = View(activity)
        host.addView(fixedTopView, FrameLayout.LayoutParams(400, 56))
        host.setFixedTopView(fixedTopView)
        activity.setContentView(host)
        // Production resolves the resizable root by walking up to the
        // DrawerLayoutContainer; point the helper at this tree explicitly and
        // run the attach it would normally reach via the posted UI runnable.
        host.adjustPanLayoutHelper.setResizableView(FrameLayout(activity))
        host.adjustPanLayoutHelper.onAttach()
    }

    @Test
    fun panTranslationCounterTranslatesFixedTopViewAndForwardsProgress() {
        var forwardedY = 0f
        var forwardedProgress = 0f
        var forwardedKeyboardVisible = false
        host.setPanTransitionDelegate(delegate(
            onUpdate = { y, progress, keyboardVisible ->
                forwardedY = y
                forwardedProgress = progress
                forwardedKeyboardVisible = keyboardVisible
            },
        ))

        host.adjustPanLayoutHelper.OnPanTranslationUpdate(48f, 0.4f, true)

        assertThat(fixedTopView.translationY).isEqualTo(48f)
        assertThat(forwardedY).isEqualTo(48f)
        assertThat(forwardedProgress).isEqualTo(0.4f)
        assertThat(forwardedKeyboardVisible).isTrue()
    }

    @Test
    fun releaseResetsTranslationsAndDropsCallbacks() {
        var updates = 0
        host.setPanTransitionDelegate(delegate(onUpdate = { _, _, _ -> updates++ }))
        host.adjustPanLayoutHelper.OnPanTranslationUpdate(32f, 0.5f, true)

        host.releasePanLayoutHelper()
        host.adjustPanLayoutHelper.OnPanTranslationUpdate(16f, 1f, false)

        assertThat(host.translationY).isEqualTo(0f)
        assertThat(fixedTopView.translationY).isEqualTo(0f)
        assertThat(updates).isEqualTo(1)
    }

    private fun delegate(
        startOffset: Int = 0,
        onStart: (Boolean, Int) -> Unit = { _, _ -> },
        onUpdate: (Float, Float, Boolean) -> Unit = { _, _, _ -> },
        onEnd: () -> Unit = {},
    ): TopicDetailHostLayout.PanTransitionDelegate {
        return object : TopicDetailHostLayout.PanTransitionDelegate {
            override fun onPanTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
                onStart(keyboardVisible, contentHeight)
            }

            override fun onPanTranslationUpdate(y: Float, progress: Float, keyboardVisible: Boolean) {
                onUpdate(y, progress, keyboardVisible)
            }

            override fun onPanTransitionEnd() {
                onEnd()
            }

            override fun getPanStartOffset(): Int = startOffset
        }
    }
}
