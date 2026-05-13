package io.github.baba983.nativedo.feature.profile.data

import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.DiscourseApiClient

open class ProfileRepository protected constructor() {
    private lateinit var client: DiscourseApiClient

    constructor(client: DiscourseApiClient) : this() {
        this.client = client
    }

    open suspend fun load(username: String): Result<UserProfile> = runCatching {
        client.fetchProfile(username)
    }
}
