package io.github.baba983.nativedo.core.model

/**
 * One of the site's tags, as its list answers it.
 *
 * <p>Discourse names a tag `text`/`name` for a reader and `slug` for a URL - and a tag that has no
 * slug of its own is addressed as `<id>-tag`, which is why both go together on this model: the
 * canonical tag route takes the slug *and* the id.</p>
 */
data class Tag(
    val id: Long,
    val name: String,
    val slug: String,
    val count: Int = 0,
)
