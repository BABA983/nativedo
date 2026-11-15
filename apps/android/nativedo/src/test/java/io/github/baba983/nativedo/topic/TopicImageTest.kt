package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import io.github.baba983.nativedo.hooks.NativeDoHooks
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
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
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.PhotoViewer
import org.telegram.ui.Components.ClippingImageView
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val photo = adapter.getMedia(0) as org.telegram.tgnet.TLRPC.Photo
        val size = org.telegram.messenger.FileLoader.getClosestPhotoSizeWithSize(
            photo.sizes, org.telegram.messenger.AndroidUtilities.getPhotoSize())
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
     * A fetch that lands after its row is already drawn must re-bind that row. Every one of the
     * screen's late fetches (an inline picture, an attachment, a clip's cover, a reaction's emoji)
     * ends by re-binding the row it belongs to, and the row is on screen with its text already
     * drawn. The failure this pins: the re-bind is answered by the list's own change animation,
     * which drops the drawn {@code ChatMessageCell} and fades a freshly built one in, so the post
     * blinks as the fetch lands. The oracle is the drawn row: it must be the row that was already
     * there, at the same pixel, with its body still on it.
     */
    @Test
    fun aFetchLandingAfterTheRowIsDrawnRebindsThatRowInsteadOfReplacingIt() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val path = "uploads/late-${System.nanoTime()}.png"
        try {
            site.serveResponse(path) {
                entered.countDown()
                release.await(60, TimeUnit.SECONDS)
                MockResponse().setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(solidPng(400, 200, Color.MAGENTA)))
            }
            site.serveCooked(
                TOPIC_ID,
                "<p>正文引用 example quote paragraph</p>" +
                    "<p><img src='${site.url(path)}' width='400' height='200'></p>",
            )
            harness.bootApp()
            harness.await("home") { rowCount() == 2 }
            harness.present(TopicFragment.forTopic(TOPIC_ID, "Late picture"))
            harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
            harness.drawApp()
            harness.await("the picture request to be in flight") { entered.count == 0L }

            val before = laidOutCells().first()
            val postId = before.messageObject!!.id
            val top = before.top
            assertTrue(
                "the body must be drawn before the fetch lands",
                before.messageObject!!.richLayout.joinedText.contains("example quote"),
            )

            release.countDown()
            // Give the download (on its own thread) real time to finish, and drain the looper so
            // the re-bind it triggers is applied.
            repeat(400) {
                harness.idle(32)
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
            }
            harness.idleUpstreamQueues()
            harness.drawApp()

            val after = laidOutCells().first { it.messageObject?.id == postId }
            assertSame(
                "the fetch must re-bind the row that was already there, not replace it",
                before, after,
            )
            assertEquals("the row must not move as the fetch lands", top, after.top)
            assertTrue(
                "the body text must still be drawn after the fetch",
                after.messageObject!!.richLayout.joinedText.contains("example quote"),
            )
            assertTrue(
                "the body must still be legible on the drawn row",
                darkPixelsIn(after) > 0,
            )
            harness.assertNoTelegramSideEffects()
        } finally {
            release.countDown()
        }
    }

    /**
     * A picture the site refuses: the block must stop its spinner and go back to its own download
     * button, and that button must be a real retry - a press fetches the picture again, and once the
     * site answers it, the picture is drawn. The spinner this pins: the block's loading state has to
     * follow this client's own fetch, not whether the picture looks like one upstream would
     * auto-fetch, or a failed fetch would leave the block turning with no way back.
     */
    @Test
    fun aRefusedPictureReturnsToItsButtonAndTheButtonFetchesItAgain() {
        val path = "uploads/refused-${System.nanoTime()}.png"
        val html = "<p>看这张图：</p><p><img src='${site.url(path)}' width='400' height='200'></p>"
        val image = DiscourseImageBridge.prepare(html, site.url("")).images.single()
        val name = DiscourseImageBridge.photoFileName(image)
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        site.serveResponse(path) {
            if (fail.get()) {
                MockResponse().setResponseCode(500).setBody("boom")
            } else {
                MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(solidPng(400, 200, Color.MAGENTA)))
            }
        }
        site.serveCooked(TOPIC_ID, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Refused image"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        val cell = laidOutCells().first()

        // The fetched picture is refused: the block has to fall back to its own download button
        // rather than keep turning. The button is read from a real draw, so a block left loading
        // fails here.
        harness.await("the block to fall back to its own download button") {
            harness.drawApp()
            !NativeDoHooks.isFileLoading(name) && buttonStateOf(photoBlockOf(cell)) == 0
        }
        retain(harness.renderApp("photo-refused-button"))
        val refused = site.recordedRequests.count { it.contains(path) }
        assertTrue("the site refused the picture at least once", refused >= 1)

        // A real press on that button starts a new fetch - the button itself is the retry. The same
        // press upstream hands the block's download branch, which is what has to reach our fetch.
        pressPhotoButton(cell)
        harness.await("the press to fetch it again") {
            site.recordedRequests.count { it.contains(path) } > refused
        }
        harness.await("the press's own refusal to return the button") {
            harness.drawApp()
            !NativeDoHooks.isFileLoading(name) && buttonStateOf(photoBlockOf(cell)) == 0
        }
        assertFalse("a Discourse retry must not queue a Telegram datacenter download",
            org.telegram.messenger.FileLoader.getInstance(0).isLoadingFile(name))

        site.serveResponse(path) {
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html")
                .setBody("<html>Verification required, not an image</html>")
        }
        val beforeInvalid = site.recordedRequests.count { it.contains(path) }
        pressPhotoButton(cell)
        harness.await("the successful HTTP response to be rejected as a non-image") {
            harness.drawApp()
            site.recordedRequests.count { it.contains(path) } > beforeInvalid &&
                !NativeDoHooks.isFileLoading(name) && buttonStateOf(photoBlockOf(cell)) == 0
        }
        assertTrue("an HTML response must not become a permanently cached broken photo",
            DiscourseImageBridge.localFiles(image).all { !it.exists() })

        fail.set(false)
        site.serveResponse(path) {
            MockResponse().setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(solidPng(400, 200, Color.MAGENTA)))
        }
        assertTrue("a failed photo can still open the viewer", openTheFirstImage())
        harness.idleClock(400)
        val viewer = PhotoViewer.getInstance()
        renderViewer("photo-viewer-failed")
        val container = field(viewer, "containerView") as View
        val now = android.os.SystemClock.uptimeMillis()
        val tap = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
            container.width / 2f, container.height / 2f, 0)
        viewer.onSingleTapConfirmed(tap)
        tap.recycle()
        harness.await("the press to fetch the picture", iterations = 12_000) {
            DiscourseImageBridge.localFiles(image).all { it.length() > 0 }
        }
        harness.await("the viewer to draw the retried photo") {
            val receiver = field(viewer, "centerImage") as org.telegram.messenger.ImageReceiver
            receiver.bitmap?.let { it.getPixel(it.width / 2, it.height / 2) == Color.MAGENTA } == true
        }
        awaitFrame("retried photo cross-fade") {
            (field(viewer, "centerImage") as org.telegram.messenger.ImageReceiver).currentAlpha == 1f
        }
        assertTrue("the viewer's download button is a real HTTP retry",
            magentaPixels(renderViewer("photo-viewer-retried")) > 1000)
        assertFalse("the viewer retry must not queue Telegram transport",
            org.telegram.messenger.FileLoader.getInstance(0).isLoadingFile(name))
        viewer.closePhoto(false, false)
        harness.idleClock(400)
        harness.await("the picture to be drawn") {
            magentaPixels(retain(harness.renderApp("photo-refused-retried"))) > 1000
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A picture still arriving, stopped by the block's own cancel button: the transfer ends, no
     * half-written file is left where a cell would draw it, and the block goes back to its button.
     */
    @Test
    fun theCancelButtonStopsTheTransferAndLeavesNoHalfWrittenFile() {
        val path = "uploads/slow-${System.nanoTime()}.png"
        val html = "<p>看这张图：</p><p><img src='${site.url(path)}' width='400' height='200'></p>"
        val image = DiscourseImageBridge.prepare(html, site.url("")).images.single()
        val name = DiscourseImageBridge.photoFileName(image)
        site.serveThrottled(path, ByteArray(1024 * 1024) { 3 }, "image/png", bytesPerPeriod = 64 * 1024, periodMillis = 500)
        site.serveCooked(TOPIC_ID, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Slow image"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        val cell = laidOutCells().first()
        val file = DiscourseImageBridge.localFiles(image).first()

        harness.await("the transfer to be running") { NativeDoHooks.isFileLoading(name) }
        harness.drawApp()
        harness.idleClock(200)
        harness.drawApp()
        retain(harness.renderApp("photo-cancel-inflight"))
        assertEquals("a fetch in flight draws the block's own cancel", 1, buttonStateOf(photoBlockOf(cell)))

        pressPhotoButton(cell)
        harness.await("the transfer to stop") { !NativeDoHooks.isFileLoading(name) }
        assertFalse("the picture is not on disk", file.exists() && file.length() > 0)
        assertTrue("and no half-written file was left behind", tempFilesBeside(file).isEmpty())
        harness.drawApp()
        harness.idleClock(200)
        harness.drawApp()
        assertEquals("the block went back to its download button", 0, buttonStateOf(photoBlockOf(cell)))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site serves a small preview beside the original. While the original is still arriving the
     * block draws the preview; when the original lands it replaces it under the same identity - the
     * same row, the same block, the same place. A picture the site serves only slowly must not hold
     * the reader on an empty shape.
     */
    @Test
    fun aSlowOriginalShowsThePreviewFirstAndThenUpgradesToTheFull() {
        val preview = "uploads/preview-${System.nanoTime()}.png"
        val original = "uploads/original-${System.nanoTime()}.png"
        site.serveImage(preview, solidPng(320, 180, Color.MAGENTA))
        site.serveThrottled(original, solidPng(1600, 900, Color.CYAN), "image/png", bytesPerPeriod = 1024, periodMillis = 200)
        val html = """
            <p>看这张图：</p>
            <p><div class="lightbox-wrapper"><a class="lightbox" href="${site.url(original)}">
            <img src="$preview" width="320" height="180"></a></div></p>
        """.trimIndent()
        val image = DiscourseImageBridge.prepare(html, site.url("")).images.single()
        assertEquals("the original is what the block draws and the viewer opens",
            site.url(original), image.url)
        site.serveCooked(TOPIC_ID, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Preview image"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        val cell = laidOutCells().first()

        harness.await("the preview to be drawn while the original is still on its way", iterations = 8_000) {
            harness.drawApp()
            val receiver = photoBlockOf(cell).imageReceiver
            val bitmap = receiver.bitmap
            cell.alpha == 1f && receiver.currentAlpha == 1f &&
                bitmap?.let { it.getPixel(it.width / 2, it.height / 2) == Color.MAGENTA } == true
        }
        harness.idleClock(250)
        assertTrue(magentaPixels(retain(harness.renderApp("photo-preview-shown"))) > 1000)
        assertTrue("the original is still arriving",
            DiscourseImageBridge.localFiles(image).all { it.length() == 0L })
        assertEquals("a preview is not the completed download; cancel remains available", 1,
            buttonStateOf(photoBlockOf(cell)))

        assertTrue("the reader opens the preview before the original arrives", openTheFirstImage())
        harness.idleClock(400)
        val viewer = PhotoViewer.getInstance()
        assertTrue("the viewer immediately draws the preview",
            magentaPixels(renderViewer("photo-viewer-preview")) > 1000)
        harness.await("the open viewer to upgrade to the original", iterations = 18_000) {
            val receiver = field(viewer, "centerImage") as org.telegram.messenger.ImageReceiver
            receiver.bitmap?.let { it.getPixel(it.width / 2, it.height / 2) == Color.CYAN } == true
        }
        awaitFrame("original photo cross-fade") {
            (field(viewer, "centerImage") as org.telegram.messenger.ImageReceiver).currentAlpha == 1f
        }
        assertTrue("the original replaces the preview in the same open viewer",
            cyanPixels(renderViewer("photo-viewer-full")) > 1000)
        viewer.closePhoto(false, false)
        harness.idleClock(400)
        harness.await("the original to replace the preview", iterations = 18_000) {
            harness.drawApp()
            val receiver = photoBlockOf(cell).imageReceiver
            val bitmap = receiver.bitmap
            receiver.currentAlpha == 1f &&
                bitmap?.let { it.getPixel(it.width / 2, it.height / 2) == Color.CYAN } == true
        }
        assertTrue(cyanPixels(retain(harness.renderApp("photo-full-shown"))) > 1000)
        assertSame("the picture upgraded in the row that was already there", cell, laidOutCells().first())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * One picture named twice in a post: both blocks are one picture, so the site is asked for it
     * once, and the row that draws it keeps its object and its place as the fetch lands.
     */
    @Test
    fun aPictureNamedTwiceIsFetchedOnceAndItsRowIsNotReplaced() {
        val path = "uploads/shared-${System.nanoTime()}.png"
        val html = "<p>看这张图：</p>" +
            "<p><img src='${site.url(path)}' width='400' height='200'></p>" +
            "<p><img src='${site.url(path)}' width='400' height='200'></p>"
        val images = DiscourseImageBridge.prepare(html, site.url("")).images
        assertEquals("both blocks name the same picture", 1, images.map { it.id }.distinct().size)
        site.serveImage(path, solidPng(400, 200, Color.MAGENTA))
        site.serveCooked(TOPIC_ID, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Shared image"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        val before = laidOutCells().first()
        val top = before.top

        harness.await("the shared picture to be drawn", iterations = 8_000) {
            magentaPixels(retain(harness.renderApp("photo-shared"))) > 1000
        }
        harness.drawApp()
        val after = laidOutCells().first()
        assertSame("the fetch lands on the row that was already there", before, after)
        assertEquals("and does not move it", top, after.top)
        assertEquals("a picture named twice is fetched once",
            1, site.recordedRequests.count { it.contains(path) })
        harness.assertNoTelegramSideEffects()
    }

    /** Closing a second topic with the same photo must not remove the first topic's retry. */
    @Test
    fun returningFromAnotherTopicKeepsTheVisiblePhotosRetry() {
        val path = "uploads/return-retry-${System.nanoTime()}.png"
        val html = "<p>仍然可以重试</p><p><img src='${site.url(path)}' width='400' height='200'></p>"
        val image = DiscourseImageBridge.prepare(html, site.url("")).images.single()
        val name = DiscourseImageBridge.photoFileName(image)
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        site.serveResponse(path) {
            if (fail.get()) MockResponse().setResponseCode(500) else
                MockResponse().setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(solidPng(400, 200, Color.MAGENTA)))
        }
        site.serveCooked(TOPIC_ID, html)
        site.serveCooked(203L, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        val first = TopicFragment.forTopic(TOPIC_ID, "First photo")
        harness.present(first)
        harness.await("first photo failure") {
            harness.drawApp()
            site.recordedRequests.any { it.contains(path) } && !NativeDoHooks.isFileLoading(name) &&
                laidOutCells().firstOrNull()?.let { buttonStateOf(photoBlockOf(it)) == 0 } == true
        }
        val firstRequests = site.recordedRequests.count { it.contains(path) }
        val second = TopicFragment.forTopic(203L, "Second photo")
        harness.present(second)
        harness.await("second photo failure") {
            harness.drawApp()
            site.recordedRequests.count { it.contains(path) } > firstRequests &&
                !NativeDoHooks.isFileLoading(name) && harness.visibleFragment() === second && laidOutCells().firstOrNull()?.let {
                buttonStateOf(photoBlockOf(it)) == 0
            } == true
        }
        second.finishFragment()
        harness.await("return to first topic") { harness.visibleFragment() === first && laidOutCells().isNotEmpty() }
        fail.set(false)
        pressPhotoButton(laidOutCells().first())
        harness.await("the returned topic can still download its photo", iterations = 8_000) {
            DiscourseImageBridge.localFiles(image).all { it.length() > 0 }
        }
        harness.await("the returned topic draws its retried photo") {
            harness.drawApp()
            val bitmap = photoBlockOf(laidOutCells().first()).imageReceiver.bitmap
            bitmap?.let { it.getPixel(it.width / 2, it.height / 2) == Color.MAGENTA } == true
        }
        assertTrue(magentaPixels(retain(harness.renderApp("photo-return-retried"))) > 1000)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Upstream's viewer groups the strip under its pictures by each page block's group id and draws
     * every cell from that block's own thumb; a bridge-built post carries neither, so the pictures
     * open but the strip never appears - a tap that opens the second picture cannot show, or move
     * to, its neighbours. The oracle is the strip's own decoded cells, next to a render, not a
     * group id flag.
     */
    @Test
    fun aPostWithThreePicturesOpensTheTappedOneAndTheStripShowsThemAll() {
        val paths = listOf("uploads/three-a.png", "uploads/three-b.png", "uploads/three-c.png")
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        site.serveImage(paths[0], solidPng(400, 300, colors[0]))
        site.serveImage(paths[2], solidPng(400, 300, colors[2]))
        // The picture the reader taps is cold (still arriving) when the viewer opens.
        site.serveThrottled(paths[1], solidPng(400, 300, colors[1]), "image/png",
            bytesPerPeriod = 1024, periodMillis = 200)
        site.serveCooked(TOPIC_ID, picturePost(paths))
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Three pictures"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        assertEquals("the post owns three pictures, one viewer group", 3, mediaBlocks().size)
        assertTrue("tapping the second picture opens the viewer at it", openTheImageAt(1))
        val viewer = PhotoViewer.getInstance()
        harness.idleClock(400)
        renderViewer("photo-strip-three-cold")
        assertEquals("the viewer is on the picture that was tapped", 1,
            field(viewer, "currentIndex"))
        assertEquals("the strip lists exactly this post's three pictures", 3, strip().count)
        assertEquals("the strip marks the picture the viewer is on", 1, strip().index)
        // The tapped picture is served in 256-byte slices every 500 ms, so the wait is bounded by
        // that transfer's real time rather than by a frame: give it the same budget the other
        // throttled-original test uses.
        harness.await("all three cells to decode their own colours", iterations = 18_000) {
            harness.idleUpstreamQueues()
            stripCellColor(0) == Color.RED &&
                stripCellColor(1) == Color.GREEN &&
                stripCellColor(2) == Color.BLUE
        }
        assertEquals("the first cell shows the first picture", Color.RED, stripCellColor(0))
        assertEquals("the second cell shows the tapped picture", Color.GREEN, stripCellColor(1))
        assertEquals("the third cell shows the third picture", Color.BLUE, stripCellColor(2))
        harness.await("the viewer to draw the tapped picture's original", iterations = 18_000) {
            (field(PhotoViewer.getInstance(), "centerImage") as ImageReceiver).bitmap
                ?.let { Color.green(it.getPixel(it.width / 2, it.height / 2)) > 200 } == true
        }
        renderViewer("photo-strip-three")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A picture whose bytes land after the viewer is already open must reach the strip that is
     * already drawn. The hook knows the preview is its own file - reported under its own name, never
     * as the original - and re-binds the strip without resetting the item the reader is on.
     */
    @Test
    fun aLateThirdPictureAppearsInTheAlreadyOpenStrip() {
        val paths = listOf("uploads/late-a.png", "uploads/late-b.png", "uploads/late-c.png")
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        site.serveImage(paths[0], solidPng(400, 300, colors[0]))
        site.serveImage(paths[1], solidPng(400, 300, colors[1]))
        site.serveResponse(paths[2]) {
            entered.countDown()
            release.await(60, TimeUnit.SECONDS)
            MockResponse().setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(solidPng(400, 300, colors[2])))
        }
        site.serveCooked(TOPIC_ID, picturePost(paths))
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Late third"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        harness.await("the third picture's request to be in flight") { entered.count == 0L }
        try {
            assertTrue(openTheImageAt(0))
            harness.idleClock(400)
            renderViewer("photo-strip-late-before")
            assertEquals("the strip shows all three positions before the third lands", 3, strip().count)
            assertNull("the missing picture's cell is a placeholder, not a fake image",
                stripCellBitmap(2))
        } finally {
            release.countDown()
        }
        harness.await("the late picture to appear in the open strip") {
            harness.idleUpstreamQueues()
            harness.idleClock(200)
            stripCellColor(2) == Color.BLUE
        }
        assertEquals("the strip stayed on the picture the reader opened", 0, strip().index)
        assertEquals("a late cell does not move the neighbours", Color.RED, stripCellColor(0))
        renderViewer("photo-strip-late-after")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The strip is not a picture: a press on one of its cells, or a slide across it, must move the
     * viewer to that picture. Both paths go through upstream's own strip delegate, so a strip drawn
     * but not wired to the viewer fails here.
     */
    @Test
    fun tappingAndSlidingTheStripMoveTheViewer() {
        val paths = listOf("uploads/nav-a.png", "uploads/nav-b.png", "uploads/nav-c.png")
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        paths.forEachIndexed { index, path -> site.serveImage(path, solidPng(400, 300, colors[index])) }
        site.serveCooked(TOPIC_ID, picturePost(paths))
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Strip navigation"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        harness.await("the three pictures on disk") {
            paths.all { site.cookieHeaderFor(it) != null }
        }
        assertTrue(openTheImageAt(0))
        val viewer = PhotoViewer.getInstance()
        harness.idleClock(400)
        renderViewer("photo-strip-nav-open")
        harness.await("the third cell's decode to be on screen before the tap") {
            harness.idleUpstreamQueues()
            stripCellColor(2) == Color.BLUE
        }

        tapStripCell(2)
        harness.idleClock(400)
        assertEquals("a press on the strip's third cell moves the viewer there", 2,
            field(viewer, "currentIndex"))
        harness.await("the viewer to draw the third picture") {
            (field(viewer, "centerImage") as ImageReceiver).bitmap
                ?.let { Color.blue(it.getPixel(it.width / 2, it.height / 2)) > 200 } == true
        }
        renderViewer("photo-strip-nav-tapped")

        slideStripTo(1)
        harness.idleClock(400)
        assertEquals("sliding the strip back moves the viewer to the second picture", 1,
            field(viewer, "currentIndex"))
        harness.await("the viewer to draw the second picture") {
            (field(viewer, "centerImage") as ImageReceiver).bitmap
                ?.let { Color.green(it.getPixel(it.width / 2, it.height / 2)) > 200 } == true
        }
        renderViewer("photo-strip-nav-slid")
        harness.assertNoTelegramSideEffects()
    }

    /** A post with one picture is a group of one: upstream hides its own strip. */
    @Test
    fun aSinglePictureOpensWithoutAStrip() {
        site.serveImage("uploads/solo.png", solidPng(400, 300, Color.GREEN))
        site.serveCooked(TOPIC_ID, picturePost(listOf("uploads/solo.png")))
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Solo picture"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        assertTrue(openTheImageAt(0))
        harness.idleClock(400)
        assertFalse("one picture must not show a strip", strip().hasPhotos())
        assertEquals("and it has no cells", 0, strip().count)
        harness.await("the single picture to be drawn") {
            (field(PhotoViewer.getInstance(), "centerImage") as ImageReceiver).bitmap
                ?.let { Color.green(it.getPixel(it.width / 2, it.height / 2)) > 200 } == true
        }
        renderViewer("photo-strip-single")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The site's preview and its original are two files. When the preview is refused, the original
     * must still arrive: the block and the strip fall back to the size whose bytes are on disk, so
     * a refused preview never leaves the reader on an empty shape.
     */
    @Test
    fun aRefusedPreviewStillShowsTheOriginalInTheViewerAndTheStrip() {
        val preview = "uploads/fail-preview.png"
        val original = "uploads/fail-original.png"
        site.serveResponse(preview) {
            MockResponse().setResponseCode(500).setBody("boom")
        }
        site.serveImage(original, solidPng(1600, 900, Color.CYAN))
        site.serveImage("uploads/fail-control.png", solidPng(400, 300, Color.RED))
        val html = """
            <p>看这张图：</p>
            <p><div class="lightbox-wrapper"><a class="lightbox" href="${site.url(original)}">
            <img src="$preview" width="400" height="300"></a></div></p>
            <p>另一张图片</p><p><img src="${site.url("uploads/fail-control.png")}" width="400" height="300"></p>
        """.trimIndent()
        val image = DiscourseImageBridge.prepare(html, site.url("")).images.first()
        site.serveCooked(TOPIC_ID, html)
        harness.bootApp()
        harness.await("home") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Refused preview"))
        harness.await("the post") { rowCount() == 1 && laidOutCells().isNotEmpty() }
        harness.await("the original to arrive after the refused preview") {
            DiscourseImageBridge.localFiles(image).any { it.length() > 0 }
        }
        assertTrue(openTheImageAt(0))
        harness.idleClock(400)
        renderViewer("photo-strip-refused-open")
        harness.await("the viewer to draw the original") {
            (field(PhotoViewer.getInstance(), "centerImage") as ImageReceiver).bitmap
                ?.let { it.getPixel(it.width / 2, it.height / 2) } == Color.CYAN
        }
        harness.await("the strip's cell to fall back to the original") {
            harness.idleUpstreamQueues()
            stripCellColor(0) == Color.CYAN
        }
        renderViewer("photo-strip-refused-preview")
        harness.assertNoTelegramSideEffects()
    }

    /** Every media block the openable post holds, in body order. */
    private fun mediaBlocks(): List<TL_iv.PageBlock> {
        val layout = laidOutCells().first().messageObject!!.richLayout
        val media = ArrayList<TL_iv.PageBlock>()
        layout.collectMediaBlocks(media)
        return media
    }

    /** Opens the viewer on the post's media block at [index], the way a tap on it does. */
    private fun openTheImageAt(index: Int): Boolean {
        val cell = laidOutCells().firstOrNull()
            ?: throw AssertionError("the topic drew no ChatMessageCell")
        val media = mediaBlocks()
        val block = media.getOrNull(index)
            ?: throw AssertionError("the post has no media block at $index")
        val opened = cell.delegate.openArticlePhoto(cell, block)
        harness.idle()
        return opened
    }

    /** Upstream's own strip, as the open viewer holds it. */
    private fun strip(): org.telegram.ui.Components.GroupedPhotosListView =
        field(PhotoViewer.getInstance(), "groupedPhotosListView")
            as org.telegram.ui.Components.GroupedPhotosListView

    /** The strip's drawn cell for picture [index], or null when it is not on screen. */
    private fun stripCell(index: Int): ImageReceiver? =
        (field(strip(), "imagesToDraw") as ArrayList<*>)
            .filterIsInstance<ImageReceiver>()
            .firstOrNull { it.param == index }

    private fun stripCellBitmap(index: Int): Bitmap? = stripCell(index)?.bitmap

    /** The colour at the centre of the strip cell for picture [index], or null when undecoded. */
    private fun stripCellColor(index: Int): Int? {
        val bitmap = stripCellBitmap(index) ?: return null
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }

    /** A press on the strip cell for picture [index], using upstream's own item geometry. */
    private fun tapStripCell(index: Int) {
        val strip = strip()
        val density = harness.context.resources.displayMetrics.density
        val itemWidth = (42 * density).toInt()
        val itemSpacing = (1 * density).toInt()
        val itemHeight = (56 * density).toInt()
        val itemY = (10 * density).toInt()
        val startX = strip.width / 2 - itemWidth / 2
        val x = (startX + (index - strip.index) * (itemWidth + itemSpacing) + itemWidth / 2).toFloat()
        val y = (itemY + itemHeight / 2).toFloat()
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        try {
            strip.onSingleTapUp(event)
        } finally {
            event.recycle()
        }
        harness.idle()
    }

    /** A slide across the strip to picture [index], through upstream's own gesture handler. */
    private fun slideStripTo(index: Int) {
        val strip = strip()
        val density = harness.context.resources.displayMetrics.density
        val step = ((42 + 1) * density).toInt()
        val distanceX = ((index - strip.index) * step).toFloat()
        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val move = MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_MOVE, distanceX, 0f, 0)
        try {
            strip.onDown(down)
            strip.onScroll(down, move, distanceX, 0f)
        } finally {
            down.recycle()
            move.recycle()
        }
        harness.idle()
    }

    /** A body with one picture block per path, kept apart by text so none is a collage. */
    private fun picturePost(paths: List<String>): String =
        paths.mapIndexed { index, path ->
            "<p>第 ${index + 1} 张图：</p><p><img src='${site.url(path)}' width='400' height='300'></p>"
        }.joinToString("")

    /** The body text's own dark pixels inside [cell]'s drawn bounds, read from a real render. */
    private fun darkPixelsIn(cell: org.telegram.ui.Cells.ChatMessageCell): Int {
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
        println("photo: dark body pixels in the late-fetch row = $dark")
        return dark
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

    /** The first photo block the row draws, so a test can read its own button state. */
    private fun photoBlockOf(cell: org.telegram.ui.Cells.ChatMessageCell): RichMessageLayout.RichPhotoBlock =
        cell.messageObject!!.richLayout.blocks
            .filterIsInstance<RichMessageLayout.RichPhotoBlock>()
            .firstOrNull()
            ?: throw AssertionError("the post drew no rich photo block")

    /** The block's own button state: -1 none, 0 download, 1 loading/cancel. */
    private fun buttonStateOf(photo: RichMessageLayout.RichPhotoBlock): Int =
        RichMessageLayout.RichMediaBlock::class.java.getDeclaredField("buttonState")
            .apply { isAccessible = true }
            .get(photo) as Int

    /**
     * A press on the block's own button, handed over the way upstream's cell hands one over: the
     * layout dispatches the event to the block that holds the coordinates, and the block's own
     * handler is what a reader's thumb reaches.
     */
    private fun pressPhotoButton(cell: org.telegram.ui.Cells.ChatMessageCell) {
        val layout = cell.messageObject!!.richLayout
        harness.layoutApp()
        val media = layout.blocks.filterIsInstance<RichMessageLayout.RichMediaBlock>().first()
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

    /** The temporary files a transfer to [file] would leave beside it, if any survived. */
    private fun tempFilesBeside(file: File): List<File> =
        file.parentFile?.listFiles { candidate ->
            candidate.name.startsWith(file.name + ".") && candidate.name.endsWith(".part")
        }?.toList().orEmpty()

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

    private fun rowCount(): Int = contentRowCount(harness)

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 202L
        const val IMAGE_PATH = "uploads/sample-image.png"
        const val ORIGINAL_PATH = "uploads/original/sample-image.png"
    }
}
