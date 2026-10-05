package io.github.baba983.nativedo.app

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.window.BackEvent
import android.window.OnBackAnimationCallback
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.FragmentSearchField
import org.telegram.ui.MainTabsLayout
import org.telegram.ui.Components.glass.GlassTabView

/**
 * Predictive back, the way the system drives it: the registered [OnBackAnimationCallback] is
 * handed `onBackStarted`/`onBackProgressed`/`onBackCancelled`/`onBackInvoked`, and the page must
 * follow the finger while the gesture is live.
 *
 * <p>Failures pinned here: a gesture that pops nothing on commit (the plain callback's "does
 * nothing during the gesture" pop), a gesture that pops although the reader cancelled it, a
 * gesture started over something that will consume the back (the home search) that either starts
 * the page animation or closes the thing before the commit, and a root gesture that ends in a
 * fragment-less shell instead of the system's own finish.</p>
 *
 * <p>Robolectric runs at SDK 35 (robolectric.properties), so the activity takes the API-34
 * animation-callback branch; the callback is fetched from the real registered dispatcher and the
 * BackEvents are built through the hidden framework constructor, exactly what the system would
 * deliver.</p>
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
internal class PredictiveBackTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serve(1L, "topic-detail.json")
        site.serveFixture("u/baba.json", "user-profile.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        // Robolectric 4.15 does not carry android:enableOnBackInvokedCallback from the merged
        // manifest (the merged manifest has it: merged_manifest/debug/.../AndroidManifest.xml:157)
        // into ApplicationInfo, so the framework would skip the registration. Its own
        // always-enforce switch is what makes the registration happen here; the device build
        // enables it through the manifest.
        Class.forName("android.window.WindowOnBackInvokedDispatcher")
            .getDeclaredField("ALWAYS_ENFORCE_PREDICTIVE_BACK")
            .apply { isAccessible = true }
            .set(null, true)
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * A topic is the case the owner reported: the page under the finger shrinks and slides with the
     * gesture, and the release slides it out to the right, revealing the feed - exactly one
     * fragment popped.
     */
    @Test
    fun aBackGestureOverATopicPullsThePageAndItsCommitPopsOneFragment() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        shell.present(TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { harness.topFragment()?.javaClass?.simpleName == "TopicFragment" }
        val topic = harness.topFragment()!!
        harness.layoutApp()

        val callback = backCallback(shell)
        gesture(callback, progress = 0.5f)

        assertSame("the gesture has not popped anything yet", topic, harness.topFragment())
        assertEquals("the topic and the feed still back each other", 2, harness.fragmentStack().size)
        assertTrue("the page follows the finger", layout().innerTranslationX > 0f)
        assertTrue("and so does the fragment container", layout().containerView.translationX > 0f)
        harness.renderAppFrame("predictive-back-mid-gesture")

        callback.onBackInvoked()
        harness.idleClock(1_000)

        assertEquals("the release pops exactly one fragment", 1, harness.fragmentStack().size)
        assertEquals("and it lands on the tab host",
            "NativeDoMainTabsActivity", harness.topFragment()?.javaClass?.simpleName)
        assertEquals("the slide is over", 0f, layout().innerTranslationX, 0.01f)
        harness.renderApp("predictive-back-after-pop")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The change of mind: lifting the finger without committing must put the page back exactly
     * where it was, with the topic still on top.
     */
    @Test
    fun aCancelledGestureRestoresTheTopicAndTheFeed() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        shell.present(TopicFragment.forTopic(1L, "A topic"))
        harness.await("the topic to open") { harness.topFragment()?.javaClass?.simpleName == "TopicFragment" }
        val topic = harness.topFragment()!!
        harness.layoutApp()

        val callback = backCallback(shell)
        gesture(callback, progress = 0.5f)
        assertTrue("the page is moved while the gesture is live", layout().innerTranslationX > 0f)

        callback.onBackCancelled()
        harness.idleClock(1_000)

        assertSame("the topic stays on top", topic, harness.topFragment())
        assertEquals("nothing was pushed or popped", 2, harness.fragmentStack().size)
        assertEquals("the page returned under the finger", 0f, layout().innerTranslationX, 0.01f)
        assertEquals("and so did the fragment container", 0f, layout().containerView.translationX, 0.01f)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * The root: nothing is behind the tab host, so no page animation may start, and the commit is
     * the activity's own leave - the finish the system animates back to home. (Upstream's
     * LaunchActivity.needCloseLastFragment does the same.)
     */
    @Test
    fun aGestureAtTheRootAnimatesNothingAndItsCommitLetstheShellFinish() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.layoutApp()

        val callback = backCallback(shell)
        gesture(callback, progress = 0.5f)

        assertEquals("the root has nothing to slide", 0f, layout().innerTranslationX, 0.01f)
        assertFalse("and starts no swipe", layout().isSwipeInProgress)
        assertEquals("the tab host never left", 1, harness.fragmentStack().size)

        callback.onBackInvoked()
        harness.idle(200)

        assertTrue("the commit is the shell's finish, not a pop", shell.isFinishing)
        assertEquals("and the layout was not emptied", 1, harness.fragmentStack().size)
    }

    /**
     * A page that is not the first: MainTabsActivity's own answer (781-793) - the commit goes to
     * the first page, the gesture itself changes nothing. Mirrored by NativeDoMainTabsActivity.
     */
    @Test
    fun aBackGestureOnANonFirstTabGoesToTheFirstTabOnlyAtCommit() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.layoutApp()
        val tabs = findTabs(harness.activity.findViewById(android.R.id.content))
            ?: throw AssertionError("no main tabs")
        tapTab(tabs, 2)
        harness.await("the settings page") { harness.visibleFragmentName() == "SettingsFragment" }

        val callback = backCallback(shell)
        gesture(callback, progress = 0.5f)
        assertEquals("no page animation on the tab host", 0f, layout().innerTranslationX, 0.01f)
        assertEquals("a started gesture stays on the page", "SettingsFragment", harness.visibleFragmentName())

        callback.onBackInvoked()
        harness.idleClock(1_000)
        assertEquals("the commit returns to the first page", "TopicListFragment", harness.visibleFragmentName())
        assertEquals("the tab host is still the only fragment", 1, harness.fragmentStack().size)
        assertFalse("and the shell did not leave", shell.isFinishing)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An open search will consume the back at commit; a started gesture must neither start the page
     * animation nor perform that consumption early.
     */
    @Test
    fun aGestureOverAnOpenSearchClosesNothingUntilTheCommit() {
        val shell = harness.bootApp()
        harness.await("the topic list to load") { harness.visibleFragmentName() == "TopicListFragment" }
        harness.layoutApp()
        val list = harness.visibleFragment() ?: throw AssertionError("no home list")
        val field = findSearchField() ?: throw AssertionError("the home screen has no search field")
        field.editText.performClick()
        harness.idleClock(400)
        assertTrue("the search is open", searching(list))
        harness.layoutApp()

        val callback = backCallback(shell)
        gesture(callback, progress = 0.5f)

        assertEquals("an open search stops the page animation", 0f, layout().innerTranslationX, 0.01f)
        assertTrue("a started gesture must not close it early", searching(list))

        callback.onBackInvoked()
        harness.idleClock(400)

        assertFalse("the commit closes the search", searching(list))
        assertEquals("on the tab host, not a popped screen", 1, harness.fragmentStack().size)
        assertFalse("and the shell stays", shell.isFinishing)
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------ the system's side of the gesture

    private fun shell(): NativeDoActivity = harness.activity as NativeDoActivity

    /**
     * The callback the system would drive: the registered one, read from the window's dispatcher.
     * The platform hands out a `ProxyOnBackInvokedDispatcher` whose registrations sit in
     * `mCallbacks` until the window is attached and are then transferred to the real
     * `WindowOnBackInvokedDispatcher` (`mActualDispatcher`), which answers `getTopCallback()`.
     * Both are framework internals, so both are reached reflectively.
     */
    private fun backCallback(shell: NativeDoActivity): OnBackAnimationCallback {
        val dispatcher = shell.onBackInvokedDispatcher
            ?: throw AssertionError("the activity has no OnBackInvokedDispatcher")
        val top = topCallbackOf(dispatcher)
            ?: throw AssertionError(
                "no back callback registered - android:enableOnBackInvokedCallback must be true in the merged manifest")
        return top as OnBackAnimationCallback
    }

    private fun topCallbackOf(dispatcher: Any): Any? {
        (readField(dispatcher, "mCallbacks") as? List<*>)?.let { callbacks ->
            if (callbacks.isNotEmpty()) {
                return callbacks.mapNotNull { pair ->
                    (pair as? android.util.Pair<*, *>)?.let { it.first to (it.second as? Int ?: Int.MIN_VALUE) }
                }.maxByOrNull { it.second }?.first
            }
        }
        val actual = readField(dispatcher, "mActualDispatcher") ?: return null
        return runCatching {
            actual.javaClass.methods.first { it.name == "getTopCallback" }.invoke(actual)
        }.getOrNull()
    }

    private fun readField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        return field.get(target)
    }

    /** One frame's worth of gesture: started, then the system's progressed fraction. */
    private fun gesture(callback: OnBackAnimationCallback, progress: Float) {
        callback.onBackStarted(backEvent(0f))
        callback.onBackProgressed(backEvent(progress))
        harness.idle()
    }

    /**
     * A BackEvent as the system builds it. The constructor is hidden from the SDK but present in
     * the framework runtime, which is why this is reflective rather than direct.
     */
    private fun backEvent(progress: Float, touchX: Float = 0f, touchY: Float = 600f): BackEvent {
        val constructor = BackEvent::class.java.getDeclaredConstructor(
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        constructor.isAccessible = true
        return constructor.newInstance(touchX, touchY, progress, 0) as BackEvent
    }

    private fun layout(): ActionBarLayout {
        val field = NativeDoActivity::class.java.getDeclaredField("actionBarLayout").apply { isAccessible = true }
        return field.get(shell()) as ActionBarLayout
    }

    private fun searching(fragment: BaseFragment): Boolean {
        val field = fragment.javaClass.getDeclaredField("searching").apply { isAccessible = true }
        return field.getBoolean(fragment)
    }

    private fun findTabs(view: View?): MainTabsLayout? {
        if (view is MainTabsLayout) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTabs(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** Taps the real upstream child through the strip's touch dispatch. */
    private fun tapTab(tabs: MainTabsLayout, index: Int) {
        harness.idle()
        val tab = tabs.getChildAt(index) as GlassTabView
        val x = tab.x + tab.width / 2f
        val y = tab.y + tab.height / 2f
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        tabs.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        tabs.dispatchTouchEvent(up)
        harness.idle()
    }

    private fun findSearchField(): FragmentSearchField? =
        firstView(android.R.id.content) { it is FragmentSearchField } as FragmentSearchField?

    private fun firstView(id: Int, matches: (View) -> Boolean): View? {
        val root = harness.activity.findViewById<ViewGroup>(id) ?: return null
        return walk(root, matches)
    }

    private fun walk(view: View, matches: (View) -> Boolean): View? {
        if (matches(view)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index), matches)?.let { return it }
            }
        }
        return null
    }
}
