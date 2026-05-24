package io.github.baba983.nativedo

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import io.github.baba983.nativedo.core.di.AppContainer
import org.telegram.ui.ActionBar.Theme

class NativeDoApp : Application(), SingletonImageLoader.Factory {
    val appContainer: AppContainer by lazy { AppContainer(context = this) }

    override fun onCreate() {
        super.onCreate()
        ApplicationLoader.init(this)
        AndroidUtilities.init(this)
        Theme.init(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return appContainer.newImageLoader(context)
    }
}
