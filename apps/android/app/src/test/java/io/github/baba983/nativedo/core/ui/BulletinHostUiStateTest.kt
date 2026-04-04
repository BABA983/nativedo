package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BulletinHostUiStateTest {
    @Test
    fun show_setsMessageAndMarksItVisible() {
        val message = BulletinMessage(title = "Can't reach Linux.do")

        val state = BulletinHostUiState().show(message)

        assertThat(state.currentMessage).isEqualTo(message)
        assertThat(state.isVisible).isTrue()
    }

    @Test
    fun hide_keepsCurrentMessageUntilAnimationFinishes() {
        val message = BulletinMessage(title = "Can't reach Linux.do")

        val state = BulletinHostUiState(currentMessage = message, isVisible = true).hide()

        assertThat(state.currentMessage).isEqualTo(message)
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun clear_resetsMessageAndVisibility() {
        val state = BulletinHostUiState(
            currentMessage = BulletinMessage(title = "Can't reach Linux.do"),
            isVisible = false,
        ).clear()

        assertThat(state.currentMessage).isNull()
        assertThat(state.isVisible).isFalse()
    }
}
