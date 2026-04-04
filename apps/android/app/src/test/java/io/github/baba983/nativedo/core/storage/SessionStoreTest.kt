package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.SessionSnapshot
import org.junit.Test

class SessionStoreTest {
    @Test
    fun save_persistsSnapshotAcrossInstances() {
        val persistence = InMemorySessionStorePersistence()
        val firstStore = SessionStore(persistence = persistence)

        firstStore.save(
            SessionSnapshot(
                username = "baba",
                displayName = "Baba",
                avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                lastValidatedAt = 1_717_123_456L,
            ),
        )

        val secondStore = SessionStore(persistence = persistence)

        assertThat(secondStore.snapshot()).isEqualTo(
            SessionSnapshot(
                username = "baba",
                displayName = "Baba",
                avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                lastValidatedAt = 1_717_123_456L,
            ),
        )
    }
}

private class InMemorySessionStorePersistence : SessionStorePersistence {
    private var snapshot: SessionSnapshot? = null

    override fun load(): SessionSnapshot? = snapshot

    override fun save(snapshot: SessionSnapshot?) {
        this.snapshot = snapshot
    }
}
