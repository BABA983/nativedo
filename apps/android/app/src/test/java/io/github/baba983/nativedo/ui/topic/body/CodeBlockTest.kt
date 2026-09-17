package io.github.baba983.nativedo.ui.topic.body

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
class CodeBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun preformattedConstants_matchTelegramArticleViewer() {
        assertThat(CodeBlock.backgroundVerticalInsetPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(CodeBlock.textHorizontalMarginPx()).isEqualTo(AndroidUtilities.dp(16f))
        assertThat(CodeBlock.textVerticalMarginPx()).isEqualTo(AndroidUtilities.dp(12f))
        assertThat(CodeBlock.textContainerEndPaddingPx()).isEqualTo(AndroidUtilities.dp(32f))
    }

    @Test
    fun derivedPreformattedGeometry_matchesTelegramArticleViewer() {
        assertThat(CodeBlock.textTopPx()).isEqualTo(AndroidUtilities.dp(8f) + AndroidUtilities.dp(12f))
        assertThat(CodeBlock.maxFallbackScrollExtraPx()).isEqualTo(
            AndroidUtilities.dp(16f) * 2 + AndroidUtilities.dp(32f),
        )
    }

    @Test
    fun preformattedBackgroundColor_usesTheArticleCodeBackgroundKey() {
        assertThat(CodeBlock.preformattedBackgroundColor())
            .isEqualTo(Theme.getColor(Theme.key_chat_inArticleCodeBackground))
    }

    private fun buildContext(): PostBodyBuildContext {
        val host = View(RuntimeEnvironment.getApplication())
        return PostBodyBuildContext(
            host = host,
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
