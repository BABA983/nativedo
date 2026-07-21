/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's
 * org/telegram/ui/ThemePreviewActivity.java local preview mode.
 */
package io.github.baba983.nativedo.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R as TelegramR
import org.telegram.messenger.SvgHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.MotionBackgroundDrawable
import org.telegram.ui.Components.SeekBarView
import org.telegram.ui.Components.ViewPagerFixed
import org.telegram.ui.Components.WallpaperCheckBoxView
import org.telegram.ui.Components.WallpaperParallaxEffect
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class TopicThemePreviewFragment @JvmOverloads constructor(
    private val applyingTheme: Theme.ThemeInfo,
    private val nightTheme: Boolean,
    private val previewAccentId: Int = -1,
) : BaseFragment() {
    private val backupAccentId = applyingTheme.currentAccentId
    private val accent: Theme.ThemeAccent?
    private var backupAccent: Theme.ThemeAccent? = null
    private val backupBlurred = applyingTheme.isBlured
    private val backupMotion = applyingTheme.isMotion
    private val backupPatternIntensity = applyingTheme.patternIntensity
    private val backupAccentIntensity: Float
    private val backupAccentMotion: Boolean
    private val backupAccentColor2: Int

    private var applied = false
    private var pager: ViewPagerFixed? = null
    private var dotsView: View? = null
    private var topicPage: TopicThemePreviewContent? = null
    private var wallpaperPage: WallpaperPreviewPage? = null
    private var isResumed = false

    init {
        if (previewAccentId >= 0) {
            applyingTheme.setCurrentAccentId(previewAccentId)
        }
        accent = applyingTheme.getAccent(false)
        backupAccent = accent?.let { Theme.ThemeAccent(it) }
        backupAccentIntensity = accent?.patternIntensity ?: 0f
        backupAccentMotion = accent?.patternMotion ?: false
        backupAccentColor2 = accent?.accentColor2 ?: 0
        Theme.applyThemeTemporary(applyingTheme, accent != null)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.goingToPreviewTheme)
    }

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(TelegramR.drawable.ic_ab_back)
        actionBar.setTitle(LocaleController.getString(TelegramR.string.ThemePreview))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    cancelThemeApply(false)
                }
            }
        })

        val root = FrameLayout(context)
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))

        pager = object : ViewPagerFixed(context) {
            override fun onItemSelected(currentPage: View?, oldPage: View?, position: Int, oldPosition: Int) {
                dotsView?.invalidate()
            }
        }.also { viewPager ->
            viewPager.setAdapter(object : ViewPagerFixed.Adapter() {
                override fun getItemCount() = 2

                override fun createView(viewType: Int): View = FrameLayout(context)

                override fun bindView(view: View, position: Int, viewType: Int) {
                    val container = view as FrameLayout
                    container.removeAllViews()
                    val page = if (position == 0) {
                        topicPage ?: TopicThemePreviewContent(context).also { topicPage = it }
                    } else {
                        wallpaperPage ?: WallpaperPreviewPage(context).also { wallpaperPage = it }
                    }
                    if (page === wallpaperPage) {
                        wallpaperPage?.setResumed(isResumed)
                    }
                    (page.parent as? ViewGroup)?.removeView(page)
                    container.addView(page, FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
                }
            })
            root.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.START, 0f, 0f, 0f, 48f))
        }

        val divider = View(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine))
        }
        root.addView(divider, FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM).apply {
            bottomMargin = AndroidUtilities.dp(48f)
        })

        val buttons = FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        root.addView(buttons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM))

        dotsView = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                val selected = if (pager?.isCurrentTabFirst == false) 1 else 0
                paint.color = Theme.getColor(Theme.key_chat_fieldOverlayText)
                repeat(2) { index ->
                    paint.alpha = if (index == selected) 255 else 127
                    canvas.drawCircle(AndroidUtilities.dp(3f + 15f * index).toFloat(), AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(3f).toFloat(), paint)
                }
            }
        }.also { dots ->
            buttons.addView(dots, LayoutHelper.createFrame(22, 8, Gravity.CENTER))
        }

        buttons.addView(createBottomButton(context, TelegramR.string.Cancel) { cancelThemeApply(false) },
            LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.START))
        buttons.addView(createBottomButton(context, TelegramR.string.ApplyTheme) { applyTheme() },
            LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END))

        fragmentView = root
        return root
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        wallpaperPage?.setResumed(true)
    }

    override fun onPause() {
        isResumed = false
        wallpaperPage?.setResumed(false)
        super.onPause()
    }

    override fun onBackPressed(): Boolean {
        cancelThemeApply(true)
        return true
    }

    override fun onFragmentDestroy() {
        wallpaperPage?.release()
        if (!applied && Theme.getPreviousTheme() != null) {
            restorePreviewChanges()
            Theme.applyPreviousTheme()
        }
        super.onFragmentDestroy()
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> {
        return arrayListOf(
            ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),
        )
    }

    private fun createBottomButton(context: Context, stringId: Int, action: () -> Unit): TextView {
        return TextView(context).apply {
            text = LocaleController.getString(stringId)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText))
            typeface = AndroidUtilities.bold()
            setPadding(AndroidUtilities.dp(29f), 0, AndroidUtilities.dp(29f), 0)
            background = Theme.createSelectorDrawable(0x0f000000, 0)
            setOnClickListener { action() }
        }
    }

    private fun applyTheme() {
        wallpaperPage?.writeStateToTheme()
        if (accent != null) {
            Theme.saveThemeAccents(applyingTheme, true, false, false, false)
        }
        Theme.clearPreviousTheme()
        Theme.applyTheme(applyingTheme, nightTheme)
        if (nightTheme) {
            Theme.setCurrentNightTheme(applyingTheme)
            Theme.saveAutoNightThemeConfig()
        }
        applied = true
        (parentLayout as? ActionBarLayout)?.rebuildAllFragmentViews(false)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated)
        if (!nightTheme) {
            previousFragment()?.let(Theme::turnOffAutoNight)
        }
        finishFragment()
    }

    private fun cancelThemeApply(fromBack: Boolean) {
        if (applied) return
        restorePreviewChanges()
        Theme.applyPreviousTheme()
        applied = true
        (parentLayout as? ActionBarLayout)?.rebuildAllFragmentViews(false)
        if (!fromBack) {
            finishFragment()
        }
    }

    private fun restorePreviewChanges() {
        applyingTheme.isBlured = backupBlurred
        applyingTheme.isMotion = backupMotion
        applyingTheme.patternIntensity = backupPatternIntensity
        applyingTheme.setCurrentAccentId(backupAccentId)
        accent?.let { current ->
            backupAccent?.let { backup ->
                current.accentColor = backup.accentColor
                current.accentColor2 = if (backup.accentColor2 != 0) backup.accentColor2 else backupAccentColor2
                current.myMessagesAccentColor = backup.myMessagesAccentColor
                current.myMessagesGradientAccentColor1 = backup.myMessagesGradientAccentColor1
                current.myMessagesGradientAccentColor2 = backup.myMessagesGradientAccentColor2
                current.myMessagesGradientAccentColor3 = backup.myMessagesGradientAccentColor3
                current.myMessagesAnimated = backup.myMessagesAnimated
                current.backgroundOverrideColor = backup.backgroundOverrideColor
                current.backgroundGradientOverrideColor1 = backup.backgroundGradientOverrideColor1
                current.backgroundGradientOverrideColor2 = backup.backgroundGradientOverrideColor2
                current.backgroundGradientOverrideColor3 = backup.backgroundGradientOverrideColor3
                current.backgroundRotation = backup.backgroundRotation
                current.patternSlug = backup.patternSlug
                current.patternIntensity = backup.patternIntensity
                current.patternMotion = backup.patternMotion
                current.overrideWallpaper = backup.overrideWallpaper
            } ?: run {
                current.patternIntensity = backupAccentIntensity
                current.patternMotion = backupAccentMotion
            }
            Theme.saveThemeAccents(applyingTheme, false, true, false, false)
        }
    }

    private fun previousFragment(): BaseFragment? {
        val stack = parentLayout?.fragmentStack ?: return null
        return stack.getOrNull(stack.size - 2)
    }

    private inner class WallpaperPreviewPage(context: Context) : FrameLayout(context), Theme.Colorable {
        private val wallpaperView = WallpaperView(context)
        private val parallax = WallpaperParallaxEffect(context)
        private val blurButton = createToggle(TelegramR.string.BackgroundBlurred)
        private val motionButton = createToggle(TelegramR.string.BackgroundMotion)
        private val patternButton = createToggle(TelegramR.string.BackgroundPattern)
        private val intensitySlider = SeekBarView(context)
        private var resumed = false
        private var blurred = applyingTheme.isBlured
        private var motion = accent?.patternMotion ?: false
        private var pattern = !accent?.patternSlug.isNullOrEmpty() || applyingTheme.patternBgColor != 0 || applyingTheme.patternIntensity != 0
        private var intensity = (accent?.patternIntensity ?: applyingTheme.patternIntensity / 100f).coerceIn(-1f, 1f)

        init {
            addView(wallpaperView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val controls = FrameLayout(context)
            addView(controls, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 112f, Gravity.BOTTOM or Gravity.START, 0f, 0f, 0f, 16f))

            controls.addView(blurButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32f, Gravity.TOP or Gravity.CENTER_HORIZONTAL, -112f, 0f, 0f, 0f))
            controls.addView(motionButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32f, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 0f, 0f, 0f))
            controls.addView(patternButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32f, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 112f, 0f, 0f, 0f))
            controls.addView(intensitySlider, LayoutHelper.createFrame(190, 44, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

            blurButton.setChecked(blurred, false)
            motionButton.setChecked(motion, false)
            patternButton.setChecked(pattern, false)
            intensitySlider.setTwoSided(Theme.getActiveTheme()?.isDark == true && Theme.getCachedWallpaper() is MotionBackgroundDrawable)
            intensitySlider.setProgress(intensity)
            intensitySlider.setReportChanges(true)
            intensitySlider.setDelegate(object : SeekBarView.SeekBarViewDelegate {
                override fun onSeekBarDrag(stop: Boolean, progress: Float) {
                    intensity = progress.coerceIn(-1f, 1f)
                    wallpaperView.setPattern(pattern, intensity)
                }
            })
            intensitySlider.visibility = if (pattern) VISIBLE else INVISIBLE

            blurButton.setOnClickListener {
                blurred = !blurred
                blurButton.setChecked(blurred, true)
                wallpaperView.setBlurred(blurred)
            }
            motionButton.setOnClickListener {
                motion = !motion
                motionButton.setChecked(motion, true)
                updateParallax()
            }
            patternButton.setOnClickListener {
                pattern = !pattern
                patternButton.setChecked(pattern, true)
                intensitySlider.visibility = if (pattern) VISIBLE else INVISIBLE
                wallpaperView.setPattern(pattern, intensity)
            }

            parallax.setCallback { x, y, _ ->
                wallpaperView.translationX = x.toFloat()
                wallpaperView.translationY = y.toFloat()
            }
            wallpaperView.setPattern(pattern, intensity)
            wallpaperView.setBlurred(blurred)
        }

        override fun updateColors() {
            wallpaperView.updateWallpaper()
            blurButton.invalidate()
            motionButton.invalidate()
            patternButton.invalidate()
        }

        fun setResumed(value: Boolean) {
            resumed = value
            updateParallax()
        }

        fun release() {
            resumed = false
            parallax.setEnabled(false)
        }

        fun writeStateToTheme() {
            applyingTheme.isBlured = blurred
            applyingTheme.isMotion = motion
            applyingTheme.patternIntensity = if (pattern) (intensity * 100f).roundToInt() else 0
            accent?.patternIntensity = if (pattern) intensity else 0f
            accent?.patternMotion = motion
        }

        private fun updateParallax() {
            parallax.setEnabled(resumed && motion)
            val scale = if (motion && wallpaperView.width > 0 && wallpaperView.height > 0) {
                parallax.getScale(wallpaperView.width, wallpaperView.height)
            } else 1f
            wallpaperView.scaleX = scale
            wallpaperView.scaleY = scale
            if (!motion) {
                wallpaperView.translationX = 0f
                wallpaperView.translationY = 0f
            }
        }

        private fun createToggle(stringId: Int): WallpaperCheckBoxView {
            val label = LocaleController.getString(stringId)
            return WallpaperCheckBoxView(context, true, this, null).apply {
                val width = ceil(textPaint.measureText(label).toDouble()).toInt()
                setText(label, width, width)
            }
        }
    }

    private class WallpaperView(context: Context) : View(context) {
        private var wallpaper: Drawable? = null
        private var patternBitmap: Bitmap? = null
        private var previewWallpaper: Drawable? = null
        private var blurredBitmap: Bitmap? = null
        private var blurred = false
        private var pattern = false
        private var intensity = .5f
        private var creatingBlur = false
        private var capturingBlur = false
        private var blurGeneration = 0
        private var blurRunnable: Runnable? = null
        private val destination = RectF()

        init {
            updateWallpaper()
        }

        fun updateWallpaper() {
            wallpaper = Theme.getCachedWallpaper()
            rebuildPreviewWallpaper()
            clearBlur()
            invalidate()
            if (blurred) createBlur()
        }

        fun setPattern(enabled: Boolean, value: Float) {
            pattern = enabled
            intensity = value
            rebuildPreviewWallpaper()
            clearBlur()
            invalidate()
            if (blurred) createBlur()
        }

        fun setBlurred(value: Boolean) {
            blurred = value
            if (value) createBlur() else {
                clearBlur()
                invalidate()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildPreviewWallpaper()
            if (blurred) createBlur()
        }

        override fun onDraw(canvas: Canvas) {
            val blur = blurredBitmap
            if (blurred && !capturingBlur && blur != null && !blur.isRecycled) {
                destination.set(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(blur, null, destination, null)
                return
            }
            drawWallpaper(canvas)
        }

        private fun drawWallpaper(canvas: Canvas) {
            val base = previewWallpaper ?: wallpaper
            if (base == null) {
                canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper))
            } else {
                base.setBounds(0, 0, width, height)
                base.draw(canvas)
            }
            if (previewWallpaper == null && pattern && patternBitmap != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (255 * kotlin.math.abs(intensity)).roundToInt().coerceIn(0, 255)
                    colorFilter = PorterDuffColorFilter(
                        AndroidUtilities.getPatternColor(Theme.getColor(Theme.key_chat_wallpaper)),
                        PorterDuff.Mode.SRC_IN,
                    )
                }
                canvas.drawBitmap(patternBitmap!!, null, destination.apply { set(0f, 0f, width.toFloat(), height.toFloat()) }, paint)
            }
        }

        private fun createBlur() {
            if (!blurred || width <= 0 || height <= 0 || creatingBlur) return
            creatingBlur = true
            clearBlur()
            val generation = ++blurGeneration
            blurRunnable = Runnable {
                if (!blurred || generation != blurGeneration) {
                    creatingBlur = false
                    return@Runnable
                }
                capturingBlur = true
                val bitmap = AndroidUtilities.makeBlurBitmap(this)
                capturingBlur = false
                if (generation == blurGeneration && blurred) {
                    blurredBitmap = bitmap
                } else {
                    bitmap?.recycle()
                }
                creatingBlur = false
                invalidate()
            }
            post(blurRunnable)
        }

        private fun clearBlur() {
            blurGeneration++
            blurRunnable?.let(::removeCallbacks)
            blurRunnable = null
            blurredBitmap?.recycle()
            blurredBitmap = null
            creatingBlur = false
        }

        override fun onDetachedFromWindow() {
            clearBlur()
            super.onDetachedFromWindow()
        }

        private fun rebuildPreviewWallpaper() {
            patternBitmap = null
            previewWallpaper = null
            val current = wallpaper
            if (current is MotionBackgroundDrawable) {
                val colors = current.colors
                val copy = MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], false)
                copy.setPhase(current.phase)
                patternBitmap = current.patternBitmap
                if (pattern && patternBitmap != null) {
                    copy.setPatternBitmap((intensity * 100f).roundToInt(), patternBitmap, true)
                    copy.setPatternColorFilter(copy.patternColor)
                    previewWallpaper = copy
                } else if (!pattern && current.patternBitmap != null) {
                    previewWallpaper = MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], false).also {
                        it.setPhase(current.phase)
                    }
                }
            }
            if (patternBitmap == null && pattern && width > 0 && height > 0) {
                patternBitmap = SvgHelper.getBitmap(TelegramR.raw.default_pattern, width, height, Color.WHITE, 1f, SvgHelper.ScaleMode.ByWidth)
            }
        }
    }
}
