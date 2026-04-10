package io.github.baba983.nativedo.core.navigation

sealed interface LinkOpenTarget {
    data class NativeTopic(val topicId: Long) : LinkOpenTarget
    data class NativeUser(val username: String) : LinkOpenTarget
    data class ExternalApp(val url: String) : LinkOpenTarget
    data class AppBrowser(val url: String) : LinkOpenTarget
    data class SystemBrowser(val url: String) : LinkOpenTarget
}

class LinkRouter(private val baseUrl: String) {

    private val linkOpenPolicy = LinkOpenPolicy(baseUrl)

    fun resolve(url: String): LinkOpenTarget {
        return when (val decision = linkOpenPolicy.resolve(url)) {
            is LinkNavigationDecision.NativeTopic -> LinkOpenTarget.NativeTopic(decision.topicId)
            is LinkNavigationDecision.NativeUser -> LinkOpenTarget.NativeUser(decision.username)
            is LinkNavigationDecision.ExternalApp -> LinkOpenTarget.ExternalApp(decision.url)
            is LinkNavigationDecision.AppBrowser -> LinkOpenTarget.AppBrowser(decision.url)
            is LinkNavigationDecision.SystemBrowser -> LinkOpenTarget.SystemBrowser(decision.url)
        }
    }
}
