package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoActivity
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.chat.ChatInputViewsContainer

/**
 * The reply box is a floating capsule, not a strip across the screen.
 *
 * <p>The failure this pins: upstream's composer is drawn as the chat's floating island only when it
 * is hosted by {@link ChatInputViewsContainer}; added straight to the screen it keeps the enter
 * view's own full-width background, which is the strip a reader saw. The host also owns the
 * composer's lifecycle, so a screen that forgets to forward pause/resume/destroy leaks the
 * composer's keyboard and notification observers.</p>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class ComposerCapsuleTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        site.stop()
    }

    @Test
    fun theComposerRidesInUpstreamsFloatingIsland() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Capsule"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.layoutApp()

        val composer = composer() ?: throw AssertionError("the screen has no composer")
        val container = viewTree().filterIsInstance<ChatInputViewsContainer>().firstOrNull()
        assertNotNull("the composer is hosted by upstream's floating container", container)

        val island = container!!.inputIslandBubbleContainer
        println("capsule: container=${container.javaClass.simpleName} islandChildren=${island.childCount}")
        assertTrue(
            "the enter view is a child of the island bubble, not of the screen",
            descendants(island).contains(composer),
        )
        assertTrue(
            "the island was measured with the composer in it",
            island.height > 0 && container.inputBubbleHeight > 0f,
        )
        // The geometry that makes it a floating capsule rather than a strip: the enter view is
        // inset from the container's sides (the 7dp the chat's island uses), and its bottom sits
        // above the container's own edge by the island's bottom gap.
        println("capsule: containerW=${container.width} composerW=${composer.width} composerBottomGap=" +
            (container.height - composer.bottom) + " inputBubbleBottom=" + container.inputBubbleBottom)
        assertTrue(
            "the composer is inset from the capsule's edges, not a full-width strip",
            composer.width < container.width,
        )
        val containerLocation = IntArray(2)
        container.getLocationInWindow(containerLocation)
        val composerLocation = IntArray(2)
        composer.getLocationInWindow(composerLocation)
        val gapToBottom = (containerLocation[1] + container.height) -
            (composerLocation[1] + composer.height)
        println("capsule: gap to the screen's bottom = $gapToBottom")
        assertTrue(
            "the composer floats above the screen's bottom edge (the island's own gap)",
            gapToBottom >= AndroidUtilities.dp(ChatInputViewsContainer.INPUT_BUBBLE_BOTTOM.toFloat()),
        )
        harness.renderApp("input-capsule-empty")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theScreenForwardsTheComposersLifecycle() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Lifecycle"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.layoutApp()

        val fragment = harness.visibleFragment() as TopicFragment
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        assertFalse("a fresh composer is not paused", privateBoolean(composer, "isPaused"))

        fragment.onPause()
        harness.idle()
        assertTrue(
            "leaving the screen pauses the composer (its keyboard runnable is posted)",
            privateBoolean(composer, "isPaused"),
        )

        fragment.onResume()
        harness.idle()
        assertFalse("coming back resumes the composer again", privateBoolean(composer, "isPaused"))

        // Destroying the screen has to destroy the composer: upstream's onDestroy() is what removes
        // the notification observers the composer registered, so a leaked one outlives the screen.
        fragment.onFragmentDestroy()
        harness.idle()
        assertTrue(
            "the composer's observers are released with the screen",
            privateBoolean(composer, "destroyed"),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theCapsuleRendersEmptyTextAndMultilineStates() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "States"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.layoutApp()

        val composer = composer() ?: throw AssertionError("the screen has no composer")
        val container = viewTree().filterIsInstance<ChatInputViewsContainer>().firstOrNull()
            ?: throw AssertionError("no floating container")

        // Empty: the send button is absent and the island is at its resting height.
        println("capsule: empty island=${container.inputBubbleHeight} composer=${composer.height}")
        harness.renderApp("input-empty")
        val emptyHeight = composer.height

        // One line of text.
        composer.setFieldText("单一行的回复")
        harness.idle()
        harness.layoutApp()
        println("capsule: text island=${container.inputBubbleHeight} composer=${composer.height}")
        harness.renderApp("input-text")

        // Multiline: the island has to grow with the field, and the list padding has to follow.
        composer.setFieldText("第一行\n第二行\n第三行\n第四行")
        harness.drawApp()
        harness.idleClock(500)
        repeat(3) { harness.drawApp() }
        val multilineIsland = container.inputBubbleHeight
        val multilineComposer = composer.height
        println("capsule: multiline island=$multilineIsland composer=$multilineComposer")
        assertTrue(
            "the island grew with the multiline field",
            multilineComposer > emptyHeight && multilineIsland > emptyHeight,
        )
        val send = composer.getSendButton()!!
        val visible = android.graphics.Rect()
        assertTrue("the send button remains visible", send.getGlobalVisibleRect(visible))
        assertEquals("the send button is not clipped by the composer host", send.height, visible.height())
        val richButton = viewTree().filterIsInstance<android.widget.TextView>()
            .single { it.text.toString() == "富文本" }
        val richBounds = android.graphics.Rect()
        assertTrue("multiline replies keep the rich editor entry visible", richButton.getGlobalVisibleRect(richBounds))
        val inputLocation = IntArray(2).also { container.getLocationOnScreen(it) }
        assertTrue("the growing capsule must not cover the rich editor entry",
            richBounds.bottom <= inputLocation[1] + container.inputBubbleTop)
        harness.renderApp("input-multiline")

        // Night: the island's own drawable follows the day/night switch.
        switchTheme("Dark Blue", toDark = true)
        assertEquals("the open composer's text switches with the theme, not its original construction color",
            Theme.getColor(Theme.key_chat_messagePanelText), composer.editField!!.currentTextColor)
        println("capsule: dark island=${container.inputBubbleHeight}")
        assertComposerContrast(harness.renderApp("input-multiline-dark"), composer, container)
        switchTheme("Blue", toDark = false)
        assertComposerContrast(harness.renderApp("input-restored-light"), composer, container)
        harness.assertNoTelegramSideEffects()
    }

    /** A stale light fill with newly white text must fail even when the text-color getter is right. */
    private fun assertComposerContrast(
        image: java.io.File,
        composer: ChatActivityEnterView,
        input: ChatInputViewsContainer,
    ) {
        val bitmap = android.graphics.BitmapFactory.decodeFile(image.absolutePath)
        val inputLocation = IntArray(2).also { input.getLocationInWindow(it) }
        val contentLocation = IntArray(2).also {
            harness.activity.findViewById<View>(android.R.id.content).getLocationInWindow(it)
        }
        // A blank interior patch, away from the text, rounded edge and bottom-right send button.
        val x = inputLocation[0] - contentLocation[0] + input.width - AndroidUtilities.dp(70f)
        val y = inputLocation[1] - contentLocation[1] + input.inputBubbleTop.toInt() + AndroidUtilities.dp(12f)
        val background = bitmap.getPixel(x, y)
        val contrast = androidx.core.graphics.ColorUtils.calculateContrast(
            composer.editField!!.currentTextColor, background,
        )
        bitmap.recycle()
        assertTrue("the rendered capsule text needs readable contrast; actual=$contrast", contrast >= 4.5)
    }

    @Test
    fun systemInsetsMoveTheIslandOnceAndKeepTheSendButtonAboveTheKeyboard() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Insets"))
        harness.await("the topic") { rowCount() == 1 }
        composer()!!.setFieldText("a reply above the keyboard")
        harness.idleClock(500)
        harness.layoutApp()
        val root = harness.visibleFragment()!!.fragmentView
        val input = viewTree().filterIsInstance<ChatInputViewsContainer>().single()
        fun inset(bottom: Int, keyboard: Int) {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, bottom))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, bottom))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, keyboard))
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets)
            harness.idleClock(500)
            harness.layoutApp()
            assertEquals("one inset, not both an inset and a resized parent",
                input.height - maxOf(bottom, keyboard) - AndroidUtilities.dp(9f), input.inputBubbleBottom.toInt())
        }
        inset(AndroidUtilities.dp(23f), 0)
        inset(AndroidUtilities.dp(23f), AndroidUtilities.dp(289f))
        harness.renderApp("input-keyboard-insets")
        inset(AndroidUtilities.dp(23f), 0)
        harness.assertNoTelegramSideEffects()
    }

    private fun switchTheme(themeName: String, toDark: Boolean) {
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needSetDayNightTheme,
            Theme.getTheme(themeName),
            false, null, -1, toDark, null, null, null, true,
        )
        harness.await("the $themeName theme") {
            harness.idleClock(60)
            Theme.isCurrentThemeDark() == toDark
        }
        harness.layoutApp()
        harness.idle()
    }

    private fun privateBoolean(target: Any, name: String): Boolean {
        val field = ChatActivityEnterView::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.getBoolean(target)
    }

    private fun composer(): ChatActivityEnterView? =
        viewTree().filterIsInstance<ChatActivityEnterView>().lastOrNull()

    private fun viewTree(): List<View> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return descendants(content)
    }

    private fun descendants(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return found
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private companion object {
        const val TOPIC_ID = 2790689L
    }
}
