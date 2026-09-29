package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.DialogCell
import java.io.ByteArrayOutputStream
import java.io.File

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
        site.serveImage(site.avatarPath(avatarTemplate), greenPng())
        site.serveImage(site.avatarPath(listAvatarTemplate), greenPng())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    private fun topicJsonWithAvatar(): String {
        val base = org.json.JSONObject(harness.fixture("topic-detail.json"))
        val posts = base.getJSONObject("post_stream").getJSONArray("posts")
        for (index in 0 until posts.length()) {
            posts.getJSONObject(index).put("avatar_template", avatarTemplate)
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
        // The rows are rebuilt when an avatar arrives and the decode runs on a worker of its own,
        // so what is waited for is the drawn result — the only thing the reader can see.
        var green = 0
        var attempts = 0
        harness.await("the row to draw the downloaded avatar", iterations = 600) {
            green = greenPixels(harness.renderApp("post-avatar"))
            if (green == 0 && attempts++ == 200) {
                val cell = laidOutCells().firstOrNull()
                println(
                    "avatar: stuck — cached=" + DiscourseAvatars.isCached(author) +
                        " cell=" + (cell != null) +
                        " avatarReceiver=" + (cell?.avatarImage != null) +
                        " bitmap=" + (cell?.avatarImage?.bitmap != null) +
                        " drawable=" + cell?.avatarImage?.imageDrawable?.javaClass?.simpleName,
                )
            }
            green > 2_000
        }
        println("avatar: green pixels in the render = $green in $attempts attempts")
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
        repeat(40) {
            harness.idle()
            harness.idleUpstreamQueues()
        }

        val green = greenPixels(harness.renderApp("topic-list-avatars"))
        println("avatar: green pixels across the list = $green")
        assertTrue("a row drew the avatar it downloaded", green > 1_000)
        val rows = laidOutChildren().filterIsInstance<DialogCell>()
        assertTrue("the list drew its rows", rows.size >= 2)
        println("avatar: row requests = " + site.requestPaths.filter { it.startsWith("user_avatar/") })
        harness.assertNoTelegramSideEffects()
    }

    private fun greenPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(0, 255, 0))
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun greenPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.green(it) > 200 && Color.red(it) < 80 && Color.blue(it) < 80 }
    }

    private fun currentFragmentName(): String? {
        val shell = harness.activity as io.github.baba983.nativedo.app.NativeDoActivity
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        return (stackField.get(layout) as List<*>).lastOrNull()?.javaClass?.simpleName
    }

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutChildren(): List<android.view.View> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private fun listView(): RecyclerView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findRecycler(content)
    }

    private fun findRecycler(view: android.view.View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecycler(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val TOPIC_ID = 404L
    }
}
