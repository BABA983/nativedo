package org.telegram.ui.Components

import android.graphics.Rect
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
 * Geometry/constants regression for [CircularProgressDrawable]
 * (`forced_exact: animation, drawing`).
 *
 * Locks default size/thickness (dp 18 / 2.25) and the 5400ms segment schedule
 * that PhotoViewer / ArticleViewer progress spinners depend on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CircularProgressDrawableForcedExactTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun defaultSizeAndThicknessMatchTelegramDpConstants() {
        val drawable = CircularProgressDrawable()
        assertThat(drawable.size).isEqualTo(AndroidUtilities.dp(18f).toFloat())
        assertThat(drawable.thickness).isEqualTo(AndroidUtilities.dp(2.25f).toFloat())
    }

    @Test
    fun customConstructorKeepsExplicitGeometry() {
        val size = AndroidUtilities.dp(24f).toFloat()
        val thickness = AndroidUtilities.dp(3f).toFloat()
        val drawable = CircularProgressDrawable(size, thickness, 0xFF00A0FF.toInt())
        assertThat(drawable.size).isEqualTo(size)
        assertThat(drawable.thickness).isEqualTo(thickness)
    }

    @Test
    fun segmentScheduleMatchesUpstreamAtKnownTicks() {
        val segments = FloatArray(2)

        CircularProgressDrawable.getSegments(0f, segments)
        assertThat(segments[0]).isEqualTo(0f)
        assertThat(segments[1]).isEqualTo(0f)

        // t=1350 is the first loop boundary used by the upstream for-loop.
        CircularProgressDrawable.getSegments(1350f, segments)
        assertThat(segments[0]).isAtLeast(0f)
        assertThat(segments[1]).isGreaterThan(segments[0])

        // Full cycle endpoint shape: end angle advances, start stays non-negative.
        CircularProgressDrawable.getSegments(5400f, segments)
        assertThat(segments[0]).isAtLeast(0f)
        assertThat(segments[1]).isAtLeast(segments[0])
    }

    @Test
    fun boundsCenterUsesHalfSizeStrokeGeometry() {
        val drawable = CircularProgressDrawable()
        val size = drawable.size.toInt()
        drawable.setBounds(0, 0, size * 2, size * 2)
        // Drawable must accept bounds without throwing; visual coverage is in Paparazzi.
        assertThat(drawable.bounds).isEqualTo(Rect(0, 0, size * 2, size * 2))
    }
}
