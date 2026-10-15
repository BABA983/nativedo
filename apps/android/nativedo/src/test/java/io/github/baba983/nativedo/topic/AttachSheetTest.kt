package io.github.baba983.nativedo.topic

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.Switch

/**
 * The Telegram-shaped attachment sheet, end to end.
 *
 * <p>The failures this pins: a paperclip that drops the reader straight into a bare file picker
 * with no way to reach the rich editor or a poll; a poll written as a Telegram poll message instead
 * of the site's own raw; a sheet that expands onto a blank area because every tab leaves it; and an
 * upload that loses the reader's draft when the site refuses it. Every case drives the shipped flow
 * - the composer's own paperclip or the reply strip, the sheet's own tab list, the sheet's inline
 * poll form, the site's own upload endpoint - and the inline-poll cases end at the field the host
 * writes the raw into, so they pin the host's own [DiscourseAttachSheet.PollSink] wiring rather than
 * a stand-in for it. The drag is a dispatched touch on the sheet's own container, never a private
 * height setter.</p>
 */
@RunWith(RobolectricTestRunner::class)
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
internal class AttachSheetTest {

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
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun thePaperclipOpensTheSheetNotABarePicker() {
        openTopic()
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.getAttachButton().performClick()
        harness.idle()

        val started = shadowOf(harness.activity).nextStartedActivityForResult
        assertTrue(
            "the paperclip must not drop the reader straight into a picker",
            started == null || started.intent.action != Intent.ACTION_OPEN_DOCUMENT,
        )
        val sheet = sheet() ?: throw AssertionError("the paperclip opened no sheet")
        println("sheet: tabs = " + tabList(sheet).adapter?.itemCount)
        assertEquals("the four actions the site can carry", 4, tabList(sheet).adapter?.itemCount)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun galleryAsksForImagesAndTheSitesMarkdownLandsInTheField() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        openSheet(composer)
        chooseTab(GALLERY_POSITION)

        pickFile("screenshot.png", transparentPng(), expectedType = "image/*")

        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }
        assertEquals(
            "the field gets the site's own markdown",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            composer.fieldText.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theFileTabTakesAnythingTheProviderCanOpen() {
        val composer = openTopicWithComposer()
        openSheet(composer)
        chooseTab(FILES_POSITION)

        val requested = shadowOf(harness.activity).nextStartedActivityForResult
            ?: throw AssertionError("the file tab opened no picker")
        assertEquals("the file action takes any file", "*/*", requested.intent.type)
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun cancellingTheSheetKeepsTheDraft() {
        val composer = openTopicWithComposer()
        composer.setFieldText("一段还没写完的回复")
        harness.idle()

        // The reply strip is the entry once the field has text; it opens the same sheet.
        val strip = attachStrip() ?: throw AssertionError("the screen has no attach strip")
        strip.performClick()
        harness.idle()
        val sheet = sheet() ?: throw AssertionError("the strip opened no sheet")
        sheet.dismiss()
        harness.idle()

        assertEquals("cancelling the sheet leaves the field alone", "一段还没写完的回复", composer.fieldText.toString())
        assertTrue("and uploads nothing", site.requestPaths.none { it == "uploads.json" })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRejectedUploadKeepsTheDraftAndSaysSo() {
        site.serveResponse("uploads.json") {
            okhttp3.mockwebserver.MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("error-422-post.json"))
        }
        val composer = openTopicWithComposer()
        composer.setFieldText("保留我写的内容")
        harness.idle()
        val strip = attachStrip() ?: throw AssertionError("the screen has no attach strip")
        strip.performClick()
        harness.idle()
        chooseTab(FILES_POSITION)
        pickFile("broken.png", transparentPng())

        harness.await("the site to refuse the upload") { site.requestPaths.contains("uploads.json") }
        harness.await("the reader to be told") { shownText().any { it.contains("回复失败") } }
        println("upload: reader told " + shownText().filter { it.contains("回复失败") })
        assertEquals(
            "a refused upload does not touch the draft",
            "保留我写的内容",
            composer.fieldText.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aLocalUriUploadsWithoutAnyPicker() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        val fragment = harness.visibleFragment() as TopicFragment
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/recorded.m4a")
        shadowOf(harness.activity.contentResolver).registerInputStream(uri, "recorded".toByteArray().inputStream())

        // The seam a recorder drives: no Activity result, no picker, just the URI.
        fragment.attachMediaUri(uri)

        harness.await("the field to hold the uploaded file") {
            !composer.getFieldText().isNullOrEmpty()
        }
        assertNull("no picker was opened", shadowOf(harness.activity).nextStartedActivityForResult)
        assertTrue("the upload went to the site", site.requestPaths.contains("uploads.json"))
        harness.assertNoTelegramSideEffects()
    }

    // --------------------------------------------------------------- the inline poll

    /**
     * The shipped paperclip, the inline poll. The sheet opens compact (the tab row, the poll
     * highlighted), and tapping Poll grows the sheet onto the form - no second popup. This is where
     * the compact and inline-poll captures live.
     */
    @Test
    fun thePollTabKeepsItsFormInTheSheetInsteadOfAPopup() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet(composer)
        assertFalse("the sheet opens compact", sheet.isExpanded)
        assertFalse(
            "and the form is clipped behind the tab row, not floating in its own window",
            pollScroller(sheet).height > 0,
        )
        renderSheet(sheet, "attach-sheet-compact")
        // The compact sheet is exactly the handle and the tab row; the row must sit wholly inside
        // the sheet, or the reader sees the actions with their captions cut off at the sheet edge.
        assertCaptionsFullyVisible(sheet)

        tabList(sheet).clickItem(null, POLL_POSITION)
        harness.idle()
        layoutDialog(sheet)

        assertTrue("tapping Poll grows the sheet", sheet.isExpanded)
        assertTrue("onto the form, above the tab row", pollScroller(sheet).height > 0)
        assertTrue("with the question field in it", pollFields(sheet).isNotEmpty())
        assertTrue(
            "and the sheet is still the latest dialog - the poll never became a second popup",
            ShadowDialog.getLatestDialog() === sheet,
        )
        renderSheet(sheet, "attach-sheet-poll")
        assertCaptionsFullyVisible(sheet)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The same paperclip, end to end: the sheet's own inline poll form is filled and its Create
     * pressed, and what the reply field receives is the site's own poll raw. This is the host's
     * [DiscourseAttachSheet.PollSink] wiring, not a captured stand-in for it.
     */
    @Test
    fun theInlinePollWritesTheSitesOwnRawIntoTheReplyField() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet(composer)
        expandByTab(sheet)

        val fields = pollFields(sheet)
        assertEquals("a question field and at least two option fields", 3, fields.size)
        fields[0].setText("周末怎么过")
        fields[1].setText("宅家")
        fields[2].setText("出门")
        createButton(sheet).performClick()
        harness.idle()

        val field = composer.fieldText.toString()
        println("poll: field = $field")
        assertTrue(
            "the site's own poll opening tag, never a Telegram poll message",
            field.startsWith("[poll type=regular results=always chartType=bar]\n"),
        )
        assertTrue("the question is the poll's title heading", field.contains("# 周末怎么过"))
        assertTrue("each option is a bullet", field.contains("* 宅家") && field.contains("* 出门"))
        assertTrue("and the poll closes", field.contains("[/poll]"))
        assertEquals(
            "the site's raw, exactly, on the composer's own line",
            "$POLL_RAW\n",
            field,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun thePollSettingsChangeTheRawTheFormWrites() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet(composer)
        expandByTab(sheet)

        val fields = pollFields(sheet)
        fields[0].setText("多选")
        fields[1].setText("甲")
        fields[2].setText("乙")
        val multiple = viewsUnder(sheet.window!!.decorView).filterIsInstance<Switch>().firstOrNull()
            ?: throw AssertionError("the form has no settings switch")
        multiple.setChecked(true, false)
        createButton(sheet).performClick()
        harness.idle()

        assertEquals(
            "the multiple-answers setting is the site's own poll type",
            "[poll type=multiple results=always chartType=bar]\n# 多选\n* 甲\n* 乙\n[/poll]\n",
            composer.fieldText.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun thePollFormNamesTheMissingQuestionAndTheMissingOptionsSeparately() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet(composer)
        expandByTab(sheet)
        val fields = pollFields(sheet)

        // A question but only one option: the option rule fails and nothing is written.
        fields[0].setText("只有标题")
        fields[1].setText("唯一选项")
        createButton(sheet).performClick()
        harness.idle()
        assertTrue(
            "the reader is told a poll needs two options",
            texts(sheet.window!!.decorView).any { it.contains("至少需要两个选项") },
        )
        assertTrue("nothing reached the field", composer.getFieldText().isNullOrEmpty())
        assertTrue("and the sheet stays for the reader", sheet.isShowing)

        // Two options but no question: the question rule fails, separately.
        fields[0].setText("")
        fields[2].setText("第二个")
        createButton(sheet).performClick()
        harness.idle()
        assertTrue(
            "the reader is told a poll needs a question",
            texts(sheet.window!!.decorView).any { it.contains("需要一个标题") },
        )
        assertTrue("still nothing reached the field", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun cancellingTheInlinePollWritesNothing() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet(composer)
        expandByTab(sheet)
        val fields = pollFields(sheet)
        fields[0].setText("半途而废")
        fields[1].setText("一")
        fields[2].setText("二")

        sheet.dismiss()
        harness.idle()

        assertTrue("dismissing the sheet writes no poll", composer.getFieldText().isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The sheet's own drag: a finger dispatched on its container, up onto the form and back down
     * onto the compact row. It is a real touch stream, not a private height setter. The expanded
     * capture lives here.
     */
    @Test
    fun theSheetExpandsByDraggingToFullHeightOntoItsContent() {
        val composer = openTopicWithComposer()
        val sheet = openShippedSheet(composer)
        val compactHeight = sheet.sheetContainer.measuredHeight
        assertFalse("it opens compact", sheet.isExpanded)
        assertFalse("with its content clipped", pollScroller(sheet).height > 0)

        // A finger starting on the compact row and travelling to the top of the screen.
        dragSheet(sheet, fromY = harness.height - 100f, toY = 100f)
        assertTrue("a drag up expands the sheet", sheet.isExpanded)
        layoutDialog(sheet)
        println("sheet: compact=$compactHeight expanded=" + sheet.isExpanded + " height=" + sheet.sheetContainer.measuredHeight)
        assertTrue(
            "and the sheet is taller than its compact row",
            sheet.sheetContainer.measuredHeight > compactHeight,
        )
        assertTrue("and the expanded area is the poll form, not a blank panel", pollScroller(sheet).height > 0)
        renderSheet(sheet, "attach-sheet-expanded")

        dragSheet(sheet, fromY = 100f, toY = harness.height - 100f)
        assertFalse("a drag back down settles it compact", sheet.isExpanded)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun openTopic() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Attach"))
        harness.await("the topic to load") { rowCount() == 1 }
    }

    private fun openTopicWithComposer(): ChatActivityEnterView {
        openTopic()
        return composer() ?: throw AssertionError("the screen has no composer")
    }

    private fun openSheet(composer: ChatActivityEnterView) {
        composer.getAttachButton().performClick()
        harness.idle()
        if (sheet() == null) {
            throw AssertionError("the paperclip opened no sheet")
        }
    }

    /**
     * The sheet as the app itself opens it: the composer's own paperclip, the host's own poll sink.
     * This is the shipped wiring - no directly-constructed stand-in for it.
     */
    private fun openShippedSheet(composer: ChatActivityEnterView): DiscourseAttachSheet {
        composer.getAttachButton().performClick()
        harness.idle()
        // The sheet opens with upstream's own animation; run the simulated clock to its end so a
        // render captures the settled sheet rather than a frame of the open animation.
        harness.idleClock(400)
        val sheet = sheet() ?: throw AssertionError("the paperclip opened no sheet")
        layoutDialog(sheet)
        return sheet
    }

    /** Expands the sheet the way its own tab does: a tap on the Poll tab. */
    private fun expandByTab(sheet: DiscourseAttachSheet) {
        tabList(sheet).clickItem(null, POLL_POSITION)
        harness.idle()
        layoutDialog(sheet)
    }

    /** The attachment sheet, as the latest dialog. */
    private fun sheet(): DiscourseAttachSheet? =
        (ShadowDialog.getLatestDialog() as? DiscourseAttachSheet)

    private fun tabList(sheet: DiscourseAttachSheet): RecyclerListView =
        viewsUnder(sheet.window!!.decorView).filterIsInstance<RecyclerListView>().firstOrNull()
            ?: throw AssertionError("the sheet has no tab list")

    /**
     * The tab row and each of its captions must be wholly inside the sheet. The row is the last
     * thing in the sheet's column, so a content height that does not fit it clips the row - and the
     * captions at its foot - against the sheet edge. A full global visible rect is what "the reader
     * can read the action" means, so it is asserted directly rather than inferred from a number.
     */
    private fun assertCaptionsFullyVisible(sheet: DiscourseAttachSheet) {
        val row = tabList(sheet)
        val rowBounds = Rect()
        assertTrue("the tab row is on screen", row.getGlobalVisibleRect(rowBounds))
        assertEquals("the tab row is not clipped horizontally", row.width, rowBounds.width())
        assertEquals("the tab row is not clipped vertically", row.height, rowBounds.height())

        val captions = viewsUnder(sheet.window!!.decorView)
            .filterIsInstance<TextView>()
            .filter { it.text?.toString().orEmpty() in TAB_LABELS }
        assertEquals("a caption for each action", TAB_LABELS.size, captions.size)
        val bounds = Rect()
        for (caption in captions) {
            val label = caption.text.toString()
            assertTrue("the $label caption is on screen", caption.getGlobalVisibleRect(bounds))
            assertEquals("the $label caption is not clipped horizontally", caption.width, bounds.width())
            assertEquals("the $label caption is not clipped vertically", caption.height, bounds.height())
        }
    }

    /** The inline poll's scrolling body; its height is whether the form is on screen. */
    private fun pollScroller(sheet: DiscourseAttachSheet): ScrollView =
        viewsUnder(sheet.window!!.decorView).filterIsInstance<ScrollView>().firstOrNull()
            ?: throw AssertionError("the sheet has no poll form")

    private fun pollFields(sheet: DiscourseAttachSheet): List<EditTextBoldCursor> =
        viewsUnder(sheet.window!!.decorView).filterIsInstance<EditTextBoldCursor>()

    /** The poll form's own Create action, by the label it draws. */
    private fun createButton(sheet: DiscourseAttachSheet): View =
        viewsUnder(sheet.window!!.decorView)
            .filterIsInstance<TextView>()
            .firstOrNull { it.text?.toString() == "创建" }
            ?: throw AssertionError("the poll form has no Create button")

    /** Fires a tab the way the shipped list does, through its own item-click callback. */
    private fun chooseTab(position: Int) {
        val sheet = sheet() ?: throw AssertionError("no sheet to choose from")
        tabList(sheet).clickItem(null, position)
        harness.idle()
    }

    /** Answers the picker the attach sheet opened, the way the system would. */
    private fun pickFile(name: String, bytes: ByteArray, expectedType: String? = null): Uri {
        val activity = harness.activity
        val requested = shadowOf(activity).nextStartedActivityForResult
            ?: throw AssertionError("the sheet's action opened no picker")
        assertEquals(
            "the picker opens a document",
            Intent.ACTION_OPEN_DOCUMENT,
            requested.intent.action,
        )
        if (expectedType != null) {
            assertEquals("the picker asks for $expectedType", expectedType, requested.intent.type)
        }
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/$name")
        shadowOf(activity.contentResolver).registerInputStream(uri, bytes.inputStream())
        shadowOf(activity).receiveResult(requested.intent, Activity.RESULT_OK, Intent().setData(uri))
        harness.idle()
        return uri
    }

    /** A drag on the sheet's own container, the way a finger reaches it. */
    private fun dragSheet(sheet: DiscourseAttachSheet, fromY: Float, toY: Float) {
        val container = sheet.container
        val start = SystemClock.uptimeMillis()
        container.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, 200f, fromY, 0))
        harness.idle(40)
        val steps = 4
        for (step in 1..steps) {
            val y = fromY + (toY - fromY) * step / steps
            container.dispatchTouchEvent(
                MotionEvent.obtain(start, start + step * 16L, MotionEvent.ACTION_MOVE, 200f, y, 0),
            )
            harness.idle(40)
        }
        container.dispatchTouchEvent(
            MotionEvent.obtain(start, start + (steps + 1) * 16L, MotionEvent.ACTION_UP, 200f, toY, 0),
        )
        harness.idle()
    }

    /** Measures and lays out the sheet's window, the way the window manager composites it. */
    private fun layoutDialog(sheet: DiscourseAttachSheet) {
        val decor = sheet.window!!.decorView
        repeat(2) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, harness.width, harness.height)
            harness.idle(60)
        }
    }

    private fun renderSheet(sheet: DiscourseAttachSheet, name: String) {
        layoutDialog(sheet)
        val decor = sheet.window!!.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(
            harness.width, harness.height, android.graphics.Bitmap.Config.ARGB_8888,
        )
        try {
            decor.draw(android.graphics.Canvas(bitmap))
            val out = repoFile(".amp/in/artifacts/nativedo/$name.png")
            out.parentFile?.mkdirs()
            java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            println("sheet render: $out")
        } finally {
            bitmap.recycle()
        }
    }

    private fun repoFile(relative: String): java.io.File {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            if (java.io.File(dir, "fixtures/discourse").isDirectory) {
                return java.io.File(dir, relative)
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root")
    }

    /** Our own attach strip, by the label it shows while it is idle. */
    private fun attachStrip(): View? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findAttachStrip(content)
    }

    private fun findAttachStrip(view: View): View? {
        if (view is TextView && view.text?.startsWith("添加") == true) return view.parent as? View
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findAttachStrip(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return viewsUnder(content).filterIsInstance<ChatActivityEnterView>().firstOrNull()
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    /** Every piece of text the screen shows, dialogs included. */
    private fun shownText(): List<String> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        val out = mutableListOf<String>()
        collectText(content, out)
        (ShadowDialog.getLatestDialog() as? android.app.Dialog)?.window?.decorView?.let { dialog ->
            collectText(dialog, out)
        }
        return out
    }

    private fun texts(root: View): List<String> {
        val out = mutableListOf<String>()
        collectText(root, out)
        return out
    }

    private fun collectText(view: View?, out: MutableList<String>) {
        when (view) {
            is TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
    }

    private fun viewsUnder(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        walk(root)
        return found
    }

    /** A 1x1 PNG: the uploader sends the file's bytes, and any real image would do. */
    private fun transparentPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT,
    )

    private companion object {
        const val TOPIC_ID = 809L
        const val GALLERY_POSITION = 0
        const val FILES_POSITION = 1
        const val POLL_POSITION = 3

        /** The four action captions the sheet's tab row draws, in its own order. */
        val TAB_LABELS = listOf("图库", "文件", "富文本", "投票")

        /** The site's own poll raw for the question and two options the reply-field case fills in. */
        const val POLL_RAW =
            "[poll type=regular results=always chartType=bar]\n# 周末怎么过\n* 宅家\n* 出门\n[/poll]"
    }
}
