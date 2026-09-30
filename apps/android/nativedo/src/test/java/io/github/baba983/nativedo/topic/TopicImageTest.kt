package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
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
        site.serveImage(IMAGE_PATH, magentaPng(300, 200))
        site.serveCooked(TOPIC_ID, postWithImage())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aPostImageIsDownloadedWithTheSessionAndDrawnByTheStockCell() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Image post"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.await("the image to be fetched") { site.cookieHeaderFor(IMAGE_PATH) != null }

        var magenta = 0
        harness.await("the stock cell to draw the downloaded image") {
            magenta = magentaPixels(harness.renderApp("post-image"))
            magenta > 0
        }
        println("image: magenta pixels in the render = $magenta")

        assertTrue("a tap on the image opens the viewer", openTheFirstImage())
        assertTrue(
            "and the viewer is upstream's, showing the tapped image",
            org.telegram.ui.PhotoViewer.getInstance().isVisible,
        )

        val cookieHeader = site.cookieHeaderFor(IMAGE_PATH)
        assertTrue("the image download must carry the session", cookieHeader!!.contains("_t="))
        assertTrue("and the forum session", cookieHeader.contains("_forum_session="))
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
        <p><div class="lightbox-wrapper"><a class="lightbox" href="${site.url(IMAGE_PATH)}"
            title="sample"><img src="$IMAGE_PATH" alt="sample" width="300" height="200"></a>
            <div class="meta">sample.png 12 KB</div></div></p>
        <p>下面是表格。</p>
    """.trimIndent()

    private fun magentaPng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.MAGENTA)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun magentaPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) > 200 && Color.blue(it) > 200 && Color.green(it) < 100 }
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
    }
}
