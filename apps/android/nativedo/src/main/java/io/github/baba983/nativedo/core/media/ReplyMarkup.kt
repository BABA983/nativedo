package io.github.baba983.nativedo.core.media

/**
 * The markdown an uploaded image or video contributes, the way the site's own composer writes it:
 * its `upload://` handle, and the dimensions after the name where it knows them (`|WxH`), which is
 * what makes the cook reserve the right space before the file is fetched.
 */
internal fun replyMediaMarkup(alt: String, url: String, width: Int = 0, height: Int = 0): String {
    if (url.isBlank()) return ""
    val name = alt.trim().replace("]", "").replace("\n", " ").ifBlank { "image" }
    val size = if (width > 0 && height > 0) "|${width}x$height" else ""
    return "![$name$size](${url.trim()})"
}

/**
 * The site's own markdown for a non-image upload: the `|attachment` marker is what makes the cook
 * emit `<a class="attachment" href="...">name</a>` (the marker is read from the link's label, never
 * from the URL), and the size text after the link is cosmetic - the site never regenerates it.
 * `[`, `]` and `|` are stripped from the label the way the site's composer strips them.
 */
internal fun replyAttachmentMarkup(name: String, url: String, humanSize: String?): String {
    if (url.isBlank()) return ""
    val label = name.trim()
        .replace("[", "")
        .replace("]", "")
        .replace("|", "")
        .replace("\n", " ")
        .ifBlank { "file" }
    val size = humanSize?.trim()?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
    return "[$label|attachment](${url.trim()})$size"
}

/**
 * Which of the two the site would write for this file, and where it points: its own composer asks
 * the file's kind (`FileHelper.is_supported_image?`), embeds the answer's `upload://` handle, and
 * falls back to the resolved URL only for a site that answered without one.
 */
internal fun replyUploadMarkup(
    name: String,
    shortUrl: String?,
    url: String,
    mimeType: String?,
    width: Int,
    height: Int,
    humanSize: String?,
): String {
    val target = shortUrl?.trim().takeIf { !it.isNullOrEmpty() } ?: url
    val isVisual = mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true
    return if (isVisual) {
        replyMediaMarkup(name, target, width, height)
    } else {
        replyAttachmentMarkup(name, target, humanSize)
    }
}

/**
 * Where an uploaded file lands in the draft: on a line of its own (the web composer writes
 * `![name](url)` the same way), with a newline first when the caret is mid-line and a trailing one
 * so typing continues on a fresh line.
 */
internal fun replyMarkupInsertion(
    currentText: CharSequence,
    selectionStart: Int,
    selectionEnd: Int,
    markdown: String,
): String {
    if (markdown.isBlank()) return ""
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, currentText.length)
    val prefix = if (start > 0 && currentText[start - 1] != '\n') "\n" else ""
    return prefix + markdown + "\n"
}

/**
 * Puts [markup] into the composer at the caret, replacing whatever is selected, and leaves the
 * caret after the inserted line. The field is upstream's own, so the text is changed through it
 * rather than through a setter: upstream reacts to the change the way it reacts to typing.
 */
fun insertReplyMarkup(field: android.widget.EditText?, markup: String) {
    if (field == null || markup.isBlank()) return
    val current = field.text ?: return
    val start = field.selectionStart.coerceAtLeast(0)
    val end = field.selectionEnd.coerceAtLeast(0)
    val insertion = replyMarkupInsertion(current, start, end, markup)
    if (insertion.isEmpty()) return
    val from = minOf(start, end).coerceIn(0, current.length)
    val to = maxOf(start, end).coerceIn(0, current.length)
    current.replace(from, to, insertion)
    field.setSelection((from + insertion.length).coerceAtMost(field.text?.length ?: 0))
}
