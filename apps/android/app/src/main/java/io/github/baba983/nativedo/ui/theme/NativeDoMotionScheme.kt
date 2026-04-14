package io.github.baba983.nativedo.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.util.lerp

@Immutable
data class NativeDoMotionSpec(
    val durationMillis: Int,
    val easing: Easing,
    val delayMillis: Int = 0,
) {
    fun <T> tweenSpec(): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = easing,
    )
}

@Immutable
data class NativeDoEnterExitMotionScheme(
    val enter: NativeDoMotionSpec,
    val exit: NativeDoMotionSpec,
)

@Immutable
data class NativeDoPopupMotionScheme(
    val open: NativeDoMotionSpec,
    val close: NativeDoMotionSpec,
    val cascadeStepMillis: Int,
)

@Immutable
data class NativeDoBulletinMotionScheme(
    val slideIn: NativeDoMotionSpec,
    val fadeIn: NativeDoMotionSpec,
    val slideOut: NativeDoMotionSpec,
    val fadeOut: NativeDoMotionSpec,
)

@Immutable
data class NativeDoBrowserMotionScheme(
    val open: NativeDoMotionSpec,
    val search: NativeDoEnterExitMotionScheme,
    val address: NativeDoEnterExitMotionScheme,
    val fullscreen: NativeDoMotionSpec,
)

@Immutable
data class NativeDoMotionScheme(
    val chrome: NativeDoEnterExitMotionScheme,
    val label: NativeDoEnterExitMotionScheme,
    val popup: NativeDoPopupMotionScheme,
    val tab: NativeDoMotionSpec,
    val content: NativeDoEnterExitMotionScheme,
    val sheet: NativeDoEnterExitMotionScheme,
    val fullscreen: NativeDoMotionSpec,
    val page: NativeDoMotionSpec,
    val media: NativeDoEnterExitMotionScheme,
    val anchor: NativeDoEnterExitMotionScheme,
    val bulletin: NativeDoBulletinMotionScheme,
    val browser: NativeDoBrowserMotionScheme,
)

internal val DefaultNativeDoMotionScheme = NativeDoMotionScheme(
    chrome = NativeDoEnterExitMotionScheme(
        enter = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ContentEnter,
            easing = NativeDoEasing.Standard,
        ),
        exit = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ContentExit,
            easing = NativeDoEasing.Standard,
        ),
    ),
    label = NativeDoEnterExitMotionScheme(
        enter = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ColorBlend,
            easing = NativeDoEasing.Standard,
        ),
        exit = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ColorBlend,
            easing = NativeDoEasing.Standard,
        ),
    ),
    popup = NativeDoPopupMotionScheme(
        open = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.PopupOpen,
            easing = NativeDoEasing.Standard,
        ),
        close = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.PopupClose,
            easing = NativeDoEasing.Standard,
        ),
        cascadeStepMillis = NativeDoDuration.PopupCascadeStep,
    ),
    tab = NativeDoMotionSpec(
        durationMillis = NativeDoDuration.ColorBlend,
        easing = NativeDoEasing.Standard,
    ),
    content = NativeDoEnterExitMotionScheme(
        enter = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ContentEnter,
            easing = NativeDoEasing.Standard,
        ),
        exit = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ContentExit,
            easing = NativeDoEasing.Standard,
        ),
    ),
    sheet = NativeDoEnterExitMotionScheme(
        enter = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BrowserOpen,
            easing = NativeDoEasing.EaseOutQuint,
        ),
        exit = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.ContentExit,
            easing = NativeDoEasing.BrowserDismiss,
        ),
    ),
    fullscreen = NativeDoMotionSpec(
        durationMillis = NativeDoDuration.BrowserFullscreen,
        easing = NativeDoEasing.EaseOutQuint,
    ),
    page = NativeDoMotionSpec(
        durationMillis = NativeDoDuration.PageHorizontal,
        easing = NativeDoEasing.EaseOutQuint,
    ),
    media = NativeDoEnterExitMotionScheme(
        enter = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.MediaOpen,
            easing = NativeDoEasing.EaseOutQuint,
        ),
        exit = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.MediaClose,
            easing = NativeDoEasing.EaseOutQuint,
        ),
    ),
    anchor = NativeDoEnterExitMotionScheme(
        enter = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.AnchorReveal,
            easing = NativeDoEasing.Standard,
        ),
        exit = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.AnchorSettle,
            easing = NativeDoEasing.Standard,
        ),
    ),
    bulletin = NativeDoBulletinMotionScheme(
        slideIn = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BulletinSlideIn,
            easing = NativeDoEasing.Standard,
        ),
        fadeIn = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BulletinFadeIn,
            easing = NativeDoEasing.Standard,
        ),
        slideOut = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BulletinSlideOut,
            easing = NativeDoEasing.Standard,
        ),
        fadeOut = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BulletinFadeOut,
            easing = NativeDoEasing.Standard,
        ),
    ),
    browser = NativeDoBrowserMotionScheme(
        open = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BrowserOpen,
            easing = NativeDoEasing.EaseOutQuint,
        ),
        search = NativeDoEnterExitMotionScheme(
            enter = NativeDoMotionSpec(
                durationMillis = NativeDoDuration.BrowserSearchEnter,
                easing = NativeDoEasing.Standard,
            ),
            exit = NativeDoMotionSpec(
                durationMillis = NativeDoDuration.BrowserSearchExit,
                easing = NativeDoEasing.Standard,
            ),
        ),
        address = NativeDoEnterExitMotionScheme(
            enter = NativeDoMotionSpec(
                durationMillis = NativeDoDuration.BrowserAddressEnter,
                easing = NativeDoEasing.Standard,
            ),
            exit = NativeDoMotionSpec(
                durationMillis = NativeDoDuration.BrowserAddressExit,
                easing = NativeDoEasing.BrowserDismiss,
            ),
        ),
        fullscreen = NativeDoMotionSpec(
            durationMillis = NativeDoDuration.BrowserFullscreen,
            easing = NativeDoEasing.EaseOutQuint,
        ),
    ),
)

val LocalNativeDoMotionScheme = staticCompositionLocalOf { DefaultNativeDoMotionScheme }

object NativeDoMotionTheme {
    val scheme: NativeDoMotionScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalNativeDoMotionScheme.current
}

fun nativeDoMotionProgressValue(
    progress: Float,
    start: Float,
    end: Float,
): Float = lerp(
    start = start,
    stop = end,
    fraction = progress.coerceIn(0f, 1f),
)
