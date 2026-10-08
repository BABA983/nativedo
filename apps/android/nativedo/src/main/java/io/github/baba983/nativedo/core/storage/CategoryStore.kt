package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The site's categories, kept the way upstream keeps the folders its chat list filters by.
 *
 * <p>Upstream's folder strip is drawn from local storage, so it is up in the first frame of the
 * list; ours used to wait for {@code /categories.json}, which left the strip's band empty until the
 * site answered - and the swipe that switches categories is gated on the strip having tabs, so a
 * slow answer took the gesture with it. The last answer is therefore written down and handed back
 * before the network is asked, and the site's own answer replaces it when it arrives.</p>
 */
class CategoryStore(
    private val client: DiscourseApiClient,
    private val persistence: CategoryPersistence = NoCategoryPersistence,
) {
    private val mutex = Mutex()
    private val _categories = MutableStateFlow(persistence.load().associateBy(CategorySummary::id))
    val categories: StateFlow<Map<Int, CategorySummary>> = _categories.asStateFlow()

    /** What the last answer said, without asking the site; empty before the first answer. */
    fun cachedCategories(): List<CategorySummary> = persistence.load()

    /** Asks the site and writes the answer down; the reader's own call, so it always goes out. */
    suspend fun refresh(): Result<List<CategorySummary>> =
        mutex.withLock {
            runCatching { client.fetchCategories() }
                .onSuccess { loaded ->
                    _categories.value = loaded.associateBy(CategorySummary::id)
                    persistence.save(loaded)
                }
        }

    suspend fun loadIfNeeded(): Result<Map<Int, CategorySummary>> {
        categories.value
            .takeIf { it.isNotEmpty() }
            ?.let { cached -> return Result.success(cached) }

        return mutex.withLock {
            categories.value
                .takeIf { it.isNotEmpty() }
                ?.let { cached -> return@withLock Result.success(cached) }

            refresh().map { loaded -> loaded.associateBy(CategorySummary::id) }
        }
    }
}

/** Where the categories are kept between launches. */
interface CategoryPersistence {
    fun load(): List<CategorySummary>

    fun save(categories: List<CategorySummary>)
}

class SharedPreferencesCategoryPersistence(
    private val preferences: SharedPreferences,
    private val key: String,
) : CategoryPersistence {
    override fun load(): List<CategorySummary> {
        val stored = preferences.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, stored) }.getOrDefault(emptyList())
    }

    override fun save(categories: List<CategorySummary>) {
        preferences.edit().putString(key, json.encodeToString(serializer, categories)).apply()
    }

    companion object {
        /** The preference one site's categories live under; a second host keeps its own. */
        fun keyFor(baseUrl: String): String = "categories|" + baseUrl.trim().trimEnd('/').lowercase()

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(CategorySummary.serializer())
    }
}

/** A store with nowhere to write: the default, and what a container without a data dir gets. */
object NoCategoryPersistence : CategoryPersistence {
    override fun load(): List<CategorySummary> = emptyList()

    override fun save(categories: List<CategorySummary>) = Unit
}
