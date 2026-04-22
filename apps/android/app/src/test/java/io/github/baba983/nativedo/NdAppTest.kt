package io.github.baba983.nativedo

import android.content.Context
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NdAppTest {
    @Test
    fun init_createsRuntimeBoundToApplicationContext() {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context

        NdApp.init(context)

        assertThat(NdApp.runtime.appContext).isSameInstanceAs(context.applicationContext)
        assertThat(NdApp.runtime.mainHandler.looper).isSameInstanceAs(Looper.getMainLooper())
        assertThat(NdApp.runtime.appContainer.baseUrl).isEqualTo("https://linux.do/")
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
