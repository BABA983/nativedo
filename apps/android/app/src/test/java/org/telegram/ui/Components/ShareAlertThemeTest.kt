package org.telegram.ui.Components

import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Stories.DarkThemeResourceProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ShareAlertThemeTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun darkProviderColorsCardAndActionText() {
        val dialog = ShareAlert(
            RuntimeEnvironment.getApplication(),
            null,
            "https://linux.do",
            false,
            "https://linux.do",
            false,
            DarkThemeResourceProvider(),
        )
        val content = dialog.findViewById<ViewGroup>(android.R.id.content)
        val card = content.getChildAt(0) as LinearLayout
        val copyRow = card.getChildAt(1) as LinearLayout
        val label = copyRow.getChildAt(1) as TextView

        assertThat((card.background as GradientDrawable).color!!.defaultColor)
            .isEqualTo(0xff1c1c1e.toInt())
        assertThat(label.currentTextColor).isEqualTo(0xffffffff.toInt())
    }
}
