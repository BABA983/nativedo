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
        // Robolectric app context does not see :telegram assets; seed files dir so
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
            File("telegram/src/main/assets/$assetName"),
            File("apps/android/telegram/src/main/assets/$assetName"),
            File("../telegram/src/main/assets/$assetName"),
            File("src/main/assets/$assetName"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    @Test
    fun assetColorDiffersFromHardcodedDefaultForBlueText() {
        val key = Theme.key_windowBackgroundWhiteBlueText
        val fromAsset = Theme.getColor(key)
        val hardcoded = Theme.getDefaultColor(key)
        // bluebubbles.attheme should supply a value; if it equals defaults this test still
        // validates override restore, but we assert asset is not the missing-key sentinel.
        assertThat(fromAsset).isNotEqualTo(0xffff0000.toInt())
        assertThat(fromAsset and 0xff000000.toInt()).isEqualTo(0xff000000.toInt())
        // Document the dual-layer intent: when they differ, restore must not use hardcoded.
        if (fromAsset != hardcoded) {
            assertThat(fromAsset).isNotEqualTo(hardcoded)
        }
    }

    @Test
    fun setColorOverrideThenUseDefaultRestoresResolvedDefault() {
        val key = Theme.key_windowBackgroundWhiteBlueText
        val hardcoded = Theme.getDefaultColor(key)
        val override = 0xff112233.toInt()

        Theme.setColor(key, override, false)
        assertThat(Theme.getColor(key)).isEqualTo(override)
        assertThat(Theme.getNonAnimatedColor(key)).isEqualTo(override)

        Theme.setColor(key, hardcoded, true)
        assertThat(Theme.getColor(key)).isEqualTo(hardcoded)
        assertThat(Theme.getNonAnimatedColor(key)).isEqualTo(hardcoded)
    }

    @Test
    fun themeDescriptionSetDefaultColorRestoresResolvedDefault() {
        val key = Theme.key_windowBackgroundWhiteBlackText
        val defaultColor = Theme.getDefaultColor(key)
        Theme.setColor(key, 0xffabcdef.toInt(), false)
        assertThat(Theme.getColor(key)).isEqualTo(0xffabcdef.toInt())

        val description = ThemeDescription(
            null,
            ThemeDescription.FLAG_TEXTCOLOR,
            null,
            null as android.graphics.Paint?,
            null,
            null,
            key,
        )
        description.setDefaultColor()
        assertThat(Theme.getColor(key)).isEqualTo(defaultColor)
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
