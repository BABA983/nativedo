package io.github.baba983.nativedo.core.web

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DiscourseSvgSpriteCaptureTest {
    @Test
    fun looksLikeDiscourseSvgSpriteMarkup_requiresSymbolAndViewBox() {
        val valid = """
            <svg xmlns="http://www.w3.org/2000/svg">
              <symbol id="far-pen-to-square" viewBox="0 0 512 512">
                <path d="M0 0"/>
              </symbol>
            </svg>
        """.trimIndent()
        assertThat(looksLikeDiscourseSvgSpriteMarkup(valid)).isTrue()
        assertThat(looksLikeDiscourseSvgSpriteMarkup("")).isFalse()
        assertThat(looksLikeDiscourseSvgSpriteMarkup("<div>no symbols</div>")).isFalse()
    }

    @Test
    fun saveDiscourseSvgSpriteMarkup_persistsValidSprite() {
        val store = SvgSpriteStore()
        val markup = """
            <symbol id="copy" viewBox="0 0 16 16"><path d="M0 0"/></symbol>
        """.trimIndent()

        assertThat(saveDiscourseSvgSpriteMarkup(store, markup)).isTrue()
        assertThat(store.sprite.value).contains("id=\"copy\"")
        assertThat(saveDiscourseSvgSpriteMarkup(store, "not a sprite")).isFalse()
    }

}
