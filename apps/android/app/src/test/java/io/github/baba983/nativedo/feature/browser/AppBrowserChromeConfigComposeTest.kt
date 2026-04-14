package io.github.baba983.nativedo.feature.browser

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppBrowserChromeConfigComposeTest {
    @Test
    fun chromeConfigSideEffect_reEmitsWhenScrollProgressChanges() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val emittedScrollProgress = mutableListOf<Float>()
        lateinit var scrollProgressState: androidx.compose.runtime.MutableState<Float>

        activity.runOnUiThread {
            scrollProgressState = mutableStateOf(0f)
            activity.setContent {
                val chrome = AppBrowserChromeModel(
                    title = "linux.do",
                    subtitle = null,
                    currentUrl = "https://linux.do",
                    isHttps = true,
                    progress = 0f,
                    scrollProgress = scrollProgressState.value,
                    isLoadingProgressVisible = false,
                    canGoBack = false,
                    canGoForward = false,
                    backHistoryEntries = emptyList(),
                    browserHistoryEntries = emptyList(),
                    alpha = 1f,
                    translationX = 0f,
                )
                SideEffect {
                    emittedScrollProgress += chrome.scrollProgress
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        activity.runOnUiThread {
            scrollProgressState.value = 0.42f
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(emittedScrollProgress).containsExactly(0f, 0.42f).inOrder()
    }
}
