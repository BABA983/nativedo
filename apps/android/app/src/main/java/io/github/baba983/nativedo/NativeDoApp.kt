package io.github.baba983.nativedo

import coil3.ImageLoader
import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import coil3.SingletonImageLoader
import okhttp3.Request
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ImageLoader as TelegramImageLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.SharedConfig
import io.github.baba983.nativedo.core.di.AppContainer
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.tl.TL_account
import org.telegram.ui.ActionBar.Theme
import java.io.File
import java.io.RandomAccessFile

class NativeDoApp : Application(), SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val appContainer: AppContainer by lazy {
        AppContainer(context = this, applicationScope = applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        ApplicationLoader.init(this)
        SharedConfig.loadBrowserConfig()
        LocaleController.init(this)
        AndroidUtilities.init(this)
        Theme.setThemeBackend(NativeDoThemeBackend)
        Theme.createDialogsResources(this)
        Theme.createChatResources(this, false)

        // Take over Telegram ImageLoader's HTTP transfer with the app's OkHttp
        // client. Product-specific concerns (auth cookies, Cloudflare clearance,
        // UA pinning, Referer) live in the OkHttp interceptor chain — :telegram
        // doesn't need to know about them.
        TelegramImageLoader.setHttpImageDownloader(
            NativeDoImageDownloader(appContainer)
        )
        // Eagerly construct the Telegram ImageLoader so FileLoader.setMediaDirs
        // runs before any ImageReceiver call site reads it. Avoids the
        // first-frame NPE we used to paper over in FileLoader.getDirectory.
        TelegramImageLoader.getInstance()
        ApplicationLoader.checkFreeDiscSpace(0)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return appContainer.newImageLoader(context)
    }
}

private object NativeDoThemeBackend : Theme.ThemeBackend {
    private fun unsupported(): Nothing = throw UnsupportedOperationException(
        "linux.do does not provide a remote theme catalog; use local theme import/export",
    )

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
}

private class NativeDoImageDownloader(
    private val appContainer: AppContainer,
) : TelegramImageLoader.HttpImageDownloader {

    override fun download(
        url: String,
        dest: File,
        progress: TelegramImageLoader.HttpImageDownloader.ProgressListener,
        cancelSignal: TelegramImageLoader.HttpImageDownloader.CancelSignal,
    ): Boolean {
        val request = Request.Builder().url(url).build()
        val call = appContainer.imageOkHttpClient.newCall(request)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                return false
            }
            val body = response.body ?: return false
            val total = body.contentLength()
            val output = RandomAccessFile(dest, "rws")
            try {
                body.byteStream().use { input ->
                    val buf = ByteArray(8 * 1024)
                    var loaded = 0L
                    while (true) {
                        if (cancelSignal.isCancelled) {
                            call.cancel()
                            return false
                        }
                        val read = input.read(buf)
                        if (read < 0) {
                            if (total > 0) progress.onProgress(total, total)
                            return true
                        }
                        if (read == 0) continue
                        output.write(buf, 0, read)
                        loaded += read
                        if (total > 0) progress.onProgress(loaded, total)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    true
                }
            } finally {
                output.close()
            }
        }
    }
}
