package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.clickMenuItem
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.Components.RecyclerListView

/**
 * The reader's own notification level for one topic, through the topic overflow.
 *
 * <p>The failures this pins: a level control that ignores `details.notification_level` and always
 * opens on the default; a choice that writes the wrong field or the wrong number; and, above all,
 * a level shown as changed before the site took the write - a refusal must leave the reader's level
 * exactly where it was.</p>
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
internal class TopicNotificationLevelTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        // The topic payload's details name the reader's own level: tracking (2).
        site.serveTopic(TOPIC_ID) { post -> post.put("cooked", "<p>一行值得留下的话。</p>") }
        site.serveJson("t/$TOPIC_ID/notifications.json", """{"success":"OK"}""")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun theCheckedChoicesStartAtTheSitesLevelAndTheChosenOneIsWritten() {
        presentTopic()

        openTopicOverflow()
        val entry = overflowEntry()
        println("notification-level: overflow entry = ${entry.textView.text}")
        assertTrue(
            "the entry starts at the site's own level (tracking)",
            entry.textView.text.toString().contains("跟踪"),
        )

        // The entry opens the four checked choices; the site's level is the checked one.
        clickRowContaining(latestItemOptionsMenu(harness.activity), "通知级别")
        val rows = latestItemOptionsMenu(harness.activity)
        val labels = menuItemLabels(rows)
        println("notification-level: choices = $labels")
        assertEquals(
            "the four levels the site offers, in order",
            listOf("静音", "常规", "跟踪", "关注"),
            labels,
        )
        // Upstream's checked item carries a real check view; only the site's own level is checked.
        val checked = rows.map { row -> row.checkView?.isChecked == true }
        println("notification-level: checked = $checked")
        assertEquals(
            "only the reader's own level starts checked",
            listOf(false, false, true, false),
            checked,
        )
        // And that check is what the platform is told: the checked row describes itself as a
        // checked CheckBox, which is the accessibility contract the reader's screen reader needs.
        val tracking = rows[2]
        val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
        tracking.onInitializeAccessibilityNodeInfo(info)
        assertTrue("the chosen level is checkable", info.isCheckable)
        assertTrue("and reports itself checked", info.isChecked)
        assertEquals(
            "as the CheckBox upstream builds it",
            "android.widget.CheckBox",
            info.className.toString(),
        )
        // The menu is its own window, so only the composited page shows it over the topic it dims.
        harness.renderComposite(".amp/in/artifacts/nativedo/topic-notification-level-menu.png")

        // Choosing watching writes level 3.
        clickMenuItem(rows, "关注")
        harness.await("the site to be told the new level") {
            site.recordedRequests.any { it == "POST /t/$TOPIC_ID/notifications.json" }
        }
        val body = site.requestBodyFor("t/$TOPIC_ID/notifications.json").orEmpty()
        println("notification-level: body = $body csrf = " + site.csrfHeaderFor("t/$TOPIC_ID/notifications.json"))
        assertTrue("the write names the level the reader chose", body.contains("notification_level=3"))
        assertEquals(
            "and carries the session's CSRF token",
            "csrf-token-demo",
            site.csrfHeaderFor("t/$TOPIC_ID/notifications.json"),
        )

        // The site took it, so the level moved; the overflow entry now says watching.
        val topic = harness.visibleFragment() as TopicFragment
        harness.await("the level to settle") {
            (fragmentField(topic, "topicNotificationLevel") as Int) == 3
        }
        openTopicOverflow()
        val updated = overflowEntry().textView.text.toString()
        println("notification-level: entry after the write = $updated")
        assertTrue("the entry follows the site to watching", updated.contains("关注"))
        harness.layoutApp()
        harness.renderApp("topic-notification-watching")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aRefusedLevelKeepsTheOneTheReaderHad() {
        site.serveResponse("t/$TOPIC_ID/notifications.json") {
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["不能设置"]}""")
        }
        presentTopic()

        openTopicOverflow()
        clickRowContaining(latestItemOptionsMenu(harness.activity), "通知级别")
        clickMenuItem(latestItemOptionsMenu(harness.activity), "静音")

        harness.await("the site to be asked") {
            site.recordedRequests.any { it == "POST /t/$TOPIC_ID/notifications.json" }
        }
        val texts = awaitBulletin("设置失败")
        println("notification-level: refusal bulletin = $texts")

        // The write was refused, so the level is still tracking - the row says what it said.
        openTopicOverflow()
        val entry = overflowEntry().textView.text.toString()
        println("notification-level: entry after refusal = $entry")
        assertTrue("a refusal keeps the level the reader had", entry.contains("跟踪"))
        assertTrue("and it never became watching", entry.contains("关注").not())
        harness.renderApp("topic-notification-refused")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ helpers

    private fun presentTopic() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Follow"))
        harness.await("the topic to load") { rowCount() == 1 }
        harness.layoutApp()
    }

    /** Opened the way the reader opens it: a tap on the topic bar's own overflow item. */
    private fun openTopicOverflow() {
        harness.topFragment()!!.actionBar.createMenu().getItem(1).performClick()
        harness.idle()
    }

    /** The overflow's notification-level row, by its label. */
    private fun overflowEntry(): ActionBarMenuSubItem =
        latestItemOptionsMenu(harness.activity)
            .firstOrNull { menuItemLabels(listOf(it)).first().contains("通知级别") }
            ?: throw AssertionError(
                "the topic overflow has no notification-level row among " +
                    menuItemLabels(latestItemOptionsMenu(harness.activity)),
            )

    private fun clickRowContaining(rows: List<ActionBarMenuSubItem>, needle: String) {
        val row = rows.firstOrNull { menuItemLabels(listOf(it)).first().contains(needle) }
            ?: throw AssertionError("no menu row contains \"$needle\" among " + menuItemLabels(rows))
        row.performClick()
        harness.idle()
    }

    private fun awaitBulletin(expected: String): List<String> {
        repeat(5_000) {
            harness.idle(1)
            val bulletin = org.telegram.ui.Components.Bulletin.getVisibleBulletin()
            if (bulletin != null) {
                val texts = bulkText(bulletin.getLayout())
                if (texts.any { it.contains(expected) }) return texts
            }
            java.util.concurrent.locks.LockSupport.parkNanos(500_000L)
        }
        throw AssertionError("the refusal Bulletin never appeared")
    }

    private fun bulkText(view: android.view.View): List<String> = when (view) {
        is android.widget.TextView -> listOf(view.text.toString())
        is org.telegram.ui.ActionBar.SimpleTextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { bulkText(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun rowCount(): Int = contentRowCount(harness)

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private fun fragmentField(fragment: org.telegram.ui.ActionBar.BaseFragment, name: String): Any? {
        val field = TopicFragment::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.get(fragment)
    }

    private companion object {
        const val TOPIC_ID = 4100L
    }
}
