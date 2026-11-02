package io.github.baba983.nativedo.topic

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
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
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import java.io.InputStream

/**
 * Image content the reader's keyboard or clipboard hands the composer.
 *
 * <p>The failures pinned here, all through the shipped screens and the site's own upload endpoint:</p>
 * <ul>
 *   <li>an IME's `commitContent` (a keyboard's GIF/sticker insertion) is sent as a Telegram photo or
 *       GIF - `SendMessagesHelper`, tgnet - instead of being uploaded to the site;</li>
 *   <li>a WebP commit falls through upstream's sticker test into the same Telegram send;</li>
 *   <li>the read grant an IME hands over outlives its copy, or is released before the URI's bytes
 *       are the screen's own (a revoked URI would then upload a truncated file);</li>
 *   <li>a screen with no intake (closed, or a composer delegate nobody bound) crashes or silently
 *       leaks the grant instead of releasing it;</li>
 *   <li>a plain-text paste stops pasting because the image branch got in its way.</li>
 * </ul>
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
internal class ComposerMediaInputTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>Hello from a linux.do post.</p>")
            post.put("post_number", 1)
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        site.stop()
    }

    /**
     * The keyboard's own insertion of a PNG reaches the site and the field, not a Telegram photo.
     * Upstream's branch for it would call `SendMessagesHelper.prepareSendingPhoto` and open its
     * photo editor; neither exists in this host.
     */
    @Test
    fun anImePngIsUploadedInsteadOfSentAsATelegramPhoto() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()

        assertTrue("the composer takes the committed image", commit(composer, imageUri("ime.png", transparentPng()), "image/png"))

        harness.await("the site's markdown to land in the field") {
            composer.getFieldText()?.toString().orEmpty().contains("upload://")
        }
        assertTrue("the upload went to the site", site.requestPaths.contains("uploads.json"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A GIF commit is upstream's `prepareSendingDocument` branch and a WebP one is its
     * `shouldSendWebPAsSticker` branch; both are Telegram media services here, so both must take
     * the same host path as a still image.
     */
    @Test
    fun gifAndWebpCommitsTakeTheSameHostPath() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()

        assertTrue("the composer takes a GIF", commit(composer, imageUri("animation.gif", transparentPng()), "image/gif"))
        harness.await("the GIF to land in the field") {
            composer.getFieldText()?.toString().orEmpty().count { it == '\n' } == 1
        }
        assertTrue("the composer takes a WebP", commit(composer, imageUri("still.webp", transparentPng()), "image/webp"))
        harness.await("the WebP to land in the field") {
            composer.getFieldText()?.toString().orEmpty().count { it == '\n' } == 2
        }

        assertEquals(
            "both commits reached the site, neither became a Telegram media object",
            2,
            site.requestPaths.count { it == "uploads.json" },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The grant is the IME's read permission for the URI. The bytes have to be copied while it is
     * still held, and it must be released exactly once afterwards - a release that happened first
     * would make this upload read a revoked URI and fail, and a missing release would leak the
     * grant for the life of the process.
     */
    @Test
    fun theGrantOutlivesTheCopyAndIsReleasedExactlyOnce() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        openTopicWithComposer()
        val bytes = transparentPng()
        val stream = GrantGatedStream(bytes)
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/ime-grant.png")
        shadowOf(harness.activity.contentResolver).registerInputStream(uri, stream)
        val fragment = harness.visibleFragment() as TopicFragment
        var releases = 0

        fragment.attachMediaUri(uri, Runnable {
            releases++
            stream.granted = false
        })

        harness.await("the site's markdown to land in the field") {
            !composer().getFieldText().isNullOrEmpty()
        }
        assertEquals("the grant is released exactly once", 1, releases)
        assertFalse("and is gone afterwards", stream.granted)
        val part = filePart(site.requestBytesFor("uploads.json")!!)
        assertArrayEquals("the file part is the URI's bytes, copied while the grant was held", bytes, part.second)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The composer's own delegate with no screen behind it (a closed topic, or a field whose host
     * never bound an intake) still owns the content: the grant is released and nothing falls into
     * upstream's Telegram branch.
     */
    @Test
    fun aNativeDelegateWithNoIntakeReleasesTheGrant() {
        harness.bootViews()
        val parent = SizeNotifierFrameLayout(harness.activity)
        harness.content.addView(parent)
        val composer = ChatActivityEnterView(harness.activity, parent, null, true, null)
        val delegate = ReleasingDelegate()
        composer.setDelegate(delegate)
        val uri = imageUri("no-intake.png", transparentPng())

        assertTrue("a native delegate claims the content", commit(composer, uri, "image/png"))

        assertEquals("the delegate was handed the URI", uri, delegate.uri)
        assertEquals("and released the grant once", 1, delegate.releases)
        assertEquals("nothing was inserted without an intake", "", composer.getFieldText()?.toString().orEmpty())
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Content the keyboard commits that is not a photo - an IME is only told image types, but it
     * may hand over something else - is still the host's: the delegate takes it and releases the
     * grant, so nothing falls into upstream's Telegram branch for "not a GIF, not a photo".
     */
    @Test
    fun aNonImageCommitStillReleasesTheGrantAndNeverReachesTelegram() {
        harness.bootViews()
        val parent = SizeNotifierFrameLayout(harness.activity)
        harness.content.addView(parent)
        val composer = ChatActivityEnterView(harness.activity, parent, null, true, null)
        val delegate = ReleasingDelegate()
        composer.setDelegate(delegate)
        val uri = imageUri("notes.txt", "plain notes".toByteArray())

        assertTrue("the composer answers for content it will not attach", commit(composer, uri, "text/plain"))

        assertEquals("the delegate owns it, not upstream's photo branch", uri, delegate.uri)
        assertEquals("the grant is released", 1, delegate.releases)
        harness.assertNoTelegramSideEffects()
    }

    /** A screen that is already gone takes no file, but the grant it was handed is still released. */
    @Test
    fun aClosedScreenReleasesTheGrantWithoutUploading() {
        harness.bootViews()
        val fragment = TopicFragment.forTopic(TOPIC_ID, "Media")
        var releases = 0

        fragment.attachMediaUri(Uri.parse("content://io.github.baba983.nativedo.test/gone.png"), Runnable { releases++ })

        assertEquals("the grant is released", 1, releases)
        assertFalse("and no upload is started for a closed screen", site.requestPaths.contains("uploads.json"))
    }

    /** The new-topic form has the same seam: the body, not a Telegram media message, gets the file. */
    @Test
    fun theNewTopicBodyTakesACommittedImage() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(NewTopicFragment.create())
        harness.await("the new-topic composer") { composerOrNull() != null }
        val composer = composer()

        assertTrue("the new topic takes the committed image", commit(composer, imageUri("new-topic.png", transparentPng()), "image/png"))

        harness.await("the site's markdown to land in the body") {
            composer.getFieldText()?.toString().orEmpty().contains("upload://")
        }
        assertTrue("the upload went to the site", site.requestPaths.contains("uploads.json"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A screen with no upload path inherits the base delegate's answer: the grant is released and
     * nothing is attached, so a commit on such a screen can neither leak the grant nor fall into
     * Telegram's send branch.
     */
    @Test
    fun aScreenWithoutAnUploadPathReleasesTheGrantAndAttachesNothing() {
        var releases = 0

        PlainDelegate().onComposerMediaInput(
            Uri.parse("content://io.github.baba983.nativedo.test/no-path.png"),
            Runnable { releases++ },
        )

        assertEquals("the grant is released", 1, releases)
    }

    /** An image on the clipboard is an attachment; upstream would open its own photo editor. */
    @Test
    fun anImagePasteUploadsInsteadOfOpeningThePhotoEditor() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openTopicWithComposer()
        val uri = imageUri("pasted.png", transparentPng())
        clipboard().setPrimaryClip(
            ClipData(ClipDescription("image", arrayOf("image/png")), ClipData.Item(uri)),
        )

        requireNotNull(composer.getEditField()).onTextContextMenuItem(android.R.id.paste)

        harness.await("the site's markdown to land in the field") {
            composer.getFieldText()?.toString().orEmpty().contains("upload://")
        }
        assertFalse("pasting media must not also paste its private provider URI",
            composer.getFieldText()?.toString().orEmpty().contains(uri.toString()))
        assertTrue("the paste was uploaded, not opened in a Telegram editor", site.requestPaths.contains("uploads.json"))
        harness.assertNoTelegramSideEffects()
    }

    /** A text paste is still upstream's own paste: image handling must not take it over. */
    @Test
    fun aPlainTextPasteStillPastes() {
        val composer = openTopicWithComposer()
        clipboard().setPrimaryClip(ClipData.newPlainText("text", "从键盘粘贴"))

        requireNotNull(composer.getEditField()).onTextContextMenuItem(android.R.id.paste)

        assertEquals("the text is in the field", "从键盘粘贴", composer.getFieldText()?.toString().orEmpty())
        assertFalse("a text paste uploads nothing", site.requestPaths.contains("uploads.json"))
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The composer's own IME connection, driven the way a keyboard drives it: `commitContent` on
     * the wrapper upstream builds in `onCreateInputConnection`, with the read-grant flag set.
     */
    private fun commit(composer: ChatActivityEnterView, uri: Uri, mime: String): Boolean {
        val editorInfo = EditorInfo()
        val connection = requireNotNull(composer.getEditField()).onCreateInputConnection(editorInfo)
            ?: throw AssertionError("the composer's field offers no input connection")
        val content = InputContentInfoCompat(uri, ClipDescription("clipboard", arrayOf(mime)), null)
        return InputConnectionCompat.commitContent(
            connection,
            editorInfo,
            content,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }

    private fun imageUri(name: String, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/$name")
        shadowOf(harness.activity.contentResolver).registerInputStream(uri, bytes.inputStream())
        return uri
    }

    private fun clipboard(): ClipboardManager =
        harness.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun openTopicWithComposer(): ChatActivityEnterView {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Media"))
        harness.await("the topic to load") { rowCount() == 1 }
        return composer()
    }

    private fun composer(): ChatActivityEnterView =
        composerOrNull() ?: throw AssertionError("the screen has no composer")

    private fun composerOrNull(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw AssertionError("the activity has no content view")
        return findComposer(content)
    }

    private fun findComposer(view: View): ChatActivityEnterView? {
        if (view is ChatActivityEnterView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposer(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun rowCount(): Int = contentRowCount(harness)

    /**
     * The file part of a multipart body, read from the bytes as they were sent: the name in its
     * `Content-Disposition` and its payload. ISO-8859-1 makes every character one byte, so the
     * header scan indexes the same bytes it slices.
     */
    private fun filePart(bytes: ByteArray): Pair<String, ByteArray> {
        val wire = bytes.toString(Charsets.ISO_8859_1)
        val header = "Content-Disposition: form-data; name=\"file\"; filename=\""
        val headerAt = wire.indexOf(header)
        assertTrue("the body carries a file part", headerAt >= 0)
        val nameAt = headerAt + header.length
        val name = wire.substring(nameAt, wire.indexOf('"', nameAt))
        val payloadAt = wire.indexOf("\r\n\r\n", headerAt) + 4
        val payloadEnd = wire.indexOf("\r\n--", payloadAt)
        assertTrue("the file part has a payload of its own", payloadAt >= 4 && payloadEnd > payloadAt)
        return name to bytes.copyOfRange(payloadAt, payloadEnd)
    }

    /** A 1x1 PNG: the bytes are what matters, and the site does not inspect them. */
    private fun transparentPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT,
    )

    /** A read stream that stops answering the moment the IME grant is released. */
    private class GrantGatedStream(private val bytes: ByteArray) : InputStream() {

        var granted = true
        private var index = 0

        override fun read(): Int {
            if (!granted) throw SecurityException("the commit grant was released")
            if (index >= bytes.size) return -1
            return bytes[index++].toInt() and 0xff
        }
    }

    /** A composer delegate with no screen and no media override: the base's own answer. */
    private open class PlainDelegate : DiscourseComposerDelegate() {

        override fun onMessageSend(
            message: CharSequence?,
            notify: Boolean,
            scheduleDate: Int,
            scheduleRepeatPeriod: Int,
            payStars: Long,
        ) {
        }

        override fun onTextChanged(text: CharSequence?, bigChange: Boolean, fromDraft: Boolean) {
        }
    }

    /** A composer delegate with no screen behind it: it counts the grant releases it is handed. */
    private class ReleasingDelegate : PlainDelegate() {

        var uri: Uri? = null
        var releases = 0

        override fun onComposerMediaInput(uri: Uri, releasePermission: Runnable) {
            this.uri = uri
            releases++
            releasePermission.run()
        }
    }

    private companion object {
        const val TOPIC_ID = 809L
    }
}
