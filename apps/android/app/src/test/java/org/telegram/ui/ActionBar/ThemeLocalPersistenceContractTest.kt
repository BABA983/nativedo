package org.telegram.ui.ActionBar

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeLocalPersistenceContractTest {

    private lateinit var backend: LocalOnlyBackend

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(app)
        AndroidUtilities.init(app)
        seedAttheme("bluebubbles.attheme")
        seedAttheme("darkblue.attheme")
        Theme.getActiveTheme()
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        backend = LocalOnlyBackend()
        Theme.setThemeBackend(backend)
    }

    @After
    fun tearDown() {
        Theme.themes
            ?.filter { it.pathToFile != null && it.name.contains("contract", ignoreCase = true) }
            ?.toList()
            ?.forEach(Theme::deleteTheme)
        Theme.applyTheme(Theme.getTheme("Blue"), false, false)
        Theme.setThemeBackend(null)
    }

    @Test
    fun finalSaveOfLocalEditorThemeDoesNotInvokeRemoteUpload() {
        val theme = Theme.createNewTheme("Local editor contract")

        assertThat(File(theme.pathToFile).isFile).isTrue()
        assertThat(Theme.getActiveTheme()).isSameInstanceAs(theme)
        assertThat(backend.uploadCalls).isEqualTo(0)
    }

    @Test
    fun installingLocalThemeFileRegistersWithoutApplying() {
        val source = File(ApplicationLoader.getFilesDirFixed(), "bluebubbles.attheme")

        val installed = Theme.installLocalThemeFile(source, "Imported contract.attheme", null)

        assertThat(installed).isNotNull()
        assertThat(Theme.getTheme("Imported contract.attheme")).isSameInstanceAs(installed)
        assertThat(Theme.getActiveTheme()?.name).isEqualTo("Blue")
    }

    @Test
    fun localOnlyBackendIgnoresRemoteMetadataDuringApply() {
        val theme = Theme.createNewTheme("Remote metadata contract")
        theme.info = TLRPC.TL_theme()

        Theme.applyTheme(theme, false)

        assertThat(Theme.getActiveTheme()).isSameInstanceAs(theme)
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

    private class LocalOnlyBackend : Theme.ThemeBackend {
        var uploadCalls = 0

        override fun supportsRemoteThemes(): Boolean = false

        override fun installRemoteTheme(
            theme: Theme.ThemeInfo,
            accent: Theme.ThemeAccent?,
            nightTheme: Boolean,
            remove: Boolean,
        ) = error("remote install must not run")

        override fun uploadTheme(theme: Theme.ThemeInfo, accent: Theme.ThemeAccent?) {
            uploadCalls++
        }

        override fun loadRemoteThemes(account: Int, force: Boolean) = error("remote catalog must not run")

        override fun loadWallpaperPatterns(
            account: Int,
            request: TL_account.getMultiWallPapers,
            callback: ConnectionsManager.RequestDelegate,
        ) = error("remote patterns must not run")

        override fun loadRemoteWallpaper(
            account: Int,
            request: TL_account.getWallPaper,
            callback: ConnectionsManager.RequestDelegate,
        ) = error("remote wallpaper must not run")

        override fun refreshCurrentRemoteTheme(
            account: Int,
            request: TL_account.getTheme,
            callback: ConnectionsManager.RequestDelegate,
        ) = error("remote refresh must not run")
    }
}
