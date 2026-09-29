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
        // Every write asks for a CSRF token first, so a site that cannot hand one out cannot be
        // written to at all.
        "session/csrf" to "csrf.json",
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

    /** Every request the site saw, as `METHOD path`, in order. */
    val recordedRequests: List<String> get() = requests.toList()

    /** The body of the last write the site received on [path], form-encoded as it was sent. */
    fun requestBodyFor(path: String): String? = requestBodies[path.trimStart('/')]

    /** The CSRF token a write carried on [path]. */
    fun csrfHeaderFor(path: String): String? = csrfHeaders[path.trimStart('/')]

    /** Serves a topic whose first post the caller can shape: body, reactions, anything. */
    fun serveTopic(topicId: Long, mutate: (org.json.JSONObject) -> Unit) {
        val base = org.json.JSONObject(repoFile("fixtures/discourse/topic-detail.json").readText())
        val posts = base.getJSONObject("post_stream").getJSONArray("posts")
        for (index in 0 until posts.length()) {
            mutate(posts.getJSONObject(index))
        }
        routes["t/$topicId.json"] = null
        bodies["t/$topicId.json"] = base.toString()
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

    /**
     * Serves [bytes] slowly: a test that has to look at a screen while a transfer is still running
     * (a progress state, a cancel button) needs the transfer to still be running when it looks.
     */
    fun serveThrottled(
        path: String,
        bytes: ByteArray,
        contentType: String,
        bytesPerPeriod: Long,
        periodMillis: Long,
    ) {
        serveResponse(path) {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", contentType)
                .setBody(okio.Buffer().write(bytes))
                .throttleBody(bytesPerPeriod, periodMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Serves a response the test builds itself, for a file the site does not hand over the usual
     * way: one it refuses, or one whose own header says it is too long to fetch.
     */
    fun serveResponse(path: String, build: () -> MockResponse) {
        responses[path.trimStart('/')] = build
    }

    /**
     * The path an avatar template turns into, with `{size}` filled in the way the client fills it.
     */
    fun avatarPath(template: String, size: Int = 96): String =
        template.replace("{size}", size.toString()).trimStart('/')

    /**
     * Avatars of a discussion that has no real pictures: a colour of its own per path, so a row's
     * avatar is recognisable in a render and two participants never look alike.
     */
    private fun generatedAvatar(path: String): ByteArray {
        val digest = java.security.MessageDigest.getInstance("MD5").digest(path.toByteArray())
        val color = android.graphics.Color.rgb(
            (digest[0].toInt() and 0xff) / 2 + 64,
            (digest[1].toInt() and 0xff) / 2 + 64,
            (digest[2].toInt() and 0xff) / 2 + 64,
        )
        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    fun start(): String {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().trimStart('/').substringBefore('?')
                synchronized(this@FakeDiscourseSite) {
                    requestedPaths += path
                    requests += request.method.orEmpty() + " " + request.path.orEmpty()
                    if (request.method != "GET" && request.method != "HEAD") {
                        requestBodies[path] = request.body.readUtf8()
                        request.getHeader("X-CSRF-Token")?.let { csrfHeaders[path] = it }
                    }
                    lastCookieHeader = request.getHeader("Cookie")
                    request.getHeader("Cookie")?.let { cookieHeaders[path] = it }
                    queries.getOrPut(path) { mutableListOf() } += request.path.orEmpty().substringAfter('?')
                }
                if (path.startsWith("user_avatar/")) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "image/png")
                        .setBody(okio.Buffer().write(images[path]?.first ?: generatedAvatar(path)))
                }
                responses[path]?.let { build -> return build() }
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
                val body = fixtureBodyFor(path)
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

    /** Serves [path] from a fixture file, for a request the screen expects an answer from. */
    fun serveFixture(path: String, fixture: String) {
        routes[path.trimStart('/')] = fixture
    }

    /**
     * Serves a topic whose whole post stream is the one post in [postFixture] (a fixture of a post
     * object, the shape `post_stream.posts[]` carries): a floor with its own payload - a poll, a
     * gallery - as the site sends it, with the stream aligned so the screen asks for no second page.
     */
    fun servePostFixture(topicId: Long, postFixture: String) {
        val base = org.json.JSONObject(repoFile("fixtures/discourse/topic-detail.json").readText())
        val post = org.json.JSONObject(repoFile("fixtures/discourse/$postFixture").readText())
        val stream = base.getJSONObject("post_stream")
        stream.put("posts", org.json.JSONArray().put(post))
        stream.put("stream", org.json.JSONArray().put(post.getLong("id")))
        routes["t/$topicId.json"] = null
        bodies["t/$topicId.json"] = base.toString()
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

    private val requests = mutableListOf<String>()
    private val requestBodies = mutableMapOf<String, String>()
    private val csrfHeaders = mutableMapOf<String, String>()
    private val requestedPaths = mutableListOf<String>()
    private val cookieHeaders = mutableMapOf<String, String>()
    private val images = mutableMapOf<String, Pair<ByteArray, String>>()
    private val responses = mutableMapOf<String, () -> MockResponse>()
    private var lastCookieHeader: String? = null

    private fun fixtureBodyFor(path: String): String? {
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