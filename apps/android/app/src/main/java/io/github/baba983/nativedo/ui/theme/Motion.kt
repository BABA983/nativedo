package io.github.baba983.nativedo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object NativeDoDuration {
    const val PopupOpen = 150
    const val PopupClose = 150
    const val PopupCascadeStep = 16

    const val ColorBlend = 200

    const val ContentEnter = 220
    const val ContentExit = 180

    const val BrowserOpen = 220
    const val BrowserSearchEnter = 320
    const val BrowserSearchExit = 220
    const val BrowserAddressEnter = 320
    const val BrowserAddressExit = 280
    const val BrowserFullscreen = 280

    const val PageHorizontal = 320

    const val MediaOpen = 200
    const val MediaClose = 200

    const val AnchorReveal = 140
    const val AnchorSettle = 180

    const val BulletinSlideIn = 240
    const val BulletinFadeIn = 180
    const val BulletinSlideOut = 200
    const val BulletinFadeOut = 160
}

object NativeDoEasing {
    val Linear: Easing = LinearEasing
    val Standard: Easing = LinearOutSlowInEasing
    val EaseOutQuint: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val BrowserDismiss: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

object NativeDoMotionPreset {
    fun popupOpenFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.popup.open.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.popup.open.delayMillis,
        easing = DefaultNativeDoMotionScheme.popup.open.easing,
    )

    fun popupCloseFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.popup.close.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.popup.close.delayMillis,
        easing = DefaultNativeDoMotionScheme.popup.close.easing,
    )

    fun colorBlendFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.tab.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.tab.delayMillis,
        easing = DefaultNativeDoMotionScheme.tab.easing,
    )

    fun contentEnterFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.content.enter.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.content.enter.delayMillis,
        easing = DefaultNativeDoMotionScheme.content.enter.easing,
    )

    fun contentExitFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.content.exit.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.content.exit.delayMillis,
        easing = DefaultNativeDoMotionScheme.content.exit.easing,
    )

    fun browserOpenFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.browser.open.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.browser.open.delayMillis,
        easing = DefaultNativeDoMotionScheme.browser.open.easing,
    )

    fun browserFullscreenFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.browser.fullscreen.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.browser.fullscreen.delayMillis,
        easing = DefaultNativeDoMotionScheme.browser.fullscreen.easing,
    )

    fun pageHorizontalFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.page.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.page.delayMillis,
        easing = DefaultNativeDoMotionScheme.page.easing,
    )

    fun mediaOpenFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.media.enter.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.media.enter.delayMillis,
        easing = DefaultNativeDoMotionScheme.media.enter.easing,
    )

    fun mediaCloseFloat(): TweenSpec<Float> = tween(
        durationMillis = DefaultNativeDoMotionScheme.media.exit.durationMillis,
        delayMillis = DefaultNativeDoMotionScheme.media.exit.delayMillis,
        easing = DefaultNativeDoMotionScheme.media.exit.easing,
    )
}
