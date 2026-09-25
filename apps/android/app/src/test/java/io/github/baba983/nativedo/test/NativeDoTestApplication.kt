package io.github.baba983.nativedo.test

import android.app.Application
import java.io.File
import java.lang.reflect.Method
import io.github.baba983.nativedo.core.ui.DiscourseInlineImages
import org.robolectric.TestLifecycleApplication
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.tl.TL_account
import org.telegram.ui.ActionBar.Theme

class NativeDoTestApplication : Application(), TestLifecycleApplication {
    override fun onCreate() {
        super.onCreate()
        ApplicationLoader.init(this)
        LocaleController.init(this)
        AndroidUtilities.init(this)
        seedThemeAsset("bluebubbles.attheme")
        seedThemeAsset("darkblue.attheme")
        installThemeBackend()
        // The shipped app installs the rich renderer's inline-image registry in `NativeDoApp`; the
        // test host installs it with no sprite markup, so a golden renders the real span path (a URL
        // image is its placeholder box, a sprite icon falls back to its alt text).
        DiscourseInlineImages.install { "" }
    }

    override fun beforeTest(method: Method) {
        installThemeBackend()
    }

    override fun prepareTest(test: Any) = Unit

    override fun afterTest(method: Method) = Unit

    private fun installThemeBackend() {
        Theme.setThemeBackend(TestThemeBackend)
    }

    private fun seedThemeAsset(assetName: String) {
        val source = listOf(
            File("../TMessagesProj/src/main/assets/$assetName"),
            File("apps/android/TMessagesProj/src/main/assets/$assetName"),
            File("TMessagesProj/src/main/assets/$assetName"),
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Missing $assetName for Telegram Theme tests")
        source.copyTo(File(filesDir, assetName), overwrite = true)
    }
}

private object TestThemeBackend : Theme.ThemeBackend {
    override fun supportsRemoteThemes(): Boolean = false

    override fun installRemoteTheme(
        theme: Theme.ThemeInfo,
        accent: Theme.ThemeAccent?,
        nightTheme: Boolean,
        remove: Boolean,
    ) = unsupported()

    override fun uploadTheme(theme: Theme.ThemeInfo, accent: Theme.ThemeAccent?) = unsupported()

    override fun loadRemoteThemes(account: Int, force: Boolean) = unsupported()

    override fun loadWallpaperPatterns(
        account: Int,
        request: TL_account.getMultiWallPapers,
        callback: ConnectionsManager.RequestDelegate,
    ) = unsupported()

    override fun loadRemoteWallpaper(
        account: Int,
        request: TL_account.getWallPaper,
        callback: ConnectionsManager.RequestDelegate,
    ) = unsupported()

    override fun refreshCurrentRemoteTheme(
        account: Int,
        request: TL_account.getTheme,
        callback: ConnectionsManager.RequestDelegate,
    ) = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException(
        "Remote themes are unavailable in the NativeDo test runtime",
    )
}
