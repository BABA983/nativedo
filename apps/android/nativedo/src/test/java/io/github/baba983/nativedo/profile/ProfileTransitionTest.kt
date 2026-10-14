package io.github.baba983.nativedo.profile

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.abs

/**
 * The flight's named failure modes, on the production wiring: an avatar that lands somewhere other
 * than the header's own rest position (the end of the flight drops a step), a topic header left
 * hidden after a flight that did not run to its end (the screen below never draws its capsule
 * again), an ordinary opening that passes through the expanded photo's white labels, its
 * avatar-average backdrop and an expanded action row and then resets them at the landing, and a
 * per-frame layout request for the avatar's frame when the flight never moves its bounds. All are
 * invisible to a final-state screenshot and to a wire test - they are continuity contracts between
 * the flight and the resting layout.
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
        destroyShell()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    private fun destroyShell() {
        // Each capture boot must release the previous window's global theme observers.
        runCatching { harness.activity }.getOrNull()?.let { activity ->
            activity.javaClass.getDeclaredMethod("onDestroy").apply { isAccessible = true }.invoke(activity)
        }
    }

    /**
     * The tap on the topic header's capsule flies the capsule's own drawn rectangle onto the
     * resting header's round avatar - not the full-width opened photo. The failures: a flight that
     * starts from an inferred action-bar slot (its first frame misses the capsule), and one that
     * lands expanded, handing an ordinary tap a white-label pill row over a light surface. The
     * close is the same contract backwards: the avatar lands on the capsule's rectangle again, and
     * the resting layout does not step it at the handover.
     */
    @Test
    fun theTopicHeaderTapFliesTheCapsuleOntoTheRoundHeaderWithoutAJump() {
        openTopic()
        harness.await("the source avatar") {
            harness.drawApp()
            findView(harness.activity.window.decorView, ChatAvatarContainer::class.java)!!
                .avatarImageView.imageReceiver.bitmap != null
        }
        val gate = java.util.concurrent.CountDownLatch(1)
        val imageId = "flight-${System.nanoTime()}"
        val response = org.json.JSONObject(harness.fixture("user-profile.json"))
        response.getJSONObject("user").put("avatar_template", "/user_avatar/linux.do/baba/{size}/$imageId.png")
        site.serveImage("user_avatar/linux.do/baba/96/$imageId.png", magentaPng(96, 96))
        site.serveImage("user_avatar/linux.do/baba/720/$imageId.png", magentaPng(720, 720))
        site.serveResponse("u/baba.json") {
            gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json")
                .setBody(response.toString())
        }
        try {
            val capsule = findView(harness.activity.window.decorView, ChatAvatarContainer::class.java)!!
            capsule.translationY = 19f
            capsule.avatarImageView.scaleX = 0.9f
            capsule.avatarImageView.scaleY = 0.9f
            val source = rect(capsule.avatarImageView)
            val profile = tapCapsule()
            freezeFlight(keepListeners = true)
            val flight = field<android.animation.AnimatorSet>(actionBarLayout(), "currentAnimation")
            // The ordinary header transition's own clock; reading it keeps this test off a
            // hardcoded duration (180 ms for type 1, 250 ms for the expanded photo).
            val duration = flight.duration
            val avatar = avatarView(profile) as org.telegram.ui.ProfileActivity.AvatarImageView
            val name = field<org.telegram.ui.ActionBar.SimpleTextView>(profile, "nameTextView")
            val actions = field<View>(profile, "actionsView")
            val avatarContainer = field<View>(profile, "avatarContainer")
            val nameAtRest = name.textColor
            val expandedAtRest = field<Float>(actions, "parentExpanded")

            flight.currentPlayTime = 0
            harness.layoutApp()
            assertRect("the opening starts at the drawn capsule", source, rect(avatar))
            assertTrue("the pending profile already draws the source picture", avatar.imageReceiver.bitmap != null)
            assertTrue(
                "the opening already keeps the profile's own title colour",
                name.textColor == nameAtRest,
            )
            harness.renderAppFrame("header-profile-open-start", 0)

            flight.currentPlayTime = duration / 2
            harness.layoutApp()
            val middle = rect(avatar)
            assertTrue("the middle grew out of the capsule", middle.width() > source.width() + 1f)
            assertTrue(
                "the middle is on its way to the round header, not the full viewport",
                middle.width() < AndroidUtilities.dp(96f),
            )
            // Upstream's ordinary header transition (type 1) never passes through the expanded
            // photo's white labels or an expanded action row while the profile opens, so nothing
            // has to step back when the flight lands.
            val titleMid = name.textColor
            val expandedMid = field<Float>(actions, "parentExpanded")
            harness.renderAppFrame("header-profile-open-mid", 0)

            gate.countDown()
            harness.await("profile answers during flight") { field<Any?>(profile, "profile") != null }
            assertRect("an async answer cannot move the flying picture", middle, rect(avatar))

            // The avatar's own 100 dp frame is already in place, so a flight frame that does not
            // move its bounds must not ask for another layout pass.
            harness.layoutApp()
            profile.setAvatarAnimationProgress(0.6f)
            val reRequested = avatarContainer.isLayoutRequested
            val problems = ArrayList<String>()
            if (titleMid != nameAtRest) {
                problems += "the title blended off the profile's colour (${hex(titleMid)} != ${hex(nameAtRest)})"
            }
            if (expandedMid != expandedAtRest) {
                problems += "the action row expanded away from its rest ($expandedMid != $expandedAtRest)"
            }
            if (reRequested) {
                problems += "a flight frame that did not move the avatar's bounds requested layout"
            }
            assertTrue(
                "the ordinary opening must stay on the profile's own palette and a resting action row: $problems",
                problems.isEmpty(),
            )

            flight.currentPlayTime = duration - 1
            harness.layoutApp()
            val landed = rect(avatar)
            assertEquals(
                "the photo lands the round header's own width",
                AndroidUtilities.dp(96f).toFloat(), landed.width(), 2f,
            )
            assertEquals(
                "the corners stay round",
                call<Int>(profile, "getSmallAvatarRoundRadius"), avatar.roundRadiusForExpand,
            )
            // The last frame the flight draws and the first frame of the return must put the same
            // round corners on the avatar - the continuity is read between the two observed frames,
            // not against a constant, so it also holds for the expanded photo's own return.
            val openEndCorner = avatar.roundRadiusForExpand
            assertFalse("a plain tap does not leave the header expanded", field<Boolean>(profile, "isPulledDown"))
            val landedName = rect(name)
            val backdropAtLanding = backdropColor(profile)

            flight.end()
            harness.layoutApp()
            assertRect("the last flight frame and the resting header agree", landed, rect(avatar))
            assertRect("the title does not jump at the handover", landedName, rect(name))
            assertColorNear(
                "the flight's backdrop does not step to the profile surface at the landing",
                backdropAtLanding, backdropColor(profile),
            )
            repeat(20) {
                harness.drawApp()
                harness.idleClock(17, maxTasks = 2)
            }
            harness.renderAppFrame("header-profile-open-end", 0)

            profile.finishFragment()
            val close = field<android.animation.AnimatorSet?>(actionBarLayout(), "currentAnimation")
            assertTrue("the round header has a return transition", close != null)
            var tasks = 0
            while (!close!!.isStarted && tasks++ < 2000) harness.idle(1)
            close!!.pause()

            // The close is the opening contract backwards: the last frame the flight drew and the
            // first frame of the return hold the same round corners. The ordinary header must not
            // step its round avatar square on the way out.
            close.currentPlayTime = 0
            harness.layoutApp()
            assertEquals(
                "the first close frame keeps the last open frame's round corners",
                openEndCorner, avatar.roundRadiusForExpand,
            )

            // The avatar's own 100 dp frame is unchanged the whole way back, so a reverse frame
            // that does not move its bounds must not ask for another layout pass.
            harness.layoutApp()
            profile.setAvatarAnimationProgress(0.6f)
            assertFalse(
                "an unchanged reverse frame requested layout",
                avatarContainer.isLayoutRequested,
            )

            close.currentPlayTime = 0
            harness.layoutApp()
            copyReviewFrame(harness.renderAppFrame("header-profile-close-start", 0))
            close.currentPlayTime = close.duration / 2
            harness.layoutApp()
            assertEquals(
                "the close keeps the last open frame's round corners",
                openEndCorner, avatar.roundRadiusForExpand,
            )
            assertEquals(
                "the close keeps the profile's own title colour",
                nameAtRest, name.textColor,
            )
            assertEquals(
                "the close never expands the action row",
                expandedAtRest, field<Float>(actions, "parentExpanded"), 0f,
            )
            copyReviewFrame(harness.renderAppFrame("header-profile-close-mid", 0))
            close.currentPlayTime = close.duration - 1
            harness.layoutApp()
            println("profile close: progress=${field<Float>(profile, "avatarAnimationProgress")}, " +
                "running=${field<Boolean>(profile, "openAnimationInProgress")}, rect=${rect(avatar)}")
            assertRect("closing returns to the capsule's actual rect", source, rect(avatar))
            close.end()
            harness.layoutApp()
            assertTrue("the actual close returns to the topic", harness.topFragment() is TopicFragment)
            assertFalse("closing restores the original topic bar", skipDrawChild(capsule.parent as ActionBar))
            harness.renderApp("header-profile-returned-topic")
        } finally {
            gate.countDown()
        }
    }

    /**
     * The site answers while the avatar is still on its way. The answer's model is taken (a landed
     * read is a landed read), but its own views must wait for the landing: the action row is built
     * from the answer's flags and shown, and the rows are rebuilt, both under the flying copy. On
     * the old contract buildActions ran here, so the row popped into the header and the list
     * re-laid out mid-flight. Upstream holds the profile's own notifications until the animation
     * finishes (`setAnimationInProgress`/`onAnimationFinish`, ProfileActivity.java:9738-9783);
     * this pins the same hand-over for the one answer this screen has.
     *
     * <p>The failures: an action row shown (or rows present) while the flight is still running, and
     * an action row that appears only after the landing without the answer's own flags.</p>
     */
    @Test
    fun aProfileAnswerDuringTheFlightWaitsForTheLanding() {
        openTopic()
        val gate = java.util.concurrent.CountDownLatch(1)
        val response = JSONObject(harness.fixture("user-profile.json"))
        response.getJSONObject("user").apply {
            put("can_send_private_message_to_user", true)
            put("can_follow", true)
            put("is_followed", false)
            put("can_mute_user", true)
            put("muted", false)
        }
        site.serveImage("user_avatar/linux.do/baba/96/1.png", magentaPng(96, 96))
        site.serveResponse("u/baba.json") {
            gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(response.toString())
        }
        try {
            val profile = tapCapsule()
            freezeFlight(keepListeners = true)
            val flight = field<android.animation.AnimatorSet>(actionBarLayout(), "currentAnimation")
            flight.currentPlayTime = flight.duration / 2
            harness.layoutApp()

            val actions = field<View>(profile, "actionsView")
            val list = field<RecyclerListView>(profile, "listView")
            val actionVisibilityBefore = actions.visibility
            val rowsBefore = list.adapter!!.itemCount
            assertEquals("the action row starts hidden", View.GONE, actionVisibilityBefore)
            assertFalse("no action before the answer", profile.hasAction(ProfileActionsView.KEY_SHARE))

            // The site answers while the avatar is on its way.
            gate.countDown()
            harness.await("the profile answer to reach the fragment") {
                field<Any?>(profile, "profile") != null
            }
            harness.layoutApp()

            // The released screen is untouched: the answer's views wait outside the flight.
            assertEquals(
                "the loaded action row does not pop in mid-flight",
                actionVisibilityBefore, actions.visibility,
            )
            assertEquals(
                "the loaded rows do not appear mid-flight",
                rowsBefore, list.adapter!!.itemCount,
            )
            assertFalse(
                "no action is offered until the flight lands",
                profile.hasAction(ProfileActionsView.KEY_SHARE),
            )
            copyToUiPolish(harness.renderAppFrame("profile-answer-during-flight", 0), "profile-answer-during-flight")

            // The landing hands the answer over.
            flight.end()
            harness.layoutApp()
            harness.idleClock(200)
            harness.await("the rows after the landing") {
                harness.layoutApp()
                list.adapter!!.itemCount > rowsBefore
            }
            assertEquals(
                "the action row is shown with the landing",
                View.VISIBLE, actions.visibility,
            )
            assertTrue(
                "the answer's own actions land with the flight",
                profile.hasAction(ProfileActionsView.KEY_SHARE),
            )
            // The stock action capsules start their appear animation on their first draw.
            repeat(25) { harness.idleClock(20); harness.drawApp() }
            copyToUiPolish(harness.renderApp("profile-answer-landed"), "profile-answer-landed")
        } finally {
            gate.countDown()
        }
    }

    /**
     * The ordinary opening's mid and end frames on both surfaces, as review captures: the flight
     * carries the profile's own palette at every step (its white-blend failure is even plainer on
     * the night surface), and the copies land under `profile-transition-v2`.
     */
    @Test
    fun theOrdinaryOpeningKeepsTheProfilesPaletteOnBothSurfaces() {
        captureOrdinaryOpening("profile-opening-light", toDark = false)
        captureOrdinaryOpening("profile-opening-dark", toDark = true)
        // Restore the day theme so this capture does not decide a later test's surface.
        applyTheme("Blue", toDark = false)
    }

    private fun captureOrdinaryOpening(prefix: String, toDark: Boolean) {
        openTopic()
        // The theme is applied directly rather than through the day/night notification: an ambient
        // theme left by another phase must not decide what this capture shows.
        applyTheme(if (toDark) "Dark Blue" else "Blue", toDark = toDark)
        val profile = tapCapsule()
        freezeFlight()
        val flight = field<android.animation.AnimatorSet>(actionBarLayout(), "currentAnimation")
        val name = field<org.telegram.ui.ActionBar.SimpleTextView>(profile, "nameTextView")
        val actions = field<View>(profile, "actionsView")
        val restName = name.textColor

        flight.currentPlayTime = flight.duration / 2
        harness.layoutApp()
        val midWidth = rect(avatarView(profile)).width()
        assertTrue(
            "the $prefix middle is between the capsule and the round landing ($midWidth)",
            midWidth > 0f && midWidth < AndroidUtilities.dp(96f),
        )
        assertEquals("the $prefix opening keeps the profile's own title colour", restName, name.textColor)
        assertEquals(
            "the $prefix opening never expands the action row",
            0f, field<Float>(actions, "parentExpanded"), 0f,
        )
        copyReviewFrame(harness.renderAppFrame("$prefix-mid", 0))

        flight.currentPlayTime = flight.duration - 1
        harness.layoutApp()
        assertEquals(
            "the $prefix landing keeps the profile's own title colour",
            restName, name.textColor,
        )
        copyReviewFrame(harness.renderAppFrame("$prefix-end", 0))
        flight.end()
        harness.idleClock(50)
    }

    /** Files the harness's render under the review folder the correction pass asked for. */
    private fun copyReviewFrame(rendered: java.io.File): java.io.File {
        val target = java.io.File(rendered.parentFile.parentFile, "profile-transition-v2/${rendered.name}")
        target.parentFile?.mkdirs()
        rendered.copyTo(target, overwrite = true)
        copyToUiPolish(rendered, rendered.nameWithoutExtension)
        println("opening-capture: wrote ${target.absolutePath} (${target.length()} bytes)")
        return target
    }

    /** The states this pass asked to keep, under `.amp/in/artifacts/ui-polish/`. */
    private fun copyToUiPolish(rendered: java.io.File, name: String): java.io.File {
        val target = java.io.File(rendered.parentFile.parentFile, "ui-polish/$name.png")
        target.parentFile?.mkdirs()
        rendered.copyTo(target, overwrite = true)
        println("ui-polish: wrote ${target.absolutePath} (${target.length()} bytes)")
        return target
    }

    private fun rect(view: View): android.graphics.RectF {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        var sx = view.scaleX
        var sy = view.scaleY
        var parent = view.parent
        while (parent is View) {
            sx *= parent.scaleX
            sy *= parent.scaleY
            parent = parent.parent
        }
        return android.graphics.RectF(location[0].toFloat(), location[1].toFloat(),
            location[0] + view.width * sx, location[1] + view.height * sy)
    }

    private fun assertRect(message: String, expected: android.graphics.RectF, actual: android.graphics.RectF) {
        assertEquals("$message left", expected.left, actual.left, 2f)
        assertEquals("$message top", expected.top, actual.top, 2f)
        assertEquals("$message width", expected.width(), actual.width(), 2f)
        assertEquals("$message height", expected.height(), actual.height(), 2f)
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

    /** Rebuilding views behind another screen must not reuse the old list's seeded offset. */
    @Test
    fun aProfileRebuiltBehindSettingsKeepsItsRoundHeaderOnReturn() {
        val profile = openProfileFromCapsule()
        val before = rect(avatarView(profile))
        harness.present(io.github.baba983.nativedo.settings.SettingsFragment())
        harness.await("settings above the profile") { harness.visibleFragment() is io.github.baba983.nativedo.settings.SettingsFragment }
        switchTheme("Dark Blue", toDark = true)
        (harness.activity as io.github.baba983.nativedo.app.NativeDoActivity).onBackPressed()
        harness.await("the profile returns with its rows") {
            harness.drawApp()
            harness.visibleFragment() === profile && detailOf("信任等级") == "3"
        }
        repeat(20) { harness.idleClock(20); harness.drawApp() }
        assertRect("the rebuilt header stays at its normal round rest", before, rect(avatarView(profile)))
        val captured = harness.renderApp("profile-after-settings-theme")
        val pixels = android.graphics.BitmapFactory.decodeFile(captured.absolutePath)
        assertTrue("the returned page actually draws the selected night surface",
            ColorUtils.calculateLuminance(pixels.getPixel(0, pixels.height / 2)) < 0.4)
        copyToUiPolish(captured, "profile-after-settings-theme")
        harness.assertNoTelegramSideEffects()
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

        freezeFlight(keepListeners = true)
        val layout = actionBarLayout()
        val animation = field<android.animation.AnimatorSet>(layout, "currentAnimation")
        animation.currentPlayTime = 100
        animation.cancel()
        harness.idle()
        harness.idleClock(100)

        assertFalse("the cancelled flight gives the topic header back", skipDrawChild(topicBar))
        assertNull(
            "the profile lets go of the previous screen's bar",
            field<Any?>(profile, "previousTransitionActionBar"),
        )
        profile.finishFragment()
        harness.await("the topic after cancelling and returning") { harness.topFragment() is TopicFragment }
        harness.renderApp("header-profile-cancel-returned-topic")
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
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile to open") { harness.topFragment() is ProfileFragment }
        val profile = harness.topFragment() as ProfileFragment
        // Let the flight finish, then the header rests at its seeded offset.
        harness.idleClock(400)

        val name = field<org.telegram.ui.ActionBar.SimpleTextView>(profile, "nameTextView")
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

    /**
     * The action row the screen rests on is readable - the labels it draws contrast with the pill
     * surface they sit on - and the photo only opens on a deliberate pull. The failure: a header
     * left expanded keeps the labels white against the light day surface, so not one button reads.
     */
    @Test
    fun theActionRowReadsOnTheDaySurfaceAndThePhotoStillOpensOnAPull() {
        val profile = openProfileFromCapsule()

        val dayCapsule = capsuleColor(profile)
        val dayLabel = drawnLabelColor(profile)
        println("profile actions day: capsule=${hex(dayCapsule)} label=${hex(dayLabel)}")
        assertTrue("the day pill surface is light", ColorUtils.calculateLuminance(dayCapsule) > 0.6f)
        assertEquals("the labels were drawn", 255, Color.alpha(dayLabel))
        assertTrue(
            "the day labels contrast with the surface they are drawn on",
            contrast(dayCapsule, dayLabel) > 0.4f,
        )
        harness.renderApp("profile-actions-rest-day")

        // A deliberate pull opens the photo to full width; the row has to stay readable over it.
        invoke(profile, "startAvatarExpand", 1f)
        harness.idleClock(500)
        assertTrue("a deliberate pull expands the header", field<Boolean>(profile, "isPulledDown"))
        val avatar = avatarView(profile) as org.telegram.ui.ProfileActivity.AvatarImageView
        assertEquals("the corners open on the pull", 0, avatar.roundRadiusForExpand)
        harness.layoutApp()
        harness.drawApp()
        println("profile actions opened: capsule=${hex(capsuleColor(profile))} label=${hex(drawnLabelColor(profile))}")
        assertTrue(
            "the opened photo keeps the labels readable",
            contrast(capsuleColor(profile), drawnLabelColor(profile)) > 0.4f,
        )
        harness.renderApp("profile-actions-expanded")

        // The deliberate expanded photo's own return: the corners the close begins from are the
        // square the pull left, held continuously into the first reverse frame - not the header's
        // round corners. The check reads the two observed frames against each other.
        val expandedCorner = avatar.roundRadiusForExpand
        profile.finishFragment()
        val close = field<android.animation.AnimatorSet?>(actionBarLayout(), "currentAnimation")
        assertTrue("the expanded photo has a return transition", close != null)
        var tasks = 0
        while (!close!!.isStarted && tasks++ < 2000) harness.idle(1)
        close.pause()
        close.currentPlayTime = 0
        harness.layoutApp()
        assertEquals(
            "the first close frame keeps the expanded photo's square corners",
            expandedCorner, avatar.roundRadiusForExpand,
        )
        copyReviewFrame(harness.renderAppFrame("header-profile-expanded-close-start", 0))
        close.end()
        harness.idle()
        harness.assertNoTelegramSideEffects()
    }

    /** The same row on the night surface: the labels go light because the surface they sit on does. */
    @Test
    fun theActionRowReadsOnTheNightSurface() {
        val profile = openProfileFromCapsule(switchToNight = true)

        val nightCapsule = capsuleColor(profile)
        val nightLabel = drawnLabelColor(profile)
        println("profile actions night: capsule=${hex(nightCapsule)} label=${hex(nightLabel)}")
        assertTrue("the night pill surface is dark", ColorUtils.calculateLuminance(nightCapsule) < 0.4f)
        assertEquals("the labels were drawn", 255, Color.alpha(nightLabel))
        assertTrue(
            "the night labels contrast with the surface they are drawn on",
            contrast(nightCapsule, nightLabel) > 0.4f,
        )
        harness.renderApp("profile-actions-rest-night")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Someone else's profile, with every action the site permits them, captured in both surfaces.
     *
     * <p>The earlier profile captures opened the reader's own profile (fewer actions: no message, no
     * follow, no mute); the reader's report was about the four actions shown on an <em>other</em>
     * person's profile. So this drives the same production flow the rest of the file uses - the site
     * answers the profile with its own `can_send_private_message_to_user` / `can_follow` /
     * `can_mute_user` flags, nothing is forced - and files the light and dark settles under the
     * correction pass's folder. The labels have to read on the pill surface in both, the same
     * contrast contract the own-profile tests check.</p>
     */
    @Test
    fun capturesTheOtherProfileWithItsFourActionsInLightAndDark() {
        site.serveJson("u/momo199412.json", otherUserProfileJson())
        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }

        // Day: a profile answered with the site's own flags, its row built on the day surface. The
        // theme is applied directly rather than through the day/night notification: an ambient theme
        // left by another test must not decide what this capture shows.
        applyTheme("Blue", toDark = false)
        val day = openOtherProfile()
        assertFourActions(day)
        assertActionRowReads("day", day, daySurface = true)
        renderProfileCapture("profile-other-light")

        // Night: the theme goes first, then a fresh profile is opened under the night resource
        // provider - the way the app opens one after a theme switch, rather than repainting a row
        // that was built for the day.
        applyTheme("Dark Blue", toDark = true)
        val night = openOtherProfile()
        assertFourActions(night)
        assertActionRowReads("night", night, daySurface = false)
        renderProfileCapture("profile-other-dark")

        // Restore the day theme so this capture does not decide a later test's surface.
        applyTheme("Blue", toDark = false)
        harness.assertNoTelegramSideEffects()
    }

    /** Applies a theme straight to [Theme] and lays the shell out, the way the app's switch lands. */
    private fun applyTheme(name: String, toDark: Boolean) {
        Theme.applyTheme(Theme.getTheme(name), false, toDark)
        harness.layoutApp()
        harness.idle()
        assertEquals("the $name theme applied", toDark, Theme.isCurrentThemeDark())
    }

    /** Opens the other user's profile through the production entry and waits for its action row. */
    private fun openOtherProfile(): ProfileFragment {
        harness.present(ProfileFragment.forUser("momo199412", "momo"))
        harness.await("their profile") { harness.visibleFragment() is ProfileFragment }
        harness.await("their action row") { actionsReady() }
        val profile = harness.visibleFragment() as ProfileFragment
        // Presented straight (no capsule flight), so the round avatar is already at rest; the action
        // capsules animate in, and a few frames settle them the way the real screen does.
        harness.idleClock(400)
        repeat(10) {
            harness.drawApp()
            harness.idleClock(20)
        }
        return profile
    }

    /** The four actions the site granted this other user - no permission invented here. */
    private fun assertFourActions(profile: ProfileFragment) {
        assertTrue("private message offered", profile.hasAction(ProfileActionsView.KEY_MESSAGE))
        assertTrue("follow offered", profile.hasAction(ProfileActionsView.KEY_FOLLOW))
        assertTrue("mute offered", profile.hasAction(ProfileActionsView.KEY_NOTIFICATION))
        assertTrue("share offered", profile.hasAction(ProfileActionsView.KEY_SHARE))
        assertFalse("not followed yet", profile.hasAction(ProfileActionsView.KEY_UNFOLLOW))
        assertFalse("bookmarks are not someone else's", profile.hasAction(ProfileActionsView.KEY_BOOKMARKS))
    }

    /**
     * The row is readable on the surface it rests on: a light pill with dark labels by day, a dark
     * pill with light labels by night, and the labels contrast in both.
     */
    private fun assertActionRowReads(which: String, profile: ProfileFragment, daySurface: Boolean) {
        val capsule = capsuleColor(profile)
        val label = drawnLabelColor(profile)
        println("profile-other $which: capsule=${hex(capsule)} label=${hex(label)}")
        assertEquals("the $which labels were drawn", 255, Color.alpha(label))
        assertEquals(
            "the $which pill surface",
            daySurface,
            ColorUtils.calculateLuminance(capsule) > 0.6f,
        )
        assertTrue(
            "the $which labels contrast with the surface they are drawn on",
            contrast(capsule, label) > 0.4f,
        )
    }

    /**
     * Renders the screen the app ships and files the capture under the correction pass's own folder,
     * so the artifact lands at the requested path rather than the harness's default directory.
     */
    private fun renderProfileCapture(name: String) {
        val rendered = harness.renderApp(name)
        val target = java.io.File(rendered.parentFile.parentFile, "profile-correction/$name.png")
        target.parentFile?.mkdirs()
        rendered.copyTo(target, overwrite = true)
        println("profile-other: wrote ${target.absolutePath} (${target.length()} bytes)")
    }

    /** A profile with the real linux.do action fields, shaped like the site's own answer. */
    private fun otherUserProfileJson(): String {
        val profile = JSONObject(harness.fixture("user-profile.json"))
        profile.getJSONObject("user").apply {
            put("username", "momo199412")
            put("name", "momo")
            put("can_send_private_message_to_user", true)
            put("can_follow", true)
            put("is_followed", false)
            put("can_mute_user", true)
            put("muted", false)
        }
        return profile.toString()
    }

    /** True once the visible profile's action row was built (its flags were applied). */
    private fun actionsReady(): Boolean {
        val profile = harness.visibleFragment() as? ProfileFragment ?: return false
        return profile.hasAction(ProfileActionsView.KEY_SHARE) ||
            profile.hasAction(ProfileActionsView.KEY_BOOKMARKS)
    }

    /**
     * A pushed profile is a full-height page, the same edge-to-edge the root tab keeps: the shell
     * reserves no band for the gesture bar, so the screen's own list reaches the window's bottom
     * edge instead of stopping above it.
     */
    @Test
    fun thePushedProfileReachesIntoTheGestureArea() {
        site.serveFixture("u/baba.json", "user-profile.json")
        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile") { harness.visibleFragment() is ProfileFragment }
        harness.layoutApp()
        val profile = harness.visibleFragment() as ProfileFragment
        assertTrue("a pushed profile is edge-to-edge", profile.isSupportEdgeToEdge())

        val root = harness.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                androidx.core.graphics.Insets.of(0, 0, 0, 37),
            )
            .setInsetsIgnoringVisibility(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                androidx.core.graphics.Insets.of(0, 0, 0, 37),
            )
            .build()
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets)
        harness.layoutApp()

        val container = field<View>(actionBarLayout(), "containerView")
        assertEquals("the shell reserves no band at the bottom", 0L, container.paddingBottom.toLong())
        val list = field<View>(profile, "listView")
        val listLocation = IntArray(2)
        list.getLocationInWindow(listLocation)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        assertEquals(
            "the profile's list reaches the window's bottom edge",
            (rootLocation[1] + root.height).toLong(),
            (listLocation[1] + list.height).toLong(),
        )
        harness.renderApp("profile-edge-to-edge")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The header's avatar is never an empty spot: before the site answers - and while its picture
     * is not yet on disk - the reader's own initials stand in (the same stand-in the settings
     * header and a row use). The failure: a pushed profile that names no source flies nothing in,
     * and its picture has not landed, so the round avatar area stays blank while the name and the
     * @handle are already there - exactly the owner's report.
     */
    @Test
    fun theHeaderAvatarShowsAFallbackUntilThePictureLands() {
        destroyShell()
        // Discourse also hands avatar templates whose size lives inside an upload path; the answer
        // names one, and this test serves nothing for it, so the bytes never land.
        val response = JSONObject(harness.fixture("user-profile.json"))
        response.getJSONObject("user").put("avatar_template", "/uploads/avatar/baba/{size}.png")
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("u/baba.json") {
            gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(response.toString())
        }
        site.serveFixture("u/baba/summary.json", "user-summary.json")
        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        // A test that ran before this one may have left a night surface; this capture is the day one.
        applyTheme("Blue", toDark = false)

        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile to open") { harness.visibleFragment() is ProfileFragment }
        val profile = harness.visibleFragment() as ProfileFragment
        harness.layoutApp()

        val avatar = avatarView(profile) as org.telegram.ui.Components.BackupImageView
        try {
            // While the site has not answered, the identity the screen was opened for is what draws.
            assertNotNull(
                "the header draws a stand-in before the site answers",
                avatar.imageReceiver.getDrawable(),
            )
            copyProfileArtifact(harness.renderApp("profile-avatar-fallback-loading"))
        } finally {
            gate.countDown()
        }
        harness.await("the profile's rows") { harness.layoutApp(); detailOf("信任等级") == "3" }
        repeat(4) { harness.drawApp(); harness.idleClock(20) }
        // The picture's bytes are still not on disk, so the stand-in is what the header keeps.
        assertNotNull(
            "an undownloaded avatar still leaves a stand-in in the header, not a blank spot",
            avatar.imageReceiver.getDrawable(),
        )
        assertNull("no picture landed for this template", avatar.imageReceiver.getBitmap())
        copyProfileArtifact(harness.renderApp("profile-avatar-fallback-unloaded"))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The loading shimmer is drawn where the first real row will land - the header's own resting
     * height - not at the list's padded scroll room, which is a full viewport reserved for the
     * expanded photo. The failure: the shimmer floats a screen-quarter below the header, so the
     * loading state shows a large blank band above a chat-list skeleton. The oracle is the empty
     * view's own drawn top (its position plus the padding its rows start from) against the first
     * real row's resting top.
     */
    @Test
    fun theLoadingShimmerStartsAtTheHeadersRestHeight() {
        openTopic()
        val gate = java.util.concurrent.CountDownLatch(1)
        val response = JSONObject(harness.fixture("user-profile.json"))
        site.serveResponse("u/baba.json") {
            gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(response.toString())
        }
        val profile = tapCapsule()
        // The flight lands; the answer is still gated, so the list shows its empty view.
        harness.idleClock(400)
        harness.drawApp()
        val empty = field<View>(profile, "progressView")
        val list = field<View>(profile, "listView")
        val headerExtra = call<Int>(profile, "getHeaderExtraHeight")
        // The shimmer's rows start at its own (private) padding top, not the View padding.
        val contentStart = empty.top + field<Int>(empty, "paddingTop")
        val restingRowTop = list.top + headerExtra
        try {
            assertEquals(
                "the loading shimmer starts where the first row will land, not a screen below",
                restingRowTop.toLong(), contentStart.toLong(),
            )
            copyProfileArtifact(harness.renderApp("profile-loading-shimmer-anchored"))
        } finally {
            // Let the answer land and the screen settle, so this loading capture does not decide a
            // later test's surface (the class's own capture hygiene).
            gate.countDown()
            harness.await("the profile's rows after the gate") { harness.layoutApp(); detailOf("信任等级") == "3" }
            harness.idleClock(200)
            harness.await("the landing") { !field<Boolean>(profile, "openAnimationInProgress") }
            applyTheme("Blue", toDark = false)
        }
    }

    /**
     * A message avatar's tap opens a profile with no shared-element flight
     * (`ChatActivity.didPressUserAvatar` -> `openProfile(user)` keeps `playProfileAnimation == 0`
     * for anyone but the current user, ChatActivity.java:12943-12970), so the profile rides the
     * shell's own default transition. Upstream still holds the profile's own view updates for that
     * whole transition - the hold is `setAnimationInProgress`/`onAnimationFinish`
     * (ProfileActivity.java:9727-9778), not the flight's. The failures: a delayed answer lands while
     * the default transition is running and its rows and action row are built straight into the
     * moving page (a rebuild mid-slide), or the answer is dropped instead of handed over at the end.
     */
    @Test
    fun aProfileAnswerDuringTheDefaultTransitionWaitsForTheTransition() {
        destroyShell()
        val gate = java.util.concurrent.CountDownLatch(1)
        val response = JSONObject(harness.fixture("user-profile.json"))
        site.serveResponse("u/baba.json") {
            gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(response.toString())
        }
        site.serveFixture("u/baba/summary.json", "user-summary.json")
        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }

        // The message avatar's own present call, driven on the production shell, then held at its
        // first frame: the shell's own transition reschedules itself off `System.nanoTime()` and
        // has no AnimatorSet to pause, so clearing its pending runnable stops it where it is (the
        // harness's upstream-queue drain also moves the clock, which would run it out).
        val fragment = ProfileFragment.forUser("baba", "Baba")
        (harness.activity as io.github.baba983.nativedo.app.NativeDoActivity).present(fragment)
        val layout = actionBarLayout()
        setFieldNull(layout, "animationRunnable")
        harness.idle()
        harness.layoutApp()
        assertTrue(
            "the profile rides the shell's own transition",
            field<Boolean>(layout, "transitionAnimationInProgress"),
        )
        assertFalse(
            "the default transition runs no shared-element flight",
            field<Boolean>(fragment, "openAnimationInProgress"),
        )

        val list = field<RecyclerListView>(fragment, "listView")
        val rowsBefore = list.adapter!!.itemCount
        assertFalse("no action before the answer", fragment.hasAction(ProfileActionsView.KEY_SHARE))

        // The site answers while the shell's own transition is still running.
        gate.countDown()
        drainMainLoop("the answer to reach the fragment") { field<Any?>(fragment, "profile") != null }
        assertTrue(
            "the answer landed while the shell's own transition still runs",
            field<Boolean>(layout, "transitionAnimationInProgress"),
        )
        harness.layoutApp()

        // The answer's own views wait for the transition (upstream's setAnimationInProgress).
        val failures = ArrayList<String>()
        if (list.adapter!!.itemCount != rowsBefore) {
            failures += "the answer rebuilt the list mid-transition (${rowsBefore} -> ${list.adapter!!.itemCount})"
        }
        if (fragment.hasAction(ProfileActionsView.KEY_SHARE)) {
            failures += "the answer's action row appeared mid-transition"
        }
        assertTrue("the answer must wait for the shell's own transition: $failures", failures.isEmpty())
        copyProfileArtifact(harness.renderAppFrame("profile-default-transition-answer", 0))

        // The frozen transition lands and hands the answer over.
        invoke(layout, "onAnimationEndCheck", false)
        harness.idleClock(200)
        harness.await("the rows after the transition") {
            harness.layoutApp()
            list.adapter!!.itemCount > rowsBefore
        }
        assertFalse(
            "the default transition ended",
            field<Boolean>(layout, "transitionAnimationInProgress"),
        )
        assertTrue(
            "the answer's own actions land with the transition",
            fragment.hasAction(ProfileActionsView.KEY_SHARE),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** Files a loading capture under the review folder this pass asked for. */
    private fun copyProfileArtifact(rendered: java.io.File): java.io.File {
        val target = java.io.File(rendered.parentFile.parentFile, "home-profile-review/profile/${rendered.name}")
        target.parentFile?.mkdirs()
        rendered.copyTo(target, overwrite = true)
        println("profile-review: wrote ${target.absolutePath} (${target.length()} bytes)")
        return target
    }

    /**
     * Drains the main looper without moving Robolectric's simulated clock, so a screenshot taken
     * mid-way through a clock-driven animation (the shell's own default transition, which has no
     * AnimatorSet to pause) stays at the frame it is observed at.
     */
    private fun drainMainLoop(what: String, condition: () -> Boolean) {
        repeat(4_000) {
            harness.idle()
            if (condition()) {
                harness.idle()
                return
            }
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun openTopic() {
        destroyShell()
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
     * Pauses the animator without letting its end listener finish the transition, so the flight's
     * own layout can be driven frame by frame.
     */
    private fun freezeFlight(keepListeners: Boolean = false) {
        val animation = field<android.animation.AnimatorSet>(actionBarLayout(), "currentAnimation")
        var tasks = 0
        while (!animation.isStarted && tasks++ < 2000) harness.idle(1)
        assertTrue("the delayed start has run before freezing", animation.isStarted)
        if (!keepListeners) animation.removeAllListeners()
        animation.pause()
        harness.idle(2)
    }

    private fun avatarView(profile: ProfileFragment): View =
        field(profile, "avatarImage")

    /**
     * The author's profile opened the way the reader opens it: the topic header's capsule tapped,
     * the flight settled, the rows bound (optionally on the night theme). This is the production
     * path, so the header state the assertions read is the one the tap actually leaves behind.
     */
    private fun openProfileFromCapsule(switchToNight: Boolean = false): ProfileFragment {
        openTopic()
        if (switchToNight) {
            switchTheme("Dark Blue", toDark = true)
        }
        val profile = tapCapsule()
        harness.idleClock(400)
        harness.await("the profile's rows") { harness.layoutApp(); detailOf("信任等级") == "3" }
        assertFalse("the flight landed before the row is read", field<Boolean>(profile, "openAnimationInProgress"))
        repeat(10) {
            harness.drawApp()
            harness.idleClock(20)
        }
        return profile
    }

    private fun detailOf(label: String): String? {
        val list = field<RecyclerListView>(harness.topFragment() as ProfileFragment, "listView")
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index)
            if (cell is org.telegram.ui.Cells.TextDetailCell && cell.valueTextView.text.toString() == label) {
                return cell.textView.text.toString()
            }
        }
        return null
    }

    /** The pill surface the action row draws itself on, as the row last painted it. */
    private fun capsuleColor(profile: ProfileFragment): Int {
        harness.drawApp()
        return field<android.graphics.Paint>(field<View>(profile, "actionsView"), "paint").color
    }

    /** The colour the row last applied to its labels and icons. */
    private fun drawnLabelColor(profile: ProfileFragment): Int {
        harness.drawApp()
        return field<Int>(field<View>(profile, "actionsView"), "lastColorFilterColor")
    }

    /**
     * Read a drawn backdrop pixel, not a mirrored assumption about its fallback theme key.
     * A flight that resets to a different surface at the landing steps here.
     */
    private fun backdropColor(profile: ProfileFragment): Int {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        field<View>(profile, "topView").draw(android.graphics.Canvas(bitmap))
        return bitmap.getPixel(0, 0)
    }

    /** The same colour within a rounding step (a flight landing one channel off the surface is fine). */
    private fun assertColorNear(message: String, expected: Int, actual: Int) {
        val distance = maxOf(
            abs(Color.alpha(expected) - Color.alpha(actual)),
            abs(Color.red(expected) - Color.red(actual)),
            abs(Color.green(expected) - Color.green(actual)),
            abs(Color.blue(expected) - Color.blue(actual)),
        )
        assertTrue("$message (expected ${hex(expected)} got ${hex(actual)})", distance <= 3)
    }

    private fun contrast(first: Int, second: Int): Double =
        abs(ColorUtils.calculateLuminance(first) - ColorUtils.calculateLuminance(second))

    private fun hex(color: Int): String = String.format("0x%08X", color)

    private fun switchTheme(themeName: String, toDark: Boolean) {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
            org.telegram.messenger.NotificationCenter.needSetDayNightTheme,
            Theme.getTheme(themeName), false, null, -1, toDark, null, null, null, true,
        )
        try {
            harness.await("the $themeName theme") {
                harness.idleClock(60)
                Theme.isCurrentThemeDark() == toDark
            }
            harness.layoutApp()
            harness.idle()
        } finally {
            org.telegram.ui.DialogsActivity.switchingTheme = false
        }
    }

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

    /** Nulls a field that lives on a superclass of the runtime class (the shell's layout is anonymous). */
    private fun setFieldNull(target: Any, name: String) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, null)
                return
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

    private fun <T> call(target: Any, name: String): T {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val method = cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
            if (method != null) {
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(target) as T
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
