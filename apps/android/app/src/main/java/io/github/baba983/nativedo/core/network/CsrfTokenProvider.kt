package io.github.baba983.nativedo.core.network

open class CsrfTokenProvider protected constructor() {
    private var csrfToken: String? = null
    private lateinit var apiClient: DiscourseApiClient

    constructor(apiClient: DiscourseApiClient) : this() {
        this.apiClient = apiClient
    }

    open suspend fun get(forceRefresh: Boolean = false): String {
        if (forceRefresh || csrfToken == null) {
            csrfToken = apiClient.fetchCsrfToken()
        }
        return checkNotNull(csrfToken)
    }
}
