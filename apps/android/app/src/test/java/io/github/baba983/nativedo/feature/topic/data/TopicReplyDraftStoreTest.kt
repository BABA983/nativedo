package io.github.baba983.nativedo.feature.topic.data

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TopicReplyDraftStoreTest {
    @Test
    fun sharedPreferencesDraftsAreIsolatedBySiteUserAndTopic() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("topic-draft-test", Context.MODE_PRIVATE)
        val store = TopicReplyDraftStore(SharedPreferencesTopicReplyDraftPersistence(preferences))
        val aliceTopic = TopicReplyDraftKey("https://linux.do/", "Alice", 101L)
        val bobTopic = TopicReplyDraftKey("https://linux.do", "bob", 101L)
        val aliceOtherTopic = TopicReplyDraftKey("https://linux.do", "alice", 102L)

        store.save(aliceTopic, TopicReplyDraft(raw = "hello", replyToPostNumber = 3))

        assertThat(store.load(TopicReplyDraftKey("https://linux.do", "alice", 101L)))
            .isEqualTo(TopicReplyDraft(raw = "hello", replyToPostNumber = 3))
        assertThat(store.load(bobTopic)).isEqualTo(TopicReplyDraft())
        assertThat(store.load(aliceOtherTopic)).isEqualTo(TopicReplyDraft())
    }

    @Test
    fun emptyDraftRemovesPersistedTextAndReplyTarget() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("topic-draft-remove-test", Context.MODE_PRIVATE)
        val store = TopicReplyDraftStore(SharedPreferencesTopicReplyDraftPersistence(preferences))
        val key = TopicReplyDraftKey("https://linux.do", "alice", 101L)
        store.save(key, TopicReplyDraft(raw = "draft", replyToPostNumber = 2))

        store.save(key, TopicReplyDraft())

        assertThat(store.load(key)).isEqualTo(TopicReplyDraft())
        assertThat(preferences.all).isEmpty()
    }
}
