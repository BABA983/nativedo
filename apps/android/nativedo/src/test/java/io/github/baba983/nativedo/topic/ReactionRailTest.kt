package io.github.baba983.nativedo.topic

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.latestItemOptionsMenu
import io.github.baba983.nativedo.harness.menuItemLabels
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
import org.json.JSONArray
import org.json.JSONObject
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
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MediaDataController
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.ChatScrimPopupContainerLayout
import org.telegram.ui.Components.ReactionsContainerLayout
import org.telegram.ui.Components.RecyclerListView

/**
 * The reactions rail on a post's long-press menu: upstream's own picker sits directly at the top of
 * the menu, so the reader swipes every reaction the site permits and a tap sends exactly that id -
 * one step, not a menu item that opens a sheet.
 *
 * <p>The failure this pins: the rail must show the whole server list (linux.do answers ten), not a
 * guessed subset, and the id a tile stands for must survive the round trip to the site. A rail that
 * only showed what fit on screen, or that mapped a glyph to the wrong id, would react with a
 * reaction the reader never picked.</p>
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
internal class ReactionRailTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    /**
     * Ids and picture paths of this run's own. Upstream's reaction table and the file cache outlive
     * a test class, so a fixed id would already carry an entry (and a missing file) from an earlier
     * test; a per-run token keeps this test's custom emoji its own.
     */
    private val runToken = System.nanoTime().toString().takeLast(4)

    @Before
    fun setUp() {
        site.start()
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>A post worth reacting to.</p>")
            post.put("post_number", 1)
        }
        val topic = JSONObject(harness.fixture("topic-detail.json"))
        val stream = topic.getJSONObject("post_stream")
        stream.put("stream", org.json.JSONArray().put(stream.getJSONArray("posts").getJSONObject(0).getLong("id")))
        topic.put("posts_count", 1)
        topic.put("highest_post_number", 1)
        topic.put("valid_reactions", org.json.JSONArray(TEN_REACTIONS))
        site.serveJson("t/$TOPIC_ID.json", topic.toString())
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The menu leads with the real rail; all ten server reactions are there, the tenth is reachable
     * by scrolling the rail itself, and tapping it toggles exactly the tenth id at the site.
     */
    @Test
    fun longPressHostsTheRailDirectlyAndTheTenthReactionIsReachableAndSent() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Rail"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        // The rail replaced the extra step: the menu no longer offers a "回应" row.
        val rows = latestItemOptionsMenu(harness.activity)
        val labels = menuItemLabels(rows)
        println("rail: menu items = $labels")
        assertFalse("no separate 回应 item - the rail is the reaction entry", labels.any { it.contains("回应") })
        assertTrue("the ordinary actions remain", labels.any { it.contains("回复") })

        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup window")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no reactions rail")
        harness.await("all ten server reactions in the rail") { rail.allReactionsList.size == 10 }

        val list = rail.recyclerListView
        assertEquals("a tile per permitted reaction, no invented ones", 10, list.adapter!!.itemCount)

        // Lay the real popup out the way its own window does, so the rail takes the width and, above
        // all, the height of its production layout params - the test does not size the list itself.
        layoutPopup(root)

        // The rail is a single line of chips; the popup's own ordinary rows are the yardstick, so the
        // bound is not a mirrored constant. A doubly-converted dp height stands several rows tall.
        val ordinaryRows = latestItemOptionsMenu(harness.activity)
        val rowHeight = ordinaryRows.map { it.height }.filter { it > 0 }.minOrNull()
            ?: throw AssertionError("the menu drew no ordinary rows to bound the rail's height")
        println("rail: height=${rail.height} ordinaryRow=$rowHeight")
        assertTrue(
            "the rail is at most two ordinary menu rows tall (${rail.height}px vs ${2 * rowHeight}px)",
            rail.height in 1..(2 * rowHeight),
        )

        // Sweep the rail to its end and prove the tenth tile is really there, not clipped away.
        list.scrollToPosition(9)
        harness.idle()
        layoutPopup(root)
        var tenth: View? = null
        for (index in 0 until list.childCount) {
            if (list.getChildAdapterPosition(list.getChildAt(index)) == 9) {
                tenth = list.getChildAt(index)
                break
            }
        }
        println(
            "rail: width=${list.width} height=${list.height} children=${list.childCount} " +
                "positions=" + (0 until list.childCount).map { list.getChildAdapterPosition(list.getChildAt(it)) },
        )
        val tile = tenth ?: throw AssertionError("the tenth reaction could not be scrolled into the rail")
        println("rail: tenth id shown = " + rail.allReactionsList[9].emojicon)

        list.clickItem(tile, 9)
        harness.await("the tenth reaction to reach the site") {
            site.recordedRequests.any {
                it.contains("custom-reactions/${TEN_REACTIONS[9]}/toggle.json")
            }
        }
        val toggle = site.recordedRequests.first { it.contains("custom-reactions/") && it.startsWith("PUT") }
        println("rail: toggle request = $toggle")
        assertTrue(
            "the tapped id - the tenth - is the one sent, not a neighbour: $toggle",
            toggle.contains("/${TEN_REACTIONS[9]}/toggle.json"),
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The rail is built before the menu is shown, and a late picture never rebuilds a closed menu.
     *
     * <p>The failure this pins has two halves. A rail whose list waits for the site's picture is a
     * blank first frame - the reader presses a post and sees an empty bubble that fills in a moment
     * later. And a rail that is rebuilt when a fetch that outlived it lands keeps a closed menu's
     * view and its decoded pictures alive behind a popup nobody sees.</p>
     */
    @Test
    fun theRailIsCompleteBeforeTheMenuShowsAndALatePictureNeverRebuildsAClosedMenu() {
        // The one permitted reaction's picture is answered slowly, so there is a real window in which
        // the menu can close before the fetched icon arrives.
        val iconPath = "images/emoji/heart.png"
        val latched = java.util.concurrent.CountDownLatch(1)
        site.serveResponse(iconPath) {
            latched.await()
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(onePixelPng()))
        }
        site.serveJson("emojis.json", """{"reactions":[{"name":"heart","url":"${site.url(iconPath)}"}]}""")
        val topic = JSONObject(harness.fixture("topic-detail.json"))
        topic.put("valid_reactions", org.json.JSONArray(listOf("heart")))
        site.serveJson("t/$TOPIC_ID.json", topic.toString())

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Late emoji"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no rail")
        val emoticon = DiscourseReactions.emoticonOf("heart")
        try {
            // The rail already carries the reaction: the list is built before the menu is shown, so the
            // first frame is not blank while the picture is still in flight.
            assertEquals(
                "the rail carries the reaction before its picture arrives",
                1, rail.recyclerListView.adapter?.itemCount ?: 0,
            )
            harness.await("the emoji request to be in flight") { site.requestPaths.contains(iconPath) }
            assertTrue("the open menu's rail is attached to its window", rail.isAttachedToWindow)
            // Retained: the rail while the picture is still in flight - the chips are all there,
            // drawn from the client's own tile until the site's PNG lands.
            renderMenuComposite("menu-late-icons")

            // The menu closes: the popup's view leaves its window.
            (rail.parent as? ViewGroup)?.removeView(rail)
            harness.idle()
            assertFalse("the rail has left its window", rail.isAttachedToWindow)

            // Only now does the picture arrive.
            latched.countDown()
            harness.await("the emoji fetch to finish") {
                org.telegram.messenger.MediaDataController.getInstance(0)
                    .getReactionsMap().containsKey(emoticon)
            }
            harness.idleClock(500)
            assertEquals(
                "a late picture does not rebuild a menu that already closed",
                1, rail.recyclerListView.adapter?.itemCount ?: 0,
            )
            assertFalse("and does not reattach it", rail.isAttachedToWindow)
        } finally {
            latched.countDown()
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The rail carries every permitted reaction on the very frame the menu is shown - not one fetch
     * later. The failure this pins: a menu that measures and shows before it fills its rail draws an
     * empty bubble first (the "blank first frame" defect), and only fills in when the emoji arrive.
     */
    @Test
    fun theRailIsCompleteOnTheFirstFrameTheMenuIsShown() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "First frame"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        // No await, no clock: this is the frame the reader first sees the menu on.
        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no rail")
        println("rail first frame: itemCount=" + rail.recyclerListView.adapter?.itemCount +
            " all=" + rail.allReactionsList.size)
        assertEquals(
            "the rail carries every permitted reaction on its first frame",
            TEN_REACTIONS.size, rail.recyclerListView.adapter?.itemCount ?: 0,
        )
        assertEquals(TEN_REACTIONS.size, rail.allReactionsList.size)
        renderMenuComposite("menu-first-frame")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The rail enters with upstream's own transition, driven by the scrim layout - it does not snap
     * from nothing to full. The failure this pins: seeding the progress at 0 and then setting it to 1
     * in one step (or skipping the enter animation) draws no enter at all; the reader sees the rail
     * appear finished, and the shared scrim never sees a transition to run.
     */
    @Test
    fun theRailAnimatesInThroughItsTransitionRatherThanSnapping() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Enter"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        // A bounded drain: the rail's enter frame callback re-posts itself, so a few frames in the
        // transition is still between nothing and full rather than already finished.
        harness.idle(8)
        val rail = findRail(popupRoot(harness.activity)!!) ?: throw AssertionError("no rail")
        val mid = transitionProgress(rail)
        println("rail enter: mid progress=$mid")
        assertTrue("the rail is mid-enter, not already full ($mid)", mid > 0f && mid < 1f)
        val midWidth = rail.width
        val midHeight = rail.height

        // Settled: it reaches full, and its geometry did not come out of the animation broken.
        harness.idleClock(400)
        val settled = transitionProgress(rail)
        println("rail enter: settled progress=$settled width=${rail.width} height=${rail.height}")
        assertEquals("the rail settles at full", 1f, settled, 0.001f)
        assertTrue("the settled rail has a real size", rail.width > 0 && rail.height > 0)
        assertTrue("the rail was sized before the animation too", midWidth > 0 && midHeight > 0)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The menu dims the page but leaves the pressed post lit. The failure this pins: dimming with the
     * popup window's own dim flag darkens the very post the reader pressed - the bubble they are
     * acting on goes dark with everything else. Upstream instead draws the scrim itself and draws the
     * pressed post again above it, so the bubble keeps its brightness while the rest recedes.
     *
     * <p>Read from the dim layer alone: outside the pressed post it is the 20% black scrim; inside
     * it, the post's own opaque pixels survive on top.</p>
     */
    @Test
    fun theMenuDimsThePageButKeepsThePressedPostLitAboveTheDim() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Dim"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        val layer = findDimLayer(harness.activity.window.decorView)
            ?: throw AssertionError(
                "the menu dims its own window instead of a layer that keeps the pressed post lit",
            )
        assertTrue("the dim layer has a size", layer.width > 0 && layer.height > 0)
        val bitmap = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        layer.draw(Canvas(bitmap))

        // Away from the pressed post (over the floating bar at the top of the page): a 20% scrim.
        val outside = bitmap.getPixel(
            AndroidUtilities.dp(4f).coerceIn(0, layer.width - 1),
            AndroidUtilities.dp(20f).coerceIn(0, layer.height - 1),
        )
        println("dim: outside=" + Integer.toHexString(outside))
        assertEquals(
            "the page outside the pressed post is dimmed to 20%",
            255 * 0.2f, Color.alpha(outside).toFloat(), 2f,
        )

        // Over the pressed post: its pixels are drawn again above the scrim, so they are opaque.
        val cellLoc = IntArray(2)
        cell.getLocationInWindow(cellLoc)
        val layerLoc = IntArray(2)
        layer.getLocationInWindow(layerLoc)
        val left = (cellLoc[0] - layerLoc[0]).coerceAtLeast(0)
        val top = (cellLoc[1] - layerLoc[1]).coerceAtLeast(0)
        val right = (left + cell.width).coerceAtMost(layer.width)
        val bottom = (top + cell.height).coerceAtMost(layer.height)
        var lit = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 255) lit++
            }
        }
        println("dim: lit pixels over the pressed post = $lit of ${(right - left) * (bottom - top)}")
        assertTrue("the pressed post is drawn above the dim, not darkened with it", lit > 0)

        // And it is restored: dismissing the menu takes the layer with it.
        val topic = harness.topFragment() as TopicFragment
        topic.onFragmentDestroy()
        harness.idleClock(300)
        assertNull("the dim layer is gone with the menu", findDimLayer(harness.activity.window.decorView))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The rail is upstream's own picker in the stock chat scrim composition: a separate rounded
     * bubble above the action list, not a row wedged inside it. The failure this pins: a rail added
     * as a view of the action-list layout is clipped by that layout's own frame, and its bubble is
     * drawn into the list's rectangle instead of floating above it. So the rail's parent has to be
     * the scrim container and the action list a sibling of it, with the rail above.
     */
    @Test
    fun theRailIsASeparateBubbleAboveTheActionListAndNotNestedInsideIt() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Separate rail"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no rail")
        val scrim = rail.parent as? ChatScrimPopupContainerLayout
            ?: throw AssertionError("the rail sat in " + rail.parent?.javaClass?.name +
                ", not upstream's scrim container")
        val menu = findMenuLayout(scrim)
            ?: throw AssertionError("the scrim hosted no action-list layout")
        assertTrue("the action list is a sibling of the rail, not its host", menu !== rail)
        assertFalse("the rail is not a descendant of the action list",
            isDescendantOf(rail, menu))
        assertTrue(
            "the rail sits above the action list in the layout",
            scrim.children().indexOfFirst { it === rail } < scrim.children().indexOfFirst { it === menu },
        )

        // Laid out the way its own window does, the rail's box really is above the list's box - not
        // merely earlier in the child order - and both are inside the one popup window's root.
        layoutPopup(root)
        println("rail: parent=" + rail.parent.javaClass.simpleName +
            " menu=" + menu.javaClass.simpleName +
            " rail=" + rail.left + "," + rail.top + "," + rail.right + "," + rail.bottom +
            " menu=" + menu.left + "," + menu.top + "," + menu.right + "," + menu.bottom)
        assertTrue("the rail was laid out with a size", rail.width > 0 && rail.height > 0)
        assertTrue("the action list was laid out with a size", menu.width > 0 && menu.height > 0)
        assertTrue(
            "the rail's box is above the action list's box (rail.top=${rail.top} menu.top=${menu.top})",
            rail.top < menu.top,
        )
        assertTrue("the action list is inside the popup root", isDescendantOf(menu, root))
        assertTrue("the rail is inside the popup root", isDescendantOf(rail, root))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The menu opens and closes over and over, by back, by a tap outside, and by the fragment going
     * away, and none of those leave a popup behind or crash the screen. The failure this pins: a
     * popup the fragment does not remember to dismiss outlives the fragment, and an outside/back
     * dismissal that does not reach the window leaves a stale menu anchored over the next screen.
     */
    @Test
    fun repeatedOpenDismissBackOutsideAndFragmentDestroyAreSafe() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        val topic = TopicFragment.forTopic(TOPIC_ID, "Dismiss")
        harness.present(topic)
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        fun openMenu(): View {
            cell.delegate.didLongPress(cell, 0f, 0f)
            harness.idle()
            return popupRoot(harness.activity) ?: throw AssertionError("the menu did not open")
        }

        // Back closes it. The scrim container is the view upstream hangs that handling on.
        val first = openMenu()
        val firstScrim = findRail(first)!!.parent as View
        firstScrim.dispatchKeyEvent(android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        harness.idleClock(700)
        assertNull("a back press closed the menu", popupRoot(harness.activity))

        // A tap outside the menu closes it too.
        val second = openMenu()
        val secondScrim = findRail(second)!!.parent as View
        secondScrim.dispatchTouchEvent(android.view.MotionEvent.obtain(
            0, 0, android.view.MotionEvent.ACTION_DOWN, -4000f, -4000f, 0))
        harness.idleClock(700)
        assertNull("a tap outside closed the menu", popupRoot(harness.activity))

        // Re-opening works after both, and destroying the fragment with it open takes it with it.
        openMenu()
        topic.onFragmentDestroy()
        harness.idleClock(700)
        assertNull("destroying the fragment discarded its menu", popupRoot(harness.activity))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The action menu keeps the width the source gives it, and the rail - whose viewport the source
     * derives from the menu's width and its own stock margins - is wide enough to show more than the
     * handful short Chinese labels leave it. The failure this pins: a menu measured to its labels
     * alone (159dp, under the source's own 200dp floor), and a rail that inherits that narrowness, so
     * the reader sees four chips and cannot tell the other six are there. The chip count is not a
     * constant copied from the layout maths: it is how many of the site's own ten reactions are on
     * screen at once, and it stays under ten - the ten are never crammed onto one screen, the rail
     * scrolls for the rest.
     */
    @Test
    fun theMenuKeepsTheSourceMinimumWidthSoTheRailIsNotShrunkToItsLabels() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Widths"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, cell.width / 2f, cell.height / 2f)
        harness.idle()

        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup")
        val menu = findMenuLayout(root) ?: throw AssertionError("the menu hosted no action list")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no rail")
        layoutPopup(root)

        val firstChild = menu.getChildAt(0)
        println(
            "widths: menu=${menu.measuredWidth} firstChild=${firstChild?.javaClass?.simpleName} " +
                "firstChildW=${firstChild?.measuredWidth} rail=${rail.measuredWidth} " +
                "viewport=${rail.recyclerListView.width} screen=${AndroidUtilities.displaySize.x}",
        )
        assertTrue(
            "the action menu keeps the source's ${AndroidUtilities.dp(200f)}px minimum, not its labels' width",
            menu.measuredWidth >= AndroidUtilities.dp(200f),
        )
        val visibleChips = rail.recyclerListView.width / AndroidUtilities.dp(36f)
        println("widths: chips visible at once = $visibleChips")
        assertTrue(
            "the rail shows the chips the source's menu width reserves (visible=$visibleChips)",
            visibleChips >= 6,
        )
        assertTrue(
            "the rail still scrolls - the ten are not crammed onto one screen (visible=$visibleChips)",
            visibleChips < TEN_REACTIONS.size,
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The menu is anchored on the point inside the bubble that was pressed, not on the bubble's own
     * corner. The failure this pins: a menu dropped below (or above) the whole cell at a fixed edge,
     * so it neither follows the finger across the bubble nor sits where the reader pressed. Pressing
     * further right has to carry the menu with the finger, and a press anywhere on the bubble has to
     * leave the menu reaching up over the bubble rather than hanging below it, inside the content
     * view.
     */
    @Test
    fun theMenuAnchorsOnThePressedPointInsideTheBubble() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Anchor"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        harness.layoutApp()
        val cellLoc = IntArray(2)
        cell.getLocationInWindow(cellLoc)

        // Further right along the bubble: the menu moves right with the finger.
        cell.delegate.didLongPress(cell, AndroidUtilities.dp(4f).toFloat(), cell.height * 0.4f)
        harness.idle()
        val left = popupWindowXY(harness.activity) ?: throw AssertionError("no popup after a left press")
        cell.delegate.didLongPress(cell, (cell.width - AndroidUtilities.dp(4f)).toFloat(), cell.height * 0.4f)
        harness.idle()
        val right = popupWindowXY(harness.activity) ?: throw AssertionError("no popup after a right press")
        println("anchor: left=${left.contentToString()} right=${right.contentToString()} cell=${cell.width}x${cell.height}")
        assertTrue("a press further right carries the menu further right ($left -> $right)", right[0] > left[0])

        // The finger's height drives the menu's own top too: the menu reaches up over the bubble
        // rather than being parked below it.
        cell.delegate.didLongPress(cell, cell.width * 0.5f, AndroidUtilities.dp(4f).toFloat())
        harness.idle()
        val highPress = popupWindowXY(harness.activity) ?: throw AssertionError("no popup after a high press")
        cell.delegate.didLongPress(cell, cell.width * 0.5f, (cell.height - AndroidUtilities.dp(4f)).toFloat())
        harness.idle()
        val lowPress = popupWindowXY(harness.activity) ?: throw AssertionError("no popup after a low press")
        val pressHigh = cellLoc[1] + AndroidUtilities.dp(4f)
        val pressLow = cellLoc[1] + cell.height - AndroidUtilities.dp(4f)
        println(
            "anchor: pressHigh=$pressHigh pressLow=$pressLow high=${highPress.contentToString()} " +
                "low=${lowPress.contentToString()}",
        )
        assertTrue(
            "a press at the bubble's top is not below the menu's own top (press=$pressHigh menu=${highPress[1]})",
            highPress[1] <= pressHigh,
        )
        assertTrue(
            "a press at the bubble's bottom is not below the menu's own top (press=$pressLow menu=${lowPress[1]})",
            lowPress[1] <= pressLow,
        )

        // Whatever the press, the whole menu stays inside the content view.
        val root = popupRoot(harness.activity) ?: throw AssertionError("no popup")
        layoutPopup(root)
        val contentBottom = contentBottomInWindow()
        val popupH = root.measuredHeight
        println("anchor: low=${lowPress.contentToString()} popupH=$popupH contentBottom=$contentBottom")
        assertTrue(
            "the menu stays above the content's bottom edge (top=${lowPress[1]} h=$popupH content=$contentBottom)",
            lowPress[1] + popupH <= contentBottom,
        )
        assertTrue("the menu stays below the content's top edge", lowPress[1] >= AndroidUtilities.dp(6f))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A reaction the reader already made is the chip that shows as chosen: its own chip carries the
     * selected state while its neighbours do not. The failure this pins: a menu that carries the
     * site's pictures for every chip but forgets which one the reader had already picked, so the
     * chosen reaction reads like any other and a second tap cannot tell it is a toggle-off.
     */
    @Test
    fun theSelectedReactionIsTheOneChipMarkedChosen() {
        val topic = JSONObject(harness.fixture("topic-detail.json"))
        val stream = topic.getJSONObject("post_stream")
        stream.put("stream", JSONArray().put(stream.getJSONArray("posts").getJSONObject(0).getLong("id")))
        topic.put("posts_count", 1)
        topic.put("highest_post_number", 1)
        topic.put("valid_reactions", JSONArray(TEN_REACTIONS))
        stream.getJSONArray("posts").getJSONObject(0).put(
            "current_user_reaction",
            JSONObject().put("id", TEN_REACTIONS[1]).put("can_undo", true),
        )
        site.serveJson("t/$TOPIC_ID.json", topic.toString())

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Selected"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, cell.width / 2f, cell.height / 2f)
        harness.idle()
        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no rail")
        layoutPopup(root)

        val list = rail.recyclerListView
        fun holderAt(position: Int): ReactionsContainerLayout.ReactionHolderView? =
            list.findViewHolderForAdapterPosition(position)?.itemView
                as? ReactionsContainerLayout.ReactionHolderView
        harness.await("the chosen chip to be bound") { holderAt(1) != null }
        val chosen = holderAt(1)!!
        val other = (0 until TEN_REACTIONS.size)
            .mapNotNull { holderAt(it) }
            .firstOrNull { it !== chosen }
            ?: throw AssertionError("the rail drew no neighbour chip")
        println("selected: chosen.selected=${chosen.selected} other.selected=${other.selected}")
        assertTrue("the chip the reader already reacted with shows as chosen", chosen.selected)
        assertFalse("its neighbours are not marked chosen", other.selected)
        renderMenuComposite("menu-selected")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Retained artifacts for the pass. Each is the whole page the window manager composites - the
     * topic, the dim, the pressed post left lit, and the menu in its own window at its real offset -
     * so the composition can be re-checked without repeating the run: light mid-enter and settled,
     * dark settled, and the rail scrolled to its last reaction.
     */
    @Test
    fun capturesTheCompositePageInLightAndDarkMidOpenSettledAndScrolled() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Shots"))
        harness.await("the topic to load") { rowCount() == 1 }
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")

        // Settled, light: the popup at rest over the dimmed page with the pressed post lit.
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        renderMenuComposite("menu-open-light")

        // Mid-enter, light: a fresh open, a bounded drain leaves the rail's transition part way.
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle(3)
        renderMenuComposite("menu-mid-open-light", 3)

        // Settled, dark.
        switchTheme(toDark = true)
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        val darkRoot = popupRoot(harness.activity) ?: throw AssertionError("no dark popup")
        val darkRail = findRail(darkRoot) ?: throw AssertionError("no rail")
        renderMenuComposite("menu-open-dark")

        // The rail scrolled to its last reaction.
        darkRail.recyclerListView.scrollToPosition(9)
        harness.idle()
        layoutPopup(darkRoot)
        harness.idle()
        awaitScrolledRailReady(darkRail)
        renderMenuComposite("menu-last-reaction")
        switchTheme(toDark = false)

        // Pressed low on the bubble: the menu is anchored at the finger and stays above the composer.
        cell.delegate.didLongPress(cell, cell.width * 0.5f, (cell.height - AndroidUtilities.dp(4f)).toFloat())
        harness.idle()
        renderMenuComposite("menu-near-bottom")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The rail swept to its end paints its last chip - a real glyph, not a blank tile.
     *
     * <p>The failure this pins: a chip a scroll just created binds its holder during the scroll's
     * layout and only asks the loader for its picture from inside its next draw, so the frame read
     * the instant the rail reaches its end is a drawn pill with an empty chip in it - the blank end
     * of `menu-last-reaction.png`. This reproduces `capturesTheCompositePage...`'s own run-up (the
     * menu opened in light, mid-enter, then dark) so the chip is freshly bound exactly as there, and
     * then reads the drawn chip's own pixels - not a click that would land on it, nor a view that
     * merely exists: a present, laid-out chip that paints nothing still fails.</p>
     *
     * <p>The fixture is the site's ten reactions with the canonical emoji glyph this client draws on
     * a transparent tile for each - the same case the blank capture was taken in. The count is of
     * opaque picture pixels, not a value copied from that rendering.</p>
     */
    @Test
    fun theScrolledLastChipPaintsItsGlyphNotABlankTile() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Scrolled glyph"))
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        // The composite capture's own flow up to the sweep: light open, mid-enter open, then a dark
        // open - the state the blank end-of-rail capture was taken in.
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle(3)
        switchTheme(toDark = true)
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()

        val root = popupRoot(harness.activity) ?: throw AssertionError("the menu hosted no popup")
        val rail = findRail(root) ?: throw AssertionError("the menu hosted no rail")
        assertEquals("the rail carries the site's ten reactions", 10, rail.allReactionsList.size)

        rail.recyclerListView.scrollToPosition(9)
        harness.idle()
        layoutPopup(root)
        harness.idle()
        awaitScrolledRailReady(rail)

        val list = rail.recyclerListView
        var chip: View? = null
        for (index in 0 until list.childCount) {
            if (list.getChildAdapterPosition(list.getChildAt(index)) == 9) {
                chip = list.getChildAt(index)
                break
            }
        }
        val tile = chip ?: throw AssertionError("the tenth reaction could not be scrolled into the rail")

        val tileBitmap = Bitmap.createBitmap(
            tile.width.coerceAtLeast(1), tile.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        tile.draw(Canvas(tileBitmap))
        var painted = 0
        for (y in 0 until tileBitmap.height) {
            for (x in 0 until tileBitmap.width) {
                if (Color.alpha(tileBitmap.getPixel(x, y)) != 0) painted++
            }
        }
        println("scrolled last chip: ${tile.width}x${tile.height} glyph pixels painted=$painted")

        // The drawn pixels, not a constant: a laid-out chip that draws nothing is the defect.
        assertTrue(
            "the scrolled tenth chip painted its glyph, not a blank tile ($painted opaque px)",
            painted > 0,
        )
        switchTheme(toDark = false)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Applies the theme the way the app's own switch does, but straight to Theme instead of through
     * the activity's reveal animation: this test only needs the colours the popup is built from, and
     * the animation path is a global screen transition the surrounding tests can leave half-done.
     */
    private fun switchTheme(toDark: Boolean) {
        org.telegram.ui.DialogsActivity.switchingTheme = false
        org.telegram.ui.ActionBar.Theme.applyTheme(
            org.telegram.ui.ActionBar.Theme.getTheme(if (toDark) "Dark Blue" else "Blue"),
            false, toDark,
        )
        harness.layoutApp()
        harness.idle()
        assertTrue(
            "the theme applied (dark=" + toDark + ")",
            org.telegram.ui.ActionBar.Theme.isCurrentThemeDark() == toDark,
        )
    }

    /** The whole page - topic, dim, the lit pressed post, and the menu window at its real offset. */
    private fun renderMenuComposite(name: String, maxTasks: Int = 1_000) {
        harness.renderComposite(".amp/in/artifacts/menu-v2/$name.png", maxTasks)
    }

    /**
     * Draws the rail the way the reader's next frame would, until every chip on screen really shows
     * the picture bound to it - the frame the reader gets once the sweep and the site's icons have
     * settled.
     *
     * <p>Two things leave a freshly-positioned chip blank, and neither is the enter animation
     * finishing late. A scroll recycles the holders it moves off screen into the positions it
     * reveals, and upstream keeps a per-holder "already played" set so a chip is entered once; a
     * recycled holder is still in that set, so the picker skips its enter play and the chip - reset
     * to nothing when it was rebound - draws empty. Our reactions take the non-animated path (their
     * picture is a plain PNG), whose reset leaves the chip at scale zero, unlike upstream's animated
     * emoji, which draw through a second image view either way. And when the site's picture lands
     * late it rebinds the rail through `setMessage`, which resets the chips the same way. Letting
     * those rebound chips play again is the host's job; this helper only draws frames and waits for
     * the icon fetch, and never repairs the rail itself - a reset here would manufacture the very
     * frame the reader's next one has to produce. The picture arrives on the loader's threads too,
     * so real time passes here as well.</p>
     */
    private fun awaitScrolledRailReady(rail: ReactionsContainerLayout) {
        val probe = Bitmap.createBitmap(
            rail.width.coerceAtLeast(1), rail.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(probe)
        var tries = 0
        while (tries++ < 40) {
            rail.draw(canvas)
            harness.idle()
            harness.idleUpstreamQueues()
            // Let the chip's own enter scale animation reach its end on the frame clock.
            harness.idleClock(200)
            if (onScreenChipsShowTheirIcon(rail)) return
            // The picture can land on a worker upstream owns that Robolectric does not pause (a
            // DispatchQueuePriority runs on a plain thread pool), so real time has to pass too.
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L)
        }
        throw AssertionError(
            "the swept rail's chips never painted their icons " + railChipDiag(rail),
        )
    }

    /** The rail's state, for the failure message: which chips still draw nothing. */
    private fun railChipDiag(rail: ReactionsContainerLayout): String {
        val list = rail.recyclerListView
        val parts = mutableListOf<String>()
        for (i in 0 until list.childCount) {
            val holder = list.getChildAt(i) as? ReactionsContainerLayout.ReactionHolderView ?: continue
            parts += "pos=${list.getChildAdapterPosition(holder)} " +
                "bmp=${holder.loopImageView.getImageReceiver().getBitmap() != null} " +
                "scale=${holder.loopImageView.scaleX} enter=${holder.hasEnterAnimation}"
        }
        return "[children=${list.childCount} ${parts.joinToString(" | ")}]"
    }

    /**
     * Whether every rail chip whose centre is on screen is actually showing its picture: the icon is
     * decoded and the chip has been played (its scale is up). A loaded-but-not-yet-played chip draws
     * nothing at scale 0, so the bitmap alone is not enough.
     */
    private fun onScreenChipsShowTheirIcon(rail: ReactionsContainerLayout): Boolean {
        val list = rail.recyclerListView
        var onScreen = 0
        for (i in 0 until list.childCount) {
            val holder = list.getChildAt(i) as? ReactionsContainerLayout.ReactionHolderView ?: continue
            val centre = holder.x + holder.width / 2f
            if (centre <= 0f || centre >= list.width) continue
            onScreen++
            if (holder.loopImageView.getImageReceiver().getBitmap() == null) return false
            if (holder.loopImageView.scaleX <= 0f) return false
        }
        return onScreen > 0
    }

    /** The screen's dim layer, found by its own name: it is an implementation detail, not a type. */
    private fun findDimLayer(view: View?): View? {
        when (view) {
            null -> return null
            is ViewGroup -> for (index in 0 until view.childCount) {
                findDimLayer(view.getChildAt(index))?.let { return it }
            }
        }
        return if (view.javaClass.simpleName == "MenuDimLayer") view else null
    }

    /** The rail's own transition progress, which its enter animation drives from 0 to 1. */
    private fun transitionProgress(rail: ReactionsContainerLayout): Float {
        val field = ReactionsContainerLayout::class.java.getDeclaredField("transitionProgress")
        field.isAccessible = true
        return field.getFloat(rail)
    }

    /**
     * Every one of the site's ten reactions, tapped in turn, sends exactly its own id. The failure
     * this pins: a rail that maps a glyph to the wrong id, or that sends a neighbour because the
     * tile at the tapped position is not the one the reader saw, reacts with a reaction nobody
     * picked. The check is the write the fake site actually received, one per reaction.
     */
    @Test
    fun everyPermittedReactionSendsExactlyItsOwnSiteId() {
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Every id"))
        harness.await("the topic to load") { rowCount() == 1 }

        val sent = mutableListOf<String>()
        for (index in 0 until TEN_REACTIONS.size) {
            val cell = laidOutCells().firstOrNull() ?: throw AssertionError("no cell at $index")
            cell.delegate.didLongPress(cell, 0f, 0f)
            harness.idle()
            val root = popupRoot(harness.activity) ?: throw AssertionError("no popup at $index")
            val rail = findRail(root) ?: throw AssertionError("no rail at $index")
            harness.await("the ten reactions at $index") { rail.allReactionsList.size == 10 }
            val list = rail.recyclerListView
            layoutPopup(root)
            list.scrollToPosition(index)
            harness.idle()
            layoutPopup(root)
            var tile: View? = null
            for (child in 0 until list.childCount) {
                if (list.getChildAdapterPosition(list.getChildAt(child)) == index) {
                    tile = list.getChildAt(child)
                    break
                }
            }
            val target = tile ?: throw AssertionError("reaction $index is not reachable")
            val expected = DiscourseReactions.idOf(rail.allReactionsList[index].emojicon)
            val before = site.recordedRequests.count { it.contains("custom-reactions/") && it.startsWith("PUT") }
            list.clickItem(target, index)
            harness.await("the toggle for reaction $index") {
                site.recordedRequests.count { it.contains("custom-reactions/") && it.startsWith("PUT") } > before
            }
            val toggle = site.recordedRequests.last { it.contains("custom-reactions/") && it.startsWith("PUT") }
            sent += toggle
            assertTrue(
                "reaction $index sent its own id, seen as $expected: $toggle",
                toggle.contains("/$expected/toggle.json"),
            )
            harness.idleClock(700)
        }
        assertEquals(
            "the ten taps sent the site's ten ids, in order",
            TEN_REACTIONS,
            sent.map { Regex("custom-reactions/([^/]+)/toggle.json").find(it)!!.groupValues[1] },
        )
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A menu reopened over a warm catalogue keeps the site picture the catalogue already holds.
     *
     * <p>The failure this pins: `showMenu` registers a drawn fallback for every permitted reaction
     * on every open, so the second open replaces the loaded site icon with the synthetic tile before
     * the rail is measured - the first frame the reopened menu draws is the placeholder, and only
     * the async fetch callback (which finds the picture already on disk) puts the site icon back. A
     * custom site emoji makes it visible: its fallback is a drawn text tile, nothing like the site's
     * own transparent picture, and the whole point of the site's catalogue is that a reader sees it.</p>
     */
    @Test
    fun aWarmReopenKeepsTheLoadedSiteIconInsteadOfRedrawingTheFallbackTile() {
        val iconPath = "images/emoji/custom-$runToken.png"
        val customId = "custom_$runToken"
        // The site's picture is a transparent icon, the shape a real custom emoji has - not an
        // opaque block that could pass for the drawn fallback.
        site.serveImage(iconPath, transparentPng(96))
        site.serveJson(
            "emojis.json",
            """{"reactions":[{"name":"$customId","url":"${site.url(iconPath)}"}]}""",
        )
        val topic = JSONObject(harness.fixture("topic-detail.json"))
        topic.put("valid_reactions", JSONArray(listOf(customId)))
        site.serveJson("t/$TOPIC_ID.json", topic.toString())

        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Warm reopen"))
        harness.await("the topic to load") { rowCount() == 1 }

        val emoticon = DiscourseReactions.emoticonOf(customId)
        val siteIcon = DiscourseReactionEmoji.file(site.url(iconPath))

        // The cold open: the site's picture is fetched and becomes the catalogue's entry. The await
        // is for the table entry, which the async fetch writes after the bytes land.
        val cell = laidOutCells().firstOrNull() ?: throw AssertionError("the topic drew no cell")
        cell.delegate.didLongPress(cell, 0f, 0f)
        harness.idle()
        harness.await("the loaded site icon to reach the catalogue") {
            MediaDataController.getInstance(0).getReactionsMap()[emoticon]
                ?.center_icon?.localPath == siteIcon.absolutePath
        }

        val iconFetchesBefore = site.requestPaths.count { it == iconPath }

        // The warm reopen, the way the reader opens it again: the same long-press path that
        // dismisses the first menu and builds the second. showMenu registers its fallbacks
        // synchronously as it builds, before the picker is measured, so the catalogue is read here
        // with no main-loop drain: a drain would run the reopen's own async fetch, which finds the
        // picture already on disk and puts the site icon back - hiding the very regression under
        // test. The first frame the reopened menu draws is the catalogue as it stands right now.
        cell.delegate.didLongPress(cell, 0f, 0f)
        val entry = MediaDataController.getInstance(0).getReactionsMap()[emoticon]
            ?: throw AssertionError("the reopened menu's catalogue lost the reaction")
        println(
            "warm reopen: localPath=${entry.center_icon?.localPath} siteIcon=${siteIcon.absolutePath} " +
                "fallback=${DiscourseReactionEmoji.fallbackFile(emoticon).absolutePath}",
        )
        assertEquals(
            "the first frame of the warm reopen keeps the loaded site icon",
            siteIcon.absolutePath,
            entry.center_icon?.localPath,
        )
        assertFalse(
            "the warm reopen does not swap in the drawn fallback tile",
            entry.center_icon?.localPath == DiscourseReactionEmoji.fallbackFile(emoticon).absolutePath,
        )
        assertEquals(
            "a warm reopen does not refetch a picture already on disk",
            iconFetchesBefore,
            site.requestPaths.count { it == iconPath },
        )

        harness.idle()
        val root = popupRoot(harness.activity) ?: throw AssertionError("the warm menu did not open")
        val rail = findRail(root) ?: throw AssertionError("the warm menu hosted no rail")
        assertEquals(
            "the reopened rail still carries the site's reaction",
            listOf(emoticon),
            rail.allReactionsList.map { it.emojicon },
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A picture with a transparent surround, the shape a real custom emoji icon has. */
    private fun transparentPng(size: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            .apply { color = android.graphics.Color.rgb(255, 0, 255) }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.4f, paint)
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun popupRoot(activity: Activity): View? {
        val windowManager = org.robolectric.shadow.api.Shadow.extract(activity.windowManager)
            as org.robolectric.shadows.ShadowWindowManagerImpl
        for (view in windowManager.views.asReversed()) {
            if (view === activity.window.decorView) continue
            if (findRail(view) != null) return view
        }
        return null
    }

    /**
     * The window offset the window manager placed the menu at: the same x/y the composite capture
     * translates the popup by, so a test can read where the menu really came up.
     */
    private fun popupWindowXY(activity: Activity): IntArray? {
        val windowManager = org.robolectric.shadow.api.Shadow.extract(activity.windowManager)
            as org.robolectric.shadows.ShadowWindowManagerImpl
        for (view in windowManager.views.asReversed()) {
            if (view === activity.window.decorView) continue
            if (findRail(view) == null) continue
            val params = view.layoutParams as? WindowManager.LayoutParams ?: continue
            return intArrayOf(params.x, params.y)
        }
        return null
    }

    /** The bottom of the fragment's own view in window coordinates, where a menu must stay above. */
    private fun contentBottomInWindow(): Int {
        val view = harness.topFragment()?.fragmentView ?: return AndroidUtilities.displaySize.y
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return loc[1] + view.height
    }

    /**
     * Lays the real popup window's root out the way the window manager does: the screen's width, the
     * screen as height bound. The rail then takes its own production layout params, including its
     * height - the test never sizes the reaction list itself.
     */
    private fun layoutPopup(root: View) {
        val width = org.telegram.messenger.AndroidUtilities.displaySize.x
        val height = org.telegram.messenger.AndroidUtilities.displaySize.y
        repeat(2) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            harness.idle(40)
        }
    }

    private fun findRail(view: View): ReactionsContainerLayout? {
        if (view is ReactionsContainerLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findRail(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun findMenuLayout(view: View): ActionBarPopupWindow.ActionBarPopupWindowLayout? {
        if (view is ActionBarPopupWindow.ActionBarPopupWindowLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findMenuLayout(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 100L
        val TEN_REACTIONS = listOf(
            "+1", "heart", "laughing", "open_mouth", "clap",
            "confetti_ball", "tada", "hugs", "distorted_face", "rocket",
        )
    }
}
