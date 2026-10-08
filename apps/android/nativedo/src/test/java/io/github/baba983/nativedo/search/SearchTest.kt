package io.github.baba983.nativedo.search

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
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
import io.github.baba983.nativedo.topic.TopicFragment
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
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.FragmentSearchField
import org.telegram.ui.Components.RecyclerListView
import okhttp3.mockwebserver.MockResponse

/**
 * Search, the field on the home screen and the pages behind it, end to end.
 *
 * <p>The failure this pins: the site answers a search with three kinds of hit - topics, posts
 * inside topics, and people - and they do not go to the same place. A result page that treats a
 * post hit as a topic (or that sends every hit to the first floor) opens the wrong place for the
 * thing the reader picked, so the assertion is on where a post hit lands: the topic it belongs to,
 * asked for at the floor the post is on.</p>
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
internal class SearchTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("search.json", "search.json")
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("tags.json", "tags.json")
        site.serveFixture("u/baba.json", "user-profile.json")
        site.serveJson("user_actions.json", """{"user_actions":[]}""")
        // The post hit of the fixture is floor 4 of its topic, answered the way the site answers a
        // request for a floor: the window around it, and the ids of the whole stream.
        site.serveJson("t/$TOPIC_ID/$FLOOR.json", topicAround(FLOOR))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aSearchDrawsTheSitesHitsAndAPostHitOpensItsFloor() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        openSearch()
        harness.await("the search panel to be built") { searchPanel() != null }
        type(QUERY)

        harness.await("the site to be asked") { site.requestPaths.contains("search.json") }
        val asked = site.queriesFor("search.json")
        println("search: asked " + asked)
        assertEquals("one search for one query", 1, asked.size)
        assertTrue("the reader's own words are what was asked", asked.first().contains("q=$QUERY"))

        // The hits live on their own pages: the posts page holds the post hit, and it opens its
        // topic at floor 4.
        val panel = searchPanel()!!
        harness.await("the posts page to hold the hit") {
            panel.pageList(SearchPanel.PAGE_POSTS).adapter?.itemCount == 1
        }
        selectPage(SearchPanel.PAGE_POSTS)
        val list = panel.pageList(SearchPanel.PAGE_POSTS)
        harness.await("the post hit to be a laid-out row") { list.childCount == 1 }
        harness.renderApp("search-page-posts")
        val row = list.getChildAt(0)
        println("search: the post hit is a " + row.javaClass.simpleName)
        list.clickItem(row, 0)
        harness.await("the topic at the floor the post is on") {
            site.requestPaths.contains("t/$TOPIC_ID/$FLOOR.json")
        }
        harness.await("the floor to be drawn") { drawnPostIds().isNotEmpty() }
        val drawn = drawnPostIds().first()
        println("search: post hit opened at post " + drawn)
        assertEquals("the floor the post hit stands for", POST_ID_BASE + FLOOR, drawn)
        harness.renderApp("search-result-floor")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aPersonHitOpensTheirSiteProfile() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        openSearch()
        harness.await("the search panel to be built") { searchPanel() != null }
        type(QUERY)
        harness.await("the people page to hold the hit") {
            searchPanel()!!.pageList(SearchPanel.PAGE_USERS).adapter?.itemCount == 1
        }

        val panel = searchPanel()!!
        selectPage(SearchPanel.PAGE_USERS)
        val list = panel.pageList(SearchPanel.PAGE_USERS)
        harness.await("the person hit to be a laid-out row") { list.childCount == 1 }
        val person = list.getChildAt(0)
        println("search: the person hit is a " + person.javaClass.simpleName)
        list.clickItem(person, 0)
        harness.await("the person's profile") { site.requestPaths.contains("u/baba.json") }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pages shimmer while the site is being asked, each with the view type upstream's own
     * search uses for that kind of row (message search: the dialog row; people: the profile
     * search cell). The failure modes: no skeleton while the answer is on its way, and one type
     * for every tab regardless of what the rows are.
     */
    @Test
    fun theSearchPagesShimmerWhileTheSiteIsAsked() {
        // Hold the answer, so the shimmer is looked at while the search really is pending.
        val answer = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("search.json") {
            answer.await(10, java.util.concurrent.TimeUnit.SECONDS)
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("search.json"))
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        openSearch()
        harness.await("the search panel to be built") { searchPanel() != null }
        type(QUERY)
        harness.await("the site to be asked") { site.requestPaths.contains("search.json") }

        val panel = searchPanel()!!
        harness.await("the topics page to shimmer") {
            panel.pageLoadingView(SearchPanel.PAGE_TOPICS).visibility == View.VISIBLE
        }
        assertEquals(
            "the message search row is the topics shimmer",
            FlickerLoadingView.DIALOG_TYPE,
            panel.pageLoadingView(SearchPanel.PAGE_TOPICS).viewType,
        )
        harness.renderApp("search-topics-loading")

        harness.await("the people page to shimmer") {
            panel.pageLoadingView(SearchPanel.PAGE_USERS).visibility == View.VISIBLE
        }
        assertEquals(
            "the people page uses the user-search row",
            FlickerLoadingView.PROFILE_SEARCH_CELL,
            panel.pageLoadingView(SearchPanel.PAGE_USERS).viewType,
        )
        selectPage(SearchPanel.PAGE_USERS)
        harness.renderApp("search-users-loading")

        answer.countDown()
        harness.await("the hits") {
            panel.pageList(SearchPanel.PAGE_POSTS).adapter?.itemCount == 1
        }
        harness.await("the shimmer to go with the answer") {
            harness.idleClock(120)
            panel.pageLoadingView(SearchPanel.PAGE_TOPICS).visibility != View.VISIBLE
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The field's own slot. DialogsActivity's search takes the bar over: the field travels up by
     * its own height while the bar's title fades out, and closing sends it back down where the
     * list keeps it. The failure this pins: the search opening while the field stays at the slot
     * the list gave it - the pager then starts under a field that never left the list, and the
     * bar's title stays where the field's glass should be.
     */
    @Test
    fun theSearchFieldTravelsIntoTheBarAndComesBack() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.drawApp()
        val field = searchField() ?: throw AssertionError("no search field")
        val bar = actionBar() ?: throw AssertionError("no action bar")
        val titles = bar.titlesContainer ?: throw AssertionError("the bar has no titles container")

        // At rest the field hangs below the bar's lower edge, over the list's first row.
        assertTrue(
            "the field rests below the bar",
            field.top + field.translationY + field.height >
                bar.bottom + AndroidUtilities.dp(20f),
        )
        assertEquals("and the bar carries its title", 1f, titles.alpha, 0.01f)

        val rest = field.translationY
        field.editText.performClick()
        // Step the open animation frame by frame. The field's slot is written by the draw pass
        // (ContentView.dispatchDraw), so each sample is a bounded draw; the failure being pinned
        // is a field that is still at its rest slot while the search page is arriving.
        var onTheWayUp = false
        repeat(8) { step ->
            harness.idle(2)
            harness.renderAppFrame(
                if (step == 3) "search-field-mid-open" else "search-field-open-steps",
                maxTasks = 2,
            )
            val translation = field.translationY
            val bottom = field.top + translation + field.height
            if (translation < rest - AndroidUtilities.dp(2f) &&
                bottom > bar.bottom + AndroidUtilities.dp(4f)
            ) {
                onTheWayUp = true
            }
        }
        assertTrue("the field was seen between its rest slot and the bar", onTheWayUp)
        harness.idleClock(400)
        harness.renderApp("search-field-open")

        // Open: the field's own lower edge is at the bar's lower edge - it is in the bar.
        assertTrue(
            "the field has moved into the bar",
            field.top + field.translationY + field.height <=
                bar.bottom + AndroidUtilities.dp(4f),
        )
        assertTrue(
            "the bar's title has left",
            titles.alpha < 0.01f || titles.visibility != View.VISIBLE,
        )

        // The close button is the field's own x: it comes back down and the title returns.
        val closeIcon = FragmentSearchField::class.java
            .getDeclaredField("closeIcon").apply { isAccessible = true }
            .get(field) as android.widget.ImageView
        val open = field.translationY
        closeIcon.performClick()
        var onTheWayDown = false
        repeat(8) { step ->
            harness.idle(2)
            harness.renderAppFrame(
                if (step == 3) "search-field-mid-close" else "search-field-close-steps",
                maxTasks = 2,
            )
            val translation = field.translationY
            if (translation > open + AndroidUtilities.dp(2f) &&
                translation < rest - AndroidUtilities.dp(2f)
            ) {
                onTheWayDown = true
            }
        }
        assertTrue("the field was seen between the bar and its rest slot", onTheWayDown)
        harness.idleClock(400)
        harness.renderApp("search-field-closed")
        assertTrue(
            "closing puts the field back below the bar",
            field.top + field.translationY + field.height >
                bar.bottom + AndroidUtilities.dp(20f),
        )
        assertEquals("and the title is back", 1f, titles.alpha, 0.01f)
        harness.assertNoTelegramSideEffects()
    }

    /** The field on the home screen, the way the reader reaches it: a tap. */
    private fun openSearch() {
        val field = searchField()!!
        field.editText.performClick()
        // Let the 200ms open animation finish before the next gesture.
        harness.idleClock(400)
    }

    @Test
    fun topicSearchScopesPostsFiltersForeignHitsAndOpensTheExactFloor() {
        site.serveFixture("t/101.json", "topic-detail.json")
        val response = JSONObject(harness.fixture("search.json"))
        response.getJSONArray("posts").put(JSONObject(response.getJSONArray("posts").getJSONObject(0).toString())
            .put("id", 9999).put("topic_id", 999).put("post_number", 8))
        response.getJSONObject("grouped_search_result").put("more_posts", false)
        site.serveJson("search.json", response.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("topic") { drawnPostIds().isNotEmpty() }
        openTopicSearch()
        val field = harness.topFragment()!!.actionBar.createMenu().getItem(10).searchField
        assertEquals("blank input does not ask the whole site", 0, site.queriesFor("search.json").size)
        field.setText("topic:999")
        harness.idleClock(500)
        assertEquals("a typed scope override is refused", 0, site.queriesFor("search.json").size)
        field.setText("NativeDo")
        harness.await("scoped result") { rowCount() == 1 }
        val query = java.net.URLDecoder.decode(site.queriesFor("search.json").single(), "UTF-8")
        assertTrue(query.contains("q=NativeDo topic:101"))
        assertTrue(query.contains("type_filter=posts"))
        harness.renderApp("topic-search-results")
        val list = firstView(android.R.id.content) { it is RecyclerListView && it.isShown } as RecyclerListView
        list.clickItem(list.getChildAt(0), 0)
        harness.await("exact floor") { site.requestPaths.contains("t/101/4.json") }
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun topicSearchRetriesTheSameQueryAndPaginatesWithoutDuplicateFloors() {
        site.serveFixture("t/101.json", "topic-detail.json")
        site.serveResponse("search.json") { MockResponse().setResponseCode(503) }
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("topic") { drawnPostIds().isNotEmpty() }
        openTopicSearch()
        val field = harness.topFragment()!!.actionBar.createMenu().getItem(10).searchField
        field.setText("NativeDo")
        harness.await("error") { firstView(android.R.id.content) {
            it is android.widget.TextView && it.text.toString().contains("点击重试") } != null }
        harness.renderApp("topic-search-error")
        site.serveResponse("search.json") { MockResponse().setHeader("Content-Type", "application/json")
            .setBody(harness.fixture("search.json")) }
        val state = firstView(android.R.id.content) { it is org.telegram.ui.Components.EmptyTextProgressView }!!
        state.performClick()
        harness.await("retry hits") { rowCount() == 2 }
        // One post plus the production load-more row. Its next page repeats the first hit.
        val list = firstView(android.R.id.content) { it is RecyclerListView && it.isShown } as RecyclerListView
        harness.layoutApp()
        list.clickItem(list.findViewHolderForAdapterPosition(1)!!.itemView, 1)
        harness.await("page 1") { site.queriesFor("search.json").any { it.contains("page=1") } }
        harness.await("no duplicate result") { rowCount() == 2 }
        assertEquals(3, site.queriesFor("search.json").size)
        field.setText("")
        harness.await("cleared") { rowCount() == 0 }
        harness.renderApp("topic-search-empty")
        harness.assertNoTelegramSideEffects()
    }

    private fun openTopicSearch() {
        harness.topFragment()!!.actionBar.createMenu().getItem(1).performClick()
        harness.idle()
        io.github.baba983.nativedo.harness.clickMenuItem(
            io.github.baba983.nativedo.harness.latestItemOptionsMenu(harness.activity), "话题内搜索")
        harness.await("topic search screen") { harness.topFragment()?.javaClass?.simpleName == "TopicSearchFragment" }
    }

    private fun type(query: String) {
        searchField()!!.editText.setText(query)
        // The field waits for the reader to stop typing before it asks the site.
        harness.idleClock(2 * SEARCH_DELAY_MS)
    }

    /** Moves to a page and lets the pager's own scroll animation finish. */
    private fun selectPage(page: Int) {
        searchPanel()!!.selectPage(page)
        harness.idleClock(700)
        harness.layoutApp()
    }

    private fun searchField(): FragmentSearchField? =
        firstView(android.R.id.content) { it is FragmentSearchField } as FragmentSearchField?

    private fun searchPanel(): SearchPanel? =
        firstView(android.R.id.content) { it is SearchPanel } as SearchPanel?

    /** The list screen's own action bar, the parent the field travels into. */
    private fun actionBar(): org.telegram.ui.ActionBar.ActionBar? =
        firstView(android.R.id.content) { it is org.telegram.ui.ActionBar.ActionBar }
            as org.telegram.ui.ActionBar.ActionBar?

    private fun firstView(id: Int, matches: (View) -> Boolean): View? {
        val root = harness.activity.findViewById<ViewGroup>(id) ?: return null
        return walk(root, matches)
    }

    private fun walk(view: View, matches: (View) -> Boolean): View? {
        if (matches(view)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index), matches)?.let { return it }
            }
        }
        return null
    }

    /** What the topic screen drew, as the posts behind its bubbles. */
    private fun drawnPostIds(): List<Long> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return chatCells(content).map { it.messageObject!!.messageOwner.id.toLong() }
    }

    private fun chatCells(view: View): List<ChatMessageCell> {
        if (view is ChatMessageCell && view.isShown) return listOf(view)
        if (view is ViewGroup) {
            return (0 until view.childCount).flatMap { chatCells(view.getChildAt(it)) }
        }
        return emptyList()
    }

    /** A topic answered as the site answers a request for one floor: the window, and the stream. */
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

    private fun rowCount(): Int =
        harness.activity.findViewById<ViewGroup>(android.R.id.content)
            ?.let { findList(it) }?.adapter?.itemCount ?: 0

    private fun findList(view: View): RecyclerListView? {
        if (view is RecyclerListView && view.parent !is org.telegram.ui.Components.FilterTabsView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findList(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val QUERY = "NativeDo"
        const val TOPIC_ID = 101L
        const val FLOOR = 4
        const val POST_ID_BASE = 1000L
        const val TOTAL_FLOORS = 12
        const val SEARCH_DELAY_MS = 300L
    }
}
