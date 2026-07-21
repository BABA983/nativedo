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
class NestedSizeNotifierLayoutHostContractTest {
    private lateinit var layout: NestedSizeNotifierLayout
    private lateinit var target: View
    private lateinit var child: FakeChildLayout

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        layout = NestedSizeNotifierLayout(RuntimeEnvironment.getApplication())
        target = View(RuntimeEnvironment.getApplication())
        target.setPadding(0, AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(8f))
        child = FakeChildLayout()
        layout.setTargetListView(target)
        layout.setChildLayout(child)
    }

    @Test
    fun defaultTopPinsChildToMeasuredBottom() {
        measureLayout(targetHeight = 640, childHeight = 220, maxTopPadding = 0)

        assertThat(layout.isPinnedToTop()).isFalse()
        child.topPx = target.measuredHeight - target.paddingBottom - child.measuredHeightPx
        assertThat(layout.isPinnedToTop()).isTrue()
    }

    @Test
    fun explicitTopPaddingOverridesNaturalChildHeight() {
        measureLayout(targetHeight = 640, childHeight = 220, maxTopPadding = 96)

        child.topPx = target.paddingTop + AndroidUtilities.dp(96f)
        assertThat(layout.isPinnedToTop()).isTrue()
    }

    private fun measureLayout(targetHeight: Int, childHeight: Int, maxTopPadding: Int) {
        target.measure(
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(targetHeight.toFloat()), View.MeasureSpec.EXACTLY),
        )
        child.measuredHeightPx = AndroidUtilities.dp(childHeight.toFloat())
        layout.setChildLayout(child, AndroidUtilities.dp(maxTopPadding.toFloat()))
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(targetHeight.toFloat()), View.MeasureSpec.EXACTLY),
        )
    }

    private class FakeChildLayout : NestedSizeNotifierLayout.ChildLayout {
        var topPx: Int = 0
        var measuredHeightPx: Int = 0

        override fun getListView(): RecyclerListView? = null
        override fun getTop(): Int = topPx
        override fun isAttached(): Boolean = true
        override fun getMeasuredHeight(): Int = measuredHeightPx
        override fun addOnLayoutChangeListener(listener: View.OnLayoutChangeListener) = Unit
        override fun removeOnLayoutChangeListener(listener: View.OnLayoutChangeListener) = Unit
    }
}
