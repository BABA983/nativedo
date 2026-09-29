package io.github.baba983.nativedo.core.media

/**
 * The markdown an uploaded image or video contributes: the site's URL, with a readable alt built
 * from the filename.
 */
internal fun replyMediaMarkup(alt: String, url: String): String {
    if (url.isBlank()) return ""
    val name = alt.trim().replace("]", "").replace("\n", " ").ifBlank { "image" }
    return "![$name](${url.trim()})"
}

/**
 * The site's own markdown for a non-image upload: the `|attachment` marker is what makes the cook
 * emit `<a class="attachment" href="...">name</a>` (the marker is read from the link's label and
 * never from the URL, so the plain `/uploads/...` URL this client uploads to is fine), and the size
 * text after the link is cosmetic - the site never regenerates it. `[`, `]` and `|` are stripped
 * from the label the way the site's composer strips them.
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
 * Which of the two the site would write for this file: its own composer asks the file's kind
 * (`FileHelper.is_supported_image?`), and a video is embedded the same way an image is.
 */
internal fun replyUploadMarkup(name: String, url: String, mimeType: String?, humanSize: String?): String {
    val isVisual = mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true
    return if (isVisual) replyMediaMarkup(name, url) else replyAttachmentMarkup(name, url, humanSize)
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
