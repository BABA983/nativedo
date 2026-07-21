package org.telegram.ui.Components

import android.view.View
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
class FragmentFloatingButtonContractTest {
    private lateinit var view: View

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        view = View(RuntimeEnvironment.getApplication())
    }

    @Test
    fun animatedVisibilityUsesPinnedMinimumScale() {
        FragmentFloatingButton.setAnimatedVisibility(view, 0.5f)

        assertThat(view.visibility).isEqualTo(View.VISIBLE)
        assertThat(view.alpha).isEqualTo(0.5f)
        assertThat(view.scaleX).isWithin(0.001f).of(0.7f)
        assertThat(view.scaleY).isWithin(0.001f).of(0.7f)

        FragmentFloatingButton.setAnimatedVisibility(view, 0f)
        assertThat(view.visibility).isEqualTo(View.GONE)
        assertThat(view.scaleX).isWithin(0.001f).of(0.4f)
    }
}
