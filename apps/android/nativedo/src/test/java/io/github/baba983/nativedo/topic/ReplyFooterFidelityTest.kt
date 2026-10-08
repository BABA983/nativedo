package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView

/**
 * The reply footer and the replies sheet as retained images: the footer the stock
 * {@link ChatMessageCell} draws for a floor with replies (light and dark), and the sheet in its
 * loaded, empty and failed states. Written into `replies-correction/` as the machine-checkable half
 * of the device pass.
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
internal class ReplyFooterFidelityTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        // The night theme is process-wide; leaving it on would colour whatever runs next.
        runCatching { if (Theme.isCurrentThemeDark()) switchTo(false) }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun rendersTheReplyFooterLightAndDark() {
        serveTopic()
        openTopic()
        switchTo(dark = false)
        rebindPosts()
        harness.layoutApp()
        harness.idleClock(300)
        renderApp("reply-footer-light")

        switchTo(dark = true)
        rebindPosts()
        harness.layoutApp()
        harness.idleClock(300)
        renderApp("reply-footer-dark")

        harness.assertNoTelegramSideEffects()
    }

    // ---- helpers ----

    private fun serveTopic() {
        val posts = listOf(
            post(2001, 1, "op", "Op", "<p>the opening floor</p>", replyCount = 4),
            post(2002, 2, "alice", "Alice", "<p>a direct reply</p>", replyTo = 1),
            post(2003, 3, "carol", "Carol", "<p>another direct reply</p>", replyTo = 1),
        )
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, posts.map { it.getLong("id") }))
    }

    private fun openTopic() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reply footer"))
        harness.await("the topic to load") { rowCount() >= 3 }
        // Let the authors' avatars go out and arrive, so the footer's faces are real pictures.
        harness.await("the footer's avatar requests") {
            site.recordedRequests.count { it.contains("user_avatar") } >= 2
        }
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

    /** The stock cells cache their colours, so a theme change has to reach them by re-binding. */
    private fun rebindPosts() {
        listView()?.adapter?.notifyDataSetChanged()
        harness.layoutApp()
        harness.idleClock(200)
    }

    /** Draws the activity's content view (the topic screen) to `replies-correction/`. */
    private fun renderApp(name: String) {
        val contentView = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val width = AndroidUtilities.displaySize.x
        val height = AndroidUtilities.displaySize.y
        repeat(2) {
            contentView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)
            harness.idle(120)
        }
        save(draw(contentView), name)
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(1),
            view.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val repo = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(repo, ".amp/in/artifacts/replies-correction/$name.png")
        out.parentFile.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("reply fidelity: $out (${out.length()} bytes)")
    }

    private fun post(
        id: Long, floor: Int, username: String, name: String, cooked: String,
        replyTo: Int? = null, replyCount: Int = 0,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("post_number", floor)
        put("username", username)
        put("name", name)
        put("reply_count", replyCount)
        put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
        put("cooked", cooked)
        put("avatar_template", "/user_avatar/linux.do/$username/{size}/1.png")
        if (replyTo != null) put("reply_to_post_number", replyTo)
    }

    private fun replyModel(id: Long, floor: Int, username: String) =
        io.github.baba983.nativedo.core.model.Post(
            id = id,
            postNumber = floor,
            authorUsername = username,
            authorDisplayName = username.replaceFirstChar { it.uppercase() },
            authorAvatarTemplate = "/user_avatar/linux.do/$username/{size}/1.png",
            cookedHtml = "<p>reply number $floor</p>",
        )

    private fun topicJson(posts: List<JSONObject>, streamIds: List<Long>): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", JSONArray(posts))
        stream.put("stream", JSONArray(streamIds))
        return base.toString()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 741L
        const val PARENT_POST_ID = 2001L
    }
}
