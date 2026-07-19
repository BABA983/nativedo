package org.telegram.ui.ActionBar

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

/**
 * Contract tests for the host Theme day/night pipeline:
 * Blue / Dark Blue assets, prefs slots, toggle, and unknown-key fallback.
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
        Theme.init(app)
    }

    @Test
    fun initLoadsBlueDayThemeFromAsset() {
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Blue")
        assertThat(Theme.isCurrentThemeDark()).isFalse()
        // Asset should override at least some keys into currentColors; background must be opaque white-ish.
        val bg = Theme.getColor(Theme.key_windowBackgroundWhite)
        assertThat(bg and 0xff000000.toInt()).isEqualTo(0xff000000.toInt())
        // Default blue accent path is non-zero.
        assertThat(Theme.getColor(Theme.key_telegram_color)).isNotEqualTo(0)
    }

    @Test
    fun toggleThemeSwitchesToDarkBlueAndBack() {
        Theme.toggleTheme()
        assertThat(Theme.isCurrentThemeDark()).isTrue()
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Dark Blue")
        assertThat(Theme.getCurrentNightTheme()?.name).isEqualTo("Dark Blue")

        Theme.toggleTheme()
        assertThat(Theme.isCurrentThemeDark()).isFalse()
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Blue")
    }

    @Test
    fun prefsPersistNightSlotAcrossReinit() {
        Theme.toggleTheme()
        assertThat(Theme.isCurrentThemeDark()).isTrue()

        val app = RuntimeEnvironment.getApplication()
        Theme.init(app)
        assertThat(Theme.isCurrentThemeDark()).isTrue()
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Dark Blue")
    }

    @Test
    fun unknownThemeKeyFallsBackToBlue() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("themeconfig", 0)
            .edit()
            .putString("theme", "NativeDo")
            .putString("nighttheme", "NotARealTheme")
            .putBoolean("activeIsNight", false)
            .commit()

        Theme.init(app)
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Blue")
        assertThat(Theme.getCurrentTheme()?.name).isEqualTo("Blue")
        assertThat(Theme.getCurrentNightTheme()?.name).isEqualTo("Dark Blue")
    }

    @Test
    fun atthemeAliasKey_telegram_color_textIsMapped() {
        // bluebubbles writes "key_telegram_color_text=..."; parser must accept key_ prefix.
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        val color = Theme.getColor(Theme.key_telegram_color_text)
        // Either from asset or default — must not be the missing-key red sentinel 0xffff0000
        // unless that is intentionally the value (it is not in defaults).
        assertThat(color).isNotEqualTo(0xffff0000.toInt())
    }
}
