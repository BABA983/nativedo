package io.github.baba983.nativedo.topic

/**
 * Discourse reactions as upstream's message chips.
 *
 * <p>Discourse names a reaction ("+1", "heart", "tieba_087"); upstream draws a chip from an
 * emoticon. The table below is read in both directions, so the reaction a chip stands for is the
 * one a tap toggles — an id upstream cannot draw as an emoji is shown as its own text.</p>
 */
object DiscourseReactions {

    private val glyphs = mapOf(
        "+1" to "\uD83D\uDC4D",
        "heart" to "\u2764",
        "laughing" to "\uD83D\uDE04",
        "open_mouth" to "\uD83D\uDE2E",
        "clap" to "\uD83D\uDC4F",
        // Two different reactions, two different glyphs: the table is read back to find the id a
        // chip toggles, so an id that shares another's glyph would toggle the wrong one. Discourse's
        // `confetti_ball` is the confetti ball (U+1F38A), not the party popper (U+1F389).
        "confetti_ball" to "\uD83C\uDF8A",
        "tada" to "\uD83C\uDF89",
        "hugs" to "\uD83E\uDD17",
        "distorted_face" to "\uD83E\uDD74",
        "eyes" to "\uD83D\uDC40",
        "rocket" to "\uD83D\uDE80",
        "thinking" to "\uD83E\uDD14",
    )

    fun emoticonOf(reactionId: String): String = glyphs[reactionId] ?: reactionId

    fun idOf(emoticon: String): String =
        glyphs.entries.firstOrNull { it.value == emoticon }?.key ?: emoticon
}
