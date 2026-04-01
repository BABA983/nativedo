package io.github.baba983.nativedo.core.storage

class SessionStore {
    private var currentUsername: String? = null

    fun save(username: String) {
        currentUsername = username
    }

    fun username(): String? = currentUsername

    fun clear() {
        currentUsername = null
    }
}
