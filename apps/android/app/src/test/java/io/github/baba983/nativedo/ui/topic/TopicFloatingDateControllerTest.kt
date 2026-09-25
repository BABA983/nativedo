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

        assertThat(topicFloatingDatePushOffset(156f, clipTop.toFloat(), height)).isEqualTo(0)
        assertThat(topicFloatingDatePushOffset(150f, clipTop.toFloat(), height)).isEqualTo(-6)
        assertThat(topicFloatingDatePushOffset(129f, clipTop.toFloat(), height)).isEqualTo(-27)
        assertThat(topicFloatingDatePushOffset(128f, clipTop.toFloat(), height)).isEqualTo(0)
    }

    @Test
    fun terminalSeparatorNeverPushesFloatingDate() {
        assertThat(
            topicFloatingDatePushOffset(
                separatorBottom = 150f,
                clipTop = 100f,
                floatingHeight = 28,
                isTerminalSeparator = true,
            ),
        ).isEqualTo(0)
    }
}
