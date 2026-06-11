package io.github.baba983.nativedo

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import okhttp3.Request
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.AutoDeleteMediaTask
import org.telegram.messenger.ImageLoader as TelegramImageLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.SharedConfig
import io.github.baba983.nativedo.core.di.AppContainer
import org.telegram.ui.ActionBar.Theme
import java.io.File
import java.io.RandomAccessFile

class NativeDoApp : Application(), SingletonImageLoader.Factory {
    val appContainer: AppContainer by lazy { AppContainer(context = this) }

    override fun onCreate() {
        super.onCreate()
        ApplicationLoader.init(this)
        SharedConfig.loadBrowserConfig()
        LocaleController.init(this)
        AndroidUtilities.init(this)
        Theme.init(this)

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
        AutoDeleteMediaTask.run()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return appContainer.newImageLoader(context)
    }
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
