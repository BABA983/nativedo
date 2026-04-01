package io.github.baba983.nativedo.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RootDestinationTest {

    @Test
    fun bottomTabs_areStable() {
        assertThat(RootDestination.bottomTabs.map { it.route })
            .containsExactly("home", "profile")
            .inOrder()
    }

    @Test
    fun startDestination_isHome() {
        assertThat(RootDestination.start.route).isEqualTo("home")
    }
}
