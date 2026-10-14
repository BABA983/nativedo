package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import okhttp3.mockwebserver.MockResponse

/**
 * The topic screen's center capsule, end to end: the picture a list row already downloaded is what
 * the capsule shows, and the site's answer only rebinds it when it names a different author.
 *
 * <p>The failures this pins: the capsule is empty until `/t/{id}.json` answers, although the row
 * that opened the screen drew the author's avatar; the picture is downloaded a second time (or the
 * capsule ends up empty after the answer because the hand-over was reset to the initials); a
 * different author named by the site never re-binds the capsule; and an answer that leaves
 * `details` out (a windowed page of a resumed read) must not leave the capsule without the author
 * an earlier answer named.</p>
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
internal class TopicHeaderAvatarTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    /**
     * Templates of this test run's own. Upstream's file loader keeps one cache directory for the
     * whole test JVM, and its image loader caches decoded bytes by file id, so an avatar another
     * test fetched at the same id would be drawn instead of the one served here.
     */
    private val runToken = System.nanoTime().toString().takeLast(6)
    private val authorTemplate = "/user_avatar/linux.do/baba/{size}/hdr$runToken.png"
    private val otherTemplate = "/user_avatar/linux.do/carol/{size}/hdr$runToken.png"

    /** The colours the two authors' pictures have, so the capsule's own bitmap says who it shows. */
    private val authorColor = Color.rgb(255, 0, 255)
    private val otherColor = Color.rgb(0, 128, 255)

    @Before
    fun setUp() {
        site.start()
        site.serveJson("latest.json", latestJson())
        site.serveJson("t/$TOPIC_ID.json", topicJson())
        site.serveImage(site.avatarPath(authorTemplate), solidPng(authorColor))
        site.serveImage(site.avatarPath(otherTemplate), solidPng(otherColor))
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> io.github.baba983.nativedo.core.di.AppContainer(context) }
        site.stop()
    }

    /** A list entry must paint cached metadata while both reads are pending, then trust detail. */
    @Test
    fun theListEntrySeedsSubtitleAndDetailCorrectsIt() {
        site.serveFixture("categories.json", "categories.json")
        val answer = CountDownLatch(1)
        site.serveResponse("t/$TOPIC_ID.json") {
            answer.await(30, TimeUnit.SECONDS)
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                org.json.JSONObject(topicJson()).put("posts_count", 4).put("category_id", 2).toString(),
            )
        }
        harness.bootApp()
        harness.await("the list screen") { harness.visibleFragment() is TopicListFragment }
        val list = harness.visibleFragment() as TopicListFragment
        val container = list.javaClass.getDeclaredField("container").apply { isAccessible = true }
            .get(list) as io.github.baba983.nativedo.core.di.AppContainer
        harness.await("the list and category cache") {
            rowCount() == 2 && container.categoryStore.categories.value.isNotEmpty()
        }
        val categories = CountDownLatch(1)
        site.serveResponse("categories.json") {
            categories.await(30, TimeUnit.SECONDS)
            MockResponse().setHeader("Content-Type", "application/json").setBody(harness.fixture("categories.json"))
        }
        try {
            DiscourseLoader.categories(container, {}, { throw AssertionError(it) })
            harness.await("the category refresh is also pending") {
                site.requestPaths.count { it == "categories.json" } >= 2
            }
            val entry = io.github.baba983.nativedo.core.model.TopicSummary(
                id = TOPIC_ID, title = "Header metadata", authorUsername = "baba",
                authorAvatarTemplate = authorTemplate, replyCount = 17, categoryId = 4,
            )
            list.javaClass.getDeclaredMethod("presentTopic", entry.javaClass).apply { isAccessible = true }
                .invoke(list, entry)
            harness.layoutApp()
            val capsule = capsule()
            assertEquals("开发调优 · 17 回复", subtitle(capsule))
            harness.renderAppFrame("header-profile-subtitle-pending")
            answer.countDown()
            harness.await("detail corrects both cached fields") {
                subtitle(capsule) == "运营反馈 · 3 回复"
            }
            harness.renderApp("header-profile-subtitle-corrected")
        } finally {
            answer.countDown()
            categories.countDown()
        }
    }

    /** Measure the Canvas translation, not the marquee enable flag. */
    @Test
    fun theLongTitleMovesItsDrawingOverTime() {
        val title = "A deliberately long topic title that cannot fit inside the center capsule and must scroll"
        site.serveJson("t/$TOPIC_ID.json", org.json.JSONObject(topicJson()).put("title", title).toString())
        harness.bootApp()
        harness.await("the list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, title, "baba", authorTemplate))
        harness.await("the topic") { rowCount() == 1 }
        val text = capsule().titleTextView
        val bitmap = Bitmap.createBitmap(text.width, text.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        text.draw(canvas)
        val initial = text.layoutX
        harness.renderAppFrame("header-profile-marquee-before")
        repeat(160) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(17))
            text.draw(canvas)
        }
        assertTrue("the title's drawn origin moves left ($initial -> ${text.layoutX})", text.layoutX < initial - 10)
        harness.renderAppFrame("header-profile-marquee-after")
    }

    /**
     * The row that opens a topic already drew the author's picture; the capsule must show it while
     * the topic is still being read, and the answer must not ask the site for it a second time.
     */
    @Test
    fun theCapsuleShowsTheRowsAuthorBeforeTheTopicIsRead() {
        // Hold the topic's answer: the capsule has nothing but the row to bind from.
        val answer = CountDownLatch(1)
        site.serveResponse("t/$TOPIC_ID.json") {
            answer.await(30, TimeUnit.SECONDS)
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(topicJson())
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.await("the list to download the author's avatar") {
            site.requestPaths.count { it == site.avatarPath(authorTemplate) } >= 1
        }
        val author = DiscourseAvatars.user("baba", "", authorTemplate, site.baseUrl)
        harness.await("the avatar to land where upstream looks for it") {
            DiscourseAvatars.isCached(author)
        }
        val requestsBefore = site.requestPaths.count { it == site.avatarPath(authorTemplate) }

        harness.present(TopicFragment.forTopic(TOPIC_ID, "Header avatar", "baba", authorTemplate))
        harness.await("the capsule to draw the picture the row downloaded") {
            harness.drawApp()
            capsuleBitmap() != null
        }
        assertEquals("the capsule draws the row's own author picture",
            authorColor, dominantColor(capsuleBitmap()!!))
        assertEquals("and nothing was downloaded a second time",
            requestsBefore, site.requestPaths.count { it == site.avatarPath(authorTemplate) })

        answer.countDown()
        harness.await("the topic to load") { rowCount() == 1 }
        harness.idleClock(200)
        assertTrue("the capsule still holds the picture after the answer",
            capsuleBitmap() != null)
        assertEquals("the answer did not re-download the row's picture",
            requestsBefore, site.requestPaths.count { it == site.avatarPath(authorTemplate) })
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site's answer is authoritative about the original poster: when it names someone else than
     * the row did (a row can carry a most-recent poster's face), the capsule must switch to them
     * and fetch their picture.
     */
    @Test
    fun aDifferentAuthorInTheAnswerRebindsTheCapsule() {
        site.serveJson("t/$TOPIC_ID.json", topicJson(createdBy = "carol", template = otherTemplate))
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val author = DiscourseAvatars.user("baba", "", authorTemplate, site.baseUrl)
        harness.await("the row's avatar to land") { DiscourseAvatars.isCached(author) }

        harness.present(TopicFragment.forTopic(TOPIC_ID, "Header avatar", "baba", authorTemplate))
        harness.await("the capsule to show the row's author") {
            harness.drawApp()
            capsuleBitmap() != null
        }
        harness.await("the answer to name the other author and rebind the capsule") {
            harness.drawApp()
            val bitmap = capsuleBitmap() ?: return@await false
            dominantColor(bitmap) == otherColor
        }
        harness.await("the other author's own picture to have been fetched") {
            DiscourseAvatars.isCached(DiscourseAvatars.user("carol", "", otherTemplate, site.baseUrl))
        }
        assertEquals("the capsule kept the stale author's picture",
            otherColor, dominantColor(capsuleBitmap()!!))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A reader who resumes deep into a topic: the head answer names the author, but its posts are
     * thrown away for a window of around the reader's floor, and that window can leave `details`
     * out. The capsule must still show the author the head answer named instead of staying empty.
     */
    @Test
    fun anAnswerWithoutDetailsDoesNotEmptyTheCapsuleAfterAResumedRead() {
        site.serveJson("t/$TOPIC_ID.json", resumedHeadJson())
        site.serveJson("t/$TOPIC_ID/$RESUME_FLOOR.json", windowJsonWithoutDetails())
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val author = DiscourseAvatars.user("baba", "", authorTemplate, site.baseUrl)
        harness.await("the author's picture to land") { DiscourseAvatars.isCached(author) }

        // Opened without a row: nothing but the site's own answers can name the author.
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Header avatar"))
        harness.await("the resumed window to load") { rowCount() == 2 }
        harness.await("the window answer to be the one on screen") {
            site.requestPaths.contains("t/$TOPIC_ID/$RESUME_FLOOR.json")
        }
        harness.await("the capsule to show the author the head answer named") {
            harness.drawApp()
            capsuleBitmap() != null
        }
        assertEquals("the capsule shows the author, not an empty spot",
            authorColor, dominantColor(capsuleBitmap()!!))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A topic opened through a body link names no title: the capsule is created and drawn with an
     * empty title, and only the site's answer names the topic (a link's factory carries none:
     * {@code DidPressUrl -> TopicFragment.forFloor(topicId, null, floor)}). An empty title still
     * measures to its padding, so the capsule is laid out before the real title exists; an
     * already-laid-out SimpleTextView then skips requestLayout when its text changes
     * ({@code recreateLayoutMaybe} only re-creates the layout), the header keeps the empty title's
     * box, and the marquee's own layer shears the site's longer line in half. The header must ask
     * to be laid out again for the site's title; the subtitle stays whole and the title still
     * scrolls.
     *
     * <p>The frame clock is held while the answer lands, so the capsule is looked at in the state a
     * reader sees when the title arrives between two frames - the transient this pins. The end
     * state (the box that holds the whole line, the subtitle, the marquee) is checked once the
     * clock runs again.</p>
     */
    @Test
    fun aLinkWithoutATitleLaysTheCapsuleOutForTheSiteTitle() {
        val title = "民航航空CaDav全新CaDav更新：一个长到必须滚动才能看全的中文标题"
        val answer = CountDownLatch(1)
        site.serveResponse("t/$TOPIC_ID.json") {
            answer.await(30, TimeUnit.SECONDS)
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody(org.json.JSONObject(topicJson()).put("title", title).toString())
        }
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        // A body link: the arguments name no title; only the site's answer has it.
        harness.present(TopicFragment.forTopic(TOPIC_ID, null))
        harness.drawApp()
        val titleView = capsule().titleTextView
        assertTrue("the link's capsule drew an empty title", titleView.text.isNullOrEmpty())
        val emptyBox = titleView.height

        val pausedFrames = org.robolectric.shadows.ShadowChoreographer.isPaused()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        try {
            answer.countDown()
            // Step the looper one task at a time: the answer sets the title in one task, and the
            // layout pass that would hide the stale box is a later one. Stop the instant the title
            // lands, so the capsule is read in the state the reader sees when it arrives.
            var landed = false
            for (i in 0 until 2_000) {
                harness.idle(1)
                if (!titleView.text.isNullOrEmpty()) {
                    landed = true
                    break
                }
                LockSupport.parkNanos(1_000_000L)
            }
            assertTrue("the site's title reached the capsule", landed)
            assertEquals("the site's title reached the capsule", title, titleView.text.toString())
            assertTrue(
                "the empty title's box ($emptyBox px) is shorter than the site's own line " +
                    "(${titleView.textHeight} px + ${titleView.paddingBottom} px of padding), yet the " +
                    "capsule was not asked to lay out again, so the line would be drawn clipped",
                titleView.isLayoutRequested,
            )
        } finally {
            org.robolectric.shadows.ShadowChoreographer.setPaused(pausedFrames)
        }

        // With the frame clock running, the pending layout pass gives the title the box its line needs.
        harness.await("the capsule to lay out for the site's title", iterations = 600) {
            harness.drawApp()
            val tv = capsule().titleTextView
            tv.height - tv.paddingBottom >= tv.textHeight
        }
        val laid = capsule().titleTextView
        assertTrue(
            "the title's box (${laid.height} px) must hold its whole line (${laid.textHeight} px)",
            laid.height - laid.paddingBottom >= laid.textHeight,
        )
        assertTrue("the subtitle stays whole", subtitle(capsule()).isNotEmpty())

        // The long title must still scroll: the fix asks for a layout, it does not disable the marquee.
        val bitmap = Bitmap.createBitmap(laid.width, laid.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        laid.draw(canvas)
        val initialX = laid.layoutX
        repeat(160) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(17))
            laid.draw(canvas)
        }
        assertTrue(
            "the title still scrolls after the late bind (${initialX} -> ${laid.layoutX})",
            laid.layoutX < initialX - 10,
        )
        harness.renderApp("header-profile-link-late-title")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The control for the link above: a topic opened from a row already names its title in the
     * arguments, so the capsule is laid out with the real title from the first pass - the box holds
     * the line without any later re-measure.
     */
    @Test
    fun aRowThatNamesTheTitleDrawsTheCapsuleWholeAtOnce() {
        val title = "民航航空CaDav全新CaDav更新：一个长到必须滚动才能看全的中文标题"
        site.serveJson("t/$TOPIC_ID.json", org.json.JSONObject(topicJson()).put("title", title).toString())
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, title, "baba", authorTemplate))
        harness.await("the topic") { rowCount() == 1 }
        harness.drawApp()
        val titleView = capsule().titleTextView
        assertEquals("the row's title reached the capsule", title, titleView.text.toString())
        assertTrue(
            "a title known before the first pass holds its whole line " +
                "(box=${titleView.height} th=${titleView.textHeight})",
            titleView.height - titleView.paddingBottom >= titleView.textHeight,
        )
        harness.renderApp("header-profile-row-title")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The same link, answered at once: the site's title lands while the capsule is still being
     * created, so it is laid out with the real title from the first pass and drawn whole.
     */
    @Test
    fun aLinkAnsweredAtOnceDrawsTheCapsuleWhole() {
        val title = "民航航空CaDav全新CaDav更新：一个长到必须滚动才能看全的中文标题"
        site.serveJson("t/$TOPIC_ID.json", org.json.JSONObject(topicJson()).put("title", title).toString())
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, null))
        harness.await("the topic") { rowCount() == 1 }
        harness.drawApp()
        val titleView = capsule().titleTextView
        assertEquals("the site's title reached the capsule", title, titleView.text.toString())
        assertTrue(
            "a link answered at once draws the whole title " +
                "(box=${titleView.height} th=${titleView.textHeight})",
            titleView.height - titleView.paddingBottom >= titleView.textHeight,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The discussion page shares the capsule, so its header goes through the same [setCapsuleTitle]
     * path: a page opened with no title in its arguments is named only by the answer that resolves
     * its parent, and its title must be laid out whole too.
     */
    @Test
    fun aThreadOpenedWithoutATitleLaysTheCapsuleOutForTheSiteTitle() {
        val title = "民航航空CaDav全新CaDav更新：一个长到必须滚动才能看全的中文标题"
        val base = org.json.JSONObject(topicJson()).put("title", title)
        // The replies endpoint is never read for a root the site says has none.
        base.getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0).put("reply_count", 0)
        site.serveJson("t/$TOPIC_ID/$THREAD_PARENT_FLOOR.json", base.toString())
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forReplies(TOPIC_ID, null, THREAD_PARENT_ID, THREAD_PARENT_FLOOR, 0))
        harness.await("the parent resolved from the site") {
            harness.visibleFragment() is TopicFragment &&
                capsule().titleTextView.text?.toString() == title
        }
        harness.drawApp()
        val titleView = capsule().titleTextView
        assertEquals("the site's title reached the thread's capsule", title, titleView.text.toString())
        assertTrue(
            "the thread's capsule holds its whole title line " +
                "(box=${titleView.height} th=${titleView.textHeight})",
            titleView.height - titleView.paddingBottom >= titleView.textHeight,
        )
        harness.renderApp("header-profile-thread-title")
        harness.assertNoTelegramSideEffects()
    }

    // ---------------------------------------------------------------------- fixture shaping

    /** The list the reader came from: the first topic's original poster is [authorTemplate]'s. */
    private fun latestJson(): String {
        val base = org.json.JSONObject(harness.fixture("latest-topics.json"))
        val users = base.optJSONArray("users")
        if (users != null) {
            for (index in 0 until users.length()) {
                val user = users.getJSONObject(index)
                if (user.optString("username") == "baba") {
                    user.put("avatar_template", authorTemplate)
                }
            }
        }
        return base.toString()
    }

    /** One topic detail, with the author the site says wrote it. */
    private fun topicJson(
        createdBy: String = "baba",
        template: String = authorTemplate,
    ): String {
        val base = org.json.JSONObject(harness.fixture("topic-detail.json"))
        base.put("id", TOPIC_ID)
        base.put("details", org.json.JSONObject().put(
            "created_by",
            org.json.JSONObject()
                .put("username", createdBy)
                .put("name", createdBy.replaceFirstChar { it.uppercase() })
                .put("avatar_template", template),
        ))
        return base.toString()
    }

    /** The head answer of a resumed read: the author is here, the reader's floor is not. */
    private fun resumedHeadJson(): String {
        val base = org.json.JSONObject(topicJson())
        base.put("highest_post_number", 60)
        base.put("posts_count", 60)
        base.put("last_read_post_number", RESUME_FLOOR)
        return base.toString()
    }

    /** The window around the reader's floor, as a site that leaves `details` out answers it. */
    private fun windowJsonWithoutDetails(): String {
        val window = org.json.JSONObject(topicJson())
        window.remove("details")
        val posts = window.getJSONObject("post_stream").getJSONArray("posts")
        val first = org.json.JSONObject(posts.getJSONObject(0).toString())
        first.put("post_number", RESUME_FLOOR)
        val second = org.json.JSONObject(posts.getJSONObject(0).toString())
        second.put("post_number", RESUME_FLOOR + 1).put("id", 10_500L + RESUME_FLOOR).put("username", "carol")
        window.getJSONObject("post_stream").put("posts", org.json.JSONArray().put(first).put(second))
        return window.toString()
    }

    private fun solidPng(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    /** The colour a picture the capsule loaded is made of; a flat avatar is one colour. */
    private fun dominantColor(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val counts = HashMap<Int, Int>()
        for (pixel in pixels) {
            counts[pixel] = (counts[pixel] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    // ---------------------------------------------------------------------- capsule inspection

    private fun capsule(): ChatAvatarContainer = TopicFragment::class.java
        .getDeclaredField("avatarContainer").apply { isAccessible = true }
        .get(harness.visibleFragment()) as ChatAvatarContainer

    private fun subtitle(capsule: ChatAvatarContainer): String =
        (capsule.subtitleTextView as org.telegram.ui.ActionBar.SimpleTextView).text?.toString().orEmpty()

    /** What the center capsule is drawing right now; null while it has no picture. */
    private fun capsuleBitmap(): Bitmap? {
        val fragment = harness.visibleFragment() as? TopicFragment ?: return null
        val field = TopicFragment::class.java.getDeclaredField("avatarContainer").apply { isAccessible = true }
        val container = field.get(fragment) as? ChatAvatarContainer ?: return null
        val receiver: ImageReceiver = container.avatarImageView.imageReceiver
        return receiver.bitmap
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 404L
        const val RESUME_FLOOR = 50
        const val THREAD_PARENT_ID = 1001L
        const val THREAD_PARENT_FLOOR = 1
    }
}
