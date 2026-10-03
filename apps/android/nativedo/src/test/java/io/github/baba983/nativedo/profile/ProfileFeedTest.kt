package io.github.baba983.nativedo.profile

import android.view.ViewGroup
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
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Components.RecyclerListView

/**
 * A reader's activity, on their profile, end to end.
 *
 * <p>Two failures are pinned here. The site keeps the topics a reader started (`filter=4`) and the
 * replies they wrote (`filter=5`) in two separate streams, so a screen that reads only one of them
 * draws someone who has been writing for years as someone who wrote nothing; the assertion on the
 * queries is that both were asked for. And a row is a link to where the action happened: tapping it
 * has to land on that floor, which is a request for `t/{id}/{floor}.json` and a body drawn from the
 * answer's window, not the top of a topic that may be hundreds of floors long.</p>
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
internal class ProfileFeedTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba.json", "user-profile.json")
        // The site answers each filter with the actions that belong to it, which is how a page of
        // one stream says nothing about where the other one stands.
        val actions = JSONObject(harness.fixture("user-actions.json")).getJSONArray("user_actions")
        site.serveJsonFor("user_actions.json") { query ->
            val filter = if (query.contains("filter=4")) 4 else 5
            val page = JSONArray()
            for (index in 0 until actions.length()) {
                val action = actions.getJSONObject(index)
                if (action.getInt("action_type") == filter) {
                    page.put(action)
                }
            }
            JSONObject().put("user_actions", page).toString()
        }
        // A topic that is long enough for the feed's floor to be a window in the middle of it.
        site.serveJson("t/$TOPIC_ID/$FLOOR.json", topicAround(FLOOR))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theProfilesActivityReadsBothStreamsAndARowOpensItsFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile to load") { detailValue("信任等级") == "3" }

        harness.await("both of the site's streams to arrive") {
            val asked = site.queriesFor("user_actions.json")
            asked.any { it.contains("filter=4") } && asked.any { it.contains("filter=5") }
        }
        // The activity sits under everything the profile itself says, the way the site's own profile
        // page reads: the reader reaches it by scrolling to the end of the list.
        val list = listView()!!
        harness.await("the activity to be a list") {
            list.scrollToPosition(list.adapter!!.itemCount - 1)
            harness.layoutApp()
            actionRows().isNotEmpty()
        }
        harness.renderApp("profile-feed")

        // One row per action of both streams: the fixture has two replies and one topic, and the
        // merge is by when the action happened, so the newest action - the 2024-06-03 reply on
        // floor 7 - is the first row and not the topic created before it.
        val rows = actionRows()
        println("feed: action rows=" + rows.size + " viewport rows=" + rowCount())
        assertEquals("one row per action of both streams", 3, rows.size)

        val first = rows.first()
        // The activity stream is the list's tail: with one row per action laid out (asserted
        // above), the stream's first row sits exactly rows.size rows before the end. Upstream's
        // list answers no adapter position for a child under this harness, and the collapsing
        // header's padding can leave a row inside the padding area, which the layout manager's
        // own first-visible answer does not count.
        val firstPosition = list.adapter!!.itemCount - rows.size
        println("feed: clicking the row at position " + firstPosition)
        list.clickItem(first, firstPosition)
        harness.await("the topic at that floor") { site.requestPaths.contains("t/$TOPIC_ID/$FLOOR.json") }
        harness.await("the floor to be drawn") { drawnPostIds().isNotEmpty() }

        val drawn = drawnPostIds().first()
        println("feed: opened at post " + drawn + " of " + drawnPostIds())
        assertEquals("the row opened the floor it stands for", POST_ID_BASE + FLOOR, drawn)
        harness.renderApp("profile-feed-floor")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The activity's own rows: upstream's row cell, one per action after the section that names the
     * stream. The cell draws its text instead of holding it in a view, so what a row stands for is
     * asserted where it matters - the topic and floor it opens.
     */
    private fun actionRows(): List<DialogCell> {
        val list = listView() ?: return emptyList()
        val section = (0 until list.childCount).firstOrNull { index ->
            (list.getChildAt(index) as? HeaderCell)?.textView?.text?.toString() == "动态"
        } ?: return emptyList()
        return (section + 1 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<DialogCell>()
    }

    /** What the topic screen drew, as the posts behind its bubbles. */
    private fun drawnPostIds(): List<Long> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return chatCells(content).map { cell ->
            assertNotNull("a drawn bubble has a message", cell.messageObject)
            cell.messageObject!!.messageOwner.id.toLong()
        }
    }

    private fun chatCells(view: android.view.View): List<ChatMessageCell> {
        if (view is ChatMessageCell && view.isShown) return listOf(view)
        if (view is ViewGroup) {
            return (0 until view.childCount).flatMap { chatCells(view.getChildAt(it)) }
        }
        return emptyList()
    }

    /**
     * A topic the way the site answers a request for one floor: the window of posts around it, and
     * the ids of the whole stream. The post ids carry their floor, so a drawn bubble says which one
     * it is.
     */
    private fun topicAround(floor: Int): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = JSONArray()
        val posts = JSONArray()
        val template = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        for (number in 1..TOTAL_FLOORS) {
            stream.put(POST_ID_BASE + number)
            if (number < floor || number > floor + 2) continue
            val post = JSONObject(template.toString())
            post.put("id", POST_ID_BASE + number)
            post.put("post_number", number)
            post.put("cooked", "<p>第 $number 楼</p>")
            posts.put(post)
        }
        base.getJSONObject("post_stream").put("stream", stream)
        base.getJSONObject("post_stream").put("posts", posts)
        base.put("posts_count", TOTAL_FLOORS)
        return base.toString()
    }

    private fun detailValue(label: String): String? {
        val list = listView() ?: return null
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index)
            if (cell is org.telegram.ui.Cells.TextDetailCell
                && cell.valueTextView.text.toString() == label
            ) {
                return cell.textView.text.toString()
            }
        }
        return null
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 4002L
        const val FLOOR = 7
        const val POST_ID_BASE = 1000L
        const val TOTAL_FLOORS = 30
    }
}
