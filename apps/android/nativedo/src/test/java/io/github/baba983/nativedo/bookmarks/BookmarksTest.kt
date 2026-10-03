package io.github.baba983.nativedo.bookmarks

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
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
import io.github.baba983.nativedo.profile.ProfileFragment
import io.github.baba983.nativedo.topic.TopicFragment
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
import java.net.URLDecoder

/**
 * Bookmarks: the reader's own list, and the menu that fills it.
 *
 * <p>The failures this pin: a reader who finds a post worth keeping has nowhere to put it, and a
 * bookmark list that is not reachable. Discourse's own bookmark is a post - {@code
 * POST /bookmarks.json} with the post's id, {@code DELETE /posts/{id}/bookmark} to take it back, and
 * {@code GET /u/{username}/bookmarks.json} for the list, which the site answers for the reader
 * themselves.</p>
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
internal class BookmarksTest {

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

    @Test
    fun thePostMenuBookmarksTheFloorAndOffersToTakeItBack() {
        site.serveTopic(TOPIC_ID) { post -> post.put("cooked", "<p>一行值得留下的文字。</p>") }
        site.serveJson("bookmarks.json", """{"success":"OK","id":41}""")
        site.serveJson("posts/$POST_ID/bookmark", """{"success":"OK","topic_bookmarked":false}""")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Bookmark"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        val postId = cell.messageObject!!.messageOwner.id.toLong()

        openTheMenu(cell)
        val items = menuItems()
        println("bookmarks: the menu offers $items")
        assertEquals(
            "the menu offers the bookmark and the post's text too",
            listOf("复制", "复制链接", "添加书签", "回复", "选择文本"),
            items,
        )

        clickMenuItem(menuRows(), "添加书签")
        harness.idle()

        harness.await("the site to take the bookmark") {
            site.recordedRequests.any { it.startsWith("POST /bookmarks.json") }
        }
        val body = site.requestBodyFor("bookmarks.json")
        println("bookmarks: body = $body csrf = " + site.csrfHeaderFor("bookmarks.json"))
        assertNotNull("the bookmark carried a body", body)
        val fields = body!!.split('&').associate { field ->
            field.substringBefore('=') to URLDecoder.decode(field.substringAfter('='), "UTF-8")
        }
        assertEquals("it is the post the reader is looking at", postId.toString(), fields["bookmarkable_id"])
        assertEquals("a post bookmark", "Post", fields["bookmarkable_type"])
        assertEquals("with the site's own default lifetime", "0", fields["auto_delete_preference"])
        assertEquals(
            "and the token the site asks its writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("bookmarks.json"),
        )

        // The site took it, so the floor is bookmarked now - and the menu is where the reader sees
        // that: the answer to the write is what the next long press draws from.
        harness.await("the menu the reader opens again to offer the way back") {
            menuOffers("取消书签")
        }
        openTheMenu(cell)
        println("bookmarks: the menu now offers " + menuItems())
        clickMenuItem(menuRows(), "取消书签")
        harness.idle()
        harness.await("the site to be told the bookmark goes") {
            site.recordedRequests.any { it == "DELETE /posts/$postId/bookmark" }
        }
        assertEquals(
            "and with the token, like every write",
            "csrf-token-demo",
            site.csrfHeaderFor("posts/$postId/bookmark"),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theReadersOwnProfileListsTheirBookmarksAndARowOpensItsFloor() {
        site.serveFixture("u/baba.json", "user-profile.json")
        // The site lists bookmarks twenty at a time and answers the page after the last one with
        // the bare empty body it uses for "nothing here" (no wrapper around it at all).
        site.serveJsonFor("u/baba/bookmarks.json") { path ->
            if (path.contains("page=1")) """{"bookmarks":[]}""" else harness.fixture("user-bookmarks.json")
        }
        site.serveJson("user_actions.json", """{"user_actions":[]}""")
        // The two bookmarks of the fixture are floors of the site's own topics: the row the reader
        // taps is about one of them.
        site.serveFixture("t/$BOOKMARKED_TOPIC_ID/${BOOKMARKED_FLOOR}.json", "topic-detail.json")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }

        val profile = ProfileFragment.forUser("baba", "Baba")
        harness.present(profile)
        harness.await("the reader's own profile") { site.requestPaths.contains("u/baba.json") }
        // The reader's own rows sit under everything the profile itself says, so the list is scrolled
        // to where they are - which is how a reader reaches them too.
        harness.await("the profile's own fields") { rowLabels().isNotEmpty() }
        val profileList = contentListOf(harness.activity) ?: throw AssertionError("no profile list")
        profileList.scrollBy(0, 20_000)
        harness.layoutApp()
        harness.idle()
        println("bookmarks: the profile's own rows = " + rowLabels())
        assertTrue(
            "the reader's own profile offers their bookmarks",
            rowLabels().contains("书签"),
        )
        harness.layoutApp()
        harness.renderApp("profile-own")

        val list = contentListOf(harness.activity) ?: throw AssertionError("the profile drew no list")
        val row = (0 until list.childCount)
            .map { index -> list.getChildAt(index) }
            .first { view -> rowLabelsOf(view).contains("书签") }
        list.clickItem(row, positionOf(row))
        harness.idle()

        harness.await("the site for the reader's own bookmarks") {
            site.requestPaths.contains("u/baba/bookmarks.json")
        }
        harness.await("the bookmarks the site listed") { rowCount() == 2 }
        val asked = site.queriesFor("u/baba/bookmarks.json")
        println("bookmarks: asked $asked rows = " + rowCount())
        assertEquals(
            "the first page, asked the way the site pages it",
            "page=0&limit=20",
            asked.first(),
        )
        assertEquals(
            "and the bare empty answer is where the list ends, not another page",
            2,
            asked.size,
        )
        harness.layoutApp()
        harness.idleClock(600)
        harness.renderApp("bookmarks")

        // A bookmark is a post in a topic: the row opens that topic at that floor.
        val bookmarks = contentListOf(harness.activity) ?: throw AssertionError("no list")
        val first = bookmarks.getChildAt(0) ?: throw AssertionError("the list drew no rows")
        bookmarks.clickItem(first, positionOf(first))
        harness.await("the topic at the floor the bookmark is on") {
            site.requestPaths.contains("t/$BOOKMARKED_TOPIC_ID/$BOOKMARKED_FLOOR.json")
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Whether the menu the reader opens offers [item]. The menu is what the reader sees, so it is
     * what a test can read: opening it is exactly what the reader does.
     */
    private fun menuOffers(item: String): Boolean {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        openTheMenu(cell)
        val offered = menuItemLabels(latestItemOptionsMenu(harness.activity)).contains(item)
        harness.idle()
        return offered
    }

    private fun openTheMenu(cell: ChatMessageCell) {
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
    }

    private fun menuItems(): List<String> = menuItemLabels(menuRows())

    /** The menu's rows are upstream's own ItemOptions items, one per choice. */
    private fun menuRows(): List<org.telegram.ui.ActionBar.ActionBarMenuSubItem> =
        latestItemOptionsMenu(harness.activity)

    /** The labels the profile's own rows draw, for the row that opens the bookmarks. */
    private fun rowLabels(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount).flatMap { index -> rowLabelsOf(list.getChildAt(index)) }
    }

    private fun rowLabelsOf(view: View): List<String> {
        val out = mutableListOf<String>()
        collectText(view, out)
        return out
    }

    private fun collectText(view: View?, out: MutableList<String>) {
        when (view) {
            is TextView -> out += view.text.toString()
            is ViewGroup -> for (index in 0 until view.childCount) collectText(view.getChildAt(index), out)
        }
    }

    private fun positionOf(view: View): Int {
        val list = contentListOf(harness.activity) ?: return -1
        val first = (list.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: return -1
        val index = (0 until list.childCount).firstOrNull { list.getChildAt(it) === view } ?: return -1
        return first + index
    }

    private fun rowCount(): Int = contentListOf(harness.activity)?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private companion object {
        const val TOPIC_ID = 707L
        const val POST_ID = 1001L
        const val BOOKMARKED_TOPIC_ID = 101L
        const val BOOKMARKED_FLOOR = 2
    }
}
