package io.github.baba983.nativedo

import com.google.common.truth.Truth.assertThat
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
    @Test
    fun setup_initializesNdAppAndDelegatesAppContainer() {
        val application = RuntimeEnvironment.getApplication() as NativeDoApplication

        assertThat(NdApp.runtime.appContext).isSameInstanceAs(application.applicationContext)
        assertThat(application.appContainer).isSameInstanceAs(NdApp.runtime.appContainer)
        assertThat(application.newImageLoader(application)).isNotNull()
    }
}
