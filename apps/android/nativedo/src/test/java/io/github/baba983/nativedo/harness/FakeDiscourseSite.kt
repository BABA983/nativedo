package io.github.baba983.nativedo.harness

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

    /** Path (without the leading slash) -> fixture file. */
    private val routes = mutableMapOf(
        "latest.json" to "latest-topics.json",
        "emojis.json" to null,
    )

    fun start(): String {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().trimStart('/').substringBefore('?')
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
        return server.url("/").toString()
    }

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
        cookedBodies["t/$topicId.json"] = base.toString()
    }

    private val cookedBodies = mutableMapOf<String, String>()

    private fun bodyFor(path: String): String? {
        cookedBodies[path]?.let { return it }
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