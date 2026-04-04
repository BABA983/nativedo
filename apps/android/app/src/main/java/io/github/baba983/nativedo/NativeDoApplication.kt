package io.github.baba983.nativedo

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.github.baba983.nativedo.core.di.AppContainer

class NativeDoApplication : Application(), SingletonImageLoader.Factory {
    val appContainer: AppContainer by lazy { AppContainer(context = this) }

    override fun newImageLoader(context: Context): ImageLoader {
        return appContainer.newImageLoader(context)
    }
}
