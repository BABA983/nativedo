package io.github.baba983.nativedo.ui.topic.body

internal class PostBodyBlockState {
    val expandedQuotesByIndex = linkedMapOf<Int, String>()
    val revealedSpoilers = linkedSetOf<Int>()
    val selectedPollOptionsByIndex = linkedMapOf<Int, Int>()
}
