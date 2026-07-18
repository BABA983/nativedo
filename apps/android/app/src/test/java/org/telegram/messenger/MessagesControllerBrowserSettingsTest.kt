package org.telegram.messenger

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MessagesControllerBrowserSettingsTest {

    private lateinit var controller: MessagesController

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        MessagesController.getGlobalMainSettings()
            .edit()
            .remove("web_browser_settings_v2")
            .commit()
        controller = MessagesController.getInstance(0)
        controller.loadWebBrowserConfig(true)
    }

    @Test
    fun defaultsToInAppBrowser() {
        assertThat(controller.isWebBrowserInAppEnabled()).isTrue()
        assertThat(controller.isWebBrowserUseCustomTabs()).isFalse()
        assertThat(controller.isWebBrowserOpenInApp("https://linux.do/t/1")).isTrue()
        assertThat(controller.getWebBrowserExceptionsList(true)).isEmpty()
        assertThat(controller.getWebBrowserExceptionsList(false)).isEmpty()
    }

    @Test
    fun externalExceptionOverridesInAppDefaultForDomainAndSubdomains() {
        controller.addWebBrowserException("https://linux.do/t/1", true)

        assertThat(controller.isWebBrowserOpenInApp("https://linux.do/t/2")).isFalse()
        assertThat(controller.isWebBrowserOpenInApp("https://cdn.linux.do/file")).isFalse()
        assertThat(controller.isWebBrowserOpenInApp("https://example.com")).isTrue()
        assertThat(controller.getWebBrowserExceptionsList(true).map { it.domain })
            .containsExactly("linux.do")
    }

    @Test
    fun inAppExceptionOverridesExternalDefault() {
        controller.toggleWebBrowserInAppEnabled()
        controller.toggleWebBrowserUseCustomTabs(true)
        controller.addWebBrowserException("docs.linux.do", false)

        assertThat(controller.isWebBrowserInAppEnabled()).isFalse()
        assertThat(controller.isWebBrowserUseCustomTabs()).isTrue()
        assertThat(controller.isWebBrowserOpenInApp("https://docs.linux.do/guide")).isTrue()
        assertThat(controller.isWebBrowserOpenInApp("https://other.example.com")).isFalse()
        assertThat(controller.getWebBrowserExceptionsList(false).map { it.domain })
            .containsExactly("docs.linux.do")
    }

    @Test
    fun addingOppositePolicyMovesDomainBetweenLists() {
        controller.addWebBrowserException("linux.do", true)
        controller.addWebBrowserException("https://linux.do", false)

        assertThat(controller.getWebBrowserExceptionsList(true)).isEmpty()
        assertThat(controller.getWebBrowserExceptionsList(false).map { it.domain })
            .containsExactly("linux.do")
    }

    @Test
    fun settingsAndExceptionsReloadFromLocalPersistence() {
        controller.toggleWebBrowserInAppEnabled()
        controller.toggleWebBrowserUseCustomTabs(true)
        controller.addWebBrowserException("docs.linux.do", false)

        controller.loadWebBrowserConfig(true)

        assertThat(controller.isWebBrowserInAppEnabled()).isFalse()
        assertThat(controller.isWebBrowserUseCustomTabs()).isTrue()
        assertThat(controller.isWebBrowserOpenInApp("https://docs.linux.do/topic")).isTrue()
    }

    @Test
    fun clearMatchesPinnedDefaultAndRemovesBothLists() {
        controller.addWebBrowserException("external.example", true)
        controller.addWebBrowserException("internal.example", false)

        controller.clearAllWebBrowserExceptions()

        assertThat(controller.isWebBrowserInAppEnabled()).isFalse()
        assertThat(controller.isWebBrowserUseCustomTabs()).isTrue()
        assertThat(controller.getWebBrowserExceptionsList(true)).isEmpty()
        assertThat(controller.getWebBrowserExceptionsList(false)).isEmpty()
    }
}
