package io.github.baba983.nativedo.topic

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.ui.Cells.ChatMessageCell

/**
 * A file a post links to, end to end: fetched with the session and handed to upstream as a document
 * block that can open it.
 *
 * <p>The failure this pins: Discourse writes an attachment as a link inside a paragraph, and
 * upstream's importer only turns a *block* element into a document block, so a link left in place
 * renders as ordinary text with a URL and the reader cannot tell that a file is attached.</p>
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
internal class AttachmentRenderTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveJson("t/$TOPIC_ID.json", topicJsonWithAttachment())
        site.serveImage(ATTACHMENT_PATH, ATTACHMENT_BYTES, "application/pdf")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theAttachmentBecomesADocumentBlockThatCanOpenItsFile() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Attachment post"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.await("the attachment to be fetched") { site.cookieHeaderFor(ATTACHMENT_PATH) != null }

        var opened = false
        harness.await("the document block to open the file on a tap") {
            opened = tapTheAttachment()
            opened
        }
        harness.renderApp("post-attachment")

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        val richMessage = cell.messageObject!!.messageOwner.rich_message!!
        assertEquals("the post carries one document", 1, richMessage.documents.size)
        val document = richMessage.documents[0]
        assertEquals("with the name the post gave it", "fixture-report.pdf", document.file_name)
        assertEquals("and the size of what was fetched", ATTACHMENT_BYTES.size.toLong(), document.size)
        val file = FileLoader.getInstance(0).getPathToAttach(document, false)
        assertTrue("upstream's own path for the document exists", file.exists())
        assertNotNull("the blocks include a document block", documentBlockOf(cell))
        assertTrue("the request carried the session", site.cookieHeaderFor(ATTACHMENT_PATH)!!.contains("_t="))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Taps the document block the way the cell does: the layout dispatches an event translated into
     * its own coordinates, so the block's own button decides what happens.
     */
    private fun tapTheAttachment(): Boolean {
        val cell = laidOutCells().firstOrNull() ?: return false
        val layout = cell.messageObject!!.richLayout ?: return false
        if (tapAttempts++ < 3) {
            println(
                "attachment: blocks=" + layout.blocks.map { it.javaClass.simpleName } +
                    " documents=" + cell.messageObject!!.messageOwner.rich_message!!.documents.size,
            )
        }
        val documentBlock = documentBlockOf(cell) ?: return false
        var y = 0
        for (block in layout.blocks) {
            if (block === documentBlock) break
            if (!block.isVisible()) continue
            y += heightOf(block)
        }

        val down = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,
            AndroidUtilities.dp(30f).toFloat(), (y + AndroidUtilities.dp(30f)).toFloat(), 0,
        )
        val downHandled = layout.onTouchEvent(down)
        val up = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
            AndroidUtilities.dp(30f).toFloat(), (y + AndroidUtilities.dp(30f)).toFloat(), 0,
        )
        val upHandled = layout.onTouchEvent(up)
        harness.idle()
        val viewField = fieldOf(documentBlock, "view")
        val blockView = viewField.get(documentBlock)
        val layoutView = fieldOf(layout, "view").get(layout)
        println("attachment: cellAttached=" + cell.isCellAttachedToWindow +
            " cellIsAttached=" + cell.isAttachedToWindow + " layoutView=" + layoutView)
        println("attachment: blockView=" + blockView + " context=" + (blockView as? android.view.View)?.context?.javaClass?.simpleName +
            " providerUri=" + runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    harness.activity,
                    org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider",
                    java.io.File("/tmp/does-not-matter.pdf"),
                )
            })
        println("attachment: touch handled down=$downHandled up=$upHandled onBlock=" +
            documentBlock.javaClass.getDeclaredField("pressed").let { field ->
                field.isAccessible = true
                field.get(documentBlock)
            })
        // `openForView` hands the file to the system with startActivityForResult, which is where a
        // device would leave for a viewer.
        val intent = org.robolectric.Shadows.shadowOf(harness.activity).nextStartedActivityForResult?.intent
        val started = intent != null && intent.action == android.content.Intent.ACTION_VIEW
        if (!started && tapAttempts < 6) {
            val pathMethod = documentBlock.javaClass.getDeclaredMethod("path").apply { isAccessible = true }
            val stateField = documentBlock.javaClass.getDeclaredField("buttonState").apply { isAccessible = true }
            val path = pathMethod.invoke(documentBlock) as java.io.File?
            println(
                "attachment: tap at y=$y blockHeight=" + heightOf(documentBlock) +
                    " buttonState=" + stateField.get(documentBlock) +
                    " path=" + path + " exists=" + (path != null && path.exists()) +
                    " started=" + started + " intent=" + intent,
            )
        }
        return started
    }

    private var tapAttempts = 0

    private fun documentBlockOf(cell: ChatMessageCell): Any? = cell.messageObject!!.richLayout
        .blocks
        .firstOrNull { it.javaClass.simpleName == "RichDocumentBlock" }

    /** A field of the block, wherever in its hierarchy it is declared. */
    private fun fieldOf(block: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = block.javaClass
        while (type != null) {
            runCatching { return type.getDeclaredField(name).apply { isAccessible = true } }
            type = type.superclass
        }
        throw NoSuchFieldException(name)
    }

    private fun heightOf(block: Any): Int =
        block.javaClass.getMethod("getHeight").invoke(block) as Int

    private fun topicJsonWithAttachment(): String {
        val base = org.json.JSONObject(harness.fixture("topic-detail.json"))
        val posts = base.getJSONObject("post_stream").getJSONArray("posts")
        posts.getJSONObject(0).put(
            "cooked",
            "<p><a class=\"attachment\" href=\"/$ATTACHMENT_PATH\">fixture-report.pdf</a> " +
                "(${ATTACHMENT_BYTES.size} bytes)</p>",
        )
        return base.toString()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findRecycler(content)
    }

    private fun findRecycler(view: android.view.View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecycler(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val TOPIC_ID = 505L
        const val ATTACHMENT_PATH = "uploads/fixture-report.pdf"
        val ATTACHMENT_BYTES = "%PDF-1.4 fixture".toByteArray()
    }
}
