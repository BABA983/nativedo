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
        durationMillis = NativeDoDuration.PopupOpen,
        easing = NativeDoEasing.Standard,
    )

    fun popupCloseFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.PopupClose,
        easing = NativeDoEasing.Standard,
    )

    fun colorBlendFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.ColorBlend,
        easing = NativeDoEasing.Standard,
    )

    fun browserOpenFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.BrowserOpen,
        easing = NativeDoEasing.EaseOutQuint,
    )

    fun browserFullscreenFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.BrowserFullscreen,
        easing = NativeDoEasing.EaseOutQuint,
    )

    fun pageHorizontalFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.PageHorizontal,
        easing = NativeDoEasing.EaseOutQuint,
    )

    fun mediaOpenFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.MediaOpen,
        easing = NativeDoEasing.EaseOutQuint,
    )

    fun mediaCloseFloat(): TweenSpec<Float> = tween(
        durationMillis = NativeDoDuration.MediaClose,
        easing = NativeDoEasing.EaseOutQuint,
    )
}
