package io.github.baba983.nativedo.settings

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoActivity
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.MainTabsLayout
import java.io.File

/**
 * The storage screen and its one dangerous button.
 *
 * <p>The failure this pins: the cache this client clears lives in the same app-private storage as
 * everything else, so a sweep that is too wide would take the reader's login with it. Clearing has
 * to empty the media directories and leave the session - cookie jar and stored identity - exactly
 * where it was.</p>
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
internal class StorageTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba.json", "user-preferences.json")
        NativeDo.setContainerFactory { context ->
            site.authenticatedContainer(context).also { container = it }
        }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun clearingTheCacheEmptiesTheMediaFilesAndKeepsTheSession() {
        val shell = bootToSettings()

        val cacheDir = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE)
            ?: throw AssertionError("the media directories are not initialised")
        val cached = File(cacheDir, "nativedo-cache-test.bin")
        cached.writeBytes(ByteArray(4096))
        assertTrue("the cache file is on disk before the sweep", cached.exists())

        clickNamedRow("缓存占用与清理")
        harness.await("the storage screen") { currentFragmentName(shell) == "StorageFragment" }
        harness.layoutApp()
        clickNamedRow("清理缓存")
        val confirm = ShadowDialog.getLatestDialog() as? AlertDialog
            ?: throw AssertionError("clearing the cache asked nothing")
        confirmButton(confirm, "清理")

        harness.await("the cached file to be swept") { !cached.exists() }
        assertTrue(
            "clearing a cache must not clear the cookies the session is made of",
            container.cookieJar.hasAuthenticatedCookies(),
        )
        assertNotNull(
            "or the identity the app stores",
            container.sessionStore.snapshot(),
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ shell traversal

    private fun bootToSettings(): NativeDoActivity {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName(shell) == "TopicListFragment" }
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<MainTabsLayout>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val tab = tabs.getChildAt(2)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
        harness.await("the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        return shell
    }

    private fun currentFragmentName(shell: NativeDoActivity): String? = harness.visibleFragmentName()

    private fun textsOf(view: View): List<String> {
        val texts = viewsUnder(view)
        return texts.filterIsInstance<TextView>().map { it.text.toString() } +
            texts.filterIsInstance<org.telegram.ui.ActionBar.SimpleTextView>()
                .mapNotNull { it.text?.toString() } +
            texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>()
                .mapNotNull { it.text?.toString() }
    }

    /** A tap on the row labelled [label], scrolling the list down when the row is below the fold. */
    private fun clickNamedRow(label: String) {
        harness.layoutApp()
        val list = contentListOf(harness.activity) ?: throw AssertionError("no list on the screen")
        fun find(): View? = (0 until list.childCount).map { list.getChildAt(it) }
            .firstOrNull { label in textsOf(it) }
        var child = find()
        if (child == null) {
            list.scrollToPosition((list.adapter?.itemCount ?: 1) - 1)
            harness.layoutApp()
            child = find()
        }
        val row = child ?: throw AssertionError("no $label row")
        list.clickItem(row, list.getChildAdapterPosition(row))
        harness.idle()
    }

    /** The dialog's own button with that label: the reader's answer, not the title that says it. */
    private fun confirmButton(dialog: AlertDialog, label: String) {
        val button = viewsUnder(dialog.window!!.decorView)
            .filterIsInstance<TextView>()
            .lastOrNull { it.text.toString() == label }
            ?: throw AssertionError("the dialog has no $label button")
        button.performClick()
        harness.idle()
    }

    private fun viewsUnder(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    walk(view.getChildAt(index))
                }
            }
        }
        walk(root)
        return found
    }
}
