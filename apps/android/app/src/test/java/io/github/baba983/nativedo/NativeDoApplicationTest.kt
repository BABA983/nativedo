package io.github.baba983.nativedo

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = NativeDoApplication::class,
    manifest = "src/main/AndroidManifest.xml",
)
class NativeDoApplicationTest {
    @After
    fun tearDown() {
        NdApp.clearForTesting()
    }

    @Test
    fun setup_initializesNdAppAndExposesRuntime() {
        val application = RuntimeEnvironment.getApplication() as NativeDoApplication

        assertThat(application.runtime).isSameInstanceAs(NdApp.runtime)
        assertThat(application.runtime.appContext).isSameInstanceAs(application.applicationContext)
        assertThat(application.newImageLoader(application)).isSameInstanceAs(application.runtime.imageLoader)
    }
}
