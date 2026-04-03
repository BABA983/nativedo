package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.navigation.RootDestination
import org.junit.Test

class NativeDoAppInteractionTest {

    @Test
    fun titleTap_requestsHomeRefreshOnlyOnRootHome() {
        assertThat(
            shouldRequestHomeRefreshFromTitleTap(
                currentRoute = RootDestination.Home.route,
                showBack = false,
            ),
        ).isTrue()

        assertThat(
            shouldRequestHomeRefreshFromTitleTap(
                currentRoute = RootDestination.Profile.route,
                showBack = false,
            ),
        ).isFalse()

        assertThat(
            shouldRequestHomeRefreshFromTitleTap(
                currentRoute = RootDestination.Home.route,
                showBack = true,
            ),
        ).isFalse()
    }

    @Test
    fun homeTabDoubleTap_requestsRefreshOnlyWhenAlreadyOnHome() {
        assertThat(
            shouldRequestHomeRefreshFromBottomTabDoubleTap(
                destination = RootDestination.Home,
                currentRoute = RootDestination.Home.route,
            ),
        ).isTrue()

        assertThat(
            shouldRequestHomeRefreshFromBottomTabDoubleTap(
                destination = RootDestination.Home,
                currentRoute = RootDestination.Profile.route,
            ),
        ).isFalse()
    }

    @Test
    fun nonHomeTabDoubleTap_doesNotRequestRefresh() {
        assertThat(
            shouldRequestHomeRefreshFromBottomTabDoubleTap(
                destination = RootDestination.Profile,
                currentRoute = RootDestination.Profile.route,
            ),
        ).isFalse()
    }
}
