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
class TopicViewportStoreTest {
    @Test
    fun returningToFirstPostPersistsWithoutRewindingReadProgress() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("viewport", Context.MODE_PRIVATE)
        val key = TopicReadPositionKey("https://linux.do", "alice", 42)
        val read = TopicReadPositionStore(SharedPreferencesTopicReadPositionPersistence(prefs))
        val viewport = TopicViewportStore(prefs)
        read.save(key, 20)
        viewport.save(key, TopicViewport(20, -300))
        viewport.save(key, TopicViewport(1, -45))
        assertThat(TopicViewportStore(prefs).load(key)).isEqualTo(TopicViewport(1, -45))
        assertThat(read.load(key)).isEqualTo(20)
        assertThat(viewport.load(key.copy(username = "bob"))).isNull()
        assertThat(viewport.load(key.copy(topicId = 43))).isNull()
    }
}
