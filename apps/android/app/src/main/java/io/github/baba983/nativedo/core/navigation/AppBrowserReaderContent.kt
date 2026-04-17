package io.github.baba983.nativedo.core.navigation

internal data class AppBrowserReaderContent(
    val document: AppBrowserReaderDocument,
) {
    constructor(
        url: String,
        title: String,
        bodyText: String,
        sourceName: String? = null,
        excerpt: String? = null,
        byline: String? = null,
        publishedTime: String? = null,
        heroImageUrl: String? = null,
    ) : this(
        document = buildPlainAppBrowserReaderDocument(
            url = url,
            title = title,
            bodyText = bodyText,
            sourceName = sourceName,
            excerpt = excerpt,
            byline = byline,
            publishedTime = publishedTime,
            heroImageUrl = heroImageUrl,
        ),
    )

    val url: String
        get() = document.url

    val title: String
        get() = document.title

    val bodyText: String
        get() = document.flattenBodyText()

    val sourceName: String?
        get() = document.sourceName

    val excerpt: String?
        get() = document.excerpt

    val byline: String?
        get() = document.byline

    val publishedTime: String?
        get() = document.publishedTime

    val heroImageUrl: String?
        get() = document.heroImageUrl
}
