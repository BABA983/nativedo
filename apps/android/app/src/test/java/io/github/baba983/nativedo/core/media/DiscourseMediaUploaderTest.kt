package io.github.baba983.nativedo.core.media

import com.google.common.truth.Truth.assertThat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.NetworkJson
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.telegram.messenger.nativedo.RichMediaUploadBridge
import retrofit2.Retrofit

/**
 * The app's end of the editor's upload seam: [DiscourseMediaUploader] takes what the pin's
 * `RichMediaUploader` asks for and posts it to the site (`POST /uploads.json`), answering with the
 * URL a post embeds. These tests drive it against MockWebServer with the site's own answer
 * (`fixtures/discourse/upload-response.json`).
 */
class DiscourseMediaUploaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var uploader: DiscourseMediaUploader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
        uploader = DiscourseMediaUploader(
            client = DiscourseApiClient(retrofit.create(DiscourseApiService::class.java)),
            baseUrl = server.url("/").toString(),
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class Recorder : RichMediaUploadBridge.Callback {
        val done = CompletableDeferred<Boolean>()
        val progress = CopyOnWriteArrayList<Float>()
        var url: String? = null
        var filename: String? = null
        var width = 0
        var height = 0
        var durationSec = 0

        override fun onProgress(progress: Float) {
            this.progress.add(progress)
        }

        override fun onUploaded(url: String, filename: String, width: Int, height: Int, durationSec: Int) {
            this.url = url
            this.filename = filename
            this.width = width
            this.height = height
            this.durationSec = durationSec
            done.complete(true)
        }

        override fun onError() {
            done.complete(false)
        }
    }

    private fun photoRequest(file: File) = RichMediaUploadBridge.Request(
        0,
        file.absolutePath,
        false,
        false,
        false,
        640,
        480,
        0,
    )

    private fun await(recorder: Recorder): Boolean = runBlocking {
        withTimeout(TimeUnit.SECONDS.toMillis(10)) { recorder.done.await() }
    }

    private fun csrfResponse(): MockResponse =
        MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody("""{"csrf":"token"}""")

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody(body)

    private fun fixture(path: String): String = File("../../../fixtures/$path").readText()

    private fun writePhoto(name: String = "screenshot.png"): File =
        folder.newFile(name).apply { writeText("png-bytes-123") }

    @Test
    fun theUploadPostsTheFileAndHandsBackTheSitesUrl() {
        server.enqueue(csrfResponse())
        server.enqueue(jsonResponse(fixture("discourse/upload-response.json")))
        val file = writePhoto()
        val recorder = Recorder()

        uploader.upload(photoRequest(file), recorder)

        assertThat(await(recorder)).isTrue()
        assertThat(recorder.url)
            .isEqualTo("https://linux.do/uploads/default/original/2X/9/9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png")
        assertThat(recorder.filename).isEqualTo("screenshot.png")
        assertThat(recorder.width).isEqualTo(1080)
        assertThat(recorder.height).isEqualTo(720)

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("POST")
        assertThat(upload.path).isEqualTo("/uploads.json")
        assertThat(upload.getHeader("X-CSRF-Token")).isEqualTo("token")
        assertThat(upload.getHeader("Content-Type")).contains("multipart/form-data")
        val body = upload.body.readUtf8()
        assertThat(body).contains("name=\"file\"")
        assertThat(body).contains("filename=\"screenshot.png\"")
        assertThat(body).contains("png-bytes-123")
        assertThat(body).contains("name=\"upload_type\"")
        assertThat(body).contains("composer")
        assertThat(body).contains("name=\"synchronous\"")
    }

    @Test
    fun theUploadReportsTheFilesProgress() {
        server.enqueue(csrfResponse())
        server.enqueue(jsonResponse(fixture("discourse/upload-response.json")))
        val recorder = Recorder()

        uploader.upload(photoRequest(writePhoto()), recorder)

        assertThat(await(recorder)).isTrue()
        assertThat(recorder.progress).isNotEmpty()
        assertThat(recorder.progress.last()).isEqualTo(1f)
        assertThat(recorder.progress).isInOrder()
    }

    @Test
    fun aRelativeUrlIsResolvedAgainstTheBaseUrl() {
        server.enqueue(csrfResponse())
        server.enqueue(
            jsonResponse(
                """{"id":7,"url":"/uploads/default/original/2X/a/abc.png","original_filename":"abc.png","width":64,"height":32}""",
            ),
        )
        val recorder = Recorder()

        uploader.upload(photoRequest(writePhoto("abc.png")), recorder)

        assertThat(await(recorder)).isTrue()
        assertThat(recorder.url).isEqualTo(server.url("/uploads/default/original/2X/a/abc.png").toString())
    }

    @Test
    fun theSitesDimensionsWinAndOursFillIn() {
        server.enqueue(csrfResponse())
        server.enqueue(
            jsonResponse(
                """{"id":7,"url":"https://linux.do/uploads/a.png","original_filename":"a.png","width":0,"height":0}""",
            ),
        )
        val recorder = Recorder()

        uploader.upload(photoRequest(writePhoto("a.png")), recorder)

        assertThat(await(recorder)).isTrue()
        // the request already knew the picked file's size; the site answering 0 keeps it
        assertThat(recorder.width).isEqualTo(640)
        assertThat(recorder.height).isEqualTo(480)
    }

    @Test
    fun aRejectedUploadReportsAnError() {
        server.enqueue(csrfResponse())
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"errors":["Invalid file"]}"""))
        val recorder = Recorder()

        uploader.upload(photoRequest(writePhoto()), recorder)

        assertThat(await(recorder)).isFalse()
        assertThat(recorder.url).isNull()
    }

    @Test
    fun aCancelledUploadReportsNothing() {
        server.enqueue(csrfResponse())
        server.enqueue(jsonResponse(fixture("discourse/upload-response.json")).setBodyDelay(3, TimeUnit.SECONDS))
        val file = writePhoto()
        val recorder = Recorder()

        uploader.upload(photoRequest(file), recorder)
        uploader.cancel(file.absolutePath)

        val outcome = runBlocking { withTimeoutOrNull(500) { recorder.done.await() } }
        assertThat(outcome).isNull()
        assertThat(recorder.url).isNull()
    }

    @Test
    fun aMissingFileIsAnErrorWithoutARequest() {
        val recorder = Recorder()

        uploader.upload(photoRequest(File(folder.root, "gone.png")), recorder)

        assertThat(await(recorder)).isFalse()
        assertThat(server.requestCount).isEqualTo(0)
    }
}
