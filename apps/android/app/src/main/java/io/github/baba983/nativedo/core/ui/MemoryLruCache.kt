package io.github.baba983.nativedo.core.ui

internal class MemoryLruCache<T>(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<String, T>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: String): T? = entries[key]

    @Synchronized
    fun put(key: String, value: T) {
        entries[key] = value
    }
}
