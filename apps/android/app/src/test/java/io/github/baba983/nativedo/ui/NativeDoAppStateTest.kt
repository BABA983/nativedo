package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.navigation.RootDestination
import org.junit.Test

class NativeDoAppStateTest {
    @Test
    fun topicRouteTitleOverride_usesTopicOverrideOnTopicRoute() {
        assertThat(
            topicRouteTitleOverride(
                currentRoute = "topic/{topicId}",
                topicTitleOverride = "Compose on linux.do",
            ),
        ).isEqualTo("Compose on linux.do")
    }

    @Test
    fun topicRouteTitleOverride_ignoresBlankTitleAndNonTopicRoutes() {
        assertThat(
            topicRouteTitleOverride(
                currentRoute = "topic/{topicId}",
                topicTitleOverride = "",
            ),
        ).isNull()

        assertThat(
            topicRouteTitleOverride(
                currentRoute = RootDestination.Search.route,
                topicTitleOverride = "Compose on linux.do",
            ),
        ).isNull()
    }

    @Test
    fun shouldRenderProfileAvatarTab_returnsTrueForLoggedInProfileTab() {
        assertThat(
            shouldRenderProfileAvatarTab(
                destination = RootDestination.Profile,
                profileAvatarLabel = "baba",
                profileAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            ),
        ).isTrue()
    }

    @Test
    fun shouldRenderProfileAvatarTab_returnsFalseForOtherTabsOrMissingAvatar() {
        assertThat(
            shouldRenderProfileAvatarTab(
                destination = RootDestination.Home,
                profileAvatarLabel = "baba",
                profileAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            ),
        ).isFalse()

        assertThat(
            shouldRenderProfileAvatarTab(
                destination = RootDestination.Profile,
                profileAvatarLabel = "baba",
                profileAvatarTemplate = "",
            ),
        ).isFalse()
    }
}
