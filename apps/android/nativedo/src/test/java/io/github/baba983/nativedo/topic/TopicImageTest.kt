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
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.tl.TL_iv
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

    /** Topic 2998428 puts a lightboxed image inside a heading, not in a separate paragraph. */
    @Test
    fun aLightboxInsideAHeadingStillDrawsAndOpensItsOriginal() {
        site.serveCooked(TOPIC_ID, """
            <p>Reset starts when the account is used.</p>
            <h2><a class="anchor" href="#account"></a><strong>Account one:</strong><br>
            <div class="lightbox-wrapper"><a class="lightbox" href="${site.url(ORIGINAL_PATH)}">
            <img src="$IMAGE_PATH" width="320" height="180" alt="account quota">
            <div class="meta">image 1600×900</div></a></div></h2>
            <p>The text after the image remains readable.</p>
        """.trimIndent())
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Heading image"))
        harness.await("the post") { rowCount() == 1 }
        harness.await("the original image to arrive") { site.cookieHeaderFor(ORIGINAL_PATH) != null }
        harness.await("the heading's image to be drawn", iterations = 100) {
            cyanPixels(retain(harness.renderApp("photo-heading-cell"))) > 1000
        }
        assertTrue("the heading's image opens its original in the viewer", openTheFirstImage())
        val adapter = field(PhotoViewer.getInstance(), "pageBlocksAdapter") as PhotoViewer.PageBlocksAdapter
        val decoded = BitmapFactory.decodeFile(adapter.getFile(0).absolutePath)
        assertEquals(1600, decoded.width)
        assertEquals(Color.CYAN, decoded.getPixel(799, 449))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A generic wrapper and a heading are text containers upstream's importer flattens, so a media
     * block inside them used to lose both its text and its picture. The wrapper must keep the
     * heading, the media block must keep its place between the two heading halves, and the photo
     * must still be drawn and open its original.
     */
    @Test
    fun aLightboxInsideAHeadingInsideAGenericWrapperKeepsTheHeadingOrderAndOriginal() {
        site.serveCooked(TOPIC_ID, """
            <p>Lead paragraph.</p>
            <div class="post-body"><h2>Alpha<br>
            <div class="lightbox-wrapper"><a class="lightbox" href="${site.url(ORIGINAL_PATH)}">
            <img src="$IMAGE_PATH" width="320" height="180" alt="quota"><div class="meta">image 1600×900</div></a></div>
            Omega</h2></div>
            <p>Tail paragraph.</p>
        """.trimIndent())
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Wrapped heading image"))
        harness.await("the post") { rowCount() == 1 }
        harness.await("the original image to arrive") { site.cookieHeaderFor(ORIGINAL_PATH) != null }
        val message = laidOutCells().first().messageObject
        val blocks = message.messageOwner.rich_message.blocks
        println("photo: wrapped-heading blocks = " + blocks.map { it.javaClass.simpleName })
        assertEquals("a generic wrapper must not flatten its children", 5, blocks.size)
        assertEquals("Lead paragraph.", RichMessageLayout.getString(blocks[0].text))
        val headings = blocks.filterIsInstance<TL_iv.pageBlockHeading2>()
        assertEquals("the heading on either side of the media must survive as a heading",
            listOf("Alpha", "Omega"), headings.map { RichMessageLayout.getString(it.text) })
        assertTrue("the media must keep its place as a block between the two heading halves",
            blocks[2] is TL_iv.pageBlockPhoto)
        assertEquals("Tail paragraph.", RichMessageLayout.getString(blocks[4].text))
        harness.await("the wrapped heading's image to be drawn", iterations = 100) {
            cyanPixels(retain(harness.renderApp("photo-wrapped-heading-cell"))) > 1000
        }
        assertTrue("the wrapped heading's image opens its original", openTheFirstImage())
        val adapter = field(PhotoViewer.getInstance(), "pageBlocksAdapter") as PhotoViewer.PageBlocksAdapter
        val decoded = BitmapFactory.decodeFile(adapter.getFile(0).absolutePath)
        assertEquals(1600, decoded.width)
        assertEquals(Color.CYAN, decoded.getPixel(799, 449))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A full-size picture, a clip and a linked document written straight inside a heading or a
     * paragraph (no lightbox wrapper) are content, not decoration, so each must survive as its own
     * block between the text that surrounded it. Inline emoji and an avatar stay in the text.
     */
    @Test
    fun bareContentImageVideoAndAttachmentSurviveInsideHeadingsAndParagraphs() {
        val headingImage = "uploads/heading-photo.png"
        val clip = "uploads/clip.mp4"
        val guide = "uploads/guide.pdf"
        site.serveImage(headingImage, solidPng(400, 200, Color.MAGENTA))
        site.serveImage(guide, ByteArray(4096) { 32 }, contentType = "application/pdf")
        site.serveCooked(TOPIC_ID, """
            <h2>Halpha <img src="$headingImage" width="400" height="200"> Homega</h2>
            <p>Palpha <video src="$clip" width="640" height="360"></video> Pomega</p>
            <h2>Filebefore <a class="attachment" href="$guide">guide.pdf</a> Fileafter</h2>
            <p>Deco <img class="emoji" alt=":smile:" width="160" height="160" src="uploads/decor-emoji.png"> stays inline</p>
            <p><img class="avatar" width="320" height="320" src="uploads/decor-avatar.png"></p>
        """.trimIndent())
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Bare media"))
        harness.await("the post") { rowCount() == 1 }
        harness.await("the heading image to be fetched") { site.cookieHeaderFor(headingImage) != null }
        val message = laidOutCells().first().messageObject
        val blocks = message.messageOwner.rich_message.blocks
        println("photo: bare-media blocks = " + blocks.map { it.javaClass.simpleName })
        val shape = blocks.map { block ->
            when (block) {
                is TL_iv.pageBlockPhoto -> "<photo>"
                is TL_iv.pageBlockVideo -> "<video>"
                is TL_iv.pageBlockDocument -> "<document>"
                else -> RichMessageLayout.getString(block.text)
            }
        }
        assertEquals(
            "media in a heading or paragraph must survive as its own block between the surrounding text",
            listOf(
                "Halpha", "<photo>", "Homega",
                "Palpha", "<video>", "Pomega",
                "Filebefore", "<document>", "Fileafter",
            ),
            shape.take(9),
        )
        assertEquals("only the one content picture is a photo block; emoji and avatar stay inline", 1,
            blocks.count { it is TL_iv.pageBlockPhoto })
        assertTrue("an inline emoji keeps its readable label",
            message.richLayout.joinedText.contains(":smile:"))
        val video = blocks.filterIsInstance<TL_iv.pageBlockVideo>().single()
        assertEquals("the clip block names the post's own source",
            site.url(clip), DiscourseImageBridge.videoOf(video.video_id)?.url)
        val document = blocks.filterIsInstance<TL_iv.pageBlockDocument>().single()
        assertTrue("the linked file block is carried by the message's documents",
            message.messageOwner.rich_message.documents.any { it.id == document.document_id })
        harness.await("the bare heading image to be drawn", iterations = 100) {
            magentaPixels(retain(harness.renderApp("photo-bare-media-cell"))) > 1000
        }
        harness.assertNoTelegramSideEffects()
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

    /**
     * Download hints and an unsorted srcset pick the original, an ordinary anchor's href is
     * navigation and not the image's original, decorative inline pictures stay in the text, and a
     * full-size content image wrapped in an ordinary link is still drawn from its own src.
     */
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
        assertNull("the ordinary anchor href was never fetched as an original", site.cookieHeaderFor("uploads/wrong.png"))
        val navigation = message.messageOwner.rich_message.blocks.flatMap { block ->
            val text = block.text?.let { org.telegram.ui.iv.RichTextStyle.toSpannable(it) }
            if (text is android.text.Spanned) text.getSpans(0, text.length,
                org.telegram.ui.Components.URLSpanReplacement::class.java).map { it.url } else emptyList()
        }
        assertTrue("drawing a linked image must also preserve its navigation target: $navigation",
            navigation.contains(site.url("uploads/wrong.png")))
        assertTrue(openTheFirstImage())
        val adapter = field(PhotoViewer.getInstance(), "pageBlocksAdapter") as PhotoViewer.PageBlocksAdapter
        assertEquals("the three lightboxes and the ordinary content image all enter the gallery",
            4, adapter.itemsCount)
        for (index in 0 until adapter.itemsCount) {
            val bitmap = BitmapFactory.decodeFile(adapter.getFile(index).absolutePath)
            assertEquals(1600, bitmap.width)
            assertEquals(Color.CYAN, bitmap.getPixel(800, 450))
        }
        assertEquals("the ordinary content image is drawn from its own src, not its anchor href",
            prepared.images[3].id,
            (adapter.getMedia(3) as org.telegram.tgnet.TLRPC.Photo).id)
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

    private fun magentaPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) > 200 && Color.blue(it) > 200 && Color.green(it) < 100 }
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
