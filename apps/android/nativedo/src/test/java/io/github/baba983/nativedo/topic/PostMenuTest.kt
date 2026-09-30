package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
import org.junit.Assert.assertNotNull
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
 * message was long-pressed.
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

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull("the long press opened a dialog", dialog)
        println("menu: dialog = " + dialog!!.javaClass.name)

        val items = menuItems(dialog)
        println("menu: items = $items")
        assertEquals("the menu offers the four things a reader does", 4, items.size)
        assertTrue("with copy", items.any { it.contains("复制") })
        assertTrue("a link", items.any { it.contains("链接") })
        assertTrue("a bookmark", items.any { it.contains("书签") })
        assertTrue("and reply", items.any { it.contains("回复") })

        // The first entry copies the post's text, not its markup.
        harness.idle()
        val row = menuRows(dialog).firstOrNull()
        println("menu: first row = " + row?.javaClass?.name)
        row?.performClick()
        harness.idle()
        val clipboard = harness.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        println("menu: clipboard after copy = $copied")
        assertEquals("the post's text is on the clipboard", "Hello from a linux.do post.", copied)
        harness.assertNoTelegramSideEffects()
    }

    /** The menu's rows are upstream's own dialog cells, one per item. */
    private fun menuRows(dialog: android.app.Dialog): List<org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell> {
        val out = mutableListOf<org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell>()
        collect(dialog.window?.decorView, out)
        return out
    }

    private fun collect(view: android.view.View?, out: MutableList<org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell>) {
        when (view) {
            is org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell -> out += view
            is ViewGroup -> for (index in 0 until view.childCount) collect(view.getChildAt(index), out)
        }
    }

    private fun menuItems(dialog: android.app.Dialog): List<String> = menuRows(dialog).map { row ->
        row.getChildAt(1).let { (it as android.widget.TextView).text.toString() }
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

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
