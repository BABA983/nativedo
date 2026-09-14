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
import io.github.baba983.nativedo.core.network.SessionCookieInterceptor
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.resolveNativeDoUserAgent
import io.github.baba983.nativedo.core.session.CookieSyncBridge
import io.github.baba983.nativedo.core.session.SessionCoordinator
import io.github.baba983.nativedo.core.session.CredentialStore
import io.github.baba983.nativedo.core.session.CompleteLoginUseCase
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import io.github.baba983.nativedo.core.storage.SharedPreferencesCookieStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSessionStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSvgSpriteStorePersistence
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import io.github.baba983.nativedo.core.web.DiscourseSvgSpriteLoader
import io.github.baba983.nativedo.feature.topic.data.SharedPreferencesTopicReplyDraftPersistence
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftStore
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class AppContainer(
    context: Context? = null,
    val baseUrl: String = "https://linux.do/",
    userAgent: String? = null,
    applicationScope: kotlinx.coroutines.CoroutineScope? = null,
) {
    private val json: Json = NetworkJson.instance
    private val appContext = context?.applicationContext
    val userAgent: String = userAgent ?: resolveNativeDoUserAgent(appContext)
    private val cookiePreferences = appContext?.getSharedPreferences(COOKIE_STORE_PREFS, Context.MODE_PRIVATE)
    private val sessionPreferences = appContext?.getSharedPreferences(SESSION_STORE_PREFS, Context.MODE_PRIVATE)
    private val profileCachePreferences = appContext?.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
    private val svgSpritePreferences = appContext?.getSharedPreferences(SVG_SPRITE_STORE_PREFS, Context.MODE_PRIVATE)
    private val topicReplyDraftPreferences = appContext?.getSharedPreferences(TOPIC_REPLY_DRAFT_STORE_PREFS, Context.MODE_PRIVATE)
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
    val topicReplyDraftStore: TopicReplyDraftStore = topicReplyDraftPreferences
        ?.let { preferences ->
            TopicReplyDraftStore(SharedPreferencesTopicReplyDraftPersistence(preferences))
        }
        ?: TopicReplyDraftStore()
    val cookieSyncBridge: CookieSyncBridge = CookieSyncBridge(
        baseUrl = baseUrl,
        cookieStore = cookieStore,
    )
    val discourseSvgSpriteLoader: DiscourseSvgSpriteLoader? = appContext?.let { context ->
        DiscourseSvgSpriteLoader(
            appContext = context,
            baseUrl = baseUrl,
            userAgent = this.userAgent,
            cookieSyncBridge = cookieSyncBridge,
            svgSpriteStore = svgSpriteStore,
        )
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(SessionCookieInterceptor(cookieStore))
        .addInterceptor(
            RequestHeadersInterceptor(
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                cookieStore = cookieStore,
            ),
        )
        .build()

    val imageOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(SessionCookieInterceptor(cookieStore))
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
    val profileRepository: ProfileRepository = ProfileRepository(
        client = discourseApiClient,
        preferences = profileCachePreferences,
        json = json,
    )
    val categoryStore: CategoryStore = CategoryStore(
        client = discourseApiClient,
    )
    val credentialStore: CredentialStore = CredentialStore(appContext)
    val sessionCoordinator: SessionCoordinator = SessionCoordinator(
        cookieStore = cookieStore,
        sessionStore = sessionStore,
        apiClient = discourseApiClient,
        cookieSyncBridge = cookieSyncBridge,
        applicationScope = applicationScope,
    )
    val completeLoginUseCase: CompleteLoginUseCase = CompleteLoginUseCase(
        sessionCoordinator = sessionCoordinator,
        cookieSyncBridge = cookieSyncBridge,
        baseUrl = baseUrl,
    )

    init {
        discourseApiClient.setSessionBoundary(
            contextProvider = sessionCoordinator::currentRequestContext,
            onSessionInvalidated = sessionCoordinator::markExpiredIfCurrent,
            onLoginRequired = sessionCoordinator::requestLogin,
        )
    }

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
        const val PROFILE_CACHE_PREFS: String = "nativedo.profile_cache"
        const val SVG_SPRITE_STORE_PREFS: String = "nativedo.svg_sprite_store"
        const val TOPIC_REPLY_DRAFT_STORE_PREFS: String = "nativedo.topic_reply_drafts"
    }
}
