package io.github.baba983.nativedo.core.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Handler
import android.os.Looper
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.util.AndroidUtilities
import io.github.baba983.nativedo.core.util.CubicBezierInterpolator

object NdTheme : ComponentCallbacks {
    const val key_windowBackgroundWhite: String = "windowBackgroundWhite"
    const val key_windowBackgroundGray: String = "windowBackgroundGray"
    const val key_windowBackgroundWhiteBlackText: String = "windowBackgroundWhiteBlackText"
    const val key_windowBackgroundWhiteGrayText: String = "windowBackgroundWhiteGrayText"
    const val key_actionBarDefault: String = "actionBarDefault"
    const val key_actionBarDefaultTitle: String = "actionBarDefaultTitle"
    const val key_actionBarDefaultSubtitle: String = "actionBarDefaultSubtitle"
    const val key_actionBarDefaultIcon: String = "actionBarDefaultIcon"
    const val key_actionBarDefaultSelector: String = "actionBarDefaultSelector"
    const val key_actionBarDefaultSearch: String = "actionBarDefaultSearch"
    const val key_actionBarDefaultSearchPlaceholder: String = "actionBarDefaultSearchPlaceholder"
    const val key_actionBarShadow: String = "actionBarShadow"
    const val key_topic_title: String = "topic_title"
    const val key_topic_excerpt: String = "topic_excerpt"
    const val key_topic_categoryBadge: String = "topic_categoryBadge"
    const val key_topic_metadata: String = "topic_metadata"
    const val key_post_body: String = "post_body"
    const val key_post_codeBlock: String = "post_codeBlock"
    const val key_post_codeBlockBackground: String = "post_codeBlockBackground"
    const val key_post_blockquote: String = "post_blockquote"
    const val key_post_blockquoteBorder: String = "post_blockquoteBorder"
    const val key_post_link: String = "post_link"
    const val key_post_mentionText: String = "post_mentionText"
    const val key_post_mentionBackground: String = "post_mentionBackground"
    const val key_divider: String = "divider"
    const val key_dialogBackground: String = "dialogBackground"
    const val key_dialogBackgroundGray: String = "dialogBackgroundGray"
    const val key_trust_level_badge: String = "trust_level_badge"

    private const val preferencesName = "nd_theme"
    private const val preferenceKeyMode = "theme_mode"
    private const val animationDurationMs = 300L
    private const val fallbackColor = Color.RED

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var preferences: android.content.SharedPreferences? = null

    @Volatile
    private var currentMode: ThemeMode = ThemeMode.System

    @Volatile
    private var currentThemeInfo: ThemeInfo = LightThemeInfo

    @Volatile
    private var currentColors: Map<String, Int> = LightThemeInfo.colors

    private var themeAnimator: ValueAnimator? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (applicationContext !== appContext) {
                applicationContext?.unregisterComponentCallbacks(this)
                applicationContext = appContext
                preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                appContext.registerComponentCallbacks(this)
            }
        }

        val persistedMode = readPersistedThemeMode()
        currentMode = persistedMode
        applyTheme(resolveThemeInfo(persistedMode), animate = false)
    }

    fun getThemeMode(): ThemeMode = currentMode

    fun getCurrentThemeInfo(): ThemeInfo = currentThemeInfo

    fun isDarkTheme(): Boolean = currentThemeInfo.isDark

    fun setThemeMode(
        mode: ThemeMode,
        animate: Boolean = true,
    ) {
        currentMode = mode
        preferences?.edit()?.putString(preferenceKeyMode, mode.name)?.apply()
        applyTheme(resolveThemeInfo(mode), animate = animate)
    }

    fun getColor(key: String): Int {
        return currentColors[key] ?: fallbackColor
    }

    fun setColor(key: String, value: Int) {
        currentColors = currentColors.toMutableMap().apply {
            put(key, value)
        }
        currentThemeInfo = currentThemeInfo.copy(colors = currentColors)
        NotificationCenter.postNotificationName(NotificationCenter.themeDidChange)
    }

    fun applyTheme(
        themeInfo: ThemeInfo,
        animate: Boolean = true,
    ) {
        runOnMainThread {
            val previousColors = currentColors
            currentThemeInfo = themeInfo
            if (previousColors.isEmpty() || previousColors == themeInfo.colors) {
                themeAnimator?.cancel()
                themeAnimator = null
                currentColors = themeInfo.colors
                NotificationCenter.postNotificationName(NotificationCenter.themeDidChange)
                return@runOnMainThread
            }

            val shouldAnimate = animate && NdUiConfig.animationsEnabled
            themeAnimator?.cancel()
            if (!shouldAnimate) {
                currentColors = themeInfo.colors
                NotificationCenter.postNotificationName(NotificationCenter.themeDidChange)
                return@runOnMainThread
            }

            val allKeys = linkedSetOf<String>().apply {
                addAll(previousColors.keys)
                addAll(themeInfo.colors.keys)
            }
            themeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animationDurationMs
                interpolator = CubicBezierInterpolator.DEFAULT
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    currentColors = buildMap(allKeys.size) {
                        allKeys.forEach { key ->
                            val fromColor = previousColors[key] ?: fallbackColor
                            val toColor = themeInfo.colors[key] ?: fallbackColor
                            put(key, AndroidUtilities.blendColors(fromColor, toColor, fraction))
                        }
                    }
                    NotificationCenter.postNotificationName(NotificationCenter.themeDidChange)
                }
                doOnEnd(themeInfo)
                start()
            }
        }
    }

    fun createPaint(key: String): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = getColor(key)
        }
    }

    fun getThemedDrawable(key: String): Drawable {
        return ThemeColorDrawable(key)
    }

    fun createSelectorDrawable(
        colorKey: String,
        radiusDp: Float = 0f,
    ): Drawable {
        val pressedDrawable = if (radiusDp <= 0f) {
            ColorDrawable(getColor(colorKey))
        } else {
            ThemeStaticRoundRectDrawable(
                color = getColor(colorKey),
                radiusPx = AndroidUtilities.dpf2(radiusDp),
            )
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    fun createRoundRectDrawable(
        colorKey: String,
        radiusDp: Float,
    ): Drawable {
        return ThemeRoundRectDrawable(colorKey = colorKey, radiusPx = AndroidUtilities.dpf2(radiusDp))
    }

    fun createRoundRectSelectorDrawable(
        colorKey: String,
        pressedColorKey: String,
        radiusDp: Float,
    ): Drawable {
        val radiusPx = AndroidUtilities.dpf2(radiusDp)
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                ThemeStaticRoundRectDrawable(getColor(pressedColorKey), radiusPx),
            )
            addState(
                intArrayOf(),
                ThemeRoundRectDrawable(colorKey = colorKey, radiusPx = radiusPx),
            )
        }
    }

    fun createHeaderShadowDrawable(
        colorKey: String = key_actionBarShadow,
    ): Drawable {
        return ThemeHeaderShadowDrawable(colorKey)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (currentMode != ThemeMode.System) {
            return
        }
        applyTheme(resolveThemeInfo(ThemeMode.System), animate = true)
    }

    override fun onLowMemory() = Unit

    private fun ValueAnimator.doOnEnd(themeInfo: ThemeInfo) {
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    themeAnimator = null
                }

                override fun onAnimationEnd(animation: Animator) {
                    currentColors = themeInfo.colors
                    currentThemeInfo = themeInfo
                    themeAnimator = null
                    NotificationCenter.postNotificationName(NotificationCenter.themeDidChange)
                }
            },
        )
    }

    private fun resolveThemeInfo(mode: ThemeMode): ThemeInfo {
        val darkTheme = mode.resolveDarkTheme(isSystemInDarkTheme())
        return if (darkTheme) {
            DarkThemeInfo
        } else {
            LightThemeInfo
        }
    }

    private fun isSystemInDarkTheme(): Boolean {
        val context = applicationContext ?: return false
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun readPersistedThemeMode(): ThemeMode {
        val rawValue = preferences?.getString(preferenceKeyMode, ThemeMode.System.name)
        return ThemeMode.entries.firstOrNull { it.name == rawValue } ?: ThemeMode.System
    }

    private fun createMaskDrawable(radiusDp: Float): Drawable {
        if (radiusDp <= 0f) {
            return ColorDrawable(Color.WHITE)
        }
        val radiusPx = AndroidUtilities.dpf2(radiusDp)
        return ShapeDrawable(
            RoundRectShape(
                FloatArray(8) { radiusPx },
                null,
                null,
            ),
        ).apply {
            paint.color = Color.WHITE
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) {
            block()
            return
        }
        mainHandler.post(block)
    }

    private class ThemeColorDrawable(
        private val colorKey: String,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            paint.color = getColor(colorKey)
            canvas.drawRect(bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ThemeRoundRectDrawable(
        private val colorKey: String,
        private val radiusPx: Float,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            rect.set(bounds)
            paint.color = getColor(colorKey)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ThemeStaticRoundRectDrawable(
        private val color: Int,
        private val radiusPx: Float,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            rect.set(bounds)
            paint.color = color
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ThemeHeaderShadowDrawable(
        private val colorKey: String,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) {
                return
            }
            paint.shader = LinearGradient(
                0f,
                bounds.top.toFloat(),
                0f,
                bounds.bottom.toFloat(),
                getColor(colorKey),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds, paint)
            paint.shader = null
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
