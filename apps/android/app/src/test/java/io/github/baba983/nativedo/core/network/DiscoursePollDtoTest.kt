package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import org.junit.Test

class DiscoursePollDtoTest {
    @Test
    fun withSerializedPollData_fillsDiscoursePollOptionsIntoCookedHtml() {
        val cooked = """
            <h2>岗位属性</h2>
            <div class="poll" data-poll-name="poll3">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="a"></li>
                  <li data-poll-option-id="b"></li>
                  <li data-poll-option-id="c"></li>
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
                    isPublic = true,
                    results = "always",
                    voters = 3,
                    chartType = "bar",
                    title = null,
                    options = listOf(
                        DiscoursePollOptionDto(id = "a", html = "公司老板 / 创始人"),
                        DiscoursePollOptionDto(id = "b", html = "产品 / 运营"),
                        DiscoursePollOptionDto(id = "c", html = "研发 / 技术"),
                    ),
                ),
            ),
            pollsVotes = mapOf("poll3" to listOf("b")),
        )

        val poll = parseCookedPostBody(enriched, "https://linux.do/")
            .filterIsInstance<CookedPostBlock.Poll>()
            .single()

        assertThat(poll.name).isEqualTo("poll3")
        assertThat(poll.title).isEqualTo("岗位属性")
        assertThat(poll.type).isEqualTo("regular")
        assertThat(poll.status).isEqualTo("open")
        assertThat(poll.results).isEqualTo("always")
        assertThat(poll.publicPoll).isTrue()
        assertThat(poll.chartType).isEqualTo("bar")
        assertThat(poll.options.map { it.label })
            .containsExactly("公司老板 / 创始人", "产品 / 运营", "研发 / 技术")
            .inOrder()
        assertThat(poll.options.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(poll.options[1].selected).isTrue()
    }

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

    @Test
    fun withSerializedPollData_syncsTotalVotersAndOptionVoteCounts() {
        val cooked = """
            <div class="poll" data-poll-name="poll3">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="a"></li>
                  <li data-poll-option-id="b"></li>
                </ul>
              </div>
              <div class="poll-info">
                <div class="poll-info_counts">
                  <div class="poll-info_counts-count">
                    <span class="info-number">0</span>
                  </div>
                </div>
              </div>
            </div>
        """.trimIndent()

        val enriched = cooked.withSerializedPollData(
            polls = listOf(
                DiscoursePollDto(
                    name = "poll3",
                    type = "regular",
                    status = "open",
                    voters = 19,
                    options = listOf(
                        DiscoursePollOptionDto(id = "a", html = "Option A", votes = 4),
                        DiscoursePollOptionDto(id = "b", html = "Option B", votes = 15),
                    ),
                ),
            ),
        )

        val poll = parseCookedPostBody(enriched, "https://linux.do/")
            .filterIsInstance<CookedPostBlock.Poll>()
            .single()

        assertThat(poll.voters).isEqualTo(19)
        assertThat(poll.options.map { it.votes }).containsExactly(4, 15).inOrder()
    }
}
