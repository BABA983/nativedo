package io.github.baba983.nativedo.feature.browser

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument
import io.github.baba983.nativedo.core.navigation.flattenBodyText
import org.junit.Test
import java.nio.charset.StandardCharsets

class AppBrowserInstantViewResolverTest {
    @Test
    fun buildAppBrowserInstantViewBootstrapHtml_referencesLocalReaderAssets() {
        val html = buildAppBrowserInstantViewBootstrapHtml(
            pageUrl = "https://example.com/article",
        )

        assertThat(html).contains("https://example.com/__nativedo/instant-view/dompurify.js")
        assertThat(html).contains("https://example.com/__nativedo/instant-view/readability.js")
        assertThat(html).contains("https://example.com/__nativedo/instant-view/runtime.js")
    }

    @Test
    fun resolveAppBrowserInstantViewReplayTarget_routesBootstrapAndLocalIndexRequests() {
        val snapshot = AppBrowserInstantViewArchiveSnapshot(
            pageUrl = "https://example.com/article",
            mainHtml = "<html><body><article><p>Archive body.</p></article></body></html>",
            resourcesByUrl = mapOf(
                "https://example.com/article" to AppBrowserInstantViewArchiveResource(
                    mimeType = "text/html",
                    encoding = "UTF-8",
                    data = "<html><body><article><p>Archive body.</p></article></body></html>"
                        .toByteArray(StandardCharsets.UTF_8),
                ),
                "https://example.com/assets/article.css" to AppBrowserInstantViewArchiveResource(
                    mimeType = "text/css",
                    encoding = "UTF-8",
                    data = "body { color: #111; }".toByteArray(StandardCharsets.UTF_8),
                ),
            ),
        )

        val bootstrapTarget = resolveAppBrowserInstantViewReplayTarget(
            snapshot = snapshot,
            requestUrl = "https://example.com/article",
            isFirstRequest = true,
        )
        val indexTarget = resolveAppBrowserInstantViewReplayTarget(
            snapshot = snapshot,
            requestUrl = "https://example.com/__nativedo/instant-view/index.html",
            isFirstRequest = false,
        )
        val assetTarget = resolveAppBrowserInstantViewReplayTarget(
            snapshot = snapshot,
            requestUrl = "https://example.com/__nativedo/instant-view/runtime.js",
            isFirstRequest = false,
        )
        val archiveTarget = resolveAppBrowserInstantViewReplayTarget(
            snapshot = snapshot,
            requestUrl = "https://example.com/assets/article.css",
            isFirstRequest = false,
        )

        assertThat(bootstrapTarget).isEqualTo(AppBrowserInstantViewReplayTarget.BootstrapDocument)
        assertThat(indexTarget).isEqualTo(AppBrowserInstantViewReplayTarget.SnapshotIndex)
        assertThat(assetTarget).isEqualTo(
            AppBrowserInstantViewReplayTarget.LocalAsset(
                fileName = appBrowserInstantViewRuntimeFileName,
                mimeType = "text/javascript",
                encoding = "UTF-8",
            ),
        )
        assertThat(archiveTarget).isEqualTo(
            AppBrowserInstantViewReplayTarget.ArchiveResource(
                url = "https://example.com/assets/article.css",
            ),
        )
    }

    @Test
    fun resolveAppBrowserInstantViewReplayTarget_returnsMissingForUnknownHttpResources() {
        val snapshot = AppBrowserInstantViewArchiveSnapshot(
            pageUrl = "https://example.com/article",
            mainHtml = "<html><body></body></html>",
            resourcesByUrl = emptyMap(),
        )

        val target = resolveAppBrowserInstantViewReplayTarget(
            snapshot = snapshot,
            requestUrl = "https://example.com/missing.js",
            isFirstRequest = false,
        )

        assertThat(target).isEqualTo(AppBrowserInstantViewReplayTarget.Missing)
    }

    @Test
    fun extractAppBrowserArchiveSnapshotFromMhtml_parsesReplayableHtmlAndCssResources() {
        val snapshot = extractAppBrowserArchiveSnapshotFromMhtml(
            mhtml = """
                From: <Saved by WebKit>
                Subject: Example
                Content-Type: multipart/related; type="text/html"; boundary="----boundary"

                ------boundary
                Content-Type: text/html; charset="utf-8"
                Content-Transfer-Encoding: quoted-printable
                Content-Location: https://example.com/article

                =3Chtml=3E
                =3Chead=3E
                =3Clink=20rel=3D"stylesheet"=20href=3D"/assets/article.css"=3E
                =3Cscript=3Ealert('x')=3C/script=3E
                =3C/head=3E
                =3Cbody=3E=3Carticle=3E=3Ch1=3EArchive=20Title=3C/h1=3E=3Cp=3EArchive=
                =20Body=3C/p=3E=3C/article=3E=3C/body=3E=3C/html=3E
                ------boundary
                Content-Type: text/css; charset="utf-8"
                Content-Transfer-Encoding: quoted-printable
                Content-Location: /assets/article.css

                body=20{=20color:=20#111;=20}
                ------boundary--
            """.trimIndent(),
            fallbackPageUrl = "https://example.com/article",
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.pageUrl).isEqualTo("https://example.com/article")
        assertThat(snapshot?.mainHtml).contains("Archive Title")
        assertThat(snapshot?.mainHtml).contains("rel=\"stylesheet\"")
        assertThat(snapshot?.mainHtml).doesNotContain("<script>")
        assertThat(snapshot?.resourcesByUrl?.keys).containsAtLeast(
            "https://example.com/article",
            "https://example.com/assets/article.css",
        )
        val cssResource = resolveAppBrowserArchiveResource(
            snapshot = snapshot!!,
            requestUrl = "https://example.com/assets/article.css",
        )
        assertThat(cssResource?.mimeType).isEqualTo("text/css")
        assertThat(cssResource?.data?.toString(StandardCharsets.UTF_8))
            .contains("body { color: #111; }")
    }

    @Test
    fun extractAppBrowserArchiveSnapshotFromMhtml_usesFallbackUrlForHtmlPartWithoutLocation() {
        val snapshot = extractAppBrowserArchiveSnapshotFromMhtml(
            mhtml = """
                From: <Saved by WebKit>
                Subject: Example
                Content-Type: multipart/related; type="text/html"; boundary="----boundary"

                ------boundary
                Content-Type: text/html; charset="utf-8"
                Content-Transfer-Encoding: base64

                PGh0bWw+PGJvZHk+PGFydGljbGU+PGgxPkZhbGxiYWNrIFVSTDwvaDE+PHA+TWFpbiBIVE1MIHBhcnQgd2l0aG91dCBhIENvbnRlbnQtTG9jYXRpb24gc2hvdWxkIHN0aWxsIGJlIHJlcGxheWFibGUuPC9wPjwvYXJ0aWNsZT48L2JvZHk+PC9odG1sPg==
                ------boundary--
            """.trimIndent(),
            fallbackPageUrl = "https://example.com/fallback-article",
        )

        assertThat(snapshot?.pageUrl).isEqualTo("https://example.com/fallback-article")
        assertThat(
            resolveAppBrowserArchiveResource(
                snapshot = snapshot!!,
                requestUrl = "https://example.com/fallback-article",
            )?.mimeType,
        ).isEqualTo("text/html")
        assertThat(snapshot.mainHtml).contains("Fallback URL")
    }

    @Test
    fun resolveAppBrowserArchiveResource_returnsNullForMissingOrUnsupportedUrl() {
        val snapshot = extractAppBrowserArchiveSnapshotFromMhtml(
            mhtml = """
                From: <Saved by WebKit>
                Subject: Example
                Content-Type: multipart/related; type="text/html"; boundary="----boundary"

                ------boundary
                Content-Type: text/html; charset="utf-8"
                Content-Transfer-Encoding: quoted-printable
                Content-Location: https://example.com/article

                =3Chtml=3E=3Cbody=3E=3Carticle=3E=3Ch1=3EArchive=20Title=3C/h1=3E=3Cp=3EArchive=
                =20Body=3C/p=3E=3C/article=3E=3C/body=3E=3C/html=3E
                ------boundary
                Content-Type: image/png
                Content-Transfer-Encoding: base64
                Content-Location: https://example.com/image.png

                aW1hZ2U=
                ------boundary--
            """.trimIndent(),
            fallbackPageUrl = "https://example.com/article",
        )

        assertThat(
            resolveAppBrowserArchiveResource(
                snapshot = snapshot!!,
                requestUrl = "https://example.com/image.png",
            ),
        ).isNull()
        assertThat(
            resolveAppBrowserArchiveImageDataUrl(
                snapshot = snapshot,
                requestUrl = "https://example.com/image.png",
            ),
        ).startsWith("data:image/png;base64,")
        assertThat(
            resolveAppBrowserArchiveResource(
                snapshot = snapshot,
                requestUrl = "https://example.com/missing.css",
            ),
        ).isNull()
    }

    @Test
    fun extractAppBrowserHtmlFromMhtml_decodesQuotedPrintableHtmlPart() {
        val html = extractAppBrowserHtmlFromMhtml(
            """
                From: <Saved by WebKit>
                Subject: Example
                Content-Type: multipart/related; type="text/html"; boundary="----boundary"

                ------boundary
                Content-Type: text/html; charset="utf-8"
                Content-Transfer-Encoding: quoted-printable
                Content-Location: https://example.com/article

                =3Chtml=3E=3Cbody=3E=3Carticle=3E=3Ch1=3EArchive=20Title=3C/h1=3E=3Cp=3EArchive=
                =20Body=3C/p=3E=3C/article=3E=3C/body=3E=3C/html=3E
                ------boundary--
            """.trimIndent(),
        )

        assertThat(html).contains("<h1>Archive Title</h1>")
        assertThat(html).contains("<p>Archive Body</p>")
    }

    @Test
    fun extractAppBrowserHtmlFromMhtml_decodesBase64HtmlPart() {
        val html = extractAppBrowserHtmlFromMhtml(
            """
                From: <Saved by WebKit>
                Subject: Example
                Content-Type: multipart/related; type="text/html"; boundary="----boundary"

                ------boundary
                Content-Type: text/html; charset="utf-8"
                Content-Transfer-Encoding: base64

                PGh0bWw+PGJvZHk+PGFydGljbGU+PHA+QmFzZTY0IEFyY2hpdmUgQm9keTwvcD48L2FydGljbGU+PC9ib2R5PjwvaHRtbD4=
                ------boundary--
            """.trimIndent(),
        )

        assertThat(html).contains("<p>Base64 Archive Body</p>")
    }

    @Test
    fun resolveAppBrowserArchiveImageDataUrl_returnsNullForMissingImage() {
        val snapshot = AppBrowserInstantViewArchiveSnapshot(
            pageUrl = "https://example.com/article",
            mainHtml = "<html><body><article><p>Archive body.</p></article></body></html>",
            resourcesByUrl = mapOf(
                "https://example.com/article" to AppBrowserInstantViewArchiveResource(
                    mimeType = "text/html",
                    encoding = "UTF-8",
                    data = "<html><body><article><p>Archive body.</p></article></body></html>"
                        .toByteArray(StandardCharsets.UTF_8),
                ),
            ),
        )

        assertThat(
            resolveAppBrowserArchiveImageDataUrl(
                snapshot = snapshot,
                requestUrl = "https://example.com/missing.jpg",
            ),
        ).isNull()
    }

    @Test
    fun appBrowserInstantViewResolver_prefersScriptJsonDocument() {
        val host = FakeAppBrowserInstantViewResolveHost(
            url = "https://example.com/article",
            title = "Example Article",
            archiveHtmlSnapshot = """
                <html>
                  <body>
                    <article>
                      <h1>Archive Title</h1>
                      <p>Archive paragraph should not win when script JSON exists.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            htmlSnapshot = """
                <html>
                  <body>
                    <article>
                      <h1>HTML Fallback Title</h1>
                      <p>HTML fallback paragraph should not win when script JSON exists.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
        )
        val resolver = AppBrowserInstantViewResolver(
            hiddenExtraction = { _, _, _, _, onResolved ->
                onResolved(
                    """
                        {
                          "title": "Script Title",
                          "blocks": [
                            { "type": "paragraph", "text": "Script paragraph wins." }
                          ]
                        }
                    """.trimIndent(),
                )
            },
        )

        var document: AppBrowserReaderDocument? = null
        resolver.resolve(host, host.url.orEmpty()) { resolved ->
            document = resolved
        }

        assertThat(document?.title).isEqualTo("Script Title")
        assertThat(document?.flattenBodyText()).contains("Script paragraph wins.")
    }

    @Test
    fun appBrowserInstantViewResolver_fallsBackToHtmlWhenScriptJsonIsUnavailable() {
        val host = FakeAppBrowserInstantViewResolveHost(
            url = "https://example.com/article",
            title = "Example Article",
            archiveHtmlSnapshot = """
                <html>
                  <body>
                    <article>
                      <h1>Archive Fallback Title</h1>
                      <p>Archive HTML should win when script JSON is unavailable.</p>
                      <p>Archive snapshots should be preferred over the looser outerHTML path.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            htmlSnapshot = """
                <html>
                  <body>
                    <article>
                      <h1>HTML Fallback Title</h1>
                      <p>First paragraph from sanitized HTML fallback path.</p>
                      <p>Second paragraph keeps the article readable and substantial.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
        )
        val resolver = AppBrowserInstantViewResolver(
            hiddenExtraction = { _, _, _, _, onResolved ->
                onResolved(null)
            },
        )

        var document: AppBrowserReaderDocument? = null
        resolver.resolve(host, host.url.orEmpty()) { resolved ->
            document = resolved
        }

        assertThat(document?.title).isEqualTo("Archive Fallback Title")
        assertThat(document?.flattenBodyText()).contains("Archive HTML should win when script JSON is unavailable.")
    }

    @Test
    fun appBrowserInstantViewResolver_fallsBackToTextWhenScriptAndHtmlMiss() {
        val host = FakeAppBrowserInstantViewResolveHost(
            url = "https://example.com/article",
            title = "Example Article",
            htmlSnapshot = "<html><body><div>short</div></body></html>",
            fallbackText = """
                First paragraph contains enough text to satisfy the local Instant View support rules.

                Second paragraph keeps the extracted fallback content substantial and readable.

                Third paragraph adds enough text to pass the conservative gating logic.

                Fourth paragraph ensures the resolver can still build a reader document from text.

                Fifth paragraph closes the example with enough substance for the fallback builder.
            """.trimIndent(),
        )
        val resolver = AppBrowserInstantViewResolver(
            hiddenExtraction = { _, _, _, _, onResolved ->
                onResolved(null)
            },
        )

        var document: AppBrowserReaderDocument? = null
        resolver.resolve(host, host.url.orEmpty()) { resolved ->
            document = resolved
        }

        assertThat(document?.title).isEqualTo("Example Article")
        assertThat(document?.flattenBodyText()).contains("First paragraph contains enough text")
    }

    @Test
    fun appBrowserInstantViewResolver_fallsBackToOuterHtmlWhenArchiveHtmlIsUnavailable() {
        val host = FakeAppBrowserInstantViewResolveHost(
            url = "https://example.com/article",
            title = "Example Article",
            archiveHtmlSnapshot = null,
            htmlSnapshot = """
                <html>
                  <body>
                    <article>
                      <h1>Outer HTML Title</h1>
                      <p>Outer HTML fallback still works when archive capture is unavailable.</p>
                      <p>Resolver should keep the existing snapshot path as a safe fallback.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
        )
        val resolver = AppBrowserInstantViewResolver(
            hiddenExtraction = { _, _, _, _, onResolved ->
                onResolved(null)
            },
        )

        var document: AppBrowserReaderDocument? = null
        resolver.resolve(host, host.url.orEmpty()) { resolved ->
            document = resolved
        }

        assertThat(document?.title).isEqualTo("Outer HTML Title")
        assertThat(document?.flattenBodyText()).contains("Outer HTML fallback still works when archive capture is unavailable.")
    }
}

private class FakeAppBrowserInstantViewResolveHost(
    override val url: String?,
    override val title: String?,
    private val archiveHtmlSnapshot: String? = null,
    private val htmlSnapshot: String? = null,
    private val fallbackText: String? = null,
) : AppBrowserInstantViewResolveHost {
    override val context: Context =
        object : ContextWrapper(null) {}

    override fun captureArchiveSnapshot(
        onResult: (AppBrowserInstantViewArchiveSnapshot?) -> Unit,
    ) {
        onResult(
            archiveHtmlSnapshot?.let { html ->
                AppBrowserInstantViewArchiveSnapshot(
                    pageUrl = url.orEmpty(),
                    mainHtml = sanitizeAppBrowserInstantViewArchiveHtmlSnapshot(html),
                    resourcesByUrl = mapOf(
                        url.orEmpty() to AppBrowserInstantViewArchiveResource(
                            mimeType = "text/html",
                            encoding = "UTF-8",
                            data = sanitizeAppBrowserInstantViewArchiveHtmlSnapshot(html)
                                .toByteArray(StandardCharsets.UTF_8),
                        ),
                    ),
                )
            },
        )
    }

    override fun evaluateJavascript(
        script: String,
        onResult: (String?) -> Unit,
    ) {
        val result = when (script) {
            appBrowserInstantViewHtmlJavascript -> htmlSnapshot?.toJavascriptString()
            appBrowserInstantViewTextJavascript -> fallbackText?.toJavascriptString()
            else -> null
        }
        onResult(result)
    }
}

private fun String.toJavascriptString(): String {
    return buildString {
        append('"')
        for (char in this@toJavascriptString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
