package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * A wide Discourse table must stay inside the bubble and scroll horizontally inside it.
 *
 * This is the regression test for a configuration mismatch, not for a rendering bug: the stock
 * cell indents the bubble by an avatar when it draws one, while `MessageObject` derives the text
 * width from its own avatar flag. A synthetic message from a user peer reports no avatar, so the
 * layout was measured for a full-width bubble and then drawn inset, which pushed the right edge
 * past the viewport — invisible with a narrow body, obvious with a table.
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
internal class WideTableLayoutTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveCooked(TOPIC_ID, WIDE_TABLE)
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        site.stop()
        NativeDo.setContainerFactory { context -> AppContainer(context) }
    }

    @Test
    fun wideTableStaysInsideTheBubble() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Wide table"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.renderApp("wide-table")

        val cell = laidOutChildren().filterIsInstance<ChatMessageCell>().firstOrNull()
            ?: throw AssertionError("the topic drew no ChatMessageCell")
        val viewport = harness.width
        val bubbleLeft = cell.backgroundDrawableLeft
        val bubbleRight = cell.backgroundDrawableRight
        println("wide-table: viewport=$viewport bubbleLeft=$bubbleLeft bubbleRight=$bubbleRight")

        // getBackgroundDrawableRight() returns the right edge, not a width.
        assertTrue(
            "the bubble must not extend past the viewport: right=$bubbleRight viewport=$viewport",
            bubbleRight <= viewport,
        )
        assertTrue("the bubble must start inside the viewport", bubbleLeft >= 0)

        val blocks = cell.messageObject!!.messageOwner.rich_message!!.blocks
        val table = blocks.filterIsInstance<TL_iv.pageBlockTable>().firstOrNull()
            ?: throw AssertionError("the wide table did not survive as a table block")
        println("wide-table: rows=" + table.rows.size + " columns=" + table.rows[0].cells.size)
        assertTrue("the fixture is wider than the bubble", table.rows[0].cells.size >= 3)

        // Upstream keeps a table's full width and scrolls it inside the bubble, so the table's
        // scroll range is the evidence that the wide content is reachable and not just cut off.
        val maxScrollX = tableScrollRange(cell)
        println("wide-table: table maxScrollX=$maxScrollX")
        assertTrue("a wide table must be scrollable inside the bubble", maxScrollX > 0)

        harness.assertNoTelegramSideEffects()
    }

    /** Reads the table block's scroll range out of the rich layout. */
    private fun tableScrollRange(cell: ChatMessageCell): Int {
        val layout = cell.messageObject!!.richLayout
        val blocksField = layout.javaClass.getDeclaredField("blocks").apply { isAccessible = true }
        val layoutBlocks = blocksField.get(layout) as List<*>
        for (block in layoutBlocks) {
            if (block!!.javaClass.simpleName != "RichTableBlock") continue
            val field = block.javaClass.getDeclaredField("maxScrollX").apply { isAccessible = true }
            return field.getInt(block)
        }
        throw AssertionError("the rich layout holds no table block")
    }

    // ------------------------------------------------------------------ view traversal helpers

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutChildren(): List<View> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private companion object {
        const val TOPIC_ID = 101L

        /** Five columns, so the natural width exceeds any phone viewport. */
        val WIDE_TABLE = """
            <div class="md-table"><table>
            <thead><tr><th>Post</th><th>Author</th><th>Replies</th><th>Views</th><th>Last activity</th></tr></thead>
            <tbody>
            <tr><td>1</td><td>alice</td><td>12</td><td>345</td><td>2026-03-31</td></tr>
            <tr><td>2</td><td>bob</td><td>7</td><td>128</td><td>2026-03-30</td></tr>
            </tbody></table></div>
            <p>After the table.</p>
        """.trimIndent()
    }
}