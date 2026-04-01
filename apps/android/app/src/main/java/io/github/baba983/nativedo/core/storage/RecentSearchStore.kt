package io.github.baba983.nativedo.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecentSearchStore {
    private val queries = MutableStateFlow(emptyList<String>())

    val recentQueries: Flow<List<String>> = queries.asStateFlow()

    suspend fun record(query: String) {
        queries.value = listOf(query) + queries.value.filterNot { it == query }
    }
}
