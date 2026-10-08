package io.github.baba983.nativedo.topic

import androidx.recyclerview.widget.LinearLayoutManager
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
import org.json.JSONArray
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
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * An incoming reply, end to end: a post that answers an earlier floor draws upstream's own reply
 * preview - the target's author and text - and a tap on it jumps to the exact floor it names.
 *
 * <p>The failure this pins: a reply names a *floor number*, and that number is not the target's
 * *post id*. A client that stored the post id and treated it as a floor would draw the wrong preview
 * and jump nowhere; a client that never resolved the target would draw no preview at all.</p>
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
internal class IncomingReplyTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * A reply to a floor already in the window: the bubble draws the target's author and text, and a
     * tap on the preview scrolls the list back to that floor - located by its number, not by its id.
     */
    @Test
    fun aLoadedTargetDrawsThePreviewAndTheTapJumpsToItsExactFloor() {
        // Floors 1..20, ids 1001..1020: the post at floor 20 answers floor 2.
        val posts = (1..20).map { floor ->
            post(id = 1000L + floor, floor = floor, username = "u$floor", name = "User $floor",
                cooked = "<p>post number $floor</p>",
                replyTo = if (floor == 20) 2 else null)
        }
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, streamIds = (1..20).map { 1000L + it }))

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Replies"))
        harness.await("the topic to load") { rowCount() == 20 }

        // The answering floor is the last one; bring it on screen so its cell is bound.
        listView()!!.scrollToPosition(rowCount() - 1)
        harness.layoutApp()
        harness.idleClock(200)
        val replyCell = laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1020 }
            ?: throw AssertionError("the answering floor drew no cell")
        val target = replyCell.messageObject!!.replyMessageObject
        assertNotNull("the bubble carries its reply target", target)
        println("reply: preview name/text = " + target!!.messageOwner.from_id + " / " + target.messageText)
        assertEquals("the preview shows the target's floor text", "post number 2", target.messageText.toString())
        assertTrue("the reply is recognised as valid", replyCell.messageObject!!.hasValidReplyMessageObject())

        // Move away from the target, then tap the preview: the list is back at floor 2.
        val manager = listView()!!.layoutManager as LinearLayoutManager
        listView()!!.scrollToPosition(19)
        harness.layoutApp()
        assertTrue("the list sat away from the target", manager.findFirstVisibleItemPosition() > 2)
        replyCell.delegate.didPressReplyMessage(replyCell, replyCell.messageObject!!.getReplyMsgId(), 0f, 0f, false)
        harness.layoutApp()
        assertTrue(
            "the tap jumped to floor 2's row, not to a row named by the target's post id",
            manager.findFirstVisibleItemPosition() <= 2,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A reply whose target is not in the loaded window: the client reads the one post the reply
     * names, draws its preview without resetting the list, and a tap then reads the window the floor
     * sits in - the same read the floor's own link makes.
     */
    @Test
    fun anUnloadedTargetIsResolvedFromTheSiteAndTheTapReadsItsWindow() {
        // The window around floor 12 carries floors 8..16; floor 12 answers floor 2, which is not in it.
        val window = (8..16).map { floor ->
            post(id = 1000L + floor, floor = floor, username = "u$floor", name = "User $floor",
                cooked = "<p>post number $floor</p>",
                replyTo = if (floor == 12) 2 else null)
        }
        site.serveJson("t/$TOPIC_ID/12.json", topicJson(window, streamIds = (1..20).map { 1000L + it }))
        site.serveJson("posts/by_number/$TOPIC_ID/2.json", post(
            id = 1002, floor = 2, username = "u2", name = "User 2", cooked = "<p>the floor two body</p>",
        ).toString())

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forFloor(TOPIC_ID, "Reply target", 12))
        harness.await("the window") { rowCount() == 9 }

        // The target is not carried by the window, so the client reads exactly that post..., and the
        // answering floor is rebuilt once it lands. Poll for that (the rebuild replaces the cell),
        // from the floor's own position in the 8..16 window (index 4).
        harness.await("the reply target to be read") {
            site.requestPaths.contains("posts/by_number/$TOPIC_ID/2.json")
        }
        listView()!!.scrollToPosition(4)
        harness.layoutApp()
        harness.await("the resolved target to reach the bubble") {
            harness.layoutApp()
            harness.idle()
            laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1012 }
                ?.messageObject?.replyMessageObject != null
        }
        val replyCell = laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1012 }
            ?: throw AssertionError("the answering floor drew no cell")
        val target = replyCell.messageObject!!.replyMessageObject
            ?: throw AssertionError("the resolved target never reached the bubble")
        println("reply: unloaded preview text = " + target.messageText)
        assertEquals("the preview names the target it read", "the floor two body", target.messageText.toString())

        // ...and a tap reads the window the floor sits in, rather than jumping by the target's id.
        replyCell.delegate.didPressReplyMessage(replyCell, replyCell.messageObject!!.getReplyMsgId(), 0f, 0f, false)
        harness.await("the window of the target floor") {
            site.requestPaths.contains("t/$TOPIC_ID/2.json")
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A reply whose target is a *later* floor of the very same loaded window. Discourse lists a
     * window in floor order, so the answering floor is always built before the floor it names; a
     * client that only knew the targets it had already built would draw no preview here.
     */
    @Test
    fun anAnswerToALaterFloorOfTheSameWindowDrawsItsPreview() {
        val posts = (1..20).map { floor ->
            post(id = 1000L + floor, floor = floor, username = "u$floor", name = "User $floor",
                cooked = "<p>post number $floor</p>",
                replyTo = if (floor == 5) 19 else null)
        }
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, streamIds = (1..20).map { 1000L + it }))

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Later floor"))
        harness.await("the topic to load") { rowCount() == 20 }

        harness.layoutApp()
        harness.idleClock(200)
        val cell = laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1005 }
            ?: throw AssertionError("the answering floor drew no cell")
        val target = cell.messageObject!!.replyMessageObject
        assertNotNull("floor 5 answers floor 19, which the same window carries after it", target)
        println("reply: same-window preview text = " + target!!.messageText)
        assertEquals("the preview shows floor 19's words", "post number 19", target.messageText.toString())
        assertTrue("the reply is recognised as valid", cell.messageObject!!.hasValidReplyMessageObject())
        // The stock reply header names the target's author through MessagesController, not through the
        // map the message was built with: without registering the resolved target's user there, the
        // header would read the placeholder "Loading" instead of the author's name.
        harness.layoutApp()
        val headerName = cell.replyNameLayout?.text?.toString().orEmpty()
        println("reply: same-window header name = \"$headerName\"")
        assertTrue(
            "the stock reply header names the target's author: \"$headerName\"",
            headerName.contains("User 19"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Two answers to the same floor the window does not carry. Both answering floors must draw the
     * preview - the read is deduplicated, but the answered result belongs to every answer, not to
     * whichever one happened to be listed first.
     */
    @Test
    fun twoAnswersToOneUnloadedFloorBothDrawItsPreview() {
        val window = (8..16).map { floor ->
            post(id = 1000L + floor, floor = floor, username = "u$floor", name = "User $floor",
                cooked = "<p>post number $floor</p>",
                replyTo = if (floor == 12 || floor == 14) 2 else null)
        }
        site.serveJson("t/$TOPIC_ID/12.json", topicJson(window, streamIds = (1..20).map { 1000L + it }))
        site.serveJson("posts/by_number/$TOPIC_ID/2.json", post(
            id = 1002, floor = 2, username = "u2", name = "User 2", cooked = "<p>the floor two body</p>",
        ).toString())

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forFloor(TOPIC_ID, "Shared target", 12))
        harness.await("the window") { rowCount() == 9 }

        harness.await("the first answer's preview") {
            listView()!!.scrollToPosition(4)
            harness.layoutApp()
            harness.idle()
            laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1012 }
                ?.messageObject?.replyMessageObject != null
        }
        listView()!!.scrollToPosition(6)
        harness.layoutApp()
        harness.idleClock(200)
        val second = laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1014 }
            ?: throw AssertionError("the second answering floor drew no cell")
        val target = second.messageObject!!.replyMessageObject
        assertNotNull("the second answer to the same floor also draws the preview", target)
        println("reply: shared-target second preview = " + target!!.messageText)
        assertEquals("the second preview shows the target", "the floor two body", target.messageText.toString())
        assertEquals(
            "the deduplicated read was asked for once",
            1, site.requestPaths.count { it == "posts/by_number/$TOPIC_ID/2.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A floor whose body *begins* with a onebox: its first bubble is the link card, not text. The
     * reply header belongs to that first bubble just the same, so the preview and the jump are not
     * lost on the floors whose body does not open with a paragraph.
     */
    @Test
    fun aFloorThatStartsWithAOneboxStillDrawsItsReplyPreviewAndJumps() {
        val link = site.url("docs/start")
        val onebox = """
            <aside class="onebox allowlistedgeneric" data-onebox-src="$link">
              <header class="source"><a href="$link" target="_blank">docs.example</a></header>
              <article class="onebox-body"><h3><a href="$link" target="_blank">Getting started</a></h3><p>How the service is set up.</p></article>
            </aside>
        """.trimIndent()
        val posts = (1..6).map { floor ->
            post(id = 1000L + floor, floor = floor, username = "u$floor", name = "User $floor",
                cooked = if (floor == 3) onebox else "<p>post number $floor</p>",
                replyTo = if (floor == 3) 1 else null)
        }
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, streamIds = (1..6).map { 1000L + it }))

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Onebox reply"))
        harness.await("the topic to load") { rowCount() == 6 }

        listView()!!.scrollToPosition(2)
        harness.layoutApp()
        harness.idleClock(200)
        val cell = laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1003 }
            ?: throw AssertionError("the onebox floor drew no cell")
        val target = cell.messageObject!!.replyMessageObject
        assertNotNull("the onebox-first floor carries its reply preview", target)
        println("reply: onebox-first preview = " + target!!.messageText)

        val manager = listView()!!.layoutManager as LinearLayoutManager
        cell.delegate.didPressReplyMessage(cell, cell.messageObject!!.getReplyMsgId(), 0f, 0f, false)
        harness.layoutApp()
        assertTrue("the tap jumps back to floor 1", manager.findFirstVisibleItemPosition() <= 1)
        harness.assertNoTelegramSideEffects()
    }

    private fun post(
        id: Long, floor: Int, username: String, name: String, cooked: String, replyTo: Int? = null,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("post_number", floor)
        put("username", username)
        put("name", name)
        put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
        put("cooked", cooked)
        put("avatar_template", "")
        if (replyTo != null) put("reply_to_post_number", replyTo)
    }

    private fun topicJson(posts: List<JSONObject>, streamIds: List<Long>): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", JSONArray(posts))
        stream.put("stream", JSONArray(streamIds))
        return base.toString()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 909L
    }
}
