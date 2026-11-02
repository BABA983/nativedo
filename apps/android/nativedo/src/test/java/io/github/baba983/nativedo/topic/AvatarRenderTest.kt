package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okio.Buffer

/**
 * Avatars, end to end: fetched from a Discourse `avatar_template` and drawn by upstream's own cells.
 *
 * <p>The failures this pins: a message cell builds its avatar but does not draw it (upstream's chat
 * list draws it after the row), so a topic whose rows are never given that step shows an empty
 * column where the avatar belongs; and an avatar built the way this client builds one needs a path
 * upstream can resolve without a Telegram datacenter, or the row stays on its initials.</p>
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
internal class AvatarRenderTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    /**
     * A template of this test's own. Upstream's image singletons outlive a test class, and a file
     * another test already downloaded at the same URL would be served from its cache instead of the
     * bytes served here, so the fixtures' templates are replaced.
     */
    /**
     * A template of this test run's own. Upstream's file loader keeps one cache directory for the
     * whole test JVM, and its image loader caches decoded bytes by file id, so an avatar another
     * test fetched at the same id would be drawn instead of the one served here.
     */
    private val runToken = System.nanoTime().toString().takeLast(6)
    private val avatarTemplate = "/user_avatar/linux.do/baba/{size}/$runToken.png"
    private val listAvatarTemplate = "/user_avatar/linux.do/baba/{size}/list$runToken.png"

    @Before
    fun setUp() {
        site.start()
        site.serveJson("t/$TOPIC_ID.json", topicJsonWithAvatar())
        site.serveJson("latest.json", latestJsonWithAvatar())
        site.serveImage(site.avatarPath(avatarTemplate), magentaPng())
        site.serveImage(site.avatarPath(listAvatarTemplate), greenPng())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun thePostAvatarIsFetchedAndDrawnInTheMessageList() {
        harness.bootApp()
        val listed = runCatching { harness.await("the topic list to load") { rowCount() == 2 } }
        println("avatar: rows=" + rowCount() + " requests=" + site.requestPaths +
            " fragment=" + currentFragmentName())
        listed.exceptionOrNull()?.let { throw it }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Avatar post"))
        harness.await("the topic to load") { rowCount() == 1 }
        val author = DiscourseAvatars.user("baba", "Baba", avatarTemplate, site.baseUrl)
        harness.await("the avatar to be fetched") { site.cookieHeaderFor(site.avatarPath(avatarTemplate)) != null }
        harness.await("the avatar to land where upstream looks for it") {
            DiscourseAvatars.isCached(author)
        }
        // A row is handed its avatar while it is drawn, and the image loader decodes it on its own
        // thread, which this wait drains the same way it drains every other one.
        harness.await("the row to hold the downloaded avatar") {
            harness.drawApp()
            laidOutCells().any { cell -> cell.avatarImage?.bitmap != null }
        }
        val drawn = magentaPixels(harness.renderApp("post-avatar"))
        println("avatar: magenta pixels in the render = $drawn")
        assertTrue(
            "the row drew the picture it downloaded, not the author's initials",
            drawn > 2_000,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theAvatarSpotIsNeverEmptyWhileThePictureIsNotDrawn() {
        // The site answers with something that is not a picture, which is what a broken download
        // looks like to a row: the spot has to keep saying who wrote the post.
        val brokenTemplate = "/user_avatar/linux.do/baba/{size}/broken$runToken.png"
        site.serveJson("t/$TOPIC_ID.json", topicJsonWithAvatar(brokenTemplate))
        site.serveImage(site.avatarPath(brokenTemplate), "not a picture".toByteArray())
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Broken avatar"))
        harness.await("the topic to load") { rowCount() == 1 }
        val author = DiscourseAvatars.user("baba", "Baba", brokenTemplate, site.baseUrl)
        harness.await("the site's answer to land on disk") { DiscourseAvatars.isCached(author) }
        // The row hands it over while it is drawn; the decode then fails, and what is left has to
        // be the author's initials rather than the hole the hand-over would otherwise leave.
        harness.await("the row to have been given the file") {
            harness.drawApp()
            laidOutCells().any { it.avatarImage?.imageLocation?.path != null }
        }
        harness.idleClock(400)

        val render = harness.renderApp("post-avatar-broken")
        val initials = colouredPixelsInAvatarSpot(render)
        println("avatar: placeholder pixels with an unreadable picture = $initials")
        assertTrue("the avatar spot still holds the author's initials", initials > 500)
        assertEquals("and no picture was drawn there", 0, magentaPixels(render))
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun theTopicListRowsDrawTheAuthorAvatar() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        // The fixture's first topic has no user object for its original poster, so the authors of
        // the two rows are resolved differently; both are served here.
        harness.await("a row avatar to be fetched") {
            site.requestPaths.any { it.startsWith("user_avatar/") && site.cookieHeaderFor(it) != null }
        }
        // The first row uses the top-level user's default photo. Its bitmap does not prove that
        // the second row's green photo has finished decoding.
        harness.await("the second row to hold the avatar it downloaded") {
            harness.drawApp()
            laidOutChildren()
                .filterIsInstance<DialogCell>()
                .getOrNull(1)?.avatarImage?.bitmap != null
        }

        val green = greenPixels(harness.renderApp("topic-list-avatars"))
        println("avatar: green pixels across the list = $green")
        assertTrue("a row drew the avatar it downloaded", green > 1_000)
        val rows = laidOutChildren().filterIsInstance<DialogCell>()
        assertTrue("the list drew its rows", rows.size >= 2)
        println("avatar: row requests = " + site.requestPaths.filter { it.startsWith("user_avatar/") })
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aListAvatarArrivingAfterAnotherRowsPhotoIsDrawnStillAppears() {
        // A decoded picture on the first row does not mean the second row's HTTP download has
        // arrived. Keep that download pending until both rows have been drawn without it.
        val path = "uploads/late-list-$runToken.png"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val latest = org.json.JSONObject(harness.fixture("latest-topics.json"))
        latest.getJSONObject("topic_list").getJSONArray("topics").getJSONObject(1)
            .getJSONArray("posters").getJSONObject(0).getJSONObject("user")
            .put("avatar_template", "/$path")
        site.serveJson("latest.json", latest.toString())
        val bytes = greenPng()
        site.serveResponse(path) {
            entered.countDown()
            check(release.await(30, TimeUnit.SECONDS)) { "the test did not release the avatar" }
            MockResponse().setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(bytes))
        }
        try {
            harness.bootApp()
            harness.await("both list rows before the delayed photo arrives") {
                harness.drawApp()
                entered.count == 0L && rowCount() == 2 &&
                    laidOutChildren().filterIsInstance<DialogCell>().firstOrNull()
                        ?.avatarImage?.bitmap != null
            }
            assertEquals("the second row's photo is still pending", 0,
                greenPixels(harness.renderApp("list-avatar-pending")))
            release.countDown()
            val author = DiscourseAvatars.user("searcher", "searcher", "/$path", site.baseUrl)
            harness.await("the delayed list avatar to land on disk") { DiscourseAvatars.isCached(author) }
            harness.await("the second row to draw its delayed photo", iterations = 200) {
                greenPixels(harness.renderApp("list-avatar-delayed")) > 1_000
            }
            harness.assertNoTelegramSideEffects()
        } finally {
            release.countDown()
        }
    }

    private fun topicJsonWithAvatar(template: String = avatarTemplate): String {
        val base = org.json.JSONObject(harness.fixture("topic-detail.json"))
        val posts = base.getJSONObject("post_stream").getJSONArray("posts")
        for (index in 0 until posts.length()) {
            posts.getJSONObject(index).put("avatar_template", template)
        }
        return base.toString()
    }

    private fun latestJsonWithAvatar(): String {
        val base = org.json.JSONObject(harness.fixture("latest-topics.json"))
        val topics = base.getJSONObject("topic_list").getJSONArray("topics")
        for (index in 0 until topics.length()) {
            val posters = topics.getJSONObject(index).optJSONArray("posters") ?: continue
            for (poster in 0 until posters.length()) {
                posters.getJSONObject(poster).optJSONObject("user")?.put("avatar_template", listAvatarTemplate)
            }
        }
        return base.toString()
    }

    /** The colour the served avatar has and the initials placeholder never does. */
    private val magenta = Color.rgb(255, 0, 255)

    private fun magentaPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(magenta)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun magentaPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) > 200 && Color.blue(it) > 200 && Color.green(it) < 80 }
    }

    /** The topic list's author picture, which is a different colour so the two never confuse. */
    private fun greenPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(0, 255, 0))
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    /**
     * What the avatar spot holds: the initials circle upstream draws for an author is coloured,
     * while a spot with nothing in it is the window's background. Only the left column of the
     * first row is looked at, which is where a chat row's avatar sits.
     */
    private fun colouredPixelsInAvatarSpot(file: File, ignored: Int = 0): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val width = bitmap.width
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until minOf(width, 200)) {
                val pixel = pixels[y * width + x]
                if (pixel == ignored) continue
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                if (maxOf(red, green, blue) - minOf(red, green, blue) > 40) count++
            }
        }
        return count
    }

    private fun greenPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.green(it) > 200 && Color.red(it) < 80 && Color.blue(it) < 80 }
    }

    private fun currentFragmentName(): String? = harness.visibleFragmentName()

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private fun laidOutChildren(): List<android.view.View> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 404L
    }
}
