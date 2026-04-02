package io.github.baba983.nativedo.core.di

import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.PersistentCookieJar
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.CsrfTokenProvider
import io.github.baba983.nativedo.core.session.CookieSyncBridge
import io.github.baba983.nativedo.core.session.SessionCoordinator
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(
    val baseUrl: String = "https://linux.do/",
) {
    private val json: Json = NetworkJson.instance
    val cookieStore: CookieStore = CookieStore()
    val sessionStore: SessionStore = SessionStore()
    val cookieSyncBridge: CookieSyncBridge = CookieSyncBridge(
        baseUrl = baseUrl,
        cookieStore = cookieStore,
    )

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(cookieStore))
        .addInterceptor(RequestHeadersInterceptor(baseUrl = baseUrl))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService: DiscourseApiService = retrofit.create(DiscourseApiService::class.java)

    val discourseApiClient: DiscourseApiClient = DiscourseApiClient(apiService)
    val csrfTokenProvider: CsrfTokenProvider = CsrfTokenProvider(discourseApiClient)
    val sessionCoordinator: SessionCoordinator = SessionCoordinator(
        cookieStore = cookieStore,
        sessionStore = sessionStore,
        apiClient = discourseApiClient,
        csrfTokenProvider = csrfTokenProvider,
        cookieSyncBridge = cookieSyncBridge,
    )
}
