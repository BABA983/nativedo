package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView

/**
 * The rich editor the reader can actually reach, and what its own buttons hand back.
 *
 * <p>The failures this pins: a "rich editor" that is a bespoke copy of the toolbar (so upstream's
 * formatting drifts), one that is opened but whose content never reaches the composer, and - the
 * bug that prompted this - an editor whose Apply closes the screen that opened it. The real
 * upstream [org.telegram.ui.iv.RichEditor] opens from the reader's own strip through
 * [DiscourseRichEditor], its Apply and back buttons route through the instance-bound
 * [io.github.baba983.nativedo.hooks.NativeDoHooks.RichEditorHost] instead of Telegram's
 * send/draft paths, and the exact owning [TopicFragment] is back underneath afterwards.</p>
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
internal class RichEditorSeamTest {

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

    /**
     * The reader's Apply: the editor exports its blocks and only the editor closes. Clearing the
     * field while the editor is up means what the composer holds afterwards can only have come
     * back from the editor.
     */
    @Test
    fun theEditorsApplyClosesOnlyTheEditorAndReturnsTheOwningTopic() {
        val (topic, composer) = openEditorFromComposer("a **bold** reply")
        val editor = harness.visibleFragment() as DiscourseRichEditor
        harness.layoutApp()
        assertEquals("Apply must not announce sending a post", "完成并返回",
            (privateField(editor, "sendButton") as View).contentDescription)
        harness.renderApp("discourse-rich-editor")

        composer.setFieldText("")
        harness.idle()

        clickPrivateView(editor, "sendButton")

        harness.await("the editor to close") { harness.visibleFragment() !is DiscourseRichEditor }
        assertSame("Apply closed only the editor; the owning topic is back", topic, harness.visibleFragment())

        val back = composer.getFieldText()?.toString().orEmpty()
        println("rich: composer after Apply = " + back.replace("\n", "\\n"))
        assertTrue("the formatted content came back to the composer", back.contains("**bold**"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The reader's back: the same export runs, and the exact owning topic is back underneath. The
     * field is cleared first for the same reason as Apply.
     */
    @Test
    fun theEditorsBackSavesTheRawAndReturnsTheOwningTopic() {
        val (topic, composer) = openEditorFromComposer("back **keeps** this")
        val editor = harness.visibleFragment() as DiscourseRichEditor
        harness.layoutApp()

        composer.setFieldText("")
        harness.idle()

        clickPrivateView(editor, "backButton")

        harness.await("the editor to close") { harness.visibleFragment() !is DiscourseRichEditor }
        assertSame("back returns to the owning topic", topic, harness.visibleFragment())

        val back = composer.getFieldText()?.toString().orEmpty()
        println("rich: composer after back = " + back.replace("\n", "\\n"))
        assertTrue("back saved the edited raw", back.contains("**keeps**"))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aNewTopicUsesTheSameEditorWithoutLosingItsTitleOrBody() {
        site.serveFixture("categories.json", "categories.json")
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(NewTopicFragment.create())
        val form = harness.visibleFragment() as NewTopicFragment
        form.titleField().setText("A title worth keeping")
        val composer = viewTree().filterIsInstance<ChatActivityEnterView>().last()
        composer.setFieldText("new **topic** body")
        harness.idleClock(500)
        harness.layoutApp()
        viewTree().first { it.contentDescription?.toString() == "富文本" }.performClick()
        harness.await("new-topic editor") { harness.visibleFragment() is DiscourseRichEditor }
        val editor = harness.visibleFragment() as DiscourseRichEditor
        composer.setFieldText("")
        harness.layoutApp()
        clickPrivateView(editor, "sendButton")
        harness.await("the same form") { harness.visibleFragment() === form }
        assertEquals("the title is not editor content", "A title worth keeping", form.titleField().text.toString())
        assertEquals("the formatted body came back", "new **topic** body", composer.getFieldText()?.toString())
        harness.renderApp("new-topic-rich-body")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun unsupportedEditorContentCannotDisappearThroughApplyOrBack() {
        val (_, composer) = openEditorFromComposer("the original draft")
        val editor = harness.visibleFragment() as DiscourseRichEditor
        val list = privateField(editor, "listView")!!
        @Suppress("UNCHECKED_CAST")
        val rows = privateField(list, "rows") as MutableList<org.telegram.ui.iv.BlockRow>
        rows.clear()
        rows.addAll(org.telegram.ui.iv.RichHtml.parse("<p>changed</p><img src=\"12345\">"))
        clickPrivateView(editor, "sendButton")
        assertSame("unsupported Apply keeps the editor open", editor, harness.visibleFragment())
        clickPrivateView(editor, "backButton")
        assertSame("back cannot discard content that cannot be saved", editor, harness.visibleFragment())
        assertEquals("the prior draft is untouched", "the original draft", composer.getFieldText()?.toString())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun pausingAnOpenEditorKeepsItsLatestChangesWithoutRequiringApply() {
        val (_, composer) = openEditorFromComposer("original")
        val editor = harness.visibleFragment() as DiscourseRichEditor
        val list = privateField(editor, "listView")!!
        @Suppress("UNCHECKED_CAST")
        val rows = privateField(list, "rows") as MutableList<org.telegram.ui.iv.BlockRow>
        rows.clear()
        rows.addAll(org.telegram.ui.iv.RichHtml.parse("<p>background <b>edit</b></p>"))
        editor.onPause()
        assertEquals("backgrounding does not lose edits made after opening the editor",
            "background **edit**", composer.getFieldText()?.toString())
        assertSame("saving on pause does not close the editor", editor, harness.visibleFragment())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Opens the editor the way the reader does - our own strip's entry on the composer - and hands
     * back the exact owning topic and its composer.
     */
    private fun openEditorFromComposer(raw: String): Pair<TopicFragment, ChatActivityEnterView> {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Editor"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.layoutApp()

        val topic = harness.visibleFragment() as TopicFragment
        val composer = viewTree().filterIsInstance<ChatActivityEnterView>().lastOrNull()
            ?: throw AssertionError("the screen has no composer")
        composer.setFieldText(raw)
        harness.idle()
        harness.layoutApp()

        val entry = editorsEntry() ?: throw AssertionError("the screen offers no rich-editor entry")
        println("rich: entry = ${entry.text}")
        entry.performClick()
        harness.idle()

        harness.await("the real upstream editor to open") { harness.visibleFragment() is DiscourseRichEditor }
        harness.layoutApp()
        println("rich: editor = ${harness.visibleFragment()?.javaClass?.name}")
        return topic to composer
    }

    /** A click on one of the editor's own private [View] fields, the button the reader presses. */
    private fun clickPrivateView(editor: Any, name: String) {
        val view = privateField(editor, name) as? View
            ?: throw AssertionError("the editor has no $name")
        view.performClick()
        harness.idle()
    }

    private fun privateField(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(name).apply { isAccessible = true }
                return field.get(target)
            } catch (ignored: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    /** Our own rich-editor entry on the composer's strip, by the label it shows. */
    private fun editorsEntry(): TextView? =
        viewTree().filterIsInstance<TextView>().firstOrNull { it.text?.toString() == "富文本" }

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

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private companion object {
        const val TOPIC_ID = 2790689L
    }
}
