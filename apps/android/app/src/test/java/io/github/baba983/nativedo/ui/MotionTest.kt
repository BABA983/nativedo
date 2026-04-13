package io.github.baba983.nativedo.ui

import androidx.compose.animation.core.TweenSpec
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.theme.NativeDoDuration
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoMotionPreset
import org.junit.Test

class MotionTest {
    @Test
    fun telegramInspiredDurations_matchExpectedValues() {
        assertThat(NativeDoDuration.PopupOpen).isEqualTo(150)
        assertThat(NativeDoDuration.PopupClose).isEqualTo(150)
        assertThat(NativeDoDuration.PopupCascadeStep).isEqualTo(16)
        assertThat(NativeDoDuration.ColorBlend).isEqualTo(200)
        assertThat(NativeDoDuration.BrowserOpen).isEqualTo(220)
        assertThat(NativeDoDuration.BrowserSearchEnter).isEqualTo(320)
        assertThat(NativeDoDuration.BrowserAddressExit).isEqualTo(280)
        assertThat(NativeDoDuration.PageHorizontal).isEqualTo(320)
        assertThat(NativeDoDuration.AnchorReveal).isEqualTo(140)
        assertThat(NativeDoDuration.AnchorSettle).isEqualTo(180)
        assertThat(NativeDoDuration.BulletinSlideIn).isEqualTo(240)
        assertThat(NativeDoDuration.BulletinFadeIn).isEqualTo(180)
        assertThat(NativeDoDuration.BulletinSlideOut).isEqualTo(200)
        assertThat(NativeDoDuration.BulletinFadeOut).isEqualTo(160)
    }

    @Test
    fun popupOpenPreset_usesPopupOpenDurationAndStandardEasing() {
        val spec = NativeDoMotionPreset.popupOpenFloat()

        assertThat(spec.durationMillis).isEqualTo(NativeDoDuration.PopupOpen)
        assertThat(spec.easing.transform(0.5f))
            .isEqualTo(NativeDoEasing.Standard.transform(0.5f))
    }

    @Test
    fun browserFullscreenPreset_usesTelegramStyleQuintCurve() {
        val spec: TweenSpec<Float> = NativeDoMotionPreset.browserFullscreenFloat()

        assertThat(spec.durationMillis).isEqualTo(NativeDoDuration.BrowserFullscreen)
        assertThat(spec.easing.transform(0.5f))
            .isEqualTo(NativeDoEasing.EaseOutQuint.transform(0.5f))
    }

    @Test
    fun browserDismissCurve_matchesDedicatedDismissEasing() {
        assertThat(NativeDoEasing.BrowserDismiss.transform(0.5f))
            .isEqualTo(NativeDoEasing.BrowserDismiss.transform(0.5f))
    }
}
