package org.telegram.ui.iv

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MediaController
import org.telegram.messenger.nativedo.RichMediaUploadBridge
import org.telegram.tgnet.TLRPC
import java.io.File

/**
 * The editor's media upload path as this client wires it: the pin's [RichMediaUploader] uploads to
 * Telegram, ours uploads to Discourse through [RichMediaUploadBridge] (installed by `:app`) and
 * fabricates the `TLRPC` objects the editor stores from the uploaded URL - `id` is the bridge's id
 * for that URL, which is how the send bridge will get back to the URL. [RichMediaConverter] has
 * nothing to convert for us, so it reports the picked file straight away. The tests drive the whole
 * hop with a fake uploader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvMediaUploadContractTest {

    private class FakeUploader : RichMediaUploadBridge.Uploader {
        var requests = mutableListOf<RichMediaUploadBridge.Request>()
        var cancelled = mutableListOf<String>()
        var callback: RichMediaUploadBridge.Callback? = null

        override fun upload(request: RichMediaUploadBridge.Request, callback: RichMediaUploadBridge.Callback) {
            requests.add(request)
            this.callback = callback
        }

        override fun cancel(path: String) {
            cancelled.add(path)
        }
    }

    private class PhotoListener : RichMediaUploader.Listener {
        var resolved: Pair<Int, Int>? = null
        var progress = -1f
        var photo: TLRPC.Photo? = null
        var document: TLRPC.Document? = null
        var error = false

        override fun onWidthHeightResolved(w: Int, h: Int) { resolved = w to h }
        override fun onProgress(progress: Float) { this.progress = progress }
        override fun onPhotoUploaded(photo: TLRPC.Photo) { this.photo = photo }
        override fun onVideoUploaded(document: TLRPC.Document) { this.document = document }
        override fun onError() { error = true }
    }

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        RichMediaUploadBridge.setUploader(null)
    }

    private fun pngFile(width: Int, height: Int): String {
        val file = File.createTempFile("nativedo-upload", ".png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    @Test
    fun theBridgeKeepsAStableIdForAUrl() {
        val id = RichMediaUploadBridge.registerUrl("https://linux.do/uploads/a.png")
        assertThat(id).isGreaterThan(0)
        assertThat(RichMediaUploadBridge.urlForId(id)).isEqualTo("https://linux.do/uploads/a.png")
        // the same URL keeps its id, and release forgets both directions
        assertThat(RichMediaUploadBridge.registerUrl("https://linux.do/uploads/a.png")).isEqualTo(id)
        RichMediaUploadBridge.releaseId(id)
        assertThat(RichMediaUploadBridge.urlForId(id)).isNull()
    }

    @Test
    fun importingAUrlFabricatesAMediaObjectThatRemembersIt() {
        val url = "https://linux.do/uploads/original/1/photo.png"
        val id = RichMediaUploadBridge.registerUrl(url)

        val photo = RichMediaUploadBridge.fabricatePhoto(id, 640, 480)

        assertThat(photo.id).isEqualTo(id)
        assertThat(RichMediaUploadBridge.urlForId(photo.id)).isEqualTo(url)
        assertThat(photo.sizes[0].w).isEqualTo(640)
        assertThat(photo.sizes[0].h).isEqualTo(480)

        val clipUrl = "https://linux.do/uploads/original/1/clip.mp4"
        val clipId = RichMediaUploadBridge.registerUrl(clipUrl)
        val clip = RichMediaUploadBridge.fabricateDocument(clipId, clipUrl, "clip.mp4", true, false, 1920, 1080, 5)
        assertThat(RichMediaUploadBridge.urlForId(clip.id)).isEqualTo(clipUrl)
        assertThat(clip.mime_type).isEqualTo("video/mp4")
        assertThat(clip.attributes.filterIsInstance<TLRPC.TL_documentAttributeVideo>()).hasSize(1)

        // a plain document keeps its name and gets no media attribute
        val docUrl = "https://linux.do/uploads/original/1/notes.pdf"
        val docId = RichMediaUploadBridge.registerUrl(docUrl)
        val doc = RichMediaUploadBridge.fabricateDocument(docId, docUrl, null, false, false, 0, 0, 0)
        assertThat(doc.attributes.filterIsInstance<TLRPC.TL_documentAttributeFilename>().single().file_name).isEqualTo("notes.pdf")
        assertThat(doc.attributes.filterIsInstance<TLRPC.TL_documentAttributeVideo>()).isEmpty()
        assertThat(doc.attributes.filterIsInstance<TLRPC.TL_documentAttributeAudio>()).isEmpty()

        RichMediaUploadBridge.releaseId(id)
        RichMediaUploadBridge.releaseId(clipId)
        RichMediaUploadBridge.releaseId(docId)
    }

    @Test
    fun aPhotoUploadResolvesItsDimensionsAndHandsTheUrlIdToTheEditor() {
        val fake = FakeUploader()
        RichMediaUploadBridge.setUploader(fake)
        val listener = PhotoListener()
        val uploader = RichMediaUploader(0, pngFile(4, 3), listener)

        uploader.start()

        assertThat(listener.resolved).isEqualTo(4 to 3)
        assertThat(fake.requests).hasSize(1)
        with(fake.requests[0]) {
            assertThat(video).isFalse()
            assertThat(audio).isFalse()
            assertThat(document).isFalse()
        }

        fake.callback!!.onProgress(0.5f)
        assertThat(listener.progress).isEqualTo(0.5f)

        fake.callback!!.onUploaded("https://linux.do/uploads/b.png", "b.png", 800, 600, 0)

        val photo = listener.photo
        assertThat(photo).isNotNull()
        assertThat(photo!!.sizes[0].w).isEqualTo(800)
        assertThat(photo.sizes[0].h).isEqualTo(600)
        assertThat(RichMediaUploadBridge.urlForId(photo.id)).isEqualTo("https://linux.do/uploads/b.png")
        assertThat(listener.error).isFalse()
        assertThat(uploader.isFinished).isTrue()
    }

    @Test
    fun aVideoUploadReportsTheShapesAndCarriesItsUrl() {
        val fake = FakeUploader()
        RichMediaUploadBridge.setUploader(fake)
        val listener = PhotoListener()
        val uploader = RichMediaUploader(0, "/tmp/clip.mp4", true, 1920, 1080, 5, listener)

        uploader.start()
        assertThat(listener.resolved).isEqualTo(1920 to 1080)
        assertThat(fake.requests[0].video).isTrue()

        fake.callback!!.onUploaded("https://linux.do/uploads/clip.mp4", "clip.mp4", 1920, 1080, 5)

        val document = listener.document
        assertThat(document).isNotNull()
        assertThat(RichMediaUploadBridge.urlForId(document!!.id)).isEqualTo("https://linux.do/uploads/clip.mp4")
        assertThat(document.mime_type).isEqualTo("video/mp4")
        val video = document.attributes.filterIsInstance<TLRPC.TL_documentAttributeVideo>().single()
        assertThat(video.w).isEqualTo(1920)
        assertThat(video.duration).isEqualTo(5)
        assertThat(uploader.isFinished).isTrue()
    }

    @Test
    fun cancellingStopsTheUploadAndIgnoresLateAnswers() {
        val fake = FakeUploader()
        RichMediaUploadBridge.setUploader(fake)
        val listener = PhotoListener()
        val uploader = RichMediaUploader(0, pngFile(2, 2), listener)

        uploader.start()
        uploader.cancel()

        assertThat(uploader.isCancelled).isTrue()
        assertThat(fake.cancelled).containsExactly(uploader.path)
        fake.callback!!.onUploaded("https://linux.do/uploads/c.png", "c.png", 0, 0, 0)
        fake.callback!!.onError()
        assertThat(listener.photo).isNull()
        assertThat(listener.error).isFalse()
    }

    @Test
    fun withoutAnUploaderTheEditorSeesAnError() {
        RichMediaUploadBridge.setUploader(null)
        val listener = PhotoListener()
        RichMediaUploader(0, pngFile(1, 1), listener).start()
        assertThat(listener.error).isTrue()
    }

    @Test
    fun theConverterReportsThePickedFileAsItIs() {
        val entry = MediaController.PhotoEntry(0, 1, 0L, "/tmp/pic.png", 0, 0, false, 640, 480, 1024L)
        var done: Triple<String, Int, Int>? = null
        var duration = -1
        var error = false
        val converter = RichMediaConverter(0, entry, object : RichMediaConverter.Listener {
            override fun onDone(mp4Path: String, width: Int, height: Int, durationSec: Int) {
                done = Triple(mp4Path, width, height)
                duration = durationSec
            }
            override fun onError() { error = true }
        })

        assertThat(RichMediaConverter.hasAnimatedMediaEntities(entry)).isFalse()
        converter.start()

        assertThat(done).isEqualTo(Triple("/tmp/pic.png", 640, 480))
        assertThat(duration).isEqualTo(0)
        assertThat(error).isFalse()
        assertThat(converter.isFinished).isTrue()
    }

    @Test
    fun theConverterErrorsOnAnEmptyPath() {
        val entry = MediaController.PhotoEntry(0, 1, 0L, "", 0, 0, false, 640, 480, 1024L)
        var error = false
        RichMediaConverter(0, entry, object : RichMediaConverter.Listener {
            override fun onDone(mp4Path: String, width: Int, height: Int, durationSec: Int) {}
            override fun onError() { error = true }
        }).start()
        assertThat(error).isTrue()
    }
}
