package io.github.baba983.nativedo

import android.content.Context
import android.os.Handler
import android.os.Looper
import coil3.ImageLoader
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.util.AndroidUtilities
import io.github.baba983.nativedo.core.util.FileLog

class NdRuntime(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    val mainHandler: Handler = Handler(Looper.getMainLooper())
    val appContainer: AppContainer = AppContainer(context = appContext)

    init {
        AndroidUtilities.init(appContext)
        FileLog.init(appContext)
    }

    fun newImageLoader(context: Context): ImageLoader {
        return appContainer.newImageLoader(context)
    }
}
