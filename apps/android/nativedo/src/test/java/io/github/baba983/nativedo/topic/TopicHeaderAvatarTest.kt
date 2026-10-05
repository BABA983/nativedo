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
    }
}
