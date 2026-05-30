package io.github.baba983.nativedo.core.di

import android.content.Context
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import coil3.intercept.Interceptor
import coil3.request.allowHardware
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.ImageRequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.PersistentCookieJar
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.resolveNativeDoUserAgent
import io.github.baba983.nativedo.core.session.CookieSyncBridge
import io.github.baba983.nativedo.core.session.SessionCoordinator
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import io.github.baba983.nativedo.core.storage.SharedPreferencesCookieStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSessionStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSvgSpriteStorePersistence
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class AppContainer(
    context: Context? = null,
    val baseUrl: String = "https://linux.do/",
    userAgent: String? = null,
) {
    private val json: Json = NetworkJson.instance
    private val appContext = context?.applicationContext
    val userAgent: String = userAgent ?: resolveNativeDoUserAgent(appContext)
    private val cookiePreferences = appContext?.getSharedPreferences(COOKIE_STORE_PREFS, Context.MODE_PRIVATE)
    private val sessionPreferences = appContext?.getSharedPreferences(SESSION_STORE_PREFS, Context.MODE_PRIVATE)
    private val svgSpritePreferences = appContext?.getSharedPreferences(SVG_SPRITE_STORE_PREFS, Context.MODE_PRIVATE)
    val cookieStore: CookieStore = cookiePreferences
        ?.let { preferences ->
            CookieStore(persistence = SharedPreferencesCookieStorePersistence(preferences))
        }
        ?: CookieStore()
    val sessionStore: SessionStore = sessionPreferences
        ?.let { preferences ->
            SessionStore(persistence = SharedPreferencesSessionStorePersistence(preferences))
        }
        ?: SessionStore()
    val svgSpriteStore: SvgSpriteStore = svgSpritePreferences
        ?.let { preferences ->
            SvgSpriteStore(persistence = SharedPreferencesSvgSpriteStorePersistence(preferences))
        }
        ?: SvgSpriteStore()
    val cookieSyncBridge: CookieSyncBridge = CookieSyncBridge(
        baseUrl = baseUrl,
        cookieStore = cookieStore,
    )

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(cookieStore))
        .addInterceptor(
            RequestHeadersInterceptor(
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                cookieStore = cookieStore,
            ),
        )
        .build()

    val imageOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(cookieStore))
        .addInterceptor(ImageRequestHeadersInterceptor(baseUrl = baseUrl))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService: DiscourseApiService = retrofit.create(DiscourseApiService::class.java)

    val discourseApiClient: DiscourseApiClient = DiscourseApiClient(
        service = apiService,
    )
    val categoryStore: CategoryStore = CategoryStore(
        client = discourseApiClient,
    )
    val sessionCoordinator: SessionCoordinator = SessionCoordinator(
        cookieStore = cookieStore,
        sessionStore = sessionStore,
        apiClient = discourseApiClient,
        cookieSyncBridge = cookieSyncBridge,
    )

    fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
                add(SvgDecoder.Factory())
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            imageOkHttpClient
                        },
                    ),
                )
                add(Interceptor { chain ->
                    val request = chain.request.newBuilder()
                        .allowHardware(false)
                        .build()
                    chain.withRequest(request).proceed()
                })
            }
            .build()
    }

    private companion object {
        const val COOKIE_STORE_PREFS: String = "nativedo.cookie_store"
        const val SESSION_STORE_PREFS: String = "nativedo.session_store"
        const val SVG_SPRITE_STORE_PREFS: String = "nativedo.svg_sprite_store"
    }
}
