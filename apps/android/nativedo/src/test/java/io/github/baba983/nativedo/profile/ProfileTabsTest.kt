package io.github.baba983.nativedo.profile

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ViewPagerFixed

/**
 * The profile tab's three complaints, pinned by failure mode:
 *
 * <p>1. the reader's own tab must wear upstream's QR button top-left - and a pushed profile must
 * keep the back arrow - and the button must open a QR screen whose link is the profile's own site
 * URL;</p>
 * <p>2. the tab strip's indicator (position and text colours) must follow the pager through a
 * finger swipe, and the swipe must commit the tab the reader landed on - the page's stream is
 * asked for, the strip ends on the new tab;</p>
 * <p>3. the media header must turn on exactly when the tab area pins under the action bar (and
 * off when it scrolls back down), with the animation between the two states actually running.</p>
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
internal class ProfileTabsTest {

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
     * The reader's own tab: the top-left button is the QR glyph (upstream's
     * ProfileActivity:2480-2491 with hasMainTabs) and it opens the QR screen for
     * `https://<site>/u/<username>`; another reader's profile keeps the back arrow.
     */
    @Test
    fun theOwnTabWearsTheQrButtonAndItOpensTheProfilesLink() {
        site.serveFixture("u/baba.json", "user-profile.json")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }

        val own = ProfileFragment.forRoot("baba", "Baba")
        harness.present(own)
        harness.await("the profile") { site.requestPaths.contains("u/baba.json") }
        harness.await("the profile's rows") { harness.layoutApp(); detailValue("信任等级") == "3" }

        val ownBar = field<org.telegram.ui.ActionBar.ActionBar>(own, "actionBar")
        val ownButton = ownBar.backButtonImageView
        assertNotNull("the own tab has no top-left button", ownButton)
        assertEquals(
            "the own tab's top-left is the QR button",
            LocaleController.getString(R.string.QrCode),
            ownButton.contentDescription,
        )

        ownBar.getBackButton().performClick()
        harness.idle()
        harness.await("the QR screen") { harness.topFragment() is ProfileQrFragment }
        val qr = harness.topFragment() as ProfileQrFragment
        assertEquals(
            "the QR link is the profile's own site URL",
            site.baseUrl.trimEnd('/') + "/u/baba",
            qr.getLink(),
        )
        harness.layoutApp()
        harness.drawApp()

        // The card really encoded something: the QR content bitmap exists once the view was sized.
        val qrView = findView(field<View?>(qr, "fragmentView"), ProfileQrView::class.java)
            ?: throw AssertionError("the QR screen has no card")
        assertNotNull("the QR card never encoded its link", field<Bitmap?>(qrView, "contentBitmap"))
        assertEquals("the card encodes the profile link", qr.getLink(), qrView.getLink())
        harness.renderApp("profile-qr")

        // A pushed profile is a step, not the reader's tab: back arrow, no QR.
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the pushed profile") { harness.topFragment() is ProfileFragment && harness.topFragment() !== qr }
        val pushed = harness.topFragment() as ProfileFragment
        val pushedBar = field<org.telegram.ui.ActionBar.ActionBar>(pushed, "actionBar")
        assertEquals(
            "a pushed profile keeps the back arrow",
            LocaleController.getString(R.string.AccDescrGoBack),
            pushedBar.backButtonImageView.contentDescription,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The strip's indicator follows the pager through a finger swipe: mid-drag it sits between the
     * two tabs (not parked on the first), and on release the strip and the screen agree on the tab
     * the reader landed on - the replies tab's own `filter=5` is asked for.
     */
    @Test
    fun theStripFollowsThePagerThroughASwipeAndCommitsOnRelease() {
        site.serveJsonFor("user_actions.json") { path ->
            val filter = path.substringAfter("filter=").substringBefore('&')
            val offset = path.substringAfter("offset=").substringBefore('&').toIntOrNull() ?: 0
            actionsPage(filter, offset)
        }
        site.serveFixture("u/baba.json", "user-profile.json")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the first tab's filter") {
            site.queriesFor("user_actions.json").any { it.contains("filter=4") }
        }

        val profile = harness.visibleFragment() as ProfileFragment
        val media = awaitMedia(profile)
        val strip = media.tabStrip
        val pager = media.pager
        val list = listView() ?: throw AssertionError("no profile list")
        // The tab area is the list's last row: it has to be on screen (bound and measured) before
        // a finger can touch its pager.
        list.scrollToPosition(field<Int>(profile, "mediaRow"))
        harness.layoutApp()
        harness.idleClock(100)
        assertTrue("the pager is laid out", pager.measuredWidth > 0 && pager.measuredHeight > 0)
        assertEquals("the strip starts on 话题", 0, strip.getCurrentTabId())

        val tabs = strip.getChildAt(0) as ViewGroup
        val firstTab = tabs.getChildAt(0)
        val secondTab = tabs.getChildAt(1)
        val indicatorAtRest = field<Int>(strip, "indicatorX")
        assertTrue(
            "the indicator starts inside the first tab",
            indicatorAtRest in firstTab.left..(firstTab.right + 1),
        )

        // A finger drag forward, held before release.
        swipe(pager, travelFraction = 0.8f, release = false)
        val indicatorMidDrag = field<Int>(strip, "indicatorX")
        val firstCenter = (firstTab.left + firstTab.right) / 2f
        val secondCenter = (secondTab.left + secondTab.right) / 2f
        println("profile strip: indicator at rest=$indicatorAtRest mid=$indicatorMidDrag centers=$firstCenter,$secondCenter")
        assertTrue(
            "mid-drag the indicator left the first tab",
            indicatorMidDrag > firstCenter + 1,
        )
        assertTrue(
            "mid-drag the indicator is still on its way to the second tab",
            indicatorMidDrag < secondCenter,
        )
        // The incoming tab's text has started interpolating away from the unselected colour.
        val unselected = Theme.getColor(Theme.key_profile_tabText)
        assertTrue(
            "the incoming tab's text is interpolating",
            (secondTab as android.widget.TextView).currentTextColor != unselected,
        )

        // Release: the pager settles on the second tab and the strip commits with it.
        release(pager)
        harness.idleClock(600)
        harness.await("the replies tab's own stream") {
            site.queriesFor("user_actions.json").any { it.contains("filter=5") && it.contains("offset=0") }
        }
        assertEquals("the pager landed on the second tab", 1, pager.getCurrentPosition())
        assertEquals("the strip followed the pager", 1, strip.getCurrentTabId())
        assertEquals("the screen's own tab", 1, media.getCurrentTab())
        val settled = field<Int>(strip, "indicatorX")
        assertTrue(
            "the indicator settled inside the second tab",
            settled in (secondTab.left - 1)..(secondTab.right + 1),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The pin boundary: while the tab area's row is under the action bar the media header is on
     * (the counter line up, the strip open, the title in its player colour), and scrolling the
     * list back down takes it off again - through the animation, not by snapping.
     */
    @Test
    fun theMediaHeaderTogglesAtThePinBoundaryAndReverses() {
        site.serveFixture("u/baba.json", "user-profile.json")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile") { site.requestPaths.contains("u/baba.json") }
        val profile = harness.visibleFragment() as ProfileFragment
        val media = awaitMedia(profile)
        val list = listView() ?: throw AssertionError("no profile list")
        val strip = media.tabStrip
        assertFalse("the header starts off", field<Boolean>(profile, "mediaHeaderVisible"))
        val nameView = field<org.telegram.ui.ActionBar.SimpleTextView>(profile, "nameTextView")
        val restNameColor = nameView.getTextColor()
        val closedPadding = AndroidUtilities.dp(6f)
        assertEquals("the strip starts closed", closedPadding, strip.paddingLeft)

        // Scroll to the tab area: its row reaches the list's top and the header turns on.
        list.scrollToPosition(field<Int>(profile, "mediaRow"))
        layoutWithoutIdle()
        assertTrue("the tab area pinned to the top", media.top <= 0)
        assertTrue("pinned, so the media header is on", field<Boolean>(profile, "mediaHeaderVisible"))
        // The state does not snap: it is driven by an animator (ProfileActivity's 150 ms header set).
        val pinAnimator = field<android.animation.AnimatorSet?>(profile, "headerAnimatorSet")
        assertNotNull("the pin starts the media header animation", pinAnimator)
        assertTrue("the media header animation runs", pinAnimator!!.isRunning)
        harness.idleClock(300)
        // The counter's text arrives with the animator's first frame; this is the layout pass that
        // measures it (the harness draws only what was laid out since the last pass).
        harness.layoutApp()
        assertEquals(
            "the header animation reached its end",
            1.0f,
            field<Float>(profile, "mediaHeaderAnimationProgress"),
            0.001f,
        )
        val mediaCounter = field<android.widget.TextView>(profile, "mediaCounterTextView")
        val onlineLine = field<View>(profile, "onlineTextView")
        assertTrue("the strip opened", strip.paddingLeft < closedPadding)
        assertEquals("the counter took the status line", 1.0f, mediaCounter.alpha, 0.001f)
        assertEquals("the counter names the tab", "话题", mediaCounter.text.toString())
        assertEquals("the status line stepped aside", 0.0f, onlineLine.alpha, 0.001f)
        assertEquals(
            "the title took the media header's colour",
            Theme.getColor(Theme.key_player_actionBarTitle),
            nameView.getTextColor(),
        )
        // The harness draws only what a layout pass measured; the counter's width arrives with its
        // text (set while the header animates), and no pass reaches it here. Measure it the way a
        // pass would, so the pinned frame shows the subtitle line it is responsible for.
        if (mediaCounter.measuredWidth == 0) {
            mediaCounter.forceLayout()
            mediaCounter.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(mediaCounter.measuredHeight.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            )
            mediaCounter.layout(
                mediaCounter.left, mediaCounter.top,
                mediaCounter.left + mediaCounter.measuredWidth, mediaCounter.top + mediaCounter.measuredHeight,
            )
        }
        harness.renderApp("profile-media-header-pinned")

        // Scrolling back down: the state reverses through the same animation. The scroll stops on a
        // row of the stats (above the tab area, inside the rows - not at the list's very top, which
        // overshoots into the header's pull-down stretch).
        list.scrollToPosition(field<Int>(profile, "mediaRow") - 4)
        layoutWithoutIdle()
        assertFalse("scrolled away, so the header is off again", field<Boolean>(profile, "mediaHeaderVisible"))
        val reverseAnimator = field<android.animation.AnimatorSet?>(profile, "headerAnimatorSet")
        assertNotNull("the reversal starts the media header animation", reverseAnimator)
        assertTrue("the reversal animation runs", reverseAnimator!!.isRunning)
        harness.idleClock(300)
        assertEquals(0.0f, field<Float>(profile, "mediaHeaderAnimationProgress"), 0.001f)
        assertEquals("the strip closed again", closedPadding, strip.paddingLeft)
        assertEquals("the status line is back", 1.0f, onlineLine.alpha, 0.001f)
        assertEquals(0.0f, mediaCounter.alpha, 0.001f)
        assertEquals("the title's own colour is back", restNameColor, nameView.getTextColor())
        harness.renderApp("profile-media-header-rest")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------------------------------

    /** The profile's tab area, built and measured once the profile answered. */
    private fun awaitMedia(profile: ProfileFragment): ProfileSharedMediaView {
        var media: ProfileSharedMediaView? = null
        harness.await("the tab area") {
            harness.layoutApp()
            media = field<ProfileSharedMediaView?>(profile, "mediaView")
            media != null
        }
        return media!!
    }

    private fun listRowCount(): Int = listView()?.adapter?.itemCount ?: 0

    /**
     * Lays the activity's content out without draining the looper, so a state change made by a
     * scroll listener (the media header's animator) can be observed *before* its animation runs -
     * the harness's own [UpstreamUiHarness.layoutApp] idles, which on this frame clock can finish
     * a 150 ms animator in the same call.
     */
    private fun layoutWithoutIdle() {
        val contentView = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val widthSpec = View.MeasureSpec.makeMeasureSpec(harness.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(harness.height, View.MeasureSpec.AT_MOST)
        contentView.measure(widthSpec, heightSpec)
        contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)
    }

    private fun listView(): RecyclerListView? =
        io.github.baba983.nativedo.harness.contentListOf(harness.activity)

    private fun detailValue(label: String): String? {
        val list = listView() ?: return null
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index)
            if (cell is org.telegram.ui.Cells.TextDetailCell && cell.valueTextView.text.toString() == label) {
                return cell.textView.text.toString()
            }
        }
        return null
    }

    /**
     * Drags the pager [travelFraction] of its width to the left without releasing. The events go
     * through the pager's own touch path, so the strip is driven by the same state a finger moves.
     */
    private fun swipe(pager: ViewPagerFixed, travelFraction: Float, release: Boolean) {
        val width = pager.measuredWidth
        val y = pager.measuredHeight * 0.5f
        val startX = width * 0.9f
        pager.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startX, y, 0))
        var time = 0L
        val steps = 16
        for (step in 1..steps) {
            time += 16
            val x = startX - width * travelFraction * (step.toFloat() / steps)
            pager.onTouchEvent(MotionEvent.obtain(0, time, MotionEvent.ACTION_MOVE, x, y, 0))
            harness.idle()
        }
    }

    private fun release(pager: ViewPagerFixed) {
        val y = pager.measuredHeight * 0.5f
        val x = pager.measuredWidth * 0.2f
        pager.onTouchEvent(MotionEvent.obtain(0, 400, MotionEvent.ACTION_UP, x, y, 0))
        harness.idle()
    }

    /** One `user_actions.json` page: twenty rows on the first page, five on the second. */
    private fun actionsPage(filter: String, offset: Int): String {
        val size = if (offset == 0) 20 else 5
        val actions = org.json.JSONArray()
        for (i in 0 until size) {
            actions.put(
                JSONObject()
                    .put("id", offset + i + 100)
                    .put("post_id", offset + i + 1000)
                    .put("topic_id", 3000 + offset + i)
                    .put("title", "filter $filter row ${offset + i}")
                    .put("excerpt", "body")
                    .put("username", "baba")
                    .put("created_at", "2026-05-0${(offset + i) % 9 + 1}T00:00:00.000Z")
                    .put("post_number", i + 1),
            )
        }
        return JSONObject().put("user_actions", actions).toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val declared = type.getDeclaredField(name)
                declared.isAccessible = true
                return declared.get(target) as T
            } catch (ignored: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun <T : View> findView(root: View?, type: Class<T>): T? {
        if (root == null) {
            return null
        }
        if (type.isInstance(root)) {
            return type.cast(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findView(root.getChildAt(index), type)?.let { return it }
            }
        }
        return null
    }
}
