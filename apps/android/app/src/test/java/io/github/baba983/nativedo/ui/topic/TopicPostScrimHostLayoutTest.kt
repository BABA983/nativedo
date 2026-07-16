package io.github.baba983.nativedo.ui.topic

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicPostScrimHostLayoutTest {

    private lateinit var activity: Activity
    private lateinit var host: TopicPostScrimHostLayout
    private lateinit var viewport: FrameLayout
    private lateinit var selectedView: View

    @Before
    fun setUp() {
        AndroidUtilities.density = 1f
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        host = TopicPostScrimHostLayout(activity)
        viewport = FrameLayout(activity)
        selectedView = View(activity)
        viewport.addView(selectedView, FrameLayout.LayoutParams(400, 120))
        host.addView(viewport, FrameLayout.LayoutParams(400, 800))
        activity.setContentView(host)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 400, 800)
        viewport.layout(0, 0, 400, 800)
        selectedView.layout(0, 100, 400, 220)
        host.setScrimViewport(viewport, null, null)
    }

    @Test
    fun staleOwnerCannotClearNewerScrim() {
        val firstOwner = Any()
        val secondOwner = Any()

        host.showScrim(selectedView, firstOwner, false)
        host.showScrim(selectedView, secondOwner, false)
        host.hideScrim(firstOwner, false)

        assertThat(host.isScrimActive).isTrue()

        host.hideScrim(secondOwner, false)
        assertThat(host.isScrimActive).isFalse()
    }

    @Test
    fun animatedHideRetainsSelectedViewUntilAnimationEnds() {
        val owner = Any()
        host.showScrim(selectedView, owner, false)

        host.hideScrim(owner, true)

        assertThat(host.isScrimActive).isTrue()
        currentAnimator().end()
        assertThat(host.isScrimActive).isFalse()
    }

    @Test
    fun progressListenerTracksTheSameScrimAnimationUsedForSideControls() {
        val values = mutableListOf<Float>()
        val owner = Any()
        host.setScrimProgressListener(values::add)

        host.showScrim(selectedView, owner, false)
        host.hideScrim(owner, false)

        assertThat(values).containsAtLeast(0f, 1f)
        assertThat(values.last()).isEqualTo(0f)
    }

    @Test
    fun detachedSelectedViewIsClearedAfterDrawValidation() {
        val detached = View(activity)
        host.showScrim(detached, Any(), false)

        host.draw(Canvas(Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)))
        shadowOf(activity.mainLooper).idle()

        assertThat(host.isScrimActive).isFalse()
    }

    @Test
    fun viewportClipsBetweenTopAndBottomOccluders() {
        val top = View(activity)
        val bottom = View(activity)
        host.addView(top, FrameLayout.LayoutParams(400, 60))
        host.addView(bottom, FrameLayout.LayoutParams(400, 100))
        top.layout(0, 0, 400, 60)
        bottom.layout(0, 700, 400, 800)
        host.setScrimViewport(viewport, top, bottom)

        val bounds = Rect()

        assertThat(host.resolveScrimViewport(bounds)).isTrue()
        assertThat(bounds).isEqualTo(Rect(0, 60, 400, 700))
    }

    private fun currentAnimator(): ValueAnimator {
        return TopicPostScrimHostLayout::class.java
            .getDeclaredField("scrimAnimator")
            .apply { isAccessible = true }
            .get(host) as ValueAnimator
    }
}
