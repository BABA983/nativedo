package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.text.input.ImeAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoSearchFieldTest {
    @Test
    fun placeholder_isVisibleWhenValueIsBlank() {
        assertThat(shouldShowNativeDoSearchFieldPlaceholder("")).isTrue()
        assertThat(shouldShowNativeDoSearchFieldPlaceholder("   ")).isTrue()
    }

    @Test
    fun placeholder_isHiddenWhenValueHasContent() {
        assertThat(shouldShowNativeDoSearchFieldPlaceholder("linux.do")).isFalse()
    }

    @Test
    fun readOnlyText_omitsBlankValues() {
        assertThat(nativeDoSearchFieldReadOnlyText("")) .isNull()
        assertThat(nativeDoSearchFieldReadOnlyText("   ")).isNull()
    }

    @Test
    fun readOnlyText_returnsNonBlankValue() {
        assertThat(nativeDoSearchFieldReadOnlyText("Search linux.do"))
            .isEqualTo("Search linux.do")
    }

    @Test
    fun imeAction_usesSearchWhenSubmitHandlerExists() {
        assertThat(nativeDoSearchFieldImeAction(hasSubmitAction = true))
            .isEqualTo(ImeAction.Search)
    }

    @Test
    fun imeAction_fallsBackToDefaultWithoutSubmitHandler() {
        assertThat(nativeDoSearchFieldImeAction(hasSubmitAction = false))
            .isEqualTo(ImeAction.Default)
    }
}
