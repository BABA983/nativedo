package io.github.baba983.nativedo.core.navigation

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserExternalOpenMode
import org.junit.Test

class LinkOpenPolicyTest {

    private val policy = LinkOpenPolicy(baseUrl = "https://linux.do")
    private val restrictedPolicy = LinkOpenPolicy(
        baseUrl = "https://linux.do",
        restrictedDomains = setOf("example.com"),
        defaultExternalOpenMode = AppBrowserExternalOpenMode.SystemBrowser,
    )

    @Test
    fun resolve_topicLink_returnsNativeTopic() {
        val result = policy.resolve("https://linux.do/t/topic-slug/12345")

        assertThat(result).isEqualTo(LinkNavigationDecision.NativeTopic(12345))
    }

    @Test
    fun resolve_userLink_returnsNativeUser() {
        val result = policy.resolve("/u/baba")

        assertThat(result).isEqualTo(LinkNavigationDecision.NativeUser("baba"))
    }

    @Test
    fun resolve_externalUrl_returnsExternalApp() {
        val result = policy.resolve("https://example.com/page")

        assertThat(result).isEqualTo(LinkNavigationDecision.ExternalApp("https://example.com/page"))
    }

    @Test
    fun resolve_categoryLink_returnsAppBrowser() {
        val result = policy.resolve("https://linux.do/c/category/5")

        assertThat(result).isEqualTo(
            LinkNavigationDecision.AppBrowser("https://linux.do/c/category/5"),
        )
    }

    @Test
    fun resolve_mailtoLink_returnsExternalApp() {
        val result = policy.resolve("mailto:test@example.com")

        assertThat(result).isEqualTo(
            LinkNavigationDecision.ExternalApp("mailto:test@example.com"),
        )
    }

    @Test
    fun resolve_restrictedDomain_returnsSystemBrowserInsteadOfAppBrowser() {
        val result = restrictedPolicy.resolve("https://example.com/page")

        assertThat(result).isEqualTo(
            LinkNavigationDecision.SystemBrowser("https://example.com/page"),
        )
    }

    @Test
    fun resolve_defaultExternalOpenMode_systemBrowserReturnsSystemBrowserForExternalWebLink() {
        val result = restrictedPolicy.resolve("https://another.example/page")

        assertThat(result).isEqualTo(
            LinkNavigationDecision.SystemBrowser("https://another.example/page"),
        )
    }

    @Test
    fun resolve_restrictedLinuxDoBrowserTarget_returnsSystemBrowserWithoutAffectingNativeRoutes() {
        val policy = LinkOpenPolicy(
            baseUrl = "https://linux.do",
            restrictedDomains = setOf("linux.do"),
            defaultExternalOpenMode = AppBrowserExternalOpenMode.InAppBrowser,
        )

        assertThat(policy.resolve("https://linux.do/c/android/5")).isEqualTo(
            LinkNavigationDecision.SystemBrowser("https://linux.do/c/android/5"),
        )
        assertThat(policy.resolve("https://linux.do/t/topic-slug/12345")).isEqualTo(
            LinkNavigationDecision.NativeTopic(12345),
        )
    }
}
