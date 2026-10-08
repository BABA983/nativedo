package io.github.baba983.nativedo.topic

import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView

/**
 * What the reply flow actually draws on the host, as retained PNGs: a loaded and an unloaded reply
 * preview (different authors, so the header must name the right one), and the native reply strip in
 * its empty, drafted, multiline and cancelled states. The screenshots are written into the wave's
 * interactions artifacts and are the machine-checkable half of the device pass.
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
internal class TopicReplyFidelityTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @org.junit.Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @org.junit.After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun rendersLoadedAndUnloadedReplyPreviewsAndTheReplyStripStates() {
        val posts = (1..6).map { floor ->
            val carol = floor == 2 || floor == 3 || floor == 6
            post(
                id = 1000L + floor, floor = floor,
                username = if (carol) "carol" else "baba",
                name = if (carol) "Carol" else "Baba",
                cooked = "<p>post number $floor</p>",
                replyTo = when (floor) {
                    3 -> 1   // a loaded target by a different author
                    5 -> 90  // a target the window does not carry, by a third author
                    else -> null
                },
            )
        }
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, (1..6).map { 1000L + it }))
        site.serveJson("posts/by_number/$TOPIC_ID/90.json", post(
            id = 90, floor = 90, username = "dora", name = "Dora", cooked = "<p>the ninetieth floor</p>",
        ).toString())

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Replies"))
        harness.await("the topic to load") { rowCount() == 6 }
        harness.await("the unloaded target to be read") {
            site.requestPaths.contains("posts/by_number/$TOPIC_ID/90.json")
        }
        // Let the rebuilt floors bind, then draw: floor 3's header names Baba, floor 5's names Dora.
        harness.await("both previews") {
            harness.layoutApp()
            harness.idle()
            laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1003 }
                ?.messageObject?.replyMessageObject != null &&
                laidOutCells().firstOrNull { it.messageObject!!.messageOwner.id == 1005 }
                    ?.messageObject?.replyMessageObject != null
        }
        harness.layoutApp()
        harness.idleClock(200)
        val loaded = laidOutCells().first { it.messageObject!!.messageOwner.id == 1003 }
        val unloaded = laidOutCells().first { it.messageObject!!.messageOwner.id == 1005 }
        println("reply render: loaded header=${loaded.replyNameLayout?.text} unloaded header=${unloaded.replyNameLayout?.text}")
        renderScreen("reply-preview")

        // The reply strip: shown for a floor, then with a draft, then multiline, then cancelled.
        replyToFirstPost()
        harness.idleClock(400)
        harness.layoutApp()
        renderScreen("reply-strip-empty")

        composer().setFieldText("a short draft")
        harness.idle()
        harness.layoutApp()
        renderScreen("reply-strip-draft")

        composer().setFieldText(
            "line one\nline two, long enough that the field wraps it across more than one line while the strip sits above it",
        )
        harness.idle()
        harness.layoutApp()
        renderScreen("reply-strip-multiline")

        findCloseButton(composer())?.performClick()
        harness.idleClock(400)
        harness.layoutApp()
        renderScreen("reply-strip-cancelled")

        assertTrue("the strip is put away after cancel", !composer().isTopViewVisible())
        harness.assertNoTelegramSideEffects()
    }

    private fun renderScreen(name: String) {
        val contentView = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        repeat(2) {
            contentView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)
            harness.idle(80)
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            contentView.measuredWidth.coerceAtLeast(1),
            contentView.measuredHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        contentView.draw(android.graphics.Canvas(bitmap))
        val repo = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "fixtures/discourse").isDirectory }
        val out = java.io.File(repo, ".amp/in/artifacts/topic-fidelity-wave/interactions/$name.png")
        out.parentFile.mkdirs()
        java.io.FileOutputStream(out).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        println("reply render: $out (${out.length()} bytes)")
    }

    /** The post menu's 回复 on the first floor, exactly as the reader reaches it. */
    private fun replyToFirstPost() {
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        clickMenuItem(latestItemOptionsMenu(harness.activity), "回复")
        harness.idle()
    }

    private fun post(
        id: Long, floor: Int, username: String, name: String, cooked: String, replyTo: Int? = null,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("post_number", floor)
        put("username", username)
        put("name", name)
        put("created_at", "2024-01-0${(floor % 9) + 1}T10:00:00.000Z")
        put("cooked", cooked)
        put("avatar_template", "")
        if (replyTo != null) put("reply_to_post_number", replyTo)
    }

    private fun topicJson(posts: List<JSONObject>, streamIds: List<Long>): String {
        val base = JSONObject(harness.fixture("topic-detail.json"))
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", JSONArray(posts))
        stream.put("stream", JSONArray(streamIds))
        return base.toString()
    }

    private fun composer(): ChatActivityEnterView {
        val view = harness.visibleFragment()?.fragmentView ?: error("no fragment view")
        return viewsUnder(view).filterIsInstance<ChatActivityEnterView>().lastOrNull()
            ?: error("the screen has no composer")
    }

    private fun findCloseButton(view: View): View? {
        if (view.contentDescription == "取消回复") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findCloseButton(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun viewsUnder(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return found
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 713L
    }
}
