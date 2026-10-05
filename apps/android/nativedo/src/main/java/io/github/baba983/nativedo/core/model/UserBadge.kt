package io.github.baba983.nativedo.core.model

/**
 * One badge a reader was granted, as `/user-badges/{username}.json` (and the summary's own `badges`
 * array) list it: the grant (`grantedAt`, `count`) joined with the badge's definition (`name`,
 * `description`, `icon`, `imageUrl`).
 *
 * <p>The site keeps the two lists apart - a grant only carries `badge_id` - so the join happens in
 * the mapper. `icon` is a Font Awesome name the site renders with its own sprite; this client has
 * no sprite at hand, so the row falls back to drawing the name and description without the glyph.
 * The description arrives as cooked HTML and is read as text before it reaches a cell.</p>
 */
data class UserBadge(
    val id: Long,
    val badgeId: Long,
    val name: String,
    val description: String = "",
    val icon: String = "",
    val imageUrl: String = "",
    val grantedAt: String = "",
    val count: Int = 1,
)
