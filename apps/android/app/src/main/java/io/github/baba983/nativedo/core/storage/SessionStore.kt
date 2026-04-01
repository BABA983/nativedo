package io.github.baba983.nativedo.core.storage

interface SessionStore {
    fun save(username: String)

    fun username(): String?

    fun clear()
}

class InMemorySessionStore : SessionStore {
    private var currentUsername: String? = null

    override fun save(username: String) {
        currentUsername = username
    }

    override fun username(): String? = currentUsername

    override fun clear() {
        currentUsername = null
    }
}
