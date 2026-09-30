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
import org.json.JSONObject
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
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import java.net.URLDecoder

/**
 * Replying, end to end: upstream's composer, and the post it sends to Discourse.
 *
 * <p>The failure this pins: a reply that reaches the site without the CSRF token, or with the wrong
 * topic or floor, is accepted by nobody — the composer's send button is the only place the reader
 * can see that the text left.</p>
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
internal class ReplyTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        // The site answers a created post with the post it created.
        site.serveJson("posts.json", createdPostJson())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theComposerSendsTheReplyToTheSiteWithTheSessionAndTheFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply"))
        harness.await("the topic to load") { rowCount() == 1 }

        val composer = composer() ?: throw AssertionError("the screen has no composer")
        println("reply: composer = " + composer.javaClass.name)
        // An empty field would offer Telegram's voice button, and this client has no voice behind it.
        harness.renderApp("topic-reply-empty")
        assertEquals(
            "no button while there is nothing to send",
            android.view.View.GONE,
            composer.getSendButton()?.visibility,
        )

        // Answering the first post, the way the post menu does.
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as? org.telegram.ui.ActionBar.AlertDialog
        assertNotNull("the post menu opened", dialog)
        val replyRow = menuRows(dialog!!).lastOrNull() ?: throw AssertionError("the menu has no rows")
        replyRow.performClick()
        harness.idle()
        println("reply: after the menu picked a reply, the field is \"" + composer.fieldText + "\"")
        composer.setFieldText("thanks, that helped")
        harness.idle()
        assertEquals(
            "and a send button as soon as there is",
            android.view.View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        harness.renderApp("topic-reply")

        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the reply to reach the site") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }

        val body = site.requestBodyFor("posts.json")
        println("reply: body = $body csrf = " + site.csrfHeaderFor("posts.json"))
        assertNotNull("the reply carried a body", body)
        val fields = body!!.split('&').associate { part ->
            val name = part.substringBefore('=')
            name to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }
        assertEquals("the reply went to this topic", TOPIC_ID.toString(), fields["topic_id"])
        assertEquals("with the text the reader typed", "thanks, that helped", fields["raw"])
        assertEquals("as an answer to the floor the menu picked", "1", fields["reply_to_post_number"])
        assertEquals("and with the token the site asks writes for", "csrf-token-demo", site.csrfHeaderFor("posts.json"))
        assertTrue("the composer was cleared", composer.fieldText.isNullOrEmpty())
        harness.assertNoTelegramSideEffects()
    }

    private fun createdPostJson(): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        return base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0).toString()
    }

    private fun menuRows(dialog: org.telegram.ui.ActionBar.AlertDialog):
        List<org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell> {
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

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findComposer(content)
    }

    private fun findComposer(view: android.view.View): ChatActivityEnterView? {
        if (view is ChatActivityEnterView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposer(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 808L
    }
}
