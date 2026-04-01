package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class CookieSyncBridge(
    private val cookieStore: CookieStore,
) {
    fun sync(url: String, cookies: List<Cookie>) {
        cookieStore.save(url.toHttpUrl(), cookies)
    }
}
