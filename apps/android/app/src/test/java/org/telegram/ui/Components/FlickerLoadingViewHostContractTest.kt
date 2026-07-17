package org.telegram.ui.Components

import android.view.View.MeasureSpec
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
 * Host-contract regression for [FlickerLoadingView] after pin 12.8.1 FF.
 *
 * Why this test exists (and what it is *not*):
 * - Upstream uses `ReactedUsersListView.ITEM_HEIGHT_DP`. NativeDo does not vendor
 *   that class, so we pin the row to **50dp** (same numeric value, host-local).
 * - Shimmer height, sheet sizing, and DiscourseUserCell-adjacent lists assume this.
 * - If a future clean-FF restores the `ITEM_HEIGHT_DP` symbol and it fails to
 *   compile, or someone "fixes" the constant, measure breaks silently.
 *
 * Not covered here: full onDraw shimmer paths, other view types, animation frames —
 * those are either pure upstream (forced_exact) or need Paparazzi/product paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FlickerLoadingViewHostContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun reactedTypeSingleCellHeightIsFiftyDp() {
        val view = FlickerLoadingView(RuntimeEnvironment.getApplication())
        view.setViewType(FlickerLoadingView.REACTED_TYPE)
        view.setIsSingleCell(true)

        val width = AndroidUtilities.dp(320f)
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )

        assertThat(view.measuredHeight).isEqualTo(AndroidUtilities.dp(50f))
    }

    @Test
    fun reactedTypeWithEmojiHintMatchesSameRowHeight() {
        val view = FlickerLoadingView(RuntimeEnvironment.getApplication())
        view.setViewType(FlickerLoadingView.REACTED_TYPE_WITH_EMOJI_HINT)
        view.setIsSingleCell(true)

        val width = AndroidUtilities.dp(320f)
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )

        assertThat(view.measuredHeight).isEqualTo(AndroidUtilities.dp(50f))
    }
}
