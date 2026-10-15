package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.profile.ProfileFragment
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Components.AnimatedFileDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Animated pictures, end to end.
 *
 * <p>The failure this pins: a GIF the module fetched is handed to upstream's loader as a plain
 * still picture, so it draws its first frame and never plays. Upstream decides still-versus-animation
 * from the {@code ImageLocation} it is given (its own {@code ImageLoader} builds an
 * {@link AnimatedFileDrawable} only for a location marked as an animation), so the bridge has to
 * say so - and a site's {@code animated_avatar} metadata is the only honest place to learn it, a
 * static {@code avatar_template} is not an animation merely because its file is on disk.</p>
 *
 * <p>The other half of the contract is the negative: an ordinary picture must stay a still bitmap.
 * Both halves are asserted on what upstream's own loader produced (its animation drawable versus a
 * decoded bitmap), so a bridge that marks everything animated fails just as loudly as one that
 * animates nothing. The animated natives are stubbed on the JVM, so the animation's frames cannot be
 * compared here; what is compared is the loader's decision, which is what was wrong.</p>
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
internal class GifMediaTest {

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
     * A picture whose source is a GIF animates in the row, and a picture whose source is a PNG does
     * not: the block upstream's own list draws must be handed an animation for the first and a
     * still for the second.
     */
    @Test
    fun aGifInThePostBodyAnimatesWhileAStillPictureStaysStill() {
        site.serveImage(GIF_PATH, animatedGif(), contentType = "image/gif")
        site.serveImage(PNG_PATH, solidPng(320, 180, Color.MAGENTA))
        site.serveImage("uploads/avatar.gif", animatedGif(), contentType = "image/gif")
        site.serveTopic(TOPIC_ID) { post ->
            post.put("animated_avatar", "/uploads/avatar.gif")
            post.put("cooked", """
                <p>动图 <img src="$GIF_PATH" width="320" height="180" alt="anim"> 之后</p>
                <p>静图 <img src="$PNG_PATH" width="320" height="180" alt="still"> 之后</p>
            """.trimIndent())
        }
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "GIF body"))
        harness.await("the post") { harness.drawApp(); laidOutCells().isNotEmpty() }
        harness.await("both pictures to be fetched") {
            site.cookieHeaderFor(GIF_PATH) != null && site.cookieHeaderFor(PNG_PATH) != null
        }
        val message = laidOutCells().first().messageObject
        assertNotNull("the post was drawn", message)
        harness.drawApp()
        harness.await("the GIF block to be rebound as an animation and the still as a bitmap") {
            harness.idleUpstreamQueues()
            val receivers = photoReceivers(message!!)
            receivers.size == 2 &&
                receivers[0].imageDrawable is AnimatedFileDrawable &&
                receivers[1].bitmap != null
        }
        val receivers = photoReceivers(message!!)
        assertTrue(
            "a GIF post image is handed to upstream's loader as an animation",
            receivers[0].imageDrawable is AnimatedFileDrawable,
        )
        assertTrue(
            "a plain post image stays a decoded still",
            receivers[1].bitmap != null,
        )
        assertFalse(
            "a still picture is never turned into an animation",
            receivers[1].imageDrawable is AnimatedFileDrawable,
        )
        harness.await("the real post author's avatar to use the site's animation") {
            harness.drawApp()
            harness.idleUpstreamQueues()
            laidOutCells().first().avatarImage.imageDrawable is AnimatedFileDrawable
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A user the site marks with {@code animated_avatar} draws from that animation, while a user
     * with only a static template draws a still: the avatar binding call site must hand the loader
     * the animation the site advertised, not the template it also carries.
     */
    @Test
    fun mentionCandidatesUseAnimatedMetadataWhileAPlainAvatarStaysStill() {
        // No GIF suffix: animated_avatar, not a guessed filename, selects the animation.
        site.serveImage("uploads/animated-avatar", animatedGif(), "image/gif")
        site.serveImage("uploads/plain-avatar.png", solidPng(96, 96, Color.MAGENTA))
        site.serveCooked(TOPIC_ID, "<p>A post with mention candidates.</p>")
        site.serveJson("u/search/users", """{"users":[
            {"username":"gizmo","name":"Gizmo","avatar_template":"/uploads/unused-still.png","animated_avatar":"/uploads/animated-avatar"},
            {"username":"gilbert","name":"Gilbert","avatar_template":"/uploads/plain-avatar.png"}
        ],"groups":[]}""")
        harness.bootApp()
        harness.await("the feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Mentions"))
        harness.await("the post") { rowCount() == 1 }
        val field = viewTree(harness.visibleFragment()!!.fragmentView)
            .filterIsInstance<org.telegram.ui.Components.ChatActivityEnterView>().single().editField!!
        field.setText("hello @gi")
        field.setSelection(field.length())
        harness.idleClock(400)
        harness.await("animated and static mention avatars") {
            harness.drawApp()
            harness.idleUpstreamQueues()
            val receivers = viewTree(harness.visibleFragment()!!.fragmentView)
                .filterIsInstance<org.telegram.ui.Cells.MentionCell>()
                .flatMap { viewTree(it).filterIsInstance<BackupImageView>() }
                .map { it.imageReceiver }.toList()
            receivers.size == 2 && receivers.count { it.imageDrawable is AnimatedFileDrawable } == 1
                && receivers.count { it.bitmap != null } == 1
        }
        assertNotNull("the animated avatar uses the authenticated downloader", site.cookieHeaderFor("uploads/animated-avatar"))
        assertFalse("the sized still does not replace the animation", site.requestPaths.contains("uploads/unused-still.png"))
        harness.assertNoTelegramSideEffects()
    }

    /** A profile response must not silently fall back to its static template either. */
    @Test
    fun theProfileHeaderUsesTheAnimatedAvatarFromTheSite() {
        val response = org.json.JSONObject(harness.fixture("user-profile.json"))
        response.getJSONObject("user").put("animated_avatar", "/uploads/profile-avatar.gif")
        site.serveJson("u/baba.json", response.toString())
        site.serveImage("uploads/profile-avatar.gif", animatedGif(), "image/gif")
        harness.bootApp()
        harness.await("the feed") { rowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile's animated avatar") {
            harness.drawApp()
            harness.idleUpstreamQueues()
            viewTree(harness.activity.window.decorView)
                .filterIsInstance<org.telegram.ui.ProfileActivity.AvatarImageView>()
                .any { it.imageReceiver.imageDrawable is AnimatedFileDrawable }
        }
        assertNotNull("the avatar uses the session's HTTP client", site.cookieHeaderFor("uploads/profile-avatar.gif"))
        harness.assertNoTelegramSideEffects()
    }

    /** The reaction-users sheet must not flatten an animated reactor to a cached bitmap. */
    @Test
    fun aReactionUsersAvatarKeepsItsAnimation() {
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>A reacted post.</p>")
            post.put("reaction_users_count", 1)
            post.put("reactions", org.json.JSONArray("""[{"id":"heart","count":1}]"""))
        }
        site.serveJson("discourse-reactions/posts/1001/reactions-users.json", """
            {"reaction_users":[{"id":"heart","count":1,"users":[
                {"username":"gizmo","name":"Gizmo","avatar_template":"/uploads/reactor.png","animated_avatar":"/uploads/reactor.gif"}
            ]}]}
        """.trimIndent())
        site.serveImage("uploads/reactor.gif", animatedGif(), "image/gif")
        harness.bootApp()
        harness.await("the feed") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
        harness.await("the reacted post") { harness.layoutApp(); laidOutCells().isNotEmpty() }
        val cell = laidOutCells().first()
        cell.delegate.didPressReaction(cell, cell.messageObject.messageOwner.reactions.results.first(), true, 0f, 0f)
        harness.await("the reactor's animation") {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            harness.idleUpstreamQueues()
            dialog?.window?.decorView?.let { root ->
                viewTree(root).filterIsInstance<org.telegram.ui.Cells.UserCell>()
                    .any { it.avatarImageView.imageReceiver.imageDrawable is AnimatedFileDrawable }
            } == true
        }
        org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss()
        assertNotNull("the reactor's animation uses the session", site.cookieHeaderFor("uploads/reactor.gif"))
        harness.assertNoTelegramSideEffects()
    }

    private fun viewTree(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(viewTree(view.getChildAt(index)))
    }

    /** The block receivers of a post's media, in body order. */
    private fun photoReceivers(message: MessageObject): List<ImageReceiver> {
        val layout = message.richLayout ?: return emptyList()
        val blocks = ArrayList<TL_iv.PageBlock>()
        layout.collectMediaBlocks(blocks)
        return blocks.mapNotNull { layout.findMediaImageReceiver(it, IntArray(2)) }
    }

    /**
     * A real, tiny two-frame GIF (red then blue, 8x8, looped). A PNG's bytes would prove only that
     * the file arrived; the source here really is an animation, which is what the metadata says.
     */
    private fun animatedGif(): ByteArray = Base64.getDecoder().decode(ANIMATED_GIF_BASE64)

    private fun solidPng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<org.telegram.ui.Cells.ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount).map { list.getChildAt(it) }
            .filterIsInstance<org.telegram.ui.Cells.ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 404L
        const val GIF_PATH = "uploads/loop.gif"
        const val PNG_PATH = "uploads/still.png"

        /** `magick -size 8x8 xc:red -delay 20 -size 8x8 xc:blue -delay 20 -loop 0 anim.gif` */
        const val ANIMATED_GIF_BASE64 =
            "R0lGODlhCAAIAPAAAP8AAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAAAAAACwAAAAACAAIAAAC" +
                "B4SPqcvtXQAAIfkEABQAAAAsAAAAAAgACACAAAD/AAAAAgeEj6nL7V0AADs="
    }
}
