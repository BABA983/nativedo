package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicFloatingDateControllerTest {
    @Test
    fun separatorPushesFloatingDateThroughOneCellHeight() {
        val clipTop = 100
        val height = 28

        assertThat(topicFloatingDatePushOffset(156, clipTop, height)).isEqualTo(0)
        assertThat(topicFloatingDatePushOffset(150, clipTop, height)).isEqualTo(-6)
        assertThat(topicFloatingDatePushOffset(129, clipTop, height)).isEqualTo(-27)
        assertThat(topicFloatingDatePushOffset(128, clipTop, height)).isEqualTo(0)
    }

    @Test
    fun translationAccountsForHostLayoutTopInsteadOfDoublingActionBarInset() {
        org.telegram.messenger.AndroidUtilities.density = 1f

        assertThat(
            topicFloatingDateTranslationY(
                viewTop = 180,
                parentWindowTop = 0,
                insetWindowTop = 0,
                insetHeight = 180,
                viewOffset = 0,
            ),
        ).isEqualTo(-4f)
        assertThat(
            topicFloatingDateTranslationY(
                viewTop = 4,
                parentWindowTop = 63,
                insetWindowTop = 63,
                insetHeight = 180,
                viewOffset = -12,
            ),
        ).isEqualTo(160f)
    }
}
