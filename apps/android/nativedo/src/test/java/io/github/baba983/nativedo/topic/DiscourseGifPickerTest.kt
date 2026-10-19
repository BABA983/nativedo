// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
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
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SearchField
import org.telegram.ui.Components.chat.ChatInputViewsContainer
import org.telegram.ui.Components.inset.WindowInsetsStateHolder

/**
 * The site-backed GIF picker, driven through the production new-topic screen.
 *
 * <p>The panel is not mounted by hand: [DiscourseComposerTools] creates it for the real
 * [NewTopicFragment], adds both panels to the composer's own [ChatInputViewsContainer], and the
 * reader opens the GIF panel from the site emoji panel's GIF affordance. The requests go through
 * [io.github.baba983.nativedo.core.network.DiscourseGifClient] against [FakeDiscourseSite], so the
 * `/gifs/categories.json` and `/gifs/search.json` wire format and the DTO mapping run for real.</p>
 *
 * <p>Each test names a failure it pins. The capability cases are deliberately about what the server
 * answered, not about a guessed setting: an absent route is not a working picker, a refusal is not
 * a Cloudflare challenge and not a disabled feature, and a challenge is not a logout. A transient
 * answer must stay retryable and must never be remembered as "this site has no GIFs".</p>
 *
 * <p>Native graphics renders the same mounted picker that these behavior assertions drive.</p>
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
internal class DiscourseGifPickerTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()
    private val screens = mutableListOf<Screen>()

    @Before
    fun setUp() {
        site.start()
        site.serveFixture("categories.json", "categories.json")
        site.serveFixture("gifs/categories.json", "gif-categories.json")
        site.serveFixture("gifs/search.json", "gif-search.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        screens.forEach { screen -> screen.fragment.onFragmentDestroy() }
        harness.idleClock(500)
        screens.clear()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    // ------------------------------------------------------------------ capability + entry

    /**
     * Failure mode: constructing or opening the emoji panel quietly probes GIFs, so a site without
     * the endpoint pays for a feature nobody asked for, and the GIF affordance is offered as a
     * working mode before the server has said it is. The emoji catalog must remain the panel's own
     * content; GIFs are fetched only when the reader chooses the GIF affordance, and the categories
     * read must carry the shared session.
     */
    @Test
    fun gifsAreFetchedOnlyWhenTheReaderAsksAndTheCatalogReadUsesTheSession() {
        val screen = openScreen()
        site.serveJson(
            "emojis.json",
            catalog("smileys" to listOf("smile" to site.url("emoji/smile.png"))),
        )
        site.serveImage("emoji/smile.png", solidPng(48, Color.rgb(0, 128, 255)))
        site.serveImage("gifs/category-trending.png", solidPng(48, Color.rgb(220, 40, 40)))
        site.serveImage("gifs/category-happy.png", solidPng(48, Color.rgb(40, 180, 80)))

        openEmoji(screen)
        harness.layoutApp()
        harness.await("the site emoji grid") { emojiGrid(screen)?.adapter?.itemCount == 3 }
        assertFalse(
            "opening the site emoji panel must not fetch GIFs",
            site.requestPaths.contains("gifs/categories.json"),
        )

        // The normal site emoji path still inserts its own shortcode.
        screen.field.setText("hello")
        screen.field.setSelection(2)
        clickEmoji(screen, 0)
        assertEquals("he:smile:llo", screen.field.text.toString())

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the site GIF categories") { gifGrid(screen)?.adapter?.itemCount == 2 }
        assertTrue(
            "the explicit GIF affordance is what loads the categories",
            site.requestPaths.contains("gifs/categories.json"),
        )
        assertNotNull(
            "the GIF categories read carries the site session",
            site.cookieHeaderFor("gifs/categories.json"),
        )
        assertFalse("the emoji panel yields to the GIF panel", screen.picker.isOpen())
        assertTrue("the GIF panel is the open input", screen.gif.isOpen())
        harness.await("category thumbnails displayed") {
            harness.layoutApp()
            descendants(requireNotNull(gifGrid(screen))).filterIsInstance<android.widget.ImageView>()
                .count { it.visibility == View.VISIBLE && it.drawable != null } >= 2
        }
        harness.idleClock(500)
        harness.layoutApp()
        harness.renderApp("composer-gif-categories")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ wire + insertion

    /**
     * Failure mode: the picker reads a provider's private format or a made-up field, so categories
     * never turn into search terms and results keep a zero size; or it inserts the provider's raw
     * id/HTML and sends the post itself. The site's own categories (`searchterm`), the provider's
     * `media_formats.webp`/`gif` with `dims`, and the composer's image Markdown at the caret are the
     * contract. Selecting is an edit, never a send.
     */
    @Test
    fun theSitesCategoriesOpenASearchAndSelectionInsertsImageMarkdownWithoutSending() {
        val screen = openScreen()
        site.serveJson(
            "emojis.json",
            catalog("smileys" to listOf("smile" to site.url("emoji/smile.png"))),
        )
        site.serveImage("gifs/category-trending.png", solidPng(48, Color.rgb(220, 40, 40)))
        site.serveImage("gifs/category-happy.png", solidPng(48, Color.rgb(40, 180, 80)))
        site.serveImage("gifs/dancing-cat.webp", solidPng(64, Color.rgb(40, 90, 214)))
        site.serveImage("gifs/clapping.gif", solidPng(64, Color.rgb(230, 200, 40)))

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the categories") { gifGrid(screen)?.adapter?.itemCount == 2 }

        // A category is a search term, not an insertable GIF.
        clickGifCell(screen, "Happy")
        harness.layoutApp()
        harness.await("the category search") { gifGrid(screen)?.adapter?.itemCount == 3 }
        val query = site.queriesFor("gifs/search.json").lastOrNull().orEmpty()
        assertTrue("the category's searchterm is the query: $query", query.contains("q=happy"))
        assertTrue("the first search page asks position 0: $query", query.contains("pos=0"))

        // The grid is the provider's own results: /gifs fixture has a fourth, unusable result.
        assertEquals(
            "the provider's GIF results become tiles",
            listOf("Dancing Cat", "Clapping", "No Preview"),
            gifTitles(screen),
        )
        harness.await("the GIF thumbnail request") {
            site.requestPaths.contains("gifs/dancing-cat.webp")
        }
        assertNull(
            "a provider CDN picture must never receive the site's private cookies",
            site.cookieHeaderFor("gifs/dancing-cat.webp"),
        )

        // Selecting writes the site component's own image Markdown at the caret, replacing a
        // selection and leaving the text around it untouched.
        harness.await("result thumbnails displayed") {
            harness.layoutApp()
            descendants(requireNotNull(gifGrid(screen))).filterIsInstance<android.widget.ImageView>()
                .count { it.visibility == View.VISIBLE && it.drawable != null } >= 2
        }
        harness.idleClock(500)
        harness.layoutApp()
        harness.renderApp("composer-gif-results")
        screen.field.setText("before  after")
        screen.field.setSelection(7, 13)
        clickGifCell(screen, "Dancing Cat")
        assertEquals(
            "the selected GIF is image Markdown with its provider dimensions at the caret",
            "before \n![Dancing Cat|480x270](${site.url("gifs/dancing-cat.webp")})\n",
            screen.field.text.toString(),
        )
        assertFalse("selecting a GIF closes the picker", screen.gif.isOpen())
        assertTrue(
            "selecting a GIF never sends the post",
            site.recordedRequests.none {
                it == "POST /posts.json" ||
                    it.startsWith("POST /chat/") ||
                    it.startsWith("PUT /posts/")
            },
        )
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun anEmptySearchIsAnEmptyStateAndClearingItRestoresTheCategories() {
        val screen = openScreen()
        site.serveResponse("gifs/search.json") {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[],"next":""}""")
        }

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the categories") { gifGrid(screen)?.adapter?.itemCount == 2 }

        gifSearch(screen)!!.searchEditText.setText("zzz")
        harness.idleClock(700)
        harness.await("the empty search state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == NO_RESULTS_TEXT
        }
        assertEquals("an empty search draws no tiles", 0, gifGrid(screen)!!.adapter!!.itemCount)
        assertFalse("an empty search is not an error", textOf(gifEmpty(screen)!!) == ERROR_TEXT)

        gifSearch(screen)!!.searchEditText.setText("")
        harness.idleClock(700)
        harness.await("the categories to return") { gifGrid(screen)!!.adapter!!.itemCount == 2 }
        assertFalse("the restored categories are not an empty state", visible(gifEmpty(screen)))
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ unavailable

    /**
     * Failure mode: a site that does not serve the GIF routes (404, the site's own
     * `ensure_gifs_enabled` answer) still shows an empty grid or a fake "no results", so the reader
     * believes GIFs exist but the search is broken. An absent endpoint is explained as unavailable,
     * and it is not remembered forever: once the site answers, the same panel can show its catalog.
     */
    @Test
    fun anAbsentGifEndpointIsExplainedAsUnavailableAndIsNotCachedAsDisabled() {
        val screen = openScreen()
        var enabled = false
        site.serveResponse("gifs/categories.json") {
            if (enabled) {
                categoryResponse()
            } else {
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"errors":["Not Found"]}""")
            }
        }

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the unavailable state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == UNAVAILABLE_TEXT
        }
        assertEquals("an absent route claims no GIF content", 0, gifGrid(screen)!!.adapter!!.itemCount)

        // An admin enables GIFs; the same panel's retry must not be blocked by a cached "disabled".
        enabled = true
        tap(gifEmpty(screen)!!)
        harness.await("the catalog after the site starts serving it") {
            gifGrid(screen)!!.adapter!!.itemCount == 2
        }
        assertFalse("a recovered catalog clears the unavailable state", visible(gifEmpty(screen)))
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Failure mode: every 403 is called "GIFs are disabled" or "the provider key is missing". A
     * plain 403 is the site refusing the read - a missing provider key and a denied permission are
     * indistinguishable from here - so it stays a retryable failure, not a capability claim; a
     * Cloudflare challenge is a 403 that must go to the existing WebView recovery; a login refusal
     * is a 401 that must expire the session.
     */
    @Test
    fun aRefusalIsRetryableWhileAChallengeAndALoginGoToSessionRecovery() {
        val screen = openScreen()
        site.serveResponse("gifs/categories.json") {
            MockResponse().setResponseCode(403)
        }

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the refusal state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == ERROR_TEXT
        }
        assertFalse(
            "a 403 is not reported as a site without GIFs",
            textOf(gifEmpty(screen)!!) == UNAVAILABLE_TEXT,
        )
        assertTrue(
            "a refusal does not expire the site session",
            screen.container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )

        // A challenge is a 403 too, but its evidence is a challenge, not a refusal.
        site.serveResponse("gifs/categories.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setHeader("Content-Type", "text/html")
                .setBody("<html><title>Just a moment...</title></html>")
        }
        tap(gifEmpty(screen)!!)
        harness.await("the challenge state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == SESSION_TEXT
        }
        assertFalse(
            "a challenge is not reported as a missing GIF capability",
            textOf(gifEmpty(screen)!!) == UNAVAILABLE_TEXT,
        )
        assertTrue(
            "a challenge does not prove a logout",
            screen.container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )

        // A real login refusal (401) goes through the existing session invalidation.
        site.serveResponse("gifs/categories.json") {
            MockResponse()
                .setResponseCode(401)
                .setHeader("Discourse-Logged-Out", "1")
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error_type":"not_logged_in"}""")
        }
        tap(gifEmpty(screen)!!)
        harness.await("the login state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == LOGIN_TEXT
        }
        assertTrue(
            "a login refusal expires the session through the existing recovery",
            screen.container.sessionCoordinator.sessionState.value is AppSessionState.Expired,
        )
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ transient

    /**
     * Failure mode: a rate limit or a malformed answer is treated as "this site has no GIFs", so a
     * later retry never happens. Transient failures are a retry state; they must not become the
     * unavailable state, and a successful retry must replace them with real tiles.
     */
    @Test
    fun aRateLimitOrMalformedAnswerStaysRetryableAndNeverBecomesUnavailable() {
        val screen = openScreen()
        var serving: MockResponse = MockResponse()
            .setResponseCode(429)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"errors":["rate limited"]}""")
        site.serveResponse("gifs/categories.json") { serving }

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the rate-limit state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == ERROR_TEXT
        }
        assertFalse(
            "a rate limit is not an unavailable site",
            textOf(gifEmpty(screen)!!) == UNAVAILABLE_TEXT,
        )

        serving = categoryResponse()
        tap(gifEmpty(screen)!!)
        harness.await("the retry to load categories") { gifGrid(screen)!!.adapter!!.itemCount == 2 }

        // A 200 whose body cannot be decoded is also transient, not "disabled".
        serving = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{not json")
        screen.gif.dismiss()
        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the malformed-answer state") {
            visible(gifEmpty(screen)) && textOf(gifEmpty(screen)!!) == ERROR_TEXT
        }
        serving = categoryResponse()
        tap(gifEmpty(screen)!!)
        harness.await("the retry after malformed JSON") { gifGrid(screen)!!.adapter!!.itemCount == 2 }
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ late answers

    /**
     * Failure mode: an answer that lands after the panel closed or after the session moved on paints
     * the previous reader's results into the panel. The panel's own generation covers the close; the
     * session generation covers an account change. Neither path may draw a tile.
     */
    @Test
    fun anAnswerThatLandsAfterCloseOrAnAccountChangePaintsNothing() {
        val screen = openScreen()
        val firstStarted = AtomicBoolean(false)
        val firstRelease = CountDownLatch(1)
        site.serveResponse("gifs/categories.json") {
            firstStarted.set(true)
            firstRelease.await(10, TimeUnit.SECONDS)
            categoryResponse()
        }

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the first categories request") { firstStarted.get() }
        screen.gif.dismiss()
        firstRelease.countDown()
        harness.idle()
        assertEquals(
            "a categories answer that lands after close paints no tiles",
            0,
            gifGrid(screen)!!.adapter!!.itemCount,
        )
        assertFalse("the dismissed panel stays closed", screen.gif.isOpen())

        val secondStarted = AtomicBoolean(false)
        val secondRelease = CountDownLatch(1)
        site.serveResponse("gifs/categories.json") {
            secondStarted.set(true)
            secondRelease.await(10, TimeUnit.SECONDS)
            categoryResponse()
        }
        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the second categories request") { secondStarted.get() }
        // The reader's session moved on while the request was in flight (an account switch or a
        // server-proven logout). The old account's answer must not become this panel's grid.
        runBlocking {
            screen.container.sessionCoordinator.markExpired(SessionExpiredReason.CookieMissing)
        }
        secondRelease.countDown()
        harness.await("the fenced answer to settle") {
            !screen.gif.isOpen() ||
                (visible(gifEmpty(screen)) && !textOf(gifEmpty(screen)!!).isNullOrEmpty())
        }
        assertEquals(
            "an answer from the previous session paints no tiles",
            0,
            gifGrid(screen)!!.adapter!!.itemCount,
        )
        if (screen.gif.isOpen()) {
            val sessionText = gifEmpty(screen)?.let { view -> textOf(view) }
            assertTrue(
                "an open panel reports the session failure rather than fake content",
                visible(gifEmpty(screen)) &&
                    (sessionText == ERROR_TEXT || sessionText == LOGIN_TEXT || sessionText == SESSION_TEXT),
            )
        }
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ panel switching

    /**
     * Failure mode: the GIF panel becomes a dead end - its own controls cannot return to the site
     * emoji catalog, or Back closes the screen instead of the panel. The GIF panel is a sibling of
     * the emoji panel; both are owned by the composer tools, and Back must release the in-app height
     * the same way the emoji panel does.
     */
    @Test
    fun theGifPanelReturnsToEmojiAndBackClosesOnlyThePanel() {
        val screen = openScreen()
        site.serveJson(
            "emojis.json",
            catalog("smileys" to listOf("smile" to site.url("emoji/smile.png"))),
        )
        site.serveImage("emoji/smile.png", solidPng(48, Color.rgb(0, 128, 255)))

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the GIF categories") { gifGrid(screen)?.adapter?.itemCount == 2 }

        val backToEmoji = descendants(screen.gif).firstOrNull { it.contentDescription == BACK_TO_EMOJI_LABEL }
            ?: throw AssertionError("the GIF panel has no way back to emoji")
        backToEmoji.performClick()
        harness.layoutApp()
        assertFalse("the GIF panel yields to the emoji panel", screen.gif.isOpen())
        assertTrue("the site emoji panel is shown again", screen.picker.isOpen())

        openGifPanel(screen)
        harness.layoutApp()
        harness.await("the GIF categories again") { gifGrid(screen)?.adapter?.itemCount == 2 }
        assertFalse(
            "Back is consumed by the GIF panel, not the screen",
            screen.fragment.onBackPressed(true),
        )
        harness.idleClock(500)
        harness.layoutApp()
        assertFalse("the GIF panel is closed", screen.gif.isOpen())
        assertTrue("the new-topic form is kept", harness.visibleFragment() === screen.fragment)
        assertEquals("Back releases the in-app height at once", 0, screen.insets.inAppKeyboardHeight)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ screens + helpers

    private class Screen(
        val fragment: BaseFragment,
        val picker: DiscourseEmojiPicker,
        val gif: DiscourseGifPicker,
        val composer: ChatActivityEnterView,
        val container: AppContainer,
        val field: EditTextBoldCursor,
        val input: ChatInputViewsContainer,
        val insets: WindowInsetsStateHolder,
    )

    private fun openScreen(): Screen {
        coldEmojiStore()
        harness.bootApp()
        harness.await("the topic list") { contentListOf(harness.activity)?.adapter?.itemCount == 2 }
        val fragment = NewTopicFragment.create()
        harness.present(fragment)
        harness.await("the new-topic screen") { harness.visibleFragment() === fragment }
        harness.layoutApp()

        val tree = viewTree()
        val composer = tree.filterIsInstance<ChatActivityEnterView>().single()
        val input = tree.filterIsInstance<ChatInputViewsContainer>().single()
        val picker = descendants(input).filterIsInstance<DiscourseEmojiPicker>().single()
        val gif = descendants(input).filterIsInstance<DiscourseGifPicker>().single()
        val screen = Screen(
            fragment = fragment,
            picker = picker,
            gif = gif,
            composer = composer,
            container = NativeDo.container(harness.activity),
            field = composer.editField!!,
            input = input,
            insets = fragmentInsets(fragment),
        )
        screens += screen
        return screen
    }

    private fun openEmoji(screen: Screen) {
        screen.composer.emojiButton.performClick()
        harness.layoutApp()
    }

    /** The production path: the emoji panel's own GIF affordance, then the GIF panel's first read. */
    private fun openGifPanel(screen: Screen) {
        if (!screen.picker.isOpen()) {
            screen.composer.emojiButton.performClick()
            harness.layoutApp()
        }
        val gifButton = descendants(screen.picker).firstOrNull { it.contentDescription == GIF_LABEL }
            ?: throw AssertionError("the site emoji panel has no GIF affordance")
        gifButton.performClick()
        harness.layoutApp()
    }

    private fun catalog(vararg groups: Pair<String, List<Pair<String, String>>>): String {
        val obj = JSONObject()
        groups.forEach { (group, emojis) ->
            val array = JSONArray()
            emojis.forEach { (name, url) -> array.put(JSONObject().put("name", name).put("url", url)) }
            obj.put(group, array)
        }
        return obj.toString()
    }

    private fun solidPng(size: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        return bytes.toByteArray()
    }

    private fun categoryResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(harness.fixture("gif-categories.json"))

    private fun emojiGrid(screen: Screen): RecyclerListView? =
        screen.picker.let { picker -> descendants(picker).filterIsInstance<RecyclerListView>().singleOrNull() }

    private fun gifGrid(screen: Screen): RecyclerListView? =
        descendants(screen.gif).filterIsInstance<RecyclerListView>().singleOrNull()

    private fun gifEmpty(screen: Screen): EmptyTextProgressView? =
        descendants(screen.gif).filterIsInstance<EmptyTextProgressView>().singleOrNull()

    private fun gifSearch(screen: Screen): SearchField? =
        descendants(screen.gif).filterIsInstance<SearchField>().singleOrNull()

    private fun gifTitles(screen: Screen): List<String> =
        gifGrid(screen)?.let { list ->
            (0 until list.childCount).map { list.getChildAt(it) }
                .filter { it.bottom > 0 && it.top < list.height }
                .mapNotNull { it.contentDescription?.toString() }
        }.orEmpty()

    private fun gifCellFor(screen: Screen, title: String): View? =
        gifGrid(screen)?.let { list ->
            (0 until list.childCount).map { list.getChildAt(it) }
                .firstOrNull { it.contentDescription?.toString() == title }
        }

    private fun clickGifCell(screen: Screen, title: String) {
        val list = gifGrid(screen) ?: throw AssertionError("no GIF grid")
        harness.layoutApp()
        val view = gifCellFor(screen, title) ?: throw AssertionError("no GIF cell $title")
        list.onItemClickListener?.onItemClick(view, list.getChildAdapterPosition(view))
        harness.idle()
    }

    private fun clickEmoji(screen: Screen, position: Int) {
        val list = emojiGrid(screen) ?: throw AssertionError("no emoji grid")
        harness.layoutApp()
        val view = (0 until list.childCount).map { list.getChildAt(it) }
            .filter { it.contentDescription?.startsWith(":") == true }
            .getOrNull(position) ?: throw AssertionError("no emoji tile $position to click")
        list.onItemClickListener?.onItemClick(view, list.getChildAdapterPosition(view))
        harness.idle()
    }

    private fun tap(view: View) {
        val down = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 4f, 4f, 0)
        val up = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 4f, 4f, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        harness.idle()
    }

    private fun textOf(view: View): String? =
        descendants(view).filterIsInstance<TextView>().firstOrNull { it.text.isNotEmpty() }?.text?.toString()

    private fun visible(view: View?): Boolean = view?.visibility == View.VISIBLE

    private fun viewTree(): List<View> {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return emptyList()
        return descendants(content)
    }

    private fun descendants(root: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(view: View) {
            found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return found
    }

    private fun coldEmojiStore() {
        harness.context.getSharedPreferences("nativedo.emoji_catalog", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun fragmentInsets(fragment: BaseFragment): WindowInsetsStateHolder {
        val field = fragment.javaClass.getDeclaredField("inputInsets").apply { isAccessible = true }
        return field.get(fragment) as WindowInsetsStateHolder
    }

    private companion object {
        const val GIF_LABEL = "GIF"
        const val BACK_TO_EMOJI_LABEL = "表情"
        const val UNAVAILABLE_TEXT = "这个站点没有启用 GIF"
        const val SESSION_TEXT = "登录状态暂时无法验证，请完成网站验证后重试"
        const val LOGIN_TEXT = "登录已失效，请重新登录"
        const val ERROR_TEXT = "GIF 加载失败，点击重试"
        const val NO_RESULTS_TEXT = "没有找到 GIF"
    }
}
