package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativePageStackStateTest {
    @Test
    fun state_isEmptyByDefault() {
        val state = NativePageStackState()

        assertThat(state.currentEntry).isNull()
        assertThat(state.previousEntry).isNull()
        assertThat(state.currentRoutePattern).isNull()
        assertThat(state.hasPushedEntry).isFalse()
        assertThat(state.depth).isEqualTo(0)
    }

    @Test
    fun presentTopic_setsCurrentEntryAndRoutePattern() {
        val state = NativePageStackState()

        state.presentTopic(42L)

        assertThat(state.currentEntry).isEqualTo(NativePageEntry.Topic(topicId = 42L))
        assertThat(state.previousEntry).isNull()
        assertThat(state.currentRoutePattern).isEqualTo("topic/{topicId}")
        assertThat(state.hasPushedEntry).isTrue()
        assertThat(state.depth).isEqualTo(1)
    }

    @Test
    fun presentUser_keepsPreviousEntryAvailableForBackNavigation() {
        val state = NativePageStackState()

        state.presentTopic(42L)
        state.presentUser("baba")

        assertThat(state.currentEntry).isEqualTo(NativePageEntry.User(username = "baba"))
        assertThat(state.previousEntry).isEqualTo(NativePageEntry.Topic(topicId = 42L))
        assertThat(state.currentRoutePattern).isEqualTo("user/{username}")
        assertThat(state.depth).isEqualTo(2)
    }

    @Test
    fun closeLast_returnsToPreviousEntryBeforeClearingStack() {
        val state = NativePageStackState()

        state.presentTopic(42L)
        state.presentUser("baba")

        assertThat(state.closeLast()).isTrue()
        assertThat(state.currentEntry).isEqualTo(NativePageEntry.Topic(topicId = 42L))
        assertThat(state.previousEntry).isNull()
        assertThat(state.depth).isEqualTo(1)

        assertThat(state.closeLast()).isTrue()
        assertThat(state.currentEntry).isNull()
        assertThat(state.hasPushedEntry).isFalse()
        assertThat(state.depth).isEqualTo(0)
    }

    @Test
    fun closeLast_returnsFalseWhenStackIsEmpty() {
        val state = NativePageStackState()

        assertThat(state.closeLast()).isFalse()
    }

    @Test
    fun predictiveBackPivotXFraction_keepsPivotNearTrailingEdge() {
        assertThat(
            predictiveBackPivotXFraction(
                containerWidthPx = 400f,
                pivotInsetPx = 82f,
                pivotFromTrailingEdge = true,
            ),
        ).isWithin(0.0001f).of(0.795f)
    }

    @Test
    fun predictiveBackPivotXFraction_movesPivotNearLeadingEdgeForRightEdgeBack() {
        assertThat(
            predictiveBackPivotXFraction(
                containerWidthPx = 400f,
                pivotInsetPx = 82f,
                pivotFromTrailingEdge = false,
            ),
        ).isWithin(0.0001f).of(0.205f)
    }

    @Test
    fun predictiveBackPivotYFraction_tracksGestureStartPosition() {
        assertThat(
            predictiveBackPivotYFraction(
                containerHeightPx = 1000f,
                touchYPx = 40f,
            ),
        ).isWithin(0.0001f).of(0.04f)

        assertThat(
            predictiveBackPivotYFraction(
                containerHeightPx = 1000f,
                touchYPx = 940f,
            ),
        ).isWithin(0.0001f).of(0.94f)
    }

    @Test
    fun normalizeInteractiveBackProgress_usesTelegramPredictiveRange() {
        assertThat(
            normalizeInteractiveBackProgress(
                offsetPx = 28f,
                maxOffsetPx = 56f,
            ),
        ).isWithin(0.0001f).of(0.5f)

        assertThat(
            normalizeInteractiveBackProgress(
                offsetPx = 140f,
                maxOffsetPx = 56f,
            ),
        ).isWithin(0.0001f).of(1f)

        assertThat(
            normalizeInteractiveBackProgress(
                offsetPx = -12f,
                maxOffsetPx = 56f,
            ),
        ).isWithin(0.0001f).of(0f)
    }

    @Test
    fun manualEdgeBackProgress_reachesFullPredictiveMotionAtCloseThreshold() {
        assertThat(
            manualEdgeBackProgress(
                offsetPx = 60f,
                containerWidthPx = 400f,
            ),
        ).isWithin(0.0001f).of(0.5f)

        assertThat(
            manualEdgeBackProgress(
                offsetPx = 120f,
                containerWidthPx = 400f,
            ),
        ).isWithin(0.0001f).of(1f)
    }

    @Test
    fun resolveTelegramPredictiveBackLayerOffsets_movesHostDuringLiveGesture() {
        assertThat(
            resolveTelegramPredictiveBackLayerOffsets(
                progress = 0.5f,
                settleTranslationPx = 0f,
                isCompleting = false,
                maxOffsetPx = 56f,
            ).hostTranslationPx,
        ).isWithin(0.0001f).of(28f)

        assertThat(
            resolveTelegramPredictiveBackLayerOffsets(
                progress = 0.5f,
                settleTranslationPx = 0f,
                isCompleting = false,
                maxOffsetPx = 56f,
            ).innerCompensationTranslationPx,
        ).isWithin(0.0001f).of(-28f)
    }

    @Test
    fun resolveTelegramPredictiveBackLayerOffsets_capsInnerCompensationAtGestureRangeDuringCompletion() {
        assertThat(
            resolveTelegramPredictiveBackLayerOffsets(
                progress = 0.75f,
                settleTranslationPx = 320f,
                isCompleting = true,
                maxOffsetPx = 56f,
            ).hostTranslationPx,
        ).isWithin(0.0001f).of(320f)

        assertThat(
            resolveTelegramPredictiveBackLayerOffsets(
                progress = 0.75f,
                settleTranslationPx = 320f,
                isCompleting = true,
                maxOffsetPx = 56f,
            ).innerCompensationTranslationPx,
            ).isWithin(0.0001f).of(-56f)
    }

    @Test
    fun shouldUseInteractiveBackCompletion_requiresRealPredictiveProgressEvents() {
        assertThat(
            shouldUseInteractiveBackCompletion(
                receivedPredictiveProgressEvents = true,
            ),
        ).isTrue()

        assertThat(
            shouldUseInteractiveBackCompletion(
                receivedPredictiveProgressEvents = false,
            ),
        ).isFalse()
    }

    @Test
    fun resolveStandardEntryRenderProgress_doesNotForceCompletedOpenAnimationBackToZero() {
        assertThat(
            resolveStandardEntryRenderProgress(
                entryProgress = 1f,
                currentDepth = 2,
                previousDepth = 1,
            ),
        ).isWithin(0.0001f).of(1f)

        assertThat(
            resolveStandardEntryRenderProgress(
                entryProgress = 1f,
                currentDepth = 1,
                previousDepth = 1,
            ),
        ).isWithin(0.0001f).of(1f)
    }

    @Test
    fun telegramPredictiveBackScale_matchesTelegramScaleCurve() {
        assertThat(
            telegramPredictiveBackScale(
                progress = 0.5f,
                currentAlpha = 1f,
            ),
        ).isWithin(0.0001f).of(0.95f)

        assertThat(
            telegramPredictiveBackScale(
                progress = 1f,
                currentAlpha = 1f,
            ),
        ).isWithin(0.0001f).of(0.9f)

        assertThat(
            telegramPredictiveBackScale(
                progress = 1f,
                currentAlpha = 0f,
            ),
        ).isWithin(0.0001f).of(0.85f)
    }

    @Test
    fun telegramPredictiveBackScrimAlpha_matchesTelegramOpacityRange() {
        assertThat(
            telegramPredictiveBackScrimAlpha(
                translationXPx = 0f,
                containerWidthPx = 1000f,
            ),
        ).isWithin(0.0001f).of(96f / 255f)

        assertThat(
            telegramPredictiveBackScrimAlpha(
                translationXPx = 500f,
                containerWidthPx = 1000f,
            ),
        ).isWithin(0.0001f).of(60f / 255f)

        assertThat(
            telegramPredictiveBackScrimAlpha(
                translationXPx = 1000f,
                containerWidthPx = 1000f,
            ),
        ).isWithin(0.0001f).of(0f)
    }
}
