package io.github.baba983.nativedo.profile

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
import io.github.baba983.nativedo.topic.TopicFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.abs

/**
 * The flight's two named failure modes, on the production wiring: an avatar that lands somewhere
 * other than the header's own rest position (the end of the flight drops a step), and a topic
 * header left hidden after a flight that did not run to its end (the screen below never draws its
 * capsule again). Both are invisible to a final-state screenshot and to a wire test - they are
 * continuity contracts between the flight and the resting layout.
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
internal class ProfileTransitionTest {

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
     * The end of the flight and the layout that takes over from it have to put the avatar in the
     * same place: the animation's last frame is at one rect, and the resting header's own layout
     * (needLayout at the seeded offset) at the same rect. A mismatch is the step the owner sees.
     */
    @Test
    fun theFlightLandsWhereTheRestingHeaderPutsTheAvatar() {
        openTopic()
        val profile = tapCapsule()
        assertTrue("the flight is running", field<Boolean>(profile, "openAnimationInProgress"))
        freezeFlight()
        val avatar = avatarView(profile)

        // The animation's last frame, driven through the same property the animator drives.
        profile.setAvatarAnimationProgress(1f)
        harness.layoutApp()
        val landed = IntArray(2)
        avatar.getLocationOnScreen(landed)

        // The resting layout takes over: the transition is over, the list re-seeds the header.
        field(profile, "openAnimationInProgress", java.lang.Boolean.FALSE)
        invoke(profile, "needLayout", java.lang.Boolean.FALSE)
        harness.layoutApp()
        val rested = IntArray(2)
        avatar.getLocationOnScreen(rested)

        assertTrue(
            "the landed avatar ${landed.toList()} and the resting avatar ${rested.toList()} " +
                "are the same spot (a jump at the end of the flight)",
            abs(landed[0] - rested[0]) <= 1 && abs(landed[1] - rested[1]) <= 1,
        )
    }

    /**
     * A flight that is cancelled (the host tears the transition down) must hand the topic's header
     * back: without the restore the capsule's avatar and title stay hidden until the topic is
     * rebuilt. The observable is the action bar's own skip flag, the one that hides its children.
     */
    @Test
    fun aCancelledFlightDrawsTheTopicsHeaderAgain() {
        openTopic()
        val profile = tapCapsule()

        val topic = harness.fragmentStack().first { it is TopicFragment }
        val topicBar = topic.actionBar
        assertTrue("the topic header is hidden while the avatar flies", skipDrawChild(topicBar))

        val layout = actionBarLayout()
        val animation = field<android.animation.AnimatorSet>(layout, "currentAnimation")
        animation.cancel()
        harness.idle()
        harness.idleClock(100)

        assertFalse("the cancelled flight gives the topic header back", skipDrawChild(topicBar))
        assertNull(
            "the profile lets go of the previous screen's bar",
            field<Any?>(profile, "previousTransitionActionBar"),
        )
    }

    /**
     * The collapsed title's scale is driven by the header offset; the boundary between the pulled
     * state and the scrolled one must not step it. Two adjacent scroll steps a pixel apart at the
     * boundary stay a pixel apart in scale (a branch that recomputed the scale differently would
     * show a jump right where the reader's finger is).
     */
    @Test
    fun theCollapsedTitleLosesItsScaleWithoutAStep() {
        openTopic()
        tapCapsule()
        harness.await("the profile to open") { harness.topFragment() is ProfileFragment }
        val profile = harness.topFragment() as ProfileFragment
        // Let the flight finish, then the header rests at its seeded offset.
        harness.idleClock(400)

        val name = field<View>(profile, "nameTextView")
        val list = listView()
        val scaleAtRest = name.scaleX
        assertEquals("the rest title keeps its 12%", 1.12f, scaleAtRest, 0.01f)

        // One step up the list: the title starts shrinking (the first event only disarms the
        // pull-down and returns the list to rest, upstream's own clamp).
        list.scrollBy(0, 1)
        list.scrollBy(0, AndroidUtilities.dp(1f))
        harness.layoutApp()
        val oneStep = name.scaleX
        assertTrue(
            "one dp of scroll moves the scale by no more than its share " +
                "(rest $scaleAtRest, one step $oneStep)",
            abs(oneStep - scaleAtRest) <= 0.01f,
        )

        // And it keeps moving the same way: no step is more than the share the offset earns.
        var previous = oneStep
        repeat(6) {
            list.scrollBy(0, AndroidUtilities.dp(8f))
            harness.layoutApp()
            val current = name.scaleX
            assertTrue(
                "the scale only shrinks while the list moves up ($previous -> $current)",
                current <= previous + 0.0001f,
            )
            assertTrue(
                "8dp of scroll moves the scale by at most its share ($previous -> $current)",
                previous - current <= 0.02f,
            )
            previous = current
        }
    }

    private fun openTopic() {
        site.serveFixture("u/baba.json", "user-profile.json")
        site.serveFixture("u/baba/summary.json", "user-summary.json")
        site.serveImage("user_avatar/linux.do/baba/96/1.png", magentaPng(96, 96))
        site.serveCooked(TOPIC_ID, "<p>你好。</p>")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A post"))
        harness.await("the topic to load") { listRowCount() == 1 }
    }

    /**
     * Taps the capsule and drains only a couple of tasks: the flight is running then (Robolectric's
     * paused clock makes an unbounded drain run it to its end before the assertions can see it).
     */
    private fun tapCapsule(): ProfileFragment {
        val capsule = findView(harness.activity.window.decorView, ChatAvatarContainer::class.java)
            ?: throw AssertionError("the topic header has no capsule")
        capsule.performClick()
        harness.idle(2)
        return harness.topFragment() as ProfileFragment
    }

    /**
     * Stops the animator without letting its end listener finish the transition, so the flight's
     * own layout can be driven frame by frame.
     */
    private fun freezeFlight() {
        val animation = field<android.animation.AnimatorSet>(actionBarLayout(), "currentAnimation")
        animation.removeAllListeners()
        animation.cancel()
        harness.idle(2)
    }

    private fun avatarView(profile: ProfileFragment): View =
        field(profile, "avatarImage")

    private fun listView(): RecyclerListView =
        field(harness.topFragment() as ProfileFragment, "listView")

    private fun actionBarLayout(): Any {
        val f = NativeDoActivityClass.getDeclaredField("actionBarLayout")
        f.isAccessible = true
        return f.get(harness.activity)
    }

    private fun skipDrawChild(actionBar: ActionBar): Boolean {
        val f = ActionBar::class.java.getDeclaredField("doNotDrawChild")
        f.isAccessible = true
        return f.get(actionBar) as Boolean
    }

    private fun <T> field(target: Any, name: String, set: T? = null): T {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                if (set != null) {
                    @Suppress("UNCHECKED_CAST")
                    f.set(target, set as Any)
                }
                @Suppress("UNCHECKED_CAST")
                return f.get(target) as T
            } catch (ignored: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun <T : View> findView(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) {
            @Suppress("UNCHECKED_CAST")
            return root as T
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), type)?.let { return it }
            }
        }
        return null
    }

    private fun invoke(target: Any, name: String, vararg args: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val method = cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
            if (method != null) {
                method.isAccessible = true
                method.invoke(target, *args)
                return
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException(name)
    }

    private fun listRowCount(): Int =
        findView(harness.activity.window.decorView, RecyclerListView::class.java)?.adapter?.itemCount ?: 0

    private fun magentaPng(width: Int, height: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private companion object {
        const val TOPIC_ID = 4242L
        val NativeDoActivityClass: Class<*> =
            Class.forName("io.github.baba983.nativedo.app.NativeDoActivity")
    }
}
