package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Paint
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
class RuleBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun dividerGeometry_matchesTelegramBlockDividerCell() {
        val parentWidth = AndroidUtilities.dp(300f)

        assertThat(RuleBlock.measuredHeightPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(RuleBlock.barTopPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(RuleBlock.barBottomPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(RuleBlock.barRadiusPx()).isEqualTo(AndroidUtilities.dp(1f))
        assertThat(RuleBlock.barLeftPx(parentWidth)).isEqualTo(parentWidth / 3)
        assertThat(RuleBlock.barRightPx(parentWidth)).isEqualTo(parentWidth / 3 * 2)
    }

    @Test
    fun dividerPaintUsesTheArticleDividerColor() {
        val context = buildContext()
        val block = RuleBlock(context)

        assertThat(block.paintColor()).isEqualTo(Theme.getColor(Theme.key_chat_inDivider))

        Theme.applyTheme(Theme.getCurrentNightTheme(), true)
        context.applyTheme()
        block.applyTheme()

        assertThat(block.paintColor()).isEqualTo(Theme.getColor(Theme.key_chat_inDivider))
    }

    private fun buildContext(): PostBodyBuildContext {
        return PostBodyBuildContext(
            host = View(RuntimeEnvironment.getApplication()),
            baseUrl = "https://linux.do/",
            delegate = object : PostBodyDelegate {
                override fun onLinkClick(url: String) = Unit
                override fun onTopicClick(topicId: Long) = Unit
                override fun onUserClick(username: String) = Unit
                override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) = Unit
            },
            textColor = 0xFF111111.toInt(),
            secondaryTextColor = 0xFF666666.toInt(),
            accentColor = 0xFF1677FF.toInt(),
            chipBackgroundColor = 0x221677FF,
            dividerColor = 0xFFE0E0E0.toInt(),
        )
    }
}

private fun RuleBlock.paintColor(): Int {
    val paint = RuleBlock::class.java.getDeclaredField("paint")
        .apply { isAccessible = true }
        .get(this) as Paint
    return paint.color
}
