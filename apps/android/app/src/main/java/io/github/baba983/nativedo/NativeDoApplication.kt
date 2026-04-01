package io.github.baba983.nativedo

import android.app.Application
import io.github.baba983.nativedo.core.di.AppContainer

class NativeDoApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }
}
