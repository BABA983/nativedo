package io.github.baba983.nativedo.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RecentSearchStore {
    val recentQueries: Flow<List<String>>

    suspend fun record(query: String)
}

class InMemoryRecentSearchStore : RecentSearchStore {
    private val queries = MutableStateFlow(emptyList<String>())

    override val recentQueries: Flow<List<String>> = queries.asStateFlow()

    override suspend fun record(query: String) {
        queries.value = listOf(query) + queries.value.filterNot { it == query }
    }
}
