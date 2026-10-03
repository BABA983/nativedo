package io.github.baba983.nativedo.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import io.github.baba983.nativedo.topic.DiscourseAvatars
import io.github.baba983.nativedo.topic.TopicFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okio.Buffer

/**
 * A post's author, tapped.
 *
 * <p>The failure this pins: a tap on the avatar or the name above a bubble goes to upstream's own
 * delegate method, and a client that leaves it unanswered opens nothing - the reader cannot reach
 * the person who wrote the post. The screen behind it has to be the site's own profile
 * (`/u/{username}.json`), shown in upstream's cells, and it must not touch Telegram: upstream's
 * `ProfileActivity` would load the full user through tgnet and write it into Telegram's database.
 * </p>
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
internal class ProfileScreenTest {

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
    fun tappingTheAuthorsAvatarOpensTheirSiteProfile() {
        val avatarPath = "user_avatar/linux.do/baba/96/1.png"
        site.serveFixture("u/baba.json", "user-profile.json")
        site.serveImage(avatarPath, magentaPng(96, 96))
        site.serveCooked(TOPIC_ID, "<p>你好。</p>")

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A post"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().first { it.messageObject != null }
        val author = authorOf(cell.messageObject!!)

        // The tap upstream's own cell hands over for both the avatar and the name.
        cell.delegate.didPressUserAvatar(cell, author, 0f, 0f, false)
        harness.idle()

        harness.await("the site for the profile") { site.requestPaths.contains("u/baba.json") }
        harness.await("the profile screen to show the site's fields") {
            harness.layoutApp()
            detailValue("信任等级") == "3"
        }
        assertEquals("the handle the site sent", "baba", author.username)
        assertEquals("the joined date", "2024-05-26", detailValue("加入时间"))
        assertEquals("the title the site sent", "活跃用户", detailValue("头衔"))
        assertEquals("the score the site sent", "2731", detailValue("得分"))
        assertEquals("the followers the site sent", "340", detailValue("关注者"))
        // A list binds the rows that are on screen: the fields below the fold need a scroll, which
        // is what the reader does to reach them. The screen itself starts at the "资料" section now
        // that the head is the collapsing header and not a list row.
        listView()?.scrollToPosition(7)
        harness.layoutApp()
        assertEquals("seconds read as hours", "79 小时", detailValue("阅读时长"))
        assertEquals("the views the site sent", "97", detailValue("浏览"))
        listView()?.scrollToPosition(13)
        harness.layoutApp()
        assertEquals("the accepted answers the site sent", "18", detailValue("被采纳回复"))

        harness.await("the avatar to be fetched") {
            site.recordedRequests.any { it.contains(avatarPath) }
        }
        harness.await("the avatar to land where the row reads it") {
            val avatarUser = DiscourseAvatars.user(
                "baba", "Baba", "/user_avatar/linux.do/baba/{size}/1.png", site.baseUrl,
            )
            DiscourseAvatars.isCached(avatarUser)
        }

        // The head of the screen is what the reader sees first: back to it, and the picture the
        // site served has to be in the pixels.
        listView()?.scrollToPosition(0)
        harness.layoutApp()
        var magenta = 0
        harness.await("the header to draw the site's picture", iterations = 200) {
            magenta = magentaPixels(harness.renderApp("profile-screen"))
            magenta > 0
        }
        println("profile: magenta pixels in the render = $magenta")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aProfileAvatarArrivingWhileItsHeaderIsOffscreenAppearsOnReturn() {
        // A missing file must not leave an upstream datacenter load pending under the same key
        // when the HTTP download arrives while the header is recycled below the fold.
        val path = "uploads/late-profile-${System.nanoTime()}.png"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val profile = org.json.JSONObject(harness.fixture("user-profile.json"))
        profile.getJSONObject("user").put("avatar_template", "/$path")
        site.serveJson("u/baba.json", profile.toString())
        val bytes = magentaPng(96, 96)
        site.serveResponse(path) {
            entered.countDown()
            check(release.await(30, TimeUnit.SECONDS)) { "the test did not release the avatar" }
            MockResponse().setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(bytes))
        }
        try {
            harness.bootApp()
            harness.await("the topic list to load") { rowCount() == 2 }
            harness.present(ProfileFragment.forUser("baba", "Baba"))
            harness.await("the profile header before its photo arrives") {
                harness.drawApp()
                entered.count == 0L && detailValue("信任等级") == "3"
            }
            harness.idleUpstreamQueues()
            harness.idle()
            assertEquals("no photo was delivered yet", 0,
                magentaPixels(harness.renderApp("profile-avatar-pending")))
            listView()?.scrollToPosition(13)
            harness.layoutApp()
            assertEquals("the header is offscreen", "18", detailValue("被采纳回复"))
            release.countDown()
            val author = DiscourseAvatars.user("baba", "Baba", "/$path", site.baseUrl)
            harness.await("the delayed avatar to land on disk") { DiscourseAvatars.isCached(author) }
            listView()?.scrollToPosition(0)
            harness.layoutApp()
            harness.await("the already bound header to draw the delayed photo", iterations = 200) {
                magentaPixels(harness.renderApp("profile-avatar-delayed")) > 0
            }
            // ImageReceiver advances its crossfade on draw, not just when the clock advances.
            repeat(15) {
                harness.idleClock(20)
                harness.drawApp()
            }
            harness.renderApp("profile-avatar-delayed-settled")
            harness.assertNoTelegramSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * The header and the list at the same time.
     *
     * <p>The failure this pins: the collapsing header is drawn over the list and hides the fields
     * the site sent, or the list stops binding its rows once the header's position starts moving.</p>
     */
    @Test
    fun theCollapsingHeaderKeepsTheProfileReadable() {
        site.serveFixture("u/baba.json", "user-profile.json")
        site.serveImage("user_avatar/linux.do/baba/96/1.png", magentaPng(96, 96))

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile to load") { detailValue("信任等级") == "3" }

        // Part of the way up: the header sits between its rest position and its collapsed one.
        val list = listView()!!
        list.scrollBy(0, AndroidUtilities.dp(100f))
        harness.layoutApp()
        harness.idle()
        assertEquals(
            "the fields stay readable while the header collapses",
            "3",
            detailValue("信任等级"),
        )
        harness.renderApp("profile-screen-mid")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * What the screen shows for [label], read out of upstream's own detail cell: the site's answer
     * is the line upstream draws large, the field's name is the line under it.
     */
    private fun detailValue(label: String): String? {
        val list = listView() ?: return null
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index)
            if (cell is TextDetailCell && cell.valueTextView.text.toString() == label) {
                return cell.textView.text.toString()
            }
        }
        return null
    }

    /** The author upstream's own tap hands over: the sender of the floor's message. */
    private fun authorOf(message: MessageObject): TLRPC.User {
        val peer = message.messageOwner.from_id
        val id = (peer as? TLRPC.TL_peerUser)?.user_id
            ?: throw AssertionError(
                "the floor's sender is not a user: " + (peer?.javaClass?.simpleName ?: "null"),
            )
        return MessagesController.getInstance(0).getUser(id)
            ?: throw AssertionError("the sender $id is not among the message's users")
    }

    private fun magentaPixels(file: File): Int {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ?: throw AssertionError("the render did not decode: $file")
        var magenta = 0
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 200 && Color.green(pixel) < 60 && Color.blue(pixel) > 200) {
                    magenta++
                }
            }
        }
        return magenta
    }

    private fun magentaPng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(255, 0, 255))
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 4002L
    }
}
