package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey
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
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatActionCell
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton

/**
 * The discussion page a floor's reply footer opens: upstream's own shape, a full pushed page - the
 * root post and its direct replies - not a bottom sheet.
 *
 * <p>The failures this pins. The reader taps a footer and gets a page over the topic, seeded with
 * the post they tapped, paging the replies by the root's real post <em>id</em> and by the greatest
 * reply <em>floor</em> read - a page that paged by id, or jumped by id, would land wrong or
 * nowhere. The footer must not open a window instead of a page. Only the replies to this parent are
 * rows (an answer to another floor is not), a repeated id is dropped, and a page that adds nothing
 * ends the listing however many the site's count still promises. The endpoint can fail on the first
 * page, when the root is already drawn: that must be retryable from the page's own line, and never
 * retried on its own. A page is read because the reader reached the end or tapped the line, never
 * because a private method was called. The topic under the page - its scroll and its draft - is left
 * exactly as it was. A thread draft is its own field, scoped to the root floor. The composer answers
 * the root floor by default, honours an explicit target, and keeps the reader's text when a send is
 * refused. A quote that points outside the thread pushes the normal topic rather than replacing the
 * page with a topic window. And a read that lands after the page is gone draws nothing.</p>
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
internal class TopicRepliesThreadTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        container = site.authenticatedContainer(harness.context)
        NativeDo.setContainerFactory { container }
    }

    @After
    fun tearDown() {
        runCatching { org.telegram.ui.Components.Bulletin.hideVisible() }
        runCatching { harness.idleClock(600) }
        runCatching { if (Theme.isCurrentThemeDark()) switchTo(false) }
        NativeDo.setContainerFactory { container }
        site.stop()
    }

    /**
     * A tap on the reply footer opens the discussion page as a pushed fragment - the same screen in
     * its thread mode - seeded with the floor that was tapped and paging its replies; the topic it
     * came from is still the fragment under it.
     */
    @Test
    fun tappingTheReplyFooterPushesADiscussionPageInsteadOfASheet() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        openTopicAtParent()

        val topic = harness.visibleFragment() as TopicFragment
        val thread = openThreadFromFooter(PARENT_FLOOR)

        assertTrue("the footer opened a discussion page", thread.isThreadMode())
        assertNotSame("it is a page of its own, not the topic redrawn", topic, thread)
        assertTrue("the topic is still under the page", harness.fragmentStack().any { it === topic })
        assertEquals(
            "the page was pushed directly over the topic",
            topic,
            harness.fragmentStack()[harness.fragmentStack().size - 2],
        )
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }

        // No reply strip is opened for the default target: like upstream's own discussion composer,
        // the root floor is where an ordinary answer goes without the page showing a strip for it.
        assertFalse(
            "the default answer opens no reply strip",
            composerOf(thread).isTopViewVisible(),
        )
        // The default target is chosen when the reader sends, not when the page loads, so the wire
        // body is what says which floor the answer went to.
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        composerOf(thread).setFieldText("an ordinary answer")
        harness.idle()
        composerOf(thread).sendMessage()
        harness.await("the ordinary answer to reach the site") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }
        assertEquals(
            "the wire body answers the root floor, not the whole topic",
            PARENT_FLOOR.toString(),
            postedField("reply_to_post_number"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pushed page draws the root post and its direct replies with their rich bodies: the root
     * carries an image, a quote and a reaction, the replies their own text. The root's own reply
     * count stays the site's, and a long press still opens the post menu.
     */
    @Test
    fun thePushedPageDrawsTheRootAndItsDirectRepliesRichly() {
        val rootHtml = """
            <p>the opening floor with a picture</p>
            <p><img src="${site.url("uploads/root.png")}" alt="root"></p>
            <blockquote><p>quoted words</p></blockquote>
        """.trimIndent()
        serveParentAtFloor12(replyCount = 4, rootCooked = rootHtml, rootReactions = 3)
        site.serveImage("uploads/root.png", png(4, 4))
        serveReplies(mapOf(1 to listOf(15, 16)))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }

        val bubbles = bubblesOf(thread)
        assertEquals("the root draws one bubble and each reply one", 3, bubbles.size)
        assertTrue(
            "the root's rich body is built, not dropped",
            bubbles[0].messageOwner.rich_message.blocks.isNotEmpty(),
        )
        assertTrue(
            "a reply's rich body is built too",
            bubbles[2].messageOwner.rich_message.blocks.isNotEmpty(),
        )
        assertEquals("the root's reply count is the site's own", 4, bubbles[0].getRepliesCount())
        assertNotNull("the root's reactions travel", bubbles[0].messageOwner.reactions)

        // Avatars for the participants were asked for, the way the topic list asks after binding.
        harness.await("the authors' avatars") {
            site.requestPaths.any { it.startsWith("user_avatar/") }
        }
        harness.layoutApp()
        harness.idleClock(200)

        // A long press on a reply still opens the post menu.
        val replyCell = laidOutCells().firstOrNull { it.messageObject === bubbles[2] }
            ?: throw AssertionError("the reply's cell is not on screen")
        replyCell.delegate.didLongPress(replyCell, 0f, 0f)
        harness.idle()
        assertTrue(
            "a long press on a reply opens the post menu",
            menuItemLabels(latestItemOptionsMenu(harness.activity)).contains("回复"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The replies are read from the root's real post *id*, one page at a time after the greatest
     * reply floor read; a reply that answers another floor is not a row of this thread.
     */
    @Test
    fun theThreadPagesByTheRootsPostIdAndTheGreatestReplyFloor() {
        serveParentAtFloor12(replyCount = 6)
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            when (afterOf(query)) {
                1 -> replyArray(listOf(15 to PARENT_FLOOR, 16 to 99, 17 to PARENT_FLOOR))
                else -> replyArray(listOf(18 to PARENT_FLOOR))
            }
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the first page of replies") { floorsOf(thread).size == 3 }
        assertEquals(
            "the endpoint is the root's id, not its floor",
            listOf("after=1"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )
        assertFalse(
            "the floor number is never mistaken for the id",
            site.requestPaths.contains("posts/$PARENT_FLOOR/replies"),
        )
        assertEquals(
            "a reply to another floor is not a row of this thread",
            listOf(PARENT_FLOOR, 15, 17),
            floorsOf(thread).map { it.postNumber },
        )

        tapThreadStatus(thread)
        harness.await("the second page of replies") { floorsOf(thread).size == 4 }
        assertEquals(
            "the next page continues after the greatest valid floor read",
            listOf("after=1", "after=17"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )
        assertEquals(
            "every reply to this parent is kept, in floor order",
            listOf(PARENT_FLOOR, 15, 17, 18),
            floorsOf(thread).map { it.postNumber },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The reader reaches the end of the loaded rows with a real scroll and the next page is read -
     * the load is driven by the list, not by a method a production caller never touches.
     */
    @Test
    fun scrollingTheThreadToTheEndReadsTheNextPage() {
        serveParentAtFloor12(replyCount = 30)
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            when (afterOf(query)) {
                1 -> replyArray((15..22).map { it to PARENT_FLOOR })
                else -> replyArray((23..26).map { it to PARENT_FLOOR })
            }
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the long first page") { floorsOf(thread).size == 9 }
        harness.layoutApp()
        harness.idleClock(200)

        val before = site.queriesFor("posts/$PARENT_ID/replies").size
        repeat(30) {
            listView()?.scrollBy(0, 300)
            harness.idle(30)
        }
        harness.await("the page the real scroll asked for") {
            site.queriesFor("posts/$PARENT_ID/replies").size > before
        }
        harness.await("the next page's rows") { floorsOf(thread).size == 13 }
        assertTrue(
            "the scroll asked from the greatest floor read",
            site.queriesFor("posts/$PARENT_ID/replies").take(2) == listOf("after=1", "after=22"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An overlapping page then an empty page: the repeated ids are dropped, the empty page ends the
     * listing, and nothing the reader can touch asks the site again.
     */
    @Test
    fun anOverlappingPageThenAnEmptyPageEndsTheThreadWithoutDuplicating() {
        serveParentAtFloor12(replyCount = 20)
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            when (afterOf(query)) {
                1 -> replyArray(listOf(15 to PARENT_FLOOR, 16 to PARENT_FLOOR))
                16 -> replyArray(listOf(15 to PARENT_FLOOR, 16 to PARENT_FLOOR, 17 to PARENT_FLOOR))
                else -> replyArray(emptyList())
            }
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the first page of replies") { floorsOf(thread).size == 3 }

        tapThreadStatus(thread)
        harness.await("the overlapping page's one new row") { floorsOf(thread).size == 4 }
        assertEquals(
            "the repeated rows are not doubled",
            listOf(PARENT_FLOOR, 15, 16, 17),
            floorsOf(thread).map { it.postNumber },
        )

        tapThreadStatus(thread)
        // Wait for the terminal page to land and end the thread, not just to be asked for. The
        // query count rises when the request reaches the site, before the empty answer is processed;
        // awaiting it alone returns while the reading line is still up.
        harness.await("the empty terminal page to end the thread") {
            site.queriesFor("posts/$PARENT_ID/replies").size >= 3 && !thread.threadHasMore()
        }
        harness.idle()
        assertEquals("the count is still the site's", 20, bubblesOf(thread).first().getRepliesCount())
        assertEquals("and no rows were invented for it", 4, floorsOf(thread).size)

        // Ended: nothing asks again, and the reading line is gone.
        listView()?.scrollBy(0, 600)
        harness.idle()
        tapThreadStatus(thread)
        harness.idle()
        assertEquals(
            "the ended thread asks the site for nothing more",
            listOf("after=1", "after=16", "after=17"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )
        assertTrue(
            "the reading line is gone once there is nothing left",
            thread.threadStatusView()?.visibility != View.VISIBLE,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The read-on affordance is upstream's floating page-down button - a small circle at the
     * trailing edge, its stock badge carrying the site's remaining count - not a made-up full-width
     * strip, and its tap reads the next page from the cursor the last one advanced to.
     */
    @Test
    fun theReadOnAffordanceIsUpstreamsFloatingPageDownButton() {
        serveParentAtFloor12(replyCount = 6)
        serveReplies(mapOf(1 to listOf(15, 16, 17), 17 to listOf(18)))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the first page of replies") { floorsOf(thread).size == 4 }
        harness.layoutApp()
        harness.idleClock(300)

        val affordance = thread.threadStatusView()
        assertTrue(
            "the affordance is upstream's page-down button, not a TextView: $affordance",
            affordance is ChatActivityBlurredRoundPageDownButton,
        )
        assertTrue(
            "the affordance is a small floating button, not a full-width bar",
            affordance!!.measuredWidth in 1..AndroidUtilities.dp(80f),
        )
        assertFalse(
            "no made-up full-width loading strip remains",
            contentViewHasText(thread, "继续下拉加载更多"),
        )
        assertTrue(
            "the affordance names the read-on: ${affordance.contentDescription}",
            affordance.contentDescription?.toString()?.contains("更多") == true,
        )

        tapThreadStatus(thread)
        harness.await("the next page") { floorsOf(thread).size == 5 }
        assertEquals(
            "the tap read the next page from the greatest floor read",
            listOf("after=1", "after=17"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A read in flight is shown on the stock spinner and refuses a second tap: a page already being
     * read must not be asked for twice.
     */
    @Test
    fun aPendingReadShowsTheStockSpinnerAndSwallowsADuplicateTap() {
        serveParentAtFloor12(replyCount = 30)
        // The first page is held open long enough for the pending state to be looked at.
        site.serveResponse("posts/$PARENT_ID/replies") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(replyArray((15..18).map { it to PARENT_FLOOR }))
                .setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS)
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the read to reach the site") {
            site.queriesFor("posts/$PARENT_ID/replies").size == 1
        }
        harness.layoutApp()
        harness.idleClock(400)

        val affordance = thread.threadStatusView()
        assertTrue(
            "a pending read is shown on upstream's button: $affordance",
            affordance is ChatActivityBlurredRoundPageDownButton,
        )
        val button = affordance as ChatActivityBlurredRoundPageDownButton
        assertFalse("a pending read disables the affordance", button.isEnabled)
        assertTrue(
            "the affordance names the pending read: ${button.contentDescription}",
            button.contentDescription?.toString()?.contains("加载") == true,
        )

        // A second tap while the first page is still on its way must not ask again.
        thread.threadStatusView()?.performClick()
        harness.idle()
        assertEquals(
            "a duplicate tap during a pending read asks the site for nothing more",
            1,
            site.queriesFor("posts/$PARENT_ID/replies").size,
        )

        harness.await("the first page to arrive") { floorsOf(thread).size == 5 }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An empty replies answer with a positive count must not claim the floor has no replies: it says
     * none are visible. The authoritative count-zero case is the one that says there are none.
     */
    @Test
    fun anEmptyRepliesAnswerWithAPositiveCountSaysNoneAreVisible() {
        // The site's own count says there are replies, but the endpoint answers with none.
        serveParentAtFloor12(replyCount = 4)
        site.serveJsonFor("posts/$PARENT_ID/replies") { replyArray(emptyList()) }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the empty answer") {
            floorsOf(thread).size == 1 && emptyTextOf(thread) != null
        }
        harness.layoutApp()
        harness.idleClock(200)
        assertEquals(
            "an empty answer with a positive count says none are visible",
            "当前没有可见的回复",
            emptyTextOf(thread),
        )
        assertTrue(
            "the read-on button is gone once nothing is left",
            thread.threadStatusView()?.visibility != View.VISIBLE,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The page-down button must never cover the composer, in either theme or with the keyboard up:
     * it floats above the real input, the way the chat's own page-down button does.
     */
    @Test
    fun thePageDownButtonClearsTheComposerInLightDarkAndWithTheKeyboard() {
        serveParentAtFloor12(replyCount = 30)
        site.serveJsonFor("posts/$PARENT_ID/replies") {
            replyArray((15..22).map { it to PARENT_FLOOR })
        }
        openTopicAtParent()
        switchTo(dark = false)

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the long first page") { floorsOf(thread).size == 9 }
        harness.layoutApp()
        harness.idleClock(300)
        assertAffordanceClearsComposer(thread, "light")

        switchTo(dark = true)
        harness.layoutApp()
        harness.idleClock(300)
        assertAffordanceClearsComposer(thread, "dark")
        switchTo(dark = false)

        // The keyboard: the system lifts the input by the IME inset, the way HomeTabsTest drives it.
        val root = harness.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val keyboard = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 260))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, keyboard)
        harness.layoutApp()
        harness.idleClock(400)
        assertAffordanceClearsComposer(thread, "ime")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The first page fails while the root is already drawn: the page says so on its own line, the
     * root is still there, and the reader's tap retries from the same cursor - once, not in a loop.
     */
    @Test
    fun aFailedFirstPageIsRetryableFromThePagesOwnLine() {
        serveParentAtFloor12(replyCount = 4)
        val failing = java.util.concurrent.atomic.AtomicBoolean(true)
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            if (failing.get()) "{\"errors\":[\"that page did not arrive\"]}"
            else replyArray(listOf(15 to PARENT_FLOOR, 16 to PARENT_FLOOR))
        }
        openTopicAtParent()

        // A Bulletin left by an earlier test in the shared sandbox must not be mistaken for this
        // page's own hint.
        org.telegram.ui.Components.Bulletin.hideVisible()
        val topic = harness.visibleFragment() as TopicFragment
        val footerBubble = bubblesOf(topic)[bubbleIndexOfFloor(topic, PARENT_FLOOR)]
        val footerCell = laidOutCells().firstOrNull { it.messageObject === footerBubble }
            ?: throw AssertionError("the footer's cell for floor $PARENT_FLOOR is not on screen")
        footerCell.delegate.didPressCommentButton(footerCell)
        // The Bulletin hides itself shortly after it appears, so it is watched one task at a time
        // from the moment the page opens, rather than after a full drain.
        val announced = awaitBulletin()
        // Put the captured hint fully away before the page is driven on: a Bulletin left up (or its
        // hide timer still pending) would be laid out under a later screen.
        org.telegram.ui.Components.Bulletin.hideVisible()
        harness.idleClock(2_500)
        val thread = harness.visibleFragment() as TopicFragment
        harness.await("the first page to fail") { thread.threadStatusView()?.visibility == View.VISIBLE }
        assertEquals("the root is still drawn", 1, floorsOf(thread).size)
        // The pass-through hint says the same thing the page's own line does - a tap retries - not
        // the topic paging copy, which promises that another scroll will try again: this page has no
        // scroll retry, so that promise would be a lie.
        assertTrue(
            "the pass-through copy names the page's own tap retry: $announced",
            announced.any { it.contains("点击重试") },
        )
        assertFalse(
            "and does not promise a scroll retry this page cannot make: $announced",
            announced.any { it.contains("继续滑动") },
        )
        assertTrue(
            "the page's own line carries the retry",
            thread.threadStatusView()!!.isClickable,
        )
        assertEquals(
            "a failed page is asked exactly once, never on its own",
            listOf("after=1"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )

        failing.set(false)
        thread.threadStatusView()!!.performClick()
        harness.await("the retried page") { floorsOf(thread).size == 3 }
        assertEquals(
            "the retry asked from the same cursor",
            listOf("after=1", "after=1"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Leaving the discussion page puts the topic back exactly as it was: its place in the list and
     * the draft in its composer are untouched, because the page never touched them.
     */
    @Test
    fun leavingTheThreadKeepsTheTopicsScrollAndDraft() {
        serveParentAtFloor12(replyCount = 4, extraFloors = (13..40).toList())
        serveReplies(mapOf(1 to listOf(41, 42)))
        openTopicAtParent()

        val topic = harness.visibleFragment() as TopicFragment
        val topicComposer = composerOf(topic)
        topicComposer.setFieldText("a draft in the topic")
        harness.idle()

        val list = listView()!!
        harness.layoutApp()
        repeat(10) {
            list.scrollBy(0, 400)
            harness.idle(30)
        }
        harness.layoutApp()
        harness.idleClock(200)
        val manager = list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
        val beforePosition = manager.findFirstVisibleItemPosition()
        assertTrue("the topic is scrolled away from its top", beforePosition > 0)

        val root = floorsOf(topic).first { it.postNumber == PARENT_FLOOR }
        harness.present(
            TopicFragment.forReplies(TOPIC_ID, "A topic", PARENT_ID, PARENT_FLOOR, 4)
                .seedThread(root, topicDetailOf(topic)),
        )
        val thread = harness.visibleFragment() as TopicFragment
        assertTrue("the page opened", thread.isThreadMode())
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }

        (harness.activity as io.github.baba983.nativedo.app.NativeDoActivity).onBackPressed()
        harness.layoutApp()
        harness.idleClock(400)

        val back = harness.visibleFragment() as TopicFragment
        assertTrue("the topic is back", !back.isThreadMode())
        val afterPosition = (list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
            .findFirstVisibleItemPosition()
        assertEquals("the topic's place is where it was", beforePosition, afterPosition)
        assertEquals(
            "and its draft is untouched",
            "a draft in the topic",
            composerOf(back).getFieldText()?.toString().orEmpty(),
        )
        assertEquals(
            "the topic's own draft is stored, not the thread's",
            "a draft in the topic",
            container.topicReplyDraftStore.load(TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID)).raw,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A thread draft is its own field: it is scoped by the root floor, so it never overwrites the
     * topic's draft and two threads of one topic never share one - while the topic's own key is
     * exactly the one it always was.
     */
    @Test
    fun aThreadDraftIsScopedToItsRootAndLeavesTheTopicDraftAlone() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        openTopicAtParent()

        val topic = harness.visibleFragment() as TopicFragment
        composerOf(topic).setFieldText("topic draft")
        harness.idle()
        val root = floorsOf(topic).first { it.postNumber == PARENT_FLOOR }
        harness.present(
            TopicFragment.forReplies(TOPIC_ID, "A topic", PARENT_ID, PARENT_FLOOR, 4)
                .seedThread(root, topicDetailOf(topic)),
        )
        val thread = harness.visibleFragment() as TopicFragment
        harness.await("the thread") { floorsOf(thread).size == 3 }
        composerOf(thread).setFieldText("thread draft")
        harness.idle()

        val topicKey = TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID)
        val thisThread = TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID, PARENT_FLOOR)
        val otherThread = TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID, 20)
        assertEquals("the topic's draft is the topic's", "topic draft",
            container.topicReplyDraftStore.load(topicKey).raw)
        assertEquals("the thread's draft is the thread's", "thread draft",
            container.topicReplyDraftStore.load(thisThread).raw)
        assertEquals("another thread of the topic is not this one", "",
            container.topicReplyDraftStore.load(otherThread).raw)

        // And the same store is what both use: a legacy-shaped topic key still resolves the topic
        // draft, so nothing written before threads existed is lost.
        container.topicReplyDraftStore.save(
            TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID),
            TopicReplyDraft(raw = "topic draft"),
        )
        assertEquals("the topic key is unchanged by the thread scope", "topic draft",
            container.topicReplyDraftStore.load(TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID)).raw)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The thread's composer answers the root floor by default; picking 回复 on one of the replies
     * retargets it to that reply's floor, and the body the site receives names it.
     */
    @Test
    fun theComposerSendsToTheRootFloorByDefaultAndHonoursAnExplicitTarget() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }

        // Default: the body answers the root floor.
        val composer = composerOf(thread)
        composer.setFieldText("answering the root")
        harness.idle()
        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the reply to reach the site") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }
        assertEquals(
            "with no explicit target the body answers the root floor",
            PARENT_FLOOR.toString(),
            postedField("reply_to_post_number"),
        )

        // Explicit: reply to a loaded reply, and the body names that reply's floor.
        // The accepted reply reloads the page: its success clears the reply target and re-reads the
        // replies. The row count alone is already 3 before that reload lands, so it cannot gate it;
        // without waiting for the reload's own read, its clearDraft can arrive after the explicit
        // target is picked below and silently drop it, falling back to the root floor.
        harness.await("the accepted reply's reload") {
            site.queriesFor("posts/$PARENT_ID/replies").size >= 2 && floorsOf(thread).size == 3
        }
        harness.layoutApp()
        harness.idleClock(200)
        val replyBubble = bubblesOf(thread).first { message ->
            floorOfModel(message) == 15
        }
        val cell = laidOutCells().firstOrNull { it.messageObject === replyBubble }
            ?: throw AssertionError("the reply's cell is not on screen")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "回复")
        harness.idle()
        composer.setFieldText("answering the reply")
        harness.idle()
        composer.sendMessage()
        harness.idleClock(400)
        harness.await("the second reply to reach the site") {
            site.requestBodiesFor("posts.json").size >= 2
        }
        assertEquals(
            "an explicit target is the reply's floor",
            "15",
            postedField("reply_to_post_number"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A send the site refuses keeps the reader's text in the field. */
    @Test
    fun aRefusedSendInAThreadKeepsTheText() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        site.serveResponse("posts.json") {
            MockResponse().setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("error-422-post.json"))
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        val composer = composerOf(thread)
        composer.setFieldText("this will be refused")
        harness.idle()
        composer.sendMessage()
        harness.idleClock(600)
        harness.await("the site to refuse the reply") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }
        harness.await("the refused text to come back") {
            composer.getFieldText()?.toString().orEmpty() == "this will be refused"
        }
        assertEquals(
            "the refused text is still there",
            "this will be refused",
            composer.getFieldText()?.toString().orEmpty(),
        )
        assertEquals(
            "and it is kept in the thread's own draft",
            "this will be refused",
            container.topicReplyDraftStore
                .load(TopicReplyDraftKey(site.baseUrl, "baba", TOPIC_ID, PARENT_FLOOR)).raw,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The root post quotes a floor outside the thread: tapping its preview pushes the normal topic
     * at that floor; it must not replace the filtered thread list with a topic window.
     */
    @Test
    fun aQuoteOutsideTheThreadPushesTheNormalTopicRatherThanReloadingTheThread() {
        serveParentAtFloor12(replyCount = 4, quoteFloor = OUTSIDE_FLOOR)
        serveReplies(mapOf(1 to listOf(15, 16)))
        site.serveJson("posts/by_number/$TOPIC_ID/$OUTSIDE_FLOOR.json", postJson(
            id = 9099, floor = OUTSIDE_FLOOR, username = "zoe", name = "Zoe",
            cooked = "<p>a post outside the thread</p>",
        ))
        site.serveJson("t/$TOPIC_ID/$OUTSIDE_FLOOR.json", topicJson(
            listOf(JSONObject(postJson(9099, OUTSIDE_FLOOR, "zoe", "Zoe", "<p>outside</p>"))),
            listOf(9099L),
        ))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        harness.await("the outside target of the root's quote") {
            (field(thread, "postByNumber") as Map<*, *>).containsKey(OUTSIDE_FLOOR)
        }
        harness.await("the root's preview to be rebuilt") {
            @Suppress("UNCHECKED_CAST")
            (field(thread, "replyTargetFloors") as Map<*, *>).isNotEmpty()
        }
        harness.layoutApp()
        harness.idleClock(200)

        val before = harness.fragmentStack().size
        val rootBubble = bubblesOf(thread).first()
        val cell = laidOutCells().firstOrNull { it.messageObject === rootBubble }
            ?: throw AssertionError("the root's cell is not on screen")
        cell.delegate.didPressReplyMessage(cell, 0, 0f, 0f, false)
        harness.idle()

        assertTrue(
            "a quote outside the thread pushes the normal topic",
            harness.fragmentStack().size > before,
        )
        val pushed = harness.visibleFragment() as TopicFragment
        assertTrue("the pushed page is a normal topic, not a thread", !pushed.isThreadMode())
        assertTrue(
            "and the thread it came from is untouched and still under it",
            harness.fragmentStack().any { it === thread } && floorsOf(thread).size == 3,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A replies read that lands after the page is gone draws nothing: the page was destroyed while
     * the first page was in flight, and completing the read must not touch the dead page.
     */
    @Test
    fun aRepliesReadThatLandsAfterThePageIsGoneDrawsNothing() {
        serveParentAtFloor12(replyCount = 4)
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("posts/$PARENT_ID/replies") {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(replyArray(listOf(15 to PARENT_FLOOR, 16 to PARENT_FLOOR)))
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies read to reach the site") {
            site.requestPaths.contains("posts/$PARENT_ID/replies")
        }
        assertEquals("only the root is drawn while the page is in flight", 1, floorsOf(thread).size)

        // The page is gone before the read lands: the reader backed out of it.
        (harness.activity as io.github.baba983.nativedo.app.NativeDoActivity).onBackPressed()
        harness.idle()
        gate.countDown()
        harness.idle()
        harness.idleClock(200)

        assertEquals(
            "a read that lands after the page is gone draws nothing",
            1,
            floorsOf(thread).size,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A page the system rebuilt has no in-memory seed, so it resolves the parent again from the
     * site by the floor it was opened for - the id alone would not name a floor.
     */
    @Test
    fun aRebuiltPageResolvesTheParentByItsFloorInsteadOfTheSeed() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        openTopicAtParent()

        // The seed is gone: only the arguments the factory wrote remain.
        harness.present(TopicFragment.forReplies(TOPIC_ID, "A topic", PARENT_ID, PARENT_FLOOR, 4))
        val thread = harness.visibleFragment() as TopicFragment
        assertTrue("the rebuilt page is a thread", thread.isThreadMode())
        harness.await("the parent resolved from the site") { floorsOf(thread).isNotEmpty() }
        assertEquals(
            "the parent is the floor the page was opened for",
            PARENT_FLOOR,
            floorsOf(thread).first().postNumber,
        )
        assertTrue(
            "and it was read by floor, not as the head of the topic",
            site.requestPaths.contains("t/$TOPIC_ID/$PARENT_FLOOR.json"),
        )
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        harness.assertNoTelegramSideEffects()
    }

    /** Renders the pushed page in its default, paged, loading and retry states, light and dark. */
    @Test
    fun rendersTheDiscussionPage() {
        serveParentAtFloor12(replyCount = 12, rootCooked = """
            <p>the opening floor, with enough words to make a real bubble on the page</p>
            <blockquote><p>something quoted from above</p></blockquote>
        """.trimIndent(), rootReactions = 2)
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            when (afterOf(query)) {
                1 -> replyArray((15..19).map { it to PARENT_FLOOR })
                else -> replyArray((20..21).map { it to PARENT_FLOOR })
            }
        }
        openTopicAtParent()
        switchTo(dark = false)

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the first page of replies") { floorsOf(thread).size == 6 }
        harness.layoutApp()
        harness.idleClock(300)
        render("comments-thread-default-light")

        switchTo(dark = true)
        harness.layoutApp()
        harness.idleClock(300)
        render("comments-thread-default-dark")
        switchTo(dark = false)

        // Paged: the reader read on and another page arrived.
        tapThreadStatus(thread)
        harness.await("the second page of replies") { floorsOf(thread).size == 8 }
        harness.layoutApp()
        harness.idleClock(300)
        render("comments-thread-paged-light")

        // Loading then retry: the next page is held open, so upstream's stock spinner shows while it
        // is pending, and the same request failing leaves the retry on the same affordance.
        site.serveResponse("posts/$PARENT_ID/replies") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"errors\":[\"this page did not arrive\"]}")
                .setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS)
        }
        tapThreadStatus(thread)
        harness.await("the pending page to reach the site") {
            site.queriesFor("posts/$PARENT_ID/replies").size == 3
        }
        harness.layoutApp()
        harness.idleClock(500)
        // The stock spinner's arc grows from its first draw; draw it once, let the clock advance,
        // then capture so the spinner is a real arc and not the drawable's empty first frame.
        harness.drawApp()
        harness.idleClock(700)
        render("comments-thread-loading-light")

        switchTo(dark = true)
        harness.layoutApp()
        harness.idleClock(400)
        harness.drawApp()
        harness.idleClock(700)
        render("comments-thread-loading-dark")

        harness.await("the failed page") {
            thread.threadStatusView()?.contentDescription?.toString()?.contains("重试") == true
        }
        // The stock error Bulletin announces the failure and then leaves; the render waits it out.
        harness.idleClock(4000)
        harness.layoutApp()
        harness.idleClock(300)
        render("comments-thread-failed-dark")

        switchTo(dark = false)
        harness.layoutApp()
        harness.idleClock(300)
        render("comments-thread-failed-light")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Renders the empty discussion both ways: the authoritative count-zero floor, and a floor the
     * site says has replies but whose endpoint answers with none.
     */
    @Test
    fun rendersTheEmptyDiscussion() {
        serveParentAtFloor12(
            replyCount = 0,
            rootCooked = "<p>the opening floor, with no replies yet</p>",
        )
        site.serveJsonFor("posts/$PARENT_ID/replies") { replyArray(emptyList()) }
        openTopicAtParent()
        switchTo(dark = false)

        harness.present(TopicFragment.forReplies(TOPIC_ID, "A topic", PARENT_ID, PARENT_FLOOR, 0))
        val emptyThread = harness.visibleFragment() as TopicFragment
        harness.await("the count-zero thread") {
            floorsOf(emptyThread).isNotEmpty() && !emptyThread.threadHasMore()
        }
        harness.layoutApp()
        harness.idleClock(300)
        assertEquals("the root alone is the whole thread", 1, floorsOf(emptyThread).size)
        assertEquals(
            "a count-zero floor says there are no replies",
            "还没有回复",
            emptyTextOf(emptyThread),
        )
        render("comments-thread-empty-light")

        switchTo(dark = true)
        harness.layoutApp()
        harness.idleClock(300)
        render("comments-thread-empty-dark")
        switchTo(dark = false)

        // A positive count the endpoint does not honour: the copy must not claim there are none.
        serveParentAtFloor12(replyCount = 3)
        site.serveJsonFor("posts/$PARENT_ID/replies") { replyArray(emptyList()) }
        harness.present(TopicFragment.forReplies(TOPIC_ID, "A topic", PARENT_ID, PARENT_FLOOR, 3))
        val unseen = harness.visibleFragment() as TopicFragment
        harness.await("the empty answer") {
            floorsOf(unseen).size == 1 && emptyTextOf(unseen) != null
        }
        harness.layoutApp()
        harness.idleClock(300)
        assertEquals(
            "a positive count with no visible reply says none are visible",
            "当前没有可见的回复",
            emptyTextOf(unseen),
        )
        render("comments-thread-empty-unseen-light")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Reacting to a floor from inside the discussion page must refresh the page, not replace its
     * root-and-replies rows with the topic's own window: the reaction belongs to a floor of this
     * page, and the topic's rows are unrelated to it.
     */
    @Test
    fun reactingInsideTheThreadKeepsTheThreadRatherThanTheTopicsRows() {
        serveParentAtFloor12(replyCount = 4, rootReactions = 3, extraFloors = (13..20).toList())
        serveReplies(mapOf(1 to listOf(15, 16)))
        // The normal head of the topic, so a path that re-reads the topic has unrelated rows to draw.
        val head = mutableListOf(parentPost(replyCount = 4, cooked = "<p>the opening floor</p>", reactions = 3))
        for (floor in 13..20) {
            head += JSONObject(postJson(7000L + floor, floor, "u$floor", "User $floor", "<p>floor $floor</p>"))
        }
        site.serveJson("t/$TOPIC_ID.json", topicJson(head, head.map { it.getLong("id") }))
        site.serveJson("discourse-reactions/posts/$PARENT_ID/custom-reactions/heart/toggle.json", "{}")
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        harness.layoutApp()
        harness.idleClock(200)

        val rootBubble = bubblesOf(thread).first()
        val cell = laidOutCells().firstOrNull { it.messageObject === rootBubble }
            ?: throw AssertionError("the root's cell is not on screen")
        val reaction = rootBubble.messageOwner.reactions.results.first()
        cell.delegate.didPressReaction(cell, reaction, false, 0f, 0f)
        harness.await("the reaction toggle to reach the site") {
            site.recordedRequests.any { it.startsWith("PUT /discourse-reactions/posts/") }
        }
        // The toggle's follow-up read (the topic's own head under the bug, the page's own floor
        // under the fix) has a local round trip; let it land before reading the page.
        settle(800)

        assertTrue("the tap stayed a discussion page", thread.isThreadMode())
        assertFalse(
            "reacting did not pull in the topic's own head rows",
            site.requestPaths.contains("t/$TOPIC_ID.json"),
        )
        assertEquals(
            "the page kept its own root and replies, not the topic's rows",
            listOf(PARENT_FLOOR, 15, 16),
            floorsOf(thread).map { it.postNumber },
        )
        harness.assertNoTelegramSideEffects()
    }

    /** The header keeps the root floor it names and carries the site's own reply count for it. */
    @Test
    fun theHeaderNamesTheRootFloorAndTheSitesReplyCount() {
        serveParentAtFloor12(replyCount = 7)
        serveReplies(mapOf(1 to listOf(15, 16)))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }

        val subtitle = capsuleSubtitle(thread)
        assertTrue(
            "the header keeps the floor the page is about: $subtitle",
            subtitle.contains("#$PARENT_FLOOR"),
        )
        assertTrue(
            "and carries the site's own count of its replies: $subtitle",
            subtitle.contains("7"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Two ordinary answers in a row must both answer the root floor. The failure this pins: the
     * first send clears the target, so a page that only ever set the target at load time would let
     * the second ordinary answer escape to the whole topic.
     */
    @Test
    fun consecutiveOrdinaryAnswersBothAnswerTheRootFloor() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        val composer = composerOf(thread)

        composer.setFieldText("first ordinary answer")
        harness.idle()
        composer.sendMessage()
        harness.await("the first answer to reach the site") {
            site.requestBodiesFor("posts.json").size >= 1
        }

        composer.setFieldText("second ordinary answer")
        harness.idle()
        composer.sendMessage()
        harness.await("the second answer to reach the site") {
            site.requestBodiesFor("posts.json").size >= 2
        }

        assertEquals(
            "the first ordinary answer answers the root floor",
            PARENT_FLOOR.toString(),
            postedFields("reply_to_post_number")[0],
        )
        assertEquals(
            "the consecutive ordinary answer answers the root floor too, not the whole topic",
            PARENT_FLOOR.toString(),
            postedFields("reply_to_post_number")[1],
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Cancelling an explicit target returns the page to its own root-floor default: the next
     * ordinary answer must go to the root, not escape to the whole topic because the target is null.
     */
    @Test
    fun cancellingAnExplicitTargetReturnsThePageToTheRootFloorDefault() {
        serveParentAtFloor12(replyCount = 4)
        serveReplies(mapOf(1 to listOf(15, 16)))
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        harness.layoutApp()
        harness.idleClock(200)

        val composer = composerOf(thread)
        val replyBubble = bubblesOf(thread).first { floorOfModel(it) == 15 }
        val cell = laidOutCells().firstOrNull { it.messageObject === replyBubble }
            ?: throw AssertionError("the reply's cell is not on screen")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "回复")
        harness.idle()
        assertTrue("an explicit target shows its strip", composer.isTopViewVisible())

        findCloseButton(composer)?.performClick()
            ?: throw AssertionError("the reply strip has no close button")
        harness.idleClock(400)
        assertFalse("the strip is put away", composer.isTopViewVisible())

        composer.setFieldText("an answer after cancelling")
        harness.idle()
        composer.sendMessage()
        harness.await("the answer to reach the site") {
            site.requestBodiesFor("posts.json").size >= 1
        }
        assertEquals(
            "an answer after cancelling an explicit target answers the root floor",
            PARENT_FLOOR.toString(),
            postedField("reply_to_post_number"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Reloading the page while its first replies read is still in flight: the read belongs to the
     * page that was there before, so it is dropped, and the reloaded page must still read its own
     * replies - a reload that forgot the in-flight load would strand the new thread on its root.
     *
     * <p>The reload seam is used directly: it is the deterministic way to invalidate an in-flight
     * read without a site write, which is the asynchronous invariant under test.</p>
     */
    @Test
    fun reloadingWhileAReadIsInFlightDoesNotStrandTheThread() {
        serveParentAtFloor12(replyCount = 6)
        val firstRead = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        site.serveResponse("posts/$PARENT_ID/replies") {
            if (calls.incrementAndGet() == 1) {
                firstRead.await(5, java.util.concurrent.TimeUnit.SECONDS)
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(replyArray(listOf(15 to PARENT_FLOOR, 16 to PARENT_FLOOR)))
            } else {
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(replyArray(listOf(21 to PARENT_FLOOR, 22 to PARENT_FLOOR)))
            }
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the first read to reach the site") { calls.get() == 1 }
        assertEquals("only the root while the first page is held", 1, floorsOf(thread).size)

        reloadThread(thread)
        harness.await("the reloaded page's own reads") { floorsOf(thread).size == 3 }

        // The held read's answer is stale now; releasing it must add nothing to the reloaded page.
        firstRead.countDown()
        harness.idle()
        harness.idleClock(200)
        assertEquals("a read invalidated by the reload did not land", 3, floorsOf(thread).size)
        assertEquals(
            "the rows are the reloaded read's own",
            listOf(21, 22),
            floorsOf(thread).drop(1).map { it.postNumber },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A rebuilt page with no seed resolves its parent from the site. Leaving before that answer
     * lands must draw nothing: the resolve belongs to the page that is gone, not the one that
     * replaced it (or no page at all).
     */
    @Test
    fun aRootResolveThatLandsAfterThePageIsGoneDrawsNothing() {
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("t/$TOPIC_ID/$PARENT_FLOOR.json") {
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(topicJson(
                    listOf(parentPost(4, "<p>the opening floor</p>", 0)),
                    listOf(PARENT_ID),
                ))
        }
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }

        harness.present(TopicFragment.forReplies(TOPIC_ID, "A topic", PARENT_ID, PARENT_FLOOR, 4))
        val thread = harness.visibleFragment() as TopicFragment
        harness.await("the root resolve to reach the site") {
            site.requestPaths.contains("t/$TOPIC_ID/$PARENT_FLOOR.json")
        }
        assertEquals("nothing is drawn while the parent is on its way", 0, floorsOf(thread).size)

        (harness.activity as io.github.baba983.nativedo.app.NativeDoActivity).onBackPressed()
        harness.idle()
        gate.countDown()
        settle(800)

        assertTrue(
            "a parent that lands after the page is gone draws nothing",
            floorsOf(thread).isEmpty(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A write the site accepted must refresh the page's root from the site: the floor's own reply
     * count only changes on the server, and a page that kept its launch-time copy would draw a root
     * whose count (and the list's end) is older than what the reader just did.
     */
    @Test
    fun aSuccessfulWriteRefreshesTheThreadsRootFromTheSite() {
        val serverReplyCount = java.util.concurrent.atomic.AtomicInteger(3)
        site.serveJsonFor("t/$TOPIC_ID/$PARENT_FLOOR.json") {
            val posts = listOf(
                parentPost(serverReplyCount.get(), "<p>the opening floor</p>", 0),
            )
            topicJson(posts, posts.map { it.getLong("id") })
        }
        site.serveJsonFor("posts/$PARENT_ID/replies") {
            replyArray(listOf(15 to PARENT_FLOOR, 16 to PARENT_FLOOR))
        }
        site.serveJson("posts.json", harness.fixture("new-topic-post.json"))
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the replies to arrive") { floorsOf(thread).size == 3 }
        assertEquals(
            "the launch count is the site's own",
            3,
            bubblesOf(thread).first().getRepliesCount(),
        )
        val rootReads = site.requestPaths.count { it == "t/$TOPIC_ID/$PARENT_FLOOR.json" }

        // The site accepted the reader's answer and now carries more replies than the page launched
        // with; only the server knows the new count.
        serverReplyCount.set(5)
        composerOf(thread).setFieldText("a new answer")
        harness.idle()
        composerOf(thread).sendMessage()
        harness.await("the write to reach the site") {
            site.recordedRequests.any { it == "POST /posts.json" }
        }
        harness.await("the page to re-read its root from the site") {
            site.requestPaths.count { it == "t/$TOPIC_ID/$PARENT_FLOOR.json" } > rootReads
        }
        harness.await("the site's own count to reach the root row") {
            bubblesOf(thread).first().getRepliesCount() == 5
        }
        assertEquals(
            "the page is still its own root and its direct replies",
            PARENT_FLOOR,
            floorsOf(thread).first().postNumber,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A page whose new ids carry no usable floor cannot advance the cursor, so the listing must end
     * there - while the rows it did carry are kept. Without this, the page would ask the same cursor
     * again for ids it already has.
     */
    @Test
    fun aPageWhoseIdsDoNotAdvanceTheCursorEndsTheListing() {
        serveParentAtFloor12(replyCount = 40)
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            when (afterOf(query)) {
                1 -> replyArray(listOf(15 to PARENT_FLOOR))
                else -> replyIdsWithoutFloor(listOf(46L, 47L))
            }
        }
        openTopicAtParent()

        val thread = openThreadFromFooter(PARENT_FLOOR)
        harness.await("the first page of replies") { floorsOf(thread).size == 2 }

        tapThreadStatus(thread)
        harness.await("the non-advancing page's readable rows") {
            floorsOf(thread).size == 4
        }
        assertEquals(
            "the readable rows of a non-advancing page are kept",
            4,
            floorsOf(thread).size,
        )

        tapThreadStatus(thread)
        harness.idle()
        assertEquals(
            "a page that does not advance the cursor ends the listing",
            listOf("after=1", "after=15"),
            site.queriesFor("posts/$PARENT_ID/replies"),
        )
        assertTrue(
            "and the reading line is gone",
            thread.threadStatusView()?.visibility != View.VISIBLE,
        )
        harness.assertNoTelegramSideEffects()
    }

    // ---- helpers ----

    private fun serveParentAtFloor12(
        replyCount: Int,
        rootCooked: String = "<p>the opening floor</p>",
        rootReactions: Int = 0,
        extraFloors: List<Int> = emptyList(),
        quoteFloor: Int? = null,
    ) {
        val posts = mutableListOf(
            parentPost(replyCount = replyCount, cooked = rootCooked, reactions = rootReactions, quoteFloor = quoteFloor),
        )
        for (floor in extraFloors) {
            posts += JSONObject(postJson(7000L + floor, floor, "u$floor", "User $floor", "<p>floor $floor</p>"))
        }
        site.serveJson("t/$TOPIC_ID/$PARENT_FLOOR.json", topicJson(posts, posts.map { it.getLong("id") }))
    }

    private fun parentPost(replyCount: Int, cooked: String, reactions: Int, quoteFloor: Int? = null): JSONObject =
        JSONObject(postJson(PARENT_ID, PARENT_FLOOR, "op", "Op", cooked)).apply {
            put("reply_count", replyCount)
            put("avatar_template", "/user_avatar/linux.do/op/{size}.png")
            if (quoteFloor != null) {
                put("reply_to_post_number", quoteFloor)
            }
            if (reactions > 0) {
                put("reaction_users_count", reactions)
                put("current_user_reaction", null)
                put("reactions", JSONArray().put(JSONObject().apply {
                    put("id", "heart"); put("count", reactions)
                }))
            }
        }

    private fun serveReplies(pages: Map<Int, List<Int>>) {
        site.serveJsonFor("posts/$PARENT_ID/replies") { query ->
            val after = afterOf(query)
            replyArray(pages.entries.firstOrNull { it.key == after }?.value.orEmpty().map { it to PARENT_FLOOR })
        }
    }

    private fun replyArray(replies: List<Pair<Int, Int>>): String {
        val array = JSONArray()
        for ((floor, parent) in replies) {
            array.put(JSONObject(postJson(7000L + floor, floor, "u$floor", "User $floor", "<p>reply $floor</p>")).apply {
                put("reply_to_post_number", parent)
                put("avatar_template", "/user_avatar/linux.do/u$floor/{size}.png")
            })
        }
        return array.toString()
    }

    private fun postJson(id: Long, floor: Int, username: String, name: String, cooked: String): String =
        JSONObject().apply {
            put("id", id)
            put("post_number", floor)
            put("username", username)
            put("name", name)
            put("reply_count", 0)
            put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
            put("cooked", cooked)
            put("avatar_template", "")
        }.toString()

    private fun topicJson(posts: List<JSONObject>, streamIds: List<Long>): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", JSONArray(posts))
        stream.put("stream", JSONArray(streamIds))
        return base.toString()
    }

    private fun openTopicAtParent() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forFloor(TOPIC_ID, "A topic", PARENT_FLOOR))
        harness.await("the topic window") { rowCount() >= 1 }
        harness.layoutApp()
        harness.idleClock(200)
    }

    /** Taps the footer of the bubble drawing [floorNumber] and returns the pushed page. */
    private fun openThreadFromFooter(floorNumber: Int): TopicFragment {
        val topic = harness.visibleFragment() as TopicFragment
        val bubble = bubblesOf(topic)[bubbleIndexOfFloor(topic, floorNumber)]
        val cell = laidOutCells().firstOrNull { it.messageObject === bubble }
            ?: throw AssertionError("the footer's cell for floor $floorNumber is not on screen")
        cell.delegate.didPressCommentButton(cell)
        harness.idle()
        val thread = harness.visibleFragment() as? TopicFragment
            ?: throw AssertionError("the footer opened no page")
        return thread
    }

    private fun bubbleIndexOfFloor(fragment: TopicFragment, floorNumber: Int): Int {
        val floors = floorsOf(fragment)
        val bubbleFloors = field(fragment, "bubbleFloors") as List<Int>
        val floorIndex = floors.indexOfFirst { it.postNumber == floorNumber }
        if (floorIndex < 0) {
            throw AssertionError("floor $floorNumber is not loaded")
        }
        return bubbleFloors.indexOfLast { it == floorIndex }
    }

    private fun tapThreadStatus(fragment: TopicFragment) {
        fragment.threadStatusView()?.performClick()
        harness.idle()
    }

    private fun pageDownButton(fragment: TopicFragment): ChatActivityBlurredRoundPageDownButton? =
        fragment.threadStatusView() as? ChatActivityBlurredRoundPageDownButton

    /** The empty discussion's copy, read from the stock service pill's own text; null when not empty. */
    private fun emptyTextOf(fragment: TopicFragment): String? {
        val field = fragment.javaClass.getDeclaredField("threadEmptyText")
        field.isAccessible = true
        return (field.get(fragment) as? CharSequence)?.toString()
    }

    /** Whether anything the presented screen draws still carries [text]. */
    private fun contentViewHasText(fragment: TopicFragment, text: String): Boolean {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        return bulkText(content).any { it.contains(text) }
    }

    /** The page-down button must sit entirely above the composer's top edge. */
    private fun assertAffordanceClearsComposer(fragment: TopicFragment, label: String) {
        val button = pageDownButton(fragment)
            ?: throw AssertionError("$label: the affordance is not upstream's page-down button")
        val buttonLocation = IntArray(2)
        button.getLocationInWindow(buttonLocation)
        val buttonBottom = buttonLocation[1] + button.height
        val composerLocation = IntArray(2)
        composerOf(fragment).getLocationInWindow(composerLocation)
        assertTrue(
            "$label: the affordance clears the composer " +
                "(button bottom $buttonBottom, composer top ${composerLocation[1]})",
            buttonBottom <= composerLocation[1] + 1,
        )
    }

    /** Switches the running screen's theme; a presented fragment is what applies it. */
    private fun switchTo(dark: Boolean) {
        if (Theme.isCurrentThemeDark() == dark) {
            return
        }
        Theme.applyTheme(Theme.getTheme(if (dark) "Dark Blue" else "Blue"), false, dark)
        harness.await("the theme to switch") {
            harness.idleClock(60)
            Theme.isCurrentThemeDark() == dark
        }
    }

    private fun render(name: String) {
        val contentView = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val width = AndroidUtilities.displaySize.x
        val height = AndroidUtilities.displaySize.y
        repeat(2) {
            contentView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)
            harness.idle(150)
        }
        val bitmap = Bitmap.createBitmap(
            contentView.measuredWidth.coerceAtLeast(1),
            contentView.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        contentView.draw(Canvas(bitmap))
        val repo = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(repo, ".amp/in/artifacts/comments-thread-v2/$name.png")
        out.parentFile.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("comments-thread: $out (${out.length()} bytes)")
    }

    private fun postedField(name: String): String? = postedFields(name).lastOrNull()

    private fun postedFields(name: String): List<String?> =
        site.requestBodiesFor("posts.json").map { body ->
            body.split('&').associate { part ->
                part.substringBefore('=') to java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            }[name]
        }

    private fun floorOfModel(message: MessageObject): Int =
        (message.messageOwner.id - 7000)

    /** The header capsule's second line, as the reader sees it. */
    private fun capsuleSubtitle(fragment: TopicFragment): String {
        val capsule = field(fragment, "avatarContainer") as org.telegram.ui.Components.ChatAvatarContainer
        val subtitle = capsule.subtitleTextView as? org.telegram.ui.ActionBar.SimpleTextView
        return subtitle?.text?.toString().orEmpty()
    }

    /** The reply strip's own close button, reached the way a tap is. */
    private fun findCloseButton(view: View): View? {
        if (view.contentDescription == "取消回复" || view.contentDescription == "取消编辑") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findCloseButton(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** Every text a view tree draws, for reading a Bulletin the screen showed in passing. */
    private fun bulkText(view: View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is org.telegram.ui.ActionBar.SimpleTextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { bulkText(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** The text of the pass-through Bulletin, watched for before it hides itself. */
    private fun awaitBulletin(): List<String> {
        repeat(5_000) {
            harness.idle(1)
            val bulletin = org.telegram.ui.Components.Bulletin.getVisibleBulletin()
            if (bulletin != null) {
                val texts = bulkText(bulletin.getLayout())
                if (texts.any { it.contains("这一页没能读出来") }) return texts
            }
            java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
        }
        throw AssertionError("the failure Bulletin never appeared")
    }

    /** Let an off-thread round trip land: drain the main looper while real time passes. */
    private fun settle(millis: Long) {
        val slices = (millis / 20).toInt().coerceAtLeast(1)
        repeat(slices) {
            harness.idle()
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L)
        }
    }

    /** The reload seam: invalidate an in-flight replies read the way a write does, deterministically. */
    private fun reloadThread(fragment: TopicFragment) {
        TopicFragment::class.java.getDeclaredMethod("reloadThread").apply { isAccessible = true }
            .invoke(fragment)
    }

    /** A reply the endpoint carries with a real id but no usable floor. */
    private fun replyIdsWithoutFloor(ids: List<Long>): String {
        val array = JSONArray()
        for (id in ids) {
            array.put(JSONObject().apply {
                put("id", id)
                put("username", "u$id")
                put("name", "User $id")
                put("reply_count", 0)
                put("created_at", "2024-01-01T10:00:00.000Z")
                put("cooked", "<p>a reply without a floor</p>")
                put("reply_to_post_number", PARENT_FLOOR)
                put("avatar_template", "")
            })
        }
        return array.toString()
    }

    private fun afterOf(query: String): Int =
        query.substringAfter("after=").substringBefore('&').toIntOrNull() ?: 1

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(120, 180, 90))
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun floorsOf(fragment: TopicFragment): List<Post> =
        field(fragment, "floors") as List<Post>

    @Suppress("UNCHECKED_CAST")
    private fun bubblesOf(fragment: TopicFragment): List<MessageObject> =
        field(fragment, "bubbles") as List<MessageObject>

    private fun topicDetailOf(fragment: TopicFragment): TopicDetail =
        field(fragment, "topicDetail") as TopicDetail

    private fun composerOf(fragment: TopicFragment): ChatActivityEnterView =
        field(fragment, "composer") as ChatActivityEnterView

    private fun field(target: Any, name: String): Any {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as Any
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 733L
        const val PARENT_ID = 7001L
        const val PARENT_FLOOR = 12
        const val OUTSIDE_FLOOR = 40
    }
}
