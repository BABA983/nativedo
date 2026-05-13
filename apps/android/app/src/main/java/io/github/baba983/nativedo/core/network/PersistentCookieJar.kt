package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    private val cookieStore: CookieStore,
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore.save(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.load(url)
}
