package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.DialogCell

/**
 * The two screens of the thin slice, driven end to end: `fixtures/discourse` is served over a
 * local HTTP server, the real client fetches and maps it, and the stock cells draw the result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    // Upstream's packages are not instrumented by default; without this the natives in
    // libtmessages.49.so stay real natives and every cell dies on UnsatisfiedLinkError.
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
internal class TopicScreensTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serve(TOPIC_ID, "topic-detail.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        site.stop()
        NativeDo.setContainerFactory { context -> AppContainer(context) }
    }

    /** /latest.json -> DialogCell.CustomDialog rows inside a RecyclerListView. */
    @Test
    fun topicListShowsLatestTopics() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.renderApp("topic-list")

        val rows = laidOutChildren().filterIsInstance<DialogCell>()
        assertEquals("the list drew one row per topic", 2, rows.size)
        harness.assertNoTelegramSideEffects()
    }

    /** Tapping a row opens the topic, whose posts are drawn by the stock ChatMessageCell. */
    @Test
    fun topicOpensAndRendersPosts() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Welcome to NativeDo Android"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.renderApp("topic-detail")

        val cell = laidOutChildren().filterIsInstance<ChatMessageCell>().firstOrNull()
            ?: throw AssertionError("the topic drew no ChatMessageCell")
        val richMessage = cell.messageObject?.messageOwner?.rich_message
        assertTrue("the post reached the cell as a rich body", richMessage != null)
        assertTrue("the body parsed into blocks", richMessage!!.blocks.isNotEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /** A complex Discourse body through the whole screen: the table must arrive as a table block. */
    @Test
    fun topicRendersRichDiscourseBody() {
        val cooked = harness.fixture("cooked-table-lightbox-blockquote.html")
        site.serveCooked(TOPIC_ID, cooked)

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Rich body"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.renderApp("topic-rich-body")

        val cell = laidOutChildren().filterIsInstance<ChatMessageCell>().firstOrNull()
            ?: throw AssertionError("the topic drew no ChatMessageCell")
        val blocks = cell.messageObject!!.messageOwner.rich_message!!.blocks
        println("screen: body blocks = " + blocks.map { it.javaClass.simpleName })
        assertTrue(
            "the table survived the whole chain as a table block",
            blocks.any { it is TL_iv.pageBlockTable },
        )
        // Recorded as evidence: the bubble has to stay inside the viewport even when a table inside
        // it is wider than the bubble, in which case the table scrolls within its own block.
        val list = listView()!!
        val richLayout = cell.messageObject!!.richLayout
        println(
            "screen: viewport=" + harness.width + " list=" + list.width + " cell=" + cell.width +
                " bubbleLeft=" + cell.backgroundDrawableLeft + " bubbleRight=" + cell.backgroundDrawableRight +
                " richMinWidth=" + richLayout.getMinWidth(),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ view traversal helpers

    private fun listView(): RecyclerView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findRecycler(content)
    }

    private fun findRecycler(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecycler(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** What the adapter holds, which is what the fetch produced. */
    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    /** What the list actually laid out and drew, i.e. what the screenshot shows. */
    private fun laidOutChildren(): List<View> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private companion object {
        const val TOPIC_ID = 101L
    }
}