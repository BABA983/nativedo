package io.github.baba983.nativedo.ui.profile

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedUiState
import org.junit.Test

class ProfileFeedPaginationTest {
    @Test
    fun shortVisiblePageRequestsMoreWithoutWaitingForScroll() {
        val state = ProfileFeedUiState(isLoading = false, hasMore = true)

        assertThat(
            shouldRequestProfileFeedPage(
                state = state,
                lastVisibleItem = 2,
                itemCount = 3,
            ),
        ).isTrue()
    }

    @Test
    fun emptyFilteredPageCanContinueFromTheRawServerCursor() {
        val state = ProfileFeedUiState(isLoading = false, hasMore = true)

        assertThat(
            shouldRequestProfileFeedPage(
                state = state,
                lastVisibleItem = NO_POSITION,
                itemCount = 0,
            ),
        ).isTrue()
    }

    @Test
    fun activeOrFailedPaginationDoesNotRequestAgain() {
        val loading = ProfileFeedUiState(
            isLoading = false,
            isLoadingMore = true,
            hasMore = true,
        )
        val failed = ProfileFeedUiState(
            isLoading = false,
            hasMore = true,
            loadMoreErrorMessage = "offline",
        )

        assertThat(shouldRequestProfileFeedPage(loading, 19, 20)).isFalse()
        assertThat(shouldRequestProfileFeedPage(failed, 19, 20)).isFalse()
    }

    private companion object {
        const val NO_POSITION = -1
    }
}
