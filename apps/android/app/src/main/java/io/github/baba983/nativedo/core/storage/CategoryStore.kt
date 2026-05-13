package io.github.baba983.nativedo.core.storage

import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CategoryStore(
    private val client: DiscourseApiClient,
) {
    private val mutex = Mutex()
    private val _categories = MutableStateFlow<Map<Int, CategorySummary>>(emptyMap())
    val categories: StateFlow<Map<Int, CategorySummary>> = _categories.asStateFlow()

    suspend fun loadIfNeeded(): Result<Map<Int, CategorySummary>> {
        categories.value
            .takeIf { it.isNotEmpty() }
            ?.let { cached -> return Result.success(cached) }

        return mutex.withLock {
            categories.value
                .takeIf { it.isNotEmpty() }
                ?.let { cached -> return@withLock Result.success(cached) }

            runCatching {
                client.fetchCategories()
                    .associateBy(CategorySummary::id)
                    .also { loaded -> _categories.value = loaded }
            }
        }
    }
}
