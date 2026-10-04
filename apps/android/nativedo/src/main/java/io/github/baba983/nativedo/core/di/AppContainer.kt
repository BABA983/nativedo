package io.github.baba983.nativedo.core.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.ImageRequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.SessionCookieInterceptor
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.resolveNativeDoUserAgent
import io.github.baba983.nativedo.core.session.LegacyCookieMigration
import io.github.baba983.nativedo.core.session.SharedCookieJar
import io.github.baba983.nativedo.core.session.SessionCoordinator
import io.github.baba983.nativedo.core.session.CredentialStore
import io.github.baba983.nativedo.core.session.CompleteLoginUseCase
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.core.storage.EmojiCatalogStore
import io.github.baba983.nativedo.core.storage.SessionStore
import io.github.baba983.nativedo.core.storage.SharedPreferencesEmojiCatalogStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSessionStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSvgSpriteStorePersistence
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import io.github.baba983.nativedo.core.web.DiscourseSvgSpriteLoader
import io.github.baba983.nativedo.feature.topic.data.SharedPreferencesTopicReplyDraftPersistence
import io.github.baba983.nativedo.feature.topic.data.SharedPreferencesTopicReadPositionPersistence
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftStore
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionStore
import io.github.baba983.nativedo.feature.notifications.data.NotificationRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.feature.bookmarks.data.BookmarkRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import kotlinx.coroutines.CoroutineScope
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
    /** The retired native snapshot: read once by [LegacyCookieMigration], never written again. */
    private val legacyCookiePreferences = appContext?.getSharedPreferences(COOKIE_STORE_PREFS, Context.MODE_PRIVATE)
    private val sessionPreferences = appContext?.getSharedPreferences(SESSION_STORE_PREFS, Context.MODE_PRIVATE)
    private val profileCachePreferences = appContext?.getSharedPreferences(PROFILE_CACHE_PREFS, Context.MODE_PRIVATE)
    private val svgSpritePreferences = appContext?.getSharedPreferences(SVG_SPRITE_STORE_PREFS, Context.MODE_PRIVATE)
    private val emojiCatalogPreferences = appContext?.getSharedPreferences(EMOJI_CATALOG_STORE_PREFS, Context.MODE_PRIVATE)
    private val topicReplyDraftPreferences = appContext?.getSharedPreferences(TOPIC_REPLY_DRAFT_STORE_PREFS, Context.MODE_PRIVATE)
    private val topicReadPositionPreferences = appContext?.getSharedPreferences(TOPIC_READ_POSITION_STORE_PREFS, Context.MODE_PRIVATE)
    val applicationScope: CoroutineScope? = applicationScope
    /**
     * The session jar every participant shares: CookieManager is the store, this facade is how
     * OkHttp reads and writes it; the login WebView and the in-app browser use the same store
     * through Chromium.
     */
    val cookieJar: SharedCookieJar = SharedCookieJar(baseUrl)
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
    val emojiCatalogStore: EmojiCatalogStore = emojiCatalogPreferences
        ?.let { preferences ->
            EmojiCatalogStore(persistence = SharedPreferencesEmojiCatalogStorePersistence(preferences))
        }
        ?: EmojiCatalogStore()
    val topicReplyDraftStore: TopicReplyDraftStore = topicReplyDraftPreferences
        ?.let { preferences ->
            TopicReplyDraftStore(SharedPreferencesTopicReplyDraftPersistence(preferences))
        }
        ?: TopicReplyDraftStore()
    val topicViewportStore = io.github.baba983.nativedo.feature.topic.data.TopicViewportStore(topicReadPositionPreferences)
    val topicReadPositionStore: TopicReadPositionStore = topicReadPositionPreferences
        ?.let { preferences ->
            TopicReadPositionStore(SharedPreferencesTopicReadPositionPersistence(preferences))
        }
        ?: TopicReadPositionStore()
    val discourseSvgSpriteLoader: DiscourseSvgSpriteLoader? = appContext?.let { context ->
        DiscourseSvgSpriteLoader(
            appContext = context,
            baseUrl = baseUrl,
            userAgent = this.userAgent,
            svgSpriteStore = svgSpriteStore,
        )
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(SessionCookieInterceptor(cookieJar))
        .addInterceptor(
            RequestHeadersInterceptor(
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                cookieJar = cookieJar,
            ),
        )
        .build()

    val imageOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(SessionCookieInterceptor(cookieJar))
        .addInterceptor(ImageRequestHeadersInterceptor(baseUrl = baseUrl, userAgent = this.userAgent))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService: DiscourseApiService = retrofit.create(DiscourseApiService::class.java)

    val discourseApiClient: DiscourseApiClient = DiscourseApiClient(
        service = apiService,
        emojiCatalogStore = emojiCatalogStore,
    )
    val profileRepository: ProfileRepository = ProfileRepository(
        client = discourseApiClient,
        preferences = profileCachePreferences,
        json = json,
    )
    val profileFeedRepository: ProfileFeedRepository = ProfileFeedRepository(
        client = discourseApiClient,
    )
    val notificationRepository: NotificationRepository = NotificationRepository(
        client = discourseApiClient,
    )
    val bookmarkRepository: BookmarkRepository = BookmarkRepository(
        client = discourseApiClient,
    )
    val categoryStore: CategoryStore = CategoryStore(
        client = discourseApiClient,
    )
    val credentialStore: CredentialStore = CredentialStore(appContext)
    val sessionCoordinator: SessionCoordinator = SessionCoordinator(
        sessionStore = sessionStore,
        cookieJar = cookieJar,
        apiClient = discourseApiClient,
        applicationScope = applicationScope,
    )
    val completeLoginUseCase: CompleteLoginUseCase = CompleteLoginUseCase(
        sessionCoordinator = sessionCoordinator,
        cookieJar = cookieJar,
        baseUrl = baseUrl,
    )

    init {
        // One-time: the old native snapshot must not keep shadowing the shared jar. Runs before
        // any request or WebView touches the store.
        legacyCookiePreferences?.let { preferences ->
            LegacyCookieMigration.migrate(preferences, cookieJar)
        }
        discourseApiClient.setSessionBoundary(
            contextProvider = sessionCoordinator::currentRequestContext,
            onSessionInvalidated = sessionCoordinator::markExpiredIfCurrent,
            onLoginRequired = sessionCoordinator::requestLogin,
            hasAuthenticatedCookies = cookieJar::hasAuthenticatedCookies,
        )
    }

    private companion object {
        const val COOKIE_STORE_PREFS: String = "nativedo.cookie_store"
        const val SESSION_STORE_PREFS: String = "nativedo.session_store"
        const val PROFILE_CACHE_PREFS: String = "nativedo.profile_cache"
        const val SVG_SPRITE_STORE_PREFS: String = "nativedo.svg_sprite_store"
        const val EMOJI_CATALOG_STORE_PREFS: String = "nativedo.emoji_catalog"
        const val TOPIC_REPLY_DRAFT_STORE_PREFS: String = "nativedo.topic_reply_drafts"
        const val TOPIC_READ_POSITION_STORE_PREFS: String = "nativedo.topic_read_positions"
    }
}
