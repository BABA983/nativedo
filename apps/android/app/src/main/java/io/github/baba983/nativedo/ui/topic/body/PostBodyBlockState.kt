package io.github.baba983.nativedo.ui.topic.body

internal class PostBodyBlockState {
    val expandedQuotesByIndex = linkedMapOf<Int, String>()
    val openedDetailsByIndex = linkedSetOf<Int>()
    val closedDetailsByIndex = linkedSetOf<Int>()
    val openedCalloutsByIndex = linkedSetOf<Int>()
    val closedCalloutsByIndex = linkedSetOf<Int>()
    val selectedPollOptionIndexesByIndex = linkedMapOf<Int, MutableSet<Int>>()
    val submittedPollIndexes = linkedSetOf<Int>()
    val removedPollIndexes = linkedSetOf<Int>()
    val inFlightPollIndexes = linkedSetOf<Int>()
    val revealedInlineSpoilersByKey = linkedSetOf<String>()
}
