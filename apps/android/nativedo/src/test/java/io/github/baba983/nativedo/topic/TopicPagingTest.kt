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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.RecyclerListView

/**
 * A long topic, end to end: the first page arrives with the topic, and the posts above it are
 * fetched as the reader reaches the end of the list.
 *
 * <p>The failure this pins: a topic payload carries only the first page of a long thread, so a
 * client that treats it as the whole topic stops halfway down and looks like a topic whose author
 * never wrote more. The second fetch has to ask for the ids the stream lists, which the assertion
 * on the query does.</p>
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
internal class TopicPagingTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveJson("t/$TOPIC_ID.json", topicJson(firstPage = FIRST_PAGE))
        // A page of posts answers with the posts it was asked for, the way the site does.
        site.serveJsonFor("t/$TOPIC_ID/posts.json") { query ->
            val requested = Regex("post_ids%5B%5D=(\\d+)").findAll(query)
                .map { it.groupValues[1].toLong() - 1000L }.toList()
            topicJson(firstPage = 0, only = requested)
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun reachingTheEndOfTheFirstPageFetchesTheRest() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Long topic"))
        harness.await("the first page to load") { rowCount() == FIRST_PAGE }

        val list = listView()!!
        harness.idle()
        list.scrollBy(0, list.height * 4)
        harness.await("the next page to load") { rowCount() == TOTAL_POSTS }
        harness.renderApp("topic-paged")

        val queries = site.queriesFor("t/$TOPIC_ID/posts.json")
        println("paging: rows=" + rowCount() + " page queries=" + queries)
        assertEquals("the rest of the stream took one request", 1, queries.size)
        val query = queries[0]
        assertTrue(
            "the page starts at the id after the first page: $query",
            query.contains("post_ids%5B%5D=" + (1000L + FIRST_PAGE + 1)),
        )
        assertTrue(
            "and does not ask for the posts it already has: $query",
            !query.contains("post_ids%5B%5D=1001"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A topic payload the way Discourse sends one: the posts of one page plus the id stream. */
    private fun topicJson(firstPage: Int, only: List<Long> = emptyList()): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = JSONArray()
        val posts = JSONArray()
        val template = base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
        for (number in 1..TOTAL_POSTS) {
            stream.put(1000L + number)
            if (number > firstPage && number.toLong() !in only) continue
            val post = JSONObject(template.toString())
            post.put("id", 1000L + number)
            post.put("post_number", number)
            post.put("cooked", "<p>第 $number 楼</p>")
            posts.put(post)
        }
        base.getJSONObject("post_stream").put("stream", stream)
        base.getJSONObject("post_stream").put("posts", posts)
        base.put("posts_count", TOTAL_POSTS)
        return base.toString()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 303L
        const val FIRST_PAGE = 20
        const val TOTAL_POSTS = 25
    }
}
