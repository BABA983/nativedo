package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryLruCacheTest {
    @Test
    fun putThenGet_returnsCachedValue() {
        val cache = MemoryLruCache<String>(maxEntries = 2)

        cache.put("first", "value")

        assertThat(cache.get("first")).isEqualTo("value")
    }

    @Test
    fun put_evictsLeastRecentlyUsedEntryWhenCapacityExceeded() {
        val cache = MemoryLruCache<String>(maxEntries = 2)

        cache.put("first", "value-1")
        cache.put("second", "value-2")
        cache.get("first")
        cache.put("third", "value-3")

        assertThat(cache.get("first")).isEqualTo("value-1")
        assertThat(cache.get("second")).isNull()
        assertThat(cache.get("third")).isEqualTo("value-3")
    }
}
