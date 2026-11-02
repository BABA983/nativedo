package io.github.baba983.nativedo.topic

import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
import io.github.baba983.nativedo.harness.menuItemLabels
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
import org.telegram.ui.Components.RecyclerListView

/**
 * The post menu: long press asks the cell's delegate, which is where upstream tells a client that a
 * message was long-pressed, and the answer is upstream's own scrim popup anchored on the bubble.
 *
 * <p>The failure this pins: a post body is upstream's rich article, whose own long-press handling
 * belongs to its text selection, so a client that never answers the delegate leaves a long press on
 * the rest of the message doing nothing at all.</p>
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
internal class PostMenuTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun longPressShowsThePostMenuAndCopyTakesThePostText() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Menu"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        val rows = latestItemOptionsMenu(harness.activity)
        val items = menuItemLabels(rows)
        println("menu: items = $items")
        assertEquals("the menu offers the things a reader does", 6, items.size)
        assertTrue("with copy", items.any { it.contains("复制") })
        assertTrue("a link", items.any { it.contains("链接") })
        assertTrue("sharing", items.any { it.contains("分享") })
        assertTrue("a bookmark", items.any { it.contains("书签") })
        assertTrue("reply", items.any { it.contains("回复") })
        assertTrue("and the post's text", items.any { it.contains("选择文本") })

        // The first entry copies the post's text, not its markup.
        clickMenuItem(rows, "复制")
        harness.idle()
        val clipboard = harness.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        println("menu: clipboard after copy = $copied")
        assertEquals("the post's text is on the clipboard", "Hello from a linux.do post.", copied)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The selection the menu starts is upstream's own: the helper upstream's cells hand their
     * touches to is in selection mode over this post, the whole body is picked, and the overlay it
     * draws sits over the list without taking touches that are not its handles - a scroll that
     * landed on the overlay has to reach the list, or a selected post would freeze the screen.
     */
    @Test
    fun theMenuStartsATextSelectionOverThePostAndTheOverlayLeavesTheListItsScroll() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val topic = TopicFragment.forTopic(TOPIC_ID, "Select")
        harness.present(topic)
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "选择文本")
        harness.idle()

        val selection = cell.delegate.textSelectionHelper
            ?: throw AssertionError("the cell has no selection helper")
        println("select: inSelection=" + selection.isInSelectionMode()
            + " rich=" + selection.isRich
            + " start=" + selection.selectionStart + " end=" + selection.selectionEnd)
        assertTrue("the menu started a selection", selection.isInSelectionMode())
        assertTrue("over the bubble's rich text, or nothing would be drawn", selection.isRich)
        assertEquals("the whole post is picked", 0, selection.selectionStart)
        assertEquals(
            "up to its own text",
            "Hello from a linux.do post.".length,
            selection.selectionEnd,
        )
        assertEquals("over the bubble the menu was opened on", cell, selection.getSelectedCell())

        val overlay = findOverlay(harness.activity.findViewById(android.R.id.content))
        assertTrue("the overlay is where the fragment draws", overlay != null)
        val away = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 4f, 4f, 0)
        assertTrue("a touch away from the handles is not the overlay's",
            !overlay!!.onTouchEvent(away))
        away.recycle()

        // And a tap away from the handles ends the selection, the way ChatActivity's dispatch does,
        // without the tap also reaching what it landed on.
        val topicView = topic.fragmentView
        topicView.dispatchTouchEvent(
            android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 8f, 8f, 0))
        topicView.dispatchTouchEvent(
            android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, 8f, 8f, 0))
        assertTrue("a tap away ends the selection", !selection.isInSelectionMode())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The selection overlay is an inner view of upstream's generic helper, so it is found by its
     * own name rather than by a type this test cannot spell.
     */
    private fun findOverlay(view: android.view.View?): android.view.View? {
        when (view) {
            null -> return null
            is android.view.ViewGroup -> for (index in 0 until view.childCount) {
                findOverlay(view.getChildAt(index))?.let { return it }
            }
            else -> if (view.javaClass.simpleName == "TextSelectionOverlay") {
                return view
            }
        }
        return null
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 707L
    }
}
