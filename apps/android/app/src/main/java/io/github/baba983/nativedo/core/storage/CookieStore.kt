package io.github.baba983.nativedo.core.storage

import okhttp3.Cookie
import okhttp3.HttpUrl

class CookieStore {
    private var cookies: Map<String, List<Cookie>> = emptyMap()

    fun hasSessionCookies(): Boolean = cookies.values.flatten().isNotEmpty()

    fun load(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty()

    fun save(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies = this.cookies + (url.host to cookies)
    }

    fun clear() {
        cookies = emptyMap()
    }
}
