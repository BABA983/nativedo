package io.github.baba983.nativedo.core.storage

import okhttp3.Cookie
import okhttp3.HttpUrl

interface CookieStore {
    fun hasSessionCookies(): Boolean

    fun load(url: HttpUrl): List<Cookie>

    fun save(url: HttpUrl, cookies: List<Cookie>)

    fun clear()
}

class InMemoryCookieStore : CookieStore {
    private var cookies: Map<String, List<Cookie>> = emptyMap()

    override fun hasSessionCookies(): Boolean = cookies.values.flatten().isNotEmpty()

    override fun load(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty()

    override fun save(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies = this.cookies + (url.host to cookies)
    }

    override fun clear() {
        cookies = emptyMap()
    }
}
