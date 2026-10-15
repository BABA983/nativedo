package io.github.baba983.nativedo.search

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
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
        // Search must retain the site's animated avatar, not just its sized still template.
        val response = JSONObject(harness.fixture("search.json"))
        response.getJSONArray("users").getJSONObject(0)
            .put("animated_avatar", "/uploads/search-avatar.gif")
        site.serveJson("search.json", response.toString())
        site.serveImage("uploads/search-avatar.gif", java.util.Base64.getDecoder().decode(
            "R0lGODlhCAAIAPAAAP8AAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAAAAAACwAAAAACAAIAAAC" +
                "B4SPqcvtXQAAIfkEABQAAAAsAAAAAAgACACAAAD/AAAAAgeEj6nL7V0AADs="), "image/gif")
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
        harness.await("the person's animated avatar") {
            harness.drawApp()
            harness.idleUpstreamQueues()
            (person as org.telegram.ui.Cells.ProfileSearchCell).avatarImage.imageDrawable is
                org.telegram.ui.Components.AnimatedFileDrawable
        }
        assertTrue("the animation uses the session client",
            site.cookieHeaderFor("uploads/search-avatar.gif") != null)
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
        assertTrue("predictive Back only probes", !harness.topFragment()!!.onBackPressed(false))
        harness.idleClock(400)
        assertTrue("probing Back keeps search open", titles.alpha < 0.01f)

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

    /**
     * The home search results are the screen, not a band under it.
     *
     * <p>The pager's own viewport starts at the top of the screen, so a row scrolled up is read
     * through the header's glass instead of being clipped below the bar, while the rows still come
     * to rest on the controls' lower edge (the pill capsule) because the list keeps that height as
     * scrollable padding. The failure this pins: the results' container hung at the bar's own
     * height - its top at the bar's lower edge - which clips every row below the header and leaves
     * the field's glass with no live result source behind it.</p>
     */
    @Test
    fun theHomeSearchResultsFillTheScreenAndRestBelowTheControls() {
        // Enough hits that the page really has somewhere to scroll under the header.
        val many = JSONObject(harness.fixture("search.json"))
        val topics = many.getJSONArray("topics")
        val template = topics.getJSONObject(0)
        for (index in 2..16) {
            topics.put(JSONObject(template.toString()).put("id", 200 + index).put("title", "Topic $index"))
        }
        site.serveJson("search.json", many.toString())

        harness.bootApp()
        harness.await("the home feed") { rowCount() == 2 }
        openSearch()
        harness.await("the search panel to be built") { searchPanel() != null }
        type(QUERY)
        val panel = searchPanel()!!
        harness.await("the topic hits") {
            panel.pageList(SearchPanel.PAGE_TOPICS).adapter?.itemCount == 16
        }
        harness.idleClock(400)
        harness.layoutApp()
        harness.renderApp("home-search-results-full-height")

        val bar = actionBar() ?: throw AssertionError("no action bar")
        val barBottom = IntArray(2).also { bar.getLocationInWindow(it) }[1] + bar.height
        val capsule = firstView(android.R.id.content) {
            it is org.telegram.ui.SearchTabsAndFiltersLayout && it.isShown
        } ?: throw AssertionError("no search capsule")
        val capsuleBottom = IntArray(2).also { capsule.getLocationInWindow(it) }[1] + capsule.height

        val list = panel.pageList(SearchPanel.PAGE_TOPICS)
        val listTop = IntArray(2).also { list.getLocationInWindow(it) }[1]

        // Full height: the results' viewport begins at the screen's own top, so it reaches behind
        // the bar - it is not parked at the bar's lower edge.
        assertTrue(
            "the results' viewport starts at the top of the screen " +
                "(results top $listTop, bar bottom $barBottom)",
            listTop <= AndroidUtilities.dp(1f),
        )
        assertTrue("the results reach behind the bar, not below it", listTop < barBottom)

        // Safe padding, scrollable not fixed: the first row rests just on the capsule's lower
        // edge, so no blank band opens between the controls and the rows.
        val firstRow = list.getChildAt(0) ?: throw AssertionError("no result row was laid out")
        val firstRowTop = IntArray(2).also { firstRow.getLocationInWindow(it) }[1]
        assertTrue(
            "the first row rests below the capsule (row top $firstRowTop, capsule bottom $capsuleBottom)",
            firstRowTop >= capsuleBottom - AndroidUtilities.dp(1f),
        )
        assertTrue(
            "and not on a blank band under it (row top $firstRowTop, capsule bottom $capsuleBottom)",
            firstRowTop <= capsuleBottom + AndroidUtilities.dp(12f),
        )

        // A scroll carries a row behind the bar: its own top is still inside the list's viewport
        // (so it is drawn, not clipped above the list) and it is up in the header's band.
        list.scrollBy(0, AndroidUtilities.dp(80f))
        harness.layoutApp()
        harness.renderApp("home-search-results-scrolled")
        val scrolledRow = list.getChildAt(0) ?: throw AssertionError("no scrolled row")
        val scrolledTop = IntArray(2).also { scrolledRow.getLocationInWindow(it) }[1]
        assertTrue(
            "a scrolled row stays inside the results' viewport (row top $scrolledTop, list top $listTop)",
            scrolledTop >= listTop - AndroidUtilities.dp(1f),
        )
        assertTrue(
            "and goes behind the header, not clipped below it (row top $scrolledTop, bar bottom $barBottom)",
            scrolledTop < barBottom,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A new query must never put a skeleton behind clickable hits from the previous query. */
    @Test
    fun changingTheQueryClearsOldHitsAndBlankInputFencesThePendingAnswer() {
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        openSearch()
        type(QUERY)
        val panel = searchPanel()!!
        harness.await("first results") { panel.pageList(SearchPanel.PAGE_TOPICS).adapter!!.itemCount == 1 }
        val release = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("search.json") {
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            MockResponse().setHeader("Content-Type", "application/json").setBody(harness.fixture("search.json"))
        }
        try {
            val priorCallbacks = siteCallbacks()
            searchField()!!.editText.setText("another")
            assertEquals("old rows cannot be mistaken for the new query", 0,
                panel.pageList(SearchPanel.PAGE_TOPICS).adapter!!.itemCount)
            harness.idleClock(2 * SEARCH_DELAY_MS)
            harness.await("second request") { site.queriesFor("search.json").size == 2 }
            val pendingCallbacks = siteCallbacks() - priorCallbacks.toSet()
            harness.renderApp("search-query-replacement-loading")
            assertTrue("results and shimmer are mutually exclusive", !panel.pageList(SearchPanel.PAGE_TOPICS).isShown)
            type("")
            release.countDown()
            awaitSiteCallbacks(pendingCallbacks)
            assertEquals("clear removes old results", 0, panel.pageList(SearchPanel.PAGE_TOPICS).adapter!!.itemCount)
            harness.renderApp("search-query-cleared")
            harness.assertNoTelegramSideEffects()
        } finally { release.countDown() }
    }

    /** Same text is not the same request: an older A must not overwrite a later A after B. */
    @Test
    fun returningToTheSameQueryKeepsTheNewestAnswerAndRetryStillWorks() {
        val release = java.util.concurrent.CountDownLatch(1)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val empty = """{"topics":[],"posts":[],"users":[],"grouped_search_result":{}}"""
        site.serveResponse("search.json") {
            if (requests.incrementAndGet() == 1) {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS)
                MockResponse().setHeader("Content-Type", "application/json").setBody(empty)
            } else MockResponse().setResponseCode(503)
        }
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        openSearch()
        try {
            val priorCallbacks = siteCallbacks()
            type(QUERY)
            harness.await("older A pending") { requests.get() == 1 }
            val olderCallbacks = siteCallbacks() - priorCallbacks.toSet()
            type("B")
            type(QUERY)
            val panel = searchPanel()!!
            harness.await("newer A failed") { firstView(android.R.id.content) {
                it is android.widget.TextView && it.isShown && it.text.toString().contains("点一下重试") } != null }
            harness.renderApp("search-global-error")
            site.serveResponse("search.json") { MockResponse().setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("search.json")) }
            val state = firstView(android.R.id.content) {
                it is org.telegram.ui.Components.StickerEmptyView && it.isShown }!!
            state.performClick()
            harness.await("newest A hits") { panel.pageList(SearchPanel.PAGE_TOPICS).adapter!!.itemCount == 1 }
            release.countDown()
            awaitSiteCallbacks(olderCallbacks)
            assertEquals("the older empty A cannot erase the new result", 1,
                panel.pageList(SearchPanel.PAGE_TOPICS).adapter!!.itemCount)
            harness.renderApp("search-global-retry-result")
            harness.assertNoTelegramSideEffects()
        } finally { release.countDown() }
    }

    @Test
    fun cachedCategoriesAndTagsRefilterAndAChildCategoryOpensItsOwnFeed() {
        site.serveFixture("c/domestic-lv1/99/l/latest.json", "latest-topics.json")
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        openSearch()
        type("开发")
        val panel = searchPanel()!!
        harness.await("category match") { panel.pageList(SearchPanel.PAGE_CATEGORIES).adapter!!.itemCount == 1 }
        selectPage(SearchPanel.PAGE_CATEGORIES)
        harness.renderApp("search-category-result")
        type("android")
        harness.await("cached tag match") { panel.pageList(SearchPanel.PAGE_TAGS).adapter!!.itemCount == 1 }
        assertEquals("the category match follows the changed query", 0,
            panel.pageList(SearchPanel.PAGE_CATEGORIES).adapter!!.itemCount)
        selectPage(SearchPanel.PAGE_TAGS)
        harness.renderApp("search-tag-result")
        type("一号")
        harness.await("child category") { panel.pageList(SearchPanel.PAGE_CATEGORIES).adapter!!.itemCount == 1 }
        selectPage(SearchPanel.PAGE_CATEGORIES)
        harness.renderApp("search-child-category-result")
        val list = panel.pageList(SearchPanel.PAGE_CATEGORIES)
        list.clickItem(list.getChildAt(0), 0)
        harness.await("real child category feed") { site.requestPaths.contains("c/domestic-lv1/99/l/latest.json") }
        harness.assertNoTelegramSideEffects()
    }

    // A callback-completion barrier, not a timed sleep: assertions above must observe the
    // late answer after the real loader has processed it, even when the correct UI stays unchanged.
    private fun siteCallbacks(): List<kotlinx.coroutines.Job> {
        val scope = io.github.baba983.nativedo.topic.DiscourseLoader::class.java
            .getDeclaredField("scope").apply { isAccessible = true }.get(null) as kotlinx.coroutines.CoroutineScope
        return scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
    }

    private fun awaitSiteCallbacks(callbacks: List<kotlinx.coroutines.Job>) {
        assertTrue("the pending request was captured", callbacks.isNotEmpty())
        harness.await("site callbacks completed") { callbacks.none { it.isActive } }
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
        val topic = harness.topFragment()
        val field = topicField()!!
        searchControl("显示为列表").performClick()
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
        assertTrue("a search hit stays in the same topic screen", harness.topFragment() === topic)
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
        val field = topicField()!!
        searchControl("显示为列表").performClick()
        field.setText("NativeDo")
        harness.await("error") { firstView(android.R.id.content) {
            it is android.widget.TextView && it.text.toString().contains("点击重试") } != null }
        harness.renderApp("topic-search-error")
        site.serveResponse("search.json") { MockResponse().setHeader("Content-Type", "application/json")
            .setBody(harness.fixture("search.json")) }
        val state = firstView(android.R.id.content) { it is org.telegram.ui.Components.EmptyTextProgressView && it.isShown }!!
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

    /** Search changes windows, not screens; closing it restores the reader's context and draft. */
    @Test
    fun topicSearchNavigatesBothWaysHighlightsChineseAndRestoresTheReadingWindow() {
        val initial = JSONObject(topicAround(1))
        initial.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
            .put("cooked", "<p>不太好，太少了。</p><blockquote><p>这里也太少</p></blockquote><pre><code>太多代码</code></pre>")
        site.serveJson("t/101.json", initial.toString())
        site.serveJson("t/101/1.json", initial.toString())
        val response = JSONObject(harness.fixture("search.json"))
        val hit = response.getJSONArray("posts").getJSONObject(0)
        hit.put("post_number", 1).put("id", 1001).put("blurb", "不太好，太少了")
        response.getJSONArray("posts").put(JSONObject(hit.toString()).put("id", 1004).put("post_number", 4))
        response.getJSONObject("grouped_search_result").put("more_posts", false)
        site.serveJson("search.json", response.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("initial window") { harness.layoutApp(); drawnPostIds().contains(1001) }
        val topic = harness.topFragment()!!
        val composer = firstView(android.R.id.content) { it is org.telegram.ui.Components.ChatActivityEnterView }
            as org.telegram.ui.Components.ChatActivityEnterView
        composer.editField!!.setText("保留这个草稿")
        openTopicSearch()
        topicField()!!.setText("太")
        harness.await("first search result") {
            harness.drawApp()
            firstView(android.R.id.content) { it.contentDescription?.toString() == "第 1 条，共 2 条" } != null
        }
        harness.renderApp("topic-search-chat-chinese")
        searchControl("下一条搜索结果").performClick()
        harness.await("next result's window") { harness.layoutApp(); drawnPostIds().contains(1004) }
        searchControl("上一条搜索结果").performClick()
        harness.await("previous result's window") { harness.layoutApp(); drawnPostIds().contains(1001) }
        searchControl("显示为列表").performClick()
        harness.renderApp("topic-search-list-chinese")
        org.telegram.ui.ActionBar.Theme.applyTheme(org.telegram.ui.ActionBar.Theme.getTheme("Dark Blue"), false, true)
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
            org.telegram.messenger.NotificationCenter.didSetNewTheme)
        harness.renderApp("topic-search-list-dark")
        searchControl("显示为消息").performClick()
        harness.renderApp("topic-search-chat-dark")
        assertTrue("predictive Back only probes", !topic.onBackPressed(false))
        assertTrue(topicField()!!.isShown)
        assertTrue("Back exits search, not the topic", !topic.onBackPressed(true))
        harness.await("reading window restored") { harness.layoutApp(); drawnPostIds().contains(1001) }
        assertTrue(harness.topFragment() === topic)
        assertEquals("保留这个草稿", composer.editField!!.text.toString())
        assertTrue("the search field is gone", topicField()?.isShown != true)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The ActionBar field's clear X is the query's own reset, and it must not also close the
     * session - only Back does that. The failure this pins: a clear that leaves the previous hits
     * clickable under a blank input, or one that tears the whole search down.
     */
    @Test
    fun topicSearchClearButtonEmptiesTheQueryAndKeepsTheSessionForBack() {
        site.serveFixture("t/101.json", "topic-detail.json")
        val response = JSONObject(harness.fixture("search.json"))
        response.getJSONObject("grouped_search_result").put("more_posts", false)
        site.serveJson("search.json", response.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("topic") { drawnPostIds().isNotEmpty() }
        openTopicSearch()
        val field = topicField()!!
        searchControl("显示为列表").performClick()
        field.setText("NativeDo")
        harness.await("scoped result") { rowCount() == 1 }

        topicClearButton().performClick()
        harness.await("the query is dropped with its hits") { rowCount() == 0 }
        assertEquals("the field itself is emptied", "", field.text.toString())
        assertTrue("clearing keeps the search open", field.isShown)
        assertEquals("and clearing is not another search", 1, site.queriesFor("search.json").size)

        val topic = harness.topFragment()!!
        assertTrue("Back exits search, not the topic", !topic.onBackPressed(true))
        harness.await("the session is gone") { topicField()?.isShown != true }
        assertTrue(harness.topFragment() === topic)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The field's IME search action is wired to the session: it lands the reader on the selected
     * hit without pushing a screen, even from list mode where a keystroke alone does not move the
     * window. The failure this pins: an IME action that does nothing, or one that leaves the topic.
     */
    @Test
    fun topicSearchImeActionLandsTheSelectedHitWithoutLeavingTheTopic() {
        site.serveFixture("t/101.json", "topic-detail.json")
        site.serveJson("t/101/4.json", topicAround(4))
        val response = JSONObject(harness.fixture("search.json"))
        response.getJSONArray("posts").put(JSONObject(response.getJSONArray("posts").getJSONObject(0).toString())
            .put("id", 1008).put("post_number", 8).put("blurb", "八楼的结果"))
        response.getJSONObject("grouped_search_result").put("more_posts", false)
        site.serveJson("search.json", response.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("topic") { drawnPostIds().isNotEmpty() }
        openTopicSearch()
        searchControl("显示为列表").performClick()
        topicField()!!.setText("NativeDo")
        harness.await("scoped results") { rowCount() == 2 }
        assertTrue("a list-mode search alone does not move the window",
            !site.requestPaths.contains("t/101/4.json"))

        val topic = harness.topFragment()!!
        topicSearchItem()!!.onSearchPressed()
        harness.await("the IME action lands on the selected hit") {
            site.requestPaths.contains("t/101/4.json")
        }
        assertTrue("and stays in the same topic screen", harness.topFragment() === topic)
        harness.assertNoTelegramSideEffects()
    }

    private fun searchControl(description: String): View =
        firstView(android.R.id.content) { it.isShown && it.contentDescription?.toString() == description }
            ?: throw AssertionError("missing search control: $description")

    /**
     * The in-topic search entry is a row of the topic overflow, not an icon on the bar.
     *
     * <p>The failures this pins: a standalone magnifier beside the overflow (the bar then reserves a
     * second icon's width and the reader has two search-looking controls); an overflow row that
     * does not open the bar's own field; and an icon that comes back after the field collapses,
     * because ActionBarMenuItem.toggleSearch re-shows the item it hid on open
     * (ActionBarMenuItem:987).</p>
     */
    @Test
    fun topicSearchIsReachedFromTheOverflowAndNeverStandsOnTheBar() {
        site.serveFixture("t/101.json", "topic-detail.json")
        val response = JSONObject(harness.fixture("search.json"))
        response.getJSONObject("grouped_search_result").put("more_posts", false)
        site.serveJson("search.json", response.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("topic") { drawnPostIds().isNotEmpty() }
        harness.layoutApp()

        // At rest the bar shows the overflow alone: the search item is in the menu but not on the
        // bar, so no magnifier reserves width beside the overflow.
        val item = topicSearchItem() ?: throw AssertionError("no in-topic search item")
        assertTrue("the bar shows no standalone search icon", !item.isShown)
        assertTrue("the overflow is the bar's only shown menu item", overflowItem().isShown)
        harness.renderComposite(".amp/in/artifacts/topic-search-menu/01-default.png")

        // The reader's way in is the overflow's own 话题内搜索 row.
        openTopicOverflow()
        val rows = menuItemLabels(latestItemOptionsMenu(harness.activity))
        println("topic-search-menu: overflow rows = $rows")
        assertTrue("the overflow offers 话题内搜索", rows.any { it.contains("话题内搜索") })
        harness.renderComposite(".amp/in/artifacts/topic-search-menu/02-menu-open.png")
        overflowRow("话题内搜索").performClick()
        harness.await("the bar's own field to open") { topicField()?.isShown == true }
        harness.idleClock(400)
        harness.layoutApp()
        harness.renderComposite(".amp/in/artifacts/topic-search-menu/03-expanded.png")
        assertTrue("the row opened the bar's field, not a second icon", !item.isShown)

        // Closing puts the bar back exactly as it was: the overflow, and no standalone icon.
        assertTrue("Back exits search, not the topic", !harness.topFragment()!!.onBackPressed(true))
        harness.await("the field to close") { topicField()?.isShown != true }
        harness.idleClock(400)
        harness.layoutApp()
        assertTrue("the search icon does not come back after close", !item.isShown)
        assertTrue("the overflow is still the bar's only shown menu item", overflowItem().isShown)
        harness.renderComposite(".amp/in/artifacts/topic-search-menu/04-after-close.png")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * What an open of the in-topic search must show: the bar's center glass capsule is the search
     * capsule (so the hint sits inside it), the bar reserves no second menu capsule, and an
     * unasked search reports no result.
     *
     * <p>The failure this pins: {@code ActionBar} lays the field across the menu but keeps the
     * center capsule at the avatar container's own width and re-centres it unless the bar's search
     * factor is driven (ActionBar:2244) - the reported screenshot shows 搜索当前话题 drawn to the
     * LEFT of the pill. And {@code countText()} reported 没有结果 before the reader asked
     * anything, which the same screenshot shows in the bottom bar.</p>
     */
    @Test
    fun topicSearchOpenKeepsTheHintInTheBarsCenterCapsuleAndClaimsNoResult() {
        site.serveFixture("t/101.json", "topic-detail.json")
        val response = JSONObject(harness.fixture("search.json"))
        response.getJSONObject("grouped_search_result").put("more_posts", false)
        site.serveJson("search.json", response.toString())
        harness.bootApp()
        harness.await("feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(101, "Topic search"))
        harness.await("topic") { drawnPostIds().isNotEmpty() }
        openTopicSearch()
        harness.idleClock(500)
        harness.drawApp()

        // An unasked search has no result to report: the count must not claim 没有结果.
        assertTrue("a blank query must not claim 没有结果",
            shownDescription("没有结果") == null)

        val bar = actionBar() ?: throw AssertionError("the topic screen has no action bar")
        val field = topicField() ?: throw AssertionError("the in-topic search field is missing")
        val barLocation = IntArray(2).also { bar.getLocationInWindow(it) }
        val fieldLocation = IntArray(2).also { field.getLocationInWindow(it) }
        val hintLeft = fieldLocation[0] - barLocation[0]
        val capsule = glassBounds(bar, "glassDrawable")
        println("topic-search-open geometry: capsule=$capsule hintLeft=$hintLeft")
        assertTrue("the bar draws a center glass capsule while searching", capsule != null)
        assertTrue(
            "the hint must not start left of the bar's center capsule (capsule=$capsule hintLeft=$hintLeft)",
            capsule!!.left <= hintLeft,
        )
        assertTrue("the bar must not reserve a menu capsule while the search owns it",
            bar.createMenu().itemsWidth == 0)
        harness.renderApp("topic-search-open")
        harness.assertNoTelegramSideEffects()
    }

    /** A shown view whose own content description is exactly [description]. */
    private fun shownDescription(description: String): View? =
        firstView(android.R.id.content) {
            it.isShown && it.contentDescription?.toString() == description
        }

    private fun glassBounds(
        bar: org.telegram.ui.ActionBar.ActionBar,
        field: String,
    ): android.graphics.Rect? {
        val declared = org.telegram.ui.ActionBar.ActionBar::class.java.getDeclaredField(field)
        declared.isAccessible = true
        val drawable = declared.get(bar) as android.graphics.drawable.Drawable?
        return drawable?.bounds
    }

    /** The topic bar's own overflow item, the way the reader opens it. */
    private fun overflowItem(): org.telegram.ui.ActionBar.ActionBarMenuItem =
        harness.topFragment()!!.actionBar.createMenu().getItem(1)

    /** Opens the topic overflow: a tap on the bar's own overflow item. */
    private fun openTopicOverflow() {
        overflowItem().performClick()
        harness.idle()
    }

    /** The overflow's row labelled [label], the way the reader taps it. */
    private fun overflowRow(label: String): org.telegram.ui.ActionBar.ActionBarMenuSubItem =
        latestItemOptionsMenu(harness.activity)
            .firstOrNull { menuItemLabels(listOf(it)).first().contains(label) }
            ?: throw AssertionError(
                "the topic overflow has no \"$label\" row among " +
                    menuItemLabels(latestItemOptionsMenu(harness.activity)),
            )

    /**
     * The topic screen's search is the bar's own ActionBar search item, not a drawn field: it is
     * the menu child whose `isSearchField()` is true, and its own EditTextBoldCursor is the field.
     */
    private fun topicSearchItem(): org.telegram.ui.ActionBar.ActionBarMenuItem? {
        val bar = harness.topFragment()?.actionBar ?: return null
        val menu = bar.createMenu()
        for (index in 0 until menu.childCount) {
            val child = menu.getChildAt(index)
            if (child is org.telegram.ui.ActionBar.ActionBarMenuItem && child.isSearchField) {
                return child
            }
        }
        return null
    }

    private fun topicField(): android.widget.EditText? = topicSearchItem()?.searchField

    /** The ActionBar field's own clear X, the way the bar built it (ActionBarMenuItem.clearButton). */
    private fun topicClearButton(): View {
        val item = topicSearchItem() ?: throw AssertionError("no in-topic search item")
        return org.telegram.ui.ActionBar.ActionBarMenuItem::class.java
            .getDeclaredField("clearButton").apply { isAccessible = true }
            .get(item) as View
    }

    /**
     * The reader's own way in: the topic bar's overflow, then its 话题内搜索 row. The entry is a
     * menu row, never an icon of its own on the bar, so this drives the production path.
     */
    private fun openTopicSearch() {
        val topic = harness.topFragment()
        openTopicOverflow()
        overflowRow("话题内搜索").performClick()
        harness.idle()
        assertTrue("topic search must not push another screen", harness.topFragment() === topic)
        harness.await("in-topic search field") { topicField()?.isShown == true }
    }

    private fun type(query: String) {
        searchField()!!.editText.setText(query)
        // The field waits for the reader to stop typing before it asks the site.
        harness.idleClock(2 * SEARCH_DELAY_MS)
    }

    /** Tap the shipped pill: the pager-only API bypasses the tab indicator's animation. */
    private fun selectPage(page: Int) {
        harness.layoutApp()
        val tabs = firstView(android.R.id.content) { it is org.telegram.ui.Components.ViewPagerFixed.TabsView }
            as org.telegram.ui.Components.ViewPagerFixed.TabsView
        val list = tabs.listView
        list.scrollToPosition(page)
        harness.layoutApp()
        list.clickItem(list.findViewHolderForAdapterPosition(page)!!.itemView, page)
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
        if (view is RecyclerListView && view.isShown && view.parent !is org.telegram.ui.Components.FilterTabsView) return view
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
