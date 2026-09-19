package io.github.baba983.nativedo.feature.topic.ui

data class PollVotersSheetArgs @JvmOverloads constructor(
    val pollName: String,
    val pollTitle: String? = null,
    val options: List<PollOptionRef> = emptyList(),
    val initialSelectedOptionId: String? = null,
    val totalVoters: Int = 0,
    val multiple: Boolean = false,
)

data class PollOptionRef(
    val id: String,
    val label: String,
    val votes: Int = 0,
)
