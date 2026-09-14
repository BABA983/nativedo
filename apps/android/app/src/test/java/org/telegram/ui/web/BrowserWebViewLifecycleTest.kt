package org.telegram.ui.web

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.ui.ActionBar.BottomSheetTabs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BrowserWebViewLifecycleTest {
    private class RecordingWebView : BotWebViewContainer.MyWebView(RuntimeEnvironment.getApplication()) {
        var destroyCalls = 0
        var lastScript: String? = null
        override fun evaluateJavascript(script: String, callback: android.webkit.ValueCallback<String>?) {
            lastScript = script
        }
        override fun destroy() { destroyCalls++; super.destroy() }
    }

    @Test
    fun pausingRetainedPageStopsHtmlMediaWithoutDestroyingPage() {
        val web = RecordingWebView()
        web.onPause()
        assertThat(web.lastScript).contains("audio,video")
        assertThat(web.lastScript).contains("m.pause()")
        assertThat(web.destroyCalls).isEqualTo(0)
        web.destroy()
        web.lastScript = null
        web.onPause()
        assertThat(web.lastScript).isNull()
    }

    @Test
    fun closingCachedTabDestroysItsWebViewExactlyOnce() {
        val web = RecordingWebView()
        val tab = BottomSheetTabs.WebTabData().apply { webView = web }
        tab.destroy()
        tab.destroy()
        assertThat(web.destroyCalls).isEqualTo(1)
        assertThat(web.isDestroyed).isTrue()
        assertThat(tab.webView).isNull()
    }

    @Test
    fun readingEmptyContainerDoesNotCreateRenderer() {
        val container = BotWebViewContainer(RuntimeEnvironment.getApplication(), null, 0, false)
        assertThat(container.webView).isNull()
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun preservingThenResettingReleasesContainerOwnershipWithoutReattachingOrDestroyingTab() {
        val container = BotWebViewContainer(RuntimeEnvironment.getApplication(), null, 0, false)
        val web = RecordingWebView()
        container.replaceWebView(0, web, null)
        container.preserveWebView()
        container.destroyWebView()
        container.resetWebView()
        assertThat(container.webView).isNull()
        assertThat(container.childCount).isEqualTo(0)
        assertThat(web.destroyCalls).isEqualTo(0)
        assertThat(web.parent).isNull()
        BottomSheetTabs.WebTabData().apply { webView = web }.destroy()
        assertThat(web.destroyCalls).isEqualTo(1)
    }
}
