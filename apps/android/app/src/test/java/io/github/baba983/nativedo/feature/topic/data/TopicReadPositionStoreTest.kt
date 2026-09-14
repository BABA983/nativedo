package io.github.baba983.nativedo.feature.topic.data

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TopicReadPositionStoreTest {
    @Test
    fun sharedPreferencesPositionsAreIsolatedBySiteUserAndTopic() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("topic-read-position-test", Context.MODE_PRIVATE)
        val store = TopicReadPositionStore(SharedPreferencesTopicReadPositionPersistence(preferences))
        val aliceTopic = TopicReadPositionKey("https://linux.do/", "Alice", 101L)

        store.save(aliceTopic, 12)

        assertThat(store.load(TopicReadPositionKey("https://linux.do", "alice", 101L))).isEqualTo(12)
        assertThat(store.load(TopicReadPositionKey("https://linux.do", "bob", 101L))).isEqualTo(0)
        assertThat(store.load(TopicReadPositionKey("https://linux.do", "alice", 102L))).isEqualTo(0)
    }

    @Test
    fun savedPositionNeverMovesBackward() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("topic-read-position-monotonic-test", Context.MODE_PRIVATE)
        val store = TopicReadPositionStore(SharedPreferencesTopicReadPositionPersistence(preferences))
        val key = TopicReadPositionKey("https://linux.do", "alice", 101L)

        store.save(key, 12)
        store.save(key, 8)
        store.save(key, -1)

        assertThat(store.load(key)).isEqualTo(12)
    }
}
