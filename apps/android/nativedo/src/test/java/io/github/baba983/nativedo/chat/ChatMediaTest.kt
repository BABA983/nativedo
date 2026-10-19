package io.github.baba983.nativedo.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
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
import io.github.baba983.nativedo.topic.DiscourseImageBridge
import io.github.baba983.nativedo.topic.TopicFragment
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ArticleViewer
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.PhotoViewer
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Rich media inside a public chat channel: pictures, a clip, a linked file, ordinary links and a
 * onebox, and the received audio clip beside them.
 *
 * <p>The failure modes pinned here, all of them the screen that draws a chat bubble rather than the
 * topic that draws a post:</p>
 * <ul>
 *   <li>a picture in a chat message draws a placeholder that no tap can open, because the screen's
 *       cell delegate answers neither {@code canPerformActions} nor {@code openArticlePhoto}; the
 *       tap must reach upstream's viewer, on the whole message's media and at the picture pressed;</li>
 *   <li>a clip in a chat message never fetches its cover (opening the channel must fetch the cover,
 *       not the clip), and pressing play must fetch the clip once and hand the stored file to the
 *       viewer - a clip the site refuses must go to the device's player, never to a viewer sitting
 *       on a file that is not there;</li>
 *   <li>a fetch that finishes after the reader has left the channel must open nothing: no viewer,
 *       no device player, on whatever screen the reader is looking at now;</li>
 *   <li>an ordinary link in a chat message is inert: a linux.do topic and a profile must stay
 *       native, anything else must open in the host's own browser - while a received voice
 *       message's own audio link stays this screen's, so it plays instead of opening a browser;</li>
 *   <li>a file linked in a chat message is not fetched with the message, so its block can never
 *       open it.</li>
 * </ul>
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
internal class ChatMediaTest {

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

    /**
     * A message with two pictures: the site's own bytes reach the bubble, and a tap on the second
     * one - a real finger on the shipped cell, not the delegate called directly - opens upstream's
     * viewer on this message's whole group, at the picture pressed. The failure this pins: a picture
     * drawn as a placeholder nobody can press (the cell asks its delegate whether it may act at
     * all), a viewer with an empty adapter, and a group that names the wrong picture. Both blocks
     * name one upload - the site's own cook does that for a repeated picture - so the group is still
     * two blocks even though only one picture file is fetched.
     */
    @Test
    fun aChatMessagesPicturesOpenAsOneGroupInTheViewer() {
        site.serveImage(FIRST, solidPng(320, 180, Color.MAGENTA))
        val fragment = openChannel(
            messagesJson(
                chatMessageJson(
                    9001,
                    """
                        <p>两张图</p>
                        <p><img src="/$FIRST" width="320" height="180" alt="first">
                        <img src="/$FIRST" width="320" height="180" alt="second"></p>
                    """.trimIndent(),
                ),
            ),
        )
        harness.await("the picture to be fetched with the session") {
            site.cookieHeaderFor(FIRST) != null
        }
        val message = fragment.bubbleForTest(0) ?: throw AssertionError("the message drew no bubble")
        harness.await("both pictures to be drawn") {
            harness.idleUpstreamQueues()
            harness.layoutApp()
            val receivers = photoReceivers(message)
            receivers.size == 2 && receivers.all { it.bitmap != null }
        }
        harness.drawApp()
        harness.idleClock(200)
        harness.renderApp("chat-media-picture")

        val cell = cellOf(fragment, 9001)
        val media = mediaBlocks(cell)
        assertEquals("the message's two pictures are its media", 2, media.size)

        assertTrue("a tap on the picture is handled by the shipped cell", tap(cell, media[1]))
        val viewer = PhotoViewer.getInstance()
        harness.await("upstream's viewer to open") { viewer.isVisibleOrAnimating() }
        val adapter = field(viewer, "pageBlocksAdapter") as PhotoViewer.PageBlocksAdapter
        assertEquals("the viewer holds this message's pictures", 2, adapter.itemsCount)
        assertSame("and the pressed picture is the one it opens", media[1], adapter.get(1))
        assertEquals(
            "the viewer opens at the picture the reader pressed, not the first one",
            1,
            field(viewer, "currentIndex") as Int,
        )
        assertEquals(
            "a message with several pictures groups them under the message's own id",
            cell.messageObject!!.id,
            media[0].groupId,
        )
        assertEquals("and the whole group shares it", media[0].groupId, media[1].groupId)
        harness.idleClock(400)
        renderViewer("chat-media-viewer")
        viewer.closePhoto(true, false)
        harness.idleClock(400)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A clip in a chat message: the cover travels with the message, the clip's bytes do not; the
     * play press fetches the clip once, the block shows the transfer, and the stored file is handed
     * to upstream's viewer - never to the device's player. The failure this pins: a cover nobody
     * fetched (a grey block), a play press that does nothing, or a viewer opened on an empty file.
     */
    @Test
    fun aClipsCoverTravelsWithTheMessageAndThePlayPressFetchesTheClip() {
        site.serveImage(POSTER, solidPng(640, 360, Color.rgb(90, 40, 140)))
        site.serveImage(CLIP, ByteArray(2048) { 7 }, "video/mp4")
        val fragment = openChannel(
            messagesJson(chatMessageJson(9001, videoHtml(site.url(CLIP), site.url(POSTER)))),
        )
        harness.await("the cover to be fetched with the message") {
            site.recordedRequests.any { it.contains(POSTER) }
        }
        assertFalse(
            "opening the channel already spent the reader's data on the clip",
            site.recordedRequests.any { it.contains(CLIP) },
        )
        val cell = cellOf(fragment, 9001)
        val block = videoBlockOf(cell.messageObject!!)
        harness.await("the clip's cover to be drawn in the bubble") {
            harness.idleUpstreamQueues()
            harness.drawApp()
            val receiver = cell.messageObject!!.richLayout
                ?.findMediaImageReceiver(block, IntArray(2))
            receiver?.bitmap != null
        }
        harness.idleClock(300)
        harness.renderApp("chat-media-video-cover")
        assertFalse("the clip is not on disk before the press", DiscourseImageBridge.videoFileExists(block.video_id))

        assertTrue("the play press is this screen's to handle", cell.delegate.openArticlePhoto(cell, block))
        harness.await("the press to fetch the clip") {
            site.recordedRequests.any { it.contains(CLIP) }
        }
        harness.await("the clip to land on disk") {
            DiscourseImageBridge.videoFileExists(block.video_id)
        }
        val file = DiscourseImageBridge.videoFile(DiscourseImageBridge.videoOf(block.video_id)!!)
        assertEquals("the whole clip was fetched", 2048L, file.length())
        harness.await("upstream's viewer to take the stored file") {
            PhotoViewer.getInstance().isVisibleOrAnimating()
        }
        assertNull(
            "a stored clip is not also streamed by the device's player",
            shadowOf(harness.activity).nextStartedActivity,
        )
        harness.idleClock(400)
        renderViewer("chat-media-video-viewer")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site will not hand the clip over: the reader still gets to watch it, through the device's
     * own player at the site's address. The failure this pins: a viewer opened on a file that was
     * never stored, which draws a player sitting on nothing.
     */
    @Test
    fun aClipTheSiteRefusesGoesToTheDevicePlayer() {
        site.serveImage(POSTER, solidPng(640, 360, Color.rgb(60, 120, 90)))
        site.serveResponse(CLIP) {
            MockResponse().setResponseCode(403).setBody("""{"errors":["you shall not pass"]}""")
        }
        val fragment = openChannel(
            messagesJson(chatMessageJson(9001, videoHtml(site.url(CLIP), site.url(POSTER)))),
        )
        harness.await("the cover to be fetched with the message") {
            site.recordedRequests.any { it.contains(POSTER) }
        }
        harness.drawApp()

        val cell = cellOf(fragment, 9001)
        val block = videoBlockOf(cell.messageObject!!)
        assertTrue(cell.delegate.openArticlePhoto(cell, block))
        harness.await("the site to refuse the clip") {
            site.recordedRequests.any { it.contains(CLIP) }
        }
        harness.await("the device's player to be handed the clip") { startedActivity() != null }
        assertEquals(
            "a clip the site will not hand over is streamed from the site's own address",
            site.url(CLIP),
            startedActivity()?.data.toString(),
        )
        assertFalse(
            "and nothing was copied into the app's cache",
            DiscourseImageBridge.videoFileExists(block.video_id),
        )
        assertFalse(
            "and no viewer was opened on a file that is not there",
            PhotoViewer.getInstance().isVisibleOrAnimating(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A clip that is still on its way when the reader opens another channel. Its fetch finishing is
     * not the reader asking to watch it: nothing may open over the screen they moved to. The
     * failure this pins: a completion captured by a screen that is no longer on top opening the
     * viewer - or the device's player - behind the reader's back.
     */
    @Test
    fun aClipThatArrivesAfterTheReaderLeavesTheChannelOpensNothing() {
        site.serveImage(POSTER, solidPng(640, 360, Color.rgb(120, 90, 40)))
        site.serveThrottled(
            CLIP,
            ByteArray(1024 * 1024) { 3 },
            "video/mp4",
            bytesPerPeriod = 64 * 1024,
            periodMillis = 150,
        )
        val fragment = openChannel(
            messagesJson(chatMessageJson(9001, videoHtml(site.url(CLIP), site.url(POSTER)))),
        )
        harness.await("the cover to be fetched with the message") {
            site.recordedRequests.any { it.contains(POSTER) }
        }
        harness.drawApp()

        val cell = cellOf(fragment, 9001)
        val block = videoBlockOf(cell.messageObject!!)
        assertTrue(cell.delegate.openArticlePhoto(cell, block))
        harness.await("the site to be asked for the clip") {
            site.recordedRequests.any { it.contains(CLIP) }
        }

        // The reader leaves for another channel while the clip is still being fetched.
        site.serveJson("chat/api/channels/73.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/73/read.json", harness.fixture("chat-read.json"))
        site.serveJson(
            "chat/api/channels/73/messages.json",
            messagesJson(chatMessageJson(9100, "<p>别处</p>")),
        )
        val elsewhere = ChatChannelFragment.forChannel(73L, "另一个频道")
        harness.present(elsewhere)
        harness.await("the other channel") { elsewhere.messageCount() == 1 }
        assertSame("the reader is on another screen", elsewhere, harness.visibleFragment())

        harness.await("the clip to finish in the background", iterations = 12_000) {
            DiscourseImageBridge.videoFileExists(block.video_id)
        }
        harness.idleClock(400)
        assertFalse(
            "a fetch that finishes after the reader left does not open the viewer",
            PhotoViewer.getInstance().isVisibleOrAnimating(),
        )
        assertNull(
            "and does not hand the clip to the device's player",
            shadowOf(harness.activity).nextStartedActivity,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The links a chat message carries: the site's own topic and profile stay in the app, anything
     * else opens in the host's in-app browser - and none of them wakes tgnet. The failure this
     * pins: every link inert, because the cell never asks its delegate to act at all.
     */
    @Test
    fun linksTheSiteOwnsStayNativeAndOtherLinksOpenInTheHostsBrowser() {
        // The link names a floor, so the app's own screen reads that floor's window by number.
        site.serveFixture("t/711/3.json", "topic-detail.json")
        site.serveFixture("t/711.json", "topic-detail.json")
        site.serveFixture("u/alice.json", "user-profile.json")
        val fragment = openChannel(
            messagesJson(
                chatMessageJson(
                    9001,
                    """<p>看 <a href="/t/711/3">这个话题</a>、<a href="/u/alice">alice</a>""" +
                        """ 和 <a href="https://docs.example.com/start">文档</a></p>""",
                ),
            ),
        )
        harness.drawApp()
        val cell = cellOf(fragment, 9001)
        val attemptsBefore = ShadowConnectionsManager.REQUEST_ATTEMPTS

        cell.delegate.didPressUrl(cell, URLSpan(site.url("t/711/3")), false)
        harness.idle()
        harness.await("the site's topic to open natively") { harness.topFragment() is TopicFragment }
        harness.idleClock(600)
        harness.await("the app's own screen to read the link's floor") {
            site.requestPaths.contains("t/711/3.json")
        }
        harness.topFragment()!!.finishFragment()
        harness.idleClock(300)
        assertSame("the channel is back", fragment, harness.topFragment())

        cell.delegate.didPressUrl(cell, URLSpan(site.url("u/alice")), false)
        harness.idle()
        harness.await("the profile to open natively") {
            harness.topFragment() is io.github.baba983.nativedo.profile.ProfileFragment
        }
        harness.await("the app's own screen to read the profile") {
            site.requestPaths.contains("u/alice.json")
        }
        harness.topFragment()!!.finishFragment()
        harness.idleClock(300)
        assertSame("the channel is back again", fragment, harness.topFragment())

        strictActivityResolution()
        cell.delegate.didPressUrl(cell, URLSpan("https://docs.example.com/start"), false)
        harness.idle()
        val sheet = browserSheet()
        assertNotNull("a foreign link opens in the host's own in-app browser", sheet)
        assertNull(
            "and not in whatever app the device would hand it to",
            shadowOf(harness.activity).nextStartedActivity,
        )
        assertEquals(
            "no link made Telegram ask for a page",
            attemptsBefore,
            ShadowConnectionsManager.REQUEST_ATTEMPTS,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A received voice message's own link is the screen's to act on, as it was before ordinary links
     * were wired: it plays through the session-aware cache and opens no browser. The failure this
     * pins: new link handling swallowing the audio press, or the audio upload leaving for a web
     * page.
     */
    @Test
    fun aReceivedVoiceLinkStillPlaysInsteadOfOpeningABrowser() {
        val audioPath = "uploads/default/original/2X/9/voice-1.m4a"
        val audioUrl = site.url(audioPath)
        site.serveImage(audioPath, ByteArray(2048) { 0x22 }, "audio/mp4")
        val uploads = JSONArray().put(
            JSONObject()
                .put("id", 4211)
                .put("url", "/$audioPath")
                .put("original_filename", "voice-1.m4a")
                .put("extension", "m4a")
                .put("human_filesize", "12.3 KB")
                .put("short_url", "upload://9d5a.m4a"),
        )
        val fragment = openChannel(
            messagesJson(
                chatMessageJson(9001, """<p><audio controls src="/$audioPath"></audio></p>""", uploads),
            ),
        )
        harness.drawApp()
        val cell = cellOf(fragment, 9001)

        cell.delegate.didPressUrl(cell, URLSpan(audioUrl), false)
        harness.await("the clip to be fetched with the session") {
            site.requestPaths.contains(audioPath)
        }
        harness.await("the local file to play") { fragment.playingAudioUrlForTest() == audioUrl }
        assertSame("the channel is still the screen", fragment, harness.visibleFragment())
        assertNull("the site's own audio link never opens a browser", browserSheet())
        assertNull(
            "and never leaves for the device's browser",
            shadowOf(harness.activity).nextStartedActivity,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A file a chat message links to: fetched with the message through the session, and handed to
     * upstream's document block on the path the block opens. The failure this pins: the block, its
     * name and its size drawn over a file nothing ever fetched.
     */
    @Test
    fun aLinkedFileIsFetchedWithTheMessageAndItsBlockHoldsTheBytes() {
        val bytes = ByteArray(4096) { 32 }
        site.serveImage(ATTACHMENT, bytes, "application/pdf")
        val fragment = openChannel(
            messagesJson(
                chatMessageJson(
                    9001,
                    """<p><a class="attachment" href="/$ATTACHMENT">fixture-report.pdf</a>""" +
                        """ (${bytes.size} bytes)</p>""",
                ),
            ),
        )
        harness.await("the file to be fetched with the message") {
            site.cookieHeaderFor(ATTACHMENT) != null
        }
        harness.await("the document block to know the file is here") {
            val message = fragment.bubbleForTest(0) ?: return@await false
            message.messageOwner.rich_message?.documents?.any { it.size == bytes.size.toLong() } == true
        }
        val message = fragment.bubbleForTest(0)!!
        val document = message.messageOwner.rich_message!!.documents.single()
        assertEquals("with the name the message gave it", "fixture-report.pdf", document.file_name)
        val file = FileLoader.getInstance(0).getPathToAttach(document, false)
        assertTrue("on the path upstream's block opens", file.exists())
        assertEquals("the whole file", bytes.size.toLong(), file.length())
        assertTrue(
            "and the fetch carried the session",
            site.cookieHeaderFor(ATTACHMENT)!!.contains("_t="),
        )
        harness.drawApp()
        harness.idleClock(200)
        harness.renderApp("chat-media-attachment")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun chatMessageJson(id: Long, cooked: String, uploads: JSONArray? = null): String {
        val user = JSONObject()
            .put("id", 8)
            .put("username", "alice")
            .put("name", "Alice")
            .put("avatar_template", "/user_avatar/linux.do/alice/{size}/2.png")
        val message = JSONObject()
            .put("id", id)
            .put("message", "")
            .put("cooked", cooked)
            .put("created_at", "2026-10-02T09:00:00.000Z")
            .put("chat_channel_id", 71)
            .put("user", user)
        if (uploads != null) {
            message.put("uploads", uploads)
        }
        return message.toString()
    }

    private fun messagesJson(vararg messages: String): String {
        val array = JSONArray()
        messages.forEach { array.put(JSONObject(it)) }
        return JSONObject()
            .put("messages", array)
            .put("meta", JSONObject().put("can_load_more_past", false).put("can_load_more_future", false))
            .toString()
    }

    /** A channel message that carries one clip, the way the site's cook writes one. */
    private fun videoHtml(clipUrl: String, posterUrl: String): String = """
        <p>一个片段</p>
        <div class="video-container">
        <video width="640" height="360" preload="metadata" controls poster="$posterUrl">
        <source src="$clipUrl" type="video/mp4">
        <a href="$clipUrl">clip.mp4</a>
        </video>
        </div>
    """.trimIndent()

    private fun openChannel(messages: String): ChatChannelFragment {
        site.serveJson("chat/api/channels/71.json", harness.fixture("chat-channel.json"))
        site.serveJson("chat/api/channels/71/read.json", harness.fixture("chat-read.json"))
        site.serveJson("chat/api/channels/71/messages.json", messages)
        harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        // The feed is drawn before the channel is pushed, as on a device: the channel's rows are
        // then bound into a window that has already painted once.
        harness.drawApp()
        val fragment = ChatChannelFragment.forChannel(71L, "站点反馈")
        harness.present(fragment)
        harness.await("the messages") { fragment.messageCount() >= 1 }
        harness.layoutApp()
        return fragment
    }

    private fun cellOf(fragment: ChatChannelFragment, id: Long): ChatMessageCell {
        val list = fragment.listViewForTest()
        for (index in 0 until list.childCount) {
            val child = list.getChildAt(index)
            if (child is ChatMessageCell && child.messageObject?.id == id.toInt()) {
                return child
            }
        }
        throw AssertionError("no drawn cell for message $id")
    }

    private fun mediaBlocks(cell: ChatMessageCell): List<TL_iv.PageBlock> {
        val layout = cell.messageObject?.richLayout ?: throw AssertionError("the bubble has no rich layout")
        val media = ArrayList<TL_iv.PageBlock>()
        layout.collectMediaBlocks(media)
        return media
    }

    private fun videoBlockOf(message: MessageObject): TL_iv.pageBlockVideo =
        message.messageOwner.rich_message!!.blocks
            .filterIsInstance<TL_iv.pageBlockVideo>()
            .firstOrNull()
            ?: throw AssertionError(
                "the clip did not arrive as a video block: " +
                    message.messageOwner.rich_message!!.blocks.map { it.javaClass.simpleName },
            )

    /** The receivers of a message's pictures, in the order its body draws them. */
    private fun photoReceivers(message: MessageObject): List<ImageReceiver> {
        val layout = message.richLayout ?: return emptyList()
        val media = ArrayList<TL_iv.PageBlock>()
        layout.collectMediaBlocks(media)
        return media.mapNotNull { layout.findMediaImageReceiver(it, IntArray(2)) }
    }

    /**
     * A real finger on the shipped cell at a media block's own centre: down and up, the way the
     * list delivers them. A cell whose delegate refuses to act never reaches its media block.
     */
    private fun tap(cell: ChatMessageCell, block: TL_iv.PageBlock): Boolean {
        val layout = cell.messageObject?.richLayout ?: throw AssertionError("the bubble has no rich layout")
        harness.layoutApp()
        val drawn = layout.blocks
            .filterIsInstance<RichMessageLayout.RichMediaBlock>()
            .firstOrNull { it.getBlock() === block }
            ?: throw AssertionError("the layout holds no drawn block for the pressed media")
        val x = cell.textX + layout.minWidth / 2f
        val y = cell.textY + drawn.currY + drawn.height / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 20L, MotionEvent.ACTION_UP, x, y, 0)
        return try {
            cell.onTouchEvent(down) && cell.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
            harness.idle()
        }
    }

    /** The device's own player, read without consuming it. */
    private fun startedActivity() = shadowOf(harness.activity).peekNextStartedActivity()

    /** The host's in-app browser sheet, when one is open over the current screen. */
    private fun browserSheet(): ArticleViewer.Sheet? {
        val layout = harness.activity.javaClass
            .getDeclaredField("actionBarLayout").apply { isAccessible = true }
            .get(harness.activity) as ActionBarLayout
        return layout.getSheetFragment(false)?.getLastSheet() as? ArticleViewer.Sheet
    }

    /** Makes Robolectric's instrumentation throw for an intent nothing resolves, as a device does. */
    private fun strictActivityResolution() {
        shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).checkActivities(true)
    }

    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching { return type.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            type = type.superclass
        }
        throw NoSuchFieldException(name)
    }

    /** Upstream's viewer owns a separate window, so a capture of the page alone cannot show it. */
    private fun renderViewer(name: String): File {
        val window = field(PhotoViewer.getInstance(), "windowView") as View
        window.measure(
            View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.EXACTLY),
        )
        window.layout(0, 0, harness.width, harness.height)
        val bitmap = Bitmap.createBitmap(harness.width, harness.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            harness.activity.findViewById<View>(android.R.id.content).draw(Canvas(bitmap))
            window.draw(Canvas(bitmap))
            val out = File(artifactDir(), "$name.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("chat-media: wrote $out (${out.length()} bytes)")
            return out
        } finally {
            bitmap.recycle()
        }
    }

    private fun artifactDir(): File {
        var root = File(System.getProperty("user.dir")).absoluteFile
        while (!File(root, "fixtures/discourse").isDirectory) root = root.parentFile
        return File(root, ".amp/in/artifacts/nativedo").apply { mkdirs() }
    }

    private fun solidPng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private companion object {
        const val FIRST = "uploads/chat/first.png"
        const val POSTER = "uploads/chat/clip-poster.png"
        const val CLIP = "uploads/chat/clip.mp4"
        const val ATTACHMENT = "uploads/chat/fixture-report.pdf"
    }
}
