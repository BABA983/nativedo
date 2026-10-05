package io.github.baba983.nativedo.settings

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
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
import org.telegram.ui.MainTabsLayout
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
        val rows = rowTextsAcrossTheList()
        println("settings: rows = $rows")
        assertTrue("the settings tab is this client's own screen", "外观" in rows)
        assertTrue("and it carries the appearance entry", "主题" in rows)
        assertTrue("the bookmarks entry is kept here as well", "书签" in rows)
        assertTrue("with the account the session belongs to", rows.any { it.startsWith("@baba") })
        assertTrue("and the in-app link choice", "在应用内打开链接" in rows)
        harness.renderApp("settings")
        harness.assertNoTelegramSideEffects()

        clickNamedRow("主题")
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
        clickNamedRow("书签")
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
        assertTrue("the reader is signed in to start with", container.cookieJar.hasAuthenticatedCookies())

        clickNamedRow("退出登录")
        val confirm = ShadowDialog.getLatestDialog() as? AlertDialog
            ?: throw AssertionError("leaving the account asked nothing")
        println("settings: the dialog says " + dialogTexts(confirm))
        confirmButton(confirm, "退出登录")

        harness.await("the login screen") { currentFragmentName(shell) == "LoginWebViewFragment" }
        assertFalse(
            "the cookies the session was made of are gone",
            container.cookieJar.hasAuthenticatedCookies(),
        )
        assertEquals(
            "the signed-out account is not remembered either",
            null,
            container.sessionStore.snapshot(),
        )
        harness.layoutApp()
        assertTrue(
            "the tab shell left with the session",
            viewsUnder(harness.activity.findViewById(android.R.id.content)).none { it is MainTabsLayout },
        )
        harness.renderApp("settings-signed-out")
        harness.assertNoTelegramSideEffects()
    }

    /** Returning from another screen must retain the verification page, not rebuild its menu and WebView. */
    @Test
    fun returningToVerificationRetainsItsPageAndSingleFinishButton() {
        val shell = openVerificationFromSettings()
        val fragment = currentFragment(shell) as org.telegram.ui.ActionBar.BaseFragment
        val web = verificationWebView()
        shell.present(io.github.baba983.nativedo.settings.SettingsFragment())
        harness.await("settings above verification") { currentFragmentName(shell) == "SettingsFragment" }
        back()
        harness.await("return to verification") { currentFragment(shell) === fragment }
        harness.layoutApp()
        assertTrue("navigation retains the page and its history", web === verificationWebView())
        assertEquals("the verification page has one explicit finish button", 1,
            viewsUnder(fragment.actionBar).filterIsInstance<org.telegram.ui.ActionBar.ActionBarMenuItem>()
                .count { "完成验证" in viewsUnder(it).filterIsInstance<TextView>().map { text -> text.text.toString() } })
        assertNoWrites()
        harness.assertNoTelegramSideEffects()
    }

    /** Existing auth must not auto-close recovery or turn Back into a successful completion. */
    @Test
    fun verificationKeepsTheSessionAndCancelDoesNotCompleteItEvenWithHistory() {
        val shell = openVerificationFromSettings()
        val identity = container.sessionStore.snapshot()
        val generation = container.sessionCoordinator.currentRequestContext()
        val cookies = container.cookieJar.requestCookieHeader(site.baseUrl.toHttpUrl())
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
        assertEquals(cookies, container.cookieJar.requestCookieHeader(site.baseUrl.toHttpUrl()))
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
        assertTrue(container.cookieJar.hasAuthenticatedCookies())
        assertEquals("renewed-clearance", io.github.baba983.nativedo.core.session.SharedCookieJar.cookieValue(
            container.cookieJar.requestCookieHeader(site.baseUrl.toHttpUrl()), "cf_clearance"))
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
        assertTrue("the account area offers non-destructive recovery", hasRow("打开验证页"))
        clickNamedRow("打开验证页")
        harness.await("verification WebView") { currentFragmentName(shell) == "LoginWebViewFragment" }
        val web = verificationWebView()
        harness.await("cookies before the first navigation") {
            org.robolectric.Shadows.shadowOf(web).lastLoadedUrl != null
        }
        assertEquals("recovery navigates to the site's challenge, not its login page", site.url("/challenge"),
            org.robolectric.Shadows.shadowOf(web).lastLoadedUrl)
        assertTrue("the browser keeps the existing auth", container.cookieJar.hasAuthenticatedCookies())
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

    private fun currentFragmentName(shell: NativeDoActivity): String? = harness.visibleFragmentName()

    private fun currentFragment(shell: NativeDoActivity): Any? = harness.visibleFragment()

    private fun back() {
        (harness.activity as NativeDoActivity).onBackPressed()
        harness.idle()
    }

    private fun tapTab(index: Int) {
        harness.drawApp()
        val tabs = viewsUnder(harness.activity.findViewById(android.R.id.content))
            .filterIsInstance<MainTabsLayout>().firstOrNull()
            ?: throw AssertionError("the shell shows no tab strip")
        val tab = tabs.getChildAt(index)
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        for (action in intArrayOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            tabs.dispatchTouchEvent(event)
            harness.idle()
        }
    }

    /**
     * The rows the settings list draws, top to bottom. A row's value is upstream's animated text
     * view, which paints its own glyphs rather than being a {@link TextView}, and a red button row
     * is upstream's {@link org.telegram.ui.ActionBar.SimpleTextView}, so all three are read.
     */
    private fun rowTexts(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        return (0 until list.childCount).flatMap { index -> textsOf(list.getChildAt(index)) }
    }

    /**
     * The settings list is taller than the window (a 188dp header and eight sections), so no one
     * viewport shows every row: the test walks the list the way the reader scrolls it and collects
     * the rows page by page. The walk advances one position at a time - the same stepping the
     * profile tests use, because a pending `scrollToPosition` is only bound by the next layout.
     */
    private fun rowTextsAcrossTheList(): List<String> {
        val list = contentListOf(harness.activity) ?: return emptyList()
        val count = list.adapter?.itemCount ?: 0
        val texts = LinkedHashSet<String>()
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            texts += rowTexts()
        }
        list.scrollToPosition(0)
        harness.layoutApp()
        return texts.toList()
    }

    /** Whether [label] is a row of the settings list, wherever in the list it sits. */
    private fun hasRow(label: String): Boolean {
        val list = contentListOf(harness.activity) ?: return false
        val count = list.adapter?.itemCount ?: 0
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            if (rowTexts().contains(label)) {
                return true
            }
        }
        return false
    }

    private fun textsOf(view: View): List<String> {
        val texts = viewsUnder(view)
        return texts.filterIsInstance<TextView>().map { it.text.toString() } +
            texts.filterIsInstance<org.telegram.ui.ActionBar.SimpleTextView>()
                .mapNotNull { it.text?.toString() } +
            texts.filterIsInstance<org.telegram.ui.Components.AnimatedTextView>()
                .mapNotNull { it.text?.toString() }
    }

    /** A tap on the row labelled [label], scrolling the list row by row when it is below the fold. */
    private fun clickNamedRow(label: String) {
        harness.layoutApp()
        val list = contentListOf(harness.activity) ?: throw AssertionError("the settings list is gone")
        val count = list.adapter?.itemCount ?: 0
        for (position in 0 until count) {
            list.scrollToPosition(position)
            harness.layoutApp()
            val child = (0 until list.childCount).map { list.getChildAt(it) }
                .firstOrNull { label in textsOf(it) }
            if (child != null) {
                list.clickItem(child, list.getChildAdapterPosition(child))
                harness.idle()
                return
            }
        }
        throw AssertionError("no $label row")
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
