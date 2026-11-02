package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.iv.RichEditorListView
import org.telegram.ui.iv.RichEditorToolbar
import java.io.File
import java.io.FileOutputStream

/**
 * The rich editor the reader can actually reach, and what its own buttons hand back.
 *
 * <p>The failures this pins: a "rich editor" that is a bespoke copy of the toolbar (so upstream's
 * formatting drifts), one that is opened but whose content never reaches the composer, and - the
 * bug that prompted this - an editor whose Apply closes the screen that opened it. The real
 * upstream article pane opens inside [DiscourseAttachSheet] from the reader's own strip. Its Apply,
 * back and pause paths must preserve the draft without closing the owning screen or using
 * Telegram's send/draft paths.</p>
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
        (ShadowDialog.getLatestDialog() as? DiscourseAttachSheet)?.dismiss()
        harness.idleClock(500)
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
        val editor = editorSheet()
        assertEquals("Apply must not announce sending a post", "完成并返回",
            editorToolbar().getSendButton().contentDescription)

        composer.setFieldText("")
        harness.idle()

        editorToolbar().getSendButton().performClick()
        harness.idleClock(500)

        harness.await("the editor to close") { !editor.isShowing }
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
        val editor = editorSheet()

        composer.setFieldText("")
        harness.idle()

        editor.selectAction(DiscourseAttachSheet.Action.POLL)
        harness.idleClock(500)
        assertTrue("switching panes saves the article", composer.getFieldText().toString().contains("**keeps**"))
        editor.selectAction(DiscourseAttachSheet.Action.RICH_EDITOR)
        harness.idleClock(500)
        composer.setFieldText("")
        editor.onBackPressed()
        harness.idleClock(500)

        harness.await("the editor to close") { !editor.isShowing }
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
        harness.idleClock(500)
        val editor = editorSheet()
        composer.setFieldText("")
        editorToolbar().getSendButton().performClick()
        harness.idleClock(500)
        harness.await("the editor to close") { !editor.isShowing }
        assertSame("Apply leaves the same form open", form, harness.visibleFragment())
        assertEquals("the title is not editor content", "A title worth keeping", form.titleField().text.toString())
        assertEquals("the formatted body came back", "new **topic** body", composer.getFieldText()?.toString())
        harness.renderApp("new-topic-rich-body")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun unsupportedEditorContentCannotDisappearThroughApplyOrBack() {
        val (_, composer) = openEditorFromComposer("the original draft")
        val editor = editorSheet()
        // Inject a Telegram-only block which the normal paste path would resolve or filter out.
        replaceEditorHtml("<p>changed</p><img src=\"12345\">")
        editorToolbar().getSendButton().performClick()
        harness.idleClock(500)
        assertTrue("unsupported Apply keeps the editor open", editor.isShowing)
        editor.selectAction(DiscourseAttachSheet.Action.POLL)
        harness.idleClock(500)
        assertTrue("switching tabs cannot discard unsupported content", editorList().isShown)
        editor.onBackPressed()
        harness.idleClock(500)
        assertTrue("back cannot discard content that cannot be saved", editor.isShowing)
        assertEquals("the prior draft is untouched", "the original draft", composer.getFieldText()?.toString())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pane's own top-left back button: the reader's exit, and the entry point the reader asked
     * for. It is upstream's own button - rounded, shadowed, accessibility-labelled - shown only for
     * this client's hosted article pane. Pressing it saves the supported draft and closes the pane
     * to the owning topic, the same ending as the sheet's own back, not Telegram's draft dialog.
     *
     * <p>The captures are the half-height and expanded sheet heights, both showing the button the
     * reader reaches for. Scrolling the pane's own list up is the reader's expand gesture, so it is
     * the shipped transition, not a hand-set state.</p>
     */
    @Test
    fun theEditorsTopBackButtonSavesTheDraftAndReturnsTheOwningTopic() {
        // Long enough that the pane's own list can scroll: the reader's expand gesture is what
        // lifts the sheet, so a short article would never show the second state.
        val seed = "top **back** keeps this\n\n" +
            (1..24).joinToString("\n\n") { "paragraph $it with enough words to fill its own line" }
        val (topic, composer) = openEditorFromComposer(seed)
        val editor = editorSheet()

        // Half height first, then the reader's own scroll to expand: both are the states the reader
        // sees the top button in.
        harness.idleClock(SETTLE_MS)
        captureSheet(editor, "attachment-navigation/article-half-height")
        val firstText = descendants(editorList()).filterIsInstance<android.widget.EditText>()
            .first { it.text.toString().startsWith("top") }
        val textBounds = android.graphics.Rect()
        val backBounds = android.graphics.Rect()
        firstText.getGlobalVisibleRect(textBounds)
        editorToolbarBackButton().getGlobalVisibleRect(backBounds)
        assertTrue("the visible back button must not cover the first editable line",
            !android.graphics.Rect.intersects(textBounds, backBounds))
        val tabs = org.telegram.ui.Components.ChatAttachAlert::class.java
            .getDeclaredField("buttonsRecyclerViewWrapper").apply { isAccessible = true }
            .get(editor) as View
        assertTrue("article formatting and attachment tabs must not overlap", !tabs.isShown || tabs.alpha == 0f)
        val list = editorList()
        // Remove the opening spacer without also scrolling the article itself under the floating controls.
        list.scrollBy(0, list.paddingTop)
        harness.idle()
        harness.idleClock(SETTLE_MS)
        captureSheet(editor, "attachment-navigation/article-expanded")
        firstText.getGlobalVisibleRect(textBounds)
        editorToolbarBackButton().getGlobalVisibleRect(backBounds)
        assertTrue("the expanded article begins below the top controls",
            !android.graphics.Rect.intersects(textBounds, backBounds))
        assertEquals("the expanded back control is not clipped at the screen edge",
            editorToolbarBackButton().height, backBounds.height())

        composer.setFieldText("")
        harness.idle()

        val back = editorToolbarBackButton()
        assertTrue("the hosted article pane shows its own top back button", back.isShown)
        back.performClick()
        harness.idleClock(500)

        harness.await("the editor to close") { !editor.isShowing }
        assertSame("the top back button returns to the owning topic", topic, harness.visibleFragment())

        val after = composer.getFieldText()?.toString().orEmpty()
        println("rich: composer after top back = " + after.replace("\n", "\\n"))
        assertTrue("the top back button saved the supported draft", after.contains("**back**"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Unsupported content is the one thing the pane must not let a back press discard. The top
     * button then answers exactly like the sheet's own back: the editor stays open and the
     * composer's prior draft is untouched.
     */
    @Test
    fun theEditorsTopBackButtonCannotDiscardUnsupportedContent() {
        val (_, composer) = openEditorFromComposer("the original draft")
        val editor = editorSheet()
        // Inject a Telegram-only block which the normal paste path would resolve or filter out.
        replaceEditorHtml("<p>changed</p><img src=\"12345\">")

        val back = editorToolbarBackButton()
        assertTrue("the hosted article pane shows its own top back button", back.isShown)
        back.performClick()
        harness.idleClock(500)

        assertTrue("unsupported content keeps the editor open", editor.isShowing)
        assertEquals("the top back button did not lose the prior draft",
            "the original draft", composer.getFieldText()?.toString())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun pausingAnOpenEditorKeepsItsLatestChangesWithoutRequiringApply() {
        val (topic, composer) = openEditorFromComposer("original")
        val editor = editorSheet()
        replaceEditorHtml("<p>background <b>edit</b></p>")
        topic.onPause()
        assertEquals("backgrounding does not lose edits made after opening the editor",
            "background **edit**", composer.getFieldText()?.toString())
        assertTrue("saving on pause does not close the editor", editor.isShowing)
        topic.onResume()
        editor.selectAction(DiscourseAttachSheet.Action.POLL)
        harness.idleClock(500)
        composer.setFieldText("newer draft in a different pane")
        topic.onPause()
        assertEquals("a hidden article cannot overwrite a newer draft on pause",
            "newer draft in a different pane", composer.getFieldText()?.toString())
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

        harness.idleClock(500)
        assertTrue("the real upstream editor pane is open", editorList().isShown)
        return topic to composer
    }

    private fun editorSheet(): DiscourseAttachSheet =
        ShadowDialog.getLatestDialog() as? DiscourseAttachSheet
            ?: throw AssertionError("the composer opened no attachment sheet")

    private fun editorToolbar(): RichEditorToolbar =
        descendants(editorSheet().window!!.decorView).filterIsInstance<RichEditorToolbar>().first()

    private fun editorList(): RichEditorListView =
        descendants(editorSheet().window!!.decorView).filterIsInstance<RichEditorListView>().first()

    /** The toolbar's own top-left back button, by the accessibility label upstream gives it. */
    private fun editorToolbarBackButton(): ImageView {
        val label = org.telegram.messenger.LocaleController.getString(
            org.telegram.messenger.R.string.AccDescrGoBack,
        )
        return descendants(editorToolbar()).filterIsInstance<ImageView>()
            .firstOrNull { it.contentDescription?.toString() == label }
            ?: throw AssertionError("the editor toolbar has no back button")
    }

    /**
     * Draws the sheet's real window and writes it under
     * `.amp/in/artifacts/nativedo/attachment-navigation/`, the way the window manager composites
     * it - two measure passes so the pane's list has bound its rows before the draw.
     */
    private fun captureSheet(sheet: DiscourseAttachSheet, name: String) {
        val decor = sheet.window!!.decorView
        repeat(2) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, harness.width, harness.height)
            harness.idle(120)
        }
        val bitmap = Bitmap.createBitmap(harness.width, harness.height, Bitmap.Config.ARGB_8888)
        try {
            decor.draw(Canvas(bitmap))
            val out = repoFile(".amp/in/artifacts/nativedo/$name.png")
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("rich editor capture: $out (${out.length()} bytes)")
        } finally {
            bitmap.recycle()
        }
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            if (File(dir, "fixtures/discourse").isDirectory) {
                return File(dir, relative)
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root from ${System.getProperty("user.dir")}")
    }

    private fun replaceEditorHtml(html: String) {
        @Suppress("UNCHECKED_CAST")
        val rows = RichEditorListView::class.java.getDeclaredField("rows")
            .apply { isAccessible = true }.get(editorList()) as MutableList<org.telegram.ui.iv.BlockRow>
        rows.clear()
        rows.addAll(org.telegram.ui.iv.RichHtml.parse(html))
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

    private fun rowCount(): Int = contentRowCount(harness)

    private companion object {
        const val TOPIC_ID = 2790689L
        const val SETTLE_MS = 400L
    }
}
