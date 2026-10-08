package io.github.baba983.nativedo.profile

import android.view.View
import androidx.core.graphics.ColorUtils
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.app.NativeDoActivity
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

/**
 * The profile screen's status-bar contrast, the two upstream states it was missing.
 *
 * <p>Upstream's {@code ProfileActivity.isLightStatusBar} (15780) answers from the expanded photo and
 * the pinned media header, not only from the profile's own action-bar colour. The failures this
 * pins:</p>
 * <ul>
 *   <li>the photo expanded to full screen keeps the profile's day icons over a dark picture, so the
 *       clock and battery vanish;</li>
 *   <li>the tab area pinned under the action bar - the media header - keeps the profile's colour as
 *       the answer even though the bar behind the icons is now the window background;</li>
 *   <li>neither transition asks the shell to recompute, so even a correct answer never reaches the
 *       window's status-bar flags.</li>
 * </ul>
 *
 * <p>The screen is built by the real shell against [FakeDiscourseSite]; the transitions are the
 * screen's own methods (the ones its gestures call), and the oracle is the window's
 * {@code SYSTEM_UI_FLAG_LIGHT_STATUS_BAR} flag, which is what upstream's
 * {@code AndroidUtilities.setLightStatusBar} writes.</p>
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
internal class ProfileStatusBarTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        site.serveBootstrap()
        site.serveFixture("u/baba.json", "user-profile.json")
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    /**
     * The whole contract in one pass: the expanded photo forces light icons (dark status bar) even
     * where the rest state said otherwise, the pinned media header follows the window background,
     * and each transition asks the shell to recompute so the window's flag follows the screen's
     * answer.
     */
    @Test
    fun theExpandedPhotoAndThePinnedHeaderChooseTheStatusBarIcons() {
        val shell = harness.bootApp()
        harness.await("the topic list") { harness.visibleFragmentName() == "TopicListFragment" }
        shell.present(ProfileFragment.forUser("baba", "Baba"))
        harness.await("the profile") { harness.visibleFragmentName() == "ProfileFragment" }
        harness.layoutApp()
        val profile = harness.visibleFragment() as ProfileFragment

        val atRest = profile.isLightStatusBar()
        assertEquals("at rest the window follows the profile's own answer", atRest, windowLightStatusBar())

        // The media header: the tab area pinned under the bar makes the window background the colour
        // behind the icons, so the answer is that colour's, and the shell is asked to recompute.
        call(profile, "setMediaHeaderVisible", Boolean::class.javaPrimitiveType!!, true)
        harness.idle()
        val pinned = profile.isLightStatusBar()
        assertEquals(
            "the pinned media header answers from the window background",
            ColorUtils.calculateLuminance(profile.getThemedColor(Theme.key_windowBackgroundWhite)) > 0.7f,
            pinned,
        )
        assertEquals("and the window followed the recompute", pinned, windowLightStatusBar())
        harness.renderApp("profile-statusbar-media-header")
        call(profile, "setMediaHeaderVisible", Boolean::class.javaPrimitiveType!!, false)
        harness.idle()
        assertEquals("unpinning hands the answer back", atRest, profile.isLightStatusBar())

        // The expanded photo: the picture is behind the status line, so the icons go light whatever
        // the rest state answered - and the shell is asked to recompute this flip too.
        call(profile, "startAvatarExpand", Float::class.javaPrimitiveType!!, 1f)
        harness.idle()
        assertFalse("the expanded photo wants light icons, not day icons", profile.isLightStatusBar())
        assertFalse("and the window followed the recompute", windowLightStatusBar())
        harness.renderApp("profile-statusbar-expanded-photo")

        call(profile, "startAvatarCollapse", Float::class.javaPrimitiveType!!, 1f)
        harness.idle()
        assertEquals("collapsing hands the answer back", atRest, profile.isLightStatusBar())
        assertEquals("and so does the window", atRest, windowLightStatusBar())
        harness.assertNoTelegramSideEffects()
    }

    /** The window's own status-bar flag: what upstream's setLightStatusBar writes. */
    private fun windowLightStatusBar(): Boolean {
        val flags = (harness.activity as NativeDoActivity).window.decorView.systemUiVisibility
        return flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
    }

    private fun call(target: Any, name: String, parameterType: Class<*>, argument: Any) {
        val method = target.javaClass.getDeclaredMethod(name, parameterType).apply { isAccessible = true }
        method.invoke(target, argument)
    }
}
