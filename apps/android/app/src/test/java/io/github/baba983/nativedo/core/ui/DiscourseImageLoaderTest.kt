package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.storage.CookieStore
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiscourseImageLoaderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun extractLargestPngPayloadFromIco_returnsLargestPngEntry() {
        val smallPng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFAAH/e+m+7wAAAABJRU5ErkJggg==",
        )
        val largePng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWP8z8Dwn4GBgYGJAQoAHxcCAr7c4eUAAAAASUVORK5CYII=",
        )

        val icoBytes = buildIco(
            IcoEntry(width = 16, height = 16, payload = smallPng),
            IcoEntry(width = 32, height = 32, payload = largePng),
        )

        val extracted = extractLargestPngPayloadFromIco(icoBytes)

        assertThat(extracted).isEqualTo(largePng)
    }

    @Test
    fun extractLargestPngPayloadFromIco_returnsNullForNonIcoBytes() {
        val pngBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFAAH/e+m+7wAAAABJRU5ErkJggg==",
        )

        val extracted = extractLargestPngPayloadFromIco(pngBytes)

        assertThat(extracted).isNull()
    }

    @Test
    fun fetchDiscourseBitmap_sendsBrowserLikeImageHeadersAndCookies() = runTest {
        val pngBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFAAH/e+m+7wAAAABJRU5ErkJggg==",
        )
        val baseUrl = server.url("/").toString()
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_t")
                        .value("auth")
                        .domain(server.hostName)
                        .path("/")
                        .build(),
                ),
            )
        }
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(pngBytes))
                .setHeader("Content-Type", "image/png"),
        )

        fetchDiscourseBitmap(
            resolvedUrl = server.url("/emoji.png").toString(),
            cookieStore = cookieStore,
            baseUrl = baseUrl,
        )

        val request = checkNotNull(server.takeRequest())
        assertThat(request.getHeader("Accept"))
            .isEqualTo("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        assertThat(request.getHeader("Accept-Language"))
            .isEqualTo("zh-CN,zh;q=0.9,en;q=0.8")
        assertThat(request.getHeader("Referer")).isEqualTo(baseUrl)
        assertThat(request.getHeader("User-Agent")).contains("Mozilla/5.0")
        assertThat(request.getHeader("Cookie")).isEqualTo("_t=auth")
    }

    @Test
    fun newImageLoader_registersAnimatedImageDecoderFactory() {
        val context = RuntimeEnvironment.getApplication()
        val imageLoader = AppContainer(
            context = context,
            baseUrl = server.url("/").toString(),
        ).newImageLoader(context)

        val decoderFactoryNames = imageLoader.components.decoderFactories
            .map { factory -> factory::class.java.name }

        assertThat(decoderFactoryNames)
            .contains("coil3.gif.AnimatedImageDecoder\$Factory")
    }

    @Test
    fun newImageLoader_registersSvgDecoderFactory() {
        val context = RuntimeEnvironment.getApplication()
        val imageLoader = AppContainer(
            context = context,
            baseUrl = server.url("/").toString(),
        ).newImageLoader(context)

        val decoderFactoryNames = imageLoader.components.decoderFactories
            .map { factory -> factory::class.java.name }

        assertThat(decoderFactoryNames)
            .contains("coil3.svg.SvgDecoder\$Factory")
    }

    @Test
    fun resolvePostImageRenderRawUrl_prefersOriginalGifOverStaticThumbnail() {
        val rawUrl = resolvePostImageRenderRawUrl(
            PostBodyImage(
                rawUrl = "https://cdn3.linux.do/optimized/demo_2_690x359.png",
                originalUrl = "https://cdn3.linux.do/original/demo.gif",
                width = 690,
                height = 359,
            ),
        )

        assertThat(rawUrl).isEqualTo("https://cdn3.linux.do/original/demo.gif")
    }

    @Test
    fun resolvePostImageRenderRawUrl_keepsStaticOriginalThumbnailUrl() {
        val rawUrl = resolvePostImageRenderRawUrl(
            PostBodyImage(
                rawUrl = "https://cdn3.linux.do/optimized/demo_2_690x359.png",
                originalUrl = "https://cdn3.linux.do/original/demo.png",
                width = 690,
                height = 359,
            ),
        )

        assertThat(rawUrl).isEqualTo("https://cdn3.linux.do/optimized/demo_2_690x359.png")
    }
}

private data class IcoEntry(
    val width: Int,
    val height: Int,
    val payload: ByteArray,
)

private fun buildIco(vararg entries: IcoEntry): ByteArray {
    val headerSize = 6 + entries.size * 16
    val output = ArrayList<Byte>(headerSize + entries.sumOf { it.payload.size })

    fun writeShort(value: Int) {
        output += (value and 0xff).toByte()
        output += ((value ushr 8) and 0xff).toByte()
    }

    fun writeInt(value: Int) {
        output += (value and 0xff).toByte()
        output += ((value ushr 8) and 0xff).toByte()
        output += ((value ushr 16) and 0xff).toByte()
        output += ((value ushr 24) and 0xff).toByte()
    }

    writeShort(0)
    writeShort(1)
    writeShort(entries.size)

    var offset = headerSize
    entries.forEach { entry ->
        output += entry.width.toIcoDimensionByte()
        output += entry.height.toIcoDimensionByte()
        output += 0
        output += 0
        writeShort(1)
        writeShort(32)
        writeInt(entry.payload.size)
        writeInt(offset)
        offset += entry.payload.size
    }

    entries.forEach { entry ->
        entry.payload.forEach(output::add)
    }

    return output.toByteArray()
}

private fun Int.toIcoDimensionByte(): Byte {
    return if (this >= 256) {
        0
    } else {
        toByte()
    }
}
