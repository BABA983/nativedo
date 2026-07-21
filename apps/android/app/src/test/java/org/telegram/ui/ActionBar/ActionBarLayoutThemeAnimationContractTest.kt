package org.telegram.ui.ActionBar

import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ActionBarLayoutThemeAnimationContractTest {

    private lateinit var activity: Activity
    private lateinit var layout: ActionBarLayout
    private lateinit var fragment: ThemeFragment

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        seedAttheme("bluebubbles.attheme")
        seedAttheme("darkblue.attheme")
        Theme.getActiveTheme()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)

        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        layout = ActionBarLayout(activity)
        fragment = ThemeFragment()
        assertThat(layout.addFragmentToStack(fragment)).isTrue()
    }

    @Test
    fun instantAnimationAppliesThemeDescriptionsAndFinalValue() {
        val settings = INavigationLayout.ThemeAnimationSettings(
            Theme.getCurrentNightTheme(),
            -1,
            true,
            true,
        )

        layout.animateThemedValues(settings, null)

        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Dark Blue")
        assertThat(layout.getThemeAnimationValue()).isEqualTo(1f)
        assertThat(fragment.text.currentTextColor)
            .isEqualTo(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
    }

    @Test
    fun timedAnimationReachesFinalValue() {
        val settings = INavigationLayout.ThemeAnimationSettings(
            Theme.getCurrentNightTheme(),
            -1,
            true,
            false,
        )
        settings.duration = 200

        layout.animateThemedValues(settings, null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))

        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Dark Blue")
        assertThat(layout.getThemeAnimationValue()).isEqualTo(1f)
        assertThat(Theme.isAnimatingColor()).isFalse()
    }

    @Test
    fun visibleDialogDescriptionsParticipateInThemeAnimation() {
        val dialogText = TextView(activity)
        fragment.visibleDialog = ThemeAlertDialog(activity, dialogText)
        val settings = INavigationLayout.ThemeAnimationSettings(
            Theme.getCurrentNightTheme(),
            -1,
            true,
            true,
        )

        layout.animateThemedValues(settings, null)

        assertThat(dialogText.currentTextColor)
            .isEqualTo(Theme.getColor(Theme.key_dialogTextBlack))
    }

    @Test
    fun fragmentOwnsShownDialogUntilDismissed() {
        val dialog = android.app.Dialog(activity)

        assertThat(fragment.showDialog(dialog)).isSameInstanceAs(dialog)
        assertThat(fragment.visibleDialog).isSameInstanceAs(dialog)
        assertThat(dialog.isShowing).isTrue()

        dialog.dismiss()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.visibleDialog).isNull()
    }

    private fun seedAttheme(assetName: String) {
        val candidates = listOf(
            File("telegram/src/main/assets/$assetName"),
            File("apps/android/telegram/src/main/assets/$assetName"),
            File("../telegram/src/main/assets/$assetName"),
        )
        val source = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("Missing $assetName for Theme unit tests")
        source.copyTo(File(ApplicationLoader.getFilesDirFixed(), assetName), overwrite = true)
    }

    private class ThemeFragment : BaseFragment() {
        lateinit var text: TextView

        override fun setParentLayout(layout: INavigationLayout?) {
            parentLayout = layout
        }

        override fun createView(context: Context): View {
            text = TextView(context)
            fragmentView = text
            return text
        }

        override fun getThemeDescriptions(): java.util.ArrayList<ThemeDescription> {
            return arrayListOf(
                ThemeDescription(
                    text,
                    ThemeDescription.FLAG_TEXTCOLOR,
                    null,
                    null as Paint?,
                    null,
                    null,
                    Theme.key_windowBackgroundWhiteBlackText,
                ),
            )
        }
    }

    private class ThemeAlertDialog(
        context: Context,
        private val text: TextView,
    ) : AlertDialog(context, ALERT_TYPE_MESSAGE) {
        override fun getThemeDescriptions(): java.util.ArrayList<ThemeDescription> {
            return arrayListOf(
                ThemeDescription(
                    text,
                    ThemeDescription.FLAG_TEXTCOLOR,
                    null,
                    null as Paint?,
                    null,
                    null,
                    Theme.key_dialogTextBlack,
                ),
            )
        }
    }
}
