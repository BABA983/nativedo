package io.github.baba983.nativedo

import android.content.Context
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.theme.NdTheme
import io.github.baba983.nativedo.core.theme.NdUiConfig
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NdAppTest {
    @After
    fun tearDown() {
        NdApp.clearForTesting()
        NotificationCenter.resetForTesting()
    }

    @Test
    fun init_createsRuntimeBoundToApplicationContext() {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context

        NdApp.init(context)

        assertThat(NdApp.appContext).isSameInstanceAs(context.applicationContext)
        assertThat(NdApp.mainHandler.looper).isSameInstanceAs(Looper.getMainLooper())
        assertThat(NdApp.runtime.theme).isSameInstanceAs(NdTheme)
        assertThat(NdApp.runtime.uiConfig).isSameInstanceAs(NdUiConfig)
        assertThat(NdApp.runtime.notificationCenter).isSameInstanceAs(NotificationCenter)
        assertThat(NdApp.runtime.sessionPreferences).isSameInstanceAs(NdApp.runtime.sessionPreferences)
        assertThat(NdApp.runtime.appLifecycleMonitor).isSameInstanceAs(NdApp.runtime.appLifecycleMonitor)
        assertThat(NdApp.runtime.imageLoader).isNotNull()
    }

    @Test
    fun initForTesting_replacesCurrentRuntime() {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        val initialRuntime = NdRuntime(context)
        val replacementRuntime = NdRuntime(context)

        NdApp.initForTesting(initialRuntime)
        NdApp.initForTesting(replacementRuntime)

        assertThat(NdApp.runtime).isSameInstanceAs(replacementRuntime)
    }
}
