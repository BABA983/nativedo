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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.FilledTabsView
import org.telegram.ui.Components.RecyclerListView

/**
 * The settings tab.
 *
 * <p>The failure this pins: a reader has one place for what they change about the app, and the
 * account lives in it. The tab has to be this client's own screen - upstream's appearance page is
 * one row in, not the whole tab - built from upstream's cell styles, and leaving the account has to
 * actually leave it: the cookies the session is made of are gone before the login page opens, or
 * that page opens with the reader still signed in.</p>
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
internal class SettingsTabTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("u/baba/bookmarks.json", "user-bookmarks.json")
        site.serveFixture("u/baba.json", "user-profile.json")
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
    fun theTabIsOurOwnScreenAndItsRowsOpenTheThingsTheyName() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName(shell) == "TopicListFragment" }

        tapTab(2)
        harness.await("the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        harness.drawApp()
        val rows = rowTexts()
        println("settings: rows = $rows")
        assertTrue("the settings tab is this client's own screen", "外观" in rows)
        assertTrue("and it carries the appearance entry", "主题" in rows)
        assertTrue("the bookmarks entry is kept here as well", "书签" in rows)
        assertTrue("with the account the session belongs to", rows.any { it == "@baba" })
        harness.renderApp("settings")
        harness.assertNoTelegramSideEffects()

        clickRow(1)
        harness.await("upstream's appearance page") { currentFragmentName(shell) == "ThemeActivity" }
        val themeField = org.telegram.ui.ThemeActivity::class.java
            .getDeclaredField("currentType").apply { isAccessible = true }
        assertEquals(
            "the appearance row opens upstream's own page for it",
            org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC,
            themeField.get(currentFragment(shell)) as Int,
        )
        harness.layoutApp()
        harness.renderApp("settings-theme")

        back()
        harness.await("back on the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        clickRow(3)
        harness.await("the bookmark list") { currentFragmentName(shell) == "BookmarksFragment" }

        back()
        harness.await("back on the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun leavingTheAccountClearsTheSessionBeforeTheLoginPageOpens() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { currentFragmentName(shell) == "TopicListFragment" }
        tapTab(2)
        harness.await("the settings screen") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        assertTrue("the reader is signed in to start with", container.cookieStore.hasAuthenticatedCookies())

        clickNamedRow("退出登录")
        val confirm = ShadowDialog.getLatestDialog() as? AlertDialog
            ?: throw AssertionError("leaving the account asked nothing")
        println("settings: the dialog says " + dialogTexts(confirm))
        confirmButton(confirm, "退出登录")

        harness.await("the login screen") { currentFragmentName(shell) == "LoginWebViewFragment" }
        assertFalse(
            "the cookies the session was made of are gone",
            container.cookieStore.hasAuthenticatedCookies(),
        )
        assertEquals(
            "the signed-out account is not remembered either",
            null,
            container.sessionStore.snapshot(),
        )
        harness.layoutApp()
        harness.renderApp("settings-signed-out")
        harness.assertNoTelegramSideEffects()
    }

    /** Existing auth must not auto-close recovery or turn Back into a successful completion. */
    @Test
    fun verificationKeepsTheSessionAndCancelDoesNotCompleteItEvenWithHistory() {
        val shell = openVerificationFromSettings()
        val identity = container.sessionStore.snapshot()
        val generation = container.sessionCoordinator.currentRequestContext()
        val cookies = container.cookieStore.load(site.baseUrl.toHttpUrl())
        val draftKey = io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey(site.baseUrl, "baba", 101)
        val readKey = io.github.baba983.nativedo.feature.topic.data.TopicReadPositionKey(site.baseUrl, "baba", 101)
        val draft = io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft("kept draft", 7)
        container.topicReplyDraftStore.save(draftKey, draft)
        container.topicReadPositionStore.save(readKey, 19)
        val web = verificationWebView()
        finishWebPage(web, site.baseUrl)
        harness.idleClock(12_000)
        assertEquals("existing cookies leave the page visible", "LoginWebViewFragment", currentFragmentName(shell))
        assertEquals("page load is not session recovery", generation, container.sessionCoordinator.currentRequestContext())
        org.robolectric.Shadows.shadowOf(web).setCanGoBack(true)
        back()
        harness.await("cancel back to settings") { currentFragmentName(shell) == "SettingsFragment" }
        assertEquals(identity, container.sessionStore.snapshot())
        assertEquals(cookies, container.cookieStore.load(site.baseUrl.toHttpUrl()))
        assertEquals(draft, container.topicReplyDraftStore.load(draftKey))
        assertEquals(19, container.topicReadPositionStore.load(readKey))
        assertEquals(generation, container.sessionCoordinator.currentRequestContext())
        assertNoWrites()
        harness.assertNoTelegramSideEffects()
    }

    /** A late DOM callback after cancellation must not announce recovery or replace the stack. */
    @Test
    fun cancelDuringExplicitCompletionDiscardsTheLateResult() {
        val shell = openVerificationFromSettings()
        val generation = container.sessionCoordinator.currentRequestContext()
        val web = verificationWebView()
        finishWebPage(web, site.baseUrl)
        completeVerification(shell)
        val callback = org.robolectric.Shadows.shadowOf(web).lastEvaluatedJavascriptCallback
            ?: throw AssertionError("completion never asked the page for identity")
        back()
        harness.await("cancel the in-flight completion") { currentFragmentName(shell) == "SettingsFragment" }
        callback.onReceiveValue(org.json.JSONObject.quote("""{"username":"baba","name":"Baba"}"""))
        harness.idle()
        assertEquals("SettingsFragment", currentFragmentName(shell))
        assertEquals("a cancelled completion cannot publish recovery", generation,
            container.sessionCoordinator.currentRequestContext())
        assertNoWrites()
        harness.assertNoTelegramSideEffects()
    }

    /** Failure and external navigation cannot complete; an explicit same-origin finish only syncs. */
    @Test
    fun onlyAnExplicitFinishOnALoadedSitePageSyncsWithoutAWriteProbe() {
        val shell = openVerificationFromSettings()
        val web = verificationWebView()
        val generation = container.sessionCoordinator.currentRequestContext()
        val client = org.robolectric.Shadows.shadowOf(web).webViewClient
        web.loadUrl(site.baseUrl)
        client.onPageStarted(web, site.baseUrl, null)
        client.onReceivedHttpError(web, object : android.webkit.WebResourceRequest {
            override fun getUrl() = android.net.Uri.parse(site.baseUrl)
            override fun isForMainFrame() = true
            override fun isRedirect() = false
            override fun hasGesture() = false
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = emptyMap<String, String>()
        }, android.webkit.WebResourceResponse("text/html", "UTF-8", 403, "Forbidden", emptyMap(), null))
        client.onPageFinished(web, site.baseUrl)
        completeVerification(shell)
        assertEquals("a failed page cannot complete", generation, container.sessionCoordinator.currentRequestContext())
        assertEquals("the challenge page stays available", View.VISIBLE, web.visibility)
        finishWebPage(web, "https://example.org/oauth")
        completeVerification(shell)
        assertEquals("neither can an external page", generation, container.sessionCoordinator.currentRequestContext())

        finishWebPage(web, site.baseUrl)
        android.webkit.CookieManager.getInstance().setCookie(site.baseUrl, "cf_clearance=renewed-clearance; Path=/")
        completeVerification(shell)
        val shadow = org.robolectric.Shadows.shadowOf(web)
        shadow.lastEvaluatedJavascriptCallback!!.onReceiveValue(
            org.json.JSONObject.quote("""{"username":"baba","name":"Baba"}"""))
        harness.await("explicit completion back to settings") { currentFragmentName(shell) == "SettingsFragment" }
        assertTrue(container.cookieStore.hasAuthenticatedCookies())
        assertEquals("renewed-clearance", container.cookieStore.load(site.baseUrl.toHttpUrl())
            .first { it.name == "cf_clearance" }.value)
        assertTrue("completion actually ran the session use case",
            generation != container.sessionCoordinator.currentRequestContext())
        assertNoWrites()
        harness.assertNoTelegramSideEffects()
    }

    private fun openVerificationFromSettings(): NativeDoActivity {
        val shell = harness.bootApp()
        harness.await("the topic list") { currentFragmentName(shell) == "TopicListFragment" }
        tapTab(2)
        harness.await("settings") { currentFragmentName(shell) == "SettingsFragment" }
        harness.layoutApp()
        assertTrue("the account area offers non-destructive recovery", "打开验证页" in rowTexts())
        clickNamedRow("打开验证页")
        harness.await("verification WebView") { currentFragmentName(shell) == "LoginWebViewFragment" }
        val web = verificationWebView()
        harness.await("cookies before the first navigation") {
            org.robolectric.Shadows.shadowOf(web).lastLoadedUrl != null
        }
        assertEquals("the verification entry uses a GET navigation", site.url("/login"),
            org.robolectric.Shadows.shadowOf(web).lastLoadedUrl)
        assertTrue("the browser keeps the existing auth", container.cookieSyncBridge.hasAuthenticatedCookiesInWebView())
        return shell
    }

    private fun verificationWebView(): android.webkit.WebView =
        viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<android.webkit.WebView>().last()

    private fun finishWebPage(web: android.webkit.WebView, url: String) {
        web.loadUrl(url)
        val client = org.robolectric.Shadows.shadowOf(web).webViewClient
        client.onPageStarted(web, url, null)
        client.onPageFinished(web, url)
        harness.idle()
    }

    private fun completeVerification(shell: NativeDoActivity) {
        val fragment = currentFragment(shell) as org.telegram.ui.ActionBar.BaseFragment
        val item = viewsUnder(fragment.actionBar).filterIsInstance<org.telegram.ui.ActionBar.ActionBarMenuItem>()
            .single { "完成验证" in viewsUnder(it).filterIsInstance<TextView>().map { text -> text.text.toString() } }
        item.performClick()
        harness.idle()
    }

    private fun assertNoWrites() {
        assertTrue("verification does not probe posts, timings, upload or logout",
            site.recordedRequests.all { it.startsWith("GET ") || it.startsWith("HEAD ") })
    }

    // ------------------------------------------------------------------ shell traversal

    private fun currentFragmentName(shell: NativeDoActivity): String? = currentFragment(shell)
        ?.javaClass?.simpleName

    private fun currentFragment(shell: NativeDoActivity): Any? {
        val layoutField = shell.javaClass.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        val layout = layoutField.get(shell)!!
        val stackField = layout.javaClass.getDeclaredField("fragmentsStack").apply { isAccessible = true }
        val stack = stackField.get(layout) as List<*>
        return stack.lastOrNull()
    }

    private fun back() {
        (harness.activity as NativeDoActivity).onBackPressed()
        harness.idle()
    }

    private fun tapTab(index: Int) {
        // The strip's touch targets are laid down while it draws, so it has to have drawn once.
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<FilledTabsView>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val field = FilledTabsView::class.java.getDeclaredField("bounds").apply { isAccessible = true }
        val bounds = field.get(tabs) as Array<*>
        val rect = bounds[index] as android.graphics.RectF
        val x = rect.centerX()
        val y = rect.centerY()
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
    }

    /**
     * The rows the settings list draws, top to bottom. A row's value is upstream's animated text
     * view, which paints its own glyphs rather than being a {@link TextView}, so both are read.
     */
    private fun rowTexts(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount).flatMap { index ->
            val texts = viewsUnder(list.getChildAt(index))
            texts.filterIsInstance<TextView>().map { it.text.toString() } +
                texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>()
                    .mapNotNull { it.text?.toString() }
        }.filter { it.isNotEmpty() }
    }

    /** A tap on the row at the adapter's [position], the way the list hands one over. */
    private fun clickNamedRow(label: String) {
        harness.layoutApp()
        val list = contentListOf(harness.activity) ?: throw AssertionError("the settings list is gone")
        val child = (0 until list.childCount).map { list.getChildAt(it) }
            .single { label in viewsUnder(it).filterIsInstance<TextView>().map { text -> text.text.toString() } }
        list.clickItem(child, list.getChildAdapterPosition(child))
        harness.idle()
    }

    private fun clickRow(position: Int) {
        val list = contentListOf(harness.activity) ?: throw AssertionError("the settings list is gone")
        harness.layoutApp()
        val first = (list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.findFirstVisibleItemPosition() ?: 0
        val child = list.getChildAt(position - first)
            ?: throw AssertionError("no row drew at $position")
        list.clickItem(child, position)
        harness.idle()
    }

    private fun dialogTexts(dialog: AlertDialog): List<String> =
        viewsUnder(dialog.window!!.decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }
            .filter { it.isNotEmpty() }

    /** The dialog's own button with that label: the reader's answer, not the title that says it. */
    private fun confirmButton(dialog: AlertDialog, label: String) {
        val button = viewsUnder(dialog.window!!.decorView)
            .filterIsInstance<TextView>()
            .lastOrNull { it.text.toString() == label }
            ?: throw AssertionError("the dialog has no $label button")
        println("settings: pressing " + button.text + " (" + button.javaClass.simpleName + ")")
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
