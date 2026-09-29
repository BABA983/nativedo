package io.github.baba983.nativedo.harness

import android.content.Context
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.SessionSnapshot
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.File

/**
 * A local Discourse site backed by `fixtures/discourse/`.
 *
 * The screen tests drive the real client against it, so what they render went through the same
 * retrofit service, DTO mapping and models the app uses. Stubbing the models instead would let the
 * screenshots pass while the wire format was broken.
 */
internal class FakeDiscourseSite {

    private val server = MockWebServer()

    /** The fake site's own URL, available once [start] has run. */
    lateinit var baseUrl: String
        private set

    /** Path (without the leading slash) -> fixture file. */
    private val routes = mutableMapOf(
        "latest.json" to "latest-topics.json",
        "emojis.json" to null,
    )

    /** What the fake site saw on the last request, so a test can assert the session was sent. */
    val lastRequestCookieHeader: String?
        get() = lastCookieHeader

    val requestPaths: List<String> get() = requestedPaths.toList()

    /** Serves [json] at [path]: a topic page, a page of posts, anything the client asks for. */
    fun serveJson(path: String, json: String) {
        routes[path.trimStart('/')] = null
        bodies[path.trimStart('/')] = json
    }

    /**
     * Serves [path] from the request itself: a query-dependent endpoint (a page of posts, a search
     * whose page matters) can only answer honestly if it can see what was asked.
     */
    fun serveJsonFor(path: String, build: (String) -> String) {
        routes[path.trimStart('/')] = null
        builders[path.trimStart('/')] = build
    }

    /** The query strings the site was asked for on [path], in order. */
    fun queriesFor(path: String): List<String> =
        queries[path.trimStart('/')].orEmpty()

    /** What the site saw on [path], by the path it was asked for. */
    fun cookieHeaderFor(path: String): String? = cookieHeaders[path.trimStart('/')]

    /** An absolute URL on this site, for a cooked body that has to point at it. */
    fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    /** Serves [bytes] at [path], so an image the post refers to really can be downloaded. */
    fun serveImage(path: String, bytes: ByteArray, contentType: String = "image/png") {
        images[path.trimStart('/')] = bytes to contentType
    }

    fun start(): String {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().trimStart('/').substringBefore('?')
                synchronized(this@FakeDiscourseSite) {
                    requestedPaths += path
                    lastCookieHeader = request.getHeader("Cookie")
                    request.getHeader("Cookie")?.let { cookieHeaders[path] = it }
                    queries.getOrPut(path) { mutableListOf() } += request.path.orEmpty().substringAfter('?')
                }
                images[path]?.let { (bytes, contentType) ->
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", contentType)
                        .setBody(okio.Buffer().write(bytes))
                }
                builders[path]?.let { build ->
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(build(request.path.orEmpty()))
                }
                val body = bodyFor(path)
                    ?: return MockResponse().setResponseCode(404)
                        .setBody("""{"errors":["no fixture for $path"]}""")
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            }
        }
        server.start()
        baseUrl = server.url("/").toString()
        return baseUrl
    }

    /**
     * A container pointing at this site with a usable session already in it.
     *
     * Every screen sits behind the session gate, so a screen test needs a session for the same
     * reason the app does; seeding it here keeps that out of each test.
     */
    fun authenticatedContainer(context: Context): AppContainer {
        val container = AppContainer(context, baseUrl, null, null)
        val url = baseUrl.toHttpUrl()
        container.cookieStore.save(
            url,
            listOf(
                cookie(url.host, "_t", "session-token"),
                cookie(url.host, "_forum_session", "forum-session"),
                cookie(url.host, "cf_clearance", "clearance"),
            ),
        )
        container.sessionStore.save(
            SessionSnapshot(
                username = "baba",
                displayName = "Baba",
                avatarTemplate = "",
                lastValidatedAt = System.currentTimeMillis(),
            ),
        )
        return container
    }

    private fun cookie(host: String, name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain(host).path("/").build()

    fun stop() = server.shutdown()

    fun serve(topicId: Long, fixture: String) {
        routes["t/$topicId.json"] = fixture
    }

    /**
     * Serves a topic whose post body is [cookedHtml] instead of the fixture's own body, so a test
     * can drive a complex Discourse body (table, quote, image, code) through the whole screen
     * without adding fixtures.
     */
    fun serveCooked(topicId: Long, cookedHtml: String) {
        val base = org.json.JSONObject(repoFile("fixtures/discourse/topic-detail.json").readText())
        val posts = base.getJSONObject("post_stream").getJSONArray("posts")
        posts.getJSONObject(0).put("cooked", cookedHtml)
        routes["t/$topicId.json"] = null
        bodies["t/$topicId.json"] = base.toString()
    }

    private val bodies = mutableMapOf<String, String>()
    private val builders = mutableMapOf<String, (String) -> String>()
    private val queries = mutableMapOf<String, MutableList<String>>()

    private val requestedPaths = mutableListOf<String>()
    private val cookieHeaders = mutableMapOf<String, String>()
    private val images = mutableMapOf<String, Pair<ByteArray, String>>()
    private var lastCookieHeader: String? = null

    private fun bodyFor(path: String): String? {
        bodies[path]?.let { return it }
        if (!routes.containsKey(path)) return null
        val fixture = routes[path] ?: return "{}"
        val file = repoFile("fixtures/discourse/$fixture")
        return file.readText()
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "fixtures/discourse").isDirectory) {
                return File(dir, relative)
            }
            dir = dir.parentFile
        }
        error("cannot locate the repo root from ${System.getProperty("user.dir")}")
    }
}