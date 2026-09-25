package org.telegram.ui.ActionBar

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

/**
 * Upstream color model: asset base + accent fill produce currentColors.
 * useDefault removes the current entry so default/fallback resolution takes over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeColorLayersContractTest {

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        app.getSharedPreferences("themeconfig", 0).edit().clear().commit()
        // Robolectric app context does not see :TMessagesProj assets; seed files dir so
        // Theme.getAssetFile / getThemeFileValues resolve Blue and Dark Blue packs.
        seedAtthemeIntoFilesDir("bluebubbles.attheme")
        seedAtthemeIntoFilesDir("darkblue.attheme")
        seedAtthemeIntoFilesDir("day.attheme")
        Theme.getActiveTheme()
        Theme.applyTheme(Theme.getTheme("Day"), false, false)
    }

    private fun seedAtthemeIntoFilesDir(assetName: String) {
        val source = resolveAtthemeSource(assetName)
            ?: throw AssertionError("Missing $assetName under telegram assets for unit tests")
        val dest = File(ApplicationLoader.getFilesDirFixed(), assetName)
        source.copyTo(dest, overwrite = true)
    }

    private fun resolveAtthemeSource(assetName: String): File? {
        val candidates = listOf(
            File("../TMessagesProj/src/main/assets/$assetName"),
            File("apps/android/TMessagesProj/src/main/assets/$assetName"),
            File("TMessagesProj/src/main/assets/$assetName"),
            File("src/main/assets/$assetName"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    @Test
    fun reapplyThemeRebuildsAccentColorsAndDropsInMemoryOverrides() {
        val key = Theme.key_windowBackgroundWhiteBlueText
        val assetColor = Theme.getColor(key)
        Theme.setColor(key, 0xff445566.toInt(), false)
        assertThat(Theme.getColor(key)).isEqualTo(0xff445566.toInt())

        Theme.applyTheme(Theme.getTheme("Day"), false, false)
        assertThat(Theme.getColor(key)).isEqualTo(assetColor)
    }

    @Test
    fun isDefaultFlagTrueOnlyWhenNeitherOverrideNorAsset() {
        val isDefault = booleanArrayOf(false)
        // Key present in bluebubbles.attheme (windowBackgroundWhiteBlueText=...).
        val key = Theme.key_windowBackgroundWhiteBlueText
        Theme.getColor(key, isDefault)
        assertThat(isDefault[0]).isFalse()

        Theme.setColor(key, 0xff010203.toInt(), false)
        Theme.getColor(key, isDefault)
        assertThat(isDefault[0]).isFalse()

        Theme.setColor(key, 0, true)
        Theme.getColor(key, isDefault)
        assertThat(isDefault[0]).isTrue()
    }

    @Test
    fun useDefaultRemovesCurrentLayerEntry() {
        val key = Theme.key_dialogTextBlack
        val hardcoded = Theme.getDefaultColor(key)
        assertThat(hardcoded).isNotEqualTo(0xffff0000.toInt())

        val override = 0xffaabbcc.toInt()
        Theme.setColor(key, override, false)
        assertThat(Theme.getColor(key)).isEqualTo(override)

        Theme.setColor(key, 0, true)
        assertThat(Theme.getColor(key)).isEqualTo(hardcoded)
        assertThat(Theme.getNonAnimatedColor(key)).isEqualTo(hardcoded)

        val isDefault = booleanArrayOf(false)
        Theme.getColor(key, isDefault)
        assertThat(isDefault[0]).isTrue()
    }
}
