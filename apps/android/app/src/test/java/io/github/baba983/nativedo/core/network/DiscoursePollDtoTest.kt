package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import org.junit.Test

class DiscoursePollDtoTest {
    @Test
    fun withSerializedPollData_clearsChosenWhenPollsVotesRetracted() {
        val cooked = """
            <div class="poll" data-poll-name="poll3">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="a" class="chosen">Option A</li>
                  <li data-poll-option-id="b">Option B</li>
                </ul>
              </div>
            </div>
        """.trimIndent()

        val enriched = cooked.withSerializedPollData(
            polls = listOf(
                DiscoursePollDto(
                    name = "poll3",
                    type = "regular",
                    status = "open",
                    options = listOf(
                        DiscoursePollOptionDto(id = "a", html = "Option A"),
                        DiscoursePollOptionDto(id = "b", html = "Option B"),
                    ),
                ),
            ),
            pollsVotes = emptyMap(),
        )

        val poll = parseCookedPostBody(enriched, "https://linux.do/")
            .filterIsInstance<CookedPostBlock.Poll>()
            .single()

        assertThat(poll.options[0].selected).isFalse()
        assertThat(poll.options[1].selected).isFalse()
    }
}
