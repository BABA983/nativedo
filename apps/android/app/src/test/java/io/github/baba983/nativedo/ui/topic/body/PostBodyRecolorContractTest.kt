package io.github.baba983.nativedo.ui.topic.body

import android.view.View
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.Theme

/**
 * Ensures post-body paints re-sample Theme keys on theme switch without
 * rebuilding the full block tree (the previous Colorable leak).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PostBodyRecolorContractTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        app.getSharedPreferences("themeconfig", 0).edit().clear().commit()
        seedAtthemeIntoFilesDir("bluebubbles.attheme")
        seedAtthemeIntoFilesDir("darkblue.attheme")
        Theme.getActiveTheme()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
    }

    @Test
    fun applyTheme_updatesSharedPaintsToCurrentThemeColors() {
        val host = View(RuntimeEnvironment.getApplication())
        val context = PostBodyBuildContext(
            host = host,
            baseUrl = "https://linux.do",
            delegate = NoOpDelegate,
            textColor = Theme.getColor(Theme.key_chat_messageTextIn),
            secondaryTextColor = Theme.getColor(Theme.key_chat_inTimeText),
            accentColor = Theme.getColor(Theme.key_chat_inReplyLine),
            chipBackgroundColor = Theme.multAlpha(Theme.getColor(Theme.key_chat_inReplyLine), 0.15f),
            dividerColor = Theme.getColor(Theme.key_divider),
        )
        val dayText = context.paragraphPaint.color
        val dayAccent = context.accentColor

        Theme.applyTheme(Theme.getCurrentNightTheme(), true)
        context.applyTheme()

        val nightText = Theme.getColor(Theme.key_chat_messageTextIn)
        val nightAccent = Theme.getColor(Theme.key_chat_inReplyLine)
        assertThat(nightText).isNotEqualTo(dayText)
        assertThat(context.paragraphPaint.color).isEqualTo(nightText)
        assertThat(context.headingPaint.color).isEqualTo(nightText)
        assertThat(context.monoPaint.color).isEqualTo(nightText)
        assertThat(context.mediaCaptionPaint.color).isEqualTo(Theme.getColor(Theme.key_chat_inTimeText))
        assertThat(context.accentColor).isEqualTo(nightAccent)
        assertThat(context.paragraphPaint.linkColor).isEqualTo(nightAccent)
        assertThat(context.accentColor).isNotEqualTo(dayAccent)
    }

    @Test
    fun applyTheme_updatesTextSizesFromSharedConfig() {
        val original = SharedConfig.fontSize
        val host = View(RuntimeEnvironment.getApplication())
        val context = PostBodyBuildContext(
            host = host,
            baseUrl = "https://linux.do",
            delegate = NoOpDelegate,
            textColor = Theme.getColor(Theme.key_chat_messageTextIn),
            secondaryTextColor = Theme.getColor(Theme.key_chat_inTimeText),
            accentColor = Theme.getColor(Theme.key_chat_inReplyLine),
            chipBackgroundColor = Theme.multAlpha(Theme.getColor(Theme.key_chat_inReplyLine), 0.15f),
            dividerColor = Theme.getColor(Theme.key_divider),
        )
        try {
            SharedConfig.fontSize = 24
            context.applyTheme()

            assertThat(context.paragraphPaint.textSize).isEqualTo(AndroidUtilities.dp(24f).toFloat())
            assertThat(context.headingPaint.textSize).isEqualTo(AndroidUtilities.dp(28f).toFloat())
            assertThat(context.mediaCaptionPaint.textSize).isEqualTo(AndroidUtilities.dp(22f).toFloat())
        } finally {
            SharedConfig.fontSize = original
        }
    }

    @Test
    fun applyTheme_resamplesTheArticleBlockColorsFromTheNewKeys() {
        val host = View(RuntimeEnvironment.getApplication())
        val context = PostBodyBuildContext(
            host = host,
            baseUrl = "https://linux.do",
            delegate = NoOpDelegate,
            textColor = Theme.getColor(Theme.key_chat_messageTextIn),
            secondaryTextColor = Theme.getColor(Theme.key_chat_inTimeText),
            accentColor = Theme.getColor(Theme.key_chat_inReplyLine),
            chipBackgroundColor = Theme.multAlpha(Theme.getColor(Theme.key_chat_inReplyLine), 0.15f),
            dividerColor = Theme.getColor(Theme.key_divider),
        )
        val dayTableBackground = context.tableBackgroundColor

        Theme.applyTheme(Theme.getCurrentNightTheme(), true)
        context.applyTheme()

        assertThat(context.articleDividerColor).isEqualTo(Theme.getColor(Theme.key_chat_inDivider))
        assertThat(context.tableBackgroundColor).isEqualTo(Theme.getColor(Theme.key_chat_inTableBackground))
        assertThat(context.tableBorderColor).isEqualTo(Theme.getColor(Theme.key_chat_inTableBorder))
        assertThat(context.articleCodeBackgroundColor)
            .isEqualTo(Theme.getColor(Theme.key_chat_inArticleCodeBackground))
        assertThat(context.articleDetailsArrowColor)
            .isEqualTo(Theme.getColor(Theme.key_chat_inArticleDetailsArrow))
        assertThat(context.articleDetailsLineColor)
            .isEqualTo(Theme.getColor(Theme.key_chat_inArticleDetailsLine))
        assertThat(context.tableBackgroundColor).isNotEqualTo(dayTableBackground)
    }

    private fun seedAtthemeIntoFilesDir(assetName: String) {
        val candidates = listOf(
            File("../TMessagesProj/src/main/assets/$assetName"),
            File("apps/android/TMessagesProj/src/main/assets/$assetName"),
            File("TMessagesProj/src/main/assets/$assetName"),
        )
        val source = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("Missing $assetName for Theme unit tests")
        source.copyTo(File(ApplicationLoader.getFilesDirFixed(), assetName), overwrite = true)
    }

    private object NoOpDelegate : PostBodyDelegate {
        override fun onLinkClick(url: String) = Unit
        override fun onTopicClick(topicId: Long) = Unit
        override fun onUserClick(username: String) = Unit
    }
}
