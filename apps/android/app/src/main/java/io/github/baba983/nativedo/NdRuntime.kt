package io.github.baba983.nativedo

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import io.github.baba983.nativedo.core.event.AppLifecycleMonitor
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.theme.NdTheme
import io.github.baba983.nativedo.core.theme.NdUiConfig
import io.github.baba983.nativedo.core.util.AndroidUtilities
import io.github.baba983.nativedo.core.util.FileLog
import okhttp3.OkHttpClient

class NdRuntime(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    val mainHandler: Handler = Handler(Looper.getMainLooper())
    val notificationCenter: NotificationCenter = NotificationCenter
    val theme: NdTheme = NdTheme
    val uiConfig: NdUiConfig = NdUiConfig

    private val serviceLock = Any()
    private val sharedServices = mutableMapOf<String, Any>()

    @Volatile
    private var bootstrapped: Boolean = false

    val sessionPreferences: SharedPreferences
        get() = getOrCreateSharedService(ServiceSessionPreferences) {
            appContext.getSharedPreferences(SessionPreferencesName, Context.MODE_PRIVATE)
        }

    val appLifecycleMonitor: AppLifecycleMonitor
        get() = getOrCreateSharedService(ServiceAppLifecycleMonitor) {
            AppLifecycleMonitor(appContext)
        }

    val imageOkHttpClient: OkHttpClient
        get() = getOrCreateSharedService(ServiceImageOkHttpClient) {
            OkHttpClient.Builder().build()
        }

    val imageLoader: ImageLoader
        get() = getOrCreateSharedService(ServiceImageLoader) {
            createImageLoader(appContext)
        }

    init {
        AndroidUtilities.init(appContext)
        FileLog.init(appContext)
        NdUiConfig.init(appContext)
        NdTheme.init(appContext)
    }

    fun bootstrap() {
        if (bootstrapped) {
            return
        }
        synchronized(serviceLock) {
            if (bootstrapped) {
                return
            }
            sessionPreferences
            bootstrapped = true
        }
        FileLog.d("$LogTag: NdRuntime bootstrapped")
    }

    fun dispose() {
        synchronized(serviceLock) {
            (sharedServices.remove(ServiceAppLifecycleMonitor) as? AppLifecycleMonitor)?.dispose()
            sharedServices.clear()
            bootstrapped = false
        }
    }

    fun newImageLoader(context: Context): ImageLoader {
        return if (context.applicationContext === appContext) {
            imageLoader
        } else {
            createImageLoader(context)
        }
    }

    private fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
                add(SvgDecoder.Factory())
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            imageOkHttpClient
                        },
                    ),
                )
            }
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getOrCreateSharedService(
        key: String,
        factory: () -> T,
    ): T {
        synchronized(serviceLock) {
            val existing = sharedServices[key]
            if (existing != null) {
                return existing as T
            }
            return factory().also { service ->
                sharedServices[key] = service
            }
        }
    }

    private companion object {
        const val LogTag: String = "NdRuntime"
        const val SessionPreferencesName: String = "nativedo.discourse.session"
        const val ServiceAppLifecycleMonitor: String = "app_lifecycle_monitor"
        const val ServiceImageLoader: String = "image_loader"
        const val ServiceImageOkHttpClient: String = "image_okhttp_client"
        const val ServiceSessionPreferences: String = "session_preferences"
    }
}
