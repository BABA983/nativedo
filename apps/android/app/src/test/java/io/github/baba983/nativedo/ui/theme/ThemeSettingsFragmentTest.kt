package io.github.baba983.nativedo.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.SparseIntArray
import android.view.View
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeSettingsFragmentTest {

    private val createdThemeNames = mutableListOf<String>()

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        seedAttheme("bluebubbles.attheme")
        seedAttheme("darkblue.attheme")
        Theme.getActiveTheme()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
    }

    @After
    fun tearDown() {
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        Theme.setCurrentNightTheme(Theme.getTheme("Dark Blue"))
        createdThemeNames.forEach { name ->
            Theme.getTheme(name)?.let(Theme::deleteTheme)
        }
        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE
        Theme.saveAutoNightThemeConfig()
    }

    @Test
    fun drawsPreviewSurface() {
        // Theme settings preview is TopicThemePreviewContent (production topic cells),
        // not the retired ThemePreviewSurface inner class.
        val preview = TopicThemePreviewContent(RuntimeEnvironment.getApplication()) as View

        preview.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY),
        )
        preview.layout(0, 0, 1080, 440)
        val bitmap = Bitmap.createBitmap(1080, 440, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(bitmap))

        assertThat(bitmap.width).isEqualTo(1080)
        assertThat(bitmap.height).isEqualTo(440)
    }

    @Test
    fun importedNightTheme_updatesNightSlotWithoutReplacingDayTheme() {
        val imported = installTheme("Fragment night import contract.attheme", "darkblue.attheme")
        val fragment = fragmentSelectingNightTheme(true)
        val dayTheme = Theme.getCurrentTheme()
        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SCHEDULED

        applyImportedTheme(fragment, imported)

        assertThat(Theme.getCurrentTheme()).isSameInstanceAs(dayTheme)
        assertThat(Theme.getCurrentNightTheme()).isSameInstanceAs(imported)
        assertThat(Theme.getActiveTheme()).isSameInstanceAs(dayTheme)
        assertThat(Theme.selectedAutoNightType).isEqualTo(Theme.AUTO_NIGHT_TYPE_SCHEDULED)
        assertThat(RuntimeEnvironment.getApplication().getSharedPreferences("global", 0)
            .getString("nighttheme", null)).isEqualTo(imported.getKey())
    }

    @Test
    fun importedDayTheme_appliesDaySlotAndTurnsOffAutoNight() {
        val imported = installTheme("Fragment day import contract.attheme", "bluebubbles.attheme")
        val fragment = fragmentSelectingNightTheme(false)
        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SCHEDULED

        applyImportedTheme(fragment, imported)

        assertThat(Theme.getCurrentTheme()).isSameInstanceAs(imported)
        assertThat(Theme.getActiveTheme()).isSameInstanceAs(imported)
        assertThat(Theme.selectedAutoNightType).isEqualTo(Theme.AUTO_NIGHT_TYPE_NONE)
    }

    @Test
    fun creatingNightTheme_preservesDaySlotAndMakesNewThemeActiveNight() {
        val fragment = fragmentSelectingNightTheme(true)
        val dayTheme = Theme.getCurrentTheme()
        val name = "Fragment night create contract"
        createdThemeNames += name

        ThemeSettingsFragment::class.java
            .getDeclaredMethod("createAndEditTheme", String::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(fragment, name, true)

        val created = Theme.getTheme(name)
        assertThat(created).isNotNull()
        assertThat(Theme.getCurrentTheme()).isSameInstanceAs(dayTheme)
        assertThat(Theme.getCurrentNightTheme()).isSameInstanceAs(created)
        assertThat(Theme.getActiveTheme()).isSameInstanceAs(created)
    }

    @Test
    fun deletingActiveNightTheme_preservesConfiguredDayTheme() {
        val nightTheme = Theme.createNewTheme("Fragment night delete contract")
        createdThemeNames += nightTheme.getName()
        val dayTheme = Theme.getTheme("Blue")
        Theme.setCurrentNightTheme(nightTheme)
        Theme.applyTheme(dayTheme, false)
        Theme.applyTheme(nightTheme, true)
        val fragment = fragmentSelectingNightTheme(true)

        ThemeSettingsFragment::class.java
            .getDeclaredMethod("deleteTheme", Theme.ThemeInfo::class.java)
            .apply { isAccessible = true }
            .invoke(fragment, nightTheme)

        assertThat(Theme.getCurrentTheme()).isSameInstanceAs(dayTheme)
        assertThat(Theme.getActiveTheme()).isSameInstanceAs(dayTheme)
        assertThat(Theme.getCurrentNightTheme()).isSameInstanceAs(Theme.getTheme("Dark Blue"))
        assertThat(Theme.getTheme(nightTheme.getName())).isNull()
    }

    @Test
    fun deletingDayThemeWhileNightIsActive_restoresNightAndDefaultDaySlots() {
        val dayTheme = installTheme("Fragment day delete contract.attheme", "bluebubbles.attheme")
        val nightTheme = installTheme("Fragment active night contract.attheme", "darkblue.attheme")
        Theme.applyTheme(dayTheme, false)
        Theme.setCurrentNightTheme(nightTheme)
        Theme.applyTheme(nightTheme, true)
        val fragment = fragmentSelectingNightTheme(false)

        ThemeSettingsFragment::class.java
            .getDeclaredMethod("deleteTheme", Theme.ThemeInfo::class.java)
            .apply { isAccessible = true }
            .invoke(fragment, dayTheme)

        assertThat(Theme.getCurrentTheme()).isSameInstanceAs(Theme.getTheme("Blue"))
        assertThat(Theme.getCurrentNightTheme()).isSameInstanceAs(nightTheme)
        assertThat(Theme.getActiveTheme()).isSameInstanceAs(nightTheme)
        assertThat(Theme.getTheme(dayTheme.getName())).isNull()
    }

    @Test
    fun invalidThemeFile_isRejectedBeforeInstall() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "invalid-theme.attheme")
        file.writeText("this is not a theme")
        val fragment = ThemeSettingsFragment()

        val valid = ThemeSettingsFragment::class.java
            .getDeclaredMethod("isValidThemeFile", File::class.java)
            .apply { isAccessible = true }
            .invoke(fragment, file) as Boolean

        assertThat(valid).isFalse()
    }

    @Test
    fun colorWallpaperOverride_persistsForSelectedNightTheme() {
        val fragment = fragmentSelectingNightTheme(true)
        val nightTheme = Theme.getCurrentNightTheme()
        val info = Theme.OverrideWallpaperInfo().apply {
            slug = Theme.COLOR_BACKGROUND_SLUG
            color = 0xff223344.toInt()
            gradientColor1 = 0xff556677.toInt()
            rotation = 45
        }

        ThemeSettingsFragment::class.java
            .getDeclaredMethod(
                "applyWallpaperOverride",
                Theme.ThemeInfo::class.java,
                Theme.OverrideWallpaperInfo::class.java,
            )
            .apply { isAccessible = true }
            .invoke(fragment, nightTheme, info)

        assertThat(nightTheme.overrideWallpaper).isSameInstanceAs(info)
        val stored = RuntimeEnvironment.getApplication()
            .getSharedPreferences("themeconfig", 0)
            .all.values
            .filterIsInstance<String>()
            .any { it.contains("\"pColor\":${info.color}") }
        assertThat(stored).isTrue()
        nightTheme.setOverrideWallpaper(null)
    }

    private fun seedAttheme(assetName: String) {
        val source = listOf(
            File("telegram/src/main/assets/$assetName"),
            File("apps/android/telegram/src/main/assets/$assetName"),
            File("../telegram/src/main/assets/$assetName"),
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Missing $assetName for Theme unit tests")
        source.copyTo(File(ApplicationLoader.getFilesDirFixed(), assetName), overwrite = true)
    }

    private fun installTheme(name: String, assetName: String): Theme.ThemeInfo {
        createdThemeNames += name
        return requireNotNull(
            Theme.installLocalThemeFile(
                File(ApplicationLoader.getFilesDirFixed(), assetName),
                name,
                null,
            ),
        )
    }

    private fun fragmentSelectingNightTheme(selectingNightTheme: Boolean): ThemeSettingsFragment {
        return ThemeSettingsFragment().also { fragment ->
            ThemeSettingsFragment::class.java
                .getDeclaredField("selectingNightTheme")
                .apply { isAccessible = true }
                .setBoolean(fragment, selectingNightTheme)
        }
    }

    private fun applyImportedTheme(fragment: ThemeSettingsFragment, theme: Theme.ThemeInfo) {
        ThemeSettingsFragment::class.java
            .getDeclaredMethod("applyImportedTheme", Theme.ThemeInfo::class.java)
            .apply { isAccessible = true }
            .invoke(fragment, theme)
    }
}
