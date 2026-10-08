package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.PhotoViewer
import org.telegram.ui.Components.ClippingImageView
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A post image, end to end: the image is served by the fake site, fetched by the app's own client
 * and drawn by the stock upstream cell.
 *
 * <p>The failure this pins: upstream's media block draws a photo from its own file cache and knows
 * nothing about URLs, so a bridge that puts the bytes anywhere else — or that forgets to resolve a
 * site-relative source, or downloads without the session — leaves a placeholder where the reader
 * expects a picture. The assertion is on the drawn pixels, so only a real decode passes.</p>
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
internal class TopicImageTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        val preview = solidPng(320, 180, Color.MAGENTA)
        val original = solidPng(1600, 900, Color.CYAN)
        File(artifactDir(), "preview-fixture.png").writeBytes(preview)
        File(artifactDir(), "original-fixture.png").writeBytes(original)
        site.serveImage(IMAGE_PATH, preview)
        site.serveImage(ORIGINAL_PATH, original)
        site.serveCooked(TOPIC_ID, postWithImage())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aLightboxDownloadsTheOriginalWithTheSessionAndTheViewerActuallyUsesIt() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Image post"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.await("the image to be fetched") {
            site.cookieHeaderFor(IMAGE_PATH) != null || site.cookieHeaderFor(ORIGINAL_PATH) != null
        }
        val cookieHeader = site.cookieHeaderFor(ORIGINAL_PATH)
        assertNotNull("unwrapping the lightbox must not discard its original href", cookieHeader)
        for (name in listOf("_t=", "_forum_session=", "cf_clearance=")) {
            assertTrue("the original download carries $name", cookieHeader!!.contains(name))
        }

        harness.await("the stock cell to draw the downloaded image") {
            cyanPixels(retain(harness.renderApp("photo-original-cell"))) > 0
        }

        assertTrue("a tap on the image opens the viewer", openTheFirstImage())
        val viewer = PhotoViewer.getInstance()
        assertTrue("the viewer is upstream's", viewer.isVisible)
        val adapter = field(viewer, "pageBlocksAdapter") as PhotoViewer.PageBlocksAdapter
        val file = adapter.getFile(0)
        file.copyTo(File(artifactDir(), "viewer-downloaded-original.png"), overwrite = true)
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        assertEquals("not the 320px preview", 1600, decoded.width)
        assertEquals(900, decoded.height)
        assertEquals(Color.CYAN, decoded.getPixel(799, 449))
        val size = (adapter.getMedia(0) as org.telegram.tgnet.TLRPC.Photo).sizes.single()
        assertEquals("the post's display aspect is retained", 320, size.w)
        assertEquals(180, size.h)
        harness.await("the full-screen receiver to decode the original") {
            val receiver = field(viewer, "centerImage") as org.telegram.messenger.ImageReceiver
            receiver.bitmap?.let { Color.green(it.getPixel(it.width / 2, it.height / 2)) > 200 } == true
        }
        assertTrue("the actual viewer draws original pixels", cyanPixels(renderViewer("photo-original-viewer")) > 1000)
        println("photo: viewer file=${file.name}, decoded=${decoded.width}x${decoded.height}, cookies=$cookieHeader")
        harness.assertNoTelegramSideEffects()
    }

    /** Conflicting hints must not fetch a resize, and an ordinary linked picture is not a lightbox. */
    @Test
    fun downloadHintsAndUnsortedSrcsetChooseOriginalsWithoutPromotingInlineDecoration() {
        val chosen = listOf("uploads/download.png", "uploads/anchor-download.png", "uploads/wide.png", IMAGE_PATH)
        chosen.forEach { site.serveImage(it, solidPng(1600, 900, Color.CYAN)) }
        val html = """
            <a class="lightbox" href="uploads/wrong.png"><img src="$IMAGE_PATH" width="320" height="180"
                data-download-href="${chosen[0]}" srcset="uploads/wrong.png 2x"></a>
            <a class="lightbox" href="uploads/wrong.png" data-download-href="${chosen[1]}">
                <img src="$IMAGE_PATH" width="320" height="180"></a>
            <a class="lightbox"><img src="$IMAGE_PATH" width="320" height="180"
                srcset="${chosen[2]} 1600w, uploads/wrong.png 640w, $IMAGE_PATH 320w"></a>
            <p><a href="uploads/wrong.png"><img src="$IMAGE_PATH" width="320" height="180"></a></p>
            <p><img class="emoji" src="uploads/emoji.png" width="160" height="160">
                <img src="uploads/icon.png" width="24" height="24"></p>
        """.trimIndent()
        site.serveImage("uploads/emoji.png", solidPng(160, 160, Color.YELLOW))
        site.serveImage("uploads/icon.png", solidPng(24, 24, Color.YELLOW))
        site.serveCooked(TOPIC_ID, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Image hints"))
        harness.await("the post") { harness.drawApp(); laidOutCells().isNotEmpty() }
        val message = laidOutCells().first().messageObject!!
        val prepared = DiscourseImageBridge.prepare(html, site.url(""))
        assertEquals("download attribute, lightbox attribute, widest srcset, then ordinary src",
            chosen.map { site.url(it) }, prepared.images.map { it.url })
        assertEquals("emoji and small icons stay inline", 4, message.messageOwner.rich_message.photos.size)
        harness.await("all selected originals on disk") {
            prepared.images.all { image -> DiscourseImageBridge.localFiles(image).all { it.length() > 0 } }
        }
        assertNull("lower-priority or ordinary href was not fetched", site.cookieHeaderFor("uploads/wrong.png"))
        assertTrue(openTheFirstImage())
        val adapter = field(PhotoViewer.getInstance(), "pageBlocksAdapter") as PhotoViewer.PageBlocksAdapter
        assertEquals("only the three lightboxes, not inline links or decoration, enter the gallery", 3, adapter.itemsCount)
        for (index in 0 until adapter.itemsCount) {
            val bitmap = BitmapFactory.decodeFile(adapter.getFile(index).absolutePath)
            assertEquals(1600, bitmap.width)
            assertEquals(Color.CYAN, bitmap.getPixel(800, 450))
        }
        renderViewer("photo-hints-viewer")
        harness.assertNoTelegramSideEffects()
    }

    /** Missing source geometry fades from nowhere; a retained row index closes onto a recycled cell. */
    @Test
    fun openingAndClosingUseTheVisibleRichMediaSourceAndOffscreenClosingFallsBack() {
        site.serveCooked(TOPIC_ID, postWithImage() + (1..65).joinToString("") { "<p>下面的正文第 $it 段。</p>" })
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Image geometry"))
        harness.await("loaded source bitmap") {
            harness.drawApp()
            laidOutCells().firstOrNull()?.messageObject?.richLayout?.let { layout ->
                val blocks = ArrayList<org.telegram.tgnet.tl.TL_iv.PageBlock>()
                layout.collectMediaBlocks(blocks)
                blocks.firstOrNull()?.let { layout.findMediaImageReceiver(it, IntArray(2))?.bitmap != null }
            } == true
        }
        val cell = laidOutCells().first()
        val list = listView()!!
        val anchorTop = cell.top
        val message = cell.messageObject!!
        val blocks = ArrayList<org.telegram.tgnet.tl.TL_iv.PageBlock>()
        message.richLayout.collectMediaBlocks(blocks)
        val block = blocks.single()
        val offset = IntArray(2)
        val receiver = message.richLayout.findMediaImageReceiver(block, offset)
        val location = IntArray(2).also { cell.getLocationInWindow(it) }
        retain(harness.renderApp("photo-source-before"))
        assertTrue(cell.delegate.openArticlePhoto(cell, block))
        val viewer = PhotoViewer.getInstance()
        val source = field(viewer, "currentPlaceObject") as? PhotoViewer.PlaceProviderObject
        assertNotNull("opening needs the visible source, not an EmptyPhotoViewerProvider", source)
        assertSame(receiver, source!!.imageReceiver)
        assertSame(list, source.parentView)
        assertEquals(location[0] + cell.textX + offset[0], source.viewX)
        assertEquals(location[1] + cell.textY + offset[1], source.viewY)
        assertNotNull("upstream takes the source bitmap for the transition", source.thumb)
        assertEquals(list.paddingTop, source.clipTopAddition)
        assertEquals(list.paddingBottom, source.clipBottomAddition)
        println("photo: source=(${source.viewX},${source.viewY}), receiver=${receiver!!.drawRegion}, " +
            "clip=${source.clipTopAddition}/${source.clipBottomAddition}, anchorTop=$anchorTop")
        val provider = field(viewer, "placeProvider") as PhotoViewer.PhotoViewerProvider
        assertNull("invalid indices do not refer to the first image", provider.getPlaceForPhoto(null, null, -1, true, true))
        assertNull(provider.getPlaceForPhoto(null, null, 1, true, true))
        renderViewer("photo-open-start")
        val clipping = field(viewer, "animatingImageView") as ClippingImageView
        awaitFrame("opening") { clipping.animationProgress in 0.4f..0.6f }
        renderViewer("photo-open-mid")
        harness.idleClock(350)
        harness.idleUpstreamQueues()
        renderViewer("photo-open-end")
        viewer.closePhoto(true, false)
        awaitFrame("closing") { clipping.animationProgress in 0.4f..0.6f }
        renderViewer("photo-close-mid")
        harness.idleClock(400)
        assertFalse(viewer.isVisible)
        assertSame("opening and closing did not replace the row", cell, laidOutCells().first())
        assertEquals("opening and closing did not move the reading anchor", anchorTop, cell.top)
        retain(harness.renderApp("photo-source-after"))

        assertTrue(cell.delegate.openArticlePhoto(cell, block))
        harness.idleClock(350)
        list.scrollBy(0, harness.height * 2)
        harness.drawApp()
        assertSame("the long cell is still attached, but its image is above the viewport",
            message, laidOutCells().first().messageObject)
        val closingProvider = field(viewer, "placeProvider") as PhotoViewer.PhotoViewerProvider
        assertNull("an offscreen image must not be the close target even when its text cell is attached",
            closingProvider.getPlaceForPhoto(null, null, 0, true, true))
        viewer.closePhoto(true, false)
        val container = field(viewer, "containerView") as View
        awaitFrame("offscreen fade close") { container.alpha in 0.4f..0.6f }
        renderViewer("photo-close-offscreen-mid")
        harness.idleClock(400)
        assertFalse("safe close without a source", viewer.isVisible)
        retain(harness.renderApp("photo-offscreen-after"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * What upstream does with a tap on a rich media block: the block's own handler calls
     * `openArticlePhoto` on the cell's delegate with the block that was pressed. The gesture
     * itself is upstream's (`ChatMessageCell` translates the event into the layout's coordinates
     * and dispatches it), so this drives the same entry point instead of re-deriving layout
     * geometry, which is upstream's business.
     */
    private fun openTheFirstImage(): Boolean {
        val cell = laidOutCells().firstOrNull()
            ?: throw AssertionError("the topic drew no ChatMessageCell")
        val layout = cell.messageObject!!.richLayout
        val media = ArrayList<org.telegram.tgnet.tl.TL_iv.PageBlock>()
        layout.collectMediaBlocks(media)
        val block = media.firstOrNull()
            ?: throw AssertionError("the post body holds no media block")
        val opened = cell.delegate.openArticlePhoto(cell, block)
        harness.idle()
        println("image: openArticlePhoto -> $opened")
        return opened
    }

    /**
     * The source is site-relative, the way Discourse writes an upload inside a post, and the image
     * sits in a lightbox wrapper with a caption block next to it.
     */
    private fun postWithImage(): String = """
        <p>看这张图：</p>
        <p><div class="lightbox-wrapper"><a class="lightbox" href="${site.url(ORIGINAL_PATH)}"
            title="sample"><img src="$IMAGE_PATH" alt="sample" width="320" height="180"></a>
            <div class="meta">sample.png 12 KB</div></div></p>
        <p>下面是表格。</p>
    """.trimIndent()

    private fun solidPng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun cyanPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) < 100 && Color.blue(it) > 200 && Color.green(it) > 200 }
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun artifactDir(): File {
        var root = File(System.getProperty("user.dir")).absoluteFile
        while (!File(root, "fixtures/discourse").isDirectory) root = root.parentFile
        return File(root, ".amp/in/artifacts/topic-fidelity-wave/photo").apply { mkdirs() }
    }

    private fun retain(file: File): File = file.copyTo(File(artifactDir(), file.name), overwrite = true)

    private fun awaitFrame(name: String, condition: () -> Boolean) {
        // A stock glass frame clock re-posts forever; idleFor cannot drain it. Step bounded tasks.
        val window = field(PhotoViewer.getInstance(), "windowView") as View
        val frame = Bitmap.createBitmap(harness.width, harness.height, Bitmap.Config.ARGB_8888)
        repeat(1500) {
            harness.idle(1)
            // The drawable's first nonzero-alpha draw hides the source, as on a real window.
            window.draw(Canvas(frame))
            if (condition()) {
                println("photo: captured actual $name intermediate frame")
                frame.recycle()
                return
            }
        }
        frame.recycle()
        throw AssertionError("no actual $name intermediate frame")
    }

    // PhotoViewer owns a separate WindowManager window, so an activity-only render cannot see it.
    private fun renderViewer(name: String): File {
        val window = field(PhotoViewer.getInstance(), "windowView") as View
        window.measure(View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.EXACTLY))
        window.layout(0, 0, harness.width, harness.height)
        val bitmap = Bitmap.createBitmap(harness.width, harness.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        harness.activity.findViewById<View>(android.R.id.content).draw(Canvas(bitmap))
        window.draw(Canvas(bitmap))
        return File(artifactDir(), "$name.png").also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 202L
        const val IMAGE_PATH = "uploads/sample-image.png"
        const val ORIGINAL_PATH = "uploads/original/sample-image.png"
    }
}
