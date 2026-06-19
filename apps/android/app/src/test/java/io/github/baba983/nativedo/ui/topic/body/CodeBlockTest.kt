package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

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
}
