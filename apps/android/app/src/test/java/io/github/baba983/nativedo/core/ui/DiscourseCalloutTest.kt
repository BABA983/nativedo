package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscourseCalloutTest {
    @Test
    fun resolveDiscourseCalloutSpec_mapsDefaultNoteType() {
        val spec = resolveDiscourseCalloutSpec("note")

        assertThat(spec).isEqualTo(
            DiscourseCalloutSpec(
                type = "note",
                icon = "far-pen-to-square",
                color = "#086ddd",
            ),
        )
    }

    @Test
    fun resolveDiscourseCalloutSpec_mapsAliasToCanonicalType() {
        val spec = resolveDiscourseCalloutSpec("important")

        assertThat(spec).isEqualTo(
            DiscourseCalloutSpec(
                type = "tip",
                icon = "fire-flame-curved",
                color = "#00bfbc",
            ),
        )
    }

    @Test
    fun resolveDiscourseCalloutSpec_fallsBackToNoteWhenTypeUnknown() {
        val spec = resolveDiscourseCalloutSpec("unknown")

        assertThat(spec).isEqualTo(
            DiscourseCalloutSpec(
                type = "note",
                icon = "far-pen-to-square",
                color = "#027aff",
            ),
        )
    }

    @Test
    fun resolveDiscourseCalloutSpec_prefersRenderedDomIconWhenProvided() {
        val spec = resolveDiscourseCalloutSpec(
            rawType = "note",
            rawIcon = "#triangle-exclamation",
        )

        assertThat(spec).isEqualTo(
            DiscourseCalloutSpec(
                type = "note",
                icon = "triangle-exclamation",
                color = "#086ddd",
            ),
        )
    }
}
