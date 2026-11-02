package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
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
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.RecyclerListView

/**
 * Who else is replying to a topic, end to end: the site's reply presence channel drives the
 * capsule's subtitle, and this reader's own composing is announced on that same channel.
 *
 * <p>The failures this pins: reading the chat plugin's channel instead of discourse-presence's
 * (the site publishes nothing there, so a topic never shows a replier); drawing the reader as a
 * replier of their own topic; leaving the replier line up after the last replier leaves, so the
 * category and count never come back; a composer that never tells the site anything, so the reader
 * stays invisible to the site's own clients; and announcing a reply while the field holds a post
 * being edited, which tells the topic somebody is answering it when nobody is.
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
internal class TopicReplyPresenceTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serveFixture("categories.json", "categories.json")
        serveSingleFloorTopic { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        site.serveJson("session/current.json", currentUserJson(hidePresence = false))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The capsule names the other reader composing a reply and never the reader themselves, then
     * returns to the topic's own category/count line when that replier leaves.
     */
    @Test
    fun theCapsuleNamesOtherRepliersAndGivesTheTopicLineBack() {
        // Held back so the topic's own line can be read before anyone is composing.
        val snapshot = CountDownLatch(1)
        site.serveResponse("presence/get") {
            snapshot.await(10, TimeUnit.SECONDS)
            json(snapshotBody(lastMessageId = 10, users = listOf(8L to "alice", 7L to "baba")))
        }
        // Held back until the replier line is on screen: a delta published before the screen
        // subscribes belongs to nobody.
        val leave = CountDownLatch(1)
        site.serveMessageBusPoll {
            leave.await(10, TimeUnit.SECONDS)
            busBatch(busMessage(11, """{"leaving_user_ids":[8]}"""))
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Replies"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.layoutApp()

        val topicLine = subtitle()
        assertTrue("the capsule starts on the topic's own line: $topicLine", topicLine.isNotEmpty())
        assertFalse("nobody is replying yet: $topicLine", topicLine.contains("正在回复"))

        snapshot.countDown()
        harness.await("the replier line") {
            harness.idleClock(60)
            subtitle() == "正在回复：alice"
        }
        assertFalse(
            "the reader is never a replier of their own topic: ${subtitle()}",
            subtitle().contains("baba"),
        )
        val dots = subtitleView()?.leftDrawable
        assertTrue(
            "Telegram's animated typing dots precede the replier line: $dots",
            dots is org.telegram.ui.Components.TypingDotsDrawable,
        )
        assertTrue(
            "and they run while someone is replying",
            (dots as org.telegram.ui.Components.TypingDotsDrawable).isStarted,
        )
        harness.layoutApp()
        harness.renderApp("topic-reply-presence")
        val query = URLDecoder.decode(site.queriesFor("presence/get").lastOrNull().orEmpty(), "UTF-8")
        assertTrue(
            "the read names discourse-presence's reply channel: $query",
            query.contains(CHANNEL),
        )

        leave.countDown()
        harness.await("the topic's own line back") {
            harness.idleClock(60)
            subtitle() == topicLine
        }
        assertNull("the dots go with the replier line", subtitleView()?.leftDrawable)
        assertFalse("and stop animating", dots.isStarted)

        val pollPath = site.requestPaths.lastOrNull { it.startsWith("message-bus/") }
            ?: throw AssertionError("the bus never polled")
        // The poll that carries the subscription may be the next turn after the delta above; the
        // client sends its channels with every poll, so a turn inside the poll interval proves it.
        harness.await("a poll that names the reply channel") {
            harness.idleClock(500)
            site.requestBodiesFor(pollPath).any {
                URLDecoder.decode(it, "UTF-8").contains("/presence$CHANNEL")
            }
        }
    }

    /**
     * Typing in the composer announces this reader on the reply channel, and clearing the field
     * retracts it with the same client id. Neither write carries the text.
     */
    @Test
    fun typingInTheComposerAnnouncesThisReaderOnTheReplyChannel() {
        site.serveJson("presence/get", snapshotBody(lastMessageId = 10, users = emptyList()))
        site.serveJson("presence/update", """{"$CHANNEL":true}""")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Replies"))
        harness.await("the topic to load") { rowCount() == 1 }
        val composer = composer()

        composer.setFieldText("我来说两句")
        harness.await("the present write") {
            harness.idleClock(200)
            presentWrites().isNotEmpty()
        }
        val present = fields(presentWrites().last())
        assertEquals("the site's reply channel is announced", listOf(CHANNEL), present["present_channels[]"])
        assertEquals(
            "a presence write names channels only",
            setOf("client_id", "present_channels[]"),
            present.keys,
        )
        assertFalse(
            "nothing from the composer is sent to the presence backend",
            presentWrites().last().contains("我来说两句"),
        )
        val clientId = present["client_id"]?.single() ?: throw AssertionError("no client id")

        composer.setFieldText("")
        harness.await("the leave write") {
            harness.idleClock(100)
            leaveWrites().isNotEmpty()
        }
        val left = fields(leaveWrites().last())
        assertEquals("the same channel is left", listOf(CHANNEL), left["leave_channels[]"])
        assertEquals("the leave uses the announcing client", clientId, left["client_id"]?.single())
    }

    /**
     * A post being edited is not a reply: the field holds markdown for an edit, and announcing it
     * as a reply would tell the topic somebody is answering it.
     */
    @Test
    fun aPostBeingEditedIsNeverAnnouncedAsAReply() {
        site.serveJson("presence/get", snapshotBody(lastMessageId = 10, users = listOf(8L to "alice")))
        site.serveJson("presence/update", """{"$CHANNEL":true}""")
        serveSingleFloorTopic { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("raw", "Hello from **Android**.")
            post.put("can_edit", true)
            post.put("yours", true)
        }
        site.serveResponse("posts/$POST_ID.json") {
            json(
                """
                {"id":$POST_ID,"post_number":1,"reply_to_post_number":null,"username":"baba",
                 "name":"Baba","avatar_template":"/user_avatar/linux.do/baba/{size}/1.png",
                 "created_at":"2026-04-05T09:07:00.000Z","version":2,"yours":true,
                 "can_edit":true,"can_delete":true,"bookmarked":false,
                 "cooked":"<p>Hello from a linux.do post.</p>","raw":"Hello from **Android**."}
                """.trimIndent(),
            )
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Replies"))
        harness.await("the topic to load") { rowCount() == 1 }
        // The channel really is bound: another reader's reply is already on the capsule.
        harness.await("the other replier") {
            harness.idleClock(60)
            subtitle() == "正在回复：alice"
        }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "编辑")
        harness.await("the editor to open on the stored markdown") {
            composer().getFieldText()?.toString() == "Hello from **Android**."
        }

        composer().setFieldText("Hello from **Android**. And more.")
        harness.idleClock(2_000)

        assertTrue(
            "editing a post never announces a reply: ${site.requestBodiesFor("presence/update")}",
            presentWrites().isEmpty(),
        )
    }

    // ------------------------------------------------------------------------------------------
    // The fake site
    // ------------------------------------------------------------------------------------------

    /**
     * The fixture's single floor, with its stream aligned to that one post. The fixture's stream
     * lists three ids, so a screen that trusts it asks for a second page the fake site has not
     * served and draws its failure banner over the topic.
     */
    private fun serveSingleFloorTopic(mutate: (org.json.JSONObject) -> Unit) {
        val base = org.json.JSONObject(harness.fixture("topic-detail.json"))
        val post = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        mutate(post)
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", org.json.JSONArray().put(post))
        stream.put("stream", org.json.JSONArray().put(post.getLong("id")))
        site.serveJson("t/$TOPIC_ID.json", base.toString())
    }

    private fun presentWrites(): List<String> =
        site.requestBodiesFor("presence/update").filter { it.contains("present_channels") }

    private fun leaveWrites(): List<String> =
        site.requestBodiesFor("presence/update").filter { it.contains("leave_channels") }

    private fun json(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun fields(body: String): Map<String, List<String>> =
        body.split('&')
            .filter { it.isNotEmpty() }
            .map { pair ->
                val separator = pair.indexOf('=')
                val name = if (separator < 0) pair else pair.substring(0, separator)
                val value = if (separator < 0) "" else pair.substring(separator + 1)
                URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
            .groupBy({ it.first }, { it.second })

    private fun snapshotBody(lastMessageId: Long, users: List<Pair<Long, String>>): String {
        val list = users.joinToString(",", "[", "]") { (id, username) ->
            """{"id":$id,"username":"$username","name":"$username",""" +
                """"avatar_template":"/user_avatar/linux.do/$username/{size}/1.png"}"""
        }
        return """{"$CHANNEL":{"count":${users.size},"last_message_id":$lastMessageId,"users":$list}}"""
    }

    private fun currentUserJson(hidePresence: Boolean): String =
        """{"current_user":{"id":7,"username":"baba","name":"Baba",""" +
            """"avatar_template":"/user_avatar/linux.do/baba/{size}/1.png",""" +
            """"user_option":{"hide_presence":$hidePresence}}}"""

    private fun busMessage(messageId: Long, data: String): String =
        """{"global_id":$messageId,"message_id":$messageId,"channel":"/presence$CHANNEL","data":$data}"""

    private fun busBatch(vararg messages: String): String =
        messages.joinToString(",", prefix = "[", postfix = "]")

    // ------------------------------------------------------------------------------------------
    // The screen
    // ------------------------------------------------------------------------------------------

    private fun subtitle(): String = subtitleView()?.text?.toString().orEmpty()

    private fun subtitleView(): SimpleTextView? {
        val fragment = harness.visibleFragment() as? TopicFragment ?: return null
        val capsule = TopicFragment::class.java.getDeclaredField("avatarContainer").apply {
            isAccessible = true
        }.get(fragment) as? ChatAvatarContainer ?: return null
        return capsule.subtitleTextView as? SimpleTextView
    }

    private fun composer(): ChatActivityEnterView {
        val view = harness.visibleFragment()?.fragmentView ?: error("the screen has no view")
        return viewsUnder(view).filterIsInstance<ChatActivityEnterView>().lastOrNull()
            ?: error("the screen has no composer")
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private fun viewsUnder(root: View): List<View> {
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

    private companion object {
        const val TOPIC_ID = 808L
        const val POST_ID = 1001L
        const val CHANNEL = "/discourse-presence/reply/$TOPIC_ID"
    }
}
