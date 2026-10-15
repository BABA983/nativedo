package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
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
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * What the reply flow actually draws on the host, as retained PNGs: a loaded and an unloaded reply
 * preview (different authors, so the header must name the right one), and the native reply strip in
 * its empty, drafted, multiline and cancelled states. The screenshots are written into the wave's
 * interactions artifacts and are the machine-checkable half of the device pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    /**
     * A reply whose target the loaded window did not carry is read from the site after the floor is
     * already drawn. When it lands, the floor's own text has not changed: only the reply preview is
     * added to the row. The failure this pins: the floor is rebuilt by removing its rows and
     * inserting fresh ones, which the list animates one after the other - the drawn post fades out,
     * its row goes briefly blank, and a new cell fades in, so the message area blinks as a preview
     * arrives. The oracle is the drawn screen: the floor may never be drawn twice at once, and its
     * body must still be drawn on the same row once the preview is.
     */
    @Test
    fun aReplyTargetThatLandsLateIsDrawnIntoTheFloorWithoutReplacingItsRow() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val posts = listOf(
            post(id = 1001, floor = 1, username = "baba", name = "Baba", cooked = "<p>floor one body</p>"),
            post(
                id = 1003, floor = 3, username = "baba", name = "Baba",
                cooked = "<p>floor three body quote paragraph</p>" +
                    "<details><summary>More</summary><p>Expanded before the target arrives</p></details>",
                replyTo = 90,
            ),
        )
        site.serveJson("t/$TOPIC_ID.json", topicJson(posts, posts.map { it.getLong("id") }))
        site.serveResponse("posts/by_number/$TOPIC_ID/90.json") {
            entered.countDown()
            release.await(60, TimeUnit.SECONDS)
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                post(id = 90, floor = 90, username = "dora", name = "Dora", cooked = "<p>the ninetieth floor</p>")
                    .toString(),
            )
        }
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Replies"))
        harness.await("the two floors") { rowCount() == 2 && laidOutCells().isNotEmpty() }
        harness.drawApp()
        harness.await("the unloaded target to be read") { entered.count == 0L }

        val before = laidOutCells().first { it.messageObject?.id == 1003 }
        val top = before.top
        assertTrue(
            "the floor's body must be drawn before its target lands",
            before.messageObject!!.richLayout.joinedText.contains("floor three body quote"),
        )
        val header = before.messageObject!!.richLayout.blocks
            .filterIsInstance<org.telegram.messenger.RichMessageLayout.RichDetailsBlock>().first()
        val x = before.textX + header.padding.left + 80f
        val y = before.textY + header.currY + header.height / 2f
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
            assertTrue("the production cell must handle the details tap", before.onTouchEvent(event))
            event.recycle()
        }
        harness.idleClock(600)
        harness.drawApp()
        assertTrue("details must expand before the target arrives",
            before.messageObject!!.richLayout.joinedText.contains("Expanded before the target arrives"))

        release.countDown()
        repeat(80) { step ->
            harness.idle(1)
            layoutContent()
            java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
            val copies = laidOutCells().count { it.messageObject?.id == 1003 }
            assertTrue(
                "the floor must not be duplicated as its reply target lands (frame $step): $copies",
                copies <= 1,
            )
        }
        harness.await("the reply preview to be drawn") {
            harness.layoutApp()
            harness.idle()
            laidOutCells().firstOrNull { it.messageObject?.id == 1003 }
                ?.messageObject?.replyMessageObject != null
        }
        harness.drawApp()

        val after = laidOutCells().first { it.messageObject?.id == 1003 }
        assertSame("the target's preview must be drawn into the floor's own row", before, after)
        assertEquals("the floor must not move as its target lands", top, after.top)
        assertTrue(
            "the floor's body must still be drawn once the preview is",
            after.messageObject!!.richLayout.joinedText.contains("floor three body quote"),
        )
        assertTrue(
            "the target's preview must be on the row",
            after.messageObject!!.replyMessageObject != null,
        )
        assertTrue("a late reply preview must not collapse the reader's expanded details",
            after.messageObject!!.richLayout.joinedText.contains("Expanded before the target arrives"))
        assertTrue(
            "the floor's own body must still be legible on the drawn row",
            darkPixelsIn(after) > 0,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** Lays the activity's content out without draining the looper, so a frame can be sampled. */
    private fun layoutContent() {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.measure(
            View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.AT_MOST),
        )
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
    }

    /** The body text's own dark pixels inside [cell]'s drawn bounds, read from a real render. */
    private fun darkPixelsIn(cell: ChatMessageCell): Int {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return 0
        val bitmap = Bitmap.createBitmap(
            content.measuredWidth.coerceAtLeast(1),
            content.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        content.draw(Canvas(bitmap))
        val location = IntArray(2).also { cell.getLocationInWindow(it) }
        val left = location[0].coerceAtLeast(0)
        val right = (location[0] + cell.width).coerceAtMost(bitmap.width)
        val top = location[1].coerceAtLeast(0)
        val bottom = (location[1] + cell.height).coerceAtMost(bitmap.height)
        var dark = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) < 90 && Color.green(pixel) < 90 && Color.blue(pixel) < 90) dark++
            }
        }
        println("reply flicker: dark body pixels in the floor's row = $dark")
        return dark
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
        if (view.contentDescription == "取消回复" || view.contentDescription == "取消编辑") return view
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
