package org.telegram.ui.Components

import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class StickerEmptyViewContractTest {
    private lateinit var view: StickerEmptyView

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        view = StickerEmptyView(
            RuntimeEnvironment.getApplication(),
            null,
            StickerEmptyView.STICKER_TYPE_SEARCH,
        )
    }

    @Test
    fun visibilityFactorAndEnabledStateFollowPinnedContract() {
        view.setVisibility(View.VISIBLE, false)
        view.setVisibility(View.GONE, false)

        assertThat(view.visibilityFactor).isEqualTo(0f)
        assertThat(view.isEnabled).isFalse()

        view.setVisibility(View.VISIBLE, false)

        assertThat(view.visibilityFactor).isEqualTo(1f)
        assertThat(view.isEnabled).isTrue()
    }

    @Test
    fun nonAnimatedProgressTransitionIsDeterministic() {
        val progress = view.getChildAt(1)

        view.showProgress(true, false)

        assertThat(view.linearLayout.alpha).isEqualTo(0f)
        assertThat(progress.alpha).isEqualTo(1f)

        view.showProgress(false, false)

        assertThat(view.linearLayout.alpha).isEqualTo(1f)
        assertThat(progress.alpha).isEqualTo(0f)
    }

    @Test
    fun keyboardOffsetAndLongSubtitleMatchPinnedHelpers() {
        view.setKeyboardHeight(200, false)
        view.setSubtitle("one two three four five six seven")

        val expectedOffset = (-100 + AndroidUtilities.dp(20f)).toFloat()
        assertThat(view.linearLayout.translationY).isWithin(0.01f).of(expectedOffset)
        assertThat(view.subtitle.text.toString()).contains("\n")

        view.setPreventMoving(false)
        assertThat(view.linearLayout.translationY).isEqualTo(0f)
    }
}
