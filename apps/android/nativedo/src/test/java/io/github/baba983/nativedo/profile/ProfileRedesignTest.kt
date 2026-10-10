package io.github.baba983.nativedo.profile

import android.view.View
import android.view.ViewGroup
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScrollSlidingTextTabStrip
import kotlin.math.abs

/**
 * The profile as the owner asked for it: opened from the topic header's capsule with the chat
 * header's own avatar flight, an action row under the header, and the site's lists on tabs.
 *
 * <p>The failures pinned here are the ones the wire makes visible: the capsule tap has to seed the
 * flight with the capsule avatar's own position (a screen that opens the profile with the default
 * transition, or seeds zero, is the bug); each tab is a different `user_actions.json` filter with
 * its own offset (one shared stream loses the tab the reader asked for); the action row follows the
 * site's `can_*` fields (offering a follow the site refuses, or a message it will not deliver); and
 * a write goes out with its CSRF header and flips the button it belongs to.</p>
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
internal class ProfileRedesignTest {

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
     * The capsule's tap: the profile has to arrive as the header's own flight - upstream's ordinary
     * header transition (`playProfileAnimation == 1`, `ChatAvatarContainer.openProfile`'s
     * non-avatar branch) with the host's avatar flight (`avatarFlight`) carrying the capsule
     * avatar's screen X - and land on the topic author. A profile opened with the default
     * transition leaves `playProfileAnimation` at 0, its flight off, and the avatar does not move.
     */
    @Test
    fun theTopicHeaderCapsuleFliesTheAvatarIntoTheAuthorsProfile() {
        site.serveFixture("u/baba.json", "user-profile.json")
        site.serveFixture("u/baba/summary.json", "user-summary.json")
        site.serveImage("user_avatar/linux.do/baba/96/1.png", magentaPng(96, 96))
        site.serveCooked(TOPIC_ID, "<p>你好。</p>")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A post"))
        harness.await("the topic to load") { listRowCount() == 1 }

        val capsule = findView(harness.activity.window.decorView, ChatAvatarContainer::class.java)
            ?: throw AssertionError("the topic header has no capsule")
        val capsuleAvatar = capsule.avatarImageView
            ?: throw AssertionError("the capsule has no avatar view")
        val onScreen = IntArray(2)
        capsuleAvatar.getLocationOnScreen(onScreen)
        val capsuleX = onScreen[0].toFloat()

        capsule.performClick()
        harness.idle()

        val profile = harness.topFragment() as? ProfileFragment
            ?: throw AssertionError("the capsule tap did not open the profile")
        assertEquals("the ordinary header transition", 1, field(profile, "playProfileAnimation"))
        assertEquals("the avatar flies", true, field(profile, "avatarFlight"))
        assertEquals(
            "the flight starts where the capsule avatar is",
            capsuleX,
            field(profile, "prevAvatarTranslation") as Float,
            1.5f,
        )

        // The flight runs to its end: the avatar animation reaches 1, and the screen shows baba.
        harness.idleClock(400)
        assertEquals(
            "the flight completed",
            1.0f,
            field(profile, "avatarAnimationProgress") as Float,
            0.01f,
        )
        assertEquals("the flight is over", false, field(profile, "openAnimationInProgress"))
        harness.await("the site for the profile") { site.requestPaths.contains("u/baba.json") }
        harness.await("the profile to show the site's fields") {
            harness.layoutApp()
            detailValue("信任等级") == "3"
        }
        assertEquals("the author's handle", "baba", profile.arguments?.getString("username"))
        // The action row's capsules animate in; a few frames settle them for the render.
        repeat(25) { harness.idleClock(20); harness.drawApp() }
        harness.renderApp("profile-open-from-capsule")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * 话题 and 回复 are two different `user_actions.json` filters; switching tabs has to ask for the
     * second one, and reaching the end of a page has to ask for the same filter at the next offset.
     */
    @Test
    fun eachTabAsksForItsOwnFilterAndPagesItsOwnStream() {
        site.serveJsonFor("user_actions.json") { path ->
            val filter = path.substringAfter("filter=").substringBefore('&')
            val offset = path.substringAfter("offset=").substringBefore('&').toIntOrNull() ?: 0
            actionsPage(filter, offset)
        }
        site.serveFixture("u/baba.json", "user-profile.json")
        // The summary arrives after the tab area is built and rebuilds the info rows: the tab row
        // has to survive that rebuild, or the reader loses the tabs the moment the stats land.
        site.serveFixture("u/baba/summary.json", "user-summary.json")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile's first tab") {
            site.queriesFor("user_actions.json").any { it.contains("filter=4") && it.contains("offset=0") }
        }
        harness.await("the summary to land") { site.requestPaths.contains("u/baba/summary.json") }
        harness.idleClock(100)
        // The tab area is the info list's last row: scrolling to it is what binds and measures it,
        // the same thing the reader does to reach the tabs.
        val list = listView() ?: throw AssertionError("no list on screen")
        list.scrollToPosition(list.adapter!!.itemCount - 1)
        harness.layoutApp()

        val profile = harness.topFragment() as? ProfileFragment
            ?: throw AssertionError("the capsule tap did not open the profile")
        val media = field<ProfileSharedMediaView>(profile, "mediaView")
        clickTab(media.tabStrip, 1)
        harness.await("the replies tab's own filter") {
            site.queriesFor("user_actions.json").any { it.contains("filter=5") && it.contains("offset=0") }
        }

        // A page of twenty; reaching its end has to ask the same filter for offset 20.
        harness.await("the replies rows") { pageRowCount(media, 1) > 0 }
        scrollPageToEnd(media)
        harness.await("the next replies page") {
            site.queriesFor("user_actions.json").any { it.contains("filter=5") && it.contains("offset=20") }
        }
        assertTrue(
            "the topics tab's own stream was asked",
            site.queriesFor("user_actions.json").count { it.contains("filter=4") } >= 1,
        )
        harness.renderApp("profile-tab-replies")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The summary's stats rows insert above what the reader is looking at. The reader scrolls the
     * tab area to the top (the way the pinned media header is reached on the device); the summary
     * lands afterwards and its rows go in above the tabs. The tab area must keep its place by
     * semantic identity, not the old numeric adapter position, which now belongs to a stats row.
     *
     * <p>The failure this pins: the pinned tab area (and the header's own offset) stepping when a
     * late summary lands. It is the viewport half of the answer-during-flight contract; the
     * in-flight half is pinned in ProfileTransitionTest.</p>
     */
    @Test
    fun aLateSummaryKeepsTheTabAreaWhereTheReaderPinnedIt() {
        site.serveFixture("u/baba.json", "user-profile.json")
        val gate = java.util.concurrent.CountDownLatch(1)
        site.serveResponse("u/baba/summary.json") {
            gate.await(30, java.util.concurrent.TimeUnit.SECONDS)
            okhttp3.mockwebserver.MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(harness.fixture("user-summary.json"))
        }

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile's info rows") {
            harness.layoutApp()
            detailValue("信任等级") == "3"
        }
        val profile = harness.visibleFragment() as ProfileFragment
        val list = listView()!!

        // The tab area is the info list's last row; scrolling to it is what pins it, the same thing
        // the reader does to reach the tabs.
        list.scrollToPosition(list.adapter!!.itemCount - 1)
        harness.layoutApp()
        val media = field<View>(profile, "mediaView")
        val locationBefore = IntArray(2).also(media::getLocationOnScreen)
        val rowsBefore = list.adapter!!.itemCount
        val extraBefore = field<Float>(profile, "extraHeight")

        // The summary lands now; its stats rows insert above the pinned tab area.
        gate.countDown()
        harness.await("the summary's rows") {
            harness.layoutApp()
            field<Any?>(profile, "summary") != null && list.adapter!!.itemCount > rowsBefore
        }
        harness.layoutApp()

        assertEquals(
            "the pinned tab area keeps the reader's place when the stats land above it",
            locationBefore[1].toFloat(), IntArray(2).also(media::getLocationOnScreen)[1].toFloat(), 1f,
        )
        assertEquals(
            "the header's own offset does not step with the inserted rows",
            extraBefore, field<Float>(profile, "extraHeight"), 1f,
        )
        copyToUiPolish(harness.renderApp("profile-summary-anchored"), "profile-summary-anchored")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * What the row shows is what the site said the reader may do: a profile with the follow, PM and
     * mute flags offers 私信/关注/静音/分享, the reader's own profile offers 书签/设置/分享, and a
     * profile without the fields does not fake them.
     */
    @Test
    fun theActionRowFollowsTheSitesOwnFlags() {
        site.serveJson("u/momo199412.json", otherUserProfileJson(canFollow = true, followed = false, canMute = true))
        site.serveJson("u/nobody.json", "{\"user\":{\"username\":\"nobody\"}}")
        site.serveFixture("u/baba.json", "user-profile.json")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }

        harness.present(ProfileFragment.forUser("momo199412", "momo"))
        harness.await("their profile") { harness.visibleFragment() is ProfileFragment }
        harness.await("their action row") { actionsReady() }
        val other = harness.visibleFragment() as ProfileFragment
        assertTrue("private message offered", other.hasAction(ProfileActionsView.KEY_MESSAGE))
        assertTrue("follow offered", other.hasAction(ProfileActionsView.KEY_FOLLOW))
        assertFalse("not yet followed", other.hasAction(ProfileActionsView.KEY_UNFOLLOW))
        assertTrue("mute offered", other.hasAction(ProfileActionsView.KEY_NOTIFICATION))
        assertTrue("share offered", other.hasAction(ProfileActionsView.KEY_SHARE))
        assertFalse("bookmarks are not someone else's", other.hasAction(ProfileActionsView.KEY_BOOKMARKS))

        harness.present(ProfileFragment.forUser("nobody", "nobody"))
        harness.await("the profile without the flags") { actionsReady() }
        val bare = harness.visibleFragment() as ProfileFragment
        assertFalse("no message without the flag", bare.hasAction(ProfileActionsView.KEY_MESSAGE))
        assertFalse("no follow without the flag", bare.hasAction(ProfileActionsView.KEY_FOLLOW))
        assertFalse("no mute without the flag", bare.hasAction(ProfileActionsView.KEY_NOTIFICATION))
        assertTrue("the share is always there", bare.hasAction(ProfileActionsView.KEY_SHARE))

        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the reader's own profile") { actionsReady() }
        val own = harness.visibleFragment() as ProfileFragment
        assertTrue("bookmarks on one's own profile", own.hasAction(ProfileActionsView.KEY_BOOKMARKS))
        assertTrue("settings on one's own profile", own.hasAction(ProfileActionsView.KEY_SETTINGS))
        assertTrue("share on one's own profile", own.hasAction(ProfileActionsView.KEY_SHARE))
        assertFalse("no message to oneself", own.hasAction(ProfileActionsView.KEY_MESSAGE))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * 关注 is a write with a CSRF header, and the button it belongs to becomes 取消关注 when the site
     * answers. A request without the token would be refused by a real site; a row that never flips
     * would make the reader press it twice.
     */
    @Test
    fun aFollowWriteRoundTripsAndTheButtonFlips() {
        site.serveJson("u/momo199412.json", otherUserProfileJson(canFollow = true, followed = false, canMute = true))
        site.serveJson("follow/momo199412.json", "{\"success\":\"OK\"}")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("momo199412", "momo"))
        harness.await("their profile") { harness.visibleFragment() is ProfileFragment }
        harness.await("their action row") { actionsReady() }
        val profile = harness.visibleFragment() as ProfileFragment

        profile.onActionClicked(ProfileActionsView.KEY_FOLLOW, 0f, 0f)
        harness.await("the follow write") { site.recordedRequests.contains("PUT /follow/momo199412.json") }
        assertNotNull(
            "the write carried its CSRF token",
            site.csrfHeaderFor("follow/momo199412.json"),
        )
        harness.await("the button to flip") {
            profile.hasAction(ProfileActionsView.KEY_UNFOLLOW) && !profile.hasAction(ProfileActionsView.KEY_FOLLOW)
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A profile that carries none of the optional fields (no bio, no location, no flags, and a site
     * whose summary and badge routes answer nothing) still opens and draws.
     */
    @Test
    fun aProfileWithMissingFieldsStillDraws() {
        site.serveJson("u/baba.json", "{\"user\":{\"username\":\"baba\"}}")

        harness.bootApp()
        harness.await("the topic list to load") { listRowCount() == 2 }
        harness.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile to load") {
            harness.layoutApp()
            detailValue("信任等级") == "0"
        }
        harness.renderApp("profile-minimal")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------------------------------

    /** The topic list and the profile's own list are the two RecyclerListViews on screen. */
    private fun listRowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun listView(): RecyclerListView? =
        io.github.baba983.nativedo.harness.contentListOf(harness.activity)

    /** True once the visible profile's action row was built (its flags were applied). */
    private fun actionsReady(): Boolean {
        val profile = harness.visibleFragment() as? ProfileFragment ?: return false
        return profile.hasAction(ProfileActionsView.KEY_SHARE) || profile.hasAction(ProfileActionsView.KEY_BOOKMARKS)
    }

    /** Taps the strip's tab at [index], the way a reader does, and lets the page slide settle
     * (a tab tap runs the pager's own 320 ms animation; the page it lands on is what a page's
     * stream is read from). */
    private fun clickTab(strip: ScrollSlidingTextTabStrip, index: Int) {
        val tabsContainer = strip.getChildAt(0) as? ViewGroup
            ?: throw AssertionError("the strip has no tab container")
        val tab = tabsContainer.getChildAt(index)
            ?: throw AssertionError("the strip has no tab $index")
        tab.performClick()
        harness.idle()
        harness.idleClock(400)
        harness.layoutApp()
    }

    /** The page at tab [index]'s row count, as the fragment bound it. */
    private fun pageRowCount(media: ProfileSharedMediaView, index: Int): Int {
        val page = media.pager.getViewPages()?.getOrNull(0) ?: return 0
        val list = (page as? ViewGroup)?.getChildAt(0) as? RecyclerListView ?: return 0
        return list.adapter?.itemCount ?: 0
    }

    private fun scrollPageToEnd(media: ProfileSharedMediaView) {
        val page = media.pager.getViewPages()?.getOrNull(0) ?: return
        val list = (page as? ViewGroup)?.getChildAt(0) as? RecyclerListView ?: return
        val count = list.adapter?.itemCount ?: return
        // A page the pager just added may not have been measured by the harness's layout path:
        // the harness lays out from the activity's content view, which re-measures only what
        // requested layout, and the profile list swallows the pager's request while it is in its
        // own layout. Lay the pager (and with it the page and its list) out the way the platform
        // would, then scroll: the list consumes the deferred position in that layout pass.
        val pager = media.pager
        forcePagerLayout(pager)
        list.scrollToPosition(count - 1)
        forcePagerLayout(pager)
        harness.idle()
    }

    /** Measures and lays the pager out at its own size, which lays its pages out with it. */
    private fun forcePagerLayout(pager: android.view.View) {
        if (pager.measuredWidth <= 0 || pager.measuredHeight <= 0) {
            return
        }
        pager.forceLayout()
        pager.measure(
            View.MeasureSpec.makeMeasureSpec(pager.measuredWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(pager.measuredHeight, View.MeasureSpec.EXACTLY),
        )
        pager.layout(pager.left, pager.top, pager.right, pager.bottom)
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

    /** A profile with the real linux.do action fields, shaped like the site's own answer. */
    private fun otherUserProfileJson(canFollow: Boolean, followed: Boolean, canMute: Boolean): String {
        val profile = JSONObject(harness.fixture("user-profile.json"))
        profile.getJSONObject("user").apply {
            put("username", "momo199412")
            put("name", "momo")
            put("can_send_private_message_to_user", true)
            put("can_follow", canFollow)
            put("is_followed", followed)
            put("can_mute_user", canMute)
            put("muted", false)
        }
        return profile.toString()
    }

    private fun detailValue(label: String): String? {
        val list = listView() ?: return null
        for (index in 0 until list.childCount) {
            val cell = list.getChildAt(index)
            if (cell is TextDetailCell && cell.valueTextView.text.toString() == label) {
                return cell.textView.text.toString()
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val declared = target.javaClass.getDeclaredField(name)
        declared.isAccessible = true
        return declared.get(target) as T
    }

    private fun <T : View> findView(root: View, type: Class<T>): T? {
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

    /** The states this pass asked to keep, under `.amp/in/artifacts/ui-polish/`. */
    private fun copyToUiPolish(rendered: java.io.File, name: String): java.io.File {
        val target = java.io.File(rendered.parentFile.parentFile, "ui-polish/$name.png")
        target.parentFile?.mkdirs()
        rendered.copyTo(target, overwrite = true)
        println("ui-polish: wrote ${target.absolutePath} (${target.length()} bytes)")
        return target
    }

    private fun magentaPng(width: Int, height: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(255, 0, 255))
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private companion object {
        const val TOPIC_ID = 4002L
    }
}
