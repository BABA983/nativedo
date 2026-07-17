package org.telegram.ui.Components

import android.view.Gravity
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

/**
 * Geometry/constants regression for [LayoutHelper] (`forced_exact: measurement`).
 *
 * Keeps Telegram's dp-based LayoutParams helpers honest so host wiring cannot
 * silently change MATCH/WRAP sentinels or dp→px conversion used by exact cells.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LayoutHelperForcedExactTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun matchAndWrapSentinelsStayTelegramShaped() {
        assertThat(LayoutHelper.MATCH_PARENT).isEqualTo(-1)
        assertThat(LayoutHelper.WRAP_CONTENT).isEqualTo(-2)
    }

    @Test
    fun createFrameConvertsDpMarginsThroughAndroidUtilities() {
        val lp = LayoutHelper.createFrame(
            40,
            24f,
            Gravity.CENTER,
            8f,
            4f,
            8f,
            4f,
        )
        assertThat(lp.width).isEqualTo(AndroidUtilities.dp(40f))
        assertThat(lp.height).isEqualTo(AndroidUtilities.dp(24f))
        assertThat(lp.gravity).isEqualTo(Gravity.CENTER)
        assertThat(lp.leftMargin).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(lp.topMargin).isEqualTo(AndroidUtilities.dp(4f))
        assertThat(lp.rightMargin).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(lp.bottomMargin).isEqualTo(AndroidUtilities.dp(4f))
    }

    @Test
    fun measureSpecHelpersUseExactlyMode() {
        val px = AndroidUtilities.dp(56f)
        assertThat(LayoutHelper.measureSpecExactly(px))
            .isEqualTo(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY))
        assertThat(LayoutHelper.measureSpecExactlyDp(56))
            .isEqualTo(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56f), View.MeasureSpec.EXACTLY))
    }

    @Test
    fun createFrameMatchParentUsesSentinels() {
        val lp = LayoutHelper.createFrameMatchParent()
        assertThat(lp.width).isEqualTo(LayoutHelper.MATCH_PARENT)
        assertThat(lp.height).isEqualTo(LayoutHelper.MATCH_PARENT)
    }
}
