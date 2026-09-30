package io.github.baba983.nativedo.topic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecyclerListView

/**
 * Attaching a file to a reply, end to end: the composer's own attach button, the site's upload
 * endpoint, and the markdown the field receives.
 *
 * <p>The failure this pins: a reply that references a file the site never accepted. Discourse only
 * serves the URL it answered with, and it embeds a relative URL in a post differently from an
 * absolute one, so the markdown has to be built from the answer and the base URL together.</p>
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
internal class UploadReplyTest {

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

    @Test
    fun anImageIsUploadedAndTheFieldGetsTheSitesOwnImageMarkdown() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openComposerAndAttach()
        // Upstream offers the attach button only while the field is empty, so this is the flow the
        // reader takes: pick a file, then write the sentence around its markup.
        pickFile("screenshot.png", transparentPng())

        // The request lands on the site before its answer is read, so what is waited for is the
        // field the answer fills.
        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }

        val body = site.requestBodyFor("uploads.json")
        println("upload: body starts with " + body?.take(120))
        assertNotNull("the upload carried a body", body)
        assertTrue("sent as the composer's own upload", body!!.contains("name=\"upload_type\"") && body.contains("composer"))
        assertTrue(
            "and asked for a synchronous answer, which is the one that carries the URL",
            body.contains("name=\"synchronous\"") && body.contains("true"),
        )
        assertTrue("with the file as its own part", body.contains("name=\"file\""))
        assertEquals(
            "and with the token the site asks writes for",
            "csrf-token-demo",
            site.csrfHeaderFor("uploads.json"),
        )

        val field = composer.fieldText.toString()
        println("upload: field = $field")
        assertEquals(
            "the field holds the markdown the site's own composer writes: its short handle and the dimensions",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            field,
        )
        assertEquals(
            "and the send button is there as soon as the upload lands",
            View.VISIBLE,
            composer.getSendButton()?.visibility,
        )
        harness.renderApp("topic-reply-upload")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anythingThatIsNotAnImageBecomesTheSitesAttachmentMarkdown() {
        site.serveJson("uploads.json", harness.fixture("upload-response-attachment.json"))
        val composer = openComposerAndAttach()
        pickFile("session-notes.zip", "PK\u0003\u0004zip".toByteArray())

        harness.await("the field to hold the uploaded file") {
            !composer.getFieldText().isNullOrEmpty()
        }

        val field = composer.fieldText.toString()
        println("upload: attachment field = $field")
        assertEquals(
            "a file the site will not render inline keeps its name, its marker and its size",
            "[session-notes.zip|attachment](upload://1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d.zip) (1.2 MB)\n",
            field,
        )
        harness.renderApp("topic-reply-upload-attachment")
        harness.assertNoTelegramSideEffects()
    }

    /** Boots the topic, presses the composer's own attach button and hands back the composer. */
    private fun openComposerAndAttach(): ChatActivityEnterView {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Upload"))
        harness.await("the topic to load") { rowCount() == 1 }
        val composer = composer() ?: throw AssertionError("the screen has no composer")
        composer.getAttachButton().performClick()
        harness.idle()
        return composer
    }

    @Test
    fun aSecondFileAppendsToTheFirstThroughTheStripsOwnButton() {
        site.serveJson("uploads.json", harness.fixture("upload-response.json"))
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", transparentPng())
        harness.await("the first upload to land in the field") {
            !composer.getFieldText().isNullOrEmpty()
        }

        // A laid-out composer has already decided what to offer: with text in the field upstream's
        // own paperclip is gone, and ours is the only way to a second file.
        harness.layoutApp()
        val strip = attachStrip() ?: throw AssertionError("the screen has no attach strip")
        assertEquals("shown while the field has something in it", View.VISIBLE, strip.visibility)
        // Upstream's own paperclip is not what the reader sees once the field has text (compare
        // topic-reply-empty.png with the render below), so a second file goes through the strip.
        println(
            "upload: upstream paperclip while the field has text — alpha=" + composer.getAttachButton()?.alpha +
                " drawnAt=" + composer.getAttachButton()?.left,
        )

        strip.performClick()
        harness.idle()
        pickFile("second.png", transparentPng())
        harness.await("the second upload to land in the field") {
            composer.getFieldText()?.count { it == '\n' } == 2
        }

        val field = composer.fieldText.toString()
        println("upload: two files = $field")
        assertEquals(
            "each upload keeps its own line and its own handle",
            "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n" +
                "![screenshot.png|1080x720](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            field,
        )
        harness.renderApp("topic-reply-upload-second")
        println(
            "upload: strip sits on the composer — margin=" +
                (strip.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin +
                " composerHeight=" + composer.height,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anAnswerWithoutAShortHandleIsStillLinkedToTheUpload() {
        site.serveJson(
            "uploads.json",
            harness.fixture("upload-response.json")
                .replace("\"short_url\": \"upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png\",", "")
                // A site-relative answer is what this test is about: it has to be resolved.
                .replace("\"url\": \"https://linux.do/", "\"url\": \"/"),
        )
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", transparentPng())
        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }
        val field = composer.fieldText.toString()
        println("upload: no short handle = $field")
        assertEquals(
            "without a handle the field points at the answer's own URL, resolved against the site",
            "![screenshot.png|1080x720](${site.url("/uploads/default/original/2X/9/9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png")})\n",
            field,
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anImageTheSiteCouldNotMeasureKeepsItsHandleWithoutDimensions() {
        site.serveJson(
            "uploads.json",
            harness.fixture("upload-response.json")
                .replace("\"width\": 1080,", "\"width\": null,")
                .replace("\"height\": 720,", "\"height\": null,"),
        )
        val composer = openComposerAndAttach()
        pickFile("screenshot.png", transparentPng())
        harness.await("the field to hold the uploaded image") {
            !composer.getFieldText().isNullOrEmpty()
        }
        println("upload: unmeasured image = " + composer.fieldText)
        assertEquals(
            "an image whose size the site did not read is embedded without one",
            "![screenshot.png](upload://9d5a2f6f0a1b2c3d4e5f60718293a4b5c6d7e8f9.png)\n",
            composer.fieldText.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** Answers the picker the attach button opened, the way the system would. */
    private fun pickFile(name: String, bytes: ByteArray): Uri {
        val activity = harness.activity
        val requested = shadowOf(activity).nextStartedActivityForResult
            ?: throw AssertionError("the attach button opened no picker")
        assertEquals(
            "the picker takes any file the provider can open",
            Intent.ACTION_OPEN_DOCUMENT,
            requested.intent.action,
        )
        val uri = Uri.parse("content://io.github.baba983.nativedo.test/$name")
        shadowOf(activity.contentResolver).registerInputStream(uri, bytes.inputStream())
        shadowOf(activity).receiveResult(requested.intent, Activity.RESULT_OK, Intent().setData(uri))
        harness.idle()
        return uri
    }

    /** Our own attach strip, by the label it shows while it is idle. */
    private fun attachStrip(): View? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findAttachStrip(content)
    }

    private fun findAttachStrip(view: View): View? {
        if (view is android.widget.TextView && view.text?.startsWith("添加") == true) return view.parent as? View
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findAttachStrip(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** A 1x1 PNG: what the uploader sends is the file's bytes, and any real image would do. */
    private fun transparentPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT,
    )

    private fun composer(): ChatActivityEnterView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
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

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 809L
    }
}
