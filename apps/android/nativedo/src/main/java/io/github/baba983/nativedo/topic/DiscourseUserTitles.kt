package io.github.baba983.nativedo.topic

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.CharacterStyle
import io.github.baba983.nativedo.hooks.NativeDoHooks
import kotlin.math.cos

/**
 * The site's holographic user titles.
 *
 * <p>An answer carries only the title's text (`user_title`); the web theme maps that text onto a
 * `user-title--<title>` class and paints a few of them with the animated gradient. That list lives
 * in the theme, not in the wire, so the titles it names are kept here; one the site adds later keeps
 * the plain tag until it is named here too.</p>
 *
 * <p>The tag itself is upstream's: a forum post writes its title into the message's `from_rank`,
 * which a chat already draws beside the sender's name. This object only decorates that text for the
 * holographic titles, and the screen's frame loop calls [advance] while one is loaded.</p>
 */
object DiscourseUserTitles {
    /** The titles the site's holographic selector paints, as its theme names them. */
    private val ANIMATED = setOf("种子用户", "破界者", "premium")

    /** The gradient's stops, taken from the theme's `holographic` rule. */
    private val COLORS = intArrayOf(
        0xFFFF00FF.toInt(),
        0xFF00FFFF.toInt(),
        0xFFFFFF00.toInt(),
        0xFFFF00FF.toInt(),
        0xFF00FFFF.toInt(),
    )
    private val POSITIONS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    /** The theme's 15 s cycle; the gradient travels the same distance in that time. */
    private const val CYCLE_MILLIS = 15_000L

    private val startedAt = SystemClock.uptimeMillis()
    private var installed = false

    /** How far the gradient has travelled, 0..1; read at draw time by every animated tag. */
    @Volatile
    var phase: Float = 0f
        private set

    /** Registers the decoration once; a tag without a holographic title is left untouched. */
    fun install() {
        if (installed) {
            return
        }
        installed = true
        NativeDoHooks.setNameTagDecorator { tag -> decorate(tag) }
    }

    fun isAnimated(title: String?): Boolean = title != null && ANIMATED.contains(title.trim())

    /**
     * Advances the gradient. The screen calls this from the frame it already draws while a title is
     * on screen, so the cost follows the content rather than running for every topic.
     */
    fun advance() {
        val elapsed = (SystemClock.uptimeMillis() - startedAt) % CYCLE_MILLIS
        val progress = elapsed / CYCLE_MILLIS.toFloat()
        // The theme animates the background's position; a smooth there-and-back keeps the travel
        // without a visible seam when one cycle meets the next.
        phase = (0.5f - 0.5f * cos(2.0 * Math.PI * progress)).toFloat()
    }

    private fun decorate(tag: SpannableStringBuilder): SpannableStringBuilder {
        val title = tag.toString()
        if (!isAnimated(title)) {
            return tag
        }
        tag.setSpan(
            HolographicSpan(title.length),
            0,
            tag.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return tag
    }

    /**
     * Paints one tag with the theme's gradient. A `CharacterStyle` keeps the tag's own metrics, so
     * the chat's name layout keeps its width; the gradient is read again on every draw, which is
     * what lets the phase move without rebuilding the layout.
     */
    private class HolographicSpan(private val chars: Int) : CharacterStyle() {
        private var shader: LinearGradient? = null
        private var matrix = Matrix()
        private var period = 0f

        override fun updateDrawState(tp: TextPaint) {
            val width = maxOf(tp.textSize * chars * 4f, tp.textSize * 8f)
            if (shader == null || period != width) {
                period = width
                shader = LinearGradient(
                    0f, 0f, width, 0f,
                    COLORS, POSITIONS, Shader.TileMode.CLAMP,
                )
                matrix = Matrix()
            }
            matrix.setTranslate(-phase * period, 0f)
            shader?.setLocalMatrix(matrix)
            tp.shader = shader
        }
    }
}
