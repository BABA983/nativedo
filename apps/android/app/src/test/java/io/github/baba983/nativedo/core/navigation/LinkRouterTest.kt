package io.github.baba983.nativedo.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkRouterTest {

    private val router = LinkRouter(baseUrl = "https://linux.do")

    @Test
    fun resolve_topicLinkWithSlug_returnsNativeTopic() {
        val result = router.resolve("https://linux.do/t/some-topic/12345")
        assertThat(result).isEqualTo(LinkOpenTarget.NativeTopic(12345))
    }

    @Test
    fun resolve_topicLinkWithoutSlug_returnsNativeTopic() {
        val result = router.resolve("https://linux.do/t/12345")
        assertThat(result).isEqualTo(LinkOpenTarget.NativeTopic(12345))
    }

    @Test
    fun resolve_relativeTopicLink_returnsNativeTopic() {
        val result = router.resolve("/t/some-topic/12345")
        assertThat(result).isEqualTo(LinkOpenTarget.NativeTopic(12345))
    }

    @Test
    fun resolve_userLink_returnsNativeUser() {
        val result = router.resolve("https://linux.do/u/baba")
        assertThat(result).isEqualTo(LinkOpenTarget.NativeUser("baba"))
    }

    @Test
    fun resolve_relativeUserLink_returnsNativeUser() {
        val result = router.resolve("/u/baba")
        assertThat(result).isEqualTo(LinkOpenTarget.NativeUser("baba"))
    }

    @Test
    fun resolve_categoryLink_returnsAppBrowser() {
        val url = "https://linux.do/c/category/5"
        val result = router.resolve(url)
        assertThat(result).isInstanceOf(LinkOpenTarget.AppBrowser::class.java)
    }

    @Test
    fun resolve_tagLink_returnsAppBrowser() {
        val url = "https://linux.do/tag/android"
        val result = router.resolve(url)
        assertThat(result).isInstanceOf(LinkOpenTarget.AppBrowser::class.java)
    }

    @Test
    fun resolve_searchLink_returnsAppBrowser() {
        val url = "https://linux.do/search?q=kotlin"
        val result = router.resolve(url)
        assertThat(result).isInstanceOf(LinkOpenTarget.AppBrowser::class.java)
    }

    @Test
    fun resolve_externalUrl_returnsExternalApp() {
        val url = "https://example.com/page"
        val result = router.resolve(url)
        assertThat(result).isEqualTo(LinkOpenTarget.ExternalApp(url))
    }

    @Test
    fun resolve_mailtoLink_returnsExternalApp() {
        val url = "mailto:test@example.com"
        val result = router.resolve(url)
        assertThat(result).isEqualTo(LinkOpenTarget.ExternalApp(url))
    }

    @Test
    fun resolve_telLink_returnsExternalApp() {
        val url = "tel:+1234567890"
        val result = router.resolve(url)
        assertThat(result).isEqualTo(LinkOpenTarget.ExternalApp(url))
    }

    @Test
    fun resolve_intentScheme_returnsExternalApp() {
        val url = "intent://some-path#Intent;scheme=myapp;end"
        val result = router.resolve(url)
        assertThat(result).isEqualTo(LinkOpenTarget.ExternalApp(url))
    }

    @Test
    fun resolve_linuxDoRoot_returnsAppBrowser() {
        val url = "https://linux.do/"
        val result = router.resolve(url)
        assertThat(result).isInstanceOf(LinkOpenTarget.AppBrowser::class.java)
    }
}
