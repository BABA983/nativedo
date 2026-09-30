package io.github.baba983.nativedo.topic

import android.view.MotionEvent
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
import io.github.baba983.nativedo.hooks.NativeDoHooks
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.PhotoViewer
import org.telegram.ui.Components.RecyclerListView
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A post that carries a video the site hosts.
 *
 * <p>The failure this pins: a topic that carries a clip spends the reader's data on it the moment
 * the topic is opened, whether or not anyone watches it. What a page fetch may bring is the cover -
 * the picture the block draws; the clip is fetched by the press on play, while the block shows that
 * the fetch is running, and a clip this client will not fetch, or the site will not hand over, is
 * streamed by the device's own player instead of copied into the app's cache.</p>
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
internal class PostVideoTest {

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
    fun theCoverTravelsWithThePageAndTheClipIsFetchedByThePlayPress() {
        val clip = "uploads/clip.mp4"
        val poster = "uploads/clip-poster.png"
        site.serveImage(clip, ByteArray(2048) { 7 }, contentType = "video/mp4")
        site.serveImage(poster, onePixelPng())

        val cell = openTopic(videoPost(clip, poster))
        val message = cell.messageObject!!
        val block = videoBlockOf(message)
        val document = documentOf(message, block)

        harness.await("the cover to be fetched with the page") {
            site.recordedRequests.any { it.contains(poster) }
        }
        assertFalse(
            "opening the topic already fetched the clip itself",
            site.recordedRequests.any { it.contains(clip) },
        )
        assertFalse(
            "and left it out of the cache",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        harness.drawApp()
        harness.idleClock(400)
        harness.renderApp("post-video-cover")

        val reports = observeProgress(FileLoader.getAttachFileName(document))
        pressPlay(cell, block)

        harness.await("the play press to fetch the clip") {
            site.recordedRequests.any { it.contains(clip) }
        }
        harness.await("the clip to land on disk") {
            DiscourseImageBridge.videoFileExists(block.video_id)
        }
        val file = DiscourseImageBridge.videoFile(DiscourseImageBridge.videoOf(block.video_id)!!)
        assertEquals("the whole clip was fetched", 2048L, file.length())
        assertEquals(
            "and the block was told the transfer ended with the whole file",
            2048L to 2048L,
            reports.last(),
        )
        harness.await("the reader's own viewer to take the file") {
            PhotoViewer.getInstance().isVisibleOrAnimating()
        }
        assertNull(
            "the device's player was not asked to stream anything",
            shadowOf(harness.activity).nextStartedActivity,
        )
        harness.drawApp()
        harness.idleClock(400)
        settleIconAnimation()
        harness.renderApp("post-video")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A fetch that lasts long enough to look at it: the block has to show that the clip is being
     * fetched - and how far it has come - rather than a play button that seems to do nothing until
     * the file appears.
     */
    @Test
    fun aFetchInFlightDrawsTheBlocksOwnLoadingStateAndItsProgress() {
        val clip = "uploads/slow-clip.mp4"
        val poster = "uploads/slow-clip-poster.png"
        site.serveImage(poster, onePixelPng())
        // 1 MB in 64 KB steps, one every 500 ms: the reader's own tap is still being fetched while
        // the screen is looked at, and the transfer is far enough along to have reported.
        site.serveThrottled(clip, ByteArray(1024 * 1024) { 3 }, "video/mp4", bytesPerPeriod = 64 * 1024, periodMillis = 500)

        val cell = openTopic(videoPost(clip, poster))
        val message = cell.messageObject!!
        val block = videoBlockOf(message)
        val name = FileLoader.getAttachFileName(documentOf(message, block))
        harness.await("the cover to be fetched with the page") {
            site.recordedRequests.any { it.contains(poster) }
        }

        val reports = observeProgress(name)
        pressPlay(cell, block)
        // A reader may press the media again while the transfer runs: that is the same fetch, not
        // another one.
        pressPlay(cell, block)
        harness.await("the site to be asked for the clip") {
            site.recordedRequests.any { it.contains(clip) }
        }

        assertTrue(
            "the transfer reported itself before it finished",
            reports.any { (_, total) -> total == 0L },
        )
        harness.await("the block to be told how far the transfer has come") {
            reports.any { (loaded, total) -> loaded in 1 until total }
        }
        assertFalse(
            "the clip is still on its way",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        assertTrue(
            "the block was told the fetch is running, under the name it looks its file up by",
            NativeDoHooks.isFileLoading(name),
        )
        harness.drawApp()
        harness.idleClock(400)
        settleIconAnimation()
        assertFalse(
            "the clip is still on its way while the reader's screen is drawn",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        harness.renderApp("post-video-downloading")

        harness.await("the clip to land on disk", iterations = 12_000) {
            DiscourseImageBridge.videoFileExists(block.video_id)
        }
        assertFalse("the fetch is over", NativeDoHooks.isFileLoading(name))
        assertEquals(
            "however many times the block was pressed, the clip was fetched once",
            1,
            site.recordedRequests.count { it.contains(clip) },
        )
        harness.await("the reader's own viewer to take the file") {
            PhotoViewer.getInstance().isVisibleOrAnimating()
        }
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aClipOverTheCapGoesToTheDevicePlayer() {
        val clip = "uploads/huge-clip.mp4"
        site.serveResponse(clip) {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", (26L * 1024 * 1024).toString())
        }

        val cell = openTopic(videoPost(clip, poster = null))
        val block = videoBlockOf(cell.messageObject!!)
        pressPlay(cell, block)

        harness.await("the site to be asked for the clip") {
            site.recordedRequests.any { it.contains(clip) }
        }
        harness.await("the device's player to be handed the clip") { startedActivity() != null }
        assertEquals(
            "a clip this long is streamed from the site's own address",
            site.url(clip),
            startedActivity()?.data.toString(),
        )
        assertFalse(
            "and nothing was copied into the app's cache",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aClipTheSiteRefusesGoesToTheDevicePlayer() {
        val clip = "uploads/private-clip.mp4"
        site.serveResponse(clip) {
            MockResponse().setResponseCode(403).setBody("""{"errors":["you shall not pass"]}""")
        }

        val cell = openTopic(videoPost(clip, poster = null))
        val block = videoBlockOf(cell.messageObject!!)
        pressPlay(cell, block)

        harness.await("the site to refuse the clip") {
            site.recordedRequests.any { it.contains(clip) }
        }
        harness.await("the device's player to be handed the clip") { startedActivity() != null }
        assertEquals(
            "a clip the site will not hand over is streamed from the site's own address",
            site.url(clip),
            startedActivity()?.data.toString(),
        )
        assertFalse(
            "and nothing was copied into the app's cache",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The viewer is only ever handed a file this client holds: opening it on a clip that is not
     * there would show a player sitting on an empty file.
     */
    @Test
    fun aClipThisClientDidNotFetchIsPlayedElsewhereInsteadOfOpeningOnAnEmptyFile() {
        val clip = "uploads/never-arrives.mp4"
        harness.bootApp()
        site.serveCooked(TOPIC_ID, videoPost(clip, poster = null))
        harness.await("the topic list to load") { rowCount() == 2 }
        val fragment = TopicFragment.forTopic(TOPIC_ID, "A clip")
        harness.present(fragment)
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().first { it.messageObject != null }
        val block = videoBlockOf(cell.messageObject!!)

        val opened = ArticlePhotoViewer.open(fragment, cell.messageObject!!, block)
        val started = shadowOf(harness.activity).nextStartedActivity
        assertTrue("the call was handled", opened)
        assertEquals(
            "a clip with no file is streamed from the site's own address",
            site.url(clip),
            started?.data.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The screen the device's own player was handed, read without consuming it: the wait and the
     * assertion that follows both have to see it.
     */
    private fun startedActivity() = shadowOf(harness.activity).peekNextStartedActivity()

    /**
     * Draws the screen until upstream's icon transition has run its course.
     *
     * <p>Upstream advances that animation inside its own draw pass, over the clock, so a single
     * draw catches it mid-morph: the frame only settles when the view is drawn again on a later
     * clock reading.</p>
     */
    private fun settleIconAnimation(rounds: Int = 16) {
        repeat(rounds) {
            harness.idleClock(30, maxTasks = 60)
            harness.drawApp()
        }
    }

    /**
     * The reader's own cancel button, on a transfer that is still running: the request stops, the
     * half-written file goes, the block goes back to its cover with a play button - and a second
     * press fetches the clip again.
     */
    @Test
    fun theCancelButtonStopsTheFetchAndLeavesTheCoverBehind() {
        val clip = "uploads/cancelled-clip.mp4"
        val poster = "uploads/cancelled-clip-poster.png"
        site.serveImage(poster, onePixelPng())
        site.serveThrottled(clip, ByteArray(1024 * 1024) { 3 }, "video/mp4", bytesPerPeriod = 64 * 1024, periodMillis = 500)

        val cell = openTopic(videoPost(clip, poster))
        val message = cell.messageObject!!
        val block = videoBlockOf(message)
        val name = FileLoader.getAttachFileName(documentOf(message, block))
        harness.await("the cover to be fetched with the page") {
            site.recordedRequests.any { it.contains(poster) }
        }

        val reports = observeProgress(name)
        pressPlay(cell, block)
        harness.await("the block to be told how far the transfer has come") {
            reports.any { (loaded, total) -> loaded in 1 until total }
        }
        val file = DiscourseImageBridge.videoFile(DiscourseImageBridge.videoOf(block.video_id)!!)

        pressCancel(cell, block)
        harness.await("the transfer to stop") { !NativeDoHooks.isFileLoading(name) }
        val reportedAtCancel = reports.size
        assertFalse(
            "the clip is not on disk",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        assertFalse(
            "and the half-written transfer was deleted",
            java.io.File(file.absolutePath + ".part").exists(),
        )
        assertFalse("the fetch is over", NativeDoHooks.isFileLoading(name))

        // A second of real time with nothing fetched: a report still coming out of the stopped
        // transfer would land here and put the block's own state back into loading.
        repeat(1_000) { java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L) }
        harness.idle()
        assertEquals(
            "no progress was reported after the reader cancelled",
            reportedAtCancel,
            reports.size,
        )
        harness.drawApp()
        settleIconAnimation()
        harness.renderApp("post-video-cancelled")
        assertNull(
            "no player was handed the clip: the reader cancelled it",
            shadowOf(harness.activity).nextStartedActivity,
        )
        assertFalse(
            "and the viewer did not open on it",
            PhotoViewer.getInstance().isVisibleOrAnimating(),
        )

        // The reader changed their mind: the same press fetches it again, and this time lands.
        pressPlay(cell, block)
        harness.await("the second press to fetch the clip", iterations = 12_000) {
            DiscourseImageBridge.videoFileExists(block.video_id)
        }
        assertEquals(
            "the clip was fetched once per press",
            2,
            site.recordedRequests.count { it.contains(clip) },
        )
        assertTrue(
            "and the file the viewer holds is the whole clip",
            file.length() == 1024L * 1024,
        )
        harness.assertNoTelegramSideEffects()
    }

    /** The topic, loaded and laid out, with the first floor's cell ready to be pressed. */
    private fun openTopic(cookedHtml: String): ChatMessageCell {
        harness.bootApp()
        site.serveCooked(TOPIC_ID, cookedHtml)
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A clip"))
        harness.await("the topic to load") { rowCount() == 1 }
        return laidOutCells().first { it.messageObject != null }
    }

    /**
     * A press on the block's play button, handed over the way upstream's own media cell hands one
     * over: the cell's delegate, which is what the cell calls from its touch handling.
     */
    private fun pressPlay(cell: ChatMessageCell, block: TL_iv.PageBlock) {
        val delegate = cell.delegate ?: throw AssertionError("the cell has no delegate")
        delegate.openArticlePhoto(cell, block)
        harness.idle()
    }

    /**
     * A press on the block's cancel button while the transfer runs - the same button, and the same
     * press, that upstream hands to the block's own cancel branch: the button is the centre of the
     * media, and the layout dispatches the event to the block that holds it.
     */
    private fun pressCancel(cell: ChatMessageCell, block: TL_iv.PageBlock) {
        val layout = cell.messageObject!!.richLayout
        // The block's own rect is only known once the list has laid its rows out.
        harness.layoutApp()
        val media = layout.blocks
            .filterIsInstance<RichMessageLayout.RichVideoBlock>()
            .firstOrNull { it.block === block }
            ?: throw AssertionError("the layout holds no block for " + block.javaClass.simpleName)
        val x = layout.getMinWidth() / 2f
        val y = media.currY + media.getHeight() / 2f
        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
        assertTrue("the block took the press", layout.onTouchEvent(down))
        assertTrue("and the release", layout.onTouchEvent(up))
        down.recycle()
        up.recycle()
        harness.idle()
    }

    /**
     * Every progress report the site's own channel carries for [name]: this is the channel
     * upstream's media block listens on for its radial progress, so a report here is the block
     * being told about a transfer this client runs.
     */
    private fun observeProgress(name: String): List<Pair<Long, Long>> {
        val reports = CopyOnWriteArrayList<Pair<Long, Long>>()
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            if (id == NotificationCenter.fileLoadProgressChanged && args[0] == name) {
                reports += (args[1] as Long) to (args[2] as Long)
            }
        }
        NotificationCenter.getInstance(0).addObserver(observer, NotificationCenter.fileLoadProgressChanged)
        return reports
    }

    private fun videoBlockOf(message: MessageObject): TL_iv.pageBlockVideo =
        message.messageOwner.rich_message!!.blocks
            .filterIsInstance<TL_iv.pageBlockVideo>()
            .firstOrNull()
            ?: throw AssertionError(
                "the video did not arrive as a video block: " +
                    message.messageOwner.rich_message!!.blocks.map { it.javaClass.simpleName },
            )

    private fun documentOf(message: MessageObject, block: TL_iv.pageBlockVideo) =
        message.messageOwner.rich_message!!.documents.firstOrNull { it.id == block.video_id }
            ?: throw AssertionError("the block names a video the message does not carry")

    /** A floor whose only content is one video the site hosts, exactly as its cook writes one. */
    private fun videoPost(clip: String, poster: String?): String {
        val posterAttribute = poster?.let { " poster=\"${site.url(it)}\"" }.orEmpty()
        return """
            <p>A clip.</p>
            <div class="video-container">
            <video width="640" height="360" preload="metadata" controls$posterAttribute>
            <source src="${site.url(clip)}" type="video/mp4">
            <a href="${site.url(clip)}">clip.mp4</a>
            </video>
            </div>
        """.trimIndent()
    }

    /** A 1x1 PNG: enough for the loader to have a cover to draw. */
    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(200, 120, 60))
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
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
        const val TOPIC_ID = 4001L
    }
}
