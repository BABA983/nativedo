package org.telegram.ui.Components

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
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
    fun nonAnimatedProgressTransitionIsDeterministic() {
        val progress = view.getChildAt(1)

        view.showProgress(true, false)

        assertThat(view.linearLayout.alpha).isEqualTo(0f)
        assertThat(progress.alpha).isEqualTo(1f)

        view.showProgress(false, false)

        assertThat(view.linearLayout.alpha).isEqualTo(1f)
        assertThat(progress.alpha).isEqualTo(0f)
    }
}
