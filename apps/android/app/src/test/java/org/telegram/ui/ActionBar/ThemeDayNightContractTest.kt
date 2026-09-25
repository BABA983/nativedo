package org.telegram.ui.ActionBar

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Looper
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter

/**
 * Contract tests for the upstream Theme day/night pipeline and host toggle bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeDayNightContractTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        // Clear theme prefs so each test starts from a known state.
        app.getSharedPreferences("themeconfig", 0).edit().clear().commit()
        // Robolectric does not package :TMessagesProj assets; seed files dir for .attheme loads.
        seedAtthemeIntoFilesDir("bluebubbles.attheme")
        seedAtthemeIntoFilesDir("darkblue.attheme")
        Theme.getActiveTheme()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
    }

    private fun seedAtthemeIntoFilesDir(assetName: String) {
        val candidates = listOf(
            File("../TMessagesProj/src/main/assets/$assetName"),
            File("apps/android/TMessagesProj/src/main/assets/$assetName"),
            File("TMessagesProj/src/main/assets/$assetName"),
        )
        val source = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("Missing $assetName for Theme unit tests")
        source.copyTo(File(ApplicationLoader.getFilesDirFixed(), assetName), overwrite = true)
    }

    @Test
    fun applyingNightAndDaySlotsSwitchesThemes() {
        Theme.applyTheme(Theme.getCurrentNightTheme(), true)
        Theme.saveAutoNightThemeConfig()
        assertThat(Theme.isCurrentThemeDark()).isTrue()
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Dark Blue")
        assertThat(Theme.getCurrentNightTheme()?.name).isEqualTo("Dark Blue")

        Theme.applyTheme(Theme.getCurrentTheme(), false)
        assertThat(Theme.isCurrentThemeDark()).isFalse()
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Blue")
    }

    @Test
    fun saveAutoNightConfigPersistsNightSlot() {
        Theme.applyTheme(Theme.getCurrentNightTheme(), true)
        Theme.saveAutoNightThemeConfig()
        assertThat(Theme.isCurrentThemeDark()).isTrue()

        val app = RuntimeEnvironment.getApplication()
        assertThat(app.getSharedPreferences("global", 0).getString("nighttheme", null))
            .isEqualTo("Dark Blue")
    }

    @Test
    fun scheduledAutoNight_isCheckedFromMessagesControllerTimerProc() {
        val calendar = Calendar.getInstance()
        Theme.autoNightDayStartTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        Theme.autoNightDayEndTime = Theme.autoNightDayStartTime
        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SCHEDULED
        MessagesController::class.java.getDeclaredField("lastThemeCheckTime")
            .apply { isAccessible = true }
            .setLong(null, 0L)
        Theme::class.java.getDeclaredField("switchNightThemeDelay")
            .apply { isAccessible = true }
            .setInt(null, 0)
        var requestedNightTheme: Boolean? = null
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            if (id == NotificationCenter.needSetDayNightTheme) {
                requestedNightTheme = args.getOrNull(1) as? Boolean
            }
        }
        NotificationCenter.getGlobalInstance().addObserver(observer, NotificationCenter.needSetDayNightTheme)

        try {
            MessagesController.getInstance(0).updateTimerProc()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(requestedNightTheme).isTrue()
        } finally {
            NotificationCenter.getGlobalInstance().removeObserver(observer, NotificationCenter.needSetDayNightTheme)
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE
        }
    }
}
