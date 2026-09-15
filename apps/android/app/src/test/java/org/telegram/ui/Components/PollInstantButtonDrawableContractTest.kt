package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.poll.buttons.PollInstantButtonDrawable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PollInstantButtonDrawableContractTest {
    private lateinit var drawable: PollInstantButtonDrawable

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        drawable = PollInstantButtonDrawable(View(RuntimeEnvironment.getApplication()), null)
        drawable.setBounds(0, 0, 240, 48)
        drawable.setButtonText("查看投票", false)
        drawable.setButtonTextColor(0xffffffff.toInt())
    }

    @Test
    fun loadingSwitchUsesPinnedProgressFactorAndStillDraws() {
        drawable.setLoading(true, false)
        assertThat(drawable.progressFactor).isEqualTo(1f)

        drawable.draw(Canvas(Bitmap.createBitmap(240, 48, Bitmap.Config.ARGB_8888)))

        drawable.setLoading(false, false)
        assertThat(drawable.progressFactor).isEqualTo(0f)
    }
}
