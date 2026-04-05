package io.github.baba983.nativedo.core.ui

internal enum class PostCodeTokenKind {
    Plain,
    Keyword,
    String,
    Comment,
    Number,
}

internal data class PostCodeToken(
    val text: String,
    val kind: PostCodeTokenKind,
)

internal fun highlightCode(
    code: String,
    language: String?,
): List<PostCodeToken> {
    if (language == null) {
        return listOf(PostCodeToken(code, PostCodeTokenKind.Plain))
    }

    val keywordRegex = when (language.lowercase()) {
        "kotlin" -> Regex("""\b(fun|val|var|class|object|when|if|else|return)\b""")
        "java" -> Regex("""\b(public|private|class|static|void|return|new)\b""")
        "json" -> Regex(""""[^"]+"(?=:)""")
        "bash", "shell" -> Regex("""\b(if|then|fi|for|do|done|echo)\b""")
        else -> return listOf(PostCodeToken(code, PostCodeTokenKind.Plain))
    }
    val stringRegex = Regex(""""([^"\\\\]|\\\\.)*"""")
    val segments = mutableListOf<PostCodeToken>()
    var cursor = 0

    while (cursor < code.length) {
        val stringMatch = stringRegex.find(code, cursor)
        val keywordMatch = keywordRegex.find(code, cursor)
        val nextMatch = listOfNotNull(stringMatch, keywordMatch).minByOrNull { it.range.first }

        if (nextMatch == null) {
            segments += PostCodeToken(
                text = code.substring(cursor),
                kind = PostCodeTokenKind.Plain,
            )
            break
        }

        if (nextMatch.range.first > cursor) {
            segments += PostCodeToken(
                text = code.substring(cursor, nextMatch.range.first),
                kind = PostCodeTokenKind.Plain,
            )
        }

        segments += PostCodeToken(
            text = nextMatch.value,
            kind = if (nextMatch == stringMatch) {
                PostCodeTokenKind.String
            } else {
                PostCodeTokenKind.Keyword
            },
        )
        cursor = nextMatch.range.last + 1
    }

    return segments
}
