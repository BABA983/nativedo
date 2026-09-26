package io.github.baba983.nativedo.core.ui

import org.telegram.ui.iv.Latex
import kotlin.math.roundToInt

/**
 * The rendered LaTeX bitmaps both bodies share.
 *
 * The inline math span and the display math block ask for the same source at the same text size,
 * and a list rebind asks for it again, so one render per (size, source) is enough - `Latex.render`
 * is pure but not free. The cache lives here rather than in either caller because the two bodies
 * draw the same formula.
 */
object MathLatexCache {
    private val entries = InlineMemoryCache<Latex>(maxEntries = 64)

    /** The rendered formula, or null when the renderer cannot parse it (callers fall back to text). */
    @JvmStatic
    fun render(source: String, textSizePx: Float): Latex? {
        val key = "${textSizePx.roundToInt()}:$source"
        entries.get(key)?.let { return it }
        val rendered = Latex.render(source, textSizePx, true)
        if (rendered != null) {
            entries.put(key, rendered)
        }
        return rendered
    }
}
