package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import org.junit.Test

class TopicRouteStateTest {
    @Test
    fun shouldShowCollapsedTopicTitle_returnsFalseWhenHeaderIsVisible() {
        assertThat(
            shouldShowCollapsedTopicTitle(
                title = "Compose on linux.do",
                visibleItemIndexes = listOf(0, 1, 2),
            ),
        ).isFalse()
    }

    @Test
    fun shouldShowCollapsedTopicTitle_returnsTrueWhenHeaderScrolledAway() {
        assertThat(
            shouldShowCollapsedTopicTitle(
                title = "Compose on linux.do",
                visibleItemIndexes = listOf(1, 2, 3),
            ),
        ).isTrue()
    }

    @Test
    fun shouldShowCollapsedTopicTitle_returnsFalseBeforeListIsMeasured() {
        assertThat(
            shouldShowCollapsedTopicTitle(
                title = "Compose on linux.do",
                visibleItemIndexes = emptyList(),
            ),
        ).isFalse()
    }

    @Test
    fun shouldRefreshTopicForHandledAuthVersion_authenticatedWithNewVersion_returnsTrue() {
        val shouldRefresh = shouldRefreshTopicForHandledAuthVersion(
            sessionState = AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
                freshness = SessionFreshness.Verified,
            ),
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isTrue()
    }

    @Test
    fun shouldRefreshTopicForHandledAuthVersion_anonymous_returnsFalse() {
        val shouldRefresh = shouldRefreshTopicForHandledAuthVersion(
            sessionState = AppSessionState.Anonymous,
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isFalse()
    }

    @Test
    fun resolveInitialTopicScrollSnapshot_returnsSavedScrollWhenAvailable() {
        assertThat(
            resolveInitialTopicScrollSnapshot(
                TopicScrollSnapshot(
                    firstVisibleItemIndex = 4,
                    firstVisibleItemScrollOffset = 320,
                ),
            ),
        ).isEqualTo(
            TopicScrollSnapshot(
                firstVisibleItemIndex = 4,
                firstVisibleItemScrollOffset = 320,
            ),
        )
    }

    @Test
    fun resolveInitialTopicScrollSnapshot_defaultsToTopWhenMissing() {
        assertThat(
            resolveInitialTopicScrollSnapshot(null),
        ).isEqualTo(
            TopicScrollSnapshot(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }
}
