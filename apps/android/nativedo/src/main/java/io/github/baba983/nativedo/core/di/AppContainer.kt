package io.github.baba983.nativedo.core.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.auth.device.DeviceAuthApi
import io.github.baba983.nativedo.auth.device.DeviceKeyStore
import io.github.baba983.nativedo.auth.device.DeviceLoginKey
import io.github.baba983.nativedo.auth.device.DeviceLoginKeys
import io.github.baba983.nativedo.auth.device.HttpDeviceAuthApi
import io.github.baba983.nativedo.core.messagebus.MessageBusSession
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.DiscourseGifClient
import io.github.baba983.nativedo.core.network.DiscourseGifService
import io.github.baba983.nativedo.core.network.ImageRequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.ReadCacheScope
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import io.github.baba983.nativedo.core.network.SessionCookieInterceptor
import io.github.baba983.nativedo.core.network.resolveNativeDoUserAgent
import io.github.baba983.nativedo.core.network.resolveAcceptLanguage
import io.github.baba983.nativedo.core.session.AndroidKeystoreCipher
import io.github.baba983.nativedo.core.session.CompleteLoginUseCase
import io.github.baba983.nativedo.core.session.CredentialStore
import io.github.baba983.nativedo.core.session.LegacyCookieMigration
import io.github.baba983.nativedo.core.session.SessionCoordinator
import io.github.baba983.nativedo.core.session.SharedCookieJar
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.core.storage.DiscourseReadCache
import io.github.baba983.nativedo.core.storage.EmojiCatalogStore
import io.github.baba983.nativedo.core.storage.NoCategoryPersistence
import io.github.baba983.nativedo.core.storage.SessionStore
import io.github.baba983.nativedo.core.storage.SharedPreferencesCategoryPersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesDiscourseReadCachePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesEmojiCatalogStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSessionStorePersistence
import io.github.baba983.nativedo.core.storage.SharedPreferencesSvgSpriteStorePersistence
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import io.github.baba983.nativedo.core.web.BrowserClearanceRefresher
import io.github.baba983.nativedo.core.web.DiscourseSvgSpriteLoader
import io.github.baba983.nativedo.feature.bookmarks.data.BookmarkRepository
import io.github.baba983.nativedo.feature.chat.data.ChatDraftStore
import io.github.baba983.nativedo.feature.chat.data.ChatRepository
import io.github.baba983.nativedo.feature.chat.data.SharedPreferencesChatDraftPersistence
import io.github.baba983.nativedo.feature.notifications.data.NotificationRepository
import io.github.baba983.nativedo.feature.messages.data.PrivateMessagesRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.feature.topic.data.SharedPreferencesTopicReadPositionPersistence
import io.github.baba983.nativedo.feature.topic.data.SharedPreferencesTopicReplyDraftPersistence
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionStore
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    private val categoryPreferences = appContext?.getSharedPreferences(CATEGORY_STORE_PREFS, Context.MODE_PRIVATE)
    private val readCachePreferences = appContext?.getSharedPreferences(READ_CACHE_PREFS, Context.MODE_PRIVATE)
    private val chatDraftPreferences = appContext?.getSharedPreferences(CHAT_DRAFT_STORE_PREFS, Context.MODE_PRIVATE)

    val applicationScope: CoroutineScope? = applicationScope

    /**
     * The session jar every participant shares: CookieManager is the store, this facade is how
     * OkHttp reads and writes it; the login WebView and the in-app browser use the same store
     * through Chromium.
     */
    val cookieJar: SharedCookieJar = SharedCookieJar(baseUrl)
    val browserClearanceRefresher: BrowserClearanceRefresher? =
        appContext?.let { context ->
            BrowserClearanceRefresher(
                appContext = context,
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                cookieJar = cookieJar,
            )
        }
    val sessionStore: SessionStore =
        sessionPreferences
            ?.let { preferences ->
                SessionStore(persistence = SharedPreferencesSessionStorePersistence(preferences))
            }
            ?: SessionStore()
    val svgSpriteStore: SvgSpriteStore =
        svgSpritePreferences
            ?.let { preferences ->
                SvgSpriteStore(persistence = SharedPreferencesSvgSpriteStorePersistence(preferences))
            }
            ?: SvgSpriteStore()
    val emojiCatalogStore: EmojiCatalogStore =
        emojiCatalogPreferences
            ?.let { preferences ->
                EmojiCatalogStore(persistence = SharedPreferencesEmojiCatalogStorePersistence(preferences))
            }
            ?: EmojiCatalogStore()

    /**
     * The reads the reader has already made, kept so an outage can still draw them. It belongs to
     * the container rather than a screen: the topic list and the topic screen both fall back through
     * the one store `DiscourseApiClient` reads and writes.
     */
    val discourseReadCache: DiscourseReadCache =
        readCachePreferences
            ?.let { preferences -> DiscourseReadCache(SharedPreferencesDiscourseReadCachePersistence(preferences)) }
            ?: DiscourseReadCache()
    val topicReplyDraftStore: TopicReplyDraftStore =
        topicReplyDraftPreferences
            ?.let { preferences ->
                TopicReplyDraftStore(SharedPreferencesTopicReplyDraftPersistence(preferences))
            }
            ?: TopicReplyDraftStore()
    val topicViewportStore =
        io.github.baba983.nativedo.feature.topic.data
            .TopicViewportStore(topicReadPositionPreferences)
    val topicReadPositionStore: TopicReadPositionStore =
        topicReadPositionPreferences
            ?.let { preferences ->
                TopicReadPositionStore(SharedPreferencesTopicReadPositionPersistence(preferences))
            }
            ?: TopicReadPositionStore()
    val discourseSvgSpriteLoader: DiscourseSvgSpriteLoader? =
        appContext?.let { context ->
            DiscourseSvgSpriteLoader(
                appContext = context,
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                svgSpriteStore = svgSpriteStore,
            )
        }

    /**
     * The app's own HTTP stack: session cookies, the site's request headers and the user agent are
     * its interceptors, so a caller that needs the same session (the message bus) shares it instead
     * of building a second jar.
     */
    val okHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .addNetworkInterceptor(SessionCookieInterceptor(cookieJar))
            .addInterceptor(
                RequestHeadersInterceptor(
                    baseUrl = baseUrl,
                    userAgent = this.userAgent,
                    acceptLanguage = resolveAcceptLanguage(),
                    cookieJar = cookieJar,
                ),
            ).build()

    val imageOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .addNetworkInterceptor(SessionCookieInterceptor(cookieJar))
            .addInterceptor(ImageRequestHeadersInterceptor(
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                acceptLanguage = resolveAcceptLanguage(),
            ))
            .build()

    /**
     * The client provider GIF thumbnails are loaded with: the image headers (UA, Accept) but no
     * cookie interceptor. A GIF URL points at the provider's CDN, which is not the site; the reader's
     * `_t`/`_forum_session` must never travel there just because a picker drew a preview.
     */
    val gifImageOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(ImageRequestHeadersInterceptor(
                baseUrl = baseUrl,
                userAgent = this.userAgent,
                acceptLanguage = resolveAcceptLanguage(),
            ))
            .build()

    private val retrofit: Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private val apiService: DiscourseApiService = retrofit.create(DiscourseApiService::class.java)

    /** The GIF proxy's own Retrofit view over the same session-aware transport. */
    private val gifService: DiscourseGifService = retrofit.create(DiscourseGifService::class.java)

    val discourseApiClient: DiscourseApiClient =
        DiscourseApiClient(
            service = apiService,
            emojiCatalogStore = emojiCatalogStore,
        )
    val profileRepository: ProfileRepository =
        ProfileRepository(
            client = discourseApiClient,
            preferences = profileCachePreferences,
            json = json,
        )
    val profileFeedRepository: ProfileFeedRepository =
        ProfileFeedRepository(
            client = discourseApiClient,
        )
    val notificationRepository: NotificationRepository =
        NotificationRepository(
            client = discourseApiClient,
        )
    val bookmarkRepository: BookmarkRepository =
        BookmarkRepository(
            client = discourseApiClient,
        )
    val privateMessagesRepository: PrivateMessagesRepository =
        PrivateMessagesRepository(
            client = discourseApiClient,
        )
    /**
     * The public chat channels and their messages. The drafts belong to the container for the same
     * reason the topic drafts do: a channel's half-written message outlives the screen that was
     * writing it.
     */
    val chatRepository: io.github.baba983.nativedo.feature.chat.data.ChatRepository =
        io.github.baba983.nativedo.feature.chat.data.ChatRepository(
            client = discourseApiClient,
        )
    val chatDraftStore: ChatDraftStore =
        chatDraftPreferences
            ?.let { preferences -> ChatDraftStore(SharedPreferencesChatDraftPersistence(preferences)) }
            ?: ChatDraftStore()
    val categoryStore: CategoryStore =
        CategoryStore(
            client = discourseApiClient,
            // Kept per site: a second linux.do-shaped host would not share this site's categories.
            persistence =
                categoryPreferences
                    ?.let { preferences ->
                        SharedPreferencesCategoryPersistence(
                            preferences,
                            SharedPreferencesCategoryPersistence.keyFor(baseUrl),
                        )
                    }
                    ?: NoCategoryPersistence,
        )
    val credentialStore: CredentialStore = CredentialStore(appContext)
    private val deviceLoginPreferences = appContext?.getSharedPreferences(DEVICE_LOGIN_PREFS, Context.MODE_PRIVATE)
    private val deviceKeyPreferences = appContext?.getSharedPreferences(DEVICE_KEY_PREFS, Context.MODE_PRIVATE)

    /**
     * The identifier linux.do's approval page shows for this install. Nothing is registered with
     * the site under it - that is why the page warns about an unregistered application - but a
     * stable value keeps one device's grants distinguishable from another's.
     */
    val deviceClientId: String =
        deviceLoginPreferences?.let { preferences ->
            preferences.getString(DEVICE_CLIENT_ID_KEY, null)?.takeIf(String::isNotBlank)
                ?: newDeviceClientId().also { clientId ->
                    preferences.edit().putString(DEVICE_CLIENT_ID_KEY, clientId).apply()
                }
        } ?: newDeviceClientId()
    val deviceKeyStore: DeviceKeyStore = DeviceKeyStore(deviceKeyPreferences)

    /** Lazily built: Keystore RSA generation (and the software fallback) is several hundred ms. */
    val deviceLoginKey: DeviceLoginKey by lazy {
        DeviceLoginKeys.create(deviceKeyPreferences, AndroidKeystoreCipher)
    }
    val deviceAuthApi: DeviceAuthApi by lazy { HttpDeviceAuthApi(baseUrl, this.userAgent, cookieJar) }
    val sessionCoordinator: SessionCoordinator =
        SessionCoordinator(
            sessionStore = sessionStore,
            cookieJar = cookieJar,
            apiClient = discourseApiClient,
            applicationScope = applicationScope,
            // A logout (explicit or server-proven) drops the session here, so the reads cached for
            // the reader it belonged to are dropped with it - through the coordinator, not beside it.
            onSessionCleared = { discourseReadCache.clear() },
        )
    val completeLoginUseCase: CompleteLoginUseCase =
        CompleteLoginUseCase(
            sessionCoordinator = sessionCoordinator,
            cookieJar = cookieJar,
            baseUrl = baseUrl,
        )

    /**
     * The site-backed GIF reads. They run on [DiscourseApiClient]'s session boundary over the same
     * transport; the client itself only owns the GIF routes and their provider mapping. Provider
     * thumbnails use [gifImageOkHttpClient], which carries no cookies.
     */
    val discourseGifClient: DiscourseGifClient =
        DiscourseGifClient(
            service = gifService,
            apiClient = discourseApiClient,
        )

    /**
     * One message-bus session per reader, owned here rather than by a screen: it follows
     * [SessionCoordinator.sessionState] and the host's foreground/background calls, and the screens
     * only observe its flows.
     */
    val messageBusSession: MessageBusSession =
        MessageBusSession(
            httpClient = okHttpClient,
            baseUrl = baseUrl.toHttpUrl(),
            sessionState = sessionCoordinator.sessionState,
            fetchBootstrapHtml = { discourseApiClient.fetchBootstrapHtml() },
            scope = applicationScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
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
        discourseApiClient.setChallengeRecovery {
            browserClearanceRefresher?.refresh() ?: false
        }
        // The reader a cached read belongs to: the persisted authenticated identity, never the
        // anonymous session. No identity means no cache fill and no fallback.
        discourseApiClient.setReadCache(
            cache = discourseReadCache,
            scope = {
                sessionStore.snapshot()
                    ?.username
                    ?.takeIf { name -> name.isNotBlank() && !name.equals("anonymous", ignoreCase = true) }
                    ?.let { name -> ReadCacheScope(site = baseUrl, username = name) }
            },
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
        const val CATEGORY_STORE_PREFS: String = "nativedo.categories"
        const val READ_CACHE_PREFS: String = "nativedo.read_cache"
        const val CHAT_DRAFT_STORE_PREFS: String = "nativedo.chat_drafts"
        const val DEVICE_LOGIN_PREFS: String = "nativedo.device_login"
        const val DEVICE_KEY_PREFS: String = "nativedo.device_key"
        const val DEVICE_CLIENT_ID_KEY: String = "client_id"
    }

    private fun newDeviceClientId(): String {
        val bytes = ByteArray(8)
        java.security.SecureRandom().nextBytes(bytes)
        return "nativedo-" + bytes.joinToString("") { "%02x".format(it) }
    }
}
